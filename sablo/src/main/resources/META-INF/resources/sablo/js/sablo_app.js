angular.module('sablo', ['webSocketModule', 'sabloUtils2']).config(function($controllerProvider) {
}).factory('$sabloApplication', function ($rootScope, $window, $webSocket, $sabloUtils, $sabloUtils2, $sabloConverters, $swingModifiers) {
	  
	   var wsSession = null;
	   
	   var formStatesConversionInfo = {};
	   
	   function getSession() {
		   if (wsSession == null) throw "Session is not created yet, first call connect()";
		   return wsSession;
	   }
	   
	   function connect() {
			$window.alert('RAGTEST connect')
			    wsSession = $webSocket.connect('', ['todosessionid'])
			   wsSession.onMessageObject = function (msg, conversionInfo) {
				  
				   alert('RAGTEST message: ' + msg)
			   };
	   }
	   
	   function callService(serviceName, methodName, argsObject, async) {
		   return getSession().callService(serviceName, methodName, argsObject, async)
	   }
	   
	   function requestFormData(formName, model) {
		   
		   return getSession().callService('formService', 'requestData', {formname:formName}, false).then(
				   function(data) {
					   for (var i in data) {
						   for (j in data[i]) model[i][j] = data[i][j]
					   }
				   });
	   }
	   
	   function getExecutor(formName) {
		   return {
			   on: function(beanName,eventName,property,args,rowId) {
				   // this is onaction, onfocuslost which is really configured in the html so it really 
				   // is something that goes to the server
				   var newargs = $sabloUtils2.getEventArgs(args,eventName);
				   var data = {}
				   if (property) {
					   data[property] = formStates[formName].model[beanName][property];
				   }
				   var cmd = {cmd:'event',formname:formName,beanname:beanName,event:eventName,args:newargs,changes:data}
				   if (rowId) cmd.rowId = rowId
				   return getSession().sendDeferredMessage(cmd)
			   },
		   }
	   }
	   
	   function sendChanges(now, prev, formname, beanname) {
		   var changes = getComponentChanges(now, prev, $sabloUtils2.getInDepthProperty(formStatesConversionInfo, formname, beanname),
				 null /*formStates[formname].layout[beanname]*/, null /*formStates[formname].properties.designSize*/, null /*getChangeNotifier(formname, beanname)*/);
		   if (Object.getOwnPropertyNames(changes).length > 0) {
			   getSession().sendMessageObject({cmd:'datapush',formname:formname,beanname:beanname,changes:changes})
		   }
	   }
	   
	   function getComponentChanges(now, prev, beanConversionInfo, beanLayout, parentSize, changeNotifier) {
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
//		   if (changes.location || changes.size || changes.visible || changes.anchors) {
//			   if (beanLayout) {
//				   applyBeanData(now /*formStates[formname].model[beanname]*/, beanLayout, changes, parentSize, changeNotifier);
//			   }
//		   }
		   for (prop in changes) {
			   if (beanConversionInfo && beanConversionInfo[prop]) changes[prop] = $sabloConverters.convertFromClientToServer(changes[prop], beanConversionInfo[prop], prev ? prev[prop] : undefined);
			   else changes[prop] = $sabloUtils.convertClientObject(changes[prop])
		   }
		   return changes;
	   }
	   

	   
	   // api
	   return {
		   connect: connect,
		   callService: callService,
		   getExecutor: getExecutor,
		   requestFormData: requestFormData,
		   sendChanges: sendChanges
	   }
	   
}).run(function($window) {
	$window.alert('RAGTEST sablo app!')
})
