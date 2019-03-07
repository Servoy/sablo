module.exports = function(config){
  config.set({
    basePath : '.',
    files : [
		// libraries for testing and angular
		'lib/jquery.js',
		'lib/phantomjs.polyfill.js',
		'lib/angular.js',
		'lib/angular-mocks.js',
		'lib/angular-webstorage.js',

		// sablo scripts
		'../META-INF/resources/sablo/js/*.js',
		
		// test scripts
		'test/**/*.js'
    ],

    frameworks: ['jasmine'],
    browsers : ['PhantomJS'],
    //browsers : ['PhantomJS', 'Chrome', 'Firefox', 'IE'],//

    /*plugins : [    <- not needed since karma loads by default all sibling plugins that start with karma-*
            'karma-junit-reporter',
            'karma-chrome-launcher',
            'karma-firefox-launcher',
            'karma-script-launcher',
            'karma-jasmine'
            ],*/
    singleRun: true,
    //singleRun: false,
    //browserNoActivityTimeout:999999,
    //autoWatch : true,
    reporters: ['dots', 'junit'],
    junitReporter: {
          outputFile: '../../target/protractor-reports/TEST-phantomjs-karma.xml'
    }
  /*,  alternative format
    junitReporter : {
      outputFile: 'test_out/unit.xml',
      suite: 'unit'
    }*/
  });
};
