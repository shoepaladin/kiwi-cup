const Clay = require('pebble-clay');
const clayConfig = require('./config.json');
const clay = new Clay(clayConfig);

function fetchWeatherFromCoords(lat, lon) {
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true`;

  return fetch(url)
    .then((response) => response.json())
    .then((json) => {
      // Send raw Celsius (rounded) to the watch. The C side handles F/C
      // conversion based on the user's TemperatureUnit setting, so toggling
      // units doesn't require a new fetch.
      const tempCelsius = Math.round(json.current_weather.temperature);

      Pebble.sendAppMessage({
        'Temperature': tempCelsius
      }, () => {
        console.log('Weather telemetry sent (raw C): ' + tempCelsius);
      }, (err) => {
        console.log('Error sending payload to watch: ' + JSON.stringify(err));
      });
    })
    .catch((err) => console.log('Open-Meteo request failed: ' + err));
}

function triggerLocationLookup() {
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      fetchWeatherFromCoords(pos.coords.latitude, pos.coords.longitude);
    },
    (err) => {
      console.log('GPS unavailable; falling back to Seattle/Redmond.');
      fetchWeatherFromCoords(47.674, -122.121);
    },
    { timeout: 15000, maximumAge: 600000 }
  );
}

Pebble.addEventListener('ready', () => {
  triggerLocationLookup();
});

// When Clay settings change (background color, unit, etc.), Clay forwards
// them to the watch automatically. We don't need to refetch on every change,
// but a new fetch is cheap and keeps the displayed temp fresh.
Pebble.addEventListener('webviewclosed', () => {
  triggerLocationLookup();
});