// PebbleKit JS — phone side. Bootstraps Clay so the user can configure the
// watchface from the Pebble mobile app.

var Clay = require('pebble-clay');
var clayConfig = require('./config');
var clay = new Clay(clayConfig);
