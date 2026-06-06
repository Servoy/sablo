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
      inject(function(_$rootScope_,_$compile_ ,_$templateCache_,_$q_){
    	  
    	  $compile = _$compile_
    	  $scope = _$rootScope_.$new();
    	  $templateCache = _$templateCache_;
  	  })
  	  // mock timout
	  jasmine.clock().install();
	});
    afterEach(function() {
        jasmine.clock().uninstall();
    })
  	it("should apply sibling sablo-tabseq", function() {
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}"><div name="myTag" sablo-tabseq="myModel1"></div><div name="myOtherTag" sablo-tabseq="myModel2"></div><div name="myOtherOtherTag" sablo-tabseq="myModel3"></div></div>'; 
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
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
				  			'<div name="myTag" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
				  				'<div name="myOtherTag" sablo-tabseq="1" sablo-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" sablo-tabseq="myModel111">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" sablo-tabseq="myModel112">' +
					  				'</div>' +
				  				'</div>' +
				  				'<div name="myOtherTag" sablo-tabseq="2" sablo-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" sablo-tabseq="myModel121">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" sablo-tabseq="myModel122">' +
					  				'</div>' +
				  				'</div>' +
				  			'</div>' +
				  			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
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
  		// default tab sequence - tabIndex should not be set
  		$scope.myModel1 = 0;
  		$scope.myModel111 = 0;
  		$scope.myModel112 = 0;
  		$scope.myModel121 = 0;
  		$scope.myModel122 = 0;
  		$scope.myModel2 = 0;
  		$scope.$digest();

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe(undefined);

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

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('4');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('4');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('152');
  		// DONT SPLIT CODE --------------- END --------------------------
  		
  	});
  	
  	it("should apply tabIndex for children if rows are added", function() {
  		// simulate record view form with 1 portal in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
			'<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
				'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
  				'<div name="myOtherTagC1" sablo-tabseq="myModel1">' +
  				'</div>' +
  				'<div name="myOtherTagC2" sablo-tabseq="myModel1">' +
  				'</div>' +
				'</div>' +
			'</div>' +
			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
		'</div>';
  		$scope.myModel1 = 1;
  		$scope.myModel2 = 2;
  		$scope.items = [{tabSeq: 1},{tabSeq: 2}];
  		$scope.$digest();
  		
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('151');
  		
  		//add 1 row
  		$scope.items.push({tabSeq:3});
  		$scope.$digest();

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[2]).children()[0]).attr('tabIndex')).toBe('3');
  		expect($($($(myDiv.children()[0]).children()[2]).children()[1]).attr('tabIndex')).toBe('3');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('151');
  	});
  	
  	it("should apply tabIndex for children correctly in a ngRepeat with tabSeq based on $index", function() {
  		// simulate record view form with 1 portal in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">'
  			+ '    <div class="btn-group" sablo-tabseq="model.tabSeq" sablo-tabseq-config="{container: true, reservedGap: 50}">'
  			+ '       <label sablo-tabseq="$index + 1" class="btn btn-primary" ng-model="model.dataprovider" ng-repeat="item in model.valuelist" btn-radio="\'{{item.realValue}}\'">'
  			+ '             {{item.displayValue}}'
  			+ '       </label>'
  			+ '    </div>'
  			+ '</div>';
  		$scope.model = {};
  		$scope.model.tabSeq = 1;
  		$scope.model.dataprovider = 222;
  		$scope.model.valuelist = [{realValue: 111, displayValue:"One Hundred and Eleven"},
  		                          {realValue: 222, displayValue:"Two Hundred and Twenty-Two"},
  		                          {realValue: 333, displayValue:"Three Hundred and Thirty-Three"}];
  		$scope.$digest();
  		
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();

  		expect(myDiv.attr('tabIndex')).toBe(undefined);
  		expect($($(myDiv.children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe('3');
  		
  		// now disable tabSequences (this is used by modal dialogs) - it must not be called on root, only lower (otherwise re-enable will fail for now)
  		$(myDiv.children()[0]).trigger("disableTabseq");
  		$scope.$digest();
  		expect(myDiv.attr('tabIndex')).toBe(undefined);
  		expect($($(myDiv.children()[0]).children()[0]).attr('tabIndex')).toBe('-1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe('-1');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe('-1');
  		
  		// re-enable them, they should be just as before
  		$(myDiv.children()[0]).trigger("enableTabseq");
  		$scope.$digest();
  		expect(myDiv.attr('tabIndex')).toBe(undefined);
  		expect($($(myDiv.children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe('3');
  		
  	});
  	
  	it("should recalculate indexes if more are needed than reserved, due to adding rows at runtime", function() {
  		// simulate record view form with 1 portal in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
			'<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
				'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
  				'<div name="myOtherTagC1" sablo-tabseq="myModel11">' +
  				'</div>' +
  				'<div name="myOtherTagC2" sablo-tabseq="myModel12">' +
  				'</div>' +
				'</div>' +
			'</div>' +
			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
		'</div>';
  		$scope.myModel1 = 1;
  		$scope.myModel11 = 1;
  		$scope.myModel12 = 1;
  		$scope.myModel2 = 2;
  		$scope.items = [{tabSeq: 1},{tabSeq: 2}];
  		$scope.$digest();
  		
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();

  		expect($(myDiv.children()[0]).children().length).toBe(2); //2 rows in portal
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('151');
  		
  		//add 148 rows
  		for (var i = 3; i <= 150; i++)
  		{
  			$scope.items.push({tabSeq:i});
  		}
  		$scope.$digest();

  		expect($(myDiv.children()[0]).children().length).toBe(150);
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[2]).children()[0]).attr('tabIndex')).toBe('3');
  		expect($($($(myDiv.children()[0]).children()[2]).children()[1]).attr('tabIndex')).toBe('3');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[149]).children()[0]).attr('tabIndex')).toBe('150');
  		expect($($($(myDiv.children()[0]).children()[149]).children()[1]).attr('tabIndex')).toBe('150');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('151');
  		
  		//add one more row - need to recalculate
  		$scope.items.push({tabSeq: 151});
  		$scope.$digest();
  		expect($(myDiv.children()[0]).children().length).toBe(151);
  		expect($($($(myDiv.children()[0]).children()[150]).children()[0]).attr('tabIndex')).toBe('151');
  		expect($($($(myDiv.children()[0]).children()[150]).children()[1]).attr('tabIndex')).toBe('151');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('302');
  		
  		$scope.items.push({tabSeq: 152});
  		$scope.$digest();
  		expect($(myDiv.children()[0]).children().length).toBe(152);
  		expect($($($(myDiv.children()[0]).children()[151]).children()[0]).attr('tabIndex')).toBe('152');
  		expect($($($(myDiv.children()[0]).children()[151]).children()[1]).attr('tabIndex')).toBe('152');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('302');
  	});
  	
  	it("should recalculate indexes if more are needed than reserved", function() {
  		// simulate record view form with 1 portal in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
				  			'<div name="myTag" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 2}">' +
				  				'<div name="myOtherTag" sablo-tabseq="1" sablo-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" sablo-tabseq="myModel1">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" sablo-tabseq="myModel1">' +
					  				'</div>' +
				  				'</div>' +
				  				'<div name="myOtherTag" sablo-tabseq="2" sablo-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" sablo-tabseq="myModel1">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" sablo-tabseq="myModel1">' +
					  				'</div>' +
				  				'</div>' +
				  			'</div>' +
				  			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
			  			'</div>';
  		
  		$scope.myModel1 = 1;
  		$scope.myModel2 = 2;
  		$scope.$digest();
  		
  		var myDiv = $compile(template)($scope);

  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('1');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('2');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('3');
  	});
  		
  	
  	it("should recalculate indexes if more are needed in case of a small reservedGap value", function() {
  		// simulate record view form with 1 portal in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
				  			'<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 2}">' +
				  				'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
					  				'<div name="myOtherTagC1" sablo-tabseq="item.tabSeqC1">' +
					  				'</div>' +
					  				'<div name="myOtherTagC2" sablo-tabseq="item.tabSeqC2">' +
					  				'</div>' +
				  				'</div>' +
				  			'</div>' +
				  			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
			  			'</div>';
  		
  		$scope.myModel1 = 1;
  		$scope.myModel2 = 2;
  		$scope.items = [{tabSeq: 1, tabSeqC1: 1, tabSeqC2: 2},{tabSeq: 2, tabSeqC1: 2, tabSeqC2: 1}];
  		$scope.$digest();
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();
  		
  		expect($(myDiv.children()[0]).children().length).toBe(2); //2 rows in the portal
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[1]).children()[0]).attr('tabIndex')).toBe('4');
  		expect($($($(myDiv.children()[0]).children()[1]).children()[1]).attr('tabIndex')).toBe('3');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('7');
  		
  		//add 1 row
  		$scope.items.push({tabSeq:3, tabSeqC1: 1, tabSeqC2: 2});
  		$scope.$digest();
  		
  		expect($(myDiv.children()[0]).children().length).toBe(3);//3 rows in portal
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[0]).children().attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($($(myDiv.children()[0]).children()[0]).children()[1]).attr('tabIndex')).toBe('2');
  		expect($($(myDiv.children()[0]).children()[2]).attr('tabIndex')).toBe(undefined);
  		expect($($($(myDiv.children()[0]).children()[2]).children()[0]).attr('tabIndex')).toBe('5');
  		expect($($($(myDiv.children()[0]).children()[2]).children()[1]).attr('tabIndex')).toBe('6');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('7');
  	});
  	
  	it("should set correct tabIndexes in tabpanel if a new tab is added", function() {
  		// simulate record view form with 1 tabpanel in body and an element in footer
  		//tabpanel
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
				  			'<tabset name="myTabpanel" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 50}">' +
				  				'<tab ng-repeat="tab in tabs" heading="tab.heading" active="tab.active">' +
				  					'<div ng-include="tab.active ? tab.content : null"></div>'+
				  				'</tab>' +
				  			'</tabset>' +
				  			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
			  			'</div>';
  		$templateCache.put('simple.html', '<div name="myOtherTag" sablo-tabseq="myModel1"></div>');
  		$templateCache.put('portal.html', '<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
			'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
				'<div name="myOtherTagC1" sablo-tabseq="item.tabSeqC1">' +
				'</div>' +
				'<div name="myOtherTagC2" sablo-tabseq="item.tabSeqC2">' +
				'</div>' +
			'</div>' +
		'</div>');
  		$scope.myModel1 = 1;
  		$scope.myModel2 = 2;
  		$scope.tabs = [{active: true, heading: 'Tab 1', content: 'simple.html'}];
  		$scope.items = [{tabSeq: 1, tabSeqC1: 1, tabSeqC2: 2},{tabSeq: 2, tabSeqC1: 2, tabSeqC2: 1}];
  		$scope.$digest();
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();
  		
  		var tabs = myDiv.find('tab');  		
  		expect(tabs.length).toBe(1);
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(tabs[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(tabs[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('51');
  		
  		//add 1 tab
  		//tab indexes should be the same, because the second tab is not active
  		$scope.tabs.push({active: false, heading: 'Tab 2', content: 'portal.html'});
  		$scope.$digest();
  		tabs = myDiv.find('tab');  	
  		var portal = $(tabs[1]).children()[0];
  		expect(tabs.length).toBe(2);
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($(tabs[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(tabs[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($(tabs[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(portal).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('51');
  		
  		$scope.tabs[0].active = false;
  		$scope.tabs[1].active = true;
  		$scope.$digest();
  		portal = $(tabs[1]).children()[0];
  		
  		expect($(tabs[1]).attr('tabIndex')).toBe(undefined);
  		expect($($(tabs[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(tabs[0]).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(portal).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($($(portal).children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('201');
  	});

  	it("should set correct tabIndexes in tabpanel", function() {
  		// simulate record view form with 1 tabpanel in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
				  			'<tabset name="myTabpanel" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 50}">' +
				  				'<tab ng-repeat="tab in tabs" heading="tab.heading" active="tab.active">' +
				  					'<div ng-include="tab.active ? tab.content : null"></div>'+
				  				'</tab>' +
				  			'</tabset>' +
				  			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
			  			'</div>';
  		$templateCache.put('simple.html', '<div name="myOtherTag" sablo-tabseq="myModel1"></div>');
  		$templateCache.put('portal.html', '<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
			'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
				'<div name="myOtherTagC1" sablo-tabseq="item.tabSeqC1">' +
				'</div>' +
				'<div name="myOtherTagC2" sablo-tabseq="item.tabSeqC2">' +
				'</div>' +
			'</div>' +
		'</div>');
  		$scope.myModel1 = 1;
  		$scope.myModel2 = 2;
  		$scope.tabs = [{active: true, heading: 'Tab 1', content: 'portal.html'}, 
  		               {active: false, heading: 'Tab 2', content: 'simple.html'} ];
  		$scope.items = [{tabSeq: 1, tabSeqC1: 1, tabSeqC2: 2},{tabSeq: 2, tabSeqC1: 2, tabSeqC2: 1}];
  		$scope.$digest();
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();
  		
  		var tabs = myDiv.find('tab');  
  		var portal = $(tabs[0]).children()[0];
  		
  		expect(tabs.length).toBe(2);
  		expect(myDiv.children().attr('tabIndex')).toBe(undefined);
  		expect($($($(portal).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($($(portal).children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($(tabs[1]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(tabs[1]).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('201');
  		
  		$scope.tabs[0].active = false;
  		$scope.tabs[1].active = true;
  		$scope.$digest();
  		tabs = myDiv.find('tab');
  		portal = $(tabs[0]).children()[0];
  		
  		var portal = $(tabs[0]).children()[0];
  		expect($(tabs[1]).attr('tabIndex')).toBe(undefined);
  		expect($($($(portal).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($($(portal).children()[0]).children()[0]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($(tabs[1]).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(tabs[1]).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		//still 201; is not changed to a lower value (51)
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('201');
  	});
  	
  	it("should set correct tabIndexes in splitpane", function() {
  		// simulate record view form with 1 splitpane in body and an element in footer
  		var template= '<div sablo-tabseq="1" sablo-tabseq-config="{root: true}">' +
				  			'<bg-splitter name="mySplitpane" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 50}">' +
		  						'<bg-pane ng-include="tabs[0].content" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true}"></bg-pane>'+
		  						'<bg-pane ng-include="tabs[1].content" sablo-tabseq="myModel2" sablo-tabseq-config="{container: true}"></bg-pane>' +
		  					'</bg-splitter>' +
				  			'<div name="myOtherOtherTag" sablo-tabseq="myModel2"></div>' +
			  		  '</div>';
  		$templateCache.put('simple.html', '<div name="myOtherTag" sablo-tabseq="myModel1"></div>');
  		$templateCache.put('portal.html', '<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
			'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
				'<div name="myOtherTagC1" sablo-tabseq="item.tabSeqC1">' +
				'</div>' +
				'<div name="myOtherTagC2" sablo-tabseq="item.tabSeqC2">' +
				'</div>' +
			'</div>' +
		'</div>');
  		$templateCache.put('portal2.html', '<div name="myPortal" sablo-tabseq="myModel1" sablo-tabseq-config="{container: true, reservedGap: 150}">' +
  				'<div ng-repeat="item in items" sablo-tabseq="item.tabSeq" sablo-tabseq-config="{container: true}">' +
  					'<div name="myOtherTagC1" sablo-tabseq="item.tabSeqC1">' +
  					'</div>' +
  					'<div name="myOtherTagC2" sablo-tabseq="item.tabSeqC2">' +
  					'</div>' +
  				'</div>' +
  			'</div>');
  		$scope.tabs = [{content: 'portal.html'}, {content: 'simple.html'} ];
  		$scope.items = [{tabSeq: 1, tabSeqC1: 1, tabSeqC2: 2},{tabSeq: 2, tabSeqC1: 2, tabSeqC2: 1}];
  		$scope.myModel1 = 1;
  		$scope.myModel2 = 2;
  		$scope.$digest();
  		var myDiv = $compile(template)($scope);
  		$scope.$digest();  	
  		
  		var panes = myDiv.find('bg-pane');
  		var portal = $(panes[0]).children()[0];
  		
  		expect(panes.length).toBe(2);
  		expect($(panes[0]).attr('tabIndex')).toBe(undefined);
  		expect($($(portal).children()[0]).attr('tabIndex')).toBe(undefined);
  		expect($($($(portal).children()[0]).children()[0]).attr('tabIndex')).toBe('1');
  		expect($($(panes[1]).children()[0]).attr('tabIndex')).toBe('151');
  		expect($(myDiv.children()[1]).attr('tabIndex')).toBe('201');
  	});

}); 
