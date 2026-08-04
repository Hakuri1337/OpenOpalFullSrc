package nhcm.bytecodevm.generator.virtualization.structure.threadeddirect;

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

public final class DirectThreadedVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public DirectThreadedVMGenerator()
    {
        super(VMStructure.THREADED_DIRECT);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local handlerToken = runtime.intLocal("directHandlerToken", InterpretContext.OPCODE);
        ib.set(handlerToken, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(handlerToken, AdvInsnBuilder.constant(0)),
                handler -> handler.set(handlerToken, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily handlers = GeneratedHandlerFamily.create(dispatch, "DirectThread", 1);
        FieldRef tableField = dispatch.generation().fieldRef("DIRECT_THREADED_HANDLERS", "Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                tableField.name(),
                tableField.descriptor(),
                "Ljava/util/Map<Ljava/lang/Integer;L" + handlers.interfaceName() + ";>;"));

        Map<Integer, VMDispatchTarget> targetByKey = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targetByKey.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local table = initializer.var("directThreadedHandlers", "java/util/Map");
            initializer.set(table, AdvInsnBuilder.newObject("java/util/HashMap"));
            for (Map.Entry<Integer, VMDispatchTarget> entry : targetByKey.entrySet())
            {
                initializer.directCall(mapPut(
                        table,
                        integer(AdvInsnBuilder.constant(entry.getKey())),
                        handlers.newHandler(entry.getValue().primaryKey())));
            }
            initializer.set(
                    AdvInsnBuilder.staticField(tableField),
                    AdvInsnBuilder.callStatic(
                            "java/util/Collections",
                            "unmodifiableMap",
                            "java/util/Map",
                            table));
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("directThreadSelector", InterpretContext.DISPATCH_SELECTOR);
        Local handler = runtime.local(
                "directThreadHandler",
                handlers.interfaceName(),
                InterpretContext.DISPATCH_SELECTOR + 1);
        Local result = runtime.intLocal("directThreadResult", InterpretContext.DISPATCH_SELECTOR + 2);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(handler, AdvInsnBuilder.cast(
                mapGet(AdvInsnBuilder.staticField(tableField), integer(selector)),
                handlers.interfaceName()));
        ib.ifCondition(AdvInsnBuilder.isNull(handler), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(result, handlers.invoke(handler, runtime));
        dispatch.finishExternal(ib, result);
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
                map,
                "java/util/Map",
                "get",
                "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"));
    }

    private static nhcm.bytecodevm.advInsn.Expr mapPut(
            nhcm.bytecodevm.advInsn.Expr map,
            nhcm.bytecodevm.advInsn.Expr key,
            nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvInsnBuilder.callInterface(
                map,
                "java/util/Map",
                "put",
                "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"),
                AdvInsnBuilder.cast(value, "java/lang/Object"));
    }
}
