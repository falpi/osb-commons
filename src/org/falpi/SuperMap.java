package org.falpi;

import java.util.ArrayList;
import java.util.Properties;
import java.util.LinkedHashMap;

public class SuperMap extends LinkedHashMap<String,Object> {

   @SuppressWarnings("compatibility")
   private static final long serialVersionUID = 1L;
   
   // ==================================================================================================================================
   // Variabili istanza
   // ==================================================================================================================================
   
   // Elemento speciale per espressioni regolari
   private transient RegexMap ObjRegExMap = new RegexMap();   
      
   // ==================================================================================================================================
   // Motodi per accesso a mappa regex
   // ==================================================================================================================================
   public ArrayList<Object> getRegex(String StrKey,Boolean BolFirst) {
      return ObjRegExMap.getRegex(StrKey,BolFirst);
   }
      
   public Object putRegex(String StrRegexKey,Object ObjValue) {
      return ObjRegExMap.put(StrRegexKey,ObjValue);
   }

   // ==================================================================================================================================
   // Motodi per estrazione tipizzata da mappa principale
   // ==================================================================================================================================      
   public Integer getInteger(String StrKey) {
      return (Integer) get(StrKey);
   }  

   public String getString(String StrKey) {
      return getString(StrKey,"");
   }

   public String getString(String StrKey,String StrDefault) {
      Object ObjKey = get(StrKey);
      return (ObjKey==null)?(""):((ObjKey instanceof Integer)?(Integer.toString((Integer)ObjKey)):(ObjKey.toString()));
   }

   public String[] getStringArray(String StrKey) {
      return (String[]) get(StrKey);
   }
   
   public Properties getProperties(String StrKey) {
      return (Properties) get(StrKey);
   }   
}
