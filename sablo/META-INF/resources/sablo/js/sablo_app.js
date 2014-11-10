angular.module('sabloApp', ['webSocketModule'])
.factory('$sabloApplication', function ($rootScope, $q, $webSocket,$sabloConverters,$sabloUtils) {
	   // formName:[beanname:{property1:1,property2:"test"}] needs to be synced to and from server
	   // this holds the form model with all the data, per form is this the "synced" view of the the IFormUI on the server 
	   // (3 way binding)
	   var formStates = {};
	   var formStatesConversionInfo = {};
	   
	   var deferredformStates = {};
	   var getChangeNotifier = function(formName, beanName) {
		   return function() {
			   // will be called by the custom property when it needs to send changes server size
			   var beanModel = formStates[formName].model[beanName];
			   sendChanges(beanModel, beanModel, formName, beanName);
		   }
	   }
	   
	   var getFormState = function(name) { 
		   var defered = null
		   if (!deferredformStates[name]) {
			   var defered = $q.defer()
			   deferredformStates[name] = defered;
		   } else {
			   defered = deferredformStates[name]
		   }

		   if (formStates[name] && !formStates[name].initializing) {
			   defered.resolve(formStates[name]); // then handlers are called even if they are applied after it is resolved
			   delete deferredformStates[name];
		   }			   
		   return defered.promise;
	   }

	   var getComponentChanges = function(now, prev, beanConversionInfo, parentSize, changeNotifier, componentScope) {
		   // first build up a list of all the properties both have.
		   var fulllist = $sabloUtils.getCombinedPropertyNames(now,prev);
		   var changes = {}, prop;

		   for (prop in fulllist) {
			   var changed = false;
			   if (!prev) {
				   changed = true;
			   }
			   else if (now[prop] && now[prop][$sabloConverters.INTERNAL_IMPL] && now[prop][$sabloConverters.INTERNAL_IMPL].isChanged)
			   {
				   changed = now[prop][$sabloConverters.INTERNAL_IMPL].isChanged();
			   }
			   else if (prev[prop] !== now[prop]) {
				   if (typeof now[prop] == "object") {
					   if ($sabloUtils.isChanged(now[prop], prev[prop], beanConversionInfo ? beanConversionInfo[prop] : undefined)) {
						   changed = true;
					   }
				   } else {
					   changed = true;
				   }
			   }
			   if (changed) {
				   changes[prop] = now[prop];
			   }
		   }
		   for (prop in changes) {
			   if (beanConversionInfo && beanConversionInfo[prop]) changes[prop] = $sabloConverters.convertFromClientToServer(changes[prop], beanConversionInfo[prop], prev ? prev[prop] : undefined);
			   else changes[prop] = $sabloUtils.convertClientObject(changes[prop])
		   }
		   return changes;
	   };
	   
	   var sendChanges = function(now, prev, formname, beanname) {
		   var changes = getComponentChanges(now, prev, $sabloUtils.getInDepthProperty(formStatesConversionInfo, formname, beanname),
				   formStates[formname].properties.designSize, getChangeNotifier(formname, beanname), formStates[formname].getScope());
		   if (Object.getOwnPropertyNames(changes).length > 0) {
			   callService('formService', 'dataPush', {formname:formname,beanname:beanname,changes:changes}, true)
		   }
		   return changes
	   };

	   var applyBeanData = function(beanModel, beanData, containerSize, changeNotifier, beanConversionInfo, newConversionInfo, componentScope) {
		   
		   if (newConversionInfo) { // then means beanConversionInfo should also be defined - we assume that
			   // beanConversionInfo will be granularly updated in the loop below
			   // (to not drop other property conversion info when only one property is being applied granularly to the bean)
			   beanData = $sabloConverters.convertFromServerToClient(beanData, newConversionInfo, beanModel, componentScope);
		   }

		   for(var key in beanData) {
			   // remember conversion info for when it will be sent back to server - it might need special conversion as well
			   if (newConversionInfo && newConversionInfo[key]) {
				   // if the value changed and it wants to be in control of it's changes, or if the conversion info for this value changed (thus possibly preparing an old value for being change-aware without changing the value reference)
				   if ((beanModel[key] !== beanData[key] || beanConversionInfo[key] !== newConversionInfo[key])
						   	&& beanData[key] && beanData[key][$sabloConverters.INTERNAL_IMPL] && beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
					   beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifier);
				   }
				   beanConversionInfo[key] = newConversionInfo[key];
			   }

			   // also make location and size available in model
			   beanModel[key] = beanData[key];
		   }
	   }
		 
	   var wsSession = null;
	   function getSession() {
		   if (wsSession == null) throw "Session is not created yet, first call connect()";
		   return wsSession;
	   }
	   
	   function sendRequest(objToStringify) {
		   getSession().sendMessageObject(objToStringify);
	   }
	   
	   function callService(serviceName, methodName, argsObject, async) {
		   return getSession().callService(serviceName, methodName, argsObject, async)
	   }
	   
	   return {
		   connect : function(context, args) {
			   wsSession = $webSocket.connect(context, args);
			   
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
					   getFormState(formname).then(getFormMessageHandler(formname, msg, conversionInfo));
				   }
		
				   if (conversionInfo && conversionInfo.call) msg.call = $sabloConverters.convertFromServerToClient(msg.call, conversionInfo.call, undefined, undefined);
				   if (msg.call) {
					   // {"call":{"form":"product","element":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}, // optionally "viewIndex":1 
					   // "{ conversions: {product: {datatextfield1: {0: "Date"}}} }
					   var call = msg.call;
					   return getFormState(call.form).then(function(formState) {
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
						   if (!func) {
							   console.warn("bean " + call.bean + " did not provide the api: " + call.api)
							   return null;
						   }
						   return func.apply(funcThis, call.args)
					   });
				   }
			   });
			   
			   function getFormMessageHandler(formname, msg, conversionInfo) {
				   return function (formState) {
					   var formModel = formState.model;
					   var newFormData = msg.forms[formname];
					   var newFormProperties = newFormData['']; // form properties
					   var newFormConversionInfo = (conversionInfo && conversionInfo.forms && conversionInfo.forms[formname]) ? conversionInfo.forms[formname] : undefined;

					   if(newFormProperties) {
						   if (newFormConversionInfo && newFormConversionInfo['']) newFormProperties = $sabloConverters.convertFromServerToClient(newFormProperties, newFormConversionInfo[''], formModel[''], formState.getScope());
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
								   var beanConversionInfo = newBeanConversionInfo ? $sabloUtils.getOrCreateInDepthProperty(formStatesConversionInfo, formname, beanname) : undefined; // we could do a get instead of undefined, but normally that value is not needed if the new conversion info is undefined
								   applyBeanData(formModel[beanname], newFormData[beanname], formState.properties.designSize, getChangeNotifier(formname, beanname), beanConversionInfo, newBeanConversionInfo, formState.getScope());
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
		   
		   // used by custom property component[] to implement nested component logic
		   applyBeanData: applyBeanData,
		   getComponentChanges: getComponentChanges,
		   getChangeNotifier: getChangeNotifier,
		   
		   getFormState: getFormState,
		   
		   getFormStatesConversionInfo: function() { return formStatesConversionInfo; },

		   hasFormstateLoaded: function(name) {
			  return typeof(formStates[name]) !== 'undefined'
		   },
		   
		   hasFormstate: function(name) {
			   return typeof(formStates[name]) !== 'undefined' || typeof(deferredformStates[name]) !== 'undefined'
		   },

		   clearformState: function(formName) {
			   delete formStates[formName];
		   },

		   initFormState: function(formName, beanDatas, formProperties, formScope) {
			   var state = formStates[formName];
			   // if the form is already initialized or if the beanDatas are not given, return that 
			   if (state != null || !beanDatas) return state; 

			   var model = {}
			   var api = {}
			   
				// send the special request initial data for this form 
				// this can also make the form (IFormUI instance) on the server if that is not already done
			   callService('formService', 'requestData', {formname:formName}, false).then(function (initialFormData) {
				   initialFormData = initialFormData[0]; // ret value is an one item array; the item contains both data and conversion info
				   if (initialFormData) {
					   var conversionInfo = initialFormData.conversions;
					   if (conversionInfo) delete initialFormData.conversions;

					   // if the formState is on the server but not here anymore, skip it. 
					   // this can happen with a refresh on the browser.
					   var formState = formStates[formName];
					   if (typeof(formState) == 'undefined') return;

					   var formModel = formState.model;
					   var initialFormProperties = initialFormData['']; // form properties

					   if (initialFormProperties) {
						   if (conversionInfo && conversionInfo['']) initialFormProperties = $sabloConverters.convertFromServerToClient(initialFormProperties, conversionInfo[''], formModel[''], formState.getScope());
						   if (!formModel['']) formModel[''] = {};
						   for(var p in initialFormProperties) {
							   formModel[''][p] = initialFormProperties[p]; 
						   } 
					   }

					   for (var beanname in initialFormData) {
						   // copy over the initialData, skip for form properties (beanname empty) as they were already dealt with
						   if (beanname != '') {
							   var initialBeanConversionInfo = conversionInfo ? conversionInfo[beanname] : undefined;
							   var beanConversionInfo = initialBeanConversionInfo ? $sabloUtils.getOrCreateInDepthProperty(formStatesConversionInfo, formName, beanname) : undefined; // we could do a get instead of undefined, but normally that value is not needed if the new conversion info is undefined
							   applyBeanData(formModel[beanname], initialFormData[beanname], formState.properties.designSize, getChangeNotifier(formName, beanname), beanConversionInfo, initialBeanConversionInfo, formState.getScope());
						   }
					   }
				   }

				   formState.addWatches();
				   delete formState.initializing;
				   
				   if(deferredformStates[formName]){
					   if (typeof(formStates[formName]) !== 'undefined') deferredformStates[formName].resolve(formStates[formName])
					   delete deferredformStates[formName]
				   }
			   });

			   // init all the objects for the beans.
			   state = formStates[formName] = { model: model, api: api, properties: formProperties, initializing: true};
			   
			   for(var beanName in beanDatas) {
				   model[beanName] = {};
				   api[beanName] = {};
			   }
			   
			   return state;
		   },

		   sendChanges: sendChanges,
		   callService: callService,
		   sendRequest: sendRequest,
		   
		   sendDeferredMessage: function(obj, scope) {
			   return getSession().sendDeferredMessage(obj, scope)
		   },
		   
		   getExecutor: function(formName) {
			   return {
				   on: function(beanName,eventName,property,args,rowId) {
					   return getFormState(formName).then(function (formState) {
						   // this is onaction, onfocuslost which is really configured in the html so it really 
						   // is something that goes to the server
						   var newargs = $sabloUtils.getEventArgs(args,eventName);
						   var data = {}
						   if (property) {
							   data[property] = formState.model[beanName][property];
						   }
						   var cmd = {cmd:'event',formname:formName,beanname:beanName,event:eventName,args:newargs,changes:data}
						   if (rowId) cmd.rowId = rowId
						   return getSession().sendDeferredMessage(cmd,formState.getScope())
					   });
				   },
			   }
		   }
		   
	   }
})
