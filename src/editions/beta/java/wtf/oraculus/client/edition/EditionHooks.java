package wtf.oraculus.client.edition;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.impl.movement.TargetStrafeModule;
import wtf.oraculus.client.feature.module.impl.utility.AutoRodModule;
import wtf.oraculus.client.feature.module.impl.combat.FakeLagModule;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;

import java.util.List;

public final class EditionHooks {
    private EditionHooks() {
    }

    public static boolean shouldCancelMouseButton(final int button) {
        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository == null) {
            return false;
        }

        final AutoRodModule autoRod = repository.getModule(AutoRodModule.class);
        return autoRod != null && autoRod.isEnabled() && autoRod.shouldInterceptButton(button);
    }

    public static boolean isTargetStrafing() {
        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository == null) {
            return false;
        }

        final TargetStrafeModule targetStrafe = repository.getModule(TargetStrafeModule.class);
        return targetStrafe != null && targetStrafe.isActivelyStrafing();
    }

    public static void disableEditionPacketBuffers(final ModuleRepository repository, final List<String> disabledModules) {
        final Module fakeLag = repository.getModule(FakeLagModule.class);
        if (fakeLag != null && fakeLag.isEnabled()) {
            fakeLag.setEnabled(false);
            disabledModules.add("FakeLag");
        }
    }
}
