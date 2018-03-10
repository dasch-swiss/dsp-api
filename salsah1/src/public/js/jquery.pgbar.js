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

(function( $ ){

	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					minval: 0.0,
					maxval: 100.0,
					show_percent: true,
					show_numbers: false,
					pgbar_img_path: SITE_URL + '/app/icons/progress_bar.gif',
					cancel: undefined
				};
				localdata.ele = {};
				$.extend(localdata.settings, options);
				
				$this.css({
					backgroundImage: 'url(\'' + localdata.settings.pgbar_img_path + '\')',
					padding: 5,
					borderStyle: 'solid',
					borderWidth: 1,
				}).append(localdata.ele.shade = $('<div>').css({
					position: 'absolute',
					left: 0,
					top: 0,
					right: 0,
					bottom: 0,
					backgroundColor: '#ddd',
				}));
				var msg = '';
				if (localdata.settings.show_percent) {
					msg += '0% done';
				}				
				if (localdata.settings.show_numbers) {
					if (localdata.settings.show_percent) msg += ' (';
					msg += '0 of ' + Math.round(localdata.settings.maxval - localdata.settings.maxval).toString();
					if (localdata.settings.show_percent) msg += ')';
					else msg += ' done';
				}
				if (localdata.settings.show_percent || localdata.settings.show_numbers) {
					$this.append(localdata.ele.donetxt = $('<div>').css({
						position: 'absolute',
						left: 10,
						right: 10,
						margin: '0px auto',
						width: '50%',
						textAlign: 'center',
						fontWeight: 'bolder'
					}).text(msg));
				}
				if (localdata.settings.cancel instanceof Function) {
					$this.append($('<div>').append('').on('click', function(event){
						localdata.settings.cancel();
					}));
				}
				//append(localdata.settings.minval + '% done');
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});			
		},
		/*===========================================================================*/

		minval: function(minval) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (minval === undefined) {
					return localdata.settings.minval
				}
				else {
					localdata.settings.minval = minval;
				}
			});
		},
		/*===========================================================================*/

		maxval: function(max) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (maxval === undefined) {
					return localdata.settings.maxval
				}
				else {
					localdata.settings.maxval = maxval;
				}
			});
		},
		/*===========================================================================*/

		update: function(val,maxval) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (maxval !== undefined) {
					localdata.settings.maxval = maxval;
				}
				var doneval = (val - localdata.settings.minval) / (localdata.settings.maxval - localdata.settings.minval);
				var msg = '';
				if (localdata.settings.show_percent) {
					msg += Math.round(100*doneval).toString() + '% done';
				}				
				if (localdata.settings.show_numbers) {
					if (localdata.settings.show_percent) msg += ' (';
					msg += val + ' of ' + Math.round(localdata.settings.maxval - localdata.settings.minval).toString();
					if (localdata.settings.show_percent) msg += ')';
					else msg += ' done';
				}

				if (localdata.settings.show_percent || localdata.settings.show_numbers) {
					localdata.ele.donetxt.text(msg);
				}
				var left = parseInt($this.width()*doneval);
				localdata.ele.shade.css({left: left});
			});
		},
		/*===========================================================================*/

		anotherMethod: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		},
		/*===========================================================================*/
	}
	
	$.fn.pgbar = function(method) {
	    // Method calling logic
	    if ( methods[method] ) {
	      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
	    } else if ( typeof method === 'object' || ! method ) {
	      return methods.init.apply( this, arguments );
	    } else {
	      throw 'Method ' +  method + ' does not exist on jQuery.pgbar';
	    }
	};
})( jQuery );