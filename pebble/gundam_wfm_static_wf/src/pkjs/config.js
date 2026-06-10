module.exports = [
  {
    "type": "heading",
    "defaultValue": "Gundam Watchface"
  },

  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Sprite Set",
        "size": 4
      },
      {
        "type": "radiogroup",
        "messageKey": "SpriteSet",
        "label": "Gundam",
        "defaultValue": "0",
        "options": [
          { "label": "Aerial",    "value": "0" },
          { "label": "Calibarn",  "value": "1" },
          { "label": "GP02A",     "value": "2" },
         //{ "label": "Gundam Xi", "value": "3" },
          { "label": "Qubeley",   "value": "4" },
          { "label": "Byarlant",  "value": "5" }
        ]
      }
    ]
  },
  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Time & Date",
        "size": 4
      },
      {
        "type": "radiogroup",
        "messageKey": "TimeFormat",
        "label": "Time Format",
        "defaultValue": "0",
        "options": [
          { "label": "Follow watch system setting", "value": "0" },
          { "label": "24-hour (14:30)",             "value": "1" },
          { "label": "12-hour (2:30)",              "value": "2" }
        ]
      },
      {
        "type": "radiogroup",
        "messageKey": "DateFormat",
        "label": "Date Format",
        "defaultValue": "0",
        "options": [
          { "label": "MON JAN 12",        "value": "0" },
          { "label": "2026-01-12 (ISO)",  "value": "1" },
          { "label": "01/12/2026 (US)",   "value": "2" },
          { "label": "12/01/2026 (EU)",   "value": "3" },
          { "label": "Monday, Jan 12",    "value": "4" }
        ]
      }
    ]
  },
  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Appearance",
        "size": 4
      },
      {
        "type": "color",
        "messageKey": "BackgroundColor",
        "label": "Background Color",
        "description": "Text color (and the low-battery rings) automatically switches to black or white to stay legible against this background.",
        "defaultValue": "0x000000",
        "sunlight": true,
        "allowGray": true
      }
    ]
  },

  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Battery & Steps",
        "size": 4
      },
      {
        "type": "input",
        "messageKey": "LowBatteryThreshold",
        "label": "Low-battery threshold (%)",
        "description": "Two concentric rings appear when battery is at or below this percent. Enter a whole number from 1 to 99.",
        "defaultValue": "20",
        "attributes": {
          "type": "number",
          "min": 1,
          "max": 99,
          "step": 1,
          "placeholder": "20"
        }
      },
      {
        "type": "input",
        "messageKey": "StepGoal",
        "label": "Daily step goal",
        "description": "Vertical tick marks radiate from the center like clock hands, filling clockwise as you hit your goal. Set to 0 to hide.",
        "defaultValue": "10000",
        "attributes": {
          "type": "number",
          "min": 0,
          "max": 100000,
          "step": 500,
          "placeholder": "10000"
        }
      },
      {
        "type": "toggle",
        "messageKey": "ShowSteps",
        "label": "Show Step Count",
        "defaultValue": true
      }
    ]
  },
  {
    "type": "submit",
    "defaultValue": "Save Settings"
  }
];
