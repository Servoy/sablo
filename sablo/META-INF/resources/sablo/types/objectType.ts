angular.module('object_property', ['webSocketModule'])
// object type / default conversions -------------------------------------------
.run(function ($typesRegistry: sablo.ITypesRegistryForSabloConverters, $sabloConverters: sablo.ISabloConverters) {
	$typesRegistry.registerGlobalType('object', new ObjectType($typesRegistry, $sabloConverters), false);
})

class ObjectType implements sablo.IType<any> {
	
	constructor(private readonly typesRegistry: sablo.ITypesRegistryForSabloConverters, private readonly sabloConverters: sablo.ISabloConverters) {}
	
	fromServerToClient(serverJSONValue: any, currentClientValue: any, scope: angular.IScope, propertyContext: sablo.IPropertyContext): any {
		// this means that it's either a property with defined 'object' type (with any value) or the result of a server-side
		// default conversion which is a JSON array or a JSON object that has a nested value with conversion(s); so convert any nested values that need it
		if (serverJSONValue instanceof Object) { // arrays are objects as well
			if (serverJSONValue[this.sabloConverters.CONVERSION_CL_SIDE_TYPE_KEY] != undefined) {
				// it's already another type; for example a Date; convert it directly
				serverJSONValue = this.sabloConverters.convertFromServerToClient(serverJSONValue, undefined,
						currentClientValue, undefined, undefined, scope, propertyContext);
			} else {
				// see which nested sub-property/sub-element
				for (const i in serverJSONValue) // works for both arrays (indexes) and objects (keys) in JS
					if (serverJSONValue[i] instanceof Object && serverJSONValue[i][this.sabloConverters.CONVERSION_CL_SIDE_TYPE_KEY] != undefined)
						serverJSONValue[i] = this.sabloConverters.convertFromServerToClient(serverJSONValue[i], undefined,
								currentClientValue ? currentClientValue[i] : undefined, undefined, undefined, scope, propertyContext);
			}
		}
		
		return serverJSONValue;
	}

	fromClientToServer(newClientData: any, oldClientData: any, scope: angular.IScope, propertyContext: sablo.IPropertyContext): any {
		let retVal = newClientData;
		
		// default conversion to server (for date values)
		if (newClientData instanceof Date) {
			const dateType = this.typesRegistry.getAlreadyRegisteredType("Date");
			
			if (dateType) {
				// as this is an object type write also the type of date being sent; this works in 'object' type similarly to how dynamic client side types are received from server but the other way around
				retVal = {};
				retVal[this.sabloConverters.CONVERSION_CL_SIDE_TYPE_KEY] = "date"; // this is the server side name for DatePropertyType
				retVal[this.sabloConverters.VALUE_KEY] = this.sabloConverters.convertFromClientToServer(newClientData,
				        		 dateType , oldClientData, scope, propertyContext)
			}
		} /* TODO if needed (like if we want dates in nested objects/arrays of prop. type 'object' to also be sent properly to server): the code below is not supported by server-side code currently (JSONObject / JSONArray that it will become on server are not currently converted to java maps/lists in 'object' type)
		    else if (newClientData instanceof Object) {
			for (const i in newClientData) { // works for both arrays (indexes) and objects (keys) in JS
				const oldEl = newClientData[i];
				const newEl = this.fromClientToServer(oldEl, oldClientData ? oldClientData[i] : undefined, scope, propertyContext);
				if (oldEl !== newEl) try { newClientData[i] = newEl; } catch (e) {} // just to not re-assign it if not needed as the very broad "Object" detection above can be anything (even with restricted access to js members) not just simple objects or arrays; in which case child els/props won't change most likely
			}
		}*/
		
		return retVal;
	}
	
	updateAngularScope(clientValue: any, componentScope: angular.IScope): void {
		// nothing to do here
	}
	
}