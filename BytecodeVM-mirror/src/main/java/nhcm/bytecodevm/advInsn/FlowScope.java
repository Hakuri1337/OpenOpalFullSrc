package nhcm.bytecodevm.advInsn;

import org.objectweb.asm.tree.LabelNode;

record FlowScope(LabelNode continueLabel, LabelNode breakLabel)
{
}
