#!/usr/bin/env python3
import pathlib
import subprocess
import tempfile
import unittest
import re
import hashlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
class ActualApkSigningCheck(unittest.TestCase):
    def test_same_password_file_signs_without_consuming_it_twice(self):
        with tempfile.TemporaryDirectory(prefix='tingxiejian-apk-sign-') as d:
            d=pathlib.Path(d); password=d/'secret.pass'; ks=d/'release.p12'
            password.write_text('test-password-123\n'); password.chmod(0o600)
            subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(ks),'-storetype','PKCS12',
              '-storepass:file',str(password),'-keypass:file',str(password),'-alias','release','-keyalg','RSA',
              '-keysize','2048','-validity','2','-dname','CN=Signing Test'],capture_output=True,check=True)
            unsigned=d/'unsigned.apk'; signed=d/'signed.apk'
            manifest=d/'AndroidManifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example.fixture" android:versionCode="1" android:versionName="1"><application android:label="fixture" /></manifest>')
            subprocess.run(['aapt2','link','--manifest',str(manifest),'-I',str(ROOT/'vendor/android.jar'),
                            '--min-sdk-version','26','-o',str(unsigned)],capture_output=True,check=True)
            p=subprocess.run(['bash',str(ROOT/'scripts/sign-apk.sh'),str(ks),'release',str(password),str(password),
                              str(signed),str(unsigned)],capture_output=True,text=True)
            self.assertEqual(p.returncode,0,p.stderr)
            verified=subprocess.run(['apksigner','verify','--print-certs','--min-sdk-version','26',str(signed)],capture_output=True,text=True)
            self.assertEqual(verified.returncode,0,verified.stderr)
            certificate=subprocess.run(['keytool','-exportcert','-keystore',str(ks),'-alias','release',
                        '-storepass:file',str(password)],capture_output=True,check=True).stdout
            digest=hashlib.sha256(certificate).hexdigest()
            actual=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',verified.stdout)
            self.assertIsNotNone(actual); self.assertEqual(actual[1],digest)
            self.assertEqual(password.read_text(),'test-password-123\n')
if __name__=='__main__': unittest.main(verbosity=2)
