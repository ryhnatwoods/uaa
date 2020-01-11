package org.cloudfoundry.identity.uaa.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

@Component
public class UaaRandomizer {

    private static final char[] CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom secureRandom;

    public UaaRandomizer() throws NoSuchProviderException, NoSuchAlgorithmException {
        secureRandom = SecureRandom.getInstance("SHA1PRNG", "SUN");
        secureRandom.setSeed(secureRandom.generateSeed(1024));
    }

    public String getSecureRandom(final int length) {
        return RandomStringUtils.random(
                10,
                0,
                CHARS.length,
                true,
                true,
                CHARS,
                secureRandom);
    }

}
