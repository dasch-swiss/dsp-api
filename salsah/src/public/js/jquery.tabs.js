/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

(function( $ ){
   /*
	* Callback functions:
	*		tabChangedCB(old_tab_name, new_tab_name): Used only in workwin.js --> try to eliminate!!
	*		onLeaveCB() : if the return value of the callback is false, the tab is not changed. If true, the tab is changed!
	*		onEnterCB() : 
	*/
	var set_active_tab = function(localdata, name) {
		if (localdata.current_tab == name) return; // do nothing, we clicked on an active tab...
		if (localdata.tabs[name]) {
			if (localdata.current_tab) {
				if (localdata.tabs[localdata.current_tab].onLeaveCB instanceof Function) {
					// call onLeaveCB for current tab
				    if (localdata.tabs[localdata.current_tab].data_hook !== undefined) {
					if (localdata.tabs[localdata.current_tab].onLeaveCB(localdata.tabs[localdata.current_tab].data_hook) === false) return;
				    }
				}
				localdata.tabs[localdata.current_tab].tab.removeClass('tabVisible').addClass('tabInvisible');
				localdata.tabs[name].tab.removeClass('tabInvisible').addClass('tabVisible');
				if (localdata.tabs[name].onEnterCB instanceof Function) {
					// call onEnterCB for new tab
				    if (localdata.tabs[name].data_hook !== undefined) {
					localdata.tabs[name].onEnterCB(localdata.tabs[name].data_hook);
				    }
				}
				localdata.tabs[localdata.current_tab].content.css('display', 'none');
			}
			localdata.tabs[name].content.css('display', 'block');
			if (localdata.settings.tabChangedCB instanceof Function) {
				localdata.settings.tabChangedCB(localdata.current_tab, name); // old tab, new tab
			}
			localdata.current_tab = name;
		}
	};
	
	var adjust_tabcontainer = function(localdata, name) {
		if (localdata.tabs[name].tab.outerHeight(true) > localdata.max_tabheight) {
			localdata.max_tabheight = localdata.tabs[name].tab.outerHeight(true);
			localdata.tabcontainer.height = localdata.max_tabheight;
			localdata.tabcontainer.ele.height(localdata.max_tabheight);
			var h = localdata.tabs[name].tab.height();
			var i;
			for (i in localdata.tabs) {
				localdata.tabs[i].tab.height(h);
				if (localdata.tabs[i].content) {
					localdata.tabs[i].content.css('top', localdata.tabcontainer.ele.outerHeight(true) + 3 + 'px');
				}
			}
		}
	};
	
	var remove_tab = function(localdata, name) {
		if (localdata.tabs[name] !== undefined) {
			if (localdata.current_tab == name) {
				var prev = null;
				for (var nn in localdata.tabs) {
					if (nn == name) break;
					prev = nn;
				}
				if (prev) {
					set_active_tab(localdata, prev);
				}
				else {
					localdata.current_tab = null;
				}
			}
			localdata.tabs[name].content.remove();
			localdata.tabs[name].tab.remove();
			delete localdata.tabs[name];
		}
	}
	
	var create_tab = function(localdata, element, name, title, content, options) {
		localdata.tabs[name] = {};
		
		//
		// create the ab specfic callbacks (enter and leave) if given
		//
		if (options !== undefined) {
			if (options.onEnterCB instanceof Function) localdata.tabs[name].onEnterCB = options.onEnterCB;
			if (options.onLeaveCB instanceof Function) localdata.tabs[name].onLeaveCB = options.onLeaveCB;
			if (options.dataHook instanceof Object) {
				localdata.tabs[name].data_hook = {};
				localdata.tabs[name].data_hook[options.dataHook.name] = options.dataHook.data; 
			}
		}
		
		//
		// create the tab label
		//
		if ((options !== undefined) && (options.setActive !== undefined)) {
			if (localdata.current_tab) {
				localdata.tabs[localdata.current_tab].tab.removeClass('tabVisible').addClass('tabInvisible');
				localdata.tabs[localdata.current_tab].content.css('display', 'none');
				if (localdata.tabs[localdata.current_tab].onLeaveCB instanceof Function) {
					// call onLeaveCB for current tab
					localdata.tabs[localdata.current_tab].onLeaveCB(localdata.tabs[name].data_hook);
				}
			}
			localdata.current_tab = name;
		}

		var tmpclass;
		var tmpdisp;
		if (localdata.current_tab === null) {
			tmpclass = 'tabVisible';
			tmpdisp = 'block';
		}
		else {
			tmpclass = 'tabInvisible';
			tmpdisp = 'none';
		}
		if ((options !== undefined) && (options.setActive !== undefined)) {
			tmpclass = 'tabVisible';
			tmpdisp = 'block';
		}		
		
		localdata.tabs[name].tab = $('<div>').addClass('tabLabel').addClass(tmpclass).css({
			'float': 'left',
		}).append(title).click(function(event){
			set_active_tab(localdata, name);
		}).appendTo(localdata.tabcontainer.ele);
		
		//
		// here we adjust the height of the tabcontainer and tab contents
		//
		localdata.tabs[name].tab.find('img').on('load', function() {
			adjust_tabcontainer(localdata, name);					
		});
		adjust_tabcontainer(localdata, name);

		//
		// create the tab content
		//
		localdata.tabs[name].content = $('<div>', {'class': 'tabContent'}).addClass(name).css({
			position: 'absolute',
			left: 0,
			top: localdata.tabcontainer.ele.outerHeight() + 'px',
			right: 0,
			bottom: 0,
			display: 'block'
		}).appendTo(element);
		
		if (typeof content === 'function') {
			content(localdata.tabs[name].content);
		}
		else {
			localdata.tabs[name].content.append(content);
		}
		
		if (localdata.current_tab === null) {
			localdata.current_tab = name;
		}

		if ((options) && options.deletable) {
			$('<img>', {src: SITE_URL + '/app/icons/16x16/delete.png'}).click(function() {
				if (options.onDeleteCB instanceof Function) {
					options.onDeleteCB(name);
				}
				remove_tab(localdata, name);
			}).appendTo(localdata.tabs[name].tab);
		}

		localdata.tabs[name].content.css('display', tmpdisp);
	};
	

	var methods = {
		init: function(options) { // $(element).navigator('init', {settings: here,...});
			return this.each(function(){
				var $this = $(this);
				var localdata = {};

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				
				localdata.settings = {
					tab_container_height: 15,
					tabChangedCB: null, // this callback is executed each time the user changes the tab
				};
				$.extend(localdata.settings, options);

				localdata.tabcontainer = {};
				localdata.tabcontainer.ele = $('<div>', {'class': 'tabContainer'}).css({
					position: 'absolute',
					left: '0px',
					top: '0px',
					right: '0px',
					overflow: 'hidden',
				}).appendTo($this);
				if (localdata.tabcontainer.ele.height() == 0) {
					localdata.tabcontainer.ele.height(localdata.settings.tab_container_height);
				}
				localdata.tabcontainer.height = localdata.tabcontainer.ele.outerHeight(true);
				localdata.tabs = {};
				localdata.current_tab = null;
				localdata.max_tabheight = 0;
			});
		},
		/*===========================================================================*/
		
		addTab: function(name, title, content, options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				create_tab(localdata, $this, name, title, content, options);
			});
		}, 
		/*===========================================================================*/

		setActiveTab: function(name) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				set_active_tab(localdata, name);
			});
		},
		/*===========================================================================*/
		
		getActiveTab: function() {
			var localdata = $this.data('localdata');
			return localdata.current_tab;
		},
		/*===========================================================================*/

		triggerOnEnterCB: function(name) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.tabs[name].onEnterCB instanceof Function) {
					// call onEnterCB for new tab
					localdata.tabs[name].onEnterCB(localdata.tabs[name].data_hook);
				}
			});
		},
		/*===========================================================================*/
		
		setTitle: function(name, title) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.tabs[name].tab.empty().append(title);
				localdata.tabs[name].tab.find('img').on('load', function() {
					adjust_tabcontainer(localdata, name);					
				});
				adjust_tabcontainer(localdata, name);					
			});
		},
		/*===========================================================================*/

		setContent: function(name, content) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.tabs[name].content.empty();

				if (typeof content === 'function') {
					content(localdata.tabs[name].content);
				}
				else {
					localdata.tabs[name].content.append(content);
				}
			});
		},
		/*===========================================================================*/
		
		setOnLeaveCB: function(name, func) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.tabs[name] === undefined) return true;
				if (func !== undefined) {
					localdata.tabs[name].onLeaveCB = func;
				}
				else {
					localdata.tabs[name].onLeaveCB = null;
				}
				return true;
			});
		},
		/*===========================================================================*/
		
		setTab: function(name, title, content, options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.tabs[name]) { // The tab is already existing - exchange the title and content
					delete localdata.tabs[name].data_hook; // reset the data hook to undefined!!
					//
					// create the ab specfic callbacks (enter and leave) if given
					//
					if (options !== undefined) {
						if (options.onEnterCB instanceof Function) localdata.tabs[name].onEnterCB = options.onEnterCB;
						if (options.onLeaveCB instanceof Function) localdata.tabs[name].onLeaveCB = options.onLeaveCB;
						if (options.dataHook instanceof Object) {
							if (localdata.tabs[name].data_hook === undefined) localdata.tabs[name].data_hook = {};
							localdata.tabs[name].data_hook[options.dataHook.name] = options.dataHook.data; 
						}
					}

					localdata.tabs[name].tab.empty().append(title);
					localdata.tabs[name].tab.find('img').on('load', function() {
						adjust_tabcontainer(localdata, name);					
					});
					adjust_tabcontainer(localdata, name);
					if (typeof content === 'function') {
						content(localdata.tabs[name].content.empty());
					}
					else {
						localdata.tabs[name].content.empty().append(content);
					}
					//					set_active_tab(localdata, name);
				}
				else { // The tab does not exist - we create it
					create_tab(localdata, $this, name, title, content, options);
				}
				localdata.tabs[name].tab.find('img').on('load', function() {
					adjust_tabcontainer(localdata, name);					
				});
			});
		},
		/*===========================================================================*/
		
		contentElement: function(name) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			return localdata.tabs[name].content;
		},
		/*===========================================================================*/

		remove: function(name) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				remove_tab(localdata, name);
			});
		},
		/*===========================================================================*/


		dataHook: function(name, dhname, data) {
			$this = $(this.get(0));
			var localdata = $this.data('localdata');
			if (localdata.tabs[name].data_hook === undefined) localdata.tabs[name].data_hook = {};
			if (data !== undefined) {
				localdata.tabs[name].data_hook[dhname] = data;
			}
			else {
				if (dhname === undefined) {
					return localdata.tabs[name].data_hook;
				}
				else {
					return localdata.tabs[name].data_hook[dhname];
				}
			}
		},
		/*===========================================================================*/

		each: function(func) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				for (var name in localdata.tabs) {
					func(name);
				}
			});
		},
		/*===========================================================================*/

		/*tabChangedCB: function(tab_changed_cb) {
			if (tab_changed_cb !== undefined) {
				// argument is given: set cb for the set of matched elements (this)
				return this.each(function(){
					var $this = $(this);
					var localdata = $this.data('localdata');
				
					if (tab_changed_cb instanceof Function) localdata.settings.tabChangedCB = tab_changed_cb;
				
				});
			} else {
				// return current cb
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				return localdata.settings.tabChangedCB;
			}
		},*/

		anotherMethod: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		},
		/*===========================================================================*/
	}
	
	$.fn.tabs = function(method) {
	    // Method calling logic
	    if ( methods[method] ) {
	      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
	    } else if ( typeof method === 'object' || ! method ) {
	      return methods.init.apply( this, arguments );
	    } else {
	      throw 'Method ' +  method + ' does not exist on jQuery.tabs';
	    }		
	};
	/*===========================================================================*/
})( jQuery );