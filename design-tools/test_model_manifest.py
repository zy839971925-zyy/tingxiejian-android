import hashlib
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('manifest_validator',ROOT/'scripts/validate-model-provenance.py')
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)

class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.temp.name)
        (self.root/'model.onnx').write_bytes(b'weights')
        self.row=dict(id='model',filename='model.onnx',local_path='model.onnx',pack='test-pack',family='test',bundled=True,
                      source='https://example.org/model.onnx',revision='a'*40,sha256=hashlib.sha256(b'weights').hexdigest(),
                      expected_bytes=7,license='Apache-2.0',license_evidence='https://example.org/LICENSE',redistribution='verified')
        self.manifest=dict(schema_version=1,models=[self.row])
    def tearDown(self):self.temp.cleanup()
    def test_release_rejects_unresolved_redistribution_even_with_valid_weight(self):
        self.row['redistribution']='unresolved'
        self.assertTrue(validator.validate(self.manifest,True,self.root))
    def test_verified_release_checks_bytes_and_hash(self):
        self.assertEqual([],validator.validate(self.manifest,True,self.root))
        (self.root/'model.onnx').write_bytes(b'corrupt')
        self.assertTrue(validator.validate(self.manifest,True,self.root))
    def test_release_rejects_missing_hash_source_revision_or_license(self):
        for field in ['sha256','source','revision','license_evidence']:
            old=self.row[field];self.row[field]=None
            self.assertTrue(validator.validate(self.manifest,True,self.root),field)
            self.row[field]=old
    def test_schema_allows_honest_unresolved_absent_model(self):
        self.row.update(sha256=None,expected_bytes=None,source=None,revision=None,license='unresolved',redistribution='unresolved')
        self.assertEqual([],validator.validate(self.manifest))
        self.assertTrue(validator.validate(self.manifest,True,self.root))
    def test_path_traversal_is_rejected_before_access(self):
        for field in ['filename','local_path']:
            old=self.row[field];self.row[field]='../outside'
            self.assertTrue(validator.validate(self.manifest),field)
            self.row[field]=old
    def test_unknown_pack_is_rejected(self):
        self.assertTrue(validator.validate(self.manifest,True,self.root,['does-not-exist']))
    def test_selected_pack_excludes_absent_optional_pack(self):
        other=dict(self.row,id='optional',filename='optional.onnx',local_path='optional.onnx',pack='optional',redistribution='unresolved')
        self.manifest['models'].append(other)
        self.assertEqual([],validator.validate(self.manifest,True,self.root,['test-pack']))
        self.assertTrue(validator.validate(self.manifest,True,self.root))
    def test_duplicate_filenames_rejected(self):
        self.manifest['models'].append(dict(self.row,id='other'))
        self.assertTrue(validator.validate(self.manifest))
    def test_false_license_does_not_make_verified_pack(self):
        self.row['license']='unresolved'
        self.assertTrue(validator.validate(self.manifest))

if __name__=='__main__':unittest.main()
