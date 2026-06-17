package io.docpilot.infrastructure.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class DefaultUserPasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT = "pbkdf2_sha256";
    private static final int DEFAULT_ITERATIONS = 600000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String pepper;
    private final int iterations;

    public DefaultUserPasswordHasher() {
        this("", DEFAULT_ITERATIONS);
    }

    public DefaultUserPasswordHasher(String pepper, int iterations) {
        if (iterations < 100000) {
            throw new IllegalArgumentException("Password iterations must be at least 100000");
        }
        this.pepper = pepper == null ? "" : pepper;
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] digest = pbkdf2(password, salt, iterations);
        return FORMAT + "$" + iterations + "$" + encode(salt) + "$" + encode(digest);
    }

    public boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !FORMAT.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = decode(parts[2]);
            byte[] expected = decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        char[] passwordChars = passwordWithPepper(password);
        try {
            KeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private char[] passwordWithPepper(String password) {
        return (password + pepper).toCharArray();
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.US_ASCII));
    }

}
