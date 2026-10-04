package org.falpi.utils.jwt;

import java.util.concurrent.ConcurrentHashMap;

public class JWTCache {
   
   // ==================================================================================================================================
   // Sottoclassi
   // ==================================================================================================================================

   // Sottoclasse la cache delle chiavi
   public static class JWTKeysCacheEntry {  
      
      public long timeStamp;
      public String modulus;      
      public String exponent; 
      
      JWTKeysCacheEntry(String StrModulus,String StrExponent) {
         this.modulus = StrModulus;
         this.exponent = StrExponent;
         this.timeStamp = System.currentTimeMillis()/1000;
      }      
   }
   
   // Sottoclasse per la cache dei token
   public static class JWTTokensCacheEntry {  
      
      public long timeStamp;
      public JWTProvider token; 
      
      JWTTokensCacheEntry(JWTProvider ObjToken) {
         this.token = ObjToken;
         this.timeStamp = System.currentTimeMillis()/1000;      
      }      
   }   
   
   // ==================================================================================================================================
   // Variabili 
   // ==================================================================================================================================
 
   // Istanze delle cache di chiavi e token
   private ConcurrentHashMap<String,JWTKeysCacheEntry> keysCache = new ConcurrentHashMap<String,JWTKeysCacheEntry>();
   private ConcurrentHashMap<String,JWTTokensCacheEntry> tokensCache = new ConcurrentHashMap<String,JWTTokensCacheEntry>();
      
   // ==================================================================================================================================
   // Metodi per caching delle chiavi
   // ==================================================================================================================================
   
   // Inserisce una chiave nella cache
   public JWTKeysCacheEntry putKey(String StrKeyID,String StrModulus,String StrExponent) {
      JWTKeysCacheEntry ObjEntry = new JWTKeysCacheEntry(StrModulus,StrExponent);
      keysCache.put(StrKeyID,ObjEntry);
      return ObjEntry;
   }

   // Acquisisce chiave dalla cache
   public JWTKeysCacheEntry getKey(String StrKeyID) {    
      return keysCache.get(StrKeyID);
   }

   // Acquisisce chiave dalla cache se non è scaduta
   public JWTKeysCacheEntry validKey(String StrKeyID,int IntKeysTTL) {
      
      // Determina timestamp attuale
      long IntTimeStamp = System.currentTimeMillis()/1000;
      
      // Cerca chiave in cache
      JWTKeysCacheEntry ObjEntry = keysCache.get(StrKeyID);
      
      // Se la chiave esiste ma è scaduta resetta riferimento
      if ((ObjEntry!=null)&&(IntTimeStamp>=(ObjEntry.timeStamp+IntKeysTTL))) {
         keysCache.remove(StrKeyID,ObjEntry);
         ObjEntry = null;
      }
         
      // Restituisce riferimento all'entry
      return ObjEntry;
   }
   
   // ==================================================================================================================================
   // Metodi per caching dei token
   // ==================================================================================================================================
   
   // Inserisce un token nella cache
   public JWTTokensCacheEntry putToken(String StrTokenID,JWTProvider ObjToken) {
      JWTTokensCacheEntry ObjEntry = new JWTTokensCacheEntry(ObjToken);
      tokensCache.put(StrTokenID,ObjEntry);
      return ObjEntry;      
   }

   // Acquisisce token dalla cache 
   public JWTTokensCacheEntry getToken(String StrTokenID,long IntTokensTTL) throws Exception {  
      
      // Determina timestamp attuale
      long IntTimeStamp = System.currentTimeMillis()/1000;
      
      // Ricerca il token in base all'indice fornito
      JWTTokensCacheEntry ObjEntry = tokensCache.get(StrTokenID);
      
      // Se il token esiste ma è scaduto in base alla sua scadenza o alla durata fornita lo rimuove dalla cache
      if ((ObjEntry!=null)&&((IntTimeStamp>=ObjEntry.token.getExpiration())||(IntTimeStamp>=(ObjEntry.timeStamp+IntTokensTTL)))) {
         tokensCache.remove(StrTokenID,ObjEntry);
         ObjEntry = null;
      }
      
      // Restituisce il token
      return ObjEntry;
   }   
   
}
