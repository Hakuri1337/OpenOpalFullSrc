package nhcm.bytecodevm.generator.virtualization.superinstruction;

import nhcm.bytecodevm.enums.Opcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SuperInstructionRegistry
{
    private final int maxHandlers;
    private final Map<List<Opcs>, Recipe> bySequence = new HashMap<>();
    private final List<Recipe> recipes = new ArrayList<>();

    public SuperInstructionRegistry(int maxHandlers)
    {
        if (maxHandlers <= 0)
        {
            throw new IllegalArgumentException("maxHandlers must be positive");
        }
        this.maxHandlers = maxHandlers;
    }

    public synchronized Recipe register(List<Opcs> sequence)
    {
        List<Opcs> key = List.copyOf(sequence);
        if (key.size() < 2)
        {
            return null;
        }
        Recipe existing = bySequence.get(key);
        if (existing != null)
        {
            return existing;
        }
        if (recipes.size() >= maxHandlers)
        {
            return null;
        }
        Recipe recipe = new Recipe(recipes.size(), key);
        bySequence.put(key, recipe);
        recipes.add(recipe);
        return recipe;
    }

    public synchronized Recipe find(List<Opcs> sequence)
    {
        return bySequence.get(sequence);
    }

    public synchronized List<Recipe> recipes()
    {
        return List.copyOf(recipes);
    }

    public synchronized void clear()
    {
        bySequence.clear();
        recipes.clear();
    }

    public record Recipe(int id, List<Opcs> sequence)
    {
        public Recipe
        {
            sequence = List.copyOf(Objects.requireNonNull(sequence, "sequence"));
        }
    }
}
