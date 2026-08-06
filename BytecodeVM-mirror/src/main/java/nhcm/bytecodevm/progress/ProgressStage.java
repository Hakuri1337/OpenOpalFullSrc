package nhcm.bytecodevm.progress;

/** One independently measured phase of VM generation. */
public interface ProgressStage extends AutoCloseable
{
    default void addWork(int amount)
    {
    }

    default void setDetail(String detail)
    {
    }

    void advance(String detail);

    @Override
    default void close()
    {
    }
}
