package com.faketils.features;

import com.faketils.Faketils;
import com.faketils.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

public final class Hunting {
    private static final double REEL_DETECTION_RANGE = 16.0;

    private static boolean reelPromptVisible = false;

    private Hunting() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Hunting::onTick);
    }

    private static void onTick(Minecraft client) {
        if (!Faketils.config().autoReel || client.player == null || client.level == null) {
            reelPromptVisible = false;
            return;
        }

        ItemStack heldItem = client.player.getMainHandItem();
        String heldItemName = Utils.stripColorCodes(heldItem.getHoverName().getString()).toLowerCase();
        if (!heldItemName.contains("lasso")) {
            reelPromptVisible = false;
            return;
        }

        boolean reelDetected = false;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.distanceToSqr(client.player) > REEL_DETECTION_RANGE * REEL_DETECTION_RANGE ||
                    !stand.hasCustomName()) {
                continue;
            }

            if (Utils.stripColorCodes(stand.getCustomName().getString()).toUpperCase().contains("REEL")) {
                reelDetected = true;
                break;
            }
        }

        if (reelDetected && !reelPromptVisible && client.gameMode != null) {
            Utils.simulateUseItem(client.gameMode);
            Utils.log("Hunting Lasso reel prompt detected.");
        }

        reelPromptVisible = reelDetected;
    }
}
