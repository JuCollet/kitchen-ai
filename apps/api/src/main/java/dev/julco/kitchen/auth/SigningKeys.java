package dev.julco.kitchen.auth;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads the RSA key that signs access tokens. The key itself is never logged. */
final class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

    private SigningKeys() {}

    static RSAKey load(String pem) {
        if (pem == null || pem.isBlank()) {
            log.warn("No kitchen.oauth.signing-key configured: using a temporary key. "
                    + "AI clients will have to reconnect after each restart. Never do this in production.");
            return generate();
        }
        try {
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey)
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return withThumbprintKeyId(new RSAKey.Builder(publicKey).privateKey(privateKey).build());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException | ClassCastException e) {
            // The message would not contain the key, but stay on the safe side.
            throw new IllegalStateException("kitchen.oauth.signing-key is not a valid PKCS#8 RSA private key");
        }
    }

    private static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var keyPair = generator.generateKeyPair();
            return withThumbprintKeyId(new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .build());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is always available", e);
        }
    }

    /** A stable key id derived from the public key, so that a restart does not change it. */
    private static RSAKey withThumbprintKeyId(RSAKey key) {
        try {
            return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Cannot compute the key thumbprint", e);
        }
    }
}
