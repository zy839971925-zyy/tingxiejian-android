#!/usr/bin/env python
"""Reads the layout measurement the app writes on its first frame and asserts the safe-area invariants.

The app writes Downloads/tingxiejian-layout.json (MediaStore, no permission needed), so this runs
from Termux against the real device instead of a guess about what the screen looks like.
"""
import json
import pathlib
import sys

PATHS = [
    pathlib.Path('/sdcard/Download/tingxiejian-layout.json'),
    pathlib.Path('/storage/emulated/0/Download/tingxiejian-layout.json'),
    pathlib.Path.home() / 'tingxiejian-layout.json',
]

path = next((p for p in PATHS if p.is_file()), None)
if path is None:
    sys.exit('layout dump not found; open the app once, then re-run (looked in %s)'
             % ', '.join(str(p) for p in PATHS))

data = json.loads(path.read_text())
failures = []


def require(condition, message):
    if not condition:
        failures.append(message)


require(data['overlapPx'] <= 0, 'app bar overlaps the status bar by %dpx' % data['overlapPx'])
require(data['statusBarCoversTitle'] is False, 'status bar covers the title')
require(data['appBarClearsList'] is True, 'app bar overlaps the scrolling list')
require(data['listClearsBottombar'] is True, 'list overlaps the bottom bar')
require(data['appBarTop'] >= data['statusBarInset'],
        'app bar top (%d) is above the status bar inset (%d)' % (data['appBarTop'], data['statusBarInset']))
require(data['columnTop'] == data['statusBarInset'],
        'content column starts at %d instead of the status bar inset %d'
        % (data['columnTop'], data['statusBarInset']))
require(data['bottombarTop'] > data['appBarBottom'], 'bottom bar is above the app bar')
require(data['listBottom'] > data['listTop'], 'list has no height')

print('device      %s · SDK %s · %dx%d @%.2fx font %.2f'
      % (data['device'], data['sdk'], data['screenWidth'], data['screenHeight'],
         data['density'], data['fontScale']))
print('insets      status bar %dpx, content column top %dpx' % (data['statusBarInset'], data['columnTop']))
print('vertical    app bar %d..%d, list %d..%d, bottom bar from %d'
      % (data['appBarTop'], data['appBarBottom'], data['listTop'], data['listBottom'], data['bottombarTop']))
print('overlap     %dpx (title top %dpx)' % (data['overlapPx'], data['titleTop']))

if failures:
    for failure in failures:
        print('FAIL: ' + failure)
    sys.exit(1)
print('PASS: safe areas (app bar below the status bar, list between the bars, nothing overlapping)')
