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

/**
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @package jqplugins
 *
 * This plugin creates an edit-form for the properties of a resource
 *
 * <pre>
 *   <em>Title:</em><div class="propedit" data-propname="title" />
 *   <em>Autor:</em><div class="propedit" data-propname="author" />
 * </pre>
 *
 * <pre>
 *   <script type="text/javascript">
 *     $('div.propedit').propedit(resdata, propdata);
 *   </script>
 * </pre>
 */
(function( $ ){

	var spin_up = new Image();
	spin_up.src = SITE_URL + '/app/icons/spin-up.png';

	var spin_down = new Image();
	spin_down.src = SITE_URL + '/app/icons/spin-down.png';
	
	var methods = {
		/**
		* $(element).spinbox({'value': 12});
		*
		* Parameter: {
		*   value:	integer value [default: 1]
		* }
		*/
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {'value': 1};
				$.extend(localdata.settings, options);
				
				$this.append(String(localdata.settings.value));
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});			
		},
		
		/**
		* $(element).spinbox('edit', {'max': 10, 'defval': })
		*
		* Parameter: {
		*   min:	Minimum value (integer value [default: 1])
		*   max:	Maximum value (integer value [default: 9999])
		*   defval:	integer value [default: 1]
		* }
		*/
		edit: function(options) { // $(element).regions('init', {settings: here,...});
			return this.each(function() {
				var $this = $(this);
				var i;
				var localdata = {};
				localdata.settings = {
					'min': 1,
					'max': 9999,
					'value': 1,
				};
				$.extend(localdata.settings, options);

				var minstr = String(localdata.settings.min);
				var maxstr = String(localdata.settings.max);
				if (localdata.settings.max == -1) {
					maxstr = '9999';
				}
				var l = minstr.length > maxstr.length ? minstr.length : maxstr.length;
				var inele;
				$this.append(inele = $('<input>').attr({'type': 'text', 'size': l}).addClass('spinbox').val(localdata.settings.value));
				$this.append($('<span>').css({display: 'inline-block', position: 'relative', width: '12px', height: /*inele.height()*/ 17 + 'px', 'vertical-align': 'text-bottom', cursor: 'pointer'})
					.append($('<img>', {src: spin_up.src}).css({'position': 'absolute', 'top': '0px', 'right': '0px'}).hover(function(event){
						$(event.target).css({'border': '1px solid red'}); // hovering
					}, function(event){
						$(event.target).css({'border-style': 'none'}); // end hovering
					}).click(function(event){
						var newval = $this.find('.spinbox').val();
						newval++;
						if (newval <= localdata.settings.max) {
							$this.find('.spinbox').val(newval)
						}
						if (localdata.settings.onUpdateCB instanceof Function) {
							localdata.settings.onUpdateCB(newval);
						}
					}))
					.append($('<img>', {src: spin_down.src}).css({'position': 'absolute', 'bottom': '0px', 'right': '0px'}).hover(function(event){
						$(event.target).css({'border': '1px solid red'}); // hovering
					}, function(event){
						$(event.target).css({'border-style': 'none'}); //end hovering
					}).click(function(event){
						var newval = $this.find('.spinbox').val();
						newval--;
						if (newval >= localdata.settings.min) {
							$this.find('.spinbox').val(newval)
						}
						if (localdata.settings.onUpdateCB instanceof Function) {
							localdata.settings.onUpdateCB(newval);
						}
					}))
				);
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		/*===========================================================================*/
		
		value: function(value) {
			if (value !== undefined) {
				return this.each(function(){
					var $this = $(this);
					var localdata = $this.data('localdata');
					
					value = value < localdata.settings.min ? localdata.settings.min : value;
					value = value > localdata.settings.max ? localdata.settings.max : value;
					
					$this.find('.spinbox').val(value);
				});
			}
			
			var $this = $(this);
			var localdata = $this.data('localdata');
			return $this.find('.spinbox').val();
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
	
	
	$.fn.spinbox = function(method) {
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