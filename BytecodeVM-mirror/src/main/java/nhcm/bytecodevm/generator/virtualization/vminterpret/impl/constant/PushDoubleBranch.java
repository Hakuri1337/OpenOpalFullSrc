package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.constant;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public class PushDoubleBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.PUSH_DOUBLE.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        pushNumber(ib, context, NumericType.DOUBLE, AdvInsnBuilder.constant((double) (opcode.opcode - Opcodes.DCONST_0)));
    }
}
