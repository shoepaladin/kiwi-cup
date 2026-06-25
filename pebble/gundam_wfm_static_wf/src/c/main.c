#include <pebble.h>

// =============================================================================
// Persistent storage and settings
// =============================================================================
#define SETTINGS_KEY 1

typedef enum {
  TIME_FORMAT_SYSTEM = 0,
  TIME_FORMAT_24H    = 1,
  TIME_FORMAT_12H    = 2,
} TimeFormatPref;

typedef enum {
  DATE_FORMAT_DOW_MON_D = 0,  // "MON JAN 12"
  DATE_FORMAT_ISO       = 1,  // "2026-01-12"
  DATE_FORMAT_US        = 2,  // "01/12/2026"
  DATE_FORMAT_EU        = 3,  // "12/01/2026"
  DATE_FORMAT_LONG      = 4,  // "Monday, Jan 12"
} DateFormatPref;

// Sprite sets — declared here so ClaySettings can reference SPRITE_SET_AERIAL
// in its default initializer.
typedef enum {
  SPRITE_SET_AERIAL   = 0,
  SPRITE_SET_CALIBARN = 1,
  SPRITE_SET_GPO2A    = 2,
  SPRITE_SET_XI       = 3,
  SPRITE_SET_QUBELEY  = 4,
  SPRITE_SET_BYARLANT = 5,
} SpriteSet;

typedef struct {
  TimeFormatPref time_format;
  DateFormatPref date_format;
  GColor         accent_color;     // deprecated; rings use text color (kept for persist layout)
  bool           show_steps;
  bool           show_battery;     // deprecated; battery bar removed (kept for persist layout)
  uint8_t        low_battery_threshold;
  GColor         bg_color;         // user-configurable background
  uint32_t       step_goal;        // daily step target; 0 hides the progress arc
  uint8_t        sprite_set;       // 0 = Aerial (default), 1 = Calibarn
} ClaySettings;

static ClaySettings s_settings;
// Cached so layer update_procs don't recompute it every frame.
static GColor      s_text_color;

static void recompute_text_color(void) {
  // Pebble's built-in helper picks black or white based on the bg's luminance
  // so foreground stays legible regardless of what color the user chose.
  s_text_color = gcolor_legible_over(s_settings.bg_color);
}

static void settings_set_defaults(void) {
  s_settings.time_format  = TIME_FORMAT_SYSTEM;
  s_settings.date_format  = DATE_FORMAT_DOW_MON_D;
  s_settings.accent_color = GColorCyan;
  s_settings.show_steps   = true;
  s_settings.show_battery = true;
  s_settings.low_battery_threshold = 20;
  s_settings.bg_color     = GColorBlack;
  s_settings.step_goal    = 10000;
  s_settings.sprite_set   = SPRITE_SET_AERIAL;
  recompute_text_color();
}

static void settings_load(void) {
  settings_set_defaults();
  if (persist_exists(SETTINGS_KEY)) {
    int bytes = persist_read_data(SETTINGS_KEY, &s_settings, sizeof(s_settings));
    APP_LOG(APP_LOG_LEVEL_INFO,
            "[INIT] settings_load: persist blob found, read %d bytes (struct size=%d)",
            bytes, (int)sizeof(s_settings));
  } else {
    APP_LOG(APP_LOG_LEVEL_INFO, "[INIT] settings_load: no persist blob, using defaults");
  }
  recompute_text_color();
  APP_LOG(APP_LOG_LEVEL_INFO,
          "[INIT] settings_load result: sprite_set=%d bg_color.argb=0x%02x "
          "low_bat_thresh=%d show_steps=%d step_goal=%lu",
          (int)s_settings.sprite_set,
          (unsigned)s_settings.bg_color.argb,
          (int)s_settings.low_battery_threshold,
          (int)s_settings.show_steps,
          (unsigned long)s_settings.step_goal);
}

static void settings_save(void) {
  persist_write_data(SETTINGS_KEY, &s_settings, sizeof(s_settings));
}

// =============================================================================
// UI state
// =============================================================================
static Window       *s_window;
static Layer        *s_root_layer;

// Single static bitmap per suit — no animation frames needed.
static BitmapLayer  *s_sprite_layer;
static GBitmap      *s_sprite_bitmap = NULL;

static TextLayer    *s_time_layer;
static TextLayer    *s_date_layer;
static GFont         s_time_font_custom;
static TextLayer    *s_bt_alert_layer;
static char          s_time_buf[8];
static char          s_date_buf[24];

// Step-progress arc layer: drawn BEHIND the sprite. Hidden only when step_goal == 0.
static Layer        *s_arc_layer;
static BatteryChargeState s_battery_state;

// Steps row: shows step count normally; shows "PARTICLES LOW: XX%" when battery is low.
static TextLayer    *s_steps_layer;
static char          s_steps_text[24];
static int           s_steps_count = 0;

// =============================================================================
// Sprite — static single bitmap per suit
// =============================================================================
static void load_sprite(void) {
  uint32_t res;
  switch (s_settings.sprite_set) {
    case SPRITE_SET_CALIBARN: res = RESOURCE_ID_CB_IDLE;       break;
    case SPRITE_SET_GPO2A:    res = RESOURCE_ID_GPO2A_IDLE;    break;
    case SPRITE_SET_XI:       res = RESOURCE_ID_XI_IDLE;       break;
    case SPRITE_SET_QUBELEY:  res = RESOURCE_ID_QUBELEY_IDLE;  break;
    case SPRITE_SET_BYARLANT: res = RESOURCE_ID_BYARLANT_IDLE; break;
    case SPRITE_SET_AERIAL:
    default:                  res = RESOURCE_ID_AE_IDLE;       break;
  }
  s_sprite_bitmap = gbitmap_create_with_resource(res);
  APP_LOG(APP_LOG_LEVEL_INFO, "[BUG1] load_sprite: suit=%d bitmap=%s",
          (int)s_settings.sprite_set, s_sprite_bitmap ? "OK" : "NULL");
  if (s_sprite_layer && s_sprite_bitmap) {
    bitmap_layer_set_bitmap(s_sprite_layer, s_sprite_bitmap);
    layer_mark_dirty(bitmap_layer_get_layer(s_sprite_layer));
  }
}

static void destroy_sprite(void) {
  if (s_sprite_layer) bitmap_layer_set_bitmap(s_sprite_layer, NULL);
  if (s_sprite_bitmap) { gbitmap_destroy(s_sprite_bitmap); s_sprite_bitmap = NULL; }
}

// =============================================================================
// Triggers — tap/focus still available as hooks for future use
// =============================================================================


// =============================================================================
// Time / Date
// =============================================================================
static void format_date(const struct tm *t) {
  switch (s_settings.date_format) {
    case DATE_FORMAT_ISO:
      strftime(s_date_buf, sizeof(s_date_buf), "%Y-%m-%d", t);
      break;
    case DATE_FORMAT_US:
      strftime(s_date_buf, sizeof(s_date_buf), "%m/%d/%Y", t);
      break;
    case DATE_FORMAT_EU:
      strftime(s_date_buf, sizeof(s_date_buf), "%d/%m/%Y", t);
      break;
    case DATE_FORMAT_LONG:
      strftime(s_date_buf, sizeof(s_date_buf), "%A, %b %d", t);
      break;
    case DATE_FORMAT_DOW_MON_D:
    default:
      strftime(s_date_buf, sizeof(s_date_buf), "%a %b %d", t);
      // Uppercase for visual punch
      for (char *p = s_date_buf; *p; p++) {
        if (*p >= 'a' && *p <= 'z') *p -= 32;
      }
      break;
  }
}

static void update_time_and_date(void) {
  time_t now = time(NULL);
  struct tm *t = localtime(&now);

  bool use_24h;
  switch (s_settings.time_format) {
    case TIME_FORMAT_24H: use_24h = true; break;
    case TIME_FORMAT_12H: use_24h = false; break;
    case TIME_FORMAT_SYSTEM:
    default:              use_24h = clock_is_24h_style(); break;
  }
  strftime(s_time_buf, sizeof(s_time_buf), use_24h ? "%H:%M" : "%I:%M", t);
  // Strip leading zero in 12h
  if (!use_24h && s_time_buf[0] == '0') {
    memmove(s_time_buf, s_time_buf + 1, strlen(s_time_buf));
  }
  text_layer_set_text(s_time_layer, s_time_buf);

  format_date(t);
  text_layer_set_text(s_date_layer, s_date_buf);
}

static void tick_handler(struct tm *tick_time, TimeUnits units) {
  (void)tick_time;
  update_time_and_date();
}

// =============================================================================
// Low-battery check (forward declare refresh_steps_display for battery_handler)
// =============================================================================
static void refresh_steps_display(void);

static bool battery_is_low(void) {
  int pct = s_battery_state.charge_percent;
  if (pct < 0)   pct = 0;
  if (pct > 100) pct = 100;
  if (s_battery_state.is_charging) return false;
  return pct <= (int)s_settings.low_battery_threshold;
}

static void battery_handler(BatteryChargeState state) {
  s_battery_state = state;
  if (s_arc_layer) layer_mark_dirty(s_arc_layer);
  refresh_steps_display();
}

// Decides what to show in the steps row: battery warning when low, step count otherwise.
static void refresh_steps_display(void) {
  if (!s_steps_layer) return;
  if (battery_is_low()) {
    int pct = s_battery_state.charge_percent;
    if (pct < 0)   pct = 0;
    if (pct > 100) pct = 100;
    snprintf(s_steps_text, sizeof(s_steps_text), "PARTICLES LOW: %d%%", pct);
  } else {
    snprintf(s_steps_text, sizeof(s_steps_text), "%d steps", s_steps_count);
  }
  text_layer_set_text(s_steps_layer, s_steps_text);
}

// =============================================================================
// Step-progress: vertical tick marks radiating outward from center like clock
// hands. 24 ticks arranged in a 240° arc (8 o'clock → 12 → 4 o'clock).
// Each filled tick is a line from inner_r to outer_r along its angle.
// =============================================================================
#define ARC_TICKS       24
#define ARC_START_ANGLE ((int32_t)((int64_t)TRIG_MAX_ANGLE * 240 / 360))
#define ARC_SWEEP_ANGLE ((int32_t)((int64_t)TRIG_MAX_ANGLE * 240 / 360))

static void arc_update_proc(Layer *layer, GContext *ctx) {
  if (s_settings.step_goal == 0) return;

  GRect b = layer_get_bounds(layer);
  GPoint center = GPoint(b.size.w / 2, b.size.h / 2);
  int outer_r = (b.size.w < b.size.h ? b.size.w : b.size.h) / 2 - 2;
  int inner_r = outer_r - 10;  // tick length = 10px
  if (inner_r < 4) return;

  uint32_t goal  = s_settings.step_goal;
  uint32_t steps = (uint32_t)(s_steps_count < 0 ? 0 : s_steps_count);
  int filled = (int)((uint64_t)steps * ARC_TICKS / goal);
  if (filled > ARC_TICKS) filled = ARC_TICKS;

  graphics_context_set_stroke_color(ctx, s_text_color);

  for (int i = 0; i < ARC_TICKS; i++) {
    int32_t angle = ARC_START_ANGLE +
                    (int32_t)(((int64_t)ARC_SWEEP_ANGLE * i) / (ARC_TICKS - 1));
    int32_t sa = sin_lookup(angle);
    int32_t ca = cos_lookup(angle);
    GPoint p1 = GPoint(center.x + (int)(sa * inner_r / TRIG_MAX_RATIO),
                       center.y - (int)(ca * inner_r / TRIG_MAX_RATIO));
    GPoint p2 = GPoint(center.x + (int)(sa * outer_r / TRIG_MAX_RATIO),
                       center.y - (int)(ca * outer_r / TRIG_MAX_RATIO));
    // Filled ticks are thick; unfilled ticks are thin (1px, barely visible)
    graphics_context_set_stroke_width(ctx, (i < filled) ? 3 : 1);
    graphics_draw_line(ctx, p1, p2);
  }
}

// =============================================================================
// Steps (horizontal text below the date, left-aligned)
// =============================================================================
static void update_steps(void) {
#if defined(PBL_HEALTH)
  HealthServiceAccessibilityMask mask = health_service_metric_accessible(
      HealthMetricStepCount, time_start_of_today(), time(NULL));
  if (mask & HealthServiceAccessibilityMaskAvailable) {
    s_steps_count = (int)health_service_sum_today(HealthMetricStepCount);
  } else {
    s_steps_count = 0;
  }
#else
  s_steps_count = 0;
#endif
  refresh_steps_display();
  if (s_arc_layer) layer_mark_dirty(s_arc_layer);
}

#if defined(PBL_HEALTH)
static void health_handler(HealthEventType event, void *context) {
  // Movement covers step-count changes during the day; Significant covers
  // day rollover and the initial-subscription fire. Together they're the
  // complete set we care about for the step total.
  if (event == HealthEventMovementUpdate
      || event == HealthEventSignificantUpdate) {
    update_steps();
  }
}
#endif

// =============================================================================
// AppMessage (Clay settings inbox)
// =============================================================================
static void apply_theme(void) {
  APP_LOG(APP_LOG_LEVEL_INFO,
          "[BUG2] apply_theme: bg_color.argb=0x%02x text_color.argb=0x%02x window=%s root=%s",
          (unsigned)s_settings.bg_color.argb, (unsigned)s_text_color.argb,
          s_window ? "OK" : "NULL", s_root_layer ? "OK" : "NULL");
  if (s_window) window_set_background_color(s_window, s_settings.bg_color);
  if (s_time_layer)  text_layer_set_text_color(s_time_layer,  s_text_color);
  if (s_date_layer)  text_layer_set_text_color(s_date_layer,  s_text_color);
  if (s_steps_layer) text_layer_set_text_color(s_steps_layer, s_text_color);
  if (s_arc_layer)   layer_mark_dirty(s_arc_layer);
  if (s_root_layer) {
    layer_mark_dirty(s_root_layer);
    APP_LOG(APP_LOG_LEVEL_INFO, "[BUG2] apply_theme: root_layer marked dirty");
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "[BUG2] apply_theme: root_layer is NULL — dirty mark skipped");
  }
}

static void apply_settings_to_ui(void) {
  s_battery_state = battery_state_service_peek();
  APP_LOG(APP_LOG_LEVEL_INFO,
          "[BUG3] apply_settings_to_ui: fresh battery peek => pct=%d charging=%d",
          s_battery_state.charge_percent, (int)s_battery_state.is_charging);

  update_time_and_date();
  update_steps();
  apply_theme();

  if (s_steps_layer) {
    bool hide = !s_settings.show_steps;
    APP_LOG(APP_LOG_LEVEL_INFO,
            "[BUG4] apply_settings_to_ui: show_steps=%d -> setting hidden=%d on steps_layer",
            (int)s_settings.show_steps, (int)hide);
    layer_set_hidden(text_layer_get_layer(s_steps_layer), hide);
    if (s_root_layer) layer_mark_dirty(s_root_layer);
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "[BUG4] apply_settings_to_ui: s_steps_layer is NULL");
  }
}

static void inbox_received_handler(DictionaryIterator *iter, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "[INBOX] inbox_received_handler fired");
  Tuple *t;

  t = dict_find(iter, MESSAGE_KEY_TimeFormat);
  if (t) {
    int v = (t->type == TUPLE_CSTRING) ? atoi(t->value->cstring) : t->value->int32;
    APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] TimeFormat=%d", v);
    if (v >= 0 && v <= 2) s_settings.time_format = (TimeFormatPref)v;
  } else {
    APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] TimeFormat key absent");
  }

  t = dict_find(iter, MESSAGE_KEY_DateFormat);
  if (t) {
    int v = (t->type == TUPLE_CSTRING) ? atoi(t->value->cstring) : t->value->int32;
    APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] DateFormat=%d", v);
    if (v >= 0 && v <= 4) s_settings.date_format = (DateFormatPref)v;
  } else {
    APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] DateFormat key absent");
  }

  t = dict_find(iter, MESSAGE_KEY_AccentColor);
  if (t) {
    if (t->type == TUPLE_CSTRING) {
      s_settings.accent_color = GColorFromHEX(strtol(t->value->cstring, NULL, 16));
      APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] AccentColor str=%s", t->value->cstring);
    } else {
      s_settings.accent_color = GColorFromHEX(t->value->int32);
      APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] AccentColor int=0x%06lx", (unsigned long)t->value->int32);
    }
  }

  // BUG 2: log every detail of the BackgroundColor path
  t = dict_find(iter, MESSAGE_KEY_BackgroundColor);
  if (t) {
    uint32_t raw_hex;
    if (t->type == TUPLE_CSTRING) {
      raw_hex = (uint32_t)strtol(t->value->cstring, NULL, 16);
      APP_LOG(APP_LOG_LEVEL_INFO, "[BUG2] BackgroundColor str='%s' -> hex=0x%06lx",
              t->value->cstring, (unsigned long)raw_hex);
      s_settings.bg_color = GColorFromHEX(raw_hex);
    } else {
      raw_hex = (uint32_t)t->value->int32;
      APP_LOG(APP_LOG_LEVEL_INFO, "[BUG2] BackgroundColor int=0x%06lx", (unsigned long)raw_hex);
      s_settings.bg_color = GColorFromHEX(raw_hex);
    }
    // GColor.argb is the 1-byte Pebble color encoding; log it so we know if
    // GColorFromHEX mapped the value correctly.
    APP_LOG(APP_LOG_LEVEL_INFO, "[BUG2] bg_color.argb after GColorFromHEX = 0x%02x",
            (unsigned)s_settings.bg_color.argb);
    recompute_text_color();
    APP_LOG(APP_LOG_LEVEL_INFO, "[BUG2] text_color.argb after recompute = 0x%02x",
            (unsigned)s_text_color.argb);
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "[BUG2] BackgroundColor key ABSENT from inbox");
  }

  t = dict_find(iter, MESSAGE_KEY_ShowSteps);
  if (t) {
    s_settings.show_steps = (t->value->int32 != 0);
    APP_LOG(APP_LOG_LEVEL_INFO, "[BUG4] ShowSteps key arrived: raw=%ld -> show_steps=%d",
            (long)t->value->int32, (int)s_settings.show_steps);
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "[BUG4] ShowSteps key ABSENT from inbox");
  }

  t = dict_find(iter, MESSAGE_KEY_LowBatteryThreshold);
  if (t) {
    int v = (t->type == TUPLE_CSTRING) ? atoi(t->value->cstring) : t->value->int32;
    if (v < 1)  v = 1;
    if (v > 99) v = 99;
    s_settings.low_battery_threshold = (uint8_t)v;
    APP_LOG(APP_LOG_LEVEL_INFO, "[BUG3] LowBatteryThreshold arrived: clamped=%d", v);
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "[BUG3] LowBatteryThreshold key ABSENT from inbox");
  }

  t = dict_find(iter, MESSAGE_KEY_StepGoal);
  if (t) {
    int32_t v = (t->type == TUPLE_CSTRING)
                ? (int32_t)atol(t->value->cstring)
                : t->value->int32;
    if (v < 0)      v = 0;
    if (v > 100000) v = 100000;
    s_settings.step_goal = (uint32_t)v;
    APP_LOG(APP_LOG_LEVEL_DEBUG, "[INBOX] StepGoal=%ld", (long)v);
  }

  // SpriteSet: destroy old bitmap, load new one
  t = dict_find(iter, MESSAGE_KEY_SpriteSet);
  if (t) {
    int v = (t->type == TUPLE_CSTRING) ? atoi(t->value->cstring) : t->value->int32;
    APP_LOG(APP_LOG_LEVEL_INFO,
            "[BUG1] SpriteSet arrived: value=%d sprite_layer=%s",
            v, s_sprite_layer ? "OK" : "NULL");
    if (v < 0) v = 0;
    if (v > SPRITE_SET_BYARLANT) v = SPRITE_SET_BYARLANT;
    s_settings.sprite_set = (uint8_t)v;
    destroy_sprite();
    load_sprite();
  } else {
    APP_LOG(APP_LOG_LEVEL_WARNING, "[BUG1] SpriteSet key ABSENT from inbox");
  }

  APP_LOG(APP_LOG_LEVEL_INFO, "[INBOX] calling settings_save + apply_settings_to_ui");
  settings_save();
  apply_settings_to_ui();
  APP_LOG(APP_LOG_LEVEL_INFO, "[INBOX] inbox_received_handler done");
}

// =============================================================================
// Bluetooth alert
// =============================================================================
#define ALERT_H        20  // height of the BT alert bar
#define ALERT_OFFSET   0  // gap between sprite band edge and alert bar (raise/lower the bar with this)

static void bt_handler(bool connected) {
  if (s_bt_alert_layer) {
    text_layer_set_text(s_bt_alert_layer, connected ? "" : "\\\\\\ I-FIELD OFF \\\\\\");
  }
}

  
// =============================================================================
// Window setup
// =============================================================================
static void main_window_load(Window *window) {
  s_root_layer = window_get_root_layer(window);
  GRect b = layer_get_bounds(s_root_layer);
  window_set_background_color(window, s_settings.bg_color);

  load_sprite();

  // Layout (top → bottom): sprite band (rings + arc drawn behind it), time,
  // date, steps. The text stack is anchored to the BOTTOM of the screen and
  // the sprite band gets whatever is left above it, so positions never depend
  // on the size of the currently selected suit bitmap (the six suits range
  // from 85 to 138 px tall).

#if defined(PBL_RECT)
  // Emery (200 x 228)
  const int side_margin   = 8;
  const int bottom_inset  = 0;
  s_time_font_custom      = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_BLACKOPS_42));
  GFont time_font         = s_time_font_custom;
  const int time_h        = 46;
  GFont date_font         = fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD);
  const int date_h        = 28;
  GFont steps_font        = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  const int steps_h       = 20;
#else
  // Chalk / Gabbro (180 x 180, round) — extra bottom inset keeps the steps
  // line inside the visible circle.
  const int side_margin   = 24;
  const int bottom_inset  = 6;
  s_time_font_custom      = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_BLACKOPS_30));
  GFont time_font         = s_time_font_custom;
  const int time_h        = 36;
  GFont date_font         = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  const int date_h        = 22;
  GFont steps_font        = fonts_get_system_font(FONT_KEY_GOTHIC_14);
  const int steps_h       = 16;
#endif

  const int inner_left  = side_margin;
  const int inner_right = b.size.w - side_margin;
  const int inner_w     = inner_right - inner_left;

  // Text rows, stacked bottom-up from the screen edge. The -2 overlaps
  // swallow the fonts' internal top padding.
  const int steps_y = b.size.h - bottom_inset - steps_h;
  const int date_y  = steps_y - date_h + 2;
  const int time_y  = date_y - time_h + 2;

  // Sprite band: everything above the time row.
  const int sprite_top    = 0;
  const int sprite_band_h = time_y - sprite_top;

  // ---------- Step progress arc (drawn BEHIND sprite) ----------
  // Arc sits just outside the sprite's footprint. Round display uses a smaller
  // extent so the top tick stays inside the visible circle.
#if defined(PBL_RECT)
  int arc_extent = 138;
#else
  int arc_extent = 96;
#endif
  GRect arc_frame = GRect(
      (b.size.w - arc_extent) / 2,
      sprite_top + (sprite_band_h - arc_extent) / 2,
      arc_extent, arc_extent);
  s_arc_layer = layer_create(arc_frame);
  layer_set_update_proc(s_arc_layer, arc_update_proc);
  layer_add_child(s_root_layer, s_arc_layer);

  // ---------- Sprite (drawn ABOVE rings + arc) ----------
  // Fixed full-band frame: swapping in a differently-sized suit bitmap can
  // never move or clip the layout. GAlignBottom keeps every suit standing on
  // the same baseline, directly above the time, centered horizontally.
  GRect sprite_frame = GRect(0, sprite_top, b.size.w, sprite_band_h);
  s_sprite_layer = bitmap_layer_create(sprite_frame);
  bitmap_layer_set_compositing_mode(s_sprite_layer, GCompOpSet);
  bitmap_layer_set_alignment(s_sprite_layer, GAlignBottom);
  bitmap_layer_set_bitmap(s_sprite_layer, s_sprite_bitmap);
  layer_add_child(s_root_layer, bitmap_layer_get_layer(s_sprite_layer));

  // ---------- BT alert bar (overlays the bottom of the sprite band) ----------
  GRect alert_frame = GRect(inner_left,
                            time_y - ALERT_H + ALERT_OFFSET,
                            inner_w,
                            ALERT_H);
  s_bt_alert_layer = text_layer_create(alert_frame);
  text_layer_set_background_color(s_bt_alert_layer, GColorClear);
  text_layer_set_text_color(s_bt_alert_layer, GColorWhite);
  text_layer_set_font(s_bt_alert_layer, steps_font);
  text_layer_set_text_alignment(s_bt_alert_layer, GTextAlignmentCenter);
  text_layer_set_text(s_bt_alert_layer,
                      connection_service_peek_pebble_app_connection()
                        ? "" : "\\\\\\ I-FIELD OFF \\\\\\");
  layer_add_child(s_root_layer, text_layer_get_layer(s_bt_alert_layer));
  
  // ---------- Time (center) ----------
  GRect time_frame = GRect(inner_left, time_y, inner_w, time_h);
  s_time_layer = text_layer_create(time_frame);
  text_layer_set_background_color(s_time_layer, GColorClear);
  text_layer_set_text_color(s_time_layer, GColorWhite);
  text_layer_set_font(s_time_layer, time_font);
  text_layer_set_text_alignment(s_time_layer, GTextAlignmentCenter);
  layer_add_child(s_root_layer, text_layer_get_layer(s_time_layer));

  // ---------- Date (center, below time) ----------
  GRect date_frame = GRect(inner_left, date_y, inner_w, date_h);
  s_date_layer = text_layer_create(date_frame);
  text_layer_set_background_color(s_date_layer, GColorClear);
  text_layer_set_text_color(s_date_layer, GColorWhite);
  text_layer_set_font(s_date_layer, date_font);
  text_layer_set_text_alignment(s_date_layer, GTextAlignmentCenter);
  layer_add_child(s_root_layer, text_layer_get_layer(s_date_layer));

  // ---------- Steps (LEFT-aligned, below date) ----------
  GRect steps_frame = GRect(inner_left, steps_y, inner_w, steps_h);
  s_steps_layer = text_layer_create(steps_frame);
  text_layer_set_background_color(s_steps_layer, GColorClear);
  text_layer_set_text_color(s_steps_layer, GColorWhite);
  text_layer_set_font(s_steps_layer, steps_font);
  text_layer_set_text_alignment(s_steps_layer, GTextAlignmentCenter);
  layer_add_child(s_root_layer, text_layer_get_layer(s_steps_layer));

  apply_settings_to_ui();
}

static void main_window_unload(Window *window) {
  text_layer_destroy(s_time_layer);
  text_layer_destroy(s_date_layer);
  text_layer_destroy(s_steps_layer);
  layer_destroy(s_arc_layer);
  bitmap_layer_destroy(s_sprite_layer);
  text_layer_destroy(s_bt_alert_layer);
  destroy_sprite();
  fonts_unload_custom_font(s_time_font_custom);
}

// =============================================================================
// init / deinit
// =============================================================================
static void init(void) {
  settings_load();

  s_window = window_create();
  window_set_window_handlers(s_window, (WindowHandlers){
      .load   = main_window_load,
      .unload = main_window_unload,
  });
  window_stack_push(s_window, true);

  // Time updates every minute
  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);

  // Battery
  s_battery_state = battery_state_service_peek();
  battery_state_service_subscribe(battery_handler);

  // Bluetooth connection
  connection_service_subscribe((ConnectionHandlers){
      .pebble_app_connection_handler = bt_handler,
  });
  
  // Health / steps
#if defined(PBL_HEALTH)
  health_service_events_subscribe(health_handler, NULL);
#endif

  // AppMessage (Clay)
  // Use a fixed buffer size rather than app_message_inbox/outbox_size_maximum().
  // size_maximum() requests ~8KB each (16KB total) which exhausts the heap after
  // sprites are loaded, causing all settings messages to NACK and never deliver.
  // 512 bytes comfortably fits all 12 Clay key/value pairs (each is a short int
  // or hex string) with overhead to spare.
  app_message_register_inbox_received(inbox_received_handler);
  app_message_open(512, 512);
}

static void deinit(void) {
#if defined(PBL_HEALTH)
  health_service_events_unsubscribe();
#endif
  battery_state_service_unsubscribe();
  connection_service_unsubscribe();
  tick_timer_service_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
  return 0;
}
