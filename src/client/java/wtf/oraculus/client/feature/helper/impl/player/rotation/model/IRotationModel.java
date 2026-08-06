package wtf.oraculus.client.feature.helper.impl.player.rotation.model;

import net.minecraft.util.math.Vec2f;

public interface IRotationModel {
    Vec2f tick(Vec2f from, Vec2f to, float timeDelta);
    EnumRotationModel getEnum();
}
