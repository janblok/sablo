
describe('component property tests', function() {
	//jasmine.DEFAULT_TIMEOUT_INTERVAL = 1000;
	var scope;
	var pushToServerUtils;
	var sabloConverters;
	var compile;
    var sabloApplication;
	var pushToServerUtils;
	var propertyWatchUtils;
	var changed = false;
	var newV;
	var oldV;
	var beanDynamicTypesHolder = {};
	var watches;
	
    function getAndClearNotified() { changedC = changed; changed = false; return changedC; }
    function getAndClearOldValue() { oldVC = oldV; oldV = undefined; return oldVC; }
    function getAndClearNewValue() { newVC = newV; newV = undefined; return newVC; }

	beforeEach(function() {
        module('webSocketModule');
        module('sabloApp');

		inject(function(_$sabloConverters_, _$compile_, _$rootScope_, _$typesRegistry_, _$pushToServerUtils_,  _$sabloApplication_, _$propertyWatchUtils_) {
            var angularEquality = function(first, second) {
                return angular.equals(first, second);
            };

            jasmine.addCustomEqualityTester(angularEquality);
            // The injector unwraps the underscores (_) from around the parameter
            // names when matching
            sabloApplication = _$sabloApplication_;
            sabloConverters = _$sabloConverters_;
            pushToServerUtils = _$pushToServerUtils_;
            propertyWatchUtils = _$propertyWatchUtils_;
            typesRegistry = _$typesRegistry_;
            compile = _$compile_;

            scope = _$rootScope_.$new();
		})
	});
    describe("push to server on root properties", function() {
        beforeEach(function() {
            typesRegistry.addComponentClientSideSpecs({
                comp1: {
                    p: {
                        objectPropDeepWatched: { s: 3 },
                        objectPropDeepWatchedArrayVal: { s: 3 },
                        intShallowWatchedNoInitialVal: { s: 2 },
                        intShallowWatched: { s: 2 }
                    }
                }
            });
            
            serverValueForModel = {
                "objectPropDeepWatched": { ala: "bala", portocala: true },
                "objectPropDeepWatchedArrayVal": [{ ala: "bala", portocala: true }, 7, false ],
                intShallowWatched: 3
            };

            scope.model = {};

            sabloApplication.applyBeanData(scope.model,
                  serverValueForModel,
                  undefined, // container/form size not used in sablo
                  function() { changed = true; }, 
                  "comp1",
                  beanDynamicTypesHolder,
                  scope);
                  
            watches = propertyWatchUtils.watchDumbPropertiesForComponent(scope, 'comp1', scope.model,
                        function (newvalue, oldvalue, propertyName) {
                            if (oldvalue === newvalue) return;
                            changed = true;
                            oldV = oldvalue;
                            newV = newvalue;
                        }
            );

            scope.$digest();
        });
        
        afterEach(function () {
            watches.forEach(function(v) { v(); });
        });

        it("push to server shallow on root prop which gets no value from server initially and on prop that gets a value from server initially", function() {
            expect(getAndClearNotified()).toEqual(false);
            
            scope.model.intShallowWatchedNoInitialVal = 1001;
            scope.$digest();
            expect(getAndClearNotified()).toBe(true);
            expect(getAndClearNewValue()).toBe(1001);
            expect(getAndClearOldValue()).toBe(undefined);

            scope.model.intShallowWatched = 777;
            scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(getAndClearNewValue()).toBe(777);
            expect(getAndClearOldValue()).toBe(3);

            scope.model.objectPropDeepWatchedArrayVal[2] = true;
            scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(getAndClearNewValue()).toEqual([{ ala: "bala", portocala: true }, 7, true ]);
            expect(getAndClearOldValue()).toEqual([{ ala: "bala", portocala: true }, 7, false ]);

            scope.model.objectPropDeepWatched.portocala = false;
            scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(getAndClearNewValue()).toEqual({ ala: "bala", portocala: false });
            expect(getAndClearOldValue()).toEqual({ ala: "bala", portocala: true });

            expect(getAndClearNotified()).toEqual(false);
        });

    });

}); 
