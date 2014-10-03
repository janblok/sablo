angular.module('sablo', ['webSocketModule', 'sabloUtils2']).config(function($controllerProvider) {
}).factory('$sabloApplication', function ($rootScope, $window, $webSocket, $sabloUtils, $sabloUtils2, $sabloConverters, $swingModifiers) {
	  
	   var wsSession = null;
	   
	   // formName:[beanname:{property1:1,property2:"test"}] needs to be synced to and from server
	   // this holds the form model with all the data, per form is this the "synced" view of the the IFormUI on the server 
	   // (3 way binding)
	   var formStates = {};
	   var formStatesConversionInfo = {};
	 
	   var getChangeNotifier = function(formName, beanName) {
		   return function() {
			   // will be called by the custom property when it needs to send changes server size
			   var beanModel = formStates[formName].model[beanName];
			   sendChanges(beanModel, beanModel, formName, beanName);
		   }
	   }

	   function getSession() {
		   if (wsSession == null) throw "Session is not created yet, first call connect()";
		   return wsSession;
	   }
	   
	   function connect() {
			$window.alert('RAGTEST connect')
			    wsSession = $webSocket.connect('', ['todosessionid'])
			   wsSession.onMessageObject = function (msg, conversionInfo) {
				   // data got back from the server
				   for(var formname in msg.forms) {
					   // current model
					   var formState = formStates[formname];
					   // if the formState is on the server but not here anymore, skip it. 
					   // this can happen with a refresh on the browser.
					   if (!formState) continue;
					   var formModel = formState.model;
					   var layout = formState.layout;
					   var newFormData = msg.forms[formname];
					   var newFormProperties = newFormData['']; // f form properties
					   var newFormConversionInfo = (conversionInfo && conversionInfo.forms && conversionInfo.forms[formname]) ? conversionInfo.forms[formname] : undefined;
		
					   if(newFormProperties) {
						   if (newFormConversionInfo && newFormConversionInfo['']) newFormProperties = $sabloConverters.convertFromServerToClient(newFormProperties, newFormConversionInfo[''], formModel[''], formState.getScope());
						   if (!formModel['']) formModel[''] = {};
						   for(var p in newFormProperties) {
							   formModel[''][p] = newFormProperties[p]; 
						   } 
					   }
					   
					   var watchesRemoved = formState.removeWatches(newFormData);
					   try {
						   for (var beanname in newFormData) {
							   // copy over the changes, skip for form properties (beanname empty)
							   if (beanname != '') {
								   if (formModel[beanname]!= undefined && (newFormData[beanname].size != undefined ||  newFormData[beanname].location != undefined)) {	
									   //size or location were changed at runtime, we need to update components with anchors
									   newFormData[beanname].anchors = formModel[beanname].anchors;
								   }
			
								   var newBeanConversionInfo = newFormConversionInfo ? newFormConversionInfo[beanname] : undefined;
								   var beanConversionInfo = newBeanConversionInfo ? $utils.getOrCreateInDepthProperty(formStatesConversionInfo, formname, beanname) : undefined; // we could do a get instead of undefined, but normally that value is not needed if the new conversion info is undefined
								   applyBeanData(formModel[beanname], layout[beanname], newFormData[beanname], formState.properties.designSize, getChangeNotifier(formname, beanname), beanConversionInfo, newBeanConversionInfo, formState.getScope());
								   for (var defProperty in deferredProperties) {
									   for(var key in newFormData[beanname]) {
										   if (defProperty == (formname + "_" + beanname + "_" + key)) {
											   deferredProperties[defProperty].resolve(newFormData[beanname][key]);
											   delete deferredProperties[defProperty];
										   }
									   }
								   } 
							   }
						   }
						   if(deferredformStates[formname]){
							   deferredformStates[formname].resolve(formStates[formname])
							   delete deferredformStates[formname]
						   }
					   }
					   finally {
						   if (watchesRemoved)
							   formState.addWatches(newFormData);
						   else if (msg.initialdatarequest)
						   		formState.addWatches();
						   formState.getScope().$digest();
						   if (formState.model.svy_default_navigator) {
							   // this form has a default navigator. also make sure those watches are triggered.
							  var controllerElement = angular.element('[ng-controller=DefaultNavigatorController]');
							  if (controllerElement && controllerElement.scope()) {
								  controllerElement.scope().$digest();
							  }
						   }
					   }
				   }
		
				   if (conversionInfo && conversionInfo.call) msg.call = $sabloConverters.convertFromServerToClient(msg.call, conversionInfo.call, undefined, undefined);
				   if (msg.call) {
					   // {"call":{"form":"product","element":"datatextfield1","api":"requestFocus","args":[arg1, arg2]}, // optionally "viewIndex":1 
					   // "{ conversions: {product: {datatextfield1: {0: "Date"}}} }
					   var call = msg.call;
					   var formState = formStates[call.form];
					   if (call.viewIndex != undefined) {
						   var funcThis = formState.api[call.bean][call.viewIndex]; 
						   if (funcThis)
						   {
							   var func = funcThis[call.api];
						   }
						   else
						   {
							   console.warn("cannot call " + call.api + " on " + call.bean + " because viewIndex "+ call.viewIndex +" api is not found")
						   }
					   }
					   else if (call.propertyPath != undefined)
					   {
						   // handle nested components; the property path is an array of string or int keys going
						   // through the form's model starting with the root bean name, then it's properties (that could be nested)
						   // then maybe nested child properties and so on 
						   var obj = formState.model;
						   var pn;
						   for (pn in call.propertyPath) obj = obj[call.propertyPath[pn]];
						   var func = obj.api[call.api];
					   }
					   else {
						   var funcThis = formState.api[call.bean];
						   var func = funcThis[call.api];
					   }
					   if (!func) {
						   // if setFindMode not present, set editable/readonly state
						   if (call.api != "setFindMode") 
						   {
							   console.warn("bean " + call.bean + " did not provide the api: " + call.api)
						   }
						   else
						   {  // TODO: move to servoy (set as default implementation for setFind
							   if (call.args[0])
							   {
								   formState.model[call.bean].readOnlyBeforeFindMode = formState.model[call.bean].readOnly;
								   formState.model[call.bean].readOnly = true;
							   }
							   else
							   {
								   formState.model[call.bean].readOnly = formState.model[call.bean].readOnlyBeforeFindMode;
							   }
							   formState.getScope().$digest();
						   }
						   return;
					   }
					   try {
						   return func.apply(funcThis, call.args)
					   } finally {
						   formState.getScope().$digest();
					   }
				   }
				   if (msg.sessionid) {
					   webStorage.session.add("sessionid",msg.sessionid);
				   }
				   if (msg.windowid) {
					   $solutionSettings.windowName = msg.windowid;
					   webStorage.session.add("windowid",msg.windowid);
				   }
			   };
	   }
	   
	   function callService(serviceName, methodName, argsObject, async) {
		   return getSession().callService(serviceName, methodName, argsObject, async)
	   }
	   
	   function requestFormData(formName, model) {
		   
		   //TODO: apply bean data
		   return getSession().callService('formService', 'requestData', {formname:formName}, false).then(
				   function(data) {
					   for (var i in data) {
						   for (j in data[i]) model[i][j] = data[i][j]
					   }
				   });
	   }
	   
	   function getExecutor(formName) {
		   return {
			   on: function(beanName,eventName,property,args,rowId) {
				   // this is onaction, onfocuslost which is really configured in the html so it really 
				   // is something that goes to the server
				   var newargs = $sabloUtils2.getEventArgs(args,eventName);
				   var data = {}
				   if (property) {
					   data[property] = formStates[formName].model[beanName][property];
				   }
				   var cmd = {cmd:'event',formname:formName,beanname:beanName,event:eventName,args:newargs,changes:data}
				   if (rowId) cmd.rowId = rowId
				   return getSession().sendDeferredMessage(cmd)
			   },
		   }
	   }
	   
	   function sendChanges(now, prev, formname, beanname) {
		   var changes = getComponentChanges(now, prev, $sabloUtils2.getInDepthProperty(formStatesConversionInfo, formname, beanname),
				 null /*formStates[formname].layout[beanname]*/, null /*formStates[formname].properties.designSize*/, null /*getChangeNotifier(formname, beanname)*/);
		   if (Object.getOwnPropertyNames(changes).length > 0) {
			   getSession().sendMessageObject({cmd:'datapush',formname:formname,beanname:beanname,changes:changes})
		   }
	   }
	   
	   function getComponentChanges(now, prev, beanConversionInfo, beanLayout, parentSize, changeNotifier) {
		   // first build up a list of all the properties both have.
		   var fulllist = $sabloUtils.getCombinedPropertyNames(now,prev);
		   var changes = {}, prop;

		   for (prop in fulllist) {
			   var changed = false;
			   if (!prev) {
				   changed = true;
			   }
			   else if (now[prop] && now[prop][$sabloConverters.INTERNAL_IMPL] && now[prop][$sabloConverters.INTERNAL_IMPL].isChanged)
			   {
				   changed = now[prop][$sabloConverters.INTERNAL_IMPL].isChanged();
			   }
			   else if (prev[prop] !== now[prop]) {
				   if (typeof now[prop] == "object") {
					   if ($sabloUtils.isChanged(now[prop], prev[prop], beanConversionInfo ? beanConversionInfo[prop] : undefined)) {
						   changed = true;
					   }
				   } else {
					   changed = true;
				   }
			   }
			   if (changed) {
				   changes[prop] = now[prop];
			   }
		   }
		   if (changes.location || changes.size || changes.visible || changes.anchors) {
			   if (beanLayout) {
				   applyBeanData(now /*formStates[formname].model[beanname]*/, beanLayout, changes, parentSize, changeNotifier);
			   }
		   }
		   for (prop in changes) {
			   if (beanConversionInfo && beanConversionInfo[prop]) changes[prop] = $sabloConverters.convertFromClientToServer(changes[prop], beanConversionInfo[prop], prev ? prev[prop] : undefined);
			   else changes[prop] = $sabloUtils.convertClientObject(changes[prop])
		   }
		   return changes;
	   }
	   
	   var applyBeanData = function(beanModel, beanLayout, beanData, containerSize, changeNotifier, beanConversionInfo, newConversionInfo, componentScope) {
		   if (newConversionInfo) { // then means beanConversionInfo should also be defined - we assume that
			   // beanConversionInfo will be granularly updated in the loop below
			   // (to not drop other property conversion info when only one property is being applied granularly to the bean)
			   beanData = $sabloConverters.convertFromServerToClient(beanData, newConversionInfo, beanModel, componentScope);
		   }

		   for(var key in beanData) {
			   // remember conversion info for when it will be sent back to server - it might need special conversion as well
			   if (newConversionInfo && newConversionInfo[key]) {
				   // if the value changed and it wants to be in control of it's changes, or if the conversion info for this value changed (thus possibly preparing an old value for being change-aware without changing the value reference)
				   if ((beanModel[key] !== beanData[key] || beanConversionInfo[key] !== newConversionInfo[key])
						   	&& beanData[key] && beanData[key][$sabloConverters.INTERNAL_IMPL] && beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier) {
					   beanData[key][$sabloConverters.INTERNAL_IMPL].setChangeNotifier(changeNotifier);
				   }
				   beanConversionInfo[key] = newConversionInfo[key];
			   }

			   // also make location and size available in model
			   beanModel[key] = beanData[key];
		   }

//		   //beanData.anchors means anchors changed or must be initialized
//		   if(beanData.anchors && containerSize && $solutionSettings.enableAnchoring) {
//			   var anchoredTop = (beanModel.anchors & $anchorConstants.NORTH) != 0; // north
//			   var anchoredRight = (beanModel.anchors & $anchorConstants.EAST) != 0; // east
//			   var anchoredBottom = (beanModel.anchors & $anchorConstants.SOUTH) != 0; // south
//			   var anchoredLeft = (beanModel.anchors & $anchorConstants.WEST) != 0; //west
//
//			   var runtimeChanges = beanData.size != undefined || beanData.location != undefined;
//
//			   if (!anchoredLeft && !anchoredRight) anchoredLeft = true;
//			   if (!anchoredTop && !anchoredBottom) anchoredTop = true;
//
//			   if (anchoredTop)
//			   {
//				   if (beanLayout.top == undefined || runtimeChanges && beanModel.location != undefined) beanLayout.top = beanModel.location.y + 'px';
//			   }
//			   else delete beanLayout.top;
//
//			   if (anchoredBottom)
//			   {
//				   if (beanLayout.bottom == undefined) {
//					   beanLayout.bottom = containerSize.height - beanModel.location.y - beanModel.size.height;
//					   if(beanModel.offsetY) {
//						   beanLayout.bottom = beanLayout.bottom - beanModel.offsetY;
//					   }
//					   beanLayout.bottom = beanLayout.bottom + "px";
//				   }
//			   }
//			   else delete beanLayout.bottom;
//
//			   if (!anchoredTop || !anchoredBottom) beanLayout.height = beanModel.size.height + 'px';
//			   else delete beanLayout.height;
//
//			   if (anchoredLeft)
//			   {
//				   if ( $solutionSettings.ltrOrientation)
//				   {
//					   if (beanLayout.left == undefined || runtimeChanges && beanModel.location != undefined)
//					   {	
//						   beanLayout.left =  beanModel.location.x + 'px';
//					   }
//				   }
//				   else
//				   {
//					   if (beanLayout.right == undefined || runtimeChanges && beanModel.location != undefined)
//					   {	
//						   beanLayout.right =  beanModel.location.x + 'px';
//					   }
//				   }
//			   }
//			   else if ( $solutionSettings.ltrOrientation)
//			   {
//				   delete beanLayout.left;
//			   }
//			   else
//			   {
//				   delete beanLayout.right;
//			   }
//
//			   if (anchoredRight)
//			   {
//				   if ( $solutionSettings.ltrOrientation)
//				   {
//					   if (beanLayout.right == undefined) beanLayout.right = (containerSize.width - beanModel.location.x - beanModel.size.width) + "px";
//				   }
//				   else
//				   {
//					   if (beanLayout.left == undefined) beanLayout.left = (containerSize.width - beanModel.location.x - beanModel.size.width) + "px";
//				   }
//			   }
//			   else if ( $solutionSettings.ltrOrientation)
//			   {
//				   delete beanLayout.right;
//			   }
//			   else
//			   {
//				   delete beanLayout.left;
//			   }
//
//			   if (!anchoredLeft || !anchoredRight) beanLayout.width = beanModel.size.width + 'px';
//			   else delete beanLayout.width;
//		   }
//
//		   //we set the following properties iff the bean doesn't have anchors
//		   if (!beanModel.anchors || !$solutionSettings.enableAnchoring)
//		   {
//			   if (beanModel.location)
//			   {
//				   if ( $solutionSettings.ltrOrientation)
//				   {
//					   beanLayout.left = beanModel.location.x+'px';
//				   }
//				   else
//				   {
//					   beanLayout.right = beanModel.location.x+'px';
//				   }
//				   beanLayout.top = beanModel.location.y+'px';
//			   }
//
//			   if (beanModel.size)
//			   {
//				   beanLayout.width = beanModel.size.width+'px';
//				   beanLayout.height = beanModel.size.height+'px';
//			   }
//		   }
//
//		   if (beanModel.visible != undefined)
//		   {
//			   if (beanModel.visible == false)
//			   {
//				   beanLayout.display = 'none';
//			   }
//			   else
//			   {
//				   delete beanLayout.display;
//			   }
//		   }
	   }

	   
	   var initFormState = function(formName, beanDatas, formProperties, formScope) {
		   var state = formStates[formName];
		   // if the form is already initialized or if the beanDatas are not given, return that 
		   if (state != null || !beanDatas) return state; 

		   // init all the objects for the beans.
		   var model = {};
		   var api = {};
		   var layout = {};

		   state = formStates[formName] = { model: model, api: api, layout: layout,
				   style: {                         
					   left: "0px",
					   top: "0px",
					   minWidth : formProperties.size.width + "px",
					   minHeight : formProperties.size.height + "px",
					   right: "0px",
					   bottom: "0px",
					   border: formProperties.border},
					   properties: formProperties};

		   for(var beanName in beanDatas) {
			   // initialize with design nara
			   model[beanName] = {};
			   api[beanName] = {};
			   layout[beanName] = { position: 'absolute' }
			   
			   var newBeanConversionInfo = beanDatas[beanName].conversions;
			   var beanConversionInfo = newBeanConversionInfo ? $utils.getOrCreateInDepthProperty(formStatesConversionInfo, formName, beanName) : undefined; // we could do a get instead of undefined, but normally that value is not needed if the new conversion info is undefined
			   
			   applyBeanData(model[beanName], layout[beanName], beanDatas[beanName], formProperties.designSize, getChangeNotifier(formName, beanName), beanConversionInfo, newBeanConversionInfo, formScope)
		   }

		   return state;
	   };

	   
	   // api
	   return {
		   connect: connect,
		   callService: callService,
		   getExecutor: getExecutor,
		   requestFormData: requestFormData,
		   sendChanges: sendChanges,
		   initFormState: initFormState
	   }
	   
}).run(function($window) {
	$window.alert('RAGTEST sablo app!')
})
