package com.ember.client.ui;

import com.ember.client.Games;
import com.ember.client.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/** Draws the Casino category: custom text, roll result card, confetti, neon border, luck stats. */
public final class CasinoRenderer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int[] TEXT_COLS = {0xFFFFFF, 0xFF8A1F, 0xFFC53D, 0x3DD68C, 0xFF5A5F, 0x4DD8FF, 0xB07CFF};
    private static final int[] NEON_COLS = {-1, 0xFF8A1F, 0xB07CFF, 0x4DD8FF, 0xFFC53D};
    private static final int PANEL = 0x70101418, PBORDER = 0x40FFFFFF;

    // confetti particles
    private static final int MAXP = 160;
    private static final float[] PX = new float[MAXP], PY = new float[MAXP], VX = new float[MAXP], VY = new float[MAXP];
    private static final int[] PC = new int[MAXP];
    private static final long[] PT = new long[MAXP];
    private static long lastFrame;

    private CasinoRenderer() {}

    // ------------------------------------------------------------------ custom text

    private static String fill(String s, MinecraftClient mc) {
        return s.replace("{fps}", Integer.toString(mc.getCurrentFps()))
                .replace("{x}", Integer.toString((int) mc.player.getX()))
                .replace("{y}", Integer.toString((int) mc.player.getY()))
                .replace("{z}", Integer.toString((int) mc.player.getZ()))
                .replace("{name}", mc.player.getName().getString())
                .replace("{time}", LocalTime.now().format(CLOCK));
    }

    public static void customText(DrawContext c, MinecraftClient mc, int sw, int sh) {
        String[] lines = new String[3];
        int n = 0, bw = 0;
        float sc = (float) Modules.ctScale.num;
        for (var st : Modules.ctLine) {
            String t = fill(st.text, mc);
            if (t.isBlank()) continue;
            lines[n++] = t;
            bw = Math.max(bw, Gfx.width(t, sc));
        }
        if (n == 0) return;
        int lineH = (int) Math.ceil(9 * sc) + 2, pad = 6;
        int bh = n * lineH;
        int boxW = bw + pad * 2, boxH = bh + pad * 2 - 2;
        int x = (int) ((sw - boxW) * Modules.ctX.num / 100.0), y = (int) ((sh - boxH) * Modules.ctY.num / 100.0);
        if (Modules.ctBg.bool) Gfx.glass(c, x, y, boxW, boxH, 5, PANEL, PBORDER);
        int col = 0xFF000000 | TEXT_COLS[Modules.ctColor.mode];
        for (int i = 0; i < n; i++) {
            int lw = Gfx.width(lines[i], sc);
            Gfx.text(c, lines[i], x + (boxW - lw) / 2f, y + pad - 1 + i * lineH, col, sc);
        }
    }

    // ------------------------------------------------------------------ roll card

    private static String spinText(Games.Roll r, long now) {
        Random rnd = new Random((now / 70) * 31L + r.spinKind);
        if (r.spinKind == 0) return rnd.nextBoolean() ? "HEADS" : "TAILS";
        return Integer.toString(rnd.nextInt(Math.max(1, r.spinMax + 1)));
    }

    public static void card(DrawContext c, int sw, int sh) {
        if (!Games.cardVisible()) return;
        Games.Roll r = Games.current;
        long now = System.currentTimeMillis(), age = now - r.start;
        boolean spinning = age < Games.SPIN_MS;
        long fade = Games.TOTAL_MS - age;
        int alpha = fade < 500 ? (int) (255 * fade / 500.0) : 255;
        int w = 150, h = 58, x = (sw - w) / 2, y = 30;

        Gfx.glass(c, x, y, w, h, 8, 0x90101418, (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF);
        Gfx.text(c, r.title, x + (w - Gfx.width(r.title, 0.8f)) / 2f, y + 6, Gfx.MUTED, 0.8f);

        String big = spinning ? spinText(r, now) : r.result;
        int col = spinning ? 0xFFFFFFFF : r.color;
        float sc = 2.2f;
        Gfx.text(c, big, x + (w - Gfx.width(big, sc)) / 2f, y + 19, (alpha << 24) | (col & 0xFFFFFF), sc);
        if (!spinning)
            Gfx.text(c, r.sub, x + (w - Gfx.width(r.sub, 0.8f)) / 2f, y + 44, Gfx.DIM, 0.8f);
        else
            c.fill(x + 10, y + h - 8, x + 10 + (int) ((w - 20) * (age / (double) Games.SPIN_MS)), y + h - 6, Gfx.ACC);

        if (!spinning && r.jackpot && !r.confettiFired) {
            r.confettiFired = true;
            if (Modules.on(Modules.confetti)) burst(sw);
        }
    }

    // ------------------------------------------------------------------ confetti

    private static void burst(int sw) {
        Random rnd = new Random();
        int[] cols = {0xFF5A5F, 0xFFC53D, 0x3DD68C, 0x4DD8FF, 0xB07CFF, 0xFF8A1F, 0xFFFFFF};
        long now = System.currentTimeMillis();
        for (int i = 0; i < MAXP; i++) {
            PX[i] = sw / 2f + (rnd.nextFloat() - 0.5f) * 80;
            PY[i] = 40;
            double a = rnd.nextDouble() * Math.PI;           // fan out upwards/sideways
            double sp = 60 + rnd.nextDouble() * 160;
            VX[i] = (float) (Math.cos(a) * sp * (rnd.nextBoolean() ? 1 : -1));
            VY[i] = (float) (-Math.sin(a) * sp * 0.6);
            PC[i] = cols[rnd.nextInt(cols.length)];
            PT[i] = now + 1800 + rnd.nextInt(1400);
        }
    }

    public static void confetti(DrawContext c, int sh) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (int i = 0; i < MAXP; i++) {
            if (PT[i] <= now) continue;
            VY[i] += 220 * dt;           // gravity
            PX[i] += VX[i] * dt; PY[i] += VY[i] * dt;
            VX[i] *= 0.995f;
            if (PY[i] > sh) { PT[i] = 0; continue; }
            int al = (int) (255 * Math.min(1.0, (PT[i] - now) / 500.0));
            c.fill((int) PX[i], (int) PY[i], (int) PX[i] + 2, (int) PY[i] + 3, (al << 24) | PC[i]);
        }
    }

    // ------------------------------------------------------------------ neon border

    public static void neon(DrawContext c, int sw, int sh) {
        int t = (int) Modules.neonThick.num;
        double sp = Modules.neonSpeed.num;
        long now = System.currentTimeMillis();
        int base = NEON_COLS[Modules.neonColor.mode];
        float hue = (float) ((now * sp / 20000.0) % 1.0);
        double pulse = 0.65 + 0.35 * Math.sin(now / (900.0 / sp));
        int a = (int) (170 * pulse);
        int col = base < 0 ? Color.HSBtoRGB(hue, 0.75f, 1f) & 0xFFFFFF : base;
        int on = (a << 24) | col, off = col;
        c.fillGradient(0, 0, sw, t, on, off);
        c.fillGradient(0, sh - t, sw, sh, off, on);
        for (int i = 0; i < t; i += 2) {
            int al = (int) (a * (1.0 - (double) i / t));
            c.fill(i, t, i + 2, sh - t, (al << 24) | col);
            c.fill(sw - i - 2, t, sw - i, sh - t, (al << 24) | col);
        }
    }

    // ------------------------------------------------------------------ luck stats (left column)

    public static int luckStats(DrawContext c, int y) {
        String[] rows = new String[3]; int n = 0;
        if (Games.heads + Games.tails > 0)
            rows[n++] = "Coin  H " + Games.heads + " / T " + Games.tails + (Games.coinStreak > 1 ? "  streak " + Games.coinStreak : "");
        if (Games.diceRolls > 0)
            rows[n++] = String.format("Dice  %d rolls  avg %.1f", Games.diceRolls, Games.diceSum / (double) Games.diceRolls);
        if (Games.red + Games.black + Games.green > 0)
            rows[n++] = "Roulette  R " + Games.red + "  B " + Games.black + "  G " + Games.green;
        for (int i = 0; i < n; i++) {
            int w = Gfx.width(rows[i], 0.8f) + 12;
            Gfx.glass(c, 4, y, w, 14, 4, PANEL, PBORDER);
            Gfx.text(c, rows[i], 10, y + 4, Gfx.TEXT, 0.8f);
            y += 17;
        }
        return y;
    }
}
