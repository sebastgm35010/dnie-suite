"""Build native desktop/service packages; outputs stay outside the repository."""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess

root = pathlib.Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--configuration', choices=['Debug', 'Release'], default='Release')
parser.add_argument('--output', type=pathlib.Path, required=True)
parser.add_argument('--artifacts', type=pathlib.Path, default=pathlib.Path('D:/DNIeToolchain/BuildArtifacts/native-desktop'))
parser.add_argument('--packages', type=pathlib.Path, help='Optional NuGet cache; overrides the machine-specific config path')
args = parser.parse_args()
artifacts = args.artifacts.resolve()
config = args.configuration
for project in ['windows-agent/WindowsAgent.csproj', 'windows-manager/WindowsManager.csproj']:
    command = ['dotnet', 'build', str(root/'tools'/project), '-c', config,
                    '--configfile', str(root/'tools/windows-pairing/NuGet.Config'),
                    '-p:ArtifactsPath='+str(artifacts), '--verbosity', 'quiet']
    if args.packages:
        command.append('-p:RestorePackagesPath='+str(args.packages.resolve()))
    subprocess.run(command, check=True, cwd=root)
destination = args.output.resolve()
if destination.exists():
    raise SystemExit('Use a new output directory; existing packages are preserved.')
destination.mkdir(parents=True)
for project in ['WindowsManager', 'WindowsAgent']:
    source = artifacts/'bin'/project/config.lower()
    for file in source.iterdir():
        if file.is_file() and file.suffix in ['.exe', '.dll', '.json']:
            target = destination/file.name
            if target.exists() and target.read_bytes() != file.read_bytes():
                raise SystemExit('Dependency mismatch: '+file.name)
            shutil.copy2(file, target)
selector = destination/'selector'
selector.mkdir()
for name in ['selector_service.py', 'selector_engine.py', 'pcsc-bridge-selector.js']:
    shutil.copy2(root/'tools/windows-reader-selector'/name, selector/name)
license_text = root/'tools/windows-manager/LICENSE-QRCoder.txt'
if not license_text.exists():
    candidates = list(license_text.parent.glob('*LICENSE*')) + list(license_text.parent.glob('*license*'))
    if not candidates:
        raise SystemExit('Missing QRCoder license; package not distributed.')
    license_text = candidates[0]
shutil.copy2(license_text, destination/'LICENSE-QRCoder.txt')
files = [{'path': file.relative_to(destination).as_posix(),
          'sha256': hashlib.sha256(file.read_bytes()).hexdigest().upper()}
         for file in sorted(destination.rglob('*')) if file.is_file()]
(destination/'distribution.json').write_text(json.dumps({'configuration': config, 'files': files}, indent=2), encoding='utf-8')
shutil.make_archive(str(destination), 'zip', root_dir=destination)
print(json.dumps({'package': str(destination), 'zip': str(destination)+'.zip', 'files': len(files)}))
