/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('custom_json_array_property', ['webSocketModule', '$typesRegistry'])
//CustomJSONArray type ------------------------------------------
.run(function ($sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $typesRegistry: sablo.ITypesRegistryForTypeFactories, $log: sablo.ILogService) {
    const TYPE_FACTORY_NAME = 'JSON_arr';
    $typesRegistry.getTypeFactoryRegistry().contributeTypeFactory(TYPE_FACTORY_NAME, new sablo.propertyTypes.CustomArrayTypeFactory($typesRegistry, $log, $sabloConverters, $sabloUtils));
});


namespace sablo.propertyTypes {

    export class CustomArrayTypeFactory implements sablo.ITypeFactory<CustomArrayValue> {
        
        private customArrayTypes: Map<sablo.IType<any>, Map<IPushToServerEnum, CustomArrayType>> = new Map(); // allows any keys, even undefined
        
        constructor(private readonly typesRegistry: sablo.ITypesRegistryForTypeFactories,
                private readonly logger: sablo.ILogService,
                private readonly sabloConverters: sablo.ISabloConverters,
                private readonly sabloUtils: sablo.ISabloUtils) {}
        
        getOrCreateSpecificType(specificElementInfo: ITypeFromServer | { t: ITypeFromServer, s: PushToServerEnumValue }, webObjectSpecName: string): CustomArrayType {
            var elementTypeWithNoPushToServer = (specificElementInfo instanceof Array || typeof specificElementInfo == "string") || specificElementInfo === null;
            const elementTypeFromSrv: ITypeFromServer = (elementTypeWithNoPushToServer) ? specificElementInfo as ITypeFromServer : (specificElementInfo as { t: ITypeFromServer, s: PushToServerEnumValue }).t;
            const pushToServer: IPushToServerEnum = (elementTypeWithNoPushToServer) ? undefined : sablo.typesRegistry.PushToServerEnum.valueOf((specificElementInfo as { t: ITypeFromServer, s: PushToServerEnumValue }).s);
            
            let staticElementType = (elementTypeFromSrv ? this.typesRegistry.processTypeFromServer(elementTypeFromSrv, webObjectSpecName) : undefined); // a custom array could have an element type that is not a client side type; but it still needs to be an array type
            let cachedArraysByType = this.customArrayTypes.get(staticElementType);
            if (!cachedArraysByType) {
                cachedArraysByType = new Map();
                this.customArrayTypes.set(staticElementType, cachedArraysByType);
            }
            let cachedArraysByTypeAndPushToServerOnElem = cachedArraysByType.get(pushToServer);
            if (!cachedArraysByTypeAndPushToServerOnElem) {
                cachedArraysByTypeAndPushToServerOnElem = new CustomArrayType(staticElementType, pushToServer, this.sabloConverters, this.sabloUtils);
                cachedArraysByType.set(pushToServer, cachedArraysByTypeAndPushToServerOnElem);
            }
                
            return cachedArraysByTypeAndPushToServerOnElem;
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
        static readonly VALUE = "v";
        static readonly CONTENT_VERSION = "vEr"; // server side sync to make sure we don't end up granular updating something that has changed meanwhile server-side
        static readonly NO_OP = "n";
        
        constructor(private readonly staticElementType: sablo.IType<any>,
                private readonly pushToServerForElements: IPushToServerEnum,
                private readonly sabloConverters: sablo.ISabloConverters, private readonly sabloUtils: sablo.ISabloUtils) {}
        
        fromServerToClient(serverJSONValue: any, currentClientValue: CustomArrayValue, componentScope: angular.IScope, propertyContext: sablo.IPropertyContext): CustomArrayValue {
            let newValue = currentClientValue;

            const elemPropertyContext = propertyContext ? new sablo.typesRegistry.PropertyContext(propertyContext.getProperty,
                    sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(propertyContext.getPushToServerCalculatedValue(), this.pushToServerForElements)) : undefined;
            // remove old watches (and, at the end create new ones) to avoid old watches getting triggered by server side change
            this.removeAllWatches(currentClientValue);

            try
            {
                if (serverJSONValue && serverJSONValue[CustomArrayType.VALUE]) {
                    // full contents
                    newValue = serverJSONValue[CustomArrayType.VALUE];
                    this.initializeNewValue(newValue, serverJSONValue[CustomArrayType.CONTENT_VERSION], propertyContext?.getPushToServerCalculatedValue());
                    const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];

                    if (newValue.length)
                    {
                        for (let c = 0; c < newValue.length; c++) {
                            let elem = newValue[c];
    
                            newValue[c] = elem = this.sabloConverters.convertFromServerToClient(elem, this.staticElementType, currentClientValue ? currentClientValue[c] : undefined,
                                    internalState.dynamicPropertyTypesHolder, "" + c, componentScope, elemPropertyContext);
    
                            if (elem && elem[this.sabloConverters.INTERNAL_IMPL] && elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
                                // child is able to handle it's own change mechanism
                                elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newValue, c));
                            }
                        }
                    }
                } else if (serverJSONValue && (serverJSONValue[CustomArrayType.UPDATES] || serverJSONValue[CustomArrayType.REMOVES] || serverJSONValue[CustomArrayType.ADDITIONS])) {
                    // granular updates received;
                    const internalState = currentClientValue[this.sabloConverters.INTERNAL_IMPL];

                    internalState.calculatedPushToServerOfWholeProp = propertyContext?.getPushToServerCalculatedValue(); // for example if a custom object value is initially received through a return value from server side api/handler call and not as a normal model property and then the component/service assigns it to model, the push to server of it might have changed; use the one received here as arg
                    if (!internalState.calculatedPushToServerOfWholeProp) internalState.calculatedPushToServerOfWholeProp = sablo.typesRegistry.PushToServerEnum.reject;

                    // if something changed browser-side, increasing the content version thus not matching next expected version,
                    // we ignore this update and expect a fresh full copy of the array from the server (currently server value is leading/has priority because not all server side values might support being recreated from client values)
                    if (internalState[CustomArrayType.CONTENT_VERSION] == serverJSONValue[CustomArrayType.CONTENT_VERSION]) {
                        if (serverJSONValue[CustomArrayType.REMOVES])
                        {
                            const removes = serverJSONValue[CustomArrayType.REMOVES];
                            
                            for (const i in removes)
                            {
                                const idx = removes[i];
                                // shift left by 1 all dynamicTypes after to idx as we are going to delete one idx
                                for (let idxToShift = idx + 1; idxToShift < currentClientValue.length; idxToShift++)
                                    if (internalState.dynamicPropertyTypesHolder["" + idxToShift]) {
                                        internalState.dynamicPropertyTypesHolder["" + (idxToShift - 1)] = internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                        delete internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                    } else delete internalState.dynamicPropertyTypesHolder["" + (idxToShift - 1)];
                                
                                currentClientValue.splice(idx, 1);
                            }
                        }
                        if (serverJSONValue[CustomArrayType.ADDITIONS])
                        {
                            const additions = serverJSONValue[CustomArrayType.ADDITIONS];
                            for (const i in additions) {
                                const element = additions[i];
                                const idx = element[CustomArrayType.INDEX];
                                let val = element[CustomArrayType.VALUE];

                                // shift right by 1 all dynamicTypes after or equal to idx as we are going to insert a new value
                                for (let idxToShift = currentClientValue.length - 1; idxToShift >= idx; idxToShift++)
                                    if (internalState.dynamicPropertyTypesHolder["" + idxToShift]) {
                                        internalState.dynamicPropertyTypesHolder["" + (idxToShift + 1)] = internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                        delete internalState.dynamicPropertyTypesHolder["" + idxToShift];
                                    } else delete internalState.dynamicPropertyTypesHolder["" + (idxToShift + 1)];
                                    
                                val = this.sabloConverters.convertFromServerToClient(val, this.staticElementType, currentClientValue[idx],
                                        internalState.dynamicPropertyTypesHolder, "" + idx, componentScope, elemPropertyContext);
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

                                currentClientValue[idx] = val = this.sabloConverters.convertFromServerToClient(val, this.staticElementType, currentClientValue[idx],
                                        internalState.dynamicPropertyTypesHolder, "" + idx, componentScope, elemPropertyContext);

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
                } else if (!serverJSONValue || !serverJSONValue[CustomArrayType.NO_OP]) newValue = null; // anything else would not be supported...    // TODO how to handle null values (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?
            } finally {
                // add back watches if needed
                this.addBackWatches(newValue, componentScope);
            }

            return newValue;
        }
        
        private getElementType(internalState, idx: number | string) {
            return this.staticElementType ? this.staticElementType : internalState.dynamicPropertyTypesHolder["" + idx];
        }

        fromClientToServer(newClientData: any, oldClientData: CustomArrayValue, scope: angular.IScope, propertyContext: sablo.IPropertyContext): any {
            // TODO how to handle null values (special watches/complete array set from client)? if null is on server and something is set on client or the other way around?

            const elemPropertyContext = propertyContext ? new sablo.typesRegistry.PropertyContext(propertyContext.getProperty,
                    sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(propertyContext.getPushToServerCalculatedValue(), this.pushToServerForElements)) : undefined;

            let internalState;
            if (newClientData) {
                if (!(internalState = newClientData[this.sabloConverters.INTERNAL_IMPL])) {
                    // this can happen when an array value was set completely in browser
                    // any 'smart' child elements will initialize in their fromClientToServer conversion;
                    // set it up, make it 'smart' and mark it as all changed to be sent to server...
                    this.initializeNewValue(newClientData, oldClientData && oldClientData[this.sabloConverters.INTERNAL_IMPL] ?
                              oldClientData[this.sabloConverters.INTERNAL_IMPL][CustomArrayType.CONTENT_VERSION] : 0,
                              propertyContext?.getPushToServerCalculatedValue());
                    if (oldClientData && oldClientData[this.sabloConverters.INTERNAL_IMPL]) this.removeAllWatches(oldClientData);
                    internalState = newClientData[this.sabloConverters.INTERNAL_IMPL];
                    
                    internalState.allChanged = true;
                } else {
                    internalState.calculatedPushToServerOfWholeProp = propertyContext?.getPushToServerCalculatedValue(); // for example if a custom object value is initially received through a return value from server side api/handler call and not as a normal model property and then the component/service assigns it to model, the push to server of it might have changed; use the one received here as arg
                    if (!internalState.calculatedPushToServerOfWholeProp) internalState.calculatedPushToServerOfWholeProp = sablo.typesRegistry.PushToServerEnum.reject;
                }
            }
            
            if (newClientData) {
                if (internalState.isChanged()) {
                    const changes = {};
                    changes[CustomArrayType.CONTENT_VERSION] = internalState[CustomArrayType.CONTENT_VERSION];
                    if (internalState.allChanged) {
                        // structure might have changed; increase version number
                        ++internalState[CustomArrayType.CONTENT_VERSION]; // we also increase the content version number - server will bump version number on full value update
                        // send all
                        const toBeSentArray = changes[CustomArrayType.VALUE] = [];
                        for (let idx = 0; idx < newClientData.length; idx++) {
                            const val = newClientData[idx];
                            const converted = this.sabloConverters.convertFromClientToServer(val, this.getElementType(internalState, idx), oldClientData ? oldClientData[idx] : undefined, scope, elemPropertyContext);
                            
                            // if it's a nested obj/array or other smart prop that just got smart in convertFromClientToServer, attach the change notifier
                            if (val && val[this.sabloConverters.INTERNAL_IMPL] && val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier)
                                val[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newClientData, idx));

                            // do not send to server if elem pushToServer is reject
                            if (!elemPropertyContext || elemPropertyContext.getPushToServerCalculatedValue().value > sablo.typesRegistry.PushToServerEnum.reject.value) toBeSentArray[idx] = converted;
                        }
                        
                        // now add watches/change notifiers to the new full value if needed (now all children are converted and made smart themselves if necessary so addBackWatches can make the distinction between smart and dumb correctly)
                        this.removeAllWatches(newClientData); // in case this was not an actual full new value but an existing array in which elements were added/removed and then it does have watches
                        this.addBackWatches(newClientData, scope);
                
                        internalState.allChanged = false;
                        internalState.changedIndexesOldValues = {};
                        if (internalState.calculatedPushToServerOfWholeProp === sablo.typesRegistry.PushToServerEnum.reject)
                        {
                            // if whole value is reject, don't sent anything
                            const x = {}; // no changes
                            x[CustomArrayType.NO_OP] = true;
                            return x;
                        }
                        else return changes;
                    } else {
                        // send only changed indexes
                        const changedElements = changes[CustomArrayType.UPDATES] = [];
                        for (const idx in internalState.changedIndexesOldValues) {
                            const newVal = newClientData[idx];
                            const oldVal = internalState.changedIndexesOldValues[idx];

                            let changed = (newVal !== oldVal);
                            if (!changed) {
                                if (internalState.elUnwatch[idx]) {
                                    // it's a dumb value - watched; see if it really changed according to sablo rules
                                    if (oldVal !== newVal) {
                                        if (typeof newVal == "object") {
                                            if (this.sabloUtils.isChanged(newVal, oldVal, internalState.conversionInfo[idx])) {
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
                                
                                let wasSmartBefore = (newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier);
                                ch[CustomArrayType.VALUE] = this.sabloConverters.convertFromClientToServer(newVal, this.getElementType(internalState, idx), oldVal, scope, elemPropertyContext);
                                if (!wasSmartBefore && (newVal && newVal[this.sabloConverters.INTERNAL_IMPL] && newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier))
                                    newVal[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(newClientData, Number.parseInt(idx))); // if it was a new object/array set at this index which was initialized by convertFromClientToServer call, do add the change notifier to it 
                                
                                changedElements.push(ch);
                            }
                        }
                        internalState.allChanged = false;
                        internalState.changedIndexesOldValues = {};
                        return changes;
                    }
                } else {
                    const x = {}; // no changes
                    x[CustomArrayType.NO_OP] = true;
                    return x;
                }
            }

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
                        const elType = this.getElementType(internalState, c);
                        if (elType) elType.updateAngularScope(elem, componentScope);
                    }
                }
            }
        }
    
        // ------------------------------------------------------------------------------

        private getChangeNotifier(propertyValue: any, idx: number) {
            return () => {
                const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
                internalState.changedIndexesOldValues[idx] = propertyValue[idx];
                internalState.changeNotifier();
            }
        }
    
        private watchDumbElementForChanges(propertyValue: CustomArrayValue, idx: number, componentScope: angular.IScope, deep: boolean): () => void  {
            // if elements are primitives or anyway not something that wants control over changes, just add an in-depth watch
            return componentScope.$watch(function() {
                return propertyValue[idx];
            }, (newvalue, oldvalue) => {
                if (oldvalue === newvalue) return;
                const internalState = propertyValue[this.sabloConverters.INTERNAL_IMPL];
                
                internalState.changedIndexesOldValues[idx] = oldvalue;
                internalState.changeNotifier();
            }, deep);
        }
    
        /** Initializes internal state on a new array value */
        private initializeNewValue(newValue: any, contentVersion: number, pushToServerCalculatedValue: sablo.typesRegistry.PushToServerEnum) {
            this.sabloConverters.prepareInternalState(newValue); 
            
            const internalState = newValue[this.sabloConverters.INTERNAL_IMPL];
            internalState[CustomArrayType.CONTENT_VERSION] = contentVersion; // being full content updates, we don't care about the version, we just accept it
            internalState.calculatedPushToServerOfWholeProp = (typeof pushToServerCalculatedValue != 'undefined' ? pushToServerCalculatedValue : sablo.typesRegistry.PushToServerEnum.reject);
    
            // implement what $sabloConverters need to make this work
            internalState.setChangeNotifier = function(changeNotifier) {
                internalState.changeNotifier = changeNotifier; 
            }
            internalState.isChanged = function() {
                let hasChanges = internalState.allChanged;
                if (!hasChanges) for (const x in internalState.changedIndexesOldValues) { hasChanges = true; break; }
                return hasChanges;
            }

            // private impl
            internalState.modelUnwatch = [];
            internalState.arrayStructureUnwatch = null;
            internalState.conversionInfo = [];
            internalState.changedIndexesOldValues = {};
            internalState.allChanged = false;
            internalState.dynamicPropertyTypesHolder = {};
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
                
                const elementCalculatedPushToServer = sablo.typesRegistry.PushToServerUtils.combineWithChildStatic(internalState.calculatedPushToServerOfWholeProp,
                        this.pushToServerForElements);
                
                // add shallow/deep watches as needed
                if (componentScope) {
                    for (let c = 0; c < value.length; c++) {
                        const elem = value[c];
                        if ((!elem || !elem[this.sabloConverters.INTERNAL_IMPL] || !elem[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier) && elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                            // watch the child's value to see if it changes
                             internalState.elUnwatch[c] = this.watchDumbElementForChanges(value, c, componentScope, elementCalculatedPushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true); // either deep or shallow because the if above checks that it is at least shallow
                        } // else if it's a smart value and the pushToServer is shallow or deep we must shallow watch it (it will manage it's own contents but we still must watch for reference changes);
                        // but that is done below in a $watchCollection
                    }
    
                    // watch for add/remove and such operations on array; this is helpful also when 'smart' child values (that have .setChangeNotifier)
                    // get changed completely by reference, so int IS NOT JUST FOR DUMB/REFERENCE changes if pushToServer >= shallow but also for updating the .setChangeNotifier on smart values
                    internalState.arrayStructureUnwatch = componentScope.$watchCollection(function() { return value; }, (newWVal, oldWVal) => {
                        if (newWVal === oldWVal) return;
    
                        let fullChangeWasAlreadySentDueToLengthChange: boolean = false;
                        if (newWVal.length !== oldWVal.length) {
                            // that watch funct. always returns the same reference "return value"; but length could differ old and new
                            // so a delete/push happened on the array; currently we treat this as a full change
                            if (elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                                internalState.allChanged = true;
                                
                                fullChangeWasAlreadySentDueToLengthChange = true;
                                
                                internalState.changeNotifier();
                            }
                        }

                        // some elements changed by reference; but array length did not change
                        let referencesChanged = false;
                        const maxLen = Math.max(newWVal.length, oldWVal.length);
                        for (let j = 0; j < maxLen; j++) {
                            const oldEl = (j < oldWVal.length ? oldWVal[j] : undefined);
                            const newEl = (j < newWVal.length ? newWVal[j] : undefined);
                            if (newEl !== oldEl) {
                                const oldElWasSmart = oldEl && oldEl[this.sabloConverters.INTERNAL_IMPL] && oldEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier;
                                if (oldElWasSmart) {
                                    oldEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(undefined);
                                }
                                
                                if (!fullChangeWasAlreadySentDueToLengthChange && elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value && oldElWasSmart) {
                                    // we only need to handle this for old smart element values,
                                    // as the others will be handled by the separate 'dumb' watches
                                    referencesChanged = true;
                                    internalState.changedIndexesOldValues[j] = oldEl;
                                }
                                
                                const newElIsSmart = newEl && newEl[this.sabloConverters.INTERNAL_IMPL] && newEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier;
                                if (newElIsSmart) {
                                    if (!oldElWasSmart) {
                                        // old el was dumb so a dumb watch was probably added on it before
                                        // it became smart so watch is obsolete; watchCollection handles smart elements
                                        if (internalState.elUnwatch[j])  internalState.elUnwatch[j]();
                                        delete internalState.elUnwatch[j];
                                    }
                                    newEl[this.sabloConverters.INTERNAL_IMPL].setChangeNotifier(this.getChangeNotifier(value, j));
                                } else { // so newEl is dumb
                                    if (!fullChangeWasAlreadySentDueToLengthChange && oldElWasSmart && elementCalculatedPushToServer.value >= sablo.typesRegistry.PushToServerEnum.shallow.value) {
                                        // it became dumb; add watch if needed
                                        internalState.elUnwatch[j] = this.watchDumbElementForChanges(value, j, componentScope, elementCalculatedPushToServer == sablo.typesRegistry.PushToServerEnum.shallow ? false : true);
                                    }
                                }
                            }
                        }

                        if (referencesChanged) internalState.changeNotifier();
                    });
                }
            }
        }
    }

    interface CustomArrayValue extends Array<any> {
        
    }
}
