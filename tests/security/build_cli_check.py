#!/usr/bin/env python3
"""Fail-closed CLI outcomes; no SDK/weights needed for these policy cases."""
import os
import pathlib
import subprocess
import unittest
import tempfile
import shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
class BuildCliCheck(unittest.TestCase):
    def run_build(self, args):
        env={k:v for k,v in os.environ.items() if not k.startswith('RELEASE_')}
        return subprocess.run(['bash', 'build.sh']+args, cwd=ROOT, env=env, capture_output=True, text=True, timeout=10)
    def rejected(self, args, message):
        p=self.run_build(args)
        self.assertNotEqual(p.returncode,0)
        self.assertIn(message,p.stderr)
        self.assertNotIn('Pinned Maven',p.stdout)
    def test_release_no_identity(self): self.rejected(['--release'],'requires explicit release keystore')
    def test_release_compile_only(self): self.rejected(['--release','--compile-only'],'Release cannot use')
    def test_release_no_models(self): self.rejected(['--release','--dev-no-models'],'Release cannot use')
    def test_conflicting_dev_modes(self): self.rejected(['--compile-only','--dev-no-models'],'Choose --compile-only')
    def test_missing_argument(self): self.rejected(['--release-keystore'],'Missing value')
    def test_unknown_option(self): self.rejected(['--releaze'],'Unknown build option')
    def test_concurrent_build_cannot_delete_outputs(self):
        with tempfile.TemporaryDirectory() as d:
            d=pathlib.Path(d); shutil.copy(ROOT/'build.sh',d/'build.sh')
            lock=d/'build/.build-lock'; lock.mkdir(parents=True)
            (lock/'pid').write_text('fixture')
            sentinel=d/'build/aligned.apk'; sentinel.write_bytes(b'owned by another build')
            p=subprocess.run(['bash',str(d/'build.sh'),'--compile-only'],capture_output=True,text=True)
            self.assertNotEqual(p.returncode,0); self.assertIn('Another build holds',p.stderr)
            self.assertEqual(sentinel.read_bytes(),b'owned by another build')
            self.assertTrue(lock.is_dir())
    def test_help_describes_identity(self):
        p=self.run_build(['--help']); self.assertEqual(p.returncode,0)
        self.assertIn('outside the checkout',p.stdout); self.assertIn('debuggable development',p.stdout)
if __name__=='__main__': unittest.main(verbosity=2)
