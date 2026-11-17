package com.ember.client;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Fair, honest mini games for the Casino modules. Every result comes from a cryptographically
 * secure RNG and nothing can be chosen or forced; this is for fun on your own screen.
 */
public final class Games {
    public static final SecureRandom RNG = new SecureRandom();
    public static final long SPIN_MS = 1300, HOLD_MS = 5000, TOTAL_MS = SPIN_MS + HOLD_MS;
    private static final Set<Integer> REDS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /** The result card currently on screen (only one at a time). */
    public static final class Roll {
        public boolean active, jackpot, confettiFired;
        public String title = "", result = "", sub = "";
        public int color = 0xFFFFFFFF, spinKind, spinMax;   // spinKind 0 coin, 1 number
        public long start;
    }
    public static final Roll current = new Roll();

    // ---- session stats (not saved)
    public static int heads, tails, coinStreak; public static boolean lastHeads;
    public static int diceRolls; public static long diceSum;
    public static int red, black, green;

    private Games() {}

    public static boolean cardVisible() { return current.active && System.currentTimeMillis() - current.start <= TOTAL_MS; }

    private static void show(String title, String result, String sub, int color, boolean jackpot, int spinKind, int spinMax) {
        Roll r = current;
        r.active = true; r.title = title; r.result = result; r.sub = sub; r.color = color;
        r.jackpot = jackpot; r.confettiFired = false; r.spinKind = spinKind; r.spinMax = spinMax;
        r.start = System.currentTimeMillis();
    }

    public static void coinFlip() {
        boolean h = RNG.nextBoolean();
        if (coinStreak > 0 && h == lastHeads) coinStreak++; else coinStreak = 1;
        lastHeads = h;
        if (h) heads++; else tails++;
        show("COIN FLIP", h ? "HEADS" : "TAILS", coinStreak > 1 ? coinStreak + " in a row" : "50 / 50",
                h ? 0xFFFFC53D : 0xFFC8CCD4, coinStreak >= 3, 0, 0);
    }

    public static void dice(int sides, int count) {
        int sum = 0; StringBuilder parts = new StringBuilder();
        boolean allMax = true;
        for (int i = 0; i < count; i++) {
            int v = RNG.nextInt(sides) + 1;
            sum += v; allMax &= v == sides;
            if (i > 0) parts.append(" + ");
            parts.append(v);
        }
        diceRolls++; diceSum += sum;
        show(count + "d" + sides, Integer.toString(sum), count > 1 ? parts.toString() : "1 - " + sides,
                allMax ? 0xFF3DD68C : 0xFFFFFFFF, allMax, 1, sides * count);
    }

    public static void roulette() {
        int n = RNG.nextInt(37);
        String col = n == 0 ? "GREEN" : REDS.contains(n) ? "RED" : "BLACK";
        if (n == 0) green++; else if (REDS.contains(n)) red++; else black++;
        show("ROULETTE", Integer.toString(n), col, n == 0 ? 0xFF3DD68C : REDS.contains(n) ? 0xFFFF5A5F : 0xFFE6E8EC, n == 0, 1, 36);
    }
}
