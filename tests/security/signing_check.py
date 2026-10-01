#!/usr/bin/env python3
"""Exercise the actual keytool identity, external-input and certificate policy."""
import pathlib
import subprocess
import tempfile
import unittest
import os

ROOT = pathlib.Path(__file__).resolve().parents[2]
POLICY = ROOT / 'scripts/signing-policy.py'

class SigningPolicyCheck(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='tingxiejian-signing-')
        cls.base = pathlib.Path(cls.temp.name)
        cls.password = cls.base / 'store.pass'
        cls.password.write_text('test-password-123\n'); cls.password.chmod(0o600)
        cls.ks = cls.base / 'release.p12'
        subprocess.run(['keytool', '-genkeypair', '-noprompt', '-keystore', str(cls.ks),
                        '-storetype', 'PKCS12', '-storepass:file', str(cls.password),
                        '-keypass:file', str(cls.password), '-alias', 'release',
                        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                        '-dname', 'CN=Policy Test'], check=True, capture_output=True)
        cls.ks.chmod(0o600)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def run_policy(self, *extra, expected=1, omitted=False):
        args = ['python3', str(POLICY), '--project-root', str(ROOT)]
        if not omitted:
            args += ['--keystore', str(self.ks), '--store-password-file', str(self.password),
                     '--key-password-file', str(self.password), '--alias', 'release']
        p = subprocess.run(args + list(extra), capture_output=True, text=True)
        self.assertEqual(p.returncode, expected, p.stdout + p.stderr)
        self.assertNotIn('test-password-123', p.stdout + p.stderr)
        return p
    def test_missing_inputs_fail_closed(self): self.run_policy(omitted=True)
    def test_valid_external_identity(self):
        p = self.run_policy(expected=0)
        self.assertRegex(p.stdout.strip(), r'^[0-9a-f]{64}$')
    def test_wrong_alias_rejected(self): self.run_policy('--alias', 'missing')
    def test_password_permission_rejected(self):
        weak = self.base / 'weak.pass'; weak.write_text('test-password-123\n'); weak.chmod(0o644)
        self.run_policy('--store-password-file', str(weak))
    def test_empty_password_rejected(self):
        empty = self.base / 'empty.pass'; empty.write_text('\n'); empty.chmod(0o600)
        self.run_policy('--store-password-file', str(empty))
    def test_bad_password_rejected(self):
        wrong = self.base / 'wrong.pass'; wrong.write_text('wrong-password\n'); wrong.chmod(0o600)
        self.run_policy('--store-password-file', str(wrong))
    def test_repo_identity_rejected(self):
        self.run_policy('--project-root', str(self.base))
    def test_symlink_cannot_escape_external_rule(self):
        link = self.base / 'link.p12'
        if not link.exists(): link.symlink_to(self.ks)
        self.run_policy('--project-root', str(self.base), '--keystore', str(link))
    def test_pin_mismatch_rejected(self): self.run_policy('--cert-sha256', '0' * 64)
    def test_pin_accepts_colons_and_case(self):
        pin = self.run_policy(expected=0).stdout.strip()
        formatted = ':'.join(pin[i:i+2] for i in range(0, len(pin), 2)).upper()
        self.run_policy('--cert-sha256', formatted, expected=0)
    def test_malformed_pin_rejected(self): self.run_policy('--cert-sha256', 'not-a-hash')

if __name__ == '__main__': unittest.main(verbosity=2)
