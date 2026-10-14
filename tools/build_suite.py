"""Build monorepo products without installing services, drivers or private identities."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--component', choices=['all', 'android', 'suite', 'bridge', 'driver'], default='all')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--configuration', choices=['Debug', 'Release'], default='Debug')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--test', action='store_true')
    parser.add_argument('--plan', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    if output == ROOT or output.is_relative_to(ROOT):
        parser.error('--output must be outside the source repository')
    if args.component in ['all', 'bridge', 'driver'] and os.name != 'nt':
        parser.error('Bridge/driver require Windows; select android or suite on this platform')
    config = args.configuration
    windows = os.name == 'nt'
    gradle = str(ROOT / ('gradlew.bat' if windows else 'gradlew'))
    native_artifacts = output / 'dotnet'
    packages = Path(os.environ.get('NUGET_PACKAGES', str(output / 'nuget-packages'))).resolve()
    init = output / 'build-output.gradle'
    integration = output / 'bridge-integration'
    jobs = []
    if args.component in ['all', 'bridge']:
        jobs.append([sys.executable, str(ROOT / 'tools/windows-manager/package-manager.py'),
                     '--configuration', config, '--output', str(output / 'bridge'),
                     '--artifacts', str(native_artifacts), '--packages', str(packages)])
    if args.component == 'all':
        jobs.append(['dotnet', 'build', str(ROOT / 'tools/windows-suite-integration/WindowsSuiteIntegration.csproj'),
                     '-c', config, '--artifacts-path', str(native_artifacts), '-p:RestorePackagesPath=' + str(packages)])
    tasks = []
    if args.component in ['all', 'android']:
        tasks.append(':app:assemble' + config)
        if args.test:
            tasks += [':app:testDebugUnitTest', ':dnie-bridge-android:testDebugUnitTest', ':dnie-pdf:testDebugUnitTest']
    if args.component in ['all', 'suite']:
        tasks += [':dnie-signing-desktop:installDist', ':dnie-signing-ui:installDist']
        if args.test:
            tasks += [':dnie-signing-desktop:test', ':dnie-signing-ui:test']
    if tasks:
        if args.test:
            tasks += [':dnie-core:test', ':dnie-signing-core:test', ':dnie-signing-link:test']
        command = [gradle, '--init-script', str(init), '--project-cache-dir', str(output / 'gradle-cache'),
                   '-PdesktopBuildDir=' + str(output / 'signing-desktop')]
        if args.component == 'all':
            command.append('-PbridgeIntegrationDir=' + str(integration))
        if args.offline:
            command.append('--offline')
        jobs.append(command + tasks)
    if args.component == 'driver':
        jobs.append(['powershell.exe', '-NoProfile', '-File', str(ROOT / 'tools/windows-pcsc-driver/build.ps1'),
                     '-Configuration', config, '-OutputDirectory', str(output / 'driver')])
    plan = {'component': args.component, 'configuration': config, 'output': str(output),
            'installs_system_components': False, 'commands': jobs}
    print(json.dumps(plan, ensure_ascii=False, indent=2), flush=True)
    if args.plan:
        return
    output.mkdir(parents=True, exist_ok=True)
    # beforeProject runs before Android captures its task output locations.
    # A single-quoted Groovy literal avoids interpolation in paths containing '$'.
    gradle_path = (output / 'modules').as_posix().replace('\\', '\\\\').replace("'", "\\'")
    init.write_text("gradle.beforeProject { p -> p.layout.buildDirectory.set(new File('" + gradle_path + "', p.name)) }\n", encoding='utf-8')
    for command in jobs:
        subprocess.run(command, cwd=ROOT, check=True)
        if args.component == 'all' and command[0] == 'dotnet':
            integration.mkdir(exist_ok=True)
            built = native_artifacts / 'bin/WindowsSuiteIntegration' / config.lower()
            for file in built.iterdir():
                if file.is_file() and file.suffix in ['.exe', '.dll', '.json']:
                    shutil.copy2(file, integration / file.name)
    (output / 'build-plan.json').write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
