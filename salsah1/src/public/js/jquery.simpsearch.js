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
 * @author: Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 */

 (function( $ ){
	 $.simpsearch = {};
	 $.simpsearch.perform_search = function(result_ele, params) {
		//
		// params
		//    searchstring: "string"
		//    display_type: "table" | "matrix"
		//    show_nrows: NUMBER
		//

		/*
		 * Turned Off
        var progvalfile = 'prog_' + Math.floor(Math.random()*1000000.0).toString() + '.salsah';
         */

		result_ele.empty(); // clean previous searches if present

        /*
         * Turned off the search progress bar for now§
        result_ele.append(progbar = $('<div>').css({position: 'absolute', left: 10, height: 20, right: 10}).pgbar({show_numbers: true, cancel: function(){
			window.clearInterval(progvaltimer);
			xhr.abort();
			result_ele.empty();
		}}));
        */

        //params.show_nrows = 10;
		var searchparams = {
			searchtype: 'fulltext',
			show_nrows: (params.show_nrows === undefined) ? -1 : params.show_nrows,
			start_at: (params.start_at === undefined) ? 0 : params.start_at,
			progvalfile: "" //progvalfile
		};
		if (params.filter_by_project !== undefined) {
			searchparams.filter_by_project = params.filter_by_project;
		}
		if (params.filter_by_restype !== undefined) {
			searchparams.filter_by_restype = params.filter_by_restype;
		}
		SALSAH.ApiGet('search', params.searchstring, searchparams, function(data) {
			if (data.status == ApiErrors.OK) {
				//window.clearInterval(progvaltimer);
				var ele = result_ele;
				var pele;
                var peleItem;
				//
				// check if we have paging
				//
				if (data.paging.length > 0) {
					pele = $('<div>').addClass('pagination_ele center');
					for (var i in data.paging) {
						var ii = parseInt(i) + 1;
						pele.append(' ');
						if (data.paging[i].current) {
							pele.append($('<a>').addClass('paging paging_active').text('[' + ii  + ']'));
						}
						else {
							var title_txt = data.paging[i].start_at + ' - ' + (parseInt(data.paging[i].start_at) + parseInt(data.paging[i].show_nrows));
							pele.append(
                                peleItem = $('<a>').attr({href: '#', title: title_txt}).addClass('paging').data('start_at', data.paging[i].start_at).text('[' + ii + ']').on('click', function(event){
								params.start_at = $(this).data('start_at');
								$.simpsearch.perform_search(result_ele, params);
							}));
                            if(peleItem.prev().hasClass('paging_active')) peleItem.addClass('next_page');
//                        $('a.paging_active').next().addClass('next_page');
						}
					}
				}
				SALSAH.searchlist(ele, pele, data, params, 'simple');

			}
			else {
				//window.clearInterval(progvaltimer);
				alert(data.errormsg);
			}
		});

        /*
         * Turned Off
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
		*/
	};


	var methods = {
		init: function(options) {
			return this.each(function() {

				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					searchstring: undefined,
					display_type: 'table',
					show_nrows: -1,
					start_at: 0,
					filter_by_project: undefined,
					filter_by_restype: undefined,
					successCB: undefined,
                    important_props: {}
				};
				$.extend(localdata.settings, options);

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				var result_ele;
				$this.append(result_ele = $('<div>').attr({name: 'result'}).addClass('searchresult simpsearch'));

				var searchparams = {
					searchstring: localdata.settings.searchstring,
					display_type: localdata.settings.display_type,
					show_nrows: localdata.settings.show_nrows,
					start_at: localdata.settings.start_at,
                    important_props: localdata.settings.important_props
				};
				if (localdata.settings.filter_by_project !== undefined) {
					searchparams.filter_by_project = localdata.settings.filter_by_project;
				}
				if (localdata.settings.filter_by_restype !== undefined) {
					searchparams.filter_by_restype = localdata.settings.filter_by_restype;
				}
				$.simpsearch.perform_search(result_ele, searchparams);

			});
		}
	};

	$.fn.simpsearch = function(method) {
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
