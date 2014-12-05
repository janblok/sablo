{
	"name": "mybutton",
	"displayName": "My Button",
	"definition": "mycomp/mybutton/mybutton.js",
	"model":
	{
		 "text" : "string",
         "itsenabled": "enable",
         "itsenabled2": { "type": "enable", "for": "onClick, onClick3" },
         "txtro": { "type": "readonly", "for": "text" },
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