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
			return this.each(function(){
				var $this = $(this);
				var localdata = {};
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				
				localdata.settings = {
				};
				$.extend(localdata.settings, options);
			});
		},
		/*===========================================================================*/

		makeDraggable: function(datatype, dataobj, ondragend) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata === undefined) {
					localdata = {};
					$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				}
				$this.css({cursor: 'url(' + SITE_URL + '/app/icons/drag_cursor.png),pointer'}).attr('draggable', true).bind('dragstart', function(event) {
					if (typeof dataobj === 'object') {
						dataobj.originator = 'SALSAH';
						dataobj.datatype = datatype;
						var baseurl = location.origin;
						if (location.pathname != '') {
							var pos = location.pathname.indexOf('/resources/');
							if (pos > -1) {
								baseurl += location.pathname.substr(0, pos + 1);
							}
							else {
								baseurl += location.pathname;
							}
						}
						event.originalEvent.dataTransfer.setData('application/json', JSON.stringify(dataobj));
						event.originalEvent.dataTransfer.setData('text/plain', baseurl + 'resources/' + dataobj.resid);
						event.originalEvent.dataTransfer.setData('text/html', '<a href="' + baseurl + 'resources/' + dataobj.resid + '">' + baseurl + 'resources/' + dataobj.resid + '</a>');
					}	
					return true;
				}).on('mousedown', function(event) {event.stopPropagation()}); // prevent change of focus: mousedown is NOT propagated to win (setFocus)
				if (ondragend instanceof Function) {
					$this.bind('dragend', function(event) {
						ondragend(event);
						return true;
					});
				}
			});
		},
		/*===========================================================================*/

		makeDropable: function(ondrop) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata === undefined) {
					localdata = {};
					$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				}
				$this.bind('dragover', function(event){
					event.preventDefault();
					//window.status = ' DRAG-OVER';
					return false;
				}).bind('dragleave', function(event) {
					event.preventDefault();
					//window.status = ' DRAG-LEAVE';
					return false;
				}).bind('dragenter', function(event){
					event.preventDefault();
					//window.status = ' DRAG-ENTER';
					return false;
				}).bind('drop', function(event) {
					event.preventDefault();
					if (ondrop instanceof Function) {
						ondrop(event, JSON.parse(event.originalEvent.dataTransfer.getData('application/json')), this);
					}
					return false;
				});
			});
		},
		/*===========================================================================*/

	};
	
	$.fn.dragndrop = function(method) {
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