package nhcm.bytecodevm.advInsn;

import nhcm.bytecodevm.utils.builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

record SimpleCondition(String source, JumpEmitter emitter) implements Condition
{
    @Override
    public void jumpIfFalse(InsnBuilder builder, LabelNode falseLabel)
    {
        emitter.jumpIfFalse(builder, falseLabel);
    }
}
