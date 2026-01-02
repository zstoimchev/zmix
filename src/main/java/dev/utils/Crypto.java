package dev.utils;

import dev.models.Message;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

public class Crypto {
    private final PublicKey pub;
    private final PrivateKey pvt;

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    public Crypto() {
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = g.generateKeyPair();
            this.pub = keyPair.getPublic();
            this.pvt = keyPair.getPrivate();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Failed to generate EC key pair", e);
        }
    }

    // ==================== SIGNING ====================

    public Message signMessage(Message message) {
        try {
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
            ecdsaSign.initSign(pvt);
            String payloadString = message.getPayload() == null ? "" : message.getPayload().toString();
            ecdsaSign.update(payloadString.getBytes(StandardCharsets.UTF_8));
            byte[] signature = ecdsaSign.sign();

            message.setSignature(Base64.getEncoder().encodeToString(signature));
            return message;
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign message", e);
        }
    }

    public static boolean verifyMessage(Message message) {
        try {
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
            ecdsaVerify.update(message.getPayload().toString().getBytes(StandardCharsets.UTF_8));
            return ecdsaVerify.verify(Base64.getDecoder().decode(message.getSignature()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify message", e);
        }
    }

    // ==================== ECDH KEY AGREEMENT ====================

    /**
     * Generate a new ephemeral EC key pair for ECDH
     */
    public KeyPair generateECDHKeyPair() {
        try {
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(ecSpec, new SecureRandom());
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Failed to generate ECDH key pair", e);
        }
    }

    public PublicKey decodePublicKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            KeyFactory kf = KeyFactory.getInstance("EC");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode EC public key", e);
        }
    }

    /**
     * Perform ECDH to derive shared secret
     * @param privateKey Our private key
     * @param publicKey Their public key
     * @return Shared secret bytes
     */
    public byte[] performECDH(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            return keyAgreement.generateSecret();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to perform ECDH", e);
        }
    }

    /**
     * Derive AES-256 key from shared secret using HKDF (simplified with SHA-256)
     * @param sharedSecret Shared secret from ECDH
     * @return 32-byte AES key
     */
    public byte[] deriveAESKey(byte[] sharedSecret) {
        try {
            // Use SHA-256 as a simple KDF
            // In production, use proper HKDF
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sharedSecret);

            // Return 32 bytes for AES-256
            return Arrays.copyOf(hash, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to derive AES key", e);
        }
    }

    // ==================== AES ENCRYPTION/DECRYPTION ====================

    /**
     * Encrypt data using AES-256-GCM
     * @param plaintext Data to encrypt
     * @param key 32-byte AES key
     * @return Encrypted data (IV + ciphertext + tag)
     */
    public byte[] encryptAES(byte[] plaintext, byte[] key) {
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Combine IV + ciphertext (GCM tag is included in ciphertext)
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return buffer.array();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException |
                 IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to encrypt with AES", e);
        }
    }

    /**
     * Decrypt data using AES-256-GCM
     * @param encrypted Encrypted data (IV + ciphertext + tag)
     * @param key 32-byte AES key
     * @return Decrypted plaintext
     */
    public byte[] decryptAES(byte[] encrypted, byte[] key) {
        try {
            // Extract IV and ciphertext
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt
            return cipher.doFinal(ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException |
                 IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to decrypt with AES", e);
        }
    }

    // ==================== GETTERS ====================

    public PublicKey getPublicKey() {
        return pub;
    }

    public PrivateKey getPrivateKey() {
        return pvt;
    }
}