{
	"name": "$typesRegistry",
	"displayName": "Sablo types registry service - for client side service/component specs",
	"version": 1,
	"definition": "sablo/js/types_registry.js",
	"libraries": [],
	"model": {},
	"api": {
	 	 "setCurrentFormUrl": { "parameters": [ { "name":"url", "type":"string" } ] },
	     "getCurrentFormUrl": { "returns": "string" },
	     
         "addComponentClientSideSpecs": { "parameters": [ { "name":"componentSpecificationsFromServer", "type": "object" } ] },
         "setServiceClientSideSpecs": { "parameters": [ { "name":"serviceSpecificationsFromServer", "type": "object" } ] }
	}
}
