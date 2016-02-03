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
 * @author: Tobias Schweizer <t.schweizer@unibas.ch>
 *
 * Implementation of a number range from position min to max 
 * @namespace Belongs to the $ jQuery structure
 * @param {HTML-DOM-Element} element The HTML-DOM-Element the plugin is assigned to
 * @param {JSON} settings The options to set
 * @constructor
 * @returns {$.numberRange} A reference to this instance
 */


(function($) {
	$.numberRange = function(element, settings) {
		// declare and initialise vars on global scope
		var currentPosition = settings.min;
		var enabled = settings.enabled;
		
		if (settings.start_position >= settings.min && settings.start_position <= settings.max){
			currentPosition = settings.start_position;
		}
		
		/**
		 * Draws the number range.
		 * Executed on instantiation and on changes
		 */
		var drawRange = function() {
			var begin = currentPosition - settings.range*settings.interval;
			var end = currentPosition + settings.range*settings.interval;
			
			// make sure that begin is not smaller than the allowed min
			while (begin < settings.min) {
				begin = begin + settings.interval;
			}
			
			// make sure that end is not smaller than the allowed max
			while (end > settings.max) {
				end = end - settings.interval;
			}
			
			$(element).empty();
			var currentNumber;
			for (var i = begin; i <= end; i = i + settings.interval) {
				if (i == begin) {
					if (settings.multiple_steps !== false) {
						currentNumber = $.create('div').addClass('prevM').text('<<');
						$(currentNumber).appendTo(element);
					}
					if (settings.single_step) { 
						currentNumber = $.create('div').addClass('prevS').text('<');
						$(currentNumber).appendTo(element);
					}
				} 
				
				currentNumber = $.create('div').addClass('number').text(i);
				
				if (i == currentPosition) {
					$(currentNumber).removeClass('number').addClass('active');
				}
				$(currentNumber).appendTo(element);
				
				if (i == end) {
					if (settings.single_step) { 
					       currentNumber = $.create('div').addClass('nextS').text('>');
					       $(currentNumber).appendTo(element); 
					}
					if (settings.multiple_steps !== false) {
					       currentNumber = $.create('div').addClass('nextM').text('>>');
					       $(currentNumber).appendTo(element);
					}
				}
			}
			
			$(element).find('.number').hover(function() {
				if (enabled) {
					settings.when_hovered(this, parseInt($(this).text()));
				}
				return;
			}, function() {
				if (enabled) {
					settings.after_hover(this, parseInt($(this).text()));
				}
				return;
			});
			
			$(element).find('.number').bind('click', function() {
				if (enabled) {
					currentPosition = parseInt($(this).text());
					drawRange();
					settings.after_change(element, currentPosition);
				}
				return;
			});
			
			$(element).find('.prevM').bind('mousedown', function() {
				$(this).addClass('prevMdown');
				return;
			});
			
			$(element).find('.prevM').bind('click', function() {
				if (enabled) {
					var prev = currentPosition - settings.multiple_steps*settings.interval; 
					while (prev < settings.min) {
						prev = prev + settings.interval;
					}
					currentPosition = prev;
					drawRange();
					settings.after_change(element, currentPosition);
					$(this).removeClass('prevMdown');
				}
				return;
			});
			
			$(element).find('.nextM').bind('mousedown', function() {
				$(this).addClass('nextMdown');
				return;
			});
			
			$(element).find('.nextM').bind('click', function() {
				if (enabled) {
					var next = currentPosition + settings.multiple_steps*settings.interval; 
					while (next > settings.max) {
						next = next - settings.interval;
					}
					currentPosition = next;
					drawRange();
					settings.after_change(element, currentPosition);
					$(this).removeClass('nextMdown');
				}
				return;
			});
			
			$(element).find('.prevS').bind('click', function() {
				if (enabled) {
					var prev = currentPosition - settings.interval; 
					if (prev < settings.min) {
						prev = settings.min;
					}
					currentPosition = prev;
					drawRange();
					settings.after_change(element, currentPosition);
				}
				return;
			});
			
			$(element).find('.nextS').bind('click', function() {
				if (enabled) {
					var next = currentPosition + settings.interval; 
					if (next > settings.max) {
						next = settings.max;
					}
					currentPosition = next;
					drawRange();
					settings.after_change(element, currentPosition);
				}
				return;
			});
		};
		
		/**
		 * Sets the position.
		 * @param {float} position The position to set
		 */
		this.setPosition = function(position) {
			if (position >= settings.min && position <= settings.max){
				currentPosition = position;
				drawRange();
			}
			return;
		};

		/**
		 * Returns the current position.
		 * @returns {float} The current position 
		 */ 
		this.getPosition = function() {
			return currentPosition;
		};

		/**
		 * Sets the range of visible positions to choose from (upside and downside)
		 * @param {int} range The new range to set 
		 */
		this.setRange = function(range) {
		    range = parseInt(range);
		    if (range >= 0) {
				settings.range = range;
				drawRange();
				return;
		    } 
		    return;
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
		
		// executed when instantiated
		drawRange();
	}
	
	/**
	 * @author: Tobias Schweizer <t.schweizer@unibas.ch>
	 * Copyright (2009 - 2010) by Lukas Rosenthaler, Patrick Ryf, Tobias Schweizer
	 *
	 * Implementation of a number range from position min to max 
	 * @namespace Belongs to the $.fn jQuery structure
	 * @param {JSON} options The options to set
	 * @returns {$.numberRange} A reference to the instantiated numberRange
	 */
	
	$.fn.numberRange = function(options) {
		// default settings
		var settings = {
			min : 0,
			max : 100,
			interval : 1,
			color : '#000000',
			start_position : 0,
			range : 3,
			multiple_steps : 5,
			single_step : true,
			enabled : true,
			when_hovered : function(){}, 
			after_hover : function(){}, 
			after_change: function(){}
		};
		
		// overwrite the default settings with the given options
		settings = $.extend({}, settings, options);
		
		if (typeof(settings.when_hovered) != 'function') {
			settings.when_hovered = function(){};
		}
		
		if (typeof(settings.after_hover) != 'function') {
			settings.after_hover = function(){};
		}
		
		if (typeof(settings.after_change) != 'function') {
			settings.after_change = function(){};
		}
		
		// create an instance of the numberrabge object and return it 
		$(this).data('numberRange', new $.numberRange(this, settings));
		
		return $(this).data('numberRange');
	}
})(jQuery);