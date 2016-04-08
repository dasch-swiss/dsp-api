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

SALSAH.showval = function(value_container, prop, value_index, options)
{

	//console.log("in showval: valtype is " + prop.valuetype_id);
	switch (prop.valuetype_id) {
		// this value type is mot used anymore: every text is a richtext now
		/*case VALTYPE_TEXT: {
			var reg = new RegExp('(http://[^<>\\s]+[\\w\\d])', 'g');     // replace URL's with anchor tags
			//value_container.append(prop.values[value_index].replace(reg, '<a href="$1" target="_blank">$1</a>'));
			value_container.append(prop.values[value_index]);
			break;
		}*/
		case VALTYPE_INTEGER: {
			value_container.append(prop.values[value_index]);
			break;
		}
		case VALTYPE_FLOAT: {
			value_container.append(prop.values[value_index]);
			break;
		}
		case VALTYPE_DATE: {
			value_container.dateobj('init', prop.values[value_index]);
			break;
		}
		case VALTYPE_PERIOD: {
			value_container.append('VALTYPE_PERIOD: NOT YET IMPLEMENTED!');
			break;
		}
		case VALTYPE_RICHTEXT: {
			
			var textobj = {};
			if (prop.attributes !== undefined && prop.attributes !== null) {
				//console.log(prop.attributes);

				var matching = {};
				var attrs = prop.attributes.split(';');

				for (var i in attrs) {
					var cur_attr = attrs[i].split('=');
					matching[cur_attr[0]] = cur_attr[1];
				}
			
				textobj.matching = matching;
			}


			textobj.utf8str = prop.values[value_index]['utf8str'];
			textobj.textattr = (prop.values[value_index]['textattr'] === undefined) ? {} : $.parseJSON(prop.values[value_index]['textattr']); // textattr is a stringified JSON
			
			var tmp_ele = $('<div>');
			value_container.append(tmp_ele.htmleditor(textobj));
			
			//
			// add handlers to SALSAH Links here
			//
			value_container.find('a.salsah-link').off('mouseover').on('mouseover', function(event) {

				var resid = $(this).attr('href');
				load_infowin(event, resid, this);

			}).off('click').on('click', function(event) {
				event.preventDefault();

				var resid = $(this).attr('href');

				RESVIEW.new_resource_editor(resid, 'Linked Resource');
				
			});

			// all links will be opened in a new window
			value_container.find('a').attr({target: '_blank'});

			// value is represented by a RESOURCE which relates to the resource (via 'salsah:value_of') the 'normal' properties refer to
			break;
		} 
		case VALTYPE_RESPTR: {
			switch (prop.guielement) {
				case 'pulldown':
				case 'searchbox':
				default: { // which is 'pulldown' and 'searchbox'
					var span = $('<span>', {'class': 'propedit'}).css('cursor', 'pointer').mouseover(function(event) {
						load_infowin(event, prop.values[value_index], this, 'local');
					}).dragndrop('makeDropable', function(event, dropdata) {
						span.data('drop_resid', dropdata.resid);
						span.next().click();
					}).click(function(event) {
						if (options.simple_view) {
							options.simple_view_action(prop.values[value_index]);
						}
						else {
							// check if this is a part of a compound object pointing to the same via its 'salsah:part_of' prop
							if (prop == 'salsah:part_of' && propinfo['salsah:seqnum'].values !== undefined) {
								// open compund object viewer at the correspondent position
								RESVIEW.new_resource_editor(prop.values[value_index], prop.value_firstprops[value_index], {}, {sequence_number: propinfo['salsah:seqnum'].values[0]});
							} else {
								// standard procedure
								RESVIEW.new_resource_editor(prop.values[value_index], prop.value_firstprops[value_index]);
							}
						}
					}).appendTo(value_container);
					$('<img>', {src: prop.value_iconsrcs[value_index]}).css({borderStyle: 'none'}).appendTo(span);
					$(span).append(' <em>' + prop.value_firstprops[value_index] + ' (' + prop.value_restype[value_index] + ')</em>');
				}
			}
			break;
		}
		case VALTYPE_HLIST: {
			var hlist_id;
			var attrs = prop.attributes.split(';');
			$.each(attrs, function() {
				var attr = this.split('=');
				if (attr[0] == 'hlist') {
					hlist_id = attr[1].slice(1,-1);
				}
			});
			switch (prop.guielement) {
				case 'hlist': {
					value_container.hlist('init', {hlist_id: hlist_id, value: prop.values[value_index]});
					break;
				}
                case 'radio': {
                    value_container.selradio('init', {selection_id: hlist_id, value: prop.values[value_index]});
                    break;
                }
                case 'pulldown': {
                    value_container.selection('init', {selection_id: hlist_id, value: prop.values[value_index]});
                    break;
                }
			}
			break;
		}
		case VALTYPE_TIME: {
			value_container.timeobj('init', prop.values[value_index]);
			//value_container.append(prop.values[value_index]);
			break;
		}
		case VALTYPE_INTERVAL: {
			//value_container.append(prop.values[value_index]);
			value_container.append('VALTYPE_INTERVAL: NOT YET IMPLEMENTED!');
			break;
		}
		case VALTYPE_GEOMETRY: {
			var geometry_object = JSON.parse(prop.values[value_index]);
			value_container.css({cursor: 'default'}).append(geometry_object.type);
			if (options.canvas !== undefined) {
				value_container.bind('mouseenter.highlight', function(event) {
					var geo = options.canvas.regions('searchObject', 'val_id', prop.value_ids[value_index]);
					options.canvas.regions('highlightObject', geo.index);
				}).bind('mousemove.highlight', function(event) {
					var geo = options.canvas.regions('searchObject', 'val_id', prop.value_ids[value_index]);
					options.canvas.regions('highlightObject', geo.index);
				}).bind('mouseout.highlight', function(event){
					options.canvas.regions('unhighlightObjects');
				});
			}
			else {
				value_container.append(' (' + strings._open_assoc_res +')');
			}
			break;
		}
		case VALTYPE_COLOR: {
			value_container.colorpicker('init', {color: prop.values[value_index]});
			break;
		}
		case VALTYPE_ICONCLASS: {
			value_container.append(prop.values[value_index]);
			break;
		}
		case VALTYPE_GEONAME: {
			value_container.geonames('init', {value: prop.values[value_index]});
			break;
		}
		default: {
			value_container.append('INTERNAL ERROR: UNKNOWN VALUE TYPE! ' + prop.valuetype_id);
		}
	} // switch(parseInt(prop.valuetype_id))
	
};
