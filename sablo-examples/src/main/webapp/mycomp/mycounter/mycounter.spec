{
	"name": "mycounter",
	"displayName": "My Counter",
	"definition": "mycomp/mycounter/mycounter.js",
	"model":
	{
		 "n" : "int"
	},
	"api":
	{
        "increment": {
            "parameters":[ {  "name":"inc", "type":"int" }  ],
            "returns": "int"
        }
	}
}