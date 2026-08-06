package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.constant;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public class PushIntBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.PUSH_INT.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        if (opcode.hasOperand)
        {
            Local value = context.intLocal("intConstant", InterpretContext.RIGHT_VALUE);
            context.nextOperand(ib, value);
            pushNumber(ib, context, NumericType.INT, value);
            return;
        }
        pushNumber(ib, context, NumericType.INT, AdvInsnBuilder.constant(opcode.opcode - Opcodes.ICONST_0));
    }
}
