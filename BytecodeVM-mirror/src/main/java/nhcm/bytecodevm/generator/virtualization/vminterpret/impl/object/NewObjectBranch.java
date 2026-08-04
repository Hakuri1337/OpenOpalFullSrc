package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.object;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class NewObjectBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_OBJECT.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var classIndex = context.intLocal("classIndex", InterpretContext.JUMP_TARGET);
        var marker = context.local("identityMarker", "[Ljava/lang/Object;", InterpretContext.FIELD_VALUE);

        context.nextOperand(ib, classIndex);
        ib.set(marker, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(1)));
        ib.setArray(marker, AdvInsnBuilder.constant(0), AdvInsnBuilder.arrayAt(context.constants(), classIndex));
        pushObject(ib, context, marker);
    }
}
