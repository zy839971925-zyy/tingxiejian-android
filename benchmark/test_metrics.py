import unittest
from metrics import score

class MetricsTests(unittest.TestCase):
    def row(self,reference='听写测试',**kwargs):return dict(id='a',audio='a.wav',category='quiet_mandarin',reference=reference,**kwargs)
    def test_character_substitution_and_unicode_normalization(self):
        report=score([self.row()],[dict(id='a',text='听写测式')])
        self.assertEqual(1,report['character_errors']);self.assertEqual(.25,report['cer'])
        report=score([self.row('ＡＢ C')],[dict(id='a',text='ab c')])
        self.assertEqual(0,report['character_errors'])
    def test_hotword_repeated_occurrences_and_numeric_substitution(self):
        row=self.row('Qwen Qwen 金额12.5元日期2026-10-01',hotwords=['Qwen'])
        report=score([row],[dict(id='a',text='Qwen 金额13.5元日期2026-10-01')])
        self.assertEqual(1,report['hotword_errors']);self.assertEqual(2,report['hotword_reference_occurrences'])
        self.assertEqual(1,report['numeric_errors']);self.assertEqual(2,report['numeric_reference_tokens'])
    def test_unobserved_latency_is_null_and_first_partial_zero_is_observed(self):
        report=score([self.row()],[dict(id='a',text='听写测试',first_partial_ms=0)])
        self.assertEqual(0,report['metrics']['first_partial_ms']['median']);self.assertEqual(1,report['metrics']['first_partial_ms']['observations'])
        self.assertIsNone(report['metrics']['finalization_ms']['median']);self.assertIsNone(report['metrics']['rtf']['median'])
    def test_rtf_load_peakmemory_and_per_category(self):
        row=self.row();result=dict(id='a',text='听写测试',decode_seconds=2,audio_duration_seconds=4,
          finalization_ms=25,peak_memory_bytes=1024,model_load_ms=10)
        report=score([row],[result])
        self.assertEqual(.5,report['metrics']['rtf']['median']);self.assertEqual(1024,report['metrics']['peak_memory_bytes']['max'])
        self.assertEqual(1,report['categories']['quiet_mandarin']['utterances'])
    def test_empty_reference_insertion_not_reported_as_perfect(self):
        report=score([self.row('')],[dict(id='a',text='幻觉')])
        self.assertEqual(2,report['character_errors']);self.assertIsNone(report['cer'])
    def test_duplicate_missing_unknown_results_are_errors(self):
        row=self.row()
        for results in [[],[dict(id='wrong',text='')],[dict(id='a',text=''),dict(id='a',text='')]]:
            with self.assertRaises(ValueError):score([row],results)
    def test_invalid_numbers_or_timings_are_rejected(self):
        for result in [dict(id='a',text='x',first_partial_ms=-1),dict(id='a',text='x',model_load_ms=float('nan')),
                       dict(id='a',text='x',decode_seconds=1,audio_duration_seconds=0)]:
            with self.assertRaises(ValueError):score([self.row()], [result])
    def test_explicit_numeric_annotations_support_chinese_numbers(self):
        row=self.row('三百元',numbers=['三百'])
        report=score([row],[dict(id='a',text='四百元')])
        self.assertEqual(1,report['numeric_errors'])
    def test_invented_category_is_rejected(self):
        row=self.row();row['category']='imaginary'
        with self.assertRaises(ValueError):score([row],[dict(id='a',text='x')])

if __name__=='__main__':unittest.main()
