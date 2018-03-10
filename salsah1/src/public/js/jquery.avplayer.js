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

/*\_________________________________________________________________________
 * _0____0____0____0____0____0____0____0____0____0____0____0____0____0____0_
 *
 * SALSAH Motion Picture Annotation Tool
 * Require: jquery.timeobj.js, jquery.avtranscript.js
 * _________________________________________________________________________
 * _0____0____0____0____0____0____0____0____0____0____0____0____0____0____0_
 \*/

(function($) {
	//"use strict";

	// -----------------------------------------------------------------------------
	// chooseExtension: chooses the correct video format in the used browser
	// -----------------------------------------------------------------------------
	var chooseExtension = function(video_tag) {
			if (video_tag.get(0).canPlayType("video/webm") !== '') {
				return {
					ext: 'webm',
					mime: 'video/webm'
				};
			} else if (video_tag.get(0).canPlayType("video/mp4") !== '') {
				return {
					ext: 'mp4',
					mime: 'video/mp4'
				};
			}
		},
		// -----------------------------------------------------------------------------
		// movieInformation: meta info about the movie e.g. duration, aspect ratio
		// -----------------------------------------------------------------------------
		movieInformation = function(localdata) {
			var video_element = localdata.video_element;
			var movieWidth, movieHeight, aspectRatio, durationTime,
				frameWidth, frameHeight, interval, countFrames,
				previewHeight, countVisibleFrames, secondsPerPixel;
			// get the movie dimension / size
			movieWidth = video_element[0].videoWidth;
			movieHeight = video_element[0].videoHeight;

			// get the aspect ratio
			aspectRatio = movieWidth / movieHeight;

			// get the duration / length
			durationTime = video_element[0].duration;

			// calculating the imagesize for the preview-frames (preview-panel)
			frameWidth = 118; //localdata.projector.av_preview.width(),
			frameHeight = Math.round(frameWidth / aspectRatio);

			// set the preview frame rate (interval) and count the frames
			// NEW: we're calculating an interval from duration time and fps
			// 		to have about 20 preview frames...
			interval = Math.round(durationTime / 20);
			//var interval = 15,
			//floor = rounded downwards || ceil = rounded upwards
			countFrames = Math.floor((durationTime - 5) / interval);

			// calculating the number of frames in one column
			previewHeight = localdata.projector.av_preview.height();
			countVisibleFrames = Math.round(previewHeight / frameHeight);

			// calculating the seconds per pixel in the timeline
			timelineWidth = localdata.projector.av_transportbar.width();
			secondsPerPixel = durationTime / timelineWidth; //!! IMPORTANT !!

			// minimal numbers of frame when the movie is really short
			if ((countVisibleFrames * interval) > durationTime) {
				//change the interval
				interval = Math.round(durationTime / countVisibleFrames);
				countFrames = Math.floor(durationTime / interval);
			}

			return {
				movieWidth: movieWidth,
				movieHeight: movieHeight,
				durationTime: durationTime,
				frameWidth: frameWidth,
				frameHeight: frameHeight,
				interval: interval,
				countFrames: countFrames,
				secondsPerPixel: secondsPerPixel
			};
		},

		// -----------------------------------------------------------------------------
		// ---------------------------- movie controls ---------------------------------
		// -----------------------------------------------------------------------------

		// -----------------------------------------------------------------------------
		// togglePlay: start / pause the movie
		// -----------------------------------------------------------------------------
		togglePlay = function(localdata) {
			var video_element = localdata.video_element;

			if (video_element[0].paused === false) {
				video_element.trigger('pause');
				localdata.controls.play.css({
					'background-position': '0 0'
				});
				localdata.controls.play.attr({
					'title': 'Play [F8 or Ctrl + k]'
				});
			} else {
				if (video_element[0].currentTime >= 1) {
					video_element[0].currentTime = video_element[0].currentTime - 0.25;
				}
				video_element.trigger('play');
				localdata.controls.play.css({
					'background-position': '-24px 0'
				});
				localdata.controls.play.attr({
					'title': 'Pause [F8 or Ctrl + k]'
				});
			}
		},

		// -----------------------------------------------------------------------------
		// stopPlay: stop the movie
		// -----------------------------------------------------------------------------
		stopPlay = function(localdata) {
			var video_element = localdata.video_element;
			video_element.trigger('pause');
			localdata.controls.play.css({
				'background-position': '0 0'
			});
			video_element[0].currentTime = 0;
		},
		// -----------------------------------------------------------------------------
		// changeVolume: from quiet to loud
		// -----------------------------------------------------------------------------
		changeVolume = function(localdata) {
			var video_element = localdata.video_element;
			var av_vol = video_element[0].volume;
			//alert(av_vol);
			if (av_vol === 0 || video_element[0].muted) {
				localdata.controls.av_volumeBar25.css({
					'background-position': '-24px -120px'
				});
				localdata.controls.av_volumeBar50.css({
					'background-position': '-24px -144px'
				});
				localdata.controls.av_volumeBar75.css({
					'background-position': '-24px -168px'
				});
				localdata.controls.av_volumeBar100.css({
					'background-position': '-24px -192px'
				});
			} else if (av_vol > 0 && av_vol <= 0.25) {
				localdata.controls.av_volumeBar25.css({
					'background-position': '0px -120px'
				});
				localdata.controls.av_volumeBar50.css({
					'background-position': '-24px -144px'
				});
				localdata.controls.av_volumeBar75.css({
					'background-position': '-24px -168px'
				});
				localdata.controls.av_volumeBar100.css({
					'background-position': '-24px -192px'
				});
			} else if (av_vol > 0.25 && av_vol <= 0.5) {
				localdata.controls.av_volumeBar25.css({
					'background-position': '0px -120px'
				});
				localdata.controls.av_volumeBar50.css({
					'background-position': '0px -144px'
				});
				localdata.controls.av_volumeBar75.css({
					'background-position': '-24px -168px'
				});
				localdata.controls.av_volumeBar100.css({
					'background-position': '-24px -192px'
				});
			} else if (av_vol > 0.5 && av_vol <= 0.75) {
				localdata.controls.av_volumeBar25.css({
					'background-position': '0px -120px'
				});
				localdata.controls.av_volumeBar50.css({
					'background-position': '0px -144px'
				});
				localdata.controls.av_volumeBar75.css({
					'background-position': '0px -168px'
				});
				localdata.controls.av_volumeBar100.css({
					'background-position': '-24px -192px'
				});
			} else if (av_vol > 0.75 && av_vol <= 1) {
				localdata.controls.av_volumeBar25.css({
					'background-position': '0px -120px'
				});
				localdata.controls.av_volumeBar50.css({
					'background-position': '0px -144px'
				});
				localdata.controls.av_volumeBar75.css({
					'background-position': '0px -168px'
				});
				localdata.controls.av_volumeBar100.css({
					'background-position': '0px -192px'
				});
			}
		},
		// -----------------------------------------------------------------------------
		// toggleMute: turns the sound off / on
		// -----------------------------------------------------------------------------
		toggleMute = function(localdata) {
			var video_element = localdata.video_element;
			video_element[0].muted = !video_element[0].muted;
			if (video_element[0].muted) {
				localdata.controls.av_volumeSpeaker.css({
					'background-position': '-24px -96px'
				});
				localdata.controls.av_volumeSpeaker.attr({
					'title': 'Sound on'
				});
				changeVolume(localdata);
				return video_element[0].muted;
			} else {
				localdata.controls.av_volumeSpeaker.css({
					'background-position': '0 -96px'
				});
				localdata.controls.av_volumeSpeaker.attr({
					'title': 'Mute'
				});
				changeVolume(localdata);
				return video_element[0].volume;
			}
		},
		// -----------------------------------------------------------------------------
		// toggleLoop: repeat the movie at the end
		// -----------------------------------------------------------------------------
		toggleLoop = function(localdata) {
			var video_element = localdata.video_element;

			if (localdata.controls.av_toggleLoop.hasClass('active')) {
				// activate the button
				localdata.controls.av_toggleLoop.css({
					'background-position': '0 -288px'
				});
				localdata.controls.av_toggleLoop.attr({
					'title': 'Loop off'
				});
				// check if we have to loop the whole movie or just the sequence (if the sequence marker are visible)
				if (localdata.projector.av_markerIn.css('display') == 'none' || localdata.projector.av_markerOut.css('display') == 'none') {
					video_element.attr({
						'loop': 'loop'
					});
					// return sequence loop: false
					return false;
				} else {
					// in this case we just have to loop the sequence: true!
					return true;
				}
			} else {
				// deactivate the button
				localdata.controls.av_toggleLoop.css({
					'background-position': '-24px -288px'
				});
				localdata.controls.av_toggleLoop.attr({
					'title': 'Loop on'
				});
				// deactivate all loops
				video_element.removeAttr('loop');
				// return sequence loop: false
				return false;
			}

		},
		// -----------------------------------------------------------------------------
		// toggleAmbientLight: turns the movie ambient light off / on
		// -----------------------------------------------------------------------------
		toggleAmbientLight = function(localdata) {
			if ($('div').find('.av_overlay').length === 0) {
				$('<div>', {
					'class': 'av_overlay'
				}).appendTo('body').insertBefore(localdata.projector.av_projector);
				localdata.video_element.css({
					'-ms-filter': 'Alpha(Opacity=90)',
					'filter': 'alpha(opacity=90)',
					'-moz-opacity': '0.9',
					'opacity': '0.9'
				});
				localdata.projector.av_timeline.css({
					'-ms-filter': 'Alpha(Opacity=60)',
					'filter': 'alpha(opacity=60)',
					'-moz-opacity': '0.6',
					'opacity': '0.6'
				});
				localdata.projector.av_controls.css({
					'-ms-filter': 'Alpha(Opacity=30)',
					'filter': 'alpha(opacity=30)',
					'-moz-opacity': '0.3',
					'opacity': '0.3'
				});
				localdata.controls.av_ambiLight.css({
					'background-position': '0 -216px',
					'-ms-filter': 'Alpha(Opacity=90)',
					'filter': 'alpha(opacity=90)',
					'-moz-opacity': '0.9',
					'opacity': '0.9'
				}).attr({
					'title': 'Light on'
				});
			} else {
				localdata.controls.av_ambiLight.css({
					'background-position': '-24px -216px'
				}).attr({
					'title': 'Light off'
				});
				localdata.video_element.css({
					'-ms-filter': 'Alpha(Opacity=100)',
					'filter': 'alpha(opacity=100)',
					'-moz-opacity': '1',
					'opacity': '1'
				});
				localdata.projector.av_timeline.css({
					'-ms-filter': 'Alpha(Opacity=100)',
					'filter': 'alpha(opacity=100)',
					'-moz-opacity': '1',
					'opacity': '1'
				});
				localdata.projector.av_controls.css({
					'-ms-filter': 'Alpha(Opacity=100)',
					'filter': 'alpha(opacity=100)',
					'-moz-opacity': '1',
					'opacity': '1'
				});
				$('.av_overlay').remove();
			}
		},
		// -----------------------------------------------------------------------------
		// togglePreview: turns the preview frame panel off and show subtitles or similar
		// -----------------------------------------------------------------------------
		togglePreview = function(localdata) {
			// preview frame: new version (2014-01-16) would be on the left side !!!!!!!!!
			/*
			if (localdata.projector.av_preview_stripe.hasClass('showframes')) {
				localdata.projector.av_preview_stripe.toggleClass('showframes');
				localdata.projector.av_preview_stripe.css({
					'display': 'none'
				});
				localdata.projector.av_subtitle.css({
					'display': 'block',
					'overflow': 'auto'
				});
				localdata.controls.av_togglePreview.css({
					'background-position': '0 -240px'
				});
				localdata.controls.av_togglePreview.attr({
					'title': 'Frames on'
				});
				animateSubtitle(localdata, SALSAH.timecode2seconds(localdata.projector.av_curTime.text()));

			} else {
				localdata.projector.av_preview_stripe.toggleClass('showframes');
				localdata.projector.av_subtitle.css({
					'display': 'none'
				});
				localdata.projector.av_preview_stripe.css({
					'display': 'block'
				});
				localdata.controls.av_togglePreview.css({
					'background-position': '-24px -240px'
				});
				localdata.controls.av_togglePreview.attr({
					'title': 'Frames off'
				});
				animateFrames(localdata, SALSAH.timecode2seconds(localdata.projector.av_curTime.text()));
			}
			*/
		},

		// -----------------------------------------------------------------------------
		// jumpHere: go to the defined time
		// -----------------------------------------------------------------------------
		jumpHere = function(video_element, timeSeconds) {
			video_element[0].currentTime = timeSeconds;
		},

		// -----------------------------------------------------------------------------
		// calcFrames: 	preload the preview images (frames)
		//				and return some information about the previews (matrix image)
		// -----------------------------------------------------------------------------
		calcFrames = function(duration, time, frames) {
			var curMatrixNr,
				lastMatrixNr,
				curMatrix,
				matrixWidth,
				matrixHeight,
				lastFrameNr,
				lastMatrixLines,
				matrixSize,
				curFrameNr,
				curLineNr,
				curColNr,
				curFramePos;

			curMatrixNr = Math.floor(time / 360);
			if(curMatrixNr < 0) curMatrixNr = 0;

			lastMatrixNr = Math.floor(duration / 360);

			curMatrix = frames.location + '&matrix=' + curMatrixNr;

			matrixWidth = Math.round(frames.width * 6);

			// the last matrix file could have another dimension size...
			if(curMatrixNr < lastMatrixNr) {
				matrixHeight = Math.round(frames.height * 6);
			} else {
				lastFrameNr = Math.round(duration / 10);
				lastMatrixLines = Math.ceil((lastFrameNr - (lastMatrixNr * 36)) / 6);
				matrixHeight = Math.round(frames.height * lastMatrixLines);
			}

			matrixSize = matrixWidth + 'px ' + matrixHeight + 'px';

			curFrameNr = Math.floor(time / 10) - Math.floor(36 * curMatrixNr);
			if(curFrameNr < 0) curFrameNr = 0;
			curLineNr = Math.floor(curFrameNr / 6);
			curColNr = Math.floor(curFrameNr - (curLineNr * 6));
			curFramePos = '-' + (curColNr * frames.width) + 'px -' + (curLineNr * frames.height) + 'px';

			return {
				current: curMatrix,
				size: matrixSize,
				framepos: curFramePos
			};

		},
		// -----------------------------------------------------------------------------
		// showFrames: loads the preview images (frames) and show them
		// -----------------------------------------------------------------------------
		showFrames = function(localdata) {
			var time = 15;
			var interval = 30;

			// start with an empty vertical timeline with placeholder for the matrix
			while(time < localdata.movieInfo.durationTime) {
				tc = SALSAH.seconds2timecode(time);

				var frames = {
					width: localdata.movieInfo.frameWidth,
					height: localdata.movieInfo.frameHeight,
					location: localdata.settings.frameslocation
				};
				var matrix = calcFrames(localdata.movieInfo.durationTime, time, frames);

				localdata.projector.av_preview
					.append(
						$('<li>').addClass('av_frame').attr({'frame-time': time})
						.css({
							width: frames.width + 'px',
							height: frames.height + 'px',
							content: '',
							'background-image': 'url(' + matrix.current + ')',
							'background-position': matrix.framepos,
							'background-size': matrix.size,
							repeat: 'none'
						})
						.append($('<p>').html(tc).css({
							top: frames.height - 12 + 'px'
						}))
					);
				time += interval;
			}

			localdata.video_element.css({
				'-ms-filter': 'Alpha(Opacity=100)',
				'filter': 'alpha(opacity=100)',
				'-moz-opacity': '1',
				'opacity': '1'
			}).attr({
				'poster': localdata.settings.frameslocation + '&matrix=0'
			});

			localdata.projector.av_preview.find(
				$('li')
				.on('click', function(e) {
					frameTime = $(this).attr("frame-time");
					jumpHere(localdata.video_element, frameTime);
				})
				.on('dblclick', function(e) {
					frameTime = $(this).attr("frame-time");
					jumpHere(localdata.video_element, frameTime);
					togglePlay(localdata);
				})
			);
		},
		// -----------------------------------------------------------------------------
		// animateFrames: loads the preview images (frames) and show them
		// -----------------------------------------------------------------------------
		animateFrames = function(localdata, curTime) {
			// preview frame: new version (2014-01-16) would be on the left side !!!!!!!!!
			var previewFrames = [];
			localdata.projector.av_preview.find($('li')).each(function(i, frame) {
				previewFrames.push($(this).attr('frame-time'));
			});

			for (var i = 0; i < (previewFrames.length - 1); i += 1) {
				if (curTime >= parseFloat(previewFrames[i]) &&
					curTime < parseFloat(previewFrames[i + 1])) {
					//localdata.projector.av_preview_frame[i].css({border:'1px solid red'});
					localdata.projector.av_preview.animate({
						scrollTop: Math.round((i - 2) * (localdata.movieInfo.frameHeight + 5))
					});
					localdata.projector.av_preview.stop(true, true);
				} else if (curTime === 0) {
					localdata.projector.av_preview.scrollTop(0);
				}
			}
		},

		// -----------------------------------------------------------------------------
		// showTimeInfo: show the time when the mouse is on the timeline
		// -----------------------------------------------------------------------------
		showTimeInfo = function(localdata, event, frameTime) {
			var chgTime,
				posX,
				posY = localdata.projector.av_transportbar.position().top - 26;

			if (typeof frameTime === 'undefined') {
				posX = event.pageX - localdata.projector.av_transportbar.offset().left - 2;
				chgTime = posX * localdata.movieInfo.secondsPerPixel;
			} else {
				posX = frameTime / localdata.movieInfo.secondsPerPixel;
				chgTime = frameTime;
			}

			var frames = {
				width: localdata.movieInfo.frameWidth,
				height: localdata.movieInfo.frameHeight,
				location: localdata.settings.frameslocation
			};
			var matrix = calcFrames(localdata.movieInfo.durationTime, chgTime, frames);

			localdata.projector.av_tooltip.css({
				width: frames.width + 'px',
				height: frames.height + 'px',
				left: Math.round(posX - (frames.width / 2)) + 'px',
				top: posY - frames.height + 8 + 'px',
				'background-image': 'url(' + matrix.current + ')',
				'background-position': matrix.framepos,
				'background-size': matrix.size,
				repeat: 'none'
			});
			var tc = SALSAH.seconds2timecode(chgTime);

			localdata.projector.av_timeinfo.css({
				width: localdata.movieInfo.frameWidth + 'px',
				height: localdata.movieInfo.frameHeight + 6 + 'px'
			})
			.html(
				// take the same style value as in the vertical preview timeline! with pos: rel
				'<p style="top:' + Math.round(localdata.movieInfo.frameHeight - 12) + 'px">' + tc + '</p>'
			);

			return chgTime;
		},
		// -----------------------------------------------------------------------------
		/* just for the demo — some subtitles for the movie "Umbruch im Märchenwald" */
		showSubtitle = function(localdata) {
			var seq_id,
				restype_name,
				restype;
			var seq = {};

			var video_element = localdata.video_element;

			localdata.projector.av_subtitle_text = [];
			/*
			// example for subtitle (sequences)
			localdata.settings.timeline.html(
					localdata.projector.av_subtitle_text[0] = $('<p>').addClass('c0 cue').attr({
						'data-time': '0',
						'aria-live': 'rude',
						'title': '00:00:00'
					}).text('')
				)
				.append(localdata.projector.av_subtitle_text[1] = $('<p>').addClass('c1 cue').attr({
					'data-time': '15',
					'aria-live': 'rude',
					'title': '00:00:15'
				}).text('TestText bei 15 Sekunden (00:00:15) '));
			*/
			// we need the following ApiGet request in avtranscript — not here!? ak: 2015-05-19
				SALSAH.ApiGet('resources', localdata.settings.res_id, function(res) {
					var compop = ['EXISTS', 'EQ'];
					// get the searchprop_id for interval and sequence_of
					var searchprop = ['325', '326'];		// <<<<<<<<< QUICK HACK!!! LIVE: 390!!!
					var searchval = ['', localdata.settings.res_id];
					var showprops = [];
						showprops.push({propname: 'salsah:interval', proplabel: 'Zeit Interval'});		// <<<<<<<<< QUICK HACK!!!
						showprops.push({propname: 'bmf:episode', proplabel: 'Episode'});		// <<<<<<<<< QUICK HACK!!!

					var searchparams = {
						filter_by_restype: '77',		// <<<<<<<<< QUICK HACK!!!
						filter_by_project: res.resinfo.project_id,
						property_id: searchprop,
						compop: compop,
						searchval: searchval,
						show_nrows: '-1',
						display_type: 'sequence',
						showprops: showprops
					};

					$.extsearch.perform_search(localdata.settings.timeline, searchparams);
				});

				return localdata.projector.av_subtitle_text;

		},

		animateSubtitle = function(localdata, curTime) {
			var offsetText,
				offsetSubtitle,
				offsetTextPos = [],
				scrollToNextPos,
				i;
			for (i = 0; i < (localdata.projector.av_subtitle_text.length - 1); i += 1) {
				offsetTextPos[i] = localdata.projector.av_subtitle_text[i].offset().top;

				if (curTime >= parseFloat(localdata.projector.av_subtitle_text[i].attr("data-time")) && curTime < parseFloat(localdata.projector.av_subtitle_text[i + 1].attr("data-time")) && localdata.projector.av_subtitle.css('display') === 'block') {
					//offsetText = localdata.projector.av_subtitle_text[i].offset();
					//offsetSubtitle = localdata.projector.av_subtitle.offset();
					//scrollToNextPos = offsetSubtitle.top - offsetText.top;
					scrollToNextPos = (i * 34) - 34; //offsetTextPos[i];
					//.height() * i)) - localdata.projector.av_subtitle.offset().top;

					//localdata.projector.tout.text(i + ': ' + localdata.projector.av_subtitle_text[i].attr("data-time") + ' vs ' + localdata.projector.av_subtitle_text[i + 1].attr("data-time") + ', scrollTo ' + scrollToNextPos);

					localdata.settings.timeline.scrollTop(scrollToNextPos);

					/*
					 localdata.projector.av_subtitle.animate({
					 scrollTop: scrollToNextPos
					 }, 1000);

					 {
					 top: "-=" + parseFloat(offsetText.top - localdata.projector.av_subtitle_text[i].height()) + "px"
					 });
					 localdata.projector.av_subtitle.stop(true, true);
					 */
				}
			}
		},

		// -----------------------------------------------------------------------------
		// SALSAH Movie Player: generate the whole projector
		// -----------------------------------------------------------------------------
		methods = {
			/*========================================================================*/
			init: function(options) {
				return this.each(function() {
					var $this = $(this),
						localdata = {},
						//	fileext = '',
						video_element;
					//	buttons = {},
					//	timeInfo = {},
					//    var transcript = {};

					var mouseOn = {
						av_timeline: false,
						av_preview: false
					};
					//var movieInfo = {};
					localdata.movieInfo = {}; // all the info (size, time etc.) about the movie; s.a. function: movieInformation

					localdata.projector = {}; // to save all other elements of the whole player
					localdata.timeline = {};
					localdata.timeline.h = {}; // all elements of the horizontal timeline
					localdata.timeline.v = {}; // all elements of the vertical timeline
					localdata.controls = {}; // all control elements (buttons)

					localdata.settings = {
						res_id: '',
						videolocation: '', // must be given!!
						frameslocation: '', //
						posterframe: '', // here we will need the time of a frame as an integer
						fps: '',
						duration: '',
						playTime: 0,
						avplayer: undefined,
						avtranscript: undefined,
						timeline: undefined
							//buttonsCB: '',
							//video_elementCB: ''
					};

					//localdata.projector.av_marker = [];
					//localdata.settings = {'value': 1};
					$.extend(localdata.settings, options);
					$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

					$this.html(
							// -----------------------------------------------------------------------------
							// projector: screen with the video, the timeline and the control buttons
							// -----------------------------------------------------------------------------

							localdata.projector.av_projector = $('<div>').addClass('av_projector ' + localdata.settings.res_id)
							.append(localdata.projector.av_preload = $('<div>').addClass('av_preload'))
							.append(localdata.projector.av_player = $('<div>').addClass('av_leftside')
								.append(
									localdata.projector.av_screen = $('<div>').addClass('av_screen')
									.append(localdata.video_element = video_element = $('<video>'))
								)
								// -----------------------------------------------------------------------------
								// timeInfo: timeline with preview frames or subtitles
								// -----------------------------------------------------------------------------
								.append(localdata.projector.av_subtitle = $('<div>').addClass('av_subtitle'))
								.append(
									localdata.projector.av_timeline = $('<div>').addClass('av_timeline horizontal')

									.append(
										localdata.projector.av_transportbar = $('<div>').addClass('av_transportbar')
										.append(localdata.projector.av_tooltip = $('<div>').addClass('av_timeinfo av_tooltip')

											.append(localdata.projector.av_timeinfo = $('<div>').addClass('av_timecontent'))
											.append($('<span>').addClass('arrow down'))
										)
										.append(localdata.projector.av_markerIn = $('<div>').addClass('av_marker start').attr({
												'marker-time': '0'
											})
											.append($('<span>').addClass('av_flag left'))
										)

										.append(localdata.projector.av_markerOut = $('<div>').addClass('av_marker end').attr({
												'marker-time': '0'
											})
											.append($('<span>').addClass('av_flag right'))
										)
										.append(localdata.projector.av_position = $('<div>').addClass('av_position')
											.append(localdata.projector.av_curPosition = $('<div>').addClass('av_curPosition'))
										)
									)
									.append($('<div>').addClass('av_timeline_block left'))
									.append($('<div>').addClass('av_timeline_block right'))
									/*

									.append(localdata.projector.av_preview = $('<div>').addClass('av_preview')
										.append(localdata.projector.av_preview_stripe = $('<ul>').addClass('av_preview_stripe showframes'))
										.append(localdata.projector.av_subtitle = $('<div>').addClass('av_subtitle'))
									)
									*/
								)
								// -----------------------------------------------------------------------------
								// buttons: control buttons
								// -----------------------------------------------------------------------------
								.append(localdata.projector.av_controls = $('<div>').addClass('av_controls')
									.append($('<div>').addClass('av_left')
										.append(localdata.controls.stop = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'STOP',
											'title': 'Stop',
											'accesskey': 'z'
										}).addClass('av_stop'))
										.append(localdata.controls.play = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'PLAY',
											'title': 'Play [F8 or Ctrl + k]',
											'accesskey': 'k'
										}).addClass('av_play'))
									)
									.append($('<div>').addClass('av_right')
										.append(localdata.controls.jumpBack = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'JUMP BACK',
											'title': 'Go to the start [Ctrl + i]',
											'accesskey': 'i'
										}).addClass('av_jumpBack'))
										.append(localdata.controls.rewind = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'REWIND',
											'title': '(Fast) Rewind [F7 or Ctrl + j]',
											'accesskey': 'j'
										}).addClass('av_fastRewind'))
									)
									.append(
										localdata.projector.av_time = $('<div>').addClass('av_center')
										.append($('<div>').addClass('av_time')
											.append(localdata.projector.av_curTime = $('<span>').addClass('av_curTime').text('00:00:00:00'))
										)
										.append($('<div>').addClass('av_time')
											.append(localdata.projector.av_duration = $('<span>').addClass('av_durTime').text('00:00:00:00'))
										)
									)
									.append(
										$('<div>').addClass('av_left')
										.append(localdata.controls.forward = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'FORWARD',
											'title': '(Fast) Forward [F9 or Ctrl + l]',
											'accesskey': 'l'
										}).addClass('av_fastForward'))
										.append(localdata.controls.jumpForward = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'JUMP FORWARD',
											'title': 'Go to the end [Ctrl + o]',
											'accesskey': 'o'
										}).addClass('av_jumpForward'))
									)
									.append($('<div>').addClass('av_right')
										//.append($('<input>').attr({'type': 'image', 'src': SITE_URL + '/app/icons/0.gif', 'alt': 'FULLSCREEN', 'title': 'Fullscreen'}).addClass('av_screensize'))
										.append(
											localdata.controls.av_volume = $('<div>').addClass('av_volume')
											.append(localdata.controls.av_volumeSpeaker = $('<input>').attr({
												'type': 'image',
												'src': SITE_URL + '/app/icons/0.gif',
												'alt': 'SOUND',
												'title': 'Mute'
											}).addClass('av_speaker'))
											.append(localdata.controls.av_volumeBar25 = $('<input>').attr({
												'type': 'image',
												'src': SITE_URL + '/app/icons/0.gif',
												'alt': 'VOLUME',
												'title': 'Volume 25%'
											}).addClass('av_volumeBar vol25'))
											.append(localdata.controls.av_volumeBar50 = $('<input>').attr({
												'type': 'image',
												'src': SITE_URL + '/app/icons/0.gif',
												'alt': 'VOLUME',
												'title': 'Volume 50%'
											}).addClass('av_volumeBar vol50'))
											.append(localdata.controls.av_volumeBar75 = $('<input>').attr({
												'type': 'image',
												'src': SITE_URL + '/app/icons/0.gif',
												'alt': 'VOLUME',
												'title': 'Volume 75%'
											}).addClass('av_volumeBar vol75'))
											.append(localdata.controls.av_volumeBar100 = $('<input>').attr({
												'type': 'image',
												'src': SITE_URL + '/app/icons/0.gif',
												'alt': 'VOLUME',
												'title': 'Volume 100%'
											}).addClass('av_volumeBar vol100'))
										)
									)
								)
							)
							.append(localdata.projector.av_rightside = $('<div>').addClass('av_rightside active')
								.append($('<div>').addClass('av_timeline vertical')
									.append(localdata.projector.av_preview = $('<div>').addClass('av_preview'))
									//.append(localdata.projector.av_preview_stripe = $('<ul>').addClass('av_preview_stripe showframes'))
									.append(localdata.projector.av_preview_zoom = $('<div>').addClass('av_preview_zoom'))
								)
								.append(localdata.projector.av_controlsPlus = $('<div>').addClass('av_controls plus')

									.append(
										$('<div>').addClass('av_center')
										.append(localdata.controls.av_ambiLight = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'LIGHT OFF',
											'title': 'Light off'
										}).addClass('av_ambiLight'))
										.append(localdata.controls.av_togglePreview = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'FRAMES OFF',
											'title': 'Frames off'
										}).addClass('av_subtitles'))
										.append(localdata.controls.av_toggleLoop = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'LOOP',
											'title': 'Loop'
										}).addClass('av_movLoop')).on('click', function() {
											localdata.controls.av_toggleLoop.toggleClass('active');
											localdata.timeline.h.checkSeqLoop = toggleLoop(localdata);
										})
										.append(localdata.controls.av_fullScreen = $('<input>').attr({
											'type': 'image',
											'src': SITE_URL + '/app/icons/0.gif',
											'alt': 'FULLSCREEN',
											'title': 'Fullscreen'
										}).addClass('av_fullScreen'))
									)

									.append(localdata.controls.av_chooseQuality = $('<div>').addClass('av_chooseQuality')
										.append($('<ul>').addClass('av_projectQuality')
											.append($('<li>').text('HD'))
											.append($('<li>').text('DVD'))
											.append($('<li>').text('VHS'))
										)
									)
								)
							)
							.append(
								localdata.controls.av_moreControls = $('<span>').attr({
									'title': 'more controls'
								}).addClass('av_moreControls')
								.append(localdata.controls.av_moreControlsIcon = $('<span>').addClass('ml left'))
							)
						)
						.append(localdata.projector.end_of = $('<div>').addClass('clearFloat'));
					// -----------------------------------------------------------------------------
					// end of movie player framework; now get some action
					// -----------------------------------------------------------------------------


					// -----------------------------------------------------------------------------
					// video_element: get the movie file with the correct extension
					// -----------------------------------------------------------------------------
					// var video = {};
					var video = chooseExtension(localdata.video_element);
					video_element.attr({
						'src': localdata.settings.videolocation + '&format=' + video.ext,
						'type': video.mime,
						'poster': localdata.settings.frameslocation + '&matrix=0',
						'preload': 'auto'
							//'autoplay': 'autoplay'
					});

					/*
					video_element.on('progress', function() {
						// preparations / test for a progress bar
						for(i=0; i<=100; i++){
							localdata.projector.end_of.append('<p>').html(i);
						}
					});
					*/
					/*
					video_element.on('progress', function() {
						transcript.av_textarea.text(console.log(video_element[0].buffered.end(0) / video_element[0].duration));
					});
					*/
					video_element.on('loadstart', function() {
						localdata.controls.av_moreControls.click();

						//						var curTime = video_element[0].currentTime; // or 0
						// set the volume
						video_element[0].volume = 0.5;
						localdata.controls.av_volumeSpeaker.attr({
							'title': 'Mute'
						});
						changeVolume(localdata);
					});

					video_element.on('loadedmetadata', function() {
						localdata.movieInfo = movieInformation(localdata);
						localdata.video_element.css({
							'-ms-filter': 'Alpha(Opacity=20)',
							'filter': 'alpha(opacity=20)',
							'-moz-opacity': '0.2',
							'opacity': '0.2'
						});

						// set the time information
						localdata.projector.av_curTime.html(SALSAH.seconds2timecode('0', localdata.settings.fps));
						localdata.projector.av_duration.html(SALSAH.seconds2timecode(localdata.settings.duration, localdata.settings.fps));

						localdata.controls.jumpBack.attr({
							'marker-time': '0'
						});
						localdata.controls.jumpForward.attr({
							'marker-time': localdata.settings.duration
						});
						localdata.projector.av_markerOut.attr({
							'marker-time': localdata.settings.duration
						});
						jumpHere(video_element, localdata.settings.playTime);
					});
					video_element.on('loadeddata', function() {
						showFrames(localdata); // incl. preloading all other frames
						animateFrames(localdata, localdata.settings.playTime);
					});
					/* +_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+ */
					/* some tests to find out, when the movie is ready */
					/* +_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+ */
					video_element.on('canplaythrough', function() {
						//alert('canplaythrough now');
					});


					localdata.controls.play.on('click', function() {
						togglePlay(localdata);
					});
					localdata.projector.av_screen.on('click', function() {
						togglePlay(localdata);
					});
					localdata.controls.stop.on('click', function() {
						stopPlay(localdata);
					});
					localdata.controls.forward.on('click', function() {
						video_element[0].currentTime = video_element[0].currentTime + 15;
					});

					var timeout;
					/*
					var mousedown,
						timeout = 0,
						setInterval,
						clearInterval;
					*/
					localdata.controls.forward.on({
						'mousedown': function() {
							timeout = setInterval(function() {
								video_element[0].currentTime = video_element[0].currentTime + 15;
							}, 250);
						},
						'mouseup': function() {
							clearInterval(timeout);
						}
					});
					localdata.controls.rewind.on('click', function() {
						video_element[0].currentTime = video_element[0].currentTime - 15;
					});
					localdata.controls.rewind.on({
						'mousedown': function() {
							timeout = setInterval(function() {
								video_element[0].currentTime = video_element[0].currentTime - 15;
							}, 250);
						},
						'mouseup': function() {
							clearInterval(timeout);
						}
					});
					localdata.controls.jumpForward.on('click', function() {
						video_element[0].currentTime = localdata.controls.jumpForward.attr('marker-time');
						/*
					searchNextMarker = (video_element[0].currentTime).toFixed(0);
					if(localdata.projector.av_marker.length > searchNextMarker){
						video_element[0].currentTime = localdata.projector.av_marker.length -1;
					} else {
						video_element[0].currentTime = video_element[0].currentTime + (localdata.movieInfo.interval * (localdata.movieInfo.countVisibleFrames/2));
					}
					*/
						//	video_element[0].currentTime = video_element[0].currentTime + (localdata.movieInfo.interval * (localdata.movieInfo.countVisibleFrames / 2));
					});
					localdata.controls.jumpBack.on('click', function() {
						video_element[0].currentTime = localdata.controls.jumpBack.attr('marker-time');
						//(localdata.movieInfo.interval * (localdata.movieInfo.countVisibleFrames / 2));
					});
					// Volume: Click or Scroll to change the volume
					localdata.controls.av_volumeSpeaker.on('click', function() {
						if (video_element[0].volume < 0.25) {
							video_element[0].volume = 0.25;
						}
						toggleMute(localdata);
					});
					localdata.controls.av_volumeBar25.on('click', function() {
						if (video_element[0].muted) {
							toggleMute(localdata);
						}
						video_element[0].volume = 0.25;
						changeVolume(localdata);
					});

					localdata.controls.av_volumeBar50.on('click', function() {
						if (video_element[0].muted) {
							toggleMute(localdata);
						}
						video_element[0].volume = 0.5;
						changeVolume(localdata);
					});

					localdata.controls.av_volumeBar75.on('click', function() {
						if (video_element[0].muted) {
							toggleMute(localdata);
						}
						video_element[0].volume = 0.75;
						changeVolume(localdata);
					});

					localdata.controls.av_volumeBar100.on('click', function() {
						if (video_element[0].muted) {
							toggleMute(localdata);
						}
						video_element[0].volume = 1;
						changeVolume(localdata);
					});

					localdata.controls.av_volume.mousewheel(function(event, delta) {
						if (delta < 0) {
							if (video_element[0].volume < 1) {
								if (video_element[0].muted) {
									toggleMute(localdata);
								}
								video_element[0].volume = (video_element[0].volume + 0.05).toFixed(2);
								changeVolume(localdata);
							}
						} else {
							if (video_element[0].volume > 0 && video_element[0].muted !== true) {
								video_element[0].volume = (video_element[0].volume - 0.05).toFixed(2);
								changeVolume(localdata);
								if (video_element[0].volume <= 0.1) {
									toggleMute(localdata);
									video_element[0].muted = true;
								}
							}
						}
					});

					localdata.controls.av_ambiLight.on('click', function() {
						toggleAmbientLight(localdata, localdata);
					});
					localdata.controls.av_togglePreview.on('click', function() {
						togglePreview(localdata, localdata);
					});
					/*
					localdata.controls.av_toggleLoop.on('click', function () {
						toggleLoop(localdata);
					});
					*/
					/*
				 * +!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+!+
				localdata.controls.av_toggleQuality.on('click', function(event){
					toggleQualitySelection(buttons);
				});
				* wird durch Lukas' quality dropdown ersetzt
				*/
					localdata.controls.av_moreControls.on('click', function() {
						if (localdata.projector.av_rightside.hasClass('active')) {
							localdata.projector.av_rightside.toggleClass('active');
							localdata.projector.av_rightside.animate({
								'width': '-=180px',
								'margin-left': '-=2px'
							}, 'fast');
							//'left': '-=' + animateQualityLength}, 'fast');
							localdata.projector.av_controlsPlus.toggle();

							localdata.controls.av_moreControlsIcon.toggleClass('left right');
							//localdata.controls.av_moreControls.html('&gt;');
						} else {
							localdata.projector.av_rightside.toggleClass('active');
							localdata.projector.av_controlsPlus.toggle();
							localdata.projector.av_rightside.animate({
								'margin-left': '+=2px',
								'width': '+=118px'
							}, 'fast');
							localdata.controls.av_moreControlsIcon.toggleClass('right left');
							//buttons.av_chooseQuality.animate({'left': '+=' + animateQualityLength}, 'fast');
							//localdata.controls.av_moreControls.html('&lt;');
						}
					});

					if (localdata.settings.timeline !== undefined) {
						var subtitles = showSubtitle(localdata);

						localdata.settings.timeline.on('mouseover', function() {
							var i;
							for (i = 0; i < subtitles.length; i += 1) {
							//	console.log(subtitles[i]);
							}
						});
					}

					localdata.projector.av_preview.on({
						'mouseenter': function() {
							mouseOn[$(this).attr('class')] = true;
						},

						'mouseleave': function() {
							var curTime = video_element[0].currentTime;
							animateFrames(localdata, curTime);
							//animateSubtitle(localdata, curTime);
							localdata.projector.av_tooltip.css({
								display: 'none'
							});
							mouseOn[$(this).attr('class')] = false;
						}
					});

					localdata.projector.av_transportbar.on({
						'mouseenter': function() {
							mouseOn[$(this).attr('class')] = true;
							localdata.projector.av_tooltip.css({
								display: 'block'
							});


							var clicking = false;

							localdata.projector.av_curPosition.on('mousedown', function() {
								clicking = true;
								localdata.projector.av_curPosition.addClass('active');
								localdata.projector.av_markerIn.removeClass('active');
								localdata.projector.av_markerOut.removeClass('active');
							});
							localdata.projector.av_markerIn.on('mousedown', function() {
								clicking = true;
								localdata.projector.av_markerIn.addClass('active');
								localdata.projector.av_curPosition.removeClass('active');
								localdata.projector.av_markerOut.removeClass('active');
							});
							localdata.projector.av_markerOut.on('mousedown', function() {
								clicking = true;
								localdata.projector.av_markerOut.addClass('active');
								localdata.projector.av_curPosition.removeClass('active');
								localdata.projector.av_markerIn.removeClass('active');
							});

							localdata.projector.av_transportbar.on('mouseup', function(event) {
								if (clicking === false) {
									var mouseTime = showTimeInfo(localdata, event);
									jumpHere(localdata.video_element, mouseTime);
								}
								clicking = false;
								localdata.projector.av_curPosition.removeClass('active');
								localdata.projector.av_markerIn.removeClass('active');
								localdata.projector.av_markerOut.removeClass('active');

							});

							localdata.projector.av_transportbar.on('mousemove', function(event) {
								if (clicking === false) {
									showTimeInfo(localdata, event);
								} else {
									var mouseTime = showTimeInfo(localdata, event);

									if (localdata.projector.av_curPosition.hasClass('active')) {
										localdata.projector.av_tooltip.css({
											display: 'none'
										});
										localdata.projector.av_curPosition.css({
											//				left: event.posX + 'px'
										});
										jumpHere(localdata.video_element, mouseTime);
									} else if (localdata.projector.av_markerIn.hasClass('active')) {
										localdata.settings.avplayer.avplayer('setMarker', 'start', mouseTime);
									} else if (localdata.projector.av_markerOut.hasClass('active')) {
										localdata.settings.avplayer.avplayer('setMarker', 'end', mouseTime);
									}
								}
							});
							//
							// preview frame: new version (2014-01-16) would be on the left side !!!!!!!!!
							//

						},
						'mouseleave': function() {
							var curTime = video_element[0].currentTime;
							animateFrames(localdata, curTime);
							//animateSubtitle(localdata, curTime);
							localdata.projector.av_tooltip.css({
								display: 'none'
							});
							mouseOn[$(this).attr('class')] = false;
						}
					});


					/* scrolling on timeline to slide through the preview frames */
					/* !!!!!!!!!!!! WORK IN PROGRESS !!!!!!!!!!!! calc the second method in animateFrames
					localdata.projector.av_timeline.on('mousewheel DOMMouseScroll', function (event) {
						var scrollTo = 0;
						event.preventDefault();
						if (event.type === 'mousewheel') {
							if (event.originalEvent.wheelDelta > 0) {

								animateFrames(localdata, localdata.settings.fps);

								//video_element[0].currentTime = video_element[0].currentTime - (1 / localdata.settings.fps); // 1 frame; replace 24 with the fps
								//localdata.movieInfo.interval;
							} else if (event.originalEvent.wheelDelta <= 0) {
								animateFrames(localdata, -localdata.settings.fps);
								//video_element[0].currentTime = video_element[0].currentTime + (1 / localdata.settings.fps);
								//localdata.movieInfo.interval;
							}
						} else if (event.type === 'DOMMouseScroll') {
							if (event.originalEvent.detail > 0) {
								animateFrames(localdata, -localdata.settings.fps);
								//video_element[0].currentTime = video_element[0].currentTime + (1 / localdata.settings.fps);
								//localdata.movieInfo.interval;
							} else if (event.originalEvent.detail <= 0) {
								animateFrames(localdata, localdata.settings.fps);
								//video_element[0].currentTime = video_element[0].currentTime - (1 / localdata.settings.fps);
								//localdata.movieInfo.interval;
							}
						}
					});
					!!!!!!!!!!!! WORK IN PROGRESS !!!!!!!!!!!! */

					localdata.projector.av_time.on('click', function() {
						togglePlay(localdata);
					});


					localdata.projector.av_time.mousewheel(function(event, delta) {
						if (delta < 0) {
							video_element[0].currentTime = video_element[0].currentTime + (1 / localdata.settings.fps);
						} else {
							video_element[0].currentTime = video_element[0].currentTime - (1 / localdata.settings.fps);
						}
					});

					video_element.on('timeupdate', function() {
						var curTime = video_element[0].currentTime,
							curTimeRound = Math.round(curTime).toFixed(3),
							getStartTime = localdata.projector.av_markerIn.attr('marker-time'),
							getEndTime = localdata.projector.av_markerOut.attr('marker-time');

						localdata.projector.av_curTime.html(SALSAH.seconds2timecode(curTime, localdata.settings.fps));

						if (curTime > (localdata.movieInfo.secondsPerPixel) && curTime <= (localdata.movieInfo.durationTime)) {
							localdata.projector.av_curPosition.css({
								'left': (curTimeRound / localdata.movieInfo.secondsPerPixel) - 0 + 'px'
							});
							localdata.projector.av_position.css({
								'width': (curTimeRound / localdata.movieInfo.secondsPerPixel) + 'px'
							});
						} else if (curTime === 0) {
							localdata.projector.av_curPosition.css({
								'left': '0px' //(curTime / localdata.movieInfo.secondsPerPixel) - 0 + 'px'
							});
							localdata.projector.av_position.css({
								'width': '0px'
							});
						}
						if (localdata.timeline.h.checkSeqLoop === true && curTime >= getEndTime) {
							// loop the sequence
							video_element[0].currentTime = getStartTime;
						}

						if (!mouseOn.av_preview) {
							animateFrames(localdata, curTime);
						}


						if (curTime === localdata.movieInfo.durationTime) {
							localdata.controls.play.css({
								'background-position': '0 0'
							});
							localdata.controls.play.attr({
								'title': 'Play'
							});
						}
					});


					localdata.settings.video_element = video_element;
				}); // end "return this.each "
			}, // end "init "


			resizeMovie: function() {
				return this.each(function() {
					var $this = $(this),
						localdata = $this.data('localdata');
					//alert('resize');
					localdata.movieInfo = movieInformation(localdata.settings.video_element, localdata);
					//showFrames(localdata.settings.video_element, localdata.settings.timeInfo, localdata.settings.frameslocation, localdata.settings.movieInfo);
				});
			},

			getCurTime: function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				return localdata.video_element[0].currentTime;
			},

			/* get the current status of the movie: curTime, is playing? etc. */
			getMovStatus: function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				var curTimeCB = localdata.video_element[0].currentTime;
				var curPlayCB = false;
				//				if(localdata.video_element)
				/* todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! todo ! */
				return {
					curTimeCB: curTimeCB,
					curPlayCB: curPlayCB
				};
			},

			controlMovie: function(option) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				switch (option) {
					case 'togglePlay':
						{
							togglePlay(localdata);
							break;
						}
					case 'rewind':
						{
							var rewindTime = localdata.video_element[0].currentTime - localdata.movieInfo.interval;
							jumpHere(localdata.video_element, rewindTime);
							break;
						}
					case 'forward':
						{
							var forwardTime = localdata.video_element[0].currentTime + localdata.movieInfo.interval;
							jumpHere(localdata.video_element, forwardTime);
							break;
						}
					case 'gotoInpoint':
						{
							var startTime = localdata.controls.jumpBack.attr('marker-time');
							jumpHere(localdata.video_element, startTime);
							break;
						}
					case 'gotoOutpoint':
						{
							var endTime = localdata.controls.jumpForward.attr('marker-time');
							jumpHere(localdata.video_element, endTime);
							break;
						}
				}
				//				return localdata.video_element;
				//				alert("play the movie with avplayer");

				//return localdata.controls;
				//alert("controlMovie " + localdata.video_element);

			},

			gotoTime: function(time_1, time_2) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				jumpHere(localdata.video_element, time_1);
//				setMarker
				var duration = time_2 - time_1;
				if (localdata.video_element[0].paused === true && duration > 0) {
					localdata.controls.play.click();
				} else if(localdata.video_element[0].paused === false && duration === 0){
					localdata.controls.play.click();
				}

			},

			showTime: function(time) {
				var $this = $(this);
				var localdata = $this.data('localdata');

				showTimeInfo(localdata, undefined, time);
				localdata.projector.av_tooltip.css({
					display: 'block'
				});
			},

			hideTime: function() {
				var $this = $(this);
				var localdata = $this.data('localdata');

				localdata.projector.av_tooltip.css({
					display: 'none'
				});
			},

			/*
			if(transcript.av_inpoint.val().length != 0){
				var startSeq = SALSAH.timecode2seconds(transcript.av_inpoint.val());
				if(startSeq > maxDuration){
					alert("The start time of the sequence is bigger than the movie duration.");
					transcript.av_inpoint.val(SALSAH.seconds2timecode(maxDuration));
					var startSeq = SALSAH.timecode2seconds(transcript.av_inpoint.val());
				}
				localdata.projector.av_markerIn.html('i' + '<br>&middot;&lt;');
				localdata.projector.av_markerIn.css({'display':'block', 'margin-left': ((startSeq / localdata.movieInfo.secondsPerPixel)-6) + 'px'});
			} else {
				localdata.projector.av_markerIn.css({'display':'none'});
			}
			if(transcript.av_outpoint.val().length != 0){
				var endSeq = SALSAH.timecode2seconds(transcript.av_outpoint.val());
				if(endSeq > maxDuration){
					alert("The end time of the sequence is bigger than the movie duration.");
					transcript.av_outpoint.val(SALSAH.seconds2timecode(maxDuration));
					var endSeq = SALSAH.timecode2seconds(transcript.av_outpoint.val());
				}
				localdata.projector.av_markerOut.html('o' + '<br>&gt;');
				localdata.projector.av_markerOut.css({'display':'block', 'margin-left': ((endSeq / localdata.movieInfo.secondsPerPixel)-12) + 'px'});
			} else {
				localdata.projector.av_markerOut.css({'display':'none'});
			}
			*/

			setMarker: function(which, time) {
				return this.each(function() {
					var $this = $(this);
					var localdata = $this.data('localdata');
					var getStartTime = localdata.projector.av_markerIn.attr('marker-time');
					var getEndTime = localdata.projector.av_markerOut.attr('marker-time');

					if (time > localdata.movieInfo.durationTime) {
						time = localdata.movieInfo.durationTime;
					}
					time = Math.round(time).toFixed(3);
					getStartTime = Math.round(getStartTime).toFixed(3);
					getEndTime = Math.round(getEndTime).toFixed(3);

					if (which === 'start') {
						if (time >= getEndTime || localdata.projector.av_markerOut.css('display') == 'none') {
							// set the end marker position
							localdata.projector.av_markerOut.css({
								'display': 'block',
								'margin-left': ((time / localdata.movieInfo.secondsPerPixel)) + 'px'
							}).attr({
								'marker-time': time
							});
							// set the jump forward value
							localdata.controls.jumpForward.attr({
								'marker-time': time,
								'title': 'go to outPoint ' + SALSAH.seconds2timecode(time)
							});
							// set the value of the end marker input field
							localdata.settings.avtranscript.avtranscript('chgEndTimeVal', time);
						}
						// set the start marker position
						localdata.projector.av_markerIn.css({
							'display': 'block',
							'margin-left': ((time / localdata.movieInfo.secondsPerPixel)) + 'px'
						}).attr({
							'marker-time': time
						});
						// set the jump back value
						localdata.controls.jumpBack.attr({
							'marker-time': time,
							'title': 'go to inPoint ' + SALSAH.seconds2timecode(time)
						});
						// set the value of the start marker input field
						localdata.settings.avtranscript.avtranscript('chgStartTimeVal', time);
					} else {
						if (time <= getStartTime || localdata.projector.av_markerIn.css('display') == 'none') {
							// set the start marker position
							localdata.projector.av_markerIn.css({
								'display': 'block',
								'margin-left': ((time / localdata.movieInfo.secondsPerPixel)) + 'px'
							}).attr({
								'marker-time': time
							});
							// set the jump back value
							localdata.controls.jumpBack.attr({
								'marker-time': time,
								'title': 'go to inPoint ' + SALSAH.seconds2timecode(time)
							});
							// set the value of the start marker input field
							localdata.settings.avtranscript.avtranscript('chgStartTimeVal', time);
						}
						// set the end marker position
						localdata.projector.av_markerOut.css({
							'display': 'block',
							'margin-left': ((time / localdata.movieInfo.secondsPerPixel)) + 'px'
						}).attr({
							'marker-time': time
						});
						// set the jump forward value
						localdata.controls.jumpForward.attr({
							'marker-time': time,
							'title': 'go to outPoint ' + SALSAH.seconds2timecode(time)
						});
						// set the value of the end marker input field
						localdata.settings.avtranscript.avtranscript('chgEndTimeVal', time);
					}
				});
			},

			getMarker: function(which) {
				if (which === 'start') {
					return localdata.projector.av_markerIn.attr('marker-time');
				} else {
					return localdata.projector.av_markerOut.attr('marker-time');
				}
			},



			anotherMethod: function() {
					return this.each(function() {
						var $this = $(this);
						var localdata = $this.data('localdata');
					});
				}
				/*========================================================================*/
		};


	$.fn.avplayer = function(method) {
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
