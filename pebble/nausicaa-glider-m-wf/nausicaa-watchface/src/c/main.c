#include <pebble.h>

static Window *s_main_window;
static TextLayer *s_time_layer;
static TextLayer *s_temp_layer;
static TextLayer *s_steps_layer;
static Layer *s_battery_layer;
static BitmapLayer *s_icon_layer;
static GBitmap *s_icon_bitmap;

// Persistent text buffers (TextLayer keeps a pointer, not a copy).
static char s_time_buf[8];
static char s_steps_buf[16];
static char s_temp_buf[12];

// Configuration state (with defaults).
static int s_bg_color = 0x1A1C1E;
static int s_time_format = 0;       // 0: System, 1: 12h, 2: 24h
static int s_font_size_choice = 2;  // Default bumped: 0=S, 1=M, 2=L (default), 3=XL
static int s_temp_unit = 0;         // 0: F, 1: C

// Cached raw temperature from JS (Celsius, integer). INT32_MIN = not yet set.
static int s_temp_celsius = 0x7FFFFFFF;

// BT state.
static bool s_bt_connected = true;

// ---------------------------------------------------------------------------
// Theme handling. apply_theme picks bg/text colors based on whether BT is
// currently connected. On disconnect we swap bg <-> text so the watchface
// "inverts" visually as a glanceable alert.
// ---------------------------------------------------------------------------
static void apply_theme() {
  GColor user_bg = GColorFromHEX(s_bg_color);
  GColor user_text = gcolor_legible_over(user_bg);

  GColor bg, text;
  if (s_bt_connected) {
    bg = user_bg;
    text = user_text;
  } else {
    // Swap: previous text color becomes background, vice versa.
    bg = user_text;
    text = user_bg;
  }

  window_set_background_color(s_main_window, bg);
  text_layer_set_text_color(s_time_layer, text);
  text_layer_set_text_color(s_temp_layer, text);
  text_layer_set_text_color(s_steps_layer, text);
  layer_mark_dirty(window_get_root_layer(s_main_window));
}

// ---------------------------------------------------------------------------
// Rendering helpers.
// ---------------------------------------------------------------------------
static void render_temperature() {
  if (s_temp_celsius == 0x7FFFFFFF) {
    text_layer_set_text(s_temp_layer, "---\u00B0");
    return;
  }
  int display;
  const char *unit;
  if (s_temp_unit == 0) {
    display = (s_temp_celsius * 9 / 5) + 32;
    unit = "F";
  } else {
    display = s_temp_celsius;
    unit = "C";
  }
  snprintf(s_temp_buf, sizeof(s_temp_buf), "%d\u00B0%s", display, unit);
  text_layer_set_text(s_temp_layer, s_temp_buf);
}

static void update_time() {
  time_t temp = time(NULL);
  struct tm *tick_time = localtime(&temp);

  if (s_time_format == 1) {
    strftime(s_time_buf, sizeof(s_time_buf), "%I:%M", tick_time);
  } else if (s_time_format == 2) {
    strftime(s_time_buf, sizeof(s_time_buf), "%H:%M", tick_time);
  } else {
    if (clock_is_24h_style()) {
      strftime(s_time_buf, sizeof(s_time_buf), "%H:%M", tick_time);
    } else {
      strftime(s_time_buf, sizeof(s_time_buf), "%I:%M", tick_time);
    }
  }
  text_layer_set_text(s_time_layer, s_time_buf);
}

static void update_steps() {
  HealthServiceAccessibilityMask mask =
      health_service_metric_accessible(HealthMetricStepCount, time(NULL), time(NULL));
  if (mask & HealthServiceAccessibilityMaskAvailable) {
    int steps = (int)health_service_sum_today(HealthMetricStepCount);
    snprintf(s_steps_buf, sizeof(s_steps_buf), "%d stp", steps);
    text_layer_set_text(s_steps_layer, s_steps_buf);
  } else {
    text_layer_set_text(s_steps_layer, "0 stp");
  }
}

static void battery_update_proc(Layer *layer, GContext *ctx) {
  BatteryChargeState state = battery_state_service_peek();
  GRect bounds = layer_get_bounds(layer);
  graphics_context_set_fill_color(ctx, GColorDarkGray);
  graphics_fill_rect(ctx, bounds, 0, GCornerNone);

  int width = (state.charge_percent * bounds.size.w) / 100;
  GRect fill_rect = GRect(0, 0, width, bounds.size.h);
  graphics_context_set_fill_color(ctx, GColorWhite);
  graphics_fill_rect(ctx, fill_rect, 0, GCornerNone);
}

static void apply_font_configuration() {
  // Bumped one tier up from the prior mapping for ~1.5x larger default.
  // Picker still has 4 options; defaults to "Large".
  GFont time_font;
  switch (s_font_size_choice) {
    case 0:  time_font = fonts_get_system_font(FONT_KEY_LECO_28_LIGHT_NUMBERS); break;
    case 1:  time_font = fonts_get_system_font(FONT_KEY_LECO_36_BOLD_NUMBERS); break;
    case 3:  time_font = fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS); break;
    case 2:
    default: time_font = fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS); break;
  }
  text_layer_set_font(s_time_layer, time_font);
}

// ---------------------------------------------------------------------------
// AppMessage inbox handler. JS sends raw Celsius integer in "Temperature".
// ---------------------------------------------------------------------------
static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  Tuple *bg_t = dict_find(iterator, MESSAGE_KEY_BackgroundColor);
  if (bg_t) {
    s_bg_color = bg_t->value->int32;
    persist_write_int(MESSAGE_KEY_BackgroundColor, s_bg_color);
  }

  Tuple *fmt_t = dict_find(iterator, MESSAGE_KEY_TimeFormat);
  if (fmt_t) {
    s_time_format = fmt_t->value->int32;
    persist_write_int(MESSAGE_KEY_TimeFormat, s_time_format);
  }

  Tuple *sz_t = dict_find(iterator, MESSAGE_KEY_TimeFontSize);
  if (sz_t) {
    s_font_size_choice = sz_t->value->int32;
    persist_write_int(MESSAGE_KEY_TimeFontSize, s_font_size_choice);
  }

  Tuple *unit_t = dict_find(iterator, MESSAGE_KEY_TemperatureUnit);
  if (unit_t) {
    s_temp_unit = unit_t->value->int32;
    persist_write_int(MESSAGE_KEY_TemperatureUnit, s_temp_unit);
  }

  Tuple *temp_t = dict_find(iterator, MESSAGE_KEY_Temperature);
  if (temp_t) {
    s_temp_celsius = (int)temp_t->value->int32;
    persist_write_int(MESSAGE_KEY_Temperature, s_temp_celsius);
  }

  apply_font_configuration();
  apply_theme();
  update_time();
  render_temperature();  // re-render even if only the unit toggled
}

// ---------------------------------------------------------------------------
// Bluetooth connection handler -- triggers visual invert on disconnect.
// ---------------------------------------------------------------------------
static void bt_handler(bool connected) {
  s_bt_connected = connected;
  apply_theme();
  if (!connected) {
    // Subtle haptic alert on disconnect.
    vibes_short_pulse();
  }
}

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);

  // Per-platform layout frames. Temp/steps frames widened/taller to fit
  // the 1.5x-larger text. Time frame also grew to host larger fonts.
  GRect time_frame, stats_frame, steps_frame, image_frame, battery_frame;

  #if defined(PBL_PLATFORM_EMERY)        // Pebble Time 2 (200x228)
    time_frame    = GRect(6, 6, 125, 100);
    stats_frame   = GRect(125, 8, 72, 32);
    steps_frame   = GRect(125, 42, 72, 26);
    image_frame   = GRect(0, 124, 200, 104);
    battery_frame = GRect(150, 212, 40, 5);
  #elif defined(PBL_PLATFORM_CHALK)      // Pebble Time Round (180x180)
    time_frame    = GRect(18, 22, 96, 90);
    stats_frame   = GRect(110, 26, 56, 30);
    steps_frame   = GRect(110, 56, 56, 24);
    image_frame   = GRect(15, 102, 150, 78);
    battery_frame = GRect(115, 158, 30, 4);
  #else                                  // Pebble 2 / Duo B&W (144x168)
    time_frame    = GRect(4, 4, 88, 88);
    stats_frame   = GRect(88, 6, 54, 28);
    steps_frame   = GRect(88, 36, 54, 22);
    image_frame   = GRect(0, 93, 144, 75);
    battery_frame = GRect(104, 155, 32, 4);
  #endif

  // Time layer.
  s_time_layer = text_layer_create(time_frame);
  text_layer_set_background_color(s_time_layer, GColorClear);
  text_layer_set_overflow_mode(s_time_layer, GTextOverflowModeWordWrap);
  layer_add_child(window_layer, text_layer_get_layer(s_time_layer));

  // Temperature layer (~1.5x larger than prior GOTHIC_18_BOLD).
  s_temp_layer = text_layer_create(stats_frame);
  text_layer_set_background_color(s_temp_layer, GColorClear);
  text_layer_set_font(s_temp_layer, fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD));
  text_layer_set_text_alignment(s_temp_layer, GTextAlignmentRight);
  text_layer_set_text(s_temp_layer, "---\u00B0");
  layer_add_child(window_layer, text_layer_get_layer(s_temp_layer));

  // Steps layer (~1.5x larger than prior GOTHIC_14).
  s_steps_layer = text_layer_create(steps_frame);
  text_layer_set_background_color(s_steps_layer, GColorClear);
  text_layer_set_font(s_steps_layer, fonts_get_system_font(FONT_KEY_GOTHIC_18));
  text_layer_set_text_alignment(s_steps_layer, GTextAlignmentRight);
  layer_add_child(window_layer, text_layer_get_layer(s_steps_layer));

  // Transparent Nausicaa asset; GCompOpSet honors the alpha channel.
  s_icon_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_NAUSICAA);
  s_icon_layer = bitmap_layer_create(image_frame);
  bitmap_layer_set_bitmap(s_icon_layer, s_icon_bitmap);
  bitmap_layer_set_compositing_mode(s_icon_layer, GCompOpSet);
  layer_add_child(window_layer, bitmap_layer_get_layer(s_icon_layer));

  // Battery bar.
  s_battery_layer = layer_create(battery_frame);
  layer_set_update_proc(s_battery_layer, battery_update_proc);
  layer_add_child(window_layer, s_battery_layer);

  // Restore saved settings.
  if (persist_exists(MESSAGE_KEY_BackgroundColor))
    s_bg_color = persist_read_int(MESSAGE_KEY_BackgroundColor);
  if (persist_exists(MESSAGE_KEY_TimeFormat))
    s_time_format = persist_read_int(MESSAGE_KEY_TimeFormat);
  if (persist_exists(MESSAGE_KEY_TimeFontSize))
    s_font_size_choice = persist_read_int(MESSAGE_KEY_TimeFontSize);
  if (persist_exists(MESSAGE_KEY_TemperatureUnit))
    s_temp_unit = persist_read_int(MESSAGE_KEY_TemperatureUnit);
  if (persist_exists(MESSAGE_KEY_Temperature))
    s_temp_celsius = persist_read_int(MESSAGE_KEY_Temperature);

  // Capture current BT state at startup.
  s_bt_connected = connection_service_peek_pebble_app_connection();

  apply_font_configuration();
  apply_theme();
  update_time();
  update_steps();
  render_temperature();
}

static void main_window_unload(Window *window) {
  text_layer_destroy(s_time_layer);
  text_layer_destroy(s_temp_layer);
  text_layer_destroy(s_steps_layer);
  layer_destroy(s_battery_layer);
  bitmap_layer_destroy(s_icon_layer);
  gbitmap_destroy(s_icon_bitmap);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
  update_steps();
}

static void init() {
  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
  app_message_register_inbox_received(inbox_received_callback);
  app_message_open(128, 128);

  // BT disconnect alert -> swap colors.
  connection_service_subscribe((ConnectionHandlers) {
    .pebble_app_connection_handler = bt_handler,
  });
}

static void deinit() {
  connection_service_unsubscribe();
  tick_timer_service_unsubscribe();
  app_message_deregister_callbacks();
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}