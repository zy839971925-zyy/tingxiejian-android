#!/usr/bin/env python3
"""Static regression checks for the native source-to-page-to-source morph."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
src = root / 'src/com/example/tingxiejian'
android = '{http://schemas.android.com/apk/res/android}'
errors = []
portal = (src / 'PortalTransition.java').read_text()
for token in ('makeSceneTransitionAnimation(activity, source, name)',
              'setSharedElementEnterTransition(morph(surface, true,',
              'setSharedElementReturnTransition(morph(surface, false,',
              'finishAfterTransition()', 'Motion.animatorsEnabled()',
              'ChangeBounds()', 'ChangeClipBounds()', 'ChangeTransform()',
              'setClipToOutline(true)', 'setExitSharedElementCallback(',
              'setSharedElementReenterTransition(', 'onMapSharedElements(',
              'registerOnBackInvokedCallback('):
    if token not in portal:
        errors.append('PortalTransition: missing ' + token)
for name in ('settings', 'transcript', 'chat'):
    tree = ET.parse(root / 'res/layout' / f'activity_{name}.xml').getroot()
    if tree.tag != 'FrameLayout' or tree.get(android + 'id') != '@+id/root':
        errors.append(f'{name}: root must remain full-screen portal frame')
    ids = {element.get(android + 'id'): element for element in tree.iter()}
    for view in ('portal_surface', 'portal_content', 'back'):
        if '@+id/' + view not in ids:
            errors.append(f'{name}: missing {view}')
    java = (src / (name.title() + 'Activity.java')).read_text()
    for token in ('PortalTransition.install(this,',
                  'UiTheme.padForSystemBars(this, findViewById(R.id.portal_content))',
                  'PortalTransition.close(this)'):
        if token not in java:
            errors.append(f'{name}: missing {token}')
main = (src / 'MainActivity.java').read_text()
if 'PortalTransition.open(this, settingsButton' not in main:
    errors.append('main: settings must open from actual 48dp button')
if 'TranscriptActivity.open(this, id, source)' not in main:
    errors.append('main: history must open from tapped row')
if 'historyRowCache' not in main or 'historyList.removeAllViews()' in main:
    errors.append('main: history rows must survive onStart for reverse shared-element mapping')
if 'setReturnTransition(new Fade(Fade.OUT).addTarget(content)' not in portal or '.setDuration(85)' in portal:
    errors.append('portal: return content must not vanish before the container shrinks')
if 'activity.getIntent() != null && activity.getIntent().hasExtra(EXTRA_NAME)' not in portal:
    errors.append('portal: disabling motion inside settings must still restore an active shared element')
transcript = (src / 'TranscriptActivity.java').read_text()
if 'PortalTransition.open(this, need(R.id.ask)' not in transcript:
    errors.append('transcript: chat must open from Ask button')
chat = (src / 'ChatActivity.java').read_text()
if 'renderedMessages' not in chat or 'Motion.messageIn(bubble)' not in chat:
    errors.append('chat: only newly appended bubbles should animate')
if 'Motion.iconSwap(play,' not in main:
    errors.append('main: playback icon should confirm its state change')
if errors:
    for error in errors:
        print('FAIL:', error, file=sys.stderr)
    sys.exit(1)
print('portal invariants ok: three native, bidirectional shared-element routes with reduce-motion fallback')
