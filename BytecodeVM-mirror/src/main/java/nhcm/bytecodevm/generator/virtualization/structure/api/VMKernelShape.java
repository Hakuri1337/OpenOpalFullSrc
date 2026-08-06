package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.enums.VMStructure;

public record VMKernelShape(
        DecodeOrder decodeOrder,
        ExceptionTableMode exceptionTableMode)
{
    public enum DecodeOrder
    {
        OPCODE_NEXT_ORIGINAL,
        OPCODE_ORIGINAL_NEXT,
        NEXT_OPCODE_ORIGINAL,
        NEXT_ORIGINAL_OPCODE,
        ORIGINAL_OPCODE_NEXT,
        ORIGINAL_NEXT_OPCODE
    }

    public enum ExceptionTableMode
    {
        EAGER,
        PER_STEP,
        ON_THROW
    }

    public static VMKernelShape forStructure(VMStructure structure)
    {
        if (structure.isAutomatic())
        {
            throw new IllegalArgumentException("Automatic structure has no kernel shape: " + structure);
        }
        int ordinal = structure.ordinal();
        DecodeOrder[] decodeOrders = DecodeOrder.values();
        ExceptionTableMode[] exceptionModes = ExceptionTableMode.values();
        return new VMKernelShape(
                decodeOrders[ordinal % decodeOrders.length],
                exceptionModes[ordinal / decodeOrders.length % exceptionModes.length]);
    }
}
