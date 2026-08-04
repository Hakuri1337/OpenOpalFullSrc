package nhcm.bytecodevm.generator.integrity;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.utils.RandomUtils;

/** Emits the authenticated, reversible integrity-state envelope. */
final class IntegrityCacheCodec
{
    private IntegrityCacheCodec()
    {
    }

    static VMIntegrityPlan.CacheLayout randomLayout()
    {
        int multiplier = nonZeroRandom() | 1;
        return new VMIntegrityPlan.CacheLayout(
                nonZeroRandom(),
                5 + RandomUtils.randomInt(22),
                multiplier,
                inverseOdd(multiplier),
                nonZeroRandom(),
                nonZeroRandom(),
                5 + RandomUtils.randomInt(22),
                nonZeroRandom() | 1,
                nonZeroRandom());
    }

    static Local emitEncode(
            AdvInsnBuilder ib,
            Expr capability,
            VMIntegrityPlan.CacheLayout layout,
            String prefix)
    {
        Local encoded = ib.var(prefix + "Encoded", "I");
        Local tag = ib.var(prefix + "Tag", "I");
        Local envelope = ib.var(prefix + "Envelope", "J");
        ib.set(encoded, AdvInsnBuilder.plus(
                AdvInsnBuilder.multiply(
                        rotateLeft(
                                AdvInsnBuilder.bitXor(
                                        capability,
                                        AdvInsnBuilder.constant(layout.xorMask())),
                                layout.rotation()),
                        AdvInsnBuilder.constant(layout.multiplier())),
                AdvInsnBuilder.constant(layout.addend())));
        ib.set(tag, tag(encoded, capability, layout));
        ib.set(envelope, AdvInsnBuilder.bitOr(
                AdvInsnBuilder.shiftLeft(
                        AdvInsnBuilder.cast(encoded, "J"),
                        AdvInsnBuilder.constant(32)),
                AdvInsnBuilder.bitAnd(
                        AdvInsnBuilder.cast(
                                AdvInsnBuilder.bitOr(
                                        AdvInsnBuilder.shiftLeft(tag, AdvInsnBuilder.constant(1)),
                                        AdvInsnBuilder.constant(1)),
                                "J"),
                        AdvInsnBuilder.constant(0xFFFF_FFFFL))));
        return envelope;
    }

    static Local emitDecode(
            AdvInsnBuilder ib,
            Expr envelope,
            VMIntegrityPlan.CacheLayout layout,
            String prefix)
    {
        Local encoded = ib.var(prefix + "Encoded", "I");
        Local metadata = ib.var(prefix + "Metadata", "I");
        Local capability = ib.var(prefix + "Capability", "I");
        Local expectedTag = ib.var(prefix + "ExpectedTag", "I");
        Local mismatch = ib.var(prefix + "Mismatch", "I");

        ib.set(encoded, AdvInsnBuilder.cast(
                AdvInsnBuilder.unsignedShiftRight(envelope, AdvInsnBuilder.constant(32)),
                "I"));
        ib.set(metadata, AdvInsnBuilder.cast(envelope, "I"));
        Expr unscaled = AdvInsnBuilder.multiply(
                AdvInsnBuilder.minus(encoded, AdvInsnBuilder.constant(layout.addend())),
                AdvInsnBuilder.constant(layout.inverseMultiplier()));
        ib.set(capability, AdvInsnBuilder.bitXor(
                rotateRight(unscaled, layout.rotation()),
                AdvInsnBuilder.constant(layout.xorMask())));
        ib.set(expectedTag, tag(encoded, capability, layout));
        ib.set(mismatch, AdvInsnBuilder.bitOr(
                AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.unsignedShiftRight(metadata, AdvInsnBuilder.constant(1)),
                        expectedTag),
                AdvInsnBuilder.bitXor(
                        AdvInsnBuilder.bitAnd(metadata, AdvInsnBuilder.constant(1)),
                        AdvInsnBuilder.constant(1))));
        Expr nonZero = AdvInsnBuilder.unsignedShiftRight(
                AdvInsnBuilder.bitOr(mismatch, AdvInsnBuilder.negative(mismatch)),
                AdvInsnBuilder.constant(31));
        ib.set(capability, AdvInsnBuilder.bitXor(
                capability,
                AdvInsnBuilder.multiply(nonZero, AdvInsnBuilder.constant(layout.failMix()))));
        return capability;
    }

    private static Expr tag(
            Expr encoded,
            Expr capability,
            VMIntegrityPlan.CacheLayout layout)
    {
        Expr mixed = AdvInsnBuilder.bitXor(
                encoded,
                rotateLeft(
                        AdvInsnBuilder.plus(capability, AdvInsnBuilder.constant(layout.tagSalt())),
                        layout.tagRotation()));
        mixed = AdvInsnBuilder.bitXor(
                mixed,
                AdvInsnBuilder.unsignedShiftRight(mixed, AdvInsnBuilder.constant(16)));
        mixed = AdvInsnBuilder.multiply(mixed, AdvInsnBuilder.constant(layout.tagMultiplier()));
        mixed = AdvInsnBuilder.bitXor(
                mixed,
                AdvInsnBuilder.unsignedShiftRight(mixed, AdvInsnBuilder.constant(13)));
        return AdvInsnBuilder.bitAnd(mixed, AdvInsnBuilder.constant(Integer.MAX_VALUE));
    }

    private static Expr rotateLeft(Expr value, int distance)
    {
        return AdvInsnBuilder.bitOr(
                AdvInsnBuilder.shiftLeft(value, AdvInsnBuilder.constant(distance)),
                AdvInsnBuilder.unsignedShiftRight(value, AdvInsnBuilder.constant(32 - distance)));
    }

    private static Expr rotateRight(Expr value, int distance)
    {
        return AdvInsnBuilder.bitOr(
                AdvInsnBuilder.unsignedShiftRight(value, AdvInsnBuilder.constant(distance)),
                AdvInsnBuilder.shiftLeft(value, AdvInsnBuilder.constant(32 - distance)));
    }

    private static int inverseOdd(int value)
    {
        int inverse = value;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        inverse *= 2 - value * inverse;
        return inverse;
    }

    private static int nonZeroRandom()
    {
        int value;
        do
        {
            value = RandomUtils.randomInt();
        } while (value == 0);
        return value;
    }
}
