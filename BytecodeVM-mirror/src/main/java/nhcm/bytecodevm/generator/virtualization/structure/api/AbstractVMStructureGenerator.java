package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.enums.VMStructure;

import java.util.Objects;

public abstract class AbstractVMStructureGenerator implements VMStructureGenerator
{
    private final VMStructure structure;
    private final VMKernelShape kernelShape;

    protected AbstractVMStructureGenerator(VMStructure structure)
    {
        this.structure = Objects.requireNonNull(structure, "structure");
        if (structure.isAutomatic())
        {
            throw new IllegalArgumentException("Automatic VM structure must be resolved first: " + structure);
        }
        this.kernelShape = VMKernelShape.forStructure(structure);
    }

    @Override
    public final VMStructure structure()
    {
        return structure;
    }

    @Override
    public final VMKernelShape kernelShape()
    {
        return kernelShape;
    }
}
