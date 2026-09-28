#!/usr/bin/env python3
"""Release regression: onboarding stays optional and transcript actions stay breathable."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'src/com/example/tingxiejian'
A = '{http://schemas.android.com/apk/res/android}'
errors = []
def require(ok, message):
    if not ok: errors.append(message)

manifest = (ROOT / 'AndroidManifest.xml').read_text()
main = (SRC / 'MainActivity.java').read_text()
settings = (SRC / 'SettingsActivity.java').read_text()
welcome_path = SRC / 'WelcomeActivity.java'
require('android:name=".WelcomeActivity"' in manifest, 'register native guide activity')
require('WelcomeActivity.shouldShow(this)' in main, 'show guide to new installations')
require(welcome_path.is_file(), 'guide implementation missing')
if welcome_path.is_file():
    welcome = welcome_path.read_text()
    require('lastUpdateTime' in welcome and 'firstInstallTime' in welcome,
            'do not interrupt established users upgrading from v0.17')
    require('POST_NOTIFICATIONS' in welcome and 'requestPermissions(' in welcome,
            'notification permission needs an explicit user action')
    require('finishSetup()' in welcome and 'completed' in welcome,
            'skip and finish must lead to the working main screen')
    require('UiTheme.padForSystemBars(' in welcome and 'onResume()' in welcome,
            'safe-area padding and refreshed permission state')
    require('RECORD_AUDIO' not in welcome and 'READ_EXTERNAL_STORAGE' not in welcome,
            'do not ask for microphone/storage privileges not needed by file picker')
require('WelcomeActivity.openGuide(this)' in settings, 'guide must be reachable from Settings')
layout = ROOT / 'res/layout/activity_welcome.xml'
require(layout.is_file(), 'guide layout missing')
if layout.is_file():
    root = ET.parse(layout).getroot()
    ids = {node.get(A+'id'): node for node in root.iter() if node.get(A+'id')}
    for key in ('guide_scroll', 'notification_permission', 'shizuku_settings', 'guide_start', 'guide_skip'):
        require('@+id/'+key in ids, 'guide missing '+key)
    require(any(node.tag == 'ScrollView' for node in root.iter()), 'guide must scroll at large font sizes')

transcript = ET.parse(ROOT / 'res/layout/activity_transcript.xml').getroot()
ids = {node.get(A+'id'): node for node in transcript.iter() if node.get(A+'id')}
for key in ('ask','copy','share','save'):
    node = ids.get('@+id/'+key)
    require(node is not None and node.tag == 'LinearLayout', key+' must center icon+label as one group')
    if node is not None:
        require(node.get(A+'minHeight') in ('56dp','60dp','64dp'),key+' needs a generous target')
        require(node.get(A+'paddingLeft') in ('12dp','14dp','16dp'),key+' needs horizontal breathing room')
        require(any(child.tag == 'ImageView' for child in node),key+' needs an explicit icon')
require(ids.get('@+id/segments') is not None and ids['@+id/segments'].get(A+'layout_marginTop') == '12dp',
        'action block needs separation from transcript list')

if errors:
    raise SystemExit('\n'.join('FAIL: '+e for e in errors))
print('first-run / transcript action layout invariants ok')
