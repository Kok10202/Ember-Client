package com.ember.client.ui;

import com.ember.client.Config;
import com.ember.client.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.Random;

public final class HudRenderer {
    // ---- sky keyframes over one Minecraft day (ticks): {tick, topRGB, horizonRGB, starVisibility*100}
    private static final int[][] SKY = {
            {0,     0x5B8FD0, 0xF4B27A,   0},   // sunrise
            {1500,  0x3E86D6, 0xBFD9F0,   0},
            {6000,  0x2F7FDB, 0xA9D0F2,   0},   // noon
            {11000, 0x3A7FD0, 0xC9D8EA,   0},
            {12500, 0x34508F, 0xF08A4B,  20},   // sunset
            {13500, 0x141B3F, 0x7A4A6E,  90},
            {18000, 0x03050F, 0x101A33, 100},   // midnight
            {22000, 0x0A1030, 0x2A2F55,  90},
            {23500, 0x3A4F8C, 0xE08E6A,  20},
            {24000, 0x5B8FD0, 0xF4B27A,   0}
    };

    // fixed star / cloud directions (yaw 0..360, pitch -85..-2 = above the horizon)
    private static final int MAX_STARS = 400, CLOUDS = 16;
    private static final float[] STAR_YAW = new float[MAX_STARS], STAR_PITCH = new float[MAX_STARS];
    private static final float[] CL_YAW = new float[CLOUDS], CL_PITCH = new float[CLOUDS], CL_SIZE = new float[CLOUDS];
    static {
        Random r = new Random(1234);
        for (int i = 0; i < MAX_STARS; i++) {
            STAR_YAW[i] = r.nextFloat() * 360f;
            STAR_PITCH[i] = -2f - r.nextFloat() * 83f;
        }
        for (int i = 0; i < CLOUDS; i++) {
            CL_YAW[i] = r.nextFloat() * 360f;
            CL_PITCH[i] = -8f - r.nextFloat() * 40f;
            CL_SIZE[i] = 0.7f + r.nextFloat() * 0.9f;
        }
    }

    private HudRenderer() {}

    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        if (Modules.on(Modules.sky)) sky(c, mc, sw, sh);
        if (Modules.on(Modules.neon)) CasinoRenderer.neon(c, sw, sh);
        if (!Config.hud) return;

        if (Modules.on(Modules.customText)) CasinoRenderer.customText(c, mc, sw, sh);
        CasinoRenderer.card(c, sw, sh);
        if (Modules.on(Modules.confetti)) CasinoRenderer.confetti(c, sh);

        int ly = 4; // left column cursor
        if (Modules.on(Modules.coords)) ly = coords(c, mc, ly);
        if (Modules.on(Modules.fpsPing)) ly = fpsPing(c, mc, ly);
        if (Modules.on(Modules.luckStats)) ly = CasinoRenderer.luckStats(c, ly);
        if (Modules.on(Modules.potionHud)) potions(c, mc, ly);
        if (Modules.on(Modules.keystrokes)) keystrokes(c, mc, sh);
        if (Modules.on(Modules.armorHud)) armor(c, mc, sw, sh);
        if (Modules.on(Modules.waypoint) && Modules.waypointPos != null) waypoint(c, mc, sw);
    }

    // ------------------------------------------------------------------ sky

    private static int lerp(int a, int b, double t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    /** {top, horizon, starVisibility 0..1} for the given time of day, smoothly interpolated. */
    private static double[] skyAt(long timeOfDay) {
        double t = timeOfDay % 24000L;
        int i = 0;
        while (i < SKY.length - 2 && t >= SKY[i + 1][0]) i++;
        int[] a = SKY[i], b = SKY[i + 1];
        double f = (t - a[0]) / (double) (b[0] - a[0]);
        f = f * f * (3 - 2 * f); // smoothstep
        return new double[]{lerp(a[1], b[1], f), lerp(a[2], b[2], f), (a[3] + (b[3] - a[3]) * f) / 100.0};
    }

    /** Gradient from the top of the screen down to the horizon line (only with open sky overhead). */
    private static void sky(DrawContext c, MinecraftClient mc, int w, int h) {
        if (mc.world == null) return;
        BlockPos pos = mc.player.getBlockPos();
        if (mc.world.getLightLevel(LightType.SKY, pos) < 12) return;
        if (!mc.world.getRegistryKey().getValue().getPath().equals("overworld")) return;

        double fov = Math.toRadians(mc.options.getFov().getValue());
        double pitchDeg = mc.player.getPitch();
        double hyD = h / 2.0 - (h / 2.0) * Math.tan(Math.toRadians(pitchDeg)) / Math.tan(fov / 2.0);
        int hy = (int) Math.max(0, Math.min(h, hyD));
        if (hy <= 0) return;

        double[] s = skyAt(mc.world.getTimeOfDay());
        int top = (int) s[0], hor = (int) s[1];
        double night = s[2];

        // rain / thunder greys the sky out
        float rain = mc.world.getRainGradient(1f);
        if (rain > 0) {
            double g = rain * 0.65;
            int grey = lerp(0x6C737D, 0x15181C, night);
            top = lerp(top, grey, g);
            hor = lerp(hor, lerp(0x8D949C, 0x20242A, night), g);
        }

        double strength = Modules.skyStrength.num;
        int a = ((int) (255 * strength)) << 24;
        int mid = lerp(top, hor, 0.55);
        int midY = (int) (hy * 0.6);
        c.fillGradient(0, 0, w, midY, a | top, a | mid);
        c.fillGradient(0, midY, w, hy, a | mid, a | hor);

        if (Modules.skyHaze.bool) {
            int hz = Math.max(8, h / 7);
            int ha = (int) (255 * Math.min(0.9, strength * 0.7)) << 24;
            c.fillGradient(0, Math.max(0, hy - hz), w, hy, 0x00000000 | hor, ha | hor);
        }
        if (Modules.skyClouds.bool && rain < 0.9)
            clouds(c, w, h, hy, fov, pitchDeg, mc.player.getYaw(), night, rain, strength);
        if (Modules.skyStars.bool && night > 0.05)
            stars(c, w, h, hy, fov, pitchDeg, mc.player.getYaw(), night * (1 - rain * 0.8));
        if (Modules.skyShooting.bool && night > 0.6) shootingStar(c, w, hy);
    }

    private static void clouds(DrawContext c, int w, int h, int hy, double fov, double pitchDeg, double yawDeg,
                               double night, float rain, double strength) {
        double f = (h / 2.0) / Math.tan(fov / 2.0);
        // clouds are white by day, warm at the edges of the day, dark blue-grey at night; drift slowly
        int base = lerp(0xFFFFFF, 0x2A3350, night);
        base = lerp(base, 0x9AA1AA, rain * 0.7);
        double drift = (System.currentTimeMillis() / 1000.0) * 0.15;
        for (int i = 0; i < CLOUDS; i++) {
            double d = ((CL_YAW[i] + drift - yawDeg) % 360.0 + 540.0) % 360.0 - 180.0;
            double eDeg = CL_PITCH[i] - pitchDeg;
            if (Math.abs(d) > 80 || Math.abs(eDeg) > 80) continue;
            double dr = Math.toRadians(d);
            int x = (int) (w / 2.0 + f * Math.tan(dr));
            int y = (int) (h / 2.0 + f * Math.tan(Math.toRadians(eDeg)) / Math.cos(dr));
            if (y >= hy) continue;
            int cw = (int) (w * 0.16 * CL_SIZE[i]), chh = Math.max(6, (int) (cw * 0.22));
            // 3 soft layers: wide + faint, medium, small + brighter  => fluffy blob
            for (int L = 0; L < 3; L++) {
                int lw = (int) (cw * (1.0 - L * 0.28)), lh = (int) (chh * (1.0 - L * 0.22));
                int al = (int) (255 * Math.min(1.0, (0.10 + L * 0.06) * (0.6 + strength)));
                Gfx.round(c, x - lw / 2, y - lh / 2 - L * 2, lw, lh, lh / 2, (al << 24) | base);
            }
        }
    }

    private static void stars(DrawContext c, int w, int h, int hy, double fov, double pitchDeg, double yawDeg, double vis) {
        double f = (h / 2.0) / Math.tan(fov / 2.0);
        int n = (int) Math.min(MAX_STARS, Modules.skyCount.num);
        long t = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            double d = ((STAR_YAW[i] - yawDeg) % 360.0 + 540.0) % 360.0 - 180.0;
            double eDeg = STAR_PITCH[i] - pitchDeg;
            if (Math.abs(d) > 80 || Math.abs(eDeg) > 80) continue;
            double dr = Math.toRadians(d);
            int x = (int) (w / 2.0 + f * Math.tan(dr));
            int y = (int) (h / 2.0 + f * Math.tan(Math.toRadians(eDeg)) / Math.cos(dr));
            if (x < 0 || x >= w || y < 0 || y >= hy) continue;
            double fade = MathHelper.clamp((hy - y) / (h * 0.12), 0, 1); // stars melt into the horizon haze
            int al = (int) ((140 + 90 * Math.sin(t / 700.0 + i)) * vis * fade);
            if (al < 8) continue;
            int sz = i % 9 == 0 ? 2 : 1;
            c.fill(x, y, x + sz, y + sz, (al << 24) | 0xF4F6FF);
        }
    }

    private static void shootingStar(DrawContext c, int w, int hy) {
        long t = System.currentTimeMillis();
        long slot = t / 6500;
        double ph = (t % 6500) / 800.0;
        if (ph > 1) return;
        Random r = new Random(slot * 7919L);
        int x0 = r.nextInt(Math.max(1, w));
        int y0 = r.nextInt(Math.max(1, hy / 2));
        for (int i = 0; i < 12; i++) {
            double p = ph - i * 0.02;
            if (p < 0) break;
            int x = x0 + (int) (p * 120), y = y0 + (int) (p * 50);
            if (y >= hy) break;
            int al = (int) (255 * (1 - i / 12.0) * (1 - ph * 0.5));
            c.fill(x, y, x + 2, y + 1, (al << 24) | 0xFFFFFF);
        }
    }

    // ------------------------------------------------------------------ HUD pieces

    private static final int PANEL = 0x70101418, PBORDER = 0x40FFFFFF;

    private static int coords(DrawContext c, MinecraftClient mc, int y) {
        String dir = mc.player.getHorizontalFacing().asString();
        dir = dir.substring(0, 1).toUpperCase() + dir.substring(1);
        String s = String.format("XYZ  %.1f  %.1f  %.1f   %s", mc.player.getX(), mc.player.getY(), mc.player.getZ(), dir);
        int w = Gfx.width(s, 0.8f) + 12;
        Gfx.glass(c, 4, y, w, 14, 4, PANEL, PBORDER);
        Gfx.text(c, s, 10, y + 4, Gfx.TEXT, 0.8f);
        return y + 18;
    }

    private static int fpsPing(DrawContext c, MinecraftClient mc, int y) {
        int ping = -1;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        String s = mc.getCurrentFps() + " FPS" + (mc.isInSingleplayer() || ping < 0 ? "" : "   " + ping + " ms");
        int w = Gfx.width(s, 0.8f) + 12;
        Gfx.glass(c, 4, y, w, 14, 4, PANEL, PBORDER);
        Gfx.text(c, s, 10, y + 4, Gfx.TEXT, 0.8f);
        return y + 18;
    }

    private static void potions(DrawContext c, MinecraftClient mc, int y) {
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int sec = e.isInfinite() ? -1 : e.getDuration() / 20;
            String time = sec < 0 ? "inf" : String.format("%d:%02d", sec / 60, sec % 60);
            String s = name + "  " + time;
            int w = Gfx.width(s, 0.8f) + 12;
            Gfx.glass(c, 4, y, w, 14, 4, PANEL, PBORDER);
            Gfx.text(c, s, 10, y + 4, sec >= 0 && sec < 10 ? Gfx.RED : Gfx.TEXT, 0.8f);
            y += 17;
        }
    }

    private static void armor(DrawContext c, MinecraftClient mc, int sw, int sh) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int x = sw - 24, y = sh / 2 - 36;
        Gfx.glass(c, x - 3, y - 3, 22, 4 * 18 + 4, 5, PANEL, PBORDER);
        for (int i = 0; i < 4; i++) {
            ItemStack st = mc.player.getEquippedStack(slots[i]);
            if (st.isEmpty()) continue;
            c.drawItem(st, x, y + i * 18);
            c.drawStackOverlay(mc.textRenderer, st, x, y + i * 18);
        }
    }

    private static void waypoint(DrawContext c, MinecraftClient mc, int sw) {
        Vec3d p = mc.player.getEntityPos(), t = Modules.waypointPos;
        double dx = t.x - p.x, dz = t.z - p.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double want = -Math.toDegrees(Math.atan2(dx, dz));
        double rel = MathHelper.wrapDegrees(want - mc.player.getYaw());
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[(int) Math.floorMod(Math.round(rel / 45.0), 8)];
        String s = arrow + "  Waypoint  " + (int) dist + "m";
        int w = Gfx.width(s, 0.9f) + 14;
        int x = (sw - w) / 2;
        Gfx.glass(c, x, 4, w, 15, 5, PANEL, PBORDER);
        Gfx.text(c, s, x + 7, 8, Gfx.TEXT, 0.9f);
    }

    private static void key(DrawContext c, String label, boolean down, int x, int y, int w) {
        Gfx.glass(c, x, y, w, 16, 4, down ? 0x66FF8A1F : PANEL, down ? Gfx.ACC : PBORDER);
        Gfx.text(c, label, x + (w - Gfx.width(label, 0.8f)) / 2f, y + 4, down ? 0xFFFFFFFF : Gfx.TEXT, 0.8f);
    }

    private static void keystrokes(DrawContext c, MinecraftClient mc, int sh) {
        int x = 8, y = sh - 84;
        key(c, "W", mc.options.forwardKey.isPressed(), x + 18, y, 16);
        key(c, "A", mc.options.leftKey.isPressed(), x, y + 18, 16);
        key(c, "S", mc.options.backKey.isPressed(), x + 18, y + 18, 16);
        key(c, "D", mc.options.rightKey.isPressed(), x + 36, y + 18, 16);
        key(c, "SPACE", mc.options.jumpKey.isPressed(), x, y + 36, 52);
    }
}
