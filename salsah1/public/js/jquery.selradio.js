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
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @author Tobias Schwizer <t.schweizer@unibas.ch>
 * @package jqplugins
 *
 */
(function( $ ){

	var methods = {
		/**
		* $(element).selradio({'value': 12});
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
					  localdata.settings.value = localdata.settings.use_val_names ? data.selection.name : data.selection.id;
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
                               /* NEW API
				$.__post(SITE_URL + '/ajax/get_selection_nodes.php', {
						selection_id: localdata.settings.selection_id,
						format: 'json'
					}, function(data){
						var dataval;
						if (data.CODE == 'OK') {
							if (localdata.settings.value === undefined) {
								localdata.settings.value = localdata.settings.use_val_names ? data.DATA[0].name : data.DATA[0].id;
							}
							for (var i in data.DATA) {
								dataval = localdata.settings.use_val_names ? data.DATA[i].name : data.DATA[i].id;
								if (dataval == localdata.settings.value) {
									$this.prepend(String(data.DATA[i].label));
								}
							}
						}
						else {
							alert('data.MSG');
						}
					}, 'json'
				);
                                */
			});			
		},
		
		edit: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata;
				if ((options !== undefined) && (options instanceof Object)) {
					localdata = {};
					localdata.settings = {
						onChangeCB: undefined,
						selection_id: undefined, // e.g. webern:textcolor, or numeric selection_id
						value: undefined, // inital value, if not overridden by options!
						use_val_names: false, //if true, use the names of the selection nodes (instead of the id's)
					};
					$.extend(localdata.settings, options);
					localdata.ele = {};
					$this.data('localdata', localdata); 
				}
				else {
					localdata = $this.data('localdata'); 
					if (options !== undefined) {
						localdata.settings.value = options; // options is the actual value!
					}
				}
                                             


                                SALSAH.ApiGet('selections/' + encodeURIComponent(localdata.settings.selection_id), {}, function(data) {
                                  var dataval;
			          var radioname = 'selradio' + String(Math.floor(Math.random()*10000));
			              if (data.status == 0) {
					  if (localdata.settings.value === undefined) {
					      localdata.settings.value = localdata.settings.use_val_names ? data.selection[0].name : data.selection[0].id;
					  }
					  for (var i in data.selection) {
					      dataval = localdata.settings.use_val_names ? data.selection[i].name : data.selection[i].id;
					      if (i > 0) $this.append(' ');
					      if (dataval == localdata.settings.value) {
						  $this.append($('<input>', {type: 'radio', value: dataval, name: radioname, checked: 'checked'}).addClass('selradio'));
						  $this.append(String(data.selection[i].label));
					      }
					      else {
						  $this.append($('<input>', {type: 'radio', value: dataval, name: radioname}).addClass('selradio'));
						  $this.append(String(data.selection[i].label));
					      }
					  }
				      }
				      else {
					  alert('data.errormsg');
				      }
                                });
                                             
                               /* NEW API
				$.__post(SITE_URL + '/ajax/get_selection_nodes.php', {
						selection_id: localdata.settings.selection_id,
						format: 'json'
					}, function (data) {
						var dataval;
						var radioname = 'selradio' + String(Math.floor(Math.random()*10000));
						if (data.CODE == 'OK') {
							if (localdata.settings.value === undefined) {
								localdata.settings.value = localdata.settings.use_val_names ? data.DATA[0].name : data.DATA[0].id;
							}
							for (var i in data.DATA) {
								dataval = localdata.settings.use_val_names ? data.DATA[i].name : data.DATA[i].id;
								if (i > 0) $this.append(' ');
								if (dataval == localdata.settings.value) {
									$this.append($('<input>', {type: 'radio', value: dataval, name: radioname, checked: 'checked'}).addClass('selradio'));
									$this.append(String(data.DATA[i].label));
								}
								else {
									$this.append($('<input>', {type: 'radio', value: dataval, name: radioname}).addClass('selradio'));
									$this.append(String(data.DATA[i].label));
								}
							}
						}
						else {
							alert('data.MSG');
						}
					}, 'json'
				);
                                */
			});
		},
		
		value: function(options) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			return $this.find('.selradio:checked').val();
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


	$.fn.selradio = function(method) {
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