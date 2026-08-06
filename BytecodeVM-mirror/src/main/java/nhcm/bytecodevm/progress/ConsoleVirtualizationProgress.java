package nhcm.bytecodevm.progress;

import java.util.Locale;

/** Interactive console renderer with a separate progress lifetime for every generation phase. */
public final class ConsoleVirtualizationProgress implements VirtualizationProgress
{
    private final String vmName;
    private final boolean enabled;
    private ConsoleProgressStage activeStage;

    public ConsoleVirtualizationProgress(String vmName)
    {
        this.vmName = vmName;
        this.enabled = System.console() != null;
    }

    @Override
    public ProgressStage compilingMethods(int totalMethods)
    {
        return activate(new CompilingMethodsProgress(vmName, totalMethods, enabled));
    }

    @Override
    public ProgressStage packingCodePools(int totalMethods)
    {
        return activate(new PackingCodePoolsProgress(vmName, totalMethods, enabled));
    }

    @Override
    public ProgressStage generatingCodePools(int totalPools)
    {
        return activate(new GeneratingCodePoolsProgress(vmName, totalPools, enabled));
    }

    @Override
    public ProgressStage generatingVmRuntime()
    {
        return activate(new GeneratingVmRuntimeProgress(vmName, enabled));
    }

    @Override
    public ProgressStage replacingMethods(int totalMethods)
    {
        return activate(new ReplacingMethodsProgress(vmName, totalMethods, enabled));
    }

    @Override
    public VirtualizationProgress forVm(String vmName)
    {
        return new ConsoleVirtualizationProgress(vmName);
    }

    @Override
    public void close()
    {
        if (activeStage != null)
        {
            activeStage.close();
            activeStage = null;
        }
    }

    private ProgressStage activate(ConsoleProgressStage stage)
    {
        if (activeStage != null)
        {
            activeStage.close();
        }
        activeStage = stage;
        stage.render(0, "Starting", true);
        return stage;
    }

    private abstract static class ConsoleProgressStage implements ProgressStage
    {
        private static final String CLEAR_LINE = "\u001B[2K";
        private static final int BAR_WIDTH = 22;
        private static final int MAX_VM_NAME_LENGTH = 24;
        private static final int MAX_DETAIL_LENGTH = 28;

        private final String phase;
        private final String vmName;
        private int total;
        private final boolean enabled;
        private int completed;
        private long lastRenderNanos;
        private boolean closed;

        private ConsoleProgressStage(String phase, String vmName, int total, boolean enabled)
        {
            this.phase = phase;
            this.vmName = vmName;
            this.total = Math.max(total, 1);
            this.enabled = enabled;
        }

        @Override
        public final void addWork(int amount)
        {
            if (!closed && amount > 0)
            {
                total += amount;
                render(completed, "Queued more work", true);
            }
        }

        @Override
        public final void setDetail(String detail)
        {
            if (!closed)
            {
                render(completed, detail, true);
            }
        }

        @Override
        public final void advance(String detail)
        {
            if (closed)
            {
                return;
            }
            completed = Math.min(completed + 1, total);
            render(completed, detail, false);
        }

        private void render(int value, String detail, boolean force)
        {
            if (!enabled || closed)
            {
                return;
            }
            long now = System.nanoTime();
            boolean edge = value == 0 || value == total;
            if (!force && !edge && now - lastRenderNanos < 50_000_000L)
            {
                return;
            }
            int filled = (int) ((value * (long) BAR_WIDTH) / total);
            int percent = (int) ((value * 100L) / total);
            String bar = "=".repeat(filled) + " ".repeat(BAR_WIDTH - filled);
            String line = String.format(
                    Locale.ROOT,
                    "%s %s [%s] %3d%% %d/%d %s",
                    phase,
                    truncate(vmName, MAX_VM_NAME_LENGTH),
                    bar,
                    percent,
                    value,
                    total,
                    truncate(detail, MAX_DETAIL_LENGTH));
            System.out.print("\r" + CLEAR_LINE + line);
            System.out.flush();
            lastRenderNanos = now;
        }

        @Override
        public final void close()
        {
            if (closed)
            {
                return;
            }
            if (completed < total)
            {
                completed = total;
                render(completed, "Done", true);
            }
            if (enabled)
            {
                System.out.print("\r" + CLEAR_LINE + "\r");
                System.out.flush();
            }
            closed = true;
        }

        private static String truncate(String value, int maxLength)
        {
            if (value == null || value.length() <= maxLength)
            {
                return value == null ? "" : value;
            }
            return value.substring(0, maxLength - 3) + "...";
        }
    }

    private static final class CompilingMethodsProgress extends ConsoleProgressStage
    {
        private CompilingMethodsProgress(String vmName, int total, boolean enabled)
        {
            super("Compiling Methods", vmName, total, enabled);
        }
    }

    private static final class PackingCodePoolsProgress extends ConsoleProgressStage
    {
        private PackingCodePoolsProgress(String vmName, int total, boolean enabled)
        {
            super("Packing CodePools", vmName, total, enabled);
        }
    }

    private static final class GeneratingCodePoolsProgress extends ConsoleProgressStage
    {
        private GeneratingCodePoolsProgress(String vmName, int total, boolean enabled)
        {
            super("Generating CodePools", vmName, total, enabled);
        }
    }

    private static final class GeneratingVmRuntimeProgress extends ConsoleProgressStage
    {
        private GeneratingVmRuntimeProgress(String vmName, boolean enabled)
        {
            super("Generating VM Runtime", vmName, 1, enabled);
        }
    }

    private static final class ReplacingMethodsProgress extends ConsoleProgressStage
    {
        private ReplacingMethodsProgress(String vmName, int total, boolean enabled)
        {
            super("Replacing Methods", vmName, total, enabled);
        }
    }
}
