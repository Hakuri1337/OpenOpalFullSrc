package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.enums.Opcs;

public record VMDispatchTarget(
        int key,
        int primaryKey,
        Opcs opcode,
        int handlerGroup,
        int handlerIndex,
        String handlerName,
        String handlerDescriptor)
{
}
