angular.module('sabloApp', ['webSocketModule', 'webStorageModule']).config(function($provide, $logProvider,$rootScopeProvider) {
	____logProvider = $logProvider; // just in case someone wants to alter debug at runtime from browser console for example
	$logProvider.debugEnabled(false);
	$provide.decorator("$log", function($delegate) {
		Object.defineProperty($delegate, "debugEnabled", {
			enumerable: false,
			configurable: false,
			get: $logProvider.debugEnabled
		});
		return $delegate;
	})
	$rootScopeProvider.digestTtl(15); 
}).value("$sabloConstants",  {
	modelChangeNotifier: "$modelChangeNotifier"
}).factory('$sabloApplication', function ($rootScope, $window, $timeout, $q, $log, $webSocket, $sabloConverters, $sabloUtils, $sabloConstants, webStorage) {
	// formName:[beanname:{property1:1,property2:"test"}] needs to be synced to and from server
	// this holds the form model with all the data, per form is this the "synced" view of the the IFormUI on the server 
	// (3 way binding)
	var currentFormUrl = null;
	var formStates = {};
	var formStatesConversionInfo = {};

	var locale = null;

	var deferredFormStates = {};
	var deferredFormStatesWithData = {};
	var getChangeNotifierGenerator = function(formName, beanName) {
		return function(property) {
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

	var getComponentChanges = function(now, prev, beanConversionInfo, parentSize, property) {
		var changes = {}
		if (property) {
			if (beanConversionInfo && beanConversionInfo[property]) changes[property] = $sabloConverters.convertFromClientToServer(now, beanConversionInfo[property], prev);
			else changes[property] = $sabloUtils.convertClientObject(now)
		} else {
			// first build up a list of all the properties both have.
			var fulllist = $sabloUtils.getCombinedPropertyNames(now, prev);
			var prop;

			for (prop in fulllist) {
				var changed;
				if (prev && now) {
					changed = $sabloUtils.isChanged(now[prop], prev[prop], beanConversionInfo ? beanConversionInfo[prop] : undefined)
				} else {
					changed = true; // true if just one of them is undefined; both cannot be undefined at this point if we are already iterating on combined property names
				}

				if (changed) {
					changes[prop] = now[prop];
				}
			}
			for (prop in changes) {
				if (beanConversionInfo && beanConversionInfo[prop]) changes[prop] = $sabloConverters.convertFromClientToServer(changes[prop], beanConversionInfo[prop], prev ? prev[prop] : undefined);
				else changes[prop] = $sabloUtils.convertClientObject(changes[prop])
			}
		}
		return changes;
	};

	var sendChanges = function(now, prev, formname, beanname, property) {
		var beanConversionInfo = $sabloUtils.getInDepthProperty(formStatesConversionInfo, formname, beanname);

		var changes = getComponentChanges(now, prev, beanConversionInfo, formStates[formname].properties.designSize, property);
		if (Object.getOwnPropertyNames(changes).length > 0) {
			// if this is a simple property change without any special conversions then then push the old value.
			if (angular.isDefined(property) && !(beanConversionInfo && beanConversionInfo[property])) {
				var oldvalues ={};
				oldvalues[property] = $sabloUtils.convertClientObject(prev)
				callService('formService', 'dataPush', {formname:formname,beanname:beanname,changes:changes,oldvalues:oldvalues}, true)
			}
			else {
				callService('formService', 'dataPush', {formname:formname,beanname:beanname,changes:changes}, true)
			}

		}
	};

	var applyBeanData = function(beanModel, beanData, containerSize, changeNotifierGenerator, beanConversionInfo, newConversionInfo, componentScope) {

		if (newConversionInfo) { // then means beanConversionInfo should also be defined - we assume that
			// beanConversionInfo will be granularly updated in the loop below
			// (to not drop other property conversion info when only one property is being applied granularly to the bean)
			beanData = $sabloConverters.convertFromServerToClient(beanData, newConversionInfo, beanModel, componentScope, function() { return beanModel });
		}

		for(var key in beanData) {
			// remember conversion info for when it will be sent back to server - it might need special conversion as well
			if (newConversionInfo && newConversionInfo[key]) {
				// if the value changed and it wants to be in control of it's changes, or if the conversion info for this value changed (thus possibly preparing an old value for being change-aware without changing the value reference)
				if ((beanModel[key] !== beanData[key] || beanConversionInfo[key] !== newConversionInfo[key])
						&& beanData[key] && beanData[key][$sabloConverters.INTERNAL_IMPL] && beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
					beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifierGenerator(key));
				}
				beanConversionInfo[key] = newConversionInfo[key];
			} else if (beanConversionInfo && angular.isDefined(beanConversionInfo[key])) delete beanConversionInfo[key]; // this prop. no longer has conversion info!

			// also make location and size available in model
			beanModel[key] = beanData[key];
		}
		// if the model had a change notifier call it now after everything is set.
		var modelChangeFunction = beanModel[$sabloConstants.modelChangeNotifier];
		if (modelChangeFunction){
			for(var key in beanData) {
				modelChangeFunction(key,beanModel[key]);
			}
		}
	}

	var updateScopeForState = function(formName, formScope, state) {
		if (!state) state = formStates[formName];

		if (state && state.model) {
			for (var componentName in state.model) {
				if (componentName !== '') {
					$sabloConverters.updateAngularScope(state.model[componentName],
							$sabloUtils.getInDepthProperty(formStatesConversionInfo, formName, componentName), formScope);
				}
			}
		} else if (formScope) {
			$log.error("When updating scope - with non-null value state.model is undefined. This is unexpected...");
		} // recreateUI destroying it completely? or complete form unload? or maybe partial load in hidden div interrupted by real show of same form?
	}

	var wsSession = null;
	function getSession() {
		if (wsSession == null) throw "Session is not created yet, first call connect()";
		return wsSession;
	}

	var currentServiceCallCallbacks = []
	var currentServiceCallDone
	var currentServiceCallWaiting = 0
	function addToCurrentServiceCall(func) {
		if (currentServiceCallWaiting == 0) {
			// No service call currently running, call the function now
			$timeout(function(){func.apply();})
		}
		else {
			currentServiceCallCallbacks.push(func)
		}
	}

	function callServiceCallbacksWhenDone() {
		if (currentServiceCallDone || --currentServiceCallWaiting == 0) {
			currentServiceCallWaiting = 0
			currentServiceCallTimeouts.map(function (id) { return clearTimeout(id) })
			var tmp = currentServiceCallCallbacks
			currentServiceCallCallbacks = []
			tmp.map(function (func) { func.apply() })
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

	function waitForServiceCallbacks(promise, times) {
		if (currentServiceCallWaiting >  0) {
			// Already waiting
			return promise
		}

		currentServiceCallDone = false
		currentServiceCallWaiting = times.length
		currentServiceCallTimeouts = times.map(function (t) { return setTimeout(callServiceCallbacksWhenDone, t) })
		return promise.then(markServiceCallDone, markServiceCallFailed)
	}

	function callService(serviceName, methodName, argsObject, async) {
		var promise = getSession().callService(serviceName, methodName, argsObject, async)
		return async ? promise :  waitForServiceCallbacks(promise, [100, 200, 500, 1000, 3000, 5000])
	}

	var getSessionId = function() {
		var sessionId = webStorage.session.get('sessionid')
		if (sessionId) {
			return sessionId;
		}
		return $webSocket.getURLParameter('sessionid');
	}

	var getWindowName = function() {
		return $webSocket.getURLParameter('windowname');
	}

	var getWindowId = function() {
		return webStorage.session.get('windowid');
	}

	var getWindowUrl = function(windowname) {
		return "index.html?windowname=" + encodeURIComponent(windowname) + "&sessionid="+getSessionId();
	}

	var formResolver = null;
	var apiCallDeferredQueue = []

	function hasResolvedFormState(name) {
		return typeof(formStates[name]) !== 'undefined' && formStates[name].resolved;
	}

	return {
		connect : function(context, args, queryArgs) {
			wsSession = $webSocket.connect(context, args, queryArgs);

			wsSession.onopen(function(evt) {
				if (evt.isReconnect) {
					// reload site
					$window.location.reload();
				}
			});

			wsSession.onMessageObject(function (msg, conversionInfo) {
				// data got back from the server
				for(var formname in msg.forms) {
					// TODO: replace with service call "applyChanges(form, changes)"
					// current model
					// if the formState is on the server but not here anymore, skip it. 
					// this can happen with a refresh on the browser.
					if (typeof(formStates[formname]) == 'undefined') continue;
					// we just checked before that formStates exists so getFormState(formname) deferr below will actually be instant right
					// it's called like this to reuse getFormState() similar to the rest of the code
					getFormState(formname).then(getFormMessageHandler(formname, msg, conversionInfo), 
							function(err) { $log.error("Error getting form state: " + err); });
				}

				if (conversionInfo && conversionInfo.call) msg.call = $sabloConverters.convertFromServerToClient(msg.call, conversionInfo.call, undefined, undefined, undefined);
				if (msg.call) {
					// {"call":{"form":"product","element":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}, // optionally "viewIndex":1 
					// "{ conversions: {product: {datatextfield1: {0: "Date"}}} }
					var call = msg.call;

					function executeAPICall(formState) {
						if (call.viewIndex != undefined) {
							var funcThis = formState.api[call.bean][call.viewIndex]; 
							if (funcThis)
							{
								var func = funcThis[call.api];
							}
							else
							{
								console.warn("cannot call " + call.api + " on " + call.bean + " because viewIndex "+ call.viewIndex +" api is not found")
							}
						}
						else if (call.propertyPath != undefined)
						{
							// handle nested components; the property path is an array of string or int keys going
							// through the form's model starting with the root bean name, then it's properties (that could be nested)
							// then maybe nested child properties and so on 
							var obj = formState.model;
							var pn;
							for (pn in call.propertyPath) obj = obj[call.propertyPath[pn]];
							var func = obj.api[call.api];
						}
						else {
							var funcThis = formState.api[call.bean];
							var func = funcThis[call.api];
						}
				
						var returnValue;
						if (!func) {
							console.warn("bean " + call.bean + " did not provide the api: " + call.api)
							returnValue = null;
						}
						else {
							returnValue = func.apply(funcThis, call.args);
						}
						return returnValue;
					};

					var previousApiCallPromise = null;
					if(apiCallDeferredQueue.length > 0) {
						previousApiCallPromise = apiCallDeferredQueue[apiCallDeferredQueue.length - 1].promise; 
					}
					apiCallDeferredQueue.push($q.defer());
					
					function resolveFormAndExecuteAPICall() {
						if (formResolver != null && !hasResolvedFormState(call.form)) {
							// this means that the form was shown and is now hidden/destroyed; but we still must handle API call to it!
							// see if the form needs to be loaded;
							if ($log.debugEnabled) $log.debug("sbl * Api call '" + call.api + "' to unresolved form " + call.form + "; will call prepareUnresolvedFormForUse.");
							formResolver.prepareUnresolvedFormForUse(call.form);
						}
						
						return getFormState(call.form).then(
								function() {
									// do it in timeout (after current digest cycle) so any form data change is already applied to the ui
									return $timeout(function() {
										return executeAPICall(formStates[call.form]); 
									}).then(function(result){
										return result;
									},function(err){
										return $q.reject(err);
									});
								},
								function(err) {
									$log.error("Error getting form state: " + err);		
									return $q.reject("Error getting form state: " + err);
								}).finally(function() {
									if(apiCallDeferredQueue.length > 0) {
										apiCallDeferredQueue.shift().resolve();	
									}
								});
						
					}
					
					if(previousApiCallPromise) {
						return previousApiCallPromise.then(
								function() {
									return resolveFormAndExecuteAPICall();
								},
								function(err) {
									$log.error("Error waiting for api call execute " + err);
									return $q.reject(err);
								});
					}
					else {
						return resolveFormAndExecuteAPICall();
					}	
				}
			});

			function getFormMessageHandler(formname, msg, conversionInfo) {
				return function (formState) {
					var formModel = formState.model;
					var newFormData = msg.forms[formname];
					var newFormProperties = newFormData['']; // form properties
					var newFormConversionInfo = (conversionInfo && conversionInfo.forms && conversionInfo.forms[formname]) ? conversionInfo.forms[formname] : undefined;

					if(newFormProperties) {
						if (newFormConversionInfo && newFormConversionInfo['']) newFormProperties = $sabloConverters.convertFromServerToClient(newFormProperties, newFormConversionInfo[''], formModel[''], formState.getScope(), function() { return formModel[''] });
						if (!formModel['']) formModel[''] = {};
						for(var p in newFormProperties) {
							formModel[''][p] = newFormProperties[p]; 
						} 
					}

					var watchesRemoved = formState.removeWatches(newFormData);
					try {
						for (var beanname in newFormData) {
							// copy over the changes, skip for form properties (beanname empty)
							if (beanname != '') {
								var newBeanConversionInfo = newFormConversionInfo ? newFormConversionInfo[beanname] : undefined;
								var beanConversionInfo = newBeanConversionInfo ? $sabloUtils.getOrCreateInDepthProperty(formStatesConversionInfo, formname, beanname) : $sabloUtils.getInDepthProperty(formStatesConversionInfo, formname, beanname);
								applyBeanData(formModel[beanname], newFormData[beanname], formState.properties.designSize, getChangeNotifierGenerator(formname, beanname), beanConversionInfo, newBeanConversionInfo, formState.getScope());
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

		disconnect : function() {
			$webSocket.disconnect();
		},

		contributeFormResolver: function(contributedFormResolver) {
			formResolver = contributedFormResolver;
		},

		getSessionId: getSessionId,
		getWindowName: getWindowName,
		getWindowId: getWindowId,
		getWindowUrl: getWindowUrl,

		// used by custom property component[] to implement nested component logic
		applyBeanData: applyBeanData,
		getComponentChanges: getComponentChanges,
		getChangeNotifierGenerator: getChangeNotifierGenerator,

		getFormState: getFormState,

		getFormStateWithData: getFormStateWithData,

		// this method should only be used to manipulate pre-resolve state if apps. need any; the returned form state if defined might not have any data in it or operational DOM/directives
		getFormStateEvenIfNotYetResolved: function(name) { 
			return formStates[name];
		},

		getFormStatesConversionInfo: function() { return formStatesConversionInfo; },

		hasFormState: function(name) {
			return typeof(formStates[name]) !== 'undefined';
		},

		hasResolvedFormState: hasResolvedFormState,

		hasFormStateWithData: function(name) {
			return typeof(formStates[name]) !== 'undefined' && !formStates[name].initializing && formStates[name].resolved;
		},

		clearFormState: function(formName) {
			delete formStates[formName];
			delete formStatesConversionInfo[formName];
		},

		initFormState: function(formName, beanDatas, formProperties, formScope, resolve) {
			var state = formStates[formName];
			// if the form is already initialized or if the beanDatas are not given, return that
			if (!state && beanDatas) {
				var model = {}
				var api = {}

				// init all the objects for the beans.
				state = formStates[formName] = { model: model, api: api, properties: formProperties, initializing: true};
				for(var beanName in beanDatas) {
					model[beanName] = {};
					api[beanName] = {};
				}
			}

			if (resolve || resolve === undefined) this.resolveFormState(formName, true);

			return state;
		},

		updateScopeForState: updateScopeForState,

		// form state has is now ready for use (even though it might not have initial data)
		resolveFormState: function(formName, skipTestResolving) {
			var formState = formStates[formName];
			if (!skipTestResolving && !formState.resolving) {
				if ($log.debugEnabled) $log.debug('sbl * form: ' + formName + ' was not in resolving state anymore ');
				if (deferredFormStates[formName]) console.log("form " + formName + " has a deferredFormState");
//				if (deferredFormStates[formName]) deferredFormStates[formName].reject();
//				delete deferredFormStates[formName];
				return null;
			}
			delete formState.resolving;
			formState.resolved = true;
			if (!formState.initializing) formState.addWatches(); // so it already got initial data a while ago - restore watches then
			if (deferredFormStates[formName]) {
				if (typeof(formState) !== 'undefined') deferredFormStates[formName].resolve(formState);
				delete deferredFormStates[formName];
			}
			if ($log.debugEnabled) $log.debug('sbl * Resolved form: ' + formName);
			return formState;
		},

		// form state has data but is not ready to be used (maybe it was hidden / temporarily with DOM disposed)
		unResolveFormState: function(formName) {
			if (formStates[formName]) {
				delete formStates[formName].resolving;
				delete formStates[formName].resolved;
			}
			if ($log.debugEnabled) $log.debug('sbl * Unresolved form: ' + formName);
		},

		// requestDataCallback gets 2 parameters, the initalFormData and the currentFormState
		requestInitialData: function(formName, requestDataCallback) {
			var formState = formStates[formName];

			if (formState.initialDataRequested) return;
			formState.initialDataRequested = true;
			if ($log.debugEnabled) $log.debug('sbl * Requesting initial data: ' + formName);

			// send the special request initial data for this form 
			// this can also make the form (IFormUI instance) on the server if that is not already done
			callService('formService', 'requestData', {formname:formName}, false).then(function (initialFormData) {
				if ($log.debugEnabled) $log.debug('sbl * Initial data received: ' + formName);
				if (!formStates[formName]) {
					if ($log.debugEnabled) $log.debug('sbl * Initial data dropped; form state was completely removed meanwhile: ' + formName);
					return; // a recreateUI might have completely dropped the form state meanwhile so another initial data req. will follow when neeeded; this one can be discarded
				}
				// it is possible that the form was unresolved meanwhile; so get it nicely just in case we have to wait for it to be resolved again TODO should we force load it again using formResolver.prepareUnresolvedFormForUse(...)? (we used that at API calls but those are blocking on server)
				getFormState(formName).then(function (formState)  {
					if ($log.debugEnabled) $log.debug('sbl * Applying initial data: ' + formName);
					initialFormData = initialFormData[0]; // ret value is an one item array; the item contains both data and conversion info
					if (initialFormData) {
						var conversionInfo = initialFormData.conversions;
						if (conversionInfo) delete initialFormData.conversions;

						// if the formState is on the server but not here anymore, skip it. 
						// this can happen with a refresh on the browser.
						if (typeof(formState) == 'undefined') return;

						var formModel = formState.model;
						var initialFormProperties = initialFormData['']; // form properties
						if (initialFormProperties) {
							if (conversionInfo && conversionInfo['']) initialFormProperties = $sabloConverters.convertFromServerToClient(initialFormProperties, conversionInfo[''], formModel[''], formState.getScope(), function () { return formModel[''] });
							if (!formModel['']) formModel[''] = {};
							for(var p in initialFormProperties) {
								formModel[''][p] = initialFormProperties[p]; 
							} 
						}

						for (var beanname in initialFormData) {
							// copy over the initialData, skip for form properties (beanname empty) as they were already dealt with
							if (beanname != '') {
								var initialBeanConversionInfo = conversionInfo ? conversionInfo[beanname] : undefined;
								var beanConversionInfo = initialBeanConversionInfo ? $sabloUtils.getOrCreateInDepthProperty(formStatesConversionInfo, formName, beanname) : $sabloUtils.getInDepthProperty(formStatesConversionInfo, formName, beanname);
								applyBeanData(formModel[beanname], initialFormData[beanname], formState.properties.designSize, getChangeNotifierGenerator(formName, beanname), beanConversionInfo, initialBeanConversionInfo, formState.getScope());
							}
						}
					}

					formState.addWatches();
					delete formState.initializing;
					delete formState.initialDataRequested;

					if (deferredFormStatesWithData[formName]) {
						if (typeof(formStates[formName]) !== 'undefined') deferredFormStatesWithData[formName].resolve(formStates[formName]);
						delete deferredFormStatesWithData[formName];
					}

					if (deferredFormStates[formName]) {
						// this should never happen cause at this point that deferr should be executed and removed
						if (typeof(formStates[formName]) !== 'undefined') deferredFormStates[formName].resolve(formStates[formName]);
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
				on: function(beanName,eventName,property,args,rowId) {
					return getFormStateWithData(formName).then(function (formState) {
						// this is onaction, onfocuslost which is really configured in the html so it really 
						// is something that goes to the server
						var newargs = $sabloUtils.getEventArgs(args,eventName);
						var data = {}
						if (property) {
							data[property] = formState.model[beanName][property];
						}
						var cmd = {formname:formName,beanname:beanName,event:eventName,args:newargs,changes:data}
						if (rowId) cmd.rowId = rowId
						return callService('formService', 'executeEvent', cmd, false)
					},
					function(err) {
						$log.error("Error getting form state: " + err);
					});
				}
			}
		},
		getLanguageAndCountryFromBrowser: function() {
			var langAndCountry;
			var browserLanguages = $window.navigator.languages;
			// this returns first one of the languages array if the browser supports this (Chrome and FF) else it falls back to language or userLanguage (IE, and IE seems to return the right one from there)
			if (browserLanguages && browserLanguages.length > 0) {
				langAndCountry = browserLanguages[0];
				if (browserLanguages.length > 1 && langAndCountry.indexOf('-') === -1
						&& browserLanguages[1].indexOf(langAndCountry + '-') == 0) {
					// if the first language in the list doesn't specify country, see if the following one is the same language but with a country specified (for example browser could give a list of "en", "en-GB", ...)
					langAndCountry = browserLanguages[1];
				}
			} else {
				langAndCountry = ($window.navigator.language || $window.navigator.userLanguage);
			}
			
			return langAndCountry;
		}, 
		getLocale: function() {
			if (!locale) {
				var langAndCountry = this.getLanguageAndCountryFromBrowser();
				var array = langAndCountry.split("-");
				locale = {language : array[0], country : array[1], full : langAndCountry};
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
			if (push != false) callService('formService', 'setCurrentFormUrl', {url:url}, true);
		}
	}
})

//IMPORTANT: always add a sablo-tabseq directive with sablo-tabseq-config="{root: true}" to $rootScope element
//- when sablo-tabseq-config="{container: true}" is used (same for 'root: true'), no real tabindex property will be set on the DOM element, it's only for grouping child sablo-tabseq
//- this directive requires full jquery to be loaded before angular.js; jQuery lite that ships with angular doesn't
//have trigger() that bubbles up; if needed that could be implemented using triggerHandler, going from parent to parent
//- note: -2 means an element and it's children should be skipped by tabsequence
.directive('sabloTabseq',  ['$parse', '$timeout', function ($parse,$timeout) {
	return {
		restrict: 'A',

		controller: function($scope, $element, $attrs) {
			// called by angular in parents first then in children
			var designTabSeq = $parse($attrs.sabloTabseq)($scope);
			if (!designTabSeq) designTabSeq = 0;
			var config = $parse($attrs.sabloTabseqConfig)($scope);

			var designChildIndexToArrayPosition = {};
			var designChildTabSeq = []; // contains ordered numbers that will be keys in 'runtimeChildIndexes'; can have duplicates
			var runtimeChildIndexes = {}; // map designChildIndex[i] -> runtimeIndex for child or designChildIndex[i] -> [runtimeIndex1, runtimeIndex2] in case there are multiple equal design time indexes
			var runtimeIndex; // initialized a bit lower
			var initializing = true;
			var isEnabled = true;

			function recalculateChildRuntimeIndexesStartingAt(posInDesignArray /*inclusive*/, triggeredByParent) {
				if (designTabSeq === -2) return;

				if(!isEnabled || runtimeIndex.startIndex === -1) {

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
				else if (runtimeIndex.nextAvailableIndex === -1)
				{
					var reservedGap = (config && config.reservedGap) ? config.reservedGap : 0;
					runtimeIndex.nextAvailableIndex = runtimeIndex.startIndex + reservedGap;
				}

				if (posInDesignArray === 0) updateCurrentDomElTabIndex();

				var recalculateStartIndex = runtimeIndex.startIndex;
				if (posInDesignArray > 0 && posInDesignArray - 1 < designChildTabSeq.length) {
					var runtimeCI = runtimeChildIndexes[designChildTabSeq[posInDesignArray - 1]]; // this can be an array in case of multiple equal design indexes being siblings
					recalculateStartIndex = runtimeCI.push ? runtimeCI[runtimeCI.length-1].nextAvailableIndex : runtimeCI.nextAvailableIndex;
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
					if (runtimeIndex.startIndex !== 0) $element.attr('tabIndex', runtimeIndex.startIndex);
					else $element.removeAttr('tabIndex');
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
					if (! runtimeChildIndexes[designChildIndex].push) {
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

			var deregisterAttrObserver = $scope.$watch($attrs.sabloTabseq, function (newDesignTabSeq) {
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
				if($element.parent().length === 0) {
					var elementWatch = $scope.$watch(function() { return $element.parent().length }, function(newValue) {
						if(newValue == 1) {
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
					$timeout(function(){
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

		link: function (scope, element, attrs) {
			// called by angular in children first, then in parents
			var config = $parse(attrs.sabloTabseqConfig)(scope);

			// check to see if this is the top-most tabSeq container
			if (config && config.root) {
				// it's root tab seq container (so no parent); just emit on self to do initial tree calculation
				element.trigger("recalculatePSTS", [0, true]);
			} else {
				var designTabSeq = $parse(attrs.sabloTabseq)(scope);
				if (designTabSeq != -2) {
					if(element.parent().length === 0) {
						var elementWatch = scope.$watch(function() { return element.parent().length }, function(newValue) {
							if(newValue == 1) {
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
}]).run(function ($sabloConverters) {
	// Date type -----------------------------------------------
	$sabloConverters.registerCustomPropertyHandler('Date', {
		fromServerToClient: function (serverJSONValue, currentClientValue, scope, modelGetter) {
			return typeof (serverJSONValue) === "number" ? new Date(serverJSONValue) : serverJSONValue;
		},

		fromClientToServer: function(newClientData, oldClientData) {
			if(!newClientData) return null;

			var r = newClientData;
			if(typeof newClientData === 'string') r = new Date(newClientData);
			if(typeof newClientData === 'number') return r;
			if (isNaN(r.getTime())) throw new Error("Invalid date/time value: " + newClientData);
			return r.getTime();
		},

		updateAngularScope: function(clientValue, componentScope) {
			// nothing to do here
		}
	});
	// other small types can be added here
});
