angular.module('sabloUtils2',[])
.factory("$sabloUtils2",function($rootScope) {
	
	// internal function
	function getPropByStringPath(o, s) {
		s = s.replace(/\[(\w+)\]/g, '.$1'); // convert indexes to properties
		s = s.replace(/^\./, '');           // strip a leading dot
		var a = s.split('.');
		while (a.length) {
			var n = a.shift();
			if (n in o) {
				o = o[n];
			} else {
					return;
			}
			return o;
		}
	}
	
	function testKeyPressed(e, keyCode) 
	{
	     var code;
	     
	     if (!e) e = window.event;
	     if (!e) return false;
	     if (e.keyCode) code = e.keyCode;
	     else if (e.which) code = e.which;
	     return code==keyCode;
	}
	
	// expression for angular scope.$watch that can watch 1 item multiple levels deep in an object
	function getInDepthWatchExpression(parentObj, propertyNameOrArrayOfNestedNames) {
		var expression;
		if ($.isArray(propertyNameOrArrayOfNestedNames)) {
			expression = function() {
				var r = parentObj;
				var i = 0;
				while (i < propertyNameOrArrayOfNestedNames.length && angular.isDefined(r)) {
					r = r[propertyNameOrArrayOfNestedNames[i]];
					i++;
				}
				return r;
			}
		}
		else expression = function() { return parentObj[propertyNameOrArrayOfNestedNames] };

		return expression;
	};
	
	function getInDepthSetter(parentObj, propertyNameOrArrayOfNestedNames) {
		var setterFunc;
		if ($.isArray(propertyNameOrArrayOfNestedNames)) {
			setterFunc = function(newValue) {
				var r = parentObj;
				var i = 0;
				while (i < propertyNameOrArrayOfNestedNames.length - 1 && angular.isDefined(r)) {
					r = r[propertyNameOrArrayOfNestedNames[i]];
					i++;
				}
				if (angular.isDefined(r)) r[propertyNameOrArrayOfNestedNames[propertyNameOrArrayOfNestedNames.length - 1]] = newValue;
				// else auto-create path?
			}
		}
		else setterFunc = function(newValue) { parentObj[propertyNameOrArrayOfNestedNames] = newValue };

		return setterFunc;
	};
	
	return{

		getEventArgs: function(args,eventName)
		{
			var newargs = []
			for (var i in args) {
				var arg = args[i]
				if (arg && arg.originalEvent) arg = arg.originalEvent;
				if(arg  instanceof MouseEvent ||arg  instanceof KeyboardEvent){
					var $event = arg;
					var eventObj = {}
					var modifiers = 0;
					if($event.shiftKey) modifiers = modifiers||$swingModifiers.SHIFT_DOWN_MASK;
					if($event.metaKey) modifiers = modifiers||$swingModifiers.META_DOWN_MASK;
					if($event.altKey) modifiers = modifiers|| $swingModifiers.ALT_DOWN_MASK;
					if($event.ctrlKey) modifiers = modifiers || $swingModifiers.CTRL_DOWN_MASK;

					eventObj.type = 'event'; 
					eventObj.eventName = eventName; 
					eventObj.modifiers = modifiers;
					eventObj.timestamp = $event.timeStamp;
					eventObj.x= $event.pageX;
					eventObj.y= $event.pageY;
					arg = eventObj
				}
				else if (arg instanceof Event || arg instanceof $.Event) {
					var eventObj = {}
					eventObj.type = 'event'; 
					eventObj.eventName = eventName; 
					eventObj.timestamp = arg.timeStamp;
					arg = eventObj
				}
				newargs.push(arg)
			}
			return newargs;
		},

		/** this function can be used in filters .It accepts a string jsonpath the property to test for null. 
    	Example: "item in  model.valuelistID  | filter:notNullOrEmpty('realValue')"*/
		notNullOrEmpty : function (propPath){
			return function(item) {
				var propByStringPath = getPropByStringPath(item,propPath); 
				return !(propByStringPath === null || propByStringPath == '')
			}
		},
	    autoApplyStyle: function(scope,element,modelToWatch,cssPropertyName){
				      	  scope.$watch(modelToWatch,function(newVal,oldVal){
				      		  if(!newVal) {element.css(cssPropertyName,''); return;}
				      		  if(typeof newVal != 'object'){ //for cases with direct values instead of json string background and foreground
				      			var obj ={}
				      			obj[cssPropertyName] = newVal;
				      			newVal = obj;
				      		  } 
				    	      element.css(cssPropertyName,'')
				    		  element.css(newVal)
				    	  })
	    				},
		getEventHandler: function($parse,scope,handler)
		{
			var functionReferenceString = handler;
			if (functionReferenceString)
			{
				var index = functionReferenceString.indexOf('(');
				if (index != -1) functionReferenceString = functionReferenceString.substring(0,index);
				if( scope.$eval(functionReferenceString) ) {
				   return $parse(handler);
				}
			}
			return null;
		},
		attachEventHandler: function($parse,element,scope,handler,domEvent, filterFunction,timeout) {
			var fn = this.getEventHandler($parse,scope,handler)
			if (fn)
			{
				element.on(domEvent, function(event) {
					if (!filterFunction || filterFunction(event)) {
						if (timeout)
						{
							setTimeout(function(){scope.$apply(function() {
								fn(scope, {$event:event});
							});},timeout);
						}
						else
						{
							scope.$apply(function() {
								fn(scope, {$event:event});
							});
						}
						return false;
					}
				}); 
			}
		},
		testEnterKey: function(e) 
		{
			return testKeyPressed(e,13);
		},
		bindTwoWayObjectProperty: function (a, propertyNameA, b, propertyNameB, useObjectEquality, scope) {
			var toWatchA = getInDepthWatchExpression(a, propertyNameA);
			var toWatchB = getInDepthWatchExpression(b, propertyNameB);
			var setA = getInDepthSetter(a, propertyNameA);
			var setB = getInDepthSetter(b, propertyNameB);

			if (!scope) scope = $rootScope;
			return [
			        scope.$watch(toWatchA, function (newValue, oldValue, scope) {
			        	if (newValue !== oldValue) setB(newValue);
			        }, useObjectEquality),
			        scope.$watch(toWatchB, function (newValue, oldValue, scope) {
			        	if (newValue !== oldValue) setA(newValue);
			        }, useObjectEquality)
			];
		},

		/**
		 * Receives variable arguments. First is the object obj and the others (for example a, b, c) are used
		 * to return obj[a][b][c] making sure if for example b is not there it returns undefined instead of
		 * throwing an exception.
		 */
		getInDepthProperty: function() {
			if (arguments.length == 0) return undefined;
			
			var ret = arguments[0];
			var i;
			for (i = 1; (i < arguments.length) && (ret !== undefined && ret !== null); i++) ret = ret[arguments[i]];
			if (i < arguments.length) ret = undefined;
			
			return ret;
			
			if (!formStatesConversionInfo[formName]) formStatesConversionInfo[formName] = {};
			if (!formStatesConversionInfo[formName][beanName]) formStatesConversionInfo[formName][beanName] = {};
		},

		/**
		 * Receives variable arguments. First is the object obj and the others (for example a, b, c) are used to
		 * return obj[a][b][c] making sure that if any does not exist or is null (for example b) it will be set to {}.
		 */
		getOrCreateInDepthProperty: function() {
			if (arguments.length == 0) return undefined;
			
			var ret = arguments[0];
			if (ret == undefined || ret === null || arguments.length == 1) return ret;
			var p;
			var i;
			for (i = 1; i < arguments.length; i++) {
				p = ret;
				ret = ret[arguments[i]];
				if (ret === undefined || ret === null) {
					ret = {};
					p[arguments[i]] = ret;
				}
			}
			
			return ret;
		}
	}
}).value("$swingModifiers" ,{
    SHIFT_MASK : 1,
    CTRL_MASK : 2,
    META_MASK : 4,
    ALT_MASK : 8,
    ALT_GRAPH_MASK : 32,
    BUTTON1_MASK : 16,
    BUTTON2_MASK : 8,
    META_MASK : 4,
    SHIFT_DOWN_MASK : 64,
    CTRL_DOWN_MASK : 128,
    META_DOWN_MASK : 256,
    ALT_DOWN_MASK : 512,
    BUTTON1_DOWN_MASK : 1024,
    BUTTON2_DOWN_MASK : 2048,
    DOWN_MASK : 4096,
    ALT_GRAPH_DOWN_MASK : 8192
})
