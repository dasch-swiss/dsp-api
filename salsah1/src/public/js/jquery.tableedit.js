/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

/* ===========================================================================
 *
 * @frame: jQuery plugin for a excel-like table editor
 *
 * @author André Kilchenmann, Lukas Rosenthaler
 *
 * @requires
 *  jQuery - min-version 1.11.1
 *
 * ===========================================================================
 * ======================================================================== */

(function( $ ){
	// -----------------------------------------------------------------------
	// define some functions
	// -----------------------------------------------------------------------
	var mark_cell = function () {
			$('tr td').click(function() {
				if($('td').hasClass('active')) {
					$(this).removeClass('active');
				}
				$(this).toggleClass('active');
			});

			//		$('table').find('.active').append($('<span>').addClass('mover cross'));
		},

		mark_row = function () {

		},

		sort_row = function () {

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

				localdata.table = {};
				localdata.table.th = {};
				localdata.table.td = {};

				localdata.settings = {
					data: undefined,			// must be given! table element <table>
					showprops: undefined,
					viewer: undefined
				};

				$.extend(localdata.settings, options);
				// initialize a local data object which is attached to the DOM object
				$this.data('localdata', localdata);
				// number of hits: localdata.settings.data.nhits

				// create an empty table
				$this
					.append(
						$('<div>').addClass('tableedit')
						.append(
							localdata.table.main = $('<table>')
								.addClass('table main editor table-striped table-bordered')
							.append(
								localdata.table.header = $('<thead>')
								.append(
									localdata.table.headerrow = $('<tr>')
										.addClass('table row header')
									.append(
										localdata.table.mainicon = $('<th>')
											.addClass('table cell top_left fixedwidth')			// header cell in the top left corner e.g. for the icon of the resource
									)
								)
							)
							.append(
								localdata.table.data = $('<tbody>')
							)
							.append(
								$('<tfoot>')
							)
						)
					);

					if(localdata.settings.viewer === 'sequence') localdata.table.main.css({'margin-top': 0});

				// get and set the data
				localdata.settings.data.subjects.forEach(function (subj) {
					var tmp = subj.obj_id.split('_-_');
					var row = [];
					SALSAH.ApiGet('resources', subj.obj_id, function(resource) {
						if (resource.status == ApiErrors.OK) {
							// set the resource icon into the top left header cell (it should be only one type!)
							localdata.table.mainicon
								.text(resource.resinfo.restype_label + ': ' + resource.resinfo.restype_description);
							var i,
								res_info = resource.resinfo,
								img,
								data = localdata.settings.data;
							if(res_info.preview !== null) {
								var spliturl = res_info.preview.path.split("&"),
									splitval = jQuery.inArray( "qtype=frames", spliturl);

								// preview thumbnail in the first column;
								// if duration is not zero, then we have movies
								if(splitval > 0) {
									var av_duration = res_info.locdata.duration,
										av_frames = res_info.preview.path,
										res_id = subj.obj_id; //subj.resource_context.canonical_res_id;

									img = $('<div>').addClass('thumbframe').css({width: (parseInt(data.thumb_max.nx) / 2) + 10, height: (parseInt(data.thumb_max.ny) / 2) + 10})
										.flipbook({
											imglocation: av_frames,
											duration: av_duration,
											movieid: res_id
										}).on('mouseover', function(event){
											// load_infowin(event, subj.obj_id, this);
										});
								} else {
									var hover_img;
									img = $('<div>').addClass('thumbframe').css({width: (parseInt(data.thumb_max.nx) / 2) + 10, height: (parseInt(data.thumb_max.ny) / 2) + 10})
										/*
										.zoom({
											on: 'grab',
											url: subj.preview_path,
											magnify: 1.5
										})
										*/
										.append($('<img>').attr({src: subj.preview_path}).addClass('thumb preview zoom').css({width: '100%', height: '100%'})
											.attr({title: 'res: #' + resource.resdata.res_id})
											.on('mouseover', function(event) {
												$('body').append(
													hover_img = $('<span>').append(
														$('<img>')
														.attr({
															src: res_info.locations[3].path
														})
														.css({
															display: 'block',
															position: 'absolute',
															top: event.pageY - 100,
															left: event.pageX + 20,
														})
													)
												);

											})
											.on('mouseout', function() {
												hover_img.remove();
											})
										);
								}
							}
							else if ( localdata.settings.viewer	=== 'sequence' ) {
								var time_pos = resource.props['salsah:interval'].values[0].timeval1.split('.')[0];
								var time_pos_2 = resource.props['salsah:interval'].values[0].timeval2.split('.')[0];

//console.log(time_pos + '-' + time_pos_2);

								if (time_pos === '0') time_pos = 1;
								if (time_pos_2 === '0') time_pos_2 = 1;

								var seq_of = resource.props['salsah:sequence_of'].values[0];

								var img_url = SITE_URL + '/core/sendlocdata.php?res=' + seq_of + '&qtype=frames&frame=' + time_pos;
								var av_player = $('.av_player .' + seq_of).parent($('div.av_player'));

								// img url like http://iml-felix.iml.unibas.ch/salsah/core/sendlocdata.php?res=398340&qtype=frames&frame=60

								img = $('<div>').addClass('thumbframe').css({height: 'auto'}).append(
									$('<img>').attr({src: img_url}).addClass('thumb preview zoom').css({width: '100%', height: '100%', cursor: 'pointer'})
									.on('click', function() {
										av_player.avplayer('gotoTime', time_pos, time_pos_2);
									})
									.on('mouseover', function() {
										av_player.avplayer('showTime', time_pos);
									})
									.on('mouseout', function() {
										av_player.avplayer('hideTime');
									})
								);
							}

							if(img === undefined) {
								img = $('<div>').addClass('empty').html($('<img>')
									.attr({
										src: localdata.table.iconsrc,
										alt: localdata.table.restype
									}));
							}

							localdata.table.data.append(row[resource.resdata.res_id] = $('<tr>')
									.addClass('table row resource')
									.hover(
										function () {
											$(this).children().css("background","rgba(255, 255, 102, 0.6)");
										},
										function () {
											$(this).children().css("background","");
										}
									)
									.append(localdata.table.cell = $('<td>')
										.addClass('table cell res_id')
										.append(img)
								)
							);
							for (i = 0; i < localdata.settings.showprops.length; i++) {
								var cell = $('<td>').addClass('table cell fixedwidth');
								// if res prop is empty
								if (resource.props[localdata.settings.showprops[i].propname] === undefined) {
									cell
										.append(
											$('<div>').addClass('propedit value_container').html('PP = ' + localdata.settings.showprops[i].propname)
										);
									/*
									 .editvalue({
									 property: undefined,
									 window_framework: false
									 }, -1)
									*/
								} else {
									var propdata = resource.props[localdata.settings.showprops[i].propname];
									cell
										.editvalue({
											propname: localdata.settings.showprops[i].propname,
											resdata: resource.resdata,
											property: propdata,
											window_framework: false
										}, -1)
										.click(function(event) {
											if($('td').hasClass('active')) {
												$('td').removeClass('active');
											}
											$(this).toggleClass('active');
										});
								}
								row[resource.resdata.res_id].append(cell);


							}
						} else {
							alert(new Error().lineNumber + ' ' + resource.errormsg);
						}


					});

				});

				// set the header titles ( = props.label)
				for (var i = 0; i < localdata.settings.showprops.length; i++) {
					localdata.table.headerrow.append(
						localdata.table.th[i] = $('<th>')
							.addClass(localdata.settings.showprops[i].propname + ' sort_row fixedwidth')
							.html(localdata.settings.showprops[i].proplabel)
					);


				}

				//
				// set the right table height
				//
				var height = $('.workwin_header').height() + $('.workwin_footer').height() + $('.go_back').height();
				$('.searchresult.extsearch').find('.center').each(function(){
						height += $(this).height();
				});
				height = window.innerHeight - height - 50;
				$('.searchresult.extsearch').find('.center').css({'position': 'relative', 'z-index': '55', 'width': '55%'});
				//
				// jquery plugin 'fixedheadertable' to fix the table header
				//
				/*
				localdata.table.main.fixedHeaderTable({
					autoShow: true,
					height: height
				});
				*/

					//localdata.table.th[i]
					$('th.sort_row')
						.wrapInner('<div title="sort this column"/>')
						.css({cursor: 'pointer'})
//						.append($('<div>').html(localdata.settings.showprops[i].proplabel))
						.each(function(){

							var th = $(this),
								thIndex = th.index(),
								inverse = false;

							th.click(function(){

								localdata.table.main.find('td').filter(function(){

									return $(this).index() === thIndex;

								}).sortElements(function(a, b){

									return $.text([a]) > $.text([b]) ?
										inverse ? -1 : 1
										: inverse ? 1 : -1;

								}, function(){

									// parentNode is the element we want to move
									return this.parentNode;

								});

								inverse = !inverse;

							});

						});

						$('th.table.cell.top_left')
							.append($('<img>')
								.attr({
									src: localdata.table.iconsrc,
				//					title: resource.resinfo.restype_label + ': ' + resource.resinfo.restype_description,
									alt: localdata.table.restype
								})
							);

/*
				$( window ).scroll(function() {
					console.log(localdata.table.headerrow.top());
					if(localdata.table.headerrow.top() === 44) {
						localdata.table.headerrow.css({position: 'fixed'});
					}
//					$( "span" ).css( "display", "inline" ).fadeOut( "slow" );
				});
*/

				// create some stuff here
				mark_cell();

/* _+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_
/* Test for fixed header
/* _+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_ */
/*
				$(function() {
					var $table = $('.table');
					var $thead = $table.find('thead');
					var $tbody = $table.find('tbody');
					var $tfoot = $table.find('tfoot');

					if (!$thead.length)
						$thead = $('<thead />').prependTo($table);
					if (!$tbody.length)
						$tbody = $('<tbody />').insertAfter($thead);
					if (!$tfoot.length)
						$tfoot = $('<tfoot />').insertAfter($tbody);

					var $hrow = $('<tr />').appendTo($thead);
					var $frow = $('<tr />').appendTo($tfoot);

					for (var row = 0; row < 200; row++)
						$tbody.append($('<tr />'));

					var $brow = $tbody.find('tr');

					for (var col = 0; col < 10; col++) {
						$('<th />').html('Hdr'+col)
							.appendTo($hrow);
						$('<td />').html('Val'+col)
							.appendTo($brow);
						$('<th />').html('Ftr'+col)
							.appendTo($frow);
					}

					$brow.each(function(ix, el) {
						$(el).children().first().html('R:'+ix);
					});

				});
				*/
/* _+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_ */
/* _+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_ */


			 /*
// design hack for multiple fields (0-n) in one property
					$('td.table.cell').find($('div'), function() {
						console.log($(this));
						if($(this).length > 1) {
							$(this).addClass('prop_item');
						}
					})
*/

			$( '.workwin_content' ).scroll(function() {
				var scroll = $('.workwin_content').scrollTop();
				if(scroll > 40) {
					$( "tr.header" ).css({position: 'fixed', top: '40px'});
				}
				else {
					$( "tr.header" ).css({position: 'static', top: ''});
				}
			});

			});											// end "return this.each"
		},												// end "init"

		anotherMethod: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		}
		/*========================================================================*/
	};


	$.fn.tableedit = function(method) {
		// Method calling logic
		if ( methods[method] ) {
			return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
		} else if ( typeof method === 'object' || ! method ) {
			return methods.init.apply( this, arguments );
		} else {
			throw 'Method ' + method + ' does not exist on jQuery.tooltip';
		}
	};
})( jQuery );
