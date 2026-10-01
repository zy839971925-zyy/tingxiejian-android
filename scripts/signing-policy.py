#!/usr/bin/env python3
"""Fail-closed release identity validation; stdout is the public certificate SHA-256 only."""
import argparse
import hashlib
import pathlib
import re
import stat
import subprocess
import sys


def private_external_file(value, label, root):
    if not value:
        raise ValueError('release requires explicit ' + label)
    path = pathlib.Path(value).expanduser().resolve(strict=True)
    if path == root or root in path.parents:
        raise ValueError(label + ' must be outside the checkout (including build/)')
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(label + ' must be a nonempty regular file')
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise ValueError(label + ' must be private (chmod 600 or 400)')
    return path


def password_file(value, label, root):
    path = private_external_file(value, label, root)
    # File-based keytool/apksigner passwords use one line; avoid accidental empty input.
    content = path.read_bytes()
    if len(content) > 4096 or not content.rstrip(b'\r\n') or b'\n' in content.rstrip(b'\r\n'):
        raise ValueError(label + ' must contain one nonempty password line')
    return path


def validate(args):
    root = pathlib.Path(args.project_root).resolve()
    ks = private_external_file(args.keystore, 'release keystore', root)
    store = password_file(args.store_password_file, 'store password file', root)
    password_file(args.key_password_file, 'key password file', root)
    if not args.alias:
        raise ValueError('release requires an explicit key alias')
    pin = (args.cert_sha256 or '').replace(':', '').strip().lower()
    if pin and not re.fullmatch('[0-9a-f]{64}', pin):
        raise ValueError('certificate pin must be a SHA-256 hex fingerprint')
    cert = subprocess.run(['keytool', '-exportcert', '-keystore', str(ks),
                           '-storepass:file', str(store), '-alias', args.alias], capture_output=True)
    if cert.returncode or not cert.stdout:
        # keytool diagnostics may include filenames/alias; do not relay secret-dependent output.
        raise ValueError('cannot read release certificate: check keystore, alias and password file')
    fingerprint = hashlib.sha256(cert.stdout).hexdigest()
    if pin and fingerprint != pin:
        raise ValueError('release certificate SHA-256 does not match the required pin')
    return fingerprint


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--project-root', default=str(pathlib.Path(__file__).resolve().parents[1]))
    p.add_argument('--keystore'); p.add_argument('--store-password-file')
    p.add_argument('--key-password-file'); p.add_argument('--alias'); p.add_argument('--cert-sha256')
    args = p.parse_args()
    try:
        print(validate(args))
    except (ValueError, OSError) as error:
        print('release signing rejected: ' + str(error), file=sys.stderr)
        return 1
    return 0

if __name__ == '__main__': sys.exit(main())
