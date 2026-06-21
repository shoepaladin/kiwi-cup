#include <pebble.h>

#define NUM_CLASSES 15
#define NUM_ROWS 3
#define STORAGE_KEY_CONFIG 1

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

static int s_step_count = 0;
static int s_battery_percent = 0;
static bool s_bt_connected = false;
static int s_weather_temp_c = 0;     // last temperature from phone, in Celsius
static bool s_weather_valid = false; // false until the phone reports weather
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
  {FONT_KEY_LECO_26_BOLD_NUMBERS, 26, FONT_KEY_GOTHIC_24_BOLD, 24},
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
  int sprite_span = screen_h - 96;
  if (sprite_span < 0) sprite_span = 0;

  for (int i = 0; i < NUM_ROWS; i++) {
    int y = i * row_h;
    // Same x for every row so the vertical overlap between figures is even.
    int sprite_x = 4;

    if (s_sprite_bitmaps[i]) {
      int sprite_y = sprite_span * i / (NUM_ROWS - 1);
      graphics_draw_bitmap_in_rect(ctx, s_sprite_bitmaps[i],
                                   GRect(sprite_x, sprite_y, 51, 96));
    }

    int text_x = sprite_x + 51 + 6;
    graphics_context_set_text_color(ctx, GColorFromRGB(240, 232, 144));

    RowConfig *row = &s_config.rows[i];
    bool is_clock = (row->stat_type == 0 || row->stat_type == 1);

    int max_w = screen_w - text_x - 4;
    const FontLevel *ladder = is_clock ? CLOCK_LADDER : STAT_LADDER;
    int levels = 4;

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

    draw_mixed_text(ctx, s_stat_text[i], text_x, baseline_y,
                    max_w, num_font, num_h, letter_font, letter_h);
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
      int km_x10 = s_step_count * 7 / 1000;  // steps * 0.0007 km, fixed-point
      if (s_config.dist_unit == 0) {
        snprintf(s_stat_text[row_idx], 32, "%d.%d KM", km_x10/10, km_x10%10);
      } else {
        int mi_x10 = km_x10 * 621 / 1000;
        snprintf(s_stat_text[row_idx], 32, "%d.%d MI", mi_x10/10, mi_x10%10);
      }
      break;
    }
    case 7:
      snprintf(s_stat_text[row_idx], 32, "%d CAL", s_step_count / 20);
      break;
    case 8:
      if (!s_weather_valid) {
        snprintf(s_stat_text[row_idx], 32, s_config.temp_unit == 0 ? "--C" : "--F");
      } else if (s_config.temp_unit == 0) {
        snprintf(s_stat_text[row_idx], 32, "%dC", s_weather_temp_c);
      } else {
        int f = s_weather_temp_c * 9 / 5 + 32;
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
static void update_all_stats() {
  s_step_count = (int)health_service_sum_today(HealthMetricStepCount);
  BatteryChargeState charge = battery_state_service_peek();
  s_battery_percent = charge.charge_percent;
  s_bt_connected = connection_service_peek_pebble_app_connection();
#if defined(PBL_HEALTH)
  if (health_service_metric_accessible(HealthMetricHeartRateBPM, time(NULL), time(NULL))
        == HealthServiceAccessibilityMaskAvailable) {
    s_heart_rate = (int)health_service_peek_current_value(HealthMetricHeartRateBPM);
  }
#endif
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
    s_weather_temp_c = (wt->type == TUPLE_CSTRING)
        ? atoi(wt->value->cstring) : (int)wt->value->int32;
    s_weather_valid = true;
  }

  save_config();
  reload_all_sprites();
  update_all_stats();
}

// ─── Service callbacks ─────────────────────────────────────────────
static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_all_stats();
}

static void battery_callback(BatteryChargeState state) {
  s_battery_percent = state.charge_percent;
  update_all_stats();
}

static void bt_callback(bool connected) {
  s_bt_connected = connected;
  update_all_stats();
}

static void health_handler(HealthEventType event, void *context) {
  if (event == HealthEventSignificantUpdate || event == HealthEventMovementUpdate) {
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
    }
  }
}

// ─── Init / deinit ─────────────────────────────────────────────────
static void init() {
  load_config();

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
}

static void deinit() {
  save_config();
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
