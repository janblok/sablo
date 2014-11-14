angular.module('sampleApp', ['sabloApp', 'mylabel', 'mybutton', 'mytextfield', 'mycounter']).config(function() {
}).controller("SampleController", function($scope, $rootScope, $window, $sabloApplication) {
	$scope.windowTitle = 'Sample Aplication';
	$scope.currentPanel = 'forms/mainForm.html';
	
	// $window.alert('Connecting...');
	$sabloApplication.connect('', ['thesessionid']);
})
.run(function($window) {
	// $window.alert('Sample Startup');
});
