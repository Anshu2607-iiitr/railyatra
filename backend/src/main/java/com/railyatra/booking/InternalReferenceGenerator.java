package com.railyatra.booking;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates opaque RailYatra references; it never generates or imitates an official railway PNR. */
@Component
public class InternalReferenceGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    // 20 base-32 characters provide approximately 100 bits of unpredictable server-side entropy.
    public String next() {
        StringBuilder value = new StringBuilder("RY");
        for (int index = 0; index < 20; index++) value.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return value.toString();
    }
}
