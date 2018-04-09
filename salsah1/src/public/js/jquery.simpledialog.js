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

(function($) {
	jQuery.fn.simpledialog = function(dbox_name, options) {
		var dialogbox = $('#' + dbox_name);
		if ((typeof options == 'undefined') || (typeof options == 'object')) {
			var origstate = dialogbox.html();
			dialogbox.css({'display' : 'none'});
			var settings = jQuery.extend({
				width: 300,
			    height: -1,
				positioning: 'middle',
				focus: undefined
			}, options);
			if (typeof $.fn.simpledialog.shade == 'undefined') {
				$.fn.simpledialog.shade = $('<div>', {id: 'shade'}).css({
					position: 'fixed',
					left: 0,
					top: 0,
					right: 0,
					bottom: 0,
					opacity: 0.6,
					filter: 'alpha(opacity=60)',
					display: 'none',
					backgroundColor: 'rgb(255, 255, 255)'
				});
				$.fn.simpledialog.shade.appendTo("body");
			}
			this.css({cursor: 'pointer'});
			this.click(function(ev) {
				var w = $(window).width();
				var h = $(window).height();
				$.fn.simpledialog.shade.css({'display' : 'block'});
				//
				// now we calculate the position of the login window
				//
				var xpos = (w - settings.width) / 2;
				var ypos;
				switch (settings.positioning) {
					case 'upper': {
						ypos = (h - settings.height) / 3;
						break;
					}
					case 'middle': {
						ypos = (h - settings.height) / 2;
						break;
					}
					case 'lower': {
						ypos = 2*(h - settings.height) / 3;
						break;
					}
					case 'exact': {
						xpos = settings.xpos;
						ypos = settings.ypos;
					}
					default: {
						ypos = (h - settings.height) / 2;						
					}
				}
				dialogbox.css({
					display: 'block',
					zIndex: 99,
					position: 'fixed',
					left: String(xpos) + 'px',
					top: String(ypos) + 'px',
				}).width(settings.width);
				if (settings.height > 0) {
					dialogbox.height(settings.height);
				}
				if (typeof settings.iframe == 'string') {
					$('<iframe>', {src: settings.iframe}).appendTo(dialogbox);
				}
				if (settings.focus !== undefined) {
					$(settings.focus).focus();
				}
			});
		}
		else {
			switch(typeof options) {
				case 'string': {
					dialogbox.css({display : 'none'});
					$.fn.simpledialog.shade.css({display: 'none'});
					break;
				}
			}
		}
		return this;
	};
	
})(jQuery);

