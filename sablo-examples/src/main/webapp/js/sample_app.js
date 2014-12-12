angular.module('sampleApp', ['sabloApp', 'webStorageModule', 'mylabel', 'mybutton', 'mytextfield', 'mycounter']).config(function() {
}).controller("SampleController", function($scope, $rootScope, $window, $sabloApplication, webStorage) {
	$scope.windowTitle = 'Sample Aplication';
	$scope.currentPanel = 'forms/mainForm.html';
	
	// $window.alert('Connecting...');
	$sabloApplication.connect('', [webStorage.session.get("sessionid")]).onMessageObject(function (msg, conversionInfo) {
		   if (msg.sessionid) {
			   webStorage.session.add("sessionid",msg.sessionid);
		   }
	   });
})
.run(function($window) {
	// $window.alert('Sample Startup');
});
