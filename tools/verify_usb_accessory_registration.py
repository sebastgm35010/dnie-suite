"""Cross-platform discovery contract: Windows AOA identity must resolve to Android UI."""
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
android = '{http://schemas.android.com/apk/res/android}'
identity = (root/'tools/windows-bridge/UsbAccessoryIdentity.cs').read_text(encoding='utf-8')
constants = dict(re.findall(r'const string (\w+) = "([^"]*)"', identity))
manifest = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
activity = next(a for a in manifest.findall('application/activity')
                if a.get(android+'name') == '.ui.bridge.BridgeUsbAttachActivity')
assert activity.get(android+'exported') == 'true'
action = 'android.hardware.usb.action.USB_ACCESSORY_ATTACHED'
assert any(a.get(android+'name') == action for a in activity.findall('intent-filter/action'))
metadata = next(m for m in activity.findall('meta-data') if m.get(android+'name') == action)
resource = metadata.get(android+'resource').removeprefix('@xml/')
accessories = ET.parse(root/f'app/src/main/res/xml/{resource}.xml').getroot().findall('usb-accessory')
assert any(a.get('manufacturer') == constants['Manufacturer'] and a.get('model') == constants['Model']
           and a.get('version') is None for a in accessories)
private_bridge = next(a for a in manifest.findall('application/activity')
                      if a.get(android+'name') == '.ui.bridge.BridgeActivity')
assert private_bridge.get(android+'exported') == 'false'
print('PASS AOA discovery identity, OS dispatch registration and private session UI boundary')
