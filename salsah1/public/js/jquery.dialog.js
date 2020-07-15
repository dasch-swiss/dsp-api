/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

(function( $ ){
	
	var close_button = new Image();
	close_button.src = SITE_URL + '/app/icons/close-button.gif';
	
	var methods = {

		init: function(options) { 
			return this.each(function() {
				// $this points to the main div of the win
				var $this = $(this);
				var localdata = {};
				localdata.elements = {}; // an object which will contain the window elements
				localdata.elements.win_ele = $this;

				localdata.settings = {
					modal: true,
					pos_x: -1,
					pos_y: -1,
					dim_x: 800,
					dim_y: 500,
					title: 'TITLE',
					content: undefined,
					url: undefined,
					overflow_y: 'visible',
					close_icon: true,
					close_button: true,
					removeCB: undefined
				};
				$.extend(localdata.settings, options);
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				localdata.elements.shade = $('<div>', {id: 'shade'}).css({
					position: 'fixed',
					left: 0,
					top: 0,
					right: 0,
					bottom: 0,
					opacity: 0.6,
					filter: 'alpha(opacity=60)',
					display: 'block',
					backgroundColor: 'rgb(255, 255, 255)',
				}).appendTo('body');
				var w = $('body').width();
				var h = $('body').height();
				
				if (localdata.settings.pos_x < 0) {
					localdata.settings.pos_x = (w - localdata.settings.dim_x) / 2;
				}
				
				if (localdata.settings.pos_y < 0) {
					localdata.settings.pos_y = (h - localdata.settings.dim_y) / 2;
				}
				if (localdata.settings.pos_y < 0) localdata.settings.pos_y = 100;
				
				localdata.elements.box = $('<div>').css({
					display: 'block',
					zIndex: 99,
					position: 'fixed',
					overflow: 'hidden',
					width: parseInt(localdata.settings.dim_x),
					height: parseInt(localdata.settings.dim_y),
					left: parseInt(localdata.settings.pos_x),
					top: parseInt(localdata.settings.pos_y),
					'border-style': 'solid',
					'border-width': '2px',
					'border-color': '#000',
					'border-radius': 10,
					'-moz-border-radius': 10,
					'-webkit-border-radius': 10,	
					'-khtml-border-radius': 10,
				  	'-moz-box-shadow': '5px 5px 5px #222',
				  	'-webkit-box-shadow': '5px 5px 5px #222',
				  	'-khtml-box-shadow': '5px 5px 5px #222',
					'box-shadow': '5px 5px 5px #222',
					'background-color': '#fff',
					opacity: 1.0,
					filter: 'alpha(opacity=100)',
				}).appendTo('body');

				localdata.elements.titlebar = $('<div>').css({
					position: 'absolute',
					left: 0,
					top: 0,
					right: 0,
					padding: 5,
					height: 18,
					'background-color': 'rgba(100, 100, 100, 100)',
					color: 'rgb(255, 255, 255)',
					overflow: 'hidden',
					'border-top-left-radius': 10,
					'border-top-right-radius': 10,
					'text-align': 'center',
				}).append($('<div>').css({position: 'absolute', left: 10, right: 10, 'text-align': 'center'}).text(localdata.settings.title))
				.append($('<div>').css({position: 'absolute', right: 5}).append($('<img>').attr({src: close_button.src})).on('click', function(ev){
					$this.dialog('remove');
				}))
				.appendTo(localdata.elements.box);

				localdata.elements.content = $('<div>').css({
					position: 'absolute',
					left: 0,
					right: 0,
					top: localdata.elements.titlebar.outerHeight(),
					bottom: 0,
					'border-bottom-left-radius': 10,
					'border-bottom-right-radius': 10,
					overflow: 'auto'
				}).appendTo(localdata.elements.box);
				if (localdata.settings.content !== undefined) {
					localdata.elements.content.append(localdata.settings.content);
				}
				else if (localdata.settings.url !== undefined) {
					localdata.elements.content.append($('<iframe>', {src: localdata.settings.url}).css({
						position: 'absolute',
						left: 0,
						right: 0,
						top: localdata.elements.titlebar.outerHeight(),
						bottom: 0,
						width: parseInt(localdata.settings.dim_x),
						height: parseInt(localdata.settings.dim_y) - localdata.elements.titlebar.outerHeight(),
						'border-bottom-left-radius': 10,
						'border-bottom-right-radius': 10,
					}));
				}
			});
		},
		/*===========================================================================*/

		title: function(str) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			if (str === undefined) {
				return localdata.settings.title;
			}
			else {
				localdata.settings.title = str;
				return this.each(function() {
					localdata.elements.titlebar.text(str);
				});
			}
		},

		remove: function(reason) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.elements.shade.remove();
				localdata.elements.shade = undefined;
				localdata.elements.box.remove();
				localdata.elements.box = undefined;
				if (localdata.settings.removeCB instanceof Function) {
					localdata.settings.removeCB(reason);
				}
			});
		},
		
		content: function(content) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			if (content === undefined) {
				return localdata.elements.content;
			}
			else {
				localdata.elements.content.html(content);
			}
		},
/*
		setFocus: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.fullscreen) return true;
				setFocus(localdata);
			});
		},

		getId: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');

			return localdata.settings.win_id;
		},
*/

		/*===========================================================================*/

	};

	$.fn.dialog = function(method) {
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