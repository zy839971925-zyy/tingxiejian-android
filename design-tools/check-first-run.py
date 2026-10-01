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
    require('finishSetup(true, skip)' in welcome and 'startHome(' in welcome
            and 'putBoolean(COMPLETED, true)' in welcome,
            'skip and finish must lead to the working main screen')
    require('PAGE_COUNT = 4' in welcome and 'STATE_PAGE' in welcome
            and 'showPage(page - 1)' in welcome and 'navigateBack()' in welcome,
            'guide pages must support restoration and reverse navigation')
    require('createCircularReveal' in welcome and 'welcome_portal' in welcome,
            'welcome arrow must expand into the home handoff')
    require('onStop()' in welcome and 'view.animate().cancel()' in welcome
            and 'changing = false' in welcome,
            'interrupted page transitions must restore navigable page state')
    require('guide_title_first' in welcome and 'guide_title_second' in welcome
            and 'STATE_WELCOME_PLAYED' in welcome and 'PAGE_TITLES[page]' in welcome,
            'native line reveal must be one-shot and keep spoken heading')
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
    for key in ('guide_scroll', 'notification_permission', 'shizuku_settings',
                'terms_review', 'guide_start', 'guide_skip', 'guide_back', 'guide_progress'):
        require('@+id/'+key in ids, 'guide missing '+key)
    for step in range(4):
        require('@+id/guide_page_'+str(step) in ids, 'missing guide page '+str(step))
    require(any(node.tag == 'ScrollView' for node in root.iter()), 'guide must scroll at large font sizes')
    require(any((node.get(A+'text') or '').startswith('欢迎使用') for node in root.iter()),
            'final guide page must welcome the user')
    heading = ids.get('@+id/guide_heading')
    require(heading is not None and heading.get(A+'contentDescription'),
            'split first title must be read as one complete heading')
    require('@+id/guide_title_first' in ids and '@+id/guide_title_second' in ids,
            'first title must have independently animated native lines')
handoff_path = SRC / 'WelcomeHandoff.java'
require(handoff_path.is_file(), 'home must continue the full-screen reveal')
if handoff_path.is_file():
    handoff = handoff_path.read_text()
    require('WelcomeHandoff.play(this' in main and 'savedInstanceState' in main,
            'main activity must consume one-shot guide handoff')
    require('!Motion.animatorsEnabled()' in handoff and 'savedState != null' in handoff,
            'home handoff must skip reduced motion and recreated activities')
    require('postDelayed(() -> clean(' in handoff,
            'interrupted handoff must release its cover and restore home controls')
require('stateTransitionToken' in main and 'panel.animate().cancel()' in main
        and 'outgoing.setVisibility(View.GONE)' in main
        and 'Motion.turnOnce(need(R.id.prep_icon))' in main,
        'home state changes must settle interrupted enters/exits and stage rare icon feedback')
chat = (SRC / 'ChatActivity.java').read_text()
require('confirmClearMessages' in chat and 'setPositiveButton("清空对话"' in chat
        and 'chip.setMinHeight((int) Motion.dp(this, 48))' in chat,
        'chat quick actions need readable targets and destructive clear needs confirmation')
motion = (SRC / 'Motion.java').read_text()
application = (SRC / 'TingxiejianApp.java').read_text()
settings_layout = ET.parse(ROOT / 'res/layout/activity_settings.xml').getroot()
settings_java = settings
require('Motion.init(this)' in application and 'return !reduced &&' in motion
        and 'Motion.setReduced(this, checked)' in settings_java
        and any(node.get(A+'id') == '@+id/reduce_motion' for node in settings_layout.iter()),
        'user-controlled reduced motion must persist and affect native routes')

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

# Release-specific UI contract: accurate cloud disclosure, scalable chrome and same-version APK.
manifest_root = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
version = manifest_root.get(A+'versionName')
build = (ROOT / 'build.sh').read_text()
require(version and 'VERSION=$(python3' in build and 'versionName' in build
        and 'OUT="dist/tingxiejian-v${VERSION}-arm64-release.apk"' in build,
        'APK filename must derive from the installable manifest version')
main_layout = ET.parse(ROOT / 'res/layout/activity_main.xml').getroot()
main_ids = {node.get(A+'id'): node for node in main_layout.iter() if node.get(A+'id')}
bar = main_ids.get('@+id/appbar')
require(bar is not None and bar.get(A+'layout_height') == 'wrap_content'
        and bar.get(A+'minHeight') == '66dp', 'home header must grow with larger fonts')
require('@+id/recognition_mode' in main_ids and '@+id/recognition_summary' in main_ids
        and 'updateRecognitionCopy();' in main and 'loadPreferences(); // Apply settings' in main
        and 'Cloud.hasAsr(this)' in main,
        'home must refresh accurate recognition disclosure and settings on return')
require(main_ids.get('@+id/bottombar') is not None
        and main_ids['@+id/bottombar'].get(A+'background') == '@color/surface',
        'fixed bottom action must remain distinguishable from scrolled content')
settings_ids = {node.get(A+'id'): node for node in settings_layout.iter() if node.get(A+'id')}
for key in ('model_button', 'cloud_key', 'cloud_url', 'cloud_model', 'cloud_asr_model'):
    require(settings_ids.get('@+id/'+key) is not None
            and settings_ids['@+id/'+key].get(A+'minHeight') == '48dp',
            key+' must have a 48dp minimum target')
chat_layout = ET.parse(ROOT / 'res/layout/activity_chat.xml').getroot()
require(any(node.get(A+'id') == '@+id/chat_input' and node.get(A+'minHeight') == '48dp'
            for node in chat_layout.iter()), 'chat composer must meet minimum touch height')

if errors:
    raise SystemExit('\n'.join('FAIL: '+e for e in errors))
print('first-run / transcript action layout invariants ok')
