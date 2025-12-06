package dev.utils;

import dev.message.Message;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class Crypto {
    private final PublicKey pub;
    private final PrivateKey pvt;

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

    public Message signMessage(Message message) {
        try {
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
            ecdsaSign.initSign(pvt);
            String payloadString = message.getPayload() == null ? "" : message.getPayload().toString();
            ecdsaSign.update(payloadString.getBytes(StandardCharsets.UTF_8));
            byte[] signature = ecdsaSign.sign();

            message.setSignature(Base64.getEncoder().encodeToString(signature));
            message.setSenderPublicKey(Base64.getEncoder().encodeToString(pub.getEncoded()));
            return message;
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign message", e);
        }
    }

    public static boolean verifyMessage(Message message) {
        try {
            byte[] pubBytes = Base64.getDecoder().decode(message.getSenderPublicKey());
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pubBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(message.getPayload().toString().getBytes(StandardCharsets.UTF_8));

            return ecdsaVerify.verify(Base64.getDecoder().decode(message.getSignature()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify message", e);
        }
    }

    public PublicKey getPublicKey() { return pub; }
    public PrivateKey getPrivateKey() { return pvt; }
}
