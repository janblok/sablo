{
	"name": "$sabloService",
	"displayName": "Sablo core service",
	"version": 1,
	"definition": "services/sablo-core/sabloService.js",
	"libraries": [],
	"model": { },
	"api": {
	 	 "setCurrentFormUrl": { "parameters":[ { "name":"url", "type":"string" } ] },
	     "getCurrentFormUrl": { "returns": "string" }
	}
}
