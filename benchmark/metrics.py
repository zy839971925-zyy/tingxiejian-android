"""Offline corpus/result scoring. No recognizer, network, private audio, or invented timings."""
from collections import Counter
import math
import re
import statistics
import unicodedata

CATEGORIES=('quiet_mandarin','conversational','fast','far_field','noise','code_switching','technical_terms',
            'names_places','numbers_dates_money','long_sentences','accent_dialect','continuous_live')
METRICS=('first_partial_ms','finalization_ms','rtf','peak_memory_bytes','model_load_ms')
DIGITS=re.compile(r'\d+(?:[./:%-]\d+)*%?')
CHINESE_NUMBERS=re.compile(r'[零〇一二三四五六七八九十百千万亿两幺]+(?:点[零〇一二三四五六七八九]+)?')

def normalize(text):
    return ''.join(unicodedata.normalize('NFKC',text).casefold().split())

def distance(reference,hypothesis):
    """Levenshtein distance over Unicode codepoints or a token list; O(shorter side) memory."""
    if len(reference)>len(hypothesis):reference,hypothesis=hypothesis,reference
    previous=list(range(len(reference)+1))
    for i,item in enumerate(hypothesis,1):
        current=[i]
        for j,expected in enumerate(reference,1):
            current.append(min(current[-1]+1,previous[j]+1,previous[j-1]+(item!=expected)))
        previous=current
    return previous[-1]

def summary(values):
    if not values:return dict(observations=0,median=None,p95=None,max=None)
    ordered=sorted(values)
    return dict(observations=len(values),median=statistics.median(values),p95=ordered[math.ceil(.95*len(values))-1],max=max(values))

def numeric_tokens(text,include_chinese=False):
    text=normalize(text)
    matches=list(DIGITS.finditer(text))
    if include_chinese:matches+=list(CHINESE_NUMBERS.finditer(text))
    return [m.group() for m in sorted(matches,key=lambda m:m.start())]

def validate_number(value,key,positive=False):
    if type(value) not in {int,float} or not math.isfinite(value) or value<0 or (positive and value<=0):
        raise ValueError(key+' must be finite '+('positive' if positive else 'nonnegative')+' number')

def index(rows,name):
    out={}
    for row in rows:
        if not isinstance(row,dict) or not isinstance(row.get('id'),str) or not row['id']:raise ValueError(name+' requires nonempty id')
        if row['id'] in out:raise ValueError('duplicate '+name+' id: '+row['id'])
        out[row['id']]=row
    return out

def score(corpus,results):
    references=index(corpus,'corpus');hypotheses=index(results,'result')
    if not references:raise ValueError('corpus is empty; no benchmark results available')
    if references.keys()!=hypotheses.keys():raise ValueError('result IDs must match corpus exactly; missing or unknown IDs')
    scored=[]
    for identifier,row in references.items():
        result=hypotheses[identifier]
        if row.get('category') not in CATEGORIES:raise ValueError('unknown corpus category')
        if not isinstance(row.get('reference'),str) or not isinstance(result.get('text'),str):raise ValueError('reference and text must be strings')
        reference=normalize(row['reference']);hypothesis=normalize(result['text'])
        hotwords=row.get('hotwords',[]);numbers=row.get('numbers')
        if not isinstance(hotwords,list) or any(not isinstance(t,str) or not normalize(t) for t in hotwords):raise ValueError('hotwords must be nonempty strings')
        if numbers is not None and (not isinstance(numbers,list) or any(not isinstance(t,str) or not normalize(t) for t in numbers)):raise ValueError('numbers must be nonempty strings')
        expected_hotwords=Counter({normalize(term):reference.count(normalize(term)) for term in hotwords})
        hotword_errors=sum(abs(count-hypothesis.count(term)) for term,count in expected_hotwords.items() if count)
        expected_numbers=[normalize(t) for t in numbers] if numbers is not None else numeric_tokens(reference)
        predicted_numbers=numeric_tokens(hypothesis,any(CHINESE_NUMBERS.fullmatch(t) for t in expected_numbers))
        metrics={}
        for field in METRICS:
            if field in result and result[field] is not None:
                validate_number(result[field],field);metrics[field]=result[field]
        for field in ['decode_seconds','audio_duration_seconds']:
            if field in result and result[field] is not None:validate_number(result[field],field,field=='audio_duration_seconds')
        if result.get('decode_seconds') is not None and result.get('audio_duration_seconds') is not None:
            metrics['rtf']=result['decode_seconds']/result['audio_duration_seconds']
        scored.append(dict(id=identifier,category=row['category'],reference_characters=len(reference),
                           character_errors=distance(reference,hypothesis),hotword_errors=hotword_errors,
                           hotword_reference_occurrences=sum(expected_hotwords.values()),numeric_errors=distance(expected_numbers,predicted_numbers),
                           numeric_reference_tokens=len(expected_numbers),metrics=metrics))
    def aggregate(rows):
        aggregate=dict(utterances=len(rows))
        for key in ['reference_characters','character_errors','hotword_errors','hotword_reference_occurrences','numeric_errors','numeric_reference_tokens']:
            aggregate[key]=sum(row[key] for row in rows)
        aggregate['cer']=aggregate['character_errors']/aggregate['reference_characters'] if aggregate['reference_characters'] else None
        aggregate['metrics']={field:summary([row['metrics'][field] for row in rows if field in row['metrics']]) for field in METRICS}
        return aggregate
    report=aggregate(scored)
    report.update(schema_version=1,normalization='NFKC + casefold + whitespace removal; punctuation retained; strict numeric tokens without ITN',
                  categories={category:aggregate([row for row in scored if row['category']==category]) for category in CATEGORIES if any(row['category']==category for row in scored)},
                  utterance_scores=scored)
    return report
