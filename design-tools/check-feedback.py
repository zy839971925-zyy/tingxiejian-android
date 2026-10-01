#!/usr/bin/env python3
"""Real layout and navigation regressions reported on the shipped phone APK."""
from pathlib import Path
import unittest
import subprocess
import tempfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent.parent
SRC=ROOT/'src/com/example/tingxiejian'
A='{http://schemas.android.com/apk/res/android}'
class Feedback(unittest.TestCase):
    def test_device_names_and_version_boundaries(self):
        with tempfile.TemporaryDirectory() as out:
            subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',out,
                str(SRC/'RealtimeDeviceProfile.java'),str(ROOT/'design-tools/RealtimeDeviceProfileCheck.java')],check=True)
            subprocess.run(['java','-cp',out,'com.example.tingxiejian.RealtimeDeviceProfileCheck'],check=True)
    def test_guide_keeps_configuration_inside_dialog(self):
        welcome=(SRC/'WelcomeActivity.java').read_text()
        self.assertNotIn('new Intent(this, SettingsActivity.class)',welcome)
        self.assertIn('RealtimeSetupDialog',welcome)
    def test_model_selection_and_bundled_preparation_are_discoverable(self):
        ids={n.get(A+'id'):n for n in ET.parse(ROOT/'res/layout/activity_settings.xml').getroot().iter()}
        self.assertEqual(ids['@+id/local_model_choice'].get(A+'drawableEnd'),'@drawable/ic_chevron')
        self.assertIn('@+id/qwen_status',ids)
        self.assertIn('@+id/prepare_qwen',ids)
    def test_wide_import_control_uses_rectangle_not_stretched_circle(self):
        ids={n.get(A+'id'):n for n in ET.parse(ROOT/'res/layout/activity_settings.xml').getroot().iter()}
        drawable=ids['@+id/import_qwen'].get(A+'background').replace('@drawable/','')
        tree=ET.parse(ROOT/f'res/drawable/{drawable}.xml')
        shapes=list(tree.getroot().iter('shape'))
        self.assertTrue(shapes)
        self.assertTrue(all(s.get(A+'shape','rectangle')=='rectangle' for s in shapes))
    def test_xiaomi_controls_have_a_device_specific_container(self):
        root=ET.parse(ROOT/'res/layout/activity_settings.xml').getroot()
        group=next((n for n in root.iter() if n.get(A+'id')=='@+id/xiaomi_island_section'),None)
        self.assertIsNotNone(group)
        self.assertTrue(any(n.get(A+'id')=='@+id/shizuku_authorize' for n in group.iter()))
if __name__=='__main__':unittest.main()
