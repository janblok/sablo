{
	"name": "mybutton",
	"displayName": "My Button",
	"definition": "mycomp/mybutton/mybutton.js",
	"model":
	{
		 "text" : "string",
         "itsenabled": "visible",
         "itsenabled2": { "type": "protected", "blockingOn": false, "default": true, "for": "onClick, onClick3" },
         "bla1": "int",
         "bla2": { "type": "int", "default": 5 }
	},
	"handlers":
	{
		"onClick" : "function",
		"onClick2" : "function",
		"onClick3" : "function"
	}
}