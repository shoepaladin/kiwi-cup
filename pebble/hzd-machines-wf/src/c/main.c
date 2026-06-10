#include <pebble.h>

// ── custom font toggle ─────────────────────────────────────────────────
// 1 = Share Tech Mono TTF (resources/fonts/ShareTechMono-Regular.ttf required)
// 0 = built-in LECO fallback (no font file needed; also remove font entries
//     from package.json to avoid a missing-file build error)
#define USE_CUSTOM_FONT 1

// ── persist storage keys ───────────────────────────────────────────────
#define PERSIST_KEY_ICON_CURRENT  0   // which icon is currently displayed
#define PERSIST_KEY_TIME_FORMAT   1   // bool: true = 12h
#define PERSIST_KEY_ICON_INDEX    2   // static-mode icon selection (0-20)
#define PERSIST_KEY_ICON_MODE     3   // int: 0=static, else minutes between changes
#define PERSIST_KEY_STEP_GOAL     4   // int: daily step target
#define PERSIST_KEY_THEME_COLOR   5   // int: GColor argb byte for time + icon
#define PERSIST_KEY_VIBE_DISC     6   // bool: buzz on Bluetooth disconnect
#define PERSIST_KEY_DISC_MODE     7   // int: 0=invert colors, 1=glyph only, 2=off

// ── icon resources (order must match package.json media array) ─────────
#define NUM_ICONS 21

static const uint32_t s_icon_resources[NUM_ICONS] = {
  RESOURCE_ID_ICON_00_BURROWER,
  RESOURCE_ID_ICON_01_CLAWSTRIDER,
  RESOURCE_ID_ICON_02_SLITHERFANG,
  RESOURCE_ID_ICON_03_FANGHORN,
  RESOURCE_ID_ICON_04_SUNWING,
  RESOURCE_ID_ICON_05_LEAPLASHER,
  RESOURCE_ID_ICON_06_SCROUNGER,
  RESOURCE_ID_ICON_07_SLAUGHTERSPINE,
  RESOURCE_ID_ICON_08_CLAMBERJAW,
  RESOURCE_ID_ICON_09_TREMORTUSK,
  RESOURCE_ID_ICON_10_BRISTLEBACK,
  RESOURCE_ID_ICON_11_ROLLERBACK,
  RESOURCE_ID_ICON_12_SHELLSNAPPER,
  RESOURCE_ID_ICON_13_SKYDRIFTER,
  RESOURCE_ID_ICON_14_WIDEMAW,
  RESOURCE_ID_ICON_15_SPIKESNOUT,
  RESOURCE_ID_ICON_16_PLOWHORN,
  RESOURCE_ID_ICON_17_TIDERIPPER,
  RESOURCE_ID_ICON_18_DREADWING,
  RESOURCE_ID_ICON_19_SPECTER,
  RESOURCE_ID_ICON_20_SPECTER_PRIME,
};

// Scan-readout labels (order must match s_icon_resources)
static const char *s_machine_names[NUM_ICONS] = {
  "BURROWER", "CLAWSTRIDER", "SLITHERFANG", "FANGHORN", "SUNWING", "LEAPLASHER",
  "SCROUNGER", "SLAUGHTERSPINE", "CLAMBERJAW", "TREMORTUSK", "BRISTLEBACK", "ROLLERBACK",
  "SHELLSNAPPER", "SKYDRIFTER", "WIDEMAW", "SPIKESNOUT", "PLOWHORN", "TIDERIPPER",
  "DREADWING", "SPECTER", "SPECTER PRIME"
};

// ── platform colors ────────────────────────────────────────────────────
// HZD palette: black field, cyan text, tiffany-blue accents, orange highlights
#if defined(PBL_COLOR)
  #define COLOR_BG       GColorBlack
  #define COLOR_TEXT     GColorCyan
  #define COLOR_SUBDUED  GColorTiffanyBlue
  #define COLOR_ACCENT   GColorOrange
#else
  #define COLOR_BG       GColorBlack
  #define COLOR_TEXT     GColorWhite
  #define COLOR_SUBDUED  GColorLightGray
  #define COLOR_ACCENT   GColorWhite
#endif

// ── platform screen geometry ───────────────────────────────────────────
//
//  basalt / diorite : 144 × 168  (Pebble Time, Core 2 Duo)
//  emery            : 200 × 228  (Pebble Time 2)
//
//  Time rect right edge is flush with AMPM left edge so right-aligned
//  digits visually sit against the stacked A/M P/M column.
//  TIME height accommodates the 48/62 px font with a small top margin.
//
#if defined(PBL_PLATFORM_EMERY)
  // 12-hour time uses a slightly smaller font so a 5-char value ("10:06")
  // fits beside the stacked AM/PM column; 24-hour uses the full-size font
  // across the whole width.
  #define TIME_FONT_12_RES RESOURCE_ID_FONT_SHARE_TECH_MONO_52
  #define TIME_FONT_24_RES RESOURCE_ID_FONT_SHARE_TECH_MONO_62
  // small mono font for the date line and machine-name label
  #define LABEL_FONT_RES   RESOURCE_ID_FONT_SHARE_TECH_MONO_18
  #define LABEL_SYS_FONT   FONT_KEY_GOTHIC_18
  // 12h: smaller digits, right edge at x=172; AMPM column 174..200
  #define TIME_RECT     GRect(4,   8, 168, 72)
  // 24-hour mode has no AMPM column, so the digits use the full width
  #define TIME_RECT_FULL GRect(4,  4, 192, 72)
  // AMPM: big bold A/P over M, stacked tight (cells overlap to close the gap)
  #define AMPM_FONT     FONT_KEY_GOTHIC_28_BOLD
  #define AMPM_T_RECT   GRect(174,12,  26, 32)
  #define AMPM_B_RECT   GRect(174,36,  26, 32)
  // icon gets the reclaimed centre space; ~emery PNG is 110 px (centred)
  #define ICON_RECT     GRect(4,  84, 124, 112)
  // step arrow pushed right, snug against the battery dots; both columns
  // stop level with the icon so the name strip below spans the full width
  #define ARROW_RECT    GRect(132, 84,  24, 112)
  #define DOTS_RECT     GRect(158, 84,  28, 112)
  // divider: full-width layer; the proc leaves 10 px ends + a centre gap
  // wide enough for the date line ("TUE 06.10" at 18 px mono)
  #define DIVIDER_RECT  GRect(0,  80, 200,  2)
  #define DIVIDER_GAP   104
  #define DATE_RECT     GRect(48, 70, 104, 22)
  // machine-name label strip under the icon/arrow/dots block
  #define NAME_RECT     GRect(0, 198, 200, 24)
  #define DOT_RADIUS    4
  #define DOT_SPACING   11
  #define SHAFT_W       6
  #define HEAD_SIZE     12
  // Focus HUD: corner bracket arm length; status glyphs above right divider
  #define BRACKET_LEN   16
  #define STATUS_Y      73
  #define STATUS_QT_X   180
  #define STATUS_BT_X   162
  #define STATUS_R      5
#else  /* basalt / diorite  144×168 */
  // 12-hour time uses a slightly smaller font so a 5-char value ("10:06")
  // fits beside the stacked AM/PM column; 24-hour uses the full-size font
  // across the whole width.
  #define TIME_FONT_12_RES RESOURCE_ID_FONT_SHARE_TECH_MONO_40
  #define TIME_FONT_24_RES RESOURCE_ID_FONT_SHARE_TECH_MONO_48
  // small mono font for the date line and machine-name label
  #define LABEL_FONT_RES   RESOURCE_ID_FONT_SHARE_TECH_MONO_14
  #define LABEL_SYS_FONT   FONT_KEY_GOTHIC_14
  // 12h: smaller digits, right edge at x=122; AMPM column 122..144
  #define TIME_RECT     GRect(0,   6, 122, 56)
  // 24-hour mode has no AMPM column, so the digits use the full width
  #define TIME_RECT_FULL GRect(4,  2, 136, 58)
  // AMPM: big bold A/P over M, stacked tight (cells overlap to close the gap)
  #define AMPM_FONT     FONT_KEY_GOTHIC_24_BOLD
  #define AMPM_T_RECT   GRect(122, 4,  22, 26)
  #define AMPM_B_RECT   GRect(122,24,  22, 26)
  // icon gets the reclaimed centre space; base PNG is 80 px (centred)
  #define ICON_RECT     GRect(4,  62,  92,  84)
  // step arrow pushed right, snug against the battery dots; both columns
  // stop level with the icon so the name strip below spans the full width
  #define ARROW_RECT    GRect(100, 62,  16,  84)
  #define DOTS_RECT     GRect(116,62,  24,  84)
  // divider: full-width layer; the proc leaves 10 px ends + a centre gap
  // wide enough for the date line ("TUE 06.10" at 14 px mono)
  #define DIVIDER_RECT  GRect(0,  60, 144,  2)
  #define DIVIDER_GAP   84
  #define DATE_RECT     GRect(30, 50, 84, 18)
  // machine-name label strip under the icon/arrow/dots block
  #define NAME_RECT     GRect(0, 146, 144, 18)
  #define DOT_RADIUS    3
  #define DOT_SPACING   8
  #define SHAFT_W       4
  #define HEAD_SIZE     8
  // Focus HUD: corner bracket arm length; status glyphs above right divider
  #define BRACKET_LEN   12
  #define STATUS_Y      53
  #define STATUS_QT_X   132
  #define STATUS_BT_X   116
  #define STATUS_R      4
#endif

// ── runtime settings (loaded from persist / updated via Clay AppMessage) ──
static bool s_use_12h             = true;   // 12-hour display
static int  s_icon_index_setting  = 0;      // static icon choice (0-20)
static int  s_icon_change_minutes = 60;     // 0 = static; else minutes/change
static int  s_step_goal           = 10000;  // daily step target
static bool s_vibe_on_disconnect  = true;   // buzz when phone disconnects
static int  s_disconnect_mode     = 0;      // 0=invert colors, 1=glyph only, 2=off

// Theme color drives both the time text and the machine-icon tint.
// On inversion (Bluetooth disconnect, mode 0) foreground/background swap.
static GColor s_theme_color;                 // configured accent (default cyan)
static GColor s_color_fg;                    // current drawing color
static GColor s_color_bg;                    // current background color
static GColor s_color_accent;                // highlight (orange on color)
static GColor s_color_subdued;               // dimmed theme (HUD frame, track)
static bool   s_bt_inverted  = false;        // true while face is inverted
static bool   s_bt_connected = true;         // current phone connection state
static bool   s_quiet_time   = false;        // last observed Quiet Time state

// ── global state ───────────────────────────────────────────────────────
static Window      *s_window;
static TextLayer   *s_time_layer;
static TextLayer   *s_ampm_top_layer;
static TextLayer   *s_ampm_bot_layer;
static TextLayer   *s_date_layer;     // "TUE 06.10" in the divider gap
static TextLayer   *s_name_layer;     // machine name / step-count readout
static BitmapLayer *s_icon_layer;
static GBitmap     *s_icon_bitmap;
static Layer       *s_arrow_layer;
static Layer       *s_dots_layer;
static Layer       *s_divider_layer;
static Layer       *s_scan_layer;     // scanline wipe over the icon
static Layer       *s_hud_layer;      // corner brackets + status glyphs
#if USE_CUSTOM_FONT
static GFont        s_time_font_12;   // smaller: 12h (digits + AM/PM column)
static GFont        s_time_font_24;   // larger: 24h (full width)
static GFont        s_label_font;     // date line + machine-name label
#endif

static char s_time_buf[6];        // "HH:MM\0"
static char s_date_buf[12];       // "TUE 06.10\0"
static char s_steps_buf[16];      // "12,345 STEPS\0"
static int  s_current_steps = 0;
static int  s_current_icon  = 0;
static int  s_minute_tick   = 0;  // counts minutes toward next icon swap

// Scan animation: -1 = idle, else 0..100 sweep position over the icon
static int        s_scan_progress = -1;
static Animation *s_scan_anim     = NULL;

// Shake gesture: name label temporarily shows the numeric step count
static AppTimer *s_steps_timer   = NULL;
static bool      s_steps_overlay = false;

// ── arrowhead GPath (built once at load) ──────────────────────────────
static GPath     *s_head_path = NULL;
static GPathInfo  s_head_info;
static GPoint     s_head_pts[3];

static void head_build_path(void) {
  // Upward-pointing triangle: tip at top-center, two base corners below
  s_head_pts[0] = GPoint(HEAD_SIZE,     0);           // tip
  s_head_pts[1] = GPoint(0,             HEAD_SIZE);   // bottom-left
  s_head_pts[2] = GPoint(HEAD_SIZE * 2, HEAD_SIZE);   // bottom-right
  s_head_info.num_points = 3;
  s_head_info.points     = s_head_pts;
  if (!s_head_path) s_head_path = gpath_create(&s_head_info);
}

// ── step-arrow layer ───────────────────────────────────────────────────
// The layer is divided into 20 equal segments.
// Each segment lights up every (step_goal / 20) steps.
// A hollow track with 25 % tick marks sits behind the fill so progress
// reads against a scale even when the arrow is short.
// Filled: solid shaft + arrowhead at the current level (accent when the
// goal is met).
static void arrow_layer_update_proc(Layer *layer, GContext *ctx) {
  GRect b  = layer_get_bounds(layer);
  int   h  = b.size.h;
  int   cx = b.size.w / 2;

  // Track outline + quarter ticks (always drawn, subdued)
  graphics_context_set_stroke_color(ctx, s_color_subdued);
  graphics_draw_rect(ctx, GRect(cx - SHAFT_W / 2 - 2, 0, SHAFT_W + 4, h));
  graphics_context_set_fill_color(ctx, s_color_subdued);
  for (int q = 1; q <= 3; q++) {
    int y = h - (h * q) / 4;
    graphics_fill_rect(ctx, GRect(cx - SHAFT_W / 2 - 5, y, 3, 1), 0, GCornerNone);
  }

  int goal    = s_step_goal > 0 ? s_step_goal : 10000;
  int capped  = s_current_steps < goal ? s_current_steps : goal;
  int segments = (capped * 20) / goal;   // 0-20
  if (segments > 20) segments = 20;
  if (segments == 0) return;             // nothing earned yet

  int seg_h  = h / 20;
  int fill_h = segments * seg_h;
  int tip_y  = h - fill_h;
  if (tip_y < 0) tip_y = 0;

  // Goal met → the whole arrow switches to the accent color
  graphics_context_set_fill_color(ctx,
    segments >= 20 ? s_color_accent : s_color_fg);

  // Solid shaft from below the arrowhead to the bottom of the layer
  int shaft_top = tip_y + HEAD_SIZE;
  if (shaft_top < h) {
    graphics_fill_rect(ctx,
      GRect(cx - SHAFT_W / 2, shaft_top, SHAFT_W, h - shaft_top),
      0, GCornerNone);
  }

  // Arrowhead centered on cx, pointing up
  if (s_head_path) {
    gpath_move_to(s_head_path, GPoint(cx - HEAD_SIZE, tip_y));
    gpath_draw_filled(ctx, s_head_path);
  }
}

// ── battery-dots layer ─────────────────────────────────────────────────
// One filled dot per 10 % charge, stacked from the bottom.
// At 0 % the column is completely blank; at 20 % or less the remaining
// dots draw in the accent color as a low-battery warning.
static void dots_layer_update_proc(Layer *layer, GContext *ctx) {
  GRect b  = layer_get_bounds(layer);
  int   h  = b.size.h;
  int   cx = b.size.w / 2;

  BatteryChargeState state = battery_state_service_peek();
  int filled = state.charge_percent / 10;
  if (filled > 10) filled = 10;

  graphics_context_set_fill_color(ctx,
    state.charge_percent <= 20 ? s_color_accent : s_color_fg);

  for (int i = 0; i < filled; i++) {
    // i=0 is the bottom dot, i=9 is the top
    int y = h - (i * DOT_SPACING) - DOT_SPACING / 2;
    if (y < DOT_RADIUS || y > h) continue;
    graphics_fill_circle(ctx, GPoint(cx, y), DOT_RADIUS);
  }
}

// ── divider line ───────────────────────────────────────────────────────
// A horizontal rule under the time and above the icon/arrow/dots. It is
// split into two segments: 10 px of empty margin at each end and a centre
// gap (DIVIDER_GAP) that the date line sits inside.
static void divider_layer_update_proc(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  int W   = b.size.w;
  int h   = b.size.h;
  const int margin = 10;            // empty px at each end
  const int gap    = DIVIDER_GAP;   // empty px in the centre (date line)

  int mid_l = W / 2 - gap / 2;   // left segment ends here
  int mid_r = W / 2 + gap / 2;   // right segment starts here

  graphics_context_set_fill_color(ctx, s_color_fg);
  graphics_fill_rect(ctx, GRect(margin, 0, mid_l - margin,     h), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(mid_r,  0, (W - margin) - mid_r, h), 0, GCornerNone);
}

// ── Focus HUD layer (topmost, full screen) ─────────────────────────────
// Draws the four corner brackets of the Focus scan reticle in the subdued
// theme shade, plus two status glyphs above the right divider segment:
//   • a crescent moon while Quiet Time is active
//   • an accent "X" while the phone is disconnected (glyph-only mode)
static void hud_layer_update_proc(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  const int t  = 2;            // bracket thickness
  const int L  = BRACKET_LEN;  // bracket arm length
  const int in = 2;            // inset from the screen edge (under the bezel)
  int x0 = in, y0 = in;
  int x1 = b.size.w - in, y1 = b.size.h - in;

  graphics_context_set_fill_color(ctx, s_color_subdued);
  // top-left
  graphics_fill_rect(ctx, GRect(x0,     y0,     L, t), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x0,     y0,     t, L), 0, GCornerNone);
  // top-right
  graphics_fill_rect(ctx, GRect(x1 - L, y0,     L, t), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x1 - t, y0,     t, L), 0, GCornerNone);
  // bottom-left
  graphics_fill_rect(ctx, GRect(x0,     y1 - t, L, t), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x0,     y1 - L, t, L), 0, GCornerNone);
  // bottom-right
  graphics_fill_rect(ctx, GRect(x1 - L, y1 - t, L, t), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x1 - t, y1 - L, t, L), 0, GCornerNone);

  // Quiet Time: crescent moon (full disc with a bg-colored bite)
  if (s_quiet_time) {
    graphics_context_set_fill_color(ctx, s_color_subdued);
    graphics_fill_circle(ctx, GPoint(STATUS_QT_X, STATUS_Y), STATUS_R);
    graphics_context_set_fill_color(ctx, s_color_bg);
    graphics_fill_circle(ctx,
      GPoint(STATUS_QT_X + STATUS_R / 2 + 1, STATUS_Y - STATUS_R / 2 - 1),
      STATUS_R);
  }

  // Disconnected, glyph-only mode: small accent X
  if (!s_bt_connected && s_disconnect_mode == 1) {
    int r = STATUS_R - 1;
    graphics_context_set_stroke_width(ctx, 2);
    graphics_context_set_stroke_color(ctx, s_color_accent);
    graphics_draw_line(ctx,
      GPoint(STATUS_BT_X - r, STATUS_Y - r), GPoint(STATUS_BT_X + r, STATUS_Y + r));
    graphics_draw_line(ctx,
      GPoint(STATUS_BT_X - r, STATUS_Y + r), GPoint(STATUS_BT_X + r, STATUS_Y - r));
  }
}

// ── scanline wipe over the icon ────────────────────────────────────────
// While a scan is running the area below the sweep line is masked with the
// background color and a 2 px accent line marks the sweep position, so the
// machine silhouette "scans in" top-to-bottom like a Focus highlight.
static void scan_layer_update_proc(Layer *layer, GContext *ctx) {
  if (s_scan_progress < 0) return;   // idle: fully transparent
  GRect b = layer_get_bounds(layer);
  int y = (b.size.h * s_scan_progress) / 100;

  graphics_context_set_fill_color(ctx, s_color_bg);
  graphics_fill_rect(ctx, GRect(0, y, b.size.w, b.size.h - y), 0, GCornerNone);
  graphics_context_set_fill_color(ctx, s_color_accent);
  graphics_fill_rect(ctx, GRect(0, y, b.size.w, 2), 0, GCornerNone);
}

static void scan_anim_update(Animation *animation, const AnimationProgress progress) {
  s_scan_progress = (int)((progress * 100) / ANIMATION_NORMALIZED_MAX);
  if (s_scan_layer) layer_mark_dirty(s_scan_layer);
}

static void scan_anim_stopped(Animation *animation, bool finished, void *context) {
  s_scan_anim     = NULL;   // SDK3 auto-destroys finished animations
  s_scan_progress = -1;
  if (s_scan_layer) layer_mark_dirty(s_scan_layer);
}

static const AnimationImplementation s_scan_impl = {
  .update = scan_anim_update,
};

static void start_scan_animation(void) {
  if (!s_scan_layer) return;
  if (s_scan_anim) {
    animation_unschedule(s_scan_anim);   // stopped handler clears the pointer
  }
  s_scan_anim = animation_create();
  if (!s_scan_anim) return;
  animation_set_duration(s_scan_anim, 500);
  animation_set_curve(s_scan_anim, AnimationCurveLinear);
  animation_set_implementation(s_scan_anim, &s_scan_impl);
  animation_set_handlers(s_scan_anim,
    (AnimationHandlers){ .stopped = scan_anim_stopped }, NULL);
  animation_schedule(s_scan_anim);
}

// ── icon management ────────────────────────────────────────────────────
// Recolor a palettized (indexed-PNG) bitmap so the silhouette takes the
// foreground color and the black field becomes transparent. The grayscale
// palette level (0-3) is used as intensity so anti-aliased edges blend.
static void tint_icon(GBitmap *bmp, GColor fg) {
  GColor *pal = gbitmap_get_palette(bmp);
  if (!pal) return;                       // 1-bit (diorite) path: no palette
  int n;
  switch (gbitmap_get_format(bmp)) {
    case GBitmapFormat1BitPalette: n = 2;  break;
    case GBitmapFormat2BitPalette: n = 4;  break;
    case GBitmapFormat4BitPalette: n = 16; break;
    default: return;
  }
  for (int i = 0; i < n; i++) {
    uint8_t lum = pal[i].r;               // grayscale entry → r==g==b
    if (pal[i].g > lum) lum = pal[i].g;
    if (pal[i].b > lum) lum = pal[i].b;   // 0..3
    if (lum == 0) {
      pal[i] = GColorClear;               // black field → transparent
    } else {
      GColor c = { .a = 3,
                   .r = (uint8_t)((fg.r * lum) / 3),
                   .g = (uint8_t)((fg.g * lum) / 3),
                   .b = (uint8_t)((fg.b * lum) / 3) };
      pal[i] = c;
    }
  }
}

static void load_icon(int index) {
  if (index < 0 || index >= NUM_ICONS) index = 0;
  if (s_icon_bitmap) {
    gbitmap_destroy(s_icon_bitmap);
    s_icon_bitmap = NULL;
  }
  s_icon_bitmap = gbitmap_create_with_resource(s_icon_resources[index]);

  if (gbitmap_get_palette(s_icon_bitmap)) {
    // Color platforms: indexed PNG → tint palette to the theme color.
    tint_icon(s_icon_bitmap, s_color_fg);
    bitmap_layer_set_compositing_mode(s_icon_layer, GCompOpSet);
  } else {
    // 1-bit (diorite): GCompOpSet draws white opaque / black transparent;
    // GCompOpAssignInverted flips that for the disconnected (inverted) state.
    bitmap_layer_set_compositing_mode(s_icon_layer,
      s_bt_inverted ? GCompOpAssignInverted : GCompOpSet);
  }

  bitmap_layer_set_bitmap(s_icon_layer, s_icon_bitmap);
  layer_mark_dirty(bitmap_layer_get_layer(s_icon_layer));

  // Scan-readout caption follows the icon (unless the step-count overlay
  // is showing; its timer restores the name afterwards).
  if (s_name_layer && !s_steps_overlay) {
    text_layer_set_text(s_name_layer, s_machine_names[index]);
  }
  start_scan_animation();
}

// Recolor the *existing* icon to the current foreground color without
// reloading the resource from flash. Used on theme-color change and on
// Bluetooth invert, where the bitmap itself does not change — only its color.
static void retint_icon(void) {
  if (!s_icon_bitmap || !s_icon_layer) return;
  if (gbitmap_get_palette(s_icon_bitmap)) {
    tint_icon(s_icon_bitmap, s_color_fg);          // color: rewrite palette
    bitmap_layer_set_compositing_mode(s_icon_layer, GCompOpSet);
  } else {
    // 1-bit (diorite): flip compositing for the inverted state.
    bitmap_layer_set_compositing_mode(s_icon_layer,
      s_bt_inverted ? GCompOpAssignInverted : GCompOpSet);
  }
  layer_mark_dirty(bitmap_layer_get_layer(s_icon_layer));
}

// Apply the current foreground/background colors everywhere. Called on load,
// on theme-color change, and on Bluetooth connect/disconnect (which inverts).
static void apply_colors(void) {
  // On 1-bit (diorite) the theme color collapses to black/white, so a mid
  // theme color could map to black and vanish against the black field.
  // Force the accent to white there so indicators stay legible.
#if defined(PBL_COLOR)
  GColor theme = s_theme_color;
  // Orange highlight straight from the HZD palette; a dimmed shade of the
  // theme color carries the HUD frame and the step track.
  GColor accent  = COLOR_ACCENT;
  GColor subdued = (GColor){ .a = 3,
                             .r = (uint8_t)((s_theme_color.r * 2 + 1) / 3),
                             .g = (uint8_t)((s_theme_color.g * 2 + 1) / 3),
                             .b = (uint8_t)((s_theme_color.b * 2 + 1) / 3) };
#else
  GColor theme   = GColorWhite;
  GColor accent  = GColorWhite;
  GColor subdued = GColorWhite;
#endif

  if (s_bt_inverted) {
    s_color_bg      = theme;
    s_color_fg      = GColorBlack;
    s_color_accent  = GColorBlack;
    s_color_subdued = GColorBlack;
  } else {
    s_color_bg      = GColorBlack;
    s_color_fg      = theme;
    s_color_accent  = accent;
    s_color_subdued = subdued;
  }
  if (s_window)         window_set_background_color(s_window, s_color_bg);
  if (s_time_layer)     text_layer_set_text_color(s_time_layer,     s_color_fg);
  if (s_ampm_top_layer) text_layer_set_text_color(s_ampm_top_layer, s_color_accent);
  if (s_ampm_bot_layer) text_layer_set_text_color(s_ampm_bot_layer, s_color_accent);
  if (s_date_layer)     text_layer_set_text_color(s_date_layer,     s_color_fg);
  if (s_name_layer)     text_layer_set_text_color(s_name_layer,     s_color_accent);
  if (s_icon_layer) {
    // First call (startup) has no bitmap yet → load it; later calls (theme
    // change / BT invert) just recolor the existing bitmap in place.
    if (s_icon_bitmap) retint_icon();
    else               load_icon(s_current_icon);
  }
  if (s_arrow_layer)    layer_mark_dirty(s_arrow_layer);
  if (s_dots_layer)     layer_mark_dirty(s_dots_layer);
  if (s_divider_layer)  layer_mark_dirty(s_divider_layer);
  if (s_scan_layer)     layer_mark_dirty(s_scan_layer);
  if (s_hud_layer)      layer_mark_dirty(s_hud_layer);
}

// ── time display ───────────────────────────────────────────────────────
static void update_time(struct tm *tick_time) {
  if (s_use_12h) {
    // Narrow rect + smaller font so the digits sit flush against the AM/PM
    // column without the minutes being clipped to "...".
    layer_set_frame(text_layer_get_layer(s_time_layer), TIME_RECT);
#if USE_CUSTOM_FONT
    text_layer_set_font(s_time_layer, s_time_font_12);
#endif
    strftime(s_time_buf, sizeof(s_time_buf), "%I:%M", tick_time);
    // Drop leading zero: "09:30" → "9:30"
    if (s_time_buf[0] == '0') {
      memmove(s_time_buf, s_time_buf + 1, sizeof(s_time_buf) - 1);
    }
    bool is_am = tick_time->tm_hour < 12;
    text_layer_set_text(s_ampm_top_layer, is_am ? "A" : "P");
    text_layer_set_text(s_ampm_bot_layer, "M");
    layer_set_hidden(text_layer_get_layer(s_ampm_top_layer), false);
    layer_set_hidden(text_layer_get_layer(s_ampm_bot_layer), false);
  } else {
    // No AM/PM in 24-hour mode → give the digits the full width and the
    // larger font so the minutes don't get clipped to "...".
    layer_set_frame(text_layer_get_layer(s_time_layer), TIME_RECT_FULL);
#if USE_CUSTOM_FONT
    text_layer_set_font(s_time_layer, s_time_font_24);
#endif
    strftime(s_time_buf, sizeof(s_time_buf), "%H:%M", tick_time);
    layer_set_hidden(text_layer_get_layer(s_ampm_top_layer), true);
    layer_set_hidden(text_layer_get_layer(s_ampm_bot_layer), true);
  }
  text_layer_set_text(s_time_layer, s_time_buf);

  // Date line in the divider gap: "TUE 06.10" (uppercased manually since
  // strftime's %a is locale-cased)
  strftime(s_date_buf, sizeof(s_date_buf), "%a %m.%d", tick_time);
  for (char *p = s_date_buf; *p; p++) {
    if (*p >= 'a' && *p <= 'z') *p -= ('a' - 'A');
  }
  if (s_date_layer) text_layer_set_text(s_date_layer, s_date_buf);
}

// ── step count ─────────────────────────────────────────────────────────
static void update_steps(void) {
#if defined(PBL_HEALTH)
  // Query a real same-day window (start-of-today → now). A zero-length
  // window (now → now) reports "not available" on some firmwares, which
  // would silently force the step count to 0.
  time_t end   = time(NULL);
  time_t start = time_start_of_today();
  HealthServiceAccessibilityMask mask =
    health_service_metric_accessible(HealthMetricStepCount, start, end);
  if (mask & HealthServiceAccessibilityMaskAvailable) {
    s_current_steps = (int)health_service_sum_today(HealthMetricStepCount);
  } else {
    s_current_steps = 0;
  }
#else
  s_current_steps = 0;   // watch has no health sensor
#endif
  if (s_arrow_layer) layer_mark_dirty(s_arrow_layer);
}

#if defined(PBL_HEALTH)
// Live step updates: fired whenever HealthService has new data, so the
// arrow tracks activity without waiting for the next minute tick.
static void health_handler(HealthEventType event, void *context) {
  if (event == HealthEventSignificantUpdate || event == HealthEventMovementUpdate) {
    update_steps();
  }
}
#endif

// ── shake gesture: scan sweep + numeric step readout ───────────────────
// A wrist flick replays the scan animation and swaps the machine-name
// label for the formatted step count for a few seconds.
static void steps_overlay_end(void *context) {
  s_steps_timer   = NULL;
  s_steps_overlay = false;
  if (s_name_layer) {
    text_layer_set_text(s_name_layer, s_machine_names[s_current_icon]);
  }
}

static void tap_handler(AccelAxisType axis, int32_t direction) {
  start_scan_animation();

  if (s_current_steps >= 1000) {
    snprintf(s_steps_buf, sizeof(s_steps_buf), "%d,%03d STEPS",
             s_current_steps / 1000, s_current_steps % 1000);
  } else {
    snprintf(s_steps_buf, sizeof(s_steps_buf), "%d STEPS", s_current_steps);
  }
  if (s_name_layer) text_layer_set_text(s_name_layer, s_steps_buf);
  s_steps_overlay = true;

  if (s_steps_timer) app_timer_reschedule(s_steps_timer, 3000);
  else               s_steps_timer = app_timer_register(3000, steps_overlay_end, NULL);
}

// ── tick handler ───────────────────────────────────────────────────────
static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time(tick_time);
  update_steps();
  if (s_dots_layer) layer_mark_dirty(s_dots_layer);

  // Quiet Time has no event service, so poll it once a minute and redraw
  // the HUD glyph row when it flips.
  bool quiet = quiet_time_is_active();
  if (quiet != s_quiet_time) {
    s_quiet_time = quiet;
    if (s_hud_layer) layer_mark_dirty(s_hud_layer);
  }

  // Rotate icon when not in static mode
  if (s_icon_change_minutes > 0) {
    s_minute_tick++;
    if (s_minute_tick >= s_icon_change_minutes) {
      s_minute_tick   = 0;
      s_current_icon  = (s_current_icon + 1) % NUM_ICONS;
      persist_write_int(PERSIST_KEY_ICON_CURRENT, s_current_icon);
      load_icon(s_current_icon);
    }
  }
}

// ── battery service callback ───────────────────────────────────────────
static void battery_handler(BatteryChargeState state) {
  if (s_dots_layer) layer_mark_dirty(s_dots_layer);
}

// ── Bluetooth connection callback ──────────────────────────────────────
// Disconnect feedback depends on the configured mode:
//   0 = invert the whole face (background ↔ foreground)
//   1 = small accent glyph in the HUD status row
//   2 = no visual change
// The optional buzz is independent of the visual mode.
static void apply_bt_state(void) {
  bool inverted = (!s_bt_connected && s_disconnect_mode == 0);
  if (inverted != s_bt_inverted) {
    s_bt_inverted = inverted;
    apply_colors();
  }
  if (s_hud_layer) layer_mark_dirty(s_hud_layer);
}

static void connection_handler(bool connected) {
  if (connected == s_bt_connected) return;
  s_bt_connected = connected;
  apply_bt_state();
  // Only buzz on a *drop*, if the user enabled it, and never during Quiet Time.
  if (!connected && s_vibe_on_disconnect && !quiet_time_is_active()) {
    vibes_double_pulse();
  }
}

// ── Clay / AppMessage settings ─────────────────────────────────────────
static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  Tuple *t;

  // Time format: 1 = 12h, 0 = 24h
  t = dict_find(iterator, MESSAGE_KEY_TimeFormat);
  if (t) {
    s_use_12h = (bool)t->value->int32;
    persist_write_bool(PERSIST_KEY_TIME_FORMAT, s_use_12h);
    time_t now = time(NULL);
    update_time(localtime(&now));
  }

  // Icon selection: always apply immediately so the user sees their choice.
  // In rotation mode this also becomes the new starting point.
  t = dict_find(iterator, MESSAGE_KEY_IconIndex);
  if (t) {
    s_icon_index_setting = (int)t->value->int32;
    if (s_icon_index_setting < 0 || s_icon_index_setting >= NUM_ICONS)
      s_icon_index_setting = 0;
    persist_write_int(PERSIST_KEY_ICON_INDEX, s_icon_index_setting);
    s_current_icon = s_icon_index_setting;
    s_minute_tick  = 0;
    persist_write_int(PERSIST_KEY_ICON_CURRENT, s_current_icon);
    load_icon(s_current_icon);
  }

  // Icon rotation speed: 0 = static, else minutes between changes
  t = dict_find(iterator, MESSAGE_KEY_IconMode);
  if (t) {
    int mode = (int)t->value->int32;
    s_icon_change_minutes = mode;
    persist_write_int(PERSIST_KEY_ICON_MODE, mode);
    s_minute_tick = 0;
    if (mode == 0) {
      // Switched to static: show the chosen icon
      s_current_icon = s_icon_index_setting;
      load_icon(s_current_icon);
    }
  }

  // Step goal. Clay's number input sends a string; tolerate int too.
  t = dict_find(iterator, MESSAGE_KEY_StepGoal);
  if (t) {
    int goal = (t->type == TUPLE_CSTRING)
      ? atoi(t->value->cstring)
      : (int)t->value->int32;
    if (goal >= 1000) s_step_goal = goal;
    persist_write_int(PERSIST_KEY_STEP_GOAL, s_step_goal);
    if (s_arrow_layer) layer_mark_dirty(s_arrow_layer);
  }

  // Theme color. Clay's color picker sends a 24-bit 0xRRGGBB value (>255 for
  // any real color), which GColorFromHEX maps to the nearest Pebble GColor.
  // The legacy hand-rolled page sent a raw GColor argb byte (0-255). Accept
  // both so the watch works regardless of which config page is in use. We
  // persist the resulting argb byte for forward compatibility.
  t = dict_find(iterator, MESSAGE_KEY_ThemeColor);
  if (t) {
    uint32_t v = (uint32_t)t->value->int32;
    if (v > 255) {
      s_theme_color = GColorFromHEX(v);
    } else {
      s_theme_color.argb = (uint8_t)v;
    }
    persist_write_int(PERSIST_KEY_THEME_COLOR, (int)s_theme_color.argb);
    apply_colors();
  }

  // Vibrate-on-disconnect toggle (Clay toggle sends 1/0).
  t = dict_find(iterator, MESSAGE_KEY_VibeOnDisconnect);
  if (t) {
    s_vibe_on_disconnect = (bool)t->value->int32;
    persist_write_bool(PERSIST_KEY_VIBE_DISC, s_vibe_on_disconnect);
  }

  // Disconnect visual mode: 0=invert, 1=glyph only, 2=off
  t = dict_find(iterator, MESSAGE_KEY_DisconnectMode);
  if (t) {
    int mode = (int)t->value->int32;
    if (mode < 0 || mode > 2) mode = 0;
    s_disconnect_mode = mode;
    persist_write_int(PERSIST_KEY_DISC_MODE, mode);
    apply_bt_state();   // re-evaluate inversion under the new mode
  }
}

// ── AppMessage failure handlers (resilient settings sync) ──────────────
static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "AppMessage inbox dropped: %d", (int)reason);
}
static void outbox_failed_callback(DictionaryIterator *it,
                                   AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "AppMessage outbox failed: %d", (int)reason);
}

// ── load settings from persist storage ────────────────────────────────
static void load_settings(void) {
  // Default time format mirrors the watch's own clock setting
  s_use_12h = persist_exists(PERSIST_KEY_TIME_FORMAT)
    ? persist_read_bool(PERSIST_KEY_TIME_FORMAT)
    : !clock_is_24h_style();

  s_icon_index_setting = persist_exists(PERSIST_KEY_ICON_INDEX)
    ? persist_read_int(PERSIST_KEY_ICON_INDEX) : 0;

  s_icon_change_minutes = persist_exists(PERSIST_KEY_ICON_MODE)
    ? persist_read_int(PERSIST_KEY_ICON_MODE) : 60;

  s_step_goal = persist_exists(PERSIST_KEY_STEP_GOAL)
    ? persist_read_int(PERSIST_KEY_STEP_GOAL) : 10000;

  s_vibe_on_disconnect = persist_exists(PERSIST_KEY_VIBE_DISC)
    ? persist_read_bool(PERSIST_KEY_VIBE_DISC) : true;

  s_disconnect_mode = persist_exists(PERSIST_KEY_DISC_MODE)
    ? persist_read_int(PERSIST_KEY_DISC_MODE) : 0;
  if (s_disconnect_mode < 0 || s_disconnect_mode > 2) s_disconnect_mode = 0;

  s_current_icon = persist_exists(PERSIST_KEY_ICON_CURRENT)
    ? persist_read_int(PERSIST_KEY_ICON_CURRENT) : 0;

  // In static mode, always show the user's chosen icon
  if (s_icon_change_minutes == 0) {
    s_current_icon = s_icon_index_setting;
  }

  // Theme color (defaults to the platform accent — cyan on color watches)
  if (persist_exists(PERSIST_KEY_THEME_COLOR)) {
    s_theme_color.argb = (uint8_t)persist_read_int(PERSIST_KEY_THEME_COLOR);
  } else {
    s_theme_color = COLOR_TEXT;
  }
  s_color_fg = s_theme_color;
  s_color_bg = GColorBlack;
}

// ── Timeline Quick View (UnobstructedArea) ─────────────────────────────
// When the system overlay covers the bottom of the screen, the icon /
// arrow / dots / name cluster would be half-hidden, so hide it outright;
// the time and the date line stay visible above the overlay. The HUD layer
// stays on: its bottom brackets are simply covered by the overlay.
static void update_obstruction(void) {
  if (!s_window) return;
  Layer *root = window_get_root_layer(s_window);
  GRect full  = layer_get_bounds(root);
  GRect unob  = layer_get_unobstructed_bounds(root);
  bool obstructed = unob.size.h < full.size.h;

  if (s_icon_layer) layer_set_hidden(bitmap_layer_get_layer(s_icon_layer), obstructed);
  if (s_arrow_layer) layer_set_hidden(s_arrow_layer, obstructed);
  if (s_dots_layer)  layer_set_hidden(s_dots_layer,  obstructed);
  if (s_name_layer)  layer_set_hidden(text_layer_get_layer(s_name_layer), obstructed);
  if (s_scan_layer)  layer_set_hidden(s_scan_layer,  obstructed);
}

static void unobstructed_did_change(void *context) {
  update_obstruction();
}

// ── window setup ───────────────────────────────────────────────────────
static void main_window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  window_set_background_color(window, COLOR_BG);

  // ── Time display (right-aligned, flush with AMPM column) ─────────────
  s_time_layer = text_layer_create(TIME_RECT);
  text_layer_set_background_color(s_time_layer, GColorClear);
  text_layer_set_text_color(s_time_layer, COLOR_TEXT);
#if USE_CUSTOM_FONT
  s_time_font_12 = fonts_load_custom_font(resource_get_handle(TIME_FONT_12_RES));
  s_time_font_24 = fonts_load_custom_font(resource_get_handle(TIME_FONT_24_RES));
  // update_time() selects the right one for the current mode; seed with 24h.
  text_layer_set_font(s_time_layer, s_time_font_24);
#else
  text_layer_set_font(s_time_layer, fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS));
#endif
  text_layer_set_text_alignment(s_time_layer, GTextAlignmentRight);
  text_layer_set_overflow_mode(s_time_layer, GTextOverflowModeTrailingEllipsis);
  layer_add_child(root, text_layer_get_layer(s_time_layer));

  // ── AM/PM stacked vertically (A or P on top, M below) ─────────────────
  s_ampm_top_layer = text_layer_create(AMPM_T_RECT);
  text_layer_set_background_color(s_ampm_top_layer, GColorClear);
  text_layer_set_text_color(s_ampm_top_layer, COLOR_TEXT);
  text_layer_set_font(s_ampm_top_layer, fonts_get_system_font(AMPM_FONT));
  text_layer_set_text_alignment(s_ampm_top_layer, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_ampm_top_layer));

  s_ampm_bot_layer = text_layer_create(AMPM_B_RECT);
  text_layer_set_background_color(s_ampm_bot_layer, GColorClear);
  text_layer_set_text_color(s_ampm_bot_layer, COLOR_TEXT);
  text_layer_set_font(s_ampm_bot_layer, fonts_get_system_font(AMPM_FONT));
  text_layer_set_text_alignment(s_ampm_bot_layer, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_ampm_bot_layer));

  // ── Machine icon (lower-left) ─────────────────────────────────────────
  s_icon_bitmap = NULL;
  s_icon_layer  = bitmap_layer_create(ICON_RECT);
  bitmap_layer_set_background_color(s_icon_layer, GColorClear);
  bitmap_layer_set_alignment(s_icon_layer, GAlignCenter);
  layer_add_child(root, bitmap_layer_get_layer(s_icon_layer));

  // ── Scanline wipe (directly over the icon) ────────────────────────────
  s_scan_layer = layer_create(ICON_RECT);
  layer_set_update_proc(s_scan_layer, scan_layer_update_proc);
  layer_add_child(root, s_scan_layer);

  // ── Step-arrow (center-right column) ─────────────────────────────────
  s_arrow_layer = layer_create(ARROW_RECT);
  layer_set_update_proc(s_arrow_layer, arrow_layer_update_proc);
  layer_add_child(root, s_arrow_layer);

  // ── Battery dots (far-right column) ──────────────────────────────────
  s_dots_layer = layer_create(DOTS_RECT);
  layer_set_update_proc(s_dots_layer, dots_layer_update_proc);
  layer_add_child(root, s_dots_layer);

  // ── Divider rule (under the time, above everything below) ────────────
  s_divider_layer = layer_create(DIVIDER_RECT);
  layer_set_update_proc(s_divider_layer, divider_layer_update_proc);
  layer_add_child(root, s_divider_layer);

#if USE_CUSTOM_FONT
  s_label_font = fonts_load_custom_font(resource_get_handle(LABEL_FONT_RES));
#endif

  // ── Date line, centered in the divider gap ────────────────────────────
  s_date_layer = text_layer_create(DATE_RECT);
  text_layer_set_background_color(s_date_layer, GColorClear);
  text_layer_set_text_color(s_date_layer, COLOR_TEXT);
#if USE_CUSTOM_FONT
  text_layer_set_font(s_date_layer, s_label_font);
#else
  text_layer_set_font(s_date_layer, fonts_get_system_font(LABEL_SYS_FONT));
#endif
  text_layer_set_text_alignment(s_date_layer, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_date_layer));

  // ── Machine-name label (scan readout under the icon block) ───────────
  s_name_layer = text_layer_create(NAME_RECT);
  text_layer_set_background_color(s_name_layer, GColorClear);
  text_layer_set_text_color(s_name_layer, COLOR_ACCENT);
#if USE_CUSTOM_FONT
  text_layer_set_font(s_name_layer, s_label_font);
#else
  text_layer_set_font(s_name_layer, fonts_get_system_font(LABEL_SYS_FONT));
#endif
  text_layer_set_text_alignment(s_name_layer, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_name_layer));

  // ── Focus HUD (corner brackets + status glyphs, topmost) ──────────────
  s_hud_layer = layer_create(layer_get_bounds(root));
  layer_set_update_proc(s_hud_layer, hud_layer_update_proc);
  layer_add_child(root, s_hud_layer);

  head_build_path();
  s_quiet_time = quiet_time_is_active();
  apply_colors();   // sets bg + fg, tints and loads the current icon

  time_t now  = time(NULL);
  struct tm *t = localtime(&now);
  update_time(t);
  update_steps();
  update_obstruction();
}

static void main_window_unload(Window *window) {
  if (s_scan_anim)      { animation_unschedule(s_scan_anim);       s_scan_anim      = NULL; }
  if (s_steps_timer)    { app_timer_cancel(s_steps_timer);         s_steps_timer    = NULL; }
  if (s_head_path)      { gpath_destroy(s_head_path);              s_head_path      = NULL; }
  if (s_time_layer)     { text_layer_destroy(s_time_layer);        s_time_layer     = NULL; }
  if (s_ampm_top_layer) { text_layer_destroy(s_ampm_top_layer);    s_ampm_top_layer = NULL; }
  if (s_ampm_bot_layer) { text_layer_destroy(s_ampm_bot_layer);    s_ampm_bot_layer = NULL; }
  if (s_date_layer)     { text_layer_destroy(s_date_layer);        s_date_layer     = NULL; }
  if (s_name_layer)     { text_layer_destroy(s_name_layer);        s_name_layer     = NULL; }
  if (s_icon_layer)     { bitmap_layer_destroy(s_icon_layer);      s_icon_layer     = NULL; }
  if (s_icon_bitmap)    { gbitmap_destroy(s_icon_bitmap);          s_icon_bitmap    = NULL; }
  if (s_arrow_layer)    { layer_destroy(s_arrow_layer);            s_arrow_layer    = NULL; }
  if (s_dots_layer)     { layer_destroy(s_dots_layer);             s_dots_layer     = NULL; }
  if (s_divider_layer)  { layer_destroy(s_divider_layer);          s_divider_layer  = NULL; }
  if (s_scan_layer)     { layer_destroy(s_scan_layer);             s_scan_layer     = NULL; }
  if (s_hud_layer)      { layer_destroy(s_hud_layer);              s_hud_layer      = NULL; }
#if USE_CUSTOM_FONT
  if (s_time_font_12)   { fonts_unload_custom_font(s_time_font_12); s_time_font_12   = NULL; }
  if (s_time_font_24)   { fonts_unload_custom_font(s_time_font_24); s_time_font_24   = NULL; }
  if (s_label_font)     { fonts_unload_custom_font(s_label_font);   s_label_font     = NULL; }
#endif
}

// ── app lifecycle ──────────────────────────────────────────────────────
static void init(void) {
  load_settings();

  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_open(256, 64);

  s_window = window_create();
  window_set_window_handlers(s_window, (WindowHandlers){
    .load   = main_window_load,
    .unload = main_window_unload,
  });

  // Seed the connection/inversion state before the window lays out, so a
  // disconnected start renders its alert state immediately.
  s_bt_connected = connection_service_peek_pebble_app_connection();
  s_bt_inverted  = (!s_bt_connected && s_disconnect_mode == 0);

  window_stack_push(s_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
  battery_state_service_subscribe(battery_handler);
  connection_service_subscribe((ConnectionHandlers){
    .pebble_app_connection_handler = connection_handler,
  });
  accel_tap_service_subscribe(tap_handler);
  unobstructed_area_service_subscribe((UnobstructedAreaHandlers){
    .did_change = unobstructed_did_change,
  }, NULL);
#if defined(PBL_HEALTH)
  health_service_events_subscribe(health_handler, NULL);
#endif
}

static void deinit(void) {
  tick_timer_service_unsubscribe();
  battery_state_service_unsubscribe();
  connection_service_unsubscribe();
  accel_tap_service_unsubscribe();
  unobstructed_area_service_unsubscribe();
#if defined(PBL_HEALTH)
  health_service_events_unsubscribe();
#endif
  app_message_deregister_callbacks();
  if (s_window) { window_destroy(s_window); s_window = NULL; }
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
