package nhcm.bytecodevm.progress;

/** Creates purpose-specific progress stages for one generated VM. */
public interface VirtualizationProgress extends AutoCloseable
{
    ProgressStage compilingMethods(int totalMethods);

    ProgressStage packingCodePools(int totalMethods);

    ProgressStage generatingCodePools(int totalPools);

    ProgressStage generatingVmRuntime();

    ProgressStage replacingMethods(int totalMethods);

    default VirtualizationProgress forVm(String vmName)
    {
        return this;
    }

    @Override
    default void close()
    {
    }

    static VirtualizationProgress silent()
    {
        return SilentVirtualizationProgress.INSTANCE;
    }

    enum SilentVirtualizationProgress implements VirtualizationProgress
    {
        INSTANCE;

        private static final ProgressStage STAGE = new ProgressStage()
        {
            @Override
            public void advance(String detail)
            {
            }
        };

        @Override
        public ProgressStage compilingMethods(int totalMethods)
        {
            return STAGE;
        }

        @Override
        public ProgressStage packingCodePools(int totalMethods)
        {
            return STAGE;
        }

        @Override
        public ProgressStage generatingCodePools(int totalPools)
        {
            return STAGE;
        }

        @Override
        public ProgressStage generatingVmRuntime()
        {
            return STAGE;
        }

        @Override
        public ProgressStage replacingMethods(int totalMethods)
        {
            return STAGE;
        }

        @Override
        public VirtualizationProgress forVm(String vmName)
        {
            return this;
        }
    }
}
