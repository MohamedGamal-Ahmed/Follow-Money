"""Check the actual release bundle for accidental debug/test packaging (stdlib only)."""
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml').getroot()
assert manifest.get('package') == 'com.mohamedgamal.masareef', 'Wrong production package'
assert manifest.find('uses-sdk').get(ns + 'targetSdkVersion') == '36', 'Wrong target API'
assert manifest.find('application').get(ns + 'debuggable', 'false') == 'false', 'Debuggable release'
assert not manifest.findall('instrumentation'), 'Instrumentation in production manifest'
permissions = {p.get(ns + 'name') for p in manifest.findall('uses-permission')}
assert {'android.permission.READ_SMS', 'android.permission.RECEIVE_SMS'} <= permissions, 'Missing core SMS permission'
assert not {'android.permission.INTERNET', 'android.permission.SEND_SMS'} & permissions, 'Unexpected Internet/SMS-send permission'
assert manifest.find('application').get(ns + 'allowBackup') == 'false', 'Backup unexpectedly enabled'

with ZipFile(root / 'app/build/outputs/bundle/release/app-release.aab') as bundle:
    assert bundle.read('base/assets/privacy-policy.txt') == (root / 'app/src/main/assets/privacy-policy.txt').read_bytes(), 'Bundled policy differs from source'
    dex = b''.join(bundle.read(name) for name in bundle.namelist() if name.startswith('base/dex/') and name.endswith('.dex'))
    assert dex, 'No application Dex'
    for forbidden in ('rescan_audit', 'ReconciliationTest', 'RescanAudit', 'تقرير تشخيص الأرقام', 'عينات رسائل البنوك'):
        assert forbidden.encode('utf-8') not in dex, 'Development audit content in release: ' + forbidden
print('Release package/API/permissions, exact privacy asset and absence of audit Dex content verified. Verify signing separately with build-release.ps1.')
