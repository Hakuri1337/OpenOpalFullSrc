package wtf.oraculus.scripting.impl.proxy;

import net.minecraft.entity.Entity;
import org.joml.Vector2d;
import wtf.oraculus.utility.player.MoveUtility;

public class MovementProxy {

    public double getBlocksPerSecond() {
        return MoveUtility.getBlocksPerSecond();
    }

    public double[] yawPos(float yaw, double value) {
        return MoveUtility.yawPos(yaw, value);
    }

    public void setSpeed(Entity entity, double speed, double yaw) {
        MoveUtility.setSpeed(entity, speed, yaw);
    }

    public void setSpeed(double speed) {
        MoveUtility.setSpeed(speed);
    }

    public void setSpeed(double speed, double strafePercentage) {
        MoveUtility.setSpeed(speed, strafePercentage);
    }

    public void setSpeed(double speed, float yaw) {
        MoveUtility.setSpeed(speed, yaw);
    }

    public double getSwiftnessSpeed(double speed, double swiftnessMultiplier) {
        return MoveUtility.getSwiftnessSpeed(speed, swiftnessMultiplier);
    }

    public double getSwiftnessSpeed(double speed) {
        return MoveUtility.getSwiftnessSpeed(speed);
    }

    public double getSpeed() {
        return MoveUtility.getSpeed();
    }

    public float getMoveYaw(Vector2d from, Vector2d to) {
        return MoveUtility.getMoveYaw(from, to);
    }

    public float getMoveYaw() {
        return MoveUtility.getMoveYaw();
    }

    public float getDirectionDegrees() {
        return MoveUtility.getDirectionDegrees();
    }

    public double getDirectionRadians() {
        return MoveUtility.getDirectionRadians();
    }

    public float getDirectionDegrees(float yaw) {
        return MoveUtility.getDirectionDegrees(yaw);
    }

    public double getDirectionRadians(float yaw) {
        return MoveUtility.getDirectionRadians(yaw);
    }

    public double getDirection(float rotationYaw, double moveForward, double moveStrafing) {
        return MoveUtility.getDirection(rotationYaw, moveForward, moveStrafing);
    }

    public boolean isMoving() {
        return MoveUtility.isMoving();
    }

}
