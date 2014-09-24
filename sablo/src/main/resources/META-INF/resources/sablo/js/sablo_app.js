angular.module('sablo', ['webSocketModule', 'sabloUtils2']).config(function($controllerProvider) {
}).factory('$sabloApplication', function ($rootScope, $window, $webSocket, $sabloUtils2) {
	  
	   var wsSession = null;
	   
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
		   
		   getSession().callService('formService', 'requestData', {formname:formName}, false).then(
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
	   
	   // api
	   return {
		   connect: connect,
		   callService: callService,
		   getExecutor: getExecutor,
		   requestFormData: requestFormData
	   }
	   
}).run(function($window) {
	$window.alert('RAGTEST sablo app!')
})
