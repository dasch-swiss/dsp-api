/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
 * @frame: jQuery plugin to slide through a lot of images e.g. movie frames
 * 			like the iPhoto mouse move on an album
 *
 * @author André Kilchenmann code@milchkannen.ch
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
	var setStyle = function (localdata) {
			var aspect, // = localdata.flipbook.startframe.width() / localdata.flipbook.startframe.height(),
				width = 120,
				height = 90; //Math.floor(width / aspect); // here we've got preload problems ;(

		//	console.log('width: ' + width + ' height: ' + height + ' aspect: ' + aspect);

			localdata.flipbook.main.css({
				position: 'relative',
				display: 'block',
				//width: width + 'px',
				//height: height + 'px'
				width: '100%',
				height: '100%',
				cursor: 'pointer'
			});

			localdata.flipbook.content.css({
				width: '100%',
				height: '100%'
			});

			return aspect;
		},

		setImages = function (localdata) {
			var frame,
				img,
				width = localdata.flipbook.main.width(),
				interval = Math.round(localdata.settings.duration / (width / 3)),
				i = interval,
				current;

				localdata.flipbook.frame = [];

			while (i < (localdata.settings.duration - interval)) {
				frame = localdata.settings.imglocation + '&frame=' + i;

				localdata.flipbook.content.append(
					localdata.flipbook.frame[i] = $('<img>').attr({src: frame}).addClass('thumbnail')
				);

				if(i === interval){
					current = localdata.flipbook.frame[i].addClass('current').css({opacity: '1'});
				}
				i += interval;
			}



			localdata.flipbook.content.children().css ({
				display: 'block',
				opacity: '0',
				position: 'absolute',
				top: '0',
				left: '0',
				width: '100%',
				height: '100%'
			});
			localdata.flipbook.content.children('img:first').css ({
				position: 'static'
			});
			return current;
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

				localdata.flipbook = {};
				localdata.movieInfo = {};

				localdata.settings = {
					imglocation: '',	// must be given!!
					duration: '60',		// must be given!!
					movieid: ''
				};

				$.extend(localdata.settings, options);
				// initialize a local data object which is attached to the DOM object
				$this.data('localdata', localdata);

				var flipbook_id = localdata.settings.movieid;

				/*
				SALSAH.ApiGet('resources', flipbook_id, {resinfo: true, reqtype: 'context'}, function(propdata) {
					if (propdata.status === ApiErrors.OK) {
						var res_info = propdata.resource_context.resinfo,
							durationTime = res_info.locdata.duration,
							framePath = res_info.preview.path,
							resID = propdata.resource_context.canonical_res_id;

						localdata.movieInfo = {
							durationTime: durationTime,
							//frameWidth: frameWidth,
							//frameHeight: frameHeight,
							framePath: framePath,
							resID: resID
						};
				*/




						$this.append(
							localdata.flipbook.main = $('<div>').addClass('flipbook')
								.append(
								localdata.flipbook.content = $('<span>').addClass('flipcont ' + flipbook_id)
									.append(localdata.flipbook.startframe = $('<img>').attr({src: localdata.settings.imglocation + '&frame=1&format=jpg'}))
							)
						);



						localdata.flipbook.aspect = setStyle(localdata);
						//	console.log(localdata.flipbook.aspect);

						localdata.flipbook.current = setImages(localdata);


						localdata.flipbook.main.on('mousemove mouseleave', function(e) {
							var flipbook_width = $this.innerWidth(),
								flipbook_slides = localdata.flipbook.content.children().length,
								flipbook_current = 1,
								posX;



							if (e.type == 'mousemove') {
								posX = e.pageX - $this.offset().left;
								flipbook_current = Math.floor(posX / (flipbook_width / flipbook_slides)) + 1;

								if(flipbook_current !== 1) {
									localdata.flipbook.current.removeClass('current').css({opacity: '0'});
									localdata.flipbook.current = $('.' + flipbook_id + ' > :nth-child(' + flipbook_current + ')').addClass('current').css({opacity: '1'});
								}
								return false;


							} else if(e.type == 'mouseleave') {
								if(flipbook_current != 1){
									flipbook_current = 1;
									localdata.flipbook.current.removeClass('current');
									localdata.flipbook.current = $('.' + flipbook_id + ' > :nth-child(' + flipbook_current + ')').addClass('current');
								}
								return false;
							}
						});

						localdata.flipbook.current.css({opacity: '1'});


					//	console.log('info: ' + localdata.movieInfo.durationTime + ' — ' + localdata.movieInfo.framePath + ' — ' + localdata.movieInfo.resID + ' — ' );
/*
					} else {
						alert(new Error().lineNumber + ' ' + data.errormsg);
					}
				});
*/

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
	$.fn.flipbook = function(method) {
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
