package nhcm.bytecodevm.generator.virtualization.structure.polymorphic;

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

public final class PolymorphicVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    private static final int VARIANT_COUNT = 3;

    public PolymorphicVMGenerator()
    {
        super(VMStructure.POLYMORPHIC);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local polymorphicResult = runtime.intLocal("polymorphicResult", InterpretContext.OPCODE);
        ib.set(polymorphicResult, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.equal(polymorphicResult, AdvInsnBuilder.constant(0)),
                variant -> variant.set(polymorphicResult, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily family = GeneratedHandlerFamily.create(dispatch, "Polymorphic", VARIANT_COUNT);
        FieldRef variantsField = dispatch.generation().fieldRef("POLYMORPHIC_HANDLERS", "Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                variantsField.name(), variantsField.descriptor()));

        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local variantMap = initializer.var("polymorphicHandlers", "java/util/Map");
            initializer.set(variantMap, AdvInsnBuilder.newObject("java/util/HashMap"));
            int ordinal = 0;
            for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
            {
                Local variants = initializer.var("variants" + ordinal++, "[L" + family.interfaceName() + ";");
                initializer.set(variants, AdvInsnBuilder.newArray(
                        family.interfaceName(), AdvInsnBuilder.constant(VARIANT_COUNT)));
                for (int variant = 0; variant < VARIANT_COUNT; variant++)
                {
                    initializer.setArray(
                            variants,
                            AdvInsnBuilder.constant(variant),
                            family.newHandler(entry.getValue().primaryKey(), variant));
                }
                initializer.directCall(mapPut(variantMap, integer(AdvInsnBuilder.constant(entry.getKey())), variants));
            }
            initializer.set(AdvInsnBuilder.staticField(variantsField), variantMap);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("polymorphicSelector", InterpretContext.DISPATCH_SELECTOR);
        Local variants = runtime.local(
                "polymorphicVariants", "[L" + family.interfaceName() + ";", InterpretContext.DISPATCH_SELECTOR + 1);
        Local variant = runtime.intLocal("polymorphicVariant", InterpretContext.DISPATCH_SELECTOR + 2);
        Local handler = runtime.local(
                "polymorphicHandler", family.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local result = runtime.intLocal("polymorphicStatus", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(variants, AdvInsnBuilder.cast(
                mapGet(AdvInsnBuilder.staticField(variantsField), integer(selector)),
                "[L" + family.interfaceName() + ";"));
        ib.ifCondition(AdvInsnBuilder.isNull(variants), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(variant, AdvInsnBuilder.callStatic(
                "java/lang/Math", "floorMod", "I",
                dispatch.generation().mix(
                        runtime.frameStateKey(), runtime.instructionIndex(), selector,
                        AdvInsnBuilder.constant(dispatch.profile().saltHandler)),
                AdvInsnBuilder.constant(VARIANT_COUNT)));
        ib.set(handler, AdvInsnBuilder.arrayAt(variants, variant));
        ib.set(result, family.invoke(handler, runtime));
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
