#include <pebble.h>

static Window *s_main_window;
static TextLayer *s_time_layer;
static TextLayer *s_date_layer;
static TextLayer *s_temp_layer;
static Layer *s_chevron_layer;
static Layer *s_battery_layer;
static BitmapLayer *s_icon_layer;
static GBitmap *s_icon_bitmap;

// Persistent text buffers (TextLayer keeps a pointer, not a copy).
static char s_time_buf[8];
static char s_date_buf[16];
static char s_steps_buf[16];
static char s_temp_buf[16];

// Configuration state with defaults.
static int s_bg_color = 0x1A1C1E;
static int s_time_format = 0;        // 0: System, 1: 12h, 2: 24h
static int s_font_size_choice = 2;   // 0=S, 1=M, 2=L (default), 3=XL
static int s_temp_unit = 0;          // 0: F, 1: C
static int s_steps_per_chevron = 2000;
static int s_temp_celsius = 0x7FFFFFFF;  // sentinel: not yet set
static int s_current_steps = 0;
static bool s_bt_connected = true;
static GColor s_current_text_color;  // initialized in apply_theme()

// ---------------------------------------------------------------------------
// Chevron shape (Option A: classic chevron arrow / military stripe).
//
// 6-point polygon:
//   - flat top edge
//   - flat bottom edge
//   - single point on the right (sticks out to the right)
//   - V-notch on the left (cuts into the shape)
//
/*
        ____________
        \           \____
         \               > <- right point sticks out
        > <- notch       
         /           ___/
        /___________/
*/
// ---------------------------------------------------------------------------
#define CHEVRON_COUNT_MAX 5
#if defined(PBL_PLATFORM_DIORITE)
  #define CHEVRON_FIXED_W   14  // narrower so 5 chevrons + step count fits 144px
#else
  #define CHEVRON_FIXED_W   18  // emery/chalk have more horizontal room
#endif
#define CHEVRON_GAP        3    // pixels between chevrons
#define STEPS_GAP          5    // pixels between last chevron and the number

static GPath *s_chevron_path = NULL;
static GPathInfo s_chevron_path_info;
static GPoint s_chevron_pts[6];

// Build the chevron GPath ONCE at window load. The previous version rebuilt
// the path inside the update_proc, which destroyed and reallocated the
// GPath on every redraw -- on emery (Pebble Time 2) that heap churn can
// eventually return NULL from gpath_create, leading to gpath_draw_filled(NULL)
// hard-faulting the watchapp and forcing OS fallback to the prior face.
static void chevron_build_path(int w, int h) {
  int flat_x  = (w * 5) / 7;   // top/bottom flat-edge length
  int notch_x = (w * 2) / 7;   // notch indent depth
  s_chevron_pts[0] = GPoint(0, 0);                // top-left
  s_chevron_pts[1] = GPoint(flat_x, 0);           // end of top flat edge
  s_chevron_pts[2] = GPoint(w, h / 2);            // right-side point
  s_chevron_pts[3] = GPoint(flat_x, h);           // start of bottom flat edge
  s_chevron_pts[4] = GPoint(0, h);                // bottom-left
  s_chevron_pts[5] = GPoint(notch_x, h / 2);      // notch tip (V indent)
  s_chevron_path_info.num_points = 6;
  s_chevron_path_info.points = s_chevron_pts;
  if (s_chevron_path == NULL) {
    s_chevron_path = gpath_create(&s_chevron_path_info);
  }
}

// Option A geometry: 6-point classic chevron arrow.
//
//   (0,0)─────────(flat_x, 0)
//      \                   \___
//       \                       (W, H/2)  right point
//        (notch_x, H/2)     ___/
//      /                   /
//   (0,H)─────────(flat_x, H)
//
// flat_x is where the flat top/bottom edges end before the right diagonal
// begins; notch_x is how far the V-notch indents into the shape from the
// left edge. Both are ~29% of the chevron width.


// Draw N chevrons left-to-right, then draw the step count text immediately
// after the last chevron (with a small gap). N is capped at 5; only earned
// chevrons are drawn -- no placeholder slots for unearned ones.
//
// At 0 steps: no chevrons, text flush at the left edge.
static void chevron_layer_update_proc(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);

  int filled = s_current_steps / s_steps_per_chevron;
  if (filled > CHEVRON_COUNT_MAX) filled = CHEVRON_COUNT_MAX;
  if (filled < 0) filled = 0;

  GColor fg = s_current_text_color;

  // Draw the filled chevrons starting from the left edge. The GPath is built
  // once in main_window_load; never reallocate per-redraw.
  int cursor_x = 0;
  graphics_context_set_fill_color(ctx, fg);
  if (s_chevron_path != NULL) {
    for (int i = 0; i < filled; i++) {
      gpath_move_to(s_chevron_path, GPoint(cursor_x, 0));
      gpath_draw_filled(ctx, s_chevron_path);
      cursor_x += CHEVRON_FIXED_W + CHEVRON_GAP;
    }
  } else {
    // Fallback in case the path failed to allocate: still advance the cursor
    // so the step count text stays placed where the chevrons would have been.
    cursor_x = filled * (CHEVRON_FIXED_W + CHEVRON_GAP);
  }

  // Draw step count text right after the last chevron.
  int text_x = (filled == 0) ? 0 : (cursor_x - CHEVRON_GAP + STEPS_GAP);
  int text_w = b.size.w - text_x;
  if (text_w < 20) text_w = 20;

  graphics_context_set_text_color(ctx, fg);
  GFont font = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  graphics_draw_text(
    ctx,
    s_steps_buf,
    font,
    GRect(text_x, -2, text_w, b.size.h + 2),  // slight y-fudge for vertical centering
    GTextOverflowModeTrailingEllipsis,
    GTextAlignmentLeft,
    NULL
  );
}

// ---------------------------------------------------------------------------
// Theme handling. apply_theme() picks bg/text colors based on whether BT is
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
    bg = user_text;
    text = user_bg;
  }

  window_set_background_color(s_main_window, bg);
  text_layer_set_text_color(s_time_layer, text);
  text_layer_set_text_color(s_date_layer, text);
  text_layer_set_text_color(s_temp_layer, text);
  s_current_text_color = text;  // chevron layer reads from this
  if (s_chevron_layer) layer_mark_dirty(s_chevron_layer);
  layer_mark_dirty(window_get_root_layer(s_main_window));
}

// ---------------------------------------------------------------------------
// Render helpers.
// ---------------------------------------------------------------------------
static void render_date(struct tm *tick_time) {
  // "May 17" -- abbreviated month + day-of-month, no leading zero.
  strftime(s_date_buf, sizeof(s_date_buf), "%b %e", tick_time);
  text_layer_set_text(s_date_layer, s_date_buf);
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
  render_date(tick_time);
}

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

static void update_steps() {
  HealthServiceAccessibilityMask mask =
      health_service_metric_accessible(HealthMetricStepCount, time(NULL), time(NULL));
  if (mask & HealthServiceAccessibilityMaskAvailable) {
    s_current_steps = (int)health_service_sum_today(HealthMetricStepCount);
  } else {
    s_current_steps = 0;
  }
  snprintf(s_steps_buf, sizeof(s_steps_buf), "%d", s_current_steps);
  if (s_chevron_layer) layer_mark_dirty(s_chevron_layer);
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
// AppMessage inbox. JS sends raw Celsius integer in "Temperature".
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

  Tuple *spc_t = dict_find(iterator, MESSAGE_KEY_StepsPerChevron);
  if (spc_t) {
    int v;
    // Clay sends the input field as a CString; tolerate either.
    if (spc_t->type == TUPLE_CSTRING) {
      v = atoi(spc_t->value->cstring);
    } else {
      v = (int)spc_t->value->int32;
    }
    if (v < 100) v = 100;        // guardrails
    if (v > 100000) v = 100000;
    s_steps_per_chevron = v;
    persist_write_int(MESSAGE_KEY_StepsPerChevron, s_steps_per_chevron);
  }

  apply_font_configuration();
  apply_theme();
  update_time();
  render_temperature();
  if (s_chevron_layer) layer_mark_dirty(s_chevron_layer);
}

// ---------------------------------------------------------------------------
// Bluetooth connection handler -- triggers visual invert on disconnect.
// ---------------------------------------------------------------------------
static void bt_handler(bool connected) {
  s_bt_connected = connected;
  apply_theme();
  if (!connected) vibes_short_pulse();
}

// ---------------------------------------------------------------------------
// Window load: build the layout per platform.
//
// Layout (top half, above the image):
//   Row 1: [ TIME big ]                              [ May 17 small ]
//   Row 2: (blank gap)
//   Row 3: [ XX°C temperature ]
//   Row 4: [ chevrons -------- step_count ]
// Then: Nausicaa image fills the bottom half.
// ---------------------------------------------------------------------------
static void main_window_load(Window *window) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Nausicaa window_load starting");
  Layer *window_layer = window_get_root_layer(window);

  GRect time_frame, date_frame, temp_frame, chev_frame;
  GRect image_frame, battery_frame;

  #if defined(PBL_PLATFORM_EMERY)        // Pebble Time 2 (200x228)
    time_frame    = GRect(4, 2, 130, 50);
    date_frame    = GRect(130, 22, 66, 22);
    temp_frame    = GRect(4, 60, 192, 28);
    chev_frame    = GRect(4, 92, 192, 22);
    image_frame   = GRect(0, 124, 200, 104);
    battery_frame = GRect(150, 212, 40, 5);
  #elif defined(PBL_PLATFORM_CHALK)      // Pebble Time Round (180x180)
    time_frame    = GRect(14, 8, 100, 46);
    date_frame    = GRect(110, 26, 58, 22);
    temp_frame    = GRect(14, 58, 152, 24);
    chev_frame    = GRect(14, 82, 152, 18);
    image_frame   = GRect(15, 102, 150, 78);
    battery_frame = GRect(115, 158, 30, 4);
  #else                                  // Pebble 2 / Duo B&W (144x168)
    time_frame    = GRect(2, 0, 92, 46);
    date_frame    = GRect(92, 16, 50, 20);
    temp_frame    = GRect(2, 48, 140, 22);
    chev_frame    = GRect(2, 72, 140, 18);
    image_frame   = GRect(0, 93, 144, 75);
    battery_frame = GRect(104, 155, 32, 4);
  #endif

  // Time.
  s_time_layer = text_layer_create(time_frame);
  text_layer_set_background_color(s_time_layer, GColorClear);
  text_layer_set_overflow_mode(s_time_layer, GTextOverflowModeWordWrap);
  layer_add_child(window_layer, text_layer_get_layer(s_time_layer));

  // Date.
  s_date_layer = text_layer_create(date_frame);
  text_layer_set_background_color(s_date_layer, GColorClear);
  text_layer_set_font(s_date_layer, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD));
  text_layer_set_text_alignment(s_date_layer, GTextAlignmentRight);
  layer_add_child(window_layer, text_layer_get_layer(s_date_layer));

  // Temperature.
  s_temp_layer = text_layer_create(temp_frame);
  text_layer_set_background_color(s_temp_layer, GColorClear);
  text_layer_set_font(s_temp_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
  text_layer_set_text_alignment(s_temp_layer, GTextAlignmentLeft);
  text_layer_set_text(s_temp_layer, "---\u00B0");
  layer_add_child(window_layer, text_layer_get_layer(s_temp_layer));

  // Chevron + step number canvas: chevrons drawn at the left, step count
  // text follows immediately after the last chevron, all in one Layer.
  s_chevron_layer = layer_create(chev_frame);
  layer_set_update_proc(s_chevron_layer, chevron_layer_update_proc);
  layer_add_child(window_layer, s_chevron_layer);

  // Build the chevron GPath exactly once, here. The update_proc will reuse it
  // for every redraw rather than allocating each time.
  chevron_build_path(CHEVRON_FIXED_W, chev_frame.size.h);

  // Image.
  s_icon_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_NAUSICAA);
  s_icon_layer = bitmap_layer_create(image_frame);
  bitmap_layer_set_bitmap(s_icon_layer, s_icon_bitmap);
  bitmap_layer_set_compositing_mode(s_icon_layer, GCompOpSet);
  layer_add_child(window_layer, bitmap_layer_get_layer(s_icon_layer));

  // Battery bar.
  s_battery_layer = layer_create(battery_frame);
  layer_set_update_proc(s_battery_layer, battery_update_proc);
  layer_add_child(window_layer, s_battery_layer);

  // Restore persisted settings.
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
  if (persist_exists(MESSAGE_KEY_StepsPerChevron))
    s_steps_per_chevron = persist_read_int(MESSAGE_KEY_StepsPerChevron);
  if (s_steps_per_chevron < 100) s_steps_per_chevron = 2000;  // sanity

  s_bt_connected = connection_service_peek_pebble_app_connection();

  apply_font_configuration();
  apply_theme();
  update_time();
  update_steps();
  render_temperature();
}

// Defensively guard every pointer: window_destroy() in deinit() will fire
// this handler again if anything else has already popped the window stack
// (e.g. quick-launch pre-emption on Pebble Time 2). Without these guards,
// the second call dereferences freed pointers and Pebble OS terminates the
// app -- and if it happens often enough, uninstalls the watchface.
static void main_window_unload(Window *window) {
  if (s_time_layer)    { text_layer_destroy(s_time_layer);   s_time_layer = NULL; }
  if (s_date_layer)    { text_layer_destroy(s_date_layer);   s_date_layer = NULL; }
  if (s_temp_layer)    { text_layer_destroy(s_temp_layer);   s_temp_layer = NULL; }
  if (s_chevron_layer) { layer_destroy(s_chevron_layer);     s_chevron_layer = NULL; }
  if (s_battery_layer) { layer_destroy(s_battery_layer);     s_battery_layer = NULL; }
  if (s_icon_layer)    { bitmap_layer_destroy(s_icon_layer); s_icon_layer = NULL; }
  if (s_icon_bitmap)   { gbitmap_destroy(s_icon_bitmap);     s_icon_bitmap = NULL; }
  if (s_chevron_path)  { gpath_destroy(s_chevron_path);      s_chevron_path = NULL; }
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
  update_steps();
}

static void init() {
  APP_LOG(APP_LOG_LEVEL_INFO, "Nausicaa init() starting");
  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
  app_message_register_inbox_received(inbox_received_callback);
  app_message_open(256, 256);

  connection_service_subscribe((ConnectionHandlers) {
    .pebble_app_connection_handler = bt_handler,
  });
  APP_LOG(APP_LOG_LEVEL_INFO, "Nausicaa init() complete");
}

static void deinit() {
  APP_LOG(APP_LOG_LEVEL_INFO, "Nausicaa deinit() starting");
  connection_service_unsubscribe();
  tick_timer_service_unsubscribe();
  app_message_deregister_callbacks();
  if (s_main_window) {
    window_destroy(s_main_window);
    s_main_window = NULL;
  }
  APP_LOG(APP_LOG_LEVEL_INFO, "Nausicaa deinit() complete");
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
