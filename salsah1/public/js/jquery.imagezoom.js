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

/**
 * @author: Lukas Rosenthaler lukas.rosenthaler@unibas.ch, Tobias Schweizer t.schweizer@unibas.ch
 *
 * Implementation of an image zoom
 *
 * The zoom plugin requires an <img>-Tag embedded witin a <div> tag which has set the
 * CSS-attributes width and height and overflow:hidden
 *
 * Constructor:
 * var zele = $(element).imagezoom();
 *
 * Public methods:
 *
 * var zf = zele.getZoomFac(); // returns the current zoom factor
 * var szf = zele.getSmallestZoomFac(); // returns the smallest posible zoomfactor (which displays the whole image within the <div>)
 * zele.zoom(zf, x, y); // Zooms with the given zoom factor so that the image position (x,y) stays static
 * zele.resize(new_win_w, new_win_h); // Changes the size of the enclosing <div>-Element to new_win_w and new_win_h
 *
 * Callbacks
 *
 * - config.whenLoaded(new_minzoom)
 * - config.whenZoomed(zoom_fac)
 *
 * Example:
 *
 * <div id="test" class="zoom" style="overflow: hidden; position: absolute; left: 200px; top: 100px; width: 500px; height: 700px; border-style: solid; border-width: 1px">
 * <img src="<?php echo SITE_URL, '/core/sendlocdata.php?res=20&qtype=full&reduce=1'; ?>" />
 * </div>
 *
 * <script>
 * function initit() {
 *   $('.zoom').imagezoom();
 * };
 *
 * function change_image() {
 *   $('#test img').attr('src', 'http://localhost/salsah/core/sendlocdata.php?res=48&qtype=full&reduce=1');
 * };
 *
 * function inc_size() {
 *   var w = $('#test').width() + 20;
 *   var h = $('#test').height() + 40;
 *   $('#test').data('imagezoom').resize(w, h);
 * }
 *
 * function dec_size() {
 *   var w = $('#test').width() - 40;
 *   var h = $('#test').height() - 20;
 *   $('#test').data('imagezoom').resize(w, h);
 * }
 *
 * @namespace Belongs to the $ jQuery structure
 * @param {HTML-DOM-Element} element The HTML-DOM-Element the plugin is assigned to
 * @param {JSON} settings
 * @constructor
 * @returns {$.imagezoom} A reference to this instance
 */

(function($) {
	var imagezoom = function(element, config) {
		var img = $(element).children('img');
		var canvas = $(element).children('canvas');
		var orig_w, orig_h; // original width and height of image
		var win_offs_x, win_offs_y; // used for mousewheel events to calculate the relative position of the pointer
		var win_w, win_h;
		var img_offs_x = 0, img_offs_y = 0;
		var zoom_fac = 1.0;
		var minzoom;
		var instance = this;
		var enabled = config.enabled;
		var img_w;
		var img_h;

		// private methods 
		

		/**
		 * Set a new image
		 * @param {URL} imgurl An accessible URL to an image
		*/
		this.setImageUrl = function(imgurl) {
			img.attr('src', imgurl);
			return;
		};
		//---------------------------------------------------------------------
		
		/**
		 * Get the actual zoom factor
		 * @returns {float} The actual zoom factor
		*/
		this.getZoomFac = function() {
			return zoom_fac;
		};
		//---------------------------------------------------------------------
		
		/**
		 * Get the smallest possible zoom factor
		 * @returns {float} The smallest possible zoom factor
		 */
		this.getSmallestZoomFac = function() {
			return minzoom;
		};
		//---------------------------------------------------------------------

		/**
		 * Get the actual indent of the image
		 * @returns {JSON} The actual indent of the image 
		 */
		this.getWinOffs = function() {
			return {x: win_offs_x, y: win_offs_y};
		};
		

		/**
		 * Get the actual indent of the image
		 * @returns {JSON} The actual indent of the image 
		 */
		this.getImgOffs = function() {
			return {x: img_offs_x, y: img_offs_y};
		};

		/**
		 * Activates the plugin
		 */
		this.enable = function() {
			enabled = true;
			return;
		};
		
		/**
		 * Deactivates the plugin
		 */
		this.disable = function() {
			enabled = false;
			return;
		};


		this.pan = function(dx, dy) {
			img_offs_x = img_offs_x + dx;
			img_offs_y = img_offs_y + dy;
			if (img_w < win_w) {
				//img_offs_x = Math.round((win_w - img_w)/2.0);
			}
			else {
				//img_offs_x = Math.round(pos_x - (pos_x - img_offs_x)*zoom_fac);
				if ((img_offs_x + img_w) < win_w) img_offs_x = win_w - img_w;
				if (img_offs_x > 0) img_offs_x = 0;				
			}
			
			if (img_h < win_h) {
				//img_offs_y = Math.round((win_h - img_h)/2.0);				
			}
			else {
				//img_offs_y = Math.round(pos_y - (pos_y - img_offs_y)*zoom_fac);
				if ((img_offs_y + img_h) < win_h) img_offs_y = win_h - img_h;
				if (img_offs_y > 0) img_offs_y = 0;				
			}
			img.css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
			canvas.each(function() {
				$(this).css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
			});
		}

		/**
		 * Zooms the image with the given factor and indent
		 * @param {float} zf The factor to zoom with
		 * @param {int} pos_x The horizontal indent
		 * @param {int} pos_y the vertical indent
 		 */
		this.zoom = function(zf, pos_x, pos_y) {
			if (zf < minzoom) zf = minzoom;
			if (zf > config.maxzoom) zf = config.maxzoom;
			
			img_w = Math.round(orig_w*zf);
			img_h = Math.round(orig_h*zf);
			
			img.width(img_w).height(img_h);
			canvas.each(function() {
				$(this).width(img_w).height(img_h);
				this.width = img_w;
				this.height = img_h;
			});

			if (img_w < win_w) {
				img_offs_x = Math.round((win_w - img_w)/2.0);
			}
			else {
				img_offs_x = Math.round(pos_x - (pos_x - img_offs_x)*zf / zoom_fac);
				if ((img_offs_x + img_w) < win_w) img_offs_x = win_w - img_w;
				if (img_offs_x > 0) img_offs_x = 0;				
			}
			
			if (img_h < win_h) {
				img_offs_y = Math.round((win_h - img_h)/2.0);				
			}
			else {
				img_offs_y = Math.round(pos_y - (pos_y - img_offs_y)*zf / zoom_fac);
				if ((img_offs_y + img_h) < win_h) img_offs_y = win_h - img_h;
				if (img_offs_y > 0) img_offs_y = 0;				
			}
			img.css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
			canvas.each(function() {
				$(this).css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
			});			
			zoom_fac = zf;
		};
		//---------------------------------------------------------------------
		
		/**
		 * Public method to reset the zoom widget
		 */
		this.reset = function() {
			win_w = $(element).width();
			win_h = $(element).height();
			var offs = $(element).offset();
			
			win_offs_x = offs.left;
			win_offs_y = offs.top;
		};
		//---------------------------------------------------------------------
		
		/**
		 * Public method to resize the enclosing div
		 * @param {int} new_w New width of the enclosing div
		 * @param {int} new_h New height of the enclosing div
		 */
		this.resize = function(new_w, new_h) {
			win_w = new_w;
			win_h = new_h;
			
			var new_minzoom;
			if ((1.0*win_w / win_h) < (1.0*orig_w / orig_h)) {
				new_minzoom = 1.0*win_w / orig_w;
			}
			else {
				new_minzoom = 1.0*win_h / orig_h;				
			}
			if (zoom_fac < new_minzoom) {
				init_to_minimal_zoom();
			}
			else {
				minzoom = new_minzoom;
				img_w = Math.round(orig_w*zoom_fac);
				img_h = Math.round(orig_h*zoom_fac);
				
				if (img_w < win_w) {
					img_offs_x = Math.round((win_w - img_w)/2.0);
				}
				else {
					if ((img_offs_x + img_w) < win_w) img_offs_x = win_w - img_w;
					if (img_offs_x > 0) img_offs_x = 0;				
				}

				if (img_h < win_h) {
					img_offs_y = Math.round((win_h - img_h)/2.0);				
				}
				else {
					if ((img_offs_y + img_h) < win_h) img_offs_y = win_h - img_h;
					if (img_offs_y > 0) img_offs_y = 0;				
				}

				img.css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
				canvas.each(function() {
					$(this).css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
				});

			}
			if (typeof config.whenLoaded == 'function') config.whenLoaded(new_minzoom);
		};
		//---------------------------------------------------------------------
		
		
		/**
		 * Private method to calculate and set the minimal zoom factor
		 */
		var init_to_minimal_zoom = function() {
			if ((1.0*win_w / win_h) < (1.0*orig_w / orig_h)) {
				zoom_fac = 1.0*win_w / orig_w;
				img_offs_x = 0;
				img_offs_y = Math.round((win_h - orig_h*zoom_fac)/2.0);
			}
			else {
				zoom_fac = 1.0*win_h / orig_h;
				img_offs_x = Math.round((win_w - orig_w*zoom_fac)/2.0);
				img_offs_y = 0;
			}
			minzoom = zoom_fac;

			var offs = $(element).offset();
			win_offs_x = offs.left;
			win_offs_y = offs.top;
			img_w = Math.round(orig_w*zoom_fac);
			img_h = Math.round(orig_h*zoom_fac);
            img.width(img_w).height(img_h).css({left: img_offs_x + 'px', top: img_offs_y + 'px'});
			canvas.each(function() {
				$(this).width(img_w).height(img_h).css({left: img_offs_x + 'px', top: img_offs_y + 'px'});
				this.width = img_w;
				this.height = img_h;
			});
		};
		//---------------------------------------------------------------------


		this.unbindAll = function() {
			event_element.unbind('.imagezoom');
			$(this).unbind('.imagezoom');
		};
		//---------------------------------------------------------------------

		//---------------------------------------------------------------------
		// On loading the image, the original dimensions of the image have to
		// be determined, as well as the minimal zoom factor (at which the whole
		// image is visible!)
		//
		img.on('load', function(){
			$(this).removeAttr('width').removeAttr('height').css({'width' : '', 'height' : ''});
			orig_w = this.width;
			orig_h = this.height;
			init_to_minimal_zoom();
			if (typeof config.whenLoaded == 'function') config.whenLoaded(minzoom);
		});
		//---------------------------------------------------------------------

		
		var event_element = canvas.length > 0 ? $(canvas.get(-1)) : img;

		//---------------------------------------------------------------------
		// mouse wheel event
		//
		event_element.bind('mousewheel.imagezoom', function(event, delta) {
			//window.status = 'x = ' + event.pageX + ' , y = ' + event.pageY;
			if(enabled) {
				var x = event.pageX - win_offs_x;
				var y = event.pageY - win_offs_y;
				instance.zoom(zoom_fac + config.mousewheel_fac*delta*zoom_fac, x, y);
				if (typeof config.whenZoomed == 'function') config.whenZoomed(zoom_fac);
			}
		});
		//---------------------------------------------------------------------

		event_element.bind('mousedown.imagezoom', function(event) {
			var panned = false;
			if(enabled) {
				//event.stopPropagation();
				event.preventDefault();
				var pos_x = event.pageX;
				var pos_y = event.pageY;
				$(element).css({'cursor' : 'pointer'});
				
				var handle_mousemove = function(event){
					img_offs_x = img_offs_x + (event.pageX - pos_x);
					img_offs_y = img_offs_y + (event.pageY - pos_y);

					if (img_w < win_w) {
						img_offs_x = Math.round((win_w - img_w)/2.0);
					}
					else {
						if ((img_offs_x + img_w) < win_w) img_offs_x = win_w - img_w;
						if (img_offs_x > 0) img_offs_x = 0;				
					}

					if (img_h < win_h) {
						img_offs_y = Math.round((win_h - img_h)/2.0);				
					}
					else {
						if ((img_offs_y + img_h) < win_h) img_offs_y = win_h - img_h;
						if (img_offs_y > 0) img_offs_y = 0;				
					}

					img.css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
					canvas.each(function() {
						$(this).css({'left' : img_offs_x + 'px', 'top' : img_offs_y + 'px'});
					});
					pos_x = event.pageX;
					pos_y = event.pageY;
					panned = true;
					if (typeof config.whenPanned == 'function') config.whenPanned(img_offs_x, img_offs_y);
				};
				
				var handle_mouseup = function(event) {
					event.stopPropagation();
					event.preventDefault();
					$(this).unbind('mousemove', handle_mousemove).unbind('mouseup', handle_mouseup).unbind('mouseout', handle_mouseout);
					$(element).css({'cursor' : 'auto'});
					if (panned && (typeof config.whenPanFinished == 'function')) config.whenPanFinished();
					panned = false;
				}
				
				var handle_mouseout = function(event){
					event.stopPropagation();
					event.preventDefault();
					$(this).unbind('mousemove', handle_mousemove).unbind('mouseup', handle_mouseup).unbind('mouseout', handle_mouseout);
					$(element).css({'cursor' : 'auto'});
					if (panned && (typeof config.whenPanFinished == 'function')) config.whenPanFinished();
					panned = false;
				}
				
				$(this).bind('mousemove.imagezoom', handle_mousemove).bind('mouseup.imagezoom', handle_mouseup).bind('mouseout.imagezoom', handle_mouseout);
			}
		});
		
		img.css({position: 'absolute'});
		canvas.each(function() {
			$(this).css({position: 'absolute'});
		});
		this.reset();
	}
	
	/**
	 * @author Lukas Rosenthaler lukas.rosenthaler@unibas.ch, Tobias Schweizer t.schweizer@unibas.ch
	 * Copyright (2009 - 2010) by Lukas Rosenthaler, Patrick Ryf, Tobias Schweizer
	 *
	 * Implementation of an image zoom 
	 * @namespace Belongs to the $.fn jQuery structure
	 * @param {JSON} options The options to set
	 * @returns {$.imagezoom} A reference to the instantiated sliderElement
	 */
	
	$.fn.imagezoom = function(settings) {
		var config = {maxzoom: 2.0, mousewheel_fac: 0.01, enabled : true};
		if (settings) $.extend(config, settings);
		
		var tmpobj;
		if (tmpobj = $(this).data('imagezoom')) {
			tmpobj.unbindAll();
			delete tmpobj;
		}
		tmpobj = new imagezoom(this, config);
		$(this).data('imagezoom', tmpobj);

		return tmpobj;
	}
	
})(jQuery);