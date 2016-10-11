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
(function ($) {

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

	$.fn.propedit = function(resdata, propinfo, project_id, optpar) {
		var $that = this;

		var res_id = resdata.res_id;
		var res_rights = resdata.rights;

		var active = undefined;

		var options = {
			viewer: undefined,
			canvas: undefined,
			change_rights: true,
			readonly: undefined,
			simple_view: false,
			simple_view_action: function() {}
		};

		$.extend(options, optpar);
		/**
		* private metthod used to reset just one value of a given property
		*
		* @param ele
		* @param prop
		* @value_index
		*/
		var reset_value = function(value_container, prop, value_index, readonly) {
			//
			// special treatment of __location__
			//
			if (prop == '__location__') { // VERY SPECIAL !!
    			var locations = propinfo[prop]['locations'];

				if (locations !== undefined) {
                    // Provide a link to the full-size image.
                    var fullSize = locations[locations.length - 1];
 				    value_container.append($('<a>').attr({href: fullSize['path'], target: "_blank"}).text(' ' + fullSize['origname'] + ' '));
				}

				if (res_rights >= RESOURCE_ACCESS_MODIFY) {
					$('<img>', {src: edit_icon.src, 'class': 'propedit'}).click(function(event) {
						edit_value(value_container, prop, value_index);
					}).css({cursor: 'pointer'}).appendTo(value_container);
				}

				return; // Ok and done...
			}

			//
			// first let's see if there is a value to be shown....If not, return immediately
			//
			if (!propinfo[prop].values) return;

			//
			// here we test if we are wallowed to view the value. If not, we just return!
			//
			if (propinfo[prop].value_rights[value_index] >= VALUE_ACCESS_VIEW) {

				SALSAH.showval(value_container, propinfo[prop], value_index, options);
				
				if (readonly !== undefined) return; // no add/edit buttons etc.

				//
				// if we have the right to edit, we add the edit button
				//
				if (propinfo[prop].value_rights[value_index] >= VALUE_ACCESS_MODIFY) {
					if ((parseInt(propinfo[prop].valuetype_id) != VALTYPE_GEOMETRY) || (options.canvas !== undefined)) {
						if (parseInt(propinfo[prop].valuetype_id) == VALTYPE_RESPTR) {
							$('<img>', {src: drop_icon.src, 'class': 'propedit', title: strings._drop_target}).click(function(event) {
								edit_value(value_container, prop, value_index);
							}).appendTo(value_container);
						}
						else {
							$('<img>', {src: edit_icon.src, 'class': 'propedit'}).click(function(event) {
								edit_value(value_container, prop, value_index);
							}).css({cursor: 'pointer'}).appendTo(value_container);
						}
					}
					
					$('<img>').attr({src: comment_icon.src})
					.valcomment({value_id: propinfo[prop].value_ids[value_index], comment: (propinfo[prop].comments === undefined) ? '' : propinfo[prop].comments[value_index]})
					.appendTo(value_container);
				}
			} // if (propinfo[prop].value_rights[value_index] >= VALUE_ACCESS_VIEW)


			//
			// if we have the right to delete this value, we add the delete button
			//
			if ((typeof propinfo[prop].occurrence != 'undefined') &&
			((propinfo[prop].occurrence == '0-n') || (propinfo[prop].occurrence == '0-1') || ((propinfo[prop].occurrence == '1-n') && (propinfo[prop].values.length > 1)))) {
				//
				// add the delete button to a value, if we have the right to do so
				//
				if (propinfo[prop].value_rights[value_index] >= VALUE_ACCESS_DELETE) {
					if ((parseInt(propinfo[prop].valuetype_id) != VALTYPE_GEOMETRY) || (options.canvas !== undefined)) {
						$('<img>', {src: delete_icon.src, 'class': 'propedit'}).click(function(event){
							if (confirm(strings._propdel)) {
								var value_id = propinfo[prop].value_ids[value_index];
								var data = {
									action: 'delete',
									value_id: value_id
								};
								SALSAH.ApiDelete('values/' + encodeURIComponent(value_id), function(data) {
									if (data.status == ApiErrors.OK) {
										propinfo[prop].values.splice(value_index, 1);
										propinfo[prop].value_ids.splice(value_index, 1);
										propinfo[prop].value_rights.splice(value_index, 1);
										propinfo[prop].value_iconsrcs.splice(value_index, 1);
										propinfo[prop].value_firstprops.splice(value_index, 1);
										propinfo[prop].value_restype.splice(value_index, 1);
										var prop_container = value_container.parent();
										prop_container.empty();
										if ((typeof propinfo[prop].values != 'undefined') && (propinfo[prop].values.length > 0)) {
											for (var i = 0; i < propinfo[prop].values.length; i++) {
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
									}
									else {
										alert(data.errormsg);
									}
								})
							}
						}).css({cursor: 'pointer'}).appendTo(value_container);
					}
				}
			}

			//
			// if we have the right to change th rights, we add the rights-edit button
			//
			if (res_rights >= RESOURCE_ACCESS_RIGHTS) {
				if ((parseInt(propinfo[prop].valuetype_id) != VALTYPE_GEOMETRY) || (options.canvas !== undefined)) {
					if (options.change_rights) {
						$('<img>', {src: rights_icon.src, 'class': 'propedit'}).click(function(event) {
							alert('CHANGE RIGHTS HERE...!');
						}).css({cursor: 'pointer'}).appendTo(value_container);
					}
				}
			}
		};

		var make_add_button = function (prop_container, prop) {
			if ((parseInt(propinfo[prop].valuetype_id) == VALTYPE_GEOMETRY) && (options.canvas === undefined) || prop == PROP_HAS_STANDOFF_LINK_TO) {
				return; // no add button
			}
			var add_button = false;
			if (typeof propinfo[prop].occurrence != 'undefined') {
				if ((propinfo[prop].occurrence == '0-n') || (propinfo[prop].occurrence == '1-n')) {
					add_button = true;
				}
				else if ((propinfo[prop].occurrence == '0-1') || (propinfo[prop].occurrence == '1')) {
					if (typeof propinfo[prop].values == 'undefined') {
						add_button = true;
					}
					else if (propinfo[prop].values.length == 0) {
						add_button = true;
					}
				}
			}
			if (add_button) {
				if (res_rights >= RESOURCE_ACCESS_EXTEND) {
					var value_container = $('<div>', {'class': 'propedit value_container'}).appendTo(prop_container);
					var add_button = $('<img>', {src: add_icon.src, 'class': 'propedit'}).click(function(event) {
						edit_value(value_container, prop);
					}).css({cursor: 'pointer'}).appendTo(value_container);
					if (propinfo[prop].valuetype_id == VALTYPE_RESPTR) {
						add_button.dragndrop('makeDropable', function(event, dropdata) {
							value_container.prepend($('<span>', {'class': 'propedit addresptr'}).data('drop_resid', dropdata.resid));
							add_button.click(); // let's click the add button.... // NO EXTERNAL RESOURCES YET!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
						});
					}
				}
			}
		};

		/**
		 * this function resets all values of a field and adds the "+"-button to add a new value, if the permisions allow
		 *
		 * @param prop The property information
		 * @param prop_index ...
		 * @param readonly If defined, then the property is readony (no buttons)
		 */
		var reset_field = function(prop, prop_index, readonly) {
			var prop_container = $($that.get(prop_index));
			/*
			var prop_label = prop_container.prev();
			prop_label.click(function(event) {
				if (prop_container.css('display') == 'none') {
					$(this).css('cursor', 'n-resize');
					$(this).find('span').css('display', 'none');
				}
				else {
					$(this).css('cursor', 's-resize');
					$(this).find('span').css('display', 'inline');
				}
				prop_container.slideToggle();
			}).css({'cursor': 'n-resize'});
			prop_label.append($('<span>').css('display','none').append('.....'));
			*/
			prop_container.empty();
			if (readonly !== undefined) alert(prop);

			if ((propinfo[prop].values !== undefined) && (propinfo[prop].values.length > 0)) {
				for (var i = 0; i < propinfo[prop].values.length; i++) {
					var tmp_value_container = $('<div>').addClass('propedit value_container').data('valid', propinfo[prop].value_ids[i]).appendTo(prop_container);
					reset_value(tmp_value_container, prop, i, readonly);
				} // for()
			}
			if (readonly === undefined)	make_add_button(prop_container, prop);
		};

		var edit_location = function(value_container, prop, value_index) {
			var is_new_value = false;

			if (active !== undefined) {
				if (!confirm(strings._canceditquest)) return;
				cancel_edit(value_container);
			}

			value_container.empty();

			var attributes = {'class': 'propedit'};
			var tmpele = $('<span>', attributes).appendTo(value_container);
			tmpele.location('edit');

			value_container.append($('<img>', {src: cancel_icon.src, title: strings._cancel, 'class': 'propedit'}).click(function(event) {
				cancel_edit(value_container);
			}));
			active = {value_container: value_container, prop: prop, value_index: value_index, is_new_value: is_new_value};
		};

		var edit_value = function(value_container, prop, value_index) {
			var is_new_value = false;

			var postdata = [];

			var init_value_structure = function() {
				if (!propinfo[active.prop].values) propinfo[active.prop].values = Array();
				if (!propinfo[active.prop].value_ids) propinfo[active.prop].value_ids = Array();
				if (!propinfo[active.prop].value_rights) propinfo[active.prop].value_rights = Array();
				if (!propinfo[active.prop].value_iconsrcs) propinfo[active.prop].value_iconsrcs = Array();
				if (!propinfo[active.prop].value_firstprops) propinfo[active.prop].value_firstprops = Array();
				if (!propinfo[active.prop].value_restype) propinfo[active.prop].value_restype = Array();
			};

			// deprecated, Knora only supports RICHTEXT
			postdata[VALTYPE_TEXT] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.value = value;
					data.res_id = res_id;
					data.prop = prop;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {
							if (!propinfo[active.prop].values) propinfo[active.prop].values = Array();
							if (!propinfo[active.prop].value_ids) propinfo[active.prop].value_ids = Array();
							if (!propinfo[active.prop].value_rights) propinfo[active.prop].value_rights = Array();
							if (!propinfo[active.prop].value_iconsrcs) propinfo[active.prop].value_iconsrcs = Array();
							if (!propinfo[active.prop].value_firstprops) propinfo[active.prop].value_firstprops = Array();
							if (!propinfo[active.prop].value_restype) propinfo[active.prop].value_restype = Array();
							if (propinfo[active.prop].valuetype_id == VALTYPE_GEOMETRY) {
								var tmpgeo = JSON.parse(data.value);
								tmpgeo.val_id = data.id;
								tmpgeo.res_id = res_id;
								if ((typeof options === 'object') && (typeof options.canvas !== 'undefined')) {
									options.canvas.regions('setObjectAttribute', 'val_id', data.id, value_container.find('span').data('figure_index'));
									options.canvas.regions('setObjectAttribute', 'res_id', res_id, value_container.find('span').data('figure_index'));
								}
								propinfo[active.prop].values[active.value_index] = JSON.stringify(tmpgeo);
							}
							else {
								propinfo[active.prop].values[active.value_index] = data.value;
							}
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
				else {
					data.value = value;
					SALSAH.ApiPut('values/' + propinfo[prop].value_ids[value_index], data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};

			postdata[VALTYPE_INTEGER] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.int_value = parseInt(value); // it is an integer
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {

							init_value_structure();

							propinfo[active.prop].values[active.value_index] = data.value;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				} else {
					data.int_value = parseInt(value); // it is an integer
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}



						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};

			postdata[VALTYPE_FLOAT] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.decimal_value = parseFloat(value); // it is an float
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {

							init_value_structure();

							propinfo[active.prop].values[active.value_index] = data.value;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				} else {
					data.decimal_value = parseFloat(value); // it is an float
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}


						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};

			postdata[VALTYPE_URI] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.uri_value = value;
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {

							init_value_structure();

							propinfo[active.prop].values[active.value_index] = data.value;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				} else {
					data.uri_value = value;
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}


						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};

			postdata[VALTYPE_RICHTEXT] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.richtext_value = value;
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {
							// data.value has the following members:
							//   data.value.utf8str
							//   data.value.textattr
							//   data.value.resource_reference
							//
							var tmpobj = {};
							tmpobj.utf8str = data.value.utf8str;
							tmpobj.textattr = data.value.textattr;
							tmpobj.resource_reference = data.value.resource_reference;

							init_value_structure();
							propinfo[active.prop].values[active.value_index] = tmpobj;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
				else {
					data.richtext_value = value;
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;


							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}

						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};


			postdata[VALTYPE_DATE] = function(value_container, prop, value_index, value, is_new_value) {
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
					data.date_value = SALSAH_API_LEGACY.make_date_string(value);
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
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

							/*
							* Knora now returns:
							* {

							 "dateval1": "2016-01-07",
							 "dateval2": "2016-01-07",
							 "calendar": "GREGORIAN"

							 }

							 SALSAH used to return:

							 {

							 "dateval1": "2266011",
							 "dateval2": "2266376",
							 "calendar": "JULIAN",
							 "dateprecision1": "YEAR",
							 "dateprecision2": "YEAR"

							 }

							*
							* */

							//tmpobj.dateval1 = datestr_to_jdc(data.value.calendar, data.value.dateval1);
							//tmpobj.dateval2 = datestr_to_jdc(data.value.calendar, data.value.dateval2);
							//tmpobj.calendar = SALSAH.calendarnames[data.value.calendar];

							tmpobj.dateval1 = data.value.dateval1;
							tmpobj.dateval2 = data.value.dateval2;
							tmpobj.calendar = data.value.calendar;

							//tmpobj.dateprecision1 = precisionnames[data.value.dateprecision1];
							//tmpobj.dateprecision2 = precisionnames[data.value.dateprecision2];

							init_value_structure();
							propinfo[active.prop].values[active.value_index] = tmpobj;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							//console.log(propinfo[active.prop].values[active.value_index]);

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);

							var prop_container = value_container.parent();
							make_add_button(prop_container, active.prop);

						}
						else {
							alert(data.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
				else {
					data.date_value = SALSAH_API_LEGACY.make_date_string(value);
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
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
							/*tmpobj.dateval1 = datestr_to_jdc(data.value.calendar, data.value.dateval1);
							tmpobj.dateval2 = datestr_to_jdc(data.value.calendar, data.value.dateval2);
							tmpobj.calendar = SALSAH.calendarnames[data.value.calendar];
							tmpobj.dateprecision1 = precisionnames[data.value.dateprecision1];
							tmpobj.dateprecision2 = precisionnames[data.value.dateprecision2];*/

							tmpobj.dateval1 = data.value.dateval1;
							tmpobj.dateval2 = data.value.dateval2;
							tmpobj.calendar = data.value.calendar;


							propinfo[active.prop].values[active.value_index] = tmpobj; // HIER IST DER FEHLER!!!!!!!!!
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);

						}
						else {
							alert(data.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};


			// TIMETIMETIMETIMETIME !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			postdata[VALTYPE_TIME] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.value = value;
					data.res_id = res_id;
					data.prop = prop;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {
							var tmpobj = {};
							tmpobj.timeval1 = data.value.timeval1;
							tmpobj.timeval2 = data.value.timeval2;
							
							if (!propinfo[active.prop].values) propinfo[active.prop].values = Array();
							if (!propinfo[active.prop].value_ids) propinfo[active.prop].value_ids = Array();
							if (!propinfo[active.prop].value_rights) propinfo[active.prop].value_rights = Array();
							if (!propinfo[prop].value_iconsrcs) propinfo[prop].value_iconsrcs = Array();
							if (!propinfo[prop].value_firstprops) propinfo[prop].value_firstprops = Array();
							if (!propinfo[prop].value_restype) propinfo[prop].value_restype = Array();
							propinfo[active.prop].values[active.value_index] = tmpobj;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);

							var prop_container = value_container.parent();
							make_add_button(prop_container, active.prop);
						}
						else {
							alert(status.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
				else {
					data.value = value;
					SALSAH.ApiPut('values/' + propinfo[prop].value_ids[value_index], data, function(data) {
						if (data.status == ApiErrors.OK) {
							var tmpobj = {};
							tmpobj.timeval1 = data.value.timeval1;
							tmpobj.timeval2 = data.value.timeval2;
							
							propinfo[active.prop].values[active.value_index] = tmpobj; // HIER IST DER FEHLER!!!!!!!!!

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};
			// TIMETIMETIMETIMETIME !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

			postdata[VALTYPE_INTERVAL] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.interval_value = value;
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {
							var tmpobj = {};
							tmpobj.timeval1 = data.value.timeval1;
							tmpobj.timeval2 = data.value.timeval2;

							init_value_structure();
							propinfo[active.prop].values[active.value_index] = tmpobj;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);

							var prop_container = value_container.parent();
							make_add_button(prop_container, active.prop);
						}
						else {
							alert(status.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
				else {
					data.interval_value = value;
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							var tmpobj = {};
							tmpobj.timeval1 = data.value.timeval1;
							tmpobj.timeval2 = data.value.timeval2;

							propinfo[active.prop].values[active.value_index] = tmpobj;

							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};
			
			
			postdata[VALTYPE_RESPTR] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				var tmp_active = {};
				$.extend(tmp_active, active);
				if (is_new_value) {
					data.link_value = value;
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {

							init_value_structure();
							propinfo[active.prop].values[active.value_index] = data.value;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							
							var tmp_active = {};
							$.extend(tmp_active, active);
							SALSAH.ApiGet('resources', data.value, {reqtype: 'info'}, function(data) {
								if (data.status == ApiErrors.OK) {
									propinfo[tmp_active.prop].value_iconsrcs[tmp_active.value_index] = data.resource_info.restype_iconsrc;
									propinfo[tmp_active.prop].value_firstprops[tmp_active.value_index] = data.resource_info.firstproperty;
									propinfo[tmp_active.prop].value_restype[tmp_active.value_index] = data.resource_info.restype_label;
									tmp_active.value_container.empty();
									
									reset_value(tmp_active.value_container, tmp_active.prop, tmp_active.value_index);

									var prop_container = tmp_active.value_container.parent();
									make_add_button(prop_container, tmp_active.prop);
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
						cancel_edit(value_container);
					});
				}
				else {
					data.link_value = value;
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;

							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							var tmp_active = {};
							$.extend(tmp_active, active);

							SALSAH.ApiGet('resources', data.value, {reqtype: 'info'}, function(data) {
								if (data.status == ApiErrors.OK) {
									var resinfo = data.resource_info;
									propinfo[tmp_active.prop].value_iconsrcs[tmp_active.value_index] = resinfo.restype_iconsrc;
									propinfo[tmp_active.prop].value_firstprops[tmp_active.value_index] = resinfo.firstproperty;
									propinfo[tmp_active.prop].value_restype[tmp_active.value_index] = resinfo.restype_label;
									tmp_active.value_container.empty();
									reset_value(tmp_active.value_container, tmp_active.prop, tmp_active.value_index);




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
						cancel_edit(value_container);
					});
				}
			};

			postdata[VALTYPE_GEOMETRY] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.geom_value = value;
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;

					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {
							init_value_structure();

							var tmpgeo = JSON.parse(data.value);
							tmpgeo.val_id = data.id;
							tmpgeo.res_id = res_id;
							if ((typeof options === 'object') && (typeof options.canvas !== 'undefined')) {
								options.canvas.regions('setObjectAttribute', 'val_id', data.id, value_container.find('span').data('figure_index'));
								options.canvas.regions('setObjectAttribute', 'res_id', res_id, value_container.find('span').data('figure_index'));
							}
							propinfo[active.prop].values[active.value_index] = JSON.stringify(tmpgeo);

							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
				else {
					data.geom_value = value;
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							// update the value id in the regions plugin
							options.canvas.regions('setObjectAttribute', 'val_id', data.id, value_container.find('span').data('figure_index'));

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}

						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};

			postdata[VALTYPE_HLIST] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.hlist_value = value; // it is a list node
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {

							init_value_structure();

							propinfo[active.prop].values[active.value_index] = data.value;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				} else {
					data.hlist_value = value; // it is a list node
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}



						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};


			postdata[VALTYPE_SELECTION] = postdata[VALTYPE_HLIST];

			postdata[VALTYPE_COLOR] = function(value_container, prop, value_index, value, is_new_value) {
				var data = {};
				if (is_new_value) {
					data.color_value = value;
					data.res_id = res_id;
					data.prop = prop;
					data.project_id = project_id;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {

							init_value_structure();

							propinfo[active.prop].values[active.value_index] = data.value;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}
						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				} else {
					data.color_value = value;
					data.project_id = project_id;
					SALSAH.ApiPut('values/' + encodeURIComponent(propinfo[prop].value_ids[value_index]), data, function(data) {
						if (data.status == ApiErrors.OK) {
							propinfo[active.prop].values[active.value_index] = data.value;
							// set new value Iri
							propinfo[active.prop].value_ids[active.value_index] = data.id;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							if (active.is_new_value) {
								var prop_container = active.value_container.parent();
								make_add_button(prop_container, active.prop);
							}



						}
						else {
							alert(status.errormsg);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};




			postdata[VALTYPE_ICONCLASS] = postdata[VALTYPE_TEXT];
			postdata[VALTYPE_GEONAME] = postdata[VALTYPE_TEXT];
            
			
			postdata['LOCATION'] = function(value_container, prop, res_id, sipi_response, is_new_value) {
				var data = {};
				if (is_new_value) {
                    /*
					data.value = value;
					data.res_id = res_id;
					data.prop = prop;
					SALSAH.ApiPost('values', data, function(data) {
						if (data.status == ApiErrors.OK) {
							// data.value has the following members:
							//   data.value.geoname_id
							//   data.value.geoname
							//
							var tmpobj = {};
							tmpobj.geoname_id = data.value.geoname_id;
							tmpobj.geoname = data.value.geoname;

							if (!propinfo[active.prop].values) propinfo[active.prop].values = Array();
							if (!propinfo[active.prop].value_ids) propinfo[active.prop].value_ids = Array();
							if (!propinfo[active.prop].value_rights) propinfo[active.prop].value_rights = Array();
							if (!propinfo[prop].value_iconsrcs) propinfo[prop].value_iconsrcs = Array();
							if (!propinfo[prop].value_firstprops) propinfo[prop].value_firstprops = Array();
							if (!propinfo[prop].value_restype) propinfo[prop].value_restype = Array();
							propinfo[active.prop].values[active.value_index] = tmpobj;
							propinfo[active.prop].value_ids[active.value_index] = data.id;
							propinfo[active.prop].value_rights[active.value_index] = data.rights;
							propinfo[active.prop].value_iconsrcs[active.value_index] = null;
							propinfo[active.prop].value_firstprops[active.value_index] = null;
							propinfo[active.prop].value_restype[active.value_index] = null;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);

							var prop_container = value_container.parent();
							make_add_button(prop_container, active.prop);
						}
						else {
							alert(data.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
                    */
				}
				else {
					data = {
						file: {
							originalFilename: sipi_response["original_filename"],
							originalMimeType: sipi_response["original_mimetype"],
							filename: sipi_response["filename"]
						}
					};
					SALSAH.ApiPut('filevalue/' + encodeURIComponent(res_id), data, function(data) {
						if (data.status == ApiErrors.OK) {
							// data.value has the following members:
							//   data.value.geoname_id
							//   data.value.geoname
							//
							/*
							var tmpobj = {};
							tmpobj.geoname_id = data.value.geoname_id;
							tmpobj.geoname = data.value.geoname;

							propinfo[active.prop].values[active.value_index] = tmpobj;

							active.value_container.empty();
							reset_value(active.value_container, active.prop, active.value_index);
							*/

							active.value_container.empty();
							var locations = data['locations'];

							if (locations !== undefined) {
							    // Provide a link to the full-size image.
							    var fullSize = locations[locations.length - 1];
                                value_container.append($('<a>').attr({href: fullSize['path'], target: "_blank"}).text(' ' + fullSize['origname'] + ' '));
							}

							if (res_rights >= RESOURCE_ACCESS_MODIFY) {
								$('<img>', {src: edit_icon.src, 'class': 'propedit'}).click(function(event) {
									edit_value(active.value_container, '__location__', 0);
								}).css({cursor: 'pointer'}).appendTo(value_container);

                                // Reload the window to display the new image.
                                var window_html = active.value_container.parents(".win")
                                window_html.win('deleteWindow');
                                RESVIEW.new_resource_editor(resdata.res_id, 'NEW RESOURCE');
							}
						}
						else {
							alert(data.errormsg);
							cancel_edit(value_container);
						}
						active = undefined;
					}).fail(function(){
						cancel_edit(value_container);
					});
				}
			};

			postdata['LABEL'] = function(value_container, prop, value_index, value, is_new_value) {
				data = {
					label: value.utf8str
				};
				SALSAH.ApiPut('resources/label/' + encodeURIComponent(res_id), data, function(data) {
					if (data.status == ApiErrors.OK) {
						propinfo[active.prop].values[active.value_index] = data.label;

						active.value_container.empty();
						reset_value(active.value_container, active.prop, active.value_index);
					} else {
						alert(status.errormsg);
					}
					active = undefined;
				});
			};
			
			if (active !== undefined) {
				if (!confirm(strings._canceditquest)) return;
				cancel_edit(value_container);
			}

			var drop_data = value_container.children('span:first').data('drop_resid');

			if (typeof value_index == 'undefined') {
				is_new_value = true;
				if (typeof propinfo[prop].values == 'undefined') {
					value_index = 0;
				}
				else {
					value_index = propinfo[prop].values.length;
				}
			}
			value_container.empty();

			var attributes = {'class': 'propedit'};
			var textattr;
			if (propinfo[prop].attributes) {
				textattr = propinfo[prop].attributes.split(';');
				for (idx in textattr) {
					var tmp = textattr[idx].split('=');
					if (tmp[0] == 'selection') continue;
					if (tmp[0] == 'hlist') continue;
					if (tmp[0] == 'restypeid') continue;
					if (tmp[0] == 'numprops') continue;
					attributes[tmp[0]] = tmp[1];
				}
			}
			switch (propinfo[prop].guielement) {
				case 'text': {
					attributes.type = 'text';
					if (!is_new_value) {

						if (propinfo[prop].valuetype_id == VALTYPE_FLOAT || propinfo[prop].valuetype_id == VALTYPE_URI) {
							attributes.value = propinfo[prop].values[value_index];
						} else if (propinfo[prop].valuetype_id == 'LABEL') {
							attributes.value = propinfo[prop].values[value_index];
						} else {
							attributes.value = propinfo[prop].values[value_index].utf8str;
						}
					}
					attributes['style'] = 'width: 95%'; // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! should be configurable !!
					var tmpele = $('<input>', attributes);
					/*.dragndrop('makeDropable', function(event, dropdata) {
						front = tmpele.val().substring(0, tmpele.attr('selectionStart'));
						back = tmpele.val().substring(tmpele.attr('selectionEnd'));
						tmpele.val(front + '<+LINKTO RESID=' + dropdata.resid + '+>'+ back);
					});*/
					value_container.append(tmpele);
					tmpele.focus();
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {

						if (propinfo[prop].valuetype_id == VALTYPE_FLOAT || propinfo[prop].valuetype_id == VALTYPE_URI) {
							postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, value_container.find('input').val(), is_new_value);
						} else {

							var richtext_value = {
								utf8str: value_container.find('input').val(),
								textattr: JSON.stringify({}),
								resource_reference: []
							};
							postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, richtext_value, is_new_value);
						}

						}).css({cursor: 'pointer'}));
					break;
				}
				case 'textarea': {
					var tmpele = $('<textarea>', attributes)/*.dragndrop('makeDropable', function(event, dropdata) {
						front = tmpele.val().substring(0, tmpele.attr('selectionStart'));
						back = tmpele.val().substring(tmpele.attr('selectionEnd'));
						tmpele.val(front + '<+LINKTO RESID=' + dropdata.resid + '+>'+ back);
					});*/
					if (!is_new_value) {
						tmpele.append(propinfo[prop].values[value_index].utf8str);
					}
					value_container.append(tmpele);
					tmpele.focus();
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						var richtext_value = {
							utf8str: value_container.find('textarea').val(),
							textattr: JSON.stringify({}),
							resource_reference: []
						};
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, richtext_value, is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'richtext': {
					
					var rtopts = {};
					
					//
					// textattr contains the matching between tagnames and offset names
					// 
					if (textattr !== undefined) {
						//console.log(extattr);
						
						var matching = {};
						for (var i in textattr) {
							var cur_attr = textattr[i].split('=');
							matching[cur_attr[0]] = cur_attr[1];
						}
						
						rtopts.matching = matching;
						
					}

					var tmpele = $('<div>', {'class': 'htmleditor'});
					
										
					if (!is_new_value) {
						rtopts.utf8str = propinfo[prop].values[value_index]['utf8str'];
						rtopts.textattr = $.parseJSON(propinfo[prop].values[value_index]['textattr']);
					}
					
					value_container.append(tmpele);
					// lock focus to prevent problems with iframe
					var win = tmpele.parents('.win');
					win.win('setFocusLock', true);
					tmpele.htmleditor('edit', rtopts);
					
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event){
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
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, props, is_new_value);
						
						win.win('setFocusLock', false);
						tmpele.htmleditor('destroy');
					}).css({cursor: 'pointer'}));
					/*
					var on_save_cb = function(event) {
						var rtdata = tmpele.texteditor('serialize');
						if (is_new_value) { // we created a new richtext value
							var propvals = {};
							propvals['salsah:utf8str'] = {};
							propvals['salsah:textattr'] = {};
							propvals['salsah:value_of'] = {};

							propvals['salsah:utf8str'].value = rtdata.text;
							propvals['salsah:textattr'].value = JSON.stringify(rtdata.props);
							propvals['salsah:value_of'].value = res_id;

							if (rtdata.props['_link'] !== undefined) {
								propvals['salsah:resource_reference'] = {};
								propvals['salsah:resource_reference'].values = [];
								for (var link_index in rtdata.props['_link']) {
									if (propvals['salsah:resource_reference'].values.indexOf(rtdata.props['_link'][link_index].resid) == -1) {
										propvals['salsah:resource_reference'].values.push(rtdata.props['_link'][link_index].resid);
									}
								}
							}
							SALSAH.ApiPost('resources', {
								restype_id: 'salsah:richtext',
								properties: propvals
							}, function(data) {
								if (data.status == ApiErrors.OK) {
									postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, data.res_id, is_new_value);
								}
								else {
									alert(data.errormsg);
								}
							});
						}
						else {
							var res_refs = [];
							for (var link_index in rtdata.props['_link']) {
								if (res_refs.indexOf(rtdata.props['_link'][link_index].resid) == -1) {
									res_refs.push(rtdata.props['_link'][link_index].resid);
								}
							}
							data = {
								res_id: propinfo[prop].values[value_index]
							};
							
							var tmp_active = {};
							$.extend(tmp_active, active);
							SALSAH.ApiDelete('values/salsah:resource_reference', data, function(data2) {
								if (data2.status == ApiErrors.OK) {
									var data = {
										res_id: propinfo[prop].values[value_index],
										value_arr: [{
											value: rtdata.text,
											valuetype_id: VALTYPE_TEXT,
											value_id: existing_rtprops['salsah:utf8str'].values[0].id
										}, {
											value: JSON.stringify(rtdata.props),
											valuetype_id: VALTYPE_TEXT,
											value_id: existing_rtprops['salsah:textattr'].values[0].id
										}]
									};
									SALSAH.ApiPut('values', data, function(data3) {
										if (data3.status == ApiErrors.OK) {
											var data = {
												res_id: propinfo[prop].values[value_index],
												value_arr: []
											};
											for (var i in res_refs) {
												data.value_arr.push({
													value: res_refs[i],
													prop: 'salsah:resource_reference',
												});
											}
											SALSAH.ApiPost('values', data, function(data4) {
												if (data4.status == ApiErrors.OK) {
													tmp_active.value_container.empty();
													reset_value(tmp_active.value_container, tmp_active.prop, tmp_active.value_index);
													active = undefined;
												}
												else {
													alert(data4.errormsg);
												}
											});
										}
										else {
											alert(data3.errormsg);
										}
									});
									
								}
								else {
									alert(data2.errormsg);
								}
							});
						}
					};

					if (!is_new_value) {
						// rtopts.text = propinfo[prop].values[value_index].textval;
						// rtopts.props = $.parseJSON(propinfo[prop].values[value_index].textattr); // texteditor.js expets a JS object, NOT a json-string
						
						SALSAH.ApiGet('properties', propinfo[prop].values[value_index], {origin: 'local'},
						function(data) {
							if (data.status == ApiErrors.OK)
							{
								existing_rtprops = data.properties; // must be saved in parent scope!
								var textobj = {};
								rtopts.text = existing_rtprops['salsah:utf8str'].values[0].val;
								rtopts.props = $.parseJSON(existing_rtprops['salsah:textattr'].values[0].val);
								tmpele.texteditor(rtopts);
							}
							else {
								alert(data.errormsg);
							}
						});
		
					}
					else {
						tmpele.texteditor(rtopts);
					}
*/
					break;
				}
				case 'pulldown': {
					var selection_id;
					var attrs = propinfo[prop].attributes.split(';');
					$.each(attrs, function() {
						var attr = this.split('=');
						if (attr[0] == 'selection' || attr[0] == 'hlist') {
							//selection_id = attr[1];
							selection_id = attr[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL
						}
					});
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.selection('edit', {selection_id: selection_id});
					}
					else {
						tmpele.selection('edit', {selection_id: selection_id, value: propinfo[prop].values[value_index]});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.selection('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'radio': {
					var selection_id;
					var attrs = propinfo[prop].attributes.split(';');
					$.each(attrs, function() {
						var attr = this.split('=');
						if (attr[0] == 'selection' || attr[0] == 'hlist') {
							//selection_id = attr[1];
							selection_id = attr[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL
						}
					});
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.selradio('edit', {selection_id: selection_id});
					}
					else {
						tmpele.selradio('edit', {selection_id: selection_id, value: propinfo[prop].values[value_index]});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.selradio('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'hlist': {

					

					var hlist_id;
					var attrs = propinfo[prop].attributes.split(';');
					$.each(attrs, function() {
						var attr = this.split('=');
						if (attr[0] == 'hlist') {
							//hlist_id = attr[1]; // "<http://data.knora.org/lists/d4f8e79ce2>"
							hlist_id = attr[1].replace("<", "").replace(">", ""); // remove brackets from Iri to make it a valid URL
						}
					});
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.hlist('edit', {hlist_id: hlist_id});
					}
					else {
						tmpele.hlist('edit', {hlist_id: hlist_id, value: propinfo[prop].values[value_index]});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.hlist('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'slider': {
					break;
				}
				case 'spinbox': {
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.spinbox('edit');
					}
					else {
						tmpele.spinbox('edit', propinfo[prop].values[value_index]);
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.spinbox('value'), is_new_value);
					}).css({cursor: 'pointer'}));
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
								var tmpele = $('<span>', attributes).appendTo(value_container);
								tmpele.append($('<img>', {src: resinfo.restype_iconsrc}).css({borderStyle: 'none'}));
								tmpele.append(' <em>' + resinfo.firstproperty + ' (' + resinfo.restype_label + ')</em>');
								value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
									postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, drop_data, is_new_value);
								}).css({cursor: 'pointer'}));
							}
							else {
								alert(data.errormsg);
							}
						});
					}
					else {
						var restype_id = -1;
						var numprops = 1;
						var attrs = propinfo[prop].attributes.split(';');
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
						value_container.append(inpele);
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
										value_container.prepend(tmpele);
									}
									else {
										alert(data.errormsg);
									}
								});
							}
						});
						inpele.searchbox('setFocus');
						value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
							var res_id = value_container.find('.__searchbox_res').data('res_id');
							postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, res_id, is_new_value);
						}).css({cursor: 'pointer'}));
					}
					break;
				}
				case 'date': {
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.dateobj('edit');
					}
					else {
						tmpele.dateobj('edit', propinfo[prop].values[value_index]);
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.dateobj('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'time': {
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.timeobj('edit');
					}
					else {
						tmpele.timeobj('edit', {timeobj: propinfo[prop].values[value_index]});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.timeobj('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'interval': {
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.timeobj('edit', {show_duration: true});
					}
					else {
						tmpele.timeobj('edit', {timeobj: propinfo[prop].values[value_index], show_duration: true});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.timeobj('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}
				case 'geometry': {
					//
					// stop highlighting !!!
					//
					if ((options.canvas === undefined) || (options.viewer === undefined)) break;
					if (is_new_value) {
						value_container.append($('<span>', attributes).css({color: 'red'}).append('Select a figure type and draw...'));

						options.viewer.topCanvas().regions('setObjectStatus', 'inactive').regions('setDefaultLineColor', propinfo['http://www.knora.org/ontology/knora-base#hasColor'].values[0]);

						RESVIEW.figure_drawing (options.viewer, function(figure, index) {
							value_container.find('span').empty().append(figure.type); // add text to show what figure type willbe drawn...
							//                                                      options.viewer.getTaskbar().find('.regionActions').remove();
							options.viewer.getTaskbar().elestack('hide', 'region_drawings');
							value_container.find('span').data('figure_index', index);
							value_container.find('span').after($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
								var new_geo = options.canvas.regions('returnObjects', value_container.find('span').data('figure_index'));
								postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, JSON.stringify(new_geo), is_new_value);
								options.viewer.topCanvas().regions('setObjectStatus', 'active');
								//                                                          options.viewer.getTaskbar().elestack('show', 'main');
								RESVIEW.resetRegionDrawing(options.viewer);
							}));
							//                                                      options.viewer.getTaskbar().elestack('show', 'main');
							//                                                      RESVIEW.resetRegionDrawing(options.viewer);
						});
					}
					else {
						value_container.unbind('.highlight');
						var geometry_object = JSON.parse(propinfo[prop].values[value_index]);
						var tmpele = $('<span>', attributes).css({color: 'red'}).append(geometry_object.type);
						value_container.append(tmpele);
						options.viewer.getTaskbar().elestack('hide', 'main');
						if (typeof options.canvas !== 'undefined') {
							var geo = options.canvas.regions('searchObject', 'val_id', propinfo[prop].value_ids[value_index]);
							options.viewer.topCanvas().regions('setMode', 'edit', {index: geo.index});
							value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
								var new_geometry_object = {};
								for (var i in geo.obj) {
									if (i == 'res_id') continue;
									if (i == 'val_id') continue;
									new_geometry_object[i] = geo.obj[i];
								}
								postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, JSON.stringify(new_geometry_object), is_new_value);
								options.viewer.topCanvas().regions('redrawObjects');
								options.viewer.getTaskbar().elestack('show', 'main');
							}).css({cursor: 'pointer'}));
						}
						else {
							alert('FATAL INTERNAL ERROR!'); //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! BESSERE FEHLERMELDUNG
						}
					}
					break;
				}
				case 'colorpicker': {
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.colorpicker('edit');
					}
					else {
						tmpele.colorpicker('edit', {color: propinfo[prop].values[value_index]});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						var colval = tmpele.colorpicker('value');
						options.viewer.topCanvas().regions('setObjectAttribute', 'lineColor', colval, 'res_id', res_id).regions('redrawObjects', true);
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, colval, is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}

				case 'geoname': {
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.geonames('edit', {new_entry_allowed: true});
					}
					else {
						tmpele.geonames('edit', {new_entry_allowed: true, value: propinfo[prop].values[value_index]});
					}
					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						postdata[propinfo[prop].valuetype_id](value_container, prop, value_index, tmpele.geonames('value'), is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}

				case 'fileupload': { // this is for resources which do have a location!
					var tmpele = $('<span>', attributes).appendTo(value_container);
					if (is_new_value) {
						tmpele.location('edit');
					}
					else {
						tmpele.location('edit');
					}

					value_container.append($('<img>', {src: save_icon.src, title: strings._save, 'class': 'propedit'}).click(function(event) {
						var sipi_response = tmpele.location('value');
						postdata['LOCATION'](value_container, prop, res_id, sipi_response, is_new_value);
					}).css({cursor: 'pointer'}));
					break;
				}

				default: {

				}
			}
			value_container.append($('<img>', {src: cancel_icon.src, title: strings._cancel, 'class': 'propedit'}).click(function(event) {
				cancel_edit(value_container);
			}));
			
			active = {value_container: value_container, prop: prop, value_index: value_index, is_new_value: is_new_value};
		};

		var cancel_edit = function(value_container) {
			if (active === undefined) return;

			//
			// some guielements need some special processing...
			//
			switch(propinfo[active.prop].guielement) {
				case 'geometry': {
					if (active.is_new_value) {
						var fig_index = value_container.find('span').data('figure_index');
						if (fig_index !== undefined) {
							options.canvas.regions('deleteObject', fig_index);
						}
						options.viewer.topCanvas().regions('setObjectStatus', 'active');
						RESVIEW.initRegionDrawing(options.viewer);
					}
					else {
						var geo = options.canvas.regions('searchObject', 'val_id', propinfo[active.prop].value_ids[active.value_index]);
						var old_geo = JSON.parse(propinfo[active.prop].values[active.value_index]);
						for (var i in old_geo) {
							geo.obj[i] = old_geo[i];
						}
						options.canvas.regions('redrawObjects');
						active.value_container.bind('mouseenter.highlight', function(event){
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
					var tmpele = value_container.find('.__searchbox');
					if (tmpele !== undefined) {
						tmpele.searchbox('remove');
						tmpele.remove();
					}
					break;
				}
				case 'richtext': {
                    /*var win = value_container.parents('.win');
                    win.win('setFocusLock', false);*/
					// destroy instance of CKEDITOR properly
                    var win = value_container.parents('.win');
					win.win('setFocusLock', false);
					value_container.find('.htmleditor').htmleditor('destroy');
					break;
				}
				RESVIEW.resetRegionDrawing(options.viewer);
			}

			active.value_container.empty();
			reset_value(active.value_container, active.prop, active.value_index);
			if (active.is_new_value) {
				var prop_container = active.value_container.parent();
				active.value_container.remove();
				make_add_button(prop_container, active.prop);
			}
			active = undefined;
		};

		// console.log("propedit iterating over props:")
		var prop_index = 0;
		return this.each(function() {
			var prop = $(this).data('propname');
			// console.log(prop);
			reset_field(prop, prop_index, options.readonly);
			prop_index++;
		});
	};
})( jQuery );
