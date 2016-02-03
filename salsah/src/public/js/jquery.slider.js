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

/**
 * Usage:
 *
 * var settings = {button_imgsrc: 'http://a.b.c/button.png', button_width: 10, button_height: 10};
 * $(ele).slider(settings);
 *
 * Settings:
 * - horizontal: true|false; if true, a horizontal slider is constructed, if false the slider is vertical
 * - min: float; minmal value
 * - max: float; maximal value
 * - incstep: float; steps for incrementing, decrementing the value when clicken lft/right or below/above the slider button
 * - start_position: float; initial position of slider
 * - show_position : true|false; show a balloon help with the position while sliding
 * - enabled : true|false; NOT In USE
 * - button_width: integer; width of the slider button. Will be overrided by the CSS value if not protected by ":override", e.g. '16:override'
 * - button_height: integer; height of the slider button. Will be overrided by the CSS value if not protected by ":override", e.g. '16:override'
 * - setButtonMoveBeginCB: null | function(); Callback function, called when the mouse button goes down on the slider button to start sliding it
 * - buttonMovingCB: null | function(slider_pos); Callback function, slider_pos is the slider value (not pixel!)
 * - buttonMovedCB: null | function(slider_pos); Callback function, slider_pos is the slider value (not pixel!)
 *
 * Methods:
 * - slider('init', settings): initialize a slider
 * - slider('resize', dx, dy): Resize the slider by adding the increment dx (horizontal slider) or dy (vertical slider) to the slider size
 * - slider('setRange', min, max): set the slider range from "min" (float) to "max (float)"
 * - slider('setValue', val): set the current value of the slider
 * - slider('setButtonMoveBeginCB', function(localdata, val){}): Set the callback function which is called when the slider is about to being moved
 * - slider('setButtonMovingCB', function(localdata, val){}): Set the callback function that is called while the slider is being moved
 * - slider('setButtonMovedCB', function(localdata, val){}): Set the callback function that is called after the slider has been moved
 * - slider('setBalloonHtml', htmlstr): Set the content of the slider balloon to "htmlstr"
 * - slider('setBalloonElements', jQElement | Array of JQElements) Set the content of the slider balloon to the jQuery element or to the array of jQuery elements
 */
(function( $ ){
	
	//
	// private method to convert the slider position to a pixel value (integer)
	//
	var position_to_px = function(localdata, pos) {
		return parseInt((pos - localdata.settings.min)*localdata.slider_length / (localdata.settings.max - localdata.settings.min));
	};
	/*===========================================================================*/

	
	//
	// private method to convert a pixel value to a slider position
	//
	var px_to_position = function(localdata, pos) {
		return pos*(localdata.settings.max - localdata.settings.min)/localdata.slider_length + localdata.settings.min;
	};
	/*===========================================================================*/

	
	var set_slider_pos = function(localdata, pos, is_pixelpos) {
		if (pos === undefined) {
			pos = localdata.slider_button.position;
			is_pixelpos = false;
		}
		
		var newpos;	
		if (is_pixelpos) {
			newpos = pos;
		}
		else {
			// we need pixel position as float value...
			newpos = (pos - localdata.settings.min)*localdata.slider_length / (localdata.settings.max - localdata.settings.min);
		}
		if (localdata.settings.horizontal) {
			if (newpos < 0) newpos = 0;
			if (newpos > localdata.slider_length) newpos = localdata.slider_length;
			localdata.slider_button.left = newpos;
			localdata.slider_button.ele.css('left', localdata.slider_button.left);
			
			//
			// calculate balloon position
			//
			var balloon_pos = localdata.slider_button.left - parseInt(localdata.slider_balloon.ele.width()/2);
			if (balloon_pos < 0) balloon_pos = 0;
			if ((balloon_pos) + localdata.slider_balloon.ele.width() > (localdata.slider_length + localdata.slider_button.ele.width())) {
				balloon_pos = localdata.slider_length + localdata.slider_button.ele.width() - localdata.slider_balloon.ele.width();
			} 
			//localdata.slider_balloon.ele.css('left', localdata.slider_button.left);
			localdata.slider_balloon.ele.css('left', balloon_pos);
		}
		else {
			if (newpos < 0) newpos = 0;
			if (newpos > localdata.slider_length) newpos = localdata.slider_length;
			localdata.slider_button.bottom = newpos;						
			localdata.slider_button.ele.css('bottom', localdata.slider_button.bottom);
			localdata.slider_balloon.ele.css('bottom', localdata.slider_button.bottom);
		}
		localdata.slider_button.position = px_to_position(localdata, newpos);
	}
	/*===========================================================================*/


	var methods = {
		init: function(options) { // $(element).navigator('init', {settings: here,...});

			return this.each(function(){
				var $this = $(this);
				
				//
				// local, private callback function which is called if the slider button is being moved
				//
				var movingButton = function(dd) {
					
					if (localdata.settings.horizontal) {
						set_slider_pos(localdata, slider_button.left + dd, true);
					}
					else {
						set_slider_pos(localdata, slider_button.bottom + dd, true);
					}
					//
					// now call the external callback if given...
					//
					if (localdata.settings.buttonMovingCB instanceof Function) {
						localdata.settings.buttonMovingCB(slider_button.position);
					}
				}
				/*===========================================================================*/
				
				
				var localdata = {};
				var slider_button = {};
				localdata.slider_button = slider_button;
				
				localdata.settings = {
					horizontal: true,
					min: 0,
					max: 100,
					incstep: 1,
					start_position : 0,
					show_position : true,
					enabled : true,
					button_width: 10,
					button_height: 10,
					buttonMoveBeginCB: null, // function(pos)
					buttonMovingCB: null, // function(pos)
					buttonMovedCB: null,
				};
				$.extend(localdata.settings, options);

				if (localdata.settings.horizontal) {
					slider_button.ele = $('<div>', {'class': 'sliderButton'}).css({position: 'absolute', bottom: 0, left: 0}).appendTo($this);
					slider_button.left = 0;
				}
				else {
					slider_button.ele = $('<div>', {'class': 'sliderButton'}).css({position: 'absolute', right: 0, bottom: 0}).appendTo($this);
					slider_button.bottom = 0;
				}
				
				if (slider_button.ele.width() == 0) { // there's no width from the external CSS-files!
					slider_button.ele.css('width', localdata.settings.button_width + 'px');
				}
				if (slider_button.ele.height() == 0) { // there's no width from the external CSS-files!
					slider_button.ele.css('height', localdata.settings.button_height + 'px');
				}
				if (localdata.settings.button_bgcol) { // there's no background color  from the external CSS-files!
					slider_button.ele.css('background-color', localdata.settings.button_bgcol);
				}
				if (localdata.settings.horizontal) {
					localdata.slider_length = $this.width() - slider_button.ele.outerWidth();
					slider_button.ele.css('left', position_to_px(localdata, localdata.settings.start_position));
				}
				else {
					localdata.slider_length = $this.height() - slider_button.ele.outerHeight();					
					slider_button.ele.css('bottom', position_to_px(localdata, localdata.settings.start_position));
				}
				
				//
				// now set the slider position to the initial value
				//
				slider_button.position = localdata.settings.start_position; //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				if (localdata.settings.horizontal) {
					slider_button.left = position_to_px(localdata, slider_button.position);
					slider_button.ele.css('left', slider_button.left + 'px');
				}
				else {
					slider_button.bottom = position_to_px(localdata, slider_button.position);
					slider_button.ele.css('bottom', slider_button.bottom);
				}
				
				//
				// add the slider balloon...
				//
				localdata.slider_balloon = {};
				if (localdata.settings.horizontal) {
					localdata.slider_balloon.ele = $('<div>', {'class': 'sliderBalloon'}).css({
						position: 'absolute',
						display: 'none',
						bottom: $this.outerHeight() + 10 + 'px',
						left: slider_button.left,
					}).appendTo($this);
				}
				else {
					localdata.slider_balloon.ele = $('<div>', {'class': 'sliderBalloon'}).css({
						position: 'absolute',
						display: 'none',
						left: $this.outerWidth() + 10 + 'px',
						bottom: slider_button.bottom,
					}).appendTo($this);
				}
				
				
				//
				// Handle the mouse events for slider movements
				//
				slider_button.ele.mousedown(function(event) {
					event.stopPropagation();
					event.preventDefault();
					var pos_x = event.pageX;
					var pos_y = event.pageY;
					if (localdata.settings.buttonMoveBeginCB instanceof Function) {
						localdata.settings.buttonMoveBeginCB(slider_button.position);
					}
					localdata.slider_balloon.ele.css('display', 'block');
					//var soffs = slider_button.ele.offset(); // !!!!!! FOR CANCEL
					//var old_slider_pos = slider_button.position; // !!!!!! FOR CANCEL

					$('#ecatcher').css({display: 'block'}).mousemove(function(event) {
						var bpos;
						if (localdata.settings.horizontal) {
							movingButton(event.pageX - pos_x);
							/*
							if (event.pageY > (soffs.top +  slider_button.ele.outerHeight())) {
								//
								// Cancel slider movement
								//
								$('#ecatcher').css({display: 'none'}).unbind();  // !!!!!! FOR CANCEL
								set_slider_pos(localdata, old_slider_pos, false);  // !!!!!! FOR CANCEL
							}
							*/
						}
						else {
							movingButton(pos_y - event.pageY);
						}
						pos_x = event.pageX;
						pos_y = event.pageY;
					}).mouseup(function() {
						$('#ecatcher').css({display: 'none'}).unbind();
						localdata.slider_balloon.ele.css('display', 'none');
						if (localdata.settings.buttonMovedCB instanceof Function) {
							localdata.settings.buttonMovedCB(slider_button.position);
						}
					}).mouseout(function(){
						$('#ecatcher').css({display: 'none'}).unbind();
						localdata.slider_balloon.ele.css('display', 'none');
						if (localdata.settings.buttonMovedCB instanceof Function) {
							localdata.settings.buttonMovedCB(slider_button.position);
						}
					});
				});
				
				//
				// Handle clicks in the slider area right/left or belowe/above the slider button
				//
				$this.mousedown(function(event){
					event.stopPropagation();
					event.preventDefault();
					$this.mouseup(function(event){
						var soffs = slider_button.ele.offset();
						var new_pos;
						if (localdata.settings.horizontal) {
							if (event.pageX < soffs.left) {
								new_pos = slider_button.position - localdata.settings.incstep;
								if (new_pos < localdata.settings.min) new_pos = localdata.settings.min;
							}
							else if (event.pageX > soffs.left) {
								new_pos = slider_button.position + localdata.settings.incstep;
								if (new_pos > localdata.settings.max) new_pos = localdata.settings.max;
							}
						}
						else {
							if (event.pageY < soffs.top) {
								new_pos = slider_button.position + localdata.settings.incstep;
								if (new_pos > localdata.settings.max) new_pos = localdata.settings.max;
							}
							else if (event.pageY > soffs.top) {
								new_pos = slider_button.position - localdata.settings.incstep;
								if (new_pos < localdata.settings.min) new_pos = localdata.settings.min;
							}						
						}
						set_slider_pos(localdata, new_pos, false);
						if (localdata.settings.buttonMovingCB instanceof Function) {
							localdata.settings.buttonMovingCB(slider_button.position);
						}
						if (localdata.settings.buttonMovedCB instanceof Function) {
							localdata.settings.buttonMovedCB(slider_button.position);
						}
						$this.unbind(event);
					});
				});
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		/*===========================================================================*/
		
		resizeRelative: function(dx, dy) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.horizontal) {
					localdata.slider_length = localdata.slider_length + dx;
				}
				else {
					localdata.slider_length = localdata.slider_length + dy;
				}
				set_slider_pos(localdata);
			});
		},
		/*===========================================================================*/
		
		resizeAbsolute: function(elesize) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.horizontal) {
					localdata.slider_length = elesize - localdata.slider_button.ele.outerWidth();
				}
				else {
					localdata.slider_length = elesize - localdata.slider_button.ele.outerHeight();
				}
				set_slider_pos(localdata);
			});
		},
		/*===========================================================================*/
		
		setButtonImage: function(src) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
			// NOT YET IMPLEMENTED !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		},
		/*===========================================================================*/
		
		setRange: function(min, max) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.min = min;
				localdata.settings.max = max;
				if (localdata.slider_button.position < min) {
					set_slider_pos(localdata, min, false);
				}
				else if (localdata.slider_button.position > max) {
					set_slider_pos(localdata, max, false);
				}
				else {
					set_slider_pos(localdata);
				}
			});			
		},
		/*===========================================================================*/
		
		setValue: function(pos) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				set_slider_pos(localdata, pos, false);
			});						
		},
		/*===========================================================================*/
		
		stepUp: function(pos) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.horizontal) {
					new_pos = localdata.slider_button.position + localdata.settings.incstep;
					if (new_pos > localdata.settings.max) new_pos = localdata.settings.max;
				}
				else {
					new_pos = localdata.slider_button.position - localdata.settings.incstep;
					if (new_pos < localdata.settings.min) new_pos = localdata.settings.min;					
				}
				set_slider_pos(localdata, new_pos, false);
				if (localdata.settings.buttonMovedCB instanceof Function) {
					localdata.settings.buttonMovedCB(localdata.slider_button.position);
				}
			});						
		},
		/*===========================================================================*/
		
		stepDown: function(pos) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.horizontal) {
					new_pos = localdata.slider_button.position - localdata.settings.incstep;
					if (new_pos < localdata.settings.min) new_pos = localdata.settings.min;					
				}
				else {
					new_pos = localdata.slider_button.position + localdata.settings.incstep;
					if (new_pos > localdata.settings.max) new_pos = localdata.settings.max;
				}
				set_slider_pos(localdata, new_pos, false);
				if (localdata.settings.buttonMovedCB instanceof Function) {
					localdata.settings.buttonMovedCB(localdata.slider_button.position);
				}
			});						
		},
		/*===========================================================================*/
		setButtonMoveBeginCB: function(func) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.buttonMoveBeginCB = func; //function(localdata, pos)
			});						
		},
		/*===========================================================================*/
		
		setButtonMovingCB: function(func) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.buttonMovingCB = func; //function(localdata, pos)
			});						
		},
		/*===========================================================================*/
		
		setButtonMovedCB: function(func) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.buttonMovedCB = func; //function(localdata, pos)
			});						
		},
		/*===========================================================================*/
		
		setBalloonHtml: function(htmlstr) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.slider_balloon.ele.html(htmlstr);

				//
				// if we loaded an image with possibly a different dimension, we may have to adjust the position of the balloon
				//
				localdata.slider_balloon.ele.find('img').on('load', function(event) {
					var balloon_pos = localdata.slider_button.left - parseInt(localdata.slider_balloon.ele.width()/2);
					if (balloon_pos < 0) balloon_pos = 0;
					if ((balloon_pos) + localdata.slider_balloon.ele.width() > (localdata.slider_length + localdata.slider_button.ele.width())) {
						balloon_pos = localdata.slider_length + localdata.slider_button.ele.width() - localdata.slider_balloon.ele.width();
					} 
					localdata.slider_balloon.ele.css('left', balloon_pos);
				});

			});
		},
		/*===========================================================================*/
		
		setBalloonElements: function(elements) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (elements instanceof Array) {
					for (var i in elements) {
						localdata.slider_balloon.ele.append(elements[i]);
					}
				}
				else {
					localdata.slider_balloon.ele.append(elements);
				}

				//
				// if we loaded an image with possibly a different dimension, we may have to adjust the position of the balloon
				//
				localdata.slider_balloon.ele.find('img').on('load', function(event){
					var balloon_pos = localdata.slider_button.left - parseInt(localdata.slider_balloon.ele.width()/2);
					if (balloon_pos < 0) balloon_pos = 0;
					if ((balloon_pos) + localdata.slider_balloon.ele.width() > (localdata.slider_length + localdata.slider_button.ele.width())) {
						balloon_pos = localdata.slider_length + localdata.slider_button.ele.width() - localdata.slider_balloon.ele.width();
					} 
					localdata.slider_balloon.ele.css('left', balloon_pos);
				});

			});
		},
		/*===========================================================================*/
		
	}
	/*===========================================================================*/
	
	$.fn.slider = function(method) {
		
	    // Method calling logic
	    if ( methods[method] ) {
	      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
	    } else if ( typeof method === 'object' || ! method ) {
	      return methods.init.apply( this, arguments );
	    } else {
	      throw 'Method ' +  method + ' does not exist on jQuery.slider';
	    }		
	};
	/*===========================================================================*/
	
})( jQuery );