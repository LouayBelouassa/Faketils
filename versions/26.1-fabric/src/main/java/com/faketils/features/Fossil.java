package com.faketils.features;

import com.faketils.Faketils;
import com.faketils.events.PacketEvent;
import com.faketils.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fossil {
    private static final String FOSSIL_EXCAVATOR_TITLE = "Fossil Excavator";
    // Second chest row, fourth column: (1 * 9) + 3.
    private static final int ROW_TWO_SLOT_FOUR = 12;
    // Fourth chest row, fifth column: (3 * 9) + 4.
    private static final int ROW_FOUR_SLOT_FIVE = 31;
    private static final int EXCAVATOR_SLOT_COUNT = 54;
    private static final int INITIAL_SLOT_CHECK_DELAY = 300;
    private static final int PANE_CLICK_DELAY_MIN = 250;
    private static final int PANE_CLICK_DELAY_MAX = 350;
    private static final int MAX_REOPEN_ATTEMPTS = 3;
    private static final int INITIAL_REOPEN_DELAY = 1000;
    private static final int REOPEN_ATTEMPT_DELAY = 1000;
    private static final Random RNG = new Random();
    private static final Pattern CHISEL_CHARGES_PATTERN = Pattern.compile("(?i)chisel\\s+charges\\s+remaining\\s*:\\s*(\\d+)");

    private static boolean excavatorOpen = false;
    private static boolean initialSlotCheckComplete = false;
    private static boolean targetClicked = false;
    private static boolean awaitingCompletion = false;
    private static boolean reopenPending = false;
    private static int reopenAttempts = 0;
    private static long initialSlotCheckAt = -1;
    private static long targetClickAt = -1;
    private static long nextPaneClickAt = -1;
    private static long nextReopenAttemptAt = -1;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> onScreenOpen(screen));
        ClientTickEvents.END_CLIENT_TICK.register(Fossil::onTick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message));
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> onChat(message));
        PacketEvent.registerReceive((packet, connection) -> {
            if (packet instanceof ClientboundSetTitleTextPacket titlePacket) {
                Faketils.mc.execute(() -> onChat(titlePacket.text()));
            }
        });
    }

    private static void onScreenOpen(Screen screen) {
        if (!Faketils.config().fossilExcavation || !(screen instanceof AbstractContainerScreen)) {
            clear();
            return;
        }

        excavatorOpen = screen.getTitle().getString().equals(FOSSIL_EXCAVATOR_TITLE);
        if (excavatorOpen) {
            initialSlotCheckComplete = false;
            initialSlotCheckAt = System.currentTimeMillis() + INITIAL_SLOT_CHECK_DELAY;
            awaitingCompletion = false;
            clearReopenState();
            Utils.log("Fossil Excavator detected");
        }
    }

    private static void onTick(Minecraft client) {
        if (!Faketils.config().fossilExcavation) {
            clearReopenState();
            awaitingCompletion = false;
            clear();
            return;
        }

        tickReopen(client);

        if (!excavatorOpen || client.player == null) {
            return;
        }

        if (!(client.screen instanceof AbstractContainerScreen)) {
            clear();
            return;
        }

        AbstractContainerMenu handler = client.player.containerMenu;

        if (handler.slots.size() <= ROW_FOUR_SLOT_FIVE) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!initialSlotCheckComplete && now < initialSlotCheckAt) {
            return;
        }

        boolean triggerHasItem = handler.slots.get(ROW_TWO_SLOT_FOUR).hasItem();
        if (!initialSlotCheckComplete) {
            initialSlotCheckComplete = true;
            if (!triggerHasItem) {
                client.player.closeContainer();
                client.player.sendSystemMessage(Component.literal("§7[§bFaketils§7] §cOut of Scrap"));
                client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
                clear();
                return;
            }
        }

        if (targetClicked) {
            tickExcavation(client, handler, now);
            return;
        }

        if (!triggerHasItem) {
            targetClickAt = -1;
            return;
        }

        if (targetClickAt == -1) {
            targetClickAt = now + RNG.nextInt(201) + 300;
            return;
        }

        if (now >= targetClickAt) {
            Experiments.clickSlot(client, handler, ROW_FOUR_SLOT_FIVE, 0, ContainerInput.PICKUP);
            targetClicked = true;
            awaitingCompletion = true;
            nextPaneClickAt = -1;
        }

        if (targetClicked) {
            tickExcavation(client, handler, now);
        }
    }

    private static void onChat(Component message) {
        if (!Faketils.config().fossilExcavation || !awaitingCompletion) {
            return;
        }

        String text = Utils.stripColorCodes(message.getString());
        if (text.toUpperCase().contains("EXCAVATION COMPLETE")) {
            requestReopen();
            awaitingCompletion = false;
            Utils.log("Excavation complete; reopening Fossil Excavator.");
        }
    }

    private static void tickExcavation(Minecraft client, AbstractContainerMenu handler, long now) {
        if (hasNoCharges(handler)) {
            client.player.closeContainer();
            awaitingCompletion = false;
            requestReopen();
            clear();
            return;
        }

        if (nextPaneClickAt == -1) {
            nextPaneClickAt = now + RNG.nextInt(PANE_CLICK_DELAY_MAX - PANE_CLICK_DELAY_MIN + 1) + PANE_CLICK_DELAY_MIN;
            return;
        }

        if (now < nextPaneClickAt) {
            return;
        }

        int slot = findNextExcavationSlot(handler);
        if (slot == -1) {
            nextPaneClickAt = -1;
            return;
        }

        Experiments.clickSlot(client, handler, slot, 0, ContainerInput.PICKUP);
        nextPaneClickAt = -1;
    }

    private static boolean hasNoCharges(AbstractContainerMenu handler) {
        int slotCount = Math.min(EXCAVATOR_SLOT_COUNT, handler.slots.size());

        for (int slot = 0; slot < slotCount; slot++) {
            ItemLore lore = handler.slots.get(slot).getItem().get(DataComponents.LORE);
            if (lore == null) {
                continue;
            }

            for (Component line : lore.lines()) {
                Matcher matcher = CHISEL_CHARGES_PATTERN.matcher(Utils.stripColorCodes(line.getString()));
                if (matcher.find() && Integer.parseInt(matcher.group(1)) == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int findNextExcavationSlot(AbstractContainerMenu handler) {
        List<Integer> brownSlots = new ArrayList<>();
        int slotCount = Math.min(EXCAVATOR_SLOT_COUNT, handler.slots.size());

        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = handler.slots.get(slot).getItem();
            if (stack.is(Items.LIME_STAINED_GLASS_PANE) || stack.is(Items.GREEN_STAINED_GLASS_PANE)) {
                return slot;
            }
            if (stack.is(Items.BROWN_STAINED_GLASS_PANE)) {
                brownSlots.add(slot);
            }
        }

        return brownSlots.isEmpty() ? -1 : brownSlots.get(RNG.nextInt(brownSlots.size()));
    }

    private static void tickReopen(Minecraft client) {
        if (!reopenPending) {
            return;
        }

        if (isFossilExcavatorScreen(client.screen)) {
            clearReopenState();
            return;
        }

        if (client.player == null || client.gameMode == null || client.screen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (nextReopenAttemptAt != -1 && now < nextReopenAttemptAt) {
            return;
        }

        if (reopenAttempts >= MAX_REOPEN_ATTEMPTS) {
            client.player.sendSystemMessage(Component.literal("§7[§bFaketils§7] §cCould not reopen Fossil Excavator."));
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
            clearReopenState();
            return;
        }

        KeyMapping.click(client.options.keyUse.getDefaultKey());
        reopenAttempts++;
        nextReopenAttemptAt = now + REOPEN_ATTEMPT_DELAY;
    }

    private static boolean isFossilExcavatorScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen && screen.getTitle().getString().equals(FOSSIL_EXCAVATOR_TITLE);
    }

    private static void requestReopen() {
        reopenPending = true;
        reopenAttempts = 0;
        nextReopenAttemptAt = System.currentTimeMillis() + INITIAL_REOPEN_DELAY;
    }

    private static void clearReopenState() {
        reopenPending = false;
        reopenAttempts = 0;
        nextReopenAttemptAt = -1;
    }

    private static void clear() {
        excavatorOpen = false;
        initialSlotCheckComplete = false;
        targetClicked = false;
        initialSlotCheckAt = -1;
        targetClickAt = -1;
        nextPaneClickAt = -1;
    }
}
