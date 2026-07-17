package wtf.opal.client.feature.helper.impl.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.server.KnownServer;
import wtf.opal.client.feature.helper.impl.target.impl.Target;
import wtf.opal.client.feature.helper.impl.target.impl.TargetLivingEntity;
import wtf.opal.client.feature.helper.impl.target.impl.TargetPlayer;

import java.util.*;

import static wtf.opal.client.Constants.mc;

public final class TargetList {
    private final Map<Integer, Target<?>> targetMap = new HashMap<>();

    public void tick() {
        if (mc.world == null) {
            this.targetMap.clear();
            return;
        }
        this.removeInvalidTargets();
        this.addTargets();
    }

    private void removeInvalidTargets() {
        for (final Iterator<Target<?>> iterator = this.targetMap.values().iterator(); iterator.hasNext(); ) {
            final Target<?> target = iterator.next();
            final LivingEntity entity = target.getEntity();
            if (!mc.world.hasEntity(entity) || !entity.isAlive() || entity.deathTime > 0) {
                iterator.remove();
            }
        }
    }

    private void addTargets() {
        final KnownServer currentServer = LocalDataWatch.get().getKnownServerManager().getCurrentServer();

        for (final Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity livingEntity)) {
                continue;
            }

            if (currentServer != null && !currentServer.isValidTarget(livingEntity)) {
                continue;
            }

            final int entityId = entity.getId();
            Target<?> target = this.getTarget(entityId, null);
            if (target == null) {
                target = this.createNewTarget(entity);
                this.targetMap.put(entityId, target);
            }

            target.setEntity(entity);
        }
    }

    private @NotNull Target<?> createNewTarget(Entity entity) {
        return switch (entity) {
            case PlayerEntity playerEntity -> new TargetPlayer(playerEntity);
            case LivingEntity livingEntity -> new TargetLivingEntity(livingEntity);
            default -> throw new RuntimeException("This should never happen!");
        };
    }

    public <T extends Target<?>> T getTarget(final int entityId, @Nullable final Class<T> clazz) {
        final Target<?> target = this.targetMap.get(entityId);
        if (clazz == null || clazz.isInstance(target)) {
            //noinspection unchecked
            return (T) target;
        }
        return null;
    }

    public boolean hasTarget(final int entityId) {
        return this.targetMap.containsKey(entityId);
    }

    public <T extends Target<?>> List<T> collectTargets(final int flags, @Nullable final Class<T> clazzType) {
        final Iterator<Target<?>> iterator = this.targetMap.values().iterator();
        final List<T> list = new ArrayList<>();
        while (iterator.hasNext()) {
            final Target<?> target = iterator.next();
            if (clazzType != null && !clazzType.isAssignableFrom(target.getClass())) {
                continue;
            }
            if (!target.isMatchingFlags(flags)) {
                continue;
            }
            //noinspection unchecked
            list.add((T) target);
        }
        return list;
    }
}
