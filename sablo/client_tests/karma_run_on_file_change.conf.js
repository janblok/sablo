var karmaBaseConfig = require('./karma.conf.js');

module.exports = function(config) {
	// apply basic configuration
	karmaBaseConfig(config);
	
	// change what is needed to nicely run them locally
	config.singleRun = false;
	config.browserNoActivityTimeout = 999999;
    config.browsers =['Chrome','Firefox'];
//    config.browsers =['Chrome'];
//    config.browsers =['PhantomJS'];
//    config.browsers =['Firefox'];
}
