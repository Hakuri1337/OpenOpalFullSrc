package nhcm.bytecodevm.generator.virtualization.structure.dataflow;

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
import nhcm.bytecodevm.utils.builder.FieldRef;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DataFlowVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public DataFlowVMGenerator()
    {
        super(VMStructure.DATA_FLOW);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local graphResult = runtime.intLocal("dataFlowResult", InterpretContext.OPCODE);
        ib.set(graphResult, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(graphResult, AdvInsnBuilder.constant(0)),
                readyQueue -> readyQueue.set(graphResult, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        int bucketCount = dispatch.plan().laneCount();
        GeneratedHandlerFamily nodes = GeneratedHandlerFamily.create(dispatch, "DataFlowNode", 1);
        FieldRef bucketsField = dispatch.generation().fieldRef("DATA_FLOW_BUCKETS", "[Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, bucketsField.name(), bucketsField.descriptor()));
        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local buckets = initializer.var("dataFlowBuckets", "[Ljava/util/Map;");
            initializer.set(buckets, AdvInsnBuilder.newArray("java/util/Map", AdvInsnBuilder.constant(bucketCount)));
            for (int bucket = 0; bucket < bucketCount; bucket++)
            {
                Local nodesInBucket = initializer.var("dataFlowBucket" + bucket, "java/util/Map");
                initializer.set(nodesInBucket, AdvInsnBuilder.newObject("java/util/HashMap"));
                for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
                {
                    if (Math.floorMod(entry.getKey(), bucketCount) != bucket)
                    {
                        continue;
                    }
                    initializer.directCall(mapPut(
                            nodesInBucket,
                            integer(AdvInsnBuilder.constant(entry.getKey())),
                            nodes.newHandler(entry.getValue().primaryKey())));
                }
                initializer.setArray(buckets, AdvInsnBuilder.constant(bucket), nodesInBucket);
            }
            initializer.set(AdvInsnBuilder.staticField(bucketsField), buckets);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("dataFlowOpcode", InterpretContext.DISPATCH_SELECTOR);
        Local bucket = runtime.intLocal("dataFlowBucket", InterpretContext.DISPATCH_SELECTOR + 1);
        Local nodeMap = runtime.local("dataFlowNodeMap", "java/util/Map", InterpretContext.DISPATCH_SELECTOR + 2);
        Local node = runtime.local("dataFlowNode", nodes.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local status = runtime.intLocal("dataFlowStatus", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(bucket, AdvInsnBuilder.callStatic(
                "java/lang/Math", "floorMod", "I", selector, AdvInsnBuilder.constant(bucketCount)));
        ib.set(nodeMap, AdvInsnBuilder.arrayAt(AdvInsnBuilder.staticField(bucketsField), bucket));
        ib.set(node, AdvInsnBuilder.cast(mapGet(nodeMap, integer(selector)), nodes.interfaceName()));
        ib.ifCondition(AdvInsnBuilder.isNull(node), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(status, nodes.invoke(node, runtime));
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
