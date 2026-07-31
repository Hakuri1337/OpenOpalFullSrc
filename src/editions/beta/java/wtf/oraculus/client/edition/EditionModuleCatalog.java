package wtf.oraculus.client.edition;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.impl.combat.*;
import wtf.oraculus.client.feature.module.impl.combat.criticals.CriticalsModule;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.tpaura.TpAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.impl.movement.*;
import wtf.oraculus.client.feature.module.impl.movement.clipper.ClipperModule;
import wtf.oraculus.client.feature.module.impl.movement.flight.FlightModule;
import wtf.oraculus.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.impl.movement.physics.PhysicsModule;
import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.impl.utility.*;
import wtf.oraculus.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.oraculus.client.feature.module.impl.utility.inventory.AutoArmorModule;
import wtf.oraculus.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.oraculus.client.feature.module.impl.utility.inventory.manager.InventoryManagerModule;
import wtf.oraculus.client.feature.module.impl.utility.nofall.NoFallModule;
import wtf.oraculus.client.feature.module.impl.visual.*;
import wtf.oraculus.client.feature.module.impl.visual.esp.ESPModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.world.ChestAuraModule;
import wtf.oraculus.client.feature.module.impl.world.FastBreakModule;
import wtf.oraculus.client.feature.module.impl.world.TimerModule;
import wtf.oraculus.client.feature.module.impl.world.legittelly.LegitTellyModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.impl.world.breaker.BreakerModule;
import wtf.oraculus.client.feature.module.impl.world.fucker.FuckerModule;
import wtf.oraculus.client.music.MusicPlayerModule;

public final class EditionModuleCatalog {
    private EditionModuleCatalog() {
    }

    public static Module[] createModules() {
        return new Module[]{
                new KillAuraModule(), new TpAuraModule(), new TeamsModule(), new BacktrackModule(), new BlockModule(),
                new ReachModule(), new PiercingModule(), new AutoClickerModule(), new AttackDelayModule(),
                new SuperKnockBackModule(), new CriticalsModule(), new VelocityModule(), new FakeLagModule(),
                new AutoHeadModule(), new FastPearlModule(), new AutoThrowModule(), new CrystalAuraModule(),

                new ClickGUIModule(), new SigmaStyleModule(), new FullBrightModule(), new AnimationsModule(), new SilenceItemRotationModule(),
                new OverlayModule(), new ChamsModule(), new ESPModule(), new BreakProgressModule(), new CapeModule(),
                new AmbienceModule(), new AttackEffectsModule(), new TabGUIModule(), new StreamerModeModule(),
                new TitleChangerModule(), new NoHurtCameraModule(), new NoFOVModule(), new NoRenderModule(),
                new PostProcessingModule(), new MotionBlurModule(), new LowFireModule(), new ViewClipModule(), new BedPlatesModule(),

                new ScaffoldModule(), new LegitTellyModule(), new TimerModule(), new BreakerModule(), new FuckerModule(), new FastBreakModule(),
                new ChestAuraModule(), new AntiStaffModule(),

                new FlightModule(), new SpeedModule(), new JumpCooldownModule(), new SprintModule(),
                new MovementFixModule(), new NoSlowModule(), new InventoryMoveModule(), new FastWebModule(),
                new TargetStrafeModule(), new PhaseModule(), new LongJumpModule(), new FastStopModule(),
                new StrafeModule(), new PhysicsModule(), new SpiderModule(), new ClipperModule(), new SafeWalkModule(),
                new StuckModule(),

                new FastUseModule(), new AutoRodModule(), new MiddlePearlModule(), new MusicPlayerModule(), new NoFallModule(),
                new ChestStealerModule(), new InventoryManagerModule(), new AutoArmorModule(),
                new DisablerModule(), new FastPlaceModule(), new AntiVoidModule(), new AutoToolModule(),
                new AntiTNTModule(), new AutoChestModule(), new AutoHypixelModule(), new BlinkModule(),
                new PingSpoofModule(), new ServerPackSpoofModule(), new AntiBotsModule(), new KillSayModule(),
                new NoRotateModule(), new SpammerModule(), new PartySpamModule()
        };
    }
}
