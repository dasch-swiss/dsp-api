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

/* ===========================================================================
 *
 * @frame: jQuery plugin for SALSAH: NEW extended search (in tableedit)
 *
 * @author André Kilchenmann code@milchkannen.ch
 *
 *
 * @requires
 *  jQuery - min-version 1.10.2
 *
 * ===========================================================================
 * ======================================================================== */

(function( $ ){

	// -----------------------------------------------------------------------
	// define some functions
	// -----------------------------------------------------------------------
	var get_restypes = function(container, id, localdata) {
		var vocabulary = id;
		var param = {
			vocabulary: vocabulary
		};

		SALSAH.ApiGet(
			'resourcetypes', param,
			function(data) {
				if (data.status == ApiErrors.OK)
				{
					var restypes_sel = container.find('select[name="restype"]').empty().append($('<option>', {value: 0}).html('&ndash;'));
					for (var i in data.resourcetypes) {
						restypes_sel.append($('<option>', {value: data.resourcetypes[i].id}).text(data.resourcetypes[i].label));
					}
					get_properties(container, restypes_sel.val(), localdata);
				}
				else {
					alert(data.errormsg);
				}
			}, 'json'
		);
	};

	var add_property_selection = function(ele) {
		var subele, selele;
		ele.append(subele = $('<div>').addClass('control-group extsearch selprop'));
		var n = $('label.control-label').length + 1;
		subele
			.append(
				$('<label>').attr({'for': 'selprop' + n}).html(strings._property + ' ' + n).addClass('control-label')
			)
			.append(
				$('<div>').addClass('controls clearfix')
				.append(selele =
					$('<select>').attr({name: 'selprop', 'id': 'selprop' + n})
								.addClass('form-control extsearch')
								.css({width: '200px', float: 'left'})
				)
				.append(
					$('<select>').attr({name: 'compop'})
								.addClass('form-control extsearch')
								.css({width: '60px', float: 'left'})

				)
				.append(
					$('<span>').attr({name: 'valfield'})
								.addClass('extsearch')
								.css({width: '240px', float: 'left'})
				)
			);

			selele.on('change', function(event) {
				property_changed(subele, $(event.target).val());
			});
		return subele;
	};

	var count_properties = function(ele) {
		// if there's only one property selection, we don't want a remove button!
			$('.remove_button').css({'display': 'inline'});
		if( ele.length === 1 ) {
			$('.remove_button').toggle();
		}
	};

	var get_properties = function(ele, restype, localdata) {
		var param = {};
		if (restype > 0) {
			param.restype = restype;
		}
		else {
			param.vocabulary = ele.find('select[name="vocabulary"].extsearch').val();
		}
		SALSAH.ApiGet(
			'propertylists', param,
			function(data) {
				if (data.status == ApiErrors.OK) {
					var properties_sel = ele.find('select[name=selprop]').empty().append($('<option>', {value: 0}).html('&ndash;'));
					properties = [];
					for (var i in data.properties) {
						properties_sel.append($('<option>').attr({value: data.properties[i].id, title: data.properties[i].longname}).text(data.properties[i].label + ' [' + data.properties[i].shortname + ']'));
						properties[data.properties[i].id] = data.properties[i];
					}
					ele.find('select[name="compop"].extsearch').empty();
					ele.find('span[name="valfield"].extsearch').empty();

					if (typeof localdata.settings.onChangeRestypeCB == 'function') {
						localdata.settings.onChangeRestypeCB(data.properties);
					}
				} else {
					alert(data.errormsg);
				}
			}, 'json'
		);
	};

	var property_changed = function(ele, prop_id) {
		var compop = ele.find('select[name="compop"].extsearch').empty();
		var valfield = ele.find('span[name="valfield"].extsearch').empty();
		var tmpele, selection_id, attrs;

		if (prop_id === 0) return;
		var datatype = parseInt(properties[prop_id].valuetype_id); // must use parseInt() !!
		switch (datatype) {
			case VALTYPE_TEXT: { // we use gui_element = "text"
				compop.append($('<option>').attr({'value': 'MATCH', 'title': 'match'}).append('&isin;'));
				compop.append($('<option>').attr({'value': 'MATCH_BOOLEAN', 'title': 'match boolean'}).append('&isin;&oplus;'));
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
				compop.append($('<option>').attr({'value': 'LIKE', 'title': 'like'}).append('&sub;'));
				compop.append($('<option>').attr({'value': '!LIKE', 'title': 'not like'}).append('&nsub;'));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				valfield.append($('<input>').attr({'type': 'text', name: 'searchval', size: 32, maxlength: 255}).addClass('form-control propval').data('gui_element', 'text'));
				break;
			}
			case VALTYPE_INTEGER: { // we use gui_element = "text"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'GT', 'title': 'greater than'}).append('&gt;'));
				compop.append($('<option>').attr({'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
				compop.append($('<option>').attr({'value': 'LT', 'title': 'less than'}).append('&lt;'));
				compop.append($('<option>').attr({'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
				compop.append($('<option>').attr({'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				valfield.append($('<input>').attr({'type': 'text', name: 'searchval', size: 8, maxlength: 16}).addClass('form-control propval').data('gui_element', 'text'));
				break;
			}
			case VALTYPE_FLOAT: { // we use gui_element = "text"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'GT', 'title': 'greater than'}).append('&gt;'));
				compop.append($('<option>').attr({'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
				compop.append($('<option>').attr({'value': 'LT', 'title': 'less than'}).append('&lt;'));
				compop.append($('<option>').attr({'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
				compop.append($('<option>').attr({'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				valfield.append($('<input>').attr({'type': 'text', name: 'searchval', size: 16, maxlength: 32}).addClass('form-control propval').data('gui_element', 'text'));
				break;
			}
			case VALTYPE_DATE: { // we use gui_element = "date"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'GT', 'title': 'greater than'}).append('&gt;'));
				compop.append($('<option>').attr({'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
				compop.append($('<option>').attr({'value': 'LT', 'title': 'less than'}).append('&lt;'));
				compop.append($('<option>').attr({'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				tmpele = $('<span>', {name: 'searchval'}).addClass('propval').data('gui_element', 'date').appendTo(valfield);
				tmpele.dateobj('edit');
				break;
			}
			case VALTYPE_PERIOD: {
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				compop.append('NOT YET IMPLEMENTED!');
				break;
			}
			case VALTYPE_RESPTR: {
				//
				// first we determine the guielement given for this property
				//
				switch(parseInt(properties[prop_id].guielement_id)) { // don't forget the parseInt() here !
					case 3: { // pulldown
						compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
						compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
						break;
					}
					case 6: { // we use gui_element = "searchbox"
						compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
						compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
						//
						// first we determine if we are able to restrict the selection to a certain resource type,
						// and the number of properties to show
						//
						var restype_id = -1;
						var numprops = 2;
						if ((properties[prop_id].attributes !== undefined) && (properties[prop_id].attributes !== null) && (properties[prop_id].attributes.length > 0)) {
							var pattributes = properties[prop_id].attributes.split(';');
							for (var i in pattributes) {
								var arr = pattributes[i].split('=');
								switch (arr[0]) {
									case 'restypeid': restype_id = arr[1]; break;
									case 'numprops': numprops = arr[1]; break;
								}
								if (arr[0] == 'restypeid') {
									restype_id = arr[1];
								}
							}
						}

						valfield.append(tmpele =
							$('<input>').attr({'type': 'text', name: 'searchval', size: 16, maxlength: 32}).addClass('propval').data('gui_element', 'searchbox'));

						tmpele.searchbox({
							restype_id: restype_id,
							numprops: numprops,
							newEntryAllowed: false,
							clickCB: function(res_id, ele) {
								tmpele.val($(ele).text());
							}
						});

						break;
					}
					case 14: { // richtext, but here we use gui_element = "text", 'cause we are searching for a textstring withing the richtext object'
						compop.append($('<option>').attr({'value': 'MATCH', 'title': 'match'}).append('&isin;'));
						compop.append($('<option>').attr({'value': 'MATCH_BOOLEAN', 'title': 'match boolean'}).append('&isin;&oplus;'));
						compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
						compop.append($('<option>').attr({'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
						compop.append($('<option>').attr({'value': 'LIKE', 'title': 'like'}).append('&sub;'));
						compop.append($('<option>').attr({'value': '!LIKE', 'title': 'not like'}).append('&nsub;'));
						compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
						valfield.append($('<input>').attr({'type': 'text', name: 'searchval', size: 32, maxlength: 255}).addClass('propval').data('gui_element', 'text'));
						break;
					}
				}
				break;
			}
			case VALTYPE_SELECTION: { // we use gui_element = "pulldown"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));

				attrs = properties[prop_id].attributes.split(';');
				$.each(attrs, function() {
					var attr = this.split('=');
					if (attr[0] == 'selection') {
						selection_id = attr[1];
					}
				});
				// var tmpele = $('<span>', {name: 'searchval'}).addClass('propval').data('gui_element', 'pulldown').appendTo(valfield);
				// tmpele.selection('edit', {selection_id: selection_id});
				$('<span>', {name: 'searchval'})
					.addClass('propval')
					.data('gui_element', 'pulldown')
					.selection('edit', {selection_id: selection_id})
					.appendTo(valfield);
				break;
			}
			case VALTYPE_TIME: { // we use gui_element = "text"
				compop.append($('<option>').attr({'value': 'EQ'}).append('='));
				compop.append($('<option>').attr({'value': 'GT', 'title': 'greater than'}).append('&gt;'));
				compop.append($('<option>').attr({'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
				compop.append($('<option>').attr({'value': 'LT', 'title': 'less than'}).append('&lt;'));
				compop.append($('<option>').attr({'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
				compop.append($('<option>').attr({'value': 'IN'}).append('in'));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
				valfield.append($('<input>').attr({'type': 'text', name: 'searchval'}).addClass('form-control propval').data('gui_element', 'text'));
				break;
			}
			case VALTYPE_INTERVAL: {
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				compop.append('NOT YET IMPLEMENTED!');
				break;
			}
			case VALTYPE_GEOMETRY: {
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				compop.append('NOT YET IMPLEMENTED!');
				break;
			}
			case VALTYPE_COLOR: { // we use gui_element = "colorpicker"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
				tmpele = $('<span>').attr({name: 'searchval'}).addClass('propval').data('gui_element', 'colorpicker').appendTo(valfield);
				tmpele.colorpicker('edit');
				break;
			}
			case VALTYPE_HLIST: { // we use gui_element = "hlist"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));

				attrs = properties[prop_id].attributes.split(';');
				$.each(attrs, function() {
					var attr = this.split('=');
					if (attr[0] == 'hlist') {
						hlist_id = attr[1];
					}
				});
				$('<span>', {name: 'searchval'})
					.addClass('propval')
					.data('gui_element', 'hlist')
					.appendTo(valfield)
					.hlist('edit', {hlist_id: hlist_id});
				break;
			}
			case VALTYPE_GEONAME: { // we use gui_element = "geoname"
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));

				$('<span>', {name: 'searchval'})
					.addClass('propval')
					.data('gui_element', 'geonames')
					.appendTo(valfield)
					.geonames('edit');
					break;
			}
			case VALTYPE_ICONCLASS: {
				compop.append($('<option>').attr({'value': 'EQ', 'title': 'equal'}).append('='));
				compop.append($('<option>').attr({'value': 'LIKE', 'title': 'like'}).append('&sub;'));
				compop.append($('<option>').attr({'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
				break;
			}
			default: {
			}
		}

		compop.off('change');
		compop.on('change', function(event) {
			if ($(this).val() == 'EXISTS') {
				valfield.hide();
			}
			else {
				valfield.show();
			}
		});
	};


	// -------------------------------------------------------------------------
	// define the methods
	// -------------------------------------------------------------------------

	var methods = {
		/*========================================================================*/
		init: function(options) {
			return this.each(function() {
				var $this = $(this),
					localdata = {};

				localdata.settings = {
					vocabulary_selected: 0, // preselect a vocabulary; 0 is for ignoring the vocabulary
					limit_sel_to_project: undefined,
					limit_sel_to_restype: undefined,
					gui_ele: undefined,
					kill_gui_ele: false,
					result_ele: undefined,
					display_type: 'table',
					show_nrows: -1,
					start_at: 0,
					onSearchAction: undefined,
					onChangeRestypeCB: undefined
				};
				$.extend(localdata.settings, options);

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				var gui_ele = localdata.settings.gui_ele === undefined ? $this : localdata.settings.gui_ele;

				//
				// set the whole framework for the extended search form
				//
				gui_ele
					// vocabulary filter
					.append(
						$('<div>').addClass('form-group voc_ele')
						.append(
							$('<label>').attr({'for': 'vocabulary'}).html(strings._extsearch_show)
						)
						.append(localdata.selvoc_ele =
							$('<select>').addClass('form-control extsearch').attr({'name': 'vocabulary', 'id': 'vocabulary'})
						)
					)
					// resource type filter
					.append(
						$('<div>').addClass('form-group restype_ele')
						.append(
							$('<label>').attr({'for': 'restype'}).html(strings._resource_type)
						)
						.append(localdata.selrestype_ele =
							$('<select>').addClass('form-control extsearch').attr({'name': 'restype', 'id': 'restype'})
						)
					)
					// property filter
					.append(localdata.selprop_ele =
						$('<div>').addClass('form-group prop_ele')
					)
					.append($('<hr>'))
					// project filter
					.append(
						$('<div>').addClass('form-group proj_ele')
						.append(
							$('<label>').attr({'for': 'project'}).html(strings._project_filter)
						)
						.append(localdata.selproj_ele =
							$('<select>').addClass('form-control extsearch').attr({'name': 'project', 'id': 'project'})
						)
					)
					// owner filter
					.append(
						$('<div>').addClass('form-group owner_ele')
						.append(
							$('<label>').attr({'for': 'owner'}).html(strings._owner_filter)
						)
						.append(localdata.selowner_ele =
							$('<select>').addClass('form-control extsearch').attr({'name': 'owner', 'id': 'owner'})
						)
					)
					.append($('<hr>'))
					.append($('<div>').addClass('clearfix')
						// select number of results
						.append(
							$('<div>').addClass('form-group numres_ele').css({'width': '200px', 'float': 'left'})
							.append(
								$('<label>').attr({'for': 'numres'}).html(strings._show_numres)
							)
							.append(localdata.selnumres_ele =
								$('<select>').addClass('form-control extsearch').attr({name: 'show_nrows', 'id': 'numres'}).css({width: '60px'})
								.append($('<option>').val(25).text('25'))
								.append($('<option>').val(50).text('50'))
								.append($('<option>').val(100).text('100'))
								.append($('<option>').val(200).text('200'))
								.append($('<option>').val(-1).text('all'))
							)
						)
						// select the viewer type: list, lighttable, tableedit
						.append(
							$('<div>').addClass('form-group dispmode_ele').css({'width': '200px', 'float': 'right'})
							.append(
								$('<label>').html(strings._disp_mode)
							)
							.append($('<br>'))
							.append(localdata.seldispmode_ele =
								$('<div>').addClass('btn-group sel_dispmode').attr({'data-toggle': 'buttons'}).css({float: 'none', 'margin-bottom': '15px'})
								.append($('<label>').addClass('btn btn-default').attr({'for': 'dt_1'})
									.append($('<input>').attr({'type': 'radio', 'name': 'display_type', 'id': 'dt_1'}).val('table'))
										.append($('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': strings._disp_list,
											'title': strings._disp_list
										}).addClass('disp_list'))
									//	.append($('<span>').html('<br>' + strings._disp_list))
								)
								.append($('<label>').addClass('btn btn-default').attr({'for': 'dt_2'})
									.append($('<input>').attr({'type': 'radio', 'name': 'display_type', 'id': 'dt_2'}).val('matrix'))
										.append($('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': strings._disp_lightbox,
											'title': strings._disp_lightbox
										}).addClass('disp_lightbox'))
									//	.append($('<span>').html('<br>' + strings._disp_lightbox))
								)
								.append($('<label>').addClass('btn btn-default').attr({'for': 'dt_3'})
									.append($('<input>').attr({'type': 'radio', 'name': 'display_type', 'id': 'dt_3'}).val('editor'))
										.append($('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': strings._disp_tableedit,
											'title': strings._disp_tableedit
										}).addClass('disp_tableedit'))
									//	.append($('<span>').html('<br>' + strings._disp_tableedit))
								)
							)
						)
					);

					// search button
					gui_ele.after(
						$('<div>').addClass('bottom space4btn')
						.append(
							localdata.search_ele = $('<input>').attr({type: 'button', value: strings._search}).css({cursor: 'pointer'})

							//.click(function(data) {do_search($('.geography div.space4filter').hlist('value'), 'geonames');
								// start search here})
						)
					);

					/*
					.append(
						$('<div>').addClass('form-group search_ele center')
						.append(localdata.search_ele = $('<input>').attr({'type': 'button', 'value': strings._search}).addClass('btn btn-defaul'))
					);
					*/

				//
				// get the vocabularies
				//
				if (localdata.settings.limit_sel_to_restype === undefined) {
					if (localdata.settings.limit_sel_to_project === undefined) {
						localdata.selvoc_ele.append($('<option>').attr({'value': 0}).append(strings._all));
						SALSAH.ApiGet('vocabularies', function(data)
						{
							if (data.status == ApiErrors.OK)
							{
								var tmpele;
								for (var i in data.vocabularies)
								{
									localdata.selvoc_ele
										.append(tmpele =
											$('<option>').attr({'value': data.vocabularies[i].id})
												.append(data.vocabularies[i].longname + ' [' + data.vocabularies[i].shortname + ']')
										);
									if (data.vocabularies[i].active) {
										tmpele.prop({selected: 'selected'});
										localdata.settings.vocabulary_selected = data.vocabularies[i].id;
									}
								}
								get_restypes(gui_ele, localdata.selvoc_ele.val(), localdata);
							}
							else {
								alert(data.errormsg);
							}
						}, 'json');
					}
					else {
						SALSAH.ApiGet('vocabularies', localdata.settings.limit_sel_to_project, function(data)
						{
							if (data.status == ApiErrors.OK)
							{
								for (var i in data.vocabularies) {
									localdata.selvoc_ele.append(
										$('<option>').attr({'value': data.vocabularies[i].id})
											.append(data.vocabularies[i].longname + ' [' + data.vocabularies[i].shortname + ']')
									);
									if (data.vocabularies[i].active) tmpele.prop({selected: 'selected'});
								}
							}
							else {
								alert(data.errormsg);
							}
						}, 'json');
					}
				}
				else {
					localdata.selrestype = localdata.settings.limit_sel_to_restype;
				}

				//
				// vocabularies on change: update the resourcetypes
				//
				localdata.selvoc_ele.on('change', function(event) {
					var id = $(this).val();
					if (localdata.settings.vocabulary_selected != id) {
						localdata.settings.vocabulary_selected = id;
						get_restypes(gui_ele, id, localdata);
					}
				});

				//
				// resourcetypes on change: update the property select options
				//
				localdata.selrestype_ele.on('change', function(event) {
					var restype_alert = $('.missing-restype');
					localdata.selrestype = $(event.target).val();
					if(localdata.selrestype !== '0' && restype_alert.is(':visible')) {
						restype_alert.remove();
					}
					get_properties(gui_ele, localdata.selrestype, localdata);
				});

				//
				// set the property select options
				//
				add_property_selection(localdata.selprop_ele);

				//
				// set the add/remove buttons for more/less property filters
				//
				localdata.selprop_ele.after(
					// add/remove icon
					$('<div>').addClass('btn-toolbar').css({float: 'left'})
					.append(
						$('<div>').addClass('btn-group btn-group-xs')
						.append(localdata.selprop_plus =
							$('<button>').addClass('btn btn-default btn-xs add_button')
							.append($('<span>').addClass('glyphicon glyphicon-plus'))
						)
						.append(localdata.selprop_minus =
							$('<button>').addClass('btn btn-default btn-xs remove_button')
							.append($('<span>').addClass('glyphicon glyphicon-minus'))
						)
					)
				);
				count_properties(localdata.selprop_ele.children());

				localdata.selprop_plus.on('click', function() {
					var subele = add_property_selection(localdata.selprop_ele);
					get_properties(subele, localdata.selrestype, localdata);
					count_properties(localdata.selprop_ele.children());
				});
				localdata.selprop_minus.on('click', function() {
					localdata.selprop_ele.find('.selprop:last').remove();
					count_properties(localdata.selprop_ele.children());
				});

				//
				// set the project filter
				//
				if (localdata.settings.limit_sel_to_project === undefined) {
					localdata.selproj_ele.append($('<option>').attr({'value': 0}).html('&ndash;'));
					/*
					localdata.projfilt = $('<select>').attr({name: 'project_filter'}).addClass('extsearch').append($('<option>', {'value': 0}).append('-'));
					gui_ele.append(strings._project_filter + ': ').append(localdata.projfilt).append($('<br>'));
					*/
					SALSAH.ApiGet('projects', function(data){
						if (data.status == ApiErrors.OK) {
							for (var i in data.projects) {
								if (data.projects[i].active) {
									localdata.selproj_ele.append($('<option>').attr({'value': data.projects[i].id, 'selected': 'selected'}).append(data.projects[i].shortname + '*'));
								}
								else {
									localdata.selproj_ele.append($('<option>').attr({'value': data.projects[i].id}).append(data.projects[i].shortname));
								}
							}
						}
						else {
							alert(data.errormsg);
						}
					});
				}
				else {
					localdata.selproj_ele = $('<input>').attr({type: 'hidden', name: 'project_filter'}).val(localdata.settings.limit_sel_to_project).addClass('extsearch');
					gui_ele.append(localdata.selproj_ele);
				}
				//
				// set the owner filter
				//
				/*
				var ownerfilt = $('<select>').attr({name: 'owner_filter'}).addClass('extsearch').append($('<option>', {'value': 0}).append('-'));
				gui_ele.append(strings._owner_filter + ': ').append(ownerfilt).append($('<br>'));
				*/
				localdata.selowner_ele.append($('<option>').attr({'value': 0}).html('&ndash;'));
				var pers_of_project = {};
				if (localdata.settings.limit_sel_to_project !== undefined) pers_of_project = {project: localdata.settings.limit_sel_to_project};
				SALSAH.ApiGet('persons', pers_of_project, function(data) {
				if (data.status == ApiErrors.OK)
				{
					for (var i in data.persons)
					{
						if (data.persons[i].active) {
							localdata.selowner_ele.append($('<option>').attr({'value': data.persons[i].id, selected: 'selected'})
								.append('*' + data.persons[i].lastname + ', ' + data.persons[i].firstname)
							);
						}
						else {
							localdata.selowner_ele.append($('<option>').attr({'value': data.persons[i].id})
								.append(data.persons[i].lastname + ', ' + data.persons[i].firstname)
							);
						}
					}
				}
				else {
					alert(data.errormsg);
				}
				}, 'json');

				var radio4disp;
				switch(localdata.settings.display_type) {
					case 'table':
						radio4disp = gui_ele.find('input[name="display_type"][value="table"]');
						break;

					case 'matrix':
						radio4disp = gui_ele.find('input[name="display_type"][value="matrix"]');
						break;

					default:
						radio4disp = gui_ele.find('input[name="display_type"][value="editor"]');
				}
				radio4disp.attr({'checked': 'checked'}).parent().toggleClass('active');

				//
				// set the action for the search button
				//
				localdata.search_ele.on('click', function(event) {

					var restype_id = localdata.selrestype;

					var searchval_ele = gui_ele.find('.propval[name="searchval"]'); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					var property_id_ele = gui_ele.find('select[name="selprop"]');
					var compop_ele = gui_ele.find('select[name="compop"]');
					//  var gui_element = searchval_ele.data('gui_element');
					var progbar;
					var xhr;

					var compop = [];
					var searchval = [];
					var property_id = [];
					property_id_ele.each(function(index) {
						property_id.push($(property_id_ele[index]).val());
						compop.push($(compop_ele[index]).val());
						var gui_element = $(searchval_ele[index]).data('gui_element');
						switch (gui_element) {
							case 'text': {
								searchval.push($(searchval_ele[index]).val());
								break;
							}
							case 'date': {
								searchval.push($(searchval_ele[index]).dateobj('value'));
								break;
							}
							case 'pulldown': {
								searchval.push($(searchval_ele[index]).selection('value'));
								break;
							}
							case 'hlist': {
								searchval.push($(searchval_ele[index]).hlist('value'));
								break;
							}
							case 'searchbox': {
								searchval.push($(searchval_ele[index]).searchbox('value'));
								break;
							}
							case 'colorpicker': {
								searchval.push($(searchval_ele[index]).colorpicker('value'));
								break;
							}
							case 'geonames': {
								searchval.push($(searchval_ele[index]).geonames('value'));
								break;
							}
							default: {
								searchval.push(undefined); // must be!!!
							}
						}
					});

					if ((restype_id === 0) && (property_id === 0) && (localdata.selproj_ele.val() === 0) && (localdata.selowner_ele.val() === 0)) {
						alert('no search params!');
						return;
					}

					$this.find('div[name=result].searchresult').empty(); // clean previous searches if present
					if ((gui_ele !== $this) && localdata.settings.kill_gui_ele) {
						gui_ele.remove();
					}

					var searchparams = {
						property_id: property_id,
						compop: compop,
						searchval: searchval,
						show_nrows: gui_ele.find('select[name="show_nrows"]').val(),
						start_at: localdata.settings.start_at,
						display_type: gui_ele.find('input[name="display_type"]:checked').val()
					};
					if (restype_id > 0) {
						searchparams.filter_by_restype = restype_id;
					}
					if (localdata.selproj_ele.val() > 0) {
						searchparams.filter_by_project = localdata.selproj_ele.val();
					}
					if (localdata.selowner_ele.val() > 0) {
						searchparams.filter_by_owner = localdata.selowner_ele.val();
					}
					if (typeof localdata.settings.onSearchAction === 'function') {
						localdata.settings.onSearchAction(searchparams);
					}
					else {
						$.searchext('perform_search', $this.find('div[name=result].searchresult'), searchparams);
					}
				});

				if (localdata.settings.result_ele === undefined) {
					$this.append($('<div>').attr({name: 'result'}).addClass('searchresult extsearch'));
				}
				else {
					localdata.settings.result_ele.append($('<div>').attr({name: 'result'}).addClass('searchresult extsearch'));
				}
				if (localdata.settings.limit_sel_to_restype === undefined) {
				//					get_restypes($this);
				}
				else {
					get_properties($this, localdata.settings.limit_sel_to_restype, localdata);
				}

			});											// end "return this.each"
		},												// end "init"


		perform_search: function(result_ele, params) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				var progvalfile = 'prog_' + Math.floor(Math.random()*1000000.0).toString() + '.salsah';

				result_ele.empty(); // clean previous searches if present
				result_ele.append(progbar = $('<div>').css({position: 'absolute', left: 10, height: 20, right: 10}).pgbar({show_numbers: true, cancel: function(){
					window.clearInterval(progvaltimer);
					xhr.abort();
					result_ele.empty();
				}}));

				var searchparams = {
					searchtype: 'extended',
					property_id: params.property_id,
					compop: params.compop,
					searchval: params.searchval,
					show_nrows: (params.show_nrows === undefined) ? -1 : params.show_nrows,
					start_at: (params.start_at === undefined) ? 0 : params.start_at,
					progvalfile: progvalfile
				};
				if (params.filter_by_restype !== undefined) {
					searchparams.filter_by_restype = params.filter_by_restype;
				}
				if (params.filter_by_project !== undefined) {
					searchparams.filter_by_project = params.filter_by_project;
				}
				if (params.filter_by_owner !== undefined) {
					searchparams.filter_by_owner = params.filter_by_owner;
				}

				SALSAH.ApiGet('search', searchparams, function(data) {
					if (data.status == ApiErrors.OK) {
						window.clearInterval(progvaltimer);
						var ele = result_ele;
						var pele;
						//
						// Create the paging
						//
						ele.empty();
						ele.append($('<div>').addClass('center').text(strings._extsearchres.replace(/%d/, data.nhits)));
						if (data.paging.length > 0) {
							pele = $('<div>').addClass('pages center');
							for (var i in data.paging) {
								var ii = parseInt(i) + 1;
								pele.append(' ');
								if (data.paging[i].current) {
									pele.append($('<span>').addClass('paging_active').text('[' + ii  + ']'));
								}
								else {
									var title_txt = data.paging[i].start_at + ' - ' + (parseInt(data.paging[i].start_at) + parseInt(data.paging[i].show_nrows));
									pele.append($('<a>').attr({href: '#', title: title_txt}).addClass('paging').data('start_at', data.paging[i].start_at).text('[' + ii + ']').on('click', function(event){
										params.start_at = $(this).data('start_at');

										ele.searchext('perform_search', result_ele, params);

//										$.extsearch.perform_search(result_ele, params);
									}));
								}
							}
						}
						SALSAH.searchlist(ele, pele, data, params, 'extended');

					}
					else {
						window.clearInterval(progvaltimer);
						alert(data.errormsg);
					}
				});

				var progvaltimer = window.setInterval(function() {
					SALSAH.ApiGet('search', {progvalfile: progvalfile}, function(data) {
						if (data.status == ApiErrors.OK) {
							var val = data.progress.split(':');
							progbar.pgbar('update', parseInt(val[0]), parseInt(val[1]));
						}
						else {
							window.status = ' ERROR: ' + data.errormsg;
						}
					});
				}, 1000);
			});
		},

/*
// perform_search as an own method or as a function!!!!!!???????
	perform_search: function(result_ele, params) {

	},
*/


/*
gui_ele.append($('<div>').append($('<img>').attr({src: add_icon.src}).on('click', function() {
	var subele = add_property_selection(propsel);
	get_properties(subele, selrestype);
})).append($('<img>').attr({src: delete_icon.src}).on('click', function() {
	propsel.find('.selprop:last').remove();
}))
);
*/

/*

				var selrestype;


				if (localdata.settings.limit_sel_to_restype === undefined) {
					var vocsel;
					gui_ele.append();
					//
					// get vocabularies
					//
					gui_ele.append(vocsel = $('<select>', {'class': 'form-control extsearch', name: 'vocabulary', 'id': 'vocabulary'}).on('change', function(ev) {
						var id = $(this).val();
						if (localdata.settings.vocabulary_selected != id) {
							localdata.settings.vocabulary_selected = id;
							get_restypes(gui_ele);
						}
					}));
					if (localdata.settings.limit_sel_to_project === undefined) {
						vocsel.append($('<option>', {value: 0}).append(strings._all));
						SALSAH.ApiGet('vocabularies', function(data)
						{
							if (data.status == ApiErrors.OK)
							{
								var tmpele;
								for (var i in data.vocabularies)
								{
									vocsel.append(tmpele = $('<option>', {value: data.vocabularies[i].id}).append(data.vocabularies[i].longname + ' [' + data.vocabularies[i].shortname + ']'));
									if (data.vocabularies[i].active) {
										tmpele.prop({selected: 'selected'});
										localdata.settings.vocabulary_selected = data.vocabularies[i].id;
									}
								}
								get_restypes($this);
							}
							else {
								alert(data.errormsg);
							}
						}, 'json');
					}
					else {
						SALSAH.ApiGet('vocabularies', localdata.settings.limit_sel_to_project, function(data)
						{
							if (data.status == ApiErrors.OK)
							{
								for (var i in data.vocabularies) {
									vocsel.append($('<option>', {value: data.vocabularies[i].id}).append(data.vocabularies[i].longname + ' [' + data.vocabularies[i].shortname + ']'));
									if (data.vocabularies[i].active) tmpele.prop({selected: 'selected'});
								}
							}
							else {
								alert(data.errormsg);
							}
						}, 'json');
					}





					gui_ele.append($('<hr>', {'class': 'extsearch'}))
					//.append(strings._extsearch_for).append($('<br>'))
					.append($('<label>').text(strings._resource_type + ': ').attr({'for': 'selrestype'}))
					.append($('<select>', {'class': 'form-control extsearch', name: 'selrestype', 'id': 'selrestype'}).change(function(event) {
						selrestype = $(event.target).val();
						get_properties(gui_ele, selrestype);
					}))
					.append($('<br>'));
				}
				else {
					selrestype = localdata.settings.limit_sel_to_restype;
					//projfilt = $('<input>').attr({type: 'hidden', name: 'project_filter'}).val(localdata.settings.limit_sel_to_project).addClass('extsearch');
					//gui_ele.append(projfilt);
				}
*/
/*
				gui_ele.append($('<hr>').addClass('extsearch'));
				var propsel = $('<div>').appendTo(gui_ele);

				add_property_selection(propsel);

				gui_ele.append($('<div>').append($('<img>').attr({src: add_icon.src}).on('click', function() {
					var subele = add_property_selection(propsel);
					get_properties(subele, selrestype);
				})).append($('<img>').attr({src: delete_icon.src}).on('click', function() {
					propsel.find('.selprop:last').remove();
				}))
				);
				gui_ele.append($('<hr>').addClass('extsearch'));
*/


/*
				gui_ele
				.append($('<br>'))
				.append(strings._disp_mode + ': ')
				.append($('<label>')
					.append($('<input>').attr({type: 'radio', name: 'display_type', title: strings._disp_list}).val('table').addClass('extsearch'))
					.append($('<input>').attr({
						'type': 'image',
						'src': SITE_URL + '/app/icons/0.gif',
						'alt': strings._disp_list,
						'title': strings._disp_list
					}).addClass('disp_list'))
				)

				.append('  ')
				.append($('<label>')
				.append($('<input>').attr({type: 'radio', name: 'display_type', title: strings._disp_lightbox}).val('matrix').addClass('extsearch'))
					.append($('<input>').attr({
						'type': 'image',
						'src': SITE_URL + '/app/icons/0.gif',
						'alt': strings._disp_lightbox,
						'title': strings._disp_lightbox
					}).addClass('disp_lightbox'))
				)

				.append('  ')
				.append($('<label>').css({display: 'none'}).addClass('label4disp_tableedit')
				.append($('<input>').attr({type: 'radio', name: 'display_type', title: strings._disp_tableedit}).val('editor').addClass('extsearch'))
					.append($('<input>').attr({
						'type': 'image',
						'src': SITE_URL + '/app/icons/0.gif',
						'alt': strings._disp_tableedit,
						'title': strings._disp_tableedit
					}).addClass('disp_tableedit'))
				);
				*/
				/*
				//				.append($('<img>').attr({src: 'app/icons/sets/gentleface-free-icon-set/black/png/2x2_grid_icon&16.png', title: strings._disp_lightbox, alt: ' | ' + strings._disp_lightbox}))
				.append('  ')
				.append($('<input>').attr({type: 'radio', name: 'display_type', title: strings._disp_grideditor}).val('editor').addClass('extsearch'))
				.append($('<input>').attr({
					'type': 'image',
					'src': SITE_URL + '/app/icons/0.gif',
					'alt': strings._disp_tableedit,
					'title': strings._disp_tableedit
				}).addClass('disp_tableedit'));
				*/
				//				.append($('<img>').attr({src: 'app/icons/sets/gentleface-free-icon-set/black/png/3x3_grid_icon&16.png', title: strings._disp_grideditor, alt: ' | ' + strings._disp_grideditor}));
				// .append(' ' + strings._disp_extlist)
				// .append($('<input>').attr({type: 'radio', name: 'display_type'}).val('exttable').addClass('extsearch'));









		anotherMethod: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		}
		/*========================================================================*/
	};



	$.fn.searchext = function(method) {
		// Method calling logic
		if ( methods[method] ) {
			return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
		} else if ( typeof method === 'object' || ! method ) {
			return methods.init.apply( this, arguments );
		} else {
			throw 'Method ' + method + ' does not exist on jQuery.searchext';
		}
	};
})( jQuery );
