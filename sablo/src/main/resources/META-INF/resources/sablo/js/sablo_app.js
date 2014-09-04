angular.module('sabloApp', ['webSocketModule']).config(function($controllerProvider) {
}).controller("SabloController", function($scope, $rootScope, $sabloApplication) {
	$scope.windowTitle = 'RAGTEST de titel!!';
}).factory('$sabloApplication', function ($rootScope, $window, $webSocket) {
	  
	$window.alert('RAGTEST connect')
	   var wsSession = $webSocket.connect('', ['todosessionid'])
	   wsSession.onMessageObject = function (msg, conversionInfo) {
		  
		   alert('RAGTEST message: ' + msg)
	   };
	   wsSession.onopen = function (evt) {
		   
		   alert('RAGTEST connected: ' + evt)
	   };
	    
	   return {
		   callService: function(serviceName, methodName, argsObject, async) {
			   return wsSession.callService(serviceName, methodName, argsObject, async)
		   }
	   }
}).run(function($window) {
	$window.alert('RAGTEST sablo app!')
})
