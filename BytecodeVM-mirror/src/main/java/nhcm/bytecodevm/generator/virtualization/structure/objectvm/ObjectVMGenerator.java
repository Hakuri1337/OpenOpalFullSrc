package nhcm.bytecodevm.generator.virtualization.structure.objectvm;

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

public final class ObjectVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public ObjectVMGenerator()
    {
        super(VMStructure.OBJECT);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local objectResult = runtime.intLocal("objectInstructionResult", InterpretContext.OPCODE);
        ib.set(objectResult, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(objectResult, AdvInsnBuilder.constant(0)),
                objectCall -> objectCall.set(objectResult, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily instructions = GeneratedHandlerFamily.create(dispatch, "ObjectInstruction", 1);
        FieldRef instructionObjects = dispatch.generation().fieldRef("OBJECT_INSTRUCTIONS", "Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                instructionObjects.name(),
                instructionObjects.descriptor(),
                "Ljava/util/Map<Ljava/lang/Integer;L" + instructions.interfaceName() + ";>;"));

        Map<Integer, VMDispatchTarget> objects = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            objects.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local objectCode = initializer.var("objectInstructionCode", "java/util/Map");
            initializer.set(objectCode, AdvInsnBuilder.newObject("java/util/LinkedHashMap"));
            for (Map.Entry<Integer, VMDispatchTarget> entry : objects.entrySet())
            {
                initializer.directCall(mapPut(
                        objectCode,
                        integer(AdvInsnBuilder.constant(entry.getKey())),
                        instructions.newHandler(entry.getValue().primaryKey())));
            }
            initializer.set(AdvInsnBuilder.staticField(instructionObjects), objectCode);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("objectInstructionId", InterpretContext.DISPATCH_SELECTOR);
        Local instruction = runtime.local(
                "objectInstruction",
                instructions.interfaceName(),
                InterpretContext.DISPATCH_SELECTOR + 1);
        Local result = runtime.intLocal("objectInstructionStatus", InterpretContext.DISPATCH_SELECTOR + 2);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(instruction, AdvInsnBuilder.cast(
                mapGet(AdvInsnBuilder.staticField(instructionObjects), integer(selector)),
                instructions.interfaceName()));
        ib.ifCondition(AdvInsnBuilder.isNull(instruction), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(result, instructions.invoke(instruction, runtime));
        dispatch.finishExternal(ib, result);
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
