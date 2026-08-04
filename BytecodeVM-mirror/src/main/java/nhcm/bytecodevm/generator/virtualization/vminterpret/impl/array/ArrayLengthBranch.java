package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class ArrayLengthBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.ARRAY_LENGTH.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context);
        pushInt(ib, context, AdvInsnBuilder.callStatic(
                "java/lang/reflect/Array",
                "getLength",
                "I",
                context.stackObject()));
    }
}
