/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('typesRegistryModule', [])
.factory('$typesRegistry', function($log: sablo.ILogService/*, $sabloConverters: sablo.ISabloConverters, $sabloUtils: sablo.ISabloUtils, $sabloConstants: sablo.SabloConstants*/) {

	return new sablo.typesRegistry.TypesRegistry($log);
	
});


/** types useful for when type information is received from server and processed; what is used from the rest of the client side code that does not care about how types are received from server should be only through sablo.ITypesRegistry and others from sablo namespace */
namespace sablo.typesRegistry {

	type ObjectOfWebObjectSpecification = { [key: string]: WebObjectSpecification }
	type ObjectOfIType = { [key: string]: IType<any> }
	
	/** This class holds (registers and provides) information about all service specifications/client side types and all needed component specifications/client side types. See also interface doc. */
	export class TypesRegistry implements sablo.ITypesRegistryForTypeFactories, sablo.ITypesRegistryForSabloConverters {
		
		private componentSpecifications: ObjectOfWebObjectSpecification = {};
		private serviceSpecifications: ObjectOfWebObjectSpecification = {};
		private typeFactoryRegistry: ITypeFactoryRegistry = new TypeFactoryRegistry();
		private types: ObjectOfIType; // simple (don't need a factory to create more specific sub-types) global types that need client-side conversion
	
		constructor(private readonly logger: sablo.ILogService) {}

		getTypeFactoryRegistry(): ITypeFactoryRegistry  {
			return this.typeFactoryRegistry;
		}
		
		registerGlobalType(typeName: string, theType: IType<any>) {
			if (!theType) throw new Error("You cannot register a null/undefined global type for '" + typeName + "'!");
			if (this.types[typeName]) this.logger.debug("[TypesRegistry] registerGlobalType - a global type with the same name (" + typeName + ") was already previously registered. Old: "
					+ this.types[typeName].constructor['name'] + ", New: " + theType.constructor['name'] + ". The old one will be discarded.");
			
			this.types[typeName] = theType;
		}
		
		getAlreadyRegisteredType(typeFromServer: ITypeFromServer): IType<any> {
			let t: IType<any> = undefined;
			if (typeof typeFromServer == "string") {
				t = this.types[typeFromServer];
				if (!t) this.logger.error("[TypeRegistry] getAlreadyRegisteredType: cannot find simple client side type '" + typeFromServer + "'; no such type was registered in client side code; ignoring...");
			}
			return t;
		}
		
		processTypeFromServer(typeFromServer: ITypeFromServer, webObjectSpecName: string): IType<any> {
			if (typeof typeFromServer == "string") {
				let t = this.types[typeFromServer];
				if (!t) this.logger.error("[TypeRegistry] processTypeFromServer: cannot find simple client side type '" + typeFromServer + "'; no such type was registered in client side code; ignoring...");
				return t;
			} else {
				const factoryTypeFromServer = typeFromServer as [string, string] ;
				// it's a factory created type; get the actual specific type name from the factory
				const typeFactory = this.typeFactoryRegistry.getTypeFactory(factoryTypeFromServer[0]);
				if (!typeFactory) {
					this.logger.error("[TypeRegistry] trying to process factory type into actual specific type for a factory type with name '" + factoryTypeFromServer[0] + "' but no such factory is registered...");
					return null;
				} else {
					return typeFactory.getOrCreateSpecificType(factoryTypeFromServer[1], webObjectSpecName);
				}
			}
		}
		
		// METHODS that are not in sablo.ITypesRegistry start here:
		
		/** Add a bunch of component specifications/client side types that the server has sent to the registry; they will be needed client side. */
		addComponentClientSideConversionTypes(componentSpecificationsFromServer: IWebObjectTypesFromServer) {
			for (const componentSpecName in componentSpecificationsFromServer) {
				this.componentSpecifications[componentSpecName] = this.processWebObjectSpecificationFromServer(componentSpecName, componentSpecificationsFromServer[componentSpecName]);
			}
		}
		
		/** The server sent all the service specifications/client side types. Those are always sent initially as you never know when client side code might call a service... */
		setServiceClientSideConversionTypes(serviceSpecificationsFromServer: IWebObjectTypesFromServer) {
			this.serviceSpecifications = {};
			for (const serviceSpecName in serviceSpecificationsFromServer) {
				this.serviceSpecifications[serviceSpecName] = this.processWebObjectSpecificationFromServer(serviceSpecName, serviceSpecificationsFromServer[serviceSpecName]);
			}
		}
		
		private processWebObjectSpecificationFromServer(webObjectSpecName: string, webObjectSpecificationFromServer: IWebObjectSpecificationFromServer): WebObjectSpecification {
			// first create the custom (object) types defined in this spec ('ftd' stands for factory type details)
			if (webObjectSpecificationFromServer.ftd) this.processFactoryTypeDetails(webObjectSpecificationFromServer.ftd, webObjectSpecName);
			
			let properties: ObjectOfIType;
			let handlers: ObjectOfIWebObjectFunctions;
			let apiFunctions: ObjectOfIWebObjectFunctions;
			
			if (webObjectSpecificationFromServer.p) {
				properties = {};
				for (const propertyName in webObjectSpecificationFromServer.p) {
					properties[propertyName] = this.processTypeFromServer(webObjectSpecificationFromServer.p[propertyName], webObjectSpecName);
				}
			}
			
			if (webObjectSpecificationFromServer.ha) {
				handlers = {};
				for (const handlerName in webObjectSpecificationFromServer.ha) {
					handlers[handlerName] = this.processFunction(webObjectSpecificationFromServer.ha[handlerName], webObjectSpecName);
				}
			}
			
			if (webObjectSpecificationFromServer.a) {
				apiFunctions = {};
				for (const apiFunctionName in webObjectSpecificationFromServer.a) {
					apiFunctions[apiFunctionName] = this.processFunction(webObjectSpecificationFromServer.a[apiFunctionName], webObjectSpecName);
				}
			}
			
			return new WebObjectSpecification(webObjectSpecName, properties, handlers, apiFunctions);
		}
		
		private processFactoryTypeDetails(factoryTypeDetails: IFactoryTypeDetails, webObjectSpecName: string): void {
			// currently this is used only for custom object types
			for (const factoryName in factoryTypeDetails) {
				const typeFactory = this.typeFactoryRegistry.getTypeFactory(factoryName);
				if (!typeFactory) this.logger.error("[TypeRegistry] trying to add details to a factory type with name '" + factoryName + "' but no such factory is registered client-side...");
				else {
					typeFactory.registerDetails(factoryTypeDetails[factoryName], webObjectSpecName);
				}
			}
		}
	
		private processFunction(functionFromServer: IWebObjectFunctionFromServer, webObjectSpecName: string): IWebObjectFunction {
			let returnType: IType<any>;
			let argumentTypes: ObjectOfITypeWithNumberKeys;
		
			if (functionFromServer.r) returnType = this.processTypeFromServer(functionFromServer.r, webObjectSpecName);
			for (const argIdx in functionFromServer) {
				if (argIdx != "r") {
					if (!argumentTypes) argumentTypes = {};
					argumentTypes[argIdx] = this.processTypeFromServer(functionFromServer[argIdx], webObjectSpecName);
				}
			}
			return new WebObjectFunction(returnType, argumentTypes);
		}
	
	}
	
	type ObjectOfITypeFactory = { [key: string]: ITypeFactory<any> }
	
	class TypeFactoryRegistry implements ITypeFactoryRegistry {
		
		private readonly typeFactories: ObjectOfITypeFactory = {};
		
		getTypeFactory(typeFactoryName: string): ITypeFactory<any> {
			return this.typeFactories[typeFactoryName];
		}
		
		contributeTypeFactory(typeFactoryName: string, typeFactory: ITypeFactory<any>) {
			this.typeFactories[typeFactoryName] = typeFactory;
		}
		
	}
	
	type ObjectOfStrings = { [key: string]: string }
	type ObjectOfIWebObjectFunctions = { [key: string]: IWebObjectFunction }
	
	class WebObjectSpecification implements IWebObjectSpecification {
		
		constructor (
			private readonly webObjectType: string,
			private readonly properties?: ObjectOfIType,
			private readonly handlers?: ObjectOfIWebObjectFunctions,
			private readonly apiFunctions?: ObjectOfIWebObjectFunctions
		) {}
		
		getPropertyType(propertyName: string): IType<any> {
			return this.properties ? this.properties[propertyName] : undefined;
		}
		
		getHandler(handlerName: string): IWebObjectFunction {
			return this.handlers ? this.handlers[handlerName] : undefined;
		}
		
		getApiFunction(apiFunctionName: string): IWebObjectFunction {
			return this.apiFunctions ? this.apiFunctions[apiFunctionName] : undefined;
		}
			 
	}
	
	type ObjectOfITypeWithNumberKeys = { [key: number]: IType<any> }
	
	class WebObjectFunction implements IWebObjectFunction {
		
		constructor (
				readonly returnType?: IType<any>,
				private readonly argumentTypes?: ObjectOfITypeWithNumberKeys,
			) {}

		getArgumentType(argumentIdx: number): IType<any> {
			return this.argumentTypes ? this.argumentTypes[argumentIdx] : undefined;
		}
	}
	
	export interface IWebObjectTypesFromServer {
		[specName: string]: IWebObjectSpecificationFromServer;
	}
	
	/** This type definition must match what the server sends; see org.sablo.specification.ClientSideTypeCache.buildClientSideTypesFor(WebObjectSpecification) javadoc and impl. */
	interface IWebObjectSpecificationFromServer {
		
			 p?: IPropertiesFromServer;
			 ftd?: IFactoryTypeDetails; // this will be the custom type details from spec something like { "JSON_obj": ICustomTypesFromServer}}
					 
			 /** any handlers */
			 ha?: IWebObjectFunctionsFromServer;
			 
			 /** any api functions */
			 a?: IWebObjectFunctionsFromServer;
			 
	}
	
	interface IFactoryTypeDetails {
		[factoryTypeName:string]: any; // generic, for any factory type; that any will be ICustomTypesFromServer in case of JSON_obj factory
	}
	
	/** Any custom object types defined in the component/service .spec (by name, each containing the sub-properties defined in spec. for it) */
	export interface ICustomTypesFromServer {
		[customTypeName: string]: IPropertiesFromServer;
	}
	
	/**
	  * So any properties that have client side conversions (by name or in case of factory types via a tuple / array of 2: factory name and factory param);
	  * these tuples are used only when getting them from server, afterwards when the IProperties obj. is genearated from this, the specific type from that factory is created and the value of the property type is changed from the tuple to the string that represents the created type.
	  */
	export interface IPropertiesFromServer {
		[propertyName: string]: sablo.ITypeFromServer;
	}
	
	interface IWebObjectFunctionsFromServer {
		[name: string]: IWebObjectFunctionFromServer;
	}
	
	interface IWebObjectFunctionFromServer {
		/** return value of api/handler call if it's a converting client side type */
		r: ITypeFromServer;
	    /** any api/handler call arguments with client side conversion types (by arg no.)  */
		[argumentIdx: number]: ITypeFromServer;
	}

}
