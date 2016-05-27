angular.module('sampleApp', ['sabloApp', '$sabloService', 'propsToWatch', 'webStorageModule', 'mylabel', 'mybutton', 'mytextfield', 'mycounter']).config(function() {
}).controller("SampleController", function($scope, $rootScope, $window, $sabloApplication, $webSocket, webStorage) {
	$scope.windowTitle = 'Sample Application';
	$scope.getCurrentFormUrl = function() {
		return $sabloApplication.getCurrentFormUrl()
	}
	
	// $window.alert('Connecting...');
	$sabloApplication.connect()
	.onMessageObject(function (msg, conversionInfo) {
		   if (msg.sessionid) {
			   webStorage.session.add("sessionid", msg.sessionid);
		   }
		   if (msg.windowid) {
			   webStorage.session.add("windowid", msg.windowid);
		   }
	   });
})
.run(function($window) {
	// $window.alert('Sample Startup');
});
