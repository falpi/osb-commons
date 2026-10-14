package org.falpi.utils;

import java.io.IOException;
import java.io.BufferedReader;

import java.io.InputStream;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import org.falpi.SuperMap;

public class StringUtils {

   // ==================================================================================================================================
   // Helper per functional programming
   // ==================================================================================================================================  
   public interface TemplateFunction {

      String apply(String StrVariable,SuperMap ObjContext) throws Exception;

      // Variante con selettore opzionale (testo che segue il carattere '#' nel template, es. un'espressione xpath)
      // Le funzioni che non supportano il selettore non devono ridefinirla: se il selettore e' presente generano eccezione
      default String apply(String StrVariable,String StrSelector,SuperMap ObjContext) throws Exception {
         if (StrSelector!=null) throw new Exception("selector not supported by template variable '"+StrVariable+"'");
         return apply(StrVariable,ObjContext);
      }
   }

   // ==================================================================================================================================
   // Fonde le proprietà in un unica stringa
   // ==================================================================================================================================
   public static String join(Properties ObjProperties, String StrSeparator) {
      
      if (ObjProperties==null) return "";
      
      StringBuilder StrProperties = new StringBuilder();
      for (String StrKey : ObjProperties.stringPropertyNames()) {
         StrProperties.append(StrKey).append("=").append(ObjProperties.getProperty(StrKey).trim()).append(StrSeparator);
      }
      
      // Remove the last comma
      if (StrProperties.length() > 0) {
         StrProperties.setLength(StrProperties.length() - 1);
      }
      
      return StrProperties.toString();
   }
   
   public static String join(String[] ArrStrings, String StrSeparator) {            
      String StrOutput = "";
      for (int IntIndex=0;IntIndex<ArrStrings.length;IntIndex++){            
         StrOutput+= ((IntIndex>0)?(StrSeparator):(""))+ArrStrings[IntIndex].trim();   
      }       
      return StrOutput;
   }
   
   public static String join(Enumeration<String> ObjStrings, String StrSeparator) {            
      String StrOutput = "";
      while (ObjStrings.hasMoreElements()) {
         StrOutput+= ((!StrOutput.equals(""))?(StrSeparator):(""))+ObjStrings.nextElement().trim();
      }
      return StrOutput;
   } 

   public static String join(List<String> ObjList, String StrSeparator) {            
      String StrOutput = "";
      Iterator<String> ObjIterator = ObjList.iterator();
      while (ObjIterator.hasNext()) {
         StrOutput+= ((!StrOutput.equals(""))?(StrSeparator):(""))+ObjIterator.next().trim();
      }
      return StrOutput;       
   }
   
   public static String join(ArrayList<String> ArrStrings, String StrSeparator) {            
      String StrOutput = "";
      Iterator<String> ObjIterator = ArrStrings.iterator();
      while (ObjIterator.hasNext()) {
         StrOutput+= ((!StrOutput.equals(""))?(StrSeparator):(""))+ObjIterator.next().trim();
      }
      return StrOutput;       
   }   
   // ==================================================================================================================================
   // Genera una sequenza di caratteri
   // ==================================================================================================================================
   public static String repeat(int IntLength, String StrFiller) {
      return new String(new char[IntLength]).replace("\0", StrFiller);
   }

   // ==================================================================================================================================
   // Esegue padding di stringhe
   // ==================================================================================================================================
   public static String padRight(String StrTarget, int IntLength, String StrFiller) {
      if (IntLength > StrTarget.length()) {
         StrTarget = StrTarget + repeat(IntLength-StrTarget.length(), StrFiller);
      }
      return StrTarget;
   }

   public static String padLeft(String StrTarget, int IntLength, String StrFiller) {
      if (IntLength > StrTarget.length()) {
         StrTarget = repeat(IntLength-StrTarget.length(), StrFiller) + StrTarget;
      }
      return StrTarget;
   }
 
   // ==================================================================================================================================
   // Calcola dimensione massima di un insieme di stringhe
   // ==================================================================================================================================
   public static int getMaxLength(Iterator<String> ObjIterator) {
      return getMaxLength(ObjIterator, new ArrayList());
   }

   public static int getMaxLength(Iterator<String> ObjIterator, List ArrExclusion) {
      int IntMaxLength = 0;
      while (ObjIterator.hasNext()) {
         String StrElement = ObjIterator.next();
         if (!ArrExclusion.contains(StrElement)) {
            IntMaxLength = Math.max(IntMaxLength, StrElement.length());
         }
      }
      return IntMaxLength;
   }

   public static int getMaxLength(Enumeration<String> ObjEnumerator) {
      return getMaxLength(ObjEnumerator, new ArrayList());
   }
   
   public static int getMaxLength(Enumeration<String> ObjEnumerator, List ArrExclusion) {      
      return getMaxLength(Collections.list(ObjEnumerator).iterator(),ArrExclusion);
   }
    
   // ==================================================================================================================================
   // Converte stringa di byte in esadecimale
   // ==================================================================================================================================
   public static String bytesToHex(byte[] ArrBytes) {
      StringBuilder ObjResult = new StringBuilder();
      for (byte ObjByte : ArrBytes) ObjResult.append(String.format("%02X", ObjByte));
      return ObjResult.toString();
   } 

   // ==================================================================================================================================
   // Wrapper di metodi di conversione stringa in via di deprecazione
   // ==================================================================================================================================
   public static String toString(BufferedReader ObjReader) throws IOException {
      return IOUtils.toString(ObjReader);
   }
   
   public static String toString(InputStream ObjInputStream) throws IOException {
      return new String(IOUtils.toByteArray(ObjInputStream));
   }

   // ==================================================================================================================================
   // Replace template variables
   // ==================================================================================================================================
   public static String replaceTemplates(SuperMap ObjContext,String StrText) throws Exception {
                  
      // ----------------------------------------------------------------------------------------------------------------------------------
      // Gestisce variabili di sostituzione
      // ----------------------------------------------------------------------------------------------------------------------------------
      String StrTemplate;
      String StrVariableName;
      String StrVariableValue;
      String StrSelector;
      Object ObjVariableValue;
      ArrayList ArrVariableValues;

      // Individua i template ${...}: la graffa di chiusura racchiusa tra apici singoli o doppi non chiude il template (es. nei selettori xpath)
      Pattern ObjPattern = Pattern.compile("(\\$\\{((?:[^}'\"]|'[^']*'|\"[^\"]*\")*)\\})");
      Matcher ObjMatcher = ObjPattern.matcher(StrText);

      while (ObjMatcher.find()) {

         StrTemplate = ObjMatcher.group(1);
         StrVariableName = ObjMatcher.group(2);
         StrVariableValue = null;
         StrSelector = null;

         // Se presente separa il selettore opzionale, cioe' tutto quello che segue il primo carattere '#' (es. ${osb.context.header#./*:ID})
         int IntSelectorIndex = StrVariableName.indexOf('#');
         if (IntSelectorIndex>=0) {
            StrSelector = StrVariableName.substring(IntSelectorIndex+1);
            StrVariableName = StrVariableName.substring(0,IntSelectorIndex);
         }

         // Verifica il formato del nome della variabile e che l'eventuale selettore sia valorizzato
         if ((!StrVariableName.matches("^[a-zA-Z_\\-]+(\\.[a-zA-Z_\\-]+)*(\\.\\*)?$"))||
             ((StrSelector!=null)&&(StrSelector.trim().isEmpty()))) {
            throw new Exception("invalid template variable format '"+StrTemplate+"'");
         }
         
         // Cerca la variabile tra le chiavi di contesto semplici
         ObjVariableValue = ObjContext.get(StrVariableName);
         
         // Se non è stato trovato alcun match cerca la prima variabile tra le chiavi di contesto regex
         if (ObjVariableValue==null) {
            ArrVariableValues = ObjContext.getRegex(StrVariableName,true);
            if (!ArrVariableValues.isEmpty()) ObjVariableValue = ArrVariableValues.get(0);
         }
         
         // Se sono funzioni le esegue, se è una stringa la converte, altrimenti genera eccezione
         if (ObjVariableValue instanceof String) {
            if (StrSelector!=null) throw new Exception("selector not supported by template variable '"+StrVariableName+"'");
            StrVariableValue = (String) ObjVariableValue;
         } else if (ObjVariableValue instanceof TemplateFunction) {
            StrVariableValue = ((TemplateFunction) ObjVariableValue).apply(StrVariableName,StrSelector,ObjContext);
         } else {
            throw new Exception("invalid template variable '"+StrTemplate+"'");               
         }
         
         // Esegue sostituzione della variabile template         
         StrText = StrText.replace(StrTemplate,StrVariableValue);
      }
      
      // Restituisce stringa con i template sostituiti
      return StrText;
   }   

   // ==================================================================================================================================
   // Esegue l'escape di una stringa per l'uso in un literal JavaScript (stesse regole di commons-lang 2.x escapeJavaScript)
   // ==================================================================================================================================
   public static String escapeJavaScript(String StrValue) {

      // Se la stringa non e' valorizzata la restituisce invariata
      if (StrValue==null) return null;

      // Prepara buffer di output
      StringBuilder ObjBuilder = new StringBuilder(StrValue.length()*2);

      // Esegue l'escape dei caratteri speciali, di controllo e non ascii
      for (int IntIndex=0;IntIndex<StrValue.length();IntIndex++) {
         char ChrValue = StrValue.charAt(IntIndex);
         switch (ChrValue) {
            case '\b': ObjBuilder.append("\\b"); break;
            case '\n': ObjBuilder.append("\\n"); break;
            case '\t': ObjBuilder.append("\\t"); break;
            case '\f': ObjBuilder.append("\\f"); break;
            case '\r': ObjBuilder.append("\\r"); break;
            case '\'': ObjBuilder.append("\\'"); break;
            case '"':  ObjBuilder.append("\\\""); break;
            case '\\': ObjBuilder.append("\\\\"); break;
            case '/':  ObjBuilder.append("\\/"); break;
            default:
               if ((ChrValue<32)||(ChrValue>0x7f)) ObjBuilder.append(String.format("\\u%04X",(int)ChrValue));
               else ObjBuilder.append(ChrValue);
         }
      }

      // Restituisce la stringa con escape
      return ObjBuilder.toString();
   }
}
