angular.module('sampleApp').controller("anotherForm", function($scope, $window, $sabloApplication, $sabloUtils) {

//	$window.alert(' starting anotherForm');

	var formName = 'anotherForm';
	
    var beans = {
			thelabel: 	 { text : 'Initial value' },
			thebutton: 	 { text: 'push me'  },
			thetextfield: 	 { value : 'should be replaced with server data' },
			thecounter: 	 { n: 3 }
	};
	
	// TODO: to sablo_app
	function getExecutor(name, event, args) {
		return function(event, args) {
			return $sabloApplication.getExecutor(formName).on(name, event, args);
		};
	}
	
	// TODO: to sablo_app, generate from spec file based on componentts in form
	$scope.handlers = {
			thebutton: {
				onClick: function (event) { getExecutor('thebutton')('onClick', [event]); }
			}
	};

	var formProperties = {"designSize":{"width":640,"height":480},"size":{"width":640,"height":480}};
	
	var formState = $sabloApplication.initFormState(formName, beans, formProperties);
	// TODO: to sablo_app
	$scope.model = formState.model;
	$scope.api = formState.api;
	$scope.layout = formState.layout;
	$scope.formStyle = formState.style;
	$scope.formProperties = formState.properties;
	
	// TODO: to sablo_app (install watches)
	var wrapper = function(beanName) {
		return function(newvalue,oldvalue) {
			if(oldvalue !== newvalue) $sabloApplication.sendChanges(newvalue,oldvalue, formName, beanName);
		};
	};
	
	var watches = {};

	// TODO: create automatically
	formState.addWatches = function (beanNames) {
		if (beanNames) {
		 	for (var beanName in beanNames) {
		 		watches[beanName] = $scope.$watch($sabloUtils.generateWatchFunctionFor($scope, ["model", beanName]), wrapper(beanName), true);
			}
		}
		else {
			 for (var beanName in beans) {
				 watches[beanName] = $scope.$watch($sabloUtils.generateWatchFunctionFor($scope, ["model", beanName]), wrapper(beanName), true);
			 }
		}
	};
	
	formState.removeWatches = function (beanNames) {
		if (Object.getOwnPropertyNames(watches).length == 0) return false;
		
		if (beanNames) {
		 	for (var beanName in beanNames) {
			 	if (watches[beanName]) watches[beanName]();
			}
		}
		else {
			 for (var beanName in watches) {
			 	watches[beanName]();
			 }
		}
		return true;
	};
        
    formState.getScope = function() { return $scope; };

});
