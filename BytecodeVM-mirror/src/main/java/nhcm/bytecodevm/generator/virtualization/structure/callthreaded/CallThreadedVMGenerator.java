package nhcm.bytecodevm.generator.virtualization.structure.callthreaded;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CallThreadedVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public CallThreadedVMGenerator()
    {
        super(VMStructure.CALL_THREADED);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        int laneCount = generation.plan().laneCount();
        List<String> segments = new ArrayList<>(laneCount);
        String descriptor = generation.schedulerDescriptor(false);
        for (int lane = 0; lane < laneCount; lane++)
        {
            segments.add(generation.methodName("callThreadTail$" + lane, descriptor));
        }
        for (int lane = laneCount - 1; lane >= 0; lane--)
        {
            generation.addMethod(createTailSegment(generation, segments, lane, descriptor));
        }

        Local action = runtime.intLocal("callThreadAction", InterpretContext.OPCODE);
        ib.set(action, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0)),
                trampoline -> trampoline.set(action, AdvInsnBuilder.callStatic(
                        generation.owner(),
                        segments.getFirst(),
                        "I",
                        runtime.program(),
                        runtime.frame(),
                        runtime.code(),
                        runtime.constants())));
    }

    private MethodNode createTailSegment(
            VMStructureGenerationContext generation,
            List<String> segments,
            int lane,
            String descriptor)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                segments.get(lane),
                descriptor);
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        InterpretContext runtime = generation.runtimeContext(null);
        Local action = runtime.intLocal("tailAction", InterpretContext.OPCODE);
        ib.set(action, generation.step(runtime));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(action, AdvInsnBuilder.constant(0)),
                done -> done.returnValue(action));
        if (lane + 1 == segments.size())
        {
            ib.returnValue(AdvInsnBuilder.constant(0));
        }
        else
        {
            ib.returnValue(AdvInsnBuilder.callStatic(
                    generation.owner(),
                    segments.get(lane + 1),
                    "I",
                    runtime.program(),
                    runtime.frame(),
                    runtime.code(),
                    runtime.constants()));
        }
        return method;
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily handlers = GeneratedHandlerFamily.create(dispatch, "CallThread", 1);
        FieldRef callsField = dispatch.generation().fieldRef("CALL_THREADED_TARGETS", "Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                callsField.name(), callsField.descriptor()));

        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local calls = initializer.var("callThreadTargets", "java/util/Map");
            initializer.set(calls, AdvInsnBuilder.newObject("java/util/HashMap"));
            for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
            {
                initializer.directCall(mapPut(
                        calls,
                        integer(AdvInsnBuilder.constant(entry.getKey())),
                        handlers.newHandler(entry.getValue().primaryKey())));
            }
            initializer.set(AdvInsnBuilder.staticField(callsField), calls);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("callThreadSelector", InterpretContext.DISPATCH_SELECTOR);
        Local callee = runtime.local(
                "callThreadCallee", handlers.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 1);
        Local status = runtime.intLocal("callThreadStatus", InterpretContext.DISPATCH_SELECTOR + 2);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(callee, AdvInsnBuilder.cast(
                mapGet(AdvInsnBuilder.staticField(callsField), integer(selector)),
                handlers.interfaceName()));
        ib.ifCondition(AdvInsnBuilder.isNull(callee), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(status, handlers.invoke(callee, runtime));
        dispatch.finishExternal(ib, status);
    }

    private static nhcm.bytecodevm.advInsn.Expr integer(nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvInsnBuilder.callStatic("java/lang/Integer", "valueOf", "java/lang/Integer", value);
    }

    private static nhcm.bytecodevm.advInsn.Expr mapGet(nhcm.bytecodevm.advInsn.Expr map, nhcm.bytecodevm.advInsn.Expr key)
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
