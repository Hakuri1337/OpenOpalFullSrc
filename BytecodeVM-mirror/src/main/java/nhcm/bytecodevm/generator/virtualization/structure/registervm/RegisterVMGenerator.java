package nhcm.bytecodevm.generator.virtualization.structure.registervm;

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

public final class RegisterVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public RegisterVMGenerator()
    {
        super(VMStructure.REGISTER_BASED);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local registerResult = runtime.intLocal("registerResult", InterpretContext.OPCODE);
        ib.set(registerResult, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(registerResult, AdvInsnBuilder.constant(0)),
                registerMachine -> registerMachine.set(registerResult, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily operations = GeneratedHandlerFamily.create(dispatch, "RegisterOperation", 1);
        FieldRef keysField = dispatch.generation().fieldRef("REGISTER_OPCODE_KEYS", "[I");
        FieldRef operationsField = dispatch.generation().fieldRef(
                "REGISTER_OPERATIONS", "[L" + operations.interfaceName() + ";");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, keysField.name(), keysField.descriptor()));
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, operationsField.name(), operationsField.descriptor()));
        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local keys = initializer.var("registerOpcodeKeys", "[I");
            Local table = initializer.var("registerOperations", operationsField.descriptor());
            initializer.set(keys, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(targets.size())));
            initializer.set(table, AdvInsnBuilder.newArray(
                    operations.interfaceName(), AdvInsnBuilder.constant(targets.size())));
            int index = 0;
            for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
            {
                initializer.setArray(keys, AdvInsnBuilder.constant(index), AdvInsnBuilder.constant(entry.getKey()));
                initializer.setArray(table, AdvInsnBuilder.constant(index),
                        operations.newHandler(entry.getValue().primaryKey()));
                index++;
            }
            initializer.set(AdvInsnBuilder.staticField(keysField), keys);
            initializer.set(AdvInsnBuilder.staticField(operationsField), table);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("registerOpcode", InterpretContext.DISPATCH_SELECTOR);
        Local cursor = runtime.intLocal("registerOperationIndex", InterpretContext.DISPATCH_SELECTOR + 1);
        Local operation = runtime.local(
                "registerOperation", operations.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 2);
        Local status = runtime.intLocal("registerStatus", InterpretContext.DISPATCH_SELECTOR + 3);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(cursor, AdvInsnBuilder.constant(0));
        ib.set(operation, AdvInsnBuilder.constant(null));
        ib.whileLoop(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.lessThan(cursor, AdvInsnBuilder.constant(targets.size())),
                        AdvInsnBuilder.isNull(operation)),
                search -> {
                    search.ifCondition(
                            AdvInsnBuilder.equal(
                                    AdvInsnBuilder.arrayAt(AdvInsnBuilder.staticField(keysField), cursor), selector),
                            found -> found.set(operation, AdvInsnBuilder.arrayAt(
                                    AdvInsnBuilder.staticField(operationsField), cursor)));
                    search.increment(cursor, 1);
                });
        ib.ifCondition(AdvInsnBuilder.isNull(operation), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(status, operations.invoke(operation, runtime));
        dispatch.finishExternal(ib, status);
    }
}
