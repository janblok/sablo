/**
 * Setup the webSocketModule.
 */
var webSocketModule = angular.module('webSocketModule', []);

/**
 * Setup the $webSocket service.
 */
webSocketModule.factory('$webSocket',
		function($rootScope, $injector, $window, $log, $q, $services, $sabloConverters, $sabloUtils, $swingModifiers, $interval) {

	var websocket = null;

	var nextMessageId = 1;

	var getNextMessageId = function() {
		return nextMessageId++;
	};

	var deferredEvents = {};

	var getURLParameter = function getURLParameter(name) {
		return decodeURIComponent((new RegExp('[?|&]' + name + '=' + '([^&;]+?)(&|#|;|$)').exec($window.location.search)||[,""])[1].replace(/\+/g, '%20'))||null
	};

	var handleMessage = function(message) {
		var obj
		var responseValue
		try {
			obj = JSON.parse(message.data);

			// data got back from the server
			if (obj.cmsgid) { // response to event
				var deferredEvent = deferredEvents[obj.cmsgid];
				if (deferredEvent != null && angular.isDefined(deferredEvent)) {
					if (obj.exception) {
						// something went wrong
						if (obj.conversions && obj.conversions.exception) {
							obj.exception = $sabloConverters.convertFromServerToClient(obj.exception, obj.conversions.exception, undefined, undefined, undefined)
						}
						$rootScope.$apply(function() {
							deferredEvent.reject(obj.exception);
						})
					} else {
						if (obj.conversions && obj.conversions.ret) {
							obj.ret = $sabloConverters.convertFromServerToClient(obj.ret, obj.conversions.ret, undefined, undefined, undefined)
						}
						$rootScope.$apply(function() {
							deferredEvent.resolve(obj.ret);
						})
					}
				} else $log.warn("Response to an unknown handler call dismissed; can happen (normal) if a handler call gets interrupted by a full browser refresh.");
				delete deferredEvents[obj.cmsgid];
			}

			if (obj.msg && obj.msg.services) {
				$services.updateServiceScopes(obj.msg.services, (obj.conversions && obj.conversions.msg) ? obj.conversions.msg.services : undefined);
			}

			if (obj.services) {
				// services call
				if (obj.conversions && obj.conversions.services) {
					obj.services = $sabloConverters.convertFromServerToClient(obj.services, obj.conversions.services, undefined, undefined, undefined)
				}
				for (var index in obj.services) {
					var service = obj.services[index];
					var serviceInstance = $injector.get(service.name);
					if (serviceInstance
							&& serviceInstance[service.call]) {
						// responseValue keeps last services call return value
						responseValue = serviceInstance[service.call].apply(serviceInstance, service.args);
						$services.digest(service.name);
					}
				}
			}

			// message
			if (obj.msg) {
				for (var handler in onMessageObjectHandlers) {
					var ret = onMessageObjectHandlers[handler](obj.msg, obj.conversions ? obj.conversions.msg : undefined)
					if (ret) responseValue = ret;
				}
			}

			if (obj && obj.smsgid) {
				// server wants a response; responseValue may be a promise
				$q.when(responseValue).then(function(ret) {
					// success
					var response = {
							smsgid : obj.smsgid
					}
					if (ret != undefined) {
						response.ret = $sabloUtils.convertClientObject(ret);
					}
					sendMessageObject(response);
				}, function(reason) {
					// error
					$log.error("Error (follows below) in parsing/processing this message (async): " + message.data);
					$log.error(reason);
					// server wants a response; send failure so that browser side script doesn't hang
					var response = {
							smsgid : obj.smsgid,
							err: "Error while executing ($q deferred) client side code. Please see browser console for more info. Error: " + reason
					}
					sendMessageObject(response);
				});
			}
		} catch (e) {
			$log.error("Error (follows below) in parsing/processing this message: " + message.data);
			$log.error(e);
			if (obj && obj.smsgid) {
				// server wants a response; send failure so that browser side script doesn't hang
				var response = {
						smsgid : obj.smsgid,
						err: "Error while executing client side code. Please see browser console for more info. Error: " + e
				}
				sendMessageObject(response);
			}
		}
	}

	var sendMessageObject = function(obj) {
		if ($sabloUtils.getCurrentEventLevelForServer()) {
			obj.prio = $sabloUtils.getCurrentEventLevelForServer();
		}
		var msg = JSON.stringify(obj)
		if (connected) {
			websocket.send(msg)
		}
		else
		{
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
			cmd.cmsgid = cmsgid
			sendMessageObject(cmd)
			return deferred.promise;
		}
	}

	var onOpenHandlers = []
	var onErrorHandlers = []
	var onCloseHandlers = []
	var onMessageObjectHandlers = []

	var WebsocketSession = function() {

		// api
		this.callService = callService

		this.sendMessageObject = sendMessageObject;

		this.onopen = function(handler) {
			onOpenHandlers.push(handler)
		}
		this.onerror = function(handler) {
			onErrorHandlers.push(handler)
		}
		this.onclose = function(handler) {
			onCloseHandlers.push(handler)
		}
		this.onMessageObject = function(handler) {
			onMessageObjectHandlers.push(handler)
		}
	};
	var wsSession = new WebsocketSession();

	var connected = undefined; // tri-state, undefined: not-connected-yet, true: connected, false: disconnected
	var pendingMessages = undefined

	// heartbeat, detect disconnects before websocket gives us connection-closed.
	var heartbeatMonitor = undefined;
	var lastHeartbeat = undefined;
	function startHeartbeat() {
		if (!angular.isDefined(heartbeatMonitor)) {
			lastHeartbeat = new Date().getTime();
			heartbeatMonitor = $interval(function() {

				websocket.send("P"); // ping

				if (connected) {
					var milliseconds = new Date().getTime();
					if (milliseconds - lastHeartbeat > 5000) {
						// no response within 5 seconds
						connected = false;
					}
				}
			}, 1000);
		}
	}
	
	function stopHeartbeat() {
		if (angular.isDefined(heartbeatMonitor)) {
			$interval.cancel(heartbeatMonitor);
			heartbeatMonitor = undefined;
		}
	}
	
	function handleHeartbeat(message) {
		if (message.data == "p") { // pong
			if (!connected) {
				$rootScope.$apply(function() {
					connected = true;
				});
			}
			lastHeartbeat = new Date().getTime();
			return true;
		}
		return false;
	}
	
	/**
	 * The $webSocket service API.
	 */
	return {

		connect : function(context, args, queryArgs) {

			var loc = window.location, new_uri;
			if (loc.protocol === "https:") {
				new_uri = "wss:";
			} else {
				new_uri = "ws:";
			}
			new_uri += "//" + loc.host;
			var pathname = loc.pathname;
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
			new_uri += pathname + '/websocket';
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

			if (loc.search)
			{
				new_uri +=  loc.search.substring(1,loc.search.length); 
			}
			else
			{
				new_uri = new_uri.substring(0,new_uri.length-1);
			}

			websocket = typeof(ReconnectingWebSocket) == 'undefined' ? new WebSocket(new_uri) : new ReconnectingWebSocket(new_uri);

			websocket.onopen = function(evt) {
				$rootScope.$apply(function() {
					connected = true;
				})
				if (pendingMessages) {
					for (var i in pendingMessages) {
						websocket.send(pendingMessages[i])
					}
					pendingMessages = undefined
				}
				startHeartbeat();
				for (var handler in onOpenHandlers) {
					onOpenHandlers[handler](evt)
				}
			}
			websocket.onerror = function(evt) {
				stopHeartbeat();
				for (var handler in onErrorHandlers) {
					onErrorHandlers[handler](evt)
				}
			}
			websocket.onclose = function(evt) {
				stopHeartbeat();
				$rootScope.$apply(function() {
					connected = false;
				})
				for (var handler in onCloseHandlers) {
					onCloseHandlers[handler](evt)
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

		getSession: function() {
			return wsSession;
		},

		isConnected: function() {
			return connected === true;
		},

		isDisconnected: function() {
			return connected === false; // undefined means never connected, false means was connected but disconnected now
		},

		disconnect: function() {
			if(websocket) {
				websocket.close();
			}
		},

		getURLParameter: getURLParameter
	};
}).factory("$services", function($rootScope, $sabloConverters, $sabloUtils){
	// serviceName:{} service model
	var serviceScopes = $rootScope.$new(true);
	var serviceScopesConversionInfo = {};
	var watches = {}
	var wsSession = null;
	var sendServiceChanges = function(now, prev, servicename) {
		// first build up a list of all the properties both have.
		var fulllist = $sabloUtils.getCombinedPropertyNames(now,prev);
		var conversionInfo = serviceScopesConversionInfo[servicename];
		var changes = {}, prop;

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
		for (prop in changes) {
			wsSession.sendMessageObject({servicedatapush:servicename,changes:changes})
			return;
		}
	};
	var getChangeNotifier = function(servicename) {
		return function() {
			var serviceModel = serviceScopes[servicename];
			sendServiceChanges(serviceModel, serviceModel, servicename);
		}
	};
	var watch = function(servicename) {
		return function(newVal, oldVal) {
			if (newVal === oldVal) return;
			sendServiceChanges(newVal,oldVal,servicename);
		}
	};
	return {
		getServiceScope: function(serviceName) {
			if (!serviceScopes[serviceName]) {
				serviceScopes[serviceName] = serviceScopes.$new(true);
				serviceScopes[serviceName].model = {};
				watches[serviceName] = serviceScopes[serviceName].$watch("model",watch(serviceName),true);
			}
			return serviceScopes[serviceName];
		},
		updateServiceScopes: function(services, conversionInfo) {
			for(var servicename in services) {
				// current model
				var serviceScope = serviceScopes[servicename];
				if (!serviceScope) {
					serviceScopes[serviceName] = serviceScopes.$new(true);
					// so no previous service state; set it now
					if (conversionInfo && conversionInfo[servicename]) {
						// convert all properties, remember type for when a client-server conversion will be needed
						services[servicename] = $sabloConverters.convertFromServerToClient(services[servicename], conversionInfo[servicename], undefined, serviceScopes[serviceName], function() { return serviceScopes[serviceName].model })
						var changeNotifier = getChangeNotifier(servicename);
						for (var pn in conversionInfo[servicename]) {
							if (services[servicename][pn] && services[servicename][pn][$sabloConverters.INTERNAL_IMPL]
							&& services[servicename][pn][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								services[servicename][pn][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifier);
							}
						}
						serviceScopesConversionInfo[servicename] = conversionInfo[servicename];
					}
					serviceScopes[servicename].model = services[servicename];
				}
				else {
					var serviceData = services[servicename];
					// unregister the watch.
					watches[servicename]();
					var changeNotifier = (conversionInfo && conversionInfo[servicename]) ? getChangeNotifier(servicename) : undefined;

					for(var key in serviceData) {
						if (conversionInfo && conversionInfo[servicename] && conversionInfo[servicename][key]) {
							// convert property, remember type for when a client-server conversion will be needed
							if (!serviceScopesConversionInfo[servicename]) serviceScopesConversionInfo[servicename] = {};
							serviceData[key] = $sabloConverters.convertFromServerToClient(serviceData[key], conversionInfo[servicename][key], serviceScope.model[key], serviceScope, function() { return serviceScope.model })

							if ((serviceData[key] !== serviceScope.model[key] || serviceScopesConversionInfo[servicename][key] !== conversionInfo[servicename][key]) && serviceData[key]
							&& serviceData[key][$sabloConverters.INTERNAL_IMPL] && serviceData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								serviceData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifier);
							}
							serviceScopesConversionInfo[servicename][key] = conversionInfo[servicename][key];
						} else if (angular.isDefined(serviceScopesConversionInfo[servicename]) && angular.isDefined(serviceScopesConversionInfo[servicename][key])) {
							delete serviceScopesConversionInfo[servicename][key];
						}

						serviceScope.model[key] = serviceData[key];
					}
				}
				// register a new watch
				watches[servicename] = serviceScopes[servicename].$watch("model",watch(servicename),true);
				serviceScopes[servicename].$digest();
			}
		},
		digest: function(servicename) {
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

	return {

		/**
		 * In a custom property value, the val[$sabloConverters.INTERNAL_IMPL] is to be used for internal state/impl details only - not to be accessed by components
		 */
		INTERNAL_IMPL: '__internalState',

		prepareInternalState: function(propertyValue, optionalInternalStateValue) {
			if (angular.isUndefined(optionalInternalStateValue)) optionalInternalStateValue = {};

			if (Object.defineProperty) {
				// try to avoid unwanted iteration/non-intended interference over the private property state
				Object.defineProperty(propertyValue, this.INTERNAL_IMPL, {
					configurable: false,
					enumerable: false,
					writable: false,
					value: optionalInternalStateValue
				});
			} else propertyValue[$sabloConverters.INTERNAL_IMPL] = optionalInternalStateValue;
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
		registerCustomPropertyHandler : function(propertyTypeID, customHandler) {
			customPropertyConverters[propertyTypeID] = customHandler;
		}

	};
}).factory("$sabloUtils", function($log, $sabloConverters) {
	var getCombinedPropertyNames = function(now,prev) {
		var fulllist = {}
		if (prev) {
			var prevNames = Object.getOwnPropertyNames(prev);
			for(var i=0;i<prevNames.length;i++) {
				fulllist[prevNames[i]] = true;
			}
		}
		if (now) {
			var nowNames = Object.getOwnPropertyNames(now);
			for(var i=0;i<nowNames.length;i++) {
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
	var sabloUtils = {
			// execution priority on server value used when for example a blocking API call from server needs to request more data from the server through this change
			// or whenever during a (blocking) API call to client we want some messages sent to the server to still be processed.
			EVENT_LEVEL_SYNC_API_CALL: 500,

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
					value = value.getTime();
				}
				return value;
			},

			getEventArgs: function(args,eventName)
			{
				var newargs = []
				for (var i in args) {
					var arg = args[i]
					if (arg && arg.originalEvent) arg = arg.originalEvent;
					if(arg  instanceof MouseEvent ||arg  instanceof KeyboardEvent){
						var $event = arg;
						var eventObj = {}
						var modifiers = 0;
						if($event.shiftKey) modifiers = modifiers||$swingModifiers.SHIFT_DOWN_MASK;
						if($event.metaKey) modifiers = modifiers||$swingModifiers.META_DOWN_MASK;
						if($event.altKey) modifiers = modifiers|| $swingModifiers.ALT_DOWN_MASK;
						if($event.ctrlKey) modifiers = modifiers || $swingModifiers.CTRL_DOWN_MASK;

						eventObj.type = 'event'; 
						eventObj.eventName = eventName; 
						eventObj.modifiers = modifiers;
						eventObj.timestamp = $event.timeStamp;
						eventObj.x= $event.pageX;
						eventObj.y= $event.pageY;
						arg = eventObj
					}
					else if (arg instanceof Event || arg instanceof $.Event) {
						var eventObj = {}
						eventObj.type = 'event'; 
						eventObj.eventName = eventName; 
						eventObj.timestamp = arg.timeStamp;
						arg = eventObj
					}
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

			//do not watch __internalState as that is handled by servoy code
			generateWatchFunctionFor: function () {
				var pathArg = arguments;
				var filteredObject = function (scope) {
					var result = {}; // deep watch doesn't care when object/array instances differ, going deeper for content so we just return a new obj. each time
					var modelObject = sabloUtils.getInDepthProperty.apply(sabloUtils, pathArg);

					for (k in modelObject) {
						if (modelObject[k] && modelObject[k].__internalState && modelObject[k].__internalState.setChangeNotifier) {
							continue;
						}
						result[k] = modelObject[k];
					}
					return result;
				};
				return filteredObject;
			}
	}

	return sabloUtils;
}).value("$swingModifiers" ,{
	SHIFT_MASK : 1,
	CTRL_MASK : 2,
	META_MASK : 4,
	ALT_MASK : 8,
	ALT_GRAPH_MASK : 32,
	BUTTON1_MASK : 16,
	BUTTON2_MASK : 8,
	META_MASK : 4,
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
		return $webSocket.isDisconnected(); 
	}
  
  // TODO: should we not introduce a scope and just watch '$webSocket.isDisconnected()'?
  return {
    restrict: 'EA',
    template: '<div ng-show="reconnecting()" ng-transclude></div>',
    transclude: true,
    scope: true,
    controller: function($scope, $element, $attrs) {
      $scope.reconnecting = reconnecting;
    }
  }
});


;
