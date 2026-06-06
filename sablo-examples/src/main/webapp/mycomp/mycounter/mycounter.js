angular.module('mycounter',[]).directive('mycounter', function() {  
	return {
		restrict: 'E',
		transclude: true,
		scope: {
			model: "=counterModel",
			api: "=sabloApi"
		},
		link:function($scope) {
			
			// fill in the api defined in the spec file
			$scope.api.increment = function(inc) {
				return $scope.model.n += inc;
			};
		},
		templateUrl: 'mycomp/mycounter/mycounter.html',
		replace: true
	};
});