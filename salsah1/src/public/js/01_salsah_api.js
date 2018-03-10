

/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * SALSAH.ApiGet(method [, value][, authorization] [, data], success [, error])
 *
 * Example: SALSAH.ApiGet('resources', res_id, {reqtype: 'info'}, function(...))
 *
 *   method: string (not a full qualified URL, just the api)
 *   value/id: the requested value/id (usually a IRI – the uriEcoding takes place within ApiGet)
 *   authorization: object {username: "USERNAME", password: "PASSWORD"}
 *   data: object
 *   success: function(PlainObject data, String textStatus, jqXHR jxXhr)
 *   error: function(jqXHR jqXhr, String textStatus, String errorThrown)
 */
SALSAH.ApiGet = function() {
	var n, m;
	var send_params;

	var success_cb;
	var error_cb;
	var value;

	//console.log(arguments)

	for (n in arguments) {
		//console.log("n: " + arguments[n] + " and type " + typeof arguments[n]);
		if (send_params === undefined) { // first run in for loop
			var data_type = 'json';
			var content_type = 'application/json';
			var method = arguments[n];
			if ((method !== undefined) && (method.indexOf('.html') > 0))
			{
				data_type = 'html';
				content_type = 'text/html';
			}
			send_params = {
				type: 'GET',
				url: ((API_URL === undefined) ? SITE_URL : API_URL) + '/v1/' + method,
				contentType: content_type,
				dataType: data_type,
                xhrFields: {
                    withCredentials: true
                }
			};
		}
		else if (typeof arguments[n] == 'string' && arguments[n] != 'json') { // ignore arg when it is 'json' because this is meant to be the datatype of the async request
			// this is the value/id, because it's a string
			value = encodeURIComponent(arguments[n]);
			//console.log('ApiGet method: ' + method + ' value:' + value)
		}
		else if (typeof arguments[n] == 'object') {
			if ((arguments[n].username !== undefined) && (arguments[n].password !== undefined)) {
				send_params.headers = {
					Authorization: 'Basic ' + btoa(arguments[n].username + ":" + arguments[n].password)
				}
			}
			else {
				send_params.data = arguments[n]; // assign the whole object to the data params
			}
		}
		else if (typeof arguments[n] == 'function') {
			if (success_cb === undefined) {
				success_cb = arguments[n];
			}
			else if (error_cb === undefined) {
				error_cb = arguments[n];
			}
		}
	}
	if (value !== undefined) {
		send_params.url += '/' + value;
	}

	send_params.success = function(data, textStatus, jqXHR) {
		if (typeof success_cb == 'function') {
			success_cb(data, textStatus, jqXHR);
		}
	}

	send_params.error = function(jqXHR, textStatus, errorThrown) {
		if (typeof error_cb == 'function') {
			error_cb(jqXHR, textStatus, errorThrown);
		}
		else {
			alert('SALSAH.ApiGet ERROR: ' + errorThrown + ' ' + textStatus);
		}
	}

	if (send_params.error === undefined) {
		send_params.error = function(jqXHR, textStatus, errorThrown) {
			alert(textStatus + "\n" + errorThrown + "\n" + jqXHR.responseText);
		}
	}

	// do note use square brackets in params serialization
	send_params.traditional = true;

	return $.ajax(send_params);
};

/*
 * SALSAH.ApiPost(url [, authorization][, postdata][, modifier], success [, error])
 *
 *   url: string (not a full qualified URL, just the api)
 *   authorization: object {username: "USERNAME", password: "PASSWORD"}
 *   data: object
 *   success: function(PlainObject data, String textStatus, jqXHR jxXhr)
 *   error: function(jqXHR jqXhr, String textStatus, String errorThrown)
 */
SALSAH.ApiPost = function() {
	var n, m;
	var send_params;
	var postdata = false;
	var success_cb;

	for (n in arguments) {
		if (send_params === undefined) {
			var method = arguments[n];
			send_params = {
				type: 'POST',
				url: (API_URL === undefined) ? SITE_URL : API_URL + '/v1/' + method,
				contentType: 'application/json',
				dataType: 'json',
                xhrFields: {
                    withCredentials: true
                }
			};
		}
		else if (typeof arguments[n] == 'object') {
			if ((arguments[n].username !== undefined) && (arguments[n].password !== undefined)) {
				send_params.headers = {
					Authorization: 'Basic ' + btoa(arguments[n].username + ":" + arguments[n].password)
				}
			}
			else {
				if (!postdata)
				{
					send_params.data = arguments[n];
					postdata = true;
				}
				else {
					if (!$.isEmptyObject(arguments[n]))
					{
						var j = 0;
						var c;
						for (m in arguments[n])
						{
							c = (j == 0) ? c = '?' : c = '&';
							send_params.url += c + m + '=' + arguments[n][m];
							j++;
						}
					}
				}
			}
		}
		else if (typeof arguments[n] == 'function') {
			if (success_cb === undefined) {
				success_cb = arguments[n];
			}
			else if (send_params.error === undefined) {
				send_params.error = arguments[n];
			}
		}
	}

	//
	// convert all data to JSON before sending...
	//
	send_params.data = JSON.stringify(send_params.data);

	send_params.success = function(data, textStatus, jqXHR) {
		if (typeof success_cb == 'function') {
			success_cb(data, textStatus, jqXHR);
		}
	}
	if (send_params.error === undefined) {
		send_params.error = function(jqXHR, textStatus, errorThrown) {
			alert(textStatus + "\n" + errorThrown + "\n" + jqXHR.responseText);
		}
	}

	return $.ajax(send_params);
};

SALSAH.ApiPut = function() {
	var n, m;
	var send_params;
	var postdata = false;
	var success_cb;

	for (n in arguments) {
		if (send_params === undefined) {
			var method = arguments[n];
			send_params = {
				type: 'PUT',
				url: (API_URL === undefined) ? SITE_URL : API_URL + '/v1/' + method,
				contentType: 'application/json',
				dataType: 'json',
                xhrFields: {
                    withCredentials: true
                }
			};
		}
		else if (typeof arguments[n] == 'object') {
			if ((arguments[n].username !== undefined) && (arguments[n].password !== undefined)) {
				//send_params.username = arguments[n].username;
				//send_params.password = arguments[n].password;
				send_params.headers = {
					Authorization: 'Basic ' + btoa(arguments[n].username + ":" + arguments[n].password)
				}
			}
			else {
				if (!postdata)
				{
					send_params.data = arguments[n];
					postdata = true;
				}
				else {
					if (!$.isEmptyObject(arguments[n]))
					{
						var j = 0;
						var c;
						for (m in arguments[n])
						{
							c = (j == 0) ? c = '?' : c = '&';
							send_params.url += c + m + '=' + arguments[n][m];
							j++;
						}
					}
				}
			}
		}
		else if (typeof arguments[n] == 'function') {
			if (success_cb === undefined) {
				success_cb = arguments[n];
			}
			else if (send_params.error === undefined) {
				send_params.error = arguments[n];
			}
		}
	}

	//
	// convert all data to JSON before sending...
	//
	send_params.data = JSON.stringify(send_params.data);

	send_params.success = function(data, textStatus, jqXHR) {
		if (typeof success_cb == 'function') {
			success_cb(data, textStatus, jqXHR);
		}
	}

	if (send_params.error === undefined) {
		send_params.error = function(jqXHR, textStatus, errorThrown) {
			alert(textStatus + "\n" + errorThrown + "\n" + jqXHR.responseText);
		}
	}

	return $.ajax(send_params);
};

SALSAH.ApiDelete = function() {
	var n;
	var send_params;
	var success_cb;

	for (n in arguments) {
		if (send_params === undefined) {
			var method = arguments[n];
			send_params = {
				type: 'DELETE',
				url: (API_URL === undefined) ? SITE_URL : API_URL + '/v1/' + method,
				contentType: 'application/json',
				dataType: 'json',
                xhrFields: {
                    withCredentials: true
                }
			};
		}
		else if (typeof arguments[n] == 'object') {
			if ((arguments[n].username !== undefined) && (arguments[n].password !== undefined)) {
				send_params.username = arguments[n].username;
				send_params.password = arguments[n].password;
				send_params.headers = {
					Authorization: 'Basic ' + btoa(arguments[n].username + ":" + arguments[n].password)
				}
			}
			else {
				send_params.data = arguments[n];
			}
		}
		else if (typeof arguments[n] == 'function') {
			if (success_cb === undefined) {
				success_cb = arguments[n];
			}
			else if (send_params.error === undefined) {
				send_params.error = arguments[n];
			}
		}
	}

	//
	// convert all data to JSON before sending...
	//
	send_params.data = JSON.stringify(send_params.data);

	send_params.success = function(data, textStatus, jqXHR) {
		if (typeof success_cb == 'function') {
			success_cb(data, textStatus, jqXHR);
		}
	}

	if (send_params.error === undefined) {
		send_params.error = function(jqXHR, textStatus, errorThrown) {
			alert(textStatus + "\n" + errorThrown + "\n" + jqXHR.responseText);
		}
	}

	return $.ajax(send_params);
};
