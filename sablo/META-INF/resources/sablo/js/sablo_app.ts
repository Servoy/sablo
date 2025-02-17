/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />
/// <reference path="../../../../typings/jquery/jquery.d.ts" />
/// <reference path="websocket.ts" />

namespace sablo_app { export class Model{ visible=true }}

angular.module('sabloApp', ['webSocketModule', 'webStorageModule']).value("$sabloConstants", {
	modelChangeNotifier: "$modelChangeNotifier"
}).factory('$sabloApplication', function($rootScope: angular.IRootScopeService, $window: angular.IWindowService, $timeout: angular.ITimeoutService,
		$q: angular.IQService, $log: sablo.ILogService, $webSocket: sablo.IWebSocket, $sabloConverters: sablo.ISabloConverters,
		$sabloUtils: sablo.ISabloUtils, $sabloConstants: sablo.SabloConstants, webStorage,
		$typesRegistry: sablo.ITypesRegistry) {

	// close the connection to the server when application is unloaded
	$window.addEventListener('beforeunload', function(event) {
        sessionStorage.removeItem('svy_session_lock');
	});
    $window.addEventListener('pagehide', function(event) {
        sessionStorage.removeItem('svy_session_lock');
    });
    
    if (sessionStorage.getItem('svy_session_lock')) {
        webStorage.session.remove('windownr');
        webStorage.session.remove('clientnr');
        $log.warn('Found a lock in session storage. The storage was cleared.');
    }

    sessionStorage.setItem('svy_session_lock', '1');

    var inLogCall = false;
	var oldError = $window.console.error;
	var oldLog = $window.console.log;
	$window.console.log =  function(msg) {
		try
		{
			oldLog.apply($window.console, arguments);
			if (!inLogCall) {
                inLogCall= true;
                callService('consoleLogger', 'info', { message: msg }, true)
		    }	
		}
		catch(e)
		{
			oldError.apply($window.console, [e]);
		}
		finally {
            inLogCall = false;
        }
	}
	
	$window.console.error =  function(msg) {
		try
		{
			oldError.apply($window.console, arguments);
            if (!inLogCall) {
                inLogCall= true;
                callService('consoleLogger', 'error', { message: msg }, true)
            }   
		}
		catch(e)
		{
			oldError.apply($window.console, [e]);
		}
        finally {
            inLogCall = false;
        }
	}
	
	var oldWarn = $window.console.warn;
	$window.console.warn =  function(msg) {
		try
		{
			oldWarn.apply($window.console, arguments);
            if (!inLogCall) {
                inLogCall= true;
                callService('consoleLogger', 'warn', { message: msg }, true)
            }   
		}
		catch(e)
		{
			oldError.apply($window.console, [e]);
		}
        finally {
            inLogCall = false;
        }
	}
	
	var oldInfo = $window.console.info;
	$window.console.info =  function(msg) {
		try
		{
			oldInfo.apply($window.console, arguments);
            if (!inLogCall) {
                inLogCall= true;
	       		callService('consoleLogger', 'info', { message: msg }, true)
            }   
		}
		catch(e)
		{
			oldError.apply($window.console, [e]);
		}
        finally {
            inLogCall = false;
        }
	}
	
	var oldDebug = $window.console.debug;
	$window.console.debug =  function(msg) {
		try
		{
			oldDebug.apply($window.console, arguments);
            if (!inLogCall) {
                inLogCall= true;
    			callService('consoleLogger', 'debug', { message: msg }, true)
            }   
		}
		catch(e)
		{
			oldError.apply($window.console, [e]);
		}
        finally {
            inLogCall = false;
        }
	}
	
	$window.onerror = function(message, source, lineno, colno, error) {
		var msg = message + "\n" + source +":" + lineno +":" + colno + "\n" + error;
		callService('consoleLogger', 'error', { message: msg }, true)
	}
	// formName:[beanname:{property1:1,property2:"test"}] needs to be synced to and from server
	// this holds the form model with all the data, per form is this the "synced" view of the the IFormUI on the server 
	// (3 way binding)
	var currentFormUrl = null;
	var formStates: { [s: string]: sablo.FormState; } = {};
	var formStatesDynamicClientSideTypes = {};

	var locale = null;

	var deferredFormStates = {};
	var deferredFormStatesWithData = {};
	var getChangeNotifierGenerator = function(formName: string, beanName: string): sablo.PropertyChangeNotifierGeneratorFunction {
		return function(property: string) {
			return function() {
				// will be called by the custom property when it needs to send changes server size
				var beanModel = formStates[formName].model[beanName];
				sendChanges(beanModel[property], beanModel[property], formName, beanName, property);
			};
		};
	}

	/*
	 * Some code is interested in form state immediately after it's loaded/initialized (needsInitialData = false) in which case only some template values might be
	 * available and some code is interested in using form state only after it got the initialData (via "requestData"'s response) from server (needsInitialData = true)
	 */
	var getFormStateImpl = function(name, needsInitialData) {
		// TODO should we also keep track here of time passed? (a timeout that is cleared if it does get resolved) (if it's not loaded/initialized in X min just reject the deferredState)
		var deferredStates = needsInitialData ? deferredFormStatesWithData : deferredFormStates;
		var defered;
		if (deferredStates[name]) {
			defered = deferredStates[name];
		} else {
			defered = $q.defer()
			deferredStates[name] = defered;
		}
		var formState = formStates[name];
		if (formState && formState.resolved && !(formState.initializing && needsInitialData)) {
			defered.resolve(formStates[name]); // then handlers are called even if they are applied after it is resolved
			delete deferredStates[name];
		}
		return defered.promise;
	}

	var getFormState = function(name) {
		return getFormStateImpl(name, false);
	}

	var getFormStateWithData = function(name) {
		return getFormStateImpl(name, true);
	}

	var getComponentPropertyChange = function(now: any, prev: any, typeOfProperty: sablo.IType<any>, propertyName: string,
			scope: angular.IScope, propertyContext: sablo.IPropertyContext, changeNotifierGenerator: sablo.PropertyChangeNotifierGeneratorFunction) {
		const changes = {};
		if (!propertyName) throw new Error('propertyName should not be null here!');
		changes[propertyName] = $sabloConverters.convertFromClientToServer(now, typeOfProperty, prev, scope, propertyContext);

        // set/update change notifier just in case a new full value was set into a smart property type that needs a changeNotifier for that specific property
		setChangeNotifierIfSmartProperty(now, propertyName, changeNotifierGenerator);
		
		return changes;
	}

	function getAllChanges(now: object, prev: object, dynamicTypes: object, scope: angular.IScope, propertyContextCreator: sablo.IPropertyContextCreator) {
		const changes = {}
		// first build up a list of all the properties both have.
		const fulllist = $sabloUtils.getCombinedPropertyNames(now, prev);

		for (const prop in fulllist) {
			let changed: boolean;
			if (prev && now) {
				changed = $sabloUtils.isChanged(now[prop], prev[prop], dynamicTypes?.[prop])
			} else {
				changed = true; // true if just one of them is undefined; both cannot be undefined at this point if we are already iterating on combined property names
			}

			if (changed) {
				changes[prop] = now[prop];
			}
		}
		for (const prop in changes) {
			changes[prop] = $sabloConverters.convertFromClientToServer(changes[prop], dynamicTypes?.[prop], prev ? prev[prop] : undefined, scope, propertyContextCreator.withPushToServerFor(prop));
		}
		return changes;
	};

	var sendChanges = function(now, prev, formname, beanname, propertyName) {
		var dynamicTypes = $sabloUtils.getInDepthProperty(formStatesDynamicClientSideTypes, formname, beanname);
		
		const formState = formStates[formname];
		const formScope = formState.getScope();
		const componentModel = formState.model[beanname];
		const componentSpec = $typesRegistry.getComponentSpecification($sabloUtils.getInDepthProperty(formStates, formname, "componentSpecNames", beanname));
		const propertyContextCreator = new sablo.typesRegistry.RootPropertyContextCreator(
				(propertyName: string) => { return componentModel ? componentModel[propertyName] : undefined },
				componentSpec);

		var changes: any;
		var propertyType: sablo.IType<any>;
		if (angular.isDefined(propertyName)) {
			// a component property; get it's type (either static or dynamic)
			propertyType = dynamicTypes[propertyName]; // try dynamic types for non-viewport props.
			if (!propertyType) { // try static types for props.
				if (componentSpec) propertyType = componentSpec.getPropertyType(propertyName);
			}

			changes = getComponentPropertyChange(now, prev, propertyType, propertyName, formScope, propertyContextCreator.withPushToServerFor(propertyName),
			             getChangeNotifierGenerator(formname, beanname));
		} else changes = getAllChanges(now, prev, dynamicTypes, formScope, propertyContextCreator); // probably for a form's own properties
		
		if (Object.getOwnPropertyNames(changes).length > 0) {
			// if this is a simple property change without any special client side type - then push the old value as well
			if (angular.isDefined(propertyName) && !propertyType) {
				var oldvalues = {};
				oldvalues[propertyName] = $sabloConverters.convertFromClientToServer(prev, undefined, undefined, formScope, propertyContextCreator.withPushToServerFor(propertyName)); // just for any default conversions
				callService('formService', 'dataPush', { formname: formname, beanname: beanname, changes: changes, oldvalues: oldvalues }, true)
			}
			else {
				callService('formService', 'dataPush', { formname: formname, beanname: beanname, changes: changes }, true)
			}

		}
	};
	
	const setChangeNotifierIfSmartProperty = function(propertyValue: any, propertyName: string, changeNotifierGenerator: sablo.PropertyChangeNotifierGeneratorFunction) {
        if (propertyValue && propertyValue[$sabloConverters.INTERNAL_IMPL] && propertyValue[$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
            // setChangeNotifier can be called now after the new client side type is applied (changeNotifierGenerator(key) will probably use the values in model and that has to point to the new value if reference was changed)
            // as setChangeNotifier on smart property types might end up calling the change notifier right away to announce it already has changes (because for example
            // the convertFromServerToClient on that property type above might have triggered some listener to the component that uses it which then requested
            // another thing from the property type and it then already has changes...) // TODO should we decouple this scenario? if we are still processing server to client changes when change notifier is called we could trigger the change notifier later/async for sending changes back to server...
            let changeNotfier = changeNotifierGenerator(propertyName);
            propertyValue[$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotfier);
            
            // we check for changes anyway in case a property type doesn't do it itself as described in the comment above
            if (propertyValue[$sabloConverters.INTERNAL_IMPL].isChanged && propertyValue[$sabloConverters.INTERNAL_IMPL].isChanged()) changeNotfier();
        }
    }

	var applyBeanData = function(beanModel: any, beanData: any, containerSize: any, changeNotifierGenerator: sablo.PropertyChangeNotifierGeneratorFunction,
			componentSpecName: string /*to access static client side types of comp*/, dynamicPropertyTypesHolder: object /*some types decide at runtime the type needed on client - for example dataprovider type could send date, and we keep that info here*/,
			componentScope: angular.IScope) {

		const componentSpec:sablo.IWebObjectSpecification = $typesRegistry.getComponentSpecification(componentSpecName); // get static client side types for this component - if it has any 
		const propertyContextCreator = new sablo.typesRegistry.RootPropertyContextCreator(
				(propertyName: string) => ( beanModel ? beanModel[propertyName] : undefined ),
				componentSpec);

		// apply the new values and client side type conversions
		for (var key in beanData) {
			let oldModelValueForKey = beanModel[key];
			let propertyType = componentSpec?.getPropertyType(key); // get static client side type if any
			let oldTypeForKey = propertyType ? propertyType : dynamicPropertyTypesHolder[key];
			
			beanModel[key] = $sabloConverters.convertFromServerToClient(beanData[key], propertyType, oldModelValueForKey,
					dynamicPropertyTypesHolder, key, componentScope, propertyContextCreator.withPushToServerFor(key));
			
			if (!propertyType) propertyType = dynamicPropertyTypesHolder[key]; // get any new dynamic client side type - if needed

			if (propertyType) {
				// if the value changed and it wants to be in control of it's changes, or if the client side type for this value changed (thus possibly preparing an old value for being change-aware without changing the value reference)
				if (oldModelValueForKey !== beanModel[key] || oldTypeForKey !== propertyType)
				    setChangeNotifierIfSmartProperty(beanModel[key], key, changeNotifierGenerator);
			}
		}
		
		// if the model had a change notifier call it now after everything is set.
		var modelChangeFunction = beanModel[$sabloConstants.modelChangeNotifier];
		if (modelChangeFunction) {
			for (var key in beanData) {
				modelChangeFunction(key, beanModel[key]);
			}
		}
	}

	var updateScopeForState = function(formName: string, formScope: angular.IScope, state: sablo.FormState) {
		if (!state) state = formStates[formName];

		if (state && state.model) {
			for (var componentName in state.model) {
				if (componentName !== '') {
					const componentSpecName = state.componentSpecNames[componentName];
					const staticPropertyTypes = $typesRegistry.getComponentSpecification(componentSpecName); 
					const dynamicPropertyTypes = $sabloUtils.getInDepthProperty(formStatesDynamicClientSideTypes, formName, componentName);
					const componentModel = state.model[componentName];
					
					for (let propertyName in componentModel) {
						let typeOfProp = staticPropertyTypes?.getPropertyType(propertyName);
						if (!typeOfProp) typeOfProp =  dynamicPropertyTypes[propertyName];
						
						if (typeOfProp) typeOfProp.updateAngularScope(componentModel[propertyName], formScope); 
					}
				}
			}
		} else if (formScope) {
			$log.error("When updating scope - with non-null value state.model is undefined. This is unexpected...");
		} // recreateUI destroying it completely? or complete form unload? or maybe partial load in hidden div interrupted by real show of same form?
	}

	var wsSession: sablo.WSSession = null;
	function getSession(): sablo.WSSession {
		if (wsSession == null) throw "Session is not created yet, first call connect()";
		return wsSession;
	}

	var currentServiceCallCallbacks = []
	var currentServiceCallDone: boolean;
	var currentServiceCallWaiting = 0
	var currentServiceCallTimeouts;
	function addToCurrentServiceCall(func) {
		if (currentServiceCallWaiting == 0) {
			// No service call currently running, call the function now
			$timeout(function() { func.apply(); })
		}
		else {
			currentServiceCallCallbacks.push(func)
		}
	}

	function callServiceCallbacksWhenDone() {
		if (currentServiceCallDone || --currentServiceCallWaiting == 0) {
			currentServiceCallWaiting = 0
			currentServiceCallTimeouts.map(function(id) { return clearTimeout(id) })
			var tmp = currentServiceCallCallbacks
			currentServiceCallCallbacks = []
			tmp.map(function(func) { func.apply() })
		}
	}

	function markServiceCallDone(arg) {
		currentServiceCallDone = true;
		return arg;
	}

	function markServiceCallFailed(arg) {
		currentServiceCallDone = true;
		return $q.reject(arg);
	}

	function waitForServiceCallbacks(promise, times): angular.IPromise<any> {
		if (currentServiceCallWaiting > 0) {
			// Already waiting
			return promise
		}

		currentServiceCallDone = false
		currentServiceCallWaiting = times.length
		currentServiceCallTimeouts = times.map(function(t) { return setTimeout(callServiceCallbacksWhenDone, t) });

		return $webSocket.wrapPromiseToPropagateCustomRequestInfoInternal(promise, promise.then(markServiceCallDone, markServiceCallFailed));
	}

	function callService(serviceName:string, methodName:string, argsObject, async?:boolean): angular.IPromise<any> {
		var promise = getSession().callService(serviceName, methodName, argsObject, async)
		return async ? promise : waitForServiceCallbacks(promise, [100, 200, 500, 1000, 3000, 5000])
	}

	var getClientnr = function() {
		var clientnr = webStorage.session.get('clientnr')
		if (clientnr) {
			return clientnr;
		}
		return $webSocket.getURLParameter('clientnr');
	}
	
    var getSessionId = function() {
   	 console.log("deprecated api usage, use getClientNr()")
   	 return getClientnr();
   	}
   	
	var getWindowName = function() {
		return $webSocket.getURLParameter('windowname');
	}

	var getWindownr = function() {
		return webStorage.session.get('windownr');
	}

	var getWindowUrl = function(windowname: string) {
		return "index.html?windowname=" + encodeURIComponent(windowname) + "&clientnr=" + getClientnr();
	}

	var formResolver = null;
	var apiCallDeferredQueue = [];

	var wsSessionArgs = {
		context: "",
		queryArgs: {},
		websocketUri: ""
	};

	function hasResolvedFormState(name) {
		return typeof (formStates[name]) !== 'undefined' && formStates[name].resolved;
	}

	function hasFormStateWithData(name) {
		return typeof (formStates[name]) !== 'undefined' && !formStates[name].initializing && formStates[name].resolved;
	}
	
	return <sablo.ISabloApplication> {
		connect: function(context, queryArgs, websocketUri) {
			wsSessionArgs = {
				context: context,
				queryArgs: queryArgs,
				websocketUri: websocketUri
			};
			wsSession = $webSocket.connect(wsSessionArgs.context, [getClientnr(), getWindowName(), getWindownr()], wsSessionArgs.queryArgs, wsSessionArgs.websocketUri);

			wsSession.onMessageObject(function(msg, scopesToDigest) {
				// data got back from the server
				for (var formname in msg.forms) {
					var formState = formStates[formname];
					if (typeof (formState) == 'undefined') {
						// if the form is not there yet, wait for the form state.
						getFormState(formname).then(getFormMessageHandler(formname, msg),
							function(err) { $log.error("Error getting form state when trying to handle msg. from server: " + err); });
					} else {
						// if the form is there apply it directly so that it is there when the form is recreated
						getFormMessageHandler(formname, msg)(formState);
						if (formState.getScope) {
							var s = formState.getScope();
							if (s) scopesToDigest.putItem(s);
						}
					}
				}
				
				if (msg.clientnr) {
					webStorage.session.set("clientnr", msg.clientnr);
				}
				if (msg.windownr) {
					webStorage.session.set("windownr", msg.windownr);
				}
				if (msg.clientnr || msg.windownr) {
					// update the arguments on the reconnection websocket.
					$webSocket.setConnectionPathArguments([getClientnr(), getWindowName(), getWindownr()]);
				}
				
				if (msg.call) {
                    // this is a component API call; execute it
					// {"call":{"form":"product","element":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}, // optionally "viewIndex":1 }
					var call = msg.call;

					if ($log.debugEnabled) $log.debug("sbl * Received API call from server: '" + call.api + "' to form " + call.form + ", component " + (call.propertyPath ? call.propertyPath : call.bean));

					function getAPICallFunctionsAndComponentSpec(formState): { funcs: (...any) => any, componentSpec: sablo.IWebObjectSpecification } {
						let retVal;
						if (call.viewIndex != undefined) {
							// I think this viewIndex' is never used; it was probably intended for components with multiple rows targeted by the same component if it want to allow calling API on non-selected rows, but it is not used
							retVal = { funcs: formState.api[call.bean][call.viewIndex],
							           componentSpec: $typesRegistry.getComponentSpecification(formState.componentSpecNames[call.bean]) };
						}
						else if (call.propertyPath != undefined) {
							// handle nested components; the property path is an array of string or int keys going
							// through the form's model starting with the root bean name, then it's properties (that could be nested)
							// then maybe nested child properties and so on 
							let obj = formState.model;
							let pn;
							for (pn in call.propertyPath) obj = obj[call.propertyPath[pn]];
							retVal = { funcs: obj.api,
							           componentSpec: obj.componentDirectiveName };
						}
						else {
							retVal = { funcs: formState.api[call.bean],
							           componentSpec: $typesRegistry.getComponentSpecification(formState.componentSpecNames[call.bean]) };
						}
						return retVal;
					}

					function executeAPICall(apiFunctionsAndComponentSpec: { funcs: (...any) => any, componentSpec: sablo.IWebObjectSpecification }): any {
						var func = apiFunctionsAndComponentSpec.funcs ? apiFunctionsAndComponentSpec.funcs[call.api] : null;
						var returnValue: any;
						if (!func) {
							$log.warn("sbl * Bean " + (call.propertyPath ? call.propertyPath : call.bean) + " on form " + call.form + " did not provide the called api: " + call.api)
							returnValue = null;
						}
						else {
							if ($log.debugEnabled) $log.debug("sbl * Api call '" + call.api + "' to form " + call.form + ", component " + (call.propertyPath ? call.propertyPath : call.bean) + " will be called now.");
							
							// convert args
							(<any[]>call.args)?.forEach((val: any, i: number) =>  
								call.args[i] = $sabloConverters.convertFromServerToClient(val,
									apiFunctionsAndComponentSpec.componentSpec?.getApiFunction?.(call.api)?.getArgumentType(i),
									undefined, undefined, undefined, undefined, $sabloUtils.PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES)); // api args do not keep dynamic types, should not add watches or be relative to a property context; at least this is how it is now
							
							// call API and make sure return value is also converted - if the API returns a value or a promise
							returnValue = $q.when(func.apply(apiFunctionsAndComponentSpec.funcs, call.args)).then(function(ret) {
								return $sabloConverters.convertFromClientToServer(ret, apiFunctionsAndComponentSpec.componentSpec?.getApiFunction?.(call.api)?.returnType,
								            undefined, undefined, $sabloUtils.PROPERTY_CONTEXT_FOR_OUTGOING_ARGS_AND_RETURN_VALUES);
							}, function(reason) {
								// error
								$log.error("sbl * Error (follows below) in in executing component Api call '" + call.api + "' to form " + call.form + ", component " + (call.propertyPath ? call.propertyPath : call.bean));
								$log.error(reason);
							});
						}
						return returnValue;
					};

					function executeAPICallInTimeout(formState, count, timeout) {
						return $timeout(function() {
							let apiFunctionsAndComponentSpec = getAPICallFunctionsAndComponentSpec(formState);
							if ($log.debugEnabled) $log.debug("sbl * Remaining wait cycles upon execution of API: '" + call.api + "' of form " + call.form + ", component " + (call.propertyPath ? call.propertyPath : call.bean) + ": " + count);
							if ((apiFunctionsAndComponentSpec.funcs && apiFunctionsAndComponentSpec.funcs[call.api]) || count < 1) {
								return executeAPICall(apiFunctionsAndComponentSpec);
							} else {
								return executeAPICallInTimeout(formState, count - 1, timeout)
							}
						}, timeout).then(function(result) {
							return result;
						}, function(err) {
							return $q.reject(err);
						});
					}

					var previousApiCallPromise = null;
					if (!call.delayUntilFormLoads) {
						// make sure normal and async API calls are called in the same sequence that they were called in server side JS
						if (apiCallDeferredQueue.length > 0) {
							previousApiCallPromise = apiCallDeferredQueue[apiCallDeferredQueue.length - 1].promise;
						}
						apiCallDeferredQueue.push($q.defer());
					} // else it's a delayed call which means it shouldn't force load (in hidden div) the form if not resolved nor should it block other APIs from execution; it just waits for form to resolve

					function resolveFormIfNeededAndExecuteAPICall() {
						if (!call.delayUntilFormLoads && formResolver != null && !hasResolvedFormState(call.form)) {
							// this means that the form was shown and is now hidden/destroyed; but we still must handle API call to it!
							// see if the form needs to be loaded;
							if ($log.debugEnabled) $log.debug("sbl * Api call '" + call.api + "' to unresolved form " + call.form + ", component " + (call.propertyPath ? call.propertyPath : call.bean) + "  will call prepareUnresolvedFormForUse.");
							formResolver.prepareUnresolvedFormForUse(call.form);
						}

						return getFormStateWithData(call.form).then(
							function(formState) {
								var apiFunctionsAndComponentSpec = getAPICallFunctionsAndComponentSpec(formState);
								if (apiFunctionsAndComponentSpec.funcs && apiFunctionsAndComponentSpec.funcs[call.api]) {
									return executeAPICall(apiFunctionsAndComponentSpec);
								} else {
									if ($log.debugEnabled) $log.debug("sbl * Waiting for API to be contributed before execution: '" + call.api + "' of form " + call.form + ", component " + (call.propertyPath ? call.propertyPath : call.bean));
									return executeAPICallInTimeout(formState, 10, 20);
								}
							},
							function(err) {
								$log.error("sbl * Error getting form state: " + err);
								return $q.reject("Error getting form state: " + err);
							}).finally(function() {
								if (!call.delayUntilFormLoads && apiCallDeferredQueue.length > 0) {
									apiCallDeferredQueue.shift().resolve();
								}
							});

					}

					if (previousApiCallPromise) {
						return previousApiCallPromise.then(
							function() {
								return resolveFormIfNeededAndExecuteAPICall();
							},
							function(err) {
								$log.error("sbl * Error waiting for api call execute " + err);
								return $q.reject(err);
							});
					}
					else {
						return resolveFormIfNeededAndExecuteAPICall();
					}
				}
			});

			function getFormMessageHandler(formname, msg) {
				return function(formState: sablo.FormState) {
					var formModel = formState.model;
					var newFormData = msg.forms[formname];
					var newFormProperties = newFormData['']; // form properties

					if (newFormProperties) {
						// currently what server side sends for the form itself doesn't need client side conversions 
						if (!formModel['']) formModel[''] = {};
						for (const p in newFormProperties) {
							formModel[''][p] = newFormProperties[p];
						}
					}

					const watchesRemoved = formState.removeWatches ? formState.removeWatches(newFormData) : false;
					try {
						for (const beanname in newFormData) {
							// copy over the changes, skip for form properties (beanname empty)
							if (beanname != '') {
								const beanDynamicTypesHolder = $sabloUtils.getOrCreateInDepthProperty(formStatesDynamicClientSideTypes, formname, beanname);
								applyBeanData(formModel[beanname], newFormData[beanname], formState.properties.designSize,
										getChangeNotifierGenerator(formname, beanname), formState.componentSpecNames[beanname], beanDynamicTypesHolder,
										formState.getScope ? formState.getScope() : undefined);
							}
						}
					}
					finally {
						if (watchesRemoved) {
							formState.addWatches(newFormData);
						}
					}
				}
			}

			return wsSession
		},

		contributeFormResolver: function(contributedFormResolver) {
			formResolver = contributedFormResolver;
		},

		getClientnr: getClientnr,
		getSessionId: getSessionId,
		getWindowName: getWindowName,
		getWindownr: getWindownr,
		getWindowUrl: getWindowUrl,

		// used by custom property component[] to implement nested component logic
		applyBeanData: applyBeanData,
		getComponentPropertyChange: getComponentPropertyChange,
		getAllChanges: getAllChanges,
		getChangeNotifierGenerator: getChangeNotifierGenerator,

		getFormState: getFormState,

		getFormStateWithData: getFormStateWithData,

		// this method should only be used to manipulate pre-resolve state if apps. need any; the returned form state if defined might not have any data in it or operational DOM/directives
		getFormStateEvenIfNotYetResolved: function(name) {
			return formStates[name];
		},

		getFormStatesDynamicClientSideTypes: function() { return formStatesDynamicClientSideTypes; },

		hasFormState: function(name) {
			return typeof (formStates[name]) !== 'undefined';
		},

		hasResolvedFormState: hasResolvedFormState,

		hasFormStateWithData: hasFormStateWithData,

		clearFormState: function(formName) {
			delete formStates[formName];
			delete formStatesDynamicClientSideTypes[formName];
		},
		
		clearSabloInfo:function (){
	        webStorage.session.remove('windownr');
	        webStorage.session.remove('clientnr');
		},

		initFormState: function(formName, beanDatas, componentSpecNames: { [componentName: string]: string }, formProperties, formScope, resolve) {
			var state = formStates[formName];
			// if the form is already initialized or if the beanDatas are not given, return that
			if (!state && beanDatas) {
				var model = {}
				var api = {}

				// init all the objects for the beans.
				state = formStates[formName] = { model: model, api: api, properties: formProperties, initializing: true, componentSpecNames: componentSpecNames };
				for (var beanName in beanDatas) {
					model[beanName] = new sablo_app.Model();
					api[beanName] = {};
				}
			}

			if (resolve || resolve === undefined) this.resolveFormState(formName, true); // this is used by pure sablo samples; Servoy will always NOT resolve here

			return state;
		},

		updateScopeForState: updateScopeForState,

		// form state has is now ready for use (even though it might not have initial data)
		resolveFormState: function(formName:string, skipTestResolving?:boolean):sablo.FormState {
			var formState = formStates[formName];
			if (!skipTestResolving && !formState.resolving) {
				if ($log.debugEnabled) $log.debug('sbl * form: ' + formName + ' was not in resolving state anymore when resolve was attempted...');
				if (deferredFormStates[formName]) $log.warn("Form " + formName + " has a deferredFormState; resolving denied/postponed though as the form is no longer in resolving state... It might resolve and execute deferred later.");
				//				if (deferredFormStates[formName]) deferredFormStates[formName].reject();
				//				delete deferredFormStates[formName];
				return null;
			}
			delete formState.resolving;
			formState.resolved = true;
			if (!formState.initializing) {
				formState.addWatches(); // so it already got initial data a while ago - restore watches then
				if (deferredFormStatesWithData[formName]) {
					if (typeof (formState) !== 'undefined') deferredFormStatesWithData[formName].resolve(formState);
					delete deferredFormStatesWithData[formName];
				}
			}
			if (deferredFormStates[formName]) {
				if (typeof (formState) !== 'undefined') deferredFormStates[formName].resolve(formState);
				delete deferredFormStates[formName];
			}
			if ($log.debugEnabled) $log.debug('sbl * Resolved form: ' + formName);
			return formState;
		},

		// form state has data but is not ready to be used (maybe it was hidden / temporarily with DOM disposed)
		unResolveFormState: function(formName) {
			if (formStates[formName]) {
				if ($log.debugEnabled) $log.debug('sbl * Unresolving form: ' + formName + ". Was: resolving=" + formStates[formName].resolving + ", resolved=" + formStates[formName].resolved);
				delete formStates[formName].resolving;
				delete formStates[formName].resolved;
			}
		},

		// requestDataCallback gets 2 parameters, the initalFormData and the currentFormState
		requestInitialData: function(formName, requestDataCallback) {
			var formState = formStates[formName];

			if (formState.initialDataRequested) return;
			formState.initialDataRequested = true;
			if ($log.debugEnabled) $log.debug('sbl * Requesting initial data: ' + formName);

			// send the special request initial data for this form 
			// this can also make the form (IFormUI instance) on the server if that is not already done
			callService('formService', 'requestData', { formname: formName }, false).then(function(initialFormData) {
				if ($log.debugEnabled) $log.debug('sbl * Initial data received: ' + formName);
				if (!formStates[formName]) {
					if ($log.debugEnabled) $log.debug('sbl * Initial data dropped; form state was completely removed meanwhile: ' + formName);
					return; // a recreateUI might have completely dropped the form state meanwhile so another initial data req. will follow when neeeded; this one can be discarded
				}
				// it is possible that the form was unresolved meanwhile; so get it nicely just in case we have to wait for it to be resolved again TODO should we force load it again using formResolver.prepareUnresolvedFormForUse(...)? (we used that at API calls but those are blocking on server)
				getFormState(formName).then(function(formState) {
					if ($log.debugEnabled) $log.debug('sbl * Applying initial data: ' + formName);
					initialFormData = initialFormData[0]; // ret value is an one item array
					if (initialFormData) {
						// if the formState is on the server but not here anymore, skip it. 
						// this can happen with a refresh on the browser.
						if (typeof (formState) == 'undefined') return;

						var formModel = formState.model;
						var initialFormProperties = initialFormData['']; // form properties
						if (initialFormProperties) {
							// currently what server side sends for properties of the form itself doesn't need client side conversions/types
							if (!formModel['']) formModel[''] = {};
							for (var p in initialFormProperties) {
								formModel[''][p] = initialFormProperties[p];
							}
						}

						for (var beanname in initialFormData) {
							// copy over the initialData, skip for form properties (beanname empty) as they were already dealt with
							if (beanname != '') {
								var beanDynamicTypesHolder = $sabloUtils.getOrCreateInDepthProperty(formStatesDynamicClientSideTypes, formName, beanname);
								applyBeanData(formModel[beanname], initialFormData[beanname], formState.properties.designSize, getChangeNotifierGenerator(formName, beanname), formState.componentSpecNames[beanname], beanDynamicTypesHolder, formState.getScope());
							}
						}
					}

					formState.addWatches();
					delete formState.initializing;
					delete formState.initialDataRequested;

					if (deferredFormStatesWithData[formName]) {
						if (typeof (formStates[formName]) !== 'undefined') deferredFormStatesWithData[formName].resolve(formStates[formName]);
						delete deferredFormStatesWithData[formName];
					}

					if (deferredFormStates[formName]) {
						// this should never happen cause at this point that defer should already have been executed and removed (when the form got resolved)
						$log.warn('sbl * form: ' + formName + ' had deferred form states when receiving initial data. Resolving those as well...');
						if (typeof (formStates[formName]) !== 'undefined') deferredFormStates[formName].resolve(formStates[formName]);
						delete deferredFormStates[formName];
					}

					if (requestDataCallback) {
						requestDataCallback(initialFormData, formState);
					}
				},
					function(err) {
						$log.error("Error getting form state: " + err);
					});
			},
				function(err) {
					$log.error("Error requesting form data: " + err);
				});
		},

		sendChanges: sendChanges,
		callService: callService,
		addToCurrentServiceCall: addToCurrentServiceCall,

		getExecutor: function(formName) {
			return {
				on: function(beanName, eventName, property/* this arg seems to never be used */, args, rowId) {
					var onFunction = function(formState, requestInfo) {
						// this is onaction, onfocuslost which is really configured in the html so it really 
						// is something that goes to the server
						const componentSpec = $typesRegistry.getComponentSpecification($sabloUtils.getInDepthProperty(formState, "componentSpecNames", beanName));
						const handlerSpec = componentSpec?.getHandler(eventName);
						var newargs = $sabloUtils.getEventArgs(args, eventName, handlerSpec);
						var data = {}
						if (property) { // TODO remove this arg completely
							throw new Error("sbl * unexpected legacy arg 'property' on getExecutor().on()...");
						}
						var cmd = { formname: formName, beanname: beanName, event: eventName, args: newargs, changes: data }
						if (rowId) cmd['rowId'] = rowId
						
						let promise = callService('formService', 'executeEvent', cmd, false);
						if (requestInfo) (promise as any).requestInfo = requestInfo; // this arg is only set if it has to wait for a form state below

						return $webSocket.wrapPromiseToPropagateCustomRequestInfoInternal(promise, promise.then((retVal) => {
							return $sabloConverters.convertFromServerToClient(retVal, handlerSpec?.returnType,
							         undefined, undefined, undefined, undefined, $sabloUtils.PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES);
						}));
					};
					if (hasFormStateWithData(formName)) {
						return onFunction(formStates[formName], undefined);
					} else {
						$log.warn("Cannot executeEvent '" + eventName + "' for '" + beanName + "' because the form '" + formName + "' has not been resolved yet.");
						// we need to return a promise
						var deferred = $q.defer();
						deferred.resolve();
						return deferred.promise;
                        // const requestInfoKeeper: { requestInfo?: any } = {};
                        // return $webSocket.wrapPromiseToPropagateCustomRequestInfoInternal(requestInfoKeeper,
                        //     getFormStateWithData(formName).then(function(formState) {
                        //             return onFunction(formState, requestInfoKeeper.requestInfo);
                        //         },
                        //         function(err) {
                        //             $log.error("Error getting form state: " + err);
                        //         })
                        // );
					}
				}
			}
		},
		getLanguageAndCountryFromBrowser: function() {
			var langAndCountry;
			var browserLanguages = $window.navigator['languages'];
			// this returns first one of the languages array if the browser supports this (Chrome and FF) else it falls back to language or userLanguage (IE, and IE seems to return the right one from there)
			if (browserLanguages && browserLanguages.length > 0) {
				langAndCountry = browserLanguages[0];
				if (browserLanguages.length > 1 && langAndCountry.indexOf('-') === -1
					&& browserLanguages[1].indexOf(langAndCountry + '-') == 0) {
					// if the first language in the list doesn't specify country, see if the following one is the same language but with a country specified (for example browser could give a list of "en", "en-GB", ...)
					langAndCountry = browserLanguages[1];
				}
			} else {
				langAndCountry = ($window.navigator.language || $window.navigator['userLanguage']);
			}
			// in some weird scenario in firefox is not set, default it to en
			if (!langAndCountry) langAndCountry = 'en';
			return langAndCountry;
		},
		getLocale: function() {
			if (!locale) {
				var langAndCountry = this.getLanguageAndCountryFromBrowser();
				var array = langAndCountry.split("-");
				locale = { language: array[0], country: array[1], full: langAndCountry };
			}
			return locale;
		},
		setLocale: function(loc) {
			locale = loc;
		},
		// Get the current form url, when not set yet start getting it from the server (when fetch is not false)
		getCurrentFormUrl: function(fetch) {
			if (currentFormUrl == null && fetch != false) {
				// not set yet, fetch from server
				currentFormUrl = ""
				callService('formService', 'getCurrentFormUrl', null, false)
					.then(function(url) {
						currentFormUrl = url;
					}, function(url) {
						currentFormUrl = null;
					});
				return null;
			}
			return currentFormUrl == "" ? null : currentFormUrl;
		},

		// set current form url, push to server when push is not false
		setCurrentFormUrl: function(url, push) {
			currentFormUrl = url
			if (push != false) callService('formService', 'setCurrentFormUrl', { url: url }, true);
		}
	}
}).filter('trustAsHtml', ['$sce', function($sce: angular.ISCEService) {
	return function(input: string, trustAsHtml?: boolean) {
		if (input && trustAsHtml) {
			return $sce.trustAsHtml('' + input);
		}
		return input;
	};
}])

	//IMPORTANT: always add a sablo-tabseq directive with sablo-tabseq-config="{root: true}" to $rootScope element
	//- when sablo-tabseq-config="{container: true}" is used (same for 'root: true'), no real tabindex property will be set on the DOM element, it's only for grouping child sablo-tabseq
	//- this directive requires full jquery to be loaded before angular.js; jQuery lite that ships with angular doesn't
	//have trigger() that bubbles up; if needed that could be implemented using triggerHandler, going from parent to parent
	//- note: -2 means an element and it's children should be skipped by tabsequence
	.directive('sabloTabseq', ['$parse', '$timeout', function($parse: angular.IParseService, $timeout: angular.ITimeoutService) {
		return {
			restrict: 'A',

			controller: function($scope: angular.IScope, $element: JQuery, $attrs: angular.IAttributes) {
				// called by angular in parents first then in children
				var designTabSeq = $parse($attrs['sabloTabseq'])($scope);
				if (!designTabSeq) designTabSeq = 0;
				var config = $parse($attrs['sabloTabseqConfig'])($scope);
				var tabSeqCallback = $parse($attrs['sablotabseqcallback']);

				var designChildIndexToArrayPosition = {};
				var designChildTabSeq = []; // contains ordered numbers that will be keys in 'runtimeChildIndexes'; can have duplicates
				var runtimeChildIndexes = {}; // map designChildIndex[i] -> runtimeIndex for child or designChildIndex[i] -> [runtimeIndex1, runtimeIndex2] in case there are multiple equal design time indexes
				var runtimeIndex; // initialized a bit lower
				var initializing = true;
				var isEnabled = true;

				function recalculateChildRuntimeIndexesStartingAt(posInDesignArray /*inclusive*/, triggeredByParent) {
					if (designTabSeq === -2) return;

					if (!isEnabled || runtimeIndex.startIndex === -1) {

						runtimeIndex.nextAvailableIndex = runtimeIndex.startIndex;
						runtimeIndex.startIndex = -1;
					}
					else if (designTabSeq === 0) {
						// this element doesn't set any tabIndex attribute (default behavior)
						runtimeIndex.nextAvailableIndex = runtimeIndex.startIndex;
						runtimeIndex.startIndex = 0;
					}
					else if (runtimeIndex.startIndex === 0) {
						runtimeIndex.nextAvailableIndex = 0;
					}
					else if (runtimeIndex.nextAvailableIndex === -1) {
						var reservedGap = (config && config.reservedGap) ? config.reservedGap : 0;
						runtimeIndex.nextAvailableIndex = runtimeIndex.startIndex + reservedGap;
					}

					if (posInDesignArray === 0) updateCurrentDomElTabIndex();

					var recalculateStartIndex = runtimeIndex.startIndex;
					if (posInDesignArray > 0 && posInDesignArray - 1 < designChildTabSeq.length) {
						var runtimeCI = runtimeChildIndexes[designChildTabSeq[posInDesignArray - 1]]; // this can be an array in case of multiple equal design indexes being siblings
						recalculateStartIndex = runtimeCI.push ? runtimeCI[runtimeCI.length - 1].nextAvailableIndex : runtimeCI.nextAvailableIndex;
					}

					for (var i = posInDesignArray; i < designChildTabSeq.length; i++) {
						var childRuntimeIndex = runtimeChildIndexes[designChildTabSeq[i]];
						if (childRuntimeIndex.push) {
							// multiple equal design time indexes as siblings
							var max = recalculateStartIndex;
							for (var k in childRuntimeIndex) {
								childRuntimeIndex[k].startIndex = recalculateStartIndex;
								childRuntimeIndex[k].recalculateChildRuntimeIndexesStartingAt(0, true); // call recalculate on whole child; normally it only makes sense for same index siblings if they are not themselfes containers, just apply the given value
								if (max < childRuntimeIndex[k].nextAvailableIndex)
									max = childRuntimeIndex[k].nextAvailableIndex;
							}
							recalculateStartIndex = max;
						} else {
							childRuntimeIndex.startIndex = recalculateStartIndex;
							childRuntimeIndex.recalculateChildRuntimeIndexesStartingAt(0, true); // call recalculate on whole child
							recalculateStartIndex = childRuntimeIndex.nextAvailableIndex;
						}
					}

					if (initializing) initializing = undefined; // it's now considered initialized as first runtime index caluculation is done

					var parentRecalculateNeeded;
					if (runtimeIndex.startIndex !== 0 && runtimeIndex.startIndex !== -1) {
						var ownTabIndexBump = hasOwnTabIndex() ? 1 : 0;
						parentRecalculateNeeded = (runtimeIndex.nextAvailableIndex < recalculateStartIndex + ownTabIndexBump);
						var reservedGap = (config && config.reservedGap) ? config.reservedGap : 0;
						if (parentRecalculateNeeded) runtimeIndex.nextAvailableIndex = recalculateStartIndex + reservedGap + ownTabIndexBump;
					} else {
						// start index 0 means default (no tabIndex attr. set)
						parentRecalculateNeeded = false;
					}

					// if this container now needs more tab indexes than it was reserved; a recalculate on parent needs to be triggered in this case
					if (parentRecalculateNeeded && !triggeredByParent) $element.parent().trigger("recalculatePSTS", [designTabSeq]);
				}

				function hasOwnTabIndex() {
					return (!config || !(config.container || config.root));
				}

				function updateCurrentDomElTabIndex() {
					if (hasOwnTabIndex()) {
						if (runtimeIndex.startIndex !== 0)
						{	
							$attrs.$set('tabindex', runtimeIndex.startIndex);
						}
						else 
						{	
							$attrs.$set('tabindex', undefined);
						}
						if (tabSeqCallback)
						{
							tabSeqCallback($scope, {$tabindex : (runtimeIndex.startIndex !== 0 ? runtimeIndex.startIndex : undefined)});
						}	
					}
				}

				// runtime index -1 == SKIP focus traversal in browser
				// runtime index  0 == DEFAULT == design tab seq 0 (not tabIndex attr set to element or it's children)
				runtimeIndex = { startIndex: -1, nextAvailableIndex: -1, recalculateChildRuntimeIndexesStartingAt: recalculateChildRuntimeIndexesStartingAt };
				updateCurrentDomElTabIndex(); // -1 runtime initially for all (in case some node in the tree has -2 design (skip) and children have >= 0, at runtime all children should be excluded as wel)

				// handle event: Child Servoy Tab Sequence registered
				function registerChildHandler(event, designChildIndex, runtimeChildIndex) {
					if (designTabSeq == -2 || designChildIndex == -2) return false;

					// insert it sorted
					var posInDesignArray = 0;
					for (var tz = 0; tz < designChildTabSeq.length && designChildTabSeq[tz] < designChildIndex; tz++) {
						posInDesignArray = tz + 1;
					}
					if (posInDesignArray === designChildTabSeq.length || designChildTabSeq[posInDesignArray] > designChildIndex) {
						designChildTabSeq.splice(posInDesignArray, 0, designChildIndex);

						// always keep in designChildIndexToArrayPosition[i] the first occurrance of design index i in the sorted designChildTabSeq array
						for (var tz = posInDesignArray; tz < designChildTabSeq.length; tz++) {
							designChildIndexToArrayPosition[designChildTabSeq[tz]] = tz;
						}
						runtimeChildIndexes[designChildIndex] = runtimeChildIndex;
					} else {
						// its == that means that we have dupliate design indexes; we treat this special - all same design index children as a list in one runtime index array cell
						if (!runtimeChildIndexes[designChildIndex].push) {
							runtimeChildIndexes[designChildIndex] = [runtimeChildIndexes[designChildIndex]];
						}
						runtimeChildIndexes[designChildIndex].push(runtimeChildIndex);
					}

					return false;
				}
				$element.on("registerCSTS", registerChildHandler);

				function unregisterChildHandler(event, designChildIndex, runtimeChildIndex) {
					if (designTabSeq == -2 || designChildIndex == -2) return false;

					var posInDesignArray = designChildIndexToArrayPosition[designChildIndex];
					if (angular.isDefined(posInDesignArray)) {
						var keyInRuntimeArray = designChildTabSeq[posInDesignArray];
						var multipleEqualDesignValues = runtimeChildIndexes[keyInRuntimeArray].push;
						if (!multipleEqualDesignValues) {
							delete designChildIndexToArrayPosition[designChildIndex];
							for (var tmp in designChildIndexToArrayPosition) {
								if (designChildIndexToArrayPosition[tmp] > posInDesignArray) designChildIndexToArrayPosition[tmp]--;
							}
							designChildTabSeq.splice(posInDesignArray, 1);
							delete runtimeChildIndexes[keyInRuntimeArray];
						} else {
							runtimeChildIndexes[keyInRuntimeArray].splice(runtimeChildIndexes[keyInRuntimeArray].indexOf(runtimeChildIndex), 1);
							if (runtimeChildIndexes[keyInRuntimeArray].length == 1) runtimeChildIndexes[keyInRuntimeArray] = runtimeChildIndexes[keyInRuntimeArray][0];
						}
					}
					return false;
				}
				$element.on("unregisterCSTS", unregisterChildHandler);

				// handle event: child tree was now linked or some child needs extra indexes; runtime indexes can be computed starting at the given child;
				// recalculate Parent Servoy Tab Sequence
				function recalculateIndexesHandler(event, designChildIndex, initialRootRecalculate) {
					if (designTabSeq == -2 || designChildIndex == -2) return false;

					if (!initializing) {
						// a new child is ready/linked; recalculate tab indexes for it and after it
						var startIdx = (designChildIndexToArrayPosition && designChildIndexToArrayPosition[designChildIndex] != undefined) ? designChildIndexToArrayPosition[designChildIndex] : 0;
						recalculateChildRuntimeIndexesStartingAt(startIdx, false);
					} else if (initialRootRecalculate) {
						// this is $rootScope (one $parent extra cause the directive creates it); we always assume a sabloTabseq directive is bound to it;
						// now that it is linked we can do initial calculation of tre
						runtimeIndex.startIndex = runtimeIndex.nextAvailableIndex = 1;
						recalculateChildRuntimeIndexesStartingAt(0, true);
					} // else wait for parent tabSeq directives to get linked as well

					return false;
				}
				$element.on("recalculatePSTS", recalculateIndexesHandler);

				var deregisterAttrObserver = $scope.$watch($attrs['sabloTabseq'], function(newDesignTabSeq) {
					if (designTabSeq !== newDesignTabSeq && !(config && config.root)) {
						if (designTabSeq != -2) $element.parent().trigger("unregisterCSTS", [designTabSeq, runtimeIndex]);
						designTabSeq = newDesignTabSeq;
						if (!designTabSeq) designTabSeq = 0;
						runtimeIndex.startIndex = -1;
						runtimeIndex.nextAvailableIndex = -1;
						initializing = true;

						if (designTabSeq != -2) {
							$element.parent().trigger("registerCSTS", [designTabSeq, runtimeIndex]);
							$element.parent().trigger("recalculatePSTS", [designTabSeq]); // here we could send [0] instead of [designTabSeq] - it would potentially calculate more but start again from first parent available index, not higher index (the end user behavior being the same)
						} else {
							updateCurrentDomElTabIndex(); // -1 runtime
						}
					}
				});

				function disableTabseq() {
					isEnabled = false;
					recalculateChildRuntimeIndexesStartingAt(0, true);
					return false;
				}
				$element.on("disableTabseq", disableTabseq);

				function enableTabseq() {
					isEnabled = true;
					$element.parent().trigger("recalculatePSTS", [0]);
					return false;
				}
				$element.on("enableTabseq", enableTabseq);

				if (designTabSeq != -2 && !(config && config.root)) {
					if ($element.parent().length === 0) {
						var elementWatch = $scope.$watch(function() { return $element.parent().length }, function(newValue) {
							if (newValue == 1) {
								$element.parent().trigger("registerCSTS", [designTabSeq, runtimeIndex]);
								elementWatch();
							}
						});
					}
					else {
						$element.parent().trigger("registerCSTS", [designTabSeq, runtimeIndex]);
					}

					function destroyHandler(event) {
						// unregister current tabSeq from parent tabSeq container
						$element.parent().trigger("unregisterCSTS", [designTabSeq, runtimeIndex]);

						// clean-up listeners in timeout, because in case of a container
						// if we would removed it now, the children would call unregister to
						// the wrong container (the parent of the correct container)
						$timeout(function() {
							$element.off("registerCSTS", registerChildHandler);
							$element.off("unregisterCSTS", unregisterChildHandler);
							$element.off("recalculatePSTS", recalculateIndexesHandler);
						}, 0);
						deregDestroy();
						deregisterAttrObserver();

						return false;
					}
					var deregDestroy = $scope.$on("$destroy", destroyHandler); // I don't use here $element.on("$destroy"... because uigrid reuses it's row divs, and that event gets called but the DOM element continues to be used, just changes tabIndex attribute... and the this directive's controller/link doesn't get called again 
				}
			},

			link: function(scope: angular.IScope, element: JQuery, attrs: angular.IAttributes) {
				// called by angular in children first, then in parents
				var config = $parse(attrs['sabloTabseqConfig'])(scope);

				// check to see if this is the top-most tabSeq container
				if (config && config.root) {
					// it's root tab seq container (so no parent); just emit on self to do initial tree calculation
					element.trigger("recalculatePSTS", [0, true]);
				} else {
					var designTabSeq = $parse(attrs['sabloTabseq'])(scope);
					if (designTabSeq != -2) {
						if (element.parent().length === 0) {
							var elementWatch = scope.$watch(function() { return element.parent().length }, function(newValue) {
								if (newValue == 1) {
									element.parent().trigger("recalculatePSTS", [designTabSeq ? designTabSeq : 0]);
									elementWatch();
								}
							});
						}
						else {
							element.parent().trigger("recalculatePSTS", [designTabSeq ? designTabSeq : 0]);
						}
					}
				}
			}

		};
	}]).run(function($sabloConverters: sablo.ISabloConverters, $typesRegistry: sablo.ITypesRegistry) {
		// configure Date type but don't overwrite it when there is already a registered object.
		$typesRegistry.registerGlobalType('Date', {
			
	        fromServerToClient(serverJSONValue: any, _currentClientValue: Date, _componentScope: angular.IScope, _propertyContext: sablo.IPropertyContext): Date {
				return typeof (serverJSONValue) === "number" ? new Date(serverJSONValue) : serverJSONValue;
	        },
	        
	        fromClientToServer(newClientData: any, _oldClientData: Date, _scope: angular.IScope, _propertyContext: sablo.IPropertyContext): any {
				if (newClientData === 0) return newClientData;
				if (!newClientData) return null;

				var r = newClientData;
				if (typeof newClientData === 'string') r = new Date(newClientData);
				if (typeof newClientData === 'number') return r;
				if (isNaN(r.getTime())) throw new Error("Invalid date/time value: " + newClientData);
				return r.getTime();
	        },
	        
	        updateAngularScope(_clientValue: Date, _componentScope: angular.IScope): void {
				// nothing to do here
	        }
	        
		}, true);

		// other small types can be added here
	});
