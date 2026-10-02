package com.campusfind.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** AES-GCM authenticates each encrypted field; the installation key is kept outside source control. */
@Service
public class CryptoService {
    private final SecretKeySpec key;
    private final SecureRandom random=new SecureRandom();
    public CryptoService(@Value("${campus.crypto-key-file:.local/encryption.key}") String keyFile) {
        try {
            Path path=Path.of(keyFile).toAbsolutePath();
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                byte[] bytes=new byte[32]; random.nextBytes(bytes);
                try { Files.writeString(path,Base64.getEncoder().encodeToString(bytes),StandardOpenOption.CREATE_NEW); }
                catch (FileAlreadyExistsException ignored) { /* A concurrent process created the same installation key. */ }
            }
            byte[] bytes=Base64.getDecoder().decode(Files.readString(path).trim());
            if (bytes.length!=32) throw new IllegalStateException("Encryption key must contain 32 bytes");
            key=new SecretKeySpec(bytes,"AES");
        } catch (Exception ex) { throw new IllegalStateException("Unable to read or create the private installation key",ex); }
    }
    public String encrypt(String plaintext) {
        if (plaintext==null || plaintext.isBlank()) return "";
        try {
            byte[] iv=new byte[12]; random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined=new byte[iv.length+encrypted.length]; System.arraycopy(iv,0,combined,0,iv.length); System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
            return "v1:"+Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Encryption unavailable",ex); }
    }
    public String decrypt(String encrypted) {
        if (encrypted==null || encrypted.isBlank()) return "";
        try {
            if (!encrypted.startsWith("v1:")) throw new IllegalArgumentException("Invalid encrypted value");
            byte[] bytes=Base64.getDecoder().decode(encrypted.substring(3));
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Protected information could not be decrypted",ex); }
    }
    /** Keyed hashes protect short OTPs against offline guessing if a database is exposed. */
    public String hash(String value) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getEncoded(),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public boolean matches(String value,String hash) {
        return value!=null && hash!=null && MessageDigest.isEqual(hash(value).getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII));
    }
    public String token() { byte[] bytes=new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
