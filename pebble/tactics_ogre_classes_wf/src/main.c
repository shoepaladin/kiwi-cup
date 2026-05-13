#include <pebble.h>

static Window *s_main_window;
static Layer *s_canvas_layer;

static GBitmap *s_sprite_bitmaps[3] = {NULL, NULL, NULL};

typedef struct {
  uint8_t class_id;   // 0-8 for 9 classes
  uint8_t gender;     // 0=female, 1=male
  uint8_t stat_type;  // 0-9 for stat types
} RowConfig;

typedef struct {
  RowConfig rows[3];
  uint8_t temp_unit;  // 0=C, 1=F
  uint8_t dist_unit;  // 0=km, 1=mi
} WatchfaceConfig;

static WatchfaceConfig s_config = {
  .rows = {
    {0, 0, 0},  // rune_fencer female, 12h clock
    {1, 1, 8},  // barbarian male, weather/temp
    {4, 0, 5}   // archer female, battery
  },
  .temp_unit = 0,
  .dist_unit = 0
};

static int s_step_count = 0;
static int s_battery_percent = 0;
static bool s_bt_connected = false;
static char s_stat_text[3][32];

static const uint32_t SPRITE_RESOURCES[14][2] = {
  {RESOURCE_ID_SPRITE_RUNE_FENCER_FEMALE, RESOURCE_ID_SPRITE_RUNE_FENCER_MALE},
  {RESOURCE_ID_SPRITE_BARBARIAN_FEMALE, RESOURCE_ID_SPRITE_BARBARIAN_MALE},
  {RESOURCE_ID_SPRITE_WARRIOR_FEMALE, RESOURCE_ID_SPRITE_WARRIOR_MALE},
  {RESOURCE_ID_SPRITE_KNIGHT_FEMALE, RESOURCE_ID_SPRITE_KNIGHT_MALE},
  {RESOURCE_ID_SPRITE_ARCHER_FEMALE, RESOURCE_ID_SPRITE_ARCHER_MALE},
  {RESOURCE_ID_SPRITE_WIZARD_FEMALE, RESOURCE_ID_SPRITE_WIZARD_MALE},
  {RESOURCE_ID_SPRITE_CLERIC_FEMALE, RESOURCE_ID_SPRITE_CLERIC_MALE},
  {RESOURCE_ID_SPRITE_SWORDMASTER_FEMALE, RESOURCE_ID_SPRITE_SWORDMASTER_MALE},
  {RESOURCE_ID_SPRITE_TERROR_KNIGHT_FEMALE, RESOURCE_ID_SPRITE_TERROR_KNIGHT_MALE},
//   {RESOURCE_ID_SPRITE_WARLOCK_WITCH_FEMALE, RESOURCE_ID_SPRITE_WARLOCK_WITCH_MALE},
//   {RESOURCE_ID_SPRITE_NECROMANCER_FEMALE,   RESOURCE_ID_SPRITE_NECROMANCER_MALE},
//   // Unique chars: same resource for both genders
//   {RESOURCE_ID_SPRITE_DENEB,    RESOURCE_ID_SPRITE_DENEB},
//   {RESOURCE_ID_SPRITE_RAVNESS,  RESOURCE_ID_SPRITE_RAVNESS},
//   {RESOURCE_ID_SPRITE_DIEGO,    RESOURCE_ID_SPRITE_DIEGO},
//   {RESOURCE_ID_SPRITE_ARYCELLE, RESOURCE_ID_SPRITE_ARYCELLE},
//   {RESOURCE_ID_SPRITE_CANOPUS,  RESOURCE_ID_SPRITE_CANOPUS},    
};

static void canvas_update_proc(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  int screen_w = bounds.size.w;
  int screen_h = bounds.size.h;
  
  graphics_context_set_fill_color(ctx, GColorFromRGB(8, 14, 6));
  graphics_fill_rect(ctx, bounds, 0, GCornerNone);
  
  int row_h = screen_h / 3;  // 76px per row
  
  // Enable transparency for sprites - critical for PNG alpha to work
  graphics_context_set_compositing_mode(ctx, GCompOpSet);
  
  for (int i = 0; i < 3; i++) {
    int y = i * row_h;
    
    // Stagger sprites horizontally: top/bottom at x=4, middle at x=20
    int sprite_x = (i == 1) ? 20 : 4;
    
    // Draw sprite - full 51×96px height
    if (s_sprite_bitmaps[i]) {
      int sprite_y;
      if (i == 0) {
        // Top row: start sprite higher so it extends into its space
        sprite_y = y - 10;
      } else if (i == 1) {
        // Middle row: center vertically
        sprite_y = y + (row_h - 96) / 2;
      } else {
        // Bottom row: start sprite so it extends below
        sprite_y = y + (row_h - 96) / 2;
      }
      GRect sprite_rect = GRect(sprite_x, sprite_y, 51, 96);
      graphics_draw_bitmap_in_rect(ctx, s_sprite_bitmaps[i], sprite_rect);
    }
    
    // Draw stat text on right - adjust x based on sprite position
    int text_x = sprite_x + 51 + 8;  // 8px padding after sprite
    graphics_context_set_text_color(ctx, GColorFromRGB(240, 232, 144));
    
    // All stats use large bold font for consistency and visibility
    // All stat fontsize to vary over platform
    GFont font;
    int font_h;  // Approximate cap height for vertical centering
    RowConfig *row = &s_config.rows[i];

    
    if (row->stat_type == 0 || row->stat_type == 1) {
      #if defined(PBL_PLATFORM_EMERY)
        font = fonts_get_system_font(FONT_KEY_BITHAM_30_BLACK);
        font_h = 34;
      #elif defined(PBL_PLATFORM_CHALK)
        font = fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD);
        font_h = 28;
      #else  // Basalt
        font = fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD);
        font_h = 24;
      #endif
    } else {
      #if defined(PBL_PLATFORM_EMERY)
        font = fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD);
        font_h = 28;
      #elif defined(PBL_PLATFORM_CHALK)
        font = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
        font_h = 24;
      #else  // Basalt
        font = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
        font_h = 18;
      #endif
    }
    
    // Vertically center text in row: row top + (row_h - font_h) / 2
    // Pebble text baseline sits a few px above bottom of rect, so subtract small offset
    int text_y = y + (row_h - font_h) / 2 - 4;
    GRect text_rect = GRect(text_x, text_y, screen_w - text_x - 8, font_h + 8);
    graphics_draw_text(ctx, s_stat_text[i], font, text_rect,
                       GTextOverflowModeTrailingEllipsis, 
                       GTextAlignmentLeft, NULL);
    
//     // Draw separator line between rows
//     if (i < 2) {
//       graphics_context_set_stroke_color(ctx, GColorFromRGB(24, 32, 16));
//       graphics_draw_line(ctx, GPoint(0, y + row_h), GPoint(screen_w, y + row_h));
//     }
  }
}

static void update_stat_text(int row_idx) {
  RowConfig *row = &s_config.rows[row_idx];
  time_t temp = time(NULL);
  struct tm *t = localtime(&temp);
  
  switch (row->stat_type) {
    case 0: {  // 12h clock
      int hour = t->tm_hour % 12;
      if (hour == 0) hour = 12;
      snprintf(s_stat_text[row_idx], 32, "%d:%02d %s", 
               hour, t->tm_min, t->tm_hour < 12 ? "AM" : "PM");
      break;
    }
    case 1:  // 24h clock
      snprintf(s_stat_text[row_idx], 32, "%02d:%02d", t->tm_hour, t->tm_min);
      break;
    case 2: {  // Date
      static const char *months[] = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
      snprintf(s_stat_text[row_idx], 32, "%s %02d", months[t->tm_mon], t->tm_mday);
      break;
    }
    case 3:  // BT
      snprintf(s_stat_text[row_idx], 32, "BT %s", s_bt_connected ? "OK" : "OFF");
      break;
    case 4:  // Steps
      snprintf(s_stat_text[row_idx], 32, "%d", s_step_count);
      break;
    case 5:  // Battery
      snprintf(s_stat_text[row_idx], 32, "%d%%", s_battery_percent);
      break;
    case 6: {  // Distance
      float km = s_step_count * 0.0007f;
      if (s_config.dist_unit == 0) {
        snprintf(s_stat_text[row_idx], 32, "%.1f KM", km);
      } else {
        snprintf(s_stat_text[row_idx], 32, "%.1f MI", km * 0.621f);
      }
      break;
    }
    case 7:  // Calories
      snprintf(s_stat_text[row_idx], 32, "%d CAL", s_step_count / 20);
      break;
    case 8:  // Weather
      if (s_config.temp_unit == 0) {
        snprintf(s_stat_text[row_idx], 32, "18°C");
      } else {
        snprintf(s_stat_text[row_idx], 32, "64°F");
      }
      break;
    case 9:  // Heart rate
      snprintf(s_stat_text[row_idx], 32, "71 BPM");
      break;
  }
}

static void load_sprite(int row_idx) {
  if (s_sprite_bitmaps[row_idx]) {
    gbitmap_destroy(s_sprite_bitmaps[row_idx]);
  }
  
  RowConfig *row = &s_config.rows[row_idx];
  uint32_t resource_id = SPRITE_RESOURCES[row->class_id][row->gender];
  s_sprite_bitmaps[row_idx] = gbitmap_create_with_resource(resource_id);
}

static void update_all_stats() {
  s_step_count = (int)health_service_sum_today(HealthMetricStepCount);
  BatteryChargeState charge = battery_state_service_peek();
  s_battery_percent = charge.charge_percent;
  s_bt_connected = connection_service_peek_pebble_app_connection();
  
  for (int i = 0; i < 3; i++) {
    update_stat_text(i);
  }
  
  layer_mark_dirty(s_canvas_layer);
}

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

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(window_layer);
  
  s_canvas_layer = layer_create(bounds);
  layer_set_update_proc(s_canvas_layer, canvas_update_proc);
  layer_add_child(window_layer, s_canvas_layer);
  
  for (int i = 0; i < 3; i++) {
    load_sprite(i);
    update_stat_text(i);
  }
  
  update_all_stats();
}

static void main_window_unload(Window *window) {
  layer_destroy(s_canvas_layer);
  
  for (int i = 0; i < 3; i++) {
    if (s_sprite_bitmaps[i]) {
      gbitmap_destroy(s_sprite_bitmaps[i]);
    }
  }
}

static void init() {
  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload,
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
}

static void deinit() {
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
