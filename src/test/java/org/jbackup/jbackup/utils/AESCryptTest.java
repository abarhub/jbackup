package org.jbackup.jbackup.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AESCryptTest {


    private static final Logger LOGGER = LoggerFactory.getLogger(AESCryptTest.class);

    @Test
    void test1() throws Exception {

        final var s = "abc";
        final var bufS = s.getBytes(StandardCharsets.UTF_8);
        final var password = "abc123";
        var in = new ByteArrayInputStream(bufS);
        var out = new ByteArrayOutputStream();

        var debut = Instant.now();
        AESCrypt crypt = new AESCrypt(false, password);
        crypt.encrypt(2, in, out);

        byte[] buf = out.toByteArray();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
        LOGGER.atInfo().log("buf={}", convertBytesToHex(buf));

        var in2 = new ByteArrayInputStream(buf);
        var out2 = new ByteArrayOutputStream();

        AESCrypt crypt2 = new AESCrypt(false, password);
        crypt2.decrypt(buf.length, in2, out2);

        byte[] buf2 = out2.toByteArray();
        assertNotNull(buf2);
        assertTrue(buf2.length > 0);
        assertArrayEquals(bufS, buf2);
        LOGGER.atInfo().log("buf2={} ({})", convertBytesToHex(buf2), new String(buf2, StandardCharsets.UTF_8));
        LOGGER.atInfo().log("duree={}", Duration.between(debut, Instant.now()));
    }

    @Test
    void testAex2() throws Exception {

        final var s = "abc";
//        final var bufS = s.getBytes(StandardCharsets.UTF_8);
        final var password = "abc123";

        String input = "Java & ChaCha20-Poly1305.";
//        var input0="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
//        input=input0;
//        var size=10_000_000;
//        var len=input0.length()*size;
//        var input2=new StringBuilder();
//        input2.append(input0);
//        for( int i=0;i<size;i++){
//            input2.append(input0);
//        }
//        input=input2.toString();
//        final var bufS=input.getBytes(StandardCharsets.UTF_8);
//        System.out.println("taille="+bufS.length);
        input=getData();
        final var bufS=input.getBytes(StandardCharsets.UTF_8);

        var debut = Instant.now();
        var in = new ByteArrayInputStream(bufS);
        var out = new ByteArrayOutputStream();
        AESCrypt crypt = new AESCrypt(false, password);
        crypt.encrypt(2, in, out);

        byte[] buf = out.toByteArray();
        assertNotNull(buf);
        assertTrue(buf.length > 0);
        //LOGGER.atInfo().log("buf={}", convertBytesToHex(buf));

        var in2 = new ByteArrayInputStream(buf);
        var out2 = new ByteArrayOutputStream();

        AESCrypt crypt2 = new AESCrypt(false, password);
        crypt2.decrypt(buf.length, in2, out2);

        byte[] buf2 = out2.toByteArray();
        assertNotNull(buf2);
        assertTrue(buf2.length > 0);
        assertArrayEquals(bufS, buf2);
        //LOGGER.atInfo().log("buf2={} ({})", convertBytesToHex(buf2), new String(buf2, StandardCharsets.UTF_8));
        LOGGER.atInfo().log("duree={}", Duration.between(debut, Instant.now()));
    }


    // https://mkyong.com/java/java-how-to-convert-bytes-to-hex/
    private static String convertBytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte temp : bytes) {
            result.append(String.format("%02x", temp));
        }
        return result.toString();
    }


    @Test
    void testChacha3() throws Exception {

        TestChaCha20Poly1305.main(null);
    }


    public static class ChaCha20Poly1305 {

        private static final String ENCRYPT_ALGO = "ChaCha20-Poly1305";
        private static final int NONCE_LEN = 12; // 96 bits, 12 bytes

        // if no nonce, generate a random 12 bytes nonce
        public byte[] encrypt(byte[] pText, SecretKey key) throws Exception {
            return encrypt(pText, key, getNonce());
        }

        public byte[] encrypt(byte[] pText, SecretKey key, byte[] nonce) throws Exception {

            Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

            // IV, initialization value with nonce
            IvParameterSpec iv = new IvParameterSpec(nonce);

            cipher.init(Cipher.ENCRYPT_MODE, key, iv);

            byte[] encryptedText = cipher.doFinal(pText);

            // append nonce to the encrypted text
            byte[] output = ByteBuffer.allocate(encryptedText.length + NONCE_LEN)
                    .put(encryptedText)
                    .put(nonce)
                    .array();

            return output;
        }

        public byte[] decrypt(byte[] cText, SecretKey key) throws Exception {

            ByteBuffer bb = ByteBuffer.wrap(cText);

            // split cText to get the appended nonce
            byte[] encryptedText = new byte[cText.length - NONCE_LEN];
            byte[] nonce = new byte[NONCE_LEN];
            bb.get(encryptedText);
            bb.get(nonce);

            Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);

            IvParameterSpec iv = new IvParameterSpec(nonce);

            cipher.init(Cipher.DECRYPT_MODE, key, iv);

            // decrypted text
            byte[] output = cipher.doFinal(encryptedText);

            return output;

        }

        // 96-bit nonce (12 bytes)
        private static byte[] getNonce() {
            byte[] newNonce = new byte[12];
            new SecureRandom().nextBytes(newNonce);
            return newNonce;
        }

    }

    public static String getData(){
        String input = "Java & ChaCha20-Poly1305.";
//        var input0="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
//        input=input0;
        var size=1_000_000_000;
//        input0= StringUtils.repeat('X',size);
//        var len=input0.length()*size;
//        var input2=new StringBuilder();
//        input2.append(input0);
//        for( int i=0;i<size;i++){
//            input2.append(input0);
//        }
//        input=input2.toString();
        input= StringUtils.repeat('X',size);
        System.out.println("taille="+input.length());
        return input;
    }

    public static class TestChaCha20Poly1305 {

        private static final int NONCE_LEN = 12;                    // 96 bits, 12 bytes
        private static final int MAC_LEN = 16;                      // 128 bits, 16 bytes

        public static void main(String[] args) throws Exception {

            String input = "Java & ChaCha20-Poly1305.";
//            var input0="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
//            input=input0;
//            var size=10000_000;
//            var len=input0.length()*size;
//            var input2=new StringBuilder();
//            input2.append(input0);
//            for( int i=0;i<size;i++){
//                input2.append(input0);
//            }
//            input=input2.toString();
//            System.out.println("taille="+input.length());
            input=getData();

            Instant debut=Instant.now();
            ChaCha20Poly1305 cipher = new ChaCha20Poly1305();

            SecretKey key = getKey();                               // 256-bit secret key (32 bytes)

//            System.out.println("Input                  : " + input);
//            System.out.println("Input             (hex): " + convertBytesToHex(input.getBytes()));

            System.out.println("\n---Encryption---");
            byte[] cText = cipher.encrypt(input.getBytes(), key);   // encrypt

            System.out.println("Key               (hex): " + convertBytesToHex(key.getEncoded()));
//            System.out.println("Encrypted         (hex): " + convertBytesToHex(cText));

            System.out.println("\n---Print Mac and Nonce---");

            ByteBuffer bb = ByteBuffer.wrap(cText);

            // This cText contains chacha20 ciphertext + poly1305 MAC + nonce

            // ChaCha20 encrypted the plaintext into a ciphertext of equal length.
            byte[] originalCText = new byte[input.getBytes().length];
            byte[] nonce = new byte[NONCE_LEN];     // 16 bytes , 128 bits
            byte[] mac = new byte[MAC_LEN];         // 12 bytes , 96 bits

            bb.get(originalCText);
            bb.get(mac);
            bb.get(nonce);

//            System.out.println("Cipher (original) (hex): " + convertBytesToHex(originalCText));
            System.out.println("MAC               (hex): " + convertBytesToHex(mac));
            System.out.println("Nonce             (hex): " + convertBytesToHex(nonce));

            System.out.println("\n---Decryption---");
//            System.out.println("Input             (hex): " + convertBytesToHex(cText));

            byte[] pText = cipher.decrypt(cText, key);              // decrypt

            System.out.println("Key               (hex): " + convertBytesToHex(key.getEncoded()));
//            System.out.println("Decrypted         (hex): " + convertBytesToHex(pText));
//            System.out.println("Decrypted              : " + new String(pText));

            System.out.println("duree              : " + Duration.between(debut,Instant.now()));

        }

        // https://mkyong.com/java/java-how-to-convert-bytes-to-hex/
        private static String convertBytesToHex(byte[] bytes) {
            StringBuilder result = new StringBuilder();
            for (byte temp : bytes) {
                result.append(String.format("%02x", temp));
            }
            return result.toString();
        }

        // A 256-bit secret key (32 bytes)
        private static SecretKey getKey() throws NoSuchAlgorithmException {
            KeyGenerator keyGen = KeyGenerator.getInstance("ChaCha20");
            keyGen.init(256, SecureRandom.getInstanceStrong());
            return keyGen.generateKey();
        }

    }


}