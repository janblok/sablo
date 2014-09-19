angular.module('sablo', ['webSocketModule']).config(function($controllerProvider) {
}).factory('$sabloApplication', function ($rootScope, $window, $webSocket) {
	  
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
	   
	   // api
	   return {
		   connect: connect,
		   callService: callService,
		   requestFormData: requestFormData
	   }
	   
}).run(function($window) {
	$window.alert('RAGTEST sablo app!')
})
