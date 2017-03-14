
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
* Function which displays the result of a search within a jQuery element
*
* @param ele JQuery element into which the search result list should be written
* @param pele Paging elemend (where the paging info should be written), can be undefined
* @param data Data object returned by call to search API
* @param searchtype Only used in project specific detail view. "extended" or "???"
*
*/
SALSAH.searchlist = function(ele, pele, data, params, searchtype) {

	//
	// Create the paging
	//
	ele.empty();
	ele.append($('<div>').addClass('center results').text(s_('_extsearchres').replace(/%d/, data.nhits)));

	var pele2;
	if (pele !== undefined) {
		pele2 = pele.clone(true);
		ele.append(pele);
	}

	var table,
		img,
		item = {};

	// display_types: table = simple list (default) | matrix = Lighttable | editor = Tableeditor (Spreadsheet)
	switch(params.display_type) {
		case 'matrix':
			table = $('<div>').addClass('viewbox');
			data.subjects.forEach(function(subject) {

				SALSAH.ApiGet('resources', subject.obj_id, {resinfo: true, reqtype: 'context'}, function(propdata) {
					var spliturl,
						splitval,
						res_info;

					spliturl = subject.preview_path.split("&");
					splitval = jQuery.inArray( "qtype=frames", spliturl );

					if (propdata.status === ApiErrors.OK) {
						res_info = propdata.resource_context.resinfo;

						// in case of film frames
						if(splitval > 0) {
							var av_duration = res_info.locdata.duration,
								av_frames = res_info.preview.path,
								res_id = propdata.resource_context.canonical_res_id;
								info_icon = SITE_URL + '/app/icons/32x32/info.png';

							table.append(
								item.media = $('<div>').addClass('thumbframe').css({width: parseInt(data.thumb_max.nx) + 10, height: parseInt(data.thumb_max.ny) + 10})
									.flipbook({
										imglocation: av_frames,
										duration: av_duration,
										movieid: res_id
									})
									.append($('<span>').addClass('thumbinfo').css({
										position: 'relative',
										top: '-32px',
										left: parseInt(data.thumb_max.nx) / 2 - 10,

									})
										.append($('<img>').attr({src: info_icon}).addClass('result_info')
											.on('mouseover', function(event) {
												load_infowin(event, subject.obj_id, this);
											})
										)
									)
							);
						} else {
							table.append($('<div>').addClass('thumbframe').css({width: parseInt(data.thumb_max.nx) + 10, height: parseInt(data.thumb_max.ny) + 10})
									.append(item.media = $('<img>').attr({src: subject.preview_path}).addClass('thumbnail').css({cursor: 'pointer'}).on('mouseover', function(event){
										load_infowin(event, subject.obj_id, this);
									}))
							);
						}

						if (RESVIEW.winclass == '.workwin_content') {
							item.media.on('click', function(event) {
								SALSAH.show_detail(subject.obj_id, {searchtype: searchtype, params: params});
							});
						}
						else {
							item.media.on('click', function(event) {
								RESVIEW.new_resource_editor(subject.obj_id, '');
							});
						}

					} else {
						alert(new Error().lineNumber + ' ' + data.errormsg);
					}

				});

			});
			break;

		// tableedit: excel-like table editor
		case 'editor':
		$('.results').addClass('result_panel');
			ele.tableedit({
				data: data,
				showprops: params.showprops
			});
			break;

		// csv: export the results as comma (or tab) separated values (csv)
		case 'csv':
			$('.results').addClass('result_panel');
				ele.csv({
					data: data,
					showprops: params.showprops
				});
				break;

		// sequence protocol view: for movies; here we're using also the tableedit stuff
		case 'sequence':
			ele.tableedit({
				data: data,
				showprops: params.showprops,
				viewer: 'sequence'
			});
			break;

		// pinboard is a masonry style viewer; it will be used in public frontends e.g. in the sgv project
		case 'pinboard':
			// pinterest style

			$('div.result_info').text(s_('_extsearchres').replace(/%d/, data.nhits));
			ele.empty();
			table = $('<div>').addClass('wall').attr({id: 'grid', 'data-columns': ''});
//			table.attr(''); // .data('columns')
			data.subjects.forEach(function(subject) {

				SALSAH.ApiGet('resources', subject.obj_id, {resinfo: true, reqtype: 'context'}, function(propdata) {
					var spliturl,
						splitval,
						res_info,
						item = {};

					spliturl = subject.preview_path.split("&");
					splitval = jQuery.inArray( "qtype=frames", spliturl );

					if (propdata.status === ApiErrors.OK) {
						res_info = propdata.resource_context.resinfo;

						// in case of film frames
						if(splitval > 0) {
							var av_duration = res_info.locdata.duration,
								av_frames = res_info.preview.path,
								res_id = propdata.resource_context.canonical_res_id;
								info_icon = SITE_URL + '/app/icons/32x32/info.png';

							table.append(item.media = $('<div>').addClass('movie item ' + subject.obj_id)
									.attr({id: subject.obj_id})
									.flipbook({
										imglocation: av_frames,
										duration: av_duration,
										movieid: res_id
									})
									.append(
										$('<span>').addClass('thumbinfo')
										.css({
											position: 'relative',
											top: '-32px',
											left: parseInt(data.thumb_max.nx) / 2 - 10,
											})
											.append($('<img>').attr({src:info_icon})
												.addClass('result_info')
												.on('mouseover', function(event) {
													load_infowin(event, subject.obj_id, this);
												})
											)
									)
									.append(
										item.data = $('<div>').addClass('data')
									)
							);
						} else {
							var image;
							if(res_info.locations !== null) {
								image = res_info.locations[4].path;
							} else {
								image = SITE_URL + '/app/icons/image-not-available.png';
							}
							table.append(
								item.frame = $('<div>').addClass('item ' + subject.obj_id)
									.attr({id: subject.obj_id})
									.append(
										item.media = $('<img>')
										.attr({src: image})
										.addClass('media')
										.on('mouseover', function(event){
										//	load_infowin(event, subject.obj_id, this);
										})
									)
									.append(
										item.data = $('<div>').addClass('data')
									)
							);
						}
						SALSAH.ApiGet('properties', subject.obj_id,  {noresedit: true}, function(data) {
							window.status = 'GET...';
							$.each(params.important_props, function(i, prop) {
								var metadata = '';
								if(data.properties[prop] !== undefined) {
									metadata = data.properties[prop].values[0].textval;
									if(data.properties[prop].guielement === 'hlist' || data.properties[prop].guielement === 'geoname') {
										metadata = metadata.split(/ (.+)?/)[1];
									}
								}
								if (i === '1') {
									item.data.append($('<h3>').html(metadata));
								}
								else if (i === '2') {
									item.data.append($('<p>').append($('<strong>').html(metadata)));
								}
								else {
									item.data.append($('<p>').html(metadata));
								}
							});
						});
						// page for only one resource
						if (RESVIEW.winclass == '.workwin_content') {
							item.media.on('click', function(event) {
								SALSAH.zoom_resource(subject.obj_id, {searchtype: searchtype, params: params});
							});
							item.media.on('contextmenu', function(e) {
								SALSAH.zoom_resource(subject.obj_id, {searchtype: searchtype, params: params});
								return false;			// no context menu
							});
						}
						else {
							item.media.on('click', function(event) {
								RESVIEW.new_resource_editor(subject.obj_id, '');
							});
						}

					} else {
						alert(new Error().lineNumber + ' ' + data.errormsg);
					}

				});

			});
			break;

		default:
			var info_icon =  SITE_URL + '/app/icons/16x16/info.png';
			table = $('<table>').addClass('admin searchres');
			var tableheader;
			table.append(tableheader = $('<tr>')
					.append($('<th>').text(s_('_info')))
					.append($('<th>').text(s_('_type')))
					//.append($('<th>').text(strings._property))
					.append($('<th>').text(s_('_value')))
			);
			var max_n_vals = 0;
			var tr;
			var value;
			data.subjects.forEach(function(arrele) {
				var valcell;
				tr = $('<tr>').addClass('result_row').data('resid', arrele.obj_id);
				tr.append($('<td>')
						.append($('<img>').attr({src: info_icon}).addClass('result_info').on('mouseover', function(event) {
							load_infowin(event, arrele.obj_id, this);
						}))
				);
				tr.append($('<td>')
						.append($('<img>').attr({src: arrele.iconsrc, title: arrele.icontitle}))
						.append(arrele.iconlabel)
				);
				var idx;
				if (arrele.value.length > max_n_vals) max_n_vals = arrele.value.length;
				for (idx in arrele.value) {
					tr.append(valcell = $('<td>')
							.append($('<em>').append(arrele.valuelabel[idx] + ' : '))
					);

					switch (parseInt(arrele.valuetype_id[idx])) {
						case VALTYPE_TEXT: {
							var valstr;
							if ((params.searchstring !== undefined) && (params.searchstring.length > 0)) {
								var p = arrele.value[idx].indexOf(params.searchstring);
								if (p != -1) {
									var s = p - 25;
									if (s < 0) s = 0;
									var e = p + 35;
									if (e >= arrele.value[idx].length) e = arrele.value[idx].length;
									valstr = '…' + arrele.value[idx].substring(s, e) + '…';
									valstr = valstr.replace(params.searchstring, '<span class="searchres_highlight">' + params.searchstring + '</span>');
								}
								else {
									if (arrele.value[idx]) {
										if (arrele.value[idx].length > 32) {
											valstr = arrele.value[idx].substr(0, 23) + '…';
										}
										else {
											valstr = arrele.value[idx];
										}
									}
									else {
										valstr = '???';
									}
								}
							}
							else {
								if (arrele.value[idx]) {
									if (arrele.value[idx].length > 32) {
										valstr = arrele.value[idx].substr(0, 23) + '…';
									}
									else {
										valstr = arrele.value[idx];
									}
								}
								else {
									valstr = '???';
								}
							}
							valcell.append(valstr)
							break;
						}
						case VALTYPE_TIME: {
							valcell.append($('<span>').timeobj(arrele.value[idx]))
							break;
						}
						case VALTYPE_DATE: {
							valcell.append($('<span>').dateobj(arrele.value[idx]))
							break;
						}
						case VALTYPE_COLOR: {
							valcell.append($('<span>').css({'background-color': arrele.value[idx]}).text(arrele.value[idx]))
							break;
						}
						case VALTYPE_RICHTEXT: {
							if ((params.searchstring !== undefined) && (params.searchstring.length > 0)) {
								var p = arrele.value[idx].utf8str.indexOf(params.searchstring);
								var s = p - 25;
								if (s < 0) s = 0;
								var e = p + 35;
								if (e >= arrele.value[idx].utf8str.length) e = arrele.value[idx].utf8str.length;
								valstr = '…' + arrele.value[idx].utf8str.substring(s, e) + '…';
								valstr = valstr.replace(params.searchstring, '<span class="searchres_highlight">' + params.searchstring + '</span>');
							}
							else {
								if (arrele.value[idx].utf8str.length > 32) {
									valstr = arrele.value[idx].utf8str.substr(0, 32) + '…';
								}
								else {
									valstr = arrele.value[idx].utf8str;
								}

							}
							valcell.append(valstr);
							break;
						}
						case VALTYPE_RESPTR: {
							if (arrele.value[idx].resinfo.value_of === undefined) {
								valcell.append(arrele.value[idx].firstprop.label + ' : ' + arrele.value[idx].firstprop.values[0].val + ' (')
									.append($('<img>').attr({src: arrele.value[idx].resinfo.restype_iconsrc, title: arrele.icontitle}).on('click', function(event) {
										event.stopImmediatePropagation();
										if (RESVIEW.winclass == '.workwin_content') {
											SALSAH.show_detail(arrele.value[idx].resid, {searchtype: searchtype, params: params});
										}
										else {
											RESVIEW.new_resource_editor(arrele.value[idx].resid, '');
										}
									}))
									.append(arrele.value[idx].resinfo.restype_label + ')')
							}
							else {
								valcell.append(arrele.value[idx].resinfo.restype_name + '>>' + arrele.value[idx].firstprop.values[0].val + ' (')
									.append($('<img>').attr({src: arrele.value[idx].resinfo.restype_iconsrc, title: arrele.icontitle}))
									.append(arrele.value[idx].resinfo.restype_label + ')');
							}
							break;
						}
						case VALTYPE_ICONCLASS: {
							valcell.append(arrele.value[idx]);
							break;
						}
						default: {
							valcell.append(arrele.value[idx]);
						}
					} // switch

				} // foreach
				if (RESVIEW.winclass == '.workwin_content') {
					tr.on('click', function(event) {
                        //console.log("calling SALSAH.show_detail")
						SALSAH.show_detail(arrele.obj_id, {searchtype: searchtype, params: params});
					});
				}
				else {
					tr.on('click', function(event) {
                        //console.log("1. calling RESVIEW.new_resource_editor")
						RESVIEW.new_resource_editor(arrele.obj_id, '');
					});
				}
				table.append(tr);
			});
			for (var i = 1; i < max_n_vals; i++) {
				tableheader.append($('<th>').text(' '));
			}
	}
	if (params.display_type == 'matrix') {

	}
	else {

	}
	ele.append(table);

	if (pele2 !== undefined) {
		ele.append(pele2);
	}


};
