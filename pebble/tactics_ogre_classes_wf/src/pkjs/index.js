var Clay = require('pebble-clay');
var clayConfig = require('./config');
var clay = new Clay(clayConfig);

// ─── Weather ─────────────────────────────────────────────────────────
// Uses Open-Meteo (no API key required). Sends the current temperature to
// the watch as an integer in Celsius under the WeatherTemp message key; the
// watch converts to Fahrenheit itself based on the user's unit setting.
//
// The watch drives the schedule: it sends WeatherRequest on launch, when the
// phone reconnects, and every 30 minutes. PebbleKit JS is suspended and
// restarted at the phone's discretion, so a setInterval here is only a
// backstop — it cannot be relied on to keep firing.

var MAX_ATTEMPTS = 3;
var RETRY_DELAY_MS = 10000;
var fetchInFlight = false;

function sendWeatherTemp(tempC, attempt) {
  var rounded = Math.round(tempC);
  Pebble.sendAppMessage(
    { WeatherTemp: rounded },
    function() {
      console.log('Weather sent: ' + rounded + 'C');
    },
    function(e) {
      console.log('Weather send failed: ' + JSON.stringify(e));
      if (attempt < MAX_ATTEMPTS) {
        setTimeout(function() { sendWeatherTemp(tempC, attempt + 1); }, RETRY_DELAY_MS);
      }
    }
  );
}

function fetchWeather(lat, lon, attempt) {
  var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat +
            '&longitude=' + lon + '&current=temperature_2m';

  function retryOrGiveUp(reason) {
    console.log('Weather: ' + reason);
    if (attempt < MAX_ATTEMPTS) {
      setTimeout(function() { fetchWeather(lat, lon, attempt + 1); }, RETRY_DELAY_MS);
    } else {
      fetchInFlight = false;
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
      sendWeatherTemp(data.current.temperature_2m, 1);
    } else {
      retryOrGiveUp('unexpected response');
    }
  };
  req.onerror = function() { retryOrGiveUp('request error'); };
  req.ontimeout = function() { retryOrGiveUp('request timed out'); };
  req.send();
}

function updateWeather() {
  if (fetchInFlight) {
    console.log('Weather: fetch already in flight, skipping');
    return;
  }
  fetchInFlight = true;
  navigator.geolocation.getCurrentPosition(
    function(pos) {
      fetchWeather(pos.coords.latitude, pos.coords.longitude, 1);
    },
    function(err) {
      fetchInFlight = false;
      console.log('Location error: ' + err.message);
    },
    { timeout: 15000, maximumAge: 60000 }
  );
}

Pebble.addEventListener('ready', function() {
  console.log('PebbleKit JS ready');
  updateWeather();
  // Backstop only — the watch's WeatherRequest is the primary trigger.
  setInterval(updateWeather, 30 * 60 * 1000);
});

Pebble.addEventListener('appmessage', function(e) {
  if (e.payload && e.payload.WeatherRequest) {
    console.log('Weather requested by watch');
    updateWeather();
  }
});
