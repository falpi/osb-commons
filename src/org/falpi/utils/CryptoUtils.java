package org.falpi.utils;

import java.io.StringReader;
import java.security.*;

import org.bouncycastle.openssl.*;
import org.bouncycastle.openssl.jcajce.*;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class CryptoUtils {

   // ==================================================================================================================================
   // Codice di inizializzazione statica
   // ==================================================================================================================================
   static {

      // Registra il provider BouncyCastle una volta sola
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
         Security.addProvider(new BouncyCastleProvider());
      }
   }

   // --------------------------------------------------------------------- //
   //  Caricamento della chiave privata da PEM (cifrato o meno)             //
   //                                                                       //
   //  Formati gestiti:                                                     //
   //    PEMKeyPair                ? OpenSSL tradizionale non cifrato      //
   //                                (BEGIN RSA/EC PRIVATE KEY)             //
   //    PEMEncryptedKeyPair       ? OpenSSL tradizionale cifrato          //
   //                                (BEGIN RSA/EC PRIVATE KEY + DEK-Info)  //
   //    PrivateKeyInfo            ? PKCS#8 non cifrato                    //
   //                                (BEGIN PRIVATE KEY)                    //
   //    PKCS8EncryptedPrivateKeyInfo ? PKCS#8 cifrato                     //
   //                                (BEGIN ENCRYPTED PRIVATE KEY)          //
   // --------------------------------------------------------------------- //
   public static PrivateKey loadPrivateKey(String StrPemObject, String StrPassword) throws Exception {

      try (PEMParser ObjParser = new PEMParser(new StringReader(StrPemObject))) {
         Object ObjPemObject = ObjParser.readObject();
         if (ObjPemObject == null) {
            throw new IllegalArgumentException("Invalid PEM object");
         }

         JcaPEMKeyConverter ObjConverter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);

         // OpenSSL legacy plain
         if (ObjPemObject instanceof PEMKeyPair) {
            unrequirePassword(StrPassword);
            return ObjConverter.getPrivateKey(((PEMKeyPair) ObjPemObject).getPrivateKeyInfo());
         }

         // OpenSSL legacy encrypted
         if (ObjPemObject instanceof PEMEncryptedKeyPair) {
            requirePassword(StrPassword);
            PEMDecryptorProvider ObjDecryptor =
               new JcePEMDecryptorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(StrPassword.toCharArray());
            PEMKeyPair keyPair = ((PEMEncryptedKeyPair) ObjPemObject).decryptKeyPair(ObjDecryptor);
            return ObjConverter.getPrivateKey(keyPair.getPrivateKeyInfo());
         }

         // PKCS#8 plain
         if (ObjPemObject instanceof PrivateKeyInfo) {
            unrequirePassword(StrPassword);
            return ObjConverter.getPrivateKey((PrivateKeyInfo) ObjPemObject);
         }

         // PKCS#8 encrypted
         if (ObjPemObject instanceof PKCS8EncryptedPrivateKeyInfo) {
            requirePassword(StrPassword);
            InputDecryptorProvider ObjDecryptor =
               new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(StrPassword.toCharArray());
            PrivateKeyInfo keyInfo = ((PKCS8EncryptedPrivateKeyInfo) ObjPemObject).decryptPrivateKeyInfo(ObjDecryptor);
            return ObjConverter.getPrivateKey(keyInfo);
         }

         throw new IllegalArgumentException("Usupported PEM format ("+ObjPemObject.getClass().getSimpleName()+")");
      }
   }

   private static void requirePassword(String StrPassword) {
      if ((StrPassword==null)||(StrPassword.isEmpty())) {
         throw new IllegalArgumentException("Encrypted PEM object require password");
      }
   }
   
   private static void unrequirePassword(String StrPassword) {
      if ((StrPassword!=null)&&(!StrPassword.isEmpty())) {
         throw new IllegalArgumentException("Plain PEM object do not require password");
      }
   }
}
