package com.example.tingxiejian;
import android.content.Context;
import java.util.*;
/** User terms bias supported decoders and are protected during LLM edits. */
final class HotwordRepository {
    static List<String> load(Context c){return parse(c.getSharedPreferences(SettingsActivity.PREFS,0).getString("hotwords",""));}
    static List<String> parse(String value){
        Set<String> terms=new LinkedHashSet<>();
        for(String term:(value==null?"":value).split("[\\n,，]")){
            term=term.trim();if(!term.isEmpty()&&term.length()<=64)terms.add(term);if(terms.size()>=128)break;}
        return new ArrayList<>(terms);
    }
    static String nativeTerms(List<String> terms){return String.join("\n",terms);}
}
