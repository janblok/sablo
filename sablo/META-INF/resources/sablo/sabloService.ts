/// <reference path="./js/types_registry.ts" />

angular.module('$sabloService', ['sabloApp'])
.factory("$sabloService", ['$sabloApplication', '$rootScope', '$window', '$q', '$typesRegistry', function($sabloApplication, $rootScope, $window, $q, $typesRegistry: sablo.typesRegistry.TypesRegistry) {
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
			var defid = messageID++;
			deferredEvents[defid] = deferred;
			return {promise:deferred.promise,defid:defid};
		},

		resolveDeferedEvent: function(defid, argument, success) {
			var defered = deferredEvents[defid];
			if (defered) {
				delete deferredEvents[defid];
				if (success) defered.resolve(argument);
				else defered.reject(argument)
			}
		}

	}

}]);

