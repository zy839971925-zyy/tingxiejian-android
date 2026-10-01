#!/usr/bin/env python3
import importlib.util
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('package_apk', ROOT / 'scripts/package-apk.py')
policy = importlib.util.module_from_spec(spec); spec.loader.exec_module(policy)

class PackSelectionCheck(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.root = pathlib.Path(self.temp.name)
        self.items = [{'pack':'core-streaming','filename':'stream.onnx','local_path':'stream.onnx'},
                      {'pack':'optional','filename':'optional-a.onnx','local_path':'optional-a.onnx'},
                      {'pack':'optional','filename':'optional-b.onnx','local_path':'optional-b.onnx'}]
    def tearDown(self): self.temp.cleanup()
    def test_missing_core_fails(self):
        with self.assertRaisesRegex(ValueError, 'core-streaming'): policy.select_models(self.items, self.root)
    def test_partial_optional_pack_is_skipped(self):
        (self.root/'stream.onnx').write_bytes(b'stream'); (self.root/'optional-a.onnx').write_bytes(b'a')
        selected = policy.select_models(self.items, self.root)
        self.assertEqual([x['filename'] for x in selected], ['stream.onnx'])
    def test_complete_optional_pack_selected(self):
        for item in self.items: (self.root/item['local_path']).write_bytes(b'model')
        self.assertEqual(len(policy.select_models(self.items, self.root)), 3)
    def test_known_hash_mismatch_rejected(self):
        (self.root/'stream.onnx').write_bytes(b'stream'); self.items[0]['sha256'] = '0'*64
        with self.assertRaisesRegex(ValueError, 'hash'): policy.select_models(self.items, self.root)
    def test_known_size_mismatch_rejected(self):
        (self.root/'stream.onnx').write_bytes(b'stream'); self.items[0]['expected_bytes'] = 100
        with self.assertRaisesRegex(ValueError, 'size'): policy.select_models(self.items, self.root)
    def test_model_path_cannot_escape(self):
        (self.root/'stream.onnx').write_bytes(b'stream'); self.items[0]['local_path']='../escape'
        with self.assertRaisesRegex(ValueError, 'path'): policy.select_models(self.items, self.root)
if __name__ == '__main__': unittest.main(verbosity=2)
