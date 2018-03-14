
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

var searchresult_window = function(searchstr, project) {
	$('.workwin_content').tabs('setActiveTab', 'workwintab');
	var viewer = RESVIEW.ObjectViewer(RESVIEW.winclass, {
		closable : true,
		moveable : true,
		minimizable : true,
		sizable : true,
		maximizable : true,
		dim_x : 500,
		dim_y : 500,
		visible : true,
		pos_x : 230,
		pos_y : 30,
		taskbar : false,
		title : searchresult_window_title,
		window_icon: SITE_URL + '/app/icons/16x16/search.png',
		content : '',
		overflow_y: 'auto',
	} /*,
	{
		content_from_url: {
			url: SITE_URL + '/helper/simplesearch.php',
			postdata: {
				simplesearch: searchstr,
			},
		}
	}*/);
/*
	var $this = viewer.contentElement();
	$.__post(SITE_URL + '/helper/simple_search.php',
		{
			simplesearch: searchstr
		},
		function(data) {
			$this.append(data.DATA.html);
			eval(data.DATA.js);
		},
		'json'
	);
*/
	if ((project === undefined) || (project == -1)) {
		viewer.contentElement().simpsearch({
			searchstring: searchstr,
		});
	}
	else {
		viewer.contentElement().simpsearch({
			searchstring: searchstr,
			filter_by_project: project
		});
	}
}

var extended_search = function() {
	$('.workwin_content').tabs('setActiveTab', 'workwintab');
	var viewer = RESVIEW.ObjectViewer(RESVIEW.winclass, {
		closable : true,
		movable : true,
		minimizable : true,
		sizable : true,
		maximizable : true,
		dim_x : 700,
		dim_y : 600,
		visible : true,
		pos_x : 80,
		pos_y : 20,
		taskbar : false,
		title : extendedsearch_window_title,
		window_icon: SITE_URL + '/app/icons/16x16/search-extended.png',
		content: '<div id="<WINID>"/>',
		overflow_y: 'auto',
	});
	var winid = viewer.windowId();
//	viewer.contentElement().extsearch({winid: winid});
	viewer.contentElement().extsearch({successCB: function($this, html, js) {
		$this.append(html);
		eval(js); // evaluate the javascript returned in the local context (thus that $this is available)
	}});
}

var add_resource = function() {
	var viewer = RESVIEW.ObjectViewer(RESVIEW.winclass, {
		closable : true,
		movable : true,
		minimizable : true,
		sizable : true,
		maximizable : true,
		dim_x : 700,
		dim_y : 600,
		visible : true,
		pos_x : 100,
		pos_y : 30,
		taskbar : false,
		title : addresource_window_title,
		window_icon: SITE_URL + '/app/icons/16x16/add.png',
		content: '<div id="<WINID>"/>',
	});
	var winid = viewer.windowId();
	$('#' + winid).resadd({viewer: viewer});
}

var collect_windowinfo = function() {
	var save_settings = new Array();
	if (RESVIEW.winclass == '.workwin_content') return; // don't save contents of index.php
/*
 * to be done....!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *
	for (index in $.windowObject.prototype.idarr) {
		var obj = $.windowObject.prototype.idarr[index];
		var htmlobj = obj.getHTMLElement();
		if (typeof htmlobj.data('visualisationObject') != 'undefined') {
			var settings = htmlobj.data('visualisationObject');
			save_settings.push(settings);
		}
		else {
			// it's a "simple" window
			save_settings.push({type: 'SimpleWindow', settings: obj.getCurrentWindowSettings()});
		}
	}
*/
	return save_settings;
}


RESVIEW.maximize_cb = function(element) {
	
	var viewer;
	var title = element.win('title');
	var wp = $('.workwin_content');
	var settings = element.data('visualisationObject');
	var new_viewer_settings = {};
	$.extend(true, new_viewer_settings, settings.viewer_settings); // some sort of a deep copy...
	var new_window_settings = {};
	$.extend(true, new_window_settings, settings.window_settings); // some sort of a deep copy...
	
	element.win('deleteWindow');

	var tabname = 'tab_' + RESVIEW.maximize_cb.prototype.cnt;
	RESVIEW.maximize_cb.prototype.cnt++;
	wp.tabs('addTab', tabname, title, '', {
		setActive: true,
		deletable: true,
		onDeleteCB: function(name) {
			var settings = {};
			$.extend(true, settings, wp.tabs('dataHook', name, 'MAXIMIZE')); // deep copy, because the reference to the data hook will be deleted....
			settings.viewer_settings = viewer.currentSettings().viewer_settings; // get the actual viewer settings
			wp.tabs('setActiveTab', 'workwintab');
			if (RESVIEW.maximize_cb) {
				settings.window_settings.whenMaximizedCB = RESVIEW.maximize_cb;
			}
			switch (settings.viewer_settings.type) {
				case 'ImageCollectionView': {
					RESVIEW.ImageCollectionView(wp.tabs('contentElement', 'workwintab'), settings.window_settings, settings.viewer_settings);
					break;
				}
				case 'ImageView': {
					RESVIEW.ImageView(wp.tabs('contentElement', 'workwintab'), settings.window_settings, settings.viewer_settings);
					break;
				}
				default: {

				}
			}
		},
	});
	switch (settings.viewer_settings.type) {
		case 'ImageCollectionView': {
			viewer = RESVIEW.ImageCollectionView(wp.tabs('contentElement', tabname), {fullscreen: true}, new_viewer_settings);
			break;
		}
		case 'ImageView': {
			viewer = RESVIEW.ImageView(wp.tabs('contentElement', tabname), {fullscreen: true}, new_viewer_settings);
			break;
		}
		default: {
			
		}
	}
	wp.tabs('dataHook', tabname, 'MAXIMIZE', {window_settings: new_window_settings, viewer: viewer}); // we don't need to save the viewer settings
}
RESVIEW.maximize_cb.prototype.cnt = 0;


$(function() {
	var wp = $('.workwin_content');
	wp.tabs({
		tabChangedCB: function(from, to) {
			//
			// if the user changed the browser window while a tab was hidden, we have to reset the size by calling whenSized
			// with dx = dy 0 0
			//
			if (to == 'workwintab') return;
			if (to.substr(0, 5) == 'graph') return;
			
			var settings = wp.tabs('dataHook', to, 'MAXIMIZE');
			settings.viewer.whenSized(0, 0);
		},
	});
	wp.tabs('addTab', 'workwintab', 'Windows', '');



	//
	// if the browser window is being resized, we may have to move some windows in order to keep them visible
	//
/*
	var old_w = $(RESVIEW.winclass).innerWidth();
	var old_h = $(RESVIEW.winclass).innerHeight();
	$(window).resize(function() {
		var w = $(RESVIEW.winclass).innerWidth();
		var h = $(RESVIEW.winclass).innerHeight();
		for (index in $.windowObject.prototype.idarr) {
			var winobj = $.windowObject.prototype.idarr[index];
			var pos = winobj.getPosition();
			var dim = winobj.getDimensions();
			var new_pos_x = pos.pos_x;
			var new_pos_y = pos.pos_y;
			if ((pos.pos_x + dim.dim_x) > w) {
				if ((w - dim.dim_x) > 0) new_pos_x = w - dim.dim_x;
			}
			if ((pos.pos_y + dim.dim_y) > w) {
				if ((h - dim.dim_y) > 0) new_pos_y = h - dim.dim_y;
			}
			if ((pos.pos_x != new_pos_x) || (pos.pos_y != new_pos_y)) {
				winobj.setPosition(new_pos_x, new_pos_y);
			}
		}
		wp.tabs('each', function(name){
			if (name == 'workwintab') return;
			var settings = wp.tabs('dataHook', name, 'MAXIMIZE');
			settings.viewer.whenSized(w - old_w, h - old_h);
		});
		old_w = w;
		old_h = h;
	});
*/
});

