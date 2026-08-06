package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.control;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class InstanceofBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INSTANCE_OF.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var classIndex = context.intLocal("classIndex", InterpretContext.JUMP_TARGET);
        var targetClass = context.local("targetClass", "java/lang/Class", InterpretContext.FIELD_VALUE);
        context.nextOperand(ib, classIndex);
        ib.set(targetClass, context.loadClass(context.constantString(classIndex)));

        popObject(ib, context);
        pushInt(ib, context, AdvInsnBuilder.callVirtual(
                targetClass,
                "java/lang/Class",
                "isInstance",
                "Z",
                AdvInsnBuilder.cast(context.stackObject(), "java/lang/Object")));
    }
}
