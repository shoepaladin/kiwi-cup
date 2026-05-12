module.exports = [
  {
    "type": "heading",
    "defaultValue": "Metal Gear Watch"
  },
  {
    "type": "text",
    "defaultValue": "Configure how Snake shuffles through Shadow Moses (or outdoor base) on your watchface."
  },

  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Disguise",
        "size": 3
      },
      {
        "type": "radiogroup",
        "messageKey": "disguise",
        "id": "disguise",
        "label": "Which box is Snake hiding in?",
        "defaultValue": "0",
        "options": [
          { "label": "Cardboard Box (MGS1)",      "value": "0" },
          { "label": "Supply Drop (MGSV)",        "value": "1" },
          { "label": "Oil Drum (MGS4)",           "value": "2" },
          { "label": "Double Love Box (Peace Walker)", "value": "3" }
        ]
      }
    ]
  },

  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Movement",
        "size": 3
      },
      {
        "type": "radiogroup",
        "messageKey": "traverseMs",
        "id": "traverseMs",
        "label": "Time for Snake to cross the screen",
        "defaultValue": "300000",
        "options": [
          { "label": "1 minute",   "value": "60000"  },
          { "label": "5 minutes",  "value": "300000" },
          { "label": "10 minutes", "value": "600000" }
        ]
      }
    ]
  },

  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Scene",
        "size": 3
      },
      {
        "type": "radiogroup",
        "messageKey": "sceneMode",
        "id": "sceneMode",
        "label": "Background scene",
        "defaultValue": "0",
        "options": [
          { "label": "Shadow Moses hallway", "value": "0" },
          { "label": "Outdoor base (night)", "value": "1" },
          { "label": "Auto by time (outdoor 8pm–6am)", "value": "2" }
        ]
      }
    ]
  },

  {
    "type": "section",
    "items": [
      {
        "type": "heading",
        "defaultValue": "Notifications",
        "size": 3
      },
      {
        "type": "toggle",
        "messageKey": "btVibrate",
        "id": "btVibrate",
        "label": "Vibrate on Bluetooth connect/disconnect",
        "description": "When off, the watch will not vibrate when the phone connection changes. The camera still turns red when disconnected either way.",
        "defaultValue": true
      }
    ]
  },

  {
    "type": "submit",
    "defaultValue": "Save"
  }
];
