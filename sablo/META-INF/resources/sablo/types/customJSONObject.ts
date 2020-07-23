/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('custom_json_object_property', ['webSocketModule', 'typesRegistryModule'])
// CustomJSONObject type ------------------------------------------
.run(function ($sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $typesRegistry: sablo.ITypesRegistryForTypeFactories, $log: sablo.ILogService) {
	const TYPE_FACTORY_NAME = 'JSON_obj';
	$typesRegistry.getTypeFactoryRegistry().contributeTypeFactory(TYPE_FACTORY_NAME, new sablo.propertyTypes.CustomObjectTypeFactory($typesRegistry, $log, $sabloConverters, $sabloUtils));
});


namespace sablo.propertyTypes {

	export class CustomObjectTypeFactory implements sablo.ITypeFactory<CustomObjectValue> {
		
		private customTypesBySpecName: {
			[webObjectSpecName: string]: {
				[customTypeName: string]: CustomObjectType
			}
		} = {};
		
		constructor(private readonly typesRegistry: sablo.ITypesRegistryForTypeFactories,
				private readonly logger: sablo.ILogService,
				private readonly sabloConverters: sablo.ISabloConverters,
				private readonly sabloUtils: sablo.ISabloUtils) {}
		
		getOrCreateSpecificType(specificTypeInfo: string, webObjectSpecName: string): CustomObjectType {
			const customTypesForThisSpec = this.customTypesBySpecName[webObjectSpecName];
			if (customTypesForThisSpec) {
				const coType = customTypesForThisSpec[webObjectSpecName];
				if (!coType) this.logger.error("[CustomObjectTypeFactory] cannot find custom object client side type '" + specificTypeInfo + "' for spec '" + webObjectSpecName + "'; no such custom object type was registered for that spec.; ignoring...");
				return coType;
			} else {
				this.logger.error("[CustomObjectTypeFactory] cannot find custom object client side type '" + specificTypeInfo + "' for spec '" + webObjectSpecName + "'; that spec. didn't register any client side types; ignoring...");
				return undefined;
			}
		}
		
		registerDetails(details: sablo.typesRegistry.ICustomTypesFromServer, webObjectSpecName: string) {
			// ok we got the custom types section of a .spec file in details; do it similarly to what we do server-side:
			//   - first create empty shells for all custom types (because they might need to reference each other)
			//   - go through each custom type's sub-properties and assign the correct type to them (could be one of the previously created "empty shells")
			
			this.customTypesBySpecName[webObjectSpecName] = {};
			const customTypesForThisSpec = this.customTypesBySpecName[webObjectSpecName];
			
			// create empty CustomObjectType instances for all custom types in this .spec
			for (const customTypeName in details) {
				customTypesForThisSpec[customTypeName] = new CustomObjectType(this.sabloConverters, this.sabloUtils); // create just an empty type reference that will be populated below with child property types
			}

			// set the sub-properties of each CustomObjectType to the correct IType
			for (const customTypeName in details) {
				const customTypeDetails = details[customTypeName];
				const properties: { [propName: string]: sablo.IType<any> } = {};
				for (const propertyName in customTypeDetails) {
					properties[propertyName] = this.typesRegistry.processTypeFromServer(customTypeDetails[propertyName], webObjectSpecName);
				}
				customTypesForThisSpec[customTypeName].setPropertyTypes(properties);
			}
		}
	
	}
	
	class CustomObjectType implements sablo.IType<CustomObjectValue> {
		
    	static readonly UPDATES = "u";
    	static readonly KEY = "k";
    	static readonly INITIALIZE = "in";
    	static readonly VALUE = "v";
    	static readonly PUSH_TO_SERVER = "w"; // value is undefined when we shouldn't send changes to server, false if it should be shallow watched and true if it should be deep watched
    	static readonly CONTENT_VERSION = "vEr"; // server side sync to make sure we don't end up granular updating something that has changed meanwhile server-side
    	static readonly NO_OP = "n";
    	static readonly angularAutoAddedKeys = ["$$hashKey"];

    	private propertyTypes: { [propName: string]: sablo.IType<any> };

		constructor(private readonly sabloConverters: sablo.ISabloConverters, private readonly sabloUtils: sablo.ISabloUtils) {}

		// this will always get called once with a non-null param before the CustomObjectType is used for conversions
		setPropertyTypes(propertyTypes: { [propName: string]: sablo.IType<any> }): void {
			this.propertyTypes = propertyTypes;
		};
		
        fromServerToClient(serverJSONValue: any, currentClientValue: CustomObjectValue, componentScope: angular.IScope, propertyContext: sablo.IPropertyContext): CustomObjectValue {
			let newValue = currentClientValue;

			// remove old watches and, at the end create new ones to avoid old watches getting triggered by server side change
			this.removeAllWatches(currentClientValue);
			
			try
			{
				if (serverJSONValue && serverJSONValue[CustomObjectType.VALUE]) {
					// full contents
					newValue = serverJSONValue[CustomObjectType.VALUE];
					this.initializeNewValue(newValue, serverJSONValue[CustomObjectType.CONTENT_VERSION]);
					const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
					if (typeof serverJSONValue[CustomObjectType.PUSH_TO_SERVER] !== 'undefined') internalState[CustomObjectType.PUSH_TO_SERVER] = serverJSONValue[CustomObjectType.PUSH_TO_SERVER];
						
					const customObjectPropertyContext = this.getCustomObjectPropertyContext(newValue, propertyContext);
					for (const c in newValue) {
						let elem = newValue[c];
						
						// if it is a typed prop. use the type to convert it, if it is a dynamic type prop (for example dataprovider of type date) do the same
						newValue[c] = elem = this.sabloConverters.convertFromServerToClient(elem, propertyTypes[c], currentClientValue ? currentClientValue[c] : undefined, componentScope, customObjectPropertyContext);

						if (elem && elem[this.sabloConverters.INTERNAL_IMPL] && elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
							// child is able to handle it's own change mechanism
							elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newValue, c));
						}
					}
				} else if (serverJSONValue && serverJSONValue[CustomObjectType.UPDATES]) {
					// granular updates received;
					
					if (serverJSONValue[CustomObjectType.INITIALIZE]) this.initializeNewValue(currentClientValue, serverJSONValue[CustomObjectType.CONTENT_VERSION]); // this can happen when an object value was set completely in browser and the child values need to instrument their browser values as well in which case the server sends 'initialize' updates for both this array and 'smart' child values
					
					const internalState = currentClientValue[this.sabloConverters.INTERNAL_IMPL];

					// if something changed browser-side, increasing the content version thus not matching next expected version,
					// we ignore this update and expect a fresh full copy of the object from the server (currently server value is leading/has priority because not all server side values might support being recreated from client values)
					if (internalState[CustomObjectType.CONTENT_VERSION] == serverJSONValue[CustomObjectType.CONTENT_VERSION]) {
						const updates = serverJSONValue[CustomObjectType.UPDATES];
						let i;
						const customObjectPropertyContext = this.getCustomObjectPropertyContext(currentClientValue, propertyContext);

						for (i in updates) {
							const update = updates[i];
							const key = update[CustomObjectType.KEY];
							let val = update[CustomObjectType.VALUE];

							currentClientValue[key] = val = this.sabloConverters.convertFromServerToClient(val, propertyTypes[key], currentClientValue[key], componentScope, customObjectPropertyContext);

							if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
								// child is able to handle it's own change mechanism
								val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(currentClientValue, key));
							}
						}
					}
					//else {
					  // else we got an update from server for a version that was already bumped by changes in browser; ignore that, as browser changes were sent to server
					  // and server will detect the problem and send back a full update
					//}
				} else if (serverJSONValue && serverJSONValue[CustomObjectType.INITIALIZE]) {
					// TODO now that client side has type information this round trip to server is no longer necessary (client to server will get called correctly and it can be done there); once it's done there this code can be removed
					// only content version update - this happens when a full object value is set on this property client side; it goes to server
					// and then server sends back the version and we initialize / prepare the existing newValue for being watched/handle child conversions
					this.initializeNewValue(currentClientValue, serverJSONValue[CustomObjectType.CONTENT_VERSION]); // here we can count on not having any 'smart' values cause if we had
					// updates would have been received with this initialize as well (to initialize child elements as well to have the setChangeNotifier and internal things)
				} else if (!serverJSONValue || !serverJSONValue[CustomObjectType.NO_OP]) newValue = null; // anything else would not be supported...	// TODO how to handle null values (special watches/complete object set from client)? if null is on server and something is set on client or the other way around?
			} finally {
				// add back watches if needed
				this.addBackWatches(newValue, componentScope);
			}

			return newValue;        	
        }
        
        fromClientToServer(newClientData: any, oldClientData: CustomObjectValue): any {
			// TODO how to handle null values (special watches/complete object set from client)? if null is on server and something is set on client or the other way around?

        	let internalState;
			if (newClientData && (internalState = newClientData[this.sabloConverters.INTERNAL_IMPL])) {
				if (internalState.isChanged()) {
					const changes = {};
					changes[CustomObjectType.CONTENT_VERSION] = internalState[CustomObjectType.CONTENT_VERSION];
					if (internalState.allChanged) {
						// structure might have changed; increase version number
						++internalState[CustomObjectType.CONTENT_VERSION]; // we also increase the content version number - server should only be expecting updates for the next version number
						// send all
						const toBeSentObj = changes[CustomObjectType.VALUE] = {};
						for (const key in newClientData) {
							if (CustomObjectType.angularAutoAddedKeys.indexOf(key) !== -1) continue;

							const val = newClientData[key];
							toBeSentObj[key] = this.sabloConverters.convertFromClientToServer(val, propertyTypes[key], oldClientData ? oldClientData[key] : undefined);
						}
						internalState.allChanged = false;
						internalState.changedKeys = {};
						return changes;
					} else {
						// send only changed keys
						const changedElements = changes[CustomObjectType.UPDATES] = [];
						for (const key in internalState.changedKeys) {
							const newVal = newClientData[key];
							const oldVal = oldClientData ? oldClientData[key] : undefined;

							let changed = (newVal !== oldVal);
							if (!changed) {
								if (internalState.elUnwatch[key]) {
									const oldDumbVal = internalState.changedKeys[key].old;
									// it's a dumb value - watched; see if it really changed acording to sablo rules
									if (oldDumbVal !== newVal) {
										if (typeof newVal == "object") {
											if (this.sabloUtils.isChanged(newVal, oldDumbVal, propertyTypes[key])) {
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
								ch[CustomObjectType.KEY] = key;
								ch[CustomObjectType.VALUE] = this.sabloConverters.convertFromClientToServer(newVal, propertyTypes[key], oldVal);
								changedElements.push(ch);
							}
						}
						internalState.allChanged = false;
						internalState.changedKeys = {};
						return changes;
					}
				} else if (angular.equals(newClientData, oldClientData)) { // can't use === because oldClientData is an angular clone not the same ref.
					const x = {}; // no changes
					x[CustomObjectType.NO_OP] = true;
					return x;
				}
			}
			
			if (internalState) delete newClientData[this.sabloConverters.INTERNAL_IMPL]; // some other new value was set; it's internal state is useless and will be re-initialized from server
				
			return newClientData;
		}
        
        updateAngularScope(clientValue: CustomObjectValue, componentScope: angular.IScope): void {
			this.removeAllWatches(clientValue);
			if (componentScope) this.addBackWatches(clientValue, componentScope);
			
			if (clientValue) {
				const internalState = clientValue[this.sabloConverters.INTERNAL_IMPL];
				if (internalState) {
					for (const key in clientValue) {
						if (CustomObjectType.angularAutoAddedKeys.indexOf(key) !== -1) continue;
						if (propertyTypes[key]) propertyTypes[key].updateAngularScope(clientValue[key], componentScope);
					}
				}
			}
        }

        // ------------------------------------------------------------------------------

    	private getChangeNotifier(propertyValue: any, key: string): () => void {
    		return function() {
    			const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
    			internalState.changedKeys[key] = true;
    			internalState.notifier();
    		}
    	}
    	
    	private watchDumbElementForChanges(propertyValue: CustomObjectValue, key: string, componentScope: angular.IScope, deep: boolean): () => void  {
    		// if elements are primitives or anyway not something that wants control over changes, just add an in-depth watch
    		return componentScope.$watch(function() {
    			return propertyValue[key];
    		}, function(newvalue, oldvalue) {
    			if (oldvalue === newvalue) return;
    			const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
    			internalState.changedKeys[key] = { old: oldvalue };
    			internalState.notifier();
    		}, deep);
    	}

    	/** Initializes internal state on a new object value */
    	private initializeNewValue(newValue: any, contentVersion: number): void {
    		let newInternalState = false; // TODO although unexpected (internal state to already be defined at this stage it can happen until SVY-8612 is implemented and property types change to use that
    		if (!newValue.hasOwnProperty(this.sabloConverters.INTERNAL_IMPL)) {
    			newInternalState = true;
    			this.sabloConverters.prepareInternalState(newValue);
    		} // else: we don't try to redefine internal state if it's already defined

    		const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
    		internalState[CustomObjectType.CONTENT_VERSION] = contentVersion; // being full content updates, we don't care about the version, we just accept it

    		if (newInternalState) {
    			// implement what $sabloConverters need to make this work
    			internalState.setChangeNotifier = function(changeNotifier: () => void) {
    				internalState.notifier = changeNotifier; 
    			}
    			internalState.isChanged = function() {
    				let hasChanges = internalState.allChanged;
    				if (!hasChanges) for (let x in internalState.changedKeys) { hasChanges = true; break; }
    				return hasChanges;
    			}

    			// private impl
    			internalState.modelUnwatch = [];
    			internalState.objStructureUnwatch = null;
    			internalState.changedKeys = {};
    			internalState.allChanged = false;
    		} // else don't reinitilize it - it's already initialized
    	}
    	
    	private removeAllWatches(value: CustomObjectValue): void {
    		if (value != null && angular.isDefined(value)) {
    			const iS = value[this.sabloConverters.INTERNAL_IMPL];
    			if (iS != null && angular.isDefined(iS)) {
    				if (iS.objStructureUnwatch) iS.objStructureUnwatch();
    				for (const key in iS.elUnwatch) {
    					iS.elUnwatch[key]();
    				}
    				iS.objStructureUnwatch = null;
    				iS.elUnwatch = null;
    			}
    		}
    	}

    	private addBackWatches(value: CustomObjectValue, componentScope: angular.IScope): void {
    		if (value) {
    			const internalState = value[this.sabloConverters.INTERNAL_IMPL];
    			internalState.elUnwatch = {};

    			// add shallow/deep watches as needed
    			if (componentScope && typeof internalState[CustomObjectType.PUSH_TO_SERVER] != 'undefined') {
    				
    				for (const c in value) {
    					const elem = value[c];
    					if (!elem || !elem[this.sabloConverters.INTERNAL_IMPL] || !elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
    						// watch the child's value to see if it changes
    						if (CustomObjectType.angularAutoAddedKeys.indexOf(c) === -1) internalState.elUnwatch[c] = this.watchDumbElementForChanges(value, c, componentScope, internalState[CustomObjectType.PUSH_TO_SERVER]);
    					}
    				}

    				// watch for add/remove and such operations on object; this is helpful also when 'smart' child values (that have .setChangeNotifier)
    				// get changed completely by reference
    				internalState.objStructureUnwatch = componentScope.$watchCollection(function() { return value; }, function(newWVal, oldWVal) {
    					if (newWVal === oldWVal) return;
    					
    					if (newWVal === null || oldWVal === null) {
    						// send new value entirely
    						internalState.allChanged = true;
    						internalState.notifier();
    					} else {
    						// search for differences between properties of the old and new objects
    						let changed = false;

    						const tmp = [];
    						let idx;
    						for (const key in newWVal) if (CustomObjectType.angularAutoAddedKeys.indexOf(key) === -1) tmp.push(key);
    						for (const key in oldWVal) {
    							if (CustomObjectType.angularAutoAddedKeys.indexOf(key) !== -1) continue;

    							if ((idx = tmp.indexOf(key)) != -1) {
    								tmp.splice(idx, 1); // this will be dealt with here; remove it from tmp
    								
    								// key in both old and new; check for difference in value
    								if (newWVal[key] !== oldWVal[key] && oldWVal[key] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
    									// some elements changed by reference; we only need to handle this for old smart element values,
    									// as the others will be handled by the separate 'dumb' watches
    									changed = true;
    									internalState.changedKeys[key] = { old: oldWVal[key] }; // just in case new value is not smart; otherwise we could just put true there for example - that is enough for smart values

    									// if new value is smart as well we have to give it the according change notifier
    									if (newWVal[key] && newWVal[key][this.sabloConverters.INTERNAL_IMPL] && newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
    										newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newWVal, key));
    								}
    							} else {
    								// old has a key that is no longer present in new one; for 'dumb' properties this will be handled by already added dumb watches;
    								// so here we need to see if old value was smart, then we need to send updates to the server
    								if (oldWVal[key] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL] && oldWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
    									changed = true;
    									internalState.changedKeys[key] = { old: oldWVal[key] };
    								}
    							}
    						}
    						// any keys left in tmp are keys that are in new value but are not in old value; handle those
    						for (idx in tmp) {
    							const key = tmp[idx];
    							// if a dumb watch is already present for this key let that watch handle the change (could happen for example if a property is initially set with a 'dumb' value then cleared then set again)
    							if (!internalState.elUnwatch[key]) {
    								// so we were not previously aware of this new key; send it to server
    								changed = true;
    								internalState.changedKeys[key] = { old: undefined };
    								
    								// if new value is smart we have to give it the according change notifier; if it's 'dumb' and it was not watched before add a 'dumb' watch on it
    								if (newWVal[key] && newWVal[key][this.sabloConverters.INTERNAL_IMPL] && newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
    									newWVal[key][this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newWVal, key));
    								else internalState.elUnwatch[key] = this.watchDumbElementForChanges(newWVal, key, componentScope, internalState[CustomObjectType.PUSH_TO_SERVER]);
    							} // TODO do we need to handle unlikely situation where the value for a key would switch from dumb in the past to smart?
    						}
    						if (changed) internalState.notifier();
    					}
    				});
    			}
    		}
    	}
    	
    	private getCustomObjectPropertyContext(customObjectValue, parentPropertyContext): sablo.IPropertyContext {
    		// property context that we pass here should search first in the current custom object value and only fallback to "parentPropertyContext" if needed
    		return function (propertyName) {
    			if (customObjectValue.hasOwnProperty(propertyName)) return customObjectValue[propertyName]; // can even be null or undefined as long as it is set on this object
    			else return parentPropertyContext ? parentPropertyContext(propertyName) : undefined; // fall back to parent object nesting context
    		}
    	}
    	
    	// ------------------------------------------------------
	}
	
	interface CustomObjectValue {
		
	}

}