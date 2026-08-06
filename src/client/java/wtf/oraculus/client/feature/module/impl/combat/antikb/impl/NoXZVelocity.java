package wtf.oraculus.client.feature.module.impl.combat.antikb.impl;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.Hand;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.holder.BlockHolder;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBMode;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import static wtf.oraculus.client.Constants.mc;

public final class NoXZVelocity extends AntiKBMode {

    private final ModeProperty<Method> method = new ModeProperty<>("Method", this, Method.PRE)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty hits = new NumberProperty("Hits", 3, 1, 5, 1)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty wtap = new BooleanProperty("WTap", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> this.module.getActiveMode() != this);

    private final BlockHolder blockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private Entity target;
    private int G = 0;
    private boolean damageReceived = false;
    private int totalAttacksSent = 0;

    public NoXZVelocity(AntiKBModule module) {
        super(module);
        module.addProperties(this.method, this.hits, this.wtap, this.debug);
    }

    @Override
    public String getSuffix() {
        return "NoXZ";
    }

    @Override
    public void onEnable() {
        target = null;
        G = 0;
        damageReceived = false;
        totalAttacksSent = 0;
        blockHolder.release();
        if (debug.getValue()) ChatUtility.debug("NoXZVelocity enabled | Method: " + method.getValue() + " | Hits: " + hits.getValue().intValue() + " | WTap: " + wtap.getValue());
        super.onEnable();
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket motionPacket) {
            if (motionPacket.getEntityId() != mc.player.getId()) {
                return;
            }

            double velX = motionPacket.getVelocity().x;
            double velY = motionPacket.getVelocity().y;
            double velZ = motionPacket.getVelocity().z;

            if (debug.getValue()) {
                ChatUtility.debug("Velocity packet received 閳?vel: (" + String.format("%.2f", velX) + ", " + String.format("%.2f", velY) + ", " + String.format("%.2f", velZ) + ") | hurtTime: " + mc.player.hurtTime + " | method: " + method.getValue() + " | WTap: " + wtap.getValue());
            }

            if (mc.player.hurtTime > 0) {
                damageReceived = true;
                if (debug.getValue()) ChatUtility.debug("Damage confirmed via hurtTime");
            }

            target = getAttackTarget();

            if (debug.getValue()) {
                ChatUtility.debug("KillAura target: " + (target != null ? target.getName().getString() : "null"));
            }

            if (method.getValue() == Method.PRE && damageReceived) {
                damageReceived = false;
                G = hits.getValue().intValue();
                totalAttacksSent = 0;
                if (debug.getValue()) {
                    ChatUtility.debug("PRE mode triggered 閳?queued " + G + " attack(s) | WTap: " + wtap.getValue());
                }
            }

            // Block velocity packet temporarily
            blockHolder.block();
        }
    }

    @Subscribe
    public void onVelocityUpdate(final VelocityUpdateEvent event) {
        if (this.module.isInvalid()) return;
        if (mc.player == null || mc.world == null) return;

        // Cancel the original velocity
        event.setCancelled();

        // Keep vertical velocity (Y), cancel horizontal (XZ)
        mc.player.setVelocity(0, event.getVelocityY(), 0);

        if (debug.getValue()) {
            ChatUtility.debug("VelocityUpdateEvent 閳?cancelled XZ, kept Y: " + String.format("%.2f", event.getVelocityY()));
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null) {
            return;
        }
        if (target != null && G > 0) {
            if (method.getValue() == Method.PRE) {
                while (G >= 1) {
                    performAttack();
                    --G;
                    totalAttacksSent++;
                    if (debug.getValue()) ChatUtility.debug("Attack sent (" + totalAttacksSent + "/" + (totalAttacksSent + G) + ")");
                }
            } else if (method.getValue() == Method.ONE_TICK) {
                if (G >= 1) {
                    performAttack();
                    totalAttacksSent++;
                    if (debug.getValue()) ChatUtility.debug("Attack sent (" + totalAttacksSent + ")");
                }
                --G;
            }

            if (G == 0) {
                blockHolder.release();
                if (debug.getValue()) {
                    ChatUtility.debug("Hit sync complete 閳?" + totalAttacksSent + " attack(s) sent");
                }
            }
        } else if (target == null && G > 0 && debug.getValue()) {
            ChatUtility.debug("No target, " + G + " attack(s) pending");
        }

        // Release block holder if not attacking anymore
        if (G == 0 && blockHolder.isBlocking()) {
            blockHolder.release();
        }
    }

    private void performAttack() {
        if (mc.player == null || target == null) return;

        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));

        double multiplier = wtap.getValue() ? 0.3 : 0.6;
        mc.player.setVelocity(mc.player.getVelocity().multiply(multiplier, 1.0, multiplier));

        mc.player.setSprinting(false);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private Entity getAttackTarget() {
        KillAuraModule aura = OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (aura == null || !aura.isEnabled()) {
            if (debug.getValue()) ChatUtility.debug("KillAura not available or disabled");
            return null;
        }
        if (aura.getTargeting() == null || aura.getTargeting().getTarget() == null) {
            if (debug.getValue()) ChatUtility.debug("KillAura has no target");
            return null;
        }
        Entity entity = aura.getTargeting().getTarget().getEntity();
        if (entity == null && debug.getValue()) ChatUtility.debug("KillAura target entity is null");
        return entity;
    }

    @Override
    public void onDisable() {
        if (debug.getValue()) {
            ChatUtility.debug("NoXZVelocity disabled 閳?total attacks sent this session: " + totalAttacksSent);
        }
        target = null;
        G = 0;
        damageReceived = false;
        totalAttacksSent = 0;
        blockHolder.release();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return AntiKBModule.Mode.NOXZ;
    }

    public enum Method {
        PRE("Pre"),
        ONE_TICK("OneTick");

        private final String name;

        Method(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}