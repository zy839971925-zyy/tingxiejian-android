#!/usr/bin/env python
"""Check a manually supplied, CURRENT on-device layout capture against safe-area invariants.

For privacy the app keeps measurements private. On the tested device open Settings → View local
diagnostics and copy the layout JSON into a local file, then pass that path explicitly. No stale
Downloads file is auto-selected: it could come from an entirely different build or UI state.
"""
import json
import pathlib
import sys

if len(sys.argv) != 2:
    sys.exit('usage: python design-tools/check-layout.py path/to/current-layout.json '
             '(copy from Settings → 本机诊断; never use a stale Downloads file)')
path = pathlib.Path(sys.argv[1])
if not path.is_file():
    sys.exit('layout capture not found: %s' % path)

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
