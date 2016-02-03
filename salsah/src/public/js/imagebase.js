
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

$(function() {

	//var metadataAreaDomCreate = function(topele, winid, tabid, regnum, resource)
	var metadataAreaDomCreate = function(topele, resource, options)
	{
        console.log(resource);
		settings = {
			winid: undefined,
			tabid: undefined,
			regnum: undefined,
			view: 'normal' // my be 'detail'
		}
		$.extend(settings, options);

		var propedit;
		var petablle;
		var datafield;

		if (settings.view == 'normal')
		{
            //debugger;
			propedit = $('<div>').addClass('propedit');
			if (resource.resinfo.restype_iconsrc)
			{
				propedit
				.append(
					$('<img>').attr({src: resource.resinfo.restype_iconsrc, title: 'DRAG TO DESTINATION'}).addClass('propedit resicon').dragndrop('makeDraggable', 'RESID', {resid: resource.resdata.res_id})
				);
			}
			propedit.append($('<em>').attr({title: 'resource_id=' + resource.resdata.res_id + ' person_id=' + resource.resinfo.person_id + ' lastmod=' + resource.resinfo.lastmod}).addClass('propedit label').text(resource.resinfo.restype_label + ':'));

			propedit.append('&nbsp;&nbsp;');

			//
			// setting the actions in dependence of the rights
			//
			if (resource.resdata.rights >= Rights.RESOURCE_ACCESS_ANNOTATE)
			{
				propedit.append($('<img>').attr({src: SITE_URL + '/app/icons/16x16/comment.png', title: 'ANNOTATE'}));
			}

			if (resource.resdata.rights >= Rights.RESOURCE_ACCESS_DELETE)
			{
				propedit.append($('<img>').attr({src: SITE_URL + '/app/icons/16x16/trash.png', title: 'DELETE'}).addClass('propedit delres').data('res_id', resource.resdata.res_id));
			}

			if (resource.resdata.rights >= Rights.RESOURCE_ACCESS_RIGHTS)
			{
				propedit.append($('<img>').attr({src: SITE_URL + '/app/icons/16x16/lock.png', title: 'RIGHTS'}));
			}

			if (settings.tabid === undefined) {
				datafield = 'datafield';
			}
			else {
				datafield = 'datafield_' + settings.tabid;
			}

			propedit.append($('<hr>').addClass('propedit'));
			if ((resource.resdata.rights >= Rights.RESOURCE_ACCESS_VIEW_RESTRICTED) && (resource.resinfo.locations))
			{
				propedit.append($('<div>')
				.append($('<em>').addClass('propedit label').text('LOCATION: '))
				.append($('<div>').addClass('propedit ' + datafield + ' winid_' + settings.winid).data('propname', '__location__')));
			}
			if (settings.regnum !== undefined)
			{
				for (var propname in resource.props)
				{
					if (propname == 'salsah:region_of') continue;
					if (propname == '__location__') continue;
					propedit
					.append($('<em>').addClass('propedit label').text(resource.props[propname].label + ' :'))
					.append($('<div>').addClass('propedit ' + datafield + ' regnum_' + settings.regnum).data('propname', propname))
					.append($('<div>').css({height: '10px'}).text(' '));
				}
			}
			else {
				var propname;
				var propdata;
				var annotations = {};
				propedit.append(
					$('<div>').addClass('propedit sectionheader metadata winid_' + settings.winid)
					.append($('<img>').attr({src: SITE_URL + '/app/icons/collapse.png'}))
					.append(' Descriptive Metadata')
				);
				var metadata_section = $('<div>').addClass('propedit section metadata winid_' + settings.winid);
				for (propname in resource.props)
				{
					propdata = resource.props[propname];
					if (propdata.is_annotation == 1) { // keep annotations for annotations section below
						annotations[propname] = propdata;
						continue;
					}
					if (propname == '__location__') continue;
					metadata_section
					.append($('<em>').addClass('propedit label').append(propdata.label + ' :'))
					.append($('<div>').addClass('propedit ' + datafield + ' winid_' + settings.winid).data('propname', propname))
					.append($('<div>').css({height: '10px'}).append(' '));
				}
				propedit.append(metadata_section);

				//
				// now we do the annotations
				//
				propedit.append(
					$('<div>').addClass('propedit sectionheader annotations winid_' + settings.winid)
					.append($('<img>').attr({src: SITE_URL + '/app/icons/collapse.png'}))
					.append(' Annotations')
				);
				var annotations_section = $('<div>').addClass('propedit section annotations winid_' + settings.winid);
				for (propname in annotations) {
					propdata = annotations[propname];
					if (propname == '__location__') continue;
					annotations_section
					.append($('<em>').addClass('propedit label').append(propdata.label + ' :'))
					.append($('<div>').addClass('propedit ' + datafield + ' winid_' + settings.winid).data('propname', propname))
					.append($('<div>').css({height: '10px'}).append(' '));
				}
				propedit.append(annotations_section);
			}

			//
			// outgoing links
			//
			propedit.append(
				$('<div>').addClass('propedit sectionheader outgoing winid_' + settings.winid)
				.append($('<img>').attr({src: SITE_URL + '/app/icons/collapse.png'}))
				.append(' References to other objects')
			);
			var outgoing_section = $('<div>').addClass('propedit section outgoing winid_' + settings.winid);
			propedit.append(outgoing_section);

			//
			// incoming links
			//
			propedit.append(
				$('<div>').addClass('propedit sectionheader incoming winid_' + settings.winid)
				.append($('<img>').attr({src: SITE_URL + '/app/icons/collapse.png'}))
				.append(' Other objects referencing this object')
			);
			var incoming_section = $('<div>').addClass('propedit section incoming winid_' + settings.winid);
			var ext_res;
			for (var i in resource.incoming)
			{
				ext_res = resource.incoming[i];
				incoming_section.append(
					$('<div>').attr('id', 'row_' + settings.winid + '_' + ext_res.ext_res_id.id).data({ext_res: ext_res}).on('click', function() {
						var ext_res = $(this).data('ext_res');
						RESVIEW.new_resource_editor(ext_res.ext_res_id.id, ext_res.value);
					}).on('mouseover', function(event){
						var ext_res = $(this).data('ext_res');
						load_infowin(event, ext_res.ext_res_id.id + '_-_local', $(this));
					}).append(
						$('<img>').attr({
							src: ext_res.resinfo.restype_iconsrc,
							title: ext_res.resinfo.restype_label
						}).addClass('propedit').data({ext_res: ext_res}).on('mouseover', function(event){
							var ext_res = $(this).data('ext_res');
							load_infowin(event, ext_res.ext_res_id.id + '_-_local', $(this));
						})
					).append(ext_res.value)
				);
			}
			propedit.append(incoming_section);
			topele.append(propedit);
		}
		else {
			petable = $('<table>').addClass('propedit');
			datafield = 'datafield';
			for (var propname in resource.props)
			{
				if (propname == 'salsah:region_of') continue;
				if (propname == '__location__') continue;
				petable
				.append(
					$('<tr>')
					.append(
						$('<td>').addClass('propedit label').text(resource.props[propname].label)
					)
					.append(
						$('<td>').text(':')
					)
					.append(
						$('<td>').addClass('propedit datafield valuefield').data('propname', propname)
					)
				)
			}
			topele.append(petable);
		}


	}
	/*=======================================================================*/

	SALSAH.metadataAreaDomCreate = metadataAreaDomCreate;

	var sectionsetup = function(element, winid) {
		$(element).find('.sectionheader.winid_' + winid).each(
			function(index){
				var section = $(this).next('.section');
				if (section.css('display') == 'none') {
					$(this).css('cursor', 's-resize');
				}
				else {
					$(this).css('cursor', 'n-resize');
				}
			}
		);
		$(element).find('.sectionheader.winid_' + winid).click(
			function(event) {
				var section = $(this).next('.section');
				if (section.css('display') == 'none') {
					$(this).css('cursor', 'n-resize');
					$(this).find('img').attr('src', SITE_URL + '/app/icons/collapse.png');
				}
				else {
					$(this).css('cursor', 's-resize');
					$(this).find('img').attr('src', SITE_URL + '/app/icons/expand.png');
				}
				$(this).next('.section').slideToggle();
			}
		);
	};
	/*=======================================================================*/

	var regionsetup = function(element, winid) {
		var open_area_id;
		$(element).find('.regionheader.winid_' + winid).each(
			function(index) {
				var reg_id = $(this).data('reg_id');
				var section = $(this).next('.section');
				if (typeof open_area_id === 'undefined') {
					section.slideDown();
					$(this).find('img:first').attr('src', SITE_URL + '/app/icons/collapse.png');
					open_area_id = reg_id;
				}
				else {
					$(this).next('.section').css('display', 'none'); // this region info is collapsed ("slideUp")
					$(this).find('img:first').attr('src', SITE_URL + '/app/icons/expand.png');
				}
			}
		);
		$(element).find('.regionheader.winid_' + winid).click(
			function(event) {
				var section = $(this).next('.section');
				if (section.css('display') == 'none') {
					$('.regionheader.winid_' + winid + '.regnum_' + open_area_id).next('.section').slideUp();
					$('.regionheader.winid_' + winid + '.regnum_' + open_area_id).find('img:first').attr('src', SITE_URL + '/app/icons/expand.png');
					section.slideDown();
					$(this).find('img:first').attr('src', SITE_URL + '/app/icons/collapse.png');
					open_area_id = $(this).data('reg_id');
				}
			}
		);

		return {
			openSection: function(index) {
				var section = $('.regionheader.winid_' + winid + '.regnum_' + index).next('.section');
				if (open_area_id != index) {
					$('.regionheader.winid_' + winid + '.regnum_' + open_area_id).next('.section').slideUp();
					$('.regionheader.winid_' + winid + '.regnum_' + open_area_id).find('img:first').attr('src', SITE_URL + '/app/icons/expand.png');
					section.slideDown();
					$('.regionheader.winid_' + winid + '.regnum_' + index).find('img:first').attr('src', SITE_URL + '/app/icons/collapse.png');
					open_area_id = index;
				}
			}
		};
	};
	/*=======================================================================*/



	var regionsTabOnEnterCB = function(data_hook) {
		var figures = $.parseJSON(data_hook['FIGURES'].figures);
		for (var reg in figures) { // loop over all regions
			data_hook['FIGURES'].canvas.regions('drawObject', figures[reg]);
		}
		//
		// now we have to setup the region area and start the detect mode!
		//
		data_hook['FIGURES'].viewer.regionSection(data_hook['FIGURES'].regsec);
		data_hook['FIGURES'].canvas.regions(
			'setMode',
			'detect',
			{
				clicked_cb: function(index) {
					var objs = data_hook['FIGURES'].canvas.regions('returnObjects');
					data_hook['FIGURES'].regsec.openSection(objs[index].res_id);
				}
			}
			);
	};
	/*=======================================================================*/

	var regionsTabOnLeaveCB = function(data_hook) {
		if (data_hook['FIGURES'] === undefined) return;
		var objs = data_hook['FIGURES'].canvas.regions('returnObjects');
		data_hook['FIGURES'].figures = JSON.stringify(objs);
		data_hook['FIGURES'].canvas.regions('reinit');
	};

	/*=======================================================================*/

	var setDim = function (dim, ele, value) {
		if (typeof value === 'number') {
			if (ele[dim]() == 0) { // dim is either 'height' or 'width' => ele.width() or ele.height()
				ele[dim](value);
				return value;
			}
			else {
				return ele[dim]();
			}
		}
		else if (typeof value === 'string') {
			var tmp = value.split(':');
			if (ele[dim]() == 0) {
				ele[dim](tmp[0]);
				return tmp[0];
			}
			else {
				if ((tmp.length > 1) && (tmp[1] == 'override')) {
					ele[dim](tmp[0]);
					return (tmp[0]);
				}
				else {
					return ele[dim]();
				}
			}
		}
		return false;
	};
	/*=======================================================================*/


	/**
	* This function fills the Metadata area with the image specific information including regions
	*
	* @param {Object} viewer The viewer object
	* @param {Number} winid The window ID
	* @param {Number} res_id The resource ID
	* @param {Object} data The result of an AJAX call to "/ajax/get_resource_context.php"
	* @param {Object} metadata_area_tabs The <div> where the metadata tabs are located within
	* @param {Number} tabid Number of the tab (1 or 2)
	* @param {Boolean} regtab_active if defined, make the regiontabe active
	*/
	var imageMetadataArea = function(viewer, winid, res_id, resinfo, tabid, regtab_active) {
		var canvas = viewer.topCanvas();
		//$.post(SITE_URL +'/app/helper/rdfresedit.php', {winid: winid, resid: res_id, tabid: tabid}, // TO DO, BUT ALREADY IN API DIR
		SALSAH.ApiGet('resources', res_id, function(data2) {
			if (data2.status == ApiErrors.OK) {
				var metadata_area_tabs = viewer.metadataArea();
				var icon = $('<img>', {src: data2.resdata.iconsrc});//.dragndrop('makeDraggable', 'RESID', {resid: res_id});
				var label = $('<div>').append(icon).append(data2.resdata.restype_label);

				metadata_area_tabs.tabs('remove', 'regions'); // remove region tabe (if there is alreay one - we don't want it now)


				//
				// create the tab and add the content to it
				//
				metadata_area_tabs.tabs('setTab', 'image_data', label, function(content_ele) {
					metadataAreaDomCreate(content_ele, data2, {winid: winid, tabid: tabid});
				});

				$('.datafield_' + tabid + '.winid_' + winid).propedit(data2.resdata, data2.props);
				var tabele = metadata_area_tabs.tabs('contentElement', 'image_data');
				tabele.addClass('propedit_frame');
				sectionsetup(tabele, winid);

				tabele.find('.delres').click(
					function(event) {
						if (confirm(strings._delentry)) {
							SALSAH.ApiDelete('resources/' + $(event.target).data('res_id'), function(data) {
								if (data.status == ApiErrors.OK) {
									viewer.destroy();
								}
								else {
									alert(data.errormsg);
								}
							});
						}
					}
				);

				//
				// here we add the region tab and fill it... (if the are regions....)
				//
				RESVIEW.setupRegionTab(resinfo, viewer, regtab_active);
				RESVIEW.transcriptionTab(viewer, res_id, resinfo.transcriptions, true);

			}
			else {
				alert(new Error().lineNumber + ' ' + data2.errormsg);
			}
		});
	};
	/*=======================================================================*/


	/**
	* This function fills the Metadata area with the movie specific information including regions
	*
	* @param {Object} viewer The viewer object
	* @param {Number} winid The window ID
	* @param {Number} res_id The resource ID
	* @param {Object} data The result of an AJAX call to "/ajax/get_resource_context.php"
	* @param {Object} metadata_area_tabs The <div> where the metadata tabs are located within
	* @param {Number} tabid Number of the tab (1 or 2)
	*/
	var movieMetadataArea = function(viewer, winid, res_id, resinfo, tabid) {
		SALSAH.ApiGet('resources', res_id, function(data2) {
			if (data2.status == ApiErrors.OK) {
				var metadata_area_tabs = viewer.metadataArea();
				var icon = $('<img>', {src: data2.resdata.iconsrc}); //.dragndrop('makeDraggable', 'RESID', {resid: res_id});
				var label1 = $('<div>').append(icon).append(data2.resdata.restype_label);

				//
				//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				// here we have to change the sequence tab! equivalent to the region tab
				//
				var label2 = $('<div>').append('SEQUENZ');

				metadata_area_tabs.tabs('setTab', 'movie_data', label1, function(topele) {
					metadataAreaDomCreate(topele, data2, {winid: winid, tabid: tabid});
				});

/*
 * !+!+!+!+!+!+!+!+!!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!
 * GET A LIST OF SEQUENCES
 * Movie Sequence, Show Tab if some sequences exists !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * 2013-11-08 @ akilchenmann: todo todo todo
 *
				metadata_area_tabs.tabs('setTab', 'sequence_data', label2, '<span>content in the activated area</span>');
				var seq = {};
				seq.ele = metadata_area_tabs.tabs('contentElement', 'sequence_data');
*/
				/* sequence-ele.append(........) */

				$('.datafield_' + tabid + '.winid_' + winid).propedit(data2.resdata, data2.props);

				var tabele = metadata_area_tabs.tabs('contentElement', 'movie_data');
				tabele.addClass('propedit_frame');
				sectionsetup(tabele, winid);

				tabele.find('.delres').click(
					function(event) {
						if (confirm(strings._delentry)) {
							SALSAH.ApiDelete('resources/' + $(event.target).data('res_id'), function(data) {
								if (data.status == ApiErrors.OK) {
									viewer.destroy();
								}
								else {
									alert(data.errormsg);
								}
							});
						}
					}
				);

				//
				// here we add the sequence tab and fill it... (if there are sequences....)
				// for movies and audio files; equivalent to the region tab in images
				//
				//RESVIEW.setupSequenceTab(resinfo, viewer);
			}
			else {
				alert(new Error().lineNumber + ' ' + data2.errormsg)
			}
		});
	};
	/*=======================================================================*/


	/**
	* starts drawing of a new figure
	*
	* @param canvas The convas object which is used as overlay
	* @param image_zoom The image zoom object which controls the zooming
	* @param region_action
	* @param figure_add_cb Callback function(figure, index) which saves the figure
	*/
	RESVIEW.figure_drawing = function(viewer, figure_add_cb) {
		//
		// Now we add the 3 icons for selecting a rectangle, polygon or circle to draw
		//
		var canvas = viewer.topCanvas();
		var image_zoom = viewer.imageZoom();
		var region_drawings = $('<div>');
		viewer.getTaskbar().elestack('add', 'region_drawings', region_drawings);

		var active_figure = null;
		var draw_rect_icon;
		var draw_polygon_icon;
		var draw_circle_icon;

		draw_rect_icon = $('<img>', {src: SITE_URL + '/app/icons/DrawRectangleTool24.gif'}).css('cursor', 'pointer').click(
			function(event) {
				var $this = $(this);
				if (active_figure !== null) { // another figure icon has already been clicked and is active ��� but we want to draw another figure! => reset!!
					switch (active_figure) {
						case 'rect': {
							draw_rect_icon.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24.gif');
							canvas.regions('setMode', 'detect', {
								clicked_cb: function(index) {
									canvas.regions('setMode', 'edit', {index: index});
								}
							});
							active_figure = null;
							return;
						}
						case 'polygon': {
							draw_polygon_icon.attr('src', SITE_URL + '/app/icons/DrawPolygonTool24.gif');
							break;
						}
						case 'circle': {
							draw_circle_icon.attr('src', SITE_URL + '/app/icons/DrawEllipseTool24.gif');
							break;
						}
					}
				}
				active_figure = 'rect';
				$this.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24-active.gif');
				image_zoom.disable();
				var params = {
					type: 'rectangle',
					draw_cb: function(figure, index) {
						image_zoom.enable();
						figure_add_cb(figure, index);
						canvas.regions(
							'setMode',
							'detect', {
								clicked_cb: function(index) {
									canvas.regions('setMode', 'edit', {index: index});
								}
							});
						$this.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24.gif');
						active_figure = null;
					}
				};
				canvas.regions('setMode', 'draw', params);
			}
		).appendTo(region_drawings);

		draw_polygon_icon = $('<img>', {src: SITE_URL + '/app/icons/DrawPolygonTool24.gif'}).css('cursor', 'pointer').click(
			function(event){
				var $this = $(this);
				if (active_figure !== null) {
					switch (active_figure) {
						case 'rect': {
							draw_rect_icon.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24.gif');
							break;
						}
						case 'polygon': {
							draw_polygon_icon.attr('src', SITE_URL + '/app/icons/DrawPolygonTool24.gif');
							canvas.regions(
								'setMode',
								'detect', {
									clicked_cb: function(index) {
										canvas.regions('setMode', 'edit', {index: index});
									}
								});
							active_figure = null;
							return;
						}
						case 'circle': {
							draw_circle_icon.attr('src', SITE_URL + '/app/icons/DrawEllipseTool24.gif');
							break;
						}
					}
					$this.attr('src', SITE_URL + '/app/icons/DrawPolygonTool24.gif');
				}
				active_figure = 'polygon';
				$this.attr('src', SITE_URL + '/app/icons/DrawPolygonTool24-active.gif');
				image_zoom.disable();
				var params = {
					type: 'polygon',
					draw_cb: function(figure, index){
						image_zoom.enable();
						figure_add_cb(figure, index);
						canvas.regions(
							'setMode',
							'detect', {
								clicked_cb: function(index) {
									canvas.regions('setMode', 'edit', {index: index});
								}
							});
						$this.attr('src', SITE_URL + '/app/icons/DrawPolygonTool24.gif');
						active_figure = null;
					}
				};
				canvas.regions('setMode', 'draw', params);
			}
		).appendTo(region_drawings);

		draw_circle_icon = $('<img>', {src: SITE_URL + '/app/icons/DrawEllipseTool24.gif'}).css('cursor', 'pointer').click(
			function(event){
				var $this = $(this);
				if (active_figure !== null) {
					switch (active_figure) {
						case 'rect': {
							draw_rect_icon.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24.gif');
							break;
						}
						case 'polygon': {
							draw_polygon_icon.attr('src', SITE_URL + '/app/icons/DrawPolygonTool24.gif');
							break;
						}
						case 'circle': {
							draw_circle_icon.attr('src', SITE_URL + '/app/icons/DrawEllipseTool24.gif');
							canvas.regions(
								'setMode', 'detect', {
									clicked_cb: function(index) {
										canvas.regions('setMode', 'edit', {index: index});
									}
								});
							active_figure = null;
							return;
						}
					}
				}
				active_figure = 'circle';
				$this.attr('src', SITE_URL + '/app/icons/DrawEllipseTool24-active.gif');
				image_zoom.disable();
				var params = {
					type: 'circle',
					draw_cb: function(figure, index){
						image_zoom.enable();
						figure_add_cb(figure, index);
						canvas.regions(
							'setMode',
							'detect',
							{
								clicked_cb: function(index) {
									canvas.regions('setMode', 'edit', {index: index});
								}
							}
							);
						$this.attr('src', SITE_URL + '/app/icons/DrawEllipseTool24.gif');
						active_figure = null;
					}
				};
				canvas.regions('setMode', 'draw', params);
			}
		).appendTo(region_drawings);
	};
	/*=======================================================================*/

	RESVIEW.initQuality = function(viewer, viewer_settings, pic, thumbnail) {
		var taskbar_main = viewer.getTaskbar().elestack('get', 'main');
		taskbar_main.find('.qualityActions').remove();
		var quality_actions = $('<span>').addClass('qualityActions').css({'vertical-align': 'top'});



		taskbar_main.append(quality_actions);


		var quality_selection = $('<select>').addClass('qualityActions').appendTo(quality_actions);

		if (pic === undefined) return;

		quality_selection.on('change', function(e) {
			viewer_settings.q_index = $(this).val();
			viewer.setImageSrc(pic[viewer_settings.q_index].path, thumbnail.path);
		});

		for (var i in pic) {
			if (Math.max(pic[i].nx, pic[i].ny) > 400) {
				if (viewer_settings.q_index == i) {
					quality_selection.append($('<option>').text(pic[i].nx + 'x' + pic[i].ny).val(i).prop({selected: true}));
				}
				else {
					quality_selection.append($('<option>').text(pic[i].nx + 'x' + pic[i].ny).val(i));
				}
			}
		}
	}
	/*=======================================================================*/

	RESVIEW.resetQuality = function(viewer, viewer_settings, pic, thumbnail) {
		var taskbar_main = viewer.getTaskbar().elestack('get', 'main');
		var quality_selection = taskbar_main.find('select.qualityActions');

		quality_selection.empty().on('change', function(e) {
			viewer_settings.q_index = $(this).val();
			viewer.setImageSrc(pic[viewer_settings.q_index].path, thumbnail.path);
		});

		for (var i in pic) {
			if (Math.max(pic[i].nx, pic[i].ny) > 400) {
				if (viewer_settings.q_index == i) {
					quality_selection.append($('<option>').text(pic[i].nx + 'x' + pic[i].ny).val(i).prop({selected: true}));
				}
				else {
					quality_selection.append($('<option>').text(pic[i].nx + 'x' + pic[i].ny).val(i));
				}
			}
		}
	}
	/*=======================================================================*/


	RESVIEW.resetRegionDrawing = function(viewer) {
		var canvas = viewer.topCanvas();
		var metadata_area_tabs = viewer.metadataArea();

		viewer.getTaskbar().elestack('remove', 'region_drawings', 'main'); // reset taskbar to main
		canvas.regions(
			'setMode',
			'detect',
			{
				clicked_cb: function(index) {
					var objs = canvas.regions('returnObjects');
					viewer.regionSection().openSection(objs[index].res_id);
				}
			}
			);
		metadata_area_tabs.tabs('setOnLeaveCB', 'regions', regionsTabOnLeaveCB);
	};
	/*=======================================================================*/


	RESVIEW.initRegionDrawing = function(viewer, res_id) {
		var canvas = viewer.topCanvas();
		var metadata_area_tabs = viewer.metadataArea();

		var taskbar_main = viewer.getTaskbar().elestack('get', 'main');
		taskbar_main.find('.regionActions').remove();
		var region_actions = $('<span>', {'class': 'regionActions'});

		var region_icon = $('<img>', {src: SITE_URL + '/app/icons/24x24/ruler_pencil.png'}).css('cursor', 'pointer').click(
			function(event){
				metadata_area_tabs.tabs(
					'setTab',
					'regions',
					'REGION',
					'',
					{
						setActive: true,
						onEnterCB: regionsTabOnEnterCB,
						onLeaveCB: regionsTabOnLeaveCB
					}
				);

				var image_zoom = viewer.imageZoom();
				var region_area = metadata_area_tabs.tabs('contentElement', 'regions');
				var figures;
				var comment;

				canvas.regions('setObjectStatus', 'inactive');
				//-------------------------------------------------------------
				// Adds the figure to the list in the formular area
				//-------------------------------------------------------------
				var add_figure_to_tab = function(figure, index) {
					var delete_figure_from_list = function(index) {
						var geos;
						canvas.regions('deleteObject', index);
						geos = canvas.regions('returnObjects', 'active');
						figures.find('.figures').remove();
						for (var ii in geos) {
							add_figure_to_tab(geos[ii], ii);
						}

						geos = canvas.regions('returnObjects');
						canvas.regions('reinit', geos);
					};
					//=========================================================

					var s = $('<div>', {'class': 'figures'}).append('Figure ' + String(index) + ' : ' + figure.type).bind(
						'click',
						function() {
							canvas.regions('setMode', 'edit', {index: index});
						}
					);
					s.append(
						$('<img>', {src: SITE_URL + '/app/icons/16x16/delete.png'}).bind(
							'click', function(event) {
								canvas.regions('setMode');
								event.stopPropagation();
								delete_figure_from_list(index);
							}
						)
					);
					figures.append(s);
				};
				//=============================================================

				//
				// Adding a new region: here we start adding the entry form on the right side
				//
				figures = $('<div>', {'class': 'figures'}).css({
					'min-height': '50px',
					'background-color': '#bbb'
				});
				SALSAH.ApiGet('resourcetypes', 'salsah:generic_region', function(data) {
					if (data.status == ApiErrors.OK) {
						region_area.resadd({
							rtinfo: data.restype_info,
							geometry_field: figures,
							viewer: viewer,
							props: [{vocabulary: 'salsah', name: 'region_of', value: res_id}],
							on_cancel_cb: function() {
								SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
									if (data.status == ApiErrors.OK) {
										var context = data.resource_context;
										RESVIEW.setupRegionTab(context.resinfo, viewer);
										if ((context.resinfo.regions !== undefined) && (context.resinfo.regions.length > 0)) {
											regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions'));
										}
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
								canvas.regions('init'); //%%%%
								RESVIEW.resetRegionDrawing(viewer);
							},
							on_submit_cb: function(data) {
								SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
									if (data.status == ApiErrors.OK) {
										RESVIEW.setupRegionTab(data.resource_context.resinfo, viewer);
										regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
								RESVIEW.resetRegionDrawing(viewer);
							},
							on_error_cb: function(data) {
								alert('ERROR: ' + data.errormsg);
								SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
									if (data.status == ApiErrors.OK) {
										RESVIEW.setupRegionTab(data.resource_context.resinfo, viewer);
										regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
								canvas.regions('init');
								RESVIEW.resetRegionDrawing(viewer);
							}
						});
					}
					else {
						alert(data.errormsg);
					}
				});

				//
				// here we add the tab handling. While we are in drawing mode, we should net be able to change the tab. If the
				// user chooses so, he will be asked if he wants to cancel the drawing mode without saving, or return to the
				// tab (that is, not change the tab)
				//
				// This behaviour is implemented using the onLeaveCB of the jquery.tabs.js plugin
				//
				metadata_area_tabs.tabs(
					'setOnLeaveCB',
					'regions',
					function() {
						if (confirm('Wollen Sie das Tab wechseln und den Zeichenvorgang ohne Sichern abbrechen ?') === true) {
							SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
								if (data.status == ApiErrors.OK) {
									RESVIEW.setupRegionTab(data.resource_context.resinfo, viewer);
								}
								else {
									alert(new Error().lineNumber + ' ' + data.errormsg);
								}
							}).always(function() {
								canvas.regions('init');
								RESVIEW.resetRegionDrawing(viewer);
							});
							return true;
						}
						else {
							return false;
						}
					}
				);
				RESVIEW.figure_drawing(viewer, add_figure_to_tab);

			}
		).appendTo(region_actions);

		SALSAH.ApiGet('resources', res_id, {reqtype: 'rights'}, function(data) {
			if (data.status == ApiErrors.OK) {
				if (parseInt(data.rights) >= RESOURCE_ACCESS_EXTEND) {
					taskbar_main.append(region_actions);
				}
			}
			else {
				alert(new Error().lineNumber + ' ' + data.errormsg);
			}
		});

	};
	/*=======================================================================*/


	RESVIEW.initSequence = function(viewer, res_id) {
		var metadata_area_tabs = viewer.metadataArea();

		var taskbar_main = viewer.getTaskbar().elestack('get', 'main');
		taskbar_main.find('.sequenceActions').remove();
		var sequence_actions = $('<span>', {'class': 'sequenceActions'});

		var sequence_icon = $('<img>', {src: SITE_URL + '/app/icons/24x24/sequence.png'}).css('cursor', 'pointer').click(
			function(event){
				metadata_area_tabs.tabs(
					'setTab',
					'sequences',
					'Sequenz',
					'',
					{
						setActive: true,
						onEnterCB: regionsTabOnEnterCB,
						onLeaveCB: regionsTabOnLeaveCB
					}
				);

				var region_area = metadata_area_tabs.tabs('contentElement', 'regions');
				var comment;

				SALSAH.ApGet('resourcetypes/salsah:generic_region', {}, function(data) {
					if (data.status == ApiErrors.OK) {
						region_area.resadd({
							rtinfo: data.restype_info,
							geometry_field: {},
							viewer: viewer,
							props: [{vocabulary: 'salsah', name: 'region_of', value: res_id}],
							on_cancel_cb: function() {
								SALSAH.ApiGet('resources', res_id, {reqtype: 'context', resinfo: true}, function(data) {
									if (data.status == ApiErrors.OK) {
										if ((data.resource_context.resinfo.regions !== undefined) && (data.resource_context.resinfo.regions.length > 0)) {
											regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions'));
										}
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
								canvas.regions('init'); //%%%%
								RESVIEW.resetRegionDrawing(viewer);
							},
							on_submit_cb: function(data) {
								SALSAH.ApiGet('resources', res_id, {reqtype: 'context', resinfo: true}, function(data) {
									if (data.status == ApiErrors.OK) {
										regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
								/* NEW API
								$.__post(SITE_URL + '/ajax/get_resource_context.php', {res_id: res_id, resinfo: true}, function(data) {
									//RESVIEW.setupRegionTab(data.resinfo, viewer);
									regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
								}, 'json');
								*/
								RESVIEW.resetRegionDrawing(viewer);
							},
							on_error_cb: function(data) {
								SALSAH.ApiGet('resources', res_id, {reqtype: 'context', resinfo: true}, function(data) {
									if (data.status == ApiErrors.OK) {
										regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
								/* NEW API
								$.__post(SITE_URL + '/ajax/get_resource_context.php', {res_id: res_id, resinfo: true}, function(data) {
									//RESVIEW.setupRegionTab(data.resinfo, viewer);
									regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
								},'json');
								*/
								//RESVIEW.resetRegionDrawing(viewer);
							}
						});
					}
					else {
						alert(new Error().lineNumber + ' ' + data.errormsg);
					}
				});

				//
				// here we add the tab handling. While we are in drawing mode, we should net be able to change the tab. If the
				// user chooses so, he will be asked if he wants to cancel the drawing mode without saving, or return to the
				// tab (that is, not change the tab)
				//
				// This behaviour is implemented using the onLeaveCB of the jquery.tabs.js plugin
				//
				metadata_area_tabs.tabs(
					'setOnLeaveCB',
					'regions',
					function() {
						if (confirm('Wollen Sie das Tab wechseln und die Sequenzdefinition ohne Sichern abbrechen ?') === true) {
							SALSAH.ApiGet('resources', res_id, {reqtype: 'context', resinfo: true}, function(data) {
								if (data.status == ApiErrors.OK) {
									//RESVIEW.setupRegionTab(data.resinfo, viewer);
								}
								else {
									alert(new Error().lineNumber + ' ' + data.errormsg);
								}
							});
							return true;
						}
						else {
							return false;
						}
					}
				);
			}
		).appendTo(sequence_actions);
		taskbar_main.append(sequence_actions);
	};
	/*=======================================================================*/


	  //
	  // Here comes the transcription code
	  //
	  RESVIEW.transcriptionTab = function(viewer, res_id, transcriptions, make_active) {
		  var canvas = viewer.topCanvas();
		  var metadata_area_tabs = viewer.metadataArea();
		  var image_zoom = viewer.imageZoom();

		  var transcription_area;

		  // delete existing transcr tab first
		  metadata_area_tabs.tabs('remove', 'transcr');
		  // delete transcr ctrls in taskbar
		  viewer.getTaskbar().elestack('remove', 'transcr', 'main');

		  var enable_transcription = function(CB) {
			  var taskbar_main = viewer.getTaskbar().elestack('get', 'main');
			  taskbar_main.find('.transcriptionActions').remove();

			  var active = true; // stores wether tab is active or not
			  metadata_area_tabs.tabs(
				  'setTab',
				  'transcr',
				  'TRANSCR',
				  '',
				  {
					  setActive: make_active,
					  onEnterCB: function() {
						  active = true;
						  viewer.getTaskbar().elestack('show', 'transcr');
						  canvas.regions('reinit', metadata_area_tabs.tabs('dataHook', 'transcr', 'figures'));
						  transcription_area.transcr('redraw').transcr('edit_regions');
					  },
					  onLeaveCB: function() {
						  active = false;
						  viewer.getTaskbar().elestack('show', 'main');
						  metadata_area_tabs.tabs('dataHook', 'transcr', 'figures', canvas.regions('returnObjects'));
						  canvas.regions('reinit');
					  }
				  }
			  );

			  transcription_area = metadata_area_tabs.tabs('contentElement', 'transcr');

			  metadata_area_tabs.tabs('dataHook', 'transcr', 'figures', []);

			  SALSAH.ApiGet('resourcetypes', 'salsah:transcription_area', function(data) {
				  if (data.status == ApiErrors.OK) {
					  // create controls
					  var transcr_ctrl = $('<span>');

					  transcription_area.transcr({canvas: canvas, text_ctrl_area: transcr_ctrl, rtinfo: data.restype_info, resid: res_id});
					  viewer.getTaskbar().elestack('add', 'transcr', transcr_ctrl);

					  var create_region_active = false;
					  var create_region = $('<img>', {src: SITE_URL + '/app/icons/DrawRectangleTool24.gif'}).click(function(event) {
						  if (!create_region_active) {
							  // activate drawing process
							  image_zoom.disable();
							  create_region_active = true;
							  create_region.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24-active.gif');
							  transcription_area.transcr('create_region', function() {
								  image_zoom.enable();
								  create_region.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24.gif');
								  create_region_active = false;
								  transcription_area.transcr('edit_regions');
							  });
						  } else {
							  // there is an active drawing process, deactivate it
							  image_zoom.enable();
							  canvas.regions('setMode');
							  create_region.attr('src', SITE_URL + '/app/icons/DrawRectangleTool24.gif');
							  create_region_active = false;
							  transcription_area.transcr('edit_regions');
						  }
					  });

					  var delete_region = $('<img>', {src: SITE_URL + '/app/icons/24x24/delete.png'}).click(function(event) {
						  var active_region = canvas.regions('getEditIndex');

						  if (active_region === false) {
							  if (canvas.regions('returnObjects').length == 0) {
								  // no regions yet, but transcription aborted by user
								  RESVIEW.transcriptionTab(viewer, res_id);
							  } else {
								  alert('no region is chosen');
							  }
						  } else {
							  transcription_area.transcr('delete_region', active_region);

							  if (canvas.regions('returnObjects').length == 0) {
								  // no regions anymore
								  RESVIEW.transcriptionTab(viewer, res_id);
							  } else {
								  transcription_area.transcr('edit_regions');
							  }
						  }

					  });

					  var save = $('<img>', {src: SITE_URL + '/app/icons/24x24/save.png', 'class': 'save'}).click(function(event) {
						  transcription_area.transcr('save');
						  transcription_area.transcr('edit_regions');
					  });

					  var set_offset_active = false;
					  var set_offset = $('<img>', {src: SITE_URL + '/app/icons/move-24.png', 'class': 'save'}).click(function(event) {
						  var active_region = canvas.regions('getEditIndex');

						  if (active_region === false) {
							  alert('no region is chosen');
						  } else {
							  transcription_area.transcr('trans_field_offset', active_region, !set_offset_active);
							  set_offset_active = !set_offset_active;
							  if (!set_offset_active) {
								  // inactive now
								  set_offset.attr('src', SITE_URL + '/app/icons/move-24.png');
								  transcription_area.transcr('edit_regions');
							  } else {
								  // active now
								  set_offset.attr('src', SITE_URL + '/app/icons/move-24-active.png');
							  }
						  }
					  });

					  var char_table = $('<img>', {src: SITE_URL + '/app/icons/character_set-24.png', 'class': 'save'}).click(function(event) {
						  var charCB = function(character, code) {

							  var textfield = viewer.metadataArea().find('.transcriptionField').texteditor('getActiveEle', viewer.windowId());
							  if (textfield === undefined) {
								  ct.remove();
								  return;
							  }

							  var pos = textfield.texteditor('getUserSelection', viewer.windowId());
							  if (pos === undefined) {
								  ct.remove();
								  return;
							  }

							  pos = pos.start;
							  textfield.texteditor('insertCharAt', character, pos);

							  ct.remove();
						  };

						  var ct = $('<div>').chartable({rowlen: 20, range: {min: 65, max: 591}, clickedCB: charCB}).css({position: 'absolute', left: 0, top: 0, width: '100%', height: '100%'});

						  viewer.contentElement().append(ct);

						  //viewer.appendToControlArea(ct);
					  });

					  var export_trans = $('<div>').text('export').css({display: 'inline'}).on('click', function() {
						  SALSAH.ApiGet('resources', res_id, function(data){
							  if (data.status == ApiErrors.OK) {
								  alertObjectContent(data.resource_info.transcriptions)
							  }
							  else {
								  alert(new Error().lineNumber + ' ' + data.errormsg);
							  }
						  });
					  });

					  transcr_ctrl.append(create_region, delete_region, save, set_offset, char_table, export_trans);

					  if (CB instanceof Function) CB(); // CB is only set in case of existing transcriptions, otherwise it is undefined

					  // set cb for viewer
					  var redrawCB = function() {
						  if (!active) return; // check if tab is active
						  transcription_area.transcr('redraw').transcr('edit_regions');

					  };

					  viewer.whenSizedCB('transcr', redrawCB);
					  viewer.separatorMovingCB('transcr', function(){ transcription_area.transcr('edit_regions');});
					  //viewer.controlAreaResizingCB('transcr', redrawCB);
					  viewer.controlAreaResizingCB('transcr', function(){transcription_area.transcr('edit_regions');});
				  }
				  else {
					  alert(data.errormsg);
				  }
			  });


	  };

	//
	// check for rights to create/modify a transcription
	// transcription is only created if return value is true
	//
	SALSAH.ApiGet('resources', res_id, {reqtype: 'rights'}, function(data) {
		if (data.status == ApiErrors.OK) {
			if (parseInt(data.rights) >= RESOURCE_ACCESS_EXTEND) {
				var taskbar_main;
				if (transcriptions !== undefined) {
					taskbar_main = viewer.getTaskbar().elestack('get', 'main');
					taskbar_main.find('.transcriptionActions').remove();

					// load existing transcriptions
					enable_transcription(function() {
						transcription_area.transcr('load', transcriptions).transcr('edit_regions');
					});

				} else {
					// create icon in taskbar in order to create a transcription

					taskbar_main = viewer.getTaskbar().elestack('get', 'main');
					taskbar_main.find('.transcriptionActions').remove();
					var transcription_actions = $('<span>', {'class': 'transcriptionActions'});

					var transcription_icon = $('<img>', {src: SITE_URL + '/app/icons/24x24/Typewriter.png'}).css('cursor', 'pointer').click(
						function(event){
							enable_transcription();
						}

					).appendTo(transcription_actions);

					taskbar_main.append(transcription_actions);
				}
			} else if (parseInt(data.rights) >= RESOURCE_ACCESS_VIEW) {
				// viewing only


			}
		}
		else {
			alert(new Error().lineNumber + ' ' + data.errormsg);
		}
	});


};


	/*=======================================================================*/

	RESVIEW.initGraph = function(viewer, res_id) {
		var taskbar_main = viewer.getTaskbar().elestack('get', 'main');
		taskbar_main.find('.graphActions').remove();
		var graph_actions = $('<span>', {'class': 'graphActions'});

		var graph_icon = $('<img>', {src: SITE_URL + '/app/icons/graph-icon.png'}).css('cursor', 'pointer').click(function(e) {
			var wp = $('.workwin_content');
			var tabname = 'graph_' + RESVIEW.initGraph.prototype.cnt;
			RESVIEW.initGraph.prototype.cnt++;

			wp.tabs('setTab', tabname, 'GRAPH', '', {
				setActive: true,
				deletable: true,
				onDeleteCB: function(name) {
			}});
			var tab_content = wp.tabs('contentElement', tabname);
			var graph_tb; // graph taskbar
			tab_content.append(graph_tb = $('<div>').css({
				left: '0px',
				top: '0px',
				right: '0px',
				height: '25px',
				'background-color': '#bbb'
			}));
			graph_tb.append($('<input>').attr({type: 'checkbox'}).prop({checked: true}).css({float: 'left'}).on('click', function(event) {
				tab_content.find('.graphview').graph('showAttributes', $(this).prop('checked'));
			}))
			.append($('<div>').css({float: 'left'}).text('Show attributes'))
			.append($('<div>').append('&nbsp;').css({float: 'left', width: '50px'}))
			.append($('<input>').attr({type: 'radio', name: 'mouseaction'}).prop({checked: 'checked'}).css({float: 'left'}).on('click', function(event){
				tab_content.find('.graphview').graph('setMouseAction', 'MOVE');
			}))
			.append($('<div>').css({float: 'left'}).text('move node'))
			.append($('<input>').attr({type: 'radio', name: 'mouseaction'}).css({float: 'left'}).on('click', function(event) {
				tab_content.find('.graphview').graph('setMouseAction', 'NODEACTION');
			}))
			.append($('<div>').css({float: 'left'}).text('collapse/expand node'));
			tab_content.append($('<canvas>').addClass('graphview').attr({
				width: tab_content.innerWidth(),
				height: tab_content.innerHeight() - 26
			}).css({
				width: tab_content.innerWidth(),
				height: tab_content.innerHeight() - 26,
				top: '26px',
				'background-color': '#fff'
			}));
			SALSAH.ApiGet('graphdata', res_id, {}, function(data) {
				if (data.status == ApiErrors.OK) {
					tab_content.find('.graphview').graph({nodes: data.graph.nodes, edges: data.graph.edges});
				}
				else {
					alert(data.errormsg);
				}
			});
		}).appendTo(graph_actions);

		taskbar_main.append(graph_actions);
	};
	/*=======================================================================*/
	RESVIEW.initGraph.prototype.cnt = 0;

	RESVIEW.ObjectViewer = function(append_to, window_options, viewer_options) {
		var window_settings = {};
		$.extend(window_settings, window_options);

		var viewer_settings = {
			type: 'ObjectViewer',
			content_from_url: null, /* {url: url, postdata: {}} */
			res_id: null
		};
		$.extend(viewer_settings, viewer_options);
		var window_html = $('<div>').appendTo(append_to);
		window_html.win(window_settings);
		if (window_html.win('taskbar') !== false) {
			window_html.win('taskbar').elestack().elestack('add', 'main', $('<div>')); // initialize element stack (taskbar is existing!)
		}
		window_html.win('contentElement').addClass('propedit_frame'); // we need this class, because in the object viewer this is the location of the propedits....

		var load_content = function(content_from_url) { // {url: url, postdata: {}}
			if (content_from_url) {
				window_html.win('setBusy');
				content_from_url.postdata.winid = window_html.win('getId'); // add window id automatically !!
				$.post( // content_from_url.url // NO SALSAH.ApiGet here !!!!! We may load anything from any URL here, therefore a generic $.post
					content_from_url.url,
					content_from_url.postdata,
					function(data) {
						window_html.win('content', data).win('unsetBusy');
					},
					'html'
				);
			}
		};

		load_content(viewer_settings.content_from_url);

		if (viewer_settings.res_id) {
			//window_html.win('setBusy');
			SALSAH.ApiGet('resources', viewer_settings.res_id, function(data) {
				if (data.status == ApiErrors.OK) {
					if (data.access == 'NO_ACCESS') {
						alert(strings._err_res_noacess); // comes from LocalAccess::showedit_properties....
						window_html.win('unsetBusy');
					}
					window_html.win('contentElement').css('overflow', 'auto'); // we have to set overlfow=auto to have a scrollbar, if the content does not fit....

					metadataAreaDomCreate(window_html.win('content'), data, {winid: window_html.win('getId')});

					window_html.find('.resicon').dragndrop('makeDraggable', 'RESID', {resid: data.resdata.res_id});
					window_html.find('.delres').click(
						function(event) {
							if (confirm(strings._delentry)) {
								SALSAH.ApiDelete('resources/' + $(event.target).data('res_id'), function(data) {
									if (data.status == ApiErrors.OK) {
										//viewer.destroy(); // viewer is not defined in this scope, rather it is the retobj of this func.
										window_html.win('deleteWindow');
									}
									else {
										alert(new Error().lineNumber + ' ' + data.errormsg);
									}
								});
							}
						}
					);
					$('.datafield.winid_' + window_html.win('getId')).propedit(data.resdata, data.props);
					sectionsetup(window_html.win('contentElement'), window_html.win('getId'));
					window_html.win('unsetBusy');
				}
				else {
					window_html.win('unsetBusy');
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});
		}

		var retobj = {
			windowId: function() {
				return window_html.win('getId');
			},
			setTitle: function(title, iconsrc) {
				window_html.win('title', title, iconsrc);
			},
			setContent: function(content) {
				window_html.win('content', content);
			},
			loadContent: function(content_from_url) {
				viewer_settings.content_from_url = content_from_url;
				load_content(content_from_url);
			},
			setBusy: function() {
				window_html.win('setBusy');
			},
			unsetBusy: function() {
				window_html.win('unsetBusy');
			},
			dataHook: function(data) {
				if (data === undefined) {
					return window_html.win('dataHook');
				}
				else {
					window_html.win('dataHook', data);
				}
				return undefined;
			},
			setupPropeditor: function() {
				sectionsetup(window_html.win('contentElement'), window_html.win('getId'));
			},
			appendToTaskbar: function(html) {
				var window_settings = window_html.win('currentWindowSettings');
				if (window_settings.taskbar) {
					window_html.win('taskbar', html);
				}
				return undefined;
			},
			getTaskbar: function() {
				return window_html.win('taskbar');
			},
			currentWindowSettings: function () {
				var window_settings = window_html.win('currentWindowSettings');
				delete window_settings.content; // must be deleted - would introduce recursion
				return window_settings;
			},
			currentSettings: function() {
				return {
					window_settings: retobj.currentWindowSettings(),
					viewer_settings: viewer_settings
				};
			},
			contentElement: function() {
				return window_html.win('contentElement');
			},
			attachSelf: function(obj) {
				window_html.data('visualisationObject', obj.currentSettings());
				return obj;
			}
		};
		if (window_options.taskbar && (viewer_settings.res_id !== undefined)) {
			RESVIEW.initGraph(retobj, viewer_settings.res_id);
		}
		return retobj.attachSelf(retobj);
	};
	/*=======================================================================*/

	RESVIEW.LinkageWindow = function(append_to, window_options) {
		var window_settings = {
			class_window: ['linkage'],
			title: 'Linkage',
			window_icon: SITE_URL + '/app/icons/16x16/link.png',
			content: '',
			dim_x: 400,
			dim_y: 300,
			taskbar: false,
			overflow_y: 'auto'
		};
		$.extend(window_settings, window_options);

		var viewer_settings = {
			type: 'LinkageWindow'
		};

		var window_html = $('<div>').appendTo(append_to);
		window_html.win(window_settings);
		if (window_html.win('taskbar') !== false) {
			window_html.win('taskbar').elestack().elestack('add', 'main', $('<div>')); // initialize element stack (taskbar is existing!)
		}

		var window_content = window_html.win('contentElement');
		window_content.addClass('propedit_frame');
		window_content.append(strings._drop_here); // Translations !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		var link_dropbox;
		window_content.append(
			link_dropbox = $('<div>', {'class': 'links'}).css({
				'min-height': '50px',
				'background-color': '#bbb'
			}).dragndrop(
				'makeDropable',
				function(event, dropdata) {
					// check for occurrence of this resource in the link items of the linkage window
					if (window_content.find('.links').children('div[data-resid=' + dropdata.resid + ']').length == 0) {
						SALSAH.ApiGet('resources', dropdata.resid, {
							parent_info: true,
							reqtype: 'info'
						}, function(data) {
							if (data.status == ApiErrors.OK) {
								var resinfo = data.resource_info;
								var linkitem = $('<div>', {title: 'remove on click', 'data-resid': dropdata.resid});
								linkitem.append($('<img>', {src: resinfo.restype_iconsrc})).append(' ');
								linkitem.append(resinfo.restype_label + ': ');
								linkitem.append($('<strong>').append(resinfo.firstproperty));
								if (resinfo.parent_res) {
									linkitem.append(' (' + resinfo.parent_res.firstproperty + ')');
								}
								linkitem.click(
									function(event) {
										var conf = confirm('Do you really want to delete this item from the link list?');
										if (conf) $(this).remove();
									}
								);
								link_dropbox.append(linkitem);
							}
							else {
								alert(new Error().lineNumber + ' ' + data.errormsg);
							}
						});
					}
				}
			)
		);
		window_content.append('Comment:<br/>');
		window_content.append($('<textarea>', {rows : 5, cols : 45}).css({marginTop : '10px', marginBottom : '10px'}));
		var submit_button = $('<button>', {id : 'link', type : 'submit'}).text('link items').bind(
			'click',
			function(event) {
				var commentField = window_content.find('textarea').val();
				var resIdsArr = [];
				window_content.find('.links div').each(
					function(index) {
						resIdsArr.push($(this).attr('data-resid'));
					}
				);
				// check if there are at least one items to be linked
				if (resIdsArr.length >= 1) {
					// add the links to the db
					SALSAH.ApiPost('annotations', {
						ids: resIdsArr,
						comment: commentField
					}, function(data) {
						if (data.status == ApiErrors.OK)
						{
							window_html.win('deleteWindow');
						}
						else {
							alert(data.errormsg);
						}
					});
				}
			}
		);
		window_content.append(submit_button);
	};
	/*=======================================================================*/

	RESVIEW.AddResourceWindow = function(append_to, window_options) {
		var window_settings = {
			class_window: ['addresource'],
			title: 'AddResource',
			window_icon: SITE_URL + '/app/icons/16x16/add.png',
			content: '',
			dim_x: 700,
			dim_y: 600
		};
		$.extend(window_settings, window_options);

		var viewer_settings = {
			type: 'AddResourceWindow'
		};

		var window_html = $('<div>').css({'overflow': 'auto'}).appendTo(append_to);
		window_html.win(window_settings);
		if (window_html.win('taskbar') !== false) {
			window_html.win('taskbar').elestack().elestack('add', 'main', $('<div>')); // initialize element stack (taskbar is existing!)
		}

		var window_content = window_html.win('contentElement');
		window_content.css('overflow', 'auto').addClass('propedit_frame'); // in case of AddResourceWindow, the propedit_frame class has to be added here, because this contains the propedit location
		window_content.resadd(
		{
			on_submit_cb: function(data) {
				window_html.win('deleteWindow');
				RESVIEW.new_resource_editor(data.res_id, 'NEW RESOURCE');
			},
			on_cancel_cb: function() {
				window_html.win('deleteWindow');
			},
			on_error_cb: function(data) {
				alert(data.errormsg);
				window_html.win('deleteWindow');
			}
		}
		);
	};

	/** ------------------------------------------------------------------------
	* Constructor function of the imageBase object
	*/
	RESVIEW.ImageBase = function(append_to, window_options, viewer_options) {
		var window_settings = {
			class_window: ['win', 'imageBase'],
			title: 'This is ImageBase...',
			fullscreen: false
		};
		$.extend(window_settings, window_options);

		var viewer_settings = {
			type: 'ImageBase',
			left_image_area: true,
			imgctrl_area: false,
			imgctrl_area_height: 16,
			metadata_area_width: 250,
			annotate_area_minwidth: 150,
			image_area_minwidth: 50,
			slider_width: 15,
			max_zoom: 2.0,
			controlAreaResizingCB: {}, // init callback with an emtpy object
			whenSizedCB: {},
			separatorMovingCB: {}
		};
		$.extend(viewer_settings, viewer_options);

		var window_html;
		var content = {};
		var left_area = {};
		var separator_area = {};
		var right_area = {};
		var control_area = {};
		var click_handle = {};
		var navigator_area = {};
		var zoom_slider = {};
		var image = {};
		var save_areadata = {};
		var image_zoom;
		var navigator_map = {};
		var navigator_maprect = {};
		var navigator_handle = {};
		var navigator_show = {};
		var canvas = {};
		var region_section;

		navigator_map.init = function(src) {
			//
			// here we load the thumbnail image for the navigator map!
			//
			navigator_map.ele.attr('src', src).on('load',
				function() {
					//
					// now we have to scale it
					//
					var nav_width = navigator_area.ele.width() - zoom_slider.ele.width();
					var nav_height = navigator_area.ele.height() - navigator_handle.ele.outerHeight();
					if (($(this).width() / $(this).height()) > (nav_width / nav_height)) {
						// adjust width
						$(this).width(nav_width);
						navigator_map.width = nav_width;
						navigator_map.height = $(this).height();
						navigator_map.left_offset = 0;
						navigator_map.top_offset = parseInt((nav_height - navigator_map.height)/2.0 + 0.5) + navigator_handle.ele.outerHeight();
					}
					else {
						// adjust height
						$(this).height(nav_height);
						navigator_map.height = nav_height;
						navigator_map.width = $(this).width();
						navigator_map.left_offset = parseInt((nav_width - navigator_map.width)/2.0 + 0.5);
						navigator_map.top_offset = navigator_handle.ele.outerHeight();
					}
					navigator_map.ele.css({left: navigator_map.left_offset + 'px', top:  navigator_map.top_offset + 'px'});
					navigator_maprect.adjust();
				}
			);
		};
		/*=======================================================================*/

		navigator_maprect.adjust = function() {
			var img_w = image.ele.width();
			var img_h = image.ele.height();
			if (viewer_settings.left_image_area) { // image is to the left
				this.w = parseInt(navigator_map.width*left_area.ele.width()/img_w + 0.5) - 2;
				this.h = parseInt(navigator_map.height*left_area.ele.height()/img_h + 0.5) - 2;
			}
			else {
				this.w = parseInt(navigator_map.width*right_area.ele.width()/img_w + 0.5) - 2;
				this.h = parseInt(navigator_map.height*right_area.ele.height()/img_h + 0.5) - 2;
			}
			var offs = image_zoom.getImgOffs();
			if (offs.x > 0) {
				this.l = navigator_map.left_offset;
				this.w = this.w - parseInt(offs.x*navigator_map.width/img_w + 0.5);
			}
			else {
				this.l = parseInt(navigator_map.left_offset - offs.x*navigator_map.width/img_w + 0.5);
			}
			if (offs.y > 0) {
				this.t = navigator_map.top_offset;
				this.h = this.h - parseInt(offs.y*navigator_map.height/img_h + 0.5);
			}
			else {
				this.t = parseInt(navigator_map.top_offset - offs.y*navigator_map.height/img_h + 0.5);
			}

			if ((this.l + this.w - navigator_map.left_offset) > (navigator_map.width - 2)) {
				this.w = navigator_map.width - 2;
			}
			if ((this.t + this.h) > (navigator_map.height +  navigator_map.top_offset - 2)) {
				this.h = navigator_map.height - 2;
			}
			this.ele.css({left: this.l + 'px', top: this.t + 'px', width: this.w + 'px', height: this.h + 'px'});
		};
		/*=======================================================================*/

		navigator_maprect.moving = function(dx, dy) {
			var img_w = image.ele.width();
			var img_h = image.ele.height();
			if (((this.l + dx) >= navigator_map.left_offset) && ((this.l + dx + this.w + 1) < (navigator_map.width + navigator_map.left_offset))) {
				this.l = this.l + dx;
			}
			else {
				dx = 0;
			}
			if (((this.t + dy) >= navigator_map.top_offset) && ((this.t + dy + this.h + 1) < (navigator_map.height + navigator_map.top_offset))) {
				this.t = this.t + dy;
			}
			else {
				dy = 0;
			}
			if ((dx != 0) || (dy != 0)) {
				this.ele.css({left: this.l + 'px', top: this.t + 'px'});
				image_zoom.pan(parseInt(-dx*img_w/navigator_map.width), parseInt(-dy*img_h/navigator_map.height));
			}
		};
		/*=======================================================================*/


		/** ------------------------------------------------------------------------
		* This is the callback function which is called when the window is being resized
		*/
		var whenSized = function(dx, dy) {
			if (separator_area.ele.css('display') == 'none') { // metadata area hidden
				if (viewer_settings.left_image_area) { // image is to the left
					if ((content.w + dx ) < (save_areadata.right_area_w + save_areadata.separator_area_w + viewer_settings.image_area_minwidth)) {
						save_areadata.right_area_w= save_areadata.right_area_w + dx;
						save_areadata.separator_area_r = save_areadata.separator_area_r + dx;
					}
					else {
						save_areadata.left_area_w = save_areadata.left_area_w + dx;
					}
				}
				else {
					if ((content.w + dx ) < (save_areadata.left_area_w + save_areadata.separator_area_w + viewer_settings.image_area_minwidth)) {
						save_areadata.left_area_w = save_areadata.left_area_w + dx;
						save_areadata.separator_area_l = save_areadata.separator_area_l + dx;
					}
					else {
						save_areadata.right_area_w = save_areadata.right_area_w + dx;
					}
				}
			}
			else {
				if (viewer_settings.left_image_area) { // image is to the left
					if ((content.w + dx ) < (right_area.w + separator_area.w + viewer_settings.image_area_minwidth)) {
						right_area.w = right_area.w + dx;
						right_area.ele.css('width', right_area.w + 'px');
						separator_area.r = separator_area.r + dx;
						separator_area.ele.css('right', separator_area.r + 'px');
						click_handle.ele.css('right', separator_area.r + 'px');
					}
				}
				else {
					if ((content.w + dx ) < (left_area.w + separator_area.w + viewer_settings.image_area_minwidth)) {
						left_area.w = left_area.w + dx;
						left_area.ele.css('width', left_area.w + 'px');
						separator_area.l = separator_area.l + dx;
						separator_area.ele.css('left', separator_area.l + 'px');
						click_handle.ele.css('left', separator_area.l + 'px');
					}
				}
			}
			content.w = content.w + dx;
			content.h = content.h + dy;
			setAreaSizes();
			if (viewer_settings.left_image_area) { // image is to the left
				image_zoom.resize(left_area.ele.width(), left_area.ele.height());
			}
			else {
				image_zoom.resize(right_area.ele.width(), right_area.ele.height());
			}
			var min_zoom = image_zoom.getSmallestZoomFac();
			var zf = image_zoom.getZoomFac();
			zoom_slider.ele.slider('setRange', min_zoom, viewer_settings.max_zoom).slider('setValue', zf);
			navigator_maprect.adjust();

			//
			// execute callback function if given
			//
			var cb_index;
			for (cb_index in viewer_settings.controlAreaResizingCB) {
				if ((viewer_settings.controlAreaResizingCB[cb_index] instanceof Function) && viewer_settings.imgctrl_area) viewer_settings.controlAreaResizingCB[cb_index](control_area.ele.width());
			}

			for (cb_index in viewer_settings.whenSizedCB) {
				if (viewer_settings.whenSizedCB[cb_index] instanceof Function) viewer_settings.whenSizedCB[cb_index](dx, dy);
			}
		};
		/*=======================================================================*/


		/** ------------------------------------------------------------------------
		* This is the callback function which is called when the window is being moved
		*/
		var whenMoved = function() {
			image_zoom.reset();
		};
		/*=======================================================================*/


		/** ------------------------------------------------------------------------
		* This is the callback function which is called when the separator is being moved
		*/
		var separatorMoving = function(dx) {
			if (viewer_settings.left_image_area) { // image is to the left
				if (((left_area.w + dx) < viewer_settings.image_area_minwidth) && (dx < 0)) return;
			}
			else {
				if (((right_area.w + dx) < viewer_settings.image_area_minwidth) && (dx > 0)) return;
			}
			left_area.w = left_area.w + dx;
			right_area.w = right_area.w - dx;
			if (viewer_settings.left_image_area) { // image is to the left
				separator_area.r = separator_area.r - dx;
				separator_area.ele.css({right: separator_area.r});
				click_handle.ele.css({right: separator_area.r});
				if (viewer_settings.imgctrl_area) {
					control_area.ele.width(left_area.w);
				}
			}
			else {
				separator_area.l = separator_area.l + dx;
				separator_area.ele.css('left', separator_area.l + 'px');
				click_handle.ele.css('left', separator_area.l + 'px');
				if (viewer_settings.imgctrl_area) {
					control_area.ele.width(right_area.w);
				}
			}
			left_area.ele.css('width', left_area.w + 'px');
			right_area.ele.css('width', right_area.w + 'px');
			if (viewer_settings.left_image_area) { // image is to the left
				image_zoom.resize(left_area.ele.width(), left_area.ele.height());
			}
			else {
				image_zoom.resize(right_area.ele.width(), right_area.ele.height());
			}
			image_zoom.reset();
			navigator_maprect.adjust();

			for (var cb_index in viewer_settings.controlAreaResizingCB) {
				if ((viewer_settings.controlAreaResizingCB[cb_index] instanceof Function) && viewer_settings.imgctrl_area) viewer_settings.controlAreaResizingCB[cb_index](control_area.ele.width());
			}

			for (var cb_index in viewer_settings.separatorMovingCB) {
				if (viewer_settings.separatorMovingCB[cb_index] instanceof Function) viewer_settings.separatorMovingCB[cb_index]();
			}
		};
		/*=======================================================================*/

		/** ------------------------------------------------------------------------
		* This function adapts the areas to the resizing of the window
		*/
		var setAreaSizes = function() {
			if (viewer_settings.left_image_area) { // image is to the left
				left_area.w = content.w - separator_area.w - right_area.w;
				left_area.ele.css('width', left_area.w + 'px');
				if (viewer_settings.imgctrl_area) {
					control_area.ele.css('width', left_area.w + 'px');
				}
			}
			else {
				right_area.w = content.w - separator_area.w - left_area.w;
				right_area.ele.css('width', right_area.w + 'px');
				if (viewer_settings.imgctrl_area) {
					control_area.ele.css('width', right_area.w + 'px');
				}
			}
		};
		/*=======================================================================*/

		/** ------------------------------------------------------------------------
		* This function toggles the visibility of the metadata area
		*/
		var toggleMetadataArea = function() {

			var adapt_zoomer = function() {
				if (viewer_settings.left_image_area) { // image is to the left
					image_zoom.resize(left_area.ele.width(), left_area.ele.height());
				}
				else {
					image_zoom.resize(right_area.ele.width(), right_area.ele.height());
				}
				image_zoom.reset();
				navigator_maprect.adjust();
			};

			if (separator_area.ele.css('display') == 'block') { // hide metadata area
				save_areadata.left_area_w = left_area.w;
				save_areadata.right_area_w = right_area.w;
				save_areadata.separator_area_w = separator_area.w;
				if (viewer_settings.imgctrl_area) {
					control_area.ele.animate({width: content.w});
				}
				if (viewer_settings.left_image_area) { // image is to the left
					save_areadata.separator_area_r = separator_area.r;
					right_area.ele.animate({right: -right_area.w});
					left_area.ele.animate({width: content.w});
					separator_area.ele.animate({right: -separator_area.w});
					click_handle.ele.animate(
						{right: 0},
						function() {
							separator_area.ele.css('display', 'none');
							separator_area.r = 0;
							separator_area.w = 0;
							left_area.w = content.w;
							right_area.ele.css('display', 'none');
							right_area.w = 0;
//							click_handle.ele.text('<');
							click_handle.ele.toggleClass('right left');
							adapt_zoomer();

							for (var cb_index in viewer_settings.controlAreaResizingCB) {
								if ((viewer_settings.controlAreaResizingCB[cb_index] instanceof Function) && viewer_settings.imgctrl_area) viewer_settings.controlAreaResizingCB[cb_index](control_area.ele.width());
							}
						}
					);
				}
				else {
					save_areadata.separator_area_l = separator_area.l;
					left_area.ele.animate({left: -left_area.w});
					right_area.ele.animate({width: content.w});
					separator_area.ele.animate({left: -separator_area.w});
					click_handle.ele.animate(
						{left: 0},
						function() {
							separator_area.ele.css('display', 'none');
							separator_area.l = 0;
							separator_area.w = 0;
							left_area.w = 0;
							left_area.ele.css('display', 'none');
							right_area.w = content.w;
//							click_handle.ele.text('>');
							click_handle.ele.toggleClass('left right');
							adapt_zoomer();

							for (var cb_index in viewer_settings.controlAreaResizingCB) {
								if ((viewer_settings.controlAreaResizingCB[cb_index] instanceof Function) && viewer_settings.imgctrl_area) viewer_settings.controlAreaResizingCB[cb_index](control_area.ele.width());
							}
						}
					);
				}
			}
			else { // show annotation area
				if (viewer_settings.left_image_area) { // image is to the left
					right_area.ele.css({display: 'block', width: save_areadata.right_area_w});
					right_area.ele.animate({right: 0});
					left_area.ele.animate({width: save_areadata.left_area_w});
					if (viewer_settings.imgctrl_area) {
						control_area.ele.animate({width: save_areadata.left_area_w});
					}
					separator_area.ele.css({display: 'block', width: save_areadata.separator_area_w});
					separator_area.ele.animate({right: save_areadata.separator_area_r});
					click_handle.ele.animate(
						{right: save_areadata.separator_area_r},
						function() {
							separator_area.r = save_areadata.separator_area_r;
							left_area.w = save_areadata.left_area_w;
							right_area.w = save_areadata.right_area_w;
							separator_area.w = save_areadata.separator_area_w;
//							click_handle.ele.text('>');
							click_handle.ele.toggleClass('left right');
							adapt_zoomer();

							for (var cb_index in viewer_settings.controlAreaResizingCB) {
								if ((viewer_settings.controlAreaResizingCB[cb_index] instanceof Function) && viewer_settings.imgctrl_area) viewer_settings.controlAreaResizingCB[cb_index](control_area.ele.width());
							}
						}
					);
				}
				else {
					left_area.ele.css({display: 'block', width: save_areadata.left_area_w});
					left_area.ele.animate({left: 0});
					right_area.ele.animate({width: save_areadata.right_area_w});
					if (viewer_settings.imgctrl_area) {
						control_area.ele.animate({width: save_areadata.right_area_w});
					}
					separator_area.ele.css({display: 'block', width: save_areadata.separator_area_w});
					separator_area.ele.animate({left: save_areadata.separator_area_l});
					click_handle.ele.animate(
						{left: save_areadata.separator_area_l},
						function() {
							separator_area.l = save_areadata.separator_area_l;
							left_area.w = save_areadata.left_area_w;
							right_area.w = save_areadata.right_area_w;
							separator_area.w = save_areadata.separator_area_w;
//							click_handle.ele.text('<');
							click_handle.ele.toggleClass('right left');
							adapt_zoomer();

							for (var cb_index in viewer_settings.controlAreaResizingCB) {
								if ((viewer_settings.controlAreaResizingCB[cb_index] instanceof Function) && viewer_settings.imgctrl_area) viewer_settings.controlAreaResizingCB[cb_index](control_area.ele.width());
							}
						}
					);
				}
			}
		};
		/*=======================================================================*/

		/** ------------------------------------------------------------------------
		* This function handles the movement of the navigator area
		*/
		var navigatorMoving = function(dx, dy) {
		var bottom = parseInt(navigator_area.ele.css('bottom'));
		if (bottom < 0) bottom = 0;
		if (viewer_settings.left_image_area) { // image is to the left
			if ((bottom + navigator_area.ele.height()) > left_area.ele.height()) bottom = left_area.ele.height() - navigator_area.ele.height();
		}
		else {
			if ((bottom + navigator_area.ele.height()) > right_area.ele.height()) bottom = right_area.ele.height() - navigator_area.ele.height();
		}
		var left = parseInt(navigator_area.ele.css('left'));
		if (left < 0) left = 0;
			if (viewer_settings.left_image_area) { // image is to the left
				if ((left + navigator_area.ele.width()) > left_area.w) left = left_area.w - navigator_area.ele.width();
			}
			else {
				if ((left + navigator_area.ele.width()) > right_area.w) left = right_area.w - navigator_area.ele.width();
			}
			navigator_area.ele.css({bottom: bottom + dy + 'px', left: left + dx + 'px'});
		};
		/*=======================================================================*/

		/*-----------------------------------------------------------------------*/
		//
		// let's create the window object
		//
		window_html = $('<div>').appendTo(append_to);
		/*
		if (window_settings.tab_window_object) {
			window_html.addClass('win');
			window_html.addClass('contentTaskbar');
			window_object = tab_window_object(window_html, window_settings);
		}
		else {
			window_html.win(window_settings);
		}
		*/
		window_html.win(window_settings);
		if (window_html.win('taskbar') !== false) {
			window_html.win('taskbar').elestack().elestack('add', 'main', $('<div>')); // initialize element stack (taskbar is existing!)
		}

		content.ele = window_html.win('contentElement');
		content.ele.empty();
		content.w = content.ele.width();
		content.h = content.ele.height();
		if (viewer_settings.left_image_area) { // image is to the left
			//
			// create the left (=image) area
			//
			if (viewer_settings.imgctrl_area) {
				left_area.ele = $('<div>', {'class': 'leftArea'}).css({overflow: 'hidden', position: 'absolute', left: 0, top:0, bottom: viewer_settings.imgctrl_area_height + 'px'}).appendTo(content.ele);
				control_area.ele = $('<div>', {'class': 'controlArea'}).css({overflow: 'visible', position: 'absolute', left: 0, height: viewer_settings.imgctrl_area_height + 'px', bottom: 0}).appendTo(content.ele);
				//
				// we have to get the height of the control area in the following priority:
				// 1) if the "viewer_settings.imgctrl_area_height" option has a ":override"-Flag, we take this value (e.g. '77:override')
				// 2) the value as given in the external CSS (window_surplus.css)
				// 3) from the "viewer_settings.imgctrl_area_height" -option (either the default or the one given when creating the viewer)
				//
				var h = setDim('height', control_area.ele, viewer_settings.imgctrl_area_height);
				left_area.ele.css('bottom', h + 'px');
			}
			else {
				left_area.ele = $('<div>').addClass('leftArea').css({overflow: 'hidden', position: 'absolute', left: 0, top:0, bottom: 0}).appendTo(content.ele);
			}

			//
			// create the separator
			//
			separator_area.ele = $('<div>').addClass('separatorArea').css({position: 'absolute', top: 0, bottom: 0}).appendTo(content.ele);

			//
			// create the right (=metadata area)
			//
			right_area.ele = $('<div>', {'class': 'rightArea'}).css({overflow: 'hidden', position: 'absolute', right: 0, top: 0, bottom: 0}).appendTo(content.ele);
			right_area.w = viewer_settings.metadata_area_width;
			right_area.ele.css('width', right_area.w + 'px');

			//
			// prepare tabs
			//
			right_area.ele.tabs();

			separator_area.r = right_area.w;
			separator_area.ele.css('right', separator_area.r + 'px');
//			click_handle.ele = $('<div>', {'class': 'il_clickhandle'}).css({position: 'absolute', right: separator_area.r + 'px'}).text('>').appendTo(content.ele);
			click_handle.ele = $('<div>').addClass('il_clickhandle ml right').css({position: 'absolute', right: separator_area.r + 'px'}).appendTo(content.ele);
			image.ele = $('<img>').appendTo(left_area.ele);
			canvas.ele1 = $('<canvas>').appendTo(left_area.ele);
			canvas.ele2 = $('<canvas>').appendTo(left_area.ele);
			//
			// create the navigator area
			//
			navigator_area.ele = $('<div>', {'class': 'navigatorArea'}).css({position: 'absolute'}).appendTo(left_area.ele);
		}
		else {
			//
			// create the left (=metadata) area
			//
			left_area.ele = $('<div>', {'class': 'leftArea'}).css({overflow: 'hidden', position: 'absolute', left: 0, top:0, bottom: 0}).appendTo(content.ele);

			//
			// create the separator
			//
			separator_area.ele = $('<div>').addClass('separatorArea').css({position: 'absolute', top: 0, bottom: 0}).appendTo(content.ele);
			if (viewer_settings.imgctrl_area) {
				right_area.ele = $('<div>', {'class': 'rightArea'}).css({overflow: 'hidden', position: 'absolute', right: 0, top: 0}).appendTo(content.ele);
				control_area.ele = $('<div>', {'class': 'controlArea'}).css({overflow: 'visible', position: 'absolute', right: 0, bottom: 0}).appendTo(content.ele);
				//
				// we have to get the height of the control area in the following priority:
				// 1) if the "viewer_settings.imgctrl_area_height" option has a ":override"-Flag, we take this value (e.g. '77:override')
				// 2) the value as given in the external CSS (window_surplus.css)
				// 3) from the "viewer_settings.imgctrl_area_height" -option (either the default or the one given when creating the viewer)
				//
				var h = setDim('height', control_area.ele, viewer_settings.imgctrl_area_height);
				right_area.ele.css('bottom', h);
			}
			else {
				right_area.ele = $('<div>', {'class': 'rightArea'}).css({overflow: 'hidden', position: 'absolute', right: 0, top: 0, bottom: 0}).appendTo(content.ele);
			}

			//
			// create the right (=image) area
			//
			left_area.w = viewer_settings.metadata_area_width;
			left_area.ele.css('width', left_area.w + 'px');

			//
			// prepare tabs
			//
			left_area.ele.tabs();

			separator_area.l = left_area.w;
			separator_area.ele.css('left', separator_area.l + 'px');
//			click_handle.ele = $('<div>', {'class': 'ir_clickhandle'}).css({position: 'absolute', left: separator_area.l + 'px'}).text('<').appendTo(content.ele);
			click_handle.ele = $('<div>').addClass('ir_clickhandle ml left').css({position: 'absolute', left: separator_area.l + 'px'}).appendTo(content.ele);
			image.ele = $('<img>').appendTo(right_area.ele);
			canvas.ele1 = $('<canvas>').appendTo(right_area.ele);
			canvas.ele2 = $('<canvas>').appendTo(right_area.ele);

			//
			// create the navigator area
			//
			navigator_area.ele = $('<div>', {'class': 'navigatorArea'}).css({position: 'absolute'}).appendTo(right_area.ele);
		}
		separator_area.w = separator_area.ele.outerWidth(true);

		if (viewer_settings.left_image_area) { // image is to the left
			navigator_show.ele = $('<div>', {'class': 'navigatorShow'}).appendTo(left_area.ele).mousedown(
				function(event) {
					//                          event.stopPropagation();
					event.preventDefault();
					navigator_area.ele.css('display', 'block');
					$(this).css('display', 'none');
				}
			);
		}
		else {
			navigator_show.ele = $('<div>', {'class': 'navigatorShow'}).appendTo(right_area.ele).mousedown(
				function(event) {
					//                          event.stopPropagation();
					event.preventDefault();
					navigator_area.ele.css('display', 'block');
					$(this).css('display', 'none');
				}
			);
		}
		navigator_handle.ele = $('<div>', {'class': 'navigatorHandle'}).appendTo(navigator_area.ele);
		$('<div>', {'class': 'navigatorMinimize'}).appendTo(navigator_handle.ele).mousedown(
			function(event) {
				navigator_area.ele.css('display', 'none');
				navigator_show.ele.css('display', 'block');
			}
		);

		//
		// add a vertical zoom slider to the navogator area
		//
		zoom_slider.ele = $('<div>', {'class': 'slider'}).css({
			position: 'absolute',
			top: navigator_handle.ele.outerHeight(),
			right: 0,
			bottom: 0,
			min: 0,
			max: 100
		}).appendTo(navigator_area.ele).slider({
			horizontal: false,
			incstep: 0.2
		});
		if (zoom_slider.ele.width() == 0) { // there's no width from the external CSS-files!
			//zoom_slider.ele.css('width', localdata.settings.slider_width + 'px'); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! woher kommt localdata??
		}

		navigator_map.ele = $('<img>').css({position: 'absolute'}).appendTo(navigator_area.ele);

		navigator_maprect.ele = $('<div>', {'class': 'mapRect'}).css({position: 'absolute'}).appendTo(navigator_area.ele);

		setAreaSizes();


		/*
		* Here are the event handlers
		*/
		//
		// moving the separator between left- and right area
		//
		separator_area.ele.mousedown(
			function(event) {
				// event.stopPropagation();
				event.preventDefault();
				var pos_x = event.pageX;
				$('#ecatcher').css({display: 'block'}).mousemove(
					function(event) {
						event.stopPropagation();
						event.preventDefault();
						if (((left_area.w + (event.pageX - pos_x)) <= 0) || ((right_area.w - (event.pageX - pos_x)) <= 0)) return;
						separatorMoving(event.pageX - pos_x);
						pos_x = event.pageX;
					}
				).mouseup(
					function() {
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				).mouseout(
					function(){
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				);
			}
		);
		click_handle.ele.click(
			function() {
				toggleMetadataArea();
			}
		);

		//
		// moving the navigator window around...
		//
		navigator_area.ele.mousedown(
			function(event) {
				//                      event.stopPropagation();
				event.preventDefault();
				var pos_x = event.pageX, pos_y = event.pageY;
				$('#ecatcher').css({display: 'block'}).mousemove(
					function(event) {
						event.stopPropagation();
						event.preventDefault();
						navigatorMoving(event.pageX - pos_x, pos_y - event.pageY);
						pos_x = event.pageX;
						pos_y = event.pageY;
					}
				).mouseup(
					function() {
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				).mouseout(
					function(){
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				);
			}
		);

		//
		// clicking and moving of rectangle showing the visible part of the image
		//
		navigator_maprect.ele.mousedown(
			function(event){
				event.stopPropagation(); // prevent other events (e.g. moving of image) from firings
				event.preventDefault();
				var pos_x = event.pageX, pos_y = event.pageY;
				$('#ecatcher').css({display: 'block'}).mousemove(
					function(event) {
						event.stopPropagation();
						event.preventDefault();
						navigator_maprect.moving(event.pageX - pos_x, event.pageY - pos_y);
						pos_x = event.pageX;
						pos_y = event.pageY;
					}
				).mouseup(
					function() {
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				).mouseout(
					function(){
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				);
			}
		);

		/**
		 * This is the "object" that is returned by the constructor of RESVIEW.ImageBase
		 */

		 var retobj = {
			 viewerType: 'ImageBase',
			whenSized: function(dx, dy) { whenSized(dx, dy); }, // EXPORT To CHILDREN
			whenMoved: function(dx, dy) { whenMoved(dx, dy); }, // EXPORT To CHILDREN
			appendHtmlBase: function(html, toLeft) {
				if (toLeft) {
					left_area.ele.html(html);
				}
				else {
					right_area.ele.html(html);
				}
			},

			setMetadataAreaContent: function(content) {
				if (viewer_settings.left_image_area) { // image is to the left
					right_area.ele.empty().append(content);
				}
				else {
					left_area.ele.ele.empty().append(content);
				}
			},

			metadataArea: function() {
				if (viewer_settings.left_image_area) { // image is to the left
					return right_area.ele;
				}
				else {
					return left_area.ele;
				}
			},
			//
			// add a new image url
			//
			setImageSrc: function(src, thumb_src, loadedCB) {
				window_html.win('setBusy');
				var timer = window.setTimeout(
					function() {
						alert('There is a problem loading the image "' + src + '"');
						window_html.win('unsetBusy');
					},
					50000
				);
				image.ele.on('load',
					function (event){
						window.clearTimeout(timer);
						window_html.win('unsetBusy');
						image.ele.unbind(event);

						if ((loadedCB) && (loadedCB instanceof Function)) {
							loadedCB();
						}
					}
				);
				image.src = src;
				image.ele.attr({src: src});
				if (viewer_settings.left_image_area) { // image is to the left
					//
					// create and activate the image zoomer
					//
					if (image_zoom !== undefined) delete image_zoom;
					image_zoom = left_area.ele.imagezoom({
						whenLoaded: function(minzoom){ // callback when an an image has been loaded
							zoom_slider.ele.slider('setRange', minzoom, viewer_settings.max_zoom).slider('setBalloonHtml', parseInt(minzoom*100) + '%');
							navigator_map.init(thumb_src === undefined ? image.src : thumb_src);
							canvas.ele2.regions('redrawObjects');
							canvas.ele2.regions(
								'setMode',
								'detect',
								{
									clicked_cb: function(index) {
										var objs = canvas.ele2.regions('returnObjects');
										if (region_section !== undefined) region_section.openSection(objs[index].res_id);
										// !!!!!!!TEMPORARY SOLUTION!!!!!!! -> this callback is region editing specific and should be set in the corresponding func.
									}/*
									  hover_cb: function(event, index) {
									  var objs = canvas.ele2.regions('returnObjects');
									  region_section.openSection(objs[index].res_id);
									},*/
								}
							);
						},
						whenZoomed: function(zf) { // callback when zoom changed through mousewheel
							zoom_slider.ele.slider('setValue', zf);
							navigator_maprect.adjust();
							canvas.ele2.regions('redrawObjects', true);
						},
						whenPanned: function(img_offs_x, img_offs_y){
							navigator_maprect.adjust();
							canvas.ele2.regions('redrawObjects', true);
						},
						whenPanFinished: function() {
						}
					});
				}
				else {
					//
					// create and activate the image zoomer
					//
					if (image_zoom !== undefined) delete image_zoom;
					image_zoom = right_area.ele.imagezoom({
						whenLoaded: function(minzoom){ // callback when an an imge has been loaded, init zoom slider including slider balloon
							zoom_slider.ele.slider('setRange', minzoom, 2.0).slider('setBalloonHtml', parseInt(minzoom*100) + '%');
							navigator_map.init(thumb_src === undefined ? image.src : thumb_src);
							canvas.ele2.regions('redrawObjects');
							canvas.ele2.regions(
								'setMode',
								'detect', {
									clicked_cb: function(index) {
										var objs = canvas.ele2.regions('returnObjects');
										if (region_section !== undefined) region_section.openSection(objs[index].res_id);
										// !!!!!!!TEMPORARY SOLUTION!!!!!!! -> this callback is region editing specific and should be set in the corresponding func.
									}/*
									  hover_cb: function(event, index) {
									  var objs = canvas.ele2.regions('returnObjects');
									  region_section.openSection(objs[index].res_id);
									},*/
								}
							);
						},
						whenZoomed: function(zf) { // callback when zoom changed through mousewheel
							zoom_slider.ele.slider('setValue', zf);
							navigator_maprect.adjust();
							canvas.ele2.regions('redrawObjects', true);
						},
						whenPanned: function(img_offs_x, img_offs_y){
							navigator_maprect.adjust();
							canvas.ele2.regions('redrawObjects');
						},
						whenPanFinished: function() {
						}
					});
				}

				navigator_area.ele.css('display', 'block');
				navigator_show.ele.css('display', 'none');

				//
				// set the callback of the zoom slider here
				//
				zoom_slider.ele.slider(
					'setButtonMovingCB',
					function(pos) {
						if (viewer_settings.left_image_area) { // image is to the left
							image_zoom.zoom(pos, left_area.ele.width()/2.0, left_area.ele.height()/2.0); // set the zoom here
						}
						else {
							image_zoom.zoom(pos, right_area.ele.width()/2.0, right_area.ele.height()/2.0); // set the zoom here
						}
						zoom_slider.ele.slider('setBalloonHtml', parseInt(pos*100) + '%'); // set the slider balloon content here
						navigator_maprect.adjust();
					}
				);
				window_html.win('setBusy');
				return image.ele;
			},
			appendToControlArea: function(ele) {
				if (ele instanceof Array) {
					$(ele).each(function(){
						control_area.ele.append(this);
					});
				}
				else {
					control_area.ele.append(ele);
				}
			},
			appendToTaskbar: function(html) {
				window_html.win('taskbar', html);
			},
			getTaskbar: function() {
				return window_html.win('taskbar');
			},
			imageControlDim: function() {
				return {
					w: control_area.ele.width(),
					h: control_area.ele.height()
				};
			},
			showNavigator: function() {
				navigator_area.ele.css('display', 'block');
			},
			hideNavigator: function() {
				navigator_area.ele.css('display', 'none');
			},
			controlAreaResizingCB: function(name, func) {
				if (func instanceof Function) {
					// a function is given: set a CB
					viewer_settings.controlAreaResizingCB[name] = func;
				} else if (name != 'REMOVE_ALL') {
					// remove the CB with the given name
					delete viewer_settings.controlAreaResizingCB[name];
				} else {
					// remove all CBs
					viewer_settings.controlAreaResizingCB = {};
				}
			},
			separatorMovingCB: function(name, func) {
				if (func instanceof Function) {
					// a function is given: set a CB
					viewer_settings.separatorMovingCB[name] = func;
				} else if (name != 'REMOVE_ALL') {
					// remove the CB with the given name
					delete viewer_settings.separatorMovingCB[name];
				} else {
					// remove all CBs
					viewer_settings.separatorMovingCB = {};
				}
			},
			windowId: function() {
				return window_html.win('getId');
			},
			topCanvas: function() {
				return canvas.ele2;
			},
			imageZoom: function() {
				return image_zoom;
			},
			setTitle: function(title, iconsrc) {
				window_html.win('title', title, iconsrc);
			},
			setBusy: function() {
				window_html.win('setBusy');
			},
			unsetBusy: function() {
				window_html.win('unsetBusy');
			},
			regionSection: function(regsec) {
				if (regsec === undefined) {
					return region_section;
				}
				else {
					region_section = regsec;
				}
			},
			currentWindowSettings: function () {
				var window_settings = window_html.win('currentWindowSettings');
				delete window_settings.content; // must be deleted - would introduce recursion
				return window_settings;
			},
			currentSettings: function() {
				return {
					window_settings: retobj.currentWindowSettings(),
					viewer_settings: viewer_settings
				};
			},
			contentElement: function() {
				return window_html.win('contentElement');
			},
			destroy: function() {
				window_html.win('deleteWindow');
			},
			attachSelf: function(obj) {
				window_html.data('visualisationObject', obj.currentSettings());
				return obj;
			},
			whenSizedCB: function(name, func) {
				if (func instanceof Function) {
					// a function is given: set a CB
					viewer_settings.whenSizedCB[name] = func;
				} else if (name != 'REMOVE_ALL') {
					// remove the CB with the given name
					delete viewer_settings.whenSizedCB[name];
				} else {
					// remove all CBs
					viewer_settings.whenSizedCB = {};
				}
			}
		};
		return retobj.attachSelf(retobj);
	};
	/*===========================================================================*/


	RESVIEW.setupRegionTab = function(resinfo, viewer, make_active) {
		var metadata_area_tabs = viewer.metadataArea();
		var figures = [];
		if ((resinfo.regions !== undefined) && (resinfo.regions.length > 0)) {
			var winid = viewer.windowId();
			var canvas = viewer.topCanvas();
			//
			// if there are regions (with each region consisting of 1-n geometrical figures), we draw them now
			//
			var region_id = [];
			canvas.regions('reinit');
			if (resinfo.regions !== undefined) {
				for (var reg in resinfo.regions) { // loop over all regions
					region_id[reg] = resinfo.regions[reg].res_id;
					figures[reg] = [];
					for (var ff in resinfo.regions[reg]['salsah:geometry'].values) {
						var fff;
						figures[reg].push(JSON.parse(resinfo.regions[reg]['salsah:geometry'].values[ff].val));
						fff = figures[reg].length - 1; // the last index pushed a line obove...

						figures[reg][fff].res_id = resinfo.regions[reg].res_id;
						figures[reg][fff].lineColor = resinfo.regions[reg]['salsah:color'].values[0].val;
						figures[reg][fff].val_id = resinfo.regions[reg]['salsah:geometry'].values[ff].id;
					}
				}
			}
			var tmpstr = '';
			var regmeta_area = [];
			var cont = $('<div>');
			var regsec;
			for (var rr in resinfo.regions) {
				//                      var icon = $('<img>', {src: regdata.resdata.iconsrc}).dragndrop('makeDraggable', 'RESID', {resid: region_id[rr]});
				//                      var label = $('<div>').append(icon).append(regdata.resdata.restype_label);
				cont.append(
					$('<div>', {'class': 'propedit regionheader metadata winid_' + winid + ' regnum_' + resinfo.regions[rr].res_id, 'data-reg_id': resinfo.regions[rr].res_id, 'data-reg_num': rr})
					.append($('<img>', {src: SITE_URL + '/app/icons/collapse.png'}))
					.append($('<span>').css({'background-color': resinfo.regions[rr]['salsah:color'].values[0].val}).append('REGION ' + rr + ' '))
					.append($('<img>', {src: resinfo.regions[rr].iconsrc}).dragndrop('makeDraggable', 'RESID', {resid: resinfo.regions[rr].res_id}))
				);
				cont.append(regmeta_area[rr] = $('<div>', {'class':'propedit section metadata winid_' + winid + ' regnum_' + resinfo.regions[rr].res_id}).append(tmpstr));
				// $.post(SITE_URL + '/app/helper/rdfresedit.php', {winid: winid, resid: region_id[rr], regnum: rr},  // TO DO, BUT ALREADY IN API DIR

				SALSAH.ApiGet('resources', region_id[rr], {regnum: rr}, function(regdata) {
					if (regdata.status == ApiErrors.OK) {
						//metadataAreaDomCreate(regmeta_area[regdata.regnum], winid, undefined, rr, regdata);
						metadataAreaDomCreate(regmeta_area[rr], regdata, {winid: winid, regnum: rr});
						regmeta_area[rr].find('.datafield.regnum_' + rr).propedit(
							regdata.resdata, regdata.props, {
								'canvas': canvas,
								viewer: viewer
							}
						);

						regmeta_area[rr].find('.delres').on(
							'click',
							function(event) {
								var res_id = $(event.target).data('res_id');
								if (confirm(strings._delentry)) {
									SALSAH.ApiDelete('resources/' + $(event.target).data('res_id'), function(data) {
										if (data.status == ApiErrors.OK) {
											// XXXXXXXXXXXXXXX
											metadata_area_tabs.tabs('remove', 'regions'); // remove region tab and all associated data (e.g.data_hook).
											viewer.resetRegionTab(true); // reinit the region tab
											// XXXXXXXXXXXXXXXX
										}
										else {
											alert(data.errormsg);
										}
									});
								}
							}
						);
					}
					else {
						alert(new Error().lineNumber + ' ' + regdata.errormsg);
					}
				});
			}

			//
			// now we have to setup the region area and start the detect mode!
			//
			regsec = regionsetup(cont, winid);

			//
			// setting up callbacks if the REGIONS tab is clicked (or "leaved")
			//

			metadata_area_tabs.tabs(
				'setTab',
				'regions',
				'REGION',
				$('<div>').append(cont), {
					onEnterCB: regionsTabOnEnterCB,
					onLeaveCB: regionsTabOnLeaveCB,
					dataHook: {
						name: 'FIGURES',
						data: {canvas: canvas, regsec: regsec, viewer: viewer, figures: JSON.stringify(figures)}
					}
				}
			);
		}
		else {
			metadata_area_tabs.tabs('remove', 'regions');
		}
		if ((make_active !== undefined) && (make_active)) {
			metadata_area_tabs.tabs('setActiveTab', 'regions');
		}
	};
	/*=======================================================================*/

	RESVIEW.ImageView = function(append_to, window_options, viewer_options) {
		var regsec; // holds the region-section-object

		var window_settings = {
			class_window: ['win', 'imageBase', 'imageView'],
			title: 'This is ImageView...'
		};
		$.extend(window_settings, window_options);

		var viewer_settings = {
			type: 'ImageView',
			pic: null,
			thumbnail: null,
			whenSizedCB: {}, // init with empty object
			res_id: null,
			img_maxsize: 1200,
			q_index: -1,
			regtab_active: false
		};
		$.extend(viewer_settings, viewer_options);

		var image_base;
		var retobj = {};


		/**
		 * This is the callback function which is called when the window is being resized
		 */
		 var whenSized = function(dx, dy) {
			 image_base.whenSized(dx, dy);
		 };
		 /*=======================================================================*/

		 var whenMoved = function(dx, dy) {
			 image_base.whenMoved(dx, dy);
		 };
		 /*=======================================================================*/


		 var setContentFromResid = function(viewer, res_id) {
			//
			// first we get the resource context data
			//
			SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
				if (data.status == ApiErrors.OK) {
					var context = data.resource_context;
					//
					// set the window title
					//
					image_base.setTitle(context.resinfo.firstproperty, context.resinfo.restype_iconsrc);

					//
					// Here we get the proper quality to display
					//
					viewer_settings.pic = context.resinfo.locations;
					if (viewer_settings.q_index == -1) {
						for (var i in viewer_settings.pic) {
							if (Math.max(viewer_settings.pic[i].nx, viewer_settings.pic[i].nx) < viewer_settings.img_maxsize) {
								viewer_settings.q_index = i;
							}
						}
					}
					if (viewer_settings.q_index == -1) { // we didn't find a quality -> let's take the best....
						viewer_settings.q_index = viewer_settings.pic.length - 1
					}
					viewer_settings.thumbnail = context.resinfo.preview;
					img = image_base.setImageSrc(
						viewer_settings.pic[viewer_settings.q_index].path,
						viewer_settings.thumbnail.path,
						function() {
							// to be filled .......!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
							imageMetadataArea(image_base, winid, res_id, context.resinfo, 1, viewer_settings.regtab_active);
						}
					);

					viewer_settings.res_id = res_id;
					RESVIEW.initQuality(image_base, viewer_settings, viewer_settings.pic, viewer_settings.thumbnail);
					RESVIEW.initRegionDrawing(viewer, res_id); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

					if (window_options.taskbar) {
						RESVIEW.initGraph(retobj, res_id);
					}

					/*
					viewer_settings.pic = context.resinfo.locations;
					viewer_settings.thumbnail = context.resinfo.preview;
					var q_index = viewer_settings.pic.length - 1;
					if (q_index > 4) q_index--;
					img = image_base.setImageSrc(
						viewer_settings.pic[q_index],
						viewer_settings.thumbnail,
						function() {
							// to be filled .......!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
							imageMetadataArea(image_base, winid, res_id, context.resinfo, 1);
						}
					);
					*/
				}
				else {
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});
			viewer_settings.res_id = res_id;
			//RESVIEW.initRegionDrawing(viewer, res_id); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

			if (window_options.taskbar) {
				RESVIEW.initGraph(retobj, res_id);
			}
		};
		/*=======================================================================*/

		window_settings.whenSizedCB = whenSized;
		window_settings.whenMovedCB = whenMoved;

		image_base = RESVIEW.ImageBase(append_to, window_settings, viewer_settings);
		var winid = image_base.windowId();
		var canvas = image_base.topCanvas();

		var metadata_area_tabs = image_base.metadataArea();

		//
		// add the region drawing and handling....
		//
		canvas.regions();
		retobj.viewerType = 'ImageView';
		retobj.whenSized =  function(dx, dy) { whenSized(dx, dy); }; // EXPORT To CHILDREN
		retobj.appendHtml =  function(html, toLeft) {
			image_base.appendHtmlBase(html, toLeft);
		};
		retobj.metadataArea =  function() {
			return image_base.metadataArea();
		};
		retobj.appendToControlArea = function(ele) {
			image_base.appendToControlArea(ele);
		};
		retobj.appendToTaskbar = function(html) {
			image_base.appendToTaskbar(html)
		};
		retobj.getTaskbar =  function() {
			return image_base.getTaskbar();
		};
		retobj.imageControlDim = function() {
			return image_base.imageControlDim();
		};
		retobj.setImageSrc =  function(src, thumb_src, loadedCB) {
			return image_base.setImageSrc(src, thumb_src, loadedCB);
		};
		retobj.showNavigator = function() {
			image_base.showNavigator();
		};
		retobj.hideNavigator =  function() {
			image_base.hideNavigator();
		};
		retobj.windowId = function() {
			return image_base.windowId();
		};
		retobj.setContentFromResid =  function(res_id) {
			setContentFromResid(retobj, res_id);
		};
		retobj.setTitle =  function(title, iconsrc) {
			image_base.setTitle(title, iconsrc);
		};
		retobj.resId =  function(res_id) { // USED To BE getImageId
			if (res_id === undefined) {
				return viewer_settings.res_id;
			}
			else {
				viewer_settings.res_id = res_id;
				if (window_options.taskbar) {
					RESVIEW.initRegionDrawing(retobj, res_id); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					// RESVIEW.initRegionDrawing(image_base, res_id); // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

					RESVIEW.transcriptionTab(retobj, res_id);

					RESVIEW.initGraph(retobj, res_id);
				}
			}
		};
		retobj.imageZoom =  function() {
			return image_base.imageZoom();
		};
		retobj.topCanvas =  function() {
			return image_base.topCanvas();
		};
		retobj.regionSection =  function(regsec) {
			if (regsec === undefined) {
				return image_base.regionSection();
			}
			else {
				image_base.regionSection(regsec);
			}
		};
		retobj.resetRegionTab =  function(make_active) {
			SALSAH.ApiGet('resources', viewer_settings.res_id, {resinfo: true, reqtype: 'context'}, function(data) {
				if (data.status == ApiErrors.OK) {
					RESVIEW.setupRegionTab(data.resource_context.resinfo, retobj, make_active); // This is the correct parameter: self (=retobj)
				}
				else {
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});
		};
		retobj.currentWindowSettings =  function () {
			return image_base.currentWindowSettings();
		};
		retobj.currentSettings =  function() {
			return {
				window_settings: retobj.currentWindowSettings(),
				viewer_settings: viewer_settings
			};
		};
		retobj.setBusy = function() {
			image_base.setBusy();
		};
		retobj.unsetBusy = function() {
			image_base.unsetBusy();
		};
		retobj.contentElement =  function() {
			return image_base.contentElement();
		};
		retobj.attachSelf =  function(obj) {
			image_base.attachSelf(obj);
			return obj;
		};
		retobj.controlAreaResizingCB =  function(name, func) {
			image_base.controlAreaResizingCB(name, func);
		};
		retobj.whenSizedCB =  function(name, func) {
			image_base.whenSizedCB(name, func);
		};
		retobj.separatorMovingCB =  function(name, func) {
			image_base.separatorMovingCB(name, func);
		};

		//
		// Here we start loading the image. If the ImageView has been initialized with an resource id (viewer_settings.res_id),
		// then we retrieve all the information to load the image and the metadata field!
		// Otherwise we try to load the image. If we have an array of qualities, we take the next to the best quality !!!!!!!!!!!!! PRELIMINARY !!!!!!!!
		//
		var img;
		if (viewer_settings.res_id) { // we have a resource id
			setContentFromResid(retobj, viewer_settings.res_id);
		}
		else if (viewer_settings.pic) { // no resource id, but  pic
			if (viewer_settings.pic instanceof Array) { // we have an array of URls, with the last being the highest quality
				if (viewer_settings.q_index == -1) {
					for (var i in viewer_settings.pic) {
						if (Math.max(viewer_settings.pic[i].nx, viewer_settings.pic[i].nx) < viewer_settings.img_maxsize) {
							viewer_settings.q_index = i;
						}
					}
				}
				if (viewer_settings.q_index == -1) { // we didn't find a quality -> let's take the best....
					viewer_settings.q_index = viewer_settings.pic.length - 1
				}
				RESVIEW.initQuality(image_base, viewer_settings, viewer_settings.pic, viewer_settings.thumbnail);
				img = image_base.setImageSrc(viewer_settings.pic[q_index].path, viewer_settings.thumbnail.path);
			}
			else { // we just have a single URL
				img = image_base.setImageSrc(viewer_settings.pic.path, viewer_settings.thumbnail.path);
			}
		}

		return retobj.attachSelf(retobj);
	};
	/*===========================================================================*/


	RESVIEW.ImageCollectionView = function(append_to, window_options, viewer_options, res_context_data) {
		var window_settings = {
			class_window: ['win', 'imageBase', 'imageView', 'imageCollectionView'],
			title: 'This is ImageCollectionView...'
		};
		$.extend(window_settings, window_options);

		var image_slider, image_slider_prev, image_slider_next, image_sel;

		var viewer_settings = {
			type: 'ImageCollectionView',
			stepper_button_width: 15,
			compound_res_id: null, // required! Must be given!!
			sequence_number : 1,
			large_preview_delay: 1000,
			large_preview_size: 2,
			imageSelectedCB: {}, // init with empty object
			img_maxsize: 1200,
			q_index: -1,
		};
		$.extend(viewer_settings, viewer_options);


		viewer_settings.imgctrl_area = true; // must be "true" to be a ImageCollectionView!!

		var pics_res_ids = [];
		var pics = [];
		var previews = [];
		var preview_labels = [];
		var eles = [];
		var previews_cache = [];
		var image_view = RESVIEW.ImageView(append_to, window_settings, viewer_settings);

		//
		// set callback by calling the method from imageView
		//
		var collectionViewCB = function(width) {
			image_slider.slider('resizeAbsolute', width - image_slider_prev.outerWidth() - image_slider_next.outerWidth() - image_sel.outerWidth());
		};

		image_view.controlAreaResizingCB('collectionViewCB', collectionViewCB);

		var setPartTab = function(index) {
			SALSAH.ApiGet('resources', pics_res_ids[index - 1], {resinfo: true, reqtype: 'context'}, function(data) {
				if (data.status == ApiErrors.OK) {
					imageMetadataArea(image_view, winid, pics_res_ids[index - 1], data.resource_context.resinfo, 2);
				}
				else {
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});
			/*
			$.__post(
				SITE_URL + '/ajax/get_resource_context.php', {res_id: pics_res_ids[index - 1], resinfo: true}, function(data) {
					imageMetadataArea(image_view, winid, pics_res_ids[index - 1], data.resinfo, 2);
				},
				'json'
			);
			*/
		};
		//---------------------------------------------------------------------

		var setTabs = function(compound_res_id, index) {
			// $.post(SITE_URL +'/app/helper/rdfresedit.php', // TO DO, BUT ALREADY IN API DIR
			SALSAH.ApiGet('resources', compound_res_id, function(data) {
				if (data.status == ApiErrors.OK) {
					var icon = $('<img>', {src: data.resdata.iconsrc});
					var label = $('<div>').append(icon).append(data.resdata.restype_label);

					metadata_area_tabs.tabs('setTab', 'compound_data', label, function(topele) {
						metadataAreaDomCreate(topele, data, {winid: winid, tabid: 1});
					});
					$('.datafield_1.winid_' + winid).propedit(data.resdata, data.props);
					var tabele = metadata_area_tabs.tabs('contentElement', 'compound_data');
					tabele.addClass('propedit_frame');

					tabele.find('.delres').click(
						function(event) {
							$('body').dialogbox();
						}
						);
					sectionsetup(tabele, winid);
					setPartTab(index);
				}
				else {
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});
		};
		//---------------------------------------------------------------------


		var applySettings = function(data) {
			var i;
			pics_res_ids = data.res_id;
			pics = data.locations;
			previews = data.preview;
			preview_labels = data.firstprop;

			//
			// set the window title
			//
			image_view.setTitle(data.resinfo.firstproperty,  data.resinfo.restype_iconsrc);

			//
			// set the image slider range
			//
			image_slider.slider('setRange', 1, pics.length);
			image_slider.slider('setValue', viewer_settings.sequence_number);
			for (var ii in preview_labels) {
				image_sel.append($('<option>').val(parseInt(ii) + 1).text(preview_labels[ii]));
			}

			// set the option in the select box to the current sequence number
			image_sel.find('option[value="' + viewer_settings.sequence_number + '"]').attr({selected: true});

			image_view.resId(data.res_id[viewer_settings.sequence_number - 1]);
			image_view.topCanvas().regions(); // clear regions...

			//
			// we load the preview images within a setTimeout callback. This leads to the a-synchronous loading of the images...
			//
			setTimeout(
				function(){
					var cnt = previews.length;
					for (i in previews) {
						previews_cache[i] = new Image();
						$(previews_cache[i]).on(
							'load',
							function(ev){
								cnt--;
								window.status = 'Remaining ' + cnt;
							}
							);
						previews_cache[i].src = previews[i].path;
					}
				},
				1
			);

			//
			// select and laod the start image
			//
			var pic = pics[viewer_settings.sequence_number - 1];
			if (viewer_settings.q_index == -1) {
				for (var i in pic) {
					if (Math.max(pic[i].nx, pic[i].nx) < viewer_settings.img_maxsize) {
						viewer_settings.q_index = i;
					}
				}
			}
			if (viewer_settings.q_index == -1) { // we didn't find a quality -> let's take the best....
				viewer_settings.q_index = pic.length - 1
			}

			RESVIEW.resetQuality(image_view, viewer_settings, pic, previews[viewer_settings.sequence_number - 1]);

			if (previews.length > 0) {
				image_view.setImageSrc(
					pic[viewer_settings.q_index].path,
					previews[viewer_settings.sequence_number - 1].path,
					function() {
						//
						// create and fill the metadata tabs with both data about the compound object and the selected part object
						//
						setTabs(viewer_settings.compound_res_id, viewer_settings.sequence_number);

					}
				);
			}
			else {
				image_view.setImageSrc(
					pic[viewer_settings.q_index].path,
					undefined,
					function() {
						//
						// create and fill the metadata tabs with both data about the compound object and the selected part object
						//
						setTabs(viewer_settings.compound_res_id, viewer_settings.sequence_number);
					}
				);
			}
		};
		//---------------------------------------------------------------------

		RESVIEW.initQuality(image_view, viewer_settings);

		//
		// here we add the controls to the controlArea, a left- and -right button plus the slider to select the image
		//
		image_sel = $('<select>').css({position: 'absolute', top: 0, right: 0, bottom: 0}).width(100).on(
			'change',
			function(event) {
				var index = parseInt($(this).val());

				//var q_index = pics[parseInt(index) - 1].length - 1;
				//if (pics[parseInt(index) - 1].length > 4) q_index--;

				var pic = pics[index - 1];
				if (viewer_settings.q_index == -1) {
					for (var i in pic) {
						if (Math.max(pic[i].nx, pic[i].nx) < viewer_settings.img_maxsize) {
							viewer_settings.q_index = i;
						}
					}
				}
				if (viewer_settings.q_index == -1) { // we didn't find a quality -> let's take the best....
					viewer_settings.q_index = pic.length - 1
				}

				RESVIEW.resetQuality(image_view, viewer_settings, pic, previews[viewer_settings.sequence_number - 1])

				image_view.setImageSrc(pic[viewer_settings.q_index].path, previews[index - 1].path, function() {
					//
					// set the matadata tab to the data of the selected image given by index
					//
					setPartTab(index);
				});
				image_view.showNavigator();
				window.clearTimeout(timer_id); // clear all remaining time


				for (var cb_index in viewer_settings.imageSelectedCB) {
					if (viewer_settings.imageSelectedCB[cb_index] instanceof Function) viewer_settings.imageSelectedCB[cb_index](parseInt(index));
				}

				viewer_settings.sequence_number = index;
				image_slider.slider('setValue', index);
				image_view.resId(pics_res_ids[index - 1]);
				image_view.topCanvas().regions();
			}
		);

		image_slider_prev = $('<div>', {'class': 'prevImage'}).css({position: 'absolute', top: 0, left: 0, bottom: 0});
		if (image_slider_prev.width() == 0) {
			image_slider_prev.width(viewer_settings.stepper_button_width);
		}
		image_slider_next = $('<div>', {'class': 'nextImage'}).css({position: 'absolute', top: 0, right: image_sel.outerWidth(), bottom: 0});
		if (image_slider_next.width() == 0) {
			image_slider_next.width(viewer_settings.stepper_button_width);
		}

		image_slider = $('<div>', {'class': 'imageSlider'}).css({
			position: 'absolute',
			top: 0,
			left:  image_slider_prev.outerWidth(),
			bottom: 0,
			right: image_slider_next.outerWidth() + image_sel.outerWidth()
		});

		image_slider_prev.click(
			function() {
				image_slider.slider('stepDown');
			}
		);
		image_slider_next.click(
			function() {
				image_slider.slider('stepUp');
			}
		);

		eles[0] = image_slider_prev;
		eles[1] = image_slider;
		eles[2] = image_slider_next;
		eles[3] = image_sel;
		image_view.appendToControlArea(eles);

		image_slider.slider({
			horizontal: true,
			min: 1,
			max: 100,
			start_position: viewer_settings.sequence_number,
			incstep: 1
		});

		var metadata_area_tabs = image_view.metadataArea();
		var winid = image_view.windowId();

		if (res_context_data) {
			applySettings(res_context_data);
		}
		else if (viewer_settings.compound_res_id) {
			SALSAH.ApiGet('resources', viewer_settings.compound_res_id, {resinfo: true, reqtype: 'context'}, function(data) {
				if (data.status == ApiErrors.OK) {
					applySettings(data.resource_context);
				}
				else {
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});
		}
		else {
			alert('INTERNAL ERROR: NO COMPOUND OBJECT TO DISPLAY!');
		}

		var balloon_content = [];

		balloon_content[0] = $('<img>');
		balloon_content[1] = $('<div>');

		image_slider.slider('setBalloonElements', balloon_content);
		//
		// here we have to install the event handling
		//
		var timer_id = 0; // is used to delay the large image preview
		image_slider.slider(
			'setButtonMoveBeginCB',
			function(index) {
				var idx = parseInt(index);
				image_view.hideNavigator();

				//                      balloon_content[0].attr('src', previews[parseInt(index) - 1]);
				balloon_content[0].attr('src', previews_cache[idx - 1].src);
				if (preview_labels.length > 0) {
					balloon_content[1].html(preview_labels[idx - 1]);
				}
				//
				// handling of the delayed large image preview
				//
				window.clearTimeout(timer_id); // clear timers that may be existing
				timer_id = window.setTimeout(
					function() {
						balloon_content[0].attr('src', pics[idx - 1][viewer_settings.large_preview_size].path);
					},
					viewer_settings.large_preview_delay
				);
			}
		).slider(
			'setButtonMovingCB',
			function(index){
				var idx = parseInt(index);
				//                      balloon_content[0].attr('src', previews[parseInt(index) - 1]);
				balloon_content[0].attr('src', previews_cache[idx - 1].src);
				if (preview_labels.length > 0) {
					balloon_content[1].html(preview_labels[idx - 1]);
				}
				//
				// handling of the delayed large image preview
				//
				window.clearTimeout(timer_id); // clear timers that may be existing
				timer_id = window.setTimeout(
					function() {
						balloon_content[0].attr('src', pics[idx - 1][viewer_settings.large_preview_size].path);
					}, viewer_settings.large_preview_delay
				);
			}
		).slider(
			'setButtonMovedCB',
			function(index) {
				var idx = parseInt(index);

				var pic = pics[idx - 1];
				if (viewer_settings.q_index == -1) {
					for (var i in pic) {
						if (Math.max(pic[i].nx, pic[i].nx) < viewer_settings.img_maxsize) {
							viewer_settings.q_index = i;
						}
					}
				}
				if (viewer_settings.q_index == -1) { // we didn't find a quality -> let's take the best....
					viewer_settings.q_index = pic.length - 1
				}

				RESVIEW.resetQuality(image_view, viewer_settings, pic, previews[viewer_settings.sequence_number - 1])

				image_view.setImageSrc(
					pic[viewer_settings.q_index].path,
					previews[idx - 1].path,
					function() {
						//
						// set the matadata tab to the data of the selected image given by index
						//
						setPartTab(idx);
					}
				);
				image_view.showNavigator();
				window.clearTimeout(timer_id); // clear all remaining time

				for (var cb_index in viewer_settings.imageSelectedCB) {
					if (viewer_settings.imageSelectedCB[cb_index] instanceof Function) viewer_settings.imageSelectedCB[cb_index](idx);
				}

				viewer_settings.sequence_number = idx;
				image_sel.find('option[value="' + idx + '"]').attr({selected: true});
				image_view.resId(pics_res_ids[idx - 1]);
				image_view.topCanvas().regions();
			}
		);

		var retobj = {
		viewerType: 'ImageCollectionView',
			whenSized: function(dx, dy) { image_view.whenSized(dx, dy); }, // EXPORT To CHILDREN
			appendHtml: function(html, toLeft) {
				image_view.appendHtml(html, toLeft);
			},
			metadataArea: function() {
				return image_view.metadataArea();
			},
			appendToControlArea: function(ele) {
				image_view.appendToControlArea(ele);
			},
			appendToTaskbar: function(html) {
				image_view.appendToTaskbar(html);
			},
			getTaskbar: function() {
				return image_view.getTaskbar();
			},
			imageControlDim: function() {
				return image_view.imageControlDim();
			},
			setImageSrc: function(src, thumb_src, loadedCB) {
				return image_view.setImageSrc(src, thumb_src, loadedCB);
			},
			showNavigator: function() {
				image_view.showNavigator();
			},
			hideNavigator: function() {
				image_view.hideNavigator();
			},
			windowId: function() {
				return image_view.windowId();
			},
			setTitle: function(title, iconsrc) {
				image_view.setTitle(title, iconsrc);
			},
			imageZoom: function() {
				return image_view.imageZoom();
			},
			topCanvas: function() {
				return image_view.topCanvas();
			},
			regionSection: function(regsec) {
				if (regsec === undefined) {
					return image_view.regionSection();
				}
				else {
					image_view.regionSection(regsec);
					return undefined;
				}
			},
			resetRegionTab: function(make_active) {
				//                              return image_view.resetRegionTab(make_active);
				SALSAH.ApiGet('resources', viewer_settings.res_id, {resinfo: true, reqtype: 'context'}, function(data) {
					if (data.status == ApiErrors.OK) {
						RESVIEW.setupRegionTab(data.resource_context.resinfo, retobj, make_active); // This is the correct parameter: self (=retobj)
					}
					else {
						alert(new Error().lineNumber + ' ' + data.errormsg);
					}
				});
			},
			imageSelectedCB: function(name, func) {
				if (func instanceof Function) {
					// a function is given: set a CB
					viewer_settings.imageSelectedCB[name] = func;
				} else if (name != 'REMOVE_ALL') {
					// remove the CB with the given name
					delete viewer_settings.imageSelectedCB[name];
				} else {
					// remove all CBs
					viewer_settings.imageSelectedCB = {};
				}
			},
			currentIndex: function() {
				return viewer_settings.sequence_number;
			},
			currentWindowSettings: function () {
				return image_view.currentWindowSettings();
			},
			currentSettings: function() {
				return {
					window_settings: retobj.currentWindowSettings(),
					viewer_settings: viewer_settings
				};
			},
			contentElement: function() {
				return image_view.contentElement();
			},
			setBusy: function() {
				image_view.setBusy();
			},
			unsetBusy: function() {
				image_view.unsetBusy();
			},
			destroy: function() {
				image_view.destroy();
			},
			attachSelf: function(obj) {
				image_view.attachSelf(obj);
				return obj;
			},
			controlAreaResizingCB: function(name, func) {
				image_view.controlAreaResizingCB(name, func);
			},
			whenSizedCB: function(name, func) {
				image_view.whenSizedCB(name, func);
			},
			separatorMovingCB: function(name, func) {
				image_view.separatorMovingCB(name, func);
			}
		};
		return retobj.attachSelf(retobj);
	};
	/*===========================================================================*/


	/*\_________________________________________________________________________
	 * _0____0____0____0____0____0____0____0____0____0____0____0____0____0____0_
	 * let's do some dynamic media stuff e.g. movie and sound
	 *
	 * initSequenceAnnotation = create sequences in movie or audio files and annotate them
	 * MovieView = win-object for movies !!@ak: here we have to prepare it for audio documents
	 * _________________________________________________________________________
	 * _0____0____0____0____0____0____0____0____0____0____0____0____0____0____0_
	\*/
	RESVIEW.initSequenceAnnotation = function (viewer, res_id) {
		//"use strict";
		var taskbar_main,
			sequence_actions,
			sequence_icon,
			wp,
			tabname,
			tab_content,
			sequence_tb,
			ct = 0, 		// current time from active video
			annotationTool;
		taskbar_main = viewer.getTaskbar().elestack('get', 'main');
		taskbar_main.find('.sequenceActions').remove();
		sequence_actions = $('<span>', {
			'class': 'sequenceActions'
		});
		/*
		sequence_icon = $('<img>', {
			src:
			title: 'Sequenz erstellen'
		}).css('cursor', 'pointer').click(function (e) {
		*/

		sequence_icon = $('<img>').attr({src: SITE_URL + '/app/icons/24x24/sequence.png', title: 'Create Sequence'}).css({cursor: 'pointer'}).on('click', function () {

			//alert(this.title);

			/* todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! */
			/* get the curTime and stop the movie if played; use the curtime in the vat (video annotation tool) */
//			localdata.settings.avplayer.avplayer('getCurTime');

			// find the active window with the current video and get the time position and if the video is running, stop it!
			var active_video = $('.win.focus').find('video');
			var ct = active_video[0].currentTime;
			if (active_video[0].paused === false) {
				active_video.trigger('pause');

				$('.win.focus').find('input.av_play').css({
					'background-position': '0 0'
				}).attr({
					'title': 'Play [F8 or Ctrl + k]'
				});


			}

			//console.log(localdata.settings.avplayer.avplayer('getCurTime'));

			/* todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! */

			wp = $('.workwin_content');
			tabname = 'sequence_' + RESVIEW.initSequenceAnnotation.prototype.cnt;
			RESVIEW.initSequenceAnnotation.prototype.cnt += 1;

			wp.tabs('setTab', tabname, 'SEQUENCE', '', {
				setActive: true,
				deletable: true,
				onDeleteCB: function (name) {}
			});
			annotationTool = {}; // sequence taskbar
			tab_content = annotationTool.workspace = wp.tabs('contentElement', tabname);
			//sequence_tb; // sequence taskbar

			// append to the tabs above: the annotation tool
			// on the left side with
			tab_content
			.append(
				annotationTool.left = $('<div>').css({
					height: tab_content.innerHeight(),
				}).addClass('av_annotationTool')
				.html(
					annotationTool.player = $('<div>').addClass('av_player')
				)
				.append(
					annotationTool.transcription = $('<div>').css({
						height: tab_content.innerHeight() - 420
					}).addClass('av_transcription')
				)
			)
			.append(
				annotationTool.right = $('<div>').css({
					width: tab_content.innerWidth() - 640,
					height: tab_content.innerHeight() - 26,
				}).addClass('av_sequences')
				.append(
					annotationTool.vertical_timeline = $('<div>').addClass('av_condat')		// connected data: vertical timeline or sequence protocol
				)
			);
			// here was an example block: you will find it now in imagebase-testSequence.js //

			SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function (data) {
				var context = data.resource_context;

				var restype_name = context.resinfo.restype_name;
				var vocabulary = restype_name.split(':')[0];

				//            SALSAH.ApiGet('graphdata', res_id, {}, function(data) {
				if (data.status === ApiErrors.OK) {
					annotationTool.player.avplayer({
						res_id: res_id,
						videolocation: context.resinfo.locations[1].path,
						frameslocation: context.resinfo.locations[0].path,
						posterframe: context.resinfo.locations[1].path,
						fps: context.resinfo.locdata.fps,
						playTime: ct,
						duration: context.resinfo.locdata.duration,
						avplayer: annotationTool.player,
						avtranscript: annotationTool.transcription,
						timeline: annotationTool.vertical_timeline
					});

					var def_vals = [];
//					def_vals['dc:title'] = '01';
					def_vals['salsah:interval'] = '00:00:00';
					annotationTool.transcription.avtranscript({
						viewer: viewer,
						sequence_restype: vocabulary + ':sequence',
						defaultvalues: def_vals,
						film_resid: res_id,
						avplayer: annotationTool.player
					});

					var leftWidth = annotationTool.player.innerWidth();
					var rightWidth = $(window).innerWidth() - leftWidth;
					annotationTool.left.width(leftWidth);
					annotationTool.player.width(640);
					annotationTool.transcription.width(640);
					/*
					annotationTool.vertical_timeline.avtimeline({
						film_resid: res_id,				// res_id of film (must be given!)
						avplayer: annotationTool.player
					});
					*/
				} else {
					alert(new Error().lineNumber + ' ' + data.errormsg);
				}
			});

		}).appendTo(sequence_actions);

		taskbar_main.append(sequence_actions);
	};
	/*=======================================================================*/
	RESVIEW.initSequenceAnnotation.prototype.cnt = 0;


	RESVIEW.MovieView = function(append_to, window_options, viewer_options) {
		var window_html;
		var winid;
		var content = {};
		var top_area = {};
		var separator_area = {};
		var bottom_area = {};
		var click_handle = {};
		var save_areadata = {};
		var canvas = {};
		var region_section;

		/** ------------------------------------------------------------------------
		* This is the callback function which is called when the window is being resized
		* !!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!!*!*!!!*
		*/
		var whenSized = function(dx, dy) {
			if (separator_area.ele.css('display') === 'none') { // metadata area hidden
				if ((content.h + dy ) < (save_areadata.bottom_area_h + save_areadata.separator_area_h + viewer_settings.movie_area_minheight)) {
					save_areadata.bottom_area_h = save_areadata.bottom_area_h + dy;
					save_areadata.separator_area_b = save_areadata.separator_area_b + dy;
				}
				else {
					save_areadata.top_area_h = save_areadata.top_area_h + dy;
				}
			}
			else {
				if ((content.h + dy ) < (bottom_area.h + separator_area.h + viewer_settings.movie_area_minheight)) {
					bottom_area.h = bottom_area.h + dy;
					bottom_area.ele.css('height', bottom_area.h + 'px');
					separator_area.b = separator_area.b + dy;
					separator_area.ele.css('bottom', separator_area.b + 'px');
					click_handle.ele.css('bottom', separator_area.b + 'px');
				}
			}
			content.w = content.w + dx;
			content.h = content.h + dy;
			setAreaSizes();

			// +`+`+`+`+`+`+`+`+ RESIZE THE MOVIE PLUGIN HERE +`+`+`+`+`+`+`+`+`+`+`+
			//image_zoom.resize(left_area.ele.width(), left_area.ele.height());
			//top_area.ele.avplayer();
			//
			// we have to recalculate the seconds per pixel and other stuff
			//

			for (var cb_index in viewer_settings.whenSizedCB) {
				if (viewer_settings.whenSizedCB[cb_index] instanceof Function) viewer_settings.whenSizedCB[cb_index](dx, dy);
			}
		};
		/*=======================================================================*/

		/** ------------------------------------------------------------------------
		* This is the callback function which is called when the separator is being moved
		*/
		var separatorMoving = function(dy) {
			if (((top_area.h + dy) < viewer_settings.movie_area_minheight) && (dy < 0)) return;

			top_area.h = top_area.h + dy;
			bottom_area.h = bottom_area.h - dy;

			separator_area.b = separator_area.b - dy;
			separator_area.ele.css({bottom: separator_area.b});
			click_handle.ele.css({bottom: separator_area.b});

			top_area.ele.css('height', top_area.h + 'px');
			bottom_area.ele.css('height', bottom_area.h + 'px');

			for (var cb_index in viewer_settings.separatorMovingCB) {
				if (viewer_settings.separatorMovingCB[cb_index] instanceof Function) viewer_settings.separatorMovingCB[cb_index]();
			}
		};
		/*=======================================================================*/

/*
		var window_settings = {
			closable : true,
			movable : true,
			minimizable : true,
			sizable : true,
			maximizable : true,
			pos_x : 110,
			pos_y : 110,
			dim_x : 700,
			dim_y : 500,
			visible : 'visible',
			title : title,
			taskbar: true
		};
*/

		var window_settings = {
			class_window: ['win', 'imageBase'],
			title: 'This is MovieBase...',
			fullscreen: false,
			dim_x: 800,
			dim_y: 640,
			whenSizedCB: whenSized
			/*
			whenSizedCB: function() {
				//window_html.win('contentElement').avplayer('resizeMovie');
			}
			*/
		};
		$.extend(window_settings, window_options);

		var viewer_settings = {
			type: 'MovieViewer',
			metadata_area_height: 200, // default 250,
			movie_area_minheight: 345, // default 200,
			separatorMovingCB: {},
			whenSizedCB: {},
			res_id: null
		};
		$.extend(viewer_settings, viewer_options);



		/** ------------------------------------------------------------------------
		* This function adapts the areas to the resizing of the window
		*/
		var setAreaSizes = function() {
//			top_area.h = '440px';
//			console.log(content);
//			content.ele.css({'height': '700px'});
			top_area.h = content.h - separator_area.h - bottom_area.h;
			top_area.ele.css({'overflow-x': 'visible'});
			top_area.ele.css('height', top_area.h + 'px');
		};
		/*=======================================================================*/

		/** ------------------------------------------------------------------------
		* This function toggles the visibility of the metadata area
		*/
		var toggleMetadataArea = function() {
			if (separator_area.ele.css('display') == 'block') { // hide metadata area
				save_areadata.top_area_h = top_area.h;
				save_areadata.bottom_area_h = bottom_area.h;
				save_areadata.separator_area_h = separator_area.h;

				save_areadata.separator_area_b = separator_area.b;
				bottom_area.ele.animate({bottom: -bottom_area.h});
				top_area.ele.animate({height: content.h});
				separator_area.ele.animate({bottom: -separator_area.h});
					click_handle.ele.animate(
						{bottom: 0},
						function() {
							separator_area.ele.css('display', 'none');
							separator_area.b = 0;
							separator_area.h = 0;
							top_area.h = content.h;
							bottom_area.ele.css('display', 'none');
							bottom_area.h = 0;
//							click_handle.ele.text('^');
							click_handle.ele.toggleClass('down up');
						}
					);
			}
			else { // show annotation area
				bottom_area.ele.css({display: 'block', height: save_areadata.bottom_area_h});
				bottom_area.ele.animate({bottom: 0});
				top_area.ele.animate({height: save_areadata.top_area_h});

				separator_area.ele.css({display: 'block', height: save_areadata.separator_area_h});
				separator_area.ele.animate({bottom: save_areadata.separator_area_b});
				click_handle.ele.animate(
					{bottom: save_areadata.separator_area_b},
					function() {
						separator_area.b = save_areadata.separator_area_b;
						top_area.h = save_areadata.top_area_h;
						bottom_area.h = save_areadata.bottom_area_h;
						separator_area.h = save_areadata.separator_area_h;
//						click_handle.ele.text('V');
						click_handle.ele.toggleClass('up down');
					}
				);
			}
		};
		/*=======================================================================*/


		window_html = $('<div>').appendTo(append_to);
		window_html.win(window_settings);
		winid = window_html.win('getId');
		if (window_html.win('taskbar') !== false) {
			window_html.win('taskbar').elestack().elestack('add', 'main', $('<div>')); // initialize element stack (taskbar is existing!)
		}

		content.ele = window_html.win('contentElement');
		content.ele.empty();
		content.w = content.ele.width();
		content.h = content.ele.height();

		//
		// create the top (=movie) area
		//
		top_area.ele = $('<div>').addClass('topArea').css({overflow: 'hidden', position: 'absolute', left: 0, top:0, right: 0}).appendTo(content.ele);

		//
		// create the separator
		//
		separator_area.ele = $('<div>').addClass('hseparatorArea').css({position: 'absolute', left: 0, right: 0}).appendTo(content.ele);

		//
		// create the bottom (=metadata area)
		//
		bottom_area.ele = $('<div>', {'class': 'bottomArea'}).css({overflow: 'hidden', position: 'absolute', right: 0, left: 0, bottom: 0}).appendTo(content.ele);
		bottom_area.h = viewer_settings.metadata_area_height;
		bottom_area.ele.css('height', bottom_area.h + 'px');

		//
		// prepare tabs
		//
		bottom_area.ele.tabs();

		separator_area.b = bottom_area.h;
		separator_area.ele.css({bottom: separator_area.b + 'px'});
//		click_handle.ele = $('<div>', {'class': 'it_clickhandle'}).css({position: 'absolute', bottom: separator_area.b + 'px'}).text('V').appendTo(content.ele);
		click_handle.ele = $('<div>').addClass('it_clickhandle ml down').css({position: 'absolute', bottom: separator_area.b + 'px'}).appendTo(content.ele);
		separator_area.h = separator_area.ele.outerHeight(true);

		setAreaSizes();

		/*
		* Here are the event handlers
		*/
		//
		// moving the separator between left- and right area
		//
		separator_area.ele.mousedown(
			function(event) {
				// event.stopPropagation();
				event.preventDefault();
				var pos_y = event.pageY;
				$('#ecatcher').css({display: 'block'}).mousemove(
					function(event) {
						event.stopPropagation();
						event.preventDefault();
						if (((top_area.h + (event.pageY - pos_y)) <= 0) || ((bottom_area.h - (event.pageY - pos_y)) <= 0)) return;
						separatorMoving(event.pageY - pos_y);
						pos_y = event.pageY;
					}
				).mouseup(
					function() {
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				).mouseout(
					function(){
						$('#ecatcher').css({display: 'none'}).unbind();
					}
				);
			}
		);
		click_handle.ele.click(
			function() {
				toggleMetadataArea();
			}
		);


		var setContentFromResid = function(viewer, res_id) {
			SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
				var context = data.resource_context;
				//alert(context.resinfo.locdata.fps + ' - ' + context.resinfo.locdata.duration);

				//window_html.win('title', context.resinfo.firstproperty, data.resinfo.restype_iconsrc);
				//image_base.setTitle(context.resinfo.firstproperty, context.resinfo.restype_iconsrc);
				window_html.win('title', context.resinfo.firstproperty, context.resinfo.restype_iconsrc);

				top_area.ele.css({'overflow': 'auto'});
				top_area.ele.avplayer({
					res_id: res_id,
					videolocation: context.resinfo.locations[1].path,
					frameslocation: context.resinfo.locations[0].path,
					posterframe: context.resinfo.locations[1].path,
					fps: context.resinfo.locdata.fps,
					duration: context.resinfo.locdata.duration

				});
				movieMetadataArea(viewer, winid, res_id, context.resinfo, 1);
			});
			/*
			$.__post(SITE_URL + '/ajax/get_resource_context.php', {res_id: viewer_settings.res_id, resinfo: true}, function(data) {

					window_html.win('title', data.resinfo.firstproperty, data.resinfo.restype_iconsrc);

					top_area.ele.css('overflow', 'auto');
					top_area.ele.avplayer({
						videolocation: data.resinfo.locations[1].path,
						frameslocation: data.resinfo.locations[0].path,
						posterframe: data.resinfo.locations[1].path
					});
					movieMetadataArea(viewer, winid, res_id, data.resinfo, 1);
					RESVIEW.initSequence(viewer, res_id);
				}, 'json'
			);
			*/
		};

		//*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*

		//window_html.win('contentElement').addClass('propedit_frame'); // we need this class, because in the object viewer this is the location of the propedits....
		/*
		var load_content = function(content_from_url) { // {url: url, postdata: {}}
			if (content_from_url) {
				window_html.win('setBusy');
				content_from_url.postdata.winid = window_html.win('getId'); // add window id automatically !!
				$.__post(
					content_from_url.url,
					content_from_url.postdata,
					function(data) {
						window_html.win('content', data).win('unsetBusy');
					},
					'html'
				);
			}
		};

		load_content(viewer_settings.content_from_url);
*/
		//alertObjectContent(viewer_settings);

		SALSAH.ApiGet('resources', viewer_settings.res_id, {resinfo: true, reqtype: 'context'}, function(data) {
			if (data.status == ApiErrors.OK) {
				var context = data.resource_context;

				window_html.win('contentElement').css('overflow', 'auto');

			//    alertObjectContent(context.resinfo.locations);
				/*
				window_html.win('contentElement').avplayer({
					videolocation: context.resinfo.locations[1].path,
					posterframe: context.resinfo.locations[1].path,
					frameslocation: context.resinfo.locations[0].path
				});
				*/
			}
			else {
				alert(new Error().lineNumber + ' ' + data.errormsg);
			}
		});


		// ?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+?+


		var retobj = {};
		retobj.viewerType = 'MovieViewer';
		retobj.whenSized = function(dx, dy) { whenSized(dx, dy); }; // EXPORT To CHILDREN
		retobj.whenMoved =  function(dx, dy) { whenMoved(dx, dy); }; // EXPORT To CHILDREN
		retobj.setMetadataAreaContent = function(content) {
				bottom_area.ele.empty().append(content);
		};

		retobj.metadataArea =  function() {
			return bottom_area.ele;
		};



		retobj.windowId =  function() {
			return window_html.win('getId');
		};
		retobj.setTitle = function(title, iconsrc) {
			window_html.win('title', title, iconsrc);
		};
		retobj.resId =  function(res_id) { // USED To BE getImageId
			if (res_id === undefined) {
				return viewer_settings.res_id;
			}
			else {
				viewer_settings.res_id = res_id;

				if (window_options.taskbar) {
					RESVIEW.initGraph(retobj, res_id);
					RESVIEW.initSequenceAnnotation(retobj, res_id);
				}
				setContentFromResid(retobj, viewer_settings.res_id); // check ImageViewer!! This line seems missing there !!!!
			}
		};
		retobj.setBusy = function() {
			window_html.win('setBusy');
		};
		retobj.unsetBusy = function() {
			window_html.win('unsetBusy');
		};
		retobj.dataHook = function(data) {
			if (data === undefined) {
				return window_html.win('dataHook');
			}
			else {
				window_html.win('dataHook', data);
			}
			return undefined;
		};
		retobj.setupPropeditor = function() {
			sectionsetup(window_html.win('contentElement'), window_html.win('getId'));
		};
		retobj.appendToTaskbar = function(html) {
			var window_settings = window_html.win('currentWindowSettings');
			if (window_settings.taskbar) {
				window_html.win('taskbar', html);
			}
			return undefined;
		};
		retobj.getTaskbar = function() {
			return window_html.win('taskbar');
		};
		retobj.currentWindowSettings = function () {
			var window_settings = window_html.win('currentWindowSettings');
			delete window_settings.content; // must be deleted - would introduce recursion
			return window_settings;
		};
		retobj.currentSettings = function() {
			return {
				window_settings: retobj.currentWindowSettings(),
				viewer_settings: viewer_settings
			};
		};
		retobj.contentElement = function() {
			return window_html.win('contentElement');
		};
		retobj.attachSelf = function(obj) {
			window_html.data('visualisationObject', obj.currentSettings());
			return obj;
		};
		retobj.destroy = function() {
			window_html.win('deleteWindow');
		};


		if (viewer_settings.res_id) { // we have a resource id
			setContentFromResid(retobj, viewer_settings.res_id);
		}

		if (window_options.taskbar && (viewer_settings.res_id !== undefined)) {
//			RESVIEW.initQuality(retobj, viewer_settings);
			RESVIEW.initGraph(retobj, viewer_settings.res_id);
			RESVIEW.initSequenceAnnotation(retobj, viewer_settings.res_id);
		}

		return retobj.attachSelf(retobj);
	};         // end of: RESVIEW.MovieView
	/*\_________________________________________________________________________
	 * _0____0____0____0____0____0____0____0____0____0____0____0____0____0____0_
	 *
	 * end of: RESVIEW.MovieView
	 *
	 *
	 * _________________________________________________________________________
	 * _0____0____0____0____0____0____0____0____0____0____0____0____0____0____0_
	\*/



	/*=======================================================================*/
	RESVIEW.new_resource_editor = function (res_id, title, window_options, viewer_options) {

		var viewer;
		var window_settings = {
			closable : true,
			movable : true,
			minimizable : true,
			sizable : true,
			maximizable : true,
			pos_x : 110,
			pos_y : 110,
			dim_x : 700,
			dim_y : 500,
			visible : 'visible',
			title : title,
			taskbar: true
		};
		var viewer_settings = {};

		if (window_options !== undefined)
		{
			$.extend(window_settings, window_options);
		}

		var create_viewer = function(context, regtab_active) {
			if (context.context == RESOURCE_CONTEXT_IS_COMPOUND) {
				if (RESVIEW.maximize_cb) {
					window_settings.whenMaximizedCB = RESVIEW.maximize_cb;
				}
				viewer_settings = {
					compound_res_id: context.canonical_res_id,
					sequence_number : 1,
					large_preview_delay: 2000,
					large_preview_size: 2,
					imageSelectedCB: {}, // init with empty object
					taskbar: true
				};
				if (viewer_options !== undefined) {
					$.extend(viewer_settings, viewer_options);
				}
				if ((regtab_active !== undefined) && (regtab_active)) {
					viewer_settings.regtab_active = true;
				}
				viewer = RESVIEW.ImageCollectionView(RESVIEW.winclass, window_settings, viewer_settings, context);
			}
			else {
				if (!context.resinfo.locations) {
					$.extend(
						window_settings,
						{
							visible : true,
							title : context.resinfo.restype_label,
							window_icon : context.resinfo.restype_iconsrc
						}
					);
					viewer_settings = {
						res_id: res_id
					};

					viewer = RESVIEW.ObjectViewer(RESVIEW.winclass, window_settings, viewer_settings);
				}
				else {
					if (RESVIEW.maximize_cb) {
						window_settings.whenMaximizedCB = RESVIEW.maximize_cb;
					}
					viewer_settings = {
						res_id: context.canonical_res_id,
						title: context.resinfo.restype_label,
						taskbar: true
					};
					if (viewer_options !== undefined) {
						$.extend(viewer_settings, viewer_options);
					}
					switch (context.resinfo.locdata.format_name) {
						case 'JPEG': {
							if ((regtab_active !== undefined) && (regtab_active)) {
								viewer_settings.regtab_active = true;
							}
							viewer = RESVIEW.ImageView(RESVIEW.winclass, window_settings, viewer_settings);
							break;
						}
						case 'SALSAH-WEBVIDEO': {
							viewer = RESVIEW.MovieView(RESVIEW.winclass, window_settings, viewer_settings);
							break;
						}
						default: {
                            console.log("3. not compound and have locations and default in create_viewer in imagebase.js")
							viewer = RESVIEW.ObjectViewer(RESVIEW.winclass, window_settings, viewer_settings);
						}
					}
				}
			}
		};

		SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
			if (data.status == ApiErrors.OK) {
				var context = data.resource_context;
				if (context.resinfo.restype_name == 'salsah:generic_region') {
					//
					// we have a region_of! Let's find the corresponding image
					//
					SALSAH.ApiGet('properties', res_id, function(data) {
						if (data.status == ApiErrors.OK) {
							SALSAH.ApiGet('resources', data.properties['salsah:region_of'].values[0].val, {resinfo: true, reqtype: 'context'}, function(data) {
								if (data.status == ApiErrors.OK) {
									var context = data.resource_context;
									create_viewer(context, true);
								}
								else {
									alert(data.errormsg);
								}
							});
						}
						else {
							alert(data.errormsg);
						}
					});
				}
				else {
					//
					// it's NOT a region_of
					//
                    console.log("2. calling create_viewer in imagebase.js")
					create_viewer(context);
				}
			}
			else {
				alert(new Error().lineNumber + ' ' + data.errormsg);
			}
		});
	};

});
