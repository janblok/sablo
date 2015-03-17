module.exports = function(config){
  config.set({
    basePath : '.',
    files : [
       'lib/jquery.js',
       'lib/angular_1.4.0b5.js',
       'lib/angular-mocks_1.4.0b5.js',
       'lib/angular-webstorage.js',
       '../META-INF/resources/sablo/js/*.js',
       'lib/phantomjs.polyfill.js',
       './test/**/*.js',	  
    ],

    frameworks: ['jasmine'],
    browsers : ['PhantomJS'],

    /*plugins : [    <- not needed since karma loads by default all sibling plugins that start with karma-*
            'karma-junit-reporter',
            'karma-chrome-launcher',
            'karma-firefox-launcher',
            'karma-script-launcher',
            'karma-jasmine'
            ],*/
    singleRun: true,
    //autoWatch : true,
    reporters: ['dots', 'junit'],
    junitReporter: {
          outputFile: 'test-results.xml'
    }
  /*,  alternative format
    junitReporter : {
      outputFile: 'test_out/unit.xml',
      suite: 'unit'
    }*/
  });
};
