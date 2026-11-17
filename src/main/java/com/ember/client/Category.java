package com.ember.client;

public enum Category {
    FAVORITES("Favorites", "FEATURES", false),
    VISUALS("Visuals", "FEATURES", false),
    CASINO("Casino", "FEATURES", false),
    /** Only listed once the code has been entered. */
    SECRET("Secret", "OTHERS", true);

    public final String label, section;
    public final boolean secret;

    Category(String label, String section, boolean secret) { this.label = label; this.section = section; this.secret = secret; }
}
