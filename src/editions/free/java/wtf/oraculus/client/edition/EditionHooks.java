package wtf.oraculus.client.edition;

import wtf.oraculus.client.feature.module.repository.ModuleRepository;

import java.util.List;

public final class EditionHooks {
    private EditionHooks() {
    }

    public static boolean shouldCancelMouseButton(final int button) {
        return false;
    }

    public static boolean isTargetStrafing() {
        return false;
    }

    public static void disableEditionPacketBuffers(final ModuleRepository repository, final List<String> disabledModules) {
        // Free has no edition-specific packet buffer modules.
    }
}
