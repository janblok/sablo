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
        sessionStorage.removeItem('svy_session_lock'); // workaround for some new code in websocket.ts
        
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
				getPushToServerCalculatedValue: function() { return pushToServerUtils.reject; }, // property context says "reject" push to server on whole array
				isInsideModel: true
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
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; }, // property context says "reject" push to server on whole array
                isInsideModel: true
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
			   				getPushToServerCalculatedValue: function() { return pushToServerUtils.shallow; }, // property context says "shallow" push to server on whole array
                            isInsideModel: true
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
		
        it( 'send array as arg to handler, change an el. by ref', () => {
            var propertyContextForArg = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; },
                isInsideModel: false
            };

            // simulate a send to server as argument to a handler for this array (oldVal undefined) - to make sure it doesn't messup it's state if it's also a model prop. (it used getParentPropertyContext above which is for a model prop)
            const changes = sabloConverters.convertFromClientToServer(realClientValue, arrayType, undefined,
                $scope, propertyContextForArg);
            $scope.$digest();
            
            expect( changes.vEr ).toBe( 0 );
            expect( changes.v ).toBeDefined();
            expect( changes.v.length ).toBe( 4 );
            expect( changes.v[0] ).toBe( 1 );
            expect( changes.v[1] ).toBe( 2 );
            expect( changes.v[2] ).toBe( 3 );
            expect( changes.v[3] ).toBe( 4 );
    
            realClientValue[2] = 55;
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
    
            const changes2 = sabloConverters.convertFromClientToServer(realClientValue, arrayType, realClientValue, $scope, propertyContext);
            expect( changes2.vEr ).toBe( 1 );
            expect( changes2.u.length ).toBe( 1 );
            expect( changes2.u[0].i ).toBe( '2' );
            expect( changes2.u[0].v ).toBe( 55 );
        } );
    
        it( 'change array el. by ref but do not send to server (so it still has changes to send for the model property), then send array as arg to handler, change another tab by ref; both tabs changed by ref in the model should be then sent to server', () => {
            var propertyContextForArg = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; },
                isInsideModel: false
            };

            realClientValue[1] = 44;
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(true);
    
            // simulate a send to server as argument to a handler for this array (oldVal undefined) - to make sure it doesn't messup it's state if it's also a model prop. (it used getParentPropertyContext above which is for a model prop)
            const changes = sabloConverters.convertFromClientToServer(realClientValue, arrayType, undefined,
                $scope, propertyContextForArg);
            $scope.$digest();
            
            expect( changes.vEr ).toBe( 0 );
            expect( changes.v ).toBeDefined();
            expect( changes.v.length ).toBe( 4 );
            expect( changes.v[0] ).toBe( 1 );
            expect( changes.v[1] ).toBe( 44 );
            expect( changes.v[2] ).toBe( 3 );
            expect( changes.v[3] ).toBe( 4 );
    
            realClientValue[2] = 55;
            $scope.$digest();
    
            expect(getAndClearNotified()).toEqual(true);
    
            const changes2 = sabloConverters.convertFromClientToServer(realClientValue, arrayType, realClientValue, $scope, propertyContext);
            expect( changes2.vEr ).toBe( 1 );
            expect( changes2.u.length ).toBe( 2 );
            expect( changes2.u[1].i ).toBe( '2' );
            expect( changes2.u[1].v ).toBe( 55 );
            expect( changes2.u[0].i ).toBe( '1' );
            expect( changes2.u[0].v ).toBe( 44 );
        } );

        it( 'send arr from model (with push to server reject) as arg to handler', () => {
            const propertyContextForArg = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; },
                isInsideModel: false
            };
            const propertyContextForModel = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.reject; },
                isInsideModel: true
            };
    
            const val = sabloConverters.convertFromServerToClient({ v: ['test'], vEr: 1 },
                   arrayType , undefined, undefined, undefined, $scope, propertyContextForModel);
    
            val[iS].setChangeNotifier(function() { changeNotified = true });
    
            // simulate a send to server as argument to a handler, which should work even though it is a model value with push to server reject
            const changes = sabloConverters.convertFromClientToServer(val, arrayType, undefined, $scope,
                propertyContextForArg);
            
            expect(changes.vEr).toBe(0);
            expect(changes.v).toBeDefined();
            expect(changes.v[0]).toBe('test');
    
            val[0] = 'test4';
            $scope.$digest();
    
            expect(getAndClearNotified()).toBe(false);
            
            // inside the model it is push to server reject
            const changes2 = sabloConverters.convertFromClientToServer(val, arrayType, val, $scope,
                propertyContextForModel);
            expect( changes2.n ).toBeTrue();
        } );

		// a test that sets a whole array value client side and checks that watches and stuff is operational is added in customJSONObjecttype.test.js
	});

});
