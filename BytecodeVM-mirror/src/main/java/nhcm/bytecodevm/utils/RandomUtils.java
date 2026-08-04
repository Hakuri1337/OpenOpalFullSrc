package nhcm.bytecodevm.utils;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RandomUtils
{
    private static volatile Random random = new SecureRandom();

    private RandomUtils()
    {
    }

    /** Uses a deterministic source for reproducible debugging builds. */
    public static void useSeed(long seed)
    {
        random = new Random(seed);
    }

    /** Restores non-deterministic generation for normal protection builds. */
    public static void useSecureRandom()
    {
        random = new SecureRandom();
    }

    public static int randomInt()
    {
        return random.nextInt();
    }

    public static int randomInt(int bound)
    {
        if (bound <= 0)
        {
            throw new IllegalArgumentException("bound must be positive");
        }

        return random.nextInt(bound);
    }

    public static int randomInt(int min, int max)
    {
        if (min > max)
        {
            throw new IllegalArgumentException("min > max");
        }

        if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE)
        {
            return randomInt();
        }

        return random.nextInt(max - min + 1) + min;
    }

    public static boolean randomBoolean()
    {
        return random.nextBoolean();
    }

    public static double randomDouble()
    {
        return random.nextDouble();
    }

    public static <T> void shuffle(List<T> list)
    {
        Collections.shuffle(list, random);
    }
}
