package br.com.castel.sharedkernel;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Time-ordered UUID as defined by RFC 9562, section 5.7.
 *
 * <p>Layout: 48 bits of Unix time in milliseconds, 4 bits of version ({@code 0111}), 12 random bits,
 * 2 bits of variant ({@code 10}) and 62 random bits. There is no monotonic counter, so ordering is
 * guaranteed only between values generated in distinct milliseconds.
 */
final class UuidVersion7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;
    private static final long VERSION_BITS = 0x7000L;
    private static final long RANDOM_A_MASK = 0x0FFFL;
    private static final long VARIANT_BITS = 0x8000_0000_0000_0000L;
    private static final long RANDOM_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private UuidVersion7() {
    }

    static UUID next() {
        long unixMillis = System.currentTimeMillis();
        long mostSignificantBits = ((unixMillis & TIMESTAMP_MASK) << 16)
                | VERSION_BITS
                | (RANDOM.nextLong() & RANDOM_A_MASK);
        long leastSignificantBits = VARIANT_BITS | (RANDOM.nextLong() & RANDOM_B_MASK);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
