/**
 * Setup the webSocketModule.
 */

/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/window/window.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

var webSocketModule = angular.module('webSocketModule', ['pushToServerData']).config(function($provide, $logProvider,$rootScopeProvider) {
	window.____logProvider = $logProvider; // just in case someone wants to alter debug at runtime from browser console for example
	
	// log levels for when debugEnabled(true) is called - if that is false, these levels are irrelevant
	// any custom debug levels can be used as well - these are just stored here so that custom code can test the level and see if it should log it's message
	$logProvider.DEBUG = 1;
	$logProvider.SPAM = 2;
	
	$logProvider.debugLvl = $logProvider.DEBUG; // default value; if someone wants SPAM debug logging, they can just switch this
	$logProvider.debugLevel = function(val) {
		if (val) {
			$logProvider.debugLvl = val;
			return $logProvider;
		} else {
			return $logProvider.debugLvl;
		}
	}
	$logProvider.debugEnabled(false);
	
	$provide.decorator("$log", function($delegate) {
		Object.defineProperty($delegate, "debugEnabled", {
			enumerable: false,
			configurable: false,
			get: $logProvider.debugEnabled
		});
		Object.defineProperty($delegate, "debugLevel", {
			enumerable: false,
			configurable: false,
			get: $logProvider.debugLevel
		});
		Object.defineProperty($delegate, "DEBUG", {
			enumerable: false,
			configurable: false,
			value: $logProvider.DEBUG
		});
		Object.defineProperty($delegate, "SPAM", {
			enumerable: false,
			configurable: false,
			value: $logProvider.SPAM
		});
		return $delegate;
	})
	$rootScopeProvider.digestTtl(15); 
});

// declare module pushToServer that generated module "pushToServerData" depends on - so that all pushToServer information is already present when starting 'webSocketModule'
angular.module('pushToServer', []).factory('$propertyWatchesRegistry', function () {
	var propertiesThatShouldBeAutoPushedToServer = {}; // key == ("components" or "services"), value is something like {
	//                                                           "pck1Component1" : { "myDeepWatchedProperty" : true, "myShallowWatchedProperty" : false }
	//                                                       }
	
	function getPropertiesToAutoWatchForComponent(componentTypeName) {
		return propertiesThatShouldBeAutoPushedToServer["components"][componentTypeName];
	};
	
	function getPropertiesToAutoWatchForService(serviceTypeName) {
		return propertiesThatShouldBeAutoPushedToServer["services"][serviceTypeName];
	};
	
	// returns an array of watch unregister functions
	// propertiesToAutoWatch is meant to be the return value of getPropertiesToAutoWatchForComponent or getPropertiesToAutoWatchForService
	function watchDumbProperties(scope, model, propertiesToAutoWatch, changedCallbackFunction) {
		var unwatchF = [];
		function getChangeFunction(property, initialV) {
			var firstTime = true;
			return function(newValue, oldValue) {
				if (firstTime) {
					// value from server should not be sent back; but as directives in their controller methods can already change the values or properties
					// (so before the first watch execution) we can't just rely only on the "if (oldValue === newValue) return;" below cause in that case it won't send
					// a value that actually changed to server
					oldValue = initialV;
					initialV = undefined;
					firstTime = false;
				}
				if (oldValue === newValue) return;
				changedCallbackFunction(newValue, oldValue, property);
			}
		}
		function getWatchFunction(property) {
			return function() { 
				return model[property];
			}
		}
		for (var p in propertiesToAutoWatch) {
			var wf = getWatchFunction(p);
			unwatchF.push(scope.$watch(wf, getChangeFunction(p, wf()), propertiesToAutoWatch[p]));
		}
		
		return unwatchF;
	}
	
	return {
		
		getPropertiesToAutoWatchForComponent: getPropertiesToAutoWatchForComponent,
		
		// returns an array of watch unregister functions
		watchDumbPropertiesForComponent: function watchDumbPropertiesForComponent(scope, componentTypeName, model, changedCallbackFunction) {
			return watchDumbProperties(scope, model, getPropertiesToAutoWatchForComponent(componentTypeName), changedCallbackFunction);
		},
		
		// returns an array of watch unregister functions
		watchDumbPropertiesForService: function watchDumbPropertiesForService(scope, serviceTypeName, model, changedCallbackFunction) {
			return watchDumbProperties(scope, model, getPropertiesToAutoWatchForService(serviceTypeName), changedCallbackFunction);
		},
		
		clearAutoWatchPropertiesList: function () {
			propertiesThatShouldBeAutoPushedToServer = {};
		},
		
		// categoryName can be "components" or "services"
		// autoWatchPropertiesPerBaseWebObject is something like {
		//                                                           "pck1Component1" : { "myDeepWatchedProperty" : true, "myShallowWatchedProperty" : false }
		//                                                       }
		setAutoWatchPropertiesList: function (categoryName, autoWatchPropertiesPerBaseWebObject) {
			propertiesThatShouldBeAutoPushedToServer[categoryName] = autoWatchPropertiesPerBaseWebObject;
		}
		
	};
});


/**
 * Setup the $webSocket service.
 */
webSocketModule.factory('$webSocket',
		function($rootScope, $injector, $window, $log, $q, $services, $sabloConverters, $sabloUtils, $swingModifiers, $interval, wsCloseCodes,$sabloLoadingIndicator, $timeout, $sabloTestability) {

	var pathname = null;
	
	var queryString = null;
	
	var websocket = null;
	
	var connectionArguments = {};
	
	var lastServerMessageNumber = null;

	var nextMessageId = 1;
	
	var functionsToExecuteAfterIncommingMessageWasHandled = undefined;

	var getNextMessageId = function() {
		return nextMessageId++;
	};

	var deferredEvents = {};
	function isPromiseLike(obj) {
		  return obj && angular.isFunction(obj.then);
	}
	
	function setPathname(name) {
		pathname = name;
	}
	
	function getPathname() {
		return pathname || $window.location.pathname;
	}
	
	function setQueryString(qs) {
		queryString = qs;
	}
	
	function getQueryString() {
		if (queryString) {
			return queryString;
		}
		
		var search = $window.location.search;
		if (search && search.indexOf('?') == 0) {
			return search.substring(1);
		}
		
		return search;
	}
	
	var getURLParameter = function getURLParameter(name) {
		return decodeURIComponent((new RegExp('[&]?' + name + '=' + '([^&;]+?)(&|#|;|$)').exec(getQueryString())||[,""])[1].replace(/\+/g, '%20'))||null
	};

	var handleMessage = function(message) {
		var obj
		var responseValue
		functionsToExecuteAfterIncommingMessageWasHandled = [];

		try {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Received message from server: " + JSON.stringify(message));

			var message_data = message.data;
			var separator = message_data.indexOf('#');
			if (separator >= 0 && separator < 5) {
				// the json is prefixed with a message number: 123#{bla: "hello"}
				lastServerMessageNumber = message_data.substring(0, separator);
				message_data = message_data.substr(separator+1);
			}
			// else message has no seq-no
			
			obj = JSON.parse(message_data);
			
			if (obj.services) {
				// services call, first process the once with the flag 'apply_first'
				if (obj[$sabloConverters.TYPES_KEY] && obj[$sabloConverters.TYPES_KEY].services) {
					obj.services = $sabloConverters.convertFromServerToClient(obj.services, obj[$sabloConverters.TYPES_KEY].services, undefined, undefined, undefined)
				}
				for (var index in obj.services) {
					var service = obj.services[index];
					if (service['pre_data_service_call']) {
						var serviceInstance = $injector.get(service.name);
						if (serviceInstance
								&& serviceInstance[service.call]) {
							// responseValue keeps last services call return value
							responseValue = serviceInstance[service.call].apply(serviceInstance, service.args);
							$services.digest(service.name);
						}
					}
				}
			}

			// if the indicator is showing and this object wants a return message then hide the indicator until we send the response
			var hideIndicator = obj && obj.smsgid && $sabloLoadingIndicator.isShowing();
			// if a request to a service is being done then this could be a blocking 
			if (hideIndicator) {
				$sabloLoadingIndicator.hideLoading();
			}
			
			// data got back from the server
			if (obj.cmsgid) { // response to event
				var deferredEvent = deferredEvents[obj.cmsgid];
				if (deferredEvent != null && angular.isDefined(deferredEvent)) {
					if (obj.exception) {
						// something went wrong
						if (obj[$sabloConverters.TYPES_KEY] && obj[$sabloConverters.TYPES_KEY].exception) {
							obj.exception = $sabloConverters.convertFromServerToClient(obj.exception, obj[$sabloConverters.TYPES_KEY].exception, undefined, undefined, undefined)
						}
						$rootScope.$apply(function() {
							deferredEvent.reject(obj.exception);
						})
					} else {
						if (obj[$sabloConverters.TYPES_KEY] && obj[$sabloConverters.TYPES_KEY].ret) {
							obj.ret = $sabloConverters.convertFromServerToClient(obj.ret, obj[$sabloConverters.TYPES_KEY].ret, undefined, undefined, undefined)
						}
						$rootScope.$apply(function() {
							deferredEvent.resolve(obj.ret);
						})
					}
				} else $log.warn("Response to an unknown handler call dismissed; can happen (normal) if a handler call gets interrupted by a full browser refresh.");
				delete deferredEvents[obj.cmsgid];
				$sabloTestability.testEvents();
				$sabloLoadingIndicator.hideLoading();
			}

			function optimizeAndCallFormScopeDigest(scopesToDigest) {
				for (var scopeId in scopesToDigest) {
					var s = scopesToDigest[scopeId];
					var p = s.$parent;
					while (p && !scopesToDigest[p.$id]) p = p.$parent;
					if (!p) { // if no parent form scope is going to do digest
						if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will call digest for scope: " + (s && s.formname ? s.formname : scopeId));
						s.$digest();
					} else if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will NOT call digest for scope: " + (s && s.formname ? s.formname : scopeId) + " because a parent form scope " + (p.formname ? p.formname : p.$id) + " is in the list...");
				}
			}

			// message
			if (obj.msg) {
				var scopesToDigest = new window.CustomHashSet(function(s) {
					return s.$id; // hash them by angular scope id to avoid calling digest on the same scope twice
				});
				for (var handler in onMessageObjectHandlers) {
					var ret = onMessageObjectHandlers[handler](obj.msg, obj[$sabloConverters.TYPES_KEY] ? obj[$sabloConverters.TYPES_KEY].msg : undefined, scopesToDigest)
					if (ret) responseValue = ret;
					
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Checking if any form scope changes need to be digested (obj.msg).");
				}
				optimizeAndCallFormScopeDigest(scopesToDigest);
			}

			if (obj.msg && obj.msg.services) {
				$services.updateServiceScopes(obj.msg.services, (obj[$sabloConverters.TYPES_KEY] && obj[$sabloConverters.TYPES_KEY].msg) ? obj[$sabloConverters.TYPES_KEY].msg.services : undefined);
			}

			if (obj.services) {
				// normal services call
				for (var index in obj.services) {
					var service = obj.services[index];
					if (!service['pre_data_service_call']) {
						var serviceInstance = $injector.get(service.name);
						if (serviceInstance
								&& serviceInstance[service.call]) {
							// responseValue keeps last services call return value
							responseValue = serviceInstance[service.call].apply(serviceInstance, service.args);
							$services.digest(service.name);
						}
					}
				}
			}

			// delayed calls
			if (obj.calls)
			{
				var scopesToDigest = new window.CustomHashSet(function(s) {
					return s.$id; // hash them by angular scope id to avoid calling digest on the same scope twice
				});
				for(var i = 0;i < obj.calls.length;i++) 
				{
					for (var handler in onMessageObjectHandlers) {
						onMessageObjectHandlers[handler](obj.calls[i], (obj[$sabloConverters.TYPES_KEY] && obj[$sabloConverters.TYPES_KEY].calls) ? obj[$sabloConverters.TYPES_KEY].calls[i] : undefined, scopesToDigest);
					}
					
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Checking if any (obj.calls) form scopes changes need to be digested (obj.calls).");
				}
				optimizeAndCallFormScopeDigest(scopesToDigest);
			}	
			if (obj && obj.smsgid) {
				if (isPromiseLike(responseValue)) {
					if ($log.debugEnabled) $log.debug("sbl * Call from server with smsgid '" + obj.smsgid + "' returned a promise; will wait for it to get resolved.");
					
					// the server wants a response, this could be a promise so a dialog could be shown
					// then just let protractor go through.
					$sabloTestability.increaseEventLoop();
				}
				// server wants a response; responseValue may be a promise
				$q.when(responseValue).then(function(ret) {
					if (isPromiseLike(responseValue)) {
						$sabloTestability.decreaseEventLoop();
						if ($log.debugEnabled) $log.debug("sbl * Promise returned by call from server with smsgid '" + obj.smsgid + "' is now resolved with value: -" + ret + "-. Sending value back to server...");
					} else if ($log.debugEnabled) $log.debug("sbl * Call from server with smsgid '" + obj.smsgid + "' returned: -" + ret + "-. Sending value back to server...");
					
					// success
					var response = {
							smsgid : obj.smsgid
					}
					if (ret != undefined) {
						response['ret'] = $sabloUtils.convertClientObject(ret);
					}
					if (hideIndicator) {
						$sabloLoadingIndicator.showLoading();
					}
					sendMessageObject(response);
				}, function(reason) {
					if (isPromiseLike(responseValue)) $sabloTestability.decreaseEventLoop();
					// error
					$log.error("Error (follows below) in parsing/processing this message with smsgid '" + obj.smsgid + "' (async): " + message_data);
					$log.error(reason);
					// server wants a response; send failure so that browser side script doesn't hang
					var response = {
							smsgid : obj.smsgid,
							err: "Error while executing ($q deferred) client side code. Please see browser console for more info. Error: " + reason
					}
					if (hideIndicator) {
						$sabloLoadingIndicator.showLoading();
					}
					sendMessageObject(response);
				});
			}
		} catch (e) {
			$log.error("Error (follows below) in parsing/processing this message: " + message_data);
			$log.error(e);
			if (obj && obj.smsgid) {
				// server wants a response; send failure so that browser side script doesn't hang
				var response = {
						smsgid : obj.smsgid,
						err: "Error while executing client side code. Please see browser console for more info. Error: " + e
				}
				if (hideIndicator) {
					$sabloLoadingIndicator.showLoading();
				}
				sendMessageObject(response);
			}
		} finally {
			var err;
			for (var i = 0; i < functionsToExecuteAfterIncommingMessageWasHandled.length; i++) {
				try {
					functionsToExecuteAfterIncommingMessageWasHandled[i]();
				} catch (e) {
					$log.error("Error (follows below) in executing PostIncommingMessageHandlingTask: " + functionsToExecuteAfterIncommingMessageWasHandled[i]);
					$log.error(e);
					err = e;
				}
			}
			functionsToExecuteAfterIncommingMessageWasHandled = undefined;
			if (err) throw err;
		}
	}
	
	var addIncomingMessageHandlingDoneTask = function(func) {
		if (functionsToExecuteAfterIncommingMessageWasHandled) functionsToExecuteAfterIncommingMessageWasHandled.push(func);
		else func(); // will not addPostIncommingMessageHandlingTask while not handling an incoming message; the task can execute right away then (maybe it was called due to a change detected in a watch instead of property listener)
	}

	var sendMessageObject = function(obj) {
		if ($sabloUtils.getCurrentEventLevelForServer()) {
			obj.prio = $sabloUtils.getCurrentEventLevelForServer();
		}
		var msg = JSON.stringify(obj)
		if (isConnected()) {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Sending message to server: " + msg);
			websocket.send(msg)
		}
		else
		{
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Disconnected; will add the following to pending messages to be sent to server: " + msg);
			pendingMessages = pendingMessages || []
			pendingMessages.push(msg)
		}
	}

	var callService = function(serviceName, methodName, argsObject,async) {
		var cmd = {
				service : serviceName,
				methodname : methodName,
				args : argsObject
		};
		if (async)
		{
			sendMessageObject(cmd);
		}
		else
		{
			var deferred = $q.defer();
			var cmsgid = getNextMessageId()
			deferredEvents[cmsgid] = deferred
			$sabloLoadingIndicator.showLoading();
			cmd['cmsgid'] = cmsgid
			sendMessageObject(cmd)
			return deferred.promise;
		}
	}
	$sabloTestability.setEventList(deferredEvents);

	var onOpenHandlers = [];
	var onErrorHandlers = [];
	var onCloseHandlers = [];
	var onMessageObjectHandlers = [];

	var WebsocketSession = function() {

		// api
		this.callService = callService;

		this.sendMessageObject = sendMessageObject;

		this.onopen = function(handler) {
			onOpenHandlers.push(handler)
		};
		this.onerror = function(handler) {
			onErrorHandlers.push(handler)
		};
		this.onclose = function(handler) {
			onCloseHandlers.push(handler)
		};
		this.onMessageObject = function(handler) {
			onMessageObjectHandlers.push(handler)
		};
	};
	var wsSession = new WebsocketSession();

	var connected = 'INITIAL'; // INITIAL/CONNECTED/RECONNECTING/CLOSED
	if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode: ... INITIAL (" + new Date().getTime() + ")");
	var pendingMessages = undefined

	// heartbeat, detect disconnects before websocket gives us connection-closed.
	var heartbeatMonitor = undefined;
	var lastHeartbeat = undefined;
	function startHeartbeat() {
		if (!angular.isDefined(heartbeatMonitor)) {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Starting heartbeat... (" + new Date().getTime() + ")");

			lastHeartbeat = new Date().getTime();
			heartbeatMonitor = $interval(function() {
				if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Sending heartbeat... (" + new Date().getTime() + ")");
				if (new Date().getTime() - lastHeartbeat >= 4000){
					websocket.send("P"); // ping
					if (isConnected() && new Date().getTime() - lastHeartbeat > 8000) {
						// no response within 8 seconds
						if (connected !== 'RECONNECTING') {
							if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode (Heartbeat timed out; connection lost; waiting to reconnect): ... RECONNECTING (" + new Date().getTime() + ")");
							connected = 'RECONNECTING';
							$rootScope.$apply();
						}
					}
				}
			}, 4000, 0, false);
		}
	}
	
	function stopHeartbeat() {
		if (angular.isDefined(heartbeatMonitor)) {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Stopping heartbeat... (" + new Date().getTime() + ")");
			$interval.cancel(heartbeatMonitor);
			heartbeatMonitor = undefined;
		}
	}
	
	function setConnected() {
		connected = 'CONNECTED';
		if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode: ... CONNECTED (" + new Date().getTime() + ")");

		if (pendingMessages) {
			for (var i in pendingMessages) {
				if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connected; sending pending message to server: " + pendingMessages[i]);
				websocket.send(pendingMessages[i])
			}
			pendingMessages = undefined
		}
	}
	
	function handleHeartbeat(message) {
		if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Received heartbeat... (" + new Date().getTime() + ")");
		lastHeartbeat = new Date().getTime(); // something is received, the server connection is up
		if (isReconnecting()) {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Heartbeat received, connection re-established...");
			$rootScope.$apply(setConnected);
		}
		if (message.data == "P") {
			websocket.send("p"); 
		}
		return message.data == "p" ||  message.data == "P"; // pong or ping
	}
	
	function isConnected() {
		return connected == 'CONNECTED';
	}

	function isReconnecting() {
		return connected == 'RECONNECTING';
	}

	function generateURL(context, args, queryArgs, websocketUri) {
		var new_uri;
		if ($window.location.protocol === "https:") {
			new_uri = "wss:";
		} else {
			new_uri = "ws:";
		}
		new_uri += "//" + $window.location.host;
		var pathname = getPathname();
		var lastIndex = pathname.lastIndexOf("/");
		if (lastIndex > 0) {
			pathname = pathname.substring(0, lastIndex);
		}
		if (context && context.length > 0)
		{
			var lastIndex = pathname.lastIndexOf(context);
			if (lastIndex >= 0) {
				pathname = pathname.substring(0, lastIndex) + pathname.substring(lastIndex + context.length)
			}
		}
		new_uri += pathname + (websocketUri?websocketUri:'/websocket');
		for (var a in args) {
			if (args.hasOwnProperty(a)) {
				new_uri += '/' + args[a]
			}
		}

		new_uri += "?";

		for (var a in queryArgs)
		{
			if (queryArgs.hasOwnProperty(a)) {
				new_uri += a+"="+queryArgs[a]+"&";
			}
		}
		
		if (lastServerMessageNumber != null) {
			new_uri += "lastServerMessageNumber="+lastServerMessageNumber+"&";
		}

		var queryString = getQueryString();
		if (queryString)
		{
			new_uri += queryString; 
		}
		else
		{
			new_uri = new_uri.substring(0,new_uri.length-1);
		}
		return new_uri;
	}
	/**
	 * The $webSocket service API.
	 */
	return  <sablo.IWebSocket> {

		connect : function(context, args, queryArgs, websocketUri) {

			connectionArguments = {
					context: context,
					args: args,
					queryArgs: queryArgs,
					websocketUri: websocketUri
			}
			
			// When ReconnectingWebSocket gets a function it will call the function to generate the url for each (re)connect.
			websocket = new window.ReconnectingWebSocket(function() {
					return generateURL(connectionArguments['context'], connectionArguments['args'],
								connectionArguments['queryArgs'], connectionArguments['websocketUri']);
				});

			websocket.onopen = function(evt) {
				$rootScope.$apply(function() {
					setConnected();
				});
				startHeartbeat();
				for (var handler in onOpenHandlers) {
					onOpenHandlers[handler](evt);
				}
			}
			websocket.onerror = function(evt) {
				stopHeartbeat();
				for (var handler in onErrorHandlers) {
					onErrorHandlers[handler](evt);
				}
			}
			websocket.onclose = function(evt) {
				stopHeartbeat();
				$rootScope.$apply(function() {
					if (connected != 'CLOSED') {
						connected = 'RECONNECTING';
						if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode (onclose receidev while not CLOSED): ... RECONNECTING (" + new Date().getTime() + ")");
					}
				});
				for (var handler in onCloseHandlers) {
					onCloseHandlers[handler](evt);
				}
			}
			websocket.onconnecting = function(evt) {
				// this event indicates we are trying to reconnect, the event has the close code and reason from the disconnect.
				if (evt.code && evt.code != wsCloseCodes.CLOSED_ABNORMALLY && evt.code != wsCloseCodes.SERVICE_RESTART) {
					
					websocket.close();
					
					if (evt.reason == 'CLIENT-OUT-OF-SYNC') {
						// Server detected that we are out-of-sync, reload completely
						$window.location.reload();
						return;
					}
					
					// server disconnected, do not try to reconnect
					$rootScope.$apply(function() {
						connected = 'CLOSED';
						if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode (onconnecting got a server disconnect/close with reason " + evt.reason + "): ... CLOSED (" + new Date().getTime() + ")");
					});
				}
			}
			websocket.onmessage = function(message) {
				handleHeartbeat(message) || handleMessage(message);
			}

			// todo should we just merge $websocket and $services into $sablo that just has all
			// the public api of sablo (like connect, conversions, services)
			$services.setSession(wsSession);

			return wsSession
		},

		// update query arguments for next reconnect-call
		setConnectionQueryArgument: function(arg, value) {
			if (angular.isDefined(value)) {
				if (!connectionArguments['queryArgs']) connectionArguments['queryArgs'] = {};
				connectionArguments['queryArgs'][arg] = value;
			} else if (connectionArguments['queryArgs']){
				connectionArguments['queryArgs'].delete(arg);
			}
		},
		
		setConnectionPathArguments: function(args) {
			connectionArguments['args'] = args;
		},
		
		getSession: function() {
			return wsSession;
		},

		isConnected: isConnected,

		isReconnecting: isReconnecting,
		
		addIncomingMessageHandlingDoneTask: addIncomingMessageHandlingDoneTask,
		
		disconnect: function() {
			if(websocket) {
				websocket.close();
				connected = 'CLOSED';
				if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode (disconnect): ... CLOSED (" + new Date().getTime() + ")");
			}
		},

		getURLParameter: getURLParameter,
		
		setPathname: setPathname,
		getPathname: getPathname,
		
		setQueryString: setQueryString,
		getQueryString: getQueryString	
	};
}).factory("$services", function($rootScope, $sabloConverters, $sabloUtils, $propertyWatchesRegistry, $log){
	// serviceName:{} service model
	var serviceScopes = $rootScope.$new(true);
	var serviceScopesConversionInfo = {};
	var watches = {}
	var wsSession = null;
	var sendServiceChanges = function(now, prev, servicename, property) {
		var changes = {}
		var conversionInfo = serviceScopesConversionInfo[servicename];
		if (property) {
			if (conversionInfo && conversionInfo[property]) changes[property] = $sabloConverters.convertFromClientToServer(now, conversionInfo[property], prev);
			else changes[property] = $sabloUtils.convertClientObject(now);
		} else {
			// TODO hmm I think it will never go through here anymore; remove this else code
			// first build up a list of all the properties both have.
			var fulllist = $sabloUtils.getCombinedPropertyNames(now,prev);
			var changes = {};
			
			for (var prop in fulllist) {
				var changed = false;
				if (!(prev && now)) {
					changed = true; // true if just one of them is undefined; both cannot be undefined at this point if we are already iterating on combined property names
				} else {
					changed = $sabloUtils.isChanged(now[prop], prev[prop], conversionInfo ? conversionInfo[prop] : undefined)
				}
				
				if (changed) {
					if (conversionInfo && conversionInfo[prop]) changes[prop] = $sabloConverters.convertFromClientToServer(now[prop], conversionInfo[prop], prev ? prev[prop] : undefined);
					else changes[prop] = $sabloUtils.convertClientObject(now[prop])
				}
			}
		}
		for (var prop in changes) { // weird way to only send it if it has at least one element
			wsSession.sendMessageObject({servicedatapush:servicename,changes:changes})
			return;
		}
	};
	var getChangeNotifier = function(servicename, property) {
		return function() {
			var serviceModel = serviceScopes[servicename].model;
			sendServiceChanges(serviceModel[property], serviceModel[property], servicename, property);
		}
	};
	
	function scriptifyServiceNameIfNeeded(serviceName) {
		if (serviceName) {
			// transform serviceNames like testpackage-myTestService into testPackageMyTestService - as latter is how getServiceScope usually gets called (from developer generated code for services) from service client js;
			// but who knows, maybe someone will try the dashed version and wonder why it doesn't work
			
			// this should do the same as ClientService.java #convertToJSName()
			var packageAndName = serviceName.split("-");
			if (packageAndName.length > 1) {
				serviceName = packageAndName[0];
				for (var i = 1; i < packageAndName.length; i++) {
					if (packageAndName[1].length > 0) serviceName += packageAndName[i].charAt(0).toUpperCase() + packageAndName[i].slice(1);
				}
			}
		}
		return serviceName;
	}

	return {
		getServiceScope: function(serviceName) {
			serviceName = scriptifyServiceNameIfNeeded(serviceName);
			
			if (!serviceScopes[serviceName]) {
				serviceScopes[serviceName] = serviceScopes.$new(true);
				serviceScopes[serviceName].model = {};
				
				watches[serviceName] = $propertyWatchesRegistry.watchDumbPropertiesForService(serviceScopes[serviceName], serviceName, serviceScopes[serviceName].model, function(newValue, oldValue, property) {
					sendServiceChanges(newValue, oldValue, serviceName, property);
				});
			}
			return serviceScopes[serviceName];
		},
		updateServiceScopes: function(services, conversionInfo) {
			for(var servicename in services) {
				// current model
				var serviceScope = serviceScopes[servicename];
				if (!serviceScope) {
					serviceScope = serviceScopes[servicename] = serviceScopes.$new(true);
					// so no previous service state; set it now
					if (conversionInfo && conversionInfo[servicename]) {
						// convert all properties, remember type for when a client-server conversion will be needed
						services[servicename] = $sabloConverters.convertFromServerToClient(services[servicename], conversionInfo[servicename], undefined, serviceScope, function() { return serviceScope.model })
						
						for (var pn in conversionInfo[servicename]) {
							if (services[servicename][pn] && services[servicename][pn][$sabloConverters.INTERNAL_IMPL]
							&& services[servicename][pn][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								services[servicename][pn][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(getChangeNotifier(servicename, pn));
							}
						}
						serviceScopesConversionInfo[servicename] = conversionInfo[servicename];
					}
					serviceScope.model = services[servicename];
				}
				else {
					var serviceData = services[servicename];

					// unregister the watches
					if (watches[servicename]) watches[servicename].forEach(function (unwatchFunctionElement, index, array) {
						unwatchFunctionElement();
					});

					for(var key in serviceData) {
						if (conversionInfo && conversionInfo[servicename] && conversionInfo[servicename][key]) {
							// convert property, remember type for when a client-server conversion will be needed
							if (!serviceScopesConversionInfo[servicename]) serviceScopesConversionInfo[servicename] = {};
							serviceData[key] = $sabloConverters.convertFromServerToClient(serviceData[key], conversionInfo[servicename][key], serviceScope.model[key], serviceScope, function() { return serviceScope.model })

							if ((serviceData[key] !== serviceScope.model[key] || serviceScopesConversionInfo[servicename][key] !== conversionInfo[servicename][key]) && serviceData[key]
							&& serviceData[key][$sabloConverters.INTERNAL_IMPL] && serviceData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								serviceData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(getChangeNotifier(servicename, key));
							}
							serviceScopesConversionInfo[servicename][key] = conversionInfo[servicename][key];
						} else if (angular.isDefined(serviceScopesConversionInfo[servicename]) && angular.isDefined(serviceScopesConversionInfo[servicename][key])) {
							delete serviceScopesConversionInfo[servicename][key];
						}

						serviceScope.model[key] = serviceData[key];
					}
				}
				
				// register a new watch
				watches[servicename] = $propertyWatchesRegistry.watchDumbPropertiesForService(serviceScope, servicename, serviceScope.model, function(newValue, oldValue, property) {
					sendServiceChanges(newValue, oldValue, servicename, property);
				});

				if ($rootScope.$$asyncQueue.length > 0) {
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will call digest from updateServiceScopes for rootscope");
					$rootScope.$digest();
				} else {
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will call digest from updateServiceScopes for services scope: " + servicename);
					serviceScopes[servicename].$digest();
				}
			}
		},
		digest: function(servicename) {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will call digest from digest(servicename) for services scope: " + servicename);
			if (serviceScopes[servicename]) serviceScopes[servicename].$digest();
		},
		setSession: function(session) {
			wsSession = session;
		}
	}
}).factory("$sabloConverters", function($log) {
	/**
	 * Custom property converters can be registered via this service method: $webSocket.registerCustomPropertyHandler(...)
	 */
	var customPropertyConverters = {};

	var convertFromServerToClient = function(serverSentData, conversionInfo, currentClientData, scope, modelGetter) {
		if (typeof conversionInfo === 'string' || typeof conversionInfo === 'number') {
			var customConverter = customPropertyConverters[conversionInfo];
			if (customConverter) serverSentData = customConverter.fromServerToClient(serverSentData, currentClientData, scope, modelGetter);
			else { //converter not found - will not convert
				$log.error("cannot find type converter (s->c) for: '" + conversionInfo + "'.");
			}
		} else if (conversionInfo) {
			for (var conKey in conversionInfo) {
				serverSentData[conKey] = convertFromServerToClient(serverSentData[conKey], conversionInfo[conKey], currentClientData ? currentClientData[conKey] : undefined, scope, modelGetter); // TODO should componentScope really stay the same here? 
			}
		}
		return serverSentData;
	};

	var updateAngularScope = function(value, conversionInfo, scope) {
		if (typeof conversionInfo === 'string' || typeof conversionInfo === 'number') {
			var customConverter = customPropertyConverters[conversionInfo];
			if (customConverter) customConverter.updateAngularScope(value, scope);
			else { //converter not found - will not convert
				$log.error("cannot find type converter (to update scope) for: '" + conversionInfo + "'.");
			}
		} else if (conversionInfo) {
			for (var conKey in conversionInfo) {
				updateAngularScope(value[conKey], conversionInfo[conKey], scope); // TODO should componentScope really stay the same here? 
			}
		}
	};

	// converts from a client property JS value to a JSON that can be sent to the server using the appropriate registered handler
	var convertFromClientToServer = function(newClientData, conversionInfo, oldClientData) {
		if (typeof conversionInfo === 'string' || typeof conversionInfo === 'number') {
			var customConverter = customPropertyConverters[conversionInfo];
			if (customConverter) return customConverter.fromClientToServer(newClientData, oldClientData);
			else { //converter not found - will not convert
				$log.error("cannot find type converter (c->s) for: '" + conversionInfo + "'.");
				return newClientData;
			}
		} else if (conversionInfo) {
			var retVal = (Array.isArray ? Array.isArray(newClientData) : $.isArray(newClientData)) ? [] : {};
			for (var conKey in conversionInfo) {
				retVal[conKey] = convertFromClientToServer(newClientData[conKey], conversionInfo[conKey], oldClientData ? oldClientData[conKey] : undefined);
			}
			return retVal;
		} else {
			return newClientData;
		}
	};

	return <sablo.ISabloConverters> {

		/**
		 * In a custom property value, the val[$sabloConverters.INTERNAL_IMPL] is to be used for internal state/impl details only - not to be accessed by components
		 */
		INTERNAL_IMPL: '__internalState',
		TYPES_KEY: 'svy_types',

		prepareInternalState: function(propertyValue, optionalInternalStateValue) {
			if (!propertyValue.hasOwnProperty(this.INTERNAL_IMPL))
			{
				if (angular.isUndefined(optionalInternalStateValue)) optionalInternalStateValue = {};
				if (Object.defineProperty) {
					// try to avoid unwanted iteration/non-intended interference over the private property state
					Object.defineProperty(propertyValue, this.INTERNAL_IMPL, {
						configurable: false,
						enumerable: false,
						writable: false,
						value: optionalInternalStateValue
					});
				} else propertyValue[this.INTERNAL_IMPL] = optionalInternalStateValue;
			} else $log.warn("An attempt to prepareInternalState on value '" + propertyValue + "' which already has internal state was ignored.");
		},

		convertFromServerToClient: convertFromServerToClient,

		convertFromClientToServer: convertFromClientToServer,

		updateAngularScope: updateAngularScope,

		/**
		 * Registers a custom client side property handler into the system. These handlers are useful
		 * for custom property types that require some special handling when received through JSON from server-side
		 * or for sending content updates back. (for example convert received JSON into a different JS object structure that will be used
		 * by beans or just implement partial updates for more complex property contents)
		 *  
		 * @param customHandler an object with the following methods/fields:
		 * {
		 * 
		 *				// Called when a JSON update is received from the server for a property
		 *				// @param serverSentJSONValue the JSON value received from the server for the property
		 *				// @param currentClientValue the JS value that is currently used for that property in the client; can be null/undefined if
		 *				//        conversion happens for service API call parameters for example...
		 *				// @param scope scope that can be used to add component/service and property related watches; can be null/undefined if
		 *				//        conversion happens for service/component API call parameters for example...
		 *				// @param modelGetter a function that returns the model that can be used to find other properties of the service/component if needed (if the
		 *              //        property is 'linked' to another one); can be null/undefined if conversion happens for service/component API call parameters for example...
		 *				// @return the new/updated client side property value; if this returned value is interested in triggering
		 *				//         updates to server when something changes client side it must have these member functions in this[$sabloConverters.INTERNAL_IMPL]:
		 *				//				setChangeNotifier: function(changeNotifier) - where changeNotifier is a function that can be called when
		 *				//                                                          the value needs to send updates to the server; this method will
		 *				//                                                          not be called when value is a call parameter for example, but will
		 *				//                                                          be called when set into a component's/service's property/model
		 *				//              isChanged: function() - should return true if the value needs to send updates to server // TODO this could be kept track of internally
		 * 				fromServerToClient: function (serverSentJSONValue, currentClientValue, scope, modelGetter) { (...); return newClientValue; },
		 * 
		 *				// Converts from a client property JS value to a JSON that will be sent to the server.
		 *				// @param newClientData the new JS client side property value
		 *				// @param oldClientData the old JS JS client side property value; can be null/undefined if
		 *				//        conversion happens for service API call parameters for example...
		 *				// @return the JSON value to send to the server.
		 *				fromClientToServer: function(newClientData, oldClientData) { (...); return sendToServerJSON; }
		 * 
		 *				// Some 'smart' property types need an angular scope to register watches to; this method will get called on them
		 *				// when the scope that they should use changed (old scope could get destroyed and then after a while a new one takes it's place).
		 *				// This gives such property types a way to keep their watches operational even on the new scope.
		 *				// @param clientValue the JS client side property value
		 *				// @param scope the new scope. If null it means that the previous scope just got destroyed and property type should perform needed cleanup.
		 *				updateAngularScope: function(clientValue, scope) { (...); }
		 * 
		 * }
		 */
		registerCustomPropertyHandler : function(propertyTypeID, customHandler, overwrite) {
			if (overwrite == false && customPropertyConverters[propertyTypeID] ) return; 
			customPropertyConverters[propertyTypeID] = customHandler;
		}

	};
}).factory("$sabloUtils", function($log, $sabloConverters:sablo.ISabloConverters,$swingModifiers) {
	// define a global custom 'hash set' based on a configurable hash function received in constructor
	window.CustomHashSet = function(hashCodeFunc) {
		Object.defineProperty(this, "hashCode", {
			configurable: true,
			enumerable: false,
			writable: true,
			value: hashCodeFunc
		});
	};
	Object.defineProperty(window.CustomHashSet.prototype, "putItem", {
		configurable: true,
		enumerable: false,
		writable: true,
		value: function(e) {
			this[this.hashCode(e)] = e; // hash them by angular scope id to avoid calling digest on the same scope twice
		}
	});
	
	var getCombinedPropertyNames = function(now,prev) {
		var fulllist = {}
		if (prev) {
			var prevNames = Object.getOwnPropertyNames(prev);
			for(var i=0; i < prevNames.length; i++) {
				fulllist[prevNames[i]] = true;
			}
		}
		if (now) {
			var nowNames = Object.getOwnPropertyNames(now);
			for(var i=0;i < nowNames.length;i++) {
				fulllist[nowNames[i]] = true;
			}
		}
		return fulllist;
	}

	var isChanged = function(now, prev, conversionInfo) {
		if ((typeof conversionInfo === 'string' || typeof conversionInfo === 'number') && now && now[$sabloConverters.INTERNAL_IMPL] && now[$sabloConverters.INTERNAL_IMPL].isChanged) {
			return now[$sabloConverters.INTERNAL_IMPL].isChanged();
		}

		if (now === prev) return false;
		if (now && prev) {
			if (now instanceof Array) {
				if (prev instanceof Array) {
					if (now.length != prev.length) return true;
				} else {
					return true;
				}
			}
			if (now instanceof Date) {
				if (prev instanceof Date) {
					return now.getTime() != prev.getTime();
				}
				return true;
			}

			if ((now instanceof Object) && (prev instanceof Object)) {
				// first build up a list of all the properties both have.
				var fulllist = getCombinedPropertyNames(now, prev);
				for (var prop in fulllist) {
					if(prop == "$$hashKey") continue; // ng repeat creates a child scope for each element in the array any scope has a $$hashKey property which must be ignored since it is not part of the model
					if (prev[prop] !== now[prop]) {
						if (typeof now[prop] == "object") {
							if (isChanged(now[prop],prev[prop], conversionInfo ? conversionInfo[prop] : undefined)) {
								return true;
							}
						} else {
							return true;
						}
					}
				}
				return false;
			}
		}
		return true;
	}
	var currentEventLevelForServer;
	var sabloUtils:sablo.ISabloUtils = {
			// execution priority on server value used when for example a blocking API call from server needs to request more data from the server through this change
			// or whenever during a (blocking) API call to client we want some messages sent to the server to still be processed.
			EVENT_LEVEL_SYNC_API_CALL: 500,
			
			// objects that have a function named like this in them will send to server the result of that function call when no conversion type is available (in case of
			// usage as handler arg. for example where we don't know the arg. types on client)
			DEFAULT_CONVERSION_TO_SERVER_FUNC: "_dctsf",

			// eventLevelValue can be undefined for DEFAULT
			setCurrentEventLevelForServer: function(eventLevelValue) {
				currentEventLevelForServer = eventLevelValue;
			},

			getCurrentEventLevelForServer: function() {
				return currentEventLevelForServer;
			},

			isChanged: isChanged,
			getCombinedPropertyNames: getCombinedPropertyNames,

			convertClientObject : function(value) {
				if (value instanceof Date) {
					value  = $sabloConverters.convertFromClientToServer(value, "Date", null);
				} else if (value && typeof value[this.DEFAULT_CONVERSION_TO_SERVER_FUNC] == 'function') {
					return value[this.DEFAULT_CONVERSION_TO_SERVER_FUNC]();
				}
				return value;
			},

			getEventArgs: function(args,eventName)
			{
				var newargs = []
				for (var i = 0; i < args.length; i++) {
					var arg = args[i]
					if (arg && arg.originalEvent) arg = arg.originalEvent;
					if(arg  instanceof MouseEvent ||arg  instanceof KeyboardEvent){
						var $event = arg;
						var eventObj = {}
						var modifiers = 0;
						if($event.shiftKey) modifiers = modifiers||$swingModifiers.SHIFT_MASK;
						if($event.metaKey) modifiers = modifiers||$swingModifiers.META_MASK;
						if($event.altKey) modifiers = modifiers|| $swingModifiers.ALT_MASK;
						if($event.ctrlKey) modifiers = modifiers || $swingModifiers.CTRL_MASK;

						eventObj['type'] = 'event'; 
						eventObj['eventName'] = eventName; 
						eventObj['modifiers'] = modifiers;
						eventObj['timestamp'] = new Date().getTime();
						eventObj['x']= $event['pageX'];
						eventObj['y']= $event['pageY'];
						arg = eventObj
					}
					else if (arg instanceof Event || arg instanceof $.Event)
					{
						var eventObj = {}
						eventObj['type'] = 'event'; 
						eventObj['eventName'] = eventName;
						eventObj['timestamp'] = new Date().getTime();
						arg = eventObj
					}
					else arg = this.convertClientObject(arg); // TODO should be $sabloConverters.convertFromClientToServer(now, beanConversionInfo[property] ?, undefined);, but as we do not know handler arg types, we just do default conversion (for dates & types that use $sabloUtils.DEFAULT_CONVERSION_TO_SERVER_FUNC)

					newargs.push(arg)
				}
				return newargs;
			},

			/**
			 * Receives variable arguments. First is the object obj and the others (for example a, b, c) are used to
			 * return obj[a][b][c] making sure that if any does not exist or is null (for example b) it will be set to {}.
			 */
			getOrCreateInDepthProperty: function() {
				if (arguments.length == 0) return undefined;

				var ret = arguments[0];
				if (ret == undefined || ret === null || arguments.length == 1) return ret;
				var p;
				var i;
				for (i = 1; i < arguments.length; i++) {
					p = ret;
					ret = ret[arguments[i]];
					if (ret === undefined || ret === null) {
						ret = {};
						p[arguments[i]] = ret;
					}
				}

				return ret;
			},

			/**
			 * Receives variable arguments. First is the object obj and the others (for example a, b, c) are used to
			 * return obj[a][b][c] making sure that if any does not exist or is null it will just return null/undefined instead of erroring out.
			 */
			getInDepthProperty: function() {
				if (arguments.length == 0) return undefined;

				var ret = arguments[0];
				if (ret == undefined || ret === null || arguments.length == 1) return ret;
				var i;
				for (i = 1; i < arguments.length; i++) {
					ret = ret[arguments[i]];
					if (ret === undefined || ret === null) {
						return i == arguments.length - 1 ? ret : undefined;
					}
				}

				return ret;
			},
			
			/**
			 * Makes a clone of "obj" (new object + iterates on properties and copies them over (so shallow clone)) that will have it's [[Prototype]] set to "newPrototype".
			 * It is not aware of property descriptors. It uses plain property assignment when cloning.
			 */
			cloneWithDifferentPrototype: function(obj, newPrototype) {
				// instead of using this impl., we could use Object.setPrototypeOf(), but that is slower in the long run due to missing JS engine optimizations for accessing props.
				// accorging to https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/setPrototypeOf
				var clone = Object.create(newPrototype);

				Object.keys(obj).forEach(function (prop) {  
					clone[prop] = obj[prop];
				});

				return clone;
			}

	}

	return sabloUtils;
}).value("wsCloseCodes", {
	NORMAL_CLOSURE: 1000, // indicates a normal closure, meaning that the purpose for which the connection was established has been fulfilled.
	GOING_AWAY: 1001, // indicates that an endpoint is "going away", such as a server going down or a browser having navigated away from a page.
	PROTOCOL_ERROR: 1002, // indicates that an endpoint is terminating the connection due to a protocol error.
	CANNOT_ACCEPT: 1003, // indicates that an endpoint is terminating the connection because it has received a type of data it cannot accept (e.g., an endpoint that understands only text data MAY send this if it receives a binary message).
	NO_STATUS_CODE: 1005, // is a reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.
	CLOSED_ABNORMALLY: 1006, // is a reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.
	NOT_CONSISTENT: 1007, // indicates that an endpoint is terminating the connection because it has received data within a message that was not consistent with the type of the message (e.g., non-UTF-8 data within a text message).
	VIOLATED_POLICY: 1008, // indicates that an endpoint is terminating the connection because it has received a message that violates its policy.
	TOO_BIG: 1009, // indicates that an endpoint is terminating the connection because it has received a message that is too big for it to process.
	NO_EXTENSION: 1010, // indicates that an endpoint (client) is terminating the connection because it has expected the server to negotiate one or more extension, but the server didn't return them in the response message of the WebSocket handshake.
	UNEXPECTED_CONDITION: 1011, // indicates that a server is terminating the connection because it encountered an unexpected condition that prevented it from fulfilling the request.
	SERVICE_RESTART: 1012, // indicates that the service will be restarted.
	TLS_HANDSHAKE_FAILURE: 1015, // is a reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.
	TRY_AGAIN_LATER: 1013 // indicates that the service is experiencing overload
}).value("$swingModifiers" ,{
	SHIFT_MASK : 1,
	CTRL_MASK : 2,
	META_MASK : 4,
	ALT_MASK : 8,
	ALT_GRAPH_MASK : 32,
	BUTTON1_MASK : 16,
	BUTTON2_MASK : 8,
	SHIFT_DOWN_MASK : 64,
	CTRL_DOWN_MASK : 128,
	META_DOWN_MASK : 256,
	ALT_DOWN_MASK : 512,
	BUTTON1_DOWN_MASK : 1024,
	BUTTON2_DOWN_MASK : 2048,
	DOWN_MASK : 4096,
	ALT_GRAPH_DOWN_MASK : 8192
	
}).directive('sabloReconnectingFeedback', function ($webSocket) {

  function reconnecting() { 
	  return $webSocket.isReconnecting(); 
  }
 
  // TODO: should we not introduce a scope and just watch '$webSocket.isReconnecting()'?
  return {
    restrict: 'EA',
    template: '<div ng-show="reconnecting()" style="z-index:2147483647;background:lightgray;opacity:.75;width:100%;height:100%;position:absolute;" ng-transclude></div>',
    transclude: true,
    scope: true,
    controller: function($scope, $element, $attrs) {
      $scope.reconnecting = reconnecting;
    }
  }
}).factory("$sabloLoadingIndicator", function($injector, $window,$log,$timeout) {
	// look for a custom implementation of the indicator
	var custom = null;
	if ($injector.has("loadingIndicator")) {
		custom = $injector.get("loadingIndicator");
		if (custom && (custom.showLoading == undefined || custom.hideLoading == undefined)){
			custom = null;
			$log.warn("a custom loading indicator is defined but doesn't have the 2 functions: showLoading or hideLoading")
		}
	}
	if (custom == null) {
		// if there is none then use the default, that uses a class to make sure the wait cursor is shown/set on all dom elements.
		var style = $window.document.createElement('style');
		style.type = 'text/css';
		style.innerHTML = '.sablowaitcursor, .sablowaitcursor * { cursor: wait !important; }';
		document.getElementsByTagName('head')[0].appendChild(style);
	}
	var showCounter = 0;
	var timeoutHidePromise = null;
	var timeoutShowPromise = null;
	return {
		showLoading: function() {
			showCounter++;
			if (showCounter == 1) {
				if (timeoutHidePromise) {
					$timeout.cancel(timeoutHidePromise);
					timeoutHidePromise = null;
				} else if (!timeoutShowPromise) {
					timeoutShowPromise = $timeout(function(){
						timeoutShowPromise = null;
						if (custom) custom.showLoading();
						else $($window.document.body).addClass("sablowaitcursor");
					},400)
				}
			}
		},
		hideLoading: function() {
			showCounter--;
			if (showCounter == 0) {
				timeoutHidePromise = $timeout(function() {
					timeoutHidePromise = null;
					if (timeoutShowPromise) {
						$timeout.cancel(timeoutShowPromise);
						timeoutShowPromise = null;
					}
					else {
						if (custom) custom.hideLoading()
						else $($window.document.body).removeClass("sablowaitcursor");
					}
				},50);
			}
		},
		isShowing: function() {
			return showCounter > 0;
		}
	};
})

angular.module("webSocketModule").factory("$sabloTestability", ["$window",function($window) {
	var blockEventLoop = 0;
	var deferredEvents;
	var deferredLength = 0;
	// add a special testability method to the window object so that protractor can ask if there are waiting server calls.
	var callbackForTesting;
	$window.testForDeferredSabloEvents= function(callback) {
		if (!blockEventLoop && Object.keys(deferredEvents).length == deferredLength) callback(false); // false means there was no waiting deferred at all.
		else {
			callbackForTesting = callback;
		}
	}
	return {
		setEventList: function(eventList) {
			deferredEvents = eventList;
		},
		testEvents: function() {
			if (!blockEventLoop && callbackForTesting && Object.keys(deferredEvents).length == deferredLength) {
				callbackForTesting(true); // true: let protractor know that an event did happen.
				callbackForTesting = null;
			}
		},
		increaseEventLoop: function() {
			deferredLength++
			if (!blockEventLoop && callbackForTesting){
				callbackForTesting(true);
				callbackForTesting = null;
			}
		},
		decreaseEventLoop: function() {
			deferredLength--;
		},
		block: function(block) {
			if (block) blockEventLoop++;
			else blockEventLoop--;
			if (!blockEventLoop && callbackForTesting) {
				callbackForTesting(true);
				callbackForTesting = null;
			}
		}
	}
}]);

