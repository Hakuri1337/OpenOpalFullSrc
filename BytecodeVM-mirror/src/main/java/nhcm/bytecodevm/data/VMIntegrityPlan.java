package nhcm.bytecodevm.data;

public record VMIntegrityPlan(
        String owner,
        String methodName,
        String descriptor,
        String stateFieldName,
        CacheLayout cacheLayout,
        String probeMethodName,
        int expectedCapability,
        double ratio)
{
    public record CacheLayout(
            int xorMask,
            int rotation,
            int multiplier,
            int inverseMultiplier,
            int addend,
            int tagSalt,
            int tagRotation,
            int tagMultiplier,
            int failMix)
    {
    }
}
