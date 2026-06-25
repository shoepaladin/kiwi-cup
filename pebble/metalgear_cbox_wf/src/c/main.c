#include <pebble.h>

// ============================================================================
// METAL GEAR WATCHFACE  v3
// Changes vs v2:
//   - Removed CROC disguise (4 disguises total: BOX, SUPPLY, DRUM, LOVEBOX)
//   - Removed eye holes from all disguises
//   - Removed all leg/boot rendering — boxes shuffle on their own
//   - MGS1 cardboard box: corrugated grain, shipping label, no tape, no eyes
//   - Box rock: rigid rotation around pivot corner (top shears + opposite
//     corner lifts), 4-frame cycle
//   - Drum: 2 reinforcement rings (not 4), proper rust + dent detail
//   - Camera idle direction now EAST (not SW)
//   - Camera tracks Snake via 3 snap directions: DL / S / DR
//   - EVASION state: camera sweeps E -> S -> DL -> DR -> repeat
//   - CAUTION state: camera holds EAST, then Snake respawns
//   - BT disconnected: "FISSION MAILED" dithered overlay; camera body turns dark red
//   - Settings: bt_vibrate toggle for BT connect/disconnect haptic
//   - Settings: scene_mode (hallway / outdoor / auto-by-time)
//   - Settings: disguise selection (button cycle removed - settings only)
//   - Outdoor scene: night sky, radio tower w/ blinking light, chain-link fence,
//     diagonal searchlight beam, snowy ground, floodlight replaces camera
//   - `!` bubble (E_BUBBLE) now shows for 600ms after BACK press from ? before
//     the grenade arc starts (was previously never drawn)
//   - Grenade: 15 frames at 80ms each (was 10 at 120ms), smoke drifts upward
//   - LIFE bar (battery) + date repositioned below floor; BACK button triggers alert
// ============================================================================

// ============================================================================
// COLOR PALETTE
// ============================================================================

#ifdef PBL_COLOR
  // Hallway
  #define COL_WALL_BACK      GColorDarkGray
  #define COL_WALL_PANEL     GColorLightGray
  #define COL_WALL_LINE      GColorBlack
  #define COL_FLOOR          GColorBlack
  #define COL_FLOOR_HI       GColorDarkGray
  #define COL_PIPE           GColorDarkGray
  #define COL_PIPE_HI        GColorLightGray
  #define COL_LAMP           GColorYellow
  #define COL_CAUTION_Y      GColorChromeYellow
  #define COL_CAUTION_K      GColorBlack
  #define COL_VENT           GColorDarkGray
  #define COL_TIME_TEXT      GColorWhite
  #define COL_DATE_TEXT      GColorChromeYellow
  // Cardboard box (MGS1) — plain corrugated
  #define COL_BOX            GColorWindsorTan
  #define COL_BOX_DARK       GColorBulgarianRose
  #define COL_BOX_MID        GColorOrange
  #define COL_BOX_SHAD       GColorDarkCandyAppleRed
  #define COL_LABEL          GColorPastelYellow
  #define COL_LABEL_TEXT     GColorBlack
  // Supply (MGSV)
  #define COL_SUPPLY_MAIN    GColorArmyGreen
  #define COL_SUPPLY_DARK    GColorDarkGreen
  #define COL_SUPPLY_HI      GColorBrass
  #define COL_SUPPLY_STENCIL GColorBlack
  #define COL_SUPPLY_BAND    GColorChromeYellow
  // Drum (MGS4)
  #define COL_DRUM           GColorDarkGray
  #define COL_DRUM_HI        GColorLightGray
  #define COL_DRUM_RING      GColorBlack
  #define COL_RUST_DK        GColorDarkCandyAppleRed
  #define COL_RUST_MD        GColorRed
  #define COL_RUST_LT        GColorOrange
  #define COL_DENT_DK        GColorBlack
  #define COL_DENT_HI        GColorLightGray
  // Love box heart
  #define COL_HEART          GColorRed
  #define COL_HEART_HI       GColorMelon
  // Bubbles
  #define COL_Q_BG           GColorWhite
  #define COL_Q_TEXT         GColorBlue
  #define COL_E_BG           GColorYellow
  #define COL_E_TEXT         GColorRed
  // Smoke
  #define COL_SMOKE_LIGHT    GColorWhite
  #define COL_SMOKE_MED      GColorLightGray
  #define COL_SMOKE_DARK     GColorDarkGray
  // Camera (hallway)
  #define COL_CAM_BODY       GColorDarkGray
  #define COL_CAM_LENS       GColorBlack
  #define COL_CAM_LENS_HI    GColorElectricUltramarine
  #define COL_CAM_ALERT      GColorRed
  #define COL_CAM_MOUNT      GColorBlack
  #define COL_CAM_BT_RED     GColorDarkCandyAppleRed
  // Outdoor scene
  #define COL_SKY_DARK       GColorOxfordBlue
  #define COL_SNOW_GROUND    GColorLightGray
  #define COL_SNOW_HI        GColorWhite
  #define COL_SNOW_SHADOW    GColorDarkGray
  #define COL_FENCE          GColorBlack
  #define COL_FENCE_HI       GColorOxfordBlue
  #define COL_TOWER          GColorBlack
  #define COL_FLOODLIGHT     GColorDarkGray
  #define COL_FLOODLIGHT_LIT GColorPastelYellow
  // Battery
  #define COL_BATT_FRAME     GColorChromeYellow
  #define COL_BATT_BG        GColorBlack
  #define COL_BATT_OK        GColorGreen
  #define COL_BATT_MID       GColorChromeYellow
  #define COL_BATT_LOW       GColorRed
#else
  #define COL_WALL_BACK      GColorBlack
  #define COL_WALL_PANEL     GColorWhite
  #define COL_WALL_LINE      GColorBlack
  #define COL_FLOOR          GColorBlack
  #define COL_FLOOR_HI       GColorWhite
  #define COL_PIPE           GColorBlack
  #define COL_PIPE_HI        GColorWhite
  #define COL_LAMP           GColorWhite
  #define COL_CAUTION_Y      GColorWhite
  #define COL_CAUTION_K      GColorBlack
  #define COL_VENT           GColorBlack
  #define COL_TIME_TEXT      GColorWhite
  #define COL_DATE_TEXT      GColorWhite
  #define COL_BOX            GColorWhite
  #define COL_BOX_DARK       GColorBlack
  #define COL_BOX_MID        GColorWhite
  #define COL_BOX_SHAD       GColorBlack
  #define COL_LABEL          GColorWhite
  #define COL_LABEL_TEXT     GColorBlack
  #define COL_SUPPLY_MAIN    GColorBlack
  #define COL_SUPPLY_DARK    GColorBlack
  #define COL_SUPPLY_HI      GColorWhite
  #define COL_SUPPLY_STENCIL GColorWhite
  #define COL_SUPPLY_BAND    GColorWhite
  #define COL_DRUM           GColorWhite
  #define COL_DRUM_HI        GColorWhite
  #define COL_DRUM_RING      GColorBlack
  #define COL_RUST_DK        GColorBlack
  #define COL_RUST_MD        GColorBlack
  #define COL_RUST_LT        GColorWhite
  #define COL_DENT_DK        GColorBlack
  #define COL_DENT_HI        GColorWhite
  #define COL_HEART          GColorBlack
  #define COL_HEART_HI       GColorWhite
  #define COL_Q_BG           GColorWhite
  #define COL_Q_TEXT         GColorBlack
  #define COL_E_BG           GColorWhite
  #define COL_E_TEXT         GColorBlack
  #define COL_SMOKE_LIGHT    GColorWhite
  #define COL_SMOKE_MED      GColorWhite
  #define COL_SMOKE_DARK     GColorBlack
  #define COL_CAM_BODY       GColorBlack
  #define COL_CAM_LENS       GColorBlack
  #define COL_CAM_LENS_HI    GColorWhite
  #define COL_CAM_ALERT      GColorBlack
  #define COL_CAM_MOUNT      GColorBlack
  #define COL_CAM_BT_RED     GColorBlack
  #define COL_SKY_DARK       GColorBlack
  #define COL_SNOW_GROUND    GColorWhite
  #define COL_SNOW_HI        GColorWhite
  #define COL_SNOW_SHADOW    GColorBlack
  #define COL_FENCE          GColorBlack
  #define COL_FENCE_HI       GColorBlack
  #define COL_TOWER          GColorBlack
  #define COL_FLOODLIGHT     GColorBlack
  #define COL_FLOODLIGHT_LIT GColorWhite
  #define COL_BATT_FRAME     GColorWhite
  #define COL_BATT_BG        GColorBlack
  #define COL_BATT_OK        GColorWhite
  #define COL_BATT_MID       GColorWhite
  #define COL_BATT_LOW       GColorWhite
#endif

// ============================================================================
// LAYOUT — computed at window_load from actual screen bounds
// ============================================================================

static int s_screen_w = 200;
static int s_screen_h = 228;
static int s_floor_y;
static int s_sprite_w;
static int s_sprite_h;
static int s_sprite_y;

static int scale_x(int v) { return (v * s_screen_w) / 144; }
static int scale_y(int v) { return (v * s_screen_h) / 168; }

// ============================================================================
// CONSTANTS
// ============================================================================

#define DISGUISE_BOX     0
#define DISGUISE_SUPPLY  1
#define DISGUISE_DRUM    2
#define DISGUISE_LOVEBOX 3
#define DISGUISE_COUNT   4

#define SCENE_HALLWAY 0
#define SCENE_OUTDOOR 1
#define SCENE_AUTO    2

typedef enum {
  STATE_IDLE,
  STATE_ALERT,      // ? bubble blinking, sprite frozen
  STATE_BANG,       // ! bubble (~600ms before grenade)
  STATE_GRENADE,    // grenade arc + smoke
  STATE_EVASION,    // Snake gone, camera sweeps (orange overlay)
  STATE_CAUTION,    // camera returns to EAST (yellow overlay), then respawn
  STATE_VANISHED    // unused; kept for switch coverage
} WatchState;

typedef enum { CAM_E, CAM_DL, CAM_DR, CAM_S } CamDir;

#define ANIM_TICK_MS           80
#define ANIM_TICK_MS_LOW_POWER 200
#define LOW_BATTERY_THRESHOLD  20
#define ALERT_TIMEOUT_MS       5000
#define BANG_DURATION_MS       600
#define GRENADE_FRAME_MS       80
#define GRENADE_TOTAL_FRAMES   15
#define GRENADE_ARC_FRAMES     6
#define EVASION_DURATION_MS    3000
#define CAUTION_DURATION_MS    2000
#define Q_BLINK_MS             400
#define SWEEP_PHASE_MS         800

enum {
  PKEY_DISGUISE     = 1,
  PKEY_TRAVERSE_MS  = 2,
  PKEY_BT_VIBRATE   = 3,
  PKEY_SCENE_MODE   = 4
};

// Rock animation — 4 frames, top shear amount per frame
static const int ROCK_TILT[4] = { 0, 3, 0, -3 };
#define ROCK_LIFT 2  // pixels the opposite bottom corner lifts

// Sweep cycle order during EVASION state
static const CamDir SWEEP_CYCLE[4] = { CAM_E, CAM_S, CAM_DL, CAM_DR };

// ============================================================================
// GLOBAL STATE
// ============================================================================

static Window    *s_main_window;
static Layer     *s_canvas_layer;
static TextLayer *s_time_layer;
static TextLayer *s_date_layer;

static bool s_window_loaded     = false;
static bool s_fully_initialized = false;
static bool s_in_focus          = true;
static bool s_bt_connected      = true;
static bool s_is_charging       = false;
static int  s_battery_level     = 100;

static WatchState s_state         = STATE_IDLE;
static int        s_disguise      = DISGUISE_BOX;
static int        s_traverse_ms   = 300000;
static bool       s_bt_vibrate    = true;
static int        s_scene_mode    = SCENE_HALLWAY;

static int32_t s_sprite_x_fp = 0;  // x * 100 for fixed-point

static AppTimer *s_anim_timer          = NULL;
static AppTimer *s_alert_timeout_timer = NULL;
static AppTimer *s_q_blink_timer       = NULL;
static AppTimer *s_bang_timer          = NULL;
static AppTimer *s_grenade_timer       = NULL;
static AppTimer *s_evasion_timer       = NULL;
static AppTimer *s_caution_timer       = NULL;
static AppTimer *s_sweep_timer         = NULL;
static bool      s_q_visible           = true;

static int s_rock_frame    = 0;  // 0..3
static int s_grenade_frame = 0;  // 0..GRENADE_TOTAL_FRAMES-1
static int s_sweep_phase   = 0;  // 0..3
static int s_anim_tick     = 0;  // global counter for snow drift, blink, etc.

static char s_time_buf[8];
static char s_date_buf[16];

// ============================================================================
// FORWARD DECLARATIONS
// ============================================================================

static void schedule_anim(void);
static void cancel_all_timers(void);
static void enter_idle(void);
static void enter_alert(void);
static void enter_bang(void);
static void enter_grenade(void);
static void enter_evasion(void);
static void enter_caution(void);
static void canvas_update_proc(Layer *layer, GContext *ctx);
static uint32_t get_anim_interval(void);
static bool should_animate(void);
static bool is_outdoor_scene(void);

// ============================================================================
// HELPERS
// ============================================================================

static int clampi(int v, int lo, int hi) {
  return v < lo ? lo : v > hi ? hi : v;
}

static int absi(int v) { return v < 0 ? -v : v; }

static void fillrect(GContext *ctx, int x, int y, int w, int h, GColor c) {
  if (w <= 0 || h <= 0) return;
  graphics_context_set_fill_color(ctx, c);
  graphics_fill_rect(ctx, GRect(x, y, w, h), 0, GCornerNone);
}

static void strokerect(GContext *ctx, int x, int y, int w, int h, GColor c) {
  graphics_context_set_stroke_color(ctx, c);
  graphics_draw_rect(ctx, GRect(x, y, w, h));
}

static void hline(GContext *ctx, int x, int y, int w, GColor c) {
  fillrect(ctx, x, y, w, 1, c);
}

static void vline(GContext *ctx, int x, int y, int h, GColor c) {
  fillrect(ctx, x, y, 1, h, c);
}

// ============================================================================
// SCENE MODE — auto switches outdoor at night (8pm-6am)
// ============================================================================

static bool is_outdoor_scene(void) {
  if (s_scene_mode == SCENE_OUTDOOR) return true;
  if (s_scene_mode == SCENE_HALLWAY) return false;
  // AUTO: outdoor between 20:00 and 06:00
  time_t now = time(NULL);
  struct tm *tm = localtime(&now);
  int h = tm->tm_hour;
  return (h >= 20 || h < 6);
}

// ============================================================================
// BACKGROUND — Shadow Moses Hallway
// ============================================================================

static void draw_hallway(GContext *ctx) {
  int W  = s_screen_w;
  int FY = s_floor_y;
  int ceiling_h = scale_y(6);
  int wall_top  = scale_y(20);

  fillrect(ctx, 0, 0, W, wall_top, COL_WALL_BACK);
  fillrect(ctx, 0, wall_top, W, FY - wall_top, COL_WALL_PANEL);
  fillrect(ctx, 0, 0, W, ceiling_h, COL_PIPE);
  fillrect(ctx, 0, ceiling_h / 3, W, ceiling_h / 3, COL_PIPE_HI);

  int lamp_w = scale_x(10), lamp_h = scale_y(5), lamp_y = ceiling_h + 2;
  fillrect(ctx, scale_x(8), lamp_y, lamp_w, lamp_h, COL_LAMP);
  fillrect(ctx, scale_x(8) + 2, lamp_y + lamp_h, lamp_w - 4, scale_y(4), COL_PIPE);
  fillrect(ctx, W - scale_x(8) - lamp_w, lamp_y, lamp_w, lamp_h, COL_LAMP);
  fillrect(ctx, W - scale_x(8) - lamp_w + 2, lamp_y + lamp_h, lamp_w - 4, scale_y(4), COL_PIPE);

  int panel_step = scale_x(36);
  graphics_context_set_stroke_color(ctx, COL_WALL_LINE);
  for (int x = 0; x < W; x += panel_step) {
    graphics_draw_line(ctx, GPoint(x, wall_top), GPoint(x, FY));
  }
  int h1 = wall_top, h2 = wall_top + scale_y(40), h3 = wall_top + scale_y(80);
  graphics_draw_line(ctx, GPoint(0, h1), GPoint(W, h1));
  graphics_draw_line(ctx, GPoint(0, h2), GPoint(W, h2));
  graphics_draw_line(ctx, GPoint(0, h3), GPoint(W, h3));
  for (int x = 0; x <= W; x += panel_step) {
    fillrect(ctx, x - 1, h1 - 1, 3, 3, COL_WALL_LINE);
    fillrect(ctx, x - 1, h2 - 1, 3, 3, COL_WALL_LINE);
    fillrect(ctx, x - 1, h3 - 1, 3, 3, COL_WALL_LINE);
  }

  int sign_w = scale_x(48), sign_h = scale_y(14);
  int sign_x = (W - sign_w) / 2, sign_y = wall_top + scale_y(4);
  fillrect(ctx, sign_x, sign_y, sign_w, sign_h, COL_CAUTION_Y);
  strokerect(ctx, sign_x, sign_y, sign_w, sign_h, COL_WALL_LINE);
  for (int i = 0; i < 7; i++) {
    int dw = (sign_w - 14) / 7;
    if (dw < 1) dw = 1;
    int dx = sign_x + 4 + i * (sign_w - 8) / 7;
    fillrect(ctx, dx, sign_y + 3, dw, 2, COL_WALL_LINE);
    fillrect(ctx, dx, sign_y + sign_h - 5, dw, 2, COL_WALL_LINE);
  }

  int vent_w = scale_x(12), vent_h = scale_y(24);
  int vent_x = W - vent_w - scale_x(4), vent_y = h2 + scale_y(6);
  fillrect(ctx, vent_x, vent_y, vent_w, vent_h, COL_VENT);
  for (int vy = vent_y + 3; vy < vent_y + vent_h - 2; vy += 4) {
    fillrect(ctx, vent_x + 2, vy, vent_w - 4, 2, COL_FLOOR);
  }
  strokerect(ctx, vent_x, vent_y, vent_w, vent_h, COL_WALL_LINE);

  int stripe_h = scale_y(8), stripe_seg = scale_x(8), stripe_y = FY - stripe_h;
  for (int x = 0; x < W; x += stripe_seg * 2) {
    fillrect(ctx, x,             stripe_y, stripe_seg, stripe_h, COL_CAUTION_Y);
    fillrect(ctx, x + stripe_seg, stripe_y, stripe_seg, stripe_h, COL_CAUTION_K);
  }

  fillrect(ctx, 0, FY, W, s_screen_h - FY, COL_FLOOR);
  fillrect(ctx, 0, FY, W, 2, COL_FLOOR_HI);
}

// ============================================================================
// BACKGROUND — Outdoor Base at Night
// ============================================================================

static void draw_outdoor(GContext *ctx) {
  int W  = s_screen_w;
  int FY = s_floor_y;

  // Solid dark night sky
  fillrect(ctx, 0, 0, W, FY, COL_SKY_DARK);

  // Radio tower — left side
  int tower_x = scale_x(18);
  int tower_top = scale_y(8);
  int tower_bot = FY - scale_y(20);
  int tower_h = tower_bot - tower_top;
  for (int ty = tower_top; ty < tower_bot; ty++) {
    int taper = ((ty - tower_top) * scale_x(8)) / tower_h;
    fillrect(ctx, tower_x - taper, ty, 1, 1, COL_TOWER);
    fillrect(ctx, tower_x + taper, ty, 1, 1, COL_TOWER);
  }
  for (int ty = tower_top + 8; ty < tower_bot; ty += 8) {
    int taper = ((ty - tower_top) * scale_x(8)) / tower_h;
    fillrect(ctx, tower_x - taper, ty, taper * 2 + 1, 1, COL_TOWER);
  }
  // Blinking aircraft warning light
  if ((s_anim_tick % 20) < 10) {
    fillrect(ctx, tower_x - 1, tower_top - 1, 2, 2, COL_CAM_ALERT);
  }

  // Chain-link fence — right side
  int fence_xs = scale_x(80);
  int fence_xe = W;
  int fence_yt = FY - scale_y(28);
  int fence_yb = FY - scale_y(8);
  for (int fx = fence_xs; fx < fence_xe; fx += 4) {
    for (int fy = fence_yt; fy < fence_yb; fy += 4) {
      fillrect(ctx, fx, fy, 1, 1, COL_FENCE_HI);
      fillrect(ctx, fx + 2, fy + 2, 1, 1, COL_FENCE_HI);
    }
  }
  for (int fx = fence_xs; fx <= fence_xe; fx += scale_x(18)) {
    fillrect(ctx, fx, fence_yt - 2, 2, fence_yb - fence_yt + 4, COL_FENCE);
  }
  hline(ctx, fence_xs, fence_yt, fence_xe - fence_xs, COL_FENCE);

  // Searchlight beam — slope rotates with sweep phase
  // Slopes correspond to phase 0..3 (E, S, DL, DR — but here just for beam tilt)
  int slope_num[4] = { 1, 2, 3, 4 };
  int slope_den    = 4;
  int snum = slope_num[s_sweep_phase % 4];
  int beam_origin_x = scale_x(110);
  int beam_w = scale_x(20);
  for (int by = 0; by < FY - scale_y(8); by += 2) {
    int bx_off = (by * snum) / slope_den;
    int this_w = beam_w + (by / 6);
    // Dithered translucent beam — fill every other pixel
    int sx = beam_origin_x + bx_off;
    if (sx >= 0 && sx < W) {
      for (int bxi = 0; bxi < this_w; bxi += 2) {
        if (sx + bxi >= 0 && sx + bxi < W) {
          fillrect(ctx, sx + bxi, by, 1, 2, COL_FLOODLIGHT_LIT);
        }
      }
    }
  }
  // Bright core
  for (int by = 0; by < FY - scale_y(8); by += 3) {
    int bx_off = (by * snum) / slope_den;
    fillrect(ctx, beam_origin_x + bx_off + beam_w / 2, by, 1, 1, COL_SNOW_HI);
  }

  // Snow flurries — pseudo-random fixed array (using simple hash on index)
  // Drift based on s_anim_tick so snow falls naturally
  for (int i = 0; i < 28; i++) {
    int fx = ((i * 37 + 13) % W);
    int base_y = ((i * 53 + 7) % 180);
    int speed = 1 + (i % 3);
    int sz = (i % 4 == 0) ? 2 : 1;
    int fy = (base_y + s_anim_tick * speed) % (FY - scale_y(5));
    fillrect(ctx, fx, fy, sz, sz, COL_SNOW_HI);
  }

  // Snowy ground
  fillrect(ctx, 0, FY, W, s_screen_h - FY, COL_SNOW_GROUND);
  for (int sx = 0; sx < W; sx += 6) {
    int variance = ((sx * 7) % 11);
    fillrect(ctx, sx, FY + 1 + (variance % 2), 3, 1, COL_SNOW_HI);
    fillrect(ctx, sx + 2, FY + 4, 2, 1, COL_SNOW_SHADOW);
  }
  for (int tx = scale_x(8); tx < W; tx += scale_x(22)) {
    fillrect(ctx, tx, FY + scale_y(8), 3, 1, COL_SNOW_SHADOW);
    fillrect(ctx, tx + 6, FY + scale_y(10), 3, 1, COL_SNOW_SHADOW);
  }
  hline(ctx, 0, FY - 1, W, COL_SNOW_SHADOW);
}

// ============================================================================
// CAMERA / FLOODLIGHT — same direction logic, different chrome
// ============================================================================

static CamDir cam_dir_for_state(int sprite_cx) {
  // 3-position snap based on Snake's center x relative to camera
  int cam_px = s_screen_w - scale_x(22);
  if (sprite_cx >= cam_px) return CAM_DR;
  if (sprite_cx >= cam_px - scale_x(35)) return CAM_S;
  return CAM_DL;
}

static CamDir current_cam_dir(int sprite_cx) {
  switch (s_state) {
    case STATE_IDLE:     return CAM_E;
    case STATE_ALERT:
    case STATE_BANG:
    case STATE_GRENADE:  return cam_dir_for_state(sprite_cx);
    case STATE_EVASION:  return SWEEP_CYCLE[s_sweep_phase % 4];
    case STATE_CAUTION:
    case STATE_VANISHED: return CAM_E;
  }
  return CAM_E;
}

static void draw_camera(GContext *ctx, int sprite_cx) {
  int cx = s_screen_w - scale_x(22);
  int cy = scale_y(7);
  int arm_h  = scale_y(9);
  int body_w = scale_x(12), body_h = scale_y(8);
  int blen   = scale_x(12), bw = scale_y(5);

  GColor body_color = s_bt_connected ? COL_CAM_BODY : COL_CAM_BT_RED;

  fillrect(ctx, cx + body_w / 2 - 1, cy, 3, arm_h, COL_CAM_MOUNT);

  int bx = cx, by = cy + arm_h;
  fillrect(ctx, bx, by, body_w, body_h, body_color);
  strokerect(ctx, bx, by, body_w, body_h, COL_CAM_MOUNT);

  bool alerting = (s_state != STATE_IDLE);
  if (alerting || !s_bt_connected) {
    fillrect(ctx, bx + body_w / 2 - 2, by - 3, 4, 3, COL_CAM_ALERT);
  }

  CamDir dir = current_cam_dir(sprite_cx);
  int px = bx + body_w / 2, py = by + body_h / 2;
  int ext = (blen * 2) / 3;

  switch (dir) {
    case CAM_E:
      fillrect(ctx, px, py - bw / 2, blen, bw, body_color);
      fillrect(ctx, px + blen - 2, py - bw / 2 - 1, 3, bw + 2, COL_CAM_MOUNT);
      fillrect(ctx, px + blen, py - 2, 3, 5, COL_CAM_LENS);
      fillrect(ctx, px + blen + 1, py - 1, 1, 2, COL_CAM_LENS_HI);
      break;
    case CAM_DL:
      fillrect(ctx, px - ext, py, ext, bw, body_color);
      fillrect(ctx, px - ext - 1, py + bw, bw, 3, COL_CAM_MOUNT);
      fillrect(ctx, px - ext - 2, py + bw + 2, 5, 3, COL_CAM_LENS);
      fillrect(ctx, px - ext - 1, py + bw + 3, 2, 1, COL_CAM_LENS_HI);
      break;
    case CAM_DR:
      fillrect(ctx, px, py, ext, bw, body_color);
      fillrect(ctx, px + ext - 1, py + bw, bw, 3, COL_CAM_MOUNT);
      fillrect(ctx, px + ext, py + bw + 2, 5, 3, COL_CAM_LENS);
      fillrect(ctx, px + ext + 1, py + bw + 3, 2, 1, COL_CAM_LENS_HI);
      break;
    case CAM_S:
      fillrect(ctx, px - bw / 2, py, bw, blen, body_color);
      fillrect(ctx, px - bw / 2 - 1, py + blen - 2, bw + 2, 3, COL_CAM_MOUNT);
      fillrect(ctx, px - 2, py + blen, 5, 3, COL_CAM_LENS);
      fillrect(ctx, px - 1, py + blen + 1, 2, 1, COL_CAM_LENS_HI);
      break;
  }
}

static void draw_floodlight(GContext *ctx, int sprite_cx) {
  GColor body_color = s_bt_connected ? COL_FLOODLIGHT : COL_CAM_BT_RED;

  int pole_x = s_screen_w - scale_x(22);
  int pole_bot = scale_y(18);
  fillrect(ctx, pole_x, 0, 2, pole_bot, COL_FLOODLIGHT);
  fillrect(ctx, pole_x - 2, 1, 6, 2, COL_SNOW_HI);

  int head_x = pole_x - scale_x(4);
  int head_y = pole_bot;
  int head_w = scale_x(8);
  int head_h = scale_y(7);
  fillrect(ctx, head_x, head_y, head_w, head_h, body_color);
  strokerect(ctx, head_x, head_y, head_w, head_h, COL_CAM_MOUNT);
  fillrect(ctx, head_x, head_y, head_w, 2, COL_SNOW_HI);
  fillrect(ctx, head_x - 1, head_y, 1, 2, COL_SNOW_HI);
  fillrect(ctx, head_x + head_w, head_y, 1, 2, COL_SNOW_HI);

  bool alerting = (s_state != STATE_IDLE);
  if (alerting || !s_bt_connected) {
    fillrect(ctx, head_x + head_w / 2 - 2, head_y - 4, 4, 3, COL_CAM_ALERT);
  }

  CamDir dir = current_cam_dir(sprite_cx);
  int lens_x = head_x + head_w / 2;
  int lens_y = head_y + head_h / 2;
  int cone_len = scale_x(20);

  switch (dir) {
    case CAM_E:
      fillrect(ctx, head_x + head_w, lens_y - 2, 3, 5, COL_FLOODLIGHT_LIT);
      for (int i = 0; i < cone_len; i += 2) {
        int spread = i / 3;
        fillrect(ctx, head_x + head_w + i, lens_y - 2 - spread, 1, 5 + spread * 2, COL_FLOODLIGHT_LIT);
      }
      break;
    case CAM_DL:
      fillrect(ctx, head_x - 3, head_y + head_h, 5, 3, COL_FLOODLIGHT_LIT);
      for (int i = 0; i < cone_len; i += 2) {
        int spread = i / 3;
        fillrect(ctx, head_x - i - 2, head_y + head_h + i - spread, 4 + spread, 1, COL_FLOODLIGHT_LIT);
      }
      break;
    case CAM_DR:
      fillrect(ctx, head_x + head_w - 2, head_y + head_h, 5, 3, COL_FLOODLIGHT_LIT);
      for (int i = 0; i < cone_len; i += 2) {
        int spread = i / 3;
        fillrect(ctx, head_x + head_w + i - 2, head_y + head_h + i - spread, 4 + spread, 1, COL_FLOODLIGHT_LIT);
      }
      break;
    case CAM_S:
      fillrect(ctx, lens_x - 2, head_y + head_h, 5, 3, COL_FLOODLIGHT_LIT);
      for (int i = 0; i < cone_len; i += 2) {
        int spread = i / 3;
        fillrect(ctx, lens_x - 2 - spread, head_y + head_h + i, 5 + spread * 2, 1, COL_FLOODLIGHT_LIT);
      }
      break;
  }
}

// ============================================================================
// BOX ROCK TRANSFORM — rigid rotation about pivot bottom corner
// ============================================================================

// Returns x,y offset for a given pixel (col_x, row_y) inside the box (w, h).
// tilt > 0: pivot bottom-right, bottom-left rises
// tilt < 0: pivot bottom-left,  bottom-right rises
static void rock_offset(int frame, int col_x, int row_y, int w, int h,
                        int *out_dx, int *out_dy) {
  int tilt = ROCK_TILT[frame % 4];
  if (tilt == 0) { *out_dx = 0; *out_dy = 0; return; }
  *out_dx = (tilt * (h - row_y)) / h;
  int lift_mag = (absi(tilt) * ROCK_LIFT) / 3;
  if (tilt > 0) {
    *out_dy = -((lift_mag * (w - col_x)) / w);
  } else {
    *out_dy = -((lift_mag * col_x) / w);
  }
}

// Draw a horizontal strip with rock transform applied per-row.
// For performance we sample the offset at the strip's CENTER column,
// not per-pixel — this still gives correct rigid-rotation appearance.
static void rock_strip(GContext *ctx, int x, int y, int rx, int ry,
                       int sw, int sh, int w, int h, int frame, GColor c) {
  int center_col = rx + sw / 2;
  for (int r = 0; r < sh; r++) {
    int dx, dy;
    rock_offset(frame, center_col, ry + r, w, h, &dx, &dy);
    fillrect(ctx, x + rx + dx, y + ry + r + dy, sw, 1, c);
  }
}

// ============================================================================
// DISGUISE: MGS1 CARDBOARD BOX (rocks)
// ============================================================================

static void draw_box(GContext *ctx, int x, int y) {
  int w = s_sprite_w, h = s_sprite_h;
  int f = s_rock_frame;

  // Base body
  rock_strip(ctx, x, y, 0, 0, w, h, w, h, f, COL_BOX);
  // Top flap dark band
  rock_strip(ctx, x, y, 0, 0, w, scale_y(7), w, h, f, COL_BOX_DARK);
  // Top flap centre seam
  rock_strip(ctx, x, y, w/2 - 1, 0, 2, scale_y(7), w, h, f, COL_WALL_LINE);
  // Flap edge highlights
  rock_strip(ctx, x, y, 0, 0, 2, scale_y(7), w, h, f, COL_BOX_SHAD);
  rock_strip(ctx, x, y, w - 2, 0, 2, scale_y(7), w, h, f, COL_BOX_SHAD);

  // Corrugation grain — horizontal lines every 4px alternating shade
  for (int cy = scale_y(10); cy < h - 3; cy += 4) {
    rock_strip(ctx, x, y, 2, cy,     w - 4, 1, w, h, f, COL_BOX_SHAD);
    rock_strip(ctx, x, y, 2, cy + 2, w - 4, 1, w, h, f, COL_BOX_MID);
  }

  // Edge shadow / highlight
  rock_strip(ctx, x, y, 0, scale_y(8), 2, h - scale_y(8), w, h, f, COL_BOX_DARK);
  rock_strip(ctx, x, y, w - 2, scale_y(8), 2, h - scale_y(8), w, h, f, COL_BOX_SHAD);

  // Shipping label
  int lbl_w = scale_x(22), lbl_h = scale_y(14);
  int lbl_x = scale_x(7), lbl_y = scale_y(20);
  rock_strip(ctx, x, y, lbl_x, lbl_y, lbl_w, lbl_h, w, h, f, COL_LABEL);
  // Label borders
  rock_strip(ctx, x, y, lbl_x, lbl_y,             lbl_w, 1, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, lbl_x, lbl_y + lbl_h - 1, lbl_w, 1, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, lbl_x, lbl_y,             1, lbl_h, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, lbl_x + lbl_w - 1, lbl_y, 1, lbl_h, w, h, f, COL_WALL_LINE);
  // Faux text on label
  rock_strip(ctx, x, y, lbl_x + 3, lbl_y + 3,  lbl_w - 6, 2, w, h, f, COL_LABEL_TEXT);
  rock_strip(ctx, x, y, lbl_x + 3, lbl_y + 7,  lbl_w - 10, 2, w, h, f, COL_LABEL_TEXT);
  rock_strip(ctx, x, y, lbl_x + 3, lbl_y + 11, lbl_w - 6, 1, w, h, f, COL_LABEL_TEXT);

  // "This way up" arrow stencil (top-left)
  int ax = scale_x(2), ay = scale_y(11);
  rock_strip(ctx, x, y, ax + 2, ay,     1, 4, w, h, f, COL_BOX_DARK);
  rock_strip(ctx, x, y, ax + 1, ay + 1, 3, 1, w, h, f, COL_BOX_DARK);
  rock_strip(ctx, x, y, ax,     ay + 2, 5, 1, w, h, f, COL_BOX_DARK);

  // Corner rivets
  rock_strip(ctx, x, y, 0, 0,         2, 2, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, w - 2, 0,     2, 2, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, 0, h - 2,     2, 2, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, w - 2, h - 2, 2, 2, w, h, f, COL_WALL_LINE);

  // Outline edges (top, bottom, left, right)
  rock_strip(ctx, x, y, 0, 0,     w, 1, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, 0, h - 1, w, 1, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, 0, 0,     1, h, w, h, f, COL_WALL_LINE);
  rock_strip(ctx, x, y, w - 1, 0, 1, h, w, h, f, COL_WALL_LINE);
}

// ============================================================================
// DISGUISE: MGSV SUPPLY DROP
// ============================================================================

static void draw_supply(GContext *ctx, int x, int y) {
  int w = s_sprite_w, h = s_sprite_h;

  fillrect(ctx, x, y, w, h, COL_SUPPLY_MAIN);
  fillrect(ctx, x, y, w, scale_y(8), COL_SUPPLY_DARK);

  // Yellow caution band
  int band_y = y + (h * 2) / 5;
  fillrect(ctx, x, band_y, w, scale_y(5), COL_SUPPLY_BAND);
  hline(ctx, x, band_y, w, COL_SUPPLY_STENCIL);
  hline(ctx, x, band_y + scale_y(5) - 1, w, COL_SUPPLY_STENCIL);

  // Caution band hazard chevrons
  for (int cx = x + 2; cx < x + w - 4; cx += scale_x(8)) {
    fillrect(ctx, cx, band_y + 1, scale_x(3), 1, COL_SUPPLY_STENCIL);
    fillrect(ctx, cx + 1, band_y + 2, scale_x(2), 1, COL_SUPPLY_STENCIL);
  }

  // Diamond hazard stencil
  int mx = x + w / 2;
  int my = y + scale_y(20);
  int dr = scale_y(7);
  for (int i = 0; i <= dr; i++) {
    fillrect(ctx, mx - i, my - dr + i, 1, 1, COL_SUPPLY_STENCIL);
    fillrect(ctx, mx + i, my - dr + i, 1, 1, COL_SUPPLY_STENCIL);
    fillrect(ctx, mx - i, my + dr - i, 1, 1, COL_SUPPLY_STENCIL);
    fillrect(ctx, mx + i, my + dr - i, 1, 1, COL_SUPPLY_STENCIL);
  }

  // Stencil "3-2"
  int num_y = y + scale_y(32);
  // 3
  fillrect(ctx, x + scale_x(15), num_y,                   scale_x(4), 2, COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(18), num_y,                   2, scale_y(8), COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(15), num_y + scale_y(3),      scale_x(4), 2, COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(15), num_y + scale_y(6),      scale_x(4), 2, COL_SUPPLY_STENCIL);
  // -
  fillrect(ctx, x + scale_x(22), num_y + scale_y(3),      scale_x(3), 2, COL_SUPPLY_STENCIL);
  // 2
  fillrect(ctx, x + scale_x(27), num_y,                   scale_x(4), 2, COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(29), num_y,                   2, scale_y(4), COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(27), num_y + scale_y(3),      scale_x(4), 2, COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(27), num_y + scale_y(4),      2, scale_y(4), COL_SUPPLY_STENCIL);
  fillrect(ctx, x + scale_x(27), num_y + scale_y(7),      scale_x(4), 2, COL_SUPPLY_STENCIL);

  // Seam rivets along top
  for (int rx = x + 3; rx < x + w - 3; rx += scale_x(8)) {
    fillrect(ctx, rx, y + scale_y(3), 2, 2, COL_SUPPLY_STENCIL);
  }

  // Corner reinforcement brackets
  fillrect(ctx, x + 1, y + 1, scale_x(5), 2, COL_SUPPLY_DARK);
  fillrect(ctx, x + 1, y + 1, 2, scale_y(5), COL_SUPPLY_DARK);
  fillrect(ctx, x + w - scale_x(5) - 1, y + 1, scale_x(5), 2, COL_SUPPLY_DARK);
  fillrect(ctx, x + w - 3, y + 1, 2, scale_y(5), COL_SUPPLY_DARK);
  fillrect(ctx, x + 1, y + h - 3, scale_x(5), 2, COL_SUPPLY_DARK);
  fillrect(ctx, x + 1, y + h - scale_y(5) - 1, 2, scale_y(5), COL_SUPPLY_DARK);
  fillrect(ctx, x + w - scale_x(5) - 1, y + h - 3, scale_x(5), 2, COL_SUPPLY_DARK);
  fillrect(ctx, x + w - 3, y + h - scale_y(5) - 1, 2, scale_y(5), COL_SUPPLY_DARK);

  // Edge shadow / highlight
  fillrect(ctx, x + 2, y + scale_y(10), 1, h - scale_y(14), COL_SUPPLY_DARK);
  fillrect(ctx, x + w - 3, y + scale_y(10), 1, h - scale_y(14), COL_SUPPLY_HI);

  // Scuff marks
  fillrect(ctx, x + scale_x(8), y + scale_y(50), scale_x(4), 2, COL_SUPPLY_DARK);
  fillrect(ctx, x + scale_x(40), y + scale_y(15), scale_x(3), 1, COL_SUPPLY_HI);
  fillrect(ctx, x + scale_x(55), y + scale_y(55), scale_x(5), 2, COL_SUPPLY_DARK);

  strokerect(ctx, x, y, w, h, COL_WALL_LINE);
}

// ============================================================================
// DISGUISE: MGS4 OIL DRUM
// ============================================================================

static void draw_drum(GContext *ctx, int x, int y) {
  int bw = (s_sprite_w * 3) / 5;
  int bx = x + (s_sprite_w - bw) / 2;
  int h  = s_sprite_h;

  // Body
  fillrect(ctx, bx, y, bw, h, COL_DRUM);
  // Top cap
  fillrect(ctx, bx, y, bw, scale_y(4), COL_DRUM_HI);
  fillrect(ctx, bx + 1, y + 1, bw - 2, scale_y(2), COL_DRUM);
  hline(ctx, bx, y + scale_y(3), bw, COL_DRUM_RING);
  // Bottom cap
  fillrect(ctx, bx, y + h - scale_y(4), bw, scale_y(4), COL_DRUM_HI);
  fillrect(ctx, bx + 1, y + h - scale_y(3), bw - 2, scale_y(2), COL_DRUM);
  hline(ctx, bx, y + h - scale_y(4), bw, COL_DRUM_RING);

  // 2 reinforcement rings at 1/3 and 2/3
  int ring1_y = y + h / 3;
  int ring2_y = y + (h * 2) / 3;
  fillrect(ctx, bx, ring1_y, bw, scale_y(4), COL_DRUM_RING);
  hline(ctx, bx, ring1_y, bw, COL_DRUM_HI);
  hline(ctx, bx, ring1_y + scale_y(3), bw, COL_DRUM_RING);
  for (int i = 0; i < 5; i++) {
    fillrect(ctx, bx + 2 + (i * (bw - 4)) / 4, ring1_y + 1, 2, 2, COL_DRUM_HI);
  }
  fillrect(ctx, bx, ring2_y, bw, scale_y(4), COL_DRUM_RING);
  hline(ctx, bx, ring2_y, bw, COL_DRUM_HI);
  hline(ctx, bx, ring2_y + scale_y(3), bw, COL_DRUM_RING);
  for (int i = 0; i < 5; i++) {
    fillrect(ctx, bx + 2 + (i * (bw - 4)) / 4, ring2_y + 1, 2, 2, COL_DRUM_HI);
  }

  // Left highlight strip
  vline(ctx, bx + 2, y + scale_y(5), h - scale_y(10), COL_DRUM_HI);

  // Vertical rust streaks
  struct { int dx, start, len, w; } rust_cols[] = {
    {6, 8, 12, 2}, {12, 14, 18, 1}, {16, 6, 22, 2}, {22, 11, 16, 1}
  };
  for (int i = 0; i < 4; i++) {
    int rx = bx + scale_x(rust_cols[i].dx);
    if (rx + rust_cols[i].w >= bx + bw - 1) continue;
    int ry = y + scale_y(rust_cols[i].start);
    int rh = scale_y(rust_cols[i].len);
    fillrect(ctx, rx, ry, rust_cols[i].w, rh, COL_RUST_DK);
    if (rust_cols[i].w >= 2) {
      fillrect(ctx, rx + rust_cols[i].w - 1, ry, 1, rh, COL_RUST_MD);
    }
    fillrect(ctx, rx, ry + rh, rust_cols[i].w, scale_y(2), COL_RUST_LT);
  }

  // Heavy rust patch right side
  int patch_x = bx + bw - scale_x(10);
  int patch_y = y + scale_y(22);
  fillrect(ctx, patch_x, patch_y, scale_x(7), scale_y(11), COL_RUST_DK);
  fillrect(ctx, patch_x + 1, patch_y + 1, scale_x(5), scale_y(8), COL_RUST_MD);
  fillrect(ctx, patch_x + 2, patch_y + 2, scale_x(3), scale_y(5), COL_RUST_LT);
  fillrect(ctx, patch_x - 1, patch_y + scale_y(3), 2, 2, COL_RUST_DK);
  fillrect(ctx, patch_x + scale_x(7), patch_y + scale_y(6), 2, 2, COL_RUST_DK);

  // Dent upper-left with shadow + highlight
  int dent_x = bx + scale_x(4);
  int dent_y = y + scale_y(10);
  fillrect(ctx, dent_x, dent_y, scale_x(7), 2, COL_DENT_DK);
  fillrect(ctx, dent_x, dent_y, 2, scale_y(6), COL_DENT_DK);
  fillrect(ctx, dent_x + 2, dent_y + 2, scale_x(5), scale_y(4), COL_DRUM_RING);
  fillrect(ctx, dent_x + scale_x(5), dent_y + 2, 2, scale_y(4), COL_DENT_HI);
  fillrect(ctx, dent_x + 2, dent_y + scale_y(4), scale_x(5), 2, COL_DENT_HI);

  // Grime dots
  int grime[][2] = { {4,18},{9,25},{14,31},{5,38},{11,42},{18,48},{7,52},{20,14} };
  for (int i = 0; i < 8; i++) {
    int px = bx + scale_x(grime[i][0]);
    int py = y + scale_y(grime[i][1]);
    if (px < bx + bw - 1) fillrect(ctx, px, py, 1, 1, COL_RUST_DK);
  }

  strokerect(ctx, bx, y, bw, h, COL_WALL_LINE);
}

// ============================================================================
// DISGUISE: PEACE WALKER LOVE BOX (double-wide cardboard, heart on front)
// ============================================================================

static void draw_lovebox(GContext *ctx, int x, int y) {
  int w  = s_sprite_w + scale_x(20);
  int h  = s_sprite_h;
  int bx = x - scale_x(10);

  fillrect(ctx, bx, y, w, h, COL_BOX);
  fillrect(ctx, bx, y, w, scale_y(7), COL_BOX_DARK);
  // Centre seam between joined boxes
  fillrect(ctx, bx + w / 2 - 1, y, 2, h, COL_BOX_SHAD);
  // Top flap seams on each half
  fillrect(ctx, bx + w / 4 - 1,   y, 2, scale_y(7), COL_WALL_LINE);
  fillrect(ctx, bx + (w*3)/4 - 1, y, 2, scale_y(7), COL_WALL_LINE);
  fillrect(ctx, bx,         y, 2, scale_y(7), COL_BOX_SHAD);
  fillrect(ctx, bx + w - 2, y, 2, scale_y(7), COL_BOX_SHAD);

  // Corrugation
  for (int cy = scale_y(10); cy < h - 3; cy += 4) {
    fillrect(ctx, bx + 2, y + cy,     w - 4, 1, COL_BOX_SHAD);
    fillrect(ctx, bx + 2, y + cy + 2, w - 4, 1, COL_BOX_MID);
  }
  fillrect(ctx, bx,         y + scale_y(8), 2, h - scale_y(8), COL_BOX_DARK);
  fillrect(ctx, bx + w - 2, y + scale_y(8), 2, h - scale_y(8), COL_BOX_SHAD);

  // Heart
  int hx = bx + w / 2 - scale_x(10);
  int hy = y + scale_y(20);
  fillrect(ctx, hx + scale_x(2),  hy,              scale_x(5), scale_y(3), COL_HEART);
  fillrect(ctx, hx + scale_x(13), hy,              scale_x(5), scale_y(3), COL_HEART);
  fillrect(ctx, hx + scale_x(1),  hy + scale_y(2), scale_x(7), scale_y(3), COL_HEART);
  fillrect(ctx, hx + scale_x(12), hy + scale_y(2), scale_x(7), scale_y(3), COL_HEART);
  fillrect(ctx, hx,               hy + scale_y(4), scale_x(20), scale_y(5), COL_HEART);
  fillrect(ctx, hx + scale_x(1),  hy + scale_y(8), scale_x(18), scale_y(3), COL_HEART);
  fillrect(ctx, hx + scale_x(3),  hy + scale_y(10), scale_x(14), scale_y(3), COL_HEART);
  fillrect(ctx, hx + scale_x(5),  hy + scale_y(12), scale_x(10), scale_y(2), COL_HEART);
  fillrect(ctx, hx + scale_x(7),  hy + scale_y(13), scale_x(6),  scale_y(2), COL_HEART);
  fillrect(ctx, hx + scale_x(9),  hy + scale_y(15), scale_x(2),  scale_y(2), COL_HEART);
  // Highlights
  fillrect(ctx, hx + scale_x(3),  hy + scale_y(2), scale_x(2), scale_y(2), COL_HEART_HI);
  fillrect(ctx, hx + scale_x(14), hy + scale_y(2), scale_x(2), scale_y(2), COL_HEART_HI);

  // Corner rivets + outline
  fillrect(ctx, bx, y, 2, 2, COL_WALL_LINE);
  fillrect(ctx, bx + w - 2, y, 2, 2, COL_WALL_LINE);
  fillrect(ctx, bx, y + h - 2, 2, 2, COL_WALL_LINE);
  fillrect(ctx, bx + w - 2, y + h - 2, 2, 2, COL_WALL_LINE);
  strokerect(ctx, bx, y, w, h, COL_WALL_LINE);
}

// ============================================================================
// ALERT BUBBLES
// ============================================================================

static void draw_q_bubble(GContext *ctx, int x) {
  int bw = scale_x(22), bh = scale_y(24);
  int bx = x + s_sprite_w / 2 - bw / 2;
  int by = s_sprite_y - bh - scale_y(5);
  if (by < scale_y(8)) by = scale_y(8);

  fillrect(ctx, bx, by, bw, bh, COL_Q_BG);
  strokerect(ctx, bx, by, bw, bh, COL_WALL_LINE);
  fillrect(ctx, bx + bw / 2 - 2, by + bh, scale_x(4), scale_y(5), COL_Q_BG);

  int qx = bx + bw / 2 - scale_x(4), qy = by + scale_y(4);
  fillrect(ctx, qx + scale_x(1), qy,              scale_x(7), scale_y(2), COL_Q_TEXT);
  fillrect(ctx, qx,              qy + scale_y(1), scale_x(2), scale_y(3), COL_Q_TEXT);
  fillrect(ctx, qx + scale_x(7), qy + scale_y(1), scale_x(2), scale_y(4), COL_Q_TEXT);
  fillrect(ctx, qx + scale_x(5), qy + scale_y(4), scale_x(3), scale_y(3), COL_Q_TEXT);
  fillrect(ctx, qx + scale_x(3), qy + scale_y(6), scale_x(3), scale_y(3), COL_Q_TEXT);
  fillrect(ctx, qx + scale_x(3), qy + scale_y(11), scale_x(3), scale_y(3), COL_Q_TEXT);
}

static void draw_e_bubble(GContext *ctx, int x) {
  int bw = scale_x(22), bh = scale_y(24);
  int bx = x + s_sprite_w / 2 - bw / 2;
  int by = s_sprite_y - bh - scale_y(5);
  if (by < scale_y(8)) by = scale_y(8);

  fillrect(ctx, bx, by, bw, bh, COL_E_BG);
  strokerect(ctx, bx, by, bw, bh, COL_WALL_LINE);
  fillrect(ctx, bx + bw / 2 - 2, by + bh, scale_x(4), scale_y(5), COL_E_BG);

  int ex = bx + bw / 2 - scale_x(2), ey = by + scale_y(4);
  fillrect(ctx, ex, ey,               scale_x(4), scale_y(10), COL_E_TEXT);
  fillrect(ctx, ex, ey + scale_y(12), scale_x(4), scale_y(4),  COL_E_TEXT);
}

// ============================================================================
// GRENADE ANIM — 15 frames, parabolic arc then drifting smoke
// ============================================================================

static void draw_grenade(GContext *ctx, int x, int frame) {
  if (frame < GRENADE_ARC_FRAMES) {
    // Parabolic arc: t goes 0..1 over arc frames
    // t and (1-t) both scaled by 100 to keep integer math
    int t100 = (frame * 100) / (GRENADE_ARC_FRAMES - 1);
    int sx = x + s_sprite_w / 2;
    int sy = s_sprite_y + scale_y(15);
    int ex = sx + scale_x(28);
    int ey = s_sprite_y + scale_y(20);
    int arc = scale_y(18);

    int gx = sx + ((ex - sx) * t100) / 100;
    int parabola = (4 * arc * t100 * (100 - t100)) / 10000;
    int gy = sy + ((ey - sy) * t100) / 100 - parabola;

    fillrect(ctx, gx, gy, scale_x(4), scale_x(4), COL_SMOKE_DARK);
    fillrect(ctx, gx + 1, gy + 1, scale_x(2), scale_x(2), COL_SUPPLY_DARK);
    fillrect(ctx, gx + scale_x(2), gy - 1, scale_x(2), 2, COL_LAMP);

    // Motion blur
    if (frame > 0) {
      int pt100 = ((frame - 1) * 100) / (GRENADE_ARC_FRAMES - 1);
      int pgx = sx + ((ex - sx) * pt100) / 100;
      int pp = (4 * arc * pt100 * (100 - pt100)) / 10000;
      int pgy = sy + ((ey - sy) * pt100) / 100 - pp;
      fillrect(ctx, pgx + 1, pgy + 1, 2, 2, COL_SMOKE_MED);
    }
  } else {
    // Smoke phase
    int sf = frame - GRENADE_ARC_FRAMES;  // 0..8
    int cx = x + s_sprite_w / 2 + scale_x(28);
    int cy = s_sprite_y + scale_y(20) - (sf * scale_y(2)) / 2;  // drift up

    int r1 = scale_x(3) + sf * scale_x(2);
    int r2 = scale_x(2) + (sf * 3) / 2;
    int r3 = scale_x(1) + sf;

    graphics_context_set_fill_color(ctx, COL_SMOKE_LIGHT);
    graphics_fill_circle(ctx, GPoint(cx - scale_x(2), cy - scale_y(1)), r1);
    graphics_context_set_fill_color(ctx, COL_SMOKE_MED);
    graphics_fill_circle(ctx, GPoint(cx + scale_x(2), cy + scale_y(1)), r2);
    graphics_context_set_fill_color(ctx, COL_SMOKE_DARK);
    graphics_fill_circle(ctx, GPoint(cx, cy), r3);

    // Wisps (deterministic, sparse based on frame)
    if (sf % 2 == 0) {
      fillrect(ctx, cx - r1 - 2, cy - r1, 1, 1, COL_SMOKE_MED);
      fillrect(ctx, cx + r2 + 2, cy + r3, 1, 1, COL_SMOKE_MED);
    }
    if (sf % 3 == 0) {
      fillrect(ctx, cx + r1 + 1, cy - r2, 1, 1, COL_SMOKE_MED);
    }
  }
}

// ============================================================================
// BATTERY INDICATOR (bottom-left)
// ============================================================================

static void draw_battery(GContext *ctx) {
  bool outdoor = is_outdoor_scene();
  GColor frame = outdoor ? COL_SNOW_HI : COL_BATT_FRAME;
  GColor bg    = COL_BATT_BG;

  GColor fill_color;
  if (s_battery_level <= 20)      fill_color = COL_BATT_LOW;
  else if (s_battery_level <= 50) fill_color = COL_BATT_MID;
  else                            fill_color = outdoor ? COL_SNOW_HI : COL_BATT_OK;

  int margin  = scale_x(4);
  int label_w = scale_x(22);
  int bar_x   = margin + label_w;
  int bar_y   = s_floor_y + scale_y(4);
  int bar_w   = s_screen_w - bar_x - margin;
  int bar_h   = scale_y(7);

  // "LIFE" label left of bar
  GFont font = fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD);
  GRect lr = GRect(margin, bar_y - 2, label_w, bar_h + 4);
  graphics_context_set_text_color(ctx, fill_color);
  graphics_draw_text(ctx, "LIFE", font, lr,
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentLeft, NULL);

  // Bar frame + fill
  strokerect(ctx, bar_x, bar_y, bar_w, bar_h, frame);
  fillrect(ctx, bar_x + 1, bar_y + 1, bar_w - 2, bar_h - 2, bg);
  int fill_w = ((bar_w - 2) * s_battery_level) / 100;
  fillrect(ctx, bar_x + 1, bar_y + 1, fill_w, bar_h - 2, fill_color);
}

static void draw_fission_mailed(GContext *ctx) {
  int bar_h = scale_y(24);
  int bar_y = s_sprite_y + (s_sprite_h - bar_h) / 2;

  // Simulate 50% alpha gray with row dithering (every other row filled)
#ifdef PBL_COLOR
  GColor dither = GColorLightGray;
#else
  GColor dither = GColorWhite;
#endif
  for (int r = bar_y; r < bar_y + bar_h; r++) {
    if (r % 2 == 0) fillrect(ctx, 0, r, s_screen_w, 1, dither);
  }

  GFont font = fonts_get_system_font(FONT_KEY_ROBOTO_CONDENSED_21);
  GRect tr = GRect(0, bar_y + 1, s_screen_w, bar_h);
  graphics_context_set_text_color(ctx, GColorBlack);
  graphics_draw_text(ctx, "FISSION MAILED", font, tr,
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
}

static void draw_alert_status(GContext *ctx, const char *label, GColor bg, GColor fg) {
  int bar_y = s_sprite_y + s_sprite_h / 4;
  int bar_h = scale_y(20);
  fillrect(ctx, 0, bar_y, s_screen_w, bar_h, bg);
  GFont font = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  GRect tr = GRect(0, bar_y + 1, s_screen_w, bar_h);
  graphics_context_set_text_color(ctx, fg);
  graphics_draw_text(ctx, label, font, tr,
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
}

// ============================================================================
// MAIN CANVAS UPDATE
// ============================================================================

static void canvas_update_proc(Layer *layer, GContext *ctx) {
  if (!s_window_loaded) return;

  bool outdoor = is_outdoor_scene();
  if (outdoor) draw_outdoor(ctx);
  else         draw_hallway(ctx);

  int x = (int)(s_sprite_x_fp / 100);
  int sprite_cx = x + s_sprite_w / 2;

  if (outdoor) draw_floodlight(ctx, sprite_cx);
  else         draw_camera(ctx, sprite_cx);

  // Sprite hidden during grenade, evasion, caution
  bool hide_sprite = (s_state == STATE_GRENADE
                   || s_state == STATE_EVASION
                   || s_state == STATE_CAUTION
                   || s_state == STATE_VANISHED);

  if (!hide_sprite) {
    switch (s_disguise) {
      case DISGUISE_BOX:     draw_box(ctx, x, s_sprite_y);     break;
      case DISGUISE_SUPPLY:  draw_supply(ctx, x, s_sprite_y);  break;
      case DISGUISE_DRUM:    draw_drum(ctx, x, s_sprite_y);    break;
      case DISGUISE_LOVEBOX: draw_lovebox(ctx, x, s_sprite_y); break;
    }
  }

  if (s_state == STATE_ALERT && s_q_visible) draw_q_bubble(ctx, x);
  if (s_state == STATE_BANG)                 draw_e_bubble(ctx, x);
  if (s_state == STATE_GRENADE)              draw_grenade(ctx, x, s_grenade_frame);

#ifdef PBL_COLOR
  if (s_state == STATE_EVASION)
    draw_alert_status(ctx, "EVASION", GColorOrange, GColorBlack);
  if (s_state == STATE_CAUTION)
    draw_alert_status(ctx, "CAUTION", GColorChromeYellow, GColorBlack);
#else
  if (s_state == STATE_EVASION)
    draw_alert_status(ctx, "EVASION", GColorWhite, GColorBlack);
  if (s_state == STATE_CAUTION)
    draw_alert_status(ctx, "CAUTION", GColorWhite, GColorBlack);
#endif

  draw_battery(ctx);

  if (!s_bt_connected) draw_fission_mailed(ctx);
}

// ============================================================================
// LIFECYCLE GUARDS
// ============================================================================

static bool should_animate(void) {
  return s_fully_initialized && s_window_loaded && s_in_focus
      && !s_is_charging;
}

static uint32_t get_anim_interval(void) {
  uint32_t i = ANIM_TICK_MS;
#ifndef PBL_COLOR
  i = ANIM_TICK_MS_LOW_POWER;
#endif
  if (s_battery_level <= LOW_BATTERY_THRESHOLD) i = ANIM_TICK_MS_LOW_POWER;
  return i < 50 ? 50 : i;
}

// ============================================================================
// IDLE / WALK ANIMATION
// ============================================================================

static void anim_tick(void *data);

static void schedule_anim(void) {
  if (s_anim_timer || !should_animate() || s_state != STATE_IDLE) return;
  s_anim_timer = app_timer_register(get_anim_interval(), anim_tick, NULL);
}

static void anim_tick(void *data) {
  s_anim_timer = NULL;
  if (s_state != STATE_IDLE || !should_animate()) return;

  s_anim_tick++;

  int path_px = s_screen_w + s_sprite_w;
  int total_ticks = s_traverse_ms / (int)get_anim_interval();
  if (total_ticks < 1) total_ticks = 1;
  int32_t dx_fp = ((int32_t)path_px * 100) / total_ticks;
  if (dx_fp < 1) dx_fp = 1;

  s_sprite_x_fp += dx_fp;
  if (s_sprite_x_fp >= (int32_t)s_screen_w * 100) s_sprite_x_fp = 0;

  // Advance box rock frame
  s_rock_frame = (s_rock_frame + 1) % 4;

  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  schedule_anim();
}

// ============================================================================
// STATE TRANSITIONS
// ============================================================================

static void cancel_alert_timers(void) {
  if (s_alert_timeout_timer) { app_timer_cancel(s_alert_timeout_timer); s_alert_timeout_timer = NULL; }
  if (s_q_blink_timer)       { app_timer_cancel(s_q_blink_timer);       s_q_blink_timer = NULL; }
  if (s_bang_timer)          { app_timer_cancel(s_bang_timer);          s_bang_timer = NULL; }
}

static void cancel_all_timers(void) {
  if (s_anim_timer)          { app_timer_cancel(s_anim_timer);          s_anim_timer = NULL; }
  cancel_alert_timers();
  if (s_grenade_timer)       { app_timer_cancel(s_grenade_timer);       s_grenade_timer = NULL; }
  if (s_evasion_timer)       { app_timer_cancel(s_evasion_timer);       s_evasion_timer = NULL; }
  if (s_caution_timer)       { app_timer_cancel(s_caution_timer);       s_caution_timer = NULL; }
  if (s_sweep_timer)         { app_timer_cancel(s_sweep_timer);         s_sweep_timer = NULL; }
}

static void enter_idle(void) {
  cancel_all_timers();
  s_state = STATE_IDLE;
  s_sweep_phase = 0;
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  schedule_anim();
}

static void q_blink_tick(void *data) {
  s_q_blink_timer = NULL;
  if (s_state != STATE_ALERT) return;
  s_q_visible = !s_q_visible;
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  s_q_blink_timer = app_timer_register(Q_BLINK_MS, q_blink_tick, NULL);
}

static void alert_timeout(void *data) {
  s_alert_timeout_timer = NULL;
  if (s_state == STATE_ALERT) enter_idle();
}

static void enter_alert(void) {
  if (s_anim_timer) { app_timer_cancel(s_anim_timer); s_anim_timer = NULL; }
  s_state = STATE_ALERT;
  s_q_visible = true;
  s_q_blink_timer       = app_timer_register(Q_BLINK_MS,       q_blink_tick,   NULL);
  s_alert_timeout_timer = app_timer_register(ALERT_TIMEOUT_MS, alert_timeout, NULL);
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
}

static void bang_timeout(void *data) {
  s_bang_timer = NULL;
  if (s_state == STATE_BANG) enter_grenade();
}

static void enter_bang(void) {
  cancel_alert_timers();
  s_state = STATE_BANG;
  s_bang_timer = app_timer_register(BANG_DURATION_MS, bang_timeout, NULL);
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
}

static void sweep_advance(void *data) {
  s_sweep_timer = NULL;
  if (s_state != STATE_EVASION) return;
  s_sweep_phase = (s_sweep_phase + 1) % 4;
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  s_sweep_timer = app_timer_register(SWEEP_PHASE_MS, sweep_advance, NULL);
}

static void caution_timeout(void *data) {
  s_caution_timer = NULL;
  s_sprite_x_fp = 0;
  enter_idle();
}

static void enter_caution(void) {
  if (s_sweep_timer) { app_timer_cancel(s_sweep_timer); s_sweep_timer = NULL; }
  s_state = STATE_CAUTION;
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  s_caution_timer = app_timer_register(CAUTION_DURATION_MS, caution_timeout, NULL);
}

static void evasion_timeout(void *data) {
  s_evasion_timer = NULL;
  if (s_state == STATE_EVASION) enter_caution();
}

static void enter_evasion(void) {
  if (s_sweep_timer)   { app_timer_cancel(s_sweep_timer);   s_sweep_timer   = NULL; }
  if (s_evasion_timer) { app_timer_cancel(s_evasion_timer); s_evasion_timer = NULL; }
  s_state = STATE_EVASION;
  s_sweep_phase = 0;
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  s_sweep_timer   = app_timer_register(SWEEP_PHASE_MS, sweep_advance, NULL);
  s_evasion_timer = app_timer_register(EVASION_DURATION_MS, evasion_timeout, NULL);
}

static void grenade_frame_tick(void *data) {
  s_grenade_timer = NULL;
  s_grenade_frame++;
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
  if (s_grenade_frame >= GRENADE_TOTAL_FRAMES) enter_evasion();
  else s_grenade_timer = app_timer_register(GRENADE_FRAME_MS, grenade_frame_tick, NULL);
}

static void enter_grenade(void) {
  cancel_alert_timers();
  s_state = STATE_GRENADE;
  s_grenade_frame = 0;
  vibes_double_pulse();
  s_grenade_timer = app_timer_register(GRENADE_FRAME_MS, grenade_frame_tick, NULL);
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
}

// ============================================================================
// BUTTON HANDLER — left (BACK) button steps through alert sequence
// IDLE -> ALERT -> BANG -> GRENADE -> EVASION -> CAUTION -> IDLE
// ============================================================================

static void back_click_handler(ClickRecognizerRef recognizer, void *context) {
  if (!s_fully_initialized) return;
  if      (s_state == STATE_IDLE)  enter_alert();
  else if (s_state == STATE_ALERT) enter_bang();
  // BANG, GRENADE, EVASION, CAUTION: ignore further presses
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_BACK, back_click_handler);
}

// ============================================================================
// TIME
// ============================================================================

static void update_time(void) {
  if (!s_fully_initialized || !s_time_layer || !s_date_layer) return;
  time_t t = time(NULL);
  struct tm *tm = localtime(&t);
  strftime(s_time_buf, sizeof(s_time_buf), clock_is_24h_style() ? "%H:%M" : "%I:%M", tm);
  text_layer_set_text(s_time_layer, s_time_buf);
  strftime(s_date_buf, sizeof(s_date_buf), "%a %b %d", tm);
  text_layer_set_text(s_date_layer, s_date_buf);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
  // Auto scene mode may have flipped over midnight - mark dirty
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
}

// ============================================================================
// SYSTEM SERVICES
// ============================================================================

static void battery_callback(BatteryChargeState state) {
  s_battery_level = state.charge_percent;
  s_is_charging   = state.is_plugged;
  if (s_fully_initialized) {
    if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
    schedule_anim();
  }
}

static void bt_handler(bool connected) {
  bool was_connected = s_bt_connected;
  s_bt_connected = connected;
  // Vibrate on change if user has it enabled
  if (s_fully_initialized && s_bt_vibrate && was_connected != connected) {
    if (connected) vibes_short_pulse();
    else           vibes_double_pulse();
  }
  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
}

static void focus_handler(bool in_focus) {
  s_in_focus = in_focus;
  if (!in_focus && s_anim_timer) { app_timer_cancel(s_anim_timer); s_anim_timer = NULL; }
  else if (in_focus) schedule_anim();
}

// ============================================================================
// APP MESSAGE — Clay sends cstring values, parse via atoi
// ============================================================================

static void inbox_received_handler(DictionaryIterator *iter, void *context) {
  Tuple *t;

  t = dict_find(iter, MESSAGE_KEY_disguise);
  if (t) {
    int val = (t->type == TUPLE_CSTRING)
              ? (int)atoi(t->value->cstring)
              : (int)t->value->int32;
    s_disguise = clampi(val, 0, DISGUISE_COUNT - 1);
    persist_write_int(PKEY_DISGUISE, s_disguise);
  }

  t = dict_find(iter, MESSAGE_KEY_traverseMs);
  if (t) {
    int v = (t->type == TUPLE_CSTRING)
            ? (int)atoi(t->value->cstring)
            : (int)t->value->int32;
    v = clampi(v, 60000, 600000);
    s_traverse_ms = v;
    persist_write_int(PKEY_TRAVERSE_MS, s_traverse_ms);
  }

  t = dict_find(iter, MESSAGE_KEY_btVibrate);
  if (t) {
    bool v = (t->type == TUPLE_CSTRING)
             ? (atoi(t->value->cstring) != 0)
             : (t->value->int32 != 0);
    s_bt_vibrate = v;
    persist_write_bool(PKEY_BT_VIBRATE, s_bt_vibrate);
  }

  t = dict_find(iter, MESSAGE_KEY_sceneMode);
  if (t) {
    int v = (t->type == TUPLE_CSTRING)
            ? (int)atoi(t->value->cstring)
            : (int)t->value->int32;
    s_scene_mode = clampi(v, 0, 2);
    persist_write_int(PKEY_SCENE_MODE, s_scene_mode);
  }

  if (s_canvas_layer) layer_mark_dirty(s_canvas_layer);
}

// ============================================================================
// WINDOW LIFECYCLE
// ============================================================================

static void main_window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect  bounds = layer_get_bounds(root);

  s_screen_w = bounds.size.w;
  s_screen_h = bounds.size.h;

  s_floor_y  = (s_screen_h * 140) / 168;
  s_sprite_w = scale_x(64);
  s_sprite_h = scale_y(48);
  s_sprite_y = s_floor_y - s_sprite_h;

  s_sprite_x_fp = 0;

  s_window_loaded = true;

  s_canvas_layer = layer_create(bounds);
  layer_set_update_proc(s_canvas_layer, canvas_update_proc);
  layer_add_child(root, s_canvas_layer);

  // Time text
  int time_y = scale_y(22), time_h = scale_y(46);
  s_time_layer = text_layer_create(GRect(0, time_y, s_screen_w, time_h));
  text_layer_set_background_color(s_time_layer, GColorClear);
  text_layer_set_text_color(s_time_layer, COL_TIME_TEXT);
  text_layer_set_font(s_time_layer, fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS));
  text_layer_set_text_alignment(s_time_layer, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_time_layer));

  // Date text below the LIFE bar
  int date_y = s_floor_y + scale_y(12);
  int date_h = s_screen_h - date_y - scale_y(1);
  s_date_layer = text_layer_create(GRect(0, date_y, s_screen_w, date_h));
  text_layer_set_background_color(s_date_layer, GColorClear);
  text_layer_set_text_color(s_date_layer, COL_DATE_TEXT);
  text_layer_set_font(s_date_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
  text_layer_set_text_alignment(s_date_layer, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_date_layer));

  battery_callback(battery_state_service_peek());
  s_bt_connected = connection_service_peek_pebble_app_connection();

  s_fully_initialized = true;
  update_time();
  schedule_anim();
}

static void main_window_unload(Window *window) {
  s_fully_initialized = false;
  s_window_loaded     = false;
  cancel_all_timers();
  if (s_time_layer)   { text_layer_destroy(s_time_layer);   s_time_layer = NULL; }
  if (s_date_layer)   { text_layer_destroy(s_date_layer);   s_date_layer = NULL; }
  if (s_canvas_layer) { layer_destroy(s_canvas_layer);      s_canvas_layer = NULL; }
}

// ============================================================================
// APP INIT / DEINIT
// ============================================================================

static void init(void) {
  if (persist_exists(PKEY_DISGUISE))    s_disguise    = persist_read_int(PKEY_DISGUISE);
  if (persist_exists(PKEY_TRAVERSE_MS)) s_traverse_ms = persist_read_int(PKEY_TRAVERSE_MS);
  if (persist_exists(PKEY_BT_VIBRATE))  s_bt_vibrate  = persist_read_bool(PKEY_BT_VIBRATE);
  if (persist_exists(PKEY_SCENE_MODE))  s_scene_mode  = persist_read_int(PKEY_SCENE_MODE);
  s_traverse_ms = clampi(s_traverse_ms, 60000, 600000);
  s_disguise    = clampi(s_disguise, 0, DISGUISE_COUNT - 1);
  s_scene_mode  = clampi(s_scene_mode, 0, 2);

  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers){
    .load   = main_window_load,
    .unload = main_window_unload,
  });
  window_stack_push(s_main_window, true);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
  battery_state_service_subscribe(battery_callback);
  connection_service_subscribe((ConnectionHandlers){
    .pebble_app_connection_handler = bt_handler,
    .pebblekit_connection_handler  = NULL,
  });
  app_focus_service_subscribe(focus_handler);
  window_set_click_config_provider(s_main_window, click_config_provider);

  app_message_register_inbox_received(inbox_received_handler);
  app_message_open(256, 64);
}

static void deinit(void) {
  cancel_all_timers();
  tick_timer_service_unsubscribe();
  battery_state_service_unsubscribe();
  connection_service_unsubscribe();
  app_focus_service_unsubscribe();
  if (s_main_window) { window_destroy(s_main_window); s_main_window = NULL; }
}

int main(void) {
  init();
  app_event_loop();
  deinit();
  return 0;
}
