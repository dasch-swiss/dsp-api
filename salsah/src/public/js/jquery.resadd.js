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
(function($) {

	'use strict';

	var add_icon = new Image();
	add_icon.src = SITE_URL + '/app/icons/16x16/add.png';

	var cancel_icon = new Image();
	cancel_icon.src = SITE_URL + '/app/icons/16x16/delete.png';

	var methods = {
		/*
		 * the "init" method takes the following parameters:
		 *   rtinfo: resource_type info; if "undefined", a selection GUI will be presented
		 *   geometry_field: figures
		 *   viewer: viewer
		 *   props: Array of objects containing vocabulary, property name and property value. These properties will also be added to the resource,
		 *      event if they or not present in the GUI.
		 *   viewer: The viewer
		 *   on_submit_cb: Callback function executed, after the resource has been created (in order to cleanup things)
		 *   on_cancel_cb: Callback function executed, if the user hit's cancel (in order to clean up things)
		 *   on_error_cb: Callback funtion executed in case something went wrong. A parameter containing .CODE and :MSG is given to the function
		 */
		init: function(options) // $(element).regions('init', {settings: here,...});
			{
				return this.each(function() {
					var $this = $(this);
					var localdata = {};
					localdata.settings = {
						on_submit_cb: undefined,
						on_cancel_cb: undefined,
						on_error_cb: undefined,
						viewer: undefined,
						rtinfo: undefined,
						props: undefined,
						options: undefined, // {no_title: true | false}
						defaultvalues: undefined // Array ["voc:propname"] = value
					};
					if (typeof options === 'object') $.extend(localdata.settings, options);

					var attributes;
					var prop_status = {};

					var vocabulary_selected = 0;

					var get_restypes = function() {
						var vocabulary = $this.find('select[name=vocabulary].resadd').val();

						var param = {
							vocabulary: vocabulary,
						};

						$('body').ajaxError(function(e, xhr, settings, exception) {
							if (exception != 'abort') // we don't catch abort-errors here! jquery.searchbox.js uses the abort function to kill ajax calls
							{
								alert('AJAX-Error: ' + exception + '\ntarget=' + settings.url + '\ndata=' + settings.data);
							}
						});

						SALSAH.ApiGet(
							'resourcetypes', param,
							function(data) {
								var i;

								if (data.status == ApiErrors.OK) {
									var restypes_sel = $this.find('select[name=selrestype]').empty(); //.append($('<option>', {value: 0}).text('-'));

									// Remove knora-base:Region from the list of resource types that can be created here, because a region
									// can only be created as a dependency of an image representation.

									var region_index = -1;

									for (i in data.resourcetypes) {
										if (data.resourcetypes[i].id == RESOURCE_TYPE_REGION) {
											region_index = i;
										}
									}

									if (region_index > -1) {
									    data.resourcetypes.splice(region_index, 1);
									}

									for (i in data.resourcetypes) {
										restypes_sel.append($('<option>', {
											value: data.resourcetypes[i].id
										}).text(data.resourcetypes[i].label));
									}
									get_rtinfo_and_build_form(restypes_sel.val());
								} else {
									alert(data.errormsg)
								}
							}, 'json'
						);
					};

					var get_subclasses = function(preselected) {

						$('body').ajaxError(function(e, xhr, settings, exception) {
							if (exception != 'abort') // we don't catch abort-errors here! jquery.searchbox.js uses the abort function to kill ajax calls
							{
								alert('AJAX-Error: ' + exception + '\ntarget=' + settings.url + '\ndata=' + settings.data);
							}
						});

						SALSAH.ApiGet(
							'subclasses/' + encodeURIComponent(localdata.settings.rtinfo.name), function(data) {
								var i;

								var subclass_sel = $this.find('select[name=selsubclass]').empty();

								if (data.status == ApiErrors.OK) {
									for (i in data.subClasses) {
										if ((preselected !== undefined) && (preselected == data.subClasses[i].id)) {
											subclass_sel.append($('<option>', {
												value: data.subClasses[i].id, selected: "selected"
											}).text(data.subClasses[i].label));
										}
										else {
											subclass_sel.append($('<option>', {
												value: data.subClasses[i].id
											}).text(data.subClasses[i].label));
										}
									}
									//get_rtinfo_and_build_form(restypes_sel.val());
								} else {
									alert(data.errormsg)
								}
							}, 'json'
						);
					};

					var vocabulary_changed = function(id) {
						if (vocabulary_selected != id) {
							vocabulary_selected = id;
							get_restypes();
						}
					};

					var create_entry = function(propname, pinfo, create_tag) {
						var add_symbol;

						prop_status[propname].td.append($('<span>').addClass('entrySep').html('&nbsp'));
						create_tag(prop_status[propname].td, attributes, pinfo);
						prop_status[propname].count = 1;

						if (prop_status[propname].occurrence != '1') {
							prop_status[propname].td.append(add_symbol = $('<img>', {
								src: add_icon.src
							}).click({
								'name': propname
							}, function(event) {
								//  $(this).before(create_tag(prop_status[event.data.name].td, prop_status[event.data.name].attributes));

								create_tag(prop_status[event.data.name].td, prop_status[event.data.name].attributes, pinfo);
								prop_status[event.data.name].count++;
								if (((prop_status[event.data.name].count == 1) &&
										((prop_status[event.data.name].occurrence == '0-n') || (prop_status[event.data.name].occurrence == '0-1'))) ||
									((prop_status[event.data.name].count == 2) && (prop_status[event.data.name].occurrence == '1-n'))) {
									$(this).after($('<img>', {
										src: cancel_icon.src
									}).click({
										'name': event.data.name
									}, function(event) {
										var tmp_add_symbol;
										var ele = prop_status[event.data.name].td.find('[name="' + event.data.name + '"]:last');
										ele.find('.htmleditor').htmleditor('destroy');

										var win = ele.parents('.win');
										win.win('setFocusLock', false);

										ele.remove();


										prop_status[event.data.name].td.find('br:last').remove();
										prop_status[event.data.name].count--;
										if (prop_status[event.data.name].count == 0) {
											if (prop_status[event.data.name].occurrence == '0-1') {
												tmp_add_symbol = add_symbol.clone(true);
												$(this).remove();
												prop_status[propname].td.append(tmp_add_symbol);
												add_symbol = tmp_add_symbol;
												add_symbol.show();
											} else if (prop_status[event.data.name].occurrence == '0-n') {
												$(this).remove();
											}
										} else if ((prop_status[event.data.name].count == 1) && (prop_status[event.data.name].occurrence == '1-n')) {
											$(this).remove();
										}
									}));
								}
								if ((prop_status[propname].occurrence == '0-1') && (prop_status[propname].count == 1)) {
									add_symbol.hide();
								}
							}));

							if ((prop_status[propname].occurrence == '0-1') && (prop_status[propname].count == 1)) {
								add_symbol.hide();
							}
						}

						if (((prop_status[propname].count == 1) &&
								((prop_status[propname].occurrence == '0-n') || (prop_status[propname].occurrence == '0-1'))) ||
							((prop_status[propname].count == 2) && (prop_status[propname].occurrence == '1-n'))) {
							add_symbol.after($('<img>', {
								src: cancel_icon.src
							}).click({
								'name': propname
							}, function(event) {
								var ele = prop_status[event.data.name].td.find('[name="' + event.data.name + '"]:last');
								ele.find('.htmleditor').htmleditor('destroy');

								var win = ele.parents('.win');
								win.win('setFocusLock', false);

								ele.remove();
								prop_status[event.data.name].td.find('br:last').remove();
								prop_status[event.data.name].count--;
								if (prop_status[event.data.name].count == 0) {
									if (prop_status[event.data.name].occurrence == '0-1') {
										var tmp_add_symbol = add_symbol.clone(true);
										$(this).remove();
										prop_status[propname].td.append(tmp_add_symbol);
										add_symbol = tmp_add_symbol;
										add_symbol.show();
									} else if (prop_status[event.data.name].occurrence == '0-n') {
										$(this).remove();
									}
								} else if ((prop_status[event.data.name].count == 1) && (prop_status[event.data.name].occurrence == '1-n')) {
									$(this).remove();
								}
							}));
						}

					};
					//=====================================================================


					var create_form = function(rtinfo, options) {
						var form = localdata.form = $('<form>', {
							'class': 'resadd'
						});
						var table = $('<table>', {
							'class': 'resadd',
							width: '100%'
						});
						var tline;
						var a1, a2, td;
						var propname;
						var propvals = {};
						var file = undefined;
						var propval_found;

						var win = formcontainer.parents('.win');
						//win.win('setFocusLock', false);

						formcontainer.find('.htmleditor').htmleditor('destroy');
						formcontainer.empty();

						if ((options !== undefined) && (options.no_title)) {
							formcontainer.append($('<p>', {
								'class': 'propedit'
							}).append(' '));
						} else {
							formcontainer.append($('<h2>', {
								'class': 'propedit'
							}).append(strings._add + ' ' + rtinfo.label));
							formcontainer.append($('<p>', {
								'class': 'propedit'
							}).append(rtinfo.description));
						}

						formcontainer.append(form);

						var labelprop = {
							name: "__LABEL__",
							gui_name: "text",
							label: 'Label',
							description: "** label **",
							vocabulary: "http://www.knora.org/ontology/knora-base",
							valuetype_id: "http://www.knora.org/ontology/knora-base#TextValue",
							occurrence: "1"
						}
						rtinfo.properties.unshift(labelprop);

						for (var pinfo in rtinfo.properties) {
							//
							// now we check if for certain properties a value is given as parameter to $().resadd(). If so, this value is
							// used and the property does not figure in the property list!
							//
							if (localdata.settings.props && (localdata.settings.props instanceof Array)) {
								propval_found = false;
								for (var ii in localdata.settings.props) {
									if (/*(localdata.settings.props[ii].vocabulary == rtinfo.properties[pinfo].vocabulary) &&*/
										(localdata.settings.props[ii].name == rtinfo.properties[pinfo].name)) {

										//propvals[/*localdata.settings.props[ii].vocabulary + ':' + */localdata.settings.props[ii].name] = {};

										// TODO: check for different value types here!
										propvals[/*localdata.settings.props[ii].vocabulary + ':' + */localdata.settings.props[ii].name] = [{link_value: localdata.settings.props[ii].value}];
										propval_found = true;
										break;
									}
								}
								if (propval_found) continue;
							}
							//propname = rtinfo.properties[pinfo].vocabulary + ':' + rtinfo.properties[pinfo].name;
							propname = rtinfo.properties[pinfo].name;
							tline = $('<tr>', {
								'class': 'propedit'
							});
							tline.append($('<td>', {
								'class': 'propedit'
							}).append(rtinfo.properties[pinfo].label));
							tline.append($('<td>', {
								'class': 'propedit'
							}).append(':'));
							tline.append(td = $('<td>', {
								'class': 'propedit'
							}));
							attributes = {
								'class': 'propedit',
								'name': propname
							};
							if (rtinfo.properties[pinfo].attributes) {
								a1 = rtinfo.properties[pinfo].attributes.split(';');
								if (a1.length > 1) {
									for (var j in a1) {
										a2 = a1[j].split('=');
										attributes[a2[0]] = a2[1];
									}
								}
							}
							prop_status[propname] = {};
							prop_status[propname].occurrence = rtinfo.properties[pinfo].occurrence;
							prop_status[propname].count = 0;
							prop_status[propname].td = td;

							switch (rtinfo.properties[pinfo].gui_name) {
								case 'text':
									{
										attributes.type = 'text';
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var tmpele = $('<input>', attr).css({
												width: '85%'
											}).dragndrop('makeDropable', function(event, dropdata) {
												front = tmpele.val().substring(0, tmpele.attr('selectionStart'));
												back = tmpele.val().substring(tmpele.attr('selectionEnd'));
												tmpele.val(front + '<+LINKTO RESID=' + dropdata.resid + '+>' + back);
											}).insertBefore(ele.find('.entrySep'));
											if ((localdata.settings.defaultvalues !== undefined) && (localdata.settings.defaultvalues[propname])) {
												tmpele.val(localdata.settings.defaultvalues[propname]);
											}
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'textarea':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var tmpele = $('<textarea>', attr).css({
												width: '85%'
											}).dragndrop('makeDropable', function(event, dropdata) {
												front = tmpele.val().substring(0, tmpele.attr('selectionStart'));
												back = tmpele.val().substring(tmpele.attr('selectionEnd'));
												tmpele.val(front + '<+LINKTO RESID=' + dropdata.resid + '+>' + back);
											}).insertBefore(ele.find('.entrySep'));
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'richtext':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {

											//console.log('resadd');
											//console.log(attr);

											var richtextbox = $('<div>', {
												'class': attr.class,
												'name': attr.name
											}).insertBefore(ele.find('.entrySep'));

											var rt_txt = $('<div>').addClass('htmleditor'); // this will not be necessary with new editor version


											richtextbox.append(rt_txt);

											var win = formcontainer.parents('.win');
											win.win('setFocusLock', true);

											//win.win('setFocusLock', true);
											rt_txt.htmleditor('edit');

											/*
											if ((localdata.settings.defaultvalues !== undefined) && (localdata.settings.defaultvalues[propname])) {
												timebox.timeobj('setStart', localdata.settings.defaultvalues[propname]);
											}
											*/

											return richtextbox;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'pulldown':
									{
										var selection_id;
										var attrs = rtinfo.properties[pinfo].attributes.split(';');
										$.each(attrs, function() {
											var attr = this.split('=');
											if (attr[0] == 'selection' || attr[0] == 'hlist') {
												//selection_id = attr[1];
												selection_id = attr[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL
											}
										});
										create_entry(propname, pinfo, (function(sel_id) {
											return function(ele, attr, pinfo) {
												var selbox = $('<span>', attr).insertBefore(ele.find('.entrySep'));

												selbox.selection('edit', {
													selection_id: sel_id
												});
											}
										}(selection_id)));
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'radio':
									{
										var selection_id;
										var attrs = rtinfo.properties[pinfo].attributes.split(';');
										$.each(attrs, function() {
											var attr = this.split('=');
											if (attr[0] == 'selection' || attr[0] == 'hlist') {
												//selection_id = attr[1];
												selection_id = attr[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL
											}
										});
										create_entry(propname, pinfo, (function(sel_id) {
                                                return function (ele, attr, pinfo) {
                                                    var radiobox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
                                                    radiobox.selradio('edit', {
                                                        selection_id: sel_id
                                                        //selection_id: selection_id
                                                    });
                                                }
                                            }(selection_id)));
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'checkbox':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var checkbox = $('<input>', {
											    type: "checkbox"
											});

											checkbox.attr(attr);

											checkbox.insertBefore(ele.find('.entrySep'));
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'spinbox':
									{
										create_entry(propname, pinfo, function(ele, attr, pfino) {
											var spinbox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
											spinbox.spinbox('edit');
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'searchbox':
									{
										attributes.type = 'text';
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var tmpele = $('<input>', attr).addClass('__searchbox').insertBefore(ele.find('.entrySep'));
											var placeholderText = 'start typing to search (min 3 letters)...';
											if (SALSAH.userprofile && SALSAH.userprofile.userData && SALSAH.userprofile.userData.lang) {
												switch(SALSAH.userprofile.userData.lang) {
													case 'fr': placeholderText = "entrez les 3 premières lettres pour chercher...";
													// case german
												}
											}
											tmpele.attr('placeholder', placeholderText);
											tmpele.attr('autocomplete', 'off');

											// see: https://bugs.jquery.com/ticket/12429
											// $('<input>', attr) doesn't set "size" in attr={size:23}
											if (attr["size"]) {
												tmpele.attr("size", attr["size"]);
											}

											var restype_id = -1;
											var numprops = 1;
											var attrs = rtinfo.properties[pinfo].attributes.split(';');


											$.each(attrs, function() {

												var curAttr = this.split('=');
												
												if (curAttr[0] == 'restypeid') {
													restype_id = curAttr[1];
												}
												else if (curAttr[0] == 'numprops') {
													numprops = curAttr[1];
												}
											});
											/*$.each(attr, function(name, val) {
												if (name == 'restypeid') {
													restype_id = val;
												} else if (name == 'numprops') {
													numprops = val;
												}
											});*/


											tmpele.searchbox({
												restype_id: restype_id,
												numprops: numprops,
												offsetParent: tmpele.offsetParent(),
												clickCB: function(res_id) {
													SALSAH.ApiGet('resources', res_id, {
														reqtype: 'info'
													}, function(data) {
														if (data.status == ApiErrors.OK) {
															tmpele.val(data.resource_info.firstproperty + ' (' + data.resource_info.restype_label + ')').data('res_id', res_id);
															// if the choice is selected, change the input value and tag it as valid
															tmpele.attr("prevVal", "");
															tmpele.attr("isValid", "true");
														} else {
															alert(data.errormsg);
														}
													});
													/* NEW API
													$.__post(SITE_URL + '/ajax/get_resource_info.php', {res_id: res_id}, function(data){
														tmpele.val(data.firstproperty + ' (' + data.restype_label + ')').data('res_id', res_id);
													}, 'json');
													*/
												},
											});
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'date':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var datebox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
											datebox.dateobj('edit');
											return datebox;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'time':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var timebox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
											timebox.timeobj('edit');
											if ((localdata.settings.defaultvalues !== undefined) && (localdata.settings.defaultvalues[propname])) {
												timebox.timeobj('setStart', localdata.settings.defaultvalues[propname]);
											}
											return timebox;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'interval':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var timebox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
											timebox.timeobj('edit', {
												show_duration: true
											});
											if ((localdata.settings.defaultvalues !== undefined) && (localdata.settings.defaultvalues[propname])) {
												timebox.timeobj('setStart', localdata.settings.defaultvalues[propname]);
												timebox.timeobj('setEnd', localdata.settings.defaultvalues[propname]);
											}
											return timebox;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'geometry':
									{
										if (localdata.settings.geometry_field) {
											td.append(localdata.settings.geometry_field);
										} else {

										}
										break;
									}
								case 'fileupload':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var tmpele = $('<span>', attributes).insertBefore(ele.find('.entrySep'));
											tmpele.location('edit');
											return tmpele;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'colorpicker':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var colbox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
											if (rtinfo.name == 'http://www.knora.org/ontology/knora-base#Region') {
												colbox.colorpicker('edit', {
													color_changed_cb: function(color) {
														if (localdata.settings.viewer.topCanvas !== undefined) {
															localdata.settings.viewer.topCanvas()
																.regions('setObjectAttribute', 'lineColor', color, 'status', 'active')
																.regions('setDefaultLineColor', color)
																.regions('redrawObjects', true);
														}
													},
												});
												if (!(localdata.settings.viewer == undefined || localdata.settings.viewer.topCanvas == undefined)) {
													localdata.settings.viewer.topCanvas().regions('setDefaultLineColor', colbox.colorpicker('value')); // init
												}
											} else {
												colbox.colorpicker('edit');
											}
											return colbox;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}
								case 'hlist':
									{
										var hlist_id;
										var attrs = rtinfo.properties[pinfo].attributes.split(';');
										$.each(attrs, function() {
											var attr = this.split('=');
											if (attr[0] == 'hlist') {
												//hlist_id = attr[1];
												hlist_id = attr[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL
											}
										});
										create_entry(propname, pinfo, (function(list_id) {
											return function(ele, attr, pinfo) {
                                                var hlistbox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
                                                hlistbox.hlist('edit', {
                                                    hlist_id: list_id
                                                });
                                                return hlistbox;
                                            }
                                        }(hlist_id)));
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}

								case 'geoname':
									{
										create_entry(propname, pinfo, function(ele, attr, pinfo) {
											var geonamebox = $('<span>', attr).insertBefore(ele.find('.entrySep'));
											geonamebox.geonames('edit', {
												new_entry_allowed: true
											});
											return geonamebox;
										});
										prop_status[propname].attributes = attributes; // save attributes for later use
										break;
									}

								default:
									{
										attributes.type = 'text';
										td.append('DEFAULT (' + rtinfo.properties[pinfo].gui_name + '): ').append($('<input>', attributes));
									}
							}
							table.append(tline);
						}
						form.append(table);

						form.append($('<input>', {
							'type': 'button',
							'value': strings._save
						}).click(function(event) {
							var propname;
							var ele;
							var vv;

							for (var pinfo in rtinfo.properties) {
								//propname = rtinfo.properties[pinfo].vocabulary + ':' + rtinfo.properties[pinfo].name;
								propname = rtinfo.properties[pinfo].name;
								//if (propname == '__LABEL__') continue;
								if (!propvals[propname]) propvals[propname] = {};
								//console.log(rtinfo.properties[pinfo].gui_name);
								switch (rtinfo.properties[pinfo].gui_name) {
									case 'text':
									case 'textarea':
										{
											ele = form.find('[name="' + propname + '"]');

											if (ele.length == 1) {

												if (ele.val().trim() == "") {
													// empty prop
													propvals[propname] = undefined;
													break;
												}

												if (rtinfo.properties[pinfo].valuetype_id == VALTYPE_FLOAT) {
													// it is a float
													propvals[propname] = [{decimal_value: parseFloat(ele.val())}];
												} else if (rtinfo.properties[pinfo].valuetype_id == VALTYPE_URI) {
													propvals[propname] = [{uri_value: ele.val()}];
												} else {
													// it is a text
													var richtext_value = {};
													richtext_value.utf8str = ele.val();
													propvals[propname] = [{richtext_value: richtext_value}];
												}
											} else if (ele.length > 1) {
												propvals[propname] = []; // initialize as array
												ele.each(function() {

													if ($(this).val().trim() == "") {
														// continue
														return true;
													}

													if (rtinfo.properties[pinfo].valuetype_id == VALTYPE_FLOAT) {
														// it is a float
														vv = {
															decimal_value: parseFloat($(this).val())
														};
													} else if (rtinfo.properties[pinfo].valuetype_id == VALTYPE_URI) {
														vv = {
															uri_value: $(this).val()
														};
													} else {
														// it is a text
														var richtext_value = {};
														richtext_value.utf8str = $(this).val();
														vv = {
															richtext_value: richtext_value
														};
													}
													propvals[propname].push(vv);

												});
												// empty prop
												if (propvals[propname].length == 0) propvals[propname] = undefined;

											}
											break;
										}
									/*case 'textarea':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname].value = ele.val();
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														value: $(this).val()
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}*/
									case 'pulldown':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{hlist_value: ele.selection('value')}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														hlist_value: $(this).selection('value')
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									case 'radio':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{hlist_value: ele.selradio('value')}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
									 					hlist_value: $(this).selradio('value')
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									case 'checkbox': {
										ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{boolean_value: ele.is(":checked")}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														boolean_value: $(this).is(":checked")
													};
													propvals[propname].push(vv);
												});
											}
											break;

									}	
									case 'spinbox':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {

												if (ele.spinbox('value').trim() == "") {
													// empty prop
													propvals[propname] = undefined;
													break;
												}

												propvals[propname] = [{int_value: parseInt(ele.spinbox('value'))}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {

													if ($(this).spinbox('value').trim() == "") {
														// continue
														return true;
													}

													vv = {
														int_value: parseInt($(this).spinbox('value'))
													};
													propvals[propname].push(vv);
												});

												// empty prop
												if (propvals[propname].length == 0) propvals[propname] = undefined;
											}
											break;
										}
									case 'searchbox':
										{
											ele = form.find('[name="' + propname + '"]');
											if ((rtinfo.name != 'http://www.knora.org/ontology/knora-base#Region') || (propname != 'http://www.knora.org/ontology/knora-base#isRegionOf')) {
												if (ele.length == 1) {

													if (ele.data('res_id') == undefined) {
														// no resid selected

														// empty prop
														propvals[propname] = undefined;
														break;

													}

													propvals[propname] = [{link_value: ele.data('res_id')}];
												} else if (ele.length > 1) {
													propvals[propname] = [];
													ele.each(function() {

														if ($(this).data('res_id') == undefined) {
															// continue
															return true;
														}

														vv = {
															link_value: $(this).data('res_id')
														};
														propvals[propname].push(vv);
													});

													// empty prop
													if (propvals[propname].length == 0) propvals[propname] = undefined;
												}
											}
											break;
										}
									case 'date':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{date_value: SALSAH_API_LEGACY.make_date_string(ele.dateobj('value'))}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														date_value: SALSAH_API_LEGACY.make_date_string($(this).dateobj('value'))
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									case 'time': // TODO: to be adapted
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{time_value:  ele.timeobj('value')}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														time_value: $(this).timeobj('value')
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									case 'interval': // TODO: to be adapted
										{
											ele = form.find('[name="' + propname + '"]');

                                            if (ele.length == 1) {
												propvals[propname] = [{interval_value: ele.timeobj('value')}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														interval_value: $(this).timeobj('value')
													};
													propvals[propname].push(vv);
												});
											}
                                            //console.log(propvals[propname]);

											// we're looking for the two input fields
											//ele = intval_ele.find('input.interval');
                                            /*console.log($(this));
                                            console.log($(this).timeobj('value', ele));
                                            ele.each(function() {
									           propvals[propname] = [];
                                                vv = {
                                                    value: $(this).timeobj('value', ele)
                                                };
                                                propvals[propname].push(vv);*/
//                                            });

                                            /*
                                            var time_1 = ele.first().val();
											var time_2 = ele.last().val();

											vv = {
												value: $this.timeobj('value', time_1, time_2)
											};
											propvals[propname].push(vv);

                                            */
											break;
										}
									case 'geometry':
										{
											if (rtinfo.name == 'http://www.knora.org/ontology/knora-base#Region') {
												var col = form.find('[name="http://www.knora.org/ontology/knora-base#hasColor"]').colorpicker('value');
												propvals[propname] = [];
												var geos = localdata.settings.viewer.topCanvas().regions('returnObjects', 'active');
												if (geos.length < 1) {
													alert(strings._err_empty_geometry);
													return false;
												}
												for (var idx in geos) {
													geos[idx].lineColor = col;
													vv = {
														geom_value: JSON.stringify(geos[idx])
													};
													propvals[propname].push(vv);
												}
											}
											break;
										}
									case 'fileupload':
										{
											/*ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname].value = ele.location('value');
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														value: $(this).location('value')
													};
													propvals[propname].push(vv);
												});
											}*/

											ele = form.find('[name="' + propname + '"]');
											var sipi_response = ele.location('value');

											file = {
												originalFilename: sipi_response["original_filename"],
												originalMimeType: sipi_response["original_mimetype"],
												filename: sipi_response["filename"]
											};

											
											break;
										}
									case 'colorpicker':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{color_value: ele.colorpicker('value')}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														color_value: $(this).colorpicker('value')
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									case 'richtext':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												var rt_txt = ele.find('.htmleditor');

												var props = rt_txt.htmleditor('value');

												// htmleditor returns false if there is no content
												if (props !== false) propvals[propname] = [{richtext_value: props}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													var rt_txt = $(this).find('.htmleditor');
													var props = rt_txt.htmleditor('value');

                                                    // htmleditor returns false if there is no content
                                                    if (props !== false) {
                                                        vv = {
                                                            richtext_value: props
                                                        };
                                                        propvals[propname].push(vv);
                                                    }
												});
											}
											break;
										}
									case 'hlist':
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname] = [{hlist_value: ele.hlist('value')}];
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														hlist_value: $(this).hlist('value')
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									case 'geoname': // TODO: to be adapted
										{
											ele = form.find('[name="' + propname + '"]');
											if (ele.length == 1) {
												propvals[propname].value = ele.geonames('value');
											} else if (ele.length > 1) {
												propvals[propname] = [];
												ele.each(function() {
													vv = {
														value: $(this).geonames('value')
													};
													propvals[propname].push(vv);
												});
											}
											break;
										}
									default:
										{
											//alert('DEFAULT FOR ' + propname); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
										}
								}
							}

							// Ignore knora-base:hasStandoffLinkTo, because it is not user-modifiable.
							propvals["http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"] = undefined;

							// Remove properties that have empty values because the user removed them from the form.
							for (var prop in propvals) {
								if (jQuery.isEmptyObject(propvals[prop])) {
									delete propvals[prop];
								}
							}

							// TODO: handle GUI  element problem
							//propvals["http://www.knora.org/ontology/knora-base#hasComment"] = undefined;
							var tmplabel = propvals['__LABEL__'];

							if (tmplabel === undefined || tmplabel.length == 0) {
								alert(strings._label_required);
								return;
							}

							var tmplabelFirstElem = tmplabel[0];
							var labelStr = tmplabelFirstElem.richtext_value.utf8str;
							propvals['__LABEL__'] = undefined;

							// fake a click to show up the model dialog
							$('#hiddenaddrespending').click();

							SALSAH.ApiPost('resources', {
								restype_id: rtinfo.name,
								properties: propvals,
								project_id: SALSAH.userprofile.active_project,
								file: file,
								label: labelStr

							}, function(data) {

								// release the modal when we are called back from the async method
								$('#hiddenaddrespending').simpledialog('processpendingbox', 'close');

								if (data.status == ApiErrors.OK) {
									if (typeof localdata.settings.on_submit_cb === "function") {
										localdata.settings.on_submit_cb(data);
									}
								} else {
									if (typeof localdata.settings.on_error_cb === "function") {
										localdata.settings.on_error_cb(data);
									} else {
										alert('XXXX' + data.errormsg);
									}
								}
							}).fail(function() {
								// release the modal when the async method failed
								$('#hiddenaddrespending').simpledialog('processpendingbox', 'close');
							});
							return false;
						}));

						form.append($('<input>', {
							'type': 'button',
							'value': strings._cancel
						}).click(function(event) {
							if (typeof localdata.settings.on_cancel_cb === "function") {
								localdata.settings.on_cancel_cb();
							}
						}));

						$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
					};
					//=====================================================================

					var get_rtinfo_and_build_form = function(restype) {
							SALSAH.ApiGet('resourcetypes', restype, function(data) {
								if (data.status == ApiErrors.OK) {
									prop_status = {};
									attributes = {};
									create_form(data.restype_info, localdata.settings.options);
								} else {
									alert(data.errormsg)
								}
							});
						}
						//=====================================================================

					var formcontainer = $('<div>', {
						'class': 'resadd'
					});
					if (localdata.settings.rtinfo === undefined) { // we don't know which resource type we want to add – present the selectors...
						var vocsel;
						var vocabulary_default;
						//
						// preselect the vocabulary of the project
						//
						if (SALSAH.userprofile && SALSAH.userprofile.active_project) {
							for (var p in SALSAH.userprofile.projects_info) {
								if (SALSAH.userprofile.projects_info[p].id == SALSAH.userprofile.active_project) {
									vocabulary_default = SALSAH.userprofile.projects_info[p].ontologies[0];
									break;
								}
							}
						}
						//
						// get vocabularies
						//
						$this.append(strings._vocabulary_label + ' : ');
						$this.append(vocsel = $('<select>', {
							'class': 'resadd',
							name: 'vocabulary'
						}).change(function(ev) {
							vocabulary_changed($(this).val());
						}));
						vocsel.append($('<option>', {
							value: 0
						}).append(strings._all));
						SALSAH.ApiGet('vocabularies', function(data) {
							if (data.status == ApiErrors.OK) {
								var tmpele;
								for (var i in data.vocabularies) {
									if (data.vocabularies[i].active) {
										vocsel.append(tmpele = $('<option>', {
											value: data.vocabularies[i].id
										}).append(data.vocabularies[i].longname + ' [' + data.vocabularies[i].id.substr(data.vocabularies[i].id.lastIndexOf('/') + 1) + ']'));
										if (data.vocabularies[i].id == vocabulary_default) {
											tmpele.prop({
												selected: 'selected'
											});
											vocabulary_selected = data.vocabularies[i].id;
										}
									}
								}
								$this.append($('<br>'));
								$this.append(strings._restype_label + ' : ');
								$this.append($('<select>', {
									'class': 'extsearch',
									name: 'selrestype'
								}).change(function(event) {
									get_rtinfo_and_build_form($(event.target).val());
								}));
								get_restypes();

								$this.append($('<hr>').css({
									'height': '2px',
									'background-color': '#888'
								}));
								$this.append(formcontainer);
							} else {
								alert(data.errormsg);
							}
						}, 'json');

					} else {
						var selele;
						$this.append(strings._resource_type + ' : ');
						var selele = $('<select>', {
							'class': 'extsearch',
							name: 'selsubclass'
						}).change(function(event) {
							get_rtinfo_and_build_form($(event.target).val());
						});
						$this.append(selele);
						get_subclasses(localdata.settings.rtinfo.name);
						$this.append($('<hr>').css({
							'height': '2px',
							'background-color': '#888'
						}));
						$this.append(formcontainer);
						get_rtinfo_and_build_form(localdata.settings.rtinfo.name);
						//create_form(localdata.settings.rtinfo, localdata.settings.options);
					}
				});
			},

		setValue: function(props) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');

				var tmp = {};
				var plist = localdata.settings.rtinfo.properties;
				for (index in plist) {
					//pp = plist[index].vocabulary + ':' + plist[index].name;
					pp = plist[index].name;
					tmp[pp] = index;
				}

				for (name in props) {
					var rtinfo_index = tmp[name];
					switch (plist[rtinfo_index].gui_name) {
						case 'time':
							{
								ele = localdata.form.find('[name="' + name + '"]');
								if (ele.length == 1) {
									ele.timeobj('setStart', props[name]);
								} else if (ele.length > 1) {
									propvals[name].value = [];
									ele.each(function() {
										$(this).timeobj('setStart', props[name]);
									});
								}
								break;
							}
						case 'interval':
							{ // format of props[param]: 23.567 (just the start of the interval) or '10.4=23.66' (interval from to) or '=44.56' (only end of an interval)
								var startval;
								var endval;
								if (typeof props[name] == 'number') {
									startval = props[name]; // it's just a plain number
								} else {
									var vals = props[name].split('=');
									if (vals.length == 1) {
										startval = parseFloat(vals[0]);
									} else {
										if (vals[0].length == 0) {
											endval = parseFloat(vals[1]);
										} else {
											startval = parseFloat(vals[0]);
											endval = parseFloat(vals[1]);
										}
									}
								}
								ele = localdata.form.find('[name="' + name + '"]');
								if (ele.length == 1) {
									if (startval !== undefined) ele.timeobj('setStart', startval);
									if (endval !== undefined) ele.timeobj('setEnd', endval);
								} else if (ele.length > 1) {
									propvals[name].value = [];
									ele.each(function() {
										if (startval !== undefined) $(this).timeobj('setStart', startval);
										if (endval !== undefined) $(this).timeobj('setEnd', endval);
									});
								}
								break;
							}
					}
				}
			});
		},

		//
		// this function is used to call a internal function of a jQuery plugin that implements a
		// specific handling of a SALSAH value, e.g. a date or time value (which itself is a complex thing)
		//
		callValobjFunc: function(propname, objfunc, parameter) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');

				var plist = localdata.settings.rtinfo.properties;
				var tmp = {};
				for (index in plist) {
					//pp = plist[index].vocabulary + ':' + plist[index].name;
					pp = plist[index].name;
					tmp[pp] = index;
				}

				var rtinfo_index = tmp[propname];
				switch (plist[rtinfo_index].gui_name) {
					case 'interval':
						{
							ele = localdata.form.find('[name="' + name + '"]');
							ele.timeobj(objfunc, parameter);
							break;
						}
				}


			});
		},

		anotherMethod: function() {
				return this.each(function() {
					var $this = $(this);
					var localdata = $this.data('localdata');
				});
			}
			/*========================================================================*/

	}

	$.fn.resadd = function(method) {
		// Method calling logic
		if (methods[method]) {
			return methods[method].apply(this, Array.prototype.slice.call(arguments, 1));
		} else if (typeof method === 'object' || !method) {
			return methods.init.apply(this, arguments);
		} else {
			throw 'Method ' + method + ' does not exist on jQuery.tooltip';
		}
	};
})(jQuery);
