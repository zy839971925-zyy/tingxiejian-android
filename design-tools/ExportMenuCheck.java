package com.example.tingxiejian;
import org.json.*;
public final class ExportMenuCheck {
 public static void main(String[] args)throws Exception {
  JSONObject record=new JSONObject().put("name","会议").put("segments",new JSONArray().put(new JSONObject().put("start",1.2).put("end",3.4).put("text","你好")));
  String[] ext={".txt",".srt",".json"},mime={"text/plain","text/plain","application/json"};
  for(int i=0;i<3;i++){
   Exporter.Format format=Exporter.menuFormat(i);
   if(!format.suffix.equals(ext[i])||!format.mime.equals(mime[i]))throw new AssertionError("menu "+i);
   if(Exporter.fromKind(format.kind)!=format)throw new AssertionError("persisted selection");
   String text=format.render(record);
   if(i==1&&!text.contains("00:00:01,200 --> 00:00:03,400"))throw new AssertionError("SRT payload");
   if(i==2&&!new JSONObject(text).getJSONArray("segments").getJSONObject(0).getString("text").equals("你好"))throw new AssertionError("JSON payload");
   if(i==0&&!text.contains("[00:01] 你好"))throw new AssertionError("TXT payload");
  }
  try{Exporter.fromKind(0);throw new AssertionError("invalid saved format");}catch(IllegalArgumentException expected){}
  try{Exporter.menuFormat(3);throw new AssertionError("invalid menu choice");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: export menu, persisted choices, MIME, extensions and actual TXT/SRT/JSON payloads");
 }
}
