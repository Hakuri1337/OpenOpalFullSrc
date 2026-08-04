package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

public interface VMStructureGenerator
{
    VMStructure structure();

    VMKernelShape kernelShape();

    default int stepBatchSize()
    {
        return 1;
    }

    void emitScheduler(
            VMStructureGenerationContext generation,
            AdvInsnBuilder instructions,
            InterpretContext runtime);
}
