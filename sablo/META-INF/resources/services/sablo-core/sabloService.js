angular.module('$sabloService', ['sabloApp'])
.factory("$sabloService", ['$sabloApplication', '$rootScope', function($sabloApplication, $rootScope) {
	return {
		setCurrentFormUrl: function(url) {
			$rootScope.$apply(function () {
				 $sabloApplication.setCurrentFormUrl(url, false)
	        });
		},
		getCurrentFormUrl: function() {
			return $sabloApplication.getCurrentFormUrl(false)
		}
	}	
}])
