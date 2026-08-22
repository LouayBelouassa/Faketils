package com.faketils.features;

import com.faketils.Faketils;
import com.faketils.config.Config;
import com.faketils.events.*;
import com.faketils.mixin.PlayerInventoryAccessor;
import com.faketils.utils.FarmingWaypoints;
import com.faketils.utils.RenderUtils;
import com.faketils.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Farming {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random random = new Random();

    private static final KeyMapping toggleKey = Faketils.config().toggleMacro;
    private static final KeyMapping pauseKey = Faketils.config().pauseMacro;
    private static final KeyMapping resetKey = Faketils.config().resetFakeFails;

    public static boolean keysAreHeld = false;

    private static enum RodPhase { IDLE, SWAP_TO_ROD, WAIT_BEFORE_CLICK, CLICK_ROD, WAIT_BEFORE_RESTORE, RESTORE_SLOT, DONE }
    private static RodPhase rodPhase = RodPhase.IDLE;
    private static long rodPhaseStart = 0;
    private static int originalHotbarSlot = -1;
    private static int rodHotbarSlot = -1;
    private static int pendingDoubleJumpTicks = 0;

    private static enum WPhase { IDLE, OPENING, CLICKING, DONE }
    private static WPhase wPhase = WPhase.IDLE;

    private static enum WardrobePhase { IDLE, OPEN_SENT, WAIT_FOR_OPEN, CLICKING_SLOT, WAIT_AFTER_CLICK, CLOSING, DONE }
    private static WardrobePhase wardrobePhase = WardrobePhase.IDLE;
    private static long wardrobePhaseStart = 0L;
    private static boolean wardrobeSuccess = false;
    private enum EqPhase { IDLE, OPEN_SENT, CLICKING_SLOT, WAIT_AFTER_CLICK, CLOSING, DONE }
    private static EqPhase eqPhase = EqPhase.IDLE;
    private static long eqPhaseStart = 0L;
    private static String eqTargetSlot = "";
    private static long lastSellTime = 0L;

    private static enum EqState { IDLE, OPENING, WAIT_AFTER_OPEN, SEARCHING_ITEMS, PICKUP_CLICKED, PLACE_CLICKED, FINISHED_ITEMS }
    private static EqState eqState = EqState.IDLE;
    private static long eqStateStart = 0L;
    private static int lastProcessedSyncId = -1;
    private static int itemsUsedThisPhase = 0;
    private static long lastClickTime = 0L;
    private static final long MAX_WAIT_PER_ACTION_MS = 4000L;
    private static long lastPestScan = 0L;

    private enum SprayPhase {
        IDLE,
        SWAP_TO_SPRAY,
        WAIT_BEFORE_USE,
        USE,
        WAIT_BEFORE_RESTORE,
        RESTORE_SLOT,
        DONE
    }

    private static boolean isSpraying = false;
    private static long sprayNoneDetectedTime = -1L;
    private static SprayPhase sprayPhase = SprayPhase.IDLE;
    private static long sprayPhaseStart = 0L;
    private static int sprayHotbarSlot = -1;
    private static int originalSpraySlot = -1;

    private static boolean isLcVacuum = false;
    private static volatile Vec3 lastAngryParticlePos = null;
    private static long lastAngryParticleTime = 0L;
    private static volatile boolean lcRunning = false;

    private static int emptyScans = 0;

    private static enum PestPhase { ROOTED, SQUEAKY }
    private static PestPhase currentPestPhase = PestPhase.ROOTED;

    private static int lastSeenSyncId = -1;
    private static long windowOpenedAt = 0L;
    private static boolean windowReady = false;

    public static boolean isActive = false;
    public static boolean isPaused = false;
    public static String currentMode = "none";

    public static boolean killingPests = false;
    private static Vec3 currentPestTarget = null;
    public static boolean pestsSpawned = false;

    private static BlockPos lastWaypoint = null;
    private static int ticksOnWaypoint = 0;
    private static int randomDelayTicks = random.nextInt(81) + 20;

    public static BlockPos pauseWaypoint = null;

    private static long wPhaseStart = 0;

    private static int wardrobeSlot = 0;
    private static boolean wardrobeClicked = false;
    public static int originalPestKillSlot = -1;
    public static int vacuumSlot = -1;
    private static enum AotvPhase {
        IDLE, INIT, SET_ROTATION, WAIT_ROTATION, SNEAK, WAIT_SNEAK, SNEAK_USE, WAIT_AFTER_USE, CHECK_HEIGHT, RESTORE, DONE
    }
    private static AotvPhase aotvPhase = AotvPhase.IDLE;
    private static long aotvPhaseStart = 0L;
    private static float aotvSavedYaw = 0f;
    private static float aotvSavedPitch = 0f;
    private static int aotvHotbarSlot = -1;
    private static int originalAotvSlot = -1;
    private static float aotvTargetPitch = -80f;
    private static int movementBlockTicks = 0;

    private static boolean eqActive = false;

    private static Vec3 pestOffset = Vec3.ZERO;
    private static long lastOffsetChange = 0L;
    private static final long OFFSET_CHANGE_INTERVAL_MS = 900L + random.nextInt(600);
    private static final float OFFSET_MAX_HORIZONTAL = 0.5f;
    private static final float OFFSET_MAX_VERTICAL   = 0.5f;
    private static long phillipDelay = 0;

    private record ParticleSample(Vec3 pos, long time) {}
    private static final java.util.ArrayDeque<ParticleSample> particleSamples = new java.util.ArrayDeque<>();
    private static volatile Vec3 extrapolatedPestPos = null;
    private static long lastLcClickTime = 0L;
    private static final long LC_CLICK_INTERVAL_MS = 2000L;
    private static final long PARTICLE_WINDOW_MS = 1500L;

    private static enum PhillipPhase { IDLE, COMMAND_SENT, WAIT_FOR_OPEN, CLICKING_ITEM, WAIT_AFTER_CLICK, CLOSING, DONE }
    private static PhillipPhase phillipPhase = PhillipPhase.IDLE;
    private static long phillipPhaseStart = 0L;

    private static int plot = 0;
    private static long lastBrokenBlock = 0L;
    private static long lastXp = 0L;
    private static long lastPest = 0L;
    private static int blocksBroken = 0;
    private static boolean isBreaking = false;
    private static long pauseTimeMs = 0;
    private static long startTime = 0L;
    private static double bps = 0.0;
    private static boolean isMining = false;
    private static BlockPos miningPos = null;
    private static boolean waitingForTrades = false;
    private static boolean worldChanged = false;
    private static float lockedYaw = 0f;
    private static float lockedPitch = 0f;
    private static long lastFailTime = 0L;
    private static int lockedSlot = -1;
    private static long bpsZeroStartTime = 0L;
    private static final float yawPitchTolerance = 0.5f;
    private static String lockedItemName = null;
    private static final Set<ArmorStand> ignoredPests = new HashSet<>();
    private static long ignoredResetTime = 0;
    private static Vec3 lastSentPestTarget = null;
    private static ArmorStand currentPestStand = null;
    private static final double RETARGET_DIST_SQ = 9.0;
    private static boolean needsRetarget = false;
    private static long lastTogglePauseTime = 0L;
    private static final long FAILSAFE_BLOCK_MS = 3000L;
    private static Vec3 lastPestStandPos = null;
    private static final double TELEPORT_THRESHOLD_SQ = 16.0;
    private static LivingEntity currentPestMob = null;
    private static final double PEST_PAIR_RADIUS_SQ = 4.0 * 4.0;
    private static final double PEST_LIVING_RADIUS = 3.5;
    private static boolean hasPaused = false;

    private static boolean isMouseLocked = false;

    private static final List<net.minecraft.world.level.block.Block> farmableBlocks = List.of(
            net.minecraft.world.level.block.Blocks.WHEAT,
            net.minecraft.world.level.block.Blocks.CARROTS,
            net.minecraft.world.level.block.Blocks.POTATOES,
            net.minecraft.world.level.block.Blocks.CARVED_PUMPKIN,
            net.minecraft.world.level.block.Blocks.MELON,
            net.minecraft.world.level.block.Blocks.SUGAR_CANE,
            net.minecraft.world.level.block.Blocks.CACTUS,
            net.minecraft.world.level.block.Blocks.COCOA,
            net.minecraft.world.level.block.Blocks.RED_MUSHROOM,
            net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM,
            net.minecraft.world.level.block.Blocks.NETHER_WART,
            net.minecraft.world.level.block.Blocks.ROSE_BUSH,
            net.minecraft.world.level.block.Blocks.SUNFLOWER
    );

    private static String currentFail = null;
    private static String currentState = "idle";

    public static String getCurrentFail() {
        return currentFail;
    }
    public static String getCurrentState() {
        return currentState;
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
        FtEventBus.onEvent(FtEvent.WorldRender.class, event -> onRenderWorldLast(event));
        FtEventBus.onEvent(FtEvent.HudRender.class, hud -> render(hud.guiGraphics));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((oldWorld, newWorld) -> {
            if (isActive) {
                handleToggle();
                releaseAllKeys();
                worldChanged = true;
                currentState = "idle";
                Utils.log("World unloaded, macro turned off");
            }
        });
        PacketEvent.registerReceive((packet, connection) -> {
            if (packet instanceof ClientboundSoundPacket soundPacket) {
                String soundId = soundPacket.getSound().value().location().toString();
                if (isActive && !isPaused && soundId.equals("minecraft:entity.experience_orb.pickup")) {
                    lastXp = System.currentTimeMillis();
                }
                if (killingPests && soundId.equals("minecraft:entity.silverfish.death")) {
                    if (currentPestTarget != null && mc.level != null) {
                        for (Entity entity : mc.level.entitiesForRendering()) {
                            if (entity instanceof ArmorStand armorStand) {
                                Vec3 pos = armorStand.position().add(0, 1.15, 0);
                                if (pos.distanceTo(currentPestTarget) < 2.5) {
                                    ignoredResetTime = System.currentTimeMillis();
                                    break;
                                }
                            }
                        }
                    }
                    Utils.log("§cSilverfish death sound detected → skipping pest");
                }
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket pp) {
                if (lcRunning && pp.getParticle().getType() == net.minecraft.core.particles.ParticleTypes.ENCHANT) {
                    Vec3 pos = new Vec3(pp.getX(), pp.getY(), pp.getZ());
                    long now = System.currentTimeMillis();
                    lastAngryParticlePos = pos;
                    lastAngryParticleTime = now;

                    synchronized (particleSamples) {
                        particleSamples.addLast(new ParticleSample(pos, now));
                        while (!particleSamples.isEmpty() && now - particleSamples.peekFirst().time() > PARTICLE_WINDOW_MS) {
                            particleSamples.pollFirst();
                        }
                        if (particleSamples.size() >= 2) {
                            extrapolatedPestPos = computeExtrapolatedPos(now);
                        }
                    }
                }
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString().replaceAll("§.", "");
            Pattern pattern = Pattern.compile("Pest have spawned in Plot\\s*-\\s*(\\d+)");
            Matcher matcher = pattern.matcher(text);

            if (matcher.find() && Faketils.config().pestFarming) {
                plot = Integer.parseInt(matcher.group(1));
                lastPest = System.currentTimeMillis();
                pestsSpawned = true;
                Utils.log("Pest spawned in plot " + plot);

                if (mc.player != null && isActive && !isPaused) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            startEqSequence(PestPhase.ROOTED);
                            eqActive = true;
                            currentState = "Changing EQ";
                            eqState = EqState.OPENING;
                            eqStateStart = System.currentTimeMillis();
                            currentPestPhase = PestPhase.ROOTED;
                            itemsUsedThisPhase = 0;
                            lastProcessedSyncId = -1;
                        } catch (InterruptedException ignored) {}
                    }).start();
                }
            }

            if (text.contains("There are not any Pests on your Garden right now!")) {
                if (killingPests) {
                    killingPests = false;
                    hasPaused = false;
                    isLcVacuum = false;
                    lastSentPestTarget = null;
                    lcRunning = false;
                    if (originalPestKillSlot >= 0 && originalPestKillSlot < 9) {
                        setSlot(originalPestKillSlot);
                        Utils.log("Restored original hotbar slot after pest killing: " + (originalPestKillSlot + 1));
                    }
                    originalPestKillSlot = -1;
                    lastPestStandPos = null;
                    currentPestStand = null;
                    FlyHandler.stop();
                    RotationHandler.reset();
                    currentPestMob = null;
                    needsRetarget = true;
                    handlePause();
                    if (!Faketils.config().pestFarming) {
                        currentMode = null;
                        mc.player.connection.sendCommand("warp garden");
                        new Thread(() -> {
                            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                            FlyHandler.setFlying(false);
                        }).start();
                    }
                    currentPestTarget = null;
                    pestOffset = Vec3.ZERO;
                    lastOffsetChange = 0L;
                    Utils.log("All pests killed → resuming normal farming");
                }
            }
        });
    }

    public static boolean isMouseLocked() {
        return isMouseLocked;
    }

    public static void onBlockBroken(BlockPos pos) {
        if (!isActive || mc.level == null || mc.player == null) return;
        var block = mc.level.getBlockState(pos).getBlock();
        if (farmableBlocks.contains(block)) {
            if (startTime == 0L) startTime = System.currentTimeMillis();
            isBreaking = true;
            blocksBroken++;
            lastBrokenBlock = System.currentTimeMillis();
        }
    }

    private static void onClientTick() {
        if (currentFail != null && System.currentTimeMillis() - lastFailTime > 2000) {
            currentFail = null;
        }
        if (!Faketils.config().funnyToggle) return;
        if (!Utils.isInGarden()) return;

        updatePestsTimer();

        while (toggleKey.consumeClick()) handleToggle();
        while (pauseKey.consumeClick()) handlePause();
        while (resetKey.consumeClick()) handleReset();

        if (killingPests && isPaused) {
            currentState = "Killing pests";
            resetKilling();
            handleKilling();
            if (currentPestTarget != null && mc.player != null) {
                double distance = mc.player.position().distanceTo(currentPestTarget);
                double threshold = Faketils.config().teleportingToPlotType == Config.TeleportingToPlotType.Disco ? 20.0 : 8.0;
                boolean inSweetSpot = distance < threshold;
                mc.options.keyUse.setDown(inSweetSpot);
            }
            if (mc.screen != null && mc.player != null && mc.screen.getTitle().getString()
                    .replaceAll("§.", "").trim().toLowerCase().contains("stereo harmony")) mc.player.closeContainer();
        }

        handleAotvSequence();

        if (isActive && !isPaused && Faketils.config().pestKilling) {mc.options.keyUse.setDown(false);}

        if (!isActive || (isPaused && !killingPests)) {
            currentState = "idle";
            releaseAllKeys();
            return;
        }

        handleRodSequence();

        if (eqActive && !isSpraying) {
            releaseAllKeys();
            handleEqSequence();
            return;
        }

        handleTradesScreen();
        handlePestBuff();
        nonPfarmingKilling();

        if (mc.screen != null) {
            lastXp = System.currentTimeMillis();
            releaseAllKeys();
            return;
        }

        handleSpray();
        handleSpraySequence();

        if (isSpraying) {
            currentState = "Spraying";
            return;
        }

        if (movementBlockTicks > 0) {
            movementBlockTicks--;
            releaseAllKeys();
        }

        updateBPS();
        checkFailSafes();
        checkInventoryForSell();
        updateMode();

        if (movementBlockTicks == 0 && !killingPests) {
            holdKeys();
        }
    }

    private static void worldChange() {
        currentFail = "World Changed";
        mc.player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
    }

    private static void nonPfarmingKilling() {
        if (!Faketils.config().pestKilling) return;
        if (Faketils.config().pestFarming) return;
        TabListParser.getTabLines().stream()
                .filter(s -> s.contains("Alive: "))
                .findFirst()
                .ifPresent(line -> {
                    try {
                        int alive = Integer.parseInt(line.replaceAll(".*Alive:\\s+(\\d+).*", "$1").trim());
                        if (alive > 4) {
                            killingPests = true;
                            if (!hasPaused) {
                                hasPaused = true;
                                handlePause();
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                });
    }

    private static void startEqSequence(PestPhase phase) {
        if (mc.player == null) return;
        currentPestPhase = phase;
        eqTargetSlot = (phase == PestPhase.ROOTED)
                ? Faketils.config().eqSlot
                : Faketils.config().eqSlotOld;
        eqPhase = EqPhase.OPEN_SENT;
        eqPhaseStart = System.currentTimeMillis();
        eqActive = true;
        currentState = "Changing EQ";
        mc.player.connection.sendCommand("loadout");
        Utils.log("Equipment sequence started for " + phase + ", target slot " + eqTargetSlot);
    }

    private static void handleEqSequence() {
        if (eqPhase == EqPhase.IDLE || mc.player == null) return;

        long now = System.currentTimeMillis();

        if (now - eqPhaseStart > 10_000) {
            Utils.log("Equipment screen never opened → aborting");
            eqPhase = EqPhase.IDLE;
            eqActive = false;
            return;
        }

        if (mc.screen == null) return;

        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
            return;
        }

        AbstractContainerMenu handler = mc.player.containerMenu;
        int syncId = handler.containerId;

        switch (eqPhase) {
            case OPEN_SENT -> {
                if (now - eqPhaseStart > 400 + random.nextInt(300)) {
                    eqPhase = EqPhase.CLICKING_SLOT;
                    eqPhaseStart = now;
                    Utils.log("Equipment window ready");
                }
            }
            case CLICKING_SLOT -> {
                if (now - eqPhaseStart < 300 + random.nextInt(300)) return;

                for (int i = 0; i < handler.slots.size(); i++) {
                    ItemStack stack = handler.getSlot(i).getItem();
                    if (stack.isEmpty()) continue;
                    String name = stack.getHoverName().getString()
                            .replaceAll("§.", "").toLowerCase().trim();
                    if (name.contains(eqTargetSlot)) {
                        mc.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.PICKUP, mc.player);
                        Utils.log("Clicked equipment preset slot " + eqTargetSlot + " at index " + i);
                        eqPhase = EqPhase.WAIT_AFTER_CLICK;
                        eqPhaseStart = now;
                        return;
                    }
                }
                if (now - eqPhaseStart > 8_000) {
                    Utils.log("Equipment slot " + eqTargetSlot + " not found → aborting");
                    mc.player.closeContainer();
                    eqPhase = EqPhase.IDLE;
                    eqActive = false;
                }
            }
            case WAIT_AFTER_CLICK -> {
                if (now - eqPhaseStart > 150 + random.nextInt(150)) {
                    eqPhase = EqPhase.CLOSING;
                    eqPhaseStart = now;
                }
            }
            case CLOSING -> {
                mc.player.closeContainer();
                Utils.log("Closing equipment screen");
                eqPhase = EqPhase.DONE;
                onEqSequenceDone(now);
                eqPhaseStart = now;
                rodPhase = RodPhase.DONE;
            }
            case DONE -> {
                //can't add anything cuz screen==null check above
            }
            default -> {}
        }
    }

    private static void onEqSequenceDone(long now) {
        eqPhase = EqPhase.IDLE;
    }

    private static void handleRodSequence() {
        if (rodPhase == RodPhase.IDLE || mc.player == null) return;

        long now = System.currentTimeMillis();
        Inventory inventory = mc.player.getInventory();

        switch (rodPhase) {
            case SWAP_TO_ROD:
                if (now - rodPhaseStart >= 50 + random.nextInt(101)) {
                    setSlot(rodHotbarSlot);
                    rodPhase = RodPhase.WAIT_BEFORE_CLICK;
                    rodPhaseStart = now;
                }
                break;

            case WAIT_BEFORE_CLICK:
                if (now - rodPhaseStart >= 100 + random.nextInt(151)) {
                    Utils.simulateUseItem(mc.gameMode);
                    rodPhase = RodPhase.WAIT_BEFORE_RESTORE;
                    rodPhaseStart = now;
                }
                break;

            case WAIT_BEFORE_RESTORE:
                if (now - rodPhaseStart >= 50 + random.nextInt(101)) {
                    rodPhase = RodPhase.RESTORE_SLOT;
                    rodPhaseStart = now;
                }
                break;

            case RESTORE_SLOT:
                if (originalHotbarSlot >= 0 && originalHotbarSlot < 9 && now - rodPhaseStart >= 50 + random.nextInt(101)) {
                    setSlot(originalHotbarSlot);
                    Utils.log("Restored original slot: " + originalHotbarSlot);
                } else {
                    Utils.log("Invalid original slot " + originalHotbarSlot + " - skipping restore");
                }
                rodPhase = RodPhase.DONE;
                rodPhaseStart = now;
                break;

            case DONE:
                if (now - rodPhaseStart >= 100) {
                    rodPhase = RodPhase.IDLE;
                    eqState = EqState.IDLE;
                    rodHotbarSlot = -1;

                    if (mc.player != null && originalHotbarSlot >= 0 && originalHotbarSlot < 9) {
                        setSlot(originalHotbarSlot);
                        Utils.log("Final slot restore: " + originalHotbarSlot);
                    }

                    if (currentPestPhase == PestPhase.ROOTED) {
                        Utils.log("Rooted pest items handled → pausing macro");
                        handlePause();
                        movementBlockTicks = 10;
                        releaseAllKeys();
                        currentPestPhase = PestPhase.SQUEAKY;
                        if (Faketils.config().teleportingToPlotType == Config.TeleportingToPlotType.TpToPlot) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(150);
                                } catch (InterruptedException ignored) {
                                }
                                mc.player.connection.sendCommand("tptoplot " + plot);
                                if (Faketils.config().pestKilling) swapToInfiniVacuum();
                                pendingDoubleJumpTicks = 10;
                                try {
                                    Thread.sleep(350);
                                } catch (InterruptedException ignored) {
                                }
                                if (Faketils.config().pestKilling) handleKilling();
                                if (Faketils.config().pestKilling) {
                                    killingPests = true;
                                    isLcVacuum = true;
                                    lcVacuum();
                                }
                            }).start();
                        }
                        if (Faketils.config().teleportingToPlotType == Config.TeleportingToPlotType.AOTV) startAotvEtherwarp();
                        if (Faketils.config().teleportingToPlotType == Config.TeleportingToPlotType.Disco) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(150);
                                } catch (InterruptedException ignored) {
                                }
                                mc.player.connection.sendCommand("tptoplot " + plot);
                                if (Faketils.config().pestKilling) swapToInfiniVacuum();
                                pendingDoubleJumpTicks = 10;
                                try {
                                    Thread.sleep(350);
                                } catch (InterruptedException ignored) {
                                }
                                if (Faketils.config().pestKilling) {
                                    killingPests = true;
                                }
                            }).start();
                        }
                    }

                    originalHotbarSlot = -1;
                    new Thread(() -> {
                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                        eqActive = false;
                    }).start();
                }
                break;

            default:
                rodPhase = RodPhase.IDLE;
                break;
        }
    }

    public static void startAotvEtherwarp() {
        if (aotvPhase != AotvPhase.IDLE || mc.player == null) return;
        aotvPhase = AotvPhase.INIT;
        aotvPhaseStart = System.currentTimeMillis();
        Utils.log("Starting AOTV etherwarp sequence");
    }

    private static void handleAotvSequence() {
        if (aotvPhase == AotvPhase.IDLE || mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();

        switch (aotvPhase) {

            case INIT: {
                aotvSavedYaw   = mc.player.getYRot();
                aotvSavedPitch = mc.player.getXRot();
                originalAotvSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();

                aotvHotbarSlot = -1;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;
                    String name = stack.getHoverName().getString()
                            .replaceAll("§.", "").toLowerCase().trim();
                    if (name.contains("aspect of the void")) {
                        aotvHotbarSlot = i;
                        break;
                    }
                }

                if (aotvHotbarSlot == -1) {
                    Utils.log("§cNo AOTV found in hotbar → skipping etherwarp, going straight to killing");
                    aotvPhase = AotvPhase.DONE;
                    aotvPhaseStart = now;
                    break;
                }

                setSlot(aotvHotbarSlot);
                Utils.log("AOTV found in slot " + (aotvHotbarSlot + 1) + ", beginning etherwarp loop");
                aotvPhase = AotvPhase.SET_ROTATION;
                aotvPhaseStart = now;
                break;
            }

            case SET_ROTATION: {
                aotvTargetPitch = -(80 + random.nextInt(11));
                RotationHandler.setTarget(aotvSavedYaw, aotvTargetPitch);
                aotvPhase = AotvPhase.WAIT_ROTATION;
                aotvPhaseStart = now;
                break;
            }

            case WAIT_ROTATION: {
                if (now - aotvPhaseStart >= 150) {
                    if (!RotationHandler.active) {
                        aotvPhase = AotvPhase.SNEAK;
                        aotvPhaseStart = now;
                    }
                }
                break;
            }

            case SNEAK: {
                Utils.simulateUseItem(mc.gameMode);
                mc.options.keyShift.setDown(true);
                aotvPhase = AotvPhase.WAIT_SNEAK;
                aotvPhaseStart = now;
                break;
            }

            case WAIT_SNEAK: {
                if (now - aotvPhaseStart >= 50 + random.nextInt(30)) {
                    aotvPhase = AotvPhase.SNEAK_USE;
                    aotvPhaseStart = now;
                }
                break;
            }

            case SNEAK_USE: {
                mc.options.keyUse.setDown(true);
                aotvPhase = AotvPhase.WAIT_AFTER_USE;
                aotvPhaseStart = now;
                break;
            }

            case WAIT_AFTER_USE: {
                if (now - aotvPhaseStart >= 400) {
                    mc.options.keyUse.setDown(false);
                    aotvPhase = AotvPhase.CHECK_HEIGHT;
                    aotvPhaseStart = now;
                }
                break;
            }

            case CHECK_HEIGHT: {
                BlockPos headAbove = BlockPos.containing(
                        mc.player.getX(),
                        mc.player.getY() + 10.5,
                        mc.player.getZ()
                );
                boolean noBlockAbove = mc.level.getBlockState(headAbove).isAir();

                if (noBlockAbove) {
                    Utils.log("Etherwarp reached top (y=" + (int) mc.player.getY() + ") → restoring state");
                    aotvPhase = AotvPhase.DONE;
                } else {
                    aotvPhase = AotvPhase.SET_ROTATION;
                }
                aotvPhaseStart = now;
                break;
            }

            case RESTORE: {
                if (now - aotvPhaseStart >= 100) {
                    mc.options.keyShift.setDown(false);
                    RotationHandler.setTarget(aotvSavedYaw, aotvSavedPitch);
                    if (originalAotvSlot >= 0 && originalAotvSlot < 9) {
                        setSlot(originalAotvSlot);
                    }
                    aotvPhase = AotvPhase.DONE;
                    aotvPhaseStart = now;
                }
                break;
            }

            case DONE: {
                if (RotationHandler.active) break;
                if (now - aotvPhaseStart < 100) break;

                mc.options.keyShift.setDown(false);

                aotvPhase        = AotvPhase.IDLE;
                aotvHotbarSlot   = -1;
                originalAotvSlot = -1;

                Utils.log("AOTV sequence done → starting pest kill");

                if (Faketils.config().pestKilling) {
                    swapToInfiniVacuum();
                    handleKilling();
                    killingPests = true;
                    isLcVacuum = true;
                    lcVacuum();
                }
                break;
            }
        }
    }

    private static boolean swapToInfiniVacuum() {
        if (mc.player == null) return false;
        if (!Faketils.config().pestKilling) return false;

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String name = stack.getHoverName().getString()
                    .replaceAll("§.", "")
                    .replaceAll("[™®©]", "")
                    .toLowerCase()
                    .trim();

            if (name.contains("infinivacuum")) {
                vacuumSlot = i;
                break;
            }
        }

        if (vacuumSlot == -1) {
            Utils.log("§cNo InfiniVacuum™ found in hotbar → using current item");
            return false;
        }

        originalPestKillSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(vacuumSlot);
        Utils.log("Swapped to InfiniVacuum™ (slot " + (vacuumSlot + 1) + ")");
        return true;
    }

    private static void lcVacuum() {
        if (!isLcVacuum || !Faketils.config().pestKilling || lcRunning || mc.player == null) return;
        lcRunning = true;
        new Thread(() -> {
            try {
                while (lcRunning && killingPests) {
                    long now = System.currentTimeMillis();

                    if (currentPestTarget != null) {
                        Thread.sleep(50);
                        continue;
                    }

                    Vec3 target = extrapolatedPestPos;
                    if (target != null && now - lastAngryParticleTime < 2000L) {
                        final Vec3 flyTarget = target;
                        needsRetarget = true;
                        mc.execute(() -> FlyHandler.flyTo(flyTarget));
                        Thread.sleep(150);
                    }

                    if (now - lastLcClickTime >= LC_CLICK_INTERVAL_MS) {
                        lastLcClickTime = now;
                        needsRetarget = true;
                        mc.execute(() -> {
                            if (mc.player == null) return;
                            PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();
                            inv.setSelectedSlot(vacuumSlot);
                            mc.gameMode.startDestroyBlock(mc.player.blockPosition(), net.minecraft.core.Direction.UP);
                            mc.options.keyAttack.setDown(true);
                        });
                        Thread.sleep(120);
                        mc.execute(() -> mc.options.keyAttack.setDown(false));
                        Utils.log("§eLC → no signal, clicked vacuum");
                    }

                    Thread.sleep(50);
                }
            } catch (InterruptedException ignored) {
            } finally {
                lcRunning = false;
                extrapolatedPestPos = null;
                needsRetarget = true;
                lastLcClickTime = 0L;
                synchronized (particleSamples) { particleSamples.clear(); }
                Utils.log("§cLC Vacuum stopped");
            }
        }, "lc-vacuum").start();
    }

    private static void resetKilling() {
        if (mc.player == null || mc.level == null || !Utils.isInGarden()) return;
        if (!Faketils.config().pestKilling) return;

        if (TabListParser.getTabLines().stream().anyMatch(s -> s.contains("Alive: 0"))) {
            if (killingPests) {
                killingPests = false;
                hasPaused = false;
                isLcVacuum = false;
                lastSentPestTarget = null;
                lcRunning = false;
                if (originalPestKillSlot >= 0 && originalPestKillSlot < 9) {
                    setSlot(originalPestKillSlot);
                    Utils.log("Restored original hotbar slot after pest killing: " + (originalPestKillSlot + 1));
                }
                originalPestKillSlot = -1;
                lastPestStandPos = null;
                currentPestStand = null;
                FlyHandler.stop();
                RotationHandler.reset();
                currentPestMob = null;
                needsRetarget = true;
                handlePause();
                if (!Faketils.config().pestFarming) {
                    currentMode = null;
                    mc.player.connection.sendCommand("warp garden");
                    new Thread(() -> {
                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                        FlyHandler.setFlying(false);
                    }).start();
                }
                currentPestTarget = null;
                pestOffset = Vec3.ZERO;
                lastOffsetChange = 0L;
                Utils.log("All pests killed → resuming normal farming");
            }
        }
    }

    public static void handleKilling() {
        if (mc.player == null || mc.level == null || !Utils.isInGarden()) return;
        if (!Faketils.config().pestKilling) return;
        if (!killingPests) return;

        ArmorStand bestStand = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (PestHelper.getPestFromHead(stand) == null) continue;

            Vec3 standPos = stand.position();
            boolean hasPairedMob = false;

            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity living)) continue;
                if (living instanceof ArmorStand) continue;
                if (living instanceof net.minecraft.world.entity.player.Player) continue;
                if (living.isDeadOrDying()) continue;
                if (!(living instanceof net.minecraft.world.entity.monster.Silverfish) &&
                        !(living instanceof net.minecraft.world.entity.ambient.Bat)) continue;
                if (living.distanceToSqr(standPos.x, standPos.y, standPos.z) <= PEST_PAIR_RADIUS_SQ) {
                    hasPairedMob = true;
                    break;
                }
            }

            if (!hasPairedMob) continue;

            double distSq = stand.distanceToSqr(mc.player);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestStand = stand;
            }
        }

        if (bestStand != null) {
            Vec3 target = bestStand.position().add(0, 1.15, 0).add(pestOffset);
            currentPestTarget = target;
            if (Faketils.config().teleportingToPlotType != Config.TeleportingToPlotType.Disco) FlyHandler.flyTo(target);
        } else {
            currentPestTarget = null;
        }
    }

    private static void updatePestsTimer() {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();

        if (pestsSpawned && now - lastPest > Faketils.config().pestTime * 1000L && Faketils.config().pestFarming) {
            pestsSpawned = false;
            if (isActive && !isPaused) {
                releaseAllKeys();
                eqActive = true;
                startEqSequence(PestPhase.SQUEAKY);
            }
            eqState = EqState.OPENING;
            eqStateStart = now;
            currentPestPhase = PestPhase.SQUEAKY;
            itemsUsedThisPhase = 0;
            lastProcessedSyncId = -1;
            mc.player.sendSystemMessage(Component.literal("§7[§bFaketils§7] §ePest Timer ran out!"));
            Utils.log(Faketils.config().pestTime + "s timer reached → starting Squeaky phase");
        }
    }

    private static void handleToggle() {
        lastTogglePauseTime = System.currentTimeMillis();
        isActive = !isActive;
        isPaused = false;
        if (!isActive) {
            lastXp = System.currentTimeMillis();
            releaseAllKeys();
            FlyHandler.stop();
            hasPaused = false;
            killingPests = false;
            isLcVacuum = false;
            lastSentPestTarget = null;
            lcRunning = false;
            currentPestTarget = null;
            pestOffset = Vec3.ZERO;
            lastOffsetChange = 0L;
            originalPestKillSlot = -1;
            needsRetarget = true;
            currentPestStand = null;
            currentPestMob = null;
            lastPestStandPos = null;
            RotationHandler.reset();
            currentMode = "none";
            lastWaypoint = null;
            pauseWaypoint = null;
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
            }
            ticksOnWaypoint = 0;
            unlockMouse();
        } else if (mc.player != null) {
            lastXp = System.currentTimeMillis();
            lockedYaw = mc.player.getYRot();
            lockedPitch = mc.player.getXRot();
            PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();
            lockedSlot = inv.getSelectedSlot();
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
            lockedItemName = mc.player.getMainHandItem().getHoverName().getString();
            lockMouse();
            lastXp = 0;
            pauseWaypoint = null;
        }
        Utils.log("Macro toggled: " + isActive);
    }

    private static void handlePause() {
        if (!isActive) return;
        lastTogglePauseTime = System.currentTimeMillis();
        isPaused = !isPaused;

        if (isPaused) {
            lastXp = System.currentTimeMillis();
            releaseAllKeys();
            movementBlockTicks = 10;

            unlockMouse();
            pauseTimeMs = System.currentTimeMillis();

            if (mc.player != null) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
                pauseWaypoint = BlockPos.containing(mc.player.position());
                if (Faketils.config().rewarpOnPause) {
                    mc.player.connection.sendCommand("setspawn");
                    mc.player.sendSystemMessage(Component.literal("§7[§bFaketils§7] §eReWarp point set!"));
                }
            }
            Utils.log("Macro paused");
        } else {
            lastXp = System.currentTimeMillis();
            lockMouse();
            releaseAllKeys();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
            }
            lastXp = 0;

            boolean shouldWarp = false;

            if (Faketils.config().rewarpOnPause && pauseWaypoint != null && mc.player != null) {
                BlockPos playerPos = BlockPos.containing(mc.player.position());

                int dx = Math.abs(playerPos.getX() - pauseWaypoint.getX());
                int dz = Math.abs(playerPos.getZ() - pauseWaypoint.getZ());

                if (dx > 1 || dz > 1) {
                    shouldWarp = true;
                }
            }

            pauseWaypoint = null;

            if (shouldWarp) {
                mc.player.connection.sendCommand("warp garden");
                new Thread(() -> {
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                    FlyHandler.setFlying(false);
                }).start();
                mc.player.sendSystemMessage(Component.literal("§7[§bFaketils§7] §eWarping back!"));
                Utils.log("Macro resumed (warp garden)");
            } else {
                Utils.log("Macro resumed");
            }
        }
    }

    private static void handleReset() {
        FlyHandler.stop();
        RotationHandler.reset();
        if (!isActive || mc.player == null) return;
        lockedYaw = mc.player.getYRot();
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
        lockedPitch = mc.player.getXRot();
        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();
        lockedSlot = inv.getSelectedSlot();
        lockedItemName = mc.player.getMainHandItem().getHoverName().getString();
        lastXp = 5000;
        Utils.log("Reset fake fails");
    }

    private static void updateBPS() {
        if (isBreaking && System.currentTimeMillis() - lastBrokenBlock > 1000) {
            bps = 0.0;
            isBreaking = false;
            blocksBroken = 0;
            startTime = 0L;
        } else if (isBreaking) {
            double secondsElapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            bps = blocksBroken / secondsElapsed;
        }
    }

    private static float getAngleDifference(float a, float b) {
        float diff = (a - b) % 360.0F;
        if (diff > 180.0F) diff -= 360.0F;
        if (diff < -180.0F) diff += 360.0F;
        return Math.abs(diff);
    }

    private static void checkFailSafes() {
        if (mc.player == null) return;
        if (System.currentTimeMillis() - lastTogglePauseTime < FAILSAFE_BLOCK_MS) return;

        if (killingPests) {
            lastXp = System.currentTimeMillis();
            bpsZeroStartTime = 0L;
            return;
        }

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();
        int currentSlot = inv.getSelectedSlot();
        String currentItemName = mc.player.getMainHandItem().getHoverName().getString();

        if (!isActive) return;

        if (isActive && System.currentTimeMillis() - lastXp > 5000) {
            mc.player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            currentFail = "NO XP";
            lastFailTime = System.currentTimeMillis();
            Utils.log("NO XP PACKET");
        }

        if (isActive && currentSlot != lockedSlot) {
            mc.player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            currentFail = "Slot changed";
            lastFailTime = System.currentTimeMillis();
            Utils.log("Slot changed");
        }

        if (isActive && !currentItemName.equals(lockedItemName)) {
            mc.player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            currentFail = "Item changed";
            lastFailTime = System.currentTimeMillis();
            Utils.log("Item changed from " + lockedItemName + " to " + currentItemName);
        }

        if (getAngleDifference(mc.player.getYRot(), lockedYaw) > yawPitchTolerance ||
                Math.abs(mc.player.getXRot() - lockedPitch) > yawPitchTolerance) {
            mc.player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            currentFail = "Yaw/pitch changed";
            lastFailTime = System.currentTimeMillis();
            Utils.log("Yaw/pitch changed");
        }

        if (bps == 0.0) {
            if (bpsZeroStartTime == 0L) {
                bpsZeroStartTime = System.currentTimeMillis();
            } else {
                BlockPos playerPos = BlockPos.containing(mc.player.position().add(0, 0.5, 0));
                boolean isOnWaypoint = isNearWaypoints(playerPos, 2);
                long delay = isOnWaypoint ? 5000L : 3000L;
                if (System.currentTimeMillis() - bpsZeroStartTime >= delay) {
                    mc.player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                    currentFail = "BPS = 0";
                    lastFailTime = System.currentTimeMillis();
                    Utils.log("BPS = 0 for " + (delay / 1000) + " seconds");
                }
            }
        } else {
            bpsZeroStartTime = 0L;
        }

        if (mc.player.getY() < 63) {
            currentMode = "none";
            releaseAllKeys();
        }
    }

    private static void render(GuiGraphicsExtractor ctx) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        if (!Utils.isInSkyblock() || !Faketils.config().funnyToggle) return;
        if (!Utils.isInGarden()) return;
        if (mc.screen != null) return;
        if (Faketils.config().noHuds) return;

        String text;
        int color;

        if (!isActive) {
            text = "Off";
            color = 0xFFFF4444;
        } else if (isPaused) {
            text = "Paused";
            color = 0xFFFFFF44;
        } else {
            text = "On";
            color = 0xFF44FF44;
        }

        int x = Faketils.config().macroHudX;
        int y = Faketils.config().macroHudY;

        Component macroText = Component.literal("§7Macro: ").append(Component.literal(text).withColor(color));
        Component stateText = Component.literal("§7State: ").append(Component.literal(getCurrentState()).withColor(0xFFAAAAAA));

        int macroWidth = mc.font.width(macroText);
        int stateWidth = mc.font.width(stateText);

        var matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(2f, 2f);

        ctx.textWithBackdrop(mc.font, macroText, 0, 0, macroWidth, 0xFFFFFFFF);
        ctx.textWithBackdrop(mc.font, stateText, 0, 9, stateWidth, 0xFFFFFFFF);

        matrices.popMatrix();
    }

    private static boolean isNearWaypoints(BlockPos pos, int range) {
        List<BlockPos> right = FarmingWaypoints.WAYPOINTS.getOrDefault("right", List.of());
        List<BlockPos> left = FarmingWaypoints.WAYPOINTS.getOrDefault("left", List.of());
        List<BlockPos> warp = FarmingWaypoints.WAYPOINTS.getOrDefault("warp", List.of());

        return right.stream().anyMatch(p -> Math.abs(p.getX() - pos.getX()) <= range && Math.abs(p.getZ() - pos.getZ()) <= range) ||
                left.stream().anyMatch(p -> Math.abs(p.getX() - pos.getX()) <= range && Math.abs(p.getZ() - pos.getZ()) <= range) ||
                warp.stream().anyMatch(p -> Math.abs(p.getX() - pos.getX()) <= range && Math.abs(p.getZ() - pos.getZ()) <= range);
    }

    private static void updateMode() {
        if (mc.player == null) return;

        if (Faketils.config().waypointModeType == Config.WaypointModeType.Block) {
            updateModeBlock();
        } else {
            updateModeLane();
        }
    }

    private static void updateModeBlock() {
        BlockPos pos = BlockPos.containing(mc.player.position().add(0, 0.5, 0));
        List<BlockPos> right = FarmingWaypoints.WAYPOINTS.getOrDefault("right", List.of());
        List<BlockPos> left  = FarmingWaypoints.WAYPOINTS.getOrDefault("left", List.of());
        List<BlockPos> warp  = FarmingWaypoints.WAYPOINTS.getOrDefault("warp", List.of());

        if (warp.contains(pos)) {
            mc.player.connection.sendCommand("warp garden");
            currentMode = "none";
            releaseAllKeys();
        } else if (right.contains(pos)) {
            currentMode = "right";
            currentState = "right";
        } else if (left.contains(pos)) {
            currentMode = "left";
            currentState = "left";
        } else {
            currentMode = "none";
            releaseAllKeys();
        }

        lastWaypoint = null;
        ticksOnWaypoint = 0;
    }

    private static void updateModeLane() {
        BlockPos pos = BlockPos.containing(mc.player.position().add(0, 0.5, 0));
        float yaw = ((mc.player.getYRot() % 360) + 360) % 360;
        boolean facingSouthOrNorth = (yaw >= 315 || yaw < 45) || (yaw >= 135 && yaw < 225);
        boolean facingEastOrWest = (yaw >= 45 && yaw < 135) || (yaw >= 225 && yaw < 315);

        List<BlockPos> right = FarmingWaypoints.WAYPOINTS.getOrDefault("right", List.of());
        List<BlockPos> left = FarmingWaypoints.WAYPOINTS.getOrDefault("left", List.of());
        List<BlockPos> warp = FarmingWaypoints.WAYPOINTS.getOrDefault("warp", List.of());

        String targetMode = "none";
        BlockPos matchedWaypoint = null;

        if (warp.contains(pos)) {
            matchedWaypoint = pos;
            targetMode = "warp";
        } else if (facingSouthOrNorth) {
            for (int x = -1000; x <= 1000; x++) {
                BlockPos candidate = new BlockPos(pos.getX() + x, pos.getY(), pos.getZ());
                if (right.contains(candidate)) {
                    matchedWaypoint = candidate;
                    targetMode = "right";
                    break;
                }
                if (left.contains(candidate)) {
                    matchedWaypoint = candidate;
                    targetMode = "left";
                    break;
                }
            }
        } else if (facingEastOrWest) {
            for (int z = -1000; z <= 1000; z++) {
                BlockPos candidate = new BlockPos(pos.getX(), pos.getY(), pos.getZ() + z);
                if (right.contains(candidate)) {
                    matchedWaypoint = candidate;
                    targetMode = "right";
                    break;
                }
                if (left.contains(candidate)) {
                    matchedWaypoint = candidate;
                    targetMode = "left";
                    break;
                }
            }
        }

        if (!targetMode.equals("none")) {
            if (lastWaypoint == null || !lastWaypoint.equals(matchedWaypoint)) {
                lastWaypoint = matchedWaypoint;
                ticksOnWaypoint = 1;
            } else {
                ticksOnWaypoint++;
                int requiredTicks = Faketils.config().instaSwitch ? 15 : randomDelayTicks;
                if (ticksOnWaypoint >= requiredTicks) {
                    if (targetMode.equals("warp")) {
                        mc.player.connection.sendCommand("warp garden");
                        currentMode = "none";
                        releaseAllKeys();
                        lastWaypoint = null;
                        ticksOnWaypoint = 0;
                    } else {
                        currentMode = targetMode;
                        currentState = targetMode;
                    }
                    if (!Faketils.config().instaSwitch) {
                        randomDelayTicks = random.nextInt(81) + 20;
                    }
                }
            }
        } else {
            lastWaypoint = null;
            ticksOnWaypoint = 0;
        }
    }

    private static void holdKeys() {
        if (currentMode.equals("left")) {
            mc.options.keyUp.setDown(Faketils.config().leftForward);
            mc.options.keyDown.setDown(Faketils.config().leftBack);
            mc.options.keyLeft.setDown(Faketils.config().leftLeft);
            mc.options.keyRight.setDown(Faketils.config().leftRight);
        } else if (currentMode.equals("right")) {
            mc.options.keyUp.setDown(Faketils.config().rightForward);
            mc.options.keyDown.setDown(Faketils.config().rightBack);
            mc.options.keyLeft.setDown(Faketils.config().rightLeft);
            mc.options.keyRight.setDown(Faketils.config().rightRight);
        }

        mc.options.keyAttack.setDown(true);
        keysAreHeld = true;
    }

    private static void releaseAllKeys() {
        if (!keysAreHeld) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyUse.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyAttack.setDown(false);
        keysAreHeld = false;
    }

    private static void lockMouse() {
        if (!isMouseLocked) {
            isMouseLocked = true;
            Utils.log("Mouse locked");
        }
    }

    private static void unlockMouse() {
        if (isMouseLocked) {
            isMouseLocked = false;
            Utils.log("Mouse unlocked");
        }
    }

    private static void onRenderWorldLast(FtEvent.WorldRender event) {
        if (!Utils.isInSkyblock() || mc.player == null || mc.level == null || !Utils.isInGarden()) {return;}

        Vec3 cameraPos = event.camera.position();
        if (Faketils.config().funnyWaypoints) {
            for (var entry : FarmingWaypoints.WAYPOINTS.entrySet()) {
                String type = entry.getKey();
                var list = entry.getValue();

                int color = switch (type.toLowerCase()) {
                    case "left" -> 0xFFFF4444;
                    case "right" -> 0xFF44FF44;
                    case "warp" -> 0xFFFFFF44;
                    default -> 0xFF4488FF;
                };

                for (BlockPos blockPos : list) {
                    RenderUtils.renderWaypointMarker(
                            new Vec3(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5),
                            cameraPos,
                            color,
                            type,
                            event
                    );
                }
            }
        }

        if (pauseWaypoint != null) {
            RenderUtils.renderWaypointMarker(
                    new Vec3(pauseWaypoint.getX()+0.5, pauseWaypoint.getY(), pauseWaypoint.getZ()+0.5),
                    cameraPos,
                    0xFF4488FF,
                    "Pause",
                    event
            );
        }
    }

    private static void checkInventoryForSell() {
        if (!Faketils.config().autoSellJunk) return;
        if (mc.player == null) return;
        if (!isActive || isPaused) return;
        if (eqActive) return;
        if (killingPests) return;
        if (waitingForTrades) return;

        Inventory inv = mc.player.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            String name = stack.getHoverName().getString()
                    .replaceAll("§.", "")
                    .toLowerCase()
                    .trim();

            if (isSellable(name)) {
                mc.player.connection.sendCommand("trades");
                waitingForTrades = true;
                wPhase = WPhase.OPENING;
                long now = System.currentTimeMillis();
                wPhaseStart = now;
                return;
            }
        }
    }

    private static void handleTradesScreen() {
        if (!waitingForTrades) return;
        if (mc.player == null) return;
        if (mc.screen == null) return;

        AbstractContainerMenu handler = mc.player.containerMenu;
        if (!mc.screen.getTitle().getString().replaceAll("§.", "").trim().toLowerCase().contains("trades")) return;
        long now = System.currentTimeMillis();

        if (wPhase == WPhase.OPENING && now - wPhaseStart > 500) {
            wPhase = WPhase.CLICKING;
            wPhaseStart = now;
        }

        if (wPhase == WPhase.CLICKING && now - wPhaseStart > 300) {
            boolean found = false;

            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.slots.get(i);
                ItemStack stack = slot.getItem();

                if (stack.isEmpty()) continue;
                if (!(slot.container instanceof Inventory)) continue;

                String name = stack.getHoverName().getString()
                        .replaceAll("§.", "")
                        .toLowerCase()
                        .trim();

                if (isSellable(name)) {
                    mc.gameMode.handleContainerInput(handler.containerId, i, 2, ContainerInput.CLONE, mc.player);
                    Utils.log("Sold: " + name);
                    wPhaseStart = now;
                    found = true;
                    break;
                }
            }

            if (!found) {
                wPhase = WPhase.DONE;
                wPhaseStart = now;
            }
        }

        if (wPhase == WPhase.DONE && now - wPhaseStart > 1000) {
            mc.player.closeContainer();
            waitingForTrades = false;
        }
    }

    private static void handlePestBuff() {
        if (!Faketils.config().autoPhillip) return;
        if (mc.player == null) return;
        if (!isActive || isPaused) return;
        if (eqActive) return;
        if (killingPests) return;

        long now = System.currentTimeMillis();

        if (now - phillipDelay <= 5000) return;

        if (phillipPhase == PhillipPhase.IDLE) {
            if (TabListParser.getTabLines().stream().anyMatch(s -> s.contains("Bonus: INACTIVE"))) {
                mc.player.connection.sendCommand("call phillip");
                phillipPhase = PhillipPhase.COMMAND_SENT;
                phillipPhaseStart = System.currentTimeMillis();
                Utils.log("Phillip command sent");
            }
            return;
        }

        switch (phillipPhase) {
            case COMMAND_SENT:
                if (now - phillipPhaseStart > 500 + random.nextInt(300)) {
                    phillipPhase = PhillipPhase.WAIT_FOR_OPEN;
                    phillipPhaseStart = now;
                }
                break;

            case WAIT_FOR_OPEN:
                if (mc.screen == null) {
                    if (now - phillipPhaseStart > 5000) {
                        Utils.log("Phillip screen never opened → aborting");
                        phillipPhase = PhillipPhase.IDLE;
                    }
                    break;
                }

                String title = mc.screen.getTitle().getString().replaceAll("§.", "").trim().toLowerCase();
                if (title.contains("pesthunter")) {
                    phillipPhase = PhillipPhase.CLICKING_ITEM;
                    phillipPhaseStart = now;
                    Utils.log("Pesthunter screen detected");
                } else if (now - phillipPhaseStart > 5000) {
                    Utils.log("Wrong screen open: " + title + " → aborting");
                    mc.player.closeContainer();
                    phillipPhase = PhillipPhase.IDLE;
                }
                break;

            case CLICKING_ITEM:
                if (now - phillipPhaseStart < 300 + random.nextInt(300)) break;

                if (mc.player.containerMenu == null) {
                    phillipPhase = PhillipPhase.IDLE;
                    break;
                }

                AbstractContainerMenu handler2 = mc.player.containerMenu;
                boolean clicked = false;

                for (int i = 0; i < handler2.slots.size(); i++) {
                    ItemStack stack = handler2.slots.get(i).getItem();
                    if (stack.isEmpty()) continue;

                    String name = stack.getHoverName().getString().replaceAll("§.", "").trim().toLowerCase();
                    if (name.contains("empty vacuum bag")) {
                        mc.gameMode.handleContainerInput(handler2.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                        Utils.log("Clicked Empty Vacuum Bag at slot " + i);
                        phillipPhase = PhillipPhase.WAIT_AFTER_CLICK;
                        phillipPhaseStart = now;
                        clicked = true;
                        break;
                    }
                }

                if (!clicked) {
                    if (now - phillipPhaseStart > 8000) {
                        Utils.log("Empty Vacuum Bag not found → aborting");
                        mc.player.closeContainer();
                        phillipPhase = PhillipPhase.IDLE;
                    }
                }
                break;

            case WAIT_AFTER_CLICK:
                if (now - phillipPhaseStart > 400 + random.nextInt(200)) {
                    phillipPhase = PhillipPhase.CLOSING;
                    phillipPhaseStart = now;
                }
                break;

            case CLOSING:
                mc.player.closeContainer();
                Utils.log("Closing Pesthunter screen");
                phillipPhase = PhillipPhase.DONE;
                phillipDelay = now;
                holdKeys();
                phillipPhaseStart = now;
                break;

            case DONE:
                if (now - phillipPhaseStart > 1000) {
                    phillipPhase = PhillipPhase.IDLE;
                    Utils.log("Phillip sequence complete");
                }
                break;
        }
    }

    private static void handleSpray() {
        if (!Faketils.config().autoSpray) return;
        if (mc.player == null) return;
        if (sprayPhase != SprayPhase.IDLE) return;
        if (killingPests) return;
        if (!isActive || isPaused) return;

        boolean sprayNone = TabListParser.getTabLines().stream().anyMatch(s -> s.contains("Spray: None"));
        long now = System.currentTimeMillis();

        if (sprayNone) {
            if (sprayNoneDetectedTime == -1L) {
                sprayNoneDetectedTime = now;
            }

            if (now - sprayNoneDetectedTime < 5000) {
                return;
            }

        } else {
            sprayNoneDetectedTime = -1L;
            return;
        }

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) mc.player.getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String name = stack.getHoverName().getString()
                    .replaceAll("§.", "")
                    .toLowerCase()
                    .trim();

            if (name.contains("sprayonator")) {
                originalSpraySlot = inv.getSelectedSlot();
                sprayHotbarSlot = i;

                sprayPhase = SprayPhase.SWAP_TO_SPRAY;
                sprayPhaseStart = System.currentTimeMillis();
                releaseAllKeys();
                isSpraying = true;
                sprayNoneDetectedTime = -1L;

                Utils.log("Starting auto spray");
                break;
            }
        }
    }

    private static void handleSpraySequence() {
        if (sprayPhase == SprayPhase.IDLE || mc.player == null) return;

        long now = System.currentTimeMillis();

        switch (sprayPhase) {

            case SWAP_TO_SPRAY:
                if (now - sprayPhaseStart >= 50) {
                    setSlot(sprayHotbarSlot);
                    sprayPhase = SprayPhase.WAIT_BEFORE_USE;
                    sprayPhaseStart = now;
                }
                break;

            case WAIT_BEFORE_USE:
                if (now - sprayPhaseStart >= 100) {
                    Utils.simulateUseItem(mc.gameMode);
                    sprayPhase = SprayPhase.WAIT_BEFORE_RESTORE;
                    sprayPhaseStart = now;
                }
                break;

            case WAIT_BEFORE_RESTORE:
                if (now - sprayPhaseStart >= 150) {
                    sprayPhase = SprayPhase.RESTORE_SLOT;
                    sprayPhaseStart = now;
                }
                break;

            case RESTORE_SLOT:
                if (now - sprayPhaseStart >= 50) {
                    if (originalSpraySlot >= 0 && originalSpraySlot < 9) {
                        setSlot(originalSpraySlot);
                        Utils.log("Final slot restore: " + originalHotbarSlot);
                    }
                    sprayPhase = SprayPhase.DONE;
                    sprayPhaseStart = now;
                }
                break;

            case DONE:
                if (now - sprayPhaseStart >= 50) {
                    sprayPhase = SprayPhase.IDLE;
                    sprayHotbarSlot = -1;
                    originalSpraySlot = -1;
                    isSpraying = false;
                    holdKeys();
                    Utils.log("Auto spray finished");
                }
                break;
        }
    }

    private static Vec3 computeExtrapolatedPos(long now) {
        ParticleSample first = particleSamples.peekFirst();
        ParticleSample last  = particleSamples.peekLast();

        double dtSec = (last.time() - first.time()) / 1000.0;
        if (dtSec < 0.05) return last.pos();

        Vec3 velocity = last.pos().subtract(first.pos()).scale(1.0 / dtSec);

        return last.pos().add(velocity.x * 50, velocity.y * 0.4, velocity.z * 100);
    }

    private static void setSlot(int slot) {
        ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
    }

    private static int getSlot() {
        return ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    }

    private static boolean isSellable(String name) {
        return name.endsWith("vinyl")
                || name.equals("overclocker 3000")
                || name.equals("chirping stereo")
                || name.equals("beady eyes")
                || name.equals("atmospheric filter")
                || name.equals("clipped wings")
                || name.equals("wriggling larva")
                || name.startsWith("bookworm")
                || name.equals("squeaky toy")
                || name.equals("mantid claw");
    }
}
