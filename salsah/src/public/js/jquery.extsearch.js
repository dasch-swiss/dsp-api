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
 * @author: Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 */

 (function( $ ) {
	 $.extsearch = {};

	var add_icon = new Image();
	add_icon.src = SITE_URL + '/app/icons/16x16/add.png';

	var delete_icon = new Image();
	delete_icon.src = SITE_URL + '/app/icons/16x16/delete.png';



	$.extsearch.perform_search = function(result_ele, params) {
		//var progvalfile = 'prog_' + Math.floor(Math.random()*1000000.0).toString() + '.salsah';

		result_ele.empty(); // clean previous searches if present
		/*result_ele.append(progbar = $('<div>').css({position: 'absolute', left: 10, height: 20, right: 10}).pgbar({show_numbers: true, cancel: function(){
			window.clearInterval(progvaltimer);
			xhr.abort();
			result_ele.empty();
		}}));*/

		//console.log(params);

		var searchparams = {
			searchtype: 'extended',
			property_id: params.property_id,
			compop: params.compop,
			searchval: params.searchval,
			show_nrows: (params.show_nrows === undefined) ? -1 : params.show_nrows,
			start_at: (params.start_at === undefined) ? 0 : params.start_at//,
			//progvalfile: progvalfile
		};

		// check if property_id is ["0"] which is the default when no property is selected
		if (searchparams.property_id.length == 1 && searchparams.property_id[0] == "0") {
			// no property_id is selected, remove all of it
			delete searchparams.property_id;
			delete searchparams.compop;
			delete searchparams.searchval;
		}

		if (params.filter_by_restype !== undefined) {
			searchparams.filter_by_restype = params.filter_by_restype;
		}
		if (params.filter_by_project !== undefined) {
			searchparams.filter_by_project = params.filter_by_project;
		}
		if (params.filter_by_owner !== undefined) {
			searchparams.filter_by_owner = params.filter_by_owner;
		}

		//console.log("search params: ")
		//console.log(searchparams)

		SALSAH.ApiGet('search/', searchparams, function(data) {
			if (data.status == ApiErrors.OK) {
				//window.clearInterval(progvaltimer);
				var ele = result_ele;
				var pele;
				//
				// Create the paging
				//
				ele.empty();
				$('.result_info').text(strings._extsearchres.replace(/%d/, data.nhits));
				ele.append($('<div>').addClass('center').text(strings._extsearchres.replace(/%d/, data.nhits)));
				if (data.paging.length > 0) {
					pele = $('<div>').addClass('center');
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
								$.extsearch.perform_search(result_ele, params);
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

		/*var progvaltimer = window.setInterval(function() {
			SALSAH.ApiGet('search', {progvalfile: progvalfile}, function(data) {
				if (data.status == ApiErrors.OK) {
					var val = data.progress.split(':');
					progbar.pgbar('update', parseInt(val[0]), parseInt(val[1]));
				}
				else {
					window.status = ' ERROR: ' + data.errormsg;
				}
			});
		}, 1000);*/

	};


	var methods = {
		/**=============================================================================================================
		* @method init Initializes the plugin
		*
		* @options
		* @int vocabulary_selected: ID of a preselected vocabulary, 0 to present selection of vocabularies [default: 0]
		* @int limit_sel_to_project Limit selection to given project (and it's selection possibilities) [default: undefined]
		* @int limit_sel_to_restype Limit the selection to the given resource_type (and it's selection possibilities) [default: undefined]
		* @object gui_ele jQuery DOM-object which is used to present the extended search gui. If undefined, $this is used [default: undefined]
		* @bool kill_gui_ele Kill the GUI before displaying the search results [default: false]
		* @object result_ele jQuery object which is used to display the result. If undefined, $tgis is used [default: undefined]
		* @string display_type Indicate weather to use the 'matrix' or 'table' (=list) format. [default: 'table']
		* @string search_script The server-side script that is called to perform [default: /helper/extended_search.php]
		*--------------------------------------------------------------------------------------------------------------
		*/
		init: function(options) {


			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					vocabulary_selected: 0, // preselect a vocabulary; 0 is for ignoring the vocabulary
					limit_sel_to_project: undefined,
					limit_sel_to_restype: undefined,
					gui_ele: undefined,
					kill_gui_ele: false,
					result_ele: undefined,
					no_owner_filter: undefined,
					display_type: 'table',
					no_display_type: undefined,
					show_nrows: -1,
					start_at: 0,
					onSearchAction: undefined,
					onChangeRestypeCB: undefined
				};
				$.extend(localdata.settings, options);

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				var get_restypes = function($this)
				{
					var vocabulary = $this.find('select[name=vocabulary].extsearch').val();
					var param = {
						vocabulary: vocabulary
					};

					SALSAH.ApiGet(
						'resourcetypes', param,
						function(data) {
							if (data.status == ApiErrors.OK)
							{
								var restypes_sel = $this.find('select[name="selrestype"]').empty().append($('<option>', {value: 0}).text('-'));
								for (var i in data.resourcetypes) {
									restypes_sel.append($('<option>', {value: data.resourcetypes[i].id}).text(data.resourcetypes[i].label));
								}
								get_properties($this, restypes_sel.val());
							}
							else {
								alert(data.errormsg);
							}
						}, 'json'
					);
				};

				var get_properties = function(ele, restype) {
					var param = {};
					if (restype != 0) { // if restype does not equal 0, a restype Iri is requested
						param.restype = restype;
					}
					else {
						param.vocabulary = ele.find('select[name="vocabulary"].extsearch').val();
					}
					SALSAH.ApiGet(
						'propertylists', param,
						function(data) {
							if (data.status == ApiErrors.OK) {
								var properties_sel = ele.find('select[name=selprop]').empty().append($('<option>', {value: 0}).text('-'));
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
								alert(data.errormsg)
							}
						}, 'json'
					);
				};

				var property_changed = function(ele, prop_id) {
					var compop = ele.find('select[name="compop"].extsearch').empty();
					var valfield = ele.find('span[name="valfield"].extsearch').empty();

					if (prop_id == 0) return;
					var datatype = properties[prop_id].valuetype_id; // it is an Iri
					//console.log("prop changed to: " + prop_id + " and datatype = " + datatype);
					//console.log("attrs: " + properties[prop_id].attributes);
					switch (datatype) {
						case VALTYPE_RICHTEXT:
						case VALTYPE_TEXT: { // we use gui_element = "text"
							compop.append($('<option>', {'value': 'MATCH', 'title': 'match'}).append('&isin;'));
							compop.append($('<option>', {'value': 'MATCH_BOOLEAN', 'title': 'match boolean'}).append('&isin;&oplus;'));
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
							compop.append($('<option>', {'value': 'LIKE', 'title': 'like'}).append('&sub;'));
							compop.append($('<option>', {'value': '!LIKE', 'title': 'not like'}).append('&nsub;'));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							valfield.append($('<input>', {'type': 'text', name: 'searchval', size: 32, maxlength: 255}).addClass('propval').data('gui_element', 'text'));
							break;
						}
						case VALTYPE_INTEGER: { // we use gui_element = "text"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'GT', 'title': 'greater than'}).append('&gt;'));
							compop.append($('<option>', {'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
							compop.append($('<option>', {'value': 'LT', 'title': 'less than'}).append('&lt;'));
							compop.append($('<option>', {'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
							compop.append($('<option>', {'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							valfield.append($('<input>', {'type': 'text', name: 'searchval', size: 8, maxlength: 16}).addClass('propval').data('gui_element', 'text'));
							break;
						}
						case VALTYPE_FLOAT: { // we use gui_element = "text"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'GT', 'title': 'greater than'}).append('&gt;'));
							compop.append($('<option>', {'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
							compop.append($('<option>', {'value': 'LT', 'title': 'less than'}).append('&lt;'));
							compop.append($('<option>', {'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
							compop.append($('<option>', {'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							valfield.append($('<input>', {'type': 'text', name: 'searchval', size: 16, maxlength: 32}).addClass('propval').data('gui_element', 'text'));
							break;
						}
						case VALTYPE_DATE: { // we use gui_element = "date"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'GT', 'title': 'greater than'}).append('&gt;'));
							compop.append($('<option>', {'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
							compop.append($('<option>', {'value': 'LT', 'title': 'less than'}).append('&lt;'));
							compop.append($('<option>', {'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							var tmpele = $('<span>', {name: 'searchval'}).addClass('propval').data('gui_element', 'date').appendTo(valfield);
							tmpele.dateobj('edit');
							break;
						}
						case VALTYPE_PERIOD: {
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							compop.append('NOT YET IMPLEMENTED!');
							break;
						}
						case VALTYPE_RESPTR: {
							//
							// first we determine the guielement given for this property
							//
							switch(parseInt(properties[prop_id].guielement_id)) { // don't forget the parseInt() here !
								case 3: { // pulldown
									compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
									compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
									break;
								}
								case 6: { // we use gui_element = "searchbox"
									compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
									compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
									//
									// first we determine if we are able to restrict the selection to a certain resource type,
									// and the number of properties to show
									//
									var restype_id = -1;
									var numprops = 2;
									if ((properties[prop_id].attributes !== undefined) && (properties[prop_id].attributes != null) && (properties[prop_id].attributes.length > 0)) {
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

									var tmpele;
									valfield.append(tmpele = $('<input>', {'type': 'text', name: 'searchval', size: 16, maxlength: 32}).addClass('propval').data('gui_element', 'searchbox'));

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
									compop.append($('<option>', {'value': 'MATCH', 'title': 'match'}).append('&isin;'));
									compop.append($('<option>', {'value': 'MATCH_BOOLEAN', 'title': 'match boolean'}).append('&isin;&oplus;'));
									compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
									compop.append($('<option>', {'value': '!EQ', 'title': 'not equal'}).append('&ne;'));
									compop.append($('<option>', {'value': 'LIKE', 'title': 'like'}).append('&sub;'));
									compop.append($('<option>', {'value': '!LIKE', 'title': 'not like'}).append('&nsub;'));
									compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
									valfield.append($('<input>', {'type': 'text', name: 'searchval', size: 32, maxlength: 255}).addClass('propval').data('gui_element', 'text'));
									break;
								}
							}
							break;
						}
						// VALTYPE_SELECTION can be treated like a hierarchical list
						/*case VALTYPE_SELECTION: { // we use gui_element = "pulldown"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));

							var selection_id;
							var attrs = properties[prop_id].attributes;//.split(';');
							selection_id = attrs.split("=")[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL

							//console.log(selection_id)
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
						}*/
						case VALTYPE_TIME: { // we use gui_element = "text"
							compop.append($('<option>', {'value': 'EQ'}).append('='));
							compop.append($('<option>', {'value': 'GT', 'title': 'greater than'}).append('&gt;'));
							compop.append($('<option>', {'value': 'GT_EQ', 'title': 'greater equal than'}).append('&ge;'));
							compop.append($('<option>', {'value': 'LT', 'title': 'less than'}).append('&lt;'));
							compop.append($('<option>', {'value': 'LT_EQ', 'title': 'less equal than'}).append('&le;'));
							compop.append($('<option>', {'value': 'IN'}).append('in'));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
							valfield.append($('<input>', {'type': 'text', name: 'searchval'}).addClass('propval').data('gui_element', 'text'));
							break;
						}
						case VALTYPE_INTERVAL: {
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							compop.append('NOT YET IMPLEMENTED!');
							break;
						}
						case VALTYPE_GEOMETRY: {
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
							compop.append('NOT YET IMPLEMENTED!');
							break;
						}
						case VALTYPE_COLOR: { // we use gui_element = "colorpicker"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));
							var tmpele = $('<span>', {name: 'searchval'}).addClass('propval').data('gui_element', 'colorpicker').appendTo(valfield);
							tmpele.colorpicker('edit');
							break;
						}
						case VALTYPE_HLIST: { // we use gui_element = "hlist"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));

							var hlist_id;
							var attrs = properties[prop_id].attributes; // "hlist=<http://data.knora.org/lists/73d0ec0302>" -> hlist's root node
							hlist_id = attrs.split("=")[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL

							/*var attrs = properties[prop_id].attributes.split(';');
							$.each(attrs, function() {
								var attr = this.split('=');
								if (attr[0] == 'hlist') {
									hlist_id = attr[1];
								}
							});*/
							$('<span>', {name: 'searchval'})
								.addClass('propval')
								.data('gui_element', 'hlist')
								.appendTo(valfield)
								.hlist('edit', {hlist_id: hlist_id});
							break;
						}
						case VALTYPE_GEONAME: { // we use gui_element = "geoname"
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'Exists'}).append('&exist;'));

							var selection_id;
							$('<span>', {name: 'searchval'})
								.addClass('propval')
								.data('gui_element', 'geonames')
								.appendTo(valfield)
								.geonames('edit');
								break;
						}
						case VALTYPE_ICONCLASS: {
							compop.append($('<option>', {'value': 'EQ', 'title': 'equal'}).append('='));
							compop.append($('<option>', {'value': 'LIKE', 'title': 'like'}).append('&sub;'));
							compop.append($('<option>', {'value': 'EXISTS', 'title': 'exists'}).append('&exist;'));
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

				var add_property_selection = function(ele) {
					var subele;
					ele.append(subele = $('<div>').addClass('extsearch selprop'));
					subele.append(strings._property + ':')
						.append($('<select>').attr({name: 'selprop'}).addClass('extsearch').change(function(event) {
							property_changed(subele, $(event.target).val());
						}))
						.append($('<select>').attr({name: 'compop'}).addClass('extsearch'))
						.append($('<span>').attr({name: 'valfield'}).addClass('extsearch'));
					return subele;
				};
				var gui_ele = localdata.settings.gui_ele === undefined ? $this : localdata.settings.gui_ele;

				var selrestype;
				if (localdata.settings.limit_sel_to_restype === undefined) {
					var vocsel;
					gui_ele.append(strings._extsearch_show);
					//
					// get vocabularies
					//
					gui_ele.append(vocsel = $('<select>', {'class': 'extsearch', name: 'vocabulary'}).on('change', function(ev) {
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
						}, 'json'); // TODO: remove datatype because this is processed as an argumgent in SALSAH.ApiGet
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
					.append(strings._extsearch_for).append($('<br>'))
					.append(strings._resource_type + ':')
					.append($('<select>', {'class': 'extsearch', name: 'selrestype'}).change(function(event) {
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

				gui_ele.append($('<hr>').addClass('extsearch'));
				var propsel = $('<div>').appendTo(gui_ele);

				add_property_selection(propsel);
				/* */
				gui_ele.append($('<div>').append($('<img>').attr({src: add_icon.src}).on('click', function() {
					var subele = add_property_selection(propsel);
					get_properties(subele, selrestype);
				})).append($('<img>').attr({src: delete_icon.src}).on('click', function() {
					propsel.find('.selprop:last').remove();
				}))
			);
			/* */
			gui_ele.append($('<hr>').addClass('extsearch'));

			var projfilt;
			if (localdata.settings.limit_sel_to_project === undefined) {
				projfilt = $('<select>').attr({name: 'project_filter'}).addClass('extsearch').append($('<option>', {'value': 0}).append('-'));
				gui_ele.append(strings._project_filter + ': ').append(projfilt).append($('<br>'));
				SALSAH.ApiGet('projects', function(data){
					if (data.status == ApiErrors.OK) {
						for (var i in data.projects) {
							if (data.projects[i].active) {
								projfilt.append($('<option>', {'value': data.projects[i].id, selected: 'selected'}).append(data.projects[i].shortname + '*'));
							}
							else {
								projfilt.append($('<option>', {'value': data.projects[i].id}).append(data.projects[i].shortname));
							}
						}
					}
					else {
						alert(data.errormsg);
					}
				});
			}
			else {
				projfilt = $('<input>').attr({type: 'hidden', name: 'project_filter'}).val(localdata.settings.limit_sel_to_project).addClass('extsearch');
				gui_ele.append(projfilt);
			}

			var ownerfilt;
			if (localdata.settings.no_owner_filter === undefined) {
				ownerfilt = $('<select>').attr({name: 'owner_filter'}).addClass('extsearch').append($('<option>', {'value': 0}).append('-'));
				gui_ele.append(strings._owner_filter + ': ').append(ownerfilt).append($('<br>'));
				var pers_of_project = {};
				if (localdata.settings.limit_sel_to_project !== undefined) pers_of_project = {project: localdata.settings.limit_sel_to_project};
				/*SALSAH.ApiGet('persons', pers_of_project, function(data) {
					if (data.status == ApiErrors.OK)
					{
						for (var i in data.persons)
						{
							if (data.persons[i].active) {
								ownerfilt.append($('<option>', {'value': data.persons[i].id, selected: 'selected'}).append('*' + data.persons[i].lastname + ', ' + data.persons[i].firstname));
							}
							else {
								ownerfilt.append($('<option>', {'value': data.persons[i].id}).append(data.persons[i].lastname + ', ' + data.persons[i].firstname));
							}
						}
					}
					else {
						alert(data.errormsg);
					}
				}, 'json');*/
			}
			else {
				ownerfilt = $('<input>').attr({type: 'hidden', name: 'owner_filter'}).val(0).addClass('extsearch');
				gui_ele.append(ownerfilt);
			}

			gui_ele.append($('<hr>').addClass('extsearch'))
			.append(strings._show_numres +  ': ')
			.append(
				$('<select>').attr({name: 'show_nrows'}).addClass('extsearch')
				.append($('<option>').val(25).text('25'))
				.append($('<option>').val(50).text('50'))
				.append($('<option>').val(100).text('100'))
				.append($('<option>').val(200).text('200'))
				.append($('<option>').val(-1).text('all'))
			);

			if (localdata.settings.no_display_type === undefined) {
				gui_ele.append($('<br>'))
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

                // show the next two radio buttons only in the /tableedit interface!
				.append('  ')
				.append($('<label>').css({display: 'none'}).addClass('label4disp_tableedit')
					.append($('<input>').attr({type: 'radio', name: 'display_type', title: strings._disp_tableedit}).val('editor').addClass('extsearch'))
					.append($('<input>').attr({
						'type': 'image',
						'src': SITE_URL + '/app/icons/0.gif',
						'alt': strings._disp_tableedit,
						'title': strings._disp_tableedit
					}).addClass('disp_tableedit'))
				)
				.append('  ')
				.append($('<label>').css({display: 'none'}).addClass('label4disp_csv')
					.append($('<input>').attr({type: 'radio', name: 'display_type', title: strings._disp_csv}).val('csv').addClass('extsearch'))
					.append($('<input>').attr({
						'type': 'image',
						'src': SITE_URL + '/app/icons/0.gif',
						'alt': strings._disp_csv,
						'title': strings._disp_csv
					}).addClass('disp_csv'))
				);
			}
			else {

			}
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
				if (localdata.settings.display_type == 'table') {
					gui_ele.find('input[name="display_type"][value="table"]').prop('checked', true);
				}
				else if (localdata.settings.display_type == 'matrix') {
					gui_ele.find('input[name="display_type"][value="matrix"]').prop('checked', true);
				}
				else {
					gui_ele.find('input[name="display_type"][value="editor"]').prop('checked', true);
				}
				gui_ele.append($('<hr>').addClass('extsearch'))
				.append($('<div>').addClass('center').append($('<input>').attr({'type': 'button', 'value': strings._search}).addClass('center').on('click', function(event) {
					var restype_id = selrestype;

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
								var dateObj = $(searchval_ele[index]).dateobj('value');
								// Knora expects a searchval string: Calendar:YYYY-MM-DD[:YYYY-MM-DD]
								var dateStr = dateObj.calendar + ":" + dateObj.dateval1;
								if (dateObj.dateval2 !== undefined) {
									// period
									dateStr += ":" + dateObj.dateval2;
								}
								searchval.push(dateStr);
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

					if ((restype_id == 0) && (property_id == 0) && (projfilt.val() == 0) && (ownerfilt.val() == 0)) {
						alert('no search params!');
						return;
					}

					$this.find('div[name=result].searchresult').empty(); // clean previous searches if present
					if ((gui_ele !== $this) && localdata.settings.kill_gui_ele) {
						gui_ele.remove();
					}

					var disptype = gui_ele.find('input[name="display_type"]:checked').val();
					if (!disptype) disptype = localdata.settings.display_type;
					var searchparams = {
						property_id: property_id,
						compop: compop,
						searchval: searchval,
						show_nrows: gui_ele.find('select[name="show_nrows"]').val(),
						start_at: localdata.settings.start_at,
						display_type: disptype
					};
					if (restype_id != 0) {
						searchparams.filter_by_restype = restype_id;
					}
					if (projfilt.val() != 0) {
						searchparams.filter_by_project = projfilt.val();
					}
					if (ownerfilt.val() != 0) {
						searchparams.filter_by_owner = ownerfilt.val();
					}
					if (typeof localdata.settings.onSearchAction === 'function') {
						localdata.settings.onSearchAction(searchparams);
					}
					else {
						$.extsearch.perform_search($this.find('div[name=result].searchresult'), searchparams);
					}
				})));

				if (localdata.settings.result_ele === undefined) {
					$this.append($('<hr>', {'class': 'extsearch'})).append($('<div>').attr({name: 'result'}).addClass('searchresult extsearch'));
				}
				else {
					localdata.settings.result_ele.append($('<div>').attr({name: 'result'}).addClass('searchresult extsearch'));
				}
				if (localdata.settings.limit_sel_to_restype === undefined) {
//					get_restypes($this);
				}
				else {
					get_properties($this, localdata.settings.limit_sel_to_restype);
				}
			});
		}
		/*===========================================================================*/
	};

	$.fn.extsearch = function(method) {
		// Method calling logic
		if ( methods[method] ) {
			return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
		} else if ( typeof method === 'object' || ! method ) {
			return methods.init.apply( this, arguments );
		} else {
			throw 'Method ' +  method + ' does not exist on jQuery.tooltip';
		}
		return undefined;
	};
})( jQuery );
