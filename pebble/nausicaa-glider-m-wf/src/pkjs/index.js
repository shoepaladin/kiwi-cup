// Defensive Clay init. If pebble-clay fails to require() (missing dep,
// version mismatch, etc.), the rest of the JS still runs and weather/steps
// keep working.
var clay = null;
try {
  var Clay = require('pebble-clay');
  var clayConfig = require('./config.json');
  clay = new Clay(clayConfig);
} catch (e) {
  console.log('Clay init failed: ' + e);
}

// ---------------------------------------------------------------------------
// Weather fetch (Open-Meteo, keyless).
// ---------------------------------------------------------------------------
function fetchWeatherFromCoords(lat, lon) {
  var url = 'https://api.open-meteo.com/v1/forecast?latitude=' +
            lat + '&longitude=' + lon + '&current_weather=true';

  return fetch(url)
    .then(function (response) { return response.json(); })
    .then(function (json) {
      // Send raw Celsius to the watch -- C does the F/C display conversion.
      var tempCelsius = Math.round(json.current_weather.temperature);

      Pebble.sendAppMessage({
        'Temperature': tempCelsius
      }, function () {
        console.log('Weather sent (raw C): ' + tempCelsius);
      }, function (err) {
        console.log('AppMessage send error: ' + JSON.stringify(err));
      });
    })
    .catch(function (err) { console.log('Open-Meteo error: ' + err); });
}

function triggerLocationLookup() {
  navigator.geolocation.getCurrentPosition(
    function (pos) {
      fetchWeatherFromCoords(pos.coords.latitude, pos.coords.longitude);
    },
    function (err) {
      console.log('GPS unavailable; falling back to Seattle/Redmond.');
      fetchWeatherFromCoords(47.674, -122.121);
    },
    { timeout: 15000, maximumAge: 600000 }
  );
}

// ---------------------------------------------------------------------------
// Pebble lifecycle events.
// ---------------------------------------------------------------------------
Pebble.addEventListener('ready', function () {
  console.log('PebbleKit JS ready.');
  triggerLocationLookup();
});

// Manual showConfiguration handler. Clay normally registers this itself, but
// if its auto-registration fails silently (the common cause of the gear-
// button doing nothing) this guarantees the settings page still opens.
Pebble.addEventListener('showConfiguration', function () {
  if (clay && typeof clay.generateUrl === 'function') {
    Pebble.openURL(clay.generateUrl());
  } else {
    console.log('Clay unavailable -- cannot open settings.');
  }
});

// When the user closes the Clay settings webview, parse the response and
// forward each setting to the watch as an AppMessage. Clay does this
// automatically too, but doing it explicitly is harmless and protects
// against the same auto-registration failure as above.
Pebble.addEventListener('webviewclosed', function (e) {
  if (!e || !e.response) return;
  if (!clay || typeof clay.getSettings !== 'function') {
    console.log('Clay unavailable -- cannot parse settings.');
    return;
  }
  var settings;
  try {
    settings = clay.getSettings(e.response);
  } catch (err) {
    console.log('Failed to parse settings: ' + err);
    return;
  }
  Pebble.sendAppMessage(settings, function () {
    console.log('Settings forwarded to watch.');
  }, function (err) {
    console.log('Settings send error: ' + JSON.stringify(err));
  });

  // Refresh weather after settings change (cheap, keeps temp fresh).
  triggerLocationLookup();
});