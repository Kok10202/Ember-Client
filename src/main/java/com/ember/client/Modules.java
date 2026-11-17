package com.ember.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry of every module. The old modules are gone; everything new lives in Category.SECRET
 * (behind "Enter Code"), except Realistic Sky which is in Visuals.
 */
public final class Modules {
    private static final List<Module> ALL = new ArrayList<>();
    public static Module sky, coords, fpsPing, keystrokes, armorHud, potionHud, waypoint;
    public static Module customText, luckStats, neon, confetti;
    public static Setting[] ctLine = new Setting[3];
    public static Setting ctX, ctY, ctScale, ctColor, ctBg, neonSpeed, neonThick, neonColor;
    public static Setting skyStrength, skyStars, skyCount, skyClouds, skyHaze, skyShooting;
    public static Vec3d waypointPos;

    private Modules() {}

    public static List<Module> all() { return ALL; }

    public static List<Module> of(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) {
            if (c == Category.FAVORITES ? m.favorite : m.category == c) out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.name));
        return out;
    }

    /** Modules the user may currently see (secret ones only after unlocking). */
    public static boolean visible(Module m) { return !m.isSecret() || CodeLock.isUnlocked(); }

    /** Is this module on AND allowed to show right now (used by the HUD renderer). */
    public static boolean on(Module m) { return m.enabled && visible(m); }

    public static void init() {
        // ---------------- Visuals ----------------
        sky = reg(new Module("Realistic Sky", "Time-of-day sky: sunrise, sunset, haze, clouds, night stars.", Category.VISUALS, false) {
            {
                skyStrength = add(Setting.num("Strength", 0.55, 0.1, 1.0, 0.05));
                skyHaze = add(Setting.bool("Horizon Haze", true));
                skyClouds = add(Setting.bool("Clouds", true));
                skyStars = add(Setting.bool("Night Stars", true));
                skyCount = add(Setting.num("Star Count", 220, 50, 400, 10));
                skyShooting = add(Setting.bool("Shooting Stars", false));
            }
        });

        // ---------------- Casino (visual only, fair RNG, nothing is faked) ----------------
        customText = reg(new Module("Custom Text", "Your own text on screen. Placeholders: {fps} {x} {y} {z} {name} {time}.", Category.CASINO, false) {
            {
                ctLine[0] = add(Setting.text("Line 1", "Ember Client"));
                ctLine[1] = add(Setting.text("Line 2", ""));
                ctLine[2] = add(Setting.text("Line 3", ""));
                ctX = add(Setting.num("Position X %", 50, 0, 100, 1));
                ctY = add(Setting.num("Position Y %", 8, 0, 100, 1));
                ctScale = add(Setting.num("Size", 1.2, 0.6, 3.0, 0.1));
                ctColor = add(Setting.mode("Colour", "White", "Orange", "Gold", "Green", "Red", "Cyan", "Purple"));
                ctBg = add(Setting.bool("Glass Background", true));
            }
        });

        reg(new Module("Coin Flip", "Flips a fair coin. Toggle on to flip; it switches itself off.", Category.CASINO, false) {
            long t0;
            @Override public void onEnable(MinecraftClient mc) { t0 = System.currentTimeMillis(); Games.coinFlip(); }
            @Override public void onTick(MinecraftClient mc) { if (System.currentTimeMillis() - t0 > Games.TOTAL_MS) enabled = false; }
        });

        reg(new Module("Dice Roller", "Rolls fair dice. Toggle on to roll; it switches itself off.", Category.CASINO, false) {
            final Setting sides = add(Setting.mode("Dice", "d6", "d20", "d100"));
            final Setting count = add(Setting.num("Dice Count", 1, 1, 3, 1));
            long t0;
            @Override public void onEnable(MinecraftClient mc) {
                t0 = System.currentTimeMillis();
                Games.dice(new int[]{6, 20, 100}[sides.mode], (int) count.num);
            }
            @Override public void onTick(MinecraftClient mc) { if (System.currentTimeMillis() - t0 > Games.TOTAL_MS) enabled = false; }
        });

        reg(new Module("Roulette", "Spins a 0-36 roulette number. Toggle on to spin; it switches itself off.", Category.CASINO, false) {
            long t0;
            @Override public void onEnable(MinecraftClient mc) { t0 = System.currentTimeMillis(); Games.roulette(); }
            @Override public void onTick(MinecraftClient mc) { if (System.currentTimeMillis() - t0 > Games.TOTAL_MS) enabled = false; }
        });

        luckStats = reg(new Hud("Luck Stats", "Session tally of your coin, dice and roulette results.", Category.CASINO, true));
        confetti = reg(new Hud("Confetti", "Confetti burst on a jackpot (3 heads in a row, max dice, roulette 0).", Category.CASINO, true));

        neon = reg(new Module("Neon Border", "Animated glowing edge around the screen.", Category.CASINO, false) {
            {
                neonColor = add(Setting.mode("Colour", "Rainbow", "Orange", "Purple", "Cyan", "Gold"));
                neonSpeed = add(Setting.num("Speed", 4, 1, 10, 1));
                neonThick = add(Setting.num("Thickness", 14, 6, 40, 2));
            }
        });

        // ---------------- Secret (needs the code) ----------------
        reg(new Module("Fullbright", "See clearly in the dark.", Category.SECRET, true) {
            @Override public void onTick(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p == null) return;
                StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 300)
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 500, 0, false, false, false));
            }
            @Override public void onDisable(MinecraftClient mc) {
                if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        });

        reg(new Module("Zoom", "Narrows your field of view. Bind a key for quick use.", Category.SECRET, false) {
            final Setting fov = add(Setting.num("Zoom FOV", 30, 30, 70, 1));
            int saved = -1;
            @Override public void onEnable(MinecraftClient mc) { saved = mc.options.getFov().getValue(); }
            @Override public void onTick(MinecraftClient mc) { mc.options.getFov().setValue((int) fov.num); }
            @Override public void onDisable(MinecraftClient mc) {
                if (saved > 0) mc.options.getFov().setValue(saved);
                saved = -1;
            }
        });

        reg(new Module("Toggle Sprint", "Sprints whenever you walk forward.", Category.SECRET, true) {
            @Override public void onTick(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p == null) return;
                if (mc.options.forwardKey.isPressed() && !p.horizontalCollision && !p.isSneaking()
                        && p.getHungerManager().getFoodLevel() > 6)
                    p.setSprinting(true);
            }
        });

        coords = reg(new Hud("Coordinates", "Shows your XYZ and facing direction.", Category.SECRET, true));
        fpsPing = reg(new Hud("FPS / Ping", "Shows your frame rate and latency.", Category.SECRET, true));
        keystrokes = reg(new Hud("Keystrokes", "Shows WASD and space on screen.", Category.SECRET, false));
        armorHud = reg(new Hud("Armor HUD", "Shows your worn armor and its durability.", Category.SECRET, false));
        potionHud = reg(new Hud("Potion HUD", "Lists active effects with time left.", Category.SECRET, false));

        waypoint = reg(new Module("Waypoint", "Marks where you were when you turned it on; shows distance + direction.", Category.SECRET, false) {
            @Override public void onEnable(MinecraftClient mc) {
                if (mc.player != null) waypointPos = mc.player.getEntityPos();
            }
            @Override public void onDisable(MinecraftClient mc) { waypointPos = null; }
        });
    }

    private static <T extends Module> T reg(T m) { ALL.add(m); return m; }

    /** HUD pieces have no tick logic; the HUD renderer reads their `enabled` flag. */
    private static final class Hud extends Module {
        Hud(String n, String d, Category cat, boolean on) { super(n, d, cat, false); enabled = on; showInHud = false; }
    }
}
