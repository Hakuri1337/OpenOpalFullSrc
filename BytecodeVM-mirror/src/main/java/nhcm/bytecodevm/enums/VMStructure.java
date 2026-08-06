package nhcm.bytecodevm.enums;

import nhcm.bytecodevm.utils.RandomUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum VMStructure
{
    SIMPLE_DISPATCH,
    DISTRIBUTED_DISPATCH,
    MULTIPLE_DISPATCH,
    THREADED_DIRECT,
    THREADED_INDIRECT,
    CALL_THREADED,
    RECURSIVE,
    CONTINUATION_PASSING,
    OBJECT,
    POLYMORPHIC,
    SELF_MODIFYING,
    REGISTER_BASED,
    DATA_FLOW,
    GRAPH,
    FSM,
    EVENT,
    COROUTINE,
    LOW,
    MEDIUM,
    HIGH;

    private static final int AUTO_WINDOW_SIZE = 12;

    private static final VMStructure[] LOW_CANDIDATES = {
            SIMPLE_DISPATCH,
            DISTRIBUTED_DISPATCH,
            MULTIPLE_DISPATCH,
            THREADED_DIRECT,
            THREADED_INDIRECT
    };
    private static final VMStructure[] MEDIUM_CANDIDATES = {
            CALL_THREADED,
            RECURSIVE,
            CONTINUATION_PASSING,
            OBJECT,
            SELF_MODIFYING,
            EVENT,
            COROUTINE
    };
    private static final VMStructure[] HIGH_CANDIDATES = {
            DATA_FLOW,
            POLYMORPHIC,
            GRAPH,
            FSM,
            REGISTER_BASED
    };
    private static final Map<VMStructure, ArrayDeque<VMStructure>> AUTO_BAGS = new EnumMap<>(VMStructure.class);

    public static VMStructure parse(String value)
    {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isAutomatic()
    {
        return this == LOW || this == MEDIUM || this == HIGH;
    }

    public boolean acceptsResolvedStructure(VMStructure resolved)
    {
        if (!isAutomatic())
        {
            return this == resolved;
        }
        VMStructure[] candidates = switch (this)
        {
            case LOW -> LOW_CANDIDATES;
            case MEDIUM -> MEDIUM_CANDIDATES;
            case HIGH -> HIGH_CANDIDATES;
            default -> throw new IllegalStateException("Unexpected automatic structure: " + this);
        };
        for (VMStructure candidate : candidates)
        {
            if (candidate == resolved)
            {
                return true;
            }
        }
        return false;
    }

    public static synchronized void resetAutomaticSelection()
    {
        AUTO_BAGS.clear();
    }

    public synchronized VMStructure resolveAuto()
    {
        VMStructure[] candidates = switch (this)
        {
            case LOW -> LOW_CANDIDATES;
            case MEDIUM -> MEDIUM_CANDIDATES;
            case HIGH -> HIGH_CANDIDATES;
            default -> null;
        };
        if (candidates == null)
        {
            return this;
        }
        ArrayDeque<VMStructure> bag = AUTO_BAGS.computeIfAbsent(this, ignored -> new ArrayDeque<>());
        if (bag.isEmpty())
        {
            refillAutoBag(bag, candidates);
        }
        return bag.removeFirst();
    }

    private static void refillAutoBag(ArrayDeque<VMStructure> bag, VMStructure[] candidates)
    {
        List<VMStructure> shuffled = new ArrayList<>(List.of(candidates));
        RandomUtils.shuffle(shuffled);
        int limit = Math.min(AUTO_WINDOW_SIZE, shuffled.size());
        for (int index = 0; index < limit; index++)
        {
            bag.addLast(shuffled.get(index));
        }
    }
}
