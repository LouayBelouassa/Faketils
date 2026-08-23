package com.faketils.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.config.v2.api.*;
import dev.isxander.yacl3.config.v2.api.autogen.*;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import org.lwjgl.glfw.GLFW;

public class Config {

    public static final ConfigClassHandler<Config> HANDLER =
            ConfigClassHandler.createBuilder(Config.class)
                    .id(Identifier.fromNamespaceAndPath("faketils", "config"))
                    .serializer(config -> GsonConfigSerializerBuilder.create(config)
                            .setPath(FabricLoader.getInstance().getConfigDir().resolve("faketils.json"))
                            .setJson5(false)
                            .build())
                    .build();

    private static final KeyMapping.Category FAKETILS_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minecraft", "faketils"));

    public static final KeyMapping toggleMacro =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping("key.faketils.toggle_macro",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_F8,
                            FAKETILS_CATEGORY)
            );

    public static final KeyMapping pauseMacro =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping("key.faketils.pause_macro",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_P,
                            FAKETILS_CATEGORY)
            );

    public static final KeyMapping resetFakeFails =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping("key.faketils.reset_fails",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_BACKSPACE,
                            FAKETILS_CATEGORY)
            );

    @AutoGen(category = "farming", group = "general")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§eFarming Toggle")
    @CustomDescription("Activates all the farming stuff.")
    public boolean funnyToggle = false;

    // Left mode keys
    @AutoGen(category = "farming", group = "left_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Forward")
    public boolean leftForward = false;

    @AutoGen(category = "farming", group = "left_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Left")
    public boolean leftLeft = true;

    @AutoGen(category = "farming", group = "left_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Back")
    public boolean leftBack = false;

    @AutoGen(category = "farming", group = "left_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Right")
    public boolean leftRight = false;

    // Right mode keys
    @AutoGen(category = "farming", group = "right_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Forward")
    public boolean rightForward = false;

    @AutoGen(category = "farming", group = "right_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Left")
    public boolean rightLeft = false;

    @AutoGen(category = "farming", group = "right_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Back")
    public boolean rightBack = false;

    @AutoGen(category = "farming", group = "right_mode")
    @TickBox
    @SerialEntry
    @CustomName("Hold Right")
    public boolean rightRight = true;

    @AutoGen(category = "farming", group = "general")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Show Waypoints")
    @CustomDescription("Display the lane switching waypoints. Disable if you aren't farming.")
    public boolean funnyWaypoints = false;

    @AutoGen(category = "farming", group = "general")
    @EnumCycler
    @SerialEntry
    @CustomName("Waypoint Mode Type")
    @CustomDescription("Select the mode you want the waypoints to work, 1 block means only switch if ur in that block, lane means if ur in that lane of that block.")
    public WaypointModeType waypointModeType = WaypointModeType.Block;

    @AutoGen(category = "farming", group = "general")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§eInsta-Lane Switching")
    @CustomDescription("150ms when switching from right to left, useful in farming contests.")
    public boolean instaSwitch = false;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Auto Pest Buff")
    @CustomDescription("Automatically calls phillip and activates the boost, (needs cookie and Phillip contact).")
    public boolean autoPhillip = false;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Auto Spraynator")
    @CustomDescription("Automatically sprays the plot you are currently farming in.")
    public boolean autoSpray = false;

    @AutoGen(category = "farming", group = "general")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Auto Reel")
    @CustomDescription("Automatically reels in the Hunting Lasso.")
    public boolean autoReel = false;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Day/Night Swapping")
    @CustomDescription("Switches Garden time before Squeaky and Rooted loadout changes.")
    public boolean dayNightSwapping = false;

    //@AutoGen(category = "farming", group = "pests")
    //@Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    //@SerialEntry
    @CustomName("Auto Sell Farming Junk (bugged)")
    @CustomDescription("Automatically sells vinyls and overclockers. Requires a Booster Cookie.")
    public boolean autoSellJunk = false;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Rewarp On Pause")
    @CustomDescription("Sets a spawn point when the macro is paused and warps back when resumed.")
    public boolean rewarpOnPause = false;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Pest Highlighter")
    @CustomDescription("Draws a line and box to the nearest pest.")
    public boolean pestHelper = false;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Equipment and Pet swapping")
    @CustomDescription("Automatically swaps equipment and pet when the pest cooldown is over.")
    public boolean pestFarming = false;

    @AutoGen(category = "farming", group = "pests")
    @EnumCycler
    @SerialEntry
    @CustomName("§cTeleporting To Plot Type §e(Disco is safe)")
    @CustomDescription("Select the method used to tp above a plot to kill pests.")
    public TeleportingToPlotType teleportingToPlotType = TeleportingToPlotType.Disco;

    @AutoGen(category = "farming", group = "pests")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§cAuto Pest Killing")
    @CustomDescription("Automatically kills pests (risky).")
    public boolean pestKilling = false;

    @AutoGen(category = "farming", group = "pests")
    @IntSlider(min = 75, max = 300, step = 1)
    @SerialEntry
    @CustomName("Pest Cooldown Time")
    @CustomDescription("Cooldown time for pests in seconds.")
    public int pestTime = 135;

    @AutoGen(category = "farming", group = "pet_swapping")
    @StringField
    @SerialEntry
    @CustomName("Rooted Loadout Name")
    @CustomDescription("Exact name of the loadout used for Rooted equipment.")
    public String eqSlot = "";

    @AutoGen(category = "farming", group = "pet_swapping")
    @StringField
    @SerialEntry
    @CustomName("Squeaky Loadout Name")
    @CustomDescription("Exact name of the loadout used for Squeaky equipment.")
    public String eqSlotOld = "";

    @AutoGen(category = "fishing", group = "helpers")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§eFishing Helper")
    @CustomDescription("Enables the fishing helper.")
    public boolean fishingHelper = false;

    @AutoGen(category = "fishing", group = "helpers")
    @StringField
    @SerialEntry
    @CustomName("§eFishing Pet swap")
    @CustomDescription("Enables the fishing pet swapper, useful for burnt text swapping.")
    public String fishingPetSwap = "";

    @AutoGen(category = "fishing", group = "helpers")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§eSlug Trophy Fishing")
    @CustomDescription("Enables the slug trophy fishing helper.")
    public boolean slugFishing = false;

    @AutoGen(category = "fishing", group = "helpers")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§cLook Down Item Use")
    @CustomDescription("Automatically looks down and uses your weapon, very useful if using a hyperion and not fishing inside water/lava.")
    public boolean fishingLookDown = false;

    @AutoGen(category = "fishing", group = "helpers")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§eSea Creature Killing")
    @CustomDescription("Automatically helps kill sea creatures (useful for lava fishing).")
    public boolean fishingHelperKilling = false;

    @AutoGen(category = "fishing", group = "helpers")
    @IntSlider(min = 1, max = 3, step = 1)
    @SerialEntry
    @CustomName("Right Click Amount")
    @CustomDescription("Number of right clicks used to kill sea creatures.")
    public int fishingHelperKillingAmount = 1;

    @AutoGen(category = "fishing", group = "helpers")
    @StringField
    @SerialEntry
    @CustomName("Weapon Name")
    @CustomDescription("Name of the weapon used for killing sea creatures (Hyperion, Flay, Veil, etc).")
    public String fishingHelperKillingWeapon = "";

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Fossil Excavation")
    @CustomDescription("Enables The fossil excavation Automation.")
    public boolean fossilExcavation = false;

    @AutoGen(category = "qol", group = "terminals")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Terminals")
    @CustomDescription("Enables terminal solver features.")
    public boolean terminals = false;

    @AutoGen(category = "qol", group = "hud")
    @IntSlider(min = -500, max = 1000, step = 1)
    @SerialEntry
    @CustomName("Macro HUD X")
    @CustomDescription("Horizontal position offset of the macro HUD.")
    public int macroHudX = 50;

    @AutoGen(category = "qol", group = "hud")
    @IntSlider(min = -300, max = 1000, step = 1)
    @SerialEntry
    @CustomName("Macro HUD Y")
    @CustomDescription("Vertical position offset of the macro HUD.")
    public int macroHudY = 80;

    @AutoGen(category = "qol", group = "hud")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Hide all huds")
    @CustomDescription("Disables the huds (useful for streaming).")
    public boolean noHuds = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("No Hurt Cam")
    @CustomDescription("Disables the hurt camera shake effect.")
    public boolean noHurtCam = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§c1.8.9 Sneaking")
    @CustomDescription("Brings back the old sneaking height.")
    public boolean oldSneak = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Auto Tipall")
    @CustomDescription("Runs /tipall every 5mins.")
    public boolean tipAll = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Auto Exp-table")
    @CustomDescription("Auto solves both exp table minigames.")
    public boolean exp = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Auto Harp")
    @CustomDescription("Auto solves harp songs.")
    public boolean harp = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Sphinx Solver")
    @CustomDescription("Automatically answers Sphinx riddles.")
    public boolean sphinxSolver = false;

    @AutoGen(category = "qol", group = "other")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("§cFull Block Panes")
    @CustomDescription("Increases the bounding box size of glass panes.")
    public boolean fullBlockPanes = false;

    @AutoGen(category = "debug", group = "dev")
    @Boolean(formatter = Boolean.Formatter.YES_NO, colored = true)
    @SerialEntry
    @CustomName("Debug Mode")
    @CustomDescription("Enables developer debug features.")
    public boolean debug = false;

    public static Screen createScreen(Screen parent) {
        Screen configScreen = HANDLER.generateGui().generateScreen(parent);
        return new InfoScreen(parent, configScreen);
    }

    public enum PetSwapType {
        ROD,
        ARMOR
    }
    public enum TeleportingToPlotType {
        AOTV,
        TpToPlot,
        Disco
    }
    public enum WaypointModeType {
        Block,
        Lane
    }
}
