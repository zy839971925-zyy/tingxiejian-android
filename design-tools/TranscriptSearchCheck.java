package com.example.tingxiejian;
import java.util.regex.Pattern;
public final class TranscriptSearchCheck {
 public static void main(String[] args) {
  String[][] cases={{"我们在上海开会","上海","true"},{"Qwen ASR is ready"," asr ","true"},{"费用 25.00 元","25.00","true"},{"费用 25x00 元","25.00","false"},{"标点 [a+b] 保留","[a+b]","true"},{"忠实稿内容","","true"},{"忠实稿内容","不存在","false"},{"😀🙂","😀","true"}};
  for(String[] c:cases){Pattern p=TranscriptSearch.pattern(c[1]);if(TranscriptSearch.matches(c[0],p)!=Boolean.parseBoolean(c[2]))throw new AssertionError(c[1]);}
  if(TranscriptSearch.pattern(" ")!=null)throw new AssertionError("blank query");
  if(TranscriptSearch.matches(null,TranscriptSearch.pattern("字")))throw new AssertionError("null text");
  System.out.println("PASS: current-layer search (literal punctuation, Unicode, case, whitespace, empty and no match)");
 }
}
