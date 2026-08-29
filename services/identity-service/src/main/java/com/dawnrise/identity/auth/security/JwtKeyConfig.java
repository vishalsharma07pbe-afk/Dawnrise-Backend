package com.dawnrise.identity.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAPrivateKey rsaPrivateKey(JwtProperties properties) {
        try {
            String key = properties.getPrivateKey()
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decodedKey = Base64.getDecoder().decode(key);

            PKCS8EncodedKeySpec keySpec =
                    new PKCS8EncodedKeySpec(decodedKey);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);

        } catch (
                IOException |
                NoSuchAlgorithmException |
                InvalidKeySpecException |
                IllegalArgumentException exception
        ) {
            throw new IllegalStateException(
                    "Unable to load the JWT private key",
                    exception
            );
        }
    }

    @Bean
    public RSAPublicKey rsaPublicKey(JwtProperties properties) {
        try {
            String key = properties.getPublicKey()
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decodedKey = Base64.getDecoder().decode(key);

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(decodedKey);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return (RSAPublicKey) keyFactory.generatePublic(keySpec);

        } catch (
                IOException |
                NoSuchAlgorithmException |
                InvalidKeySpecException |
                IllegalArgumentException exception
        ) {
            throw new IllegalStateException(
                    "Unable to load the JWT public key",
                    exception
            );
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey
    ) {
        return NimbusJwtEncoder
                .withKeyPair(publicKey, privateKey)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();
    }
}