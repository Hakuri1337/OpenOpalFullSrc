package nhcm.bytecodevm.data;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class CompiledMethod
{
    public final ClassNode owner;
    public final MethodNode source;
    public final VMMethod vmMethod;

    public final int codeId;
    public final List<Integer> codeIds;
    public final String descriptor;
    public final boolean isStatic;
    public final boolean virtualizeInstructionAddresses;
    public final BytecodeVMConfig config;

    public CompiledMethod(ClassNode owner, MethodNode source, VMMethod vmMethod, int codeId, String descriptor, boolean isStatic)
    {
        this(owner, source, vmMethod, codeId, List.of(codeId), descriptor, isStatic, true, null);
    }

    public CompiledMethod(
            ClassNode owner,
            MethodNode source,
            VMMethod vmMethod,
            int codeId,
            String descriptor,
            boolean isStatic,
            boolean virtualizeInstructionAddresses)
    {
        this(owner, source, vmMethod, codeId, List.of(codeId), descriptor, isStatic, virtualizeInstructionAddresses, null);
    }

    public CompiledMethod(
            ClassNode owner,
            MethodNode source,
            VMMethod vmMethod,
            int codeId,
            List<Integer> codeIds,
            String descriptor,
            boolean isStatic,
            boolean virtualizeInstructionAddresses)
    {
        this(owner, source, vmMethod, codeId, codeIds, descriptor, isStatic, virtualizeInstructionAddresses, null);
    }

    public CompiledMethod(
            ClassNode owner,
            MethodNode source,
            VMMethod vmMethod,
            int codeId,
            List<Integer> codeIds,
            String descriptor,
            boolean isStatic,
            boolean virtualizeInstructionAddresses,
            BytecodeVMConfig config)
    {
        this.owner = owner;
        this.source = source;
        this.vmMethod = vmMethod;
        this.codeId = codeId;
        this.codeIds = List.copyOf(codeIds);
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.virtualizeInstructionAddresses = virtualizeInstructionAddresses;
        this.config = config;
    }

    public boolean isSegmented()
    {
        return codeIds.size() > 1;
    }
}
