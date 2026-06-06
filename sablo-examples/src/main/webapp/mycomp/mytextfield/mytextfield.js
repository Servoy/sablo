angular.module('mytextfield',[]).directive('mytextfield', function() {  
    return {
      restrict: 'E',
      transclude: true,
      scope: {
			model: "=fieldModel"
		},
      controller: function($scope, $element, $attrs) {
		  },
      templateUrl: 'mycomp/mytextfield/mytextfield.html',
      replace: true
    };
  });
