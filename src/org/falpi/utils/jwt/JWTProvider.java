package org.falpi.utils.jwt;

import java.util.Map;

public abstract class JWTProvider<T> {
   
   // ==================================================================================================================================
   // Variabili 
   // ==================================================================================================================================
   
   // Istanza del token interno
   protected T ObjToken;
   
   // Flag di inizializzazione avvenuta del token
   private Boolean BolReady = false;

   // ==================================================================================================================================
   // Crea istanze del token provider
   // ==================================================================================================================================

   // Crea nuova istanza del token provider specificato
   public synchronized static JWTProvider create(String StrProvider) throws Exception {      
      return (JWTProvider) Class.forName(JWTProvider.class.getCanonicalName()+StrProvider+"Impl").newInstance();
   }
   
   // Crea nuova istanza del token provider a partire dalla classe dell'istanza
   public synchronized JWTProvider createInstance() throws Exception {      
      return this.getClass().newInstance();
   }

   // ==================================================================================================================================
   // Inizializza il token
   // ==================================================================================================================================
   public void init(T ObjToken) throws Exception {
      this.BolReady = true;
      this.ObjToken = ObjToken;
   }
   
   // ==================================================================================================================================
   // Inizializza eseguendo parsing di una stringa base64
   // ==================================================================================================================================
   public abstract void parse(String StrToken) throws Exception;

   // ==================================================================================================================================
   // Acquisisce la versione della libreria
   // ==================================================================================================================================
   public abstract String version() throws Exception;
         
   // ==================================================================================================================================
   // Acquisisce il keyid del token
   // ==================================================================================================================================
   public abstract String getKeyID() throws Exception;

   // ==================================================================================================================================
   // Acquisisce l'header del token
   // ==================================================================================================================================
   public abstract Map getHeader() throws Exception;

   // ==================================================================================================================================
   // Acquisisce il payload del token
   // ==================================================================================================================================
   public abstract Map getPayload() throws Exception;

   // ==================================================================================================================================
   // Verifica che il token jwt sia conforme a quanto atteso
   // ==================================================================================================================================
   public abstract boolean verify(String StrModulus, String StrExponent) throws Exception;

   // ==================================================================================================================================
   // Verifica che il token jwt sia inizializzato
   // ==================================================================================================================================
   public boolean isReady() {
      return BolReady;
   }
}
