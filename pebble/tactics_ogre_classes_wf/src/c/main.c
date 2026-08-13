#include <pebble.h>

#define NUM_CLASSES 15
#define NUM_ROWS 3
#define STORAGE_KEY_CONFIG 1
#define STORAGE_KEY_WEATHER 2

// Watchfaces are unloaded and relaunched constantly, so the last reading is
// persisted and restored on launch rather than showing "--" until the phone
// happens to push a fresh one. A reading older than this is treated as stale.
#define WEATHER_MAX_AGE_SECS (3 * 60 * 60)
// How often the watch asks the phone for a new reading.
#define WEATHER_REFRESH_SECS (30 * 60)

#define SPRITE_W 51
#define SPRITE_H 96
#define EDGE_MARGIN 4
#define SPRITE_TEXT_GAP 6

static Window *s_main_window;
static Layer *s_canvas_layer;
static GBitmap *s_sprite_bitmaps[NUM_ROWS] = {NULL, NULL, NULL};

typedef struct {
  uint8_t class_id;   // 0..NUM_CLASSES-1
  uint8_t gender;     // 0=female, 1=male (ignored for unique heroes)
  uint8_t stat_type;  // 0..9
} RowConfig;

typedef struct {
  RowConfig rows[NUM_ROWS];
  uint8_t temp_unit;
  uint8_t dist_unit;
} WatchfaceConfig;

static WatchfaceConfig s_config = {
  .rows = {
    {0, 0, 0},   // rune_fencer female, 12h clock
    {1, 1, 8},   // barbarian male, weather
    {4, 0, 5}    // archer female, battery
  },
  .temp_unit = 0,
  .dist_unit = 0
};

// Last weather reading from the phone, persisted across relaunches.
typedef struct {
  int32_t temp_c;      // integer Celsius
  int32_t fetched_at;  // unix time of the reading (0 = never)
} WeatherState;

static WeatherState s_weather = {0, 0};
static time_t s_last_weather_request = 0;

static int s_step_count = 0;
static int s_distance_m = 0;         // walked distance today, meters
static int s_calories = 0;           // total kcal today (active + resting)
static int s_battery_percent = 0;
static bool s_bt_connected = false;
static int s_heart_rate = 0;         // last heart-rate sample in BPM (0 = none)
static char s_stat_text[NUM_ROWS][32];

// Maps [class_id][gender] -> resource id.
// For unique heroes (no gender), both columns point to the same resource.
static const uint32_t SPRITE_RESOURCES[NUM_CLASSES][2] = {
  {RESOURCE_ID_SPRITE_RUNE_FENCER_FEMALE,    RESOURCE_ID_SPRITE_RUNE_FENCER_MALE},      // 0
  {RESOURCE_ID_SPRITE_BARBARIAN_FEMALE,      RESOURCE_ID_SPRITE_BARBARIAN_MALE},        // 1
  {RESOURCE_ID_SPRITE_WARRIOR_FEMALE,        RESOURCE_ID_SPRITE_WARRIOR_MALE},          // 2
  {RESOURCE_ID_SPRITE_KNIGHT_FEMALE,         RESOURCE_ID_SPRITE_KNIGHT_MALE},           // 3
  {RESOURCE_ID_SPRITE_ARCHER_FEMALE,         RESOURCE_ID_SPRITE_ARCHER_MALE},           // 4
  {RESOURCE_ID_SPRITE_WIZARD_FEMALE,         RESOURCE_ID_SPRITE_WIZARD_MALE},           // 5
  {RESOURCE_ID_SPRITE_CLERIC_FEMALE,         RESOURCE_ID_SPRITE_CLERIC_MALE},           // 6
  {RESOURCE_ID_SPRITE_SWORDMASTER_FEMALE,    RESOURCE_ID_SPRITE_SWORDMASTER_MALE},      // 7
  {RESOURCE_ID_SPRITE_TERROR_KNIGHT_FEMALE,  RESOURCE_ID_SPRITE_TERROR_KNIGHT_MALE},    // 8
  {RESOURCE_ID_SPRITE_WARLOCK_WITCH_FEMALE,  RESOURCE_ID_SPRITE_WARLOCK_WITCH_MALE},    // 9
  {RESOURCE_ID_SPRITE_NECROMANCER_FEMALE,    RESOURCE_ID_SPRITE_NECROMANCER_MALE},      // 10
  {RESOURCE_ID_SPRITE_DENEB,                 RESOURCE_ID_SPRITE_DENEB},                 // 11
  {RESOURCE_ID_SPRITE_RAVNESS,               RESOURCE_ID_SPRITE_RAVNESS},               // 12
  {RESOURCE_ID_SPRITE_DIEGO,                 RESOURCE_ID_SPRITE_DIEGO},                 // 13
  {RESOURCE_ID_SPRITE_CANOPUS,               RESOURCE_ID_SPRITE_CANOPUS}                // 14
};

// ─── Drawing ───────────────────────────────────────────────────────

// Draw a string with separate fonts for digits vs letters,
// baseline-aligned so both sit on the same bottom edge.
static void draw_mixed_text(GContext *ctx, const char *text,
                            int x, int baseline_y, int max_w,
                            GFont num_font, int num_h,
                            GFont letter_font, int letter_h) {
  char buf[32];
  int i = 0;
  int cur_x = x;
  int max_x = x + max_w;

  while (text[i] != '\0' && cur_x < max_x) {
    bool is_digit = (text[i] >= '0' && text[i] <= '9');
    GFont font  = is_digit ? num_font   : letter_font;
    int font_h  = is_digit ? num_h      : letter_h;

    // Collect consecutive same-type characters
    int j = 0;
    while (text[i] != '\0' && j < 31) {
      bool char_is_digit = (text[i] >= '0' && text[i] <= '9');
      if (char_is_digit != is_digit) break;
      buf[j++] = text[i++];
    }
    buf[j] = '\0';

    // Measure rendered width of this chunk
    GSize size = graphics_text_layout_get_content_size(
        buf, font,
        GRect(0, 0, max_x - cur_x, 50),
        GTextOverflowModeWordWrap, GTextAlignmentLeft);

    // Align chunk to common baseline (bottom of tallest font)
    int chunk_y = baseline_y - font_h;

    graphics_draw_text(ctx, buf, font,
        GRect(cur_x, chunk_y, size.w + 4, font_h + 8),
        GTextOverflowModeWordWrap, GTextAlignmentLeft, NULL);

    cur_x += size.w;
  }
}

// Total rendered width of a mixed digit/letter string, using the same
// chunking as draw_mixed_text so the two stay in sync.
static int measure_mixed_width(const char *text, GFont num_font, GFont letter_font) {
  char buf[32];
  int i = 0;
  int total = 0;

  while (text[i] != '\0') {
    bool is_digit = (text[i] >= '0' && text[i] <= '9');
    GFont font = is_digit ? num_font : letter_font;

    int j = 0;
    while (text[i] != '\0' && j < 31) {
      bool char_is_digit = (text[i] >= '0' && text[i] <= '9');
      if (char_is_digit != is_digit) break;
      buf[j++] = text[i++];
    }
    buf[j] = '\0';

    GSize size = graphics_text_layout_get_content_size(
        buf, font, GRect(0, 0, 200, 50),
        GTextOverflowModeWordWrap, GTextAlignmentLeft);
    total += size.w;
  }
  return total;
}

// Font ladders: index 0 is the largest. The renderer walks down the ladder
// until the string fits the available column, so wide screens get the big
// font and narrow ones (basalt) shrink just enough to avoid clipping.
typedef struct { const char *num_key; int num_h; const char *letter_key; int letter_h; } FontLevel;

static const FontLevel CLOCK_LADDER[] = {
  {FONT_KEY_BITHAM_42_BOLD,            42, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_BITHAM_34_MEDIUM_NUMBERS,  34, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_BITHAM_30_BLACK,           30, FONT_KEY_GOTHIC_24_BOLD, 24},
  {FONT_KEY_GOTHIC_24_BOLD,            24, FONT_KEY_GOTHIC_24_BOLD, 24},
};

static const FontLevel STAT_LADDER[] = {
  {FONT_KEY_LECO_38_BOLD_NUMBERS, 38, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_LECO_32_BOLD_NUMBERS, 32, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_LECO_26_BOLD_NUMBERS_AM_PM, 26, FONT_KEY_GOTHIC_24_BOLD, 24},
  {FONT_KEY_LECO_20_BOLD_NUMBERS, 20, FONT_KEY_GOTHIC_18_BOLD, 18},
};

// Emery (200x228) has a wider column and higher PPI, so its numeric stats can
// start one rung larger. Same fall-through behavior — if 42px doesn't fit the
// column, the ladder walks down to the standard sizes, so nothing clips.
static const FontLevel STAT_LADDER_EMERY[] = {
  {FONT_KEY_LECO_42_NUMBERS,      42, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_LECO_38_BOLD_NUMBERS, 38, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_LECO_32_BOLD_NUMBERS, 32, FONT_KEY_GOTHIC_28_BOLD, 28},
  {FONT_KEY_LECO_26_BOLD_NUMBERS_AM_PM, 26, FONT_KEY_GOTHIC_24_BOLD, 24},
  {FONT_KEY_LECO_20_BOLD_NUMBERS, 20, FONT_KEY_GOTHIC_18_BOLD, 18},
};

static void canvas_update_proc(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  int screen_w = bounds.size.w;
  int screen_h = bounds.size.h;

  graphics_context_set_fill_color(ctx, GColorBlack);
  graphics_fill_rect(ctx, bounds, 0, GCornerNone);

  int row_h = screen_h / NUM_ROWS;
  graphics_context_set_compositing_mode(ctx, GCompOpSet);

  // Sprites are a fixed 96px tall, so three of them (288px) cannot stack
  // without overlapping on any supported screen. Distribute them across the
  // full usable height [0 .. screen_h-96] so every unit is fully on-screen
  // (overlapping each other) rather than clipped off the top or bottom edge.
  int sprite_span = screen_h - SPRITE_H;
  if (sprite_span < 0) sprite_span = 0;

  bool is_emery = (screen_h >= 228);

  for (int i = 0; i < NUM_ROWS; i++) {
    int y = i * row_h;
    // The middle row is mirrored: sprite on the right, text on the left.
    bool icon_on_right = (i == 1);
    int sprite_x = icon_on_right ? (screen_w - SPRITE_W - EDGE_MARGIN) : EDGE_MARGIN;

    if (s_sprite_bitmaps[i]) {
      int sprite_y = sprite_span * i / (NUM_ROWS - 1);
      graphics_draw_bitmap_in_rect(ctx, s_sprite_bitmaps[i],
                                   GRect(sprite_x, sprite_y, SPRITE_W, SPRITE_H));
    }

    // Text occupies the side opposite the sprite. max_w is identical for both
    // orientations (screen_w - 65), so every stat option fits the same as before.
    int text_x = icon_on_right ? EDGE_MARGIN
                               : (sprite_x + SPRITE_W + SPRITE_TEXT_GAP);
    int max_w  = icon_on_right ? (sprite_x - SPRITE_TEXT_GAP - EDGE_MARGIN)
                               : (screen_w - text_x - EDGE_MARGIN);

    graphics_context_set_text_color(ctx, GColorFromRGB(255, 255, 170)); // #FFFFAA

    RowConfig *row = &s_config.rows[i];
    bool is_clock = (row->stat_type == 0 || row->stat_type == 1);

    const FontLevel *ladder = is_clock ? CLOCK_LADDER
                                       : (is_emery ? STAT_LADDER_EMERY : STAT_LADDER);
    int levels = is_clock ? ARRAY_LENGTH(CLOCK_LADDER)
                          : (is_emery ? ARRAY_LENGTH(STAT_LADDER_EMERY)
                                      : ARRAY_LENGTH(STAT_LADDER));

    // Walk down the ladder until the string fits (or we hit the smallest).
    GFont num_font = NULL, letter_font = NULL;
    int num_h = 0, letter_h = 0;
    for (int s = 0; s < levels; s++) {
      GFont nf = fonts_get_system_font(ladder[s].num_key);
      GFont lf = fonts_get_system_font(ladder[s].letter_key);
      if (s == levels - 1 ||
          measure_mixed_width(s_stat_text[i], nf, lf) <= max_w) {
        num_font = nf;    num_h = ladder[s].num_h;
        letter_font = lf; letter_h = ladder[s].letter_h;
        break;
      }
    }

    // Baseline = vertical center of row, anchored to tallest font
    int max_h = num_h > letter_h ? num_h : letter_h;
    int baseline_y = y + (row_h + max_h) / 2 - 2;

    // Right-align the mirrored row so its text hugs the icon, mirroring the
    // left rows where the text sits immediately right of the sprite.
    int draw_x = text_x;
    if (icon_on_right) {
      int tw = measure_mixed_width(s_stat_text[i], num_font, letter_font);
      draw_x = text_x + max_w - tw;
      if (draw_x < text_x) draw_x = text_x;
    }

    draw_mixed_text(ctx, s_stat_text[i], draw_x, baseline_y,
                    max_w, num_font, num_h, letter_font, letter_h);
  }
}

// ─── Weather ───────────────────────────────────────────────────────
static bool weather_is_fresh(void) {
  if (s_weather.fetched_at == 0) return false;
  int32_t age = (int32_t)time(NULL) - s_weather.fetched_at;
  return age >= 0 && age < WEATHER_MAX_AGE_SECS;
}

// Ask the phone for a new reading. The watch drives this rather than relying
// on the JS side to push on its own schedule: PebbleKit JS is suspended and
// restarted at the phone's discretion, so a JS-side interval alone will
// silently stop delivering.
static void request_weather(void) {
  if (!connection_service_peek_pebble_app_connection()) return;

  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;
  dict_write_uint8(out, MESSAGE_KEY_WeatherRequest, 1);
  if (app_message_outbox_send() == APP_MSG_OK) {
    s_last_weather_request = time(NULL);
  }
}

// ─── Stat text formatting ──────────────────────────────────────────
static void update_stat_text(int row_idx) {
  RowConfig *row = &s_config.rows[row_idx];
  time_t now = time(NULL);
  struct tm *t = localtime(&now);

  switch (row->stat_type) {
    case 0: {
      int hour = t->tm_hour % 12;
      if (hour == 0) hour = 12;
      snprintf(s_stat_text[row_idx], 32, "%d:%02d %s",
               hour, t->tm_min, t->tm_hour < 12 ? "AM" : "PM");
      break;
    }
    case 1:
      snprintf(s_stat_text[row_idx], 32, "%02d:%02d", t->tm_hour, t->tm_min);
      break;
    case 2: {
      static const char *months[] = {"JAN","FEB","MAR","APR","MAY","JUN",
                                     "JUL","AUG","SEP","OCT","NOV","DEC"};
      snprintf(s_stat_text[row_idx], 32, "%s %02d", months[t->tm_mon], t->tm_mday);
      break;
    }
    case 3:
      snprintf(s_stat_text[row_idx], 32, "BT %s", s_bt_connected ? "OK" : "OFF");
      break;
    case 4:
      snprintf(s_stat_text[row_idx], 32, "%d", s_step_count);
      break;
    case 5:
      snprintf(s_stat_text[row_idx], 32, "%d%%", s_battery_percent);
      break;
    case 6: {
      // Real distance walked from the Health service, in meters.
      if (s_config.dist_unit == 0) {
        int km_x10 = s_distance_m / 100;
        snprintf(s_stat_text[row_idx], 32, "%d.%d KM", km_x10/10, km_x10%10);
      } else {
        int mi_x10 = s_distance_m * 10 / 1609;
        snprintf(s_stat_text[row_idx], 32, "%d.%d MI", mi_x10/10, mi_x10%10);
      }
      break;
    }
    case 7:
      snprintf(s_stat_text[row_idx], 32, "%d CAL", s_calories);
      break;
    case 8:
      if (!weather_is_fresh()) {
        snprintf(s_stat_text[row_idx], 32, s_config.temp_unit == 0 ? "--C" : "--F");
      } else if (s_config.temp_unit == 0) {
        snprintf(s_stat_text[row_idx], 32, "%dC", (int)s_weather.temp_c);
      } else {
        int f = (int)s_weather.temp_c * 9 / 5 + 32;
        snprintf(s_stat_text[row_idx], 32, "%dF", f);
      }
      break;
    case 9:
      if (s_heart_rate > 0) {
        snprintf(s_stat_text[row_idx], 32, "%d BPM", s_heart_rate);
      } else {
        snprintf(s_stat_text[row_idx], 32, "-- BPM");
      }
      break;
    default:
      s_stat_text[row_idx][0] = '\0';
  }
}

// ─── Sprite loading ────────────────────────────────────────────────
static void load_sprite(int row_idx) {
  if (s_sprite_bitmaps[row_idx]) {
    gbitmap_destroy(s_sprite_bitmaps[row_idx]);
    s_sprite_bitmaps[row_idx] = NULL;
  }
  RowConfig *row = &s_config.rows[row_idx];
  uint8_t class_id = row->class_id;
  uint8_t gender = row->gender;
  if (class_id >= NUM_CLASSES) class_id = 0;
  if (gender > 1) gender = 0;
  s_sprite_bitmaps[row_idx] = gbitmap_create_with_resource(SPRITE_RESOURCES[class_id][gender]);
}

// ─── Updates ───────────────────────────────────────────────────────
#if defined(PBL_HEALTH)
// Today's total for a metric, or 0 if the metric isn't available on this
// device / the user hasn't enabled Health.
static int health_sum_today(HealthMetric metric) {
  time_t start = time_start_of_today();
  time_t end = time(NULL);
  if (!(health_service_metric_accessible(metric, start, end)
          & HealthServiceAccessibilityMaskAvailable)) {
    return 0;
  }
  return (int)health_service_sum_today(metric);
}
#endif

static void update_health_stats(void) {
#if defined(PBL_HEALTH)
  s_step_count  = health_sum_today(HealthMetricStepCount);
  s_distance_m  = health_sum_today(HealthMetricWalkedDistanceMeters);
  s_calories    = health_sum_today(HealthMetricActiveKCalories)
                + health_sum_today(HealthMetricRestingKCalories);

  time_t now = time(NULL);
  if (health_service_metric_accessible(HealthMetricHeartRateBPM, now, now)
        & HealthServiceAccessibilityMaskAvailable) {
    s_heart_rate = (int)health_service_peek_current_value(HealthMetricHeartRateBPM);
  } else {
    s_heart_rate = 0;
  }
#else
  s_step_count = 0;
  s_distance_m = 0;
  s_calories   = 0;
  s_heart_rate = 0;
#endif
}

static void update_all_stats() {
  update_health_stats();
  BatteryChargeState charge = battery_state_service_peek();
  s_battery_percent = charge.charge_percent;
  s_bt_connected = connection_service_peek_pebble_app_connection();
  for (int i = 0; i < NUM_ROWS; i++) {
    update_stat_text(i);
  }
  layer_mark_dirty(s_canvas_layer);
}

static void reload_all_sprites() {
  for (int i = 0; i < NUM_ROWS; i++) {
    load_sprite(i);
  }
}

// ─── Persistent storage ────────────────────────────────────────────
static void save_config() {
  persist_write_data(STORAGE_KEY_CONFIG, &s_config, sizeof(s_config));
}

static void load_config() {
  if (persist_exists(STORAGE_KEY_CONFIG)) {
    persist_read_data(STORAGE_KEY_CONFIG, &s_config, sizeof(s_config));
  }
}

static void save_weather() {
  persist_write_data(STORAGE_KEY_WEATHER, &s_weather, sizeof(s_weather));
}

static void load_weather() {
  if (persist_exists(STORAGE_KEY_WEATHER)) {
    persist_read_data(STORAGE_KEY_WEATHER, &s_weather, sizeof(s_weather));
  }
}

// ─── AppMessage from Clay config page ──────────────────────────────
static uint8_t read_uint8(DictionaryIterator *iter, uint32_t key, uint8_t fallback) {
  Tuple *t = dict_find(iter, key);
  if (!t) return fallback;
  // Clay sends select values as strings; convert via atoi
  if (t->type == TUPLE_CSTRING) {
    return (uint8_t)atoi(t->value->cstring);
  }
  return (uint8_t)t->value->int32;
}

static void inbox_received_handler(DictionaryIterator *iter, void *context) {
  s_config.rows[0].class_id  = read_uint8(iter, MESSAGE_KEY_Row0Class,  s_config.rows[0].class_id);
  s_config.rows[0].gender    = read_uint8(iter, MESSAGE_KEY_Row0Gender, s_config.rows[0].gender);
  s_config.rows[0].stat_type = read_uint8(iter, MESSAGE_KEY_Row0Stat,   s_config.rows[0].stat_type);
  s_config.rows[1].class_id  = read_uint8(iter, MESSAGE_KEY_Row1Class,  s_config.rows[1].class_id);
  s_config.rows[1].gender    = read_uint8(iter, MESSAGE_KEY_Row1Gender, s_config.rows[1].gender);
  s_config.rows[1].stat_type = read_uint8(iter, MESSAGE_KEY_Row1Stat,   s_config.rows[1].stat_type);
  s_config.rows[2].class_id  = read_uint8(iter, MESSAGE_KEY_Row2Class,  s_config.rows[2].class_id);
  s_config.rows[2].gender    = read_uint8(iter, MESSAGE_KEY_Row2Gender, s_config.rows[2].gender);
  s_config.rows[2].stat_type = read_uint8(iter, MESSAGE_KEY_Row2Stat,   s_config.rows[2].stat_type);
  s_config.temp_unit = read_uint8(iter, MESSAGE_KEY_TempUnit, s_config.temp_unit);
  s_config.dist_unit = read_uint8(iter, MESSAGE_KEY_DistUnit, s_config.dist_unit);

  // Weather pushed from the phone (integer Celsius).
  Tuple *wt = dict_find(iter, MESSAGE_KEY_WeatherTemp);
  if (wt) {
    s_weather.temp_c = (wt->type == TUPLE_CSTRING)
        ? atoi(wt->value->cstring) : (int32_t)wt->value->int32;
    s_weather.fetched_at = (int32_t)time(NULL);
    save_weather();
  }

  save_config();
  reload_all_sprites();
  update_all_stats();
}

// ─── Service callbacks ─────────────────────────────────────────────
static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_all_stats();

  // Re-ask the phone once the current reading is due for a refresh. Retried
  // every minute while the reading is stale, so a dropped request or a
  // suspended JS runtime recovers on its own.
  time_t now = time(NULL);
  bool due = (now - s_last_weather_request) >= WEATHER_REFRESH_SECS;
  if (!weather_is_fresh() && (now - s_last_weather_request) >= 60) due = true;
  if (due) request_weather();
}

static void battery_callback(BatteryChargeState state) {
  s_battery_percent = state.charge_percent;
  update_all_stats();
}

static void bt_callback(bool connected) {
  bool was_connected = s_bt_connected;
  s_bt_connected = connected;
  update_all_stats();
  // The phone just came back — grab a reading rather than waiting for the
  // next scheduled refresh.
  if (connected && !was_connected) request_weather();
}

static void health_handler(HealthEventType event, void *context) {
  if (event == HealthEventSignificantUpdate ||
      event == HealthEventMovementUpdate ||
      event == HealthEventHeartRateUpdate) {
    update_all_stats();
  }
}

// ─── Window lifecycle ──────────────────────────────────────────────
static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(window_layer);

  s_canvas_layer = layer_create(bounds);
  layer_set_update_proc(s_canvas_layer, canvas_update_proc);
  layer_add_child(window_layer, s_canvas_layer);

  reload_all_sprites();
  update_all_stats();
}

static void main_window_unload(Window *window) {
  layer_destroy(s_canvas_layer);
  for (int i = 0; i < NUM_ROWS; i++) {
    if (s_sprite_bitmaps[i]) {
      gbitmap_destroy(s_sprite_bitmaps[i]);
      s_sprite_bitmaps[i] = NULL;
    }
  }
}

// ─── Init / deinit ─────────────────────────────────────────────────
static void init() {
  load_config();
  load_weather();

  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
  battery_state_service_subscribe(battery_callback);
  connection_service_subscribe((ConnectionHandlers) {
    .pebble_app_connection_handler = bt_callback
  });
  health_service_events_subscribe(health_handler, NULL);

  battery_callback(battery_state_service_peek());
  bt_callback(connection_service_peek_pebble_app_connection());

  app_message_register_inbox_received(inbox_received_handler);
  app_message_open(256, 256);

  // Ask for weather straight away; the tick handler retries if this one is
  // lost or the phone isn't listening yet.
  request_weather();
}

static void deinit() {
  save_config();
  save_weather();
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
