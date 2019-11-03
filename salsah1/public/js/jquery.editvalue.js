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

	var edit_icon = new Image();
	edit_icon.src = SITE_URL + '/app/icons/16x16/edit.png';

	var save_icon = new Image();
	save_icon.src = SITE_URL + '/app/icons/16x16/save.png';

	var cancel_icon = new Image();
	cancel_icon.src = SITE_URL + '/app/icons/16x16/delete.png';

	var delete_icon = new Image();
	delete_icon.src = SITE_URL + '/app/icons/16x16/trash_can.png';

	var add_icon = new Image();
	add_icon.src = SITE_URL + '/app/icons/16x16/add.png';

	var drop_icon = new Image();
	drop_icon.src = SITE_URL + '/app/icons/16x16/orange_arrow_down.png';

	var rights_icon = new Image();
	rights_icon.src = SITE_URL + '/app/icons/16x16/lock.png';

	var comment_icon = new Image();
	comment_icon.src = SITE_URL + '/app/icons/16x16/comment.png';

	var active = undefined;
	/*
	"_edit" : "Modifizieren",
		"_cancel" : "Abbrechen",
		"_save" : "Sichern",
		"_delete" : "Löschen",
		"_new_entry" : "Neuer Eintrag...",
		"_remove" : "Entfernen",
*/

	// new bootstrap style: buttons
	var empty_btn = $('<button>').addClass('btn btn-default btn-xs');
	var edit_btn = $('<span>').addClass('glyphicon glyphicon-pencil').attr({title: strings._edit});
	var save_btn = $('<span>').addClass('glyphicon glyphicon-floppy-save').attr({title: strings._save});
	var cancel_btn = $('<span>').addClass('glyphicon glyphicon-remove').attr({title: strings._cancel});
	var delete_btn = $('<span>').addClass('glyphicon glyphicon-trash').attr({title: strings._delete});
	var add_btn = $('<span>').addClass('glyphicon glyphicon-plus').attr({title: strings._new_entry});
	var drop_btn = $('<span>').addClass('glyphicon glyphicon-minus').attr({title: strings._remove});
	var rights_btn = $('<span>').addClass('glyphicon glyphicon-lock').attr({title: strings._rights});
	var comment_btn = $('<span>').addClass('glyphicon glyphicon-comment').attr({title: strings._annotate});
	var valinfo_btn = $('<span>').addClass('glyphicon glyphicon-info-sign').attr({title: strings._valinfo});

	var set_button = function(btn_ele, type) {
		switch (type) {
			case 'add':
				btn_ele.append(
					$('<button>').addClass('btn btn-default btn-xs add_button').append(
						$('<span>').addClass('glyphicon glyphicon-plus')
					).attr({title: strings._new_entry})
				);
				break;

		}
	};

	var show_one_value = function(ele, value_index, settings)
	{
		if (settings.property.value_rights[value_index] >= VALUE_ACCESS_VIEW) {
			switch (parseInt(settings.property.valuetype_id)) {
				case VALTYPE_TEXT: {
					var reg = new RegExp('(http://[^<>\\s]+[\\w\\d])', 'g');     // replace URL's with anchor tags
					ele.append(settings.property.values[value_index].replace(reg, '<a href="$1" target="_blank">$1</a>'));
					break;
				}
				case VALTYPE_INTEGER: {
					ele.append(settings.property.values[value_index]);
					break;
				}
				case VALTYPE_FLOAT: {
					ele.append(settings.property.values[value_index]);
					break;
				}
				case VALTYPE_DATE: {
					ele.dateobj('init', settings.property.values[value_index]);
					break;
				}
				case VALTYPE_PERIOD: {
					ele.append('VALTYPE_PERIOD: NOT YET IMPLEMENTED!');
					break;
				}
				case VALTYPE_RICHTEXT: {
					var textobj = {};
					textobj.utf8str = settings.property.values[value_index]['utf8str'];
					textobj.textattr = (settings.property.values[value_index]['textattr'] === undefined) ? {} : settings.property.values[value_index]['textattr'];
//					ele.texteditor('showHTML', textobj);
					ele.htmleditor('init', textobj);

					// value is represented by a RESOURCE which relates to the resource (via 'salsah:value_of') the 'normal' properties refer to
					break;
				}
				case VALTYPE_RESPTR: {
					switch (settings.property.guielement) {
						case 'pulldown':
						case 'searchbox':
						default: { // which is 'pulldown' and 'searchbox'
							var span = $('<span>', {'class': 'propedit'}).css('cursor', 'pointer');
							if (settings.window_framework)
							{
								span.mouseover(function(event) {
									load_infowin(event, settings.property.values[value_index], this, 'local');
								}).dragndrop('makeDropable', function(event, dropdata) {
									span.data('drop_resid', dropdata.resid);
									span.next().click();
								});
								span.click(function(event) {
									// check if this is a part of a compound object pointing to the same via its 'salsah:part_of' prop
									if (prop == 'salsah:part_of' && propinfo['salsah:seqnum'].values !== undefined) {
										// open compund object viewer at the correspondent position
										RESVIEW.new_resource_editor(prop.values[value_index], prop.value_firstprops[value_index], {}, {sequence_number: propinfo['salsah:seqnum'].values[0]});
									} else {
										// standard procedure
										RESVIEW.new_resource_editor(settings.property.values[value_index], settings.property.value_firstprops[value_index]);
									}
								});
							}
							span.appendTo(ele);
							$('<img>', {src: settings.property.value_iconsrcs[value_index]}).css({borderStyle: 'none'}).appendTo(span);
							$(span).append(' <em>' + settings.property.value_firstprops[value_index] + ' (' + settings.property.value_restype[value_index] + ')</em>');
						}
					}
					break;
				}
				case VALTYPE_SELECTION: {
					var selection_id;
					var attrs = settings.property.attributes.split(';');
					$.each(attrs, function() {
						var attr = this.split('=');
						if (attr[0] == 'selection') {
							selection_id = attr[1];
						}
					});
					switch (settings.property.guielement) {
						case 'radio': {
							ele.selradio('init', {selection_id: selection_id, value: settings.property.values[value_index]});
							break;
						}
						case 'pulldown': {
							ele.selection('init', {selection_id: selection_id, value: settings.property.values[value_index]});
							break;
						}
					}
					break;
				}
				case VALTYPE_HLIST: {
					var hlist_id;
					var attrs = settings.property.attributes.split(';');
					$.each(attrs, function() {
						var attr = this.split('=');
						if (attr[0] == 'hlist') {
							hlist_id = attr[1];
						}
					});
					switch (settings.property.guielement) {
						case 'hlist': {
							ele.hlist('init', {hlist_id: hlist_id, value: settings.property.values[value_index]});
							break;
						}
					}
					break;
				}
				case VALTYPE_TIME: {
					ele.timeobj('init', settings.property.values[value_index]);
					//ele.append(settings.property.values[value_index]);
					break;
				}
				case VALTYPE_INTERVAL: {
					//ele.append(settings.property.values[value_index]);
					ele.append('VALTYPE_INTERVAL: NOT YET IMPLEMENTED!');
					break;
				}
				case VALTYPE_GEOMETRY: {
					var geometry_object = JSON.parse(settings.property.values[value_index]);
					ele.css({cursor: 'default'}).append(geometry_object.type);
					if (settings.options.canvas !== undefined) {
						ele.bind('mouseenter.highlight', function(event) {
							var geo = settings.options.canvas.regions('searchObject', 'val_id', settings.property.value_ids[value_index]);
							settings.options.canvas.regions('highlightObject', geo.index);
						}).bind('mousemove.highlight', function(event) {
							var geo = settings.options.canvas.regions('searchObject', 'val_id', settings.property.value_ids[value_index]);
							settings.options.canvas.regions('highlightObject', geo.index);
						}).bind('mouseout.highlight', function(event){
							settings.options.canvas.regions('unhighlightObjects');
						});
					}
					else {
						ele.append(' (' + strings._open_assoc_res +')');
					}
					break;
				}
				case VALTYPE_COLOR: {
					ele.colorpicker('init', {color: settings.property.values[value_index]});
					break;
				}
				case VALTYPE_ICONCLASS: {
					ele.append(settings.property.values[value_index]);
					break;
				}
				case VALTYPE_GEONAME: {
					ele.geonames('init', {value: settings.property.values[value_index]});
					break;
				}
				default: {
					ele.append('INTERNAL ERROR: UNKNOWN VALUE TYPE! ' + settings.property.valuetype_id);
				}
			} // switch(parseInt(settings.property.valuetype_id))
		}
		else { // no right to view value
			ele.append('(no access rights!)');
		}

		var btn_toolbar = $('<div>').addClass('btn-toolbar');
		var btn_group = $('<div>').addClass('btn-group btn-group-xs');
		ele.append(btn_toolbar.append(btn_group));

		if (settings.property.value_rights[value_index] >= VALUE_ACCESS_MODIFY) {
			if ((parseInt(settings.property.valuetype_id) != VALTYPE_GEOMETRY)) { //} || (options.canvas !== undefined)) {
				if (parseInt(settings.property.valuetype_id) == VALTYPE_RESPTR) {
					btn_group
						.append( $('<button>').addClass('btn btn-default btn-xs')
							.attr({title: strings._remove})
							.append($('<span>').addClass('glyphicon glyphicon-minus'))
							.on('click', function(event) {
								edit_value(ele, value_index, settings);
							})
					);
					/*
					 // before Bootstrap!!
					 $('<img>', {src: drop_icon.src, 'class': 'propedit', title: strings._drop_target}).click(function(event) {
						edit_value(ele, value_index, settings);
					}).appendTo(ele);
					*/
				}
				else {
					btn_group
						.append($('<button>').addClass('btn btn-default btn-xs')
							.attr({title: strings._edit})
							.append($('<span>').addClass('glyphicon glyphicon-pencil'))
							.on('click', function(event) {
								edit_value(ele, value_index, settings);
							})
					);
						/*
						$('<button>').addClass('btn btn-default btn-xs').append(
							$('<span>').addClass('glyphicon glyphicon-pencil')
						)
						*/


					/*
					// before Bootstrap!!
					$('<img>', {src: edit_icon.src, 'class': 'propedit'}).click(function(event) {
						edit_value(ele, value_index, settings);
					}).css({cursor: 'pointer'}).appendTo(ele);
					*/
				}
			}

			btn_group.append(
				$('<button>').addClass('btn btn-default btn-xs')
					.append($('<span>').addClass('glyphicon glyphicon-comment'))
					.valcomment({value_id: settings.property.value_ids[value_index], comment: (settings.property.comments === undefined) ? '' : settings.property.comments[value_index]})
			);
			/*
			// before Bootstrap!!
			$('<img>').attr({src: comment_icon.src})
			.valcomment({value_id: settings.property.value_ids[value_index], comment: (settings.property.comments === undefined) ? '' : settings.property.comments[value_index]})
			.appendTo(btn_group);
			*/
		}


		if ((settings.property.occurrence !== undefined) &&
			((settings.property.occurrence == '0-n') ||
			(settings.property.occurrence == '0-1') ||
			((settings.property.occurrence == '1-n') &&
			(settings.property.values.length > 1)))) {
			//
			// add the delete button to a value, if we have the right to do so
			//
			if (settings.property.value_rights[value_index] >= VALUE_ACCESS_DELETE) {
				if ((parseInt(settings.property.valuetype_id) != VALTYPE_GEOMETRY)) {

					btn_group.append(
						$('<button>').addClass('btn btn-default btn-xs')
							.attr({title: strings._delete})
							.append($('<span>').addClass('glyphicon glyphicon-trash'))
							.on('click', function (event) {
								if (confirm(strings._propdel)) {
									var value_id = settings.property.value_ids[value_index];
									var data = {
										action: 'delete',
										value_id: value_id
									};
									SALSAH.ApiDelete('values/' + encodeURIComponent(value_id), function (data) {
										if (data.status == ApiErrors.OK) {
											settings.property.values.splice(value_index, 1);
											settings.property.value_ids.splice(value_index, 1);
											settings.property.value_rights.splice(value_index, 1);
											settings.property.value_iconsrcs.splice(value_index, 1);
											settings.property.value_firstprops.splice(value_index, 1);
											settings.property.value_restype.splice(value_index, 1);
											var parent = ele.parent();
											ele.remove();
											make_add_button(parent, settings, settings.options); // !+!+!+!+!+!+!+!+!+!+!+
										}
										else {
											alert(data.errormsg);
										}
									})
								}
							})
					);


/*
// before Bootstrap!!
					$('<img>', {src: delete_icon.src, 'class': 'propedit'}).click(function(event){
						if (confirm(strings._propdel)) {
							var value_id = settings.property.value_ids[value_index];
							var data = {
								action: 'delete',
								value_id: value_id
							};
							SALSAH.ApiDelete('values/' + value_id, function(data) {
								if (data.status == ApiErrors.OK) {
									settings.property.values.splice(value_index, 1);
									settings.property.value_ids.splice(value_index, 1);
									settings.property.value_rights.splice(value_index, 1);
									settings.property.value_iconsrcs.splice(value_index, 1);
									settings.property.value_firstprops.splice(value_index, 1);
									settings.property.value_restype.splice(value_index, 1);
									var parent = ele.parent();
									ele.remove();
									make_add_button(parent, settings, settings.options); // !+!+!+!+!+!+!+!+!+!+!+
*/
									/*
									var prop_container = ele.parent();
									prop_container.empty();
									if ((settings.property.values !== undefined) &&
										(settings.property.values.length > 0)) {
										for (var i = 0; i < settings.property.values.length; i++) {
											var tmp_value_container = $('<div>', {'class': 'propedit value_container'}).appendTo(prop_container);
											reset_value(tmp_value_container, prop, i);
										} // for()
									}
									make_add_button(prop_container, prop);

									if (propinfo[prop].guielement == 'geometry') { // if we cancel editing a figure from a region!!
										options.canvas.regions('deleteObject', 'val_id', value_id);
										value_container.bind('mouseenter.highlight', function(event){
											options.canvas.regions('highlightObject', geo.index);
										}).bind('mousemove.highlight', function(event){
											options.canvas.regions('highlightObject', geo.index);
										}).bind('mouseout.highlight', function(event){
											options.canvas.regions('unhighlightObjects');
										});
									}
									*/
/*
								}

								else {
									alert(data.errormsg);
								}
							})
						}
					}).css({cursor: 'pointer'}).appendTo(ele);
*/
				}
			}
		}

	};


	var show_value = function(eles, value_index, settings) // if settings is undefined, it's a reset
	{


		var i;
		var ele;
		if (value_index == -1) {
			if (settings.property.values)
			{
				$.each(settings.property.values, function(index, obj) {
					if (settings.property.value_rights[value_index] >= VALUE_ACCESS_MODIFY) {
						if (settings.event_binding !== undefined) {
							eles[index].on(settings.event_binding.event_type, function(event) {
								settings.event_binding.callback(eles[index], index, settings);
							});
						}
					}
					show_one_value(eles[index], index, settings);
				});
			}
		}
		else {
			eles.empty();
			show_one_value(eles, value_index, settings);
		}
	};
	//----------------------------------------------------------------------------


	var edit_value = function($this, value_index, settings)
	{
		var postdata = [];

		postdata[VALTYPE_TEXT] = function(value, is_new_value) {
			var data = {};
			if (is_new_value) {
				data.value = value;
				data.res_id = settings.resdata.res_id;
				data.prop = settings.propname;
				SALSAH.ApiPost('values', data, function(data) {
					if (data.status == ApiErrors.OK) {
						if (!settings.property.values) settings.property.values = Array();
						if (!settings.property.value_ids) settings.property.value_ids = Array();
						if (!settings.property.value_rights) settings.property.value_rights = Array();
						if (!settings.property.value_iconsrcs) settings.property.value_iconsrcs = Array();
						if (!settings.property.value_firstprops) settings.property.value_firstprops = Array();
						if (!settings.property.value_restype) settings.property.value_restype = Array();
						if (settings.property.valuetype_id == VALTYPE_GEOMETRY) {
							var tmpgeo = JSON.parse(data.value);
							tmpgeo.val_id = data.id;
							tmpgeo.res_id = res_id;
							if ((typeof options === 'object') && (typeof options.canvas !== 'undefined')) {
								options.canvas.regions('setObjectAttribute', 'val_id', data.id, $this.find('span').data('figure_index'));
								options.canvas.regions('setObjectAttribute', 'res_id', res_id, $this.find('span').data('figure_index'));
							}
							settings.property.values[value_index] = JSON.stringify(tmpgeo);
						}
						else {
							settings.property.values[value_index] = data.value;
						}
						settings.property.value_ids[value_index] = data.id;
						settings.property.value_rights[value_index] = data.rights;
						settings.property.value_iconsrcs[value_index] = null;
						settings.property.value_firstprops[value_index] = null;
						settings.property.value_restype[value_index] = null;

						active.value_container.empty();
						show_one_value(active.value_container, value_index, settings);
//						show_value = function(eles, value_index, settings) // if settings is undefined, it's a reset
						if (active.is_new_value) {
							var prop_container = active.value_container.parent();
							make_add_button(prop_container, settings, settings.options);
						}
					}
					else {
						alert(status.errormsg);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
			else {
				data.value = value;
				SALSAH.ApiPut('values/' + settings.property.value_ids[value_index], data, function(data) {
					if (data.status == ApiErrors.OK) {
						settings.property.values[value_index] = data.value;

						show_value($this, value_index, settings);
						if (active.is_new_value) {
							var prop_container = $this.parent();
							make_add_button($this, localdata.settings, localdata.settings.options); // !+!+!+!+!+!+!+!+!+!+!+
						}
					}
					else {
						alert(status.errormsg);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
		};

		postdata[VALTYPE_INTEGER] = postdata[VALTYPE_TEXT];
		postdata[VALTYPE_FLOAT] = postdata[VALTYPE_TEXT];

		postdata[VALTYPE_RICHTEXT] = function(value, is_new_value) {
			var data = {};
			if (is_new_value) {
				data.value = value;
				data.res_id = settings.resdata.res_id;
				data.prop = settings.propname;
				SALSAH.ApiPost('values', data, function(data) {
					if (data.status == ApiErrors.OK) {
						// data.value has the following members:
						//   data.value.utf8str
						//   data.value.textattr
						//   data.value.resource_refrence
						//
						var tmpobj = {};
						tmpobj.utf8str = data.value.utf8str;
						tmpobj.textattr = data.value.textattr;
						tmpobj.resource_reference = data.value.resource_refrence;
						if (!settings.property.values) settings.property.values = Array();
						if (!settings.property.value_ids) settings.property.value_ids = Array();
						if (!settings.property.value_rights) settings.property.value_rights = Array();
						if (!settings.property.value_iconsrcs) settings.property.value_iconsrcs = Array();
						if (!settings.property.value_firstprops) settings.property.value_firstprops = Array();
						if (!settings.property.value_restype) settings.property.value_restype = Array();
						settings.property.values[value_index] = tmpobj;
						settings.property.value_ids[value_index] = data.id;
						settings.property.value_rights[value_index] = data.rights;
						settings.property.value_iconsrcs[value_index] = null;
						settings.property.value_firstprops[value_index] = null;
						settings.property.value_restype[value_index] = null;

						active.value_container.empty();
						show_one_value(active.value_container, value_index, settings);
						if (active.is_new_value) {
							var prop_container = active.value_container.parent();
							make_add_button(prop_container, settings, settings.options);
						}
					}
					else {
						alert(status.errormsg);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
			else {
				data.value = value;
				SALSAH.ApiPut('values/' + settings.property.value_ids[value_index], data, function(data) {
					if (data.status == ApiErrors.OK) {
						settings.property.values[value_index] = data.value;

						$this.empty();
						show_value($this, value_index, settings);
						if (active.is_new_value) {
							var prop_container = $this.parent();
							make_add_button($this, settings, settings.options); // !+!+!+!+!+!+!+!+!+!
						}
					}
					else {
						alert(status.errormsg);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
		};


		postdata[VALTYPE_DATE] = function(value, is_new_value) {
			var precisionnames = ['ZERO', 'DAY', 'MONTH', 'YEAR'];

			var datestr_to_jdc = function(cal, datestr) {
				var tmparr = datestr.split('-');
				var day = parseInt(tmparr[2]);
				var month = parseInt(tmparr[1]);
				var year = parseInt(tmparr[0]);
				if (typeof cal == 'number') {
					cal = SALSAH.calendarnames[cal];
				}
				return (SALSAH.date_to_jdc(day, month, year, cal, 'START'));
			};

			var data = {};
			if (is_new_value) {
				data.value = value;
				data.res_id = settings.resdata.res_id;
				data.prop = settings.propname;
				SALSAH.ApiPost('values', data, function(data) {
					if (data.status == ApiErrors.OK) {
						// data.value has the following members:
						//   data.value.calendar
						//   data.value.dateprecision1
						//   data.value.dateprecision2
						//   data.value.dateval1 (yyyy-mm-dd)
						//   data.value.dateval2 (yyy-mm-dd)
						//
						// What we want is:
						//   [{"dateval1":"2267168","dateval2":"2267168","calendar":"JULIAN","dateprecision1":"DAY","dateprecision2":"DAY"}]
						//
						var tmpobj = {};
						tmpobj.dateval1 = datestr_to_jdc(data.value.calendar, data.value.dateval1);
						tmpobj.dateval2 = datestr_to_jdc(data.value.calendar, data.value.dateval2);
						tmpobj.calendar = SALSAH.calendarnames[data.value.calendar];
						tmpobj.dateprecision1 = precisionnames[data.value.dateprecision1];
						tmpobj.dateprecision2 = precisionnames[data.value.dateprecision2];

						if (!settings.property.values) settings.property.values = Array();
						if (!settings.property.value_ids) settings.property.value_ids = Array();
						if (!settings.property.value_rights) settings.property.value_rights = Array();
						if (!settings.property.value_iconsrcs) settings.property.value_iconsrcs = Array();
						if (!settings.property.value_firstprops) settings.property.value_firstprops = Array();
						if (!settings.property.value_restype) settings.property.value_restype = Array();
						settings.property.values[value_index] = tmpobj;
						settings.property.value_ids[value_index] = data.id;
						settings.property.value_rights[value_index] = data.rights;
						settings.property.value_iconsrcs[value_index] = null;
						settings.property.value_firstprops[value_index] = null;
						settings.property.value_restype[value_index] = null;

						active.value_container.empty();
//						reset_value(active.value_container, active.prop, value_index);
						show_one_value(active.value_container, value_index, settings);

						var prop_container = active.value_container.parent();
						make_add_button($this, settings, settings.options);
					}
					else {
						alert(data.errormsg);
						cancel_edit(value_index, settings);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
			else {
				data.value = value;
				SALSAH.ApiPut('values/' + settings.property.value_ids[value_index], data, function(data) {
					if (data.status == ApiErrors.OK) {
						// data.value has the following members:
						//   data.value.calendar
						//   data.value.dateprecision1
						//   data.value.dateprecision2
						//   data.value.dateval1 (yyyy-mm-dd)
						//   data.value.dateval2 (yyy-mm-dd)
						//
						// What we want is:
						//   [{"dateval1":"2267168","dateval2":"2267168","calendar":"JULIAN","dateprecision1":"DAY","dateprecision2":"DAY"}]
						//
						var tmpobj = {};
						tmpobj.dateval1 = datestr_to_jdc(data.value.calendar, data.value.dateval1);
						tmpobj.dateval2 = datestr_to_jdc(data.value.calendar, data.value.dateval2);
						tmpobj.calendar = SALSAH.calendarnames[data.value.calendar];
						tmpobj.dateprecision1 = precisionnames[data.value.dateprecision1];
						tmpobj.dateprecision2 = precisionnames[data.value.dateprecision2];

						settings.property.values[value_index] = tmpobj; // HIER IST DER FEHLER!!!!!!!!!

						$this.empty();
						show_value($this, value_index, settings);
					}
					else {
						alert(data.errormsg);
						cancel_edit(value_index, settings);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
		};


		// TIMETIMETIMETIMETIME !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		postdata[VALTYPE_TIME] = function(value, is_new_value) {
			var data = {};
			if (is_new_value) {
				data.value = value;
				data.res_id = settings.resdata.res_id;
				data.prop = settings.propname;
				SALSAH.ApiPost('values', data, function(data) {
					if (data.status == ApiErrors.OK) {
						var tmpobj = {};
						tmpobj.timeval1 = data.value.timeval1;
						tmpobj.timeval2 = data.value.timeval2;

						if (!settings.property.values) settings.property.values = Array();
						if (!settings.property.value_ids) settings.property.value_ids = Array();
						if (!settings.property.value_rights) settings.property.value_rights = Array();
						if (!settings.property.value_iconsrcs) settings.property.value_iconsrcs = Array();
						if (!settings.property.value_firstprops) settings.property.value_firstprops = Array();
						if (!settings.property.value_restype) settings.property.value_restype = Array();
						settings.property.values[value_index] = tmpobj;
						settings.property.value_ids[value_index] = data.id;
						settings.property.value_rights[value_index] = data.rights;
						settings.property.value_iconsrcs[value_index] = null;
						settings.property.value_firstprops[value_index] = null;
						settings.property.value_restype[value_index] = null;

						active.value_container.empty();
//						reset_value(active.value_container, active.prop, value_index);
						show_one_value(active.value_container, value_index, settings);

						var prop_container = active.value_container.parent();
						make_add_button($this, settings, settings.options);
					}
					else {
						alert(status.errormsg);
						cancel_edit(value_index, settings);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
			else {
				data.value = value;
				SALSAH.ApiPut('values/' + settings.property.value_ids[value_index], data, function(data) {
					if (data.status == ApiErrors.OK) {
						var tmpobj = {};
						tmpobj.timeval1 = data.value.timeval1;
						tmpobj.timeval2 = data.value.timeval2;

						settings.property.values[value_index] = tmpobj; // HIER IST DER FEHLER!!!!!!!!!

						$this.empty();
						show_value($this, value_index, settings);
						if (active.is_new_value) {
							var prop_container = $this.parent();
							make_add_button($this, localdata.settings, localdata.settings.options); // !+!+!+!+!+!+!+!+
						}
					}
					else {
						alert(status.errormsg);
						cancel_edit(value_index, settings);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
		};
		// TIMETIMETIMETIMETIME !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

		//postdata[VALTYPE_INTERVAL] = postdata[VALTYPE_TEXT]; // At the moment it's only a string


		postdata[VALTYPE_RESPTR] = function(value, is_new_value) {
			var data = {};
			if (is_new_value) {
				data.value = value;
				data.res_id = settings.resdata.res_id;
				data.prop = settings.propname;
				SALSAH.ApiPost('values', data, function(data) {
					if (data.status == ApiErrors.OK) {

						if (!settings.property.values) settings.property.values = Array();
						if (!settings.property.value_ids) settings.property.value_ids = Array();
						if (!settings.property.value_rights) settings.property.value_rights = Array();
						if (!settings.property.value_iconsrcs) settings.property.value_iconsrcs = Array();
						if (!settings.property.value_firstprops) settings.property.value_firstprops = Array();
						if (!settings.property.value_restype) settings.property.value_restype = Array();
						settings.property.values[value_index] = data.value;
						settings.property.value_ids[value_index] = data.id;
						settings.property.value_rights[value_index] = data.rights;



						var tmp_active = {};
						$.extend(tmp_active, active);
						SALSAH.ApiGet('resources', data.value, {reqtype: 'info'}, function(data) {
							if (data.status == ApiErrors.OK) {
								settings.property.value_iconsrcs[value_index] = data.resource_info.restype_iconsrc;
								settings.property.value_firstprops[value_index] = data.resource_info.firstproperty;
								settings.property.value_restype[value_index] = data.resource_info.restype_label;

								tmp_active.value_container.empty();
								show_one_value(tmp_active.value_container, value_index, settings);

								var prop_container = tmp_active.value_container.parent();
								make_add_button($this, settings, settings.options);
							}
							else {
								alert(data.errormsg);
							}
						});

					}
					else {
						alert(data.errormsg);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
			else {
				data.value = value;
				SALSAH.ApiPut('values/' + settings.property.value_ids[value_index], data, function(data) {
					if (data.status == ApiErrors.OK) {
						settings.property.values[value_index] = data.value;

						var tmp_active = {};
						$.extend(tmp_active, active);

						SALSAH.ApiGet('resources', data.value, {reqtype: 'info'}, function(data) {
							if (data.status == ApiErrors.OK) {
								var resinfo = data.resource_info;
								settings.property.value_iconsrcs[value_index] = resinfo.restype_iconsrc;
								settings.property.value_firstprops[value_index] = resinfo.firstproperty;
								settings.property.value_restype[value_index] = resinfo.restype_label;
								$this.empty();
								show_value($this, value_index, settings);
							}
							else {
								alert(data.errormsg);
							}
						});
					}
					else {
						alert(data.errormsg);
					}
					active = undefined;
				}).fail(function(){
					cancel_edit(value_index, settings);
				});
			}
		};

		postdata[VALTYPE_GEOMETRY] = postdata[VALTYPE_TEXT];
		postdata[VALTYPE_COLOR] = postdata[VALTYPE_TEXT];
		postdata[VALTYPE_SELECTION] = postdata[VALTYPE_TEXT];
		postdata[VALTYPE_HLIST] = postdata[VALTYPE_TEXT];
		postdata[VALTYPE_ICONCLASS] = postdata[VALTYPE_TEXT];
		postdata[VALTYPE_GEONAME] = postdata[VALTYPE_TEXT];


		if (active !== undefined) {
			if (!confirm(strings._canceditquest)) return;
			cancel_edit(value_index, settings);
		}

		var is_new_value = false;

		var drop_data = $this.children('span:first').data('drop_resid');

		/*********************************************************************************************/
		if ((value_index === undefined) || (value_index == -1)){
			is_new_value = true;
			if (settings.property.values === undefined) {
				value_index = 0;
			}
			else {
				value_index = settings.property.values.length;
			}
		}
		$this.empty();

		var attributes = {'class': 'propedit'};
		if (settings.property.attributes) {
			var extattr = settings.property.attributes.split(';');
			for (idx in extattr) {
				var tmp = extattr[idx].split('=');
				if (tmp[0] == 'selection') continue;
				if (tmp[0] == 'hlist') continue;
				if (tmp[0] == 'restypeid') continue;
				if (tmp[0] == 'numprops') continue;
				attributes[tmp[0]] = tmp[1];
			}
		}
		var btn_toolbar = $('<div>').addClass('btn-toolbar');
		var btn_group = $('<div>').addClass('btn-group btn-group-xs');


		switch (settings.property.guielement) {
			case 'text': {
				attributes.type = 'text';
				if (!is_new_value) {
					attributes.value = settings.property.values[value_index];
				}
				attributes['style'] = 'width: 95%'; // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! should be configurable !!
				var tmpele = $('<input>', attributes).dragndrop('makeDropable', function(event, dropdata) {
					front = tmpele.val().substring(0, tmpele.attr('selectionStart'));
					back = tmpele.val().substring(tmpele.attr('selectionEnd'));
					tmpele.val(front + '<+LINKTO RESID=' + dropdata.resid + '+>'+ back);
				});
				$this.append(tmpele);
				tmpele.focus();
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id]($this.find('input').val(), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id]($this.find('input').val(), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'textarea': {
				var tmpele = $('<textarea>', attributes).dragndrop('makeDropable', function(event, dropdata) {
					front = tmpele.val().substring(0, tmpele.attr('selectionStart'));
					back = tmpele.val().substring(tmpele.attr('selectionEnd'));
					tmpele.val(front + '<+LINKTO RESID=' + dropdata.resid + '+>'+ back);
				});
				if (!is_new_value) {
					tmpele.append(settings.property.values[value_index]);
				}
				$this.append(tmpele);
				tmpele.focus();
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id]($this.find('textarea').val(), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id]($this.find('textarea').val(), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'richtext': {
				// attributes['style'] = 'width: 95%'; // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! should be configurable !!!!!!!!!!!!!!!!!!!!!!!!!!
				var existing_rtprops;
				var tmpele = $('<div>', {'class': 'htmleditor'});
//				var tmpele = $('<div>').css({width: '100%', minHeight: '20px'});
				var controls = $('<div>');
				var rtopts = {
					buttons: [
						{'span_class': 'bold', label: 'b'},
						{'span_class': 'underline', label: 'u'},
						{'span_class': 'italic', label: 'i'},
						{'span_class': 'strike', label: 's'},
						{'span_class': 'super', label: 'super'},
						{'span_class': 'sub', label: 'sub'}
					], // the corresponding css classes have to be existent!
					dims: {width: '100%', height: '100%', minHeight: '20px'},
					selection_names: ['salsah:textcolor_rt', 'salsah:textbg_rt']
				};
				rtopts.controls = controls;

				/*
				if (!is_new_value) {
					rtopts.text = settings.property.values[value_index]['utf8str'];
					rtopts.props = $.parseJSON(settings.property.values[value_index]['textattr']);
				}
				*/
				if (!is_new_value) {
					rtopts.utf8str = settings.property.values[value_index]['utf8str'];
					rtopts.textattr = $.parseJSON(settings.property.values[value_index]['textattr']);
				}
//				tmpele.texteditor(rtopts);
				tmpele.htmleditor('edit', rtopts);
				$this.append(controls);
				$this.append(tmpele);
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						var rtdata = tmpele.htmleditor('value');
						var props = {};
						props['utf8str'] = rtdata.utf8str;
						props['textattr'] = JSON.stringify(rtdata.textattr);
						props['resource_reference'] = [];

						if (rtdata.textattr['_link'] !== undefined) {
							for (var link_index in rtdata.textattr['_link']) {
								if (rtdata.textattr['_link'][link_index].resid !== undefined && props['resource_reference'].indexOf(rtdata.textattr['_link'][link_index].resid) == -1) {
									props['resource_reference'].push(rtdata.textattr['_link'][link_index].resid);
								}
							}
						}


/*
						if (rtdata.props['_link'] !== undefined) {
							for (var link_index in rtdata.props['_link']) {
								if (props['resource_reference'].indexOf(rtdata.props['_link'][link_index].resid) == -1) {
									props['resource_reference'].push(rtdata.props['_link'][link_index].resid);
								}
							}
						}
*/
						postdata[settings.property.valuetype_id](props, is_new_value);
						tmpele.htmleditor('destroy');
					}));



				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event){
					var rtdata = tmpele.texteditor('serialize');
					var props = {};
					props['utf8str'] = rtdata.text;
					props['textattr'] = JSON.stringify(rtdata.props);
					props['resource_reference'] = [];
					if (rtdata.props['_link'] !== undefined) {
						for (var link_index in rtdata.props['_link']) {
							if (props['resource_reference'].indexOf(rtdata.props['_link'][link_index].resid) == -1) {
								props['resource_reference'].push(rtdata.props['_link'][link_index].resid);
							}
						}
					}
					postdata[settings.property.valuetype_id](props, is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'pulldown': {
				var selection_id;
				var attrs = settings.property.attributes.split(';');
				$.each(attrs, function() {
					var attr = this.split('=');
					if (attr[0] == 'selection') {
						selection_id = attr[1];
					}
				});
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.selection('edit', {selection_id: selection_id});
				}
				else {
					tmpele.selection('edit', {selection_id: selection_id, value: settings.property.values[value_index]});
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.selection('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.selection('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'radio': {
				var selection_id;
				var attrs = settings.property.attributes.split(';');
				$.each(attrs, function() {
					var attr = this.split('=');
					if (attr[0] == 'selection') {
						selection_id = attr[1];
					}
				});
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.selradio('edit', {selection_id: selection_id});
				}
				else {
					tmpele.selradio('edit', {selection_id: selection_id, value: settings.property.values[value_index]});
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.selradio('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.selradio('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'hlist': {
				var hlist_id;
				var attrs = settings.property.attributes.split(';');
				$.each(attrs, function() {
					var attr = this.split('=');
					if (attr[0] == 'hlist') {
						hlist_id = attr[1];
					}
				});
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.hlist('edit', {hlist_id: hlist_id});
				}
				else {
					tmpele.hlist('edit', {hlist_id: hlist_id, value: settings.property.values[value_index]});
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.hlist('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.hlist('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'slider': {
				break;
			}
			case 'spinbox': {
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.spinbox('edit');
				}
				else {
					tmpele.spinbox('edit', settings.property.values[value_index]);
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.spinbox('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.spinbox('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'searchbox': {
				if (drop_data) {
					//                                          alert ('DROP_EVENT: ' + drop_data);
					if (is_new_value) {
						//                                                      alert('--add--');
					}
					SALSAH.ApiGet('resources', drop_data, {reqtype: 'info'}, function(data) {
						if (data.status == ApiErrors.OK) {
							var resinfo = data.resource_info;
							var tmpele = $('<span>', attributes).appendTo($this);
							tmpele.append($('<img>', {src: resinfo.restype_iconsrc}).css({borderStyle: 'none'}));
							tmpele.append(' <em>' + resinfo.firstproperty + ' (' + resinfo.restype_label + ')</em>');
							btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
								$('<span>').addClass('glyphicon glyphicon-save'))
								.attr({title: strings._save})
								.on('click', function(event) {
									postdata[settings.property.valuetype_id](drop_data, is_new_value);
								}));
							/*
							$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
								postdata[settings.property.valuetype_id](drop_data, is_new_value);
							}).css({cursor: 'pointer'}));
							*/
						}
						else {
							alert(data.errormsg);
						}
					});
				}
				else {
					var restype_id = -1;
					var numprops;
					var attrs = settings.property.attributes.split(';');
					$.each(attrs, function() {
						var attr = this.split('=');
						if (attr[0] == 'restypeid') {
							restype_id = attr[1];
						}
						else if (attr[0] == 'numprops') {
							numprops = attr[1];
						}
					});

					var inpele = $('<input>').attr({type: 'text'}).addClass('__searchbox'); // class is used to remove the element if editing is canceled. Unfortunately JS has no "onRemove"- or "onDelete"-event!
					$this.append(inpele);
					inpele.searchbox({
						restype_id: restype_id,
						numprops: numprops,
						clickCB: function (res_id) {
							inpele.remove();
							SALSAH.ApiGet('resources', res_id, {reqtype: 'info'}, function(data) {
								if (data.status == ApiErrors.OK) {
									var resinfo = data.resource_info;
									var tmpele = $('<span>')
									.addClass('propedit').addClass('__searchbox_res')
									.data('res_id', res_id)
									.append($('<img>', {src: resinfo.restype_iconsrc}).css({borderStyle: 'none'}))
									.append(' <em>' + resinfo.firstproperty + ' (' + resinfo.restype_label + ')</em>');
									$this.prepend(tmpele);
								}
								else {
									alert(data.errormsg);
								}
							});
						}
					});
					inpele.searchbox('setFocus');
					btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
						$('<span>').addClass('glyphicon glyphicon-save'))
						.attr({title: strings._save})
						.on('click', function(event) {
							var res_id = $this.find('.__searchbox_res').data('res_id');
							postdata[settings.property.valuetype_id](res_id, is_new_value);
						}));
					/*
					$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						var res_id = $this.find('.__searchbox_res').data('res_id');
						postdata[settings.property.valuetype_id](res_id, is_new_value);
					}).css({cursor: 'pointer'}));
					*/
				}
				break;
			}
			case 'date': {
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.dateobj('edit');
				}
				else {
					tmpele.dateobj('edit', settings.property.values[value_index]);
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.dateobj('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.dateobj('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'time': {
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.timeobj('edit');
				}
				else {
					tmpele.timeobj('edit', {timeobj: settings.property.values[value_index]});
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.timeobj('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.timeobj('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'interval': {
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.timeobj('edit', {show_duration: true});
				}
				else {
					tmpele.timeobj('edit', {timeobj: settings.property.values[value_index], show_duration: true});
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.timeobj('value'), is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					postdata[settings.property.valuetype_id](tmpele.timeobj('value'), is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'geometry': {
				//
				// stop highlighting !!!
				//
				if ((options.canvas === undefined) || (options.viewer === undefined)) break;
				if (is_new_value) {
					$this.append($('<span>', attributes).css({color: 'red'}).append('Select a figure type and draw...'));

					options.viewer.topCanvas().regions('setObjectStatus', 'inactive').regions('setDefaultLineColor', propinfo['salsah:color'].values[0]);

					RESVIEW.figure_drawing (options.viewer, function(figure, index) {
						$this.find('span').empty().append(figure.type); // add text to show what figure type willbe drawn...
						//                                                      options.viewer.getTaskbar().find('.regionActions').remove();
						options.viewer.getTaskbar().elestack('hide', 'region_drawings');
						$this.find('span').data('figure_index', index);
						btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
							$('<span>').addClass('glyphicon glyphicon-save'))
							.attr({title: strings._save})
							.on('click', function(event) {
								var new_geo = options.canvas.regions('returnObjects', $this.find('span').data('figure_index'));
								//+/+/+/+/+/+postdata[settings.property.valuetype_id]($this, prop, value_index, JSON.stringify(new_geo), is_new_value);
								options.viewer.topCanvas().regions('setObjectStatus', 'active');
								//                                                          options.viewer.getTaskbar().elestack('show', 'main');
								RESVIEW.resetRegionDrawing(options.viewer);
							}));
						/*
						$this.find('span').after($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
							var new_geo = options.canvas.regions('returnObjects', $this.find('span').data('figure_index'));
							//+/+/+/+/+/+postdata[settings.property.valuetype_id]($this, prop, value_index, JSON.stringify(new_geo), is_new_value);
							options.viewer.topCanvas().regions('setObjectStatus', 'active');
							//                                                          options.viewer.getTaskbar().elestack('show', 'main');
							RESVIEW.resetRegionDrawing(options.viewer);
						}));
						//                                                      options.viewer.getTaskbar().elestack('show', 'main');
						//                                                      RESVIEW.resetRegionDrawing(options.viewer);
						*/
					});
				}
				else {
					$this.unbind('.highlight');
					var geometry_object = JSON.parse(settings.property.values[value_index]);
					var tmpele = $('<span>', attributes).css({color: 'red'}).append(geometry_object.type);
					$this.append(tmpele);
					options.viewer.getTaskbar().elestack('hide', 'main');
					if (typeof options.canvas !== 'undefined') {
						var geo = options.canvas.regions('searchObject', 'val_id', settings.property.value_ids[value_index]);
						options.viewer.topCanvas().regions('setMode', 'edit', {index: geo.index});
						btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
							$('<span>').addClass('glyphicon glyphicon-save'))
							.attr({title: strings._save})
							.on('click', function(event) {
								var new_geometry_object = {};
								for (var i in geo.obj) {
									if (i == 'res_id') continue;
									if (i == 'val_id') continue;
									new_geometry_object[i] = geo.obj[i];
								}
								postdata[settings.property.valuetype_id](JSON.stringify(new_geometry_object), is_new_value);
								options.viewer.topCanvas().regions('redrawObjects');
								options.viewer.getTaskbar().elestack('show', 'main');
							}));
						/*
						$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
							var new_geometry_object = {};
							for (var i in geo.obj) {
								if (i == 'res_id') continue;
								if (i == 'val_id') continue;
								new_geometry_object[i] = geo.obj[i];
							}
							postdata[settings.property.valuetype_id](JSON.stringify(new_geometry_object), is_new_value);
							options.viewer.topCanvas().regions('redrawObjects');
							options.viewer.getTaskbar().elestack('show', 'main');
						}).css({cursor: 'pointer'}));
						*/
					}
					else {
						alert('FATAL INTERNAL ERROR!'); //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! BESSERE FEHLERMELDUNG
					}
				}
				break;
			}
			case 'colorpicker': {
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.colorpicker('edit');
				}
				else {
					tmpele.colorpicker('edit', {color: settings.property.values[value_index]});
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						var colval = tmpele.colorpicker('value');
						options.viewer.topCanvas().regions('setObjectAttribute', 'lineColor', colval, 'res_id', res_id).regions('redrawObjects', true);
						postdata[settings.property.valuetype_id](colval, is_new_value);
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					var colval = tmpele.colorpicker('value');
					options.viewer.topCanvas().regions('setObjectAttribute', 'lineColor', colval, 'res_id', res_id).regions('redrawObjects', true);
					postdata[settings.property.valuetype_id](colval, is_new_value);
				}).css({cursor: 'pointer'}));
				*/
				break;
			}

			case 'fileupload': { // this is for resources which do have a location!
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.location('edit');
				}
				else {
					tmpele.location('edit');
				}
				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						var result = tmpele.location('value');
						//                                          options.viewer.topCanvas().regions('setObjectAttribute', 'lineColor', colval, 'res_id', res_id).regions('redrawObjects', true);
						//                                          postdata[settings.property.valuetype_id]($this, prop, value_index, colval, is_new_value);
						//                                          alert('here we should upload the file'); //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					}));
				/*
				$this.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
					var result = tmpele.location('value');
					//                                          options.viewer.topCanvas().regions('setObjectAttribute', 'lineColor', colval, 'res_id', res_id).regions('redrawObjects', true);
					//                                          postdata[settings.property.valuetype_id]($this, prop, value_index, colval, is_new_value);
					//                                          alert('here we should upload the file'); //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				}).css({cursor: 'pointer'}));
				*/
				break;
			}
			case 'geoname': {
				var tmpele = $('<span>', attributes).appendTo($this);
				if (is_new_value) {
					tmpele.geonames('edit', {new_entry_allowed: true});
				}
				else {
					tmpele.geonames('edit', {new_entry_allowed: true, value: settings.property.values[value_index]});
				}

				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						postdata[settings.property.valuetype_id](tmpele.geonames('value'), is_new_value);
					}));


				break;
			}
			default: {

			}
		}
		$this.append(btn_toolbar.append(btn_group));
		btn_group.append(
			$('<button>').addClass('btn btn-default btn-xs')
				.attr({title: strings._cancel})
				.append($('<span>').addClass('glyphicon glyphicon-remove'))
				.on('click', function (event) {
					cancel_edit(value_index, settings);
				})
		);

		/*
		//before bootstrap!!
				$this.append($('<img>', {src: cancel_icon.src, title: strings._cancel, 'class': 'propedit'})
			.click(function(event) {
				cancel_edit(value_index, settings);

		}));
		*/

		/*
		// close the edit field, with esc
		$(document).keyup(function(e) {
			if ($('input.propedit').length() !== 0) {
				cancel_edit(value_index, settings);
			}
		});
		*/

		/*********************************************************************************************/

		active = {value_container: $this, is_new_value: is_new_value};
	};

	var cancel_edit = function(value_index, settings) {
		if (active === undefined) return;

		var ele = active.value_container;

		//
		// some guielements need some special processing...
		//
		switch(settings.property.guielement) {
			case 'geometry': {
				if (active.is_new_value) {
					var fig_index = ele.find('span').data('figure_index');
					if (fig_index !== undefined) {
						options.canvas.regions('deleteObject', fig_index);
					}
					options.viewer.topCanvas().regions('setObjectStatus', 'active');
					RESVIEW.initRegionDrawing(options.viewer);
				}
				else {
					var geo = options.canvas.regions('searchObject', 'val_id', settings.property.value_ids[active.value_index]); //!+!+!+!+!+!+!++!+!+!
					var old_geo = JSON.parse(settings.property.values[active.value_index]);
					for (var i in old_geo) {
						geo.obj[i] = old_geo[i];
					}
					options.canvas.regions('redrawObjects');
					ele.bind('mouseenter.highlight', function(event){
						options.canvas.regions('highlightObject', geo.index);
					}).bind('mousemove.highlight', function(event){
						options.canvas.regions('highlightObject', geo.index);
					}).bind('mouseout.highlight', function(event){
						options.canvas.regions('unhighlightObjects');
					});
					options.viewer.getTaskbar().elestack('show', 'main');
				}
				break;
			}
			case 'searchbox': { // we have to remove the searchlist, if existing....
				var tmpele = ele.find('.__searchbox');
				if (tmpele !== undefined) {
					tmpele.searchbox('remove');
					tmpele.remove();
				}
				break;
			}
			//RESVIEW.resetRegionDrawing(options.viewer);
		}


		//active.value_container, active.prop);
		if (active.is_new_value) {
			var prop_container = ele.parent();
			ele.remove();
			make_add_button(prop_container, settings, settings.options);
		}
		else {
			show_value(ele, value_index, settings);
		}
		active = undefined;
	};

	var make_add_button = function (parent, settings, options) {
		var ab = parent.find('.add_button');
		if (ab)
		{
			ab.parent().remove();
		}

		if ((parseInt(settings.property.valuetype_id) == VALTYPE_GEOMETRY) && (options.canvas === undefined)) {
			return; // no add button
		}
		var add_button = false;
		if (typeof settings.property.occurrence != 'undefined') {
			if ((settings.property.occurrence == '0-n') || (settings.property.occurrence == '1-n')) {
				add_button = true;
			}
			else if ((settings.property.occurrence == '0-1') || (settings.property.occurrence == '1')) {
				if (typeof settings.property.values == 'undefined') {
					add_button = true;
				}
				else if (settings.property.values.length == 0) {
					add_button = true;
				}
			}
		}
		if (add_button) {
			var add_button_ele;
			if (settings.resdata.rights >= RESOURCE_ACCESS_EXTEND) {
				var value_container = $('<div>', {'class': 'propedit value_container'}).appendTo(parent);
//				var add_button = $('<img>', {src: add_icon.src, 'class': 'propedit'}).css({'z-index': 55}).on('click', function(event) {
				var btn_toolbar = $('<div>').addClass('btn-toolbar');
				var btn_group = $('<div>').addClass('btn-group btn-group-xs');
				value_container.append(
					btn_toolbar.append(
						btn_group.append(
							add_button_ele = $('<button>').addClass('btn btn-default btn-xs add_button').append(
								$('<span>').addClass('glyphicon glyphicon-plus')
							).on('click', function(event) {
									edit_value(value_container, -1, settings)
								})
						)
					)
				);
/*
// before bootstrap
				var add_button = $('<img>', {src: add_icon.src, 'class': 'propedit add_button'}).on('click', function(event) {
					edit_value(value_container, -1, settings)
				}).css({cursor: 'pointer'}).appendTo(value_container);
*/
				if (settings.property.valuetype_id == VALTYPE_RESPTR) {
					 add_button_ele.dragndrop('makeDropable', function(event, dropdata) {
						value_container.prepend($('<span>', {'class': 'propedit addresptr'}).data('drop_resid', dropdata.resid));
						add_button.click(); // let's click the add button.... // NO EXTERNAL RESOURCES YET!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					});

				}
			}
		}

	};

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
					propname: undefined,
					resdata: undefined,
					property: undefined,			// Property as given by SALSAH API
					options: undefined,				// special parameter only used with imagebase.js etc. (used with canvas for regions etc.)
					value_index: undefined, 		// give her an index, if we want to show/edit a very specific value
					window_framework: true,
					event_binding: {
						event_type: 'dblclick',
						callback: edit_value
					}
				};
				localdata.ele = {};
				$.extend(localdata.settings, options);
				var value_index = undefined;
				if (localdata.settings.value_index)
				{
					value_index = localdata.settings.value_index;
					delete localdata.settings.value_index;
				}
				if (value_index === undefined)
				{
					localdata.eles = [];
					if (localdata.settings.property.values)
					{
						$.each(localdata.settings.property.values, function(index, value) {
							var ele = $('<div>').addClass('propedit value_container');
							$this.append(ele);
							localdata.eles.push(ele);
						});
					}
					value_index = -1;
				}
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				show_value(localdata.eles, value_index, localdata.settings);
				make_add_button ($this, localdata.settings, localdata.settings.options)
			});
		},

		edit: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					propname: undefined,
					property: undefined,				// Property as given by SALSAH API
					value_index: undefined,				// value index of value to be edited/shown [default: 0]
					options: undefined				// special parameter only used with imagebase.js etc. (used with canvas for regions etc.)
				};
				localdata.ele = {};
				$.extend(localdata.settings, options);
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				edit_value($this, localdata.settings)
			});
		},
		/*
		add_add_button: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					property: undefined,				// Property as given by SALSAH API
					value_index: undefined,					// value index of value to be edited/shown [default: 0]
					options: undefined				// special parameter only used with imagebase.js etc. (used with canvas for regions etc.)
				};
				localdata.ele = {};
				$.extend(localdata.settings, options);
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				edit_value($this, localdata.settings)
		},
		*/
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
		}
		/*===========================================================================*/
	};


	$.fn.editvalue = function(method) {
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
