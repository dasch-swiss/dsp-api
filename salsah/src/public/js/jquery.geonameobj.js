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

/* ===========================================================================
 *
 * @frame: jQuery plugin
 *
 * @author André Kilchenmann a.kilchenmann@unibas.ch
 *
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
	var getGeoNames = function(method, data, callback, my) {
		var deferred = $.Deferred();
		// TODO: validate method(exists), and params
		$.ajax({
			url: my.geoNamesProtocol + '://' + my.geoNamesApiServer + '/' + method + 'JSON',
			dataType: 'jsonp',
			data: $.extend({}, my.defaultData, data),
			success: function(data) {
				deferred.resolve(data);
				if (!!callback) callback(data);
			},
			error: function (xhr, textStatus) {
				deferred.reject(xhr, textStatus);
				alert('Ooops, geonames server returned: ' + textStatus);
			}
		});
		return deferred.promise();
	},
		jeoCityAutoComplete = function (options) {
			this.autocomplete({
				source: function (request, response) {
					jeoquery.getGeoNames('search', {
						featureClass: jeoquery.featureClass.PopulatedPlaceFeatures,
						style: "full",
						maxRows: 12,
						name_startsWith: request.term
					}, function (data) {
						response($.map(data.geonames, function (item) {
							var displayName = item.name + (item.adminName1 ? ", " + item.adminName1 : "") + ", " + item.countryName;

							return {
								label: displayName,
								value: displayName,
								details: item
							};
						}));
					});
				},
				minLength: 1,
				select: function( event, ui ) {
					if (ui && ui.item && options && options.callback) {
						options.callback(ui.item.details);
					}
				},
				open: function () {
					$(this).removeClass("ui-corner-all").addClass("ui-corner-top");
				},
				close: function () {
					$(this).removeClass("ui-corner-top").addClass("ui-corner-all");
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
						lang: 'en'
					};

				var my = {};

				my.defaultCountryCode = 'CH';
				my.defaultLanguage = 'en';
				my.geoNamesApiServer = 'api.geonames.org';
				my.geoNamesProtocol = 'http';

				my.featureClass = {
					AdministrativeBoundaryFeatures: 'A',
					HydrographicFeatures: 'H',
					AreaFeatures: 'L',
					PopulatedPlaceFeatures: 'P',
					RoadRailroadFeatures: 'R',
					SpotFeatures: 'S',
					HypsographicFeatures: 'T',
					UnderseaFeatures: 'U',
					VegetationFeatures: 'V'
				};

					
				$.extend(localdata.settings, options);
				// initialize a local data object which is attached to the DOM object
				$this.data('localdata', localdata);
				my.defaultData = {
					userName: 'milchkannen',
					lang: SALSAH.userdata.lang
				};

				$this.html(
					my.input = $('<input>')
						.attr({
							type: 'text',
							id: 'city'})
						.css({
							width: '500px'})
						.addClass('input-large')
				);

				my.input.autocomplete({
					source: function (request, response) {
						getGeoNames('search', {
							featureClass: my.featureClass.PopulatedPlaceFeatures,
							style: "full",
							maxRows: 12,
							name_startsWith: request.term
						}, function (data) {
							response($.map(data.geonames, function (item) {
								var displayName = item.name + (item.adminName1 ? ", " + item.adminName1 : "") + ", " + item.countryName;

								return {
									label: displayName,
									value: displayName,
									details: item
								};
							}));
						}, my);
					},
					minLength: 2,
					select: function( event, ui ) {

						console.log(ui);
						alert('geonameId: ' + ui.item.details.geonameId + ', value: ' + ui.item.value + ', label: ' + ui.item.label + ' ' + ui.item.details.lat + ', ' + ui.item.details.lng);

						if (ui && ui.item && options && options.callback) {
							options.callback(ui.item.details);
						}
					},
					open: function () {
						$(this).removeClass("ui-corner-all").addClass("ui-corner-top");
					},
					close: function () {
						$(this).removeClass("ui-corner-top").addClass("ui-corner-all");
					}
				});


/*
				my.input.jeoCityAutoComplete({
					callback: function(city) {
						if (console) console.log(city);
						if (console) console.log(city.geonameId);
					}
				});
*/
				// create some stuff here

			//	$this.append($('div').addClass('TEST').css({background: 'red', width: '222px'}).html('TEST TEST TEST'));

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


	// change the pluginname !! IMPORTANT !!
	$.fn.geonameobj = function(method) {
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
