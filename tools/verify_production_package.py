"""Verify production exclusion in compiled artifacts, not just UI visibility."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile

def verify(apk: Path, classes: Path, aapt: Path, windows: Path):
    with zipfile.ZipFile(classes) as archive:
        names = archive.namelist()
    forbidden = ('/ui/console/', '/ui/diagnostics/', '/data/diagnostics/', 'BridgeApduTrace', 'ConsoleViewModel')
    assert not any(fragment in name for fragment in forbidden for name in names), 'Debug implementation compiled into Release classes'
    manifest = subprocess.run([str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], check=True,
                              capture_output=True, text=True, encoding='utf-8').stdout
    assert 'DiagnosticsActivity' not in manifest, 'Debug activity in production manifest'
    debug_line = next((line for line in manifest.splitlines() if 'android:debuggable' in line), '')
    assert not debug_line or '0x0' in debug_line and '0xffffffff' not in debug_line, 'Production APK debuggable'
    files = [p.relative_to(windows).as_posix() for p in windows.rglob('*') if p.is_file()]
    assert not any(part in file for file in files for part in ('/debug/', 'host-diagnostics/', 'BridgeDebug', '.pdb', 'pair-phone.ps1')), 'Debug module distributed in production package'
    gui = (windows / 'tools/windows-desktop/BridgeDesktop.ps1').read_text(encoding='utf-8-sig')
    assert 'BridgeDebugMenu' not in gui and '[switch]$SelfTest' not in gui and '[switch]$LayoutTest' not in gui, 'Production GUI has a debug entry'
    host = windows / 'runtime/WindowsBridge.dll'
    for command in ['doctor', 'pcsc-relay', 'serve-local', 'serve']:
        result = subprocess.run(['dotnet', str(host), command], capture_output=True, timeout=15)
        assert result.returncode == 2, f'Debug command {command} exposed in production host'
    return {'passed': True, 'androidReleaseDebuggable': False, 'debugImplementationsCompiled': False,
            'windowsDebugCommandsRejected': 4, 'windowsDebugModulesDistributed': False}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ['apk', 'classes', 'aapt', 'windows']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.apk, args.classes, args.aapt, args.windows), indent=2))
