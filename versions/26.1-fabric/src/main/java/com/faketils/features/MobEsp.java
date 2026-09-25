package com.faketils.features;

import com.faketils.Faketils;
import com.faketils.events.FtEvent;
import com.faketils.events.FtEventBus;
import com.faketils.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Uses the same world marker renderer as PestHelper for matching SkyBlock name stands. */
public final class MobEsp {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Set<ArmorStand> trackedStands = new HashSet<>();
    private static Object trackedLevel;

    private MobEsp() {
    }

    public static void initialize() {
        FtEventBus.onEvent(FtEvent.WorldRender.class, MobEsp::onRenderWorldLast);
    }

    private static void onRenderWorldLast(FtEvent.WorldRender event) {
        String targetName = Faketils.config().mobEspName.trim();
        if (!Faketils.config().mobEsp || targetName.isEmpty() || MC.player == null || MC.level == null) {
            trackedStands.clear();
            trackedLevel = null;
            return;
        }

        if (trackedLevel != MC.level) {
            trackedStands.clear();
            trackedLevel = MC.level;
        }

        Vec3 cameraPos = event.camera.position();
        List<String> normalizedTargets = List.of(targetName.toLowerCase(Locale.ROOT).split(","))
                .stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
        if (normalizedTargets.isEmpty()) return;
        double range = Faketils.config().mobEspRange;
        double rangeSquared = range * range;
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
            double xDistance = stand.getX() - MC.player.getX();
            double zDistance = stand.getZ() - MC.player.getZ();
            if (xDistance * xDistance + zDistance * zDistance > rangeSquared) continue;

            String standName = stand.getCustomName().getString()
                    .replaceAll("§.", "")
                    .toLowerCase(Locale.ROOT);
            if (normalizedTargets.stream().noneMatch(standName::contains)) continue;

            trackedStands.add(stand);
        }

        // Once detected, keep rendering the stand's marker even when it leaves entitiesForRendering().
        trackedStands.removeIf(stand -> stand.isRemoved() || !stand.isAlive());
        for (ArmorStand stand : trackedStands) {
            String mobName = stand.hasCustomName()
                    ? stand.getCustomName().getString().replaceAll("§.", "")
                    : targetName;
            RenderUtils.renderWaypointMarker(
                    stand.getPosition(event.tickDelta).add(0, -0.5, 0),
                    cameraPos,
                    0xFF00FFFF,
                    mobName,
                    event
            );
        }
    }
}
