package com.faketils;

import com.faketils.commands.Command;
import com.faketils.config.Config;
import com.faketils.events.FlyHandler;
import com.faketils.events.RotationHandler;
import com.faketils.features.*;
import com.faketils.utils.FarmingTitleRenderer;
import com.faketils.utils.Utils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class Faketils implements ClientModInitializer {
    public static final Minecraft mc = Minecraft.getInstance();
    public static Screen currentGui = null;
    public static final String MOD_ID = "faketils";
    public static final String NAME = "Faketils";

    public static Config config() {
        return Config.HANDLER.instance();
    }

    @Override
    public void onInitializeClient() {
        Config.HANDLER.load();

        Command.register();

        RotationHandler.init();
        FlyHandler.init();
        TipAll.initialize();
        Experiments.init();
        Fossil.init();
        Harp.init();
        Fishing.initialize();
        Farming.initialize();
        FarmingTitleRenderer.init();
        SphinxSolver.init();
        PestHelper.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null) {
                if (currentGui != null) {
                    client.setScreen(currentGui);
                    currentGui = null;
                }
                if (client.level.getGameTime() % 20 == 0L) {
                    Utils.isInSkyblock();
                }
            }
        });
    }
}
