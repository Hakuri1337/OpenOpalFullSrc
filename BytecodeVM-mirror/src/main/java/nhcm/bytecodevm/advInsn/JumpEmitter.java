package nhcm.bytecodevm.advInsn;

import nhcm.bytecodevm.utils.builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

@FunctionalInterface
interface JumpEmitter
{
    void jumpIfFalse(InsnBuilder builder, LabelNode falseLabel);
}
