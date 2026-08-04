package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.generator.virtualization.VMObfProfile;
import nhcm.bytecodevm.generator.virtualization.structure.VMStructurePlan;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;
import java.util.Objects;

public final class VMDispatchGenerationContext
{
    @FunctionalInterface
    public interface TargetEmitter
    {
        void emit(
                AdvInsnBuilder instructions,
                InterpretContext runtime,
                VMDispatchTarget target,
                Expr instructionIndex);
    }

    @FunctionalInterface
    public interface SelectorEmitter
    {
        void emit(AdvInsnBuilder instructions, InterpretContext runtime, Local selector);
    }

    private final VMStructureGenerationContext generation;
    private final AdvInsnBuilder instructions;
    private final InterpretContext runtime;
    private final List<VMDispatchTarget> targets;
    private final LabelNode completed;
    private final LabelNode unknown;
    private final TargetEmitter targetEmitter;
    private final SelectorEmitter selectorEmitter;
    private final String dispatchDescriptor;

    public VMDispatchGenerationContext(
            VMStructureGenerationContext generation,
            AdvInsnBuilder instructions,
            InterpretContext runtime,
            List<VMDispatchTarget> targets,
            LabelNode completed,
            LabelNode unknown,
            TargetEmitter targetEmitter,
            SelectorEmitter selectorEmitter,
            String dispatchDescriptor)
    {
        this.generation = Objects.requireNonNull(generation, "generation");
        this.instructions = Objects.requireNonNull(instructions, "instructions");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.targets = List.copyOf(targets);
        this.completed = Objects.requireNonNull(completed, "completed");
        this.unknown = Objects.requireNonNull(unknown, "unknown");
        this.targetEmitter = Objects.requireNonNull(targetEmitter, "targetEmitter");
        this.selectorEmitter = Objects.requireNonNull(selectorEmitter, "selectorEmitter");
        this.dispatchDescriptor = Objects.requireNonNull(dispatchDescriptor, "dispatchDescriptor");
    }

    public VMStructureGenerationContext generation()
    {
        return generation;
    }

    public AdvInsnBuilder instructions()
    {
        return instructions;
    }

    public InterpretContext runtime()
    {
        return runtime;
    }

    public List<VMDispatchTarget> targets()
    {
        return targets;
    }

    public LabelNode completed()
    {
        return completed;
    }

    public LabelNode unknown()
    {
        return unknown;
    }

    public VMStructurePlan plan()
    {
        return generation.plan();
    }

    public VMObfProfile profile()
    {
        return generation.profile();
    }

    public String dispatchDescriptor()
    {
        return dispatchDescriptor;
    }

    public void setSelector(AdvInsnBuilder ib, InterpretContext context, Local selector)
    {
        selectorEmitter.emit(ib, context, selector);
    }

    public void emitTarget(
            AdvInsnBuilder ib,
            InterpretContext context,
            VMDispatchTarget target,
            Expr instructionIndex)
    {
        targetEmitter.emit(ib, context, target, instructionIndex);
    }

    public Expr callDispatcher(String name, InterpretContext context)
    {
        return AdvInsnBuilder.callStatic(
                generation.owner(),
                name,
                "I",
                context.program(),
                context.frame(),
                context.code(),
                context.constants(),
                context.opcode(),
                context.instructionIndex());
    }

    public void finishExternal(AdvInsnBuilder ib, Local result)
    {
        ib.ifCondition(
                AdvInsnBuilder.equal(result, AdvInsnBuilder.constant(0)),
                missing -> missing.gotoLabel(unknown));
        ib.gotoLabel(completed);
    }

    public Expr variantSelector(Expr selector, int variant)
    {
        return generation.mix(
                AdvInsnBuilder.constant(profile().saltHandler ^ variant),
                selector,
                AdvInsnBuilder.constant(variant),
                AdvInsnBuilder.constant(profile().dispatchSalt));
    }

    public int variantKey(int key, int variant)
    {
        return profile().mix(profile().saltHandler ^ variant, key, variant, profile().dispatchSalt);
    }
}
