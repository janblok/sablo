/**
 * Setup the webSocketModule.
 */

/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/window/window.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

let webSocketModule = angular.module('webSocketModule', ['$typesRegistry']).config(function($provide, $logProvider, $rootScopeProvider) {
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
	$rootScopeProvider.digestTtl(25); 
});

webSocketModule.factory('$propertyWatchUtils', function ($typesRegistry: sablo.ITypesRegistry) {
	/** returns an array of watch unregister functions */
	function watchDumbProperties(scope: angular.IScope, model, webObjectSpecification: sablo.IWebObjectSpecification,
	                             changedCallbackFunction: (newValue: any, oldValue:any, propertyName: string) => void): (() => void)[] {
		let unwatchF = [];
		const pds = webObjectSpecification?.getPropertyDescriptions();
		
		if (pds) {
			function getChangeFunction(propertyName: string, initialV: any) {
				let firstTime = true;
				return function(newValue: any, oldValue: any) {
					if (firstTime) {
						// value from server should not be sent back; but as directives in their controller methods can already change the values or properties
						// (so before the first watch execution) we can't just rely only on the "if (oldValue === newValue) return;" below cause in that case it won't send
						// a value that actually changed to server
						oldValue = initialV;
						initialV = undefined;
						firstTime = false;
					}
					if (oldValue === newValue) return;
					changedCallbackFunction(newValue, oldValue, propertyName);
				}
			}
			function getWatchFunction(propertyName) {
				return function() { 
					return model[propertyName];
				}
			}
			for (const propertyName of Object.keys(pds)) {
				const wf = getWatchFunction(propertyName);
				const pushToServer = pds[propertyName].getPropertyPushToServer();
				if (pushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) unwatchF.push(scope.$watch(wf, getChangeFunction(propertyName, wf()), pushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true));
			}
		}
		
		return unwatchF;
	}
	
	return {
		
		/** returns an array of watch unregister functions */
		watchDumbPropertiesForComponent: function watchDumbPropertiesForComponent(scope: angular.IScope, componentTypeName: string,
		                                                                          model: object, changedCallbackFunction: (newValue: any, oldValue:any, propertyName: string) => void): (() => void)[] {
			return watchDumbProperties(scope, model, 
					$typesRegistry.getComponentSpecification(componentTypeName),
					changedCallbackFunction);
		},
		
		/** returns an array of watch unregister functions */
		watchDumbPropertiesForService: function watchDumbPropertiesForService(scope: angular.IScope, serviceTypeName: string,
		                                                                      model: object, changedCallbackFunction: (newValue: any, oldValue:any, propertyName: string) => void): (() => void)[] {
			return watchDumbProperties(scope, model, 
					$typesRegistry.getServiceSpecification(serviceTypeName),
					changedCallbackFunction);
		}
		
	};
})


/**
 * Setup the $webSocket service.
 */
.factory('$webSocket',
		function($rootScope, $injector, $window, $log, $q, $services, $sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $swingModifiers, $interval,
				wsCloseCodes, $sabloLoadingIndicator, $timeout, $sabloTestability, $typesRegistry: sablo.ITypesRegistry) {

	let pathname = null;
	
	let queryString = null;
	
	let websocket = null;
	
	let connectionArguments = {};
	
	let lastServerMessageNumber = null;

	let nextMessageId = 1;
	
	let functionsToExecuteAfterIncommingMessageWasHandled: Array<{scopeHint: angular.IScope, task: () => Array<angular.IScope>}> = undefined;

	let currentRequestInfo = undefined

	let getNextMessageId = function() {
		return nextMessageId++;
	};

	let deferredEvents = {};
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
		
		let search = $window.location.search;
		if (search && search.indexOf('?') == 0) {
			return search.substring(1);
		}
		
		return search;
	}
	
	let getURLParameter = function getURLParameter(name) {
		return decodeURIComponent((new RegExp('[&]?\\b' + name + '=' + '([^&;]+?)(&|#|;|$)').exec(getQueryString())||[,""])[1].replace(/\+/g, '%20'))||null
	};
	
	let optimizeAndCallFormScopeDigest = function(scopesToDigest: ScopeSet, digestAsync?: boolean) {
		let scopesArray = scopesToDigest.getItems();
		for (let idx in scopesArray) {
			let s = scopesArray[idx];
			let scopeId = s.$id;
			let p = s.$parent;
			while (p && !scopesToDigest.hasItem(p)) p = p.$parent;
			if (!p) { // if no parent form scope is going to do digest
        		if (s["$$destroyed"]) $log.warn("[optimizeAndCallFormScopeDigest] - IGNORING an already $$destroyed scope (id:" + s.$id + "). Digest will not be called on such scopes.");
        		else {
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will call digest for scope: " + (s && s["formname"] ? s["formname"] : scopeId));
	
	                if (digestAsync) {
	                    if ($log.debugLevel === $log.SPAM)
	                        $log.debug("sbl * digestAsync == true ?! Will call $evalAsync for scope: " + (s && s["formname"] ? s["formname"] : scopeId));
	                    // should normally never happen - but for example unit tests could do this
	                	s.$evalAsync(function() { /* just to trigger a digest on that scope */ });
	                } else {
	                	setIMHDTScopeHintInternal(s);
	                	s.$digest();
	                	setIMHDTScopeHintInternal(undefined);
	                }
        		}
			} else if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Will NOT call digest for scope: " + (s && s["formname"] ? s["formname"] : scopeId) + " because a parent form scope " + (p["formname"] ? p["formname"] : p.$id) + " is in the list...");
		}
	}

	let handleMessage = function(message) {
		let obj;
		let responseValue;

        let oldFTEAIMWH = functionsToExecuteAfterIncommingMessageWasHandled; // normally this will always be null here; but in some browsers (FF) an alert on window can make these calls nest unexpectedly; so oldFTEAIMWH avoids exceptions in those cases as well - as we do not restore to null but to old when nesting happens
        let oldRequestInfo = currentRequestInfo; // same as for the oldFTEAIMWH comment above - to handle that tricky nesting scenario

        currentRequestInfo = undefined;
		functionsToExecuteAfterIncommingMessageWasHandled = [];
		let hideIndicatorCounter = 0;

		try {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Received message from server: " + JSON.stringify(message, function(key, value) {
				  if (key === 'data') {
				    return "...see below...";
				  }
				  return value;
				}, "  "));

			let message_data = message.data;
			let separator = message_data.indexOf('#');
			if (separator >= 0 && separator < 5) {
				// the json is prefixed with a message number: 123#{bla: "hello"}
				lastServerMessageNumber = message_data.substring(0, separator);
				if ($log.debugLevel === $log.SPAM) $log.debug("sbl * message number = " + lastServerMessageNumber);
				message_data = message_data.substr(separator + 1);
			}
			// else message has no seq-no
			
			obj = JSON.parse(message_data);
			
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * message.data (parsed) = " + JSON.stringify(obj, null, "  "));

			if(obj.cmsgid && deferredEvents[obj.cmsgid] && deferredEvents[obj.cmsgid].promise) {
				currentRequestInfo = deferredEvents[obj.cmsgid].promise.requestInfo;
			}
 
			let responseValueType: sablo.IType<any>;
			
			if (obj.serviceApis) {
				// services calls; first process the once with the flag 'apply_first'
				for (let index in obj.serviceApis) {
					let serviceCall = obj.serviceApis[index];
					if (serviceCall['pre_data_service_call']) {
						let serviceSpec = $typesRegistry.getServiceSpecification(serviceCall.name);
						let serviceInstance = $injector.get(serviceCall.name);
						if (serviceInstance
								&& serviceInstance[serviceCall.call]) {
							let serviceCallSpec = serviceSpec?.getApiFunction(serviceCall.call);
							
							if (serviceCall.args) for (let argNo = 0; argNo < serviceCall.args.length; argNo++) {
								serviceCall.args[argNo] = $sabloConverters.convertFromServerToClient(serviceCall.args[argNo], serviceCallSpec?.getArgumentType(argNo),
								                        undefined, undefined, undefined, undefined, $sabloUtils.PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES)
							}
							
							responseValueType = serviceCallSpec?.returnType;

							// responseValue keeps last services call return value
							responseValue = serviceInstance[serviceCall.call].apply(serviceInstance, serviceCall.args);
							$services.digest(serviceCall.name);
						}
					}
				}
			}

			// message
			if (obj.msg) {
				let scopesToDigest = new ScopeSet();
				for (let handler in onMessageObjectHandlers) {
					let ret = onMessageObjectHandlers[handler](obj.msg, scopesToDigest);
					if (ret) responseValue = ret;
					
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Checking if any form scope changes need to be digested (obj.msg).");
				}
				optimizeAndCallFormScopeDigest(scopesToDigest); // as we are currently not inside a digest cycle, digest the affected scopes
			}

			if (obj.msg && obj.msg.services) {
				$services.updateServiceScopes(obj.msg.services);
			}

			if (obj.serviceApis) {
				// normal service api calls
				for (let index in obj.serviceApis) {
					let serviceCall = obj.serviceApis[index];
					if (!serviceCall['pre_data_service_call']) {
						let serviceSpec = $typesRegistry.getServiceSpecification(serviceCall.name);
						let serviceInstance = $injector.get(serviceCall.name);
						if (serviceInstance
								&& serviceInstance[serviceCall.call]) {
							let serviceCallSpec = serviceSpec?.getApiFunction(serviceCall.call);
							
							if (serviceCall.args) for (let argNo = 0; argNo < serviceCall.args.length; argNo++) {
								serviceCall.args[argNo] = $sabloConverters.convertFromServerToClient(serviceCall.args[argNo], serviceCallSpec?.getArgumentType(argNo),
								                        undefined, undefined, undefined, undefined, $sabloUtils.PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES)
							}
							
							responseValueType = serviceCallSpec?.returnType;
							
							// responseValue keeps last services call return value
							responseValue = serviceInstance[serviceCall.call].apply(serviceInstance, serviceCall.args);
							$services.digest(serviceCall.name);
						}
					}
				}
			}

			// if the indicator is showing and this object wants a return message then hide the indicator until we send the response
			let hideIndicatorCounter = 0;
			// if a request to a service is being done then this could be a blocking 
			if (obj && obj.smsgid) 
			{
				while ($sabloLoadingIndicator.isShowing() ) {
					hideIndicatorCounter++;
					$sabloLoadingIndicator.hideLoading();
				}
			}
			
			// component api calls
			if (obj.componentApis)
			{
				let scopesToDigest = new ScopeSet();
				for(let i = 0;i < obj.componentApis.length;i++) 
				{
					for (let handler in onMessageObjectHandlers) {
						let ret = onMessageObjectHandlers[handler]({ call: obj.componentApis[i] }, scopesToDigest);
                        if (ret) responseValue = ret;
					}
					
					if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Checking if any (obj.componentApis) form scopes changes need to be digested (obj.componentApis).");
				}
				optimizeAndCallFormScopeDigest(scopesToDigest); // as we are currently not inside a digest cycle, digest the affected scopes
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
					let response = {
							smsgid : obj.smsgid
					}
					if (ret != undefined) {
						response['ret'] = $sabloConverters.convertFromClientToServer(ret, responseValueType, undefined, undefined, $sabloUtils.PROPERTY_CONTEXT_FOR_OUTGOING_ARGS_AND_RETURN_VALUES);
					}
					if (hideIndicatorCounter) {
						while ( hideIndicatorCounter-- > 0 ) {
							$sabloLoadingIndicator.showLoading();
						}
					}
					sendMessageObject(response);
				}, function(reason) {
					if (isPromiseLike(responseValue)) $sabloTestability.decreaseEventLoop();
					// error
					$log.error("Error (follows below) in parsing/processing this message with smsgid '" + obj.smsgid + "' (async): " + message_data);
					$log.error(reason);
					// server wants a response; send failure so that browser side script doesn't hang
					let response = {
							smsgid : obj.smsgid,
							err: "Error while executing ($q deferred) client side code. Please see browser console for more info. Error: " + reason
					}
					if (hideIndicatorCounter) {
						while ( hideIndicatorCounter-- > 0 ) {
							$sabloLoadingIndicator.showLoading();
						}
					}
					sendMessageObject(response);
				});
			}

            // got the return value for a client-to-server call (that has a defer/waiting promise) back from the server
			if (obj.cmsgid) { // response to event
				let deferredEvent = deferredEvents[obj.cmsgid];
				if (deferredEvent != null && angular.isDefined(deferredEvent)) {
					if (obj.exception) {
                        // something went wrong
						// do a default conversion although I doubt it will do anything (don't think server will send client side type for exceptions)
						obj.exception = $sabloConverters.convertFromServerToClient(obj.exception, undefined, undefined, undefined, undefined, undefined, undefined);
						deferredEvent.reject(obj.exception);
                        $rootScope.$applyAsync(); // not sure that this is needed at all here but it is meant to replace (by making sure a root digest is called) a very old sync $apply that was used here for the reject; it would error out because of that in firefox where an $apply might happen on top of another $apply then in some situations (alerts combined with sync calls to and from server); and that is not allowed by angular 
					} else {
						deferredEvent.resolve(obj.ret);
                        $rootScope.$applyAsync(); // not sure that this is needed at all here but it is meant to replace (by making sure a root digest is called) a very old sync $apply that was used here for the resolve; it would error out because of that in firefox where an $apply might happen on top of another $apply then in some situations (alerts combined with sync calls to and from server); and that is not allowed by angular 
					}
				} else $log.warn("Response to an unknown handler call dismissed; can happen (normal) if a handler call gets interrupted by a full browser refresh.");
				delete deferredEvents[obj.cmsgid];
				$sabloTestability.testEvents();
				$sabloLoadingIndicator.hideLoading();
			}			
		} catch (e) {
			$log.error("Error (follows below) in parsing/processing this message: " + message.data);
			$log.error(e);
			if (obj && obj.smsgid) {
				// server wants a response; send failure so that browser side script doesn't hang
				let response = {
						smsgid : obj.smsgid,
						err: "Error while executing client side code. Please see browser console for more info. Error: " + e
				}
				if (hideIndicatorCounter) {
					while ( hideIndicatorCounter-- > 0 ) {
						$sabloLoadingIndicator.showLoading();
					}
				}
				sendMessageObject(response);
			}
		} finally {
			let err;
			let scopesToDigest = new ScopeSet();

			let toExecuteAfterIncommingMessageWasHandled = functionsToExecuteAfterIncommingMessageWasHandled;

            functionsToExecuteAfterIncommingMessageWasHandled = oldFTEAIMWH; // clear/restore this before calling just in the unlikely case that some handlers want to add more such tasks (and we don't want to loose those but rather execute them right away)
			currentRequestInfo = oldRequestInfo;
			
			for (let i = 0; i < toExecuteAfterIncommingMessageWasHandled.length; i++) {
				try {
					let touchedScopes = toExecuteAfterIncommingMessageWasHandled[i].task();
					if (touchedScopes) {
						touchedScopes.forEach(
								(touchedScope) => {
				            		if (touchedScope["$$destroyed"]) $log.warn("[addIncomingMessageHandlingDoneTask] - exec after incomming is done; func returned an already $$destroyed scope (id:" + touchedScope.$id + ") to be digested. This usually happens when some component failed to unregister some listeners on scope $destroy. Please check to see why this happens - in order to avoid memory leaks. Func:\n", toExecuteAfterIncommingMessageWasHandled[i].task);
									scopesToDigest.putItem(touchedScope);
								}
						);
					} else if (toExecuteAfterIncommingMessageWasHandled[i].scopeHint) {
						scopesToDigest.putItem(toExecuteAfterIncommingMessageWasHandled[i].scopeHint);
					}
				} catch (e) {
					$log.error("Error (follows below) in executing PostIncommingMessageHandlingTask: " + toExecuteAfterIncommingMessageWasHandled[i]);
					$log.error(e);
					err = e;
				}
			}
			
			optimizeAndCallFormScopeDigest(scopesToDigest); // as we are currently not inside a digest cycle, digest the affected scopes
			
			if (err) throw err;
		}
	}
	
	let currentIMHDTScopeHint: angular.IScope;
	
	/**
	 * For internal use. Only used in order to implement backwards compatibility with addIncomingMessageHandlingDoneTask tasks that do not return the touched/modified scopes.
	 * In that case we try to guess those scopes via this method that should be called whenever we know code executing on a specific scope might call addIncomingMessageHandlingDoneTask(...).
	 */
	let setIMHDTScopeHintInternal = function(scope: angular.IScope) {
		currentIMHDTScopeHint = scope;
	}

	/**
	 * Wait for all incoming changes to be applied to properties first (if a message from server is currently being processed) before executing given function
	 * 
	 * @param func will be called once the incoming message from server has been processed; can return an array of angular scopes that were touched/changed by this function; those scopes will be
	 * digested after all "incomingMessageHandlingDoneTask"s are executed. If the function returns nothing then sablo tries to detect situations when the task is added while some property value change
	 * from server is being processed and to digest the appropriate scope afterwards...
	 */
	let addIncomingMessageHandlingDoneTask = function(func: () => Array<angular.IScope>) {
		if (functionsToExecuteAfterIncommingMessageWasHandled) functionsToExecuteAfterIncommingMessageWasHandled.push({scopeHint: currentIMHDTScopeHint, task: func});
		else {
            // should normally not happen (as this is meant to be called by code running while a server message is being handled) - but for example unit tests could do this
			// will not addPostIncommingMessageHandlingTask while not handling an incoming message; the task can execute right away then (maybe it was called due to a change detected in a watch instead of property listener)
            let touchedScopes = func();
            let scopesSet = null;
            if (touchedScopes) {
            	scopesSet = new ScopeSet(touchedScopes);
            	touchedScopes.forEach(function (scope) {
            		if (scope["$$destroyed"]) $log.warn("[addIncomingMessageHandlingDoneTask] - exec directly; func returned an already $$destroyed scope (id:" + scope.$id + ") to be digested. This usually happens when some component failed to unregister some listeners on scope $destroy. Please check to see why this happens - in order to avoid memory leaks. Func:\n", func);
            	});
            }
            else if (currentIMHDTScopeHint)
            	scopesSet = new ScopeSet([currentIMHDTScopeHint]);

			// here we don't know where it's called from (might already be in a digest cycle so in order to avoid an exception trying to call digest again, better use $scope.evalAsync(), so async = true)
            if (scopesSet) optimizeAndCallFormScopeDigest(scopesSet, true);
		}
	}

	let sendMessageObject = function(obj) {
		if ($sabloUtils.getCurrentEventLevelForServer()) {
			obj.prio = $sabloUtils.getCurrentEventLevelForServer();
		}
		let msg = JSON.stringify(obj)
		if (isConnected()) {
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Sending message to server: " + JSON.stringify(obj, null, "  "));
			websocket.send(msg)
		}
		else
		{
			if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Disconnected; will add the following to pending messages to be sent to server: " + msg);
			pendingMessages = pendingMessages || []
			pendingMessages.push(msg)
		}
	}

	let callService = function(serviceName, methodName, argsObject, async) {
		let cmd = {
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
			let deferred = $q.defer();
			let cmsgid = getNextMessageId()
			deferredEvents[cmsgid] = deferred
			$sabloLoadingIndicator.showLoading();
			cmd['cmsgid'] = cmsgid
			sendMessageObject(cmd)
			return deferred.promise;
		}
	}
	$sabloTestability.setEventList(deferredEvents);

	let onOpenHandlers = [];
	let onErrorHandlers = [];
	let onCloseHandlers = [];
	let onMessageObjectHandlers: sablo.MessageObjectHandler[] = [];

	let WebsocketSession = function() {

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
	let wsSession = new WebsocketSession();

	let connected = 'INITIAL'; // INITIAL/CONNECTED/RECONNECTING/CLOSED
	if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode: ... INITIAL (" + new Date().getTime() + ")");
	let pendingMessages = undefined

	// heartbeat, detect disconnects before websocket gives us connection-closed.
	let heartbeatMonitor = undefined;
	let lastHeartbeat = undefined;
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
			for (let i in pendingMessages) {
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
		let new_uri;
		if ($window.location.protocol === "https:") {
			new_uri = "wss:";
		} else {
			new_uri = "ws:";
		}
		new_uri += "//" + $window.location.host;
		let pathname = getPathname();
		let lastIndex = pathname.lastIndexOf("/");
		if (lastIndex > 0) {
			pathname = pathname.substring(0, lastIndex);
		}
		if (context && context.length > 0)
		{
			let lastIndex = pathname.lastIndexOf(context);
			if (lastIndex >= 0) {
				pathname = pathname.substring(0, lastIndex) + pathname.substring(lastIndex + context.length)
			}
		}
		new_uri += pathname + (websocketUri?websocketUri:'/websocket');
		for (let a in args) {
			if (args.hasOwnProperty(a)) {
				new_uri += '/' + args[a]
			}
		}

		// connectnr is only used in this call
		new_uri += "?connectNr="+Math.floor((Math.random() * 10000000000000))+"&";

		for (let a in queryArgs)
		{
			if (queryArgs.hasOwnProperty(a)) {
				new_uri += a+"="+queryArgs[a]+"&";
			}
		}
		
		if (lastServerMessageNumber != null) {
			new_uri += "lastServerMessageNumber="+lastServerMessageNumber+"&";
		}
		

		let queryString = getQueryString();
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

		connect: function(context, args, queryArgs, websocketUri) {

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
				for (let handler in onOpenHandlers) {
					onOpenHandlers[handler](evt);
				}
			}
			websocket.onerror = function(evt) {
				stopHeartbeat();
				for (let handler in onErrorHandlers) {
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
				for (let handler in onCloseHandlers) {
					onCloseHandlers[handler](evt);
				}
			}
			websocket.onconnecting = function(evt) {
				// if the reason out of sync then it is always a reload.
				if (evt.reason == 'CLIENT-OUT-OF-SYNC') {
					// Server detected that we are out-of-sync, reload completely
					$window.location.reload();
				}
				// client is shutdown just force close the websocket and set the connected state toe CLOSED so no reconnecting is shown
				else if (evt.reason == 'CLIENT-SHUTDOWN') {
					
					websocket.close();
					
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
			// the public api of sablo (like connect, client side types, services)
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
		
		setIMHDTScopeHintInternal: setIMHDTScopeHintInternal,
		
		addIncomingMessageHandlingDoneTask: addIncomingMessageHandlingDoneTask,
		
		disconnect: function() {
			if (websocket) {
				websocket.close();
				connected = 'CLOSED';
				if ($log.debugLevel === $log.SPAM) $log.debug("sbl * Connection mode (disconnect): ... CLOSED (" + new Date().getTime() + ")");
			}
		},

		getURLParameter: getURLParameter,
		
		setPathname: setPathname,
		getPathname: getPathname,
		
		setQueryString: setQueryString,
		getQueryString: getQueryString,

		getCurrentRequestInfo: function() {
			return currentRequestInfo;
		}
	};
}).factory("$services", function($rootScope, $sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $propertyWatchUtils: sablo.IPropertyWatchUtils,
		$log: sablo.ILogService, $typesRegistry: sablo.ITypesRegistry, $pushToServerUtils: sablo.IPushToServerUtils) {
	// serviceName:{} service model
	let serviceScopes = $rootScope.$new(true);
	let serviceScopesDynamicClientSideTypes = {};
	let watches = {}
	let wsSession = null;
	
	const addWatchesToService = function(serviceName: string) {
        watches[serviceName] = $propertyWatchUtils.watchDumbPropertiesForService(serviceScopes[serviceName], serviceName, serviceScopes[serviceName].model, function(newValue, oldValue, property) {
                    sendServiceChanges(newValue, oldValue, serviceName, property);
                });    
    }
    
    const createServiceScopeIfNeeded = function(serviceName: string, andAddWatches: boolean) {
        if (!serviceScopes[serviceName]) {
            serviceScopes[serviceName] = serviceScopes.$new(true);
            serviceScopes[serviceName].model = {};
            serviceScopesDynamicClientSideTypes[serviceName] = {};

            
            if (andAddWatches) addWatchesToService(serviceName);
        }
        return serviceScopes[serviceName];
    };
    
	const getServiceScope = function(serviceName: string) {
		serviceName = scriptifyServiceNameIfNeeded(serviceName);
		
        createServiceScopeIfNeeded(serviceName, true);
		return serviceScopes[serviceName];
	};
	
	const setChangeNotifierIfSmartProperty = function(propertyValue: any, serviceName: string, propertyName: string) {
        if (propertyValue && propertyValue[$sabloConverters.INTERNAL_IMPL] && propertyValue[$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
            let changeNotifier = getChangeNotifier(serviceName, propertyName);
            propertyValue[$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifier);
            // we check for changes anyway in case a property type doesn't do that itself in setChangeNotifier
            if (propertyValue[$sabloConverters.INTERNAL_IMPL].isChanged && propertyValue[$sabloConverters.INTERNAL_IMPL].isChanged()) changeNotifier();
        }
    }

	let sendServiceChanges = function(now, prev, servicename, propertyName) {
		let changes = {}
		
		var propertyType: sablo.IType<any>;
		const serviceSpec = $typesRegistry.getServiceSpecification(servicename);
		propertyType = $sabloUtils.getInDepthProperty(serviceScopesDynamicClientSideTypes, servicename, propertyName); // first check if it's a dynamic type

		if (!propertyType) { // try static types for props.
			propertyType = serviceSpec?.getPropertyType(propertyName);
		}
		
		const serviceScope = getServiceScope(servicename);
		
		changes[propertyName] = $sabloConverters.convertFromClientToServer(now, propertyType, prev,
				serviceScope, {
					getProperty: (propertyN: string) => { return serviceScope.model ? serviceScope.model[propertyN] : undefined },
					getPushToServerCalculatedValue: () => { return serviceSpec ? serviceSpec.getPropertyPushToServer(propertyName) : $pushToServerUtils.reject }
				});

        // set/update change notifier just in case a new full value was set into a smart property type that needs a changeNotifier for that specific property 
        setChangeNotifierIfSmartProperty(now, servicename, propertyName);
		
		wsSession.sendMessageObject( { servicedatapush: servicename, changes: changes } );
	};
	let getChangeNotifier = function(servicename, propertyName) {
		return function() {
			let serviceModel = serviceScopes[servicename].model;
			sendServiceChanges(serviceModel[propertyName], serviceModel[propertyName], servicename, propertyName);
		}
	};
	
	function scriptifyServiceNameIfNeeded(serviceName) {
		if (serviceName) {
			// transform serviceNames like testpackage-myTestService into testPackageMyTestService - as latter is how getServiceScope usually gets called (from developer generated code for services) from service client js;
			// but who knows, maybe someone will try the dashed version and wonder why it doesn't work
			
			// this should do the same as ClientService.java #convertToJSName()
			let packageAndName = serviceName.split("-");
			if (packageAndName.length > 1) {
				serviceName = packageAndName[0];
				for (let i = 1; i < packageAndName.length; i++) {
					if (packageAndName[1].length > 0) serviceName += packageAndName[i].charAt(0).toUpperCase() + packageAndName[i].slice(1);
				}
			}
		}
		return serviceName;
	}

	return {
		getServiceScope: getServiceScope,
		updateServiceScopes: function(services) {
			for (let servicename in services) {
				// current model
				let serviceScope = serviceScopes[servicename];
				const serviceSpec: sablo.IWebObjectSpecification = $typesRegistry.getServiceSpecification(servicename); // get static client side types for this service - if it has any
				const propertyContextCreator: sablo.IPropertyContextCreator = $pushToServerUtils.newRootPropertyContextCreator(
						function(propertyName: string) { return serviceScope.model ? serviceScope.model[propertyName] : undefined },
						serviceSpec
				);
				
				if (!serviceScope) {
					serviceScope = createServiceScopeIfNeeded(servicename, false); // so no previous service state; initialize it now

					// convert all properties, remember type for when a client-server conversion will be needed
					for (var propertyName in services[servicename]) {
						let propertyType = serviceSpec?.getPropertyType(propertyName); // get static client side type if any
						
						serviceScope.model[propertyName] = $sabloConverters.convertFromServerToClient(services[servicename][propertyName], propertyType, undefined,
								serviceScopesDynamicClientSideTypes[servicename], propertyName, serviceScope, propertyContextCreator.withPushToServerFor(propertyName));
						
						setChangeNotifierIfSmartProperty(serviceScope.model[propertyName], servicename, propertyName);
					}
				} else {
					let serviceData = services[servicename];

					// unregister the watches
					if (watches[servicename]) watches[servicename].forEach(function (unwatchFunctionElement, index, array) {
						unwatchFunctionElement();
					});

					// convert all properties, remember type for when a client-server conversion will be needed
					for (let propertyName in serviceData) {
						let oldValue = serviceScope.model[propertyName];
						
						let staticPropertyType = serviceSpec?.getPropertyType(propertyName); // get static client side type if any
						let oldPropertyType = staticPropertyType ? staticPropertyType : serviceScopesDynamicClientSideTypes[servicename][propertyName]; // use dynamic type (if available) if no static is available
						
						serviceScope.model[propertyName] = $sabloConverters.convertFromServerToClient(serviceData[propertyName], staticPropertyType, serviceScope.model[propertyName],
								serviceScopesDynamicClientSideTypes[servicename], propertyName, serviceScope, propertyContextCreator.withPushToServerFor(propertyName));

						let newPropertyType = staticPropertyType ? staticPropertyType : serviceScopesDynamicClientSideTypes[servicename][propertyName]; // use dynamic type (if available) if no static is available
						
						if (newPropertyType) {
							if (serviceScope.model[propertyName] !== oldValue || oldPropertyType !== newPropertyType) {
								// setChangeNotifier can be called now after the new conversion info and value are set (getChangeNotifier(servicename, propertyName) will probably use the values in model and that has to point to the new value if reference was changed)
								// as setChangeNotifier on smart property types might end up calling the change notifier right away to announce it already has changes (because for example
								// the convertFromServerToClient on that property type above might have triggered some listener to the service that uses it which then requested
								// another thing from the property type and it then already has changes...) // TODO should we decouple this scenario? if we are still processing server to client changes when change notifier is called we could trigger the change notifier later/async for sending changes back to server...
                                setChangeNotifierIfSmartProperty(serviceScope.model[propertyName], servicename, propertyName);
							}
						}
					}
				}
				
				// register a new watch
				watches[servicename] = $propertyWatchUtils.watchDumbPropertiesForService(serviceScope, servicename, serviceScope.model, function(newValue, oldValue, property) {
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
}).factory("$sabloConverters", function($log, $typesRegistry: sablo.ITypesRegistryForSabloConverters) {
	const CONVERSION_CL_SIDE_TYPE_KEY = "_T";
	const VALUE_KEY = "_V";

	let convertFromServerToClient = function(serverSentData: any,
			typeOfData: sablo.IType<any>,
			currentClientData: any,
			dynamicPropertyTypesHolder: { [nameOrIndex: string]: sablo.IType<any> } /*some types decide at runtime the type needed on client - for example dataprovider type could send date, and we will store that info here*/,
			keyForDynamicTypes: string,
			scope: angular.IScope,
			propertyContext: sablo.IPropertyContext): any {
				
		let convertedData = serverSentData;
		if (typeOfData) {
			convertedData = typeOfData.fromServerToClient(serverSentData, currentClientData, scope, propertyContext);
			
			// if no dynamic type, remove any previously stored dynamic type for this value
			if (dynamicPropertyTypesHolder && keyForDynamicTypes) delete dynamicPropertyTypesHolder[keyForDynamicTypes];
		} else if (serverSentData instanceof Object && serverSentData[CONVERSION_CL_SIDE_TYPE_KEY] != undefined) {
			// NOTE: default server conversions will end up here with 'object' type if they need any Date conversions (value or sub-property/sub-elements)
			
			// so a conversion is required client side but the type is not known beforehand on client (can be a result of
			// JSONUtils.java # defaultToJSONValue(...) or could be a dataprovider type in a record view - foundset based
			// impl. should handle these varying simple types nicer already inside special property types such as foundset/component/foundsetLinked)
			
			let lookedUpType = $typesRegistry.getAlreadyRegisteredType(serverSentData[CONVERSION_CL_SIDE_TYPE_KEY]);
			if (lookedUpType) {
				// if caller is interested in storing dynamic types do that; (remember dynamic conversion info for when it will be sent back to server - it might need special conversion as well)
				if (dynamicPropertyTypesHolder && keyForDynamicTypes) dynamicPropertyTypesHolder[keyForDynamicTypes] = lookedUpType;
				
				convertedData = lookedUpType.fromServerToClient(serverSentData[VALUE_KEY], currentClientData, scope, propertyContext);
				
			} else { // needed type not found - will not convert
				$log.error("no such type was registered (s->c varying or default type conversion) for: " + JSON.stringify(serverSentData[CONVERSION_CL_SIDE_TYPE_KEY], null, 2) + ".");
			}
		} else if (dynamicPropertyTypesHolder && keyForDynamicTypes) delete dynamicPropertyTypesHolder[keyForDynamicTypes]; // no dynamic type; so remove any previously stored dynamic type for this value
		
		return convertedData;
	};

	let lookedUpObjectType:sablo.IType<any>;
	
	// converts from a client property JS value to a JSON that can be sent to the server using the appropriate registered handler
	let convertFromClientToServer = function(newClientData: any,
			typeOfData: sablo.IType<any>,
			oldClientData: any,
			scope: angular.IScope,
			propertyContext: sablo.IPropertyContext): any {
			
		let ret: any;	
		if (typeOfData) ret = typeOfData.fromClientToServer(newClientData, oldClientData, scope, propertyContext);
		else {
			// this should rarely or never happen... but do our best to not fail sending (for example due to Date instances that can't be stringified via standard JSON)
			if (! lookedUpObjectType) lookedUpObjectType = $typesRegistry.getAlreadyRegisteredType("object");
			
			if (lookedUpObjectType) ret = lookedUpObjectType.fromClientToServer(newClientData, oldClientData, scope, propertyContext);
			else { // 'object' type not found?! it should always be there - no default conversion can be done...
				$log.error("'object' type was not registered (c->s default conversion) for.");
				ret = newClientData;
			}
		}
		
		return ret === undefined ? null : ret; // JSON does not allow undefined; use null instead...
	};

	return <sablo.ISabloConverters> {

		/**
		 * In a custom property value, the val[$sabloConverters.INTERNAL_IMPL] is to be used for internal state/impl details only - not to be accessed by components
		 */
		INTERNAL_IMPL: '__internalState', // important - if you change this value you HAVE to change a number of typescript classes that declare this at compile time
		CONVERSION_CL_SIDE_TYPE_KEY: CONVERSION_CL_SIDE_TYPE_KEY,
        VALUE_KEY: VALUE_KEY,
        
		prepareInternalState: function(propertyValue, optionalInternalStateValue?) {
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

		convertFromClientToServer: convertFromClientToServer

	};
}).factory("$sabloUtils", function($log, $sabloConverters:sablo.ISabloConverters,$swingModifiers) {
	let getCombinedPropertyNames = function(now,prev) {
		let fulllist = {}
		if (prev) {
			let prevNames = Object.getOwnPropertyNames(prev);
			for(let i=0; i < prevNames.length; i++) {
				fulllist[prevNames[i]] = true;
			}
		}
		if (now) {
			let nowNames = Object.getOwnPropertyNames(now);
			for(let i=0;i < nowNames.length;i++) {
				fulllist[nowNames[i]] = true;
			}
		}
		return fulllist;
	}

	let isChanged = function(now, prev, clientSideType: sablo.IType<any>) {
		if (clientSideType && now && now[$sabloConverters.INTERNAL_IMPL] && now[$sabloConverters.INTERNAL_IMPL].isChanged) {
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
				let fulllist = getCombinedPropertyNames(now, prev);
				for (let prop in fulllist) {
					if(prop == "$$hashKey") continue; // ng repeat creates a child scope for each element in the array any scope has a $$hashKey property which must be ignored since it is not part of the model
					if (prev[prop] !== now[prop]) {
						if (typeof now[prop] == "object") {
							if (isChanged(now[prop],prev[prop], undefined)) {
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
	let currentEventLevelForServer;
	
    const PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES:sablo.IPropertyContext = {
        getProperty: (propertyName: string): any  => { return undefined; }, // arguments/return values received from server in case of api calls/handlers are not properties of a component or service so can't return sibling properties 
        getPushToServerCalculatedValue: () => { return sablo.typesRegistry.PushToServerEnum.reject; }
    };
    
    const PROPERTY_CONTEXT_FOR_OUTGOING_ARGS_AND_RETURN_VALUES:sablo.IPropertyContext = {
        getProperty: (propertyName: string): any  => { return undefined; }, // arguments/return values sent to server in case of api calls/handlers are not properties of a component or service so can't return sibling properties
        getPushToServerCalculatedValue: () => { return sablo.typesRegistry.PushToServerEnum.allow; }
    };
    
	let sabloUtils:sablo.ISabloUtils = {
			// execution priority on server value used when for example a blocking API call from server needs to request more data from the server through this change
			// or whenever during a (blocking) API call to client we want some messages sent to the server to still be processed.
			EVENT_LEVEL_SYNC_API_CALL: 500,
			
			PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES: PROPERTY_CONTEXT_FOR_INCOMMING_ARGS_AND_RETURN_VALUES,
			PROPERTY_CONTEXT_FOR_OUTGOING_ARGS_AND_RETURN_VALUES: PROPERTY_CONTEXT_FOR_OUTGOING_ARGS_AND_RETURN_VALUES,
			
			// eventLevelValue can be undefined for DEFAULT
			setCurrentEventLevelForServer: function(eventLevelValue) {
				currentEventLevelForServer = eventLevelValue;
			},

			getCurrentEventLevelForServer: function() {
				return currentEventLevelForServer;
			},

			isChanged: isChanged,
			getCombinedPropertyNames: getCombinedPropertyNames,

			getEventArgs: function(args, eventName: string, handlerSpecification: sablo.IWebObjectFunction)
			{
				let newargs = []
				for (let i = 0; i < args.length; i++) {
					let arg = args[i]
					if (arg && arg.originalEvent) arg = arg.originalEvent;
					
					// TODO these two ifs could be moved to a "JSEvent" client side type implementation (and if spec is correct it will work through the normal types system)
					if(arg  instanceof MouseEvent ||arg  instanceof KeyboardEvent){
						let $event = arg;
						let eventObj = {}
						let modifiers = 0;
						if($event.shiftKey) modifiers = modifiers||$swingModifiers.SHIFT_MASK;
						if($event.metaKey) modifiers = modifiers||$swingModifiers.META_MASK;
						if($event.altKey) modifiers = modifiers|| $swingModifiers.ALT_MASK;
						if($event.ctrlKey) modifiers = modifiers || $swingModifiers.CTRL_MASK;

						eventObj['type'] = 'event'; 
						eventObj['eventName'] = eventName; 
						eventObj['modifiers'] = modifiers;
						eventObj['timestamp'] = new Date().getTime();
						eventObj['data'] = $event['data'];
						eventObj['x']= $event['pageX'];
						eventObj['y']= $event['pageY'];
						arg = eventObj
					}
					else if (arg instanceof Event || arg instanceof $.Event)
					{
						let eventObj = {}
						eventObj['type'] = 'event'; 
						eventObj['eventName'] = eventName;
						eventObj['timestamp'] = new Date().getTime();
						eventObj['data'] = arg['data'];
						arg = eventObj
					}
					else arg = $sabloConverters.convertFromClientToServer(arg, handlerSpecification?.getArgumentType(i), undefined, undefined, PROPERTY_CONTEXT_FOR_OUTGOING_ARGS_AND_RETURN_VALUES);

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

				let ret = arguments[0];
				if (ret == undefined || ret === null || arguments.length == 1) return ret;
				let p;
				let i;
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

				let ret = arguments[0];
				if (ret == undefined || ret === null || arguments.length == 1) return ret;
				let i;
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
				let clone = Object.create(newPrototype);

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
		template: '<div ng-show="reconnecting()" class="svy-reconnecting-overlay" style="z-index:2147483647;width:100%;height:100%;position:absolute;" ng-transclude></div>',
		transclude: true,
		scope: true,
		controller: function($scope, $element, $attrs) {
			$scope.reconnecting = reconnecting;
		}
	}
}).factory("$sabloLoadingIndicator", function($injector, $window,$log,$timeout) {
	// look for a custom implementation of the indicator
	let custom = null;
	if ($injector.has("loadingIndicator")) {
		custom = $injector.get("loadingIndicator");
		if (custom && (custom.showLoading == undefined || custom.hideLoading == undefined)){
			custom = null;
			$log.warn("a custom loading indicator is defined but doesn't have the 2 functions: showLoading or hideLoading")
		}
	}
	if (custom == null) {
		// if there is none then use the default, that uses a class to make sure the wait cursor is shown/set on all dom elements.
		let style = $window.document.createElement('style');
		style.type = 'text/css';
		style.innerHTML = '.sablowaitcursor, .sablowaitcursor * { cursor: wait !important; }';
		document.getElementsByTagName('head')[0].appendChild(style);
	}
	let showCounter = 0;
	let timeoutHidePromise = null;
	let timeoutShowPromise = null;
	let isDefaultShowing = false;
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
						else {
							$($window.document.body).addClass("sablowaitcursor");
							isDefaultShowing = true;
						}
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
						else {
							$($window.document.body).removeClass("sablowaitcursor");
							isDefaultShowing = false;
						}
					}
				},50);
			}
		},
		isShowing: function() {
			return showCounter > 0;
		},
		isDefaultShowing: function() {
			return isDefaultShowing;
		}
	};
}).factory("$sabloDeferHelper", function($timeout: angular.ITimeoutService, $log, $sabloTestability, $q: angular.IQService) {
	function retrieveDeferForHandling(msgId, internalState) {
	     let deferred = internalState.deferred[msgId];
	     let defer;
	     if (deferred) {
	    	 defer = deferred.defer;
	    	 $timeout.cancel(deferred.timeoutPromise);
	    	 delete internalState.deferred[msgId];
	    	 
	    	 if (Object.keys(internalState.deferred).length == 0) $sabloTestability.block(false);
	     }
	     return defer;
	}
	
	return <sablo.ISabloDeferHelper> {
		
		initInternalStateForDeferring: function(internalState, timeoutRejectLogPrefix) {
			internalState.deferred = {}; // key is msgId (which always increases), values is { defer: ...q defer..., timeoutPromise: ...timeout promise for cancel... }
			internalState.currentMsgId = 0;
			internalState.timeoutRejectLogPrefix = timeoutRejectLogPrefix;
		},
		
		initInternalStateForDeferringFromOldInternalState: function(internalState, oldInternalState) {
			internalState.deferred = oldInternalState.deferred;
			internalState.currentMsgId = oldInternalState.currentMsgId;
			internalState.timeoutRejectLogPrefix = oldInternalState.timeoutRejectLogPrefix;
		},
		
		getNewDeferId: function(internalState) {
			if (Object.keys(internalState.deferred).length == 0) $sabloTestability.block(true);
				
			let d = $q.defer();
			let newMsgID = ++internalState.currentMsgId;
			internalState.deferred[newMsgID] = { defer: d, timeoutPromise : $timeout(function() {
				// if nothing comes back for a while do cancel the promise to avoid memory leaks/infinite waiting
				let defer = retrieveDeferForHandling(newMsgID, internalState);
				if (defer) {
					let rejMsg = "deferred req. with id " + newMsgID + " was rejected due to timeout...";
					defer.reject(rejMsg);
					if ($log.debugEnabled && $log.debugLevel === $log.SPAM) $log.debug((internalState.timeoutRejectLogPrefix ? internalState.timeoutRejectLogPrefix : "") + rejMsg);
				}
			}, 120000) }; // is 2 minutes cancel-if-not-resolved too high or too low?

			return newMsgID;
		},
		
		cancelAll: function(internalState) {
			for (let id in internalState.deferred) {
				$timeout.cancel(internalState.deferred[id].timeoutPromise);
				internalState.deferred[id].defer.reject();
			}
			internalState.deferred = {};
		},
		
		retrieveDeferForHandling: retrieveDeferForHandling
		
	};
});

angular.module("webSocketModule").factory("$sabloTestability", ["$window","$log",function($window, $log) {
	let blockEventLoop = 0;
	let deferredEvents;
	let deferredLength = 0;
	// add a special testability method to the window object so that protractor can ask if there are waiting server calls.
	let callbackForTesting;
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
			if (deferredLength > 0) deferredLength--;
			else $log.warn("decrease event loop called without an increase")
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

interface HashCodeFunc<T> {
    (T: any): number;
}

/** A custom 'hash set' based on a configurable hash function received in constructor for now it can only do putItem, hasItem and getItems */
class CustomHashSet<T> {
	private items: { [index: number]: T; } = {};
	
	constructor(private hashCodeFunc: HashCodeFunc<T>) {}
	
	public putItem(item: T) : void {
		this.items[this.hashCodeFunc(item)] = item;
	}
	
	public hasItem(item: T) : boolean {
		return !!this.items[this.hashCodeFunc(item)];
	}
	
	public getItems() : Array<T> {
		let allItems: Array<T> = [];
		for (let k in this.items) allItems.push(this.items[k]);
		
		return allItems;
	}
}

/** A CustomHashSet that uses as hashCode for angular scopes their $id. */
class ScopeSet extends CustomHashSet<angular.IScope> {
	
	constructor(initialScopes?: Array<angular.IScope>) {
		super(function(s : angular.IScope) {
			return s.$id; // hash them by angular scope id to avoid calling digest on the same scope twice
		});
		
		if (initialScopes) initialScopes.forEach((value) => { this.putItem(value) });
	}
	
}