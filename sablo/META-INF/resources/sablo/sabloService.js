angular.module('$sabloService', ['sabloApp'])
.factory("$sabloService", ['$sabloApplication', '$rootScope', '$window','$q', function($sabloApplication, $rootScope, $window,$q) {
	var deferredEvents = {};
	var messageID = 0;
	return {
		setCurrentFormUrl: function(url) {
			$rootScope.$apply(function () {
				 $sabloApplication.setCurrentFormUrl(url, false)
	        });
		},
		getCurrentFormUrl: function() {
			return $sabloApplication.getCurrentFormUrl(false)
		},
		windowOpen: function(url, name, specs, replace) {
			$sabloApplication.addToCurrentServiceCall(function() {
				$window.open(url, name, specs, replace)
			})
		},
		createDeferedEvent: function() {
			var deferred = $q.defer();
			var cmsgid = messageID++;
			deferredEvents[cmsgid] = deferred;
			return {promise:deferred.promise,cmsgid:cmsgid};
		},
		
		resolveDeferedEvent: function(msgid, argument, success) {
			var defered = deferredEvents[msgid];
			if (defered) {
				delete deferredEvents[msgid];
				if (success) defered.resolve(argument);
				else defered.reject(argument)
			}
		}
	}	
}])
