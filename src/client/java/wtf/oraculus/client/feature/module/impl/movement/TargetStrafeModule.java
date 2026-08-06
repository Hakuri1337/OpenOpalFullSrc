package wtf.oraculus.client.feature.module.impl.movement;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.math.MathUtility;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.player.RotationUtility;
import wtf.oraculus.utility.render.ColorUtility;

import static wtf.oraculus.client.Constants.mc;

public final class TargetStrafeModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Strafe mode", Mode.CIRCLE);

    private final MultipleBooleanProperty requirements = new MultipleBooleanProperty("Requirements",
            new BooleanProperty("Jump key", true),
            new BooleanProperty("Speed module", true)
    );

    private final NumberProperty range = new NumberProperty("Range", 3F, 0.1F, 6F, 0.1F);
    private final BooleanProperty showRing = new BooleanProperty("Show ring", true);

    private final BooleanProperty auto3rdPerson = new BooleanProperty("Auto 3rd person", false);

    private static final float RING_SEGMENT_THICKNESS = 0.03F;
    private static final int RING_SEGMENT_COUNT = 12;

    private final RingSegment[] ringSegments = new RingSegment[RING_SEGMENT_COUNT];
    private final RingSegment[] innerOutlineRingSegments = new RingSegment[RING_SEGMENT_COUNT];
    private final RingSegment[] outerOutlineRingSegments = new RingSegment[RING_SEGMENT_COUNT];
    private float prevInnerRadius = -1;

    private boolean left, overFall, colliding, active, returnState;
    private float yaw;

    public TargetStrafeModule() {
        super("Target Strafe", "Makes you go in circles around targets.", ModuleCategory.MOVEMENT);
        addProperties(mode, requirements, range, showRing, auto3rdPerson);
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (!shouldRun()) {
            active = false;

            if (auto3rdPerson.getValue() && !mc.options.getPerspective().isFirstPerson() && returnState) {
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                returnState = false;
            }

            return;
        }

        active = true;

        final LivingEntity target = this.getKillAuraTarget();

        if (mc.player.horizontalCollision) {
            if (!colliding) {
                left = !left;
            }
            colliding = true;
        } else {
            colliding = false;
        }

        final Box nextTickBox = mc.player.getBoundingBox().offset(mc.player.getVelocity());
        if (PlayerUtility.isAirUntil(target.getY() - 3, nextTickBox) || PlayerUtility.isOverVoid(nextTickBox)) {
            if (!overFall) {
                left = !left;
            }
            overFall = true;
        } else {
            overFall = false;
        }

        if (auto3rdPerson.getValue() && mc.options.getPerspective().isFirstPerson()) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            returnState = true;
        }

        final double range = this.range.getValue() + (Math.random() / 50);

        final float targetYaw = switch (mode.getValue()) {
            case CIRCLE -> RotationUtility.getRotationFromPosition(target.getEntityPos()).x + 160 * (left ? -1 : 1);
            case BEHIND -> target.getYaw() - 180;
        };

        final Vec3d positionToMove = new Vec3d(
                -MathHelper.sin((float) Math.toRadians(targetYaw)) * range + target.getX(),
                target.getY(),
                MathHelper.cos((float) Math.toRadians(targetYaw)) * range + target.getZ()
        );

        this.yaw = RotationUtility.getRotationFromPosition(positionToMove).x;
    }

    private LivingEntity getKillAuraTarget() {
        return OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class).getTargeting().getTarget().getEntity();
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (!active || !showRing.getValue() || !shouldRun()) {
            return;
        }

        final LivingEntity target = this.getKillAuraTarget();
        final Vec3d position = MathUtility.interpolate(target, event.tickDelta());

        final int blackColor = 0xFF000000;
        final MatrixStack stack = event.matrixStack();
        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();

        stack.push();
        stack.translate(position.x, position.y, position.z);

        this.calculateRingSegments();

//        this.renderRingSegments(stack, ringSegments, colors.first, colors.second);
//        this.renderRingSegments(stack, innerOutlineRingSegments, blackColor, blackColor);
//        this.renderRingSegments(stack, outerOutlineRingSegments, blackColor, blackColor);

        stack.pop();
    }

    @Override
    protected void onDisable() {
        active = false;
        super.onDisable();
    }

    public boolean isActive() {
        return active;
    }

    public float getYaw() {
        return yaw;
    }

    private boolean shouldRun() {
        if (requirements.getProperty("Jump key").getValue() && !PlayerUtility.isKeyPressed(mc.options.jumpKey)) {
            return false;
        }

        final KillAuraModule killAuraModule = OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (!killAuraModule.isEnabled() || !killAuraModule.getTargeting().isTargetSelected()) {
            return false;
        }

        final SpeedModule speedModule = OraculusClient.getInstance().getModuleRepository().getModule(SpeedModule.class);
        return !requirements.getProperty("Speed module").getValue() || speedModule.isEnabled();
    }

//    private void renderRingSegments(final MatrixStack stack, final RingSegment[] segments, final int firstColor, final int secondColor) {
//        WorldRenderer.useBuffer(
//                VertexFormat.DrawMode.QUADS,
//                VertexFormats.POSITION_COLOR,
//                ShaderProgramKeys.POSITION_COLOR,
//                buffer -> {
//                    final Matrix4f matrix = stack.peek().getPositionMatrix();
//
//                    for (int i = 0; i < RING_SEGMENT_COUNT; i++) {
//                        final int prev = (i + RING_SEGMENT_COUNT - 1) % RING_SEGMENT_COUNT;
//
//                        final RingSegment prevSegment = segments[prev];
//                        final RingSegment currSegment = segments[i];
//
//                        buffer.vertex(matrix, prevSegment.innerX, 0, prevSegment.innerZ).color(secondColor).normal(stack.peek(), 0, 0, 0);
//                        buffer.vertex(matrix, prevSegment.outerX, 0, prevSegment.outerZ).color(firstColor).normal(stack.peek(), 0, 0, 0);
//                        buffer.vertex(matrix, currSegment.outerX, 0, currSegment.outerZ).color(firstColor).normal(stack.peek(), 0, 0, 0);
//                        buffer.vertex(matrix, currSegment.innerX, 0, currSegment.innerZ).color(secondColor).normal(stack.peek(), 0, 0, 0);
//                    }
//                }
//        );
//    }

    private void calculateRingSegments() {
        final float innerRadius = (this.range.getValue().floatValue() + 1) - (RING_SEGMENT_THICKNESS / 2);
        if (prevInnerRadius != -1 && prevInnerRadius == innerRadius) {
            return;
        }

        prevInnerRadius = innerRadius;
        final float outlineThickness = RING_SEGMENT_THICKNESS / 2F;

        for (int i = 0; i < RING_SEGMENT_COUNT; i++) {
            final float angle = MathHelper.TAU * ((float) i / RING_SEGMENT_COUNT);

            final float sin = MathHelper.sin(angle);
            final float cos = MathHelper.cos(angle);

            final float mainInnerX = innerRadius * sin;
            final float mainInnerZ = innerRadius * cos;
            final float mainOuterX = (innerRadius + RING_SEGMENT_THICKNESS) * sin;
            final float mainOuterZ = (innerRadius + RING_SEGMENT_THICKNESS) * cos;
            ringSegments[i] = new RingSegment(mainInnerX, mainInnerZ, mainOuterX, mainOuterZ);

            final float outlineInnerInnerRadius = innerRadius - outlineThickness;
            final float innerOutlineInnerX = outlineInnerInnerRadius * sin;
            final float innerOutlineInnerZ = outlineInnerInnerRadius * cos;
            final float innerOutlineOuterX = innerRadius * sin;
            final float innerOutlineOuterZ = innerRadius * cos;
            innerOutlineRingSegments[i] = new RingSegment(innerOutlineInnerX, innerOutlineInnerZ, innerOutlineOuterX, innerOutlineOuterZ);

            final float outlineOuterInnerRadius = innerRadius + RING_SEGMENT_THICKNESS;
            final float outlineOuterOuterRadius = innerRadius + RING_SEGMENT_THICKNESS + outlineThickness;
            final float outerOutlineInnerX = outlineOuterInnerRadius * sin;
            final float outerOutlineInnerZ = outlineOuterInnerRadius * cos;
            final float outerOutlineOuterX = outlineOuterOuterRadius * sin;
            final float outerOutlineOuterZ = outlineOuterOuterRadius * cos;
            outerOutlineRingSegments[i] = new RingSegment(outerOutlineInnerX, outerOutlineInnerZ, outerOutlineOuterX, outerOutlineOuterZ);
        }
    }

    private record RingSegment(float innerX, float innerZ, float outerX, float outerZ) {
    }

    private enum Mode {
        CIRCLE("Circle"),
        BEHIND("Behind");

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
