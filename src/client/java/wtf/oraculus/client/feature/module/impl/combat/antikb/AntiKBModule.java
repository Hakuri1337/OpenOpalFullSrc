package wtf.oraculus.client.feature.module.impl.combat.antikb;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.combat.antikb.impl.*;
import wtf.oraculus.client.feature.module.impl.movement.flight.FlightModule;
import wtf.oraculus.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;

import static wtf.oraculus.client.Constants.mc;

public final class AntiKBModule extends Module {

    public AntiKBModule() {
        super("Velocity", "Reduces or nullifies your players velocity when being hit.", ModuleCategory.COMBAT);
        ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.REDUCE);
        this.addProperties(mode);
        addModuleModes(mode,
                new WatchdogVelocity(this),
                new MushMCVelocity(this),
                new JumpResetVelocity(this),
                new ReduceVelocity(this),
                new NoXZVelocity(this)
        );
    }

    @Override
    public String getSuffix() {
        return ((AntiKBMode) this.getActiveMode()).getSuffix();
    }

    public boolean isInvalid() {
        if (mc.player == null) {
            return true;
        }

        final ModuleRepository moduleRepository = OraculusClient.getInstance().getModuleRepository();
        if (moduleRepository.getModule(LongJumpModule.class).isEnabled()
                || moduleRepository.getModule(FlightModule.class).isEnabled()) {
            return true;
        }

        final HypixelServer.ModAPI.Location currentLocation = HypixelServer.ModAPI.get().getCurrentLocation();
        return LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer
                && currentLocation != null && currentLocation.isLobby();
    }

    public enum Mode {
        WATCHDOG("Watchdog"),
        MUSHMC("MushMC"),
        REDUCE("Reduce"),
        JUMPRESET("JumpReset"),
        NOXZ("NoXZ");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}