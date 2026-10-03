package org.falpi.utils;

import java.util.ArrayList;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.security.AccessController;

import javax.xml.bind.DatatypeConverter;
import javax.servlet.http.HttpServletRequest;
import javax.management.InvalidAttributeValueException;

import weblogic.management.EncryptionHelper;
import weblogic.management.security.ProviderMBean;
import weblogic.management.provider.ManagementService;
import weblogic.security.service.PrivilegedActions;
import weblogic.security.acl.internal.AuthenticatedSubject;
import weblogic.security.providers.authentication.DefaultAuthenticatorMBean;

public class WLSUtils {
   
   // ##################################################################################################################################
   // Sottoclassi
   // ##################################################################################################################################

   // ==================================================================================================================================
   // Wrapper per incapsulare la gestione dell'autenticatore embedded di wls
   // ==================================================================================================================================
   public static class Authenticator {

      // Prepara handle per autenticatore embedded di wls
      private Object ObjAuthenticator;
      
      // Imposta il nome della classe di autenticazione embedded di weblogic
      private String StrClassName = 
         "weblogic.security.providers.authentication.EmbeddedLDAPAtnDelegate";
            
      // Imposta il prefisso del classpath dei provider di sicurezza di weblogic
      private String StrClassPath = 
         Paths.get(System.getenv().get("WL_HOME"),"server","lib","mbeantypes").toString(); 
      
      // Imposta i classpath possibili per il caricamento della classe di autenticazione
      private String[] ArrClassPath = 
         { Paths.get(StrClassPath,"cssWlSecurityProviders.jar").toString(),
           Paths.get(StrClassPath,"wls-security-providers.jar").toString() };
      
      // ==================================================================================================================================
      // Costruttore
      // ==================================================================================================================================
      Authenticator(ProviderMBean ObjMBean, String StrRealmName,String StrDomainName) throws Exception { 
         
         // Carica la classe dell'autenticatore integrato di weblogic
         Class<?> ObjClass = JavaUtils.loadClass(ArrClassPath,StrClassName);
         
         // Istanzia il costruttore della classe 
         Constructor ObjConstructor = 
            ObjClass.getConstructor(ProviderMBean.class,DefaultAuthenticatorMBean.class,String.class,String.class,boolean.class);         
         
         // Istanzia la classe
         ObjAuthenticator = ObjConstructor.newInstance(ObjMBean, null,StrRealmName, StrDomainName, false);
      }     
      
      // ==================================================================================================================================
      // Autentica uno username e password sul realm weblogic
      // ==================================================================================================================================
      public String authenticate(String StrUserName,String  StrPassword) throws Exception {
         
         // Istanzia il metodo della classe
         Method ObjMethod = ObjAuthenticator.getClass().getMethod("authenticate",String.class,String.class);         

         // Esegue autenticazione
         return (String) ObjMethod.invoke(ObjAuthenticator,StrUserName,StrPassword);  
      }
   }
   
   // ##################################################################################################################################
   // Variabili statiche
   // ##################################################################################################################################

   // Prepara handle per accesso al kernel wls
   private static final AuthenticatedSubject ObjKernelId =
      (AuthenticatedSubject) AccessController.doPrivileged(PrivilegedActions.getKernelIdentityAction());

   // ##################################################################################################################################
   // Metodi statici 
   // ##################################################################################################################################
   
   // ==================================================================================================================================
   // Acquisisce handle per accesso al kernel wls
   // ==================================================================================================================================
   public static AuthenticatedSubject getKernelId() {   
      return ObjKernelId;
   }
   
   // ==================================================================================================================================
   // Acquisisce l'authenticator integrato di wls
   // ==================================================================================================================================      
   public static Authenticator getAuthenticator(ProviderMBean ObjMBean, String StrRealmName,String StrDomainName) throws Exception { 
      return new Authenticator(ObjMBean,StrRealmName,StrDomainName);
   }   
   
   // ==================================================================================================================================
   // Acquisisce nome dominio wls
   // ==================================================================================================================================      
   public static String getDomainName() { 
      return ManagementService.getRuntimeAccess(ObjKernelId).getDomainName();
   }

   // ==================================================================================================================================
   // Acquisisce nome del managed server wls
   // ==================================================================================================================================      
   public static String getManagedName() { 
      return ManagementService.getRuntimeAccess(ObjKernelId).getServerName();
   }   
   
   // ==================================================================================================================================
   // Servizi di cifratura/decifratura infrastrutturali
   // ==================================================================================================================================
   public static String encrypt(byte[] ObjPlainValue) {
      return DatatypeConverter.printBase64Binary(EncryptionHelper.encrypt(ObjPlainValue));
   }

   public static String encryptString(String StrPlainValue) {
      return DatatypeConverter.printBase64Binary(EncryptionHelper.encryptString(StrPlainValue));
   }

   public static byte[] decrypt(String StrEncryptedValue) {
      return EncryptionHelper.decrypt(DatatypeConverter.parseBase64Binary(StrEncryptedValue),ObjKernelId);
   }

   public static String decryptString(String StrEncryptedValue) {
      return EncryptionHelper.decryptString(DatatypeConverter.parseBase64Binary(StrEncryptedValue),ObjKernelId);
   }   

   // ==================================================================================================================================
   // Helper per gestione cifratura
   // ==================================================================================================================================
   public static String encryptionHelper(String StrValue) throws InvalidAttributeValueException {
      
      if (!StrValue.equals("")) {
         if (!StrValue.startsWith("encrypted:")) {
            try { StrValue = "encrypted:"+encryptString(StrValue); } catch (Exception ObjException) { throw new InvalidAttributeValueException("Error while encrypting value"); }
         } else {
            String[] ArrParts = StrValue.split(":");
            if ((ArrParts.length!=2)||ArrParts[1].isEmpty()) throw new InvalidAttributeValueException("Invalid format for encrypted attribute"); 
            try { decryptString(ArrParts[1]); } catch (Exception ObjException) { throw new InvalidAttributeValueException("Error while decrypting value"); }
         }        
      }
      return StrValue;
   }
   
   public static String decryptionHelper(String StrValue) throws InvalidAttributeValueException {
      return (!StrValue.equals(""))?(WLSUtils.decryptString(WLSUtils.encryptionHelper(StrValue).split(":")[1])):("");
   }   
   
   // ==================================================================================================================================
   // Helper per aggiunta di header alla request servlet che non espone il metodo (a differenza della response servlet)
   // ==================================================================================================================================
   public static String addRequestHeader(HttpServletRequest ObjRequest,String StrHeaderName,String StrHeaderValue) throws Exception {
      
      // Accede agli header della request
      Object ObjHeaders = JavaUtils.getField(ObjRequest,"headers");
      ArrayList<String> ArrHeaderNames = (ArrayList<String>) JavaUtils.getField(ObjHeaders, "headerNames");
      ArrayList<byte[]> ArrHeaderValues = (ArrayList<byte[]>) JavaUtils.getField(ObjHeaders, "headerValues");
      
      // Se l'header indicato esiste ne sostituisce il valore ed esce restituendo il valore precedente
      for (int IntIndex=0;IntIndex<ArrHeaderNames.size(); IntIndex++) {
         String StrName = ArrHeaderNames.get(IntIndex);
         if (StrName.equals(StrHeaderName)) {
            return new String(ArrHeaderValues.set(IntIndex,StrHeaderValue.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
         }
      }
      
      // Imposta il nuovo header alla fine dell'array
      ArrHeaderNames.add(StrHeaderName);
      ArrHeaderValues.add(StrHeaderValue.getBytes(StandardCharsets.UTF_8)); 

      // Restituisce null in quanto header non esisteva
      return null;
   }
}
