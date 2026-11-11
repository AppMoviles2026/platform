package com.collabtech.platform.billing.infrastructure.security;
import com.collabtech.platform.billing.application.ports.BillingPorts.Secrets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
/** Technical encryption boundary; secret values never participate in logs or response DTOs. */
public final class BillingSecrets implements Secrets {
    private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();private final ObjectMapper json;
    public BillingSecrets(String base64,ObjectMapper json){byte[] bytes=Base64.getDecoder().decode(base64);
        if(bytes.length!=32)throw new IllegalArgumentException("Billing encryption requires a stable 32-byte key");key=new SecretKeySpec(bytes,"AES");this.json=json;}
    public String random(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public String digest(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    public String fingerprint(Object input){return digest(json.writeValueAsString(canonical(input)));}
    private Object canonical(Object value){
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(this::canonical).toList();return value;
    }
    public String encrypt(String value){try{byte[] nonce=new byte[12];random.nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] result=Arrays.copyOf(nonce,nonce.length+encrypted.length);System.arraycopy(encrypted,0,result,nonce.length,encrypted.length);return Base64.getEncoder().encodeToString(result);
    }catch(GeneralSecurityException error){throw new IllegalStateException("Billing encryption failed");}}
    public String decrypt(String value){try{byte[] bytes=Base64.getDecoder().decode(value);if(bytes.length<28)throw new GeneralSecurityException();
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,bytes,0,12));
        return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
    }catch(GeneralSecurityException|IllegalArgumentException error){throw new IllegalStateException("Billing decryption failed");}}
}
