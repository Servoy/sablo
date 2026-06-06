/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('custom_json_array_property', ['webSocketModule', '$typesRegistry'])
//CustomJSONArray type ------------------------------------------
.run(function ($sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $typesRegistry: sablo.ITypesRegistryForTypeFactories, $log: sablo.ILogService) {
    const TYPE_FACTORY_NAME = 'JSON_arr';
    $typesRegistry.getTypeFactoryRegistry().contributeTypeFactory(TYPE_FACTORY_NAME, new sablo.propertyTypes.CustomArrayTypeFactory($typesRegistry, $sabloConverters, $sabloUtils));
});


namespace sablo.propertyTypes {

    export class CustomArrayTypeFactory implements sablo.ITypeFactory<CustomArrayValue> {

        private customArrayTypes: Map<sablo.IType<any>, Map<IPushToServerEnum, CustomArrayType>> = new Map(); // allows any keys, even undefined

        constructor(private readonly typesRegistry: sablo.ITypesRegistryForTypeFactories,
                private readonly sabloConverters: sablo.ISabloConverters,
                private readonly sabloUtils: sablo.ISabloUtils) {}

        getOrCreateSpecificType(specificElementInfo: ITypeFromServer | { t: ITypeFromServer; s: PushToServerEnumValue }, webObjectSpecName: string): CustomArrayType {
            const elementTypeWithNoPushToServer = (specificElementInfo instanceof Array || typeof specificElementInfo == "string") || specificElementInfo === null;
            const elementTypeFromSrv: ITypeFromServer = (elementTypeWithNoPushToServer) ? specificElementInfo as ITypeFromServer : (specificElementInfo as { t: ITypeFromServer, s: PushToServerEnumValue }).t;
            const pushToServer: IPushToServerEnum = (elementTypeWithNoPushToServer) ? undefined : sablo.typesRegistry.PushToServerEnum.valueOf((specificElementInfo as { t: ITypeFromServer, s: PushToServerEnumValue }).s);

            let staticElementType = (elementTypeFromSrv ? this.typesRegistry.processTypeFromServer(elementTypeFromSrv, webObjectSpecName) : undefined); // a custom array could have an element type that is not a client side type; but it still needs to be an array type
            let cachedArraysByType = this.customArrayTypes.get(staticElementType);
            if (!cachedArraysByType) {
                cachedArraysByType = new Map();
                this.customArrayTypes.set(staticElementType, cachedArraysByType);
            }
            let cachedArraysByTypeAndPushToServerOnElem = cachedArraysByType.get(pushToServer);
            if (!cachedArraysByTypeAndPushToServerOnElem) {
                cachedArraysByTypeAndPushToServerOnElem = new CustomArrayType(staticElementType, pushToServer, this.sabloConverters, this.sabloUtils);
                cachedArraysByType.set(pushToServer, cachedArraysByTypeAndPushToServerOnElem);
            }

            return cachedArraysByTypeAndPushToServerOnElem;
        }

        registerDetails(details: sablo.typesRegistry.ICustomTypesFromServer, webObjectSpecName: string) {
            // arrays don't need to pre-register stuff
        }

    }
    
    export function isCustomArrayType(typeToCheck: sablo.IType<any>) { return typeToCheck instanceof CustomArrayType; }

    class CustomArrayType implements sablo.IType<CustomArrayValue> {
        static readonly GRANULAR_UPDATES = "g";
        static readonly GRANULAR_UPDATE_DATA = "d";
        static readonly OP_ARRAY_START_END_TYPE = "op";
        static readonly CHANGED = 0;
        static readonly INSERT = 1;
        static readonly DELETE = 2;    

        static readonly UPDATES = "u";
        static readonly INDEX = "i";
        static readonly VALUE = "v";
        static readonly CONTENT_VERSION = "vEr"; // server side sync to make sure we don't end up granular updating something that has changed meanwhile server-side
        static readonly NO_OP = "n";

        constructor(private readonly staticElementType: sablo.IType<any>,
                private readonly pushToServerForElements: IPushToServerEnum,
                private readonly sabloConverters: sablo.ISabloConverters, private readonly sabloUtils: sablo.ISabloUtils) {}

        fromServerToClient(serverJSONValue: any, currentClientValue: CustomArrayValue, componentScope: angular.IScope, propertyContext: sablo.IPropertyContext): CustomArrayValue {
            let newValue = currentClientValue;

            const elemPropertyContext = propertyContext ? new sablo.typesRegistry.PropertyContext(propertyContext.getProperty,
                    sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(propertyContext.getPushToServerCalculatedValue(), this.pushToServerForElements),
                    propertyContext.isInsideModel) : undefined;
            // remove old watches (and, at the end create new ones) to avoid old watches getting triggered by server side change
            this.removeAllWatches(currentClientValue);

            try
            {
                if (serverJSONValue && serverJSONValue[CustomArrayType.VALUE]) {
                    // full contents
                    newValue = serverJSONValue[CustomArrayType.VALUE];
                    this.initializeNewValue(newValue, serverJSONValue[CustomArrayType.CONTENT_VERSION], propertyContext?.getPushToServerCalculatedValue());
                    const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];

                    if (newValue.length)
                    {
                        for (let c = 0; c < newValue.length; c++) {
                            let elem = newValue[c];
    
                            newValue[c] = elem = this.sabloConverters.convertFromServerToClient(elem, this.staticElementType, currentClientValue ? currentClientValue[c] : undefined,
                                    internalState.dynamicPropertyTypesHolder, "" + c, componentScope, elemPropertyContext);
    
                            if (elem && elem[this.sabloConverters.INTERNAL_IMPL] && elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
                                // child is able to handle it's own change mechanism
                                elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newValue, c));
                            }
                        }
                    }
                } else if (serverJSONValue && serverJSONValue[CustomArrayType.GRANULAR_UPDATES]) {
                    // granular updates received;
                    const internalState = currentClientValue[this.sabloConverters.INTERNAL_IMPL];

                    internalState.calculatedPushToServerOfWholeProp = propertyContext?.getPushToServerCalculatedValue(); // for example if a custom object value is initially received through a return value from server side api/handler call and not as a normal model property and then the component/service assigns it to model, the push to server of it might have changed; use the one received here as arg
                    if (!internalState.calculatedPushToServerOfWholeProp) internalState.calculatedPushToServerOfWholeProp = sablo.typesRegistry.PushToServerEnum.reject;

                    // if something changed browser-side, increasing the content version thus not matching next expected version,
                    // we ignore this update and expect a fresh full copy of the array from the server (currently server value is leading/has priority because not all server side values might support being recreated from client values)
                    if (internalState[CustomArrayType.CONTENT_VERSION] <= serverJSONValue[CustomArrayType.CONTENT_VERSION]) {
                        internalState[CustomArrayType.CONTENT_VERSION] = serverJSONValue[CustomArrayType.CONTENT_VERSION]; // if we assume that versions are in sync even if it is < not just = (workaround for SVYX-431), update it client side so the any change sent to server works (normally it should always be === here)
                        let i: number;
                        for (const granularOp of serverJSONValue[CustomArrayType.GRANULAR_UPDATES]) {
                            const startIndex_endIndex_opType = granularOp[CustomArrayType.OP_ARRAY_START_END_TYPE]; // it's an array of 3 elements in the order given in name
                            const startIndex = startIndex_endIndex_opType[0];
                            const endIndex = startIndex_endIndex_opType[1];
                            const opType = startIndex_endIndex_opType[2];

                            if (opType === CustomArrayType.CHANGED) {
                                const changedData = granularOp[CustomArrayType.GRANULAR_UPDATE_DATA];
                                for (i = startIndex; i <= endIndex; i++) {
                                    const relIdx = i - startIndex;

                                    // apply the conversions, update value and kept conversion info for changed indexes
                                    currentClientValue[i] = this.sabloConverters.convertFromServerToClient(changedData[relIdx], this.staticElementType,
                                                                currentClientValue[i], internalState.dynamicPropertyTypesHolder, "" + i, componentScope, elemPropertyContext);

                                    if (currentClientValue[i] && currentClientValue[i][this.sabloConverters.INTERNAL_IMPL] && currentClientValue[i][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
                                        // child is able to handle it's own change mechanism
                                        currentClientValue[i][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, i));
                                    }
                                }
                            } else if (opType === CustomArrayType.INSERT) {
                                const insertedData = granularOp[CustomArrayType.GRANULAR_UPDATE_DATA];
                                const numberOfInsertedRows = insertedData.length;

                                // shift right by "numberOfInsertedRows" all dynamicTypes after or equal to idx as we are going to insert new values there
                                for (let idxToShift = currentClientValue.length - 1; idxToShift >= startIndex; idxToShift--)
                                    if (internalState.dynamicPropertyTypesHolder["" + idxToShift]) {
                                        internalState.dynamicPropertyTypesHolder["" + (idxToShift + numberOfInsertedRows)] = internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                        delete internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                    } else delete internalState.dynamicPropertyTypesHolder["" + (idxToShift + numberOfInsertedRows)];

                                // apply conversions
                                for (i = numberOfInsertedRows - 1; i >= 0 ; i--) {
                                    const addedRow = this.sabloConverters.convertFromServerToClient(insertedData[i], this.staticElementType, undefined,
                                                    internalState.dynamicPropertyTypesHolder, "" + (startIndex + i), componentScope, elemPropertyContext);
                                    currentClientValue.splice(startIndex, 0, addedRow);
                                }

                                // update any affected change notifiers
                                for (i = startIndex; i < currentClientValue.length; i++) {
                                    if (currentClientValue[i] && currentClientValue[i][this.sabloConverters.INTERNAL_IMPL] && currentClientValue[i][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
                                        // child is able to handle it's own change mechanism
                                        currentClientValue[i][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, i));
                                    }
                                }
                            } else if (opType === CustomArrayType.DELETE) {
                                const numberOfDeletedRows = endIndex - startIndex + 1;

                                // shift left by "numberOfDeletedRows" all dynamicTypes after "startIndex + numberOfDeletedRows" as we are going to delete that interval
                                for (let idxToShift = startIndex + numberOfDeletedRows; idxToShift < currentClientValue.length; idxToShift++)
                                    if (internalState.dynamicPropertyTypesHolder["" + idxToShift]) {
                                        internalState.dynamicPropertyTypesHolder["" + (idxToShift - numberOfDeletedRows)] = internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                        delete internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                    } else delete internalState.dynamicPropertyTypesHolder["" + (idxToShift - numberOfDeletedRows)];
                                    
                                // now delete/shift actual array contents
                                currentClientValue.splice(startIndex, numberOfDeletedRows);

                                // update any affected change notifiers
                                for (i = startIndex; i < currentClientValue.length; i++) {
                                    if (currentClientValue[i] && currentClientValue[i][this.sabloConverters.INTERNAL_IMPL] && currentClientValue[i][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
                                        // child is able to handle it's own change mechanism
                                        currentClientValue[i][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, i));
                                    }
                                }
                            }
                        }
                    }
                    //else {
                    // else we got an update from server for a version that was already bumped by changes in browser; ignore that, as browser changes were sent to server
                    // and server will detect the problem and send back a full update
                    //}
                } else if (!serverJSONValue || !serverJSONValue[CustomArrayType.NO_OP]) newValue = null; // anything else would not be supported...    // TODO how to handle null values (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?
            } finally {
                // add back watches if needed
                this.addBackWatches(newValue, componentScope);
            }

            return newValue;
        }
        
        private getElementType(internalState, idx: number | string) {
            return this.staticElementType ? this.staticElementType : internalState.dynamicPropertyTypesHolder["" + idx];
        }

        fromClientToServer(newClientData: any, oldClientData: CustomArrayValue, scope: angular.IScope, propertyContext: sablo.IPropertyContext): any {
            // TODO how to handle null values (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?

            const elemPropertyContext = propertyContext ? new sablo.typesRegistry.PropertyContext(propertyContext.getProperty,
                    sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(propertyContext.getPushToServerCalculatedValue(), this.pushToServerForElements),
                    propertyContext.isInsideModel) : undefined;

            let internalState;
            if (newClientData) {
                if (!(internalState = newClientData[this.sabloConverters.INTERNAL_IMPL])) {
                    if (oldClientData && oldClientData[this.sabloConverters.INTERNAL_IMPL]) this.removeAllWatches(oldClientData);
                    // this can happen when an array value was set completely in browser
                    // any 'smart' child elements will initialize in their fromClientToServer conversion;
                    // set it up, make it 'smart' and mark it as all changed to be sent to server...
                    this.initializeNewValue(newClientData, 1, propertyContext?.getPushToServerCalculatedValue());
                    internalState = newClientData[this.sabloConverters.INTERNAL_IMPL];
                    
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

                if (!propertyContext?.isInsideModel || internalState.isChanged()) { // so either it has changes or it's used as an arg/return value to a handler/api call
                    const changes = {};
                    if (!propertyContext?.isInsideModel || internalState.allChanged) { // fully changed or arg/return value of handler/api call
                        // send all
                        
                        // we can't rely/use the current contentVersion on ng2 impl. - and this means ng1 and server are adjusted as well, because, in case of a change-by-reference in an ng2 service followed
                        // by a now deprecated ServoyPublicService.sendServiceChanges that did not have an oldPropertyValue argument, we sometimes do not have
                        // access to the old contentVersion to be able to use it... so full change from client will ignore old contentVersion on client and on server
                        // but that should not be a problem as those are meant more to ensure that granular updates don't happen on an wrong/obsolete value
                        changes[CustomArrayType.CONTENT_VERSION] = 0; // server treats this as a "don't check server content version as it's a full new value from client"

                        // we only reset client side contentVersion when sending full changes for model properties (so things that might have an equivalent on server);
                        // (args and return values to api/handlers should not reset client side state version when being sent to server (there they will be
                        // full new values anyway with no previous value - and not in sync with any client side value), because it is possible to send as argument or
                        // return value a value that is also present in the model at the same time, in which case this full send as an arg/return value should not
                        // alter client side version in the model - that version must remain unaltered, in sync with the server side version in the model)
                        if (propertyContext?.isInsideModel) internalState[CustomArrayType.CONTENT_VERSION] = 1; // start fresh
                        
                        const toBeSentArray = changes[CustomArrayType.VALUE] = [];
                        for (let idx = 0; idx < newClientData.length; idx++) {
                            const val = newClientData[idx];
                            
                            // even if child value has only partial changes or no changes, do send the full elem. value as we are sending full array value here
                            // that is, if this conversion is sending model values; otherwise (handler/api call arg/return values) it will always be sent fully anyway
                            // this is a bit of a hack because .allChanged might mean something or not for the "val"'s internal state - depending on what type it is; titanium ng2 code is a bit better here
                            if (val && val[this.sabloConverters.INTERNAL_IMPL] && propertyContext?.isInsideModel) val[this.sabloConverters.INTERNAL_IMPL].allChanged = true;

                            const converted = this.sabloConverters.convertFromClientToServer(val, this.getElementType(internalState, idx),
                                oldClientData ? oldClientData[idx] : undefined, scope, elemPropertyContext);
                            // TODO although this is a full change, we give oldClientData[idx] (oldvalue) because server side does the same for some reason,
                            // but normally both should use undefined/null for old value of elements as this is a full change; SVY-17854 is created for looking into this

                            // if it's a nested obj/array or other smart prop that just got smart in convertFromClientToServer, attach the change notifier
                            if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
                                val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newClientData, idx));

                            // do not send to server if elem pushToServer is reject
                            if (!elemPropertyContext || elemPropertyContext.getPushToServerCalculatedValue().value > sablo.typesRegistry.PushToServerEnum.reject.value) toBeSentArray[idx] = converted;
                        }

                        // now add watches/change notifiers to the new full value if needed (now all children are converted and made smart themselves if necessary so addBackWatches can make the distinction between smart and dumb correctly)
                        this.removeAllWatches(newClientData); // in case this was not an actual full new value but an existing array in which elements were added/removed and then it does have watches
                        this.addBackWatches(newClientData, scope);

                        if (propertyContext?.isInsideModel) { 
                            internalState.allChanged = false;
                            internalState.changedIndexesOldValues = {};
                        }

                        if (calculatedPushToServerOfWholeProp === sablo.typesRegistry.PushToServerEnum.reject)
                        {
                            // if whole value is reject, don't sent anything
                            const x = {}; // no changes
                            x[CustomArrayType.NO_OP] = true;
                            return x;
                        }
                        else return changes;
                    } else {
                        // send only changed indexes
                        changes[CustomArrayType.CONTENT_VERSION] = internalState[CustomArrayType.CONTENT_VERSION];
                        const changedElements = changes[CustomArrayType.UPDATES] = [];
                        for (const idx in internalState.changedIndexesOldValues) {
                            const newVal = newClientData[idx];
                            const oldVal = internalState.changedIndexesOldValues[idx];

                            let changed = (newVal !== oldVal);

                            if (!changed) {
                                if (!internalState.elUnwatch || !internalState.elUnwatch[idx]) {
                                	// must be a smart value then; same reference as checked above; so ask it if it changed
                                    changed = newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].isChanged && newVal[this.sabloConverters.INTERNAL_IMPL].isChanged();
                                } // else it's a dumb value - with the same ref; no changes then
                            }

                            if (changed) {
                                const ch = {};
                                ch[CustomArrayType.INDEX] = idx;
                                
                                let wasSmartBeforeConversion = (newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier);
                                ch[CustomArrayType.VALUE] = this.sabloConverters.convertFromClientToServer(newVal, this.getElementType(internalState, idx), oldVal, scope, elemPropertyContext);
                                if (!wasSmartBeforeConversion && (newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier))
                                    newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newClientData, Number.parseInt(idx))); // if it was a new object/array set at this index which was initialized by convertFromClientToServer call, do add the change notifier to it 

                                changedElements.push(ch);
                            }
                        }
                        internalState.allChanged = false;
                        internalState.changedIndexesOldValues = {};
                        return changes;
                    }
                } else {
                    const x = {}; // no changes
                    x[CustomArrayType.NO_OP] = true;
                    return x;
                }
            }

            return newClientData;
        }

        updateAngularScope(clientValue: CustomArrayValue, componentScope: angular.IScope): void {
            // it is possible here that the clientValue exists but it is not initialized (no internal state)
            // that can happen for example if properties in .spec are scope: private and pushToServer: reject (which is default pushToServer)
            // and those properties are only assigned on client - so they never go through either fromServerToClient nor fromClientToServer

            this.removeAllWatches(clientValue);

            if (clientValue) {
                const internalState = clientValue[this.sabloConverters.INTERNAL_IMPL];
                if (internalState) {
                    if (componentScope) this.addBackWatches(clientValue, componentScope);

                    for (let c = 0; c < clientValue.length; c++) {
                        const elem = clientValue[c];
                        const elType = this.getElementType(internalState, c);
                        if (elType) elType.updateAngularScope(elem, componentScope);
                    }
                }
            }
        }

        // ------------------------------------------------------------------------------

        private getChangeNotifier(propertyValue: any, idx: number) {
            return () => {
                const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
                internalState.changedIndexesOldValues[idx] = propertyValue[idx];
                if (internalState.changeNotifier) internalState.changeNotifier();
            }
        }

        private watchDumbElementForChanges(propertyValue: CustomArrayValue, idx: number, componentScope: angular.IScope, deep: boolean): () => void  {
            // if elements are primitives or anyway not something that wants control over changes, just add an in-depth watch
            return componentScope.$watch(function() {
                return propertyValue[idx];
            }, (newvalue, oldvalue) => {
                if (oldvalue === newvalue) return;
                const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
                
                internalState.changedIndexesOldValues[idx] = oldvalue;
                if (internalState.changeNotifier) internalState.changeNotifier();
            }, deep);
        }

        /** Initializes internal state on a new array value */
        private initializeNewValue(newValue: any, contentVersion: number, pushToServerCalculatedValue: sablo.typesRegistry.PushToServerEnum) {
            this.sabloConverters.prepareInternalState(newValue); 
            
            const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
            internalState[CustomArrayType.CONTENT_VERSION] = contentVersion; // being full content updates, we don't care about the version, we just accept it
            internalState.calculatedPushToServerOfWholeProp = (typeof pushToServerCalculatedValue != 'undefined' ? pushToServerCalculatedValue : sablo.typesRegistry.PushToServerEnum.reject);
    
            // implement what $sabloConverters need to make this work
            internalState.setChangeNotifier = function(changeNotifier) {
                internalState.changeNotifier = changeNotifier; 
            }
            internalState.isChanged = function() {
                let hasChanges = internalState.allChanged;
                if (!hasChanges) for (const _x in internalState.changedIndexesOldValues) { hasChanges = true; break; }
                return hasChanges;
            }

            // private impl
            internalState.modelUnwatch = [];
            internalState.arrayStructureUnwatch = null;
            internalState.changedIndexesOldValues = {};
            internalState.allChanged = false;
            internalState.dynamicPropertyTypesHolder = {};
        }

        private removeAllWatches(value: CustomArrayValue): void {
            if (value != null && angular.isDefined(value)) {
                const iS = value[this.sabloConverters.INTERNAL_IMPL];
                if (iS != null && angular.isDefined(iS)) {
                    if (iS.arrayStructureUnwatch) iS.arrayStructureUnwatch();
                    for (const key in iS.elUnwatch) {
                        iS.elUnwatch[key]();
                    }
                    iS.arrayStructureUnwatch = null;
                    iS.elUnwatch = null;
                }
            }
        }

        private addBackWatches(value: CustomArrayValue, componentScope: angular.IScope): void {
            if (value) {
                const internalState = value[this.sabloConverters.INTERNAL_IMPL];
                internalState.elUnwatch = {};

                const elementCalculatedPushToServer = sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(internalState.calculatedPushToServerOfWholeProp,
                        this.pushToServerForElements);

                // add shallow/deep watches as needed
                if (componentScope) {
                    for (let c = 0; c < value.length; c++) {
                        const elem = value[c];
                        if ((!elem || !elem[this.sabloConverters.INTERNAL_IMPL] || !elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) && elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                            // watch the child's value to see if it changes
                             internalState.elUnwatch[c] = this.watchDumbElementForChanges(value, c, componentScope, elementCalculatedPushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true); // either deep or shallow because the if above checks that it is at least shallow
                        } // else if it's a smart value and the pushToServer is shallow or deep we must shallow watch it (it will manage it's own contents but we still must watch for reference changes);
                        // but that is done below in a $watchCollection
                    }

                    // watch for add/remove and such operations on array; this is helpful also when 'smart' child values (that have .setChangeNotifier)
                    // get changed completely by reference, so int IS NOT JUST FOR DUMB/REFERENCE changes if pushToServer >= shallow but also for updating the .setChangeNotifier on smart values
                    internalState.arrayStructureUnwatch = componentScope.$watchCollection(function() { return value; }, (newWVal, oldWVal) => {
                        if (newWVal === oldWVal) return;

                        let fullChangeWasAlreadySentDueToLengthChange: boolean = false;
                        if (newWVal.length !== oldWVal.length) {
                            // that watch funct. always returns the same reference "return value"; but length could differ old and new
                            // so a delete/push happened on the array; currently we treat this as a full change
                            if (elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                                internalState.allChanged = true;
                                
                                fullChangeWasAlreadySentDueToLengthChange = true;
                                
                                if (internalState.changeNotifier) internalState.changeNotifier();
                            }
                        }

                        // some elements changed by reference; but array length did not change
                        let referencesChanged = false;
                        const maxLen = Math.max(newWVal.length, oldWVal.length);
                        for (let j = 0; j < maxLen; j++) {
                            const oldEl = (j < oldWVal.length ? oldWVal[j] : undefined);
                            const newEl = (j < newWVal.length ? newWVal[j] : undefined);
                            if (newEl !== oldEl) {
                                const oldElWasSmart = oldEl && oldEl[this.sabloConverters.INTERNAL_IMPL] && oldEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier;
                                if (oldElWasSmart) {
                                    oldEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(undefined);
                                }

                                if (!fullChangeWasAlreadySentDueToLengthChange && elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value && oldElWasSmart) {
                                    // we only need to handle this for old smart element values,
                                    // as the others will be handled by the separate 'dumb' watches
                                    referencesChanged = true;
                                    internalState.changedIndexesOldValues[j] = oldEl;
                                }

                                const newElIsSmart = newEl && newEl[this.sabloConverters.INTERNAL_IMPL] && newEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier;
                                if (newElIsSmart) {
                                    if (!oldElWasSmart) {
                                        // old el was dumb so a dumb watch was probably added on it before
                                        // it became smart so watch is obsolete; watchCollection handles smart elements
                                        if (internalState.elUnwatch[j])  internalState.elUnwatch[j]();
                                        delete internalState.elUnwatch[j];
                                    }
                                    newEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(value, j));
                                } else { // so newEl is dumb
                                    if (!fullChangeWasAlreadySentDueToLengthChange && oldElWasSmart && elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                                        // it became dumb; add watch if needed
                                        internalState.elUnwatch[j] = this.watchDumbElementForChanges(value, j, componentScope, elementCalculatedPushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true);
                                    }
                                }
                            }
                        }

                        if (referencesChanged && internalState.changeNotifier) internalState.changeNotifier();
                    });
                }
            }
        }
    }

    interface CustomArrayValue extends Array<any> {

    }

}
