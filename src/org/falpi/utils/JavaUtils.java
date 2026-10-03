package org.falpi.utils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.net.URL;
import java.net.URLClassLoader;

import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

public class JavaUtils {

   // ==================================================================================================================================
   // Helper per l'inizializzazione inline di properties
   // ==================================================================================================================================
   public static Properties createProperties(String... ArrKeyValuePairs) {
       Properties ObjProperties = new Properties();
       for (int IntIndex=0;IntIndex+1<ArrKeyValuePairs.length;IntIndex+=2) {
           ObjProperties.setProperty(ArrKeyValuePairs[IntIndex],ArrKeyValuePairs[IntIndex + 1]);
       }
       return ObjProperties;
   }  

   // ==================================================================================================================================
   // Gestione del tempo
   // ==================================================================================================================================
   public static String getDateTime() {
       return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
   }   
 
   public static Long getTimestamp() {
       return Long.valueOf(System.currentTimeMillis());
   }
   
   // ==================================================================================================================================
   // Class loader
   // ==================================================================================================================================
   public static Class loadClass(String[] ArrClassPath,String StrClassName) throws Exception {
      
      // Converte array di stringhe in array di url
      URL[] ArrClassPathURL = new URL[ArrClassPath.length];
      for (int IntIndex=0;IntIndex<ArrClassPath.length;IntIndex++) {
         ArrClassPathURL[IntIndex] = new File(ArrClassPath[IntIndex]).toURI().toURL();
      }
      
      // Prova a caricare la classe con il classloader url based
      return new URLClassLoader(ArrClassPathURL).loadClass(StrClassName);
   }   
   
   // ==================================================================================================================================
   // Inizializza script engine
   // ==================================================================================================================================   
   public static ScriptEngine getScriptEngine() throws Exception {   
      
      // Prova a istanziare lo sript engine built-in
      ScriptEngine ObjScriptEngine =  new ScriptEngineManager().getEngineByName("JavaScript"); 
      
      // Se lo script engine JavaScript non è disponibile prova ad utilizzare lo script engine esterno      
      if (ObjScriptEngine==null) {
         ObjScriptEngine = ((ScriptEngineFactory) Class.forName("org.mozilla.javascript.engine.RhinoScriptEngineFactory").newInstance()).getScriptEngine();
      }
      
      return ObjScriptEngine;
   }

   // ==================================================================================================================================
   // Formatta lo stack trace
   // ==================================================================================================================================
   public static String getStackTrace(int IntLines,Exception ObjException) {
            
      // Acquisisce lo stacktrace
      StringWriter ObjStringWriter = new StringWriter();
      PrintWriter ObjPrintWriter = new PrintWriter(ObjStringWriter);
      ObjException.printStackTrace(ObjPrintWriter);
      
      // Frammenta lo stacktrace in righe
      String[] ArrStackTrace = ObjStringWriter.toString().split("\n");
      
      // Filtra le righe richieste
      String StrStackTrace = "";            
      for (int IntIndex=0;IntIndex<Math.min(IntLines,ArrStackTrace.length);IntIndex++) {
         StrStackTrace+= (StrStackTrace.equals("")?(""):("\n"))+ArrStackTrace[IntIndex].toString();
      }
      
      // Restituisce stacktrace filtrato
      return StrStackTrace;
   }
   
   // ==================================================================================================================================
   // Imposta un attributo anche se privato/final mediante reflection
   // ==================================================================================================================================
   public static void setField(Object ObjInstance, String StrField, Object ObjValue) throws Exception {      
      Field ObjField = ObjInstance.getClass().getDeclaredField(StrField);      
      ObjField.setAccessible(true);
      Field ObjFieldModifiers = Field.class.getDeclaredField("modifiers");
      ObjFieldModifiers.setAccessible(true);
      ObjFieldModifiers.setInt(ObjField, ObjField.getModifiers() & ~Modifier.FINAL);
      ObjField.set(null,ObjValue);
   }
   
   // ==================================================================================================================================
   // Acquisisce un attributo anche se privato mediante reflection
   // ==================================================================================================================================
   public static Object getField(Object ObjInstance, String StrField) throws Exception {      
      Field ObjField = ObjInstance.getClass().getDeclaredField(StrField);
      ObjField.setAccessible(true);
      return ObjField.get(ObjInstance);
   }
   
   // ==================================================================================================================================
   // Risale una catena di classi attraverso la specifica di una lista di attributi da utilizzare
   // ==================================================================================================================================
   public static Object getFieldNested(Object ObjInstance, String... ArrFields) throws Exception {     
      Field ObjField = null;
      if (ArrFields.length>0) {
         ObjField = ObjInstance.getClass().getDeclaredField(ArrFields[0]);
         ObjField.setAccessible(true);
         ObjField = (Field) ObjField.get(ObjInstance);
         if (ArrFields.length>1) {
            ObjField = (Field) getFieldNested(ObjField,Arrays.copyOfRange(ArrFields,1,ArrFields.length));
         }
      }
      return ObjField;
   } 
      
   // ==================================================================================================================================
   // Acquisisce versione java
   // ==================================================================================================================================
   public static int getJavaVersion() {
      String StrVersion = System.getProperty("java.version");
      if(StrVersion.startsWith("1.")) {
          StrVersion = StrVersion.substring(2, 3);
      } else {
          int IntDotIndex = StrVersion.indexOf(".");
          if(IntDotIndex != -1) { 
             StrVersion = StrVersion.substring(0,IntDotIndex); 
          }
      } 
      return Integer.parseInt(StrVersion);
   }   
}
