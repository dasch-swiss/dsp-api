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

	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					positioning: 'middle', // 'upper', 'middle', 'lower', 'exact'
					xpos: 100,
					ypos: 100, 
					width: -1,
					height: -1,
					modal: true,
					html: '',
					focus: undefined
				};
				$.extend(localdata.settings, options);
				
				$this.append(String(localdata.settings.value));
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				
				var shade = $('<div>', {id: 'shade'}).css({
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

				var w = $this.outerWidth();
				var h = $this.outerHeight();
				alert('w: ' + w + ' h: ' + h + ' BH:' + $('body').height());


				var box = $('<div>').css({
					display: 'block',
					zIndex: 99,
					position: 'fixed',
					left: String(xpos) + 'px',
					top: String(ypos) + 'px',
				}).addClass('loginbox') // should be 'dialogclass'
				.html(localdata.settings.html)
				.appendTo('body');
				
				var width;
				var height;
				
				if (localdata.settings.width < 0) {
					 width = box.outerWidth();
				}
				else {
					width = localdata.settings.width;
					box.css('width', width);
				}
				
				if (localdata.settings.height < 0) {
					 height = box.outerHeight();
				}
				else {
					height = localdata.settings.height;
					box.css('height', height);
				}
				
				alert('Width: ' + width + ' Height: ' + height);
				
				var xpos = (w - width) / 2;
				var ypos;
				switch (localdata.settings.positioning) {
					case 'upper': {
						ypos = (h - height) / 3;
						break;
					}
					case 'middle': {
						ypos = (h - height) / 2;
						break;
					}
					case 'lower': {
						ypos = 2*(h - height) / 3;
						break;
					}
					case 'exact': {
						xpos = localdata.settings.xpos;
						ypos = localdata.settings.ypos;
						break;
					}
					default: {
						ypos = (h - height) / 2;
					}
				}
				box.css({
					left: String(xpos) + 'px',
					top : String(ypos) + 'px',
				});
			});
			if (localdata.settings.focus !== undefined) {
				box.find(localdata.settings.focus).focus();
			}		
		},
	};

	$.fn.dialogbox = function(method) {
	    // Method calling logic
	    if ( methods[method] ) {
	      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
	    } else if ( typeof method === 'object' || ! method ) {
	      return methods.init.apply( this, arguments );
	    } else {
	      throw 'Method ' +  method + ' does not exist on jQuery.dialogbox';
	    }		
	};
	/*===========================================================================*/

})( jQuery );