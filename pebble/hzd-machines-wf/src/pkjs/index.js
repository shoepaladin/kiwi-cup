/* jshint undef:true, unused:true */
/* globals Pebble, localStorage */

// ── Machine names (index must match ICON_xx constants in main.c) ────────
var ICONS = [
  'Burrower', 'Clawstrider', 'Slitherfang', 'Fanghorn', 'Sunwing', 'Leaplasher',
  'Scrounger', 'Slaughterspine', 'Clamberjaw', 'Tremortusk', 'Bristleback', 'Rollerback',
  'Shellsnapper', 'Skydrifter', 'Widemaw', 'Spikesnout', 'Plowhorn', 'Tideripper',
  'Dreadwing', 'Specter', 'Specter Prime'
];

// ── Phone-side settings persistence ─────────────────────────────────────
function loadSettings() {
  try { return JSON.parse(localStorage.getItem('hzd_settings') || '{}'); } catch(e) { return {}; }
}
function saveSettings(s) {
  try { localStorage.setItem('hzd_settings', JSON.stringify(s)); } catch(e) {}
}

// ── Build the HTML settings page as a string ─────────────────────────────
// No frameworks, no npm — plain HTML/CSS/JS so webpack 1.x bundles cleanly.
function buildConfigPage(s) {
  var tf    = (s.TimeFormat !== undefined ? s.TimeFormat : 1);
  var icon  = (s.IconIndex  !== undefined ? s.IconIndex  : 0);
  var mode  = (s.IconMode   !== undefined ? s.IconMode   : 60);
  var goal  = (s.StepGoal   !== undefined ? s.StepGoal   : 10000);
  var color = (s.ThemeColor !== undefined ? s.ThemeColor : 207); // 207 = cyan

  var iconOpts = ICONS.map(function(name, i) {
    return '<option value="' + i + '"' + (icon == i ? ' selected' : '') + '>' + name + '</option>';
  }).join('');

  var modeOpts = [
    ['Static (no rotation)', 0],
    ['Every 1 minute',  1],
    ['Every 5 minutes', 5],
    ['Every 15 minutes',15],
    ['Every 30 minutes',30],
    ['Every hour',      60]
  ].map(function(o) {
    return '<option value="' + o[1] + '"' + (mode == o[1] ? ' selected' : '') + '>' + o[0] + '</option>';
  }).join('');

  // Theme colors: each value is the Pebble GColor argb byte (alpha=3).
  var colorOpts = [
    ['Cyan',    207],
    ['Green',   204],
    ['Yellow',  252],
    ['Orange',  248],
    ['Red',     240],
    ['Magenta', 243],
    ['Blue',    195],
    ['White',   255]
  ].map(function(o) {
    return '<option value="' + o[1] + '"' + (color == o[1] ? ' selected' : '') + '>' + o[0] + '</option>';
  }).join('');

  return [
    '<!DOCTYPE html><html><head>',
    '<meta name="viewport" content="width=device-width,initial-scale=1">',
    '<style>',
    'body{margin:0;padding:16px 16px 32px;background:#111;color:#ddd;',
    '     font-family:-apple-system,BlinkMacSystemFont,sans-serif;font-size:15px}',
    'h2{color:#00ffff;margin:0 0 20px;font-size:18px;letter-spacing:3px;text-transform:uppercase}',
    '.card{background:#1e1e1e;border:1px solid #333;border-radius:8px;padding:14px;margin-bottom:12px}',
    '.lbl{color:#00ffff;font-size:10px;letter-spacing:2px;text-transform:uppercase;margin-bottom:8px}',
    'select{width:100%;box-sizing:border-box;background:#2a2a2a;color:#fff;',
    '       border:1px solid #444;border-radius:4px;padding:9px 8px;font-size:14px}',
    'input[type=number]{width:100%;box-sizing:border-box;background:#2a2a2a;color:#fff;',
    '       border:1px solid #444;border-radius:4px;padding:9px 8px;font-size:14px}',
    '.hint{color:#666;font-size:11px;margin-top:6px}',
    'button{display:block;width:100%;padding:14px;background:#00ffff;color:#000;',
    '       border:none;border-radius:6px;font-size:15px;font-weight:bold;',
    '       letter-spacing:2px;text-transform:uppercase;margin-top:8px;cursor:pointer}',
    'button:active{background:#00cccc}',
    '</style></head><body>',

    '<h2>HZD Machines</h2>',

    '<div class="card">',
    '<div class="lbl">Time Format</div>',
    '<select id="tf">',
    '<option value="1"' + (tf  ? ' selected' : '') + '>12-Hour</option>',
    '<option value="0"' + (!tf ? ' selected' : '') + '>24-Hour</option>',
    '</select></div>',

    '<div class="card">',
    '<div class="lbl">Machine Icon</div>',
    '<select id="icon">' + iconOpts + '</select></div>',

    '<div class="card">',
    '<div class="lbl">Icon Rotation</div>',
    '<select id="mode">' + modeOpts + '</select></div>',

    '<div class="card">',
    '<div class="lbl">Theme Color</div>',
    '<select id="color">' + colorOpts + '</select></div>',

    '<div class="card">',
    '<div class="lbl">Daily Step Goal</div>',
    '<input type="number" id="goal" min="1000" max="100000" step="100" ',
    'inputmode="numeric" value="' + goal + '">',
    '<div class="hint">Enter a value between 1,000 and 100,000</div>',
    '</div>',

    '<button onclick="save()">Save Settings</button>',

    '<script>',
    'function save(){',
    '  var g=parseInt(document.getElementById("goal").value,10);',
    '  if(isNaN(g)||g<1000){g=1000;}',
    '  if(g>100000){g=100000;}',
    '  var c={',
    '    TimeFormat:+document.getElementById("tf").value,',
    '    IconIndex: +document.getElementById("icon").value,',
    '    IconMode:  +document.getElementById("mode").value,',
    '    StepGoal:  g,',
    '    ThemeColor:+document.getElementById("color").value',
    '  };',
    '  location.href="pebblejs://close#"+encodeURIComponent(JSON.stringify(c));',
    '}',
    '<\/script></body></html>'
  ].join('');
}

// ── Pebble event handlers ─────────────────────────────────────────────────
Pebble.addEventListener('ready', function() {
  console.log('[HZD] JS runtime ready');
});

Pebble.addEventListener('showConfiguration', function() {
  var settings = loadSettings();
  Pebble.openURL('data:text/html,' + encodeURIComponent(buildConfigPage(settings)));
});

Pebble.addEventListener('webviewclosed', function(e) {
  if (!e.response || e.response === 'CANCELLED') { return; }
  var config;
  try { config = JSON.parse(decodeURIComponent(e.response)); } catch(ex) {
    console.log('[HZD] bad response: ' + e.response);
    return;
  }

  saveSettings(config);

  Pebble.sendAppMessage({
    'TimeFormat': config.TimeFormat | 0,
    'IconIndex':  config.IconIndex  | 0,
    'IconMode':   config.IconMode   | 0,
    'StepGoal':   config.StepGoal   | 0,
    'ThemeColor': config.ThemeColor | 0
  },
  function()    { console.log('[HZD] settings sent');         },
  function(err) { console.log('[HZD] send error: ' + err);   }
  );
});
