#!/usr/bin/env python3
"""Fast schema/provenance validation; release also audits exactly selected on-disk packs."""
import argparse
import hashlib
import json
import pathlib
import re
import sys

ROOT=pathlib.Path(__file__).resolve().parents[1]
REQUIRED={'id','filename','local_path','family','pack','bundled','source','revision','expected_bytes','sha256','license','redistribution'}

def safe_path(value):
    return isinstance(value,str) and value and not value.startswith('/') and '\\' not in value and all(
        p not in {'','.','..'} and ':' not in p for p in value.split('/'))

def validate(manifest, release=False, model_root=None, packs=None):
    errors=[]
    if not isinstance(manifest,dict) or manifest.get('schema_version')!=1 or not isinstance(manifest.get('models'),list) or not manifest['models']:
        return ['manifest must have schema_version=1 and a nonempty models array']
    models=manifest['models'];ids=set();filenames=set();known_packs=set()
    for row in models:
        if not isinstance(row,dict) or not REQUIRED.issubset(row):
            errors.append('model row is missing required fields');continue
        label=str(row['id'])
        for field in ['id','family','pack','license']:
            if not isinstance(row[field],str) or not row[field]:errors.append(label+': invalid '+field)
        if not safe_path(row['filename']) or not safe_path(row['local_path']):errors.append(label+': unsafe model path')
        if row['id'] in ids or row['filename'] in filenames:errors.append(label+': duplicate id or filename')
        ids.add(row['id']);filenames.add(row['filename']);known_packs.add(row['pack'])
        if not isinstance(row['bundled'],bool):errors.append(label+': bundled must be boolean')
        if row['redistribution'] not in {'verified','unresolved','restricted'}:errors.append(label+': invalid redistribution status')
        if row['sha256'] is not None and not (isinstance(row['sha256'],str) and re.fullmatch('[a-f0-9]{64}',row['sha256'])):errors.append(label+': invalid SHA-256')
        if row['expected_bytes'] is not None and (type(row['expected_bytes']) is not int or row['expected_bytes']<=0):errors.append(label+': invalid expected_bytes')
        if row['redistribution']=='verified':
            if row['license'] in {'unresolved','unknown'}:errors.append(label+': verified redistribution requires known model license')
            for field in ['source','revision','license_evidence','sha256','expected_bytes']:
                if not row.get(field):errors.append(label+': verified provenance requires '+field)
            for field in ['source','license_evidence']:
                if row.get(field) and not str(row[field]).startswith('https://'):errors.append(label+': provenance URLs must use https')
            if row.get('revision') in {'main','master','latest','HEAD'}:errors.append(label+': revision is mutable')
    if packs:
        for pack in packs:
            if pack not in known_packs:errors.append('unknown selected pack: '+pack)
    if errors or not release:return errors
    model_root=pathlib.Path(model_root or ROOT/'models').resolve()
    selected=[row for row in models if row['pack'] in packs] if packs else [row for row in models if row['bundled']]
    if not selected:return ['release has no selected model files']
    for row in selected:
        label=row['id']
        if row['redistribution']!='verified':errors.append(label+': redistribution unresolved/restricted; cannot publish as verified')
        for field in ['sha256','expected_bytes','source','revision','license_evidence']:
            if not row.get(field):errors.append(label+': missing release '+field)
        file=(model_root/row['local_path']).resolve()
        if not file.is_relative_to(model_root):errors.append(label+': local path leaves model root');continue
        if not file.is_file():errors.append(label+': model file unavailable');continue
        if file.stat().st_size!=row['expected_bytes']:errors.append(label+': model size differs from lock');continue
        if row['sha256']:
            digest=hashlib.sha256()
            with file.open('rb') as stream:
                for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
            if digest.hexdigest()!=row['sha256']:errors.append(label+': model SHA-256 differs from lock')
    return errors

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',type=pathlib.Path,default=ROOT/'assets/model-manifest.json')
    parser.add_argument('--model-root',type=pathlib.Path,default=ROOT/'models')
    parser.add_argument('--release',action='store_true')
    parser.add_argument('--pack',action='append',help='validate exactly this included pack; repeat for multiple packs')
    args=parser.parse_args()
    try:errors=validate(json.loads(args.manifest.read_text()),args.release,args.model_root,args.pack)
    except (OSError,ValueError,TypeError) as error:errors=[str(error)]
    if errors:
        print('\n'.join('model validation: '+error for error in errors),file=sys.stderr);return 1
    print('model manifest: '+('selected release weights/provenance verified' if args.release else 'schema and provenance consistency passed'));return 0

if __name__=='__main__':sys.exit(main())
