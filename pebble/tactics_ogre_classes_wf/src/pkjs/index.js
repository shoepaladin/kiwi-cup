var Clay = require('pebble-clay');
var clayConfig = require('./config');
var clay = new Clay(clayConfig);

// ─── Weather ─────────────────────────────────────────────────────────
// Uses Open-Meteo (no API key required). Sends the current temperature to
// the watch as an integer in Celsius under the WeatherTemp message key; the
// watch converts to Fahrenheit itself based on the user's unit setting.

function sendWeatherTemp(tempC) {
  Pebble.sendAppMessage(
    { WeatherTemp: Math.round(tempC) },
    function() { console.log('Weather sent: ' + Math.round(tempC) + 'C'); },
    function(e) { console.log('Weather send failed: ' + JSON.stringify(e)); }
  );
}

function fetchWeather(lat, lon) {
  var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat +
            '&longitude=' + lon + '&current=temperature_2m';
  var req = new XMLHttpRequest();
  req.open('GET', url, true);
  req.onload = function() {
    try {
      var data = JSON.parse(req.responseText);
      if (data && data.current && typeof data.current.temperature_2m === 'number') {
        sendWeatherTemp(data.current.temperature_2m);
      } else {
        console.log('Weather: unexpected response');
      }
    } catch (err) {
      console.log('Weather parse error: ' + err);
    }
  };
  req.onerror = function() { console.log('Weather request error'); };
  req.send();
}

function updateWeather() {
  navigator.geolocation.getCurrentPosition(
    function(pos) { fetchWeather(pos.coords.latitude, pos.coords.longitude); },
    function(err) { console.log('Location error: ' + err.message); },
    { timeout: 15000, maximumAge: 60000 }
  );
}

Pebble.addEventListener('ready', function() {
  console.log('PebbleKit JS ready');
  updateWeather();
  // Refresh every 30 minutes while the phone connection is alive.
  setInterval(updateWeather, 30 * 60 * 1000);
});
