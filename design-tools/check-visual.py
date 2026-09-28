#!/usr/bin/env python3
"""Regression guard: shape XML must use real <corners>; dark buttons remain legible."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re

root = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
errors = []
for path in (root / 'res/drawable').glob('bg_*.xml'):
    tree = ET.parse(path).getroot()
    if re.search(r'android:corners\s+android:radius', ''.join(tree.itertext())):
        errors.append(f'{path.name}: invalid text instead of <corners>')
    if tree.tag == 'shape' and tree.attrib.get(a+'shape') == 'rectangle':
        if tree.find('corners') is None:
            errors.append(f'{path.name}: rectangular surface has no corners')

for folder in ('values', 'values-night'):
    colors = ET.parse(root / 'res' / folder / 'colors.xml').getroot()
    palette = {x.attrib['name']: x.text for x in colors.findall('color')}
    def lum(hexcode):
        rgb = [int(hexcode[i:i+2], 16)/255 for i in (1,3,5)]
        rgb = [x/12.92 if x <= .04045 else ((x+.055)/1.055)**2.4 for x in rgb]
        return sum(x*y for x,y in zip(rgb,(.2126,.7152,.0722)))
    foreground, background = lum(palette['on_accent']), lum(palette['primary_fill'])
    contrast = (max(foreground, background)+.05)/(min(foreground, background)+.05)
    if contrast < 4.5:
        errors.append(f'{folder}: on_accent/primary_fill contrast {contrast:.2f} < 4.5')
    for surface in ('bg', 'surface', 'sunken'):
        muted, panel = lum(palette['muted']), lum(palette[surface])
        ratio = (max(muted, panel) + .05) / (min(muted, panel) + .05)
        if ratio < 4.5:
            errors.append(f'{folder}: small muted text/{surface} contrast {ratio:.2f} < 4.5')

for path in (root / 'res/layout').glob('*.xml'):
    txt = path.read_text()
    if '@color/white' in txt:
        errors.append(f'{path.name}: primary-control text uses theme-dependent white')
    for view in ET.parse(path).getroot().iter():
        if view.get('{http://schemas.android.com/apk/res/android}drawableStart') and not view.get(
                '{http://schemas.android.com/apk/res/android}drawableTint'):
            errors.append(f'{path.name}: dynamic button icon missing theme tint')

night_styles = ET.parse(root / 'res/values-night/styles.xml').getroot()
night_theme = next((x for x in night_styles.findall('style') if x.get('name') == 'AppTheme'), None)
if night_theme is None or night_theme.get('parent') != 'android:style/Theme.Material.NoActionBar':
    errors.append('night styles: must use real dark system theme, not just token overrides')
ring = (root / 'src/com/example/tingxiejian/RingView.java').read_text()
if 'canvas.drawText(' in ring or 'Color.parseColor(' in ring:
    errors.append('RingView: duplicate percentage label or hard-coded light palette')
adapter = (root / 'src/com/example/tingxiejian/SegmentAdapter.java').read_text()
if 'Color.parseColor(' in adapter:
    errors.append('SegmentAdapter: hard-coded transcript text invisible in dark theme')
job = (root / 'src/com/example/tingxiejian/Job.java').read_text()
if 'indeterminate' not in job or '正在分人' not in job or '无法估算' not in job:
    errors.append('Job: speaker separation must disclose unmeasurable stage progress')
service = (root / 'src/com/example/tingxiejian/LocalService.java').read_text()
if 'islandWanted && XiaomiIslandPublisher.isSupported(this)' not in service:
    errors.append('LocalService: island switch/capability must gate island publication')
manifest = (root / 'AndroidManifest.xml').read_text()
if '<package android:name="com.xiaomi.xmsf"/>' not in manifest:
    errors.append('manifest: Android package visibility blocks XMSF UID probe')
if errors:
    raise SystemExit('FAIL: ' + '; '.join(errors))
print('visual invariants ok: shape corners and small-text/primary contrast in both themes')
