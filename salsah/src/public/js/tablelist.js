
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
 * Function which displays the result of a search in a excel like table
 * tablelist is a part of 02_searchlist.js (case 'editor')
 * it is a standalone version, just for testing.
 * one day we can implement it directly in searchlist?!
 *
 *
 * @param ele JQuery element into which the search result list should be written
 * @param data Data object returned by call to search API
 * @param showprops array with 0 = props <voc:propname> and the label of it
 */


SALSAH.tablelist = function(ele, data, showprops) {
	var tmp, localdata = {};

	localdata.table = {};
	localdata.result = {};

	// start with an empty table
	// localdata.table.header = here you can append table rows (tr) for every result
	// localdata.table.icon = here you can append table headers (th) for every property type (the labels as titles)

	ele
		.append($('<div>').addClass('tableedit')
			.append(localdata.table.main = $('<table>')
				.addClass('table main editor')
				.append(localdata.table.header = $('<thead>')
					.append($('<tr>')
						.addClass('table row header')
						.append(localdata.table.icon = $('<th>')
							.addClass('table cell top_left')			// header cell in the top left corner e.g. for the icon of the resource
					)
				)
			)
				.append(localdata.table.data = $('<tbody>'))
				.append($('<tfoot>'))
		)
	);

	localdata.result.num = data.nhits;

	// set the header titles ( = props.label)
		for (var i = 0; i < showprops.length; i++) {
			localdata.table.header.append($('<th>')
					.addClass(showprops[i].propname)
					.html(showprops[i].proplabel)
			);
		}

	// get the data
	data.subjects.forEach(function (subj) {
		var tmp = subj.obj_id.split('_-_');
		var row = [];
		SALSAH.ApiGet('resources', subj.obj_id, function(resource) {
			if (resource.status == ApiErrors.OK) {

				// set the resource icon into the top left header cell (it should be only one type!)
				localdata.table.icon.html($('<img>').attr({src: resource.resinfo.restype_iconsrc}));

				var i;
				localdata.table.data.append(row[resource.resdata.res_id] = $('<tr>')
						.addClass('table row resource').hover(
					function () {
						$(this).children().css("background","rgba(255, 255, 102, 0.6)");
					},
					function () {
						$(this).children().css("background","");
					}
				)
						.append(localdata.table.cell = $('<td>')
							.html(resource.resdata.res_id)
							.addClass('table cell res_id')
					)
				);
				for (i = 0; i < showprops.length; i++) {
					var cell = $('<td>').addClass('table cell');
					// if res prop is empty
					if (resource.props[showprops[i].propname] === undefined) {
						cell
						.append(
							$('<div>').html('PP='+showprops[i].propname)
						);
					} else {
						var propdata = resource.props[showprops[i].propname];
						
						if (propdata.values !== undefined) 
						{
							cell.editvalue({
								property: propdata,
								window_framework: false
							}, -1)
								.click(function(event) {
									if($('td').hasClass('active')) {
										$('td').removeClass('active');
									}
									$(this).toggleClass('active');
								});
							/*
							for (var ii in propdata.values) {
								var datavalue;
								cell
								.append(
									datavalue = $('<div>').data({
										res_id: resource.resdata.res_id,
										propname: showprops[i].propname,
										property: resource.props[showprops[i].propname],
										value_index: ii
									}).editvalue({
										property: propdata,
										value_index: ii,
										window_framework: false
									}).on('dblclick', function(event) {
										$(this).editvalue('edit', {property: $(this).data('property'), value_index: $(this).data('value_index')});
									})
								);
							}
							*/
						}
					}
					row[resource.resdata.res_id].append(cell)


				}
			} else {
				alert(new Error().lineNumber + ' ' + resource.errormsg);
			}
		});


//		append.after(tmp[0]).html('<br>');
	});

/*
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
				alert(data.errormsg)
			}
		}, 'json'
	);


/*
// create my data list
//	alertObjectContent(showprops, '============= SALSAH.tablelist: SHOWPROPS =============');


	var tabcontent = [];
	tabcontent[0] = [];
	var i;
	for (i in showprops) {
		tabcontent[0].push(showprops[i]);
	}
	var row = 1;
	var resid_to_row = [];
	data.subjects.forEach(function(subject) {
		var cell;
		var tmp = subject.obj_id.split('_-_');
		resid_to_row[tmp[0]] = row;
		cell = [];
		for (propname in showprops) {
			cell.push('-');
		}
		tabcontent.push(cell);
		row++;
	});


	ele.empty();
	ele.append(
		$('<div>').addClass('tableedit')
	);

	row = 1;
	data.subjects.forEach(function(subject) {

		SALSAH.ApiGet('resources', subject.obj_id, function(resource) {
				contentData = [];

			if (resource.status == ApiErrors.OK) {
				// get the resource icon

//				restype_icon = resource.resinfo.restype_iconsrc;

				//alertObjectContent(resid_to_row);
			//console.log('RES_ID=' + resource.resdata.res_id + ' TABROW=' + resid_to_row[resource.resdata.res_id]);

				//var tabrow = tabcontent[resid_to_row[resource.resdata.res_id]];
				var propname;
				var i;
				var ii = 0;

				for (i in showprops) {
					if (resource.props[showprops[i]] !== undefined) {
			//			console.log(resource.props[showprops[i]].label);
						tabcontent[resid_to_row[resource.resdata.res_id]][ii] = resource.props[showprops[i]].values;
						//tabcontent[resid_to_row[resource.resdata.res_id]][ii] = 'Waseliwas soll denn das?';
					}
					else {
						tabcontent[resid_to_row[resource.resdata.res_id]][ii] = '';
					}
					ii++;
				}
			//	topele.handsontable('render');
			//	console.log(tabcontent);

				// -------------------------------------------------------------------------------------
				// HACKer - test: hide the paging elements !!!!!!!!!!!!!!!
				// -------------------------------------------------------------------------------------
				$('.paging').hide();
				// -------------------------------------------------------------------------------------
				// end HACKer - test
				// -------------------------------------------------------------------------------------
			}
			else {
				alert(new Error().lineNumber + ' ' + resource.errormsg)
			}

		});
	});




	var topele = $(".tableedit");
	topele.tableedit({
		data: tabcontent
		//			colHeaders: []
	});

	/*
	var $container = $("#example1");
	var $console = $("#example1console");
	var $parent = $container.parent();
	var autosaveNotification;

	$container.handsontable({
		startRows: 0,
		startCols: 0,
		rowHeaders: true,
		colHeaders: true,
		minSpareRows: 1,
		contextMenu: false,
		data: resource.resinfo,
		colWidths: [120, 200, 80]

		/*
		afterChange: function (change, source) {
			if (source === 'loadData') {
				return; //don't save this change
			}
			if ($parent.find('input[name=autosave]').is(':checked')) {
				clearTimeout(autosaveNotification);
				$.ajax({
					url: "json/save.json",
					dataType: "json",
					type: "POST",
					data: change, //contains changed cells' data
					complete: function (data) {
						$console.text('Autosaved (' + change.length + ' ' +
							'cell' + (change.length > 1 ? 's' : '') + ')');
						autosaveNotification = setTimeout(function () {
							$console.text('Changes will be autosaved');
						}, 1000);
					}
				});
			}
		}
		*//*
	});
*/



};

