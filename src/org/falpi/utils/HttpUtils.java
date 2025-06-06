package org.falpi.utils;

import java.net.URL;
import java.util.HashMap;
import java.util.ArrayList;
import java.security.Principal;
import java.security.PrivilegedAction;

import java.util.List;
import java.util.Enumeration;
import java.util.Collections;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

import org.falpi.SuperMap;
import org.falpi.utils.logging.LogLevel;
import org.falpi.utils.logging.LogManager;
import org.falpi.utils.SecurityUtils.CustomKrb5LoginModule;

public class HttpUtils {
      
   // ==================================================================================================================================
   // Metodi http
   // ==================================================================================================================================
   public enum HttpMethod { GET,PUT,POST,HEAD,DELETE,OPTIONS }
   
   // ==================================================================================================================================
   // Acquisisce risorsa via http
   // ==================================================================================================================================
   public static byte[] fetch(HttpMethod ObjHttpMethod,
                              String StrRequestURL,byte[] ObjRequestBody,
                              String StrRequestContentType,String StrResponseContentType, 
                              String StrHostAuthMode,String StrHostUserName,String StrHostPassword,  
                              String StrProxyServerMode,String StrProxyUserName,String StrProxyPassword,
                              String StrProxyHost,int IntProxyPort,Boolean BolSSLEnforce,                         
                              int IntConnectTimeout,int IntRequestTimeout,LogManager Logger) throws Exception {

      // ==================================================================================================================================
      // Dichiara variabili
      // ==================================================================================================================================
      HttpResponse ObjHttpResponse;
      final HttpRequestBase ObjHttpRequest;
      final CloseableHttpClient ObjHttpClient;
      final HashMap<String,Object> ObjContext = new HashMap<String,Object>();      
      ArrayList<CustomKrb5LoginModule> ArrLoginContext = new ArrayList<CustomKrb5LoginModule>();
      
      // ==================================================================================================================================
      // Prepara la request e la esegue
      // ==================================================================================================================================
            
      // Prepara client di connessione
      ObjHttpClient = buildClient(StrRequestURL,
                                  StrHostAuthMode,StrHostUserName,StrHostPassword,  
                                  StrProxyServerMode,StrProxyUserName,StrProxyPassword,
                                  StrProxyHost,IntProxyPort,BolSSLEnforce,                              
                                  IntConnectTimeout,IntRequestTimeout,
                                  ArrLoginContext,Logger);

      // Prepara request
      ObjHttpRequest = buildRequest(ObjHttpMethod,StrRequestURL,StrRequestContentType,ObjRequestBody);

      // Se non � stato allocato alcun login context kerberos esegue, altrimenti procede
      if (ArrLoginContext.size() == 0) {
         
         // Esegue la request nel contesto ordinario, altimenti procede
         ObjHttpResponse = ObjHttpClient.execute(ObjHttpRequest);
                        
      } else {
         
         // Acquisisce i login context kerberos (al momento supportato solo un context)
         CustomKrb5LoginModule ObjLoginModule = ArrLoginContext.get(0);
         Subject ObjSubject = ObjLoginModule.getSubject();
         
         // Racchiude la request in contesto privilegiato
         PrivilegedAction<Boolean> ObjAction = new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
               try {
                  ObjContext.put("response",ObjHttpClient.execute(ObjHttpRequest));
               } catch (Exception ObjException) {
                  ObjContext.put("exception",ObjException);
               } 
               return true;
            }
         };

         // Esecuzione privilegiata della request
         Subject.doAs(ObjSubject, ObjAction);
         
         // Esegue logout e svuota l'array
         ObjLoginModule.logout();
         ArrLoginContext.clear();
         
         // Se c'� stata eccezione la genera
         if (ObjContext.containsKey("exception")) {
            throw (Exception) ObjContext.get("exception");
         }
         
         // Acquisisce response
         ObjHttpResponse = (HttpResponse) ObjContext.get("response");
      }

      // ==================================================================================================================================
      // Gestisce la response
      // ==================================================================================================================================

      // Se lo statuscode � diverso da 200 genera eccezione
      if (ObjHttpResponse.getStatusLine().getStatusCode()!=200) {
         throw new Exception(ObjHttpResponse.getStatusLine().getReasonPhrase()+" (HTTP "+ObjHttpResponse.getStatusLine().getStatusCode()+")");
      }

      // Se il content-type di response non � corretto genera eccezione
      if ((!StrResponseContentType.equals(""))&&
          (!ObjHttpResponse.getEntity().getContentType().getValue().startsWith(StrResponseContentType))) {
         throw new Exception("Unexpected content-type ("+ObjHttpResponse.getEntity().getContentType().getValue()+")");
      }

      // Restituisce payload al chiamante
      return IOUtils.toByteArray(ObjHttpResponse.getEntity().getContent());
   }
   
   // ==================================================================================================================================
   // Costruisce http request
   // ==================================================================================================================================   
   public static HttpRequestBase buildRequest(final HttpMethod ObjHttpMethod,String StrRequestURL,String StrRequestContentType,byte[] ObjRequestBody) {
      
      HttpRequestBase ObjHttpRequest = null;

      if (ObjRequestBody!=null) {
         ObjHttpRequest = new HttpPost(StrRequestURL) { @Override public String getMethod() { return ObjHttpMethod.name(); }};
         ((HttpPost) ObjHttpRequest).setEntity(new ByteArrayEntity(ObjRequestBody));
      } else {
         switch (ObjHttpMethod) {
            case GET: ObjHttpRequest = new HttpGet(StrRequestURL);break;
            case PUT: ObjHttpRequest = new HttpPut(StrRequestURL);break;
            case POST: ObjHttpRequest = new HttpPost(StrRequestURL);break;
            case HEAD: ObjHttpRequest = new HttpHead(StrRequestURL);break;
            case DELETE: ObjHttpRequest = new HttpDelete(StrRequestURL);break;
            case OPTIONS: ObjHttpRequest = new HttpOptions(StrRequestURL);break;
         }
      }
      
      // Se il content type � definito lo aggiunge come header
      if (StrRequestContentType!= "") ObjHttpRequest.addHeader("Content-Type", StrRequestContentType);
      
      // Restrituisce request
      return ObjHttpRequest;
   }

   // ==================================================================================================================================
   // Prepara request http
   // ==================================================================================================================================
   public static CloseableHttpClient buildClient(String StrRequestURL, 
                                                 String StrHostAuthMode,String StrHostUserName,String StrHostPassword,  
                                                 String StrProxyServerMode,String StrProxyUserName,String StrProxyPassword,
                                                 String StrProxyHost,int IntProxyPort,Boolean BolSSLEnforce,                              
                                                 int IntConnectTimeout,int IntRequestTimeout,
                                                 ArrayList<CustomKrb5LoginModule> ArrLoginContext,LogManager Logger) throws Exception {
               
      // ==================================================================================================================================
      // Dichiara variabili
      // ==================================================================================================================================
      String[] ArrParts;
      String StrSplitDomain;
      String StrSplitUserName;
      ArrayList<String> ArrTargetAuthSchemes = new ArrayList<String>();
      
      // ==================================================================================================================================
      // Prepara configurazione request di base
      // ==================================================================================================================================

      // Configurazione base
      RequestConfig.Builder ObjRequestConfigBuilder =
         RequestConfig.custom().setConnectTimeout(IntConnectTimeout * 1000)
                               .setConnectionRequestTimeout(IntRequestTimeout * 1000);
              
      // ==================================================================================================================================
      // Prepara configurazione per autenticazione
      // ==================================================================================================================================

      // Prepara provider delle credenziali
      BasicCredentialsProvider ObjAuthCredsProvider = new BasicCredentialsProvider();

      // Prepara schemi di autenticazione supportati
      RegistryBuilder<AuthSchemeProvider> ObjAuthSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create();         
      
      // ==================================================================================================================================
      // Se richiesto gestisce url con autenticazione
      // ==================================================================================================================================
      if (!StrHostAuthMode.equals("ANONYMOUS")) {
                           
         // Prepara auth scope dell'host
         URL ObjRequestURL = new URL(StrRequestURL);
         AuthScope ObjHostAuthScope = new AuthScope(new HttpHost(ObjRequestURL.getHost(), ObjRequestURL.getPort()));

         switch (StrHostAuthMode) {

            // ----------------------------------------------------------------------------------------------------------------------------------
            // Autenticazione BASIC
            // ----------------------------------------------------------------------------------------------------------------------------------
            case "BASIC":        
            
               // Genera logging
               Logger.logProperty(LogLevel.DEBUG,"Host Auth UserName",StrHostUserName);                  

               // Predispone schema di autenticazione
               ArrTargetAuthSchemes.add(AuthSchemes.BASIC);
               ObjAuthSchemeRegistry.register(AuthSchemes.BASIC,new BasicSchemeFactory());

               // Predispone credenziali
               ObjAuthCredsProvider.setCredentials(ObjHostAuthScope,new UsernamePasswordCredentials(StrHostUserName, StrHostPassword));
               break;
            
            // ----------------------------------------------------------------------------------------------------------------------------------
            // Autenticazione NTLM
            // ----------------------------------------------------------------------------------------------------------------------------------
            case "NTLM":
               
               // Prepara credenziali
               ArrParts = StrHostUserName.split("\\\\", 2);
               
               StrSplitDomain = null;
               StrSplitUserName = null;
                                       
               if (ArrParts.length>1) {
                  StrSplitDomain = ArrParts[0];
                  StrSplitUserName = ArrParts[1];               
               } else {
                  StrSplitUserName = ArrParts[0];               
               }
               
               // Genera logging
               Logger.logProperty(LogLevel.DEBUG,"Host Auth Domain",StrSplitDomain);                  
               Logger.logProperty(LogLevel.DEBUG,"Host Auth UserName",StrSplitUserName);   

               // Predispone schema di autenticazione
               ArrTargetAuthSchemes.add(AuthSchemes.NTLM);
               ObjAuthSchemeRegistry.register(AuthSchemes.NTLM,new NTLMSchemeFactory());

               // Predispone credenziali
               ObjAuthCredsProvider.setCredentials(ObjHostAuthScope,new NTCredentials(StrSplitUserName, StrHostPassword, null, StrSplitDomain));
               break;
               
            // ----------------------------------------------------------------------------------------------------------------------------------
            // Autenticazione KERBEROS
            // ----------------------------------------------------------------------------------------------------------------------------------
            case "KERBEROS":
            
               // Genera logging
               Logger.logProperty(LogLevel.DEBUG,"Host Auth Principal",StrHostUserName);               
               
               // Esegue login kerberos
               ArrLoginContext.add(SecurityUtils.loginKerberos(StrHostUserName,StrHostPassword));
               
               // Predispone schema di autenticazione
               ArrTargetAuthSchemes.add(AuthSchemes.SPNEGO);               
               ObjAuthSchemeRegistry.register(AuthSchemes.SPNEGO,new SPNegoSchemeFactory());

               // Predispone credenziali
               ObjAuthCredsProvider.setCredentials(ObjHostAuthScope,
                  new Credentials() {
                     public String getPassword() { return null; }
                     public Principal getUserPrincipal() { return null; } 
                  });
               
               break;        
            }
      }

      // ==================================================================================================================================
      // Se richiesto acquisisce parametri proxy
      // ==================================================================================================================================
      if (!StrProxyServerMode.equals("DIRECT")) {

         // Genera logging
         Logger.logProperty(LogLevel.DEBUG,"Proxy Server Host",StrProxyHost);                  
         Logger.logProperty(LogLevel.DEBUG,"Proxy Server Port",String.valueOf(IntProxyPort));                  

         // Prepara auth scope del proxy
         HttpHost ObjProxyHost = new HttpHost(StrProxyHost, IntProxyPort);
         AuthScope ObjProxyAuthScope = new AuthScope(ObjProxyHost);

         // Aggiunge parametro proxy a request client
         ObjRequestConfigBuilder.setProxy(ObjProxyHost);

         // Gestisce autenticazione proxy            
         switch (StrProxyServerMode) {

            // ----------------------------------------------------------------------------------------------------------------------------------
            // Autenticazione BASIC
            // ----------------------------------------------------------------------------------------------------------------------------------
            case "BASIC":
               
               // Genera logging
               Logger.logProperty(LogLevel.DEBUG,"Proxy Server Auth UserName",StrProxyUserName);                  

               // Se necessario predispone schema di autenticazione
               if (!StrHostAuthMode.equals("BASIC")) {
                  ArrTargetAuthSchemes.add(AuthSchemes.BASIC);                              
                  ObjAuthSchemeRegistry.register(AuthSchemes.BASIC,new BasicSchemeFactory());
               }
               
               // Predispone credenziali
               ObjAuthCredsProvider.setCredentials(ObjProxyAuthScope,new UsernamePasswordCredentials(StrProxyUserName,StrProxyPassword));
               break;

            // ----------------------------------------------------------------------------------------------------------------------------------
            // Autenticazione NTLM
            // ----------------------------------------------------------------------------------------------------------------------------------
            case "NTLM":
               
               // Prepara credenziali
               ArrParts = StrProxyUserName.split("\\\\", 2);   
               
               StrSplitDomain = null;
               StrSplitUserName = null;
                                    
               if (ArrParts.length>1) {
                  StrSplitDomain = ArrParts[0];
                  StrSplitUserName = ArrParts[1];               
               } else {
                  StrSplitUserName = ArrParts[0];               
               }

               // Genera logging
               Logger.logProperty(LogLevel.DEBUG,"Proxy Server Auth Domain",StrSplitDomain);                  
               Logger.logProperty(LogLevel.DEBUG,"Proxy Server Auth UserName",StrSplitUserName);  
               
               // Se necessario predispone schema di autenticazione
               if (!StrHostAuthMode.equals("NTLM")) {
                  ArrTargetAuthSchemes.add(AuthSchemes.NTLM); 
                  ObjAuthSchemeRegistry.register(AuthSchemes.NTLM,new NTLMSchemeFactory());
               }
               
               // Predispone credenziali
               ObjAuthCredsProvider.setCredentials(ObjProxyAuthScope,new NTCredentials(StrSplitUserName, StrProxyPassword, null,StrSplitDomain));
               break;
            
            // ----------------------------------------------------------------------------------------------------------------------------------
            // Autenticazione KERBEROS
            // ----------------------------------------------------------------------------------------------------------------------------------
            case "KERBEROS":
               
               // Genera logging
               Logger.logProperty(LogLevel.DEBUG,"Proxy Server Auth Principal",StrProxyUserName);   
               
               // Esegue login kerberos
               ArrLoginContext.add(SecurityUtils.loginKerberos(StrProxyUserName,StrProxyPassword));                  

               // Se necessario predispone schema di autenticazione
               if (!StrHostAuthMode.equals("KERBEROS")) {
                  ArrTargetAuthSchemes.add(AuthSchemes.SPNEGO); 
                  ObjAuthSchemeRegistry.register(AuthSchemes.SPNEGO,new SPNegoSchemeFactory());                     
               }

               // Predispone credenziali
               ObjAuthCredsProvider.setCredentials(ObjProxyAuthScope,                     
                  new Credentials() {
                     public String getPassword() { return null; }
                     public Principal getUserPrincipal() { return null; } 
                  });
               
               break;        
         }
      }

      // ==================================================================================================================================
      // Prepara request client
      // ==================================================================================================================================

      // Evita il fastidioso logging che avvisa della mancanza di supporto per uno schema proposto dal server
      ObjRequestConfigBuilder.setTargetPreferredAuthSchemes(ArrTargetAuthSchemes);

      // Request client base
      HttpClientBuilder ObjHttpClientBuilder = HttpClients.custom()
         .setDefaultCredentialsProvider(ObjAuthCredsProvider)
         .setDefaultAuthSchemeRegistry(ObjAuthSchemeRegistry.build())
         .setDefaultRequestConfig(ObjRequestConfigBuilder.build());

      // Aggiunge eventuale tolleranza errori certificati ssl
      if (!BolSSLEnforce) {
         ObjHttpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                             .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null,TrustAllStrategy.INSTANCE).build());
      }
            
      // Restituisce client http
      return ObjHttpClientBuilder.build();
   }
   
   // ==================================================================================================================================
   // Acquisisce headers in formato mappa
   // ==================================================================================================================================
   public static SuperMap getHeaders(HttpServletRequest ObjRequest,List<String> ArrExclusions) throws Exception {
      
      String StrHeaderName;
      SuperMap ObjMap = new SuperMap();
      Enumeration<String> ObjEnumerator = ObjRequest.getHeaderNames();
      
      while (ObjEnumerator.hasMoreElements()) {
         StrHeaderName = ObjEnumerator.nextElement();
         if (!ArrExclusions.contains(StrHeaderName)) {
            ObjMap.put(StrHeaderName,Collections.list(ObjRequest.getHeaders(StrHeaderName)));
         }         
      }
      
      return ObjMap;
   }   
}
