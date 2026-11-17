package com.ember.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Small drawing helpers + the frosted-glass colour palette (translucent panels, orange accent). */
public final class Gfx {
    public static final int BG = 0x8C12161C, SIDE = 0x3CFFFFFF, ROW = 0x2EFFFFFF, ROW_H = 0x48FFFFFF,
            BORDER = 0x5CFFFFFF, TEXT = 0xFFFFFFFF, DIM = 0xFFD0D4DC, MUTED = 0xFFA4AAB6, ACC = 0xFFFF8A1F,
            ACC_DARK = 0x4DFF8A1F, OFF = 0x66FFFFFF, RED = 0xFFFF6369;
    public static final int TOG_W = 22, TOG_H = 12;

    private Gfx() {}

    public static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Filled rectangle with rounded corners (radius r, in GUI pixels). */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    /** Translucent-safe bordered shape: draws a 1px ring, then the fill inside it (no border bleeding through). */
    public static void roundBorder(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        // ring
        c.fill(x + r, y, x + w - r, y + 1, border);
        c.fill(x + r, y + h - 1, x + w - r, y + h, border);
        c.fill(x, y + r, x + 1, y + h - r, border);
        c.fill(x + w - 1, y + r, x + w, y + h - r, border);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            double dy2 = r - (i + 1) - 0.5;
            int next = i + 1 < r ? r - (int) Math.round(Math.sqrt(r * r - dy2 * dy2)) : 0;
            int seg = Math.max(1, inset - next);
            // top corners
            c.fill(x + next, y + i, x + next + seg, y + i + 1, border);
            c.fill(x + w - next - seg, y + i, x + w - next, y + i + 1, border);
            // bottom corners
            c.fill(x + next, y + h - 1 - i, x + next + seg, y + h - i, border);
            c.fill(x + w - next - seg, y + h - 1 - i, x + w - next, y + h - i, border);
        }
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Frosted glass panel: translucent fill, light border and a soft sheen along the top. */
    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        roundBorder(c, x, y, w, h, r, fill, border);
        int sh = Math.min(h / 2, 26);
        c.fillGradient(x + r, y + 1, x + w - r, y + 1 + sh, 0x26FFFFFF, 0x00FFFFFF);
    }

    public static void text(DrawContext c, String s, float x, float y, int col, float scale) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr(), s, 0, 0, col, false);
        m.popMatrix();
    }

    public static int width(String s, float scale) { return (int) Math.ceil(tr().getWidth(s) * scale); }

    public static void textRight(DrawContext c, String s, int right, float y, int col, float scale) {
        text(c, s, right - width(s, scale), y, col, scale);
    }

    /** Shortens s with "..." until it fits maxW. Always terminates (the old loop grew the string forever). */
    public static String fit(String s, float scale, int maxW) {
        if (width(s, scale) <= maxW) return s;
        int n = s.length();
        while (n > 0 && width(s.substring(0, n).trim() + "...", scale) > maxW) n--;
        return s.substring(0, n).trim() + "...";
    }

    /** 22x12 pill switch. */
    public static void toggle(DrawContext c, int x, int y, boolean on) {
        round(c, x, y, TOG_W, TOG_H, 6, on ? ACC : OFF);
        c.fill(x + 4, y + 1, x + TOG_W - 4, y + 2, 0x30FFFFFF);
        round(c, on ? x + TOG_W - 10 : x + 2, y + 2, 8, 8, 4, 0xFFFFFFFF);
    }

    public static String keyName(int key) {
        if (key < 0) return "NONE";
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> {
                String n = GLFW.glfwGetKeyName(key, 0);
                if (n != null) yield n.toUpperCase();
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                yield "KEY " + key;
            }
        };
    }
}
