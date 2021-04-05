describe("Test array_custom_property suite", function() {

	var sabloConverters;
	var $scope;
	var iS;
	var typesRegistry;
	var pushToServerUtils;

	var propertyContext;

	var serverValue;
	var realClientValue;

	var changeNotified = false;

	function getAndClearNotified() {
		var tm = changeNotified;
		changeNotified = false;
		return tm;
	};

	beforeEach(function() {
        module('sabloApp'); // for Date type
        module('custom_json_array_property');

		inject(function(_$sabloConverters_, _$compile_, _$rootScope_, _$typesRegistry_, _$pushToServerUtils_) {
			var angularEquality = function(first, second) {
				return angular.equals(first, second);
			};

			jasmine.addCustomEqualityTester(angularEquality);
			// The injector unwraps the underscores (_) from around the parameter
			// names when matching
			sabloConverters = _$sabloConverters_;
			typesRegistry = _$typesRegistry_;
			pushToServerUtils = _$pushToServerUtils_;
			iS = sabloConverters.INTERNAL_IMPL;
			$compile = _$compile_;

			$scope = _$rootScope_.$new();
		});

		// mock timout
//		jasmine.clock().install();
	});

	afterEach(function() {
//		jasmine.clock().uninstall();
	});

    // NOTE: some more tests with nested objects/arrays are present in customJSONObjecttype.test.js
	describe("array_custom_property with dumb values suite; pushToServer not set (so reject)", function() {
		var arrayType;
		beforeEach(function() {
			serverValue = {
					"vEr": 1,
					"v": [ 1, 2, 3, 4 ]
			};

			var template = '<div></div>';
			$compile(template)($scope);
			
			arrayType = typesRegistry.processTypeFromServer(["JSON_arr",null], null); // array of types that are not client side types with element config pushToServer not set
			propertyContext = {
			    getProperty: function() {},
				getPushToServerCalculatedValue: function() { return pushToServerUtils.reject; } // property context says "reject" push to server on whole array
			};
			
			realClientValue = sabloConverters.convertFromServerToClient(
					serverValue, arrayType, undefined,
					undefined, undefined,
					$scope, propertyContext);
			realClientValue[iS].setChangeNotifier(function () { changeNotified = true });
			$scope.$digest();
		});


		it("Should not send value updates for when pushToServer is not specified", function() {
			realClientValue[2] = 100; 
			$scope.$digest();

			expect(getAndClearNotified()).toEqual(false);
			expect(realClientValue[iS].isChanged()).toEqual(false);
		});

	});

    describe("custom_array_property with dumb values suite; pushToServer allow on root and shallow on a dynamic typed subprop", function() {
        beforeEach(function() {
            serverValue = {
                    "vEr": 1,
                    "v": [ 1, { _T: "Date", _V: 1141240331661 }, 3, 4 ]
            };

            var template = '<div></div>';
            $compile(template)($scope);
            
            arrayType = typesRegistry.processTypeFromServer(["JSON_arr", { t: null, s: 2 }], null); // array of types that are not client side types with element config pushToServer not set
            propertyContext = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; } // property context says "reject" push to server on whole array
            };
            
            realClientValue = sabloConverters.convertFromServerToClient(
                    serverValue, arrayType, undefined,
                    undefined, undefined,
                    $scope, propertyContext);
            realClientValue[iS].setChangeNotifier(function () { changeNotified = true });
            $scope.$digest();
        });

        it("Should remember dynamic type and convert the value correctly", function() {
            expect(realClientValue[1] instanceof Date).toBe(true);
            expect(realClientValue[iS].dynamicPropertyTypesHolder["1"]).toBe(typesRegistry.getAlreadyRegisteredType("Date"));
        });

        it("Should use remembered dynamic type and convert the value correctly to server", function() {
            realClientValue[1] = new Date();
            var ms = realClientValue[1].getTime();
            $scope.$digest();
            
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(getAndClearNotified()).toEqual(true);
            
            expect(sabloConverters.convertFromClientToServer(realClientValue, arrayType, realClientValue, $scope, propertyContext)).toEqual(
                    { vEr: 1, u: [ { i: '1', v: ms } ] }
            );
            
            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });
        
    });

	describe("array_custom_property with dumb values suite; pushToServer set to shallow", function() {
		beforeEach(function() {
			serverValue = {
					"vEr": 1,
					"v": [ 1, 2, 3, 4 ]
			};

			var template = '<div></div>';
			$compile(template)($scope);
			arrayType = typesRegistry.processTypeFromServer(["JSON_arr",null], null); // array of types that are not client side types with element config pushToServer not set
			propertyContext = {
			   			    getProperty: function() {},
			   				getPushToServerCalculatedValue: function() { return pushToServerUtils.shallow; } // property context says "shallow" push to server on whole array
			   			};

			realClientValue = sabloConverters.convertFromServerToClient(
					serverValue, arrayType, undefined,
					undefined, undefined,
					$scope, propertyContext);
			realClientValue[iS].setChangeNotifier(function () { changeNotified = true });
			$scope.$digest();
		});

		it("Should not send value updates for when pushToServer is not specified", function() {
			realClientValue[2] = 100; 
			$scope.$digest();

			expect(getAndClearNotified()).toEqual(true);
			expect(realClientValue[iS].isChanged()).toEqual(true);
			expect(sabloConverters.convertFromClientToServer(realClientValue, arrayType, realClientValue, $scope, propertyContext)).toEqual(
					{ vEr: 1, u: [ { i: '2', v: 100 } ] }
			);

			expect(getAndClearNotified()).toEqual(false);
			expect(realClientValue[iS].isChanged()).toEqual(false);
		});
		
		// a test that sets a whole array value client side and checks that watches and stuff is operational is added in customJSONObjecttype.test.js
	});

});
