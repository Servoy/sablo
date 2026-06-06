{
	"name": "$sabloService",
	"displayName": "Sablo core service",
	"version": 1,
	"definition": "sablo/sabloService.js",
	"libraries": [],
	"model": {},
	"api": {
	 	 "setCurrentFormUrl": { "parameters": [ { "name": "url", "type": "string" } ] },
	     "getCurrentFormUrl": { "returns": "string" },
	     
	     "windowOpen": { "parameters":[
	     		{ "name": "url", "type": "string" },
	     		{ "name": "name", "type": "string", "optional" : true },
	     		{ "name": "specs", "type": "string", "optional" : true },
	     		{ "name": "replace", "type": "string", "optional": true } ]
	      }
	}
}
