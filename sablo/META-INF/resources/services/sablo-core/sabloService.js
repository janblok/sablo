angular.module('$sabloService', ['sabloApp'])
.factory("$sabloService", ['$sabloApplication', '$rootScope', '$window', function($sabloApplication, $rootScope, $window) {
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
			$sabloApplication.addRagtest(function() {
				$window.open(url, name, specs, replace)
			})
			return true;
		}
	}	
}])
