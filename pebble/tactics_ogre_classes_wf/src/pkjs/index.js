// ─── Weather ─────────────────────────────────────────────────────────
// Uses Open-Meteo (no API key required). Sends the current temperature to
// the watch as an integer in Celsius under the WeatherTemp message key; the
// watch converts to Fahrenheit itself based on the user's unit setting.
//
// Ordering matters here. The 'ready' and 'appmessage' listeners are
// registered at the very bottom, but everything above them is written so
// that nothing can throw at module scope. A single uncaught exception while
// this file is being evaluated means NO listeners ever get registered, and
// the watch then sits on "--" forever with no way to recover — which is
// exactly what a bare `new Clay(...)` at the top of the file used to cause.

var MAX_ATTEMPTS = 3;
var RETRY_DELAY_MS = 10000;
var COORD_KEY = 'lastCoords';
var fetchInFlight = false;

function log(msg) {
  console.log('[weather] ' + msg);
}

// ─── Coordinate cache ────────────────────────────────────────────────
// GPS is the single most common point of failure: permission denied, no
// fix indoors, or a timeout. Remembering the last good position means one
// bad lookup doesn't cost us the temperature.
function saveCoords(lat, lon) {
  try {
    localStorage.setItem(COORD_KEY, JSON.stringify({ lat: lat, lon: lon }));
  } catch (e) {
    log('could not cache coords: ' + e);
  }
}

function loadCoords() {
  try {
    var raw = localStorage.getItem(COORD_KEY);
    if (!raw) return null;
    var c = JSON.parse(raw);
    if (c && typeof c.lat === 'number' && typeof c.lon === 'number') return c;
  } catch (e) {
    log('could not read cached coords: ' + e);
  }
  return null;
}

// ─── Send ────────────────────────────────────────────────────────────
function sendWeatherTemp(tempC, attempt) {
  var rounded = Math.round(tempC);
  Pebble.sendAppMessage(
    { WeatherTemp: rounded },
    function() {
      log('sent ' + rounded + 'C to watch');
    },
    function(e) {
      log('send failed: ' + JSON.stringify(e));
      if (attempt < MAX_ATTEMPTS) {
        setTimeout(function() { sendWeatherTemp(tempC, attempt + 1); }, RETRY_DELAY_MS);
      }
    }
  );
}

// ─── Fetch ───────────────────────────────────────────────────────────
function fetchWeather(lat, lon, attempt) {
  var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat +
            '&longitude=' + lon + '&current=temperature_2m';

  function retryOrGiveUp(reason) {
    log(reason);
    if (attempt < MAX_ATTEMPTS) {
      setTimeout(function() { fetchWeather(lat, lon, attempt + 1); }, RETRY_DELAY_MS);
    } else {
      fetchInFlight = false;
      log('giving up after ' + MAX_ATTEMPTS + ' attempts');
    }
  }

  var req = new XMLHttpRequest();
  req.open('GET', url, true);
  req.timeout = 20000;

  req.onload = function() {
    if (req.status !== 200) {
      retryOrGiveUp('HTTP ' + req.status);
      return;
    }
    var data;
    try {
      data = JSON.parse(req.responseText);
    } catch (err) {
      retryOrGiveUp('parse error: ' + err);
      return;
    }
    if (data && data.current && typeof data.current.temperature_2m === 'number') {
      fetchInFlight = false;
      saveCoords(lat, lon);
      sendWeatherTemp(data.current.temperature_2m, 1);
    } else {
      retryOrGiveUp('unexpected response: ' + req.responseText.slice(0, 120));
    }
  };
  req.onerror = function() { retryOrGiveUp('network error'); };
  req.ontimeout = function() { retryOrGiveUp('timed out'); };

  try {
    req.send();
  } catch (err) {
    retryOrGiveUp('send threw: ' + err);
  }
}

// ─── Entry point ─────────────────────────────────────────────────────
function updateWeather() {
  if (fetchInFlight) {
    log('fetch already in flight, skipping');
    return;
  }
  fetchInFlight = true;

  function onPosition(pos) {
    log('got GPS fix');
    fetchWeather(pos.coords.latitude, pos.coords.longitude, 1);
  }

  function onPositionError(err) {
    // Don't lose the temperature just because GPS is unavailable — fall
    // back to wherever we last successfully fetched from.
    var cached = loadCoords();
    if (cached) {
      log('GPS failed (' + err.message + '), using cached coords');
      fetchWeather(cached.lat, cached.lon, 1);
    } else {
      fetchInFlight = false;
      log('GPS failed (' + err.message + ') and no cached coords available');
    }
  }

  try {
    navigator.geolocation.getCurrentPosition(onPosition, onPositionError,
      { timeout: 15000, maximumAge: 60000 });
  } catch (err) {
    fetchInFlight = false;
    log('geolocation threw: ' + err);
  }
}

// ─── Clay (settings page) ────────────────────────────────────────────
// Constructed with autoHandleEvents disabled and wrapped in try/catch: if
// Clay fails to load, weather must keep working, and the config events are
// handled explicitly below so there's exactly one handler for each.
var clay = null;
try {
  var Clay = require('pebble-clay');
  var clayConfig = require('./config');
  clay = new Clay(clayConfig, null, { autoHandleEvents: false });
} catch (e) {
  console.log('Clay init failed (settings page disabled): ' + e);
}

Pebble.addEventListener('showConfiguration', function() {
  if (clay && typeof clay.generateUrl === 'function') {
    Pebble.openURL(clay.generateUrl());
  } else {
    console.log('Clay unavailable — cannot open settings.');
  }
});

Pebble.addEventListener('webviewclosed', function(e) {
  if (!e || !e.response) return;
  if (!clay || typeof clay.getSettings !== 'function') return;

  var settings;
  try {
    settings = clay.getSettings(e.response);
  } catch (err) {
    console.log('Failed to parse settings: ' + err);
    return;
  }
  Pebble.sendAppMessage(settings,
    function() { console.log('Settings sent to watch.'); },
    function(err) { console.log('Settings send error: ' + JSON.stringify(err)); }
  );
  // Units may have changed; refresh so the new unit shows a real value.
  updateWeather();
});

// ─── Pebble lifecycle ────────────────────────────────────────────────
Pebble.addEventListener('ready', function() {
  console.log('PebbleKit JS ready');
  updateWeather();
  // Backstop only — the watch's WeatherRequest is the primary trigger,
  // since the phone suspends this JS runtime at will.
  setInterval(updateWeather, 30 * 60 * 1000);
});

Pebble.addEventListener('appmessage', function(e) {
  if (e && e.payload && e.payload.WeatherRequest) {
    log('watch requested an update');
    updateWeather();
  }
});
