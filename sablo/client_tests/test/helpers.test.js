describe('styles helpers', function() {
	//jasmine.DEFAULT_TIMEOUT_INTERVAL = 1000;
    var $scope
    var $compile

	beforeEach(function(){
	   // 1. Include your application module for testing.
	  module('sabloApp');
	  
      // 2. Define a new mock module. (don't need to mock the servoy module for tabpanel since it receives it's dependencies with attributes in the isolated scope)
      // 3. Define a provider with the same name as the one you want to mock (in our case we want to mock 'servoy' dependency.
//      angular.module('servoyMock', [])
//          .factory('$X', function(){
//              // Define you mock behaviour here.
//          });

      // 4. Include your new mock module - this will override the providers from your original module.
//      angular.mock.module('servoyMock');

      // 5. Get an instance of the provider you want to test.
      inject(function(_$rootScope_,_$compile_ ,$templateCache,_$q_){
    	  
    	  $compile = _$compile_
    	  $scope = _$rootScope_.$new();
  	  })
  	  // mock timout
	  jasmine.clock().install();
	});
    afterEach(function() {
        jasmine.clock().uninstall();
    })
  	it("should apply sibling svy-tabseq", function() {
  		var template= '<div svy-tabseq="1" svy-tabseq-config="{root: true}"><div name="myTag" svy-tabseq="myModel1"></div><div name="myOtherTag" svy-tabseq="myModel2"></div><div name="myOtherOtherTag" svy-tabseq="myModel3"></div></div>'; 
  		$scope.myModel1 = -2;
  		$scope.myModel2 = -2;
  		$scope.myModel3 = -2;
  		var myDiv = $compile(template)($scope);
  		expect($(myDiv.children()[0]).attr('tabIndex')).toBe('-1');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('-1');
  		expect($(myDiv.children()[2]).attr('tabIndex')).toBe('-1');
  		$scope.myModel1 = 5;
  		$scope.myModel2 = -2;
  		$scope.myModel3 = 3;
  		$scope.$digest();
  		expect($(myDiv.children()[0]).attr('tabIndex')).toBe('2');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('-1');
  		expect($(myDiv.children()[2]).attr('tabIndex')).toBe('1');
  		// runtime change 
  		$scope.myModel2 = 2;
  		$scope.$digest();		
  		expect($(myDiv.children()[0]).attr('tabIndex')).toBe('3');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('1');
  		expect($(myDiv.children()[2]).attr('tabIndex')).toBe('2');
  	});
  	
  	it("should apply default/no tabIndex for children", function() {
  		// simulate record view form with 1 portal in body and an element in footer
  		var template= '<div svy-tabseq="1" svy-tabseq-config="{root: true}">' +
				  			'<div name="myTag" svy-tabseq="myModel1" svy-tabseq-config="{container: true, reservedGap: 150}">' +
				  				'<div name="myOtherTag" svy-tabseq="1" svy-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" svy-tabseq="myModel111">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" svy-tabseq="myModel112">' +
					  				'</div>' +
				  				'</div>' +
				  				'<div name="myOtherTag" svy-tabseq="2" svy-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" svy-tabseq="myModel121">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" svy-tabseq="myModel122">' +
					  				'</div>' +
				  				'</div>' +
				  			'</div>' +
				  			'<div name="myOtherOtherTag" svy-tabseq="myModel2"></div>' +
			  			'</div>';
  		
  		// default tab seq. - no tabIndex should be set at all
  		$scope.myModel1 = undefined;
  		$scope.myModel111 = undefined;
  		$scope.myModel112 = undefined;
  		$scope.myModel121 = undefined;
  		$scope.myModel122 = undefined;
  		$scope.myModel2 = undefined;
  		
  		var myDiv = $compile(template)($scope);

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe(undefined);
  		
  		// some non-default tab seq check as well
  		$scope.myModel1 = 2;
  		$scope.myModel111 = 1;
  		$scope.myModel112 = 1;
  		$scope.myModel121 = 1;
  		$scope.myModel122 = 1;
  		$scope.myModel2 = 1;
  		$scope.$digest();

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('3');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('3');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('1');

  		// DONT SPLIT BELOW CODE --------------- START --------------------------
  		// another one
  		$scope.myModel1 = undefined;
  		$scope.myModel111 = 2;
  		$scope.myModel112 = 1;
  		$scope.myModel121 = 2;
  		$scope.myModel122 = 1;
  		$scope.myModel2 = 1;
  		$scope.$digest();

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('1');

  		// another one
  		$scope.myModel1 = 3;
  		$scope.myModel111 = 1;
  		$scope.myModel112 = 1;
  		$scope.myModel121 = 1;
  		$scope.myModel122 = 1;
  		$scope.myModel2 = 4;
  		$scope.$digest();

  		// here the tabIndex starts with 2 and skips a few indexes because of optimisations (old 'undefined', '1', '2' and '1' design tab seq. get removed from parent one by one and replaced by '3', '4', '1' and '1')
  		// and when '3' gets readded it starts counting from where old '1' left off
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('4');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('4');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('156');
  		// DONT SPLIT CODE --------------- END --------------------------
  		
  		
  		
  	});
}); 
