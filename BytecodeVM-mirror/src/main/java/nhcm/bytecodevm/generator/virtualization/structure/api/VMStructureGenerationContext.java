package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.generator.globalclass.MethodFrameLayout;
import nhcm.bytecodevm.generator.globalclass.VMProgramLayout;
import nhcm.bytecodevm.generator.virtualization.VMObfProfile;
import nhcm.bytecodevm.generator.virtualization.VMRuntimeLayout;
import nhcm.bytecodevm.generator.virtualization.structure.VMStructurePlan;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.utils.builder.FieldRef;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class VMStructureGenerationContext
{
    @FunctionalInterface
    public interface MixEmitter
    {
        Expr emit(Expr key, Expr first, Expr second, Expr third);
    }

    private final String owner;
    private final ClassNode classNode;
    private final MethodFrameLayout frameLayout;
    private final VMProgramLayout programLayout;
    private final VMRuntimeLayout runtimeLayout;
    private final VMObfProfile profile;
    private final VMStructurePlan plan;
    private final BiFunction<InterpretContext, Expr, Expr> stepEmitter;
    private final Function<LabelNode, InterpretContext> contextFactory;
    private final Function<String, String> classNamer;
    private final BiFunction<String, String, String> methodNamer;
    private final BiFunction<String, String, String> fieldNamer;
    private final Function<Boolean, String> schedulerDescriptor;
    private final Function<Void, String> coroutineDescriptor;
    private final MixEmitter mixEmitter;
    private final List<ClassNode> auxiliaryClasses;
    private final List<Consumer<nhcm.bytecodevm.advInsn.AdvInsnBuilder>> classInitializers = new ArrayList<>();

    public VMStructureGenerationContext(
            String owner,
            ClassNode classNode,
            MethodFrameLayout frameLayout,
            VMProgramLayout programLayout,
            VMRuntimeLayout runtimeLayout,
            VMObfProfile profile,
            VMStructurePlan plan,
            BiFunction<InterpretContext, Expr, Expr> stepEmitter,
            Function<LabelNode, InterpretContext> contextFactory,
            Function<String, String> classNamer,
            BiFunction<String, String, String> methodNamer,
            BiFunction<String, String, String> fieldNamer,
            Function<Boolean, String> schedulerDescriptor,
            Function<Void, String> coroutineDescriptor,
            MixEmitter mixEmitter,
            List<ClassNode> auxiliaryClasses)
    {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.classNode = Objects.requireNonNull(classNode, "classNode");
        this.frameLayout = Objects.requireNonNull(frameLayout, "frameLayout");
        this.programLayout = Objects.requireNonNull(programLayout, "programLayout");
        this.runtimeLayout = Objects.requireNonNull(runtimeLayout, "runtimeLayout");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.stepEmitter = Objects.requireNonNull(stepEmitter, "stepEmitter");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.classNamer = Objects.requireNonNull(classNamer, "classNamer");
        this.methodNamer = Objects.requireNonNull(methodNamer, "methodNamer");
        this.fieldNamer = Objects.requireNonNull(fieldNamer, "fieldNamer");
        this.schedulerDescriptor = Objects.requireNonNull(schedulerDescriptor, "schedulerDescriptor");
        this.coroutineDescriptor = Objects.requireNonNull(coroutineDescriptor, "coroutineDescriptor");
        this.mixEmitter = Objects.requireNonNull(mixEmitter, "mixEmitter");
        this.auxiliaryClasses = Objects.requireNonNull(auxiliaryClasses, "auxiliaryClasses");
    }

    public String owner()
    {
        return owner;
    }

    public ClassNode classNode()
    {
        return classNode;
    }

    public MethodFrameLayout frameLayout()
    {
        return frameLayout;
    }

    public VMProgramLayout programLayout()
    {
        return programLayout;
    }

    public VMRuntimeLayout runtimeLayout()
    {
        return runtimeLayout;
    }

    public VMObfProfile profile()
    {
        return profile;
    }

    public VMStructurePlan plan()
    {
        return plan;
    }

    public Expr step(InterpretContext runtime)
    {
        return step(runtime, nhcm.bytecodevm.advInsn.AdvInsnBuilder.constant(0));
    }

    public Expr step(InterpretContext runtime, Expr structureState)
    {
        return stepEmitter.apply(runtime, structureState);
    }

    public InterpretContext runtimeContext(LabelNode loopStart)
    {
        return contextFactory.apply(loopStart);
    }

    public String methodName(String base, String descriptor)
    {
        return methodNamer.apply(base, descriptor);
    }

    public String className(String base)
    {
        return classNamer.apply(base);
    }

    public String fieldName(String owner, String base)
    {
        return fieldNamer.apply(owner, base);
    }

    public FieldRef fieldRef(String base, String descriptor)
    {
        return new FieldRef(owner, fieldName(owner, base), descriptor);
    }

    public String schedulerDescriptor(boolean depth)
    {
        return schedulerDescriptor.apply(depth);
    }

    public String coroutineDescriptor()
    {
        return coroutineDescriptor.apply(null);
    }

    public Expr mix(Expr key, Expr first, Expr second, Expr third)
    {
        return mixEmitter.emit(key, first, second, third);
    }

    public void addMethod(MethodNode method)
    {
        classNode.methods.add(method);
    }

    public void addField(FieldNode field)
    {
        classNode.fields.add(field);
    }

    public void addAuxiliaryClass(ClassNode auxiliaryClass)
    {
        auxiliaryClasses.add(auxiliaryClass);
    }

    public void onClassInitialize(Consumer<nhcm.bytecodevm.advInsn.AdvInsnBuilder> initializer)
    {
        classInitializers.add(Objects.requireNonNull(initializer, "initializer"));
    }

    public void emitClassInitializers(nhcm.bytecodevm.advInsn.AdvInsnBuilder instructions)
    {
        for (Consumer<nhcm.bytecodevm.advInsn.AdvInsnBuilder> initializer : classInitializers)
        {
            initializer.accept(instructions);
        }
    }
}
