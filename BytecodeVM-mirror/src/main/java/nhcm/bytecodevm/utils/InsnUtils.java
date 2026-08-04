package nhcm.bytecodevm.utils;

import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.utils.builder.InsnBuilder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class InsnUtils
{
    public static ClassNode addPrivateInit(ClassNode classnode)
    {
        classnode.methods.add(newPrivateInit());
        return classnode;
    }

    public static MethodNode newPrivateInit()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE}, "<init>", "()V");
        InsnBuilder ib = new InsnBuilder();
        ib.aload(0);
        ib.invokeSpecial("java/lang/Object", "<init>", "()V");
        ib._return();
        methodNode.instructions.add(ib.toInsnList());
        return methodNode;
    }
}
