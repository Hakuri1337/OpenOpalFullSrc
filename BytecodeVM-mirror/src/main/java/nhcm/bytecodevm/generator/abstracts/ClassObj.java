package nhcm.bytecodevm.generator.abstracts;

public abstract class ClassObj
{
    private final String className;

    public ClassObj(String className)
    {
        this.className = className;
    }

    public String descriptor()
    {
        return "L" + className + ";";
    }

    public String className()
    {
        return className;
    }
}
