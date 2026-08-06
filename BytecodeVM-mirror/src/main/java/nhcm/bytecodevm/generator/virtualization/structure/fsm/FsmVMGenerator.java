package nhcm.bytecodevm.generator.virtualization.structure.fsm;

import nhcm.bytecodevm.advInsn.AdvInsnBuilder;
import nhcm.bytecodevm.advInsn.Expr;
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

public final class FsmVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public FsmVMGenerator()
    {
        super(VMStructure.FSM);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvInsnBuilder ib, InterpretContext runtime)
    {
        Local action = runtime.intLocal("fsmAction", InterpretContext.OPCODE);
        Local state = runtime.intLocal("fsmState", InterpretContext.DISPATCH_SELECTOR + 1);
        Local transition = runtime.intLocal("fsmTransition", InterpretContext.DISPATCH_SELECTOR + 2);
        ib.set(action, AdvInsnBuilder.constant(0));
        ib.set(state, state(generation, runtime, AdvInsnBuilder.constant(0)));
        ib.whileLoop(
                AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0)),
                machine -> {
                    machine.set(transition, AdvInsnBuilder.bitXor(
                            state,
                            AdvInsnBuilder.constant(generation.profile().saltState)));
                    machine.set(action, generation.step(runtime, state));
                    machine.ifCondition(
                            AdvInsnBuilder.equal(action, AdvInsnBuilder.constant(0)),
                            next -> next.set(state, state(generation, runtime, transition)));
                });
    }

    private Expr state(
            VMStructureGenerationContext generation,
            InterpretContext runtime,
            Expr previous)
    {
        return AdvInsnBuilder.bitXor(
                generation.mix(
                        runtime.frameProgramCounter(),
                        runtime.frameBlockIndex(),
                        previous,
                        AdvInsnBuilder.constant(generation.profile().saltState)),
                AdvInsnBuilder.constant(generation.profile().saltState));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        int stateCount = dispatch.plan().laneCount();
        GeneratedHandlerFamily handlers = GeneratedHandlerFamily.create(dispatch, "State", stateCount);
        FieldRef resolverField = dispatch.generation().fieldRef("FSM_SYMBOLS", "Ljava/util/Map;");
        FieldRef transitionField = dispatch.generation().fieldRef(
                "FSM_TRANSITIONS", "[L" + handlers.interfaceName() + ";");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, resolverField.name(), resolverField.descriptor()));
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, transitionField.name(), transitionField.descriptor()));

        Map<Integer, Integer> slotByPrimary = new LinkedHashMap<>();
        Map<Integer, Integer> slotByKey = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            int slot = slotByPrimary.computeIfAbsent(target.primaryKey(), ignored -> slotByPrimary.size());
            slotByKey.putIfAbsent(target.key(), slot);
        }
        int symbolCount = slotByPrimary.size();
        dispatch.generation().onClassInitialize(initializer -> {
            Local symbols = initializer.var("fsmSymbols", "java/util/Map");
            Local transitions = initializer.var("fsmTransitions", transitionField.descriptor());
            initializer.set(symbols, AdvInsnBuilder.newObject("java/util/HashMap"));
            for (Map.Entry<Integer, Integer> entry : slotByKey.entrySet())
            {
                initializer.directCall(mapPut(
                        symbols,
                        integer(AdvInsnBuilder.constant(entry.getKey())),
                        integer(AdvInsnBuilder.constant(entry.getValue()))));
            }
            initializer.set(transitions, AdvInsnBuilder.newArray(
                    handlers.interfaceName(), AdvInsnBuilder.constant(stateCount * symbolCount)));
            for (int state = 0; state < stateCount; state++)
            {
                for (Map.Entry<Integer, Integer> entry : slotByPrimary.entrySet())
                {
                    initializer.setArray(
                            transitions,
                            AdvInsnBuilder.constant(state * symbolCount + entry.getValue()),
                            handlers.newHandler(entry.getKey(), state));
                }
            }
            initializer.set(AdvInsnBuilder.staticField(resolverField), symbols);
            initializer.set(AdvInsnBuilder.staticField(transitionField), transitions);
        });

        AdvInsnBuilder ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("fsmSymbol", InterpretContext.DISPATCH_SELECTOR);
        Local symbolObject = runtime.local("fsmSymbolObject", "java/lang/Integer", InterpretContext.DISPATCH_SELECTOR + 1);
        Local symbol = runtime.intLocal("fsmSymbolIndex", InterpretContext.DISPATCH_SELECTOR + 2);
        Local state = runtime.intLocal("fsmActiveState", InterpretContext.DISPATCH_SELECTOR + 3);
        Local handler = runtime.local("fsmStateHandler", handlers.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 4);
        Local status = runtime.intLocal("fsmStatus", InterpretContext.DISPATCH_SELECTOR + 5);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(symbolObject, AdvInsnBuilder.cast(
                mapGet(AdvInsnBuilder.staticField(resolverField), integer(selector)), "java/lang/Integer"));
        ib.ifCondition(AdvInsnBuilder.isNull(symbolObject), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(symbol, AdvInsnBuilder.callVirtual(symbolObject, "java/lang/Integer", "intValue", "I"));
        ib.set(state, AdvInsnBuilder.callStatic(
                "java/lang/Math", "floorMod", "I", runtime.structureState(), AdvInsnBuilder.constant(stateCount)));
        ib.set(handler, AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(transitionField),
                AdvInsnBuilder.plus(AdvInsnBuilder.multiply(state, AdvInsnBuilder.constant(symbolCount)), symbol)));
        ib.set(status, handlers.invoke(handler, runtime));
        dispatch.finishExternal(ib, status);
    }

    private static Expr integer(Expr value)
    {
        return AdvInsnBuilder.callStatic("java/lang/Integer", "valueOf", "java/lang/Integer", value);
    }

    private static Expr mapGet(Expr map, Expr key)
    {
        return AdvInsnBuilder.callInterface(
                map, "java/util/Map", "get", "java/lang/Object", AdvInsnBuilder.cast(key, "java/lang/Object"));
    }

    private static Expr mapPut(Expr map, Expr key, Expr value)
    {
        return AdvInsnBuilder.callInterface(
                map, "java/util/Map", "put", "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"), AdvInsnBuilder.cast(value, "java/lang/Object"));
    }
}
