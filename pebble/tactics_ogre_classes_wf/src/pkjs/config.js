// Class options - shared across all 3 rows
var CLASS_OPTIONS = [
  { "label": "Rune Fencer",   "value": "0" },
  { "label": "Barbarian",     "value": "1" },
  { "label": "Warrior",       "value": "2" },
  { "label": "Knight",        "value": "3" },
  { "label": "Archer",        "value": "4" },
  { "label": "Wizard",        "value": "5" },
  { "label": "Cleric",        "value": "6" },
  { "label": "Swordmaster",   "value": "7" },
  { "label": "Terror Knight", "value": "8" },
  { "label": "Warlock/Witch", "value": "9" },
  { "label": "Necromancer",   "value": "10" },
  { "label": "Deneb",         "value": "11" },
  { "label": "Ravness",       "value": "12" },
  { "label": "Diego",         "value": "13" },
  { "label": "Canopus",       "value": "14" }
];

var GENDER_OPTIONS = [
  { "label": "Female", "value": "0" },
  { "label": "Male",   "value": "1" }
];

var STAT_OPTIONS = [
  { "label": "12-hour Clock",   "value": "0" },
  { "label": "24-hour Clock",   "value": "1" },
  { "label": "Date",            "value": "2" },
  { "label": "Bluetooth",       "value": "3" },
  { "label": "Steps",           "value": "4" },
  { "label": "Battery %",       "value": "5" },
  { "label": "Distance",        "value": "6" },
  { "label": "Calories",        "value": "7" },
  { "label": "Weather/Temp",    "value": "8" },
  { "label": "Heart Rate",      "value": "9" }
];

module.exports = [
  {
    "type": "heading",
    "defaultValue": "Tactics Ogre Watchface"
  },
  {
    "type": "text",
    "defaultValue": "Configure each row's sprite and data display."
  },

  // ─── Row 0 (Top) ──────────────────────────────────────
  {
    "type": "section",
    "items": [
      { "type": "heading", "defaultValue": "Top Row", "size": 4 },
      {
        "type": "select",
        "messageKey": "Row0Class",
        "label": "Class",
        "defaultValue": "0",
        "options": CLASS_OPTIONS
      },
      {
        "type": "select",
        "messageKey": "Row0Gender",
        "label": "Gender",
        "defaultValue": "0",
        "options": GENDER_OPTIONS
      },
      {
        "type": "select",
        "messageKey": "Row0Stat",
        "label": "Display",
        "defaultValue": "0",
        "options": STAT_OPTIONS
      }
    ]
  },

  // ─── Row 1 (Middle) ───────────────────────────────────
  {
    "type": "section",
    "items": [
      { "type": "heading", "defaultValue": "Middle Row", "size": 4 },
      {
        "type": "select",
        "messageKey": "Row1Class",
        "label": "Class",
        "defaultValue": "1",
        "options": CLASS_OPTIONS
      },
      {
        "type": "select",
        "messageKey": "Row1Gender",
        "label": "Gender",
        "defaultValue": "1",
        "options": GENDER_OPTIONS
      },
      {
        "type": "select",
        "messageKey": "Row1Stat",
        "label": "Display",
        "defaultValue": "8",
        "options": STAT_OPTIONS
      }
    ]
  },

  // ─── Row 2 (Bottom) ───────────────────────────────────
  {
    "type": "section",
    "items": [
      { "type": "heading", "defaultValue": "Bottom Row", "size": 4 },
      {
        "type": "select",
        "messageKey": "Row2Class",
        "label": "Class",
        "defaultValue": "4",
        "options": CLASS_OPTIONS
      },
      {
        "type": "select",
        "messageKey": "Row2Gender",
        "label": "Gender",
        "defaultValue": "0",
        "options": GENDER_OPTIONS
      },
      {
        "type": "select",
        "messageKey": "Row2Stat",
        "label": "Display",
        "defaultValue": "5",
        "options": STAT_OPTIONS
      }
    ]
  },

  // ─── Global Units ─────────────────────────────────────
  {
    "type": "section",
    "items": [
      { "type": "heading", "defaultValue": "Units", "size": 4 },
      {
        "type": "select",
        "messageKey": "TempUnit",
        "label": "Temperature",
        "defaultValue": "0",
        "options": [
          { "label": "Celsius (°C)",    "value": "0" },
          { "label": "Fahrenheit (°F)", "value": "1" }
        ]
      },
      {
        "type": "select",
        "messageKey": "DistUnit",
        "label": "Distance",
        "defaultValue": "0",
        "options": [
          { "label": "Kilometers", "value": "0" },
          { "label": "Miles",      "value": "1" }
        ]
      }
    ]
  },

  {
    "type": "submit",
    "defaultValue": "Save Settings"
  }
];
