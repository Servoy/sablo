/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('custom_json_object_property', ['webSocketModule', '$typesRegistry'])
// CustomJSONObject type ------------------------------------------
.run(function ($sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $typesRegistry: sablo.ITypesRegistryForTypeFactories, $log: sablo.ILogService) {
	const TYPE_FACTORY_NAME = 'JSON_obj';
	$typesRegistry.getTypeFactoryRegistry().contributeTypeFactory(TYPE_FACTORY_NAME, new sablo.propertyTypes.CustomObjectTypeFactory($typesRegistry, $log, $sabloConverters, $sabloUtils));
});


namespace sablo.propertyTypes {

	export class CustomObjectTypeFactory implements sablo.ITypeFactory<CustomObjectValue> {

		private customTypesBySpecName: {
			[webObjectSpecName: string]: {
				[customTypeName: string]: CustomObjectType
			}
		} = {};

		constructor(private readonly typesRegistry: sablo.ITypesRegistryForTypeFactories,
				private readonly logger: sablo.ILogService,
				private readonly sabloConverters: sablo.ISabloConverters,
				private readonly sabloUtils: sablo.ISabloUtils) {}

		getOrCreateSpecificType(specificTypeInfo: string, webObjectSpecName: string): CustomObjectType {
			const customTypesForThisSpec = this.customTypesBySpecName[webObjectSpecName];
			if (customTypesForThisSpec) {
				const coType = customTypesForThisSpec[specificTypeInfo];
				if (!coType) this.logger.error("[CustomObjectTypeFactory] cannot find custom object client side type '" + specificTypeInfo + "' for spec '" + webObjectSpecName + "'; no such custom object type was registered for that spec.; ignoring...");
				return coType;
			} else {
				this.logger.error("[CustomObjectTypeFactory] cannot find custom object client side type '" + specificTypeInfo + "' for spec '" + webObjectSpecName + "'; that spec. didn't register any client side types; ignoring...");
				return undefined;
			}
		}

		registerDetails(details: sablo.typesRegistry.ICustomTypesFromServer, webObjectSpecName: string) {
			// ok we got the custom types section of a .spec file in details; do it similarly to what we do server-side:
			//   - first create empty shells for all custom types (because they might need to reference each other)
			//   - go through each custom type's sub-properties and assign the correct type to them (could be one of the previously created "empty shells")
			
			this.customTypesBySpecName[webObjectSpecName] = {};
			const customTypesForThisSpec = this.customTypesBySpecName[webObjectSpecName];
			
			// create empty CustomObjectType instances for all custom types in this .spec
			for (const customTypeName in details) {
				customTypesForThisSpec[customTypeName] = new CustomObjectType(this.sabloConverters, this.sabloUtils); // create just an empty type reference that will be populated below with child property types
			}

			// set the sub-properties of each CustomObjectType to the correct IType
			for (const customTypeName in details) {
				const customTypeDetails = details[customTypeName];
				const properties: { [propName: string]: sablo.IPropertyDescription } = {};
				for (const propertyName in customTypeDetails) {
					properties[propertyName] = this.typesRegistry.processPropertyDescriptionFromServer(customTypeDetails[propertyName], webObjectSpecName);
				}
				customTypesForThisSpec[customTypeName].setPropertyDescriptions(properties);
			}
		}

	}

    export function isCustomObjectType(typeToCheck: sablo.IType<any>) { return typeToCheck instanceof CustomObjectType; }

	class CustomObjectType implements sablo.IType<CustomObjectValue> {

    	static readonly UPDATES = "u";
    	static readonly KEY = "k";
    	static readonly VALUE = "v";
    	static readonly CONTENT_VERSION = "vEr"; // server side sync to make sure we don't end up granular updating something that has changed meanwhile server-side
    	static readonly NO_OP = "n";
    	static readonly angularAutoAddedKeys = ["$$hashKey"];

    	private propertyDescriptions: { [propName: string]: sablo.IPropertyDescription };

		constructor(private readonly sabloConverters: sablo.ISabloConverters, private readonly sabloUtils: sablo.ISabloUtils) {}

		// this will always get called once with a non-null param before the CustomObjectType is used for conversions
		setPropertyDescriptions(propertyDescriptions: { [propertyName: string]: sablo.IPropertyDescription } ): void {
			this.propertyDescriptions = propertyDescriptions;
		}

		private getStaticPropertyType(propertyName: string) {
			return this.propertyDescriptions[propertyName]?.getPropertyType();
		}

		private getPropertyType(internalState, propertyName: string) {
			let propType = this.getStaticPropertyType(propertyName);
			if (!propType) propType = internalState.dynamicPropertyTypesHolder[propertyName];
			return propType;
		}

        fromServerToClient(serverJSONValue: any, currentClientValue: CustomObjectValue, componentScope: angular.IScope, propertyContext: sablo.IPropertyContext): CustomObjectValue {
			let newValue: CustomObjectValue = currentClientValue;

			// remove old watches and, at the end create new ones to avoid old watches getting triggered by server side change
			this.removeAllWatches(currentClientValue);

			try
			{
				if (serverJSONValue && serverJSONValue[CustomObjectType.VALUE]) {
					// full contents
					newValue = serverJSONValue[CustomObjectType.VALUE];
					this.initializeNewValue(newValue, serverJSONValue[CustomObjectType.CONTENT_VERSION], propertyContext?.getPushToServerCalculatedValue());
					const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];

					const propertyContextCreator = new sablo.typesRegistry.ChildPropertyContextCreator(
							this.getCustomObjectPropertyContextGetter(newValue, propertyContext),
							this.propertyDescriptions, propertyContext?.getPushToServerCalculatedValue(), propertyContext?.isInsideModel);

					for (const c in newValue) {
						let elem = newValue[c];

						// if it is a typed prop. use the type to convert it, if it is a dynamic type prop (for example dataprovider of type date) do the same and store the type
						newValue[c] = elem = this.sabloConverters.convertFromServerToClient(elem, this.getStaticPropertyType(c), currentClientValue ? currentClientValue[c] : undefined,
								internalState.dynamicPropertyTypesHolder, c, componentScope, propertyContextCreator.withPushToServerFor(c));

						if (elem && elem[this.sabloConverters.INTERNAL_IMPL] && elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
							// child is able to handle it's own change mechanism
							elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newValue, c));
						}
					}
				} else if (serverJSONValue && serverJSONValue[CustomObjectType.UPDATES]) {
					// granular updates received;
					const internalState = currentClientValue[this.sabloConverters.INTERNAL_IMPL];

                    internalState.calculatedPushToServerOfWholeProp = propertyContext?.getPushToServerCalculatedValue(); // for example if a custom object value is initially received through a return value from server side api/handler call and not as a normal model property and then the component/service assigns it to model, the push to server of it might have changed; use the one received here as arg
		            if (!internalState.calculatedPushToServerOfWholeProp) internalState.calculatedPushToServerOfWholeProp = sablo.typesRegistry.PushToServerEnum.reject;

					// if something changed browser-side, increasing the content version thus not matching next expected version,
					// we ignore this update and expect a fresh full copy of the object from the server (currently server value is leading/has priority because not all server side values might support being recreated from client values)
					if (internalState[CustomObjectType.CONTENT_VERSION] <= serverJSONValue[CustomObjectType.CONTENT_VERSION]) {
                        internalState[CustomObjectType.CONTENT_VERSION] = serverJSONValue[CustomObjectType.CONTENT_VERSION]; // if we assume that versions are in sync even if it is < not just = (workaround for SVYX-431), update it client side so the any change sent to server works (normally it should always be === here)

						const updates = serverJSONValue[CustomObjectType.UPDATES];

						const propertyContextCreator = new sablo.typesRegistry.ChildPropertyContextCreator(
								this.getCustomObjectPropertyContextGetter(currentClientValue, propertyContext),
								this.propertyDescriptions, propertyContext?.getPushToServerCalculatedValue(), propertyContext?.isInsideModel);

						for (const i in updates) {
							const update = updates[i];
							const key = update[CustomObjectType.KEY];
							let val = update[CustomObjectType.VALUE];

							currentClientValue[key] = val = this.sabloConverters.convertFromServerToClient(val, this.getStaticPropertyType(key), currentClientValue[key],
									internalState.dynamicPropertyTypesHolder, key, componentScope, propertyContextCreator.withPushToServerFor(key));

							if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								// child is able to handle it's own change mechanism
								val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, key));
							}
						}
					}
					//else {
					  // else we got an update from server for a version that was already bumped by changes in browser; ignore that, as browser changes were sent to server
					  // and server will detect the problem and send back a full update
					//}
				} else if (!serverJSONValue || !serverJSONValue[CustomObjectType.NO_OP]) newValue = null; // anything else would not be supported...	// TODO how to handle null values (special watches/complete object set from client)? if null is on server and something is set on client or the other way around?
			} finally {
				// add back watches if needed
				this.addBackWatches(newValue, componentScope);
			}

			return newValue;
        }

        fromClientToServer(newClientData: any, oldClientData: CustomObjectValue, scope: angular.IScope, propertyContext: sablo.IPropertyContext): any {
        	let internalState: any;

        	if (newClientData) {
                if (!(internalState = newClientData[this.sabloConverters.INTERNAL_IMPL])) {
                    if (oldClientData && oldClientData[this.sabloConverters.INTERNAL_IMPL]) this.removeAllWatches(oldClientData);
            		// this can happen when a new obj. value was set completely in browser
            		// any 'smart' child elements will initialize in their fromClientToServer conversion;
            		// set it up, make it 'smart' and mark it as all changed to be sent to server...
            		this.initializeNewValue(newClientData, 1, propertyContext?.getPushToServerCalculatedValue());

            		internalState = newClientData[this.sabloConverters.INTERNAL_IMPL];

            		// mark it to be fully sent to server as well below
    				if (propertyContext?.isInsideModel) internalState.allChanged = true;
                } else if (propertyContext?.isInsideModel && newClientData !== oldClientData) {
                    // the conversion happens for a value from the model (not handler/api arg or return value); and we see it as a change by ref

                    if (oldClientData && oldClientData[this.sabloConverters.INTERNAL_IMPL]) this.removeAllWatches(oldClientData);
                    // if a different smart value from the browser is assigned to replace old value it is a full value change; also adjust the version to it's new location

                    // clear old internal state and watches in order to re-initialize/start fresh in the new location (old watches would send change notif to wrong place)
                    // we only need from the old internal state the dynamic types
                    const previousNewValDynamicTypesHolder = internalState.dynamicPropertyTypesHolder;
                    this.removeAllWatches(newClientData);
                    delete newClientData[this.sabloConverters.INTERNAL_IMPL];

                    this.initializeNewValue(newClientData, 1, propertyContext?.getPushToServerCalculatedValue());
                    internalState = newClientData[this.sabloConverters.INTERNAL_IMPL];

                    if (previousNewValDynamicTypesHolder) internalState.dynamicPropertyTypesHolder = previousNewValDynamicTypesHolder;
                    internalState.allChanged = true;
                } else internalState = newClientData[this.sabloConverters.INTERNAL_IMPL]; // an already initialized value that is either the same value as before or it is used here as an argument or return value to api calls/handlers
        	}

			if (newClientData) {
                let calculatedPushToServerOfWholeProp: typesRegistry.PushToServerEnum; 
                if (propertyContext.isInsideModel) {
                    internalState.calculatedPushToServerOfWholeProp = (typeof propertyContext?.getPushToServerCalculatedValue() != 'undefined' ? propertyContext?.getPushToServerCalculatedValue() : sablo.typesRegistry.PushToServerEnum.reject);
                    calculatedPushToServerOfWholeProp = internalState.calculatedPushToServerOfWholeProp;
                } else calculatedPushToServerOfWholeProp = sablo.typesRegistry.PushToServerEnum.allow; // args/return values are always "allow"

    			const propertyContextCreator = new sablo.typesRegistry.ChildPropertyContextCreator(
    					this.getCustomObjectPropertyContextGetter(newClientData, propertyContext),
    					this.propertyDescriptions, propertyContext?.getPushToServerCalculatedValue(), propertyContext?.isInsideModel);
				if (!propertyContext?.isInsideModel || internalState.isChanged()) { // so either it has changes or it's used as an arg/return value to a handler/api call
					const changes = {};
					if (!propertyContext?.isInsideModel || internalState.allChanged) { // fully changed or arg/return value of handler/api call
						// send all

                        // we can't rely/use the contentVersion on ng2 impl. - and this means ng1 and server are adjusted as well, because, in case of a change-by-reference in an ng2 service followed
                        // by a now deprecated ServoyPublicService.sendServiceChanges that did not have an oldPropertyValue argument, we sometimes do not have
                        // access to the old contentVersion to be able to use it... so full change from client will ignore old contentVersion on client and on server
                        // but that should not be a problem as those are meant more to ensure that granular updates don't happen on an wrong/obsolete value
                        changes[CustomObjectType.CONTENT_VERSION] = 0; // server treats this as a "don't check server content version as it's a full new value from client"
                        
                        // we only reset client side contentVersion when sending full changes for model properties (so things that might have an equivalent on server);
                        // (args and return values to api/handlers should not reset client side state version when being sent to server (there they will be
                        // full new values anyway with no previous value - and not in sync with any client side value), because it is possible to send as argument or
                        // return value a value that is also present in the model at the same time, in which case this full send as an arg/return value should not
                        // alter client side version in the model - that version must remain unaltered, in sync with the server side version in the model)
                        if (propertyContext?.isInsideModel) internalState[CustomObjectType.CONTENT_VERSION] = 1; // start fresh
                        
						const toBeSentObj = changes[CustomObjectType.VALUE] = {};
						for (const key in newClientData) {
							if (CustomObjectType.angularAutoAddedKeys.indexOf(key) !== -1) continue;

							const val = newClientData[key];

                            // even if child value has only partial changes or no changes, do send the full subprop. value as we are sending full object value here
                            // that is, if this conversion is sending model values; otherwise (handler/api call arg/return values) it will always be sent fully anyway
                            // this is a bit of a hack because .allChanged might mean something or not for the "val"'s internal state - depending on what type it is; titanium ng2 code is a bit better here
                            if (val && val[this.sabloConverters.INTERNAL_IMPL] && propertyContext?.isInsideModel) val[this.sabloConverters.INTERNAL_IMPL].allChanged = true;

							toBeSentObj[key] = this.sabloConverters.convertFromClientToServer(val, this.getPropertyType(internalState, key),
							     oldClientData ? oldClientData[key] : undefined, scope, propertyContextCreator.withPushToServerFor(key));
                            // TODO although this is a full change, we give oldClientData[key] (oldvalue) because server side does the same for some reason,
                            // but normally both should use undefined/null for old value of subprops as this is a full change; SVY-17854 is created for looking into this 

							// if it's a nested obj/array or other smart prop that just got smart in convertFromClientToServer, attach the change notifier
                            if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
                                val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newClientData, key));

							if (sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(calculatedPushToServerOfWholeProp, this.propertyDescriptions[key]?.getPropertyDeclaredPushToServer())
							     === sablo.typesRegistry.PushToServerEnum.reject) delete toBeSentObj[key]; // don't send to server pushToServer reject keys
						}

                        // now add watches/change notifiers to the new full value if needed (now all children are converted and made smart themselves if necessary so addBackWatches can make the distinction between smart and dumb correctly)
                        this.addBackWatches(newClientData, scope);

                        if (propertyContext?.isInsideModel) {
    						internalState.allChanged = false;
    						internalState.changedKeysOldValues = {};
						}

						if (calculatedPushToServerOfWholeProp === sablo.typesRegistry.PushToServerEnum.reject)
						{
                            // if whole value is reject, don't sent anything
                            const x = {}; // no changes
                            x[CustomObjectType.NO_OP] = true;
                            return x;
                        }
						else return changes;
					} else {
						// send only changed keys
                        changes[CustomObjectType.CONTENT_VERSION] = internalState[CustomObjectType.CONTENT_VERSION];
						const changedElements = changes[CustomObjectType.UPDATES] = [];
						for (const key in internalState.changedKeysOldValues) {
							const newVal = newClientData[key];
							const oldVal = internalState.changedKeysOldValues[key];

							let changed = (newVal !== oldVal);
							if (!changed) {
								if (!internalState.elUnwatch || !internalState.elUnwatch[key]) {
									changed = newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].isChanged && newVal[this.sabloConverters.INTERNAL_IMPL].isChanged(); // must be smart value then; same reference as checked above; so ask it if it changed
								} // else it's a dumb value; but old and new are the same reference so there are no changes
							}

							if (changed) {
								const ch = {};
								ch[CustomObjectType.KEY] = key;

                                let wasSmartBeforeConversion = (newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier);
                                ch[CustomObjectType.VALUE] = this.sabloConverters.convertFromClientToServer(newVal, this.getPropertyType(internalState, key), oldVal, scope, propertyContextCreator.withPushToServerFor(key));
                                if (!wasSmartBeforeConversion && (newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier))
                                    newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newClientData, key)); // if it was a new object/array set in this key, which was initialized by convertFromClientToServer call above, do add the change notifier to it

								changedElements.push(ch);
							}
						}
						internalState.allChanged = false;
						internalState.changedKeysOldValues = {};
						return changes;
					}
				} else {
					const x = {}; // no changes
					x[CustomObjectType.NO_OP] = true;
					return x;
				}
			}

			return newClientData;
		}

        updateAngularScope(clientValue: CustomObjectValue, componentScope: angular.IScope): void {
            // it is possible here that the clientValue exists but it is not initialized (no internal state)
            // that can happen for example if properties in .spec are scope: private and pushToServer: reject (which is default pushToServer)
            // and those properties are only assigned on client - so they never go through either fromServerToClient nor fromClientToServer

			this.removeAllWatches(clientValue);

			if (clientValue) {
				const internalState = clientValue[this.sabloConverters.INTERNAL_IMPL];
				if (internalState) {
        			if (componentScope) this.addBackWatches(clientValue, componentScope);

					for (const key in clientValue) {
						if (CustomObjectType.angularAutoAddedKeys.indexOf(key) !== -1) continue;
						const propType = this.getPropertyType(internalState, key);
						if (propType) propType.updateAngularScope(clientValue[key], componentScope);
					}
				}
			}
        }

        // ------------------------------------------------------------------------------

    	private getChangeNotifier(propertyValue: any, key: string): () => void {
    		return () => {
    			const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
    			internalState.changedKeysOldValues[key] = propertyValue[key];
    			if (internalState.notifier) internalState.notifier();
    		}
    	}

    	private watchDumbElementForChanges(propertyValue: CustomObjectValue, key: string, componentScope: angular.IScope, deep: boolean): () => void  {
    		// if elements are primitives or anyway not something that wants control over changes, just add an in-depth watch
    		return componentScope.$watch(function() {
    			return propertyValue[key];
    		}, (newvalue, oldvalue) => {
    			if (oldvalue === newvalue) return;

    			const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
    			internalState.changedKeysOldValues[key] = oldvalue;
    			if (internalState.notifier) internalState.notifier();
    		}, deep);
    	}

    	/** Initializes internal state on a new object value */
    	private initializeNewValue(newValue: any, contentVersion: number, pushToServerCalculatedValue: sablo.IPushToServerEnum): void {
    		this.sabloConverters.prepareInternalState(newValue);

    		const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
    		internalState[CustomObjectType.CONTENT_VERSION] = contentVersion; // being full content updates, we don't care about the version, we just accept it
			internalState.calculatedPushToServerOfWholeProp = (typeof pushToServerCalculatedValue != 'undefined' ? pushToServerCalculatedValue : sablo.typesRegistry.PushToServerEnum.reject);

			// implement what $sabloConverters need to make this work
			internalState.setChangeNotifier = function(changeNotifier: () => void) {
				internalState.notifier = changeNotifier; 
			}
			internalState.isChanged = function() {
				let hasChanges = internalState.allChanged;
				if (!hasChanges) for (let x in internalState.changedKeysOldValues) { hasChanges = true; break; }
				return hasChanges;
			}

			// private impl
			internalState.modelUnwatch = [];
			internalState.objStructureUnwatch = null;
			internalState.changedKeysOldValues = {};
			internalState.allChanged = false;
			internalState.dynamicPropertyTypesHolder = {};
    	}

    	private removeAllWatches(value: CustomObjectValue): void {
    		if (value != null && angular.isDefined(value)) {
    			const iS = value[this.sabloConverters.INTERNAL_IMPL];
    			if (iS != null && angular.isDefined(iS)) {
    				if (iS.objStructureUnwatch) iS.objStructureUnwatch();
    				for (const key in iS.elUnwatch) {
    					iS.elUnwatch[key]();
    				}
    				iS.objStructureUnwatch = null;
    				iS.elUnwatch = null;
    			}
    		}
    	}

    	private addBackWatches(value: CustomObjectValue, componentScope: angular.IScope): void {
    		if (value) {
    			const internalState = value[this.sabloConverters.INTERNAL_IMPL];
    			internalState.elUnwatch = {};
    			
    			// add shallow/deep watches as needed
    			if (componentScope) {
    				
    				for (const c in value) {
    					const elem = value[c];
    	    			const childPropCalculatedPushToServer = sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(internalState.calculatedPushToServerOfWholeProp,
    							this.propertyDescriptions[c]?.getPropertyDeclaredPushToServer());

    					if ((!elem || !elem[this.sabloConverters.INTERNAL_IMPL] || !elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) && childPropCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
    						// watch the child's value to see if it changes
    						if (CustomObjectType.angularAutoAddedKeys.indexOf(c) === -1) internalState.elUnwatch[c] = this.watchDumbElementForChanges(value, c, componentScope, childPropCalculatedPushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true); // either deep or shallow because the if above checks that it is at least shallow
    					}
    				}

    				// watch for add/remove and such operations on object; this is helpful also when 'smart' child values (that have .setChangeNotifier)
    				// get changed completely by reference
    				internalState.objStructureUnwatch = componentScope.$watchCollection(function() { return value; }, (newWVal, oldWVal) => {
    					if (newWVal === oldWVal) return;

						// search for differences between properties of the old and new objects
						let changed = false;

						const tmp = [];
						let idx;
						for (const key in newWVal) if (CustomObjectType.angularAutoAddedKeys.indexOf(key) === -1) tmp.push(key);
						for (const key in oldWVal) {
							if (CustomObjectType.angularAutoAddedKeys.indexOf(key) !== -1) continue;
							const childPropCalculatedPushToServer = sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(internalState.calculatedPushToServerOfWholeProp,
	    							this.propertyDescriptions[key]?.getPropertyDeclaredPushToServer());

							if ((idx = tmp.indexOf(key)) != -1) {
								tmp.splice(idx, 1); // this will be dealt with here; remove it from tmp

								// key in both old and new; check for difference in value
								if (newWVal[key] !== oldWVal[key] && oldWVal[key] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
                                    oldWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(undefined);
									// some elements changed by reference; we only need to handle this for old smart element values,
									// as the others will be handled by the separate 'dumb' watches
									if (childPropCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
										changed = true;
										internalState.changedKeysOldValues[key] = oldWVal[key];
									}

									// if new value is smart as well we have to give it the according change notifier
									if (newWVal[key] && newWVal[key][this.sabloConverters.INTERNAL_IMPL] && newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
										newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newWVal, key));
								}
							} else {
								// old has a key that is no longer present in new one; for 'dumb' properties this will be handled by already added dumb watches;
								// so here we need to see if old value was smart, then we need to send updates to the server
								if (oldWVal[key] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier && childPropCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                                    oldWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(undefined);
									changed = true;
									internalState.changedKeysOldValues[key] = oldWVal[key];
								} else {
                                    // old was dumb and now it's there no more? remove dumb watch
                                    if (internalState.elUnwatch[key]) internalState.elUnwatch[key]();
                                    delete internalState.elUnwatch[key];
                                }
							}
						}
						// any keys left in tmp are keys that are in new value but are not in old value; handle those
						for (idx in tmp) {
							const key = tmp[idx];
							const childPropCalculatedPushToServer = sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(internalState.calculatedPushToServerOfWholeProp,
	    							this.propertyDescriptions[key]?.getPropertyDeclaredPushToServer());

							// so we were not previously aware of this new key; send it to server
							if (childPropCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
								changed = true;
								internalState.changedKeysOldValues[key] = undefined;
							}

							// if new value is smart we have to give it the according change notifier; if it's 'dumb' and it was not watched before add a 'dumb' watch on it
							if (newWVal[key] && newWVal[key][this.sabloConverters.INTERNAL_IMPL] && newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
								newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newWVal, key));
							else if (childPropCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) internalState.elUnwatch[key] = this.watchDumbElementForChanges(newWVal, key, componentScope, childPropCalculatedPushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true); // either deep or shallow because the if above checks that it is at least shallow
						}
						if (changed && internalState.notifier) internalState.notifier();
    				});
    			}
    		}
    	}

    	private getCustomObjectPropertyContextGetter(customObjectValue: CustomObjectValue, parentPropertyContext: sablo.IPropertyContext): sablo.IPropertyContextGetterMethod {
    		// property context that we pass here should search first in the current custom object value and only fallback to "parentPropertyContext" if needed
    		return function (propertyName) {
    			if (customObjectValue.hasOwnProperty(propertyName)) return customObjectValue[propertyName]; // can even be null or undefined as long as it is set on this object
    			else return parentPropertyContext ? parentPropertyContext.getProperty(propertyName) : undefined; // fall back to parent object nesting context
    		}
    	}

    	// ------------------------------------------------------
	}
	
	interface CustomObjectValue {

	}

}