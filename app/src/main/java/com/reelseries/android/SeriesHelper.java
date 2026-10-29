package com.reelseries.android;
import org.json.JSONObject;
import java.util.*;
import java.util.regex.*;
/** Groups episode records while leaving films untouched. */
public final class SeriesHelper {
 private SeriesHelper(){}
 private static final Pattern SE=Pattern.compile("(?i)(?:epis[oó]dio\\s*)?(?:s|t)?(\\d{1,3})\\s*[xXeE]\\s*(\\d{1,4})");
 private static final Pattern TEMP=Pattern.compile("(?i)(?:temporada|temp\\.?|season|s)\\s*0*(\\d{1,3})");
 private static final Pattern EP=Pattern.compile("(?i)(?:epis[oó]dio|ep\\.?|episode)\\s*0*(\\d{1,4})");
 public static int[] numbers(JSONObject o){String s=o.optString("episodio","")+" "+o.optString("nome","");if(o.optInt("temporada",0)>0 && o.optInt("episodio",0)>0)return new int[]{o.optInt("temporada"),o.optInt("episodio")};Matcher m=SE.matcher(s);if(m.find())return new int[]{num(m.group(1)),num(m.group(2))};Matcher t=TEMP.matcher(s),e=EP.matcher(s);if(t.find()&&e.find())return new int[]{num(t.group(1)),num(e.group(1))};return null;}
 private static int num(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
 public static String name(JSONObject o){String s=o.optString("serie",o.optString("nome","")).trim();
  s=s.replaceAll("(?i)\\s*[-–:]?\\s*[sStT]\\d{1,3}[eExX]\\d{1,4}.*$","");s=s.replaceAll("(?i)\\s*[-–:]?\\s*(?:temporada|temp\\.?|season)\\s*\\d+.*$","");s=s.replaceAll("(?i)\\s*[-–:]?\\s*(?:epis[oó]dio|episode|ep\\.?)\\s*\\d+.*$","");return s.trim();}
 public static String key(JSONObject o){return name(o).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
 public static boolean episode(JSONObject o){return numbers(o)!=null || (o.optInt("temporada",0)>0 && o.optInt("episodio",0)>0);}
 public static boolean isSeries(JSONObject o){String c=o.optString("categoria","").toLowerCase(Locale.ROOT);String t=o.optString("tipo","").toLowerCase(Locale.ROOT);return t.equals("serie")||t.equals("série")||c.contains("serie")||c.contains("série")||c.contains("novela")||c.contains("episod")||episode(o);}
}
