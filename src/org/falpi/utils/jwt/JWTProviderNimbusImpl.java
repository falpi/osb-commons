package org.falpi.utils.jwt;

// ##################################################################################################################################
// Referenze
// ##################################################################################################################################

import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;

import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSHeader.Builder;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;

import org.falpi.utils.CryptoUtils;
import org.falpi.utils.jwt.JWTProvider.JWTClaimsMap;

public class JWTProviderNimbusImpl extends JWTProvider<SignedJWT> {

   @Override
   public String serialize() {
      return token.serialize();
   }      

   @Override
   public String version() {
      return token.getClass().getPackage().toString();
   }      

   @Override
   public String getKeyID() {
      return token.getHeader().getKeyID();
   }      

   @Override
   public Map getHeader() {
      return token.getHeader().toJSONObject();
   }      

   @Override
   public Map getPayload() {
      return token.getPayload().toJSONObject();
   }         

   @Override
   public long getExpiration() throws Exception {
      return token.getJWTClaimsSet().getExpirationTime().getTime()/1000;
   }

   @Override
   public boolean verify(String StrModulus, String StrExponent) throws Exception {
            
      // Predispone per la verifica della firma
      RSAKey ObjKey = new RSAKey.Builder(Base64URL.from(StrModulus),Base64URL.from(StrExponent))
         .keyUse(KeyUse.SIGNATURE)
         .keyID(token.getHeader().getKeyID())
         .algorithm(token.getHeader().getAlgorithm())
         .build(); 
      
      JWKSet ObjKeySet = new JWKSet(ObjKey);
      JWKSource<SecurityContext> ObjKeySource = new ImmutableJWKSet<SecurityContext>(ObjKeySet);
      JWSKeySelector<SecurityContext> ObjKeySelector = new JWSVerificationKeySelector<>(token.getHeader().getAlgorithm(),ObjKeySource);
            
      // Predispone per la verifica dei claim
      JWTClaimsSetVerifier<SecurityContext> ObjClaimSetVerifier = 
         new DefaultJWTClaimsVerifier<>(
            new JWTClaimsSet.Builder().build(),
            new HashSet<>(Arrays.asList(JWTClaimNames.EXPIRATION_TIME,JWTClaimNames.NOT_BEFORE)));
      
      // Predispone il processore per la verifica del token
      ConfigurableJWTProcessor<SecurityContext> ObjProcessor = new DefaultJWTProcessor<>();
      
      ObjProcessor.setJWSKeySelector(ObjKeySelector);      
      ObjProcessor.setJWTClaimsSetVerifier(ObjClaimSetVerifier);
            
      // Esegue la verifica dei claim
      JWTClaimsSet ObjClaimSet = ObjProcessor.process(token, null);
      
      // Restituisce l'esito della verifica
      return (token.getState() == JWSObject.State.VERIFIED);
   }

   @Override
   public JWTProvider parse(String StrToken) throws Exception {
      return init(SignedJWT.parse(StrToken));
   }      
   
   @Override
   public JWTProvider build(JWTClaimsMap ObjClaims,String StrAlgorithm,String StrKeyID,String StrPrivateKey,String StrPassword) throws Exception {
          
      // Inizializza l'algoritmo            
      JWSAlgorithm ObjAlgorithm = JWSAlgorithm.parse(StrAlgorithm);

      // Costruisce headers del token
      Builder ObjHeaders = new JWSHeader.Builder(ObjAlgorithm).type(JOSEObjectType.JWT);
      if (!StrKeyID.isEmpty()) ObjHeaders.keyID(StrKeyID);
          
      // Inizializza token di asserzione secondo lo standard e in base ai parametri forniti
      SignedJWT ObjToken = new SignedJWT(ObjHeaders.build(),JWTClaimsSet.parse(ObjClaims));
      
      // Esegue firma del token
      ObjToken.sign(buildSigner(ObjAlgorithm,StrPrivateKey,StrPassword));

      // Salva il token nella classe
      return init(ObjToken);
   }
   
   private JWSSigner buildSigner(JWSAlgorithm ObjAlgorithm, String StrPrivateKey,String StrPassword) throws Exception {

       // RSA (RS* + PS*)
       if (JWSAlgorithm.Family.RSA.contains(ObjAlgorithm)) {
           RSAPrivateKey ObjRSAKey = (RSAPrivateKey) CryptoUtils.loadPrivateKey(StrPrivateKey,StrPassword);
           return new RSASSASigner(ObjRSAKey);
       }

       // EC (ES*)
       if (JWSAlgorithm.Family.EC.contains(ObjAlgorithm)) {
           ECPrivateKey ObjECKey = (ECPrivateKey) CryptoUtils.loadPrivateKey(StrPrivateKey, StrPassword);
           return new ECDSASigner(ObjECKey);
       }

       throw new IllegalArgumentException("Unsupported algorithm ("+ObjAlgorithm.getName()+")");
   }
}
