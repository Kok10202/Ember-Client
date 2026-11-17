# Ember Client (Fabric, Minecraft 1.21.11)

A singleplayer-only utility client with a frosted-glass ClickGUI, a time-of-day sky, a HUD, and a code-locked secret menu.

## Build
Needs Java 21.
1. Easiest: generate a fresh project at https://fabricmc.net/develop/template/ for 1.21.11 (Yarn mappings),
   then copy this project's `src/` folder over the template's `src/` (replace `fabric.mod.json`).
   Or use the build files included here and run `gradle build` (add the wrapper with `gradle wrapper`).
2. Put `build/libs/ember-client-1.3.0.jar` and Fabric API in your `.minecraft/mods` folder.

## Use
- **Right Shift** opens the menu. Left click a module = toggle, right click = expand its settings (keybind, sliders...).
- Star a module to pin it in **Favorites**. Just type to search.
- **The code:** type `code` in the search bar and click **Enter Code**. First time it asks you to *create*
  a 5-character code; after that it asks you to *enter* it. Unlocking reveals the **Secret** category
  (Fullbright, Zoom, Toggle Sprint, Coordinates, FPS/Ping, Keystrokes, Armor HUD, Potion HUD, Waypoint). It re-locks when you restart the game, or press **Lock** in the footer.
- Only a salted hash of the code is saved (`config/ember.json`). Forgot it? Delete `salt` and `codeHash` from that file.
  This hides the menu, it is not real security: anyone can edit the config or read the jar.

## 1.3.0
- All old modules removed. "Enter Code" is unchanged.
- **Glass menu:** translucent frosted panels, light borders, top sheen (the world behind is blurred by Minecraft's own menu blur).
- **Realistic Sky** (Visuals): follows the world's time of day (sunrise, noon, sunset, night), horizon haze, soft clouds,
  stars only at night, greyer in rain. It is still a screen overlay (not a replaced sky), so vanilla sun/moon are tinted, not removed.
- **Secret modules:** Fullbright, Zoom, Toggle Sprint, Coordinates, FPS / Ping, Keystrokes, Armor HUD, Potion HUD, Waypoint.
- Gameplay modules (Fullbright, Toggle Sprint) only run in singleplayer.

## 1.4.0 Casino (Casino category, always visible)
Visual-only and honest: every result is a fair secure-random roll shown on *your* screen. Nothing can be chosen or forced,
and there are no fake balances or payouts.
- **Custom Text:** up to 3 lines of your own text. Right-click the module, click a Line box, type (Enter/Esc to finish).
  Placeholders: `{fps} {x} {y} {z} {name} {time}`. Position X/Y %, Size, Colour, Glass Background.
- **Coin Flip / Dice Roller (d6, d20, d100, 1-3 dice) / Roulette:** toggle (or bind a key) to roll; animated result card, switches itself off.
- **Luck Stats:** session tally of your rolls and streaks. **Confetti:** burst on a jackpot. **Neon Border:** animated glowing screen edge.

## If it fails to compile
Not compiled where it was written. Likeliest spots: `getEntityPos()` (may be `getPos()`) in Modules/HudRenderer,
`drawStackOverlay` / `drawItem` signatures, `getRainGradient`, `StatusEffectInstance.isInfinite`, and the Click/KeyInput/CharInput
screens inherited from the previous version.
