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
    browsers: ['ChromeHeadless'],
    //browsers : ['PhantomJS', 'Chrome', 'Firefox', 'IE'],//

    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('@chiragrupani/karma-chromium-edge-launcher'),
      require('karma-coverage'),
      require('karma-junit-reporter')
    ],
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
