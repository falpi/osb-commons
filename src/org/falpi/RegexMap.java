package org.falpi;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class RegexMap extends LinkedHashMap<String,Object> {
   
   @SuppressWarnings("compatibility")
   private static final long serialVersionUID = 1L;
   
   // Ricerca il match su base espressione regolare
   public ArrayList<Object> getRegex(String StrKey,Boolean BolFirst) {
      
      // Prepara array dei risultati
      ArrayList<Object> ObjResults = new ArrayList<Object>();
      
      // Aggiunge ai risultati tutte le entry che matchano con la chiave fornita
      for (Map.Entry<String,Object> ObjEntry : entrySet()) {
         if (StrKey.matches(ObjEntry.getKey())) {
            ObjResults.add(ObjEntry.getValue());
            if (BolFirst) break;
         }
      }                  
      
      // Restituisce risultati
      return ObjResults;
   }      
}

