package com.faketils.features;

import com.faketils.Faketils;
import com.faketils.events.RotationHandler;
import com.faketils.mixin.PlayerInventoryAccessor;
import com.faketils.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

public class Fishing {

    private static final Minecraft mc = Minecraft.getInstance();
    private static int clickTimer = 0;
    private static boolean hasClickedOnce = false;
    private static boolean scheduledClick = false;
    private static int delayTimer = 0;

    private static int weaponState = 0;
    private static int originalSlot = 0;
    private static int weaponSlot = 0;
    private static int delayCounter = 0;
    private static int clickCount = 0;
    private static long nextWeaponUseTick = 0L;

    private static final float LOOK_DOWN_PITCH = 89f;
    private static float savedYaw = 0f;
    private static float savedPitch = 0f;

    private static boolean slugFishingActive = false;
    private static long slugStartTime = 0L;
    private static int lastBobberId = -1;

    private static String pendingPetSwap = null;
    private static long petSwapTimeout = 0;
    private static int petScreenDelay = 0;

    private static final Set<Integer> handledArmorStands = new HashSet<>();

    private static final int STATE_IDLE              = 0;
    private static final int STATE_PRE_ROTATE        = 1;
    private static final int STATE_SWITCH_TO_WEAPON  = 3;
    private static final int STATE_WAIT_SWITCH       = 4;
    private static final int STATE_CLICK_WEAPON      = 5;
    private static final int STATE_WAIT_SWITCH_BACK  = 6;
    private static final int STATE_POST_ROTATE       = 8;
    private static final int STATE_DONE              = 9;
    private static final int WEAPON_RESTORE_DELAY_TICKS = 1;
    private static final int ROD_RECAST_DELAY_TICKS = 1;
    private static final int MULTI_WEAPON_USE_COOLDOWN_TICKS = 2;

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            onClientTick();
            onRenderWorldLast();
        });
    }

    private static void onRenderWorldLast() {
        if (!Utils.isInSkyblock() || !Faketils.config().fishingHelper) return;
        if (mc.screen != null || mc.player == null || mc.level == null) return;

        ItemStack heldItem = mc.player.getMainHandItem();
        if (!(heldItem.getItem() instanceof FishingRodItem)) return;

        handledArmorStands.removeIf(id -> mc.level.getEntity(id) == null);

        for (ArmorStand armorStand : mc.level.getEntitiesOfClass(ArmorStand.class,
                mc.player.getBoundingBox().inflate(64), e -> true)) {

            if (armorStand.isRemoved() || !armorStand.hasCustomName()) continue;

            Component customName = armorStand.getCustomName();
            if (customName == null) continue;

            if (customName.getString().equals("!!!")) {
                if (!handledArmorStands.contains(armorStand.getId())) {
                    if (delayTimer == 0) {
                        scheduledClick = true;
                        handledArmorStands.add(armorStand.getId());
                        Utils.log("Detected ArmorStand (ID: " + armorStand.getId() + ")");
                    }
                }
            }
        }
    }

    private static void onClientTick() {
        if (!Utils.isInSkyblock() || !Faketils.config().fishingHelper) return;
        if (mc.player == null || mc.gameMode == null) return;

        if (pendingPetSwap != null && mc.screen instanceof AbstractContainerScreen containerScreen) {

            if (petScreenDelay < 9) {
                petScreenDelay++;
                return;
            }

            onScreenOpen(containerScreen);
            petScreenDelay = 0;
        }

        if (petSwapTimeout != 0 && System.currentTimeMillis() > petSwapTimeout) {
            Utils.log("Pet swap timed out");
            pendingPetSwap = null;
            petSwapTimeout = 0;
        }

        var player = mc.player;
        var gameMode = mc.gameMode;
        PlayerInventoryAccessor inventory = (PlayerInventoryAccessor) player.getInventory();

        if (Faketils.config().slugFishing) {
            var bobber = player.fishing;
            if (bobber != null) {
                if (bobber.getId() != lastBobberId) {
                    lastBobberId = bobber.getId();
                    slugStartTime = System.currentTimeMillis();
                    slugFishingActive = true;
                    Utils.log("Slug fishing cooldown started.");
                }
            } else if (lastBobberId != -1) {
                lastBobberId = -1;
                slugFishingActive = false;
            }

            if (slugFishingActive) {
                long elapsed = System.currentTimeMillis() - slugStartTime;
                if (elapsed < 10_000) {
                    scheduledClick = false;
                    return;
                } else {
                    slugFishingActive = false;
                    Utils.log("Slug fishing cooldown ended.");
                }
            }
        }

        if (scheduledClick) {
            clickTimer = ROD_RECAST_DELAY_TICKS;
            hasClickedOnce = true;

            Utils.simulateUseItem(gameMode);
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            scheduledClick = false;

            if (Faketils.config().fishingHelperKilling && !Faketils.config().fishingHelperKillingWeapon.isEmpty()) {
                WeaponResult weapon = WeaponDetector.findWeapon();
                if (weapon != null) {
                    originalSlot = inventory.getSelectedSlot();
                    weaponSlot = weapon.slot;
                    nextWeaponUseTick = 0L;

                    if (Faketils.config().fishingLookDown) {
                        savedYaw = player.getYRot();
                        savedPitch = player.getXRot();
                        RotationHandler.setTarget(savedYaw, LOOK_DOWN_PITCH);
                        weaponState = STATE_PRE_ROTATE;
                    } else {
                        weaponState = STATE_SWITCH_TO_WEAPON;
                    }
                    Utils.log("Weapon found in slot " + weaponSlot + ", switching...");
                }
            }
        }

        if (weaponState > STATE_IDLE) {
            switch (weaponState) {
                case STATE_PRE_ROTATE -> {
                    if (!RotationHandler.active) {
                        weaponState = STATE_SWITCH_TO_WEAPON;
                    }
                }
                case STATE_SWITCH_TO_WEAPON -> {
                    inventory.setSelectedSlot(weaponSlot);
                    delayCounter = 0;
                    weaponState = STATE_WAIT_SWITCH;
                }
                case STATE_WAIT_SWITCH -> {
                    if (delayCounter-- <= 0) {
                        weaponState = STATE_CLICK_WEAPON;
                        delayCounter = 0;
                    }
                }
                case STATE_CLICK_WEAPON -> {
                    if (mc.level != null && mc.level.getGameTime() < nextWeaponUseTick) {
                        break;
                    }
                    if (delayCounter-- <= 0) {
                        Utils.simulateUseItem(gameMode);
                        clickCount++;

                        int maxClicks = Faketils.config().fishingHelperKillingAmount;
                        if (clickCount >= maxClicks) {
                            clickCount = 0;
                            nextWeaponUseTick = 0L;
                            weaponState = STATE_WAIT_SWITCH_BACK;
                            delayCounter = WEAPON_RESTORE_DELAY_TICKS;
                        } else {
                            nextWeaponUseTick = mc.level == null
                                    ? 0L
                                    : mc.level.getGameTime() + MULTI_WEAPON_USE_COOLDOWN_TICKS;
                            delayCounter = 0;
                        }
                    }
                }
                case STATE_WAIT_SWITCH_BACK -> {
                    if (delayCounter-- <= 0) {
                        inventory.setSelectedSlot(originalSlot);
                        Utils.log("Switched back to original slot.");
                        if (Faketils.config().fishingLookDown) {
                            RotationHandler.setTarget(savedYaw, savedPitch);
                            weaponState = STATE_POST_ROTATE;
                        } else {
                            weaponState = STATE_DONE;
                        }
                    }
                }
                case STATE_POST_ROTATE -> {
                    if (!RotationHandler.active) {
                        weaponState = STATE_DONE;
                    }
                }
                case STATE_DONE -> {
                    weaponState = STATE_IDLE;
                    Utils.log("Weapon sequence finished, ready for reel.");
                }
            }
            return;
        }

        if (clickTimer > 0) {
            clickTimer--;
            if (clickTimer == 0 && hasClickedOnce) {
                Utils.simulateUseItem(gameMode);
                petSwap();
                hasClickedOnce = false;
            }
        }
    }

    private static void onScreenOpen(AbstractContainerScreen containerScreen) {

        if (pendingPetSwap == null) return;

        var player = mc.player;
        if (player == null) return;

        AbstractContainerMenu menu = containerScreen.getMenu();

        for (int slot = 0; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();

            if (stack.isEmpty()) continue;

            Component displayName = stack.getHoverName();

            if (displayName.getString()
                    .trim()
                    .toLowerCase()
                    .contains(pendingPetSwap.trim().toLowerCase())) {

                Utils.log("Found pet '" + pendingPetSwap + "' in slot " + slot);

                assert mc.gameMode != null;

                mc.gameMode.handleContainerInput(
                        menu.containerId,
                        slot,
                        0,
                        ContainerInput.PICKUP,
                        player
                );

                pendingPetSwap = null;
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                        mc.execute(() -> {
                            if (mc.player != null) {
                                mc.player.closeContainer();
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                return;
            }
        }

        Utils.log("Pet '" + pendingPetSwap + "' not found in menu");
    }

    public static void petSwap() {
        if (Faketils.config().fishingPetSwap.trim().isEmpty()) return;

        pendingPetSwap = Faketils.config().fishingPetSwap;
        petSwapTimeout = System.currentTimeMillis() + 2000;

        new Thread(() -> {
            try {
                Thread.sleep(100);
                petScreenDelay = 0;
                mc.getConnection().sendChat("/pets");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public static class WeaponResult {
        public final int slot;
        public final KeyMapping hotbarKey;

        public WeaponResult(int slot, KeyMapping hotbarKey) {
            this.slot = slot;
            this.hotbarKey = hotbarKey;
        }
    }

    public static class WeaponDetector {
        public static WeaponResult findWeapon() {
            var player = mc.player;
            if (player == null) return null;

            String targetName = Faketils.config().fishingHelperKillingWeapon;

            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty()) continue;

                Component displayName = stack.getHoverName();
                if (displayName.getString().contains(targetName)) {
                    Utils.log("Found weapon '" + targetName + "' in slot " + slot);
                    return new WeaponResult(slot, mc.options.keyHotbarSlots[slot]);
                }
            }
            return null;
        }
    }
}
