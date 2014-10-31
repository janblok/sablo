angular.module('sabloApp', ['webSocketModule'])
.factory('$sabloInternal', function ($rootScope,$swingModifiers,webStorage,$anchorConstants, $q,$solutionSettings, $window, $webSocket,$sessionService,$sabloConverters,$sabloUtils,$utils) {
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
		   var changes = getComponentChanges(now, prev, $utils.getInDepthProperty(formStatesConversionInfo, formname, beanname),
				   formStates[formname].layout[beanname], formStates[formname].properties.designSize, getChangeNotifier(formname, beanname), formStates[formname].getScope());
		   if (Object.getOwnPropertyNames(changes).length > 0) {
			   sendRequest({cmd:'datapush',formname:formname,beanname:beanname,changes:changes})
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
			   return wsSession = $webSocket.connect(context, args)
		   },
		   
		   // used by custom property component[] to implement nested component logic
		   applyBeanData: applyBeanData,
		   getComponentChanges: getComponentChanges,
		   getChangeNotifier: getChangeNotifier,
		   
		   getFormState: function(name) { 
			   var defered = null
			   if (!deferredformStates[name]) {
				   var defered = $q.defer()
				   deferredformStates[name] = defered;
			   } else {
				   defered = deferredformStates[name]
			   }

			   if (formStates[name]) {
				   defered.resolve(formStates[name]); // then handlers are called even if they are applied after it is resolved
			   }			   
			   return defered.promise;
		   },
		   
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

		   cleardeferredformState: function(formname) {
			   if(deferredformStates[formname]){
				   if (typeof(formStates[name]) !== 'undefined') deferredformStates[formname].resolve(formStates[formname])
				   delete deferredformStates[formname]
			   }
		   },

		   initFormState: function(formName, beanDatas, formProperties, formScope) {
			   var state = formStates[formName];
			   // if the form is already initialized or if the beanDatas are not given, return that 
			   if (state != null || !beanDatas) return state; 

			   var model = {}
			   var api = {}
			   
				// send the special request initial data for this form 
				// this can also make the form (IFormUI instance) on the server if that is not already done
				callService('formService', 'initialrequestdata', {formname:formName}, true);

			   // init all the objects for the beans.
			   state = formStates[formName] = { model: model, api: api, properties: formProperties};
			   
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
		   }
	   }
})
