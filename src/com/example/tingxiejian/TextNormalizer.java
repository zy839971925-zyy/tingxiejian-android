package com.example.tingxiejian;

/** Deterministic conservative ITN: width/spacing only; never infer a number/date from speech. */
final class TextNormalizer {
    static String normalize(String text) {
        if(text==null)return "";
        StringBuilder out=new StringBuilder();boolean space=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c>='\uff01'&&c<='\uff5e'&&c!='，'&&c!='！'&&c!='？'&&c!='：'&&c!='；')c-=0xfee0;
            if(Character.isWhitespace(c)||c=='\u3000') {space=out.length()>0;continue;}
            if(space)out.append(' ');space=false;out.append(c);
        }
        return out.toString().trim();
    }
}
