package nhcm.bytecodevm.generator.virtualization.structure.coroutine;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.virtualization.structure.api.AbstractVMStructureGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.GeneratedHandlerFamily;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchTarget;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerationContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.builder.FieldRef;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CoroutineVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public CoroutineVMGenerator()
    {
        super(VMStructure.COROUTINE);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        String descriptor = generation.coroutineDescriptor();
        String resumeName = generation.methodName("resumeCoroutine", descriptor);
        generation.addMethod(createResumeMethod(generation, resumeName, descriptor));
        Local action = runtime.intLocal("coroutineAction", InterpretContext.OPCODE);
        Local state = runtime.local("coroutineState", "[I", InterpretContext.DISPATCH_SELECTOR + 1);
        ib.set(state, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(4)));
        ib.set(action, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0)),
                resume -> resume.set(action, AdvInsnBuilder.callStatic(
                        generation.owner(),
                        resumeName,
                        "I",
                        runtime.program(),
                        runtime.frame(),
                        runtime.code(),
                        runtime.constants(),
                        state)));
    }

    private MethodNode createResumeMethod(
            VMStructureGenerationContext generation,
            String name,
            String descriptor)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        InterpretContext runtime = generation.runtimeContext(null);
        Local state = ib.getLocal("coroutineState", "[I", 4);
        Local action = ib.getLocal("resumeAction", "I", 5);
        Local budget = ib.getLocal("resumeBudget", "I", 6);
        ib.set(action, AdvInsnBuilder.constant(0));
        ib.forLoop(
                init -> init.set(budget, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.and(
                        AdvInsnBuilder.lessThan(budget, AdvInsnBuilder.constant(generation.plan().laneCount())),
                        AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0))),
                update -> update.increment(budget, 1),
                execute -> execute.set(action, generation.step(
                        runtime,
                        AdvInsnBuilder.bitXor(
                                AdvInsnBuilder.arrayAt(state, AdvInsnBuilder.constant(1)),
                                budget))));
        ib.setArray(state, AdvInsnBuilder.constant(0), action);
        ib.setArray(state, AdvInsnBuilder.constant(1), runtime.frameProgramCounter());
        ib.setArray(state, AdvInsnBuilder.constant(2), runtime.frameStateKey());
        ib.setArray(state, AdvInsnBuilder.constant(3), budget);
        ib.returnValue(action);
        return method;
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily resumes = GeneratedHandlerFamily.create(dispatch, "CoroutineResume", 2);
        FieldRef phasesField = dispatch.generation().fieldRef("COROUTINE_PHASES", "[Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, phasesField.name(), phasesField.descriptor()));
        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local phases = initializer.var("coroutinePhases", "[Ljava/util/Map;");
            initializer.set(phases, AdvInsnBuilder.newArray("java/util/Map", AdvInsnBuilder.constant(2)));
            for (int phase = 0; phase < 2; phase++)
            {
                Local resumeTable = initializer.var("resumePhase" + phase, "java/util/Map");
                initializer.set(resumeTable, AdvInsnBuilder.newObject("java/util/HashMap"));
                for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
                {
                    initializer.directCall(mapPut(
                            resumeTable,
                            integer(AdvInsnBuilder.constant(entry.getKey())),
                            resumes.newHandler(entry.getValue().primaryKey(), phase)));
                }
                initializer.setArray(phases, AdvInsnBuilder.constant(phase), resumeTable);
            }
            initializer.set(AdvInsnBuilder.staticField(phasesField), phases);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("coroutineOpcode", InterpretContext.DISPATCH_SELECTOR);
        Local phase = runtime.intLocal("coroutinePhase", InterpretContext.DISPATCH_SELECTOR + 1);
        Local table = runtime.local("coroutineResumeTable", "java/util/Map", InterpretContext.DISPATCH_SELECTOR + 2);
        Local resume = runtime.local("coroutineResume", resumes.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local status = runtime.intLocal("coroutineStatus", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(phase, AdvInsnBuilder.bitAnd(runtime.structureState(), AdvInsnBuilder.constant(1)));
        ib.set(table, AdvInsnBuilder.arrayAt(AdvInsnBuilder.staticField(phasesField), phase));
        ib.set(resume, AdvInsnBuilder.cast(mapGet(table, integer(selector)), resumes.interfaceName()));
        ib.ifCondition(AdvInsnBuilder.isNull(resume), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(status, resumes.invoke(resume, runtime));
        dispatch.finishExternal(ib, status);
    }

    private static nhcm.bytecodevm.advInsn.Expr integer(nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvInsnBuilder.callStatic("java/lang/Integer", "valueOf", "java/lang/Integer", value);
    }

    private static nhcm.bytecodevm.advInsn.Expr mapGet(
            nhcm.bytecodevm.advInsn.Expr map,
            nhcm.bytecodevm.advInsn.Expr key)
    {
        return AdvInsnBuilder.callInterface(
                map, "java/util/Map", "get", "java/lang/Object", AdvInsnBuilder.cast(key, "java/lang/Object"));
    }

    private static nhcm.bytecodevm.advInsn.Expr mapPut(
            nhcm.bytecodevm.advInsn.Expr map,
            nhcm.bytecodevm.advInsn.Expr key,
            nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvInsnBuilder.callInterface(
                map, "java/util/Map", "put", "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"), AdvInsnBuilder.cast(value, "java/lang/Object"));
    }
}
