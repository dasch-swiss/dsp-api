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
* @author Tobias Schweizer <t.schweizer@unibas.ch>
* @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
* @package jQuery
*
*/
(function($){

	// local function declarations here ...

	/*
	* Create a new region to be transcribed
	*/
	var create_region = function(canvas, trans_cont, text_ctrl_area, CB) {

		var rotation_CB = function(trans_field) {
			var obj = canvas.regions('returnObjects', parseInt(trans_field.data('index')));
			adjust_trans_field(trans_cont, obj, trans_field);
		};

		var params = {
			type: 'rectangle',
			lineColor: '#FF0000',
			draw_cb: function(obj, index) {
				// create corresponding figure to given obj within trans_cont (by region index)
				var trans_field = $('<div>', {'class': 'transcriptionField'})
				.css({position: 'absolute', border: '1px solid red'})
				.attr('data-index', index)
				.data('rotation_fac', 0)
				.data('offset', {x: 0, y: 0})
				.data('fontSize', 14) // initial font size
				.appendTo(trans_cont);

				adjust_trans_field(trans_cont, obj, trans_field);

				// attach texteditor-plugin
				trans_field.texteditor({
					controls: text_ctrl_area,
					dims: {width: '100%', height: '100%'},
					global_props: true,
					fontSize: '14px', // inital font size
					fontSize_CB: function(editor_div, diff) {
						editor_div.parent().data('fontSize', (editor_div.parent().data('fontSize') + diff));
					},
					lineHeight: '16px', // default diff to font size: 2px
					rotation_CB: rotation_CB,
					selection_names: ['webern:fontfamily']
				});
		
				if (CB instanceof Function) CB();
		
			}
		};
		canvas.regions('setMode', 'draw', params);
	};

	var edit_regions = function(canvas, trans_cont){
		canvas.regions('setMode', 'detect', {
			clicked_cb: function(index) {
				// set active ele
				var active_ele = trans_cont.find('[data-index=' + index + ']');
				//active_ele.texteditor('setActiveEle', viewer.windowId(), active_ele);

				// edit the figure which has been activated
				canvas.regions('setMode', 'edit', {index: index, edit_cb: function(obj, index) {
					// reset the offset to: 0, 0
					trans_cont.find('[data-index=' + index + ']').data('offset', {x: 0, y: 0});
					adjust_trans_field(trans_cont, obj, trans_cont.find('[data-index=' + index + ']'));
					//order_transcription_fields(canvas.regions('returnGeometricalOrder'));
					//transcription_tab_container.find('.transcriptionField[data-index=' + index + ']').data('changed', true).css('border-color', 'yellow');
					//if ((metadata_area).find('.save').prop('disabled') == true) (metadata_area).find('.save').attr({disabled: false});
					//control_save_button();
					return;
				}});
				return;
			}
		});
	};

	var delete_region = function(canvas, trans_cont, index){
		canvas.regions('deleteObject', index).regions('remove_handlers_and_events');
		var del = trans_cont.find('[data-index='+ index + ']');

		trans_cont.find('div').each(function() {
			// adapt data-index of all transcription fields because one area has been deleted, so there might be a gap
			var $this = $(this);
			var cur_index = $this.data('index');
			if (cur_index > index) {
				$this.attr('data-index', cur_index-1); 
			}
		});

		if (del.data('resid') === undefined) {
			// area has not been saved yet, exists only in the GUI
			del.remove();
			return; 
		}
	
		// delete transcription area in db
		SALSAH.ApiDelete('resources/' + encodeURIComponent(del.data('values').res_id), {}, function(data) {
			if (data.status == ApiErrors.OK) {
				del.remove();
			}
			else {
				alert(data.errormsg);
			}
			
		});
		/* NEW API
		$.__post(SITE_URL + '/ajax/del_resource.php', {
			resid: del.data('values').res_id
		}, function(data) {
			if (data['CODE'] != 'OK') alert('deletion failed');
			del.remove();
		}, 'json');
		*/
	};


	var adjust_trans_field = function(trans_cont, obj, trans_field) {
		// obj is a region figure, transcription_field the corresponding div on the trans_cont
		var top, left, width, height, rot;

		if (trans_field.data('rotation_fac') !== undefined) {
			rot = parseInt(trans_field.data('rotation_fac'));
		} else {
			rot = 0;
		}

		left = Math.min(obj.points[0].x * trans_cont.width(), obj.points[1].x * trans_cont.width());
		top = Math.min(obj.points[0].y * trans_cont.height(), obj.points[1].y * trans_cont.height());
		width = Math.abs((obj.points[0].x * trans_cont.width()) - (obj.points[1].x * trans_cont.width()));
		height = Math.abs((obj.points[0].y * trans_cont.height()) - (obj.points[1].y * trans_cont.height()));

		if (rot != 0) {
			top = parseInt(top + height/2 - width/2);
			left = parseInt(left + width/2 - height/2);
			var tmp = width;
			width = height;
			height = tmp;
		}

		var cssobj = {
			left: left + trans_field.data('offset').x + 'px',
			top: top + trans_field.data('offset').y + 'px',
			width: width,
			height: height
		};

		if (rot == 90) {
			cssobj['-webkit-transform'] = 'rotate(90deg)';
			cssobj['-moz-transform'] = 'rotate(90deg)';
		}
		else if (rot == -90) {
			cssobj['-webkit-transform'] = 'rotate(-90deg)';
			cssobj['-moz-transform'] = 'rotate(-90deg)';
		} else {
			// remove rotation
			cssobj['-webkit-transform'] = 'rotate(0deg)';
			cssobj['-moz-transform'] = 'rotate(0deg)';
		}

		trans_field.css(cssobj);

		return;
	};

	var adjust_trans_cont = function(canvas, trans_cont) {
		var cont_width, relation, cont_height;

		if (canvas.height() > canvas.width()) {
			// 'Hochformat'
			cont_width = trans_cont.parent().innerWidth(); // take width from parent div which adapts itself to user selection
			relation = canvas.width()/cont_width;
			cont_height = parseInt(canvas.height() * (1/relation));
		} else {
			// 'Breitformat'
			cont_height = trans_cont.parent().innerHeight(); // take height from parent div which adapts itself to user selection
			relation = canvas.height()/cont_height;
			cont_width = parseInt(canvas.width() * (1/relation));
		}
		trans_cont.css({
			height: cont_height + 'px',
			width: cont_width + 'px'
		});

		return;
	};

	var save = function(canvas, trans_cont, rtinfo, vo_resid) {
		// vo_resid: value_of resid


		// process each transcriptionField

		trans_cont.find('.transcriptionField').each(
			function(){
				var $this = $(this);

				var texteditor_infos = $this.texteditor('serialize'); //props and global_props

				// calculate font-size relative to trans_cont height
				var rel_font_size = $this.data('fontSize')/trans_cont.innerHeight();

				var rotation_fac = $this.data('rotation_fac');
				var offset = $this.data('offset');
				var rel_offset = {};

				rel_offset.x = offset.x/trans_cont.innerWidth();
				rel_offset.y = offset.y/trans_cont.innerHeight();

				var geom = canvas.regions('returnObjects', $this.data('index'));

				var cur_props = {
					text: texteditor_infos.text,
					props: texteditor_infos.props,
					global_props: {'rel_font-size': rel_font_size, rel_offset: rel_offset, 'rotation_fac': rotation_fac},
					geom: geom
				};


				var propname, pinfo;
				if ($this.data('resid') === undefined) {
					// does not exist yet, create it
					var propvals = {};
					//var propname;

					for (pinfo in rtinfo.properties) {

						propname = rtinfo.properties[pinfo].vocabulary + ':' + rtinfo.properties[pinfo].name;

						propvals[propname] = {};

						switch (rtinfo.properties[pinfo].name) {
						case 'geometry':
							propvals[propname].value = JSON.stringify(cur_props.geom);
							break;
						case 'utf8str':
							propvals[propname].value = cur_props.text;
							break;
						case 'transcription_global_props':
							propvals[propname].value = JSON.stringify(cur_props.global_props);
							break;
						case 'value_of':
							propvals[propname].value = vo_resid;
							break;
						case 'textattr':
							propvals[propname].value = JSON.stringify(cur_props.props);
							break;
						default:
							break;
						}
					}

					SALSAH.ApiPost('resources/', {
						restype_id: rtinfo.name,
//						properties: JSON.stringify(propvals)
						properties: propvals,
					    return_values: 1
					},  function(data){
						if (data.status != ApiErrors.OK) alert('resadd failed');

						// assign res id to transField
						$this.attr('data-resid', data.res_id);

						$this.data('value', data.value);

						// add property to target of value_of
						SALSAH.ApiPost('values', {res_id: vo_resid, value: data.res_id, prop: 'salsah:transcription'}, function(data2) {
							if (data2['CODE'] != 'OK') alert('edit failed');
						});
						/*
						$.post(SITE_URL + '/api/ajax/__prop_edit_ajax.php', {  // TO DO, BUT ALREADY IN API DIR
							action: 'edit',
							res_id: vo_resid,
							prop: 'salsah:transcription',
							value: data.res_id,
							value_id: -1
						},
						function(data2) {
							if (data2['CODE'] != 'OK') alert('edit failed');
						},
						'json');
						*/
					});
					/*
					$.__post(SITE_URL + '/ajax/add_resource.php',{
					restype_id: rtinfo.name,
					properties: JSON.stringify(propvals),
					return_values: 1
					}, function(data) {
					if (data['CODE'] != 'OK') alert('resadd failed');

					// assign res id to transField
					$this.attr('data-resid', data['RES_ID']);

					$this.data('values', data['VALUES']);
					$this.data('values').res_id = data['RES_ID'];

					// add property to target of value_of
					$.__post(SITE_URL + '/api/ajax/__prop_edit_ajax.php', {
					action: 'edit',
					res_id: vo_resid,
					prop: 'salsah:transcription',
					value: data['RES_ID'],
					value_id: -1
					},
					function(data2) {
					if (data2['CODE'] != 'OK') alert('edit failed');
					},
					'json');
					});
					*/

				} else {
					// already exists, edit it

					var value, value_id, valuetype_id;
					var data = new Array();

					var res_id = $this.data('resid');

					for (pinfo in rtinfo.properties) {
						if (rtinfo.properties[pinfo].name == 'value_of') continue; // ignore 'value_of', it cannot be changed

						propname = rtinfo.properties[pinfo].vocabulary + ':' + rtinfo.properties[pinfo].name;

						//alert(propname);
						value_id = $this.data('value')[propname]['value'][0]['id'];
						valuetype_id = $this.data('value')[propname]['valuetype_id'];

						switch (rtinfo.properties[pinfo].name) {
						case 'geometry':
							value = JSON.stringify(cur_props.geom);
							break;
						case 'utf8str':
							value = cur_props.text;
							break;
						case 'transcription_global_props':
							value = JSON.stringify(cur_props.global_props);
							break;
						case 'textattr':
							value = JSON.stringify(cur_props.props);
							break;
						default:
							break;
						}
						data.push({
//							action: 'edit',
//							res_id: res_id,
//							prop: propname,
							value: value,
							value_id: value_id,
//							valuetype_id: valuetype_id
						});

					}
					// change values for this transField
					SALSAH.ApiPut('values', {value_arr: data}, function(data) {
						for (var j in data) {
							if (data[j]['CODE'] != 'OK') alert('edit failed');
						}
					});
					/*
					$.post(SITE_URL + '/api/ajax/__prop_edit_ajax.php', {  // TO DO, BUT ALREADY IN API DIR
						action: data
					},
					function(data) {
						for (var j in data) {
							if (data[j]['CODE'] != 'OK') alert('edit failed');
						}
					},
					'json');
					*/
				}
			}
		);
	};

	var redraw = function(canvas, trans_cont) {
		// save current heigth and width
		var cur_dims = {
			w: trans_cont.innerWidth(),
			h: trans_cont.innerHeight()
		};

		adjust_trans_cont(canvas, trans_cont);
		var objs = canvas.regions('returnObjects');

		trans_cont.find('.transcriptionField').each(function() {
			var $this = $(this);
	    
			var cur_offset = $this.data('offset');
			var new_offset = {};

			// adjust offset and font-size to new height and width
			new_offset.x = (cur_offset.x/cur_dims.w) * trans_cont.innerWidth();
			new_offset.y = (cur_offset.y/cur_dims.h) * trans_cont.innerHeight();

			$this.data('offset', new_offset);
	    
			var edit_div = $this.find('> div');

			var new_font_size = ($this.data('fontSize')/cur_dims.h) * trans_cont.innerHeight();
			//STATUS.push(new_font_size);

			$this.data('fontSize', new_font_size);

			edit_div.css({
				fontSize: new_font_size + 'px',
				lineHeight: (new_font_size + 2) + 'px'
			});

			adjust_trans_field(trans_cont, objs[$this.data('index')], $this);
		});
	};

	var load = function(canvas, trans_cont, transcriptions, text_ctrl_area) {
		// transcriptions is an array returned from get_resource_context

		var rotation_CB = function(trans_field) {
			var obj = canvas.regions('returnObjects', parseInt(trans_field.data('index')));
			adjust_trans_field(trans_cont, obj, trans_field);
		};

		canvas.regions('reinit');
		trans_cont.empty();

		adjust_trans_cont(canvas, trans_cont);

		for (var i in transcriptions) {

			var obj = JSON.parse(transcriptions[i]['salsah:geometry']['values'][0]['val']);
			canvas.regions('drawObject', obj);

			var global_props = JSON.parse(transcriptions[i]['salsah:transcription_global_props']['values'][0]['val']);

			var offset = {x: (global_props.rel_offset.x * trans_cont.innerWidth()), y: (global_props.rel_offset.y * trans_cont.innerHeight())};

			var trans_field = $('<div>', {'class': 'transcriptionField'})
			.css({position: 'absolute', border: '1px solid red'})
			.attr({'data-index': i, 'data-resid': transcriptions[i]['res_id']})
			.data('rotation_fac', global_props.rotation_fac)
			.data('offset', offset) // relative
			.data('value', transcriptions[i]) // obj vals are still stringified but they are not needed rsp. read!
			.data('fontSize', global_props['rel_font-size'] * trans_cont.innerHeight())
			.appendTo(trans_cont);

			adjust_trans_field(trans_cont, obj, trans_field);

			// attach texteditor-plugin
			trans_field.texteditor({
				controls: text_ctrl_area,
				dims: {width: '100%', height: '100%'},
				global_props: true,
				fontSize: (global_props['rel_font-size'] * trans_cont.innerHeight()) + 'px', // relative
				fontSize_CB: function(editor_div, diff) {
					editor_div.parent().data('fontSize', (editor_div.parent().data('fontSize') + diff));
				},
				lineHeight: ((global_props['rel_font-size'] * trans_cont.innerHeight()) + 2) + 'px', // relative
				rotation_CB: rotation_CB,
				text: transcriptions[i]['salsah:utf8str']['values'][0]['val'],
				props: JSON.parse(transcriptions[i]['salsah:textattr']['values'][0]['val']),
				selection_names: ['webern:fontfamily']
			});

		}
	};

	var methods = {
		init: function(options) {
			return this.each(
				function() {
					var $this = $(this);

					// defaults
					var localdata = {
						settings: {
							//transcription_areas
						}
					};

					if (typeof options === 'object') $.extend(localdata.settings, options);

					if (localdata.settings.canvas === undefined || localdata.settings.text_ctrl_area === undefined  || localdata.settings.rtinfo === undefined || localdata.settings.resid === undefined) {
						// resid references the target of the transcription for example a page (target of value_of)
						alert('aborting, options not complete');
						return false;
					}

					// reinit the regions plugin
					localdata.settings.canvas.regions('reinit');

					adjust_trans_cont(localdata.settings.canvas, $this);

					$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				});
			},
			create_region: function(CB){
				return this.each(
					function() {
						var $this = $(this);
						var localdata = $this.data('localdata');

						create_region(localdata.settings.canvas, $this, localdata.settings.text_ctrl_area, CB);

					}
				);
			},
			edit_regions: function(){
				return this.each(
					function() {
						var $this = $(this);
						var localdata = $this.data('localdata');

						edit_regions(localdata.settings.canvas, $this);

					}
				);
			},
			delete_region: function(index){
				return this.each(
					function() {
						var $this = $(this);
						var localdata = $this.data('localdata');

						delete_region(localdata.settings.canvas, $this, index);

					}
				);
			},
			trans_field_offset: function(index, activate){
				return this.each(
					function() {
						var $this = $(this);
						var localdata = $this.data('localdata');

						if (activate == true) {

							$(document).off('keydown.offset');
							$(document).on(
								'keydown.offset',
								function(event){
									var top, left, offset;

									offset = $this.find('[data-index=' + index + ']').data('offset');

									if (event.which == 38) {
										top = parseInt($this.find('[data-index=' + index + ']').css('top'));
										offset.y--;
									} else if (event.which == 40) {
										top  = parseInt($this.find('[data-index=' + index + ']').css('top'));
										offset.y++;
									} else if (event.which == 37) {
										left  = parseInt($this.find('[data-index=' + index + ']').css('left'));
										offset.x--;
									} else if (event.which == 39) {
										left  = parseInt($this.find('[data-index=' + index + ']').css('left'));
										offset.x++;
									}

									var obj = localdata.settings.canvas.regions('returnObjects', index);
									adjust_trans_field($this, obj, $this.find('[data-index=' + index + ']'));

									//$this.find('[data-index=' + index + ']').data('offset', offset);
								}
							);
						} else {
							$(document).off('keydown.offset');
						}
					}
				);
			},
			redraw: function() {
				return this.each(function() {
					var $this = $(this);
					var localdata = $this.data('localdata');
		
					redraw(localdata.settings.canvas, $this);
		
				});
			},
			save: function() {
				return this.each(
					function() {
						var $this = $(this);
						var localdata = $this.data('localdata');

						save(localdata.settings.canvas, $this, localdata.settings.rtinfo, localdata.settings.resid);

					}
				);
			},
			load: function(transcriptions) {
				return this.each(
					function() {
						var $this = $(this);
						var localdata = $this.data('localdata');

						return load(localdata.settings.canvas, $this, transcriptions, localdata.settings.text_ctrl_area);

					}
				);
			}
		};

		/**
		* @memberOf jQuery.fn
		*/
		$.fn.transcr = function(method) {
			// Method calling logic
			if ( methods[method] ) {
				return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
			} else if ( typeof method === 'object' || ! method ) {
				return methods.init.apply( this, arguments );
			} else {
				throw 'Method ' +  method + ' does not exist on jQuery.tooltip';
			}
		};

	})(jQuery);