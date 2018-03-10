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

// this plugin handles taskbar symbols between different tabs
(function( $ ) {


	var methods = {
		init: function() {
			return this.each(function(){
								
				var $this = $(this);
				var localdata = {};

				$this.empty();
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				
				localdata.elements = {};
				localdata.active = null;
			});
		},
		
		add: function(name, ele) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.active !== null) localdata.elements[localdata.active].css({display: 'none'});
				ele.css({
					position: 'absolute',
					left: 0,
					right: 0,
					top: 0,
					bottom: 0,
					display: 'block'
				});
				if (localdata.elements[name] !== undefined) {
					localdata.elements[name].remove(); //remove from DOM
				}
				$this.append(ele);
				localdata.elements[name] = ele;
				localdata.active = name;
			});
		},
		
		remove: function(name, name2) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.elements[name] !== undefined) {
					localdata.elements[name].remove();
					delete localdata.elements[name];
				}
				if (name2 === undefined) {
					localdata.active = null;
				}
				else {
					localdata.elements[name2].css({display: 'block'});
					localdata.active = name2;
				}
			});
		},
		
		show: function(name) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.elements[name] !== undefined) {
					if (localdata.active !== null) localdata.elements[localdata.active].css({display: 'none'});
					localdata.elements[name].css({display: 'block'});
					localdata.active = name;
				}
			});
		},
		
		hide: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.active !== null) localdata.elements[localdata.active].css({display: 'none'});
				localdata.active = null;
			});
		},
		
		get: function(name) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			if (localdata.elements[name] !== undefined) {
				return localdata.elements[name];
			}
			else {
				return false;
			}
		}
/*		
		anotherMethod: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		}
*/
		/*===========================================================================*/
	};
	
	$.fn.elestack = function(method) {
	    // Method calling logic
	    if ( methods[method] ) {
	      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
	    } else if ( typeof method === 'object' || ! method ) {
	      return methods.init.apply( this, arguments );
	    } else {
	      throw 'Method ' +  method + ' does not exist on jQuery.tooltip';
	    }
	};

})( jQuery );