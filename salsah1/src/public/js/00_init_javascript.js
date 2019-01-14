/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */


function getUrlVars()
{
    var vars = [], hash;
    var hashes = window.location.href.slice(window.location.href.indexOf('?') + 1).split('&');
    for(var i = 0; i < hashes.length; i++)
    {
        hash = hashes[i].split('=');
        vars.push(hash[0]);
        vars[hash[0]] = hash[1];
    }
    return vars;
}


var API_URL = 'http://localhost:3333';
var SIPI_URL = 'http://localhost:1024';

/*
 * let's find the SITE_URL...
 */
var SITE_URL;
(function(){
	var url = window.location.href;
	var pos = url.lastIndexOf('/');
	if (pos !== -1) {
		url = url.substr(0, pos + 1);
	}
	SITE_URL = url;
})();

var RESVIEW = {
	winclass: '.workwintab'
};

var urlparams = getUrlVars();

var SALSAH = {}; // Populated in index.html.
var strings = {}; // Populated in index.html.

var searchresult_window_title = "searchresult";
var extendedsearch_window_title = "Erweiterte Suche";
var addresource_window_title = "addresource";

var STANDARD_MAPPING = "http://rdfh.ch/standoff/mappings/StandardMapping"; // the standard mapping used for text editing in the GUI

var VALTYPE_TEXT = "-"; // obsolete, there is only richtext now
var VALTYPE_INTEGER = "http://www.knora.org/ontology/knora-base#IntValue";
var VALTYPE_FLOAT = "http://www.knora.org/ontology/knora-base#DecimalValue";
var VALTYPE_DATE = "http://www.knora.org/ontology/knora-base#DateValue";
var VALTYPE_PERIOD = 5;
var VALTYPE_RESPTR = "http://www.knora.org/ontology/knora-base#LinkValue";
var VALTYPE_TIME = "http://www.knora.org/ontology/knora-base#TimeValue";
var VALTYPE_INTERVAL = "http://www.knora.org/ontology/knora-base#IntervalValue";
var VALTYPE_GEOMETRY = "http://www.knora.org/ontology/knora-base#GeomValue";
var VALTYPE_COLOR = "http://www.knora.org/ontology/knora-base#ColorValue";
var VALTYPE_HLIST = "http://www.knora.org/ontology/knora-base#ListValue";
var VALTYPE_SELECTION = "http://www.knora.org/ontology/knora-base#ListValue"; // VALTYPE_SELECTION can be treated like a hierarchical list
var VALTYPE_ICONCLASS = 13;
var VALTYPE_RICHTEXT = "http://www.knora.org/ontology/knora-base#TextValue";
var VALTYPE_GEONAME = 15;
var VALTYPE_URI = "http://www.knora.org/ontology/knora-base#UriValue";
var VALTYPE_BOOLEAN = "http://www.knora.org/ontology/knora-base#BooleanValue";

var RESOURCE_TYPE_REGION = "http://www.knora.org/ontology/knora-base#Region";

var PROP_HAS_STANDOFF_LINK_TO = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo";

var RESOURCE_CONTEXT_NONE = 0;
var RESOURCE_CONTEXT_IS_PARTOF = 1;
var RESOURCE_CONTEXT_IS_COMPOUND = 2;
var RESOURCE_ACCESS_NONE = 0;
var RESOURCE_ACCESS_VIEW = 2;
var RESOURCE_ACCESS_ANNOTATE = 3;
var RESOURCE_ACCESS_EXTEND = 4;
var RESOURCE_ACCESS_OVERRIDE = 5;
var RESOURCE_ACCESS_MODIFY = 6;
var RESOURCE_ACCESS_DELETE = 7;
var RESOURCE_ACCESS_RIGHTS = 8;
var VALUE_ACCESS_NONE = 0;
var VALUE_ACCESS_VIEW = 1;
var VALUE_ACCESS_ANNOTATE = 2;
var VALUE_ACCESS_MODIFY = 3;
var VALUE_ACCESS_DELETE = 4;
var langs = {
	de: 1,
	fr: 2,
	it: 3,
	en: 4
};

var ApiErrors = {
	OK: 0,
	INVALID_REQUEST_METHOD: 1,
	CREDENTIALS_NOT_VALID: 2,
	NO_RIGHTS_FOR_OPERATION: 3,
	INTERNAL_SALSAH_ERROR: 4,
	NO_PROPERTIES: 5,
	NOT_IN_USERDATA: 6,
	RESOURCE_ID_MISSING: 7,
	UNKNOWN_VOCABULARY: 8,
	NO_NODES_FOUND: 9,
	API_ENDPOINT_NOT_FOUND: 10,
	INVALID_REQUEST_TYPE: 11,
	PROPERTY_ID_MISSING: 12,
	NOT_YET_IMPLEMENTED: 13,
	COULD_NOT_OPEN_PROGRESS_FILE: 14,
	VALUE_ID_MISSING: 15,
	RESTYPE_ID_MISSING: 15,
	HLIST_ALREADY_EXISTENT: 16,
	HLIST_NO_LABELS: 17,
	HLIST_NOT_EXISTING: 18,
	HLIST_NO_POSITION: 19,
	HLIST_INVALID_POSITION: 20,
	SELECTION_NO_LABELS: 21,
	SELECTION_ALREADY_EXISTENT: 22,
	SELECTION_NO_POSITION: 23,
	SELECTION_INVALID_POSITION: 23,
	UNSPECIFIED_ERROR: 999
};

var Rights = {
	ADMIN_PROPERTIES: 1,
	ADMIN_RESOURCE_TYPES: 2,
	ADMIN_RIGHTS: 4,
	ADMIN_PERSONS: 8,
	ADMIN_ADD_RESOURCE: 256,
	ADMIN_ROOT: 65536,
	RESOURCE_ACCESS_NONE: 0,
	RESOURCE_ACCESS_VIEW_RESTRICTED: 1,
	RESOURCE_ACCESS_VIEW: 2,
	RESOURCE_ACCESS_ANNOTATE: 3,
	RESOURCE_ACCESS_EXTEND: 4,
	RESOURCE_ACCESS_OVERRIDE: 5,
	RESOURCE_ACCESS_MODIFY: 6,
	RESOURCE_ACCESS_DELETE: 7,
	RESOURCE_ACCESS_RIGHTS: 8,
	VALUE_ACCESS_NONE: 0,
	VALUE_ACCESS_VIEW: 1,
	VALUE_ACCESS_ANNOTATE: 2,
	VALUE_ACCESS_MODIFY: 3,
	VALUE_ACCESS_DELETE: 4,
};

var s_ = function(key) {
    if (strings[key] === undefined) {
        return key;
    }
    else {
        return strings[key];
    }
}

SALSAH.reload_css = function() {
    var href = $('#loadcss').attr('href'); + ',#';
	if ((SALSAH.userprofile.active_project !== undefined)) href += '&project_id=' + SALSAH.userprofile.active_project;

    $('#loadcss').attr({href: href});
}


function alertObjectContent(obj, title, maxlevel) {
    var str;

    if (title !== undefined) {
        str = title + "\n";
    }
    else {
        str = '';
    }
    var func = function(obj, prefix, level) {
        if (typeof obj !== 'object') return;
        var i;
        for (i in obj) {
            if (typeof obj[i] === 'object') {
                if (maxlevel === undefined) {
                    func(obj[i], i, level + 1);
                }
                else {
                    if (level <= maxlevel) {
                        func(obj[i], i, level + 1);
                    }
                    else {
			//                        str += prefix + '.' + i + ' : ' + obj[i] + '\n';
                        str += prefix + '.' + i + ' : ' + '(object)' + '\n';
                    }
                }
            }
            else {
                str += prefix + '.' + i + ' : ' + obj[i] + '\n';
            }
        }
    }

    func(obj, '', 0);
    alert(str);
}


// -----------------------------------------------------------------------------
// seconds2timecode: from seconds to timecode
// -----------------------------------------------------------------------------
SALSAH.seconds2timecode = function(sec, fps){
	var sec = parseFloat(sec),
	hours,
	minutes,
	seconds,
	frames,
	floatFrames;

	if( sec > 0 ) {
		hours = Math.floor(sec / 3600.0);
		sec -= hours * 3600.0;
		if (hours < 10 && hours > 0) {
			hours = '0' + hours.toString();
		} else if (hours <= 0) {
			hours = '00';
		} else {
			hours = hours.toString();
		}
		minutes = Math.floor(sec / 60.0);
		sec -= minutes * 60.0;
		if (minutes < 10 && minutes > 0) {
			minutes = '0' + minutes.toString();
		} else if (minutes <= 0) {
			minutes = '00';
		} else {
			minutes = minutes.toString();
		}
		if (sec > 0) {
			seconds = sec;
		} else {
			seconds = 0;
		}
	} else {
		hours = '00';
		minutes = '00';
		seconds = 0;
	}

	if(fps){
		frames;
		seconds = parseInt(sec);
		floatFrames = parseFloat(sec - seconds);

		if (seconds < 10) {
			seconds = '0' + seconds;
		} else {
			seconds = seconds;
		}

		frames = Math.floor(fps * floatFrames);
		if (frames < 10) {
			frames = '0' + frames.toString();
		} else {
			frames = frames.toString();
		}
		return hours + ":" + minutes + ":" + seconds + ":" + frames;
	} else {
		if (seconds < 9.5 && seconds > 0) {
			seconds = '0' + seconds.toFixed(0); // or 3
		} else if (seconds <= 0){
			seconds = '00';
		} else {
			seconds = seconds.toFixed(0); // or 3
		}
		//			frames = '';
		return hours + ":" + minutes + ":" + seconds;
	}
}


// -----------------------------------------------------------------------------
// timecodeSeconds: from timecode to seconds
// -----------------------------------------------------------------------------
SALSAH.timecode2seconds = function(tc){
	var secs = parseFloat(tc.substr(0, 2)) * 3600.0;
	secs += parseFloat(tc.substr(3, 2)) * 60.0;
	secs += parseFloat(tc.substr(6, 6));
	return secs;
};

// helper functions for differences between the old and the new API

var SALSAH_API_LEGACY = {
	make_date_string: function(dateObj) {
		// Knora expects a searchval string: Calendar:YYYY-MM-DD[:YYYY-MM-DD]
		var dateStr = dateObj.calendar + ":" + dateObj.dateval1 + ' ' + dateObj.era1;
		if (dateObj.dateval2 !== undefined) {
			// period
			dateStr += ":" + dateObj.dateval2 + ' ' + dateObj.era2;
		}
		return dateStr;
	}

};

/*
 * work out an ontology's short name out of its URI
 * arg id : ontology's URI, for example: `http://www.knora.org/ontology/0103/theatre-societe`
 * return: the very last part of the URI, in this example: `theatre-societe`
 */
SALSAH.vocabularyId2shortName = function(ontologyid) {
	return ontologyid.substr(ontologyid.lastIndexOf('/') + 1)
};
