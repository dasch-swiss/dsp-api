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
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @author Tobias Schwizer <t.schweizer@unibas.ch>
 * @package jqplugins
 *
 */
(function( $ ){

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
				localdata.settings = {
					onChangeCB: undefined,
					selection_id: undefined, // e.g. webern:textcolor, or numeric selection_id
					value: undefined, // inital value, if not overridden by options!
					use_val_names: false, //if true, use the names of the selection nodes (instead of the id's)
				};
				localdata.ele = {};
				$.extend(localdata.settings, options);

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				SALSAH.ApiGet('selections', localdata.settings.selection_id, {}, function(data) {
					var dataval;
					if (data.status == 0) {
						if (localdata.settings.value === undefined) {
							localdata.settings.value = localdata.settings.use_val_names ? data.selection[0].name : data.selection[0].id;
						}
						for (var i in data.selection) {
							dataval = localdata.settings.use_val_names ? data.selection[i].name : data.selection[i].id;
							if (dataval == localdata.settings.value) {
								$this.prepend(String(data.selection[i].label));
							}
						}
					}
					else {
						alert('data.errormsg');
					}
				});
			});
		},

		edit: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata;
				if ((options !== undefined) && (options instanceof Object)) {
					localdata = {};
					localdata.settings = {
						onChangeCB: undefined,
						selection_id: undefined, // e.g. webern:textcolor, or numeric selection_id
						value: undefined, // inital value, if not overridden by options!
						use_val_names: false, //if true, use the names of the selection nodes (instead of the id's)
						empty: undefined // if set it will be used to create the first option: {label: 'name', value: 'val'}
					};
					$.extend(localdata.settings, options); // initialize a local data object which is attached to the DOM object
					localdata.ele = {};
					$this.data('localdata', localdata);
				}
				else {
					localdata = $this.data('localdata');
					if (options !== undefined) {
						localdata.settings.value = options; // options is the actual value!
					}
				}


				$this.append(localdata.ele.selection = $('<select>').addClass('form-control propedit'));

				if (localdata.settings.empty !== undefined) {
					// create an empty entry at the beginning
					localdata.ele.selection.append($('<option>', {
						value: localdata.settings.empty.value,
					}).text(localdata.settings.empty.label));
					if (localdata.settings.value == localdata.settings.empty.value) {
						// empty.value is selected by default
						localdata.ele.selection.children('option:first').attr('selected', true);
					}
				}
			        SALSAH.ApiGet('selections', localdata.settings.selection_id, {}, function(data) {
	                              var dataval;
				      if (data.status == 0) {
					  if (localdata.settings.value === undefined) {
					      localdata.settings.value = localdata.settings.use_val_names ? data.selection[0].name : data.selection[0].id;
					  }
					  for (var i in data.selection) {
					      dataval = localdata.settings.use_val_names ? data.selection[i].name : data.selection[i].id;
					      if (dataval == localdata.settings.value) {
							  localdata.ele.selection.append($('<option>', {
								  value:  dataval,
								  selected: 'selected',
							  }).text(data.selection[i].label));
						  }
					      else {
						  localdata.ele.selection.append($('<option>', {
										       value:  dataval,
									           }).text(data.selection[i].label));
					      }
					  }
					  localdata.ele.selection.on('change', function(event) {
					    if (localdata.settings.onChangeCB instanceof Function) {
					        localdata.settings.onChangeCB(event.target.value, data.DATA); // first param ist the actual value, second param is the array containing all values
					    }
					  });
				      }
				      else {
					  alert('data.errormsg');
				      }
				  });
			});
		},

		value: function(options) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			return localdata.ele.selection.val();
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


	$.fn.selection = function(method) {
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
