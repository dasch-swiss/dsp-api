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
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @package jqplugins
 *
 * This plugin creates an edit-form for the time/interval properties of a resource
 *
 * <pre>
 *   <em>Title:</em><div class="propedit" data-propname="title" />
 *   <em>Autor:</em><div class="propedit" data-propname="author" />
 * </pre>
 *
 * <pre>
 *   <script type="text/javascript">
 *     $('div.propedit').propedit(resdata, propdata);
 *   </script>
 * </pre>
 */

(function($) {
	var spin_up = new Image();
	spin_up.src = SITE_URL + '/app/icons/up.png';

	var spin_down = new Image();
	spin_down.src = SITE_URL + '/app/icons/down.png';

	var create_time_entry = function(ele, time, onChangeCB, type) {
		var spin_up_hour,
			spin_up_min,
			spin_up_sec,
			timesel,
			spin_down_hour,
			spin_down_min,
			spin_down_sec;
			if (type === undefined) type = '';
		ele.append(
			$('<div>').addClass('inputTime ' + type)
			.append(spin_up_hour = $('<span>').addClass('ml up').css({
				position: 'absolute',
				'margin-left': '-4px'
			}).on('click', function(event) {
				time = SALSAH.timecode2seconds(timesel.val()) + 3600.0;
				timesel.val(SALSAH.seconds2timecode(time));
				if (typeof onChangeCB === 'function') {
					onChangeCB(time);
				}
			}))
			.append(spin_up_min = $('<span>').addClass('ml up').css({
				position: 'absolute',
				'margin-left': '18px'
			}).on('click', function(event) {
				time = SALSAH.timecode2seconds(timesel.val()) + 60.0;
				timesel.val(SALSAH.seconds2timecode(time));
				if (typeof onChangeCB === 'function') {
					onChangeCB(time);
				}
			}))
			.append(spin_up_sec = $('<span>').addClass('ml up').css({
				position: 'absolute',
				'margin-left': '40px'
			}).on('click', function(event) {
				time = SALSAH.timecode2seconds(timesel.val()) + 1.0;
				timesel.val(SALSAH.seconds2timecode(time));
				if (typeof onChangeCB === 'function') {
					onChangeCB(time);
				}
			}))
			.append($('<br>'))
			.append(timesel = $('<input>').attr({
				type: 'text',
				value: SALSAH.seconds2timecode(time),
				size: '13',
				maxlength: '13'
			}).addClass('propedit timesel interval').css({
				width: '68px',
				'margin-top': '-12px',
				position: 'absolute',
				'font-size': 'medium',
				border: '1px solid rgba(3, 3, 3, 0.3)',
				'border-radius': '6px',
				'z-index': '1'

			}))
			.append($('<br>'))
			.append(spin_down_hour = $('<span>').addClass('ml down').css({
				position: 'absolute',
				'margin-left': '-4px',
				'margin-top': '-13px'
			}).on('click', function(event) {
				time = SALSAH.timecode2seconds(timesel.val()) - 3600.0;
				if (time <= 0) time = 0;
				timesel.val(SALSAH.seconds2timecode(time));
				if (typeof onChangeCB === 'function') {
					onChangeCB(time);
				}
			}))
			.append(spin_down_min = $('<span>').addClass('ml down').css({
				position: 'absolute',
				'margin-left': '18px',
				'margin-top': '-13px'
			}).on('click', function(event) {
				time = SALSAH.timecode2seconds(timesel.val()) - 60.0;
				if (time <= 0) time = 0;
				timesel.val(SALSAH.seconds2timecode(time));
				if (typeof onChangeCB === 'function') {
					onChangeCB(time);
				}
			}))
			.append(spin_down_sec = $('<span>').addClass('ml down').css({
				position: 'absolute',
				'margin-left': '40px',
				'margin-top': '-13px'
			}).on('click', function(event) {
				time = SALSAH.timecode2seconds(timesel.val()) - 1.0;
				if (time <= 0) time = 0;
				timesel.val(SALSAH.seconds2timecode(time));
				if (typeof onChangeCB === 'function') {
					onChangeCB(time);
				}
			}))
		);

		timesel.on('focusout', function(event) {
			if (typeof onChangeCB === 'function') {
				onChangeCB(SALSAH.timecode2seconds(timesel.val()));
			}
		});
		return timesel;
	};

	var methods = {
		init: function(timeobj) {
			var $that = this;

			if (timeobj.timeval1 == timeobj.timeval2) {
				timestr = SALSAH.seconds2timecode(timeobj.timeval1);
			} else {
				timestr = SALSAH.seconds2timecode(timeobj.timeval1) + ' - ' + SALSAH.seconds2timecode(timeobj.timeval2);
			}

			return this.each(function() {
				$(this).append(timestr);
			});
		},

		//
		// domele.timeobj('edit', function(t) { alert('New Time=' + t); }, function(t) { ... });
		//
		edit: function(options) {
			var $this = $(this);
			var localdata = {};
			localdata.settings = {
				timeobj: undefined, // inital value, if not overridden by options!
				onChangeStartCB: undefined,
				onChangeEndCB: undefined,
				show_period: true,
				show_duration: false
			};
			$.extend(localdata.settings, options);

			var timeobj = localdata.settings.timeobj; // saves some typeing
			var period = false;
			localdata.ele = {};

			$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			var timeval1;
			var timeval2;
			if (timeobj === undefined) {
				var jsdateobj = new Date();
				timeval1 = jsdateobj.getHours() * 3600.0 + jsdateobj.getMinutes() * 60.0 + jsdateobj.getSeconds();
				timeval1 = 0;
				timeval2 = timeval1;
			} else {
				timeval1 = timeobj.timeval1;
				timeval2 = timeobj.timeval2;
			}

			var timecontainer1 = $('<span>').css({
				float: 'left',
				width: '80px',
				height: '48px',
			}).appendTo(this);
			var durationcontainer = $('<span>').css({
				float: 'left',
				width: '80px',
				height: '48px',
				'margin-top': '7px',

			}).appendTo(this);
			var timecontainer2 = $('<span>').css({
				float: 'left',
				width: '80px',
				height: '48px',

			}).appendTo(this);

			if (timeval1 != timeval2) {
				period = true;
			}

			localdata.ele.time1 = create_time_entry(timecontainer1, timeval1, function(starttime) {
				if (localdata.settings.show_duration) {
					endtime = SALSAH.timecode2seconds(localdata.ele.time2.val());
					localdata.ele.duration.val(parseFloat(endtime - starttime).toFixed(2));
				}

				if (typeof localdata.settings.onChangeStartCB === 'function') {
					localdata.settings.onChangeStartCB(starttime);
				}
			}, 'start_time');
			if (localdata.settings.show_duration) {
				durationcontainer.append(localdata.ele.duration = $('<input>').attr({
					type: 'text',
					size: '13',
					maxlength: '13',
					placeholder: 'Duration'
				}).css({
					width: '68px'
				}).addClass('propedit timesel duration').on('change', function(event) {
					//
					// the following code is executed when the user changes the duration
					//
					starttime = SALSAH.timecode2seconds(localdata.ele.time1.val());
					endtime = starttime + parseFloat(localdata.ele.duration.val());
					timeval2 = SALSAH.seconds2timecode(endtime);
					localdata.ele.time2.val(timeval2);
					if (typeof localdata.settings.onChangeEndCB === 'function') {
						localdata.settings.onChangeEndCB(endtime);
					}
				}));
				localdata.ele.time2 = create_time_entry(timecontainer2, timeval2, function(endtime) {
					if (localdata.settings.show_duration) {
						starttime = SALSAH.timecode2seconds(localdata.ele.time1.val());
						localdata.ele.duration.val(parseFloat(endtime - starttime).toFixed(2));
					}

					if (typeof localdata.settings.onChangeEndCB === 'function') {
						localdata.settings.onChangeEndCB(endtime);
					}
				}, 'end_time');
				localdata.ele.duration.val(parseFloat(timeval2 - timeval1).toFixed(2));
			} else {
				if (period) {
					durationcontainer.append(' – ');
					locadata.ele.time2 = create_time_entry(timecontainer2, timeval2, localdata.settings.onChangeEndCB, 'end_time');
				}
			}

			//
			// period...
			//
			var periodattr = {
				'class': 'propedit periodsel',
				type: 'checkbox'
			};
			if (period) periodattr.checked = 'checked';
			if (localdata.settings.show_duration) {
				// do nothing
			} else if (period || localdata.settings.show_period) { // create period checkbox
				this.append(' ' + strings._period + ':');
				var periodsel = $('<input>', periodattr).on('click', function(event) {
					if (event.target.checked) {
						durationcontainer.append(' - ');
						localdata.ele.time2 = create_time_entry(timecontainer2, timeval2);
						period = true;
					} else {
						durationcontainer.empty();
						timecontainer2.empty();
						localdata.ele.time2 = undefined;
						period = false;
					}
				}).appendTo(this);
			}
			this.append(' ');
		},

		value: function() {
			var time_1 = this.find('.interval').first();

			var seconds = [];
			seconds.push(SALSAH.timecode2seconds(time_1.val()));

			var period = this.find('.periodsel').prop('checked'); // attr() -> prop()
			if (this.find('.duration')) period = true;

			if (period) {
				var time_2 = this.find('.interval').last();
				seconds.push(SALSAH.timecode2seconds(time_2.val()));
			}
			return seconds;
		},

		setStart: function(starttime) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');

				var timecode1;
				if ((typeof starttime == 'string') && (starttime.indexOf(':') != -1)) {
					timecode1 = starttime;
					starttime = SALSAH.timecode2seconds(starttime);
				} else {
					timecode1 = SALSAH.seconds2timecode(starttime);
				}
				localdata.ele.time1.val(timecode1);

				if (localdata.settings.show_duration) {
					var tc2 = localdata.ele.time2.val();
					var endtime = SALSAH.timecode2seconds(tc2);
					localdata.ele.duration.val(parseFloat(endtime - starttime).toFixed(2));
				}
			});
		},

		setEnd: function(endtime) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');

				var timecode2;
				if ((typeof endtime == 'string') && (endtime.indexOf(':') != -1)) {
					timecode2 = endtime;
					endtime = SALSAH.timecode2seconds(endtime);
				} else {
					timecode2 = SALSAH.seconds2timecode(endtime);
				}
				localdata.ele.time2.val(timecode2);

				if (localdata.settings.show_duration) {
					var tc1 = localdata.ele.time1.val();
					var starttime = SALSAH.timecode2seconds(tc1);
					localdata.ele.duration.val(parseFloat(endtime - starttime).toFixed(2));
				}
			});
		},

		setOnChangeStartCB: function(callback) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.onChangeStartCB = callback;
			});
		},

		setOnChangeEndCB: function(callback) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.onChangeEndCB = callback;
			});
		},

		sec2tc: function(sec) {
			var $this = $(this);
			var localdata = $this.data('localdata');

			var timecode = SALSAH.seconds2timecode(sec);
			this.html(timecode);
		},
		tc2sec: function(tc) {
			var $this = $(this);
			var localdata = $this.data('localdata');

			var seconds = SALSAH.timecode2seconds(tc);
			this.html(seconds);
		},
	};

	$.fn.timeobj = function(method) {

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
