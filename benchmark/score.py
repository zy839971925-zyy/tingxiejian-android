#!/usr/bin/env python3
"""Score desensitized corpus JSONL and genuine recognizer observation JSONL."""
import argparse
import json
import pathlib
import sys
from metrics import score

def read_jsonl(path):
    rows=[]
    for number,line in enumerate(path.read_text(encoding='utf-8').splitlines(),1):
        if not line.strip():continue
        try:rows.append(json.loads(line))
        except ValueError as error:raise ValueError(f'{path}:{number}: invalid JSON') from error
    return rows

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('corpus',type=pathlib.Path)
    parser.add_argument('results',type=pathlib.Path)
    parser.add_argument('--output',type=pathlib.Path)
    args=parser.parse_args()
    try:
        corpus=read_jsonl(args.corpus);results=read_jsonl(args.results)
        for row in corpus:
            if not isinstance(row.get('audio'),str) or not row['audio'].strip():raise ValueError('corpus requires a private audio path')
        for row in results:
            for field in ['model_id','runtime','device']:
                if not isinstance(row.get(field),str) or not row[field].strip():raise ValueError('result requires actual '+field+' metadata')
        if len({row['model_id'] for row in results})>1:raise ValueError('score one model per results file; mixed models cannot share a CER')
        report=score(corpus,results)
    except (OSError,ValueError) as error:print(str(error),file=sys.stderr);return 1
    rendered=json.dumps(report,ensure_ascii=False,indent=2,allow_nan=False)+'\n'
    if args.output:args.output.write_text(rendered,encoding='utf-8')
    else:print(rendered,end='')
    return 0

if __name__=='__main__':sys.exit(main())
