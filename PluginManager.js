/**
 * plugin manager.
 * 
 * To use this PluginManager, following additional variables are needed:
 * 
 * currentUserName : file transfer needs this global variable
 * language : file transfer needs this global variable
 * 
 * @type PluginManager
 * @author qiaomingkui
 */

var PluginManager = (function() {
	var NAME  = 'XeXtension',
			PORTS = [ 20052, 26126, 22862 ],
			OPERATORS = {
				echoBack               : 1,
				fileBrowser            : 2,
				fileTransfer           : 3,
				runApp                 : 4,
				versionInfo            : 5
			},
			RETURN_CODES = {
				OPERATION_SUCCEED      : 0,
				OPERATION_UNCOMPLETED  : 1,
				UNKNOWN_ERROR          : -1,
				UNKNOWN_OPERATOR       : 101,
				UNSUPPORTED_METHOD     : 102,
				UNKNOWN_ID             : 103,
				UNSUPPORT_OPERATION    : 104
			};

	var _init    		  = false,
			latestVersion = {
				ver : '1.0',
				compatibility : '1.0' // inclusive
			},
			port          = -1,
			url           = null;

	// (private)
	function init() {
		function callback(resp, status, xhr, ex) {
			if (resp && resp.returnCode === RETURN_CODES.OPERATION_SUCCEED) {
				var verInfo = resp.extraData;
				if (verInfo && verInfo.name === NAME) {
					_init = true;
				} else {
					_sendRequest();
				}
			} else {
				_sendRequest();
			}
		}

		var i = 0;
		function _sendRequest() {
			if (i < PORTS.length) {
				port = PORTS[i];
				url = 'http://localhost:' + port + '/?jsoncallback=?';
				i++;
				sendRequest(OPERATORS.versionInfo, null, null, callback);
			} else {
				alert('It seems XeXtension hasn\'t been installed on your system'); // TODO show error info and download url
			}
		}

		_sendRequest();
	}

	// (private)
	function sendRequest(op, id, params, callback, scope) {
		var data = {
			op : op
		};
		if (id != null) data.reqId = id;
		if (params) {
			for (var k in params) {
				data[k] = params[k];
			}
		}

		// send request
		var jqXHR = $.getJSON(url, data, callback);

		jqXHR.fail(function(jqXHR, status, ex) {
			/*
			 * in ie8, if service is down, the arguments will be:
			 * 
			 * status: 'parsererror'
			 * ex.description: 'xxxx was not called' (xxxx is the name of jsoncallback function)
			 * ex.message: (same as ex.description)
			 * ex.name: 'Error'
			 * 
			 * Note: in ie8, jquery will do those clean jobs when request failed
			 */
			if (_isFunction(callback)) {
				callback.call(scope, null, status, jqXHR, ex);
			} 
		});

		// in chrome and some other browsers, if service is down, the jqXHR's fail callback won't be called
		// in other words, getJSON request failed silently.
		// thus we must capture errors when request failed in this way.
		// (JSONP in IE 8 succeeds even though service is down and thus request is failed)
		var head = document.head || $('head')[0] || document.documentElement; // copy from jquery
		var script = $(head).find('script')[0];
		var tOnError = script.onerror;
		script.onerror = function(evt) {
			// do clean
			// delete script node
			if (script.parentNode) {
				script.parentNode.removeChild(script);
			}
			// delete jsonCallback global function
			var src = script.src || '';
			var idx = src.indexOf('jsoncallback=');
			if (idx != -1) {
				var idx2 = src.indexOf('&');
				if (idx2 == -1) {
					idx2 = src.length;
				}
				var jsonCallback = src.substring(idx + 13, idx2);
				if (jsonCallback) {
					delete window[jsonCallback];
				}
			}

			// 
			if (_isFunction(callback)) {
				callback.call(scope, null, 'error', jqXHR, {
					description: 'error occurs when send request',
					message: 'error occurs when send request',
					name: 'Error'
				});
			}
		};
	}

	// (private)
	function _isFunction(func) {
		return typeof(func) === 'function';
	}

	// (private)
	function _checkInit(callback, scope) {
		if (!_init) {
			if (_isFunction(callback)) {
				callback.call(scope, null, 'error', null, {
					description : 'PluginManager has not been initialized',
					message     : 'PluginManager has not been initialized',
					name        : 'Error'
				});
			}
			return false;
		}

		return true;
	}

	/**
	 * return if PluginManager has been initialized.
	 */
	function isInit() {
		return _init;
	}

	/**
	 * get version info of XeXtension. 
	 * 
	 * @param callback (optional) callback being called while request either succeeds or fails.
	 * 
	 * 		callback has following parameters:
	 * 
	 * 			resp: response data, which is a json object and contains the following attributes:
	 * 					operator  : the operator of version info request
	 * 					reqId     : always null
	 * 					respId    : the response id
	 * 					returnCode: returnCode (0 succeeds, or else failed)
	 *          exception : exception message (if any)
	 *          extraData : version detail info, which consists of:
	 *          	name    : XeXtension's name
	 *          	version : current version of XeXtension
	 *          	copyright: copyright info
	 * 
	 * 			status: the final status of this request (String)
	 * 			jqXHR: jqXHR object
	 * 			ex: the exception object, if error occurs when send request.
	 * 					It contains a description, a message and a name attributes.
	 * 					(Note: such error occures during sending and receiving requests (e.g.: network exception),
	 * 					not one in normal response)
	 * 
	 * @param scope (optional) the scope of callback call (callback's "this" object)
	 * 
	 */
	function versionInfo(callback, scope) {
		if (!_checkInit(callback, scope)) return;

		sendRequest(OPERATORS.versionInfo, null, null, callback, scope);
	}

	/**
	 * just for testing and debugging. 
	 * 
	 * @param id (optional) request id
	 * 		If it is a continuous request (e.g.: query uploading progress),
	 * 		the request id will be set to the response id of last request.
	 * 
	 * @param params (optional) extra request parameters
	 * 
	 * @param callback (optional) callback being called while request either succeeds or fails.
	 * 
	 * 		callback has following parameters:
	 * 
	 * 			resp: response data, which is a json object and contains the following attributes:
	 * 					operator  : the operator of version info request
	 * 					reqId     : always null
	 * 					respId    : the response id
	 * 					returnCode: returnCode (0 succeeds, or else failed)
	 *          exception : exception message (if any)
	 *          extraData : TODO
	 * 
	 * 			status: the final status of this request (String)
	 * 			jqXHR: jqXHR object
	 * 			ex: the exception object, if error occurs when send request.
	 * 					It contains a description, a message and a name attributes.
	 * 					(Note: such error occures during sending and receiving requests (e.g.: network exception),
	 * 					not one in normal response)
	 * 
	 * @param scope (optional) the scope of callback call (callback's "this" object)
	 * 
	 */
	function echoBack(id, params, callback, scope) {
		if (!_checkInit(callback, scope)) return;

		sendRequest(OPERATORS.echoBack, id, params, callback, scope);
	}

	/**
	 * show a file browser dialog to let user choose local files. 
	 * 
	 * @param id (optional) request id
	 * 		If it is a continuous request (e.g.: query uploading progress),
	 * 		the request id will be set to the response id of last request.
	 *  
	 * @param params (optional) extra request parameters
	 * 
	 * @param callback (optional) callback being called while request either succeeds or fails.
	 * 
	 * 		callback has following parameters:
	 * 
	 * 			resp: response data, which is a json object and contains the following attributes:
	 * 					operator  : the operator of version info request
	 * 					reqId     : always null
	 * 					respId    : the response id
	 * 					returnCode: returnCode (0 succeeds, or else failed)
	 *          exception : exception message (if any)
	 *          extraData : TODO
	 * 
	 * 			status: the final status of this request (String)
	 * 			jqXHR: jqXHR object
	 * 			ex: the exception object, if error occurs when send request.
	 * 					It contains a description, a message and a name attributes.
	 * 					(Note: such error occures during sending and receiving requests (e.g.: network exception),
	 * 					not one in normal response)
	 * 
	 * @param scope (optional) the scope of callback call (callback's "this" object)
	 * 
	 */
	function fileBrowser(id, params, callback, scope) {
		if (!_checkInit(callback, scope)) return;

		function _filesSelected(resp, status, jqXHR, ex) {
			if (!resp || ex) {
				if (_isFunction(callback)) {
					ex = ex || {
						description: 'error occurs when send filebrowser request',
						message: 'error occurs when send filebrowser request',
						name: 'filebrowser error'
					};
					callback.call(scope, resp, status, jqXHR, ex);
				}
				return;
			}

			var respId = resp.respId;
			var returnCode = resp.returnCode;
			switch (returnCode) {
				case RETURN_CODES.OPERATION_SUCCEED:
				case RETURN_CODES.UNKNOWN_ID:
					callback.call(scope, resp, status, jqXHR);
					break;
				case RETURN_CODES.OPERATION_UNCOMPLETED:
					setTimeout(function() {
						sendRequest(OPERATORS.fileBrowser, respId, params, _filesSelected);
					}, 0);
					break;
				case RETURN_CODES.UNSUPPORT_OPERATION:
				default:
					// these cases should not happen
					alert('request failed!');
					break;
			}
		}

		sendRequest(OPERATORS.fileBrowser, id, params, _filesSelected);
	}

	/**
	 * transfer files between local and clusters (upload and download). 
	 * 
	 * @param id (optional) request id
	 * 		If it is a continuous request (e.g.: query uploading progress),
	 * 		the request id will be set to the response id of last request.
	 *  
	 * @param params (optional) extra request parameters
	 * 
	 * @param callback (optional) callback being called while request either succeeds or fails.
	 * 
	 * 		callback has following parameters:
	 * 
	 * 			resp: response data, which is a json object and contains the following attributes:
	 * 					operator  : the operator of version info request
	 * 					reqId     : always null
	 * 					respId    : the response id
	 * 					returnCode: returnCode (0 succeeds, or else failed)
	 *          exception : exception message (if any)
	 *          extraData : TODO
	 * 
	 * 			status: the final status of this request (String)
	 * 			jqXHR: jqXHR object
	 * 			ex: the exception object, if error occurs when send request.
	 * 					It contains a description, a message and a name attributes.
	 * 					(Note: such error occures during sending and receiving requests (e.g.: network exception),
	 * 					not one in normal response)
	 * 
	 * @param scope (optional) the scope of callback call (callback's "this" object)
	 * 
	 */
	function fileTransfer(id, params, callback, scope) {
		if (!_checkInit(callback, scope)) return;

		// TODO
	}

	/**
	 * start and run a local program. 
	 * 
	 * @param id (optional) request id
	 * 		If it is a continuous request (e.g.: query uploading progress),
	 * 		the request id will be set to the response id of last request.
	 *  
	 * @param params (optional) extra request parameters
	 * 
	 * @param callback (optional) callback being called while request either succeeds or fails.
	 * 
	 * 		callback has following parameters:
	 * 
	 * 			resp: response data, which is a json object and contains the following attributes:
	 * 					operator  : the operator of version info request
	 * 					reqId     : always null
	 * 					respId    : the response id
	 * 					returnCode: returnCode (0 succeeds, or else failed)
	 *          exception : exception message (if any)
	 *          extraData : TODO
	 * 
	 * 			status: the final status of this request (String)
	 * 			jqXHR: jqXHR object
	 * 			ex: the exception object, if error occurs when send request.
	 * 					It contains a description, a message and a name attributes.
	 * 					(Note: such error occures during sending and receiving requests (e.g.: network exception),
	 * 					not one in normal response)
	 * 
	 * @param scope (optional) the scope of callback call (callback's "this" object)
	 * 
	 */
	function runApp(id, params, callback, scope) {
		if (!_checkInit(callback, scope)) return;

		sendRequest(OPERATORS.runApp, id, params, callback, scope);
	}

	init();

	return {
		isInit       : isInit,
		versionInfo  : versionInfo,
		echoBack     : echoBack,
		fileBrowser  : fileBrowser,
		fileTransfer : fileTransfer,
		runApp       : runApp
	};
})();