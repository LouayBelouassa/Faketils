package com.faketils.features;

import com.faketils.Faketils;
import com.faketils.events.FtEvent;
import com.faketils.events.FtEventBus;
import com.faketils.events.RotationHandler;
import com.faketils.utils.RenderUtils;
import com.faketils.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Aims at and mines nearby gemstone blocks while a drill is held. */
public final class Mining {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final double NORMAL_REACH = 4.5;
    private static final double NORMAL_REACH_SQUARED = NORMAL_REACH * NORMAL_REACH;

    /** The block the helper is actively rotating toward and mining. */
    private static BlockPos currentTarget;
    /** The most recent gemstone block confirmed to have been broken. */
    private static BlockPos lastBrokenTarget;
    /** The gemstone block that was under the crosshair on the preceding tick. */
    private static BlockPos observedMinedTarget;
    private static boolean ownsRotation;
    private static boolean ownsAttack;

    private Mining() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
        FtEventBus.onEvent(FtEvent.WorldRender.class, Mining::onWorldRender);
    }

    private static void onClientTick() {
        if (!Faketils.config().drillPaneAim || MC.player == null || MC.level == null || !isHoldingDrill()) {
            clearTarget();
            return;
        }

        updateBrokenTarget();

        BlockPos target = findNextTarget();
        if (target == null) {
            stopTargeting();
            return;
        }

        // Keep mining held for the entire valid-target chain, including while rotating to the next block.
        MC.options.keyAttack.setDown(true);
        ownsAttack = true;

        if (!target.equals(currentTarget)) {
            Vec3 eyePosition = MC.player.getEyePosition();
            Vec3 targetPosition = Vec3.atCenterOf(target);
            double x = targetPosition.x - eyePosition.x;
            double y = targetPosition.y - eyePosition.y;
            double z = targetPosition.z - eyePosition.z;
            double horizontalDistance = Math.sqrt(x * x + z * z);

            float yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(y, horizontalDistance));
            RotationHandler.setTarget(yaw, pitch);
            currentTarget = target;
            ownsRotation = true;
        }
    }

    private static void onWorldRender(FtEvent.WorldRender event) {
        if (!Faketils.config().drillPaneAim || !Faketils.config().showGemstoneTarget ||
                MC.player == null || MC.level == null || currentTarget == null || !isHoldingDrill()) {
            return;
        }

        if (!isGemstoneTarget(currentTarget)) {
            return;
        }

        RenderUtils.renderWaypointMarker(
                Vec3.atCenterOf(currentTarget),
                event.camera.position(),
                0xFFFFAA00,
                "Gemstone Target",
                event
        );
    }

    private static boolean isHoldingDrill() {
        ItemStack heldItem = MC.player.getMainHandItem();
        String itemName = Utils.stripColorCodes(heldItem.getHoverName().getString()).toLowerCase(Locale.ROOT);
        return itemName.contains("drill");
    }

    /**
     * Retains a valid visible target. When it becomes unavailable, candidates are ranked
     * relative to the last block confirmed to have been broken, rather than a stale target.
     */
    private static BlockPos findNextTarget() {
        Vec3 eyePosition = MC.player.getEyePosition();
        BlockPos origin = BlockPos.containing(eyePosition);

        if (isUsableTarget(currentTarget, eyePosition)) {
            return currentTarget;
        }
        currentTarget = null;

        Vec3 searchCenter = lastBrokenTarget != null ? Vec3.atCenterOf(lastBrokenTarget) : eyePosition;
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!isGemstoneTarget(candidate)) continue;

                    Vec3 candidateCenter = Vec3.atCenterOf(candidate);
                    if (eyePosition.distanceToSqr(candidateCenter) > NORMAL_REACH_SQUARED) continue;
                    if (!isVisible(candidate, eyePosition)) continue;

                    double distance = searchCenter.distanceToSqr(candidateCenter);
                    if (distance < closestDistance) {
                        closest = candidate;
                        closestDistance = distance;
                    }
                }
            }
        }
        return closest;
    }

    /** Records only blocks that actually disappeared while being mined; losing sight does not count. */
    private static void updateBrokenTarget() {
        if (currentTarget != null && !isGemstoneTarget(currentTarget)) {
            lastBrokenTarget = currentTarget;
            currentTarget = null;
        }

        if (observedMinedTarget != null && MC.options.keyAttack.isDown() && !isGemstoneTarget(observedMinedTarget)) {
            lastBrokenTarget = observedMinedTarget;
            if (observedMinedTarget.equals(currentTarget)) {
                currentTarget = null;
            }
        }

        observedMinedTarget = null;
        if (MC.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK &&
                isGemstoneTarget(hit.getBlockPos())) {
            observedMinedTarget = hit.getBlockPos();
        }
    }

    private static boolean isUsableTarget(BlockPos pos, Vec3 eyePosition) {
        return pos != null && isGemstoneTarget(pos) &&
                eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) <= NORMAL_REACH_SQUARED &&
                isVisible(pos, eyePosition);
    }

    private static boolean isVisible(BlockPos pos, Vec3 eyePosition) {
        HitResult hit = MC.level.clip(new ClipContext(
                eyePosition,
                Vec3.atCenterOf(pos),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                MC.player
        ));
        return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos);
    }

    private static boolean isGemstoneTarget(BlockPos pos) {
        var block = MC.level.getBlockState(pos).getBlock();
        return block instanceof StainedGlassPaneBlock || block instanceof StainedGlassBlock;
    }

    private static void clearTarget() {
        stopTargeting();
        lastBrokenTarget = null;
        observedMinedTarget = null;
    }

    private static void stopTargeting() {
        releaseAttack();
        if (ownsRotation) {
            RotationHandler.reset();
        }
        currentTarget = null;
        ownsRotation = false;
    }

    private static void releaseAttack() {
        if (ownsAttack) {
            MC.options.keyAttack.setDown(false);
            ownsAttack = false;
        }
    }
}
