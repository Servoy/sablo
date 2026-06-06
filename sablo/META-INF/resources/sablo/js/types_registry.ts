/// <reference path="../../../../typings/angularjs/angular.d.ts" />
/// <reference path="../../../../typings/sablo/sablo.d.ts" />

angular.module('$typesRegistry', [])
.factory('$typesRegistry', function($log: sablo.ILogService) {

    return new sablo.typesRegistry.TypesRegistry($log);
    
})
.factory('$pushToServerUtils', function() {

    // we have to declare this service - as it is needed from servoy as well, not just from sablo and in NG1 this is how you get access to sablo stuff
    return new sablo.typesRegistry.PushToServerUtils();
    
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
        private types: ObjectOfIType = {}; // simple (don't need a factory to create more specific sub-types) global types that need client-side conversion
        private disablePushToServerWatches: boolean = false; // only needed by Servoy form desiner - it will block all push to server watch creation

        constructor(private readonly logger: sablo.ILogService) {}

        getTypeFactoryRegistry(): ITypeFactoryRegistry  {
            return this.typeFactoryRegistry;
        }

        registerGlobalType(typeName: string, theType: IType<any>, onlyIfNotAlreadyRegistered: boolean) {
            if (!theType) throw new Error("You cannot register a null/undefined global type for '" + typeName + "'!");
            if (this.types[typeName]) {
                this.logger.debug("[TypesRegistry] registerGlobalType - a global type with the same name (" + typeName + ") was already previously registered. Old: "
                    + this.types[typeName].constructor['name'] + ", New: " + theType.constructor['name'] + ". The old one will be" + (onlyIfNotAlreadyRegistered ? "kept" : " discarded."));
                if (!onlyIfNotAlreadyRegistered) this.types[typeName] = theType;
            } else this.types[typeName] = theType;
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
                const factoryTypeFromServer = typeFromServer as [string, object] ;
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

        processPropertyDescriptionFromServer(propertyDescriptionFromServer: IPropertyDescriptionFromServer, webObjectSpecName: string): IPropertyDescription {
            if (propertyDescriptionFromServer instanceof Array || typeof propertyDescriptionFromServer == "string") {
                // it's just a type, no pushToServer value
                const t = this.processTypeFromServer(propertyDescriptionFromServer as ITypeFromServer, webObjectSpecName);
                return t ? new PropertyDescription(t) : undefined;
            } else {
                // it's a PD, type && pushToServer
                const propertyDescriptionWithMultipleEntries = propertyDescriptionFromServer as IPropertyDescriptionFromServerWithMultipleEntries;
                return new PropertyDescription(
                        propertyDescriptionWithMultipleEntries.t ? this.processTypeFromServer(propertyDescriptionWithMultipleEntries.t, webObjectSpecName) : undefined,
                        this.disablePushToServerWatches ? PushToServerEnum.reject : PushToServerEnum.valueOf(propertyDescriptionWithMultipleEntries.s));
            }
        }
        
        disablePushToServerWatchesReceivedFromServer(): void {
            this.disablePushToServerWatches = true;
        }

        // METHODS that are not in sablo.ITypesRegistry start here:

        /** Add a bunch of component specifications/client side types/push to server values that the server has sent to the registry; they will be needed client side. */
        addComponentClientSideSpecs(componentSpecificationsFromServer: IWebObjectTypesFromServer) {
            if (this.logger.debugEnabled && this.logger.debugLevel == this.logger.SPAM) this.logger.debug("[typesRegistry] Adding component specifications for: " + JSON.stringify(componentSpecificationsFromServer, undefined, 2));
            
            for (const componentSpecName in componentSpecificationsFromServer) {
                this.componentSpecifications[componentSpecName] = this.processWebObjectSpecificationFromServer(componentSpecName, componentSpecificationsFromServer[componentSpecName]);
            }
        }

        /** The server sent all the service specifications/client side types/push to server values. Those are always sent initially as you never know when client side code might call a service... */
        setServiceClientSideSpecs(serviceSpecificationsFromServer: IWebObjectTypesFromServer) {
            if (this.logger.debugEnabled && this.logger.debugLevel == this.logger.SPAM) this.logger.debug("[sabloService] Setting service specifications for: " + JSON.stringify(serviceSpecificationsFromServer, undefined, 2));
            
            this.serviceSpecifications = {};
            for (const serviceSpecName in serviceSpecificationsFromServer) {
                this.serviceSpecifications[serviceSpecName] = this.processWebObjectSpecificationFromServer(serviceSpecName, serviceSpecificationsFromServer[serviceSpecName]);
            }
        }

        getComponentSpecification(componentSpecName: string): IWebObjectSpecification {
            return this.componentSpecifications[componentSpecName];
        }

        getServiceSpecification(serviceSpecName: string): IWebObjectSpecification {
            return this.serviceSpecifications[serviceSpecName];
        }

        /**
         * @param webObjectSpecificationFromServer see ***ClientSideTypeCache.java method buildClientSideTypesFor*** javadoc that describes what we receive here.
         */
        private processWebObjectSpecificationFromServer(webObjectSpecName: string, webObjectSpecificationFromServer: IWebObjectSpecificationFromServer): WebObjectSpecification {

            // first create the custom object types defined in this spec ('ftd' stands for factory type details)
            if (webObjectSpecificationFromServer.ftd) this.processFactoryTypeDetails(webObjectSpecificationFromServer.ftd, webObjectSpecName);

            let properties: ObjectOfIPropertyDescription;
            let handlers: ObjectOfIEventHandlerFunctions;
            let apiFunctions: ObjectOfIWebObjectFunctions;

            // properties
            if (webObjectSpecificationFromServer.p) {
                properties = {};
                for (const propertyName in webObjectSpecificationFromServer.p) {
                    properties[propertyName] = this.processPropertyDescriptionFromServer(webObjectSpecificationFromServer.p[propertyName], webObjectSpecName);
                }
            }

            // handlers
            if (webObjectSpecificationFromServer.h) {
                handlers = {};
                for (const handlerName in webObjectSpecificationFromServer.h) {
                    handlers[handlerName] = this.processEventHandler(webObjectSpecificationFromServer.h[handlerName], webObjectSpecName);
                }
            }

            // api functions
            if (webObjectSpecificationFromServer.a) {
                apiFunctions = {};
                for (const apiFunctionName in webObjectSpecificationFromServer.a) {
                    apiFunctions[apiFunctionName] = this.processApiFunction(webObjectSpecificationFromServer.a[apiFunctionName], webObjectSpecName);
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

        private processEventHandler(functionFromServer: IEventHandlerFromServer, webObjectSpecName: string): IEventHandler {
            const retTypeAndArgTypes = this.processFunction(functionFromServer, webObjectSpecName);
            return new WebObjectEventHandler(retTypeAndArgTypes[0], retTypeAndArgTypes[1], functionFromServer.iBDE);
        }

        private processApiFunction(functionFromServer: IWebObjectFunctionFromServer, webObjectSpecName: string): IWebObjectFunction {
            const retTypeAndArgTypes = this.processFunction(functionFromServer, webObjectSpecName);
            return new WebObjectFunction(retTypeAndArgTypes[0], retTypeAndArgTypes[1]);
        }

        private processFunction(functionFromServer: IWebObjectFunctionFromServer, webObjectSpecName: string): [IType<any>, ObjectOfITypeWithNumberKeys] {
            let returnType: IType<any>;
            let argumentTypes: ObjectOfITypeWithNumberKeys;

            if (functionFromServer.r) returnType = this.processTypeFromServer(functionFromServer.r, webObjectSpecName);
            for (const argIdx in functionFromServer) {
                if (argIdx !== 'r' && argIdx !== 'iBDE') {
                    if (!argumentTypes) argumentTypes = {};
                    argumentTypes[argIdx] = this.processTypeFromServer(functionFromServer[argIdx], webObjectSpecName);
                }
            }
            return [returnType, argumentTypes];
        }
    }

    export class RootPropertyContextCreator implements IPropertyContextCreator {

        constructor(private readonly getProperty: IPropertyContextGetterMethod, private readonly webObjectSpec: IWebObjectSpecification) {}

        withPushToServerFor(rootPropertyName: string): PropertyContext {
            return new PropertyContext(this.getProperty,
                this.webObjectSpec ? this.webObjectSpec.getPropertyPushToServer(rootPropertyName) : PushToServerEnum.reject, // getPropertyPushToServer not getPropertyDeclaredPushToServer
                true);
        }
    }

    export class ChildPropertyContextCreator implements IPropertyContextCreator {

        constructor(private readonly getProperty: IPropertyContextGetterMethod,
                private readonly propertyDescriptions: { [propName: string]: sablo.IPropertyDescription },
                private readonly computedParentPushToServer: sablo.IPushToServerEnum,
                private readonly isInsideModel: boolean) {}

        withPushToServerFor(childPropertyName: string): PropertyContext {
            return new PropertyContext(this.getProperty,
                PushToServerUtils.combineWithChildStatic(this.computedParentPushToServer, this.propertyDescriptions[childPropertyName]?.getPropertyDeclaredPushToServer()), // getPropertyDeclaredPushToServer not getPropertyPushToServer
                this.isInsideModel);
        }
    }

    export class PropertyContext implements IPropertyContext {

        constructor(public readonly getProperty: IPropertyContextGetterMethod,
            private readonly pushToServerComputedValue: PushToServerEnum,
            public readonly isInsideModel: boolean) {}

        getPushToServerCalculatedValue(): PushToServerEnum { return this.pushToServerComputedValue; }
    }

    export class PushToServerEnum implements sablo.IPushToServerEnum {

        public static readonly reject = new PushToServerEnum(0); // default, throw exception when updates are pushed to server
        public static readonly allow = new PushToServerEnum(1); // allow changes, no default watch client-side
        public static readonly shallow = new PushToServerEnum(2); // allow changes, creates a watcher on client with objectEquality = false
        public static readonly deep = new PushToServerEnum(3); // allow changes, creates a watcher on client with objectEquality = true; only meant to be used with 'object' type (which can be nested JSON values), not with custom json object/array types which are smart and shallow would be enough/inherited through all nested levels of them (all levels)

        private static readonly values = [PushToServerEnum.reject, PushToServerEnum.allow, PushToServerEnum.shallow, PushToServerEnum.deep]; // indexes in array match the raw value

        private constructor(public readonly value: PushToServerEnumValue) {};

        public combineWithChild(childDeclaredPushToServer: IPushToServerEnum) {
            return PushToServerUtils.combineWithChildStatic(this, childDeclaredPushToServer);
        }

        public static valueOf(pushToServerRawValue: PushToServerEnumValue): PushToServerEnum {
            return PushToServerEnum.values[pushToServerRawValue];
        }

    }

    export class PushToServerUtils implements sablo.IPushToServerUtils {

        public readonly reject = PushToServerEnum.reject;
        public readonly allow = PushToServerEnum.allow;
        public readonly shallow = PushToServerEnum.shallow;
        public readonly deep = PushToServerEnum.deep; // only meant to be used with 'object' type (which can be nested JSON values)

        public static combineWithChildStatic(parentComputedPushToServer: PushToServerEnum, childDeclaredPushToServer: PushToServerEnum) {
            let computed:PushToServerEnum;
            if (typeof parentComputedPushToServer == 'undefined') parentComputedPushToServer = PushToServerEnum.reject; // so parent can never be undefined; it would be reject then

            if (parentComputedPushToServer == PushToServerEnum.reject || childDeclaredPushToServer == PushToServerEnum.reject) computed = PushToServerEnum.reject;
            else
            {
                // parent is not reject; child is not reject; all other values are inherited if not present in child or replaced by child value if present
                if (childDeclaredPushToServer == null) computed = parentComputedPushToServer; // parent cannot be undefined
                else computed = childDeclaredPushToServer;
            }

            return computed;
        }

        public enumValueOf(pushToServerRawValue: PushToServerEnumValue): PushToServerEnum {
            return PushToServerEnum.valueOf(pushToServerRawValue);
        }

        public newRootPropertyContextCreator(getProperty: IPropertyContextGetterMethod, webObjectSpec: IWebObjectSpecification): IPropertyContextCreator {
            return new RootPropertyContextCreator(getProperty, webObjectSpec);
        }

        public newChildPropertyContextCreator(getProperty: IPropertyContextGetterMethod,
                    propertyDescriptions: { [propName: string]: sablo.IPropertyDescription },
                    computedParentPushToServer: sablo.IPushToServerEnum, isInsideModel: boolean): IPropertyContextCreator {
            return new ChildPropertyContextCreator(getProperty, propertyDescriptions, computedParentPushToServer, isInsideModel);
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

    type ObjectOfIPropertyDescription = { [key: string]: IPropertyDescription }
    type ObjectOfIWebObjectFunctions = { [key: string]: IWebObjectFunction }
    type ObjectOfIEventHandlerFunctions = { [key: string]: IEventHandler }

    class PropertyDescription implements IPropertyDescription {

        constructor (
                private readonly propertyType?: IType<any>,
                private readonly pushToServer?: IPushToServerEnum,
            ) {}

        getPropertyType(): IType<any> { return this.propertyType; }
        getPropertyDeclaredPushToServer(): IPushToServerEnum { return this.pushToServer;  }
        getPropertyPushToServer(): IPushToServerEnum { return this.pushToServer ? this.pushToServer : PushToServerEnum.reject; }
    }

    class WebObjectSpecification implements IWebObjectSpecification {

        constructor (
            public readonly webObjectType: string,
            private readonly propertyDescriptions?: ObjectOfIPropertyDescription,
            private readonly handlers?: ObjectOfIWebObjectFunctions,
            private readonly apiFunctions?: ObjectOfIWebObjectFunctions
        ) {}

        getPropertyDescription(propertyName:string): IPropertyDescription {
            return this.propertyDescriptions ? this.propertyDescriptions[propertyName] : undefined;
        }

        getPropertyType(propertyName: string): IType<any> {
            return this.propertyDescriptions ? this.propertyDescriptions[propertyName]?.getPropertyType() : undefined;
        }

        getPropertyDeclaredPushToServer(propertyName:string): IPushToServerEnum {
            return this.propertyDescriptions ? this.propertyDescriptions[propertyName]?.getPropertyDeclaredPushToServer() : undefined;
        }

        getPropertyPushToServer(propertyName:string): IPushToServerEnum {
            if (!this.propertyDescriptions || !this.propertyDescriptions[propertyName]) return PushToServerEnum.reject;
            return this.propertyDescriptions[propertyName].getPropertyPushToServer();
        }

        /** this can return null if no property descriptions needed to be sent to client (no special client side type nor pushToServer) */
        getPropertyDescriptions(): ObjectOfIPropertyDescription {
            return this.propertyDescriptions;
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

        constructor(
                readonly returnType?: IType<any>,
                private readonly argumentTypes?: ObjectOfITypeWithNumberKeys,
            ) {}

        getArgumentType(argumentIdx: number): IType<any> {
            return this.argumentTypes ? this.argumentTypes[argumentIdx] : undefined;
        }

    }

    class WebObjectEventHandler extends WebObjectFunction implements IEventHandler {

        constructor(
                returnType?: IType<any>,
                argumentTypes?: ObjectOfITypeWithNumberKeys,
                readonly ignoreNGBlockDuplicateEvents?: boolean
        ) {
            super(returnType, argumentTypes);
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
             h?: IEventHandlersFromServer;

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
        [propertyName: string]: sablo.IPropertyDescriptionFromServer;
    }

    interface IEventHandlersFromServer {
        [name: string]: IEventHandlerFromServer;
    }

    interface IWebObjectFunctionsFromServer {
        [name: string]: IWebObjectFunctionFromServer;
    }

    interface IEventHandlerFromServer extends IWebObjectFunctionFromServer {
        /** "ignoreNGBlockDuplicateEvents" flag from spec. - if the handler is supposed to ignore the blocking of duplicates - when that is enabled via client or ui properties of component */
        iBDE?: boolean;
    }

    interface IWebObjectFunctionFromServer {
        /** return value of api/handler call if it's a converting client side type */
        r?: ITypeFromServer;
        /** any api/handler call arguments with client side conversion types (by arg no.)  */
        [argumentIdx: number]: ITypeFromServer;
    }

}