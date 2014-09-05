angular.module('sablo', ['webSocketModule']).config(function($controllerProvider) {
}).factory('$sabloApplication', function ($rootScope, $window, $webSocket) {
	  
	   var wsSession = null;
	   
	   function getSession() {
		   if (wsSession == null) throw "Session is not created yet, first call connect()";
		   return wsSession;
	   }
	   
	   function connect() {
			$window.alert('RAGTEST connect')
			    wsSession = $webSocket.connect('', ['todosessionid', 'todowindowid'])
			   wsSession.onMessageObject = function (msg, conversionInfo) {
				  
				   alert('RAGTEST message: ' + msg)
			   };
	   }
	    
	   return {
		   
		   connect: connect,
		   
		   callService: function(serviceName, methodName, argsObject, async) {
			   return getSession().callService(serviceName, methodName, argsObject, async)
		   }
	   }
	   
}).run(function($window) {
	$window.alert('RAGTEST sablo app!')
})
