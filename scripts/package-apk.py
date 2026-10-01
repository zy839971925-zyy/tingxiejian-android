#!/usr/bin/env python3
"""Package complete available model packs; full release validates their exact provenance."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


def safe_path(base, relative):
    path = (base / relative).resolve()
    if path == base.resolve() or base.resolve() not in path.parents:
        raise ValueError('model path escapes its root')
    return path


def select_models(items, model_root):
    packs = {}
    for item in items:
        safe_path(model_root, item['local_path'])
        safe_path(pathlib.Path('/assets/model'), item['filename'])
        packs.setdefault(item['pack'], []).append(item)
    core = packs.get('core-streaming', [])
    if not core or not all(safe_path(model_root, i['local_path']).is_file() for i in core):
        raise ValueError('Missing complete core-streaming model pack; use --compile-only or --dev-no-models for validation')
    selected = []
    for pack, entries in packs.items():
        if not all(safe_path(model_root, i['local_path']).is_file() for i in entries):
            continue  # Never bundle a partial optional pack which runtime cannot initialize.
        for item in entries:
            source = safe_path(model_root, item['local_path'])
            size = source.stat().st_size
            if size == 0 or item.get('expected_bytes') is not None and size != item['expected_bytes']:
                raise ValueError('model size mismatch: ' + item['filename'])
            expected = item.get('sha256')
            if expected:
                digest = hashlib.sha256()
                with source.open('rb') as stream:
                    for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
                if digest.hexdigest().lower() != expected.lower():
                    raise ValueError('model hash mismatch: ' + item['filename'])
            selected.append(item)
    return selected


def package(mode, no_models):
    manifest_path = ROOT / 'assets/model-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    items = manifest['models']
    selected = [] if no_models else select_models(items, ROOT / 'models')
    selected_names = {i['filename'] for i in selected}
    if mode == 'release':
        command = ['python3', str(ROOT / 'scripts/validate-model-provenance.py'), '--release']
        for pack in sorted({i['pack'] for i in selected}): command += ['--pack', pack]
        subprocess.run(command, check=True, cwd=ROOT)
    # Runtime knows which optional assets are really bundled in this particular APK.
    for item in items: item['bundled'] = item['filename'] in selected_names
    with zipfile.ZipFile(ROOT/'build/unsigned.apk', 'a', allowZip64=True) as apk:
        for dex in (ROOT/'build/dex').glob('classes*.dex'):
            apk.write(dex, dex.name, compress_type=zipfile.ZIP_DEFLATED)
        for source, target in (
            ('LICENSE', 'assets/licenses/Tingxiejian-MIT.txt'),
            ('THIRD_PARTY_NOTICES.md', 'assets/THIRD_PARTY_NOTICES.md'),
            ('licenses/Apache-2.0.txt', 'assets/licenses/Apache-2.0.txt'),
            ('licenses/Shizuku-API-MIT.txt', 'assets/licenses/Shizuku-API-MIT.txt'),
            ('licenses/DISCLAIMER.txt', 'assets/licenses/DISCLAIMER.txt'),
        ): apk.write(ROOT/source, target, compress_type=zipfile.ZIP_DEFLATED)
        apk.writestr('assets/model-manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2))
        if no_models:
            apk.writestr('assets/DEV_NO_MODELS.txt', 'Development validation APK. Recognition models are absent; this is not a usable offline ASR build.\n')
        for lib in (ROOT/'build/lib/arm64-v8a').glob('*.so'):
            apk.write(lib, 'lib/arm64-v8a/' + lib.name, compress_type=zipfile.ZIP_DEFLATED)
        for item in selected:
            source = safe_path(ROOT/'models', item['local_path'])
            print('Bundling', item['filename'], round(source.stat().st_size/1048576, 1), 'MB', flush=True)
            # Android's AssetManager.openFd requires STORED model assets.
            apk.write(source, 'assets/model/' + item['filename'], compress_type=zipfile.ZIP_STORED)
    print('Bundled packs:', ', '.join(sorted({i['pack'] for i in selected})) or 'none (development validation only)')


def verify(path, no_models):
    with zipfile.ZipFile(path) as apk:
        if apk.testzip(): raise ValueError('corrupt ZIP entry')
        manifest = json.loads(apk.read('assets/model-manifest.json'))
        expected = {'assets/model/' + i['filename'] for i in manifest['models'] if i['bundled']}
        models = {i.filename for i in apk.infolist() if i.filename.startswith('assets/model/')}
        if models != expected: raise ValueError('bundled model manifest and actual assets differ')
        if no_models and (models or 'assets/DEV_NO_MODELS.txt' not in apk.namelist()):
            raise ValueError('missing development no-models marker or unexpected models')
        if not no_models and not any(i['bundled'] and i['pack']=='core-streaming' for i in manifest['models']):
            raise ValueError('missing core streaming model pack')
        if any(apk.getinfo(name).compress_type != zipfile.ZIP_STORED for name in models):
            raise ValueError('AssetFileDescriptor needs STORED model assets')
        for name in ('classes.dex', 'resources.arsc', 'res/layout/activity_main.xml',
                     'assets/THIRD_PARTY_NOTICES.md', 'assets/licenses/Tingxiejian-MIT.txt',
                     'assets/licenses/Apache-2.0.txt', 'assets/licenses/Shizuku-API-MIT.txt',
                     'assets/licenses/DISCLAIMER.txt'):
            if name not in apk.namelist(): raise ValueError('missing ' + name)
        if not any(n.startswith('lib/arm64-v8a/') for n in apk.namelist()): raise ValueError('missing native libraries')
    print('Packaging verified:', len(models), 'stored model assets, DEX, UI resources and native libraries')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mode', choices=['dev', 'release'], default='dev'); p.add_argument('--no-models', action='store_true')
    p.add_argument('--verify')
    args = p.parse_args()
    try:
        if args.mode == 'release' and args.no_models:
            raise ValueError('release packaging cannot omit models')
        if args.verify: verify(args.verify, args.no_models)
        else: package(args.mode, args.no_models)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print('APK packaging rejected: ' + str(error), file=sys.stderr); return 1
    return 0

if __name__ == '__main__': sys.exit(main())
