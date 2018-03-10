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

/******************************************************************************
 *
 * av.transcript is a jquery plugin as a tool for our movie-/audioplayer
 * - with this tool you can transcript / annotate audio-visual media
 * - you will need the functions from the av.controls plugin
 *
 *****************************************************************************/

// !+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!!+!
// NOT YET FOR PRODUCTIVE USE : 2013-11-01
// !-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!!-!

(function( $ ){

// -----------------------------------------------------------------------------
// changeMarkerValue: calculate the marker input value from seconds to timecode
// -----------------------------------------------------------------------------
	var changeMarkerValue = function (markerValue) {
		var intRegex = /^\d+$/;
		var floatRegex = /^((\d+(\.\d *)?)|((\d*\.)?\d+))$/;
		if(intRegex.test(markerValue) || floatRegex.test(markerValue)){
			return secondsTimecode(markerValue);
		}
	};

	var toggleSequenceLoop = function (localdata) {
		localdata.controls.av_toggleLoop.toggleClass('active');
		if(localdata.controls.av_toggleLoop.hasClass('active')){
			localdata.controls.av_toggleLoop.css({
				'background-position': '0 -288px'
			});
			localdata.controls.av_toggleLoop.attr({
				'title': 'Loop off'
			});
		} else {
			localdata.controls.av_toggleLoop.css({
				'background-position': '-24px -288px'
			});
			localdata.controls.av_toggleLoop.attr({
				'title': 'Loop on'
			});
		}
	};
// -----------------------------------------------------------------------------
// calcSequenceDuration: calculate the duration between in- and outpoint
// -----------------------------------------------------------------------------
	var calcSequenceDuration = function (localdata, transcript){
		var maxDuration = localdata.movieInfo.durationTime;
		var startSeq = 0;
		var endSeq = 0;


		if(transcript.mp_inpoint.val().length !== 0){
			startSeq = timecodeSeconds(transcript.mp_inpoint.val());
			if(startSeq > maxDuration){
				alert("The start time of the sequence is bigger than the movie duration.");
				transcript.mp_inpoint.val(secondsTimecode(maxDuration));
				startSeq = timecodeSeconds(transcript.mp_inpoint.val());
			}
			localdata.projector.mp_markerIn.html('i' + '<br>&middot;');
			localdata.projector.mp_markerIn.css({'display':'block', 'margin-left': ((startSeq / localdata.movieInfo.secondsPerPixel)-6) + 'px'});
		} else {
			localdata.projector.mp_markerIn.css({'display':'none'});
		}
		if(transcript.mp_outpoint.val().length !== 0){
			endSeq = timecodeSeconds(transcript.mp_outpoint.val());
			if(endSeq > maxDuration){
				alert("The end time of the sequence is bigger than the movie duration.");
				transcript.mp_outpoint.val(secondsTimecode(maxDuration));
				endSeq = timecodeSeconds(transcript.mp_outpoint.val());
			}
			localdata.projector.mp_markerOut.html('o' + '<br>&gt;');
			localdata.projector.mp_markerOut.css({'display':'block', 'margin-left': ((endSeq / localdata.movieInfo.secondsPerPixel)-12) + 'px'});
		} else {
			localdata.projector.mp_markerOut.css({'display':'none'});
		}
		if(startSeq >= 0 && endSeq >= 0){
			var sequenceDuration = Math.round(endSeq - startSeq).toFixed(2);
			if(sequenceDuration > maxDuration){
				transcript.mp_sequenceDuration.attr({'value':'too long'});
			} else {
				if(sequenceDuration > 0){
					if(transcript.mp_sequenceDuration.hasClass('mp_timeWarning')){
						transcript.mp_sequenceDuration.toggleClass('mp_timeWarning');
						localdata.projector.mp_markerIn.css({'color':'#ffffcc'});
						localdata.projector.mp_markerOut.css({'color':'#ffffcc'});
					}
					transcript.mp_sequenceDuration.attr({'value':sequenceDuration+'s'});
				} else if (sequenceDuration < 0){
					if(!transcript.mp_sequenceDuration.hasClass('mp_timeWarning')){
						transcript.mp_sequenceDuration.toggleClass('mp_timeWarning');
					}
					transcript.mp_sequenceDuration.attr({'value':'IN > OUT'});
					localdata.projector.mp_markerIn.css({'color':'#ff0000'});
					localdata.projector.mp_markerOut.css({'color':'#ff0000'});
				} else {
					if(!transcript.mp_sequenceDuration.hasClass('mp_timeWarning')){
						transcript.mp_sequenceDuration.toggleClass('mp_timeWarning');
					}
					transcript.mp_sequenceDuration.attr({'value':'IN = OUT'});
					localdata.projector.mp_markerIn.css({'color':'#ff0000'});
					localdata.projector.mp_markerOut.css({'color':'#ff0000'});
				}
			}
		}
	};



// -----------------------------------------------------------------------------
// SALSAH Movie Player: generating the whole projector
// -----------------------------------------------------------------------------
	var methods = {
	/*========================================================================*/
		init: function(options) {
			return this.each(function() {

				var $this = $(this),
				localdata = {},
				transcript = {},
				buttons = {};

				localdata.projector = {};
				localdata.controls = {};

				localdata.settings = {
					viewer: undefined,
					sequence_restype: undefined,
					defaultvalues: undefined,			// must be given!!
					videolocation: '',					// must be given!!
					frameslocation: '',					//
					posterframe: '',					//
					film_resid: undefined,				// res_id of film (must be given!)
					avplayer: undefined
				};

				localdata.projector.av_marker = [];
				//localdata.settings = {'value': 1};
				$.extend(localdata.settings, options);

				if (localdata.settings.sequence_restype === undefined) {
					alert('ERROR: Call to avtranscript() without sequence_restype!');
				}
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				$this.append(localdata.controls.av_transcript = $('<div>').addClass('av_controls')
					.append($('<div>').addClass('av_left').html('Mark a sequence'))
					.append($('<div>').addClass('av_right')
						.append($('<input>').attr({
							'type': 'image',
							'src': SITE_URL + '/app/icons/0.gif',
							'alt': 'IN',
							'title': 'Set IN-point [F2 or Alt + i]',
							'accesskey': 'i'
						}).addClass('av_setInpoint')
						.on('click', function(event){
							var curTimeStart = localdata.settings.avplayer.avplayer('getCurTime');
//							alert(localdata.settings.avplayer.avplayer('getMovStatus').curPlayCB);

							localdata.settings.avplayer.avplayer('setMarker', 'start', curTimeStart);
//							$this.resadd('setValue', {'salsah:interval': curTimeStart});
						}))
					)
					.append(
						localdata.projector.av_time = $('<div>').addClass('av_center')
							.append($('<div>').addClass('av_time')
								.append(localdata.projector.seq_duration = $('<span>').addClass('seq_duration').text('00:00:00'))
							)
					)
					.append(
						$('<div>').addClass('av_left')
							.append($('<input>').attr({
								'type': 'image',
								'src': SITE_URL + '/app/icons/0.gif',
								'alt': 'OUT',
								'title': 'Set OUT-point [F4 or Alt + o]',
								'accesskey': 'o'
							}).addClass('av_setOutpoint')
							.on('click', function(event){
								var curTimeEnd = localdata.settings.avplayer.avplayer('getCurTime');
//								$this.resadd('setValue', {'salsah:interval': '=' + curTimeEnd});
								localdata.settings.avplayer.avplayer('setMarker', 'end', curTimeEnd);
							}))
					)

				).append(
					localdata.controls.av_warning = $('<div>').addClass('av_warning')
				);




				// ! TODO ! TODO ! TODO ! TODO ! TODO ! TODO ! TODO ! TODO !
				// change $this to an other element; at the moment (2015-05-05)
				// it doesn't work with the richtext editor ;-(
				// ! TODO ! TODO ! TODO ! TODO ! TODO ! TODO ! TODO ! TODO !
				//$this
				$(document).on('keypress', function (event) {


					var curTimeStart = 0;
					// disable the default function keys appeal
					// F2 or Alt + i -> set InPoint marker


					// some tests for clicking outside a textarea or input field
	//				console.log(event);
	//				var tag = event.target.tagName.toLowerCase();
	//				if(event.keyCode === 119 && tag != 'input' && tag != 'textarea') localdata.settings.avplayer.avplayer('controlMovie', 'togglePlay'); return false;

					if(event.keyCode == 113 || (event.altKey && event.keyCode == 73)) {
						//event.keyCode = 000;
						curTimeStart = localdata.settings.avplayer.avplayer('getCurTime');			// 1. get the current time position from avplayer
						localdata.settings.avplayer.avplayer('setMarker', 'start', curTimeStart);		// 2. set a start position (visually and on jump
						return false;
					}
					// Ctrl + F2 or Ctrl + i -> go to the InPoint marker
					if (event.ctrlKey && event.keyCode == 113 || (event.ctrlKey && event.keyCode == 73)){
						localdata.settings.avplayer.avplayer('controlMovie', 'gotoInpoint');
					}
					// F4 or Alt + o -> set OutPoint marker
					if(event.keyCode == 115 || (event.altKey && event.keyCode == 79)) {
						//event.keyCode = 000;
						curTimeStart = localdata.settings.avplayer.avplayer('getCurTime');		// 1. get the current time position from avplayer
						localdata.settings.avplayer.avplayer('setMarker', 'end', curTimeStart);		// 2. set a start position (visually and on jump
						return false;
					}
					// Ctrl + F4 or Ctrl + o -> go to the OutPoint marker
					if (event.ctrlKey && event.keyCode == 39 || (event.ctrlKey && event.keyCode == 79)){
						localdata.settings.avplayer.avplayer('controlMovie', 'gotoOutpoint');
					}
					// F7 or Ctrl + j -> rewind
					if(event.keyCode == 118 || (event.ctrlKey && event.keyCode == 74)) {
						//event.keyCode = 000;
						localdata.settings.avplayer.avplayer('controlMovie', 'rewind');
						return false;
					}
					// F8 or Ctrl + k or Alt + p  -> play/pause
					if(event.keyCode == 119 || (event.ctrlKey && event.keyCode == 75) || (event.altKey && event.keyCode == 80)) {
						//event.keyCode = 000;
						localdata.settings.avplayer.avplayer('controlMovie', 'togglePlay');
						return false;
					}
					// F9 or Ctrl + l -> forward
					if(event.keyCode == 120 || (event.ctrlKey && event.keyCode == 76)) {
						//event.keyCode = 000;
						localdata.settings.avplayer.avplayer('controlMovie', 'forward');
						return false;
					}

					/*
					if (event.ctrlKey && event.keyCode==77){ // Ctrl + m = set some marker
						var markerTime = (video_element[0].currentTime).toFixed(0);
						var markerPosition = markerTime / localdata.movieInfo.secondsPerPixel;
						if(typeof localdata.projector.av_marker[markerTime] == 'undefined'){
							localdata.projector.av_transportbar.append(localdata.projector.av_marker[markerTime] = $('<div>').addClass('av_marker ' + markerTime).html('m' + '<br>|').attr({'title':SALSAH.seconds2timecode(markerTime)}).css({'display':'block', 'margin-left': (markerPosition - 6) + 'px'}));
						} else {
							alert('This marker already exists: marker time is '+ markerTime + ' (' + SALSAH.seconds2timecode(markerTime) + ')');
						}
					}
					*/
				});


				SALSAH.ApiGet('resourcetypes', localdata.settings.sequence_restype, function(data) {
					if (data.status == ApiErrors.OK) {
						$this.resadd({
							rtinfo: data.restype_info,
							props: [{/*vocabulary: 'salsah', */name: 'sequence_of', value: localdata.settings.film_resid}], // TODO: use knora-base IRI for sequence_of
							options: {no_title: true},
							viewer: localdata.settings.viewer,
							defaultvalues: localdata.settings.defaultvalues,

							on_cancel_cb: function() {
								alert('CANCEL');
							},
							on_submit_cb: function(data) {
								alert('SUBMIT');
/*
								SALSAH.ApiGet('resources', res_id, {resinfo: true, reqtype: 'context'}, function(data) {
									if (data.status == ApiErrors.OK) {
										console.log(viewer);

//										RESVIEW.setupRegionTab(data.resource_context.resinfo, viewer);
//										regionsTabOnEnterCB(metadata_area_tabs.tabs('dataHook', 'regions')); //%%%% we have to perform the callback as if we clicked on the REGION tab!
									}
									else {
										alert(data.errormsg);
									}
								});

//								RESVIEW.resetRegionDrawing(viewer);
								/* +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- */
								/* +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- */
							},
							on_error_cb: function(data) {
								alert('ERROR: ' + data.errormsg);
							}
						});

						$this.resadd('callValobjFunc', 'salsah:interval', 'setOnChangeStartCB', function(time) {
							localdata.settings.avplayer.avplayer('setMarker', 'start', time);

						});
						$this.resadd('callValobjFunc', 'salsah:interval', 'setOnChangeEndCB', function(time) {
							localdata.settings.avplayer.avplayer('setMarker', 'end', time);
						});

					}
					else {
						alert(data.errormsg);
					}
				});


/*
				$this.html(
// -----------------------------------------------------------------------------
// transcript: form for annotations and markers
// -----------------------------------------------------------------------------
						transcript.mp_transcript = $('<div>').addClass('av_transcript')
							.append(transcript.av_controls = $('<div>').addClass('av_transcriptControls')
								.append(buttons.setInpoint = $('<input>').attr({'type': 'image', 'src': SITE_URL + '/app/icons/0.gif', 'alt': 'IN', 'title': 'In [Alt + i]'}).addClass('av_setInpoint'))
								.append(transcript.av_inpoint = $('<input>').attr({'type':'text', 'placeholder':'IN [Alt + i]', 'title':'Mark the start of the sequence with [Ctrl + ,] or [Alt + i]'}).addClass('av_inpoint'))
								.append(buttons.gotoInpoint = $('<input>').attr({'type': 'image', 'src': SITE_URL + '/app/icons/0.gif', 'alt': 'START', 'title': 'go to the start'}).addClass('av_gotoInpoint'))
								.append(transcript.av_sequenceDuration = $('<input>').attr({'type':'text', 'readonly':'readonly', 'placeholder':'duration'}).addClass('av_sequenceDuration'))
								.append(buttons.gotoOutpoint = $('<input>').attr({'type': 'image', 'src': SITE_URL + '/app/icons/0.gif', 'alt': 'END', 'title': 'go to the end'}).addClass('av_gotoOutpoint'))
								.append(transcript.av_outpoint = $('<input>').attr({'type':'text', 'placeholder':'OUT [Alt + o]', 'title':'Mark the start of the sequence with [Ctrl + .] or [Alt + o]'}).addClass('av_outpoint'))
								.append(buttons.setOutpoint = $('<input>').attr({'type': 'image', 'src': SITE_URL + '/app/icons/0.gif', 'alt': 'OUT', 'title': 'Out [Alt + o]'}).addClass('av_setOutpoint'))
							)
							.append(transcript.av_property = $('<select>').attr({'placeholder':'Property'}).addClass('av_properties')
								.append($('<option>').attr({'value':'', 'disabled':'disabled', 'selected':'selected'}).text('Property'))
							)
							.append(transcript.av_headline = $('<input>').attr({'type':'text', 'placeholder':'Sequence Title'}).addClass('av_headline'))
							.append(transcript.av_textarea = $('<textarea>').addClass('av_textarea'))
							.append(transcript.av_saveText = $('<input>').attr({'type':'image', 'src': SITE_URL + '/app/icons/24x24/save.png', 'alt': 'SAVE', 'title': 'Save'}).addClass('av_saveText'))
							//.append('<br>')
							//.append(transcript.av_resetText = $('<input>').attr({'type':'image', 'src': SITE_URL + '/app/icons/24x24/delete.png', 'alt': 'VOLUME', 'title': 'Volume 25%'}).addClass('av_saveText'))

				.append($('<div>').css({'clear': 'both'}))
				);
// -----------------------------------------------------------------------------
// end of movie player framework; now get some action
// -----------------------------------------------------------------------------

				buttons.setInpoint.on('click', function(){
					setInpoint(video_element, localdata, transcript);
				});
				buttons.setOutpoint.on('click', function(){
					setOutpoint(video_element, localdata, transcript);
				});
				buttons.gotoInpoint.on('click', function(){
					gotoInpoint(video_element, localdata, transcript);
				});
				buttons.gotoOutpoint.on('click', function(){
					gotoOutpoint(video_element, localdata, transcript);
				});
*/
/*
				annotationTool.on('keydown', function(event){
					if (event.ctrlKey && event.keyCode==32){ // Ctrl + Space = Play/Pause
						togglePlay(video_element, buttons);
					}
					//in- & out-point with ctrl + , or alt + i / ctrl + . or alt + o
					if ((event.ctrlKey && event.keyCode==188)||(event.altKey && event.keyCode==73)){ // Ctrl + , = set inPoint
						setInpoint(video_element, localdata, transcript);
					}
					if ((event.ctrlKey && event.keyCode==190)||(event.altKey && event.keyCode==79)){ // Ctrl + . = set outPoint
						setOutpoint(video_element, localdata, transcript);
					}
					if (event.ctrlKey && event.keyCode==37){ // Ctrl + arrowLeft = jump to the inPoint
						gotoInpoint(video_element, localdata, transcript);
					}
					if (event.ctrlKey && event.keyCode==39){ // Ctrl + arrowRight = jump to the outPoint
						gotoOutpoint(video_element, localdata, transcript);

					}
					if (event.ctrlKey && event.keyCode==77){ // Ctrl + m = set some marker
						var markerTime = (video_element[0].currentTime).toFixed(0);
						var markerPosition = markerTime / localdata.movieInfo.secondsPerPixel;
						if(typeof localdata.projector.av_marker[markerTime] == 'undefined'){
							localdata.projector.av_transportbar.append(localdata.projector.av_marker[markerTime] = $('<div>').addClass('av_marker ' + markerTime).html('m' + '<br>|').attr({'title':SALSAH.seconds2timecode(markerTime)}).css({'display':'block', 'margin-left': (markerPosition - 6) + 'px'}));
						} else {
							alert('This marker already exists: marker time is '+ markerTime + ' (' + SALSAH.seconds2timecode(markerTime) + ')');
						}
					}
					var key = (event.keyCode ? event.keyCode : event.charCode);
					//var key = (event.which ? event.which : event.charCode);
						switch (key) {
							case 113: //F2 = rewind
								video_element[0].currentTime = video_element[0].currentTime - localdata.movieInfo.interval;
								break;
							case 119: //F8 = forward
								video_element[0].currentTime = video_element[0].currentTime + localdata.movieInfo.interval;
								break;
						}
				});

				transcript.av_inpoint.on('mousewheel DOMMouseScroll', function(event) {
					var scrollTo = 0;
					event.preventDefault();
					var maxTime = Math.floor(localdata.movieInfo.durationTime);
					var inTime = '';
					if(transcript.av_inpoint.val().length != 0){
						var inTime = SALSAH.timecode2seconds(transcript.av_inpoint.val());
					}
					if(inTime >= 0){
						if (event.type == 'mousewheel') {
							if(event.originalEvent.wheelDelta > 0 && inTime < maxTime){
								inTime++;
								transcript.av_inpoint.val(SALSAH.seconds2timecode(inTime));
							} else if (event.originalEvent.wheelDelta<=0 && inTime > 0){
								inTime--;
								transcript.av_inpoint.val(SALSAH.seconds2timecode(inTime));
							}
						} else if (event.type == 'DOMMouseScroll') {
							if(event.originalEvent.detail > 0 && inTime < maxTime){
								inTime++;
								transcript.av_inpoint.val(SALSAH.seconds2timecode(inTime));
							} else if (event.originalEvent.detail <= 0 && inTime > 0){
								inTime--;
								transcript.av_inpoint.val(SALSAH.seconds2timecode(inTime));
							}
						}
						calcSequenceDuration(localdata, transcript);
					}
				});
				transcript.av_outpoint.on('mousewheel DOMMouseScroll', function(event) {
					var scrollTo = 0;
					event.preventDefault();
					var maxTime = Math.floor(localdata.movieInfo.durationTime);
					var outTime = '';
					if(transcript.av_outpoint.val().length != 0){
						var outTime = SALSAH.timecode2seconds(transcript.av_outpoint.val());
					}
					if(outTime >= 0){
						if (event.type == 'mousewheel') {
							if(event.originalEvent.wheelDelta > 0 && outTime < maxTime){
								outTime++;
								transcript.av_outpoint.val(SALSAH.seconds2timecode(outTime));
							} else if (event.originalEvent.wheelDelta<=0 && outTime > 0){
								outTime--;
								transcript.av_outpoint.val(SALSAH.seconds2timecode(outTime));
							}
						} else if (event.type == 'DOMMouseScroll') {
							if(event.originalEvent.detail > 0 && outTime < maxTime){
								outTime++;
								transcript.av_outpoint.val(SALSAH.seconds2timecode(outTime));
							} else if (event.originalEvent.detail <= 0 && outTime > 0){
								outTime--;
								transcript.av_outpoint.val(SALSAH.seconds2timecode(outTime));
							}
						}
						calcSequenceDuration(localdata, transcript);
					}
				});
				transcript.av_inpoint.on('change', function(){
					var inpointValue = transcript.av_inpoint.val();
					var intRegex = /^\d+$/;
					var floatRegex = /^((\d+(\.\d *)?)|((\d*\.)?\d+))$/;
					if(intRegex.test(inpointValue) || floatRegex.test(inpointValue)){
						transcript.av_inpoint.val(changeMarkerValue(inpointValue));
					}
					calcSequenceDuration(localdata, transcript);
				});
				transcript.av_outpoint.on('change', function(){
					var outpointValue = transcript.av_outpoint.val();
					var intRegex = /^\d+$/;
					var floatRegex = /^((\d+(\.\d *)?)|((\d*\.)?\d+))$/;
					if(intRegex.test(outpointValue) || floatRegex.test(outpointValue)){
						transcript.av_outpoint.val(changeMarkerValue(outpointValue));
					}
					calcSequenceDuration(localdata, transcript);
				});
				*/

//                localdata.settings.video_element = video_element;

			});        // end "return this.each"
		},        // end "init"

		// change the start time value
		chgStartTimeVal: function(time) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				$this.resadd('setValue', {'salsah:interval': time});
			});
		},

		// change the end time value
		chgEndTimeVal: function(time) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				$this.resadd('setValue', {'salsah:interval': '=' + time});
			});
		},

		// check if the loop-function is active
		checkSeqLoop: function(localdata) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

				return(localdata.controls.av_toggleLoop.hasClass('active'));
/*
				if(localdata.controls.av_toggleLoop.hasClass('active')){
					return true;
				} else {
					return false;
				}
*/
			});
		},

		// on submit: append, prepend or insert a new line
		insertNewLine: function(localdata) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

			});
		},

		anotherMethod: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		}
	/*========================================================================*/
	};


	$.fn.avtranscript = function(method) {
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
