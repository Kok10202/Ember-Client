package com.ember.client.ui;

import com.ember.client.Category;
import com.ember.client.CodeLock;
import com.ember.client.Config;
import com.ember.client.EmberClient;
import com.ember.client.Module;
import com.ember.client.Modules;
import com.ember.client.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** The main menu: sidebar of categories, search bar, module list with expandable settings. */
public class ClickGui extends Screen {
    private static final int SIDE_W = 108, MOD_H = 28, SET_H = 16;

    private enum Kind { CATEGORY, MODULE, STAR, EXPAND, KEY, HUDFLAG, BOOL, MODE, NUM, TEXT, CODE_ENTRY, F_HUD, F_MSG, F_LOCK }

    private record Hit(int x, int y, int w, int h, Kind kind, Module m, Setting s, Category c, boolean list) {}

    private Category cat;
    private String query = "";
    private double scroll, contentH;
    private final Set<Module> expanded = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private Hit drag;
    private Module listening;
    private Setting editing;
    private String notice = "";
    private long noticeUntil;
    private int lx, ly, lw, lh; // list viewport

    public ClickGui() { this(Category.VISUALS); }

    public ClickGui(Category start) {
        super(Text.literal("Ember"));
        this.cat = start;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void removed() { Config.save(); }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        hits.clear();

        int pw = Math.min(460, width - 20), ph = Math.min(300, height - 20);
        int px = (width - pw) / 2, py = (height - ph) / 2;

        for (int i = 4; i >= 1; i--) Gfx.round(c, px - i, py - i, pw + i * 2, ph + i * 2, 8 + i, 0x0C000000); // soft shadow
        Gfx.glass(c, px, py, pw, ph, 8, Gfx.BG, Gfx.BORDER);

        drawSidebar(c, mx, my, px, py, ph);

        int x0 = px + SIDE_W + 10, w0 = pw - SIDE_W - 20;

        // search bar
        Gfx.roundBorder(c, x0, py + 10, w0, 20, 5, Gfx.ROW, Gfx.BORDER);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty()) {
            Gfx.text(c, "Search modules...", x0 + 8, py + 16, Gfx.MUTED, 1f);
            if (w0 > 250) Gfx.textRight(c, "Type to search", x0 + w0 - 8, py + 16, Gfx.MUTED, 1f);
        } else Gfx.text(c, query + (blink ? "_" : ""), x0 + 8, py + 16, Gfx.TEXT, 1f);

        // list
        lx = x0; ly = py + 38; lw = w0 - 6; lh = (py + ph - 30) - ly;
        drawList(c, mx, my);

        // scrollbar
        if (contentH > lh) {
            int bh = Math.max(16, (int) (lh * (lh / contentH)));
            int by = ly + (int) ((lh - bh) * (scroll / Math.max(1, contentH - lh)));
            Gfx.round(c, lx + lw + 3, ly, 2, lh, 1, Gfx.ROW);
            Gfx.round(c, lx + lw + 3, by, 2, bh, 1, Gfx.OFF);
        }

        drawFooter(c, mx, my, x0, w0, py + ph - 24);
    }

    private int count(Category k) {
        int n = 0;
        for (Module m : Modules.of(k)) if (Modules.visible(m)) n++;
        return n;
    }

    private void drawSidebar(DrawContext c, int mx, int my, int px, int py, int ph) {
        Gfx.round(c, px + 2, py + 2, SIDE_W - 2, ph - 4, 6, Gfx.SIDE);

        // brand
        Gfx.round(c, px + 10, py + 10, 20, 20, 5, Gfx.ACC);
        Gfx.text(c, "E", px + 17, py + 16, 0xFF1A1004, 1f);
        Gfx.text(c, "Ember", px + 37, py + 11, Gfx.TEXT, 1f);
        Gfx.text(c, "v" + EmberClient.version(), px + 37, py + 21, Gfx.MUTED, 1f);
        c.fill(px + 10, py + 38, px + SIDE_W - 8, py + 39, Gfx.BORDER);

        int y = py + 46;
        String lastSec = "";
        for (Category k : Category.values()) {
            if (k.secret && !CodeLock.isUnlocked()) continue;
            if (!k.section.equals(lastSec)) {
                lastSec = k.section;
                if (y > py + 46) y += 6;
                Gfx.text(c, k.section, px + 12, y, Gfx.MUTED, 1f);
                y += 13;
            }
            boolean sel = query.isEmpty() && k == cat;
            boolean hov = Gfx.in(mx, my, px + 6, y, SIDE_W - 10, 16);
            if (sel) {
                Gfx.round(c, px + 6, y, SIDE_W - 10, 16, 4, Gfx.ROW_H);
                Gfx.round(c, px + 6, y + 3, 2, 10, 1, Gfx.ACC);
            } else if (hov) Gfx.round(c, px + 6, y, SIDE_W - 10, 16, 4, Gfx.ROW);
            Gfx.text(c, k.label, px + 14, y + 4, sel ? Gfx.TEXT : 0xFFA0A0AA, 1f);
            int n = count(k);
            if (n > 0) Gfx.textRight(c, Integer.toString(n), px + SIDE_W - 10, y + 4, k.secret ? Gfx.ACC : Gfx.MUTED, 1f);
            hits.add(new Hit(px + 6, y, SIDE_W - 10, 16, Kind.CATEGORY, null, null, k, false));
            y += 18;
        }

        // profile
        int by = py + ph - 34;
        if (by > y + 4) {
            c.fill(px + 10, by - 6, px + SIDE_W - 8, by - 5, Gfx.BORDER);
            String name = client != null && client.player != null ? client.player.getName().getString() : "player";
            Gfx.round(c, px + 10, by, 20, 20, 10, Gfx.ACC_DARK);
            Gfx.text(c, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), px + 17, by + 6, Gfx.ACC, 1f);
            Gfx.text(c, Gfx.fit(name, 1f, SIDE_W - 46), px + 36, by + 2, Gfx.TEXT, 1f);
            Gfx.round(c, px + 36, by + 14, 4, 4, 2, 0xFF3DD68C);
            Gfx.text(c, "Online", px + 43, by + 12, Gfx.MUTED, 1f);
        }
    }

    private void drawList(DrawContext c, int mx, int my) {
        String q = query.toLowerCase().trim();
        List<Module> items = new ArrayList<>();
        if (!q.isEmpty()) {
            for (Module m : Modules.all())
                if (Modules.visible(m) && (m.name.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) items.add(m);
            items.sort(Comparator.comparing(m -> m.name));
        } else {
            for (Module m : Modules.of(cat)) if (Modules.visible(m)) items.add(m);
        }
        boolean codeEntry = !q.isEmpty() && q.length() >= 3 && "enter code".contains(q) && !CodeLock.isUnlocked();

        c.enableScissor(lx, ly, lx + lw, ly + lh);
        int top = ly - (int) scroll;
        int y = top;
        if (codeEntry) {
            boolean hov = Gfx.in(mx, my, lx, y, lw, MOD_H) && Gfx.in(mx, my, lx, ly, lw, lh);
            Gfx.round(c, lx, y, lw, MOD_H, 5, hov ? Gfx.ROW_H : Gfx.ROW);
            Gfx.text(c, "Enter Code", lx + 10, y + 5, Gfx.TEXT, 1f);
            Gfx.text(c, CodeLock.hasCode() ? "Unlock the hidden menu." : "Create a code to hide the secret menu.", lx + 10, y + 16, Gfx.DIM, 1f);
            Gfx.textRight(c, ">", lx + lw - 12, y + 10, Gfx.ACC, 1f);
            hits.add(new Hit(lx, y, lw, MOD_H, Kind.CODE_ENTRY, null, null, null, true));
            y += MOD_H + 4;
        }
        for (Module m : items) y = drawModule(c, m, lx, y, lw, mx, my);

        if (items.isEmpty() && !codeEntry) {
            String msg = !q.isEmpty() ? "No modules found" : cat == Category.FAVORITES ? "Star a module to pin it here" : "Nothing here yet";
            Gfx.text(c, msg, lx + lw / 2f - Gfx.width(msg, 1f) / 2f, ly + 28, Gfx.MUTED, 1f);
        }
        c.disableScissor();

        contentH = y - top;
        double max = Math.max(0, contentH - lh);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private int drawModule(DrawContext c, Module m, int x, int y, int w, int mx, int my) {
        boolean inView = y + MOD_H > ly && y < ly + lh;
        boolean hov = inView && Gfx.in(mx, my, x, y, w, MOD_H) && Gfx.in(mx, my, lx, ly, lw, lh);
        int bg = m.enabled ? (hov ? 0x66FF8A1F : 0x4DFF8A1F) : (hov ? Gfx.ROW_H : Gfx.ROW);
        Gfx.round(c, x, y, w, MOD_H, 6, bg);
        c.fillGradient(x + 6, y, x + w - 6, y + 1, 0x30FFFFFF, 0x30FFFFFF);
        if (m.enabled) Gfx.round(c, x, y + 7, 2, MOD_H - 14, 1, Gfx.ACC);

        boolean open = expanded.contains(m);
        int tx = x + w - Gfx.TOG_W - 10, ex = tx - 22, sx = ex - 20;

        Gfx.text(c, m.name, x + 11, y + 5, Gfx.TEXT, 1f);
        Gfx.text(c, Gfx.fit(m.desc, 1f, sx - x - 20), x + 11, y + 16, Gfx.DIM, 1f);

        Gfx.text(c, m.favorite ? "★" : "☆", sx + 4, y + 10, m.favorite ? Gfx.ACC : Gfx.MUTED, 1f);
        Gfx.text(c, open ? "v" : ">", ex + 7, y + 10, Gfx.MUTED, 1f);
        Gfx.toggle(c, tx, y + 8, m.enabled);

        hits.add(new Hit(x, y, w, MOD_H, Kind.MODULE, m, null, null, true));
        hits.add(new Hit(sx, y, 18, MOD_H, Kind.STAR, m, null, null, true));
        hits.add(new Hit(ex, y, 20, MOD_H, Kind.EXPAND, m, null, null, true));
        hits.add(new Hit(tx - 4, y, Gfx.TOG_W + 8, MOD_H, Kind.MODULE, m, null, null, true));
        y += MOD_H;

        if (open) {
            y += 2;
            y = settingRow(c, m, null, Kind.KEY, "Keybind", x, y, w, mx, my);
            y = settingRow(c, m, null, Kind.HUDFLAG, "Show in Arraylist", x, y, w, mx, my);
            for (Setting s : m.settings) {
                Kind k = s.type == Setting.Type.BOOL ? Kind.BOOL : s.type == Setting.Type.MODE ? Kind.MODE : s.type == Setting.Type.TEXT ? Kind.TEXT : Kind.NUM;
                y = settingRow(c, m, s, k, s.name, x, y, w, mx, my);
            }
            y += 2;
        }
        return y + 4;
    }

    private int settingRow(DrawContext c, Module m, Setting s, Kind k, String label, int x, int y, int w, int mx, int my) {
        Gfx.round(c, x + 6, y, w - 12, SET_H, 3, 0x38000000);
        Gfx.text(c, label, x + 14, y + 4, Gfx.DIM, 1f);
        int right = x + w - 14;
        switch (k) {
            case KEY -> {
                String t = listening == m ? "..." : Gfx.keyName(m.key);
                int bw = Math.max(40, Gfx.width(t, 1f) + 12);
                Gfx.roundBorder(c, right - bw, y + 2, bw, 12, 3, Gfx.ROW, listening == m ? Gfx.ACC : Gfx.BORDER);
                Gfx.text(c, t, right - bw + (bw - Gfx.width(t, 1f)) / 2f, y + 4, Gfx.TEXT, 1f);
                hits.add(new Hit(right - bw, y, bw, SET_H, Kind.KEY, m, null, null, true));
            }
            case HUDFLAG -> {
                Gfx.toggle(c, right - Gfx.TOG_W, y + 2, m.showInHud);
                hits.add(new Hit(right - Gfx.TOG_W - 4, y, Gfx.TOG_W + 8, SET_H, Kind.HUDFLAG, m, null, null, true));
            }
            case BOOL -> {
                Gfx.toggle(c, right - Gfx.TOG_W, y + 2, s.bool);
                hits.add(new Hit(right - Gfx.TOG_W - 4, y, Gfx.TOG_W + 8, SET_H, Kind.BOOL, m, s, null, true));
            }
            case MODE -> {
                String t = "< " + s.modeName() + " >";
                Gfx.textRight(c, t, right, y + 4, Gfx.TEXT, 1f);
                int tw = Gfx.width(t, 1f);
                hits.add(new Hit(right - tw - 4, y, tw + 8, SET_H, Kind.MODE, m, s, null, true));
            }
            case NUM -> {
                String v = s.numText();
                Gfx.textRight(c, v, right, y + 4, Gfx.TEXT, 1f);
                int tw = 90, tx = right - 34 - tw;
                double f = (s.num - s.min) / (s.max - s.min);
                Gfx.round(c, tx, y + 7, tw, 3, 1, Gfx.OFF);
                Gfx.round(c, tx, y + 7, Math.max(2, (int) (tw * f)), 3, 1, Gfx.ACC);
                Gfx.round(c, tx + (int) (tw * f) - 2, y + 3, 4, 10, 2, 0xFFFFFFFF);
                hits.add(new Hit(tx - 2, y, tw + 4, SET_H, Kind.NUM, m, s, null, true));
            }
            case TEXT -> {
                boolean ed = editing == s;
                int bx = x + 14 + Gfx.width(label, 1f) + 10, bwid = right - bx;
                if (bwid > 30) {
                    Gfx.roundBorder(c, bx, y + 2, bwid, 12, 3, Gfx.ROW, ed ? Gfx.ACC : Gfx.BORDER);
                    String t = s.text + (ed && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                    while (Gfx.width(t, 1f) > bwid - 8 && t.length() > 1) t = t.substring(1);
                    Gfx.text(c, t, bx + 4, y + 4, s.text.isEmpty() && !ed ? Gfx.MUTED : Gfx.TEXT, 1f);
                    hits.add(new Hit(bx, y, bwid, SET_H, Kind.TEXT, m, s, null, true));
                }
            }
            default -> {}
        }
        return y + SET_H + 2;
    }

    private void drawFooter(DrawContext c, int mx, int my, int x0, int w0, int y) {
        int fx = x0;
        Gfx.text(c, "HUD", fx, y + 4, Gfx.DIM, 1f);
        fx += Gfx.width("HUD", 1f) + 6;
        Gfx.toggle(c, fx, y + 2, Config.hud);
        hits.add(new Hit(fx - 2, y, Gfx.TOG_W + 4, 16, Kind.F_HUD, null, null, null, false));
        fx += Gfx.TOG_W + 16;

        Gfx.text(c, "Messages", fx, y + 4, Gfx.DIM, 1f);
        fx += Gfx.width("Messages", 1f) + 6;
        Gfx.toggle(c, fx, y + 2, Config.toggleMessages);
        hits.add(new Hit(fx - 2, y, Gfx.TOG_W + 4, 16, Kind.F_MSG, null, null, null, false));
        fx += Gfx.TOG_W + 16;

        if (CodeLock.isUnlocked()) {
            int bw = Gfx.width("Lock", 1f) + 16;
            Gfx.roundBorder(c, fx, y, bw, 16, 4, Gfx.ACC_DARK, Gfx.ACC);
            Gfx.text(c, "Lock", fx + 8, y + 4, Gfx.ACC, 1f);
            hits.add(new Hit(fx, y, bw, 16, Kind.F_LOCK, null, null, null, false));
            fx += bw + 12;
        }

        boolean showNotice = System.currentTimeMillis() < noticeUntil;
        String hint = showNotice ? notice : "Right-click to expand";
        if (x0 + w0 - Gfx.width(hint, 1f) > fx + 8)
            Gfx.textRight(c, hint, x0 + w0, y + 4, showNotice ? Gfx.ACC : Gfx.MUTED, 1f);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int b = click.button();
        if (listening != null) { listening = null; return true; }
        if (editing != null) { editing = null; Config.save(); }
        boolean inList = Gfx.in(mx, my, lx, ly, lw, lh);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!Gfx.in(mx, my, h.x, h.y, h.w, h.h)) continue;
            if (h.list && !inList) continue;
            handle(h, mx, b);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void handle(Hit h, double mx, int button) {
        switch (h.kind) {
            case CATEGORY -> { cat = h.c; query = ""; scroll = 0; }
            case MODULE -> {
                if (button == 1) toggleExpanded(h.m);
                else if (button == 0) toggleModule(h.m);
            }
            case STAR -> { h.m.favorite = !h.m.favorite; Config.save(); }
            case EXPAND -> toggleExpanded(h.m);
            case KEY -> listening = h.m;
            case HUDFLAG -> h.m.showInHud = !h.m.showInHud;
            case BOOL -> h.s.bool = !h.s.bool;
            case MODE -> h.s.cycle(button == 1 ? -1 : 1);
            case NUM -> { drag = h; setFromMouse(h, mx); }
            case TEXT -> editing = h.s;
            case CODE_ENTRY -> client.setScreen(new CodeScreen());
            case F_HUD -> { Config.hud = !Config.hud; Config.save(); }
            case F_MSG -> { Config.toggleMessages = !Config.toggleMessages; Config.save(); }
            case F_LOCK -> {
                CodeLock.lock();
                if (cat.secret) cat = Category.VISUALS;
                scroll = 0;
            }
        }
    }

    private void toggleExpanded(Module m) { if (!expanded.remove(m)) expanded.add(m); }

    private void toggleModule(Module m) {
        m.enabled = !m.enabled;
        if (m.enabled && m.gameplay && !client.isInSingleplayer()) {
            notice = m.name + " only runs in singleplayer";
            noticeUntil = System.currentTimeMillis() + 2500;
        }
        Config.save();
    }

    private void setFromMouse(Hit h, double mx) {
        double f = (mx - h.x - 2) / (double) (h.w - 4);
        h.s.setNum(h.s.min + Math.max(0, Math.min(1, f)) * (h.s.max - h.s.min));
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag != null) { setFromMouse(drag, click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drag != null) { drag = null; Config.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll = Math.max(0, Math.min(Math.max(0, contentH - lh), scroll - vAmount * 14));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (listening != null) {
            if (k == GLFW.GLFW_KEY_BACKSPACE) listening.key = -1;
            else if (k != GLFW.GLFW_KEY_ESCAPE) listening.key = k;
            listening = null;
            Config.save();
            return true;
        }
        if (editing != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { editing = null; Config.save(); }
            else if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!editing.text.isEmpty()) editing.text = editing.text.substring(0, editing.text.length() - 1); }
            else if (input.isPaste()) { editing.text = (editing.text + client.keyboard.getClipboard()).replaceAll("[\\r\\n]", " "); if (editing.text.length() > 60) editing.text = editing.text.substring(0, 60); }
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) { query = ""; scroll = 0; } else close();
            return true;
        }
        if (k == EmberClient.OPEN_KEY) { close(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); scroll = 0; }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (listening != null) return true;
        int cp = input.codepoint();
        if (editing != null) {
            if (cp >= 32 && cp != 127 && editing.text.length() < 60) editing.text += new String(Character.toChars(cp));
            return true;
        }
        if (cp >= 32 && cp != 127 && query.length() < 32) {
            query += new String(Character.toChars(cp));
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }
}
