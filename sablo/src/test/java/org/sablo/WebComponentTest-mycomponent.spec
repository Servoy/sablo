{
	"name": "mycomponent",
	"displayName": "My Component",
	"definition": "mycomponent.js",
	"libraries": [],
	"model":
	{
	        "background": { "type": "color", "pushToServer": "allow" },
	        "size": { "type": "dimension", "pushToServer": "allow" },
	        "location": { "type": "point", "pushToServer": "allow" },
	        "font": { "type": "font", "pushToServer": "allow" },
	        "atype": { "type": "mytype", "pushToServer": "allow" },
	        "types": { "type": "mytype[]", "pushToServer": "allow" },
	        "atypeReject": "mytype",
	        "typesReject": "mytype[]",
	        "simpleArrayReject": { "type": "int[]" },
	        "simpleArrayAllow": { "type": "int[]", "pushToServer": "allow" },
	        "nochangeint1" : "int",
	        "nochangeint2" : { "type": "int" },
	        "nochangeint3" : { "type": "int", "pushToServer": "reject" },
	        "changeintallow" : { "type": "int", "pushToServer": "allow" },
	        "changeintshallow" : { "type": "int", "pushToServer": "shallow" },
	        "changeintdeep" : { "type": "int", "pushToServer": "deep" }
	},
	"types": {
	  "mytype": {
		"name": "string",
		"text": "string",
		"active": "boolean",
		"foreground": "color",
		"size": "dimension",
		"mnemonic": "string",
		"subtypearray": "mysubtype[]"
	  },
	  "mysubtype": {
		"caption": "string",
		"in_date": "date"
	  }
	}
} 
