/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('custom_json_array_property', ['webSocketModule', 'typesRegistryModule'])
//CustomJSONArray type ------------------------------------------
.run(function ($sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $typesRegistry: sablo.ITypesRegistryForTypeFactories, $log: sablo.ILogService) {
	const TYPE_FACTORY_NAME = 'JSON_arr';
	$typesRegistry.getTypeFactoryRegistry().contributeTypeFactory(TYPE_FACTORY_NAME, new sablo.propertyTypes.CustomArrayTypeFactory($typesRegistry, $log, $sabloConverters, $sabloUtils));
});


namespace sablo.propertyTypes {

	export class CustomArrayTypeFactory implements sablo.ITypeFactory<CustomArrayValue> {
		
		private customArrayTypesByElementType: Map<sablo.IType<any>, CustomArrayType> = new Map();
		
		constructor(private readonly typesRegistry: sablo.ITypesRegistryForTypeFactories,
				private readonly logger: sablo.ILogService,
				private readonly sabloConverters: sablo.ISabloConverters,
				private readonly sabloUtils: sablo.ISabloUtils) {}
		
		getOrCreateSpecificType(specificTypeInfo: string, webObjectSpecName: string): CustomArrayType {
			let elementType = this.typesRegistry.processTypeFromServer(specificTypeInfo, webObjectSpecName);
			if (elementType) {
				let cachedArrayType = this.customArrayTypesByElementType.get(elementType);
				if (!cachedArrayType) {
					cachedArrayType = new CustomArrayType(elementType, this.sabloConverters, this.sabloUtils);
					this.customArrayTypesByElementType.set(elementType, cachedArrayType);
				}
				return cachedArrayType;
			} else this.logger.error("[CustomArrayTypeFactory] cannot find array element's client side type '" + specificTypeInfo + "' for spec '" + webObjectSpecName + "'; no such type was registered; ignoring...");
			
			return undefined;
		}
		
		registerDetails(details: sablo.typesRegistry.ICustomTypesFromServer, webObjectSpecName: string) {
			// arrays don't need to pre-register stuff
		}
	
	}
	
	class CustomArrayType implements sablo.IType<CustomArrayValue> {
		static readonly UPDATES = "u";
		static readonly REMOVES = "r";
		static readonly ADDITIONS = "a";
		static readonly INDEX = "i";
		static readonly INITIALIZE = "in";
		static readonly VALUE = "v";
		static readonly PUSH_TO_SERVER = "w"; // value is undefined when we shouldn't send changes to server, false if it should be shallow watched and true if it should be deep watched
		static readonly CONTENT_VERSION = "vEr"; // server side sync to make sure we don't end up granular updating something that has changed meanwhile server-side
		static readonly NO_OP = "n";
		
		constructor(private elementType: sablo.IType<any>, private readonly sabloConverters: sablo.ISabloConverters, private readonly sabloUtils: sablo.ISabloUtils) {}
		
		fromServerToClient(serverJSONValue: any, currentClientValue: CustomArrayValue, componentScope: angular.IScope, propertyContext: sablo.IPropertyContext): CustomArrayValue {
			let newValue = currentClientValue;

			// remove old watches (and, at the end create new ones) to avoid old watches getting triggered by server side change
			this.removeAllWatches(currentClientValue);

			try
			{
				if (serverJSONValue && serverJSONValue[CustomArrayType.VALUE]) {
					// full contents
					newValue = serverJSONValue[CustomArrayType.VALUE];
					this.initializeNewValue(newValue, serverJSONValue[CustomArrayType.CONTENT_VERSION]);
					const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
					if (typeof serverJSONValue[CustomArrayType.PUSH_TO_SERVER] !== 'undefined') internalState[CustomArrayType.PUSH_TO_SERVER] = serverJSONValue[CustomArrayType.PUSH_TO_SERVER];

					if (newValue.length)
					{
						for (let c = 0; c < newValue.length; c++) {
							let elem = newValue[c];
	
							newValue[c] = elem = this.sabloConverters.convertFromServerToClient(elem, this.elementType, currentClientValue ? currentClientValue[c] : undefined, componentScope, propertyContext);
	
							if (elem && elem[this.sabloConverters.INTERNAL_IMPL] && elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								// child is able to handle it's own change mechanism
								elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newValue, c));
							}
						}
					}
				} else if (serverJSONValue && (serverJSONValue[CustomArrayType.UPDATES] || serverJSONValue[CustomArrayType.REMOVES] || serverJSONValue[CustomArrayType.ADDITIONS])) {
					// granular updates received;

					if (serverJSONValue[CustomArrayType.INITIALIZE]) this.initializeNewValue(currentClientValue, serverJSONValue[CustomArrayType.CONTENT_VERSION]); // this can happen when an array value was set completely in browser and the child elements need to instrument their browser values as well in which case the server sends 'initialize' updates for both this array and 'smart' child elements

					const internalState = currentClientValue[this.sabloConverters.INTERNAL_IMPL];

					// if something changed browser-side, increasing the content version thus not matching next expected version,
					// we ignore this update and expect a fresh full copy of the array from the server (currently server value is leading/has priority because not all server side values might support being recreated from client values)
					if (internalState[CustomArrayType.CONTENT_VERSION] == serverJSONValue[CustomArrayType.CONTENT_VERSION]) {
						if (serverJSONValue[CustomArrayType.REMOVES])
						{
							const removes = serverJSONValue[CustomArrayType.REMOVES];
							for (const idx in removes)
							{
								currentClientValue.splice(removes[idx], 1 );
							}
						}
						if (serverJSONValue[CustomArrayType.ADDITIONS])
						{
							const additions = serverJSONValue[CustomArrayType.ADDITIONS];
							for (const i in additions) {
								const element = additions[i];
								const idx = element[CustomArrayType.INDEX];
								let val = element[CustomArrayType.VALUE];

								val = this.sabloConverters.convertFromServerToClient(val, this.elementType, currentClientValue[idx], componentScope, propertyContext);
								currentClientValue.splice(idx, 0, val);

								if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
									val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, idx));
								}
							}
						}
						if (serverJSONValue[CustomArrayType.UPDATES])
						{
							const updates = serverJSONValue[CustomArrayType.UPDATES];
							for (const i in updates) {
								const update = updates[i];
								const idx = update[CustomArrayType.INDEX];
								let val = update[CustomArrayType.VALUE];

								currentClientValue[idx] = val = this.sabloConverters.convertFromServerToClient(val, this.elementType, currentClientValue[idx], componentScope, propertyContext);

								if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
									// child is able to handle it's own change mechanism
									val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, idx));
								}
							}
						}
					}
					//else {
					// else we got an update from server for a version that was already bumped by changes in browser; ignore that, as browser changes were sent to server
					// and server will detect the problem and send back a full update
					//}
				} else if (serverJSONValue && serverJSONValue[CustomArrayType.INITIALIZE]) {
					// TODO now that client side has type information this round trip to server is no longer necessary (client to server will get called correctly and it can be done there); once it's done there this code can be removed
					// only content version update - this happens when a full array value is set on this property client side; it goes to server
					// and then server sends back the version and we initialize / prepare the existing newValue for being watched/handle child conversions
					this.initializeNewValue(currentClientValue, serverJSONValue[CustomArrayType.CONTENT_VERSION]); // here we can count on not having any 'smart' values cause if we had
					// updates would have been received with this initialize as well (to initialize child elements as well to have the setChangeNotifier and internal things)
				} else if (!serverJSONValue || !serverJSONValue[CustomArrayType.NO_OP]) newValue = null; // anything else would not be supported...	// TODO how to handle null values (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?
			} finally {
				// add back watches if needed
				this.addBackWatches(newValue, componentScope);
			}

			return newValue;
		}

		fromClientToServer(newClientData: any, oldClientData: CustomArrayValue): any {
			// TODO how to handle null values (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?

			let internalState;
			if (newClientData && (internalState = newClientData[this.sabloConverters.INTERNAL_IMPL])) {
				if (internalState.isChanged()) {
					const changes = {};
					changes[CustomArrayType.CONTENT_VERSION] = internalState[CustomArrayType.CONTENT_VERSION];
					if (internalState.allChanged) {
						// structure might have changed; increase version number
						++internalState[CustomArrayType.CONTENT_VERSION]; // we also increase the content version number - server should only be expecting updates for the next version number
						// send all
						const toBeSentArray = changes[CustomArrayType.VALUE] = [];
						for (let idx = 0; idx < newClientData.length; idx++) {
							const val = newClientData[idx];
							toBeSentArray[idx] = this.sabloConverters.convertFromClientToServer(val, this.elementType, oldClientData ? oldClientData[idx] : undefined);
						}
						internalState.allChanged = false;
						internalState.changedIndexes = {};
						return changes;
					} else {
						// send only changed indexes
						const changedElements = changes[CustomArrayType.UPDATES] = [];
						for (const idx in internalState.changedIndexes) {
							const newVal = newClientData[idx];
							const oldVal = oldClientData ? oldClientData[idx] : undefined;

							let changed = (newVal !== oldVal);
							if (!changed) {
								if (internalState.elUnwatch[idx]) {
									const oldDumbVal = internalState.changedIndexes[idx].old;
									// it's a dumb value - watched; see if it really changed according to sablo rules
									if (oldDumbVal !== newVal) {
										if (typeof newVal == "object") {
											if (this.sabloUtils.isChanged(newVal, oldDumbVal, internalState.conversionInfo[idx])) {
												changed = true;
											}
										} else {
											changed = true;
										}
									}
								} else changed = newVal && newVal[this.sabloConverters.INTERNAL_IMPL].isChanged(); // must be smart value then; same reference as checked above; so ask it if it changed
							}

							if (changed) {
								const ch = {};
								ch[CustomArrayType.INDEX] = idx;
								ch[CustomArrayType.VALUE] = this.sabloConverters.convertFromClientToServer(newVal, this.elementType, oldVal);
								changedElements.push(ch);
							}
						}
						internalState.allChanged = false;
						internalState.changedIndexes = {};
						return changes;
					}
				} else if (angular.equals(newClientData, oldClientData)) {
					const x = {}; // no changes
					x[CustomArrayType.NO_OP] = true;
					return x;
				}
			}

			if (internalState) delete newClientData[this.sabloConverters.INTERNAL_IMPL]; // some other new value was set; it's internal state is useless and will be re-initialized from server

			return newClientData;
		}
		
		updateAngularScope(clientValue: CustomArrayValue, componentScope: angular.IScope): void {
			this.removeAllWatches(clientValue);
			if (componentScope) this.addBackWatches(clientValue, componentScope);

			if (clientValue) {
				const internalState = clientValue[this.sabloConverters.INTERNAL_IMPL];
				if (internalState) {
					for (let c = 0; c < clientValue.length; c++) {
						const elem = clientValue[c];
						if (this.elementType) this.elementType.updateAngularScope(elem, componentScope);
					}
				}
			}
		}
	
		// ------------------------------------------------------------------------------

		private getChangeNotifier(propertyValue: any, idx: number) {
			return function() {
				const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
				internalState.changedIndexes[idx] = true;
				internalState.changeNotifier();
			}
		}
	
		private watchDumbElementForChanges(propertyValue: CustomArrayValue, idx: number, componentScope: angular.IScope, deep: boolean): () => void  {
			// if elements are primitives or anyway not something that wants control over changes, just add an in-depth watch
			return componentScope.$watch(function() {
				return propertyValue[idx];
			}, function(newvalue, oldvalue) {
				if (oldvalue === newvalue) return;
				const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
				internalState.changedIndexes[idx] = { old: oldvalue };
				internalState.changeNotifier();
			}, deep);
		}
	
		/** Initializes internal state on a new array value */
		private initializeNewValue(newValue: any, contentVersion: number) {
			let newInternalState = false; // TODO although unexpected (internal state to already be defined at this stage it can happen until SVY-8612 is implemented and property types change to use that
			if (!newValue.hasOwnProperty(this.sabloConverters.INTERNAL_IMPL)) {
				newInternalState = true;
				this.sabloConverters.prepareInternalState(newValue); 
			} // else: we don't try to redefine internal state if it's already defined
			
			const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
			internalState[CustomArrayType.CONTENT_VERSION] = contentVersion; // being full content updates, we don't care about the version, we just accept it
	
			if (newInternalState) {
				// implement what $sabloConverters need to make this work
				internalState.setChangeNotifier = function(changeNotifier) {
					internalState.changeNotifier = changeNotifier; 
				}
				internalState.isChanged = function() {
					let hasChanges = internalState.allChanged;
					if (!hasChanges) for (const x in internalState.changedIndexes) { hasChanges = true; break; }
					return hasChanges;
				}
	
				// private impl
				internalState.modelUnwatch = [];
				internalState.arrayStructureUnwatch = null;
				internalState.conversionInfo = [];
				internalState.changedIndexes = {};
				internalState.allChanged = false;
			} // else don't reinitilize it - it's already initialized
		}
	
		private removeAllWatches(value: CustomArrayValue): void {
			if (value != null && angular.isDefined(value)) {
				const iS = value[this.sabloConverters.INTERNAL_IMPL];
				if (iS != null && angular.isDefined(iS)) {
					if (iS.arrayStructureUnwatch) iS.arrayStructureUnwatch();
					for (const key in iS.elUnwatch) {
						iS.elUnwatch[key]();
					}
					iS.arrayStructureUnwatch = null;
					iS.elUnwatch = null;
				}
			}
		}
	
		private addBackWatches(value: CustomArrayValue, componentScope: angular.IScope): void {
			if (value) {
				const internalState = value[this.sabloConverters.INTERNAL_IMPL];
				internalState.elUnwatch = {};
				
				// add shallow/deep watches as needed
				if (componentScope && typeof internalState[CustomArrayType.PUSH_TO_SERVER] != 'undefined') {
					for (let c = 0; c < value.length; c++) {
						const elem = value[c];
						if (!elem || !elem[this.sabloConverters.INTERNAL_IMPL] || !elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
							// watch the child's value to see if it changes
							 internalState.elUnwatch[c] = this.watchDumbElementForChanges(value, c, componentScope, internalState[CustomArrayType.PUSH_TO_SERVER]);
						} // else if it's a smart value and the pushToServer is shallow or deep we must shallow watch it (it will manage it's own contents but we still must watch for reference changes);
						// but that is done below in a $watchCollection
					}
	
					// watch for add/remove and such operations on array; this is helpful also when 'smart' child values (that have .setChangeNotifier)
					// get changed completely by reference
					internalState.arrayStructureUnwatch = componentScope.$watchCollection(function() { return value; }, function(newWVal, oldWVal) {
						if (newWVal === oldWVal) return;
	
						if (newWVal === null || oldWVal === null || newWVal.length !== oldWVal.length) {
							internalState.allChanged = true;
							internalState.changeNotifier();
						} else {
							// some elements changed by reference; we only need to handle this for smart element values,
							// as the others will be handled by the separate 'dumb' watches
							let referencesChanged = false;
							for (const j in newWVal) {
								if (newWVal[j] !== oldWVal[j] && oldWVal[j] && oldWVal[j][this.sabloConverters.INTERNAL_IMPL] && oldWVal[j][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
									referencesChanged = true;
									internalState.changedIndexes[j] = { old: oldWVal[j] };
								}
							}
	
							if (referencesChanged) internalState.changeNotifier();
						}
					});
				}
			}
		}
	}

	interface CustomArrayValue extends Array<any> {
		
	}
}
