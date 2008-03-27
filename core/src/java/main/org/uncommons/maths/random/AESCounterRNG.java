// ============================================================================
//   Copyright 2006, 2007 Daniel W. Dyer
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
// ============================================================================
package org.uncommons.maths.random;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Random;
import javax.crypto.Cipher;
import org.uncommons.maths.binary.BinaryUtils;

/**
 * Non-linear random number generator based on the AES block cipher in counter mode.
 * Uses the seed as a key to encrypt a 128-bit counter using AES(Rijndael).
 * By default we only use a 128-bit key for the cipher because any larger key requires
 * the inconvenience of installing the unlimited strength cryptography policy
 * files for the Java platform.  Larger keys can be used (192 or 256 bits) but if the
 * cryptography policy files are not installed, a
 * {@link java.security.GeneralSecurityException} will be thrown.
 * @author Daniel Dyer
 */
public class AESCounterRNG extends Random implements RepeatableRNG
{
    private static final int DEFAULT_SEED_SIZE_BYTES = 16;

    private final byte[] seed;
    private final Cipher cipher; // TO DO: This field is not Serializable.
    private final byte[] counter = new byte[16]; // 128-bit counter.

    // Lock to prevent concurrent modification of the RNG's internal state.
    private final Object lock = new Object();

    private byte[] currentBlock = null;
    private int index = 0;


    /**
     * Creates a new RNG and seeds it using 128 bits from the default seeding strategy.
     * @throws GeneralSecurityException If there is a problem initialising the AES cipher.
     */
    public AESCounterRNG() throws GeneralSecurityException
    {
        this(DEFAULT_SEED_SIZE_BYTES);
    }


    /**
     * Seed the RNG using the provided seed generation strategy to create a 128-bit
     * seed.
     * @param seedGenerator The seed generation strategy that will provide
     * the seed value for this RNG.
     * @throws SeedException If there is a problem generating a seed.
     * @throws GeneralSecurityException If there is a problem initialising the AES cipher.
     */
    public AESCounterRNG(SeedGenerator seedGenerator) throws SeedException,
                                                             GeneralSecurityException
    {
        this(seedGenerator.generateSeed(DEFAULT_SEED_SIZE_BYTES));
    }


    /**
     * Seed the RNG using the default seed generation strategy to create a seed of the
     * specified size.
     * @param seedSizeBytes The number of bytes to use for seed data.  Valid values
     * are 16 (128 bits), 24 (192 bits) and 32 (256 bits).  Any other values will
     * result in an exception from the AES implementation.
     * @throws GeneralSecurityException If there is a problem initialising the AES cipher.
     * @since 1.0.2
     */
    public AESCounterRNG(int seedSizeBytes) throws GeneralSecurityException
    {
        this(DefaultSeedGenerator.getInstance().generateSeed(seedSizeBytes));
    }


    /**
     * Creates an RNG and seeds it with the specified seed data.
     * @param seed The seed data used to initialise the RNG.
     * @throws GeneralSecurityException If there is a problem initialising the AES cipher.
     */
    public AESCounterRNG(byte[] seed) throws GeneralSecurityException
    {
        if (seed == null)
        {
            throw new IllegalArgumentException("AES RNG requires a 128-bit, 192-bit or 256-bit seed.");
        }
        this.seed = seed.clone();

        cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new AESKey(this.seed));

        // Always log seed so that an indentical RNG can be created later if necessary.
        System.out.println("AES RNG created with seed " + BinaryUtils.convertBytesToHexString(seed));
    }


    /**
     * {@inheritDoc}
     */
    public byte[] getSeed()
    {
        return seed.clone();
    }


    private void incrementCounter()
    {
        for (int i = 0; i < counter.length; i++)
        {
            ++counter[i];
            if (counter[i] != 0) // Check whether we need to loop again to carry the one.
            {
                break;
            }
        }
    }


    /**
     * Generates a single 128-bit block (16 bytes).
     * @throws GeneralSecurityException If there is a problem with the cipher
     * that generates the random data.
     * @return A 16-byte block of random data.
     */
    private byte[] nextBlock() throws GeneralSecurityException
    {
        incrementCounter();
        return cipher.doFinal(counter);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected final int next(int bits)
    {
        int result;
        synchronized (lock)
        {
            if (currentBlock == null || currentBlock.length - index < 4)
            {
                try
                {
                    currentBlock = nextBlock();
                    index = 0;
                }
                catch (GeneralSecurityException ex)
                {
                    // Should never happen.  If initialisation succeeds without exceptions
                    // we should be able to proceed indefinitely without exceptions.
                    throw new IllegalStateException("Failed creating next random block.", ex);
                }
            }
            result = BinaryUtils.convertBytesToInt(currentBlock, index);
            index += 4;
        }
        return result >>> (32 - bits);
    }



    /**
     * Trivial key implementation for use with AES cipher.
     */
    private static final class AESKey implements Key
    {
        private final byte[] keyData;

        public AESKey(byte[] keyData)
        {
            this.keyData = keyData;
        }

        public String getAlgorithm()
        {
            return "AES";
        }

        public String getFormat()
        {
            return "RAW";
        }

        public byte[] getEncoded()
        {
            return keyData;
        }
    }
}
