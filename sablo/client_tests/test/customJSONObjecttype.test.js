describe("Test custom_object_property suite", function() {

    var sabloConverters;
    var $scope;
    var iS;
    var typesRegistry;
    var pushToServerUtils;

    var untypedObjectWithREJECTOnSubpropType;
    var untypedObjectWithREJECTOnSubpropContext;

    var propertyContext;
    var pd;

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
        module('custom_json_object_property');
        module('custom_json_array_property');

        inject(function(_$sabloConverters_, _$compile_, _$rootScope_, _$typesRegistry_, _$pushToServerUtils_) {
            var angularEquality = function(first, second) {
                return angular.equals(first, second);
            };

            jasmine.addCustomEqualityTester(angularEquality);
            // The injector unwraps the underscores (_) from around the parameter
            // names when matching
            sabloConverters = _$sabloConverters_;
            pushToServerUtils = _$pushToServerUtils_;
            typesRegistry = _$typesRegistry_;
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

    describe("custom_object_property with dumb values suite; pushToServer reject/udefined on root prop", function() {
        beforeEach(function() {
            // prepare the client side property descriptions for the custom object types that we will use
            // see ClientSideTypesTest for what it can be
            typesRegistry.addComponentClientSideSpecs({
                comp1: {
                    p: {
                        customType: ["JSON_obj", "comp1.mytype"]
                    },
                    ftd: {
                        JSON_obj: {
                            "comp1.mytype": {
                            }
                        }
                    }
                }
            });
            var compSpec = typesRegistry.getComponentSpecification("comp1");
            serverValue = {
                "vEr": 1,
                "v":
                {
                    "relationName": null,
                    "text": "pers_edit_rv"
                }
            };

            pd = compSpec.getPropertyDescription("customType");
            propertyContext = {
                getProperty: function() { },
                getPushToServerCalculatedValue: function() { return pd.getPropertyPushToServer(); }, // property context says "reject" push to server on whole array
                isInsideModel: true
            };

            realClientValue = sabloConverters.convertFromServerToClient(serverValue, pd.getPropertyType(), undefined,
                undefined, undefined,
                $scope, propertyContext);
            realClientValue[iS].setChangeNotifier(function() { changeNotified = true });
            $scope.$digest();
        });

       it("Should not send value updates for when pushToServer is not specified", function() {
            realClientValue.text = "some_modified_text";
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

    });

    describe("custom_object_property with dumb values suite; pushToServer allow on root an shallow on a dynamic typed subprop", function() {
        beforeEach(function() {
            // prepare the client side property descriptions for the custom object types that we will use
            // see ClientSideTypesTest for what it can be
            typesRegistry.addComponentClientSideSpecs({
                comp1: {
                    p: {
                        customType: { t: ["JSON_obj", "comp1.mytype"], s: 1 }
                    },
                    ftd: {
                        JSON_obj: {
                            "comp1.mytype": {
                                dateDynamicType: { s: 2 }
                            }
                        }
                    }
                }
            });
            var compSpec = typesRegistry.getComponentSpecification("comp1");
            serverValue = {
                "vEr": 1,
                "v":
                {
                    "dateDynamicType": { _T: "Date", _V: 1141240331661 }
                }
            };

            pd = compSpec.getPropertyDescription("customType");
            propertyContext = {
                getProperty: function() { },
                getPushToServerCalculatedValue: function() { return pd.getPropertyPushToServer(); }, // property context says "reject" push to server on whole array
                isInsideModel: true
            };

            realClientValue = sabloConverters.convertFromServerToClient(serverValue, pd.getPropertyType(), undefined,
                undefined, undefined,
                $scope, propertyContext);
            realClientValue[iS].setChangeNotifier(function() { changeNotified = true });
            $scope.$digest();
        });

        it("Should remember dynamic type and convert the value correctly", function() {
            expect(realClientValue.dateDynamicType instanceof Date).toBe(true);
            expect(realClientValue[iS].dynamicPropertyTypesHolder.dateDynamicType).toBe(typesRegistry.getAlreadyRegisteredType("Date"));
        });

        it("Should use remembered dynamic type and convert the value correctly to server", function() {
            realClientValue.dateDynamicType = new Date();
            var ms = realClientValue.dateDynamicType.getTime();
            $scope.$digest();
            
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(getAndClearNotified()).toEqual(true);
            
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'dateDynamicType', v: ms }] }
            );
            
            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });
        
    });

    describe("custom_object_property with dumb values suite; pushToServer tests with shallow root prop and various subprops", function() {
        beforeEach(function() {
            // prepare the client side property descriptions for the custom object types that we will use
            typesRegistry.addComponentClientSideSpecs({
                comp1: {
                    p: {
                        octWithCustomTypeAllow: {
                            t: ["JSON_obj", "comp1.octWithCustomType"],
                            s: 2
                        },
                        untypedObjectREJECT: {
                            t: ["JSON_obj", 'comp1.mytype'],
                            s: 0 }
                    },
                    ftd: {
                        JSON_obj: {
                            "comp1.mytype": {},
                            "comp1.octWithCustomType": {
                                customType: {
                                    t: ["JSON_obj", "comp1.mytype"],
                                    s: 3
                                },
                                someString: { s: 0 },
                                customTypeArray: ["JSON_arr", ["JSON_obj", "comp1.mytype"]],
                                customTypeArrayWithElReject: {
                                    t: ["JSON_arr", { t: ["JSON_obj", "comp1.mytype"], s: 0 }],
                                    s: 2
                                }
                            }
                        }
                    }
                }
            });
            var compSpec = typesRegistry.getComponentSpecification("comp1");
            serverValue = {
                "vEr": 1,
                "v":
                {
                    "relationName": null,
                    "text": "pers_edit_rv",
                    "active": true,
                    "customType": { "vEr": 1, "v": { "christmasTree": true } },
                    "customTypeArray": {
                        "vEr": 1,
                        "v": [{ "vEr": 1, "v": { "christmasTree": true } }, { "vEr": 1, "v": { "tea": "yes" } }]
                    },
                    "customTypeArrayWithElReject": {
                        "vEr": 1,
                        "v": [{ "vEr": 1, "v": { "christmasTree": true } }, { "vEr": 1, "v": { "tea": "yes" } }]
                    }
                }
            };

            pd = compSpec.getPropertyDescription("octWithCustomTypeAllow");
            propertyContext = {
                getProperty: function() { },
                getPushToServerCalculatedValue: function() { return pd.getPropertyPushToServer(); }, // property context says "reject" push to server on whole array
                isInsideModel: true
            };

            untypedObjectWithREJECTOnSubpropType = compSpec.getPropertyType('untypedObjectREJECT');
            untypedObjectWithREJECTOnSubpropContext = {
                getProperty: function() { },
                getPushToServerCalculatedValue: function() { return compSpec.getPropertyPushToServer('untypedObjectREJECT'); },
                isInsideModel: true
            }

            realClientValue = sabloConverters.convertFromServerToClient(serverValue, pd.getPropertyType(), undefined,
                undefined, undefined,
                $scope, propertyContext);
            realClientValue[iS].setChangeNotifier(function() { changeNotified = true });
            $scope.$digest();
        });

        it("Should send value updates for when pushToServer is 'shallow', inherited from root prop", function() {
            realClientValue.text = "some_modified_text";  // text subprob is not present in client side spec so it inherits root prop push to server ""shallow"
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'text', v: 'some_modified_text' }] }
            );

            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

        it("Should not send value updates when child has 'reject' even though root prop has 'shallow'", function() {
            realClientValue.someString = "some_modified_text";  // someString subprob is present in client side spec and forces "reject"
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

        it("Should send value updates for nested custom type subprop's subprop that has 'deep' push to server; root prop has 'shallow'", function() {
            realClientValue.customType.hithere = "some_modified_text";
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'customType', v: { vEr: 1, u: [{ k: 'hithere', v: 'some_modified_text' }] } }] }
            );

            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

        it("Should send value updates for nested array of custom type with calculated pushToServer val. 'shallow'", function() {
            realClientValue.customTypeArray[1].tea = "no, thank you";
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'customTypeArray', v: { vEr: 1, u: [{ i: '1', v: { vEr: 1, u: [{ k: 'tea', v: "no, thank you" }] } }] } }] }
            );

            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

        it("Should send not send value updates for nested array of custom type with calculated parent pushToServer val. 'shallow' and element config pushToServer reject", function() {
            realClientValue.customTypeArrayWithElReject[1].tea = "no, thank you";
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

        it("Should allow setting a new full value of nested object/arrays, send it to server and add watches as needed", function() {
            oldVal = realClientValue;
            realClientValue = {
                "text": "wholeNewText",
                "active": false,
                "someString": "rejectME!",
                "customType": { "christmasTree": false, "yogi": "bubu" },
                "customTypeArray": [{ "christmasTree": true }, { "tea": "yes" }],
                "customTypeArrayWithElReject": [{ "christmasTree": true }, { "tea": "yes" }]
            };

            // not everything will be sent to server because of pushToServer reject
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), oldVal, $scope, propertyContext)).toEqual(
                {
                    vEr: 0, v: {
                        text: 'wholeNewText',
                        active: false,
                        customType: {
                            vEr: 0, v: {
                                christmasTree:
                                    false, yogi: 'bubu'
                            }
                        },
                        customTypeArray: {
                            vEr: 0, v: [{ vEr: 0, v: { christmasTree: true } }, {
                                vEr: 0, v: { tea: 'yes' }
                            }]
                        },
                        customTypeArrayWithElReject: { vEr: 0, v: [] }
                    }
                });

            // realClientValue should now be 'initialized' with internal state, watches etc.
            realClientValue[iS].setChangeNotifier(function() { changeNotified = true });
            $scope.$digest(); // just to initialize watches (run them once so changes have correct old values to compare with)
            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
            
            // ok now do check that notifiers and watches were set up correctly
            
            realClientValue.customTypeArray[1].tea = "no, thank you";
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'customTypeArray', v: { vEr: 1, u: [{ i: '1', v: { vEr: 1, u: [{ k: 'tea', v: "no, thank you" }] } }] } }] }
            );
            
            realClientValue.customType.hithere = "how are you today?";
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'customType', v: { vEr: 1, u: [{ k: 'hithere', v: 'how are you today?' }] } }] }
            );
            
            realClientValue.customTypeArrayWithElReject[1].tea = "no, no";
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
            
            realClientValue.someString = "some_modified_text";  // someString subprob is present in client side spec and forces "reject"
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);

            realClientValue.text = "some_modified_text";  // text subprob is not present in client side spec so it inherits root prop push to server ""shallow"
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                { vEr: 1, u: [{ k: 'text', v: 'some_modified_text' }] }
            );

            // old key in custom object full new value with old val != null
            realClientValue.customType = { "happy holidays!": true, "ranger": "cartoons" };
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                {
                    vEr: 1, u: [{
                        k: 'customType',
                        v: {
                            vEr: 0, v: { "happy holidays!": true, "ranger": "cartoons" }
                        }
                    }]
                });
                
            // old key in custom object full new value
            delete realClientValue.customType;
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                {
                    vEr: 1, u: [{
                        k: 'customType',
                        v: null
                    }]
                });
            
            // old key in custom object full new value with old val == null
            realClientValue.customType = { "happy holidays!": true, "ranger": "cartoons" };
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                {
                    vEr: 1, u: [{
                        k: 'customType',
                        v: {
                            vEr: 0, v: { "happy holidays!": true, "ranger": "cartoons" }
                        }
                    }]
                });

            // old idx in custom array full new value
            realClientValue.customTypeArray[0] = { itsJanuaryAlready: "so out with the tree" };
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
            expect(realClientValue[iS].isChanged()).toEqual(true);
            expect(sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext)).toEqual(
                {
                    vEr: 1, u: [{
                        k: 'customTypeArray',
                        v: {
                            vEr: 1,
                            u: [{
                                i: '0',
                                v: { vEr: 0, v: { itsJanuaryAlready: "so out with the tree" } }
                            }]
                        }
                    }]
                });
                
            expect(getAndClearNotified()).toEqual(false);
            expect(realClientValue[iS].isChanged()).toEqual(false);
        });

        it( 'send obj as arg to handler, change a tab by ref', () => {
            var propertyContextForArg = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; },
                isInsideModel: false
            };

            // simulate a send to server as argument to a handler for this array (oldVal undefined) - to make sure it doesn't messup it's state if it's also a model prop. (it used getParentPropertyContext above which is for a model prop)
            const changes = sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), undefined,
                $scope, propertyContextForArg);
            $scope.$digest();
            
            expect( changes ).toEqual({ vEr: 0, v: {
                    relationName: null,
                    text: "pers_edit_rv",
                    active: true,
                    customType: { vEr: 0, v: { christmasTree: true } },
                    customTypeArray: {
                        vEr: 0,
                        v: [{ vEr: 0, v: { christmasTree: true } }, { vEr: 0, v: { tea: "yes" } }]
                    },
                    customTypeArrayWithElReject: {
                        vEr: 0,
                        v: []
                    }
                } });
    
            realClientValue.text = 'hi';
            $scope.$digest();
            expect(getAndClearNotified()).toEqual(true);
    
            const changes2 = sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext);
            expect( changes2 ).toEqual( { vEr: 1, u: [ { k: 'text', v: 'hi' } ] } );
        } );
    
        it( 'change obj subprop. by ref but do not send to server (so it still has changes to send for the model property), then send obj as arg to handler, change another subprop; both subprops changed by ref in the model should be then sent to server', () => {
            var propertyContextForArg = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; },
                isInsideModel: false
            };

            realClientValue.text = 'hello';
            $scope.$digest();

            expect(getAndClearNotified()).toEqual(true);
    
            // simulate a send to server as argument to a handler for this array (oldVal undefined) - to make sure it doesn't messup it's state if it's also a model prop. (it used getParentPropertyContext above which is for a model prop)
            const changes = sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), undefined,
                $scope, propertyContextForArg);
            $scope.$digest();
            
            expect( changes ).toEqual({ vEr: 0, v: {
                    relationName: null,
                    text: "hello",
                    active: true,
                    customType: { vEr: 0, v: { christmasTree: true } },
                    customTypeArray: {
                        vEr: 0,
                        v: [{ vEr: 0, v: { christmasTree: true } }, { vEr: 0, v: { tea: "yes" } }]
                    },
                    customTypeArrayWithElReject: {
                        vEr: 0,
                        v: []
                    }
                } });
    
            realClientValue.customType.christmasTree = false;
            $scope.$digest();
    
            expect(getAndClearNotified()).toEqual(true);
    
            const changes2 = sabloConverters.convertFromClientToServer(realClientValue, pd.getPropertyType(), realClientValue, $scope, propertyContext);
            expect( changes2 ).toEqual( { vEr: 1, u: [ { k: 'text', v: 'hello' }, { k: 'customType', v: { vEr: 1, u: [ { k: 'christmasTree', v: false } ] } } ] } );
        } );

        it( 'send obj from model (with push to server reject) as arg to handler', () => {
            const propertyContextForArg = {
                getProperty: function() {},
                getPushToServerCalculatedValue: function() { return pushToServerUtils.allow; },
                isInsideModel: false
            };
    
            const val = sabloConverters.convertFromServerToClient({ v: { sa007: 'test' }, vEr: 1 },
                   untypedObjectWithREJECTOnSubpropType , undefined, undefined, undefined, $scope, untypedObjectWithREJECTOnSubpropContext);
            val[iS].setChangeNotifier(function() { changeNotified = true });
    
            // simulate a send to server as argument to a handler, which should work even though it is a model value with push to server reject
            const changes = sabloConverters.convertFromClientToServer(val, untypedObjectWithREJECTOnSubpropType, undefined, $scope,
                propertyContextForArg);
            
            expect(changes.vEr).toBe(0);
            expect(changes.v).toBeDefined();
            expect(changes.v.sa007).toBe('test');
    
            val.sa007 = 'test4';
            $scope.$digest();
    
            expect(getAndClearNotified()).toBe(false);
            
            // inside the model it is push to server reject
            const changes2 = sabloConverters.convertFromClientToServer(val, untypedObjectWithREJECTOnSubpropType, val, $scope,
                untypedObjectWithREJECTOnSubpropContext);
            expect( changes2.n ).toBeTrue();
        } );

    });


});
