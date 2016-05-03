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
 * This plugin creates an edit-form for the properties of a resource
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
(function( $ ){

	'use strict';

	var spin_up = new Image();
	spin_up.src = SITE_URL + '/app/icons/up.png';

	var spin_down = new Image();
	spin_down.src = SITE_URL + '/app/icons/down.png';

/*
	var spin_up2 = new Image();
	spin_up2.src = SITE_URL + '/app/icons/spin-up2.png';

	var spin_down2 = new Image();
	spin_down2.src = SITE_URL + '/app/icons/spin-down2.png';

	var spin_up3 = new Image();
	spin_up3.src = SITE_URL + '/app/icons/spin-up3.png';

	var spin_down3 = new Image();
	spin_down3.src = SITE_URL + '/app/icons/spin-down3.png';
*/
	// TODO: temporary fix due to async loading problem of strings, https://github.com/dhlab-basel/Knora/issues/92
	var cal_strings = {
		"_day_su" : "Sun",
		"_day_mo" : "Mon",
		"_day_tu" : "Tue",
		"_day_we" : "Wed",
		"_day_th" : "Thu",
		"_day_fr" : "Fri",
		"_day_sa" : "Sat",
		"_mon_jan_short" : "Jan",
		"_mon_feb_short" : "Feb",
		"_mon_mar_short" : "Mar",
		"_mon_apr_short" : "Apr",
		"_mon_may_short" : "May",
		"_mon_jun_short" : "Jun",
		"_mon_jul_short" : "Jul",
		"_mon_aug_short" : "Aug",
		"_mon_sep_short" : "Sep",
		"_mon_oct_short" : "Oct",
		"_mon_nov_short" : "Nov",
		"_mon_dec_short" : "Dec",
		"_mon_jan_long" : "January",
		"_mon_feb_long" : "February",
		"_mon_mar_long" : "March",
		"_mon_apr_long" : "April",
		"_mon_may_long" : "May",
		"_mon_jun_long" : "June",
		"_mon_jul_long" : "July",
		"_mon_aug_long" : "August",
		"_mon_sep_long" : "September",
		"_mon_oct_long" : "October",
		"_mon_nov_long" : "November",
		"_mon_dec_long" : "December",
		"_not_stated" : "Not stated",
		"_change_year" : "Change Year",
		"_change_decade" : "Change decade",
		"_change_century" : "Change century",
		"_period" : "Period"
	};

	var weekday = [cal_strings._day_su, cal_strings._day_mo, cal_strings._day_tu, cal_strings._day_we, cal_strings._day_th, cal_strings._day_fr, cal_strings._day_sa];

	var months = {
		GREGORIAN: ['ZERO', cal_strings._mon_jan_short, cal_strings._mon_feb_short, cal_strings._mon_mar_short, cal_strings._mon_apr_short, cal_strings._mon_may_short, cal_strings._mon_jun_short, cal_strings._mon_jul_short, cal_strings._mon_aug_short, cal_strings._mon_sep_short, cal_strings._mon_oct_short, cal_strings._mon_nov_short, cal_strings._mon_dec_short],
		JULIAN: ['ZERO', cal_strings._mon_jan_short, cal_strings._mon_feb_short, cal_strings._mon_mar_short, cal_strings._mon_apr_short, cal_strings._mon_may_short, cal_strings._mon_jun_short, cal_strings._mon_jul_short, cal_strings._mon_aug_short, cal_strings._mon_sep_short, cal_strings._mon_oct_short, cal_strings._mon_nov_short, cal_strings._mon_dec_short],
		JEWISH: ['ZERO', 'Tishri', 'Heshvan', 'Kislev', 'Tevet', 'Shevat', 'AdarI', 'AdarII', 'Nisan', 'Iyyar', 'Sivan', 'Tammuz', 'Av', 'Elul'],
		FRENCH: ['ZERO', 'Vendemiaire', 'Brumaire', 'Frimaire', 'Nivose', 'Pluviose', 'Ventose', 'Germinal', 'Floreal', 'Prairial', 'Messidor', 'Thermidor', 'Fructidor', 'Extra']
	};

	var months_long = {
		GREGORIAN: ['ZERO', cal_strings._mon_jan_long, cal_strings._mon_feb_long, cal_strings._mon_mar_long, cal_strings._mon_apr_long, cal_strings._mon_may_long, cal_strings._mon_jun_long, cal_strings._mon_jul_long, cal_strings._mon_aug_long, cal_strings._mon_sep_long, cal_strings._mon_oct_long, cal_strings._mon_nov_long, cal_strings._mon_dec_long],
		JULIAN: ['ZERO', cal_strings._mon_jan_long, cal_strings._mon_feb_long, cal_strings._mon_mar_long, cal_strings._mon_mapr_long, cal_strings._mon_may_long, cal_strings._mon_jun_long, cal_strings._mon_jul_long, cal_strings._mon_aug_long, cal_strings._mon_sep_long, cal_strings._mon_oct_long, cal_strings._mon_nov_long, cal_strings._mon_dec_long],
		JEWISH: ['ZERO', 'Tishri', 'Heshvan', 'Kislev', 'Tevet', 'Shevat', 'AdarI', 'AdarII', 'Nisan', 'Iyyar', 'Sivan', 'Tammuz', 'Av', 'Elul'],
		FRENCH: ['ZERO', 'Vendemiaire', 'Brumaire', 'Frimaire', 'Nivose', 'Pluviose', 'Ventose', 'Germinal', 'Floreal', 'Prairial', 'Messidor', 'Thermidor', 'Fructidor', 'Extra']
	};

	var precision = {
		DAY: 'Day',
		MONTH: 'Month',
		YEAR: 'Year'
	};

	var day_popoup_is_open = false;

	var open_daysel_popup = function(daysel, month, year, current_cal, precision) {
		var p = daysel.offset(); // relative to browser window
		var tmpcss = {
			position: 'fixed',
			left: p.left + daysel.width(),
			top: p.top
		};


		if (day_popoup_is_open) {
			return;
		}

		var __daysel = $('<div>').addClass('daysel').css(tmpcss);

		$('<div>').css({'text-align': 'center', 'font-style': 'italic', 'font-weight': 'bold', 'font-size': 'large'}).text(months_long[current_cal][month]).appendTo(__daysel);
		var daytab = $('<table>').appendTo(__daysel);
		var line = $('<tr>').appendTo(daytab);
		for (var i = 0; i < 7; i++) {
		    $('<th>').text(weekday[i]).appendTo(line);
		}

		var data2 = SALSAH.daycnt(current_cal, year, month);
		var i, cnt, td_ele;

		line = $('<tr>').appendTo(daytab);
		for (cnt = 0; cnt < data2.weekday_first; cnt++) {
			$('<td>').text(' ').appendTo(line);
		}
		for (i = 1; i <= data2.days; i++) {
			if ((cnt % 7) == 0) {
				line = $('<tr>').appendTo(daytab);
			}
			td_ele = $('<td>').text(i).data('day', i).on('click', function(e) {
				e.stopPropagation();
				daysel.val($(this).data('day'));
				__daysel.remove();
				$(document).off('click.daysel');
				day_popoup_is_open = false;
			}).appendTo(line);
			if ((i == daysel.val()) && (precision == 'DAY')) {
				td_ele.addClass('highlight');
			}
			cnt++;
		}
		line = $('<tr>').appendTo(daytab);
		td_ele = $('<td>', {colspan: 7}).text(cal_strings._not_stated).on('click', function(e) {
			e.stopPropagation();
			daysel.val('-');
			__daysel.remove();
			$(document).off('click.daysel');
			day_popoup_is_open = false;
		}).appendTo(line);
		if ((daysel.val() == '-') || (precision != 'DAY')) {
			td_ele.addClass('highlight');
		}

		$(document).on('click.daysel', function() {
			__daysel.remove();
			$(document).off('click.daysel');
			day_popoup_is_open = false;
		});
		day_popoup_is_open = true;
		return __daysel;
	}

	var create_date_entry = function (ele, jdc, current_cal, precision, no_day, no_month) {
		var postdata = {
			func: 'jdc2date',
			jdc: jdc,
			cal: current_cal
		};
		var tmparr;
		var day, month, year;

		switch (current_cal) {
			case 'GREGORIAN':
			case 'gregorian': {
				tmparr = SALSAH.jd_to_gregorian(jdc);
				year = tmparr[0];
				month = tmparr[1];
				day = tmparr[2];
				break;
			}
			case 'JULIAN':
			case 'julian': {
				tmparr = SALSAH.jd_to_julian(jdc);
				year = tmparr[0];
				month = tmparr[1];
				day = tmparr[2];
				break;
			}
			case 'JEWISH':
			case 'jewish': {
				tmparr = SALSAH.jd_to_hebrew(jdc);
				year = tmparr[0];
				month = tmparr[1];
				day = tmparr[2];
				break;
			}
			case 'FRENCH':
			case 'french': {
				//list($m, $d, $y)  = explode('/', jdtofrench($jdc));
				break;
			}
		}

		var daysel, monthsel, yearsel;
		var dayval;

		//
		// selection for day
		//
		if (precision != 'DAY') {
			dayval = '-';
		}
		else {
			dayval = day > 0 ? day : '-';
		}
		var dayselattr = {type: 'text', size: 1, maxlength: 1, readonly: true};
		if (precision == 'YEAR') {
			dayselattr.disabled = true;
		}
		daysel = $('<input>').attr(dayselattr).addClass('propedit').addClass('daysel').on('click.dayselin', function(e){
			e.stopPropagation();
			ele.append(open_daysel_popup(daysel, monthsel.val(), yearsel.val(), current_cal, precision));
		}).val(dayval).appendTo(ele);
		if ((no_day !== undefined) && no_day) {
			daysel.css('display', 'none');
		}


		//
		// pulldown for month
		//
		monthsel = $('<select>', {'class': 'propedit monthsel'}).change(function(event) {
			//
			// month changed...
			//
			month = event.target.value;
			if (month == 0) {
				daysel.val('-').attr({disabled: 'disabled'});
			}
			else {
				var actual_day = daysel.val(); // save current day for use below...
				daysel.empty();
				daysel.removeAttr('disabled');
				if (precision != 'DAY') {
					$('<option>').attr({selected: 'selected'}).append('-').appendTo(daysel);
				}
				else {
					$('<option>').append('-').appendTo(daysel);
				}
				var data2 = SALSAH.daycnt(current_cal, year, month);
				if (actual_day > data2.days) actual_day = data2.days;
				for (var i = 1; i <= data2.days; i++) {
					var attributes = {Class: 'propedit'};
					if ((precision == 'DAY') && (i == actual_day)) {
						attributes.selected = 'selected';
					}
					attributes.value = i;
					$('<option>', attributes).append(i).appendTo(daysel);
				}
			}
		});
		var monthattr = {'class': 'propedit monthsel', value: 0};
		if (precision != 'MONTH') {
			monthattr.selected = 'selected';
		}
		$('<option>', monthattr).append('-').appendTo(monthsel);
		for (var i = 1; i <= SALSAH.calendars[current_cal].n_months; i++) {
			var attributes = {Class: 'propedit monthsel'};
			if ((i == month) && ((precision == 'MONTH') || (precision == 'DAY'))) {
				attributes.selected = 'selected';
			}
			attributes.value = i;
			$('<option>', attributes).append(months[current_cal][i]).appendTo(monthsel);
		}
		ele.append(monthsel);

		//
		// textfield for year
		//
		yearsel = $('<input>', {type: 'text', 'class': 'propedit yearsel', value: year, size: '4', maxlength: '4'}).appendTo(ele);
		ele.append($('<span>').attr({title: cal_strings._change_year})
			.append($('<img>', {src: spin_up.src}).css({'vertical-align': 'middle', cursor: 'pointer'}).attr({title: 'click: +1\nshift+click: +10\nshift+alt+click: +100'}).click(function(event){
				if (event.shiftKey && event.altKey){
					yearsel.val(parseInt(yearsel.val()) + 100);
				}
				else if (event.shiftKey) {
					yearsel.val(parseInt(yearsel.val()) + 10);
				}
				else {
					yearsel.val(parseInt(yearsel.val()) + 1);
				}
			}))
			.append($('<img>', {src: spin_down.src}).css({'vertical-align': 'middle', cursor: 'pointer'}).attr({title: 'click: -1\nshift+click: -10\nshift+alt+click: -100'}).click(function(event){
				if (event.shiftKey && event.altKey){
					yearsel.val(parseInt(yearsel.val()) - 100);
				}
				else if (event.shiftKey) {
					yearsel.val(parseInt(yearsel.val()) - 10);
				}
				else {
					yearsel.val(parseInt(yearsel.val()) - 1);
				}
			}))
		);
	};


	var parse_datestr = function(datestr, calendar, periodpart) {
		var d = {};
		var dd;
		var d_arr = datestr.split('-');
		
		if (d_arr.length == 3) { 
			d.precision = 'DAY';
			d.jdc = SALSAH.date_to_jdc(d_arr[2], d_arr[1], d_arr[0], calendar, periodpart);
		}
		else if (d_arr.length == 2) {
			d.precision = 'MONTH';
			d.jdc = SALSAH.date_to_jdc(0, d_arr[1], d_arr[0], calendar, periodpart);
		}
		else if (d_arr.length == 1) {
			d.precision = 'YEAR';
			d.jdc = SALSAH.date_to_jdc(0, 0, d_arr[0], calendar, periodpart);
		}
		else {
			alert('ERROR: Invalid datestr: ' + datestr);
		}
		dd = SALSAH.jdc_to_date(d.jdc, calendar);
		d.year = dd.year;
		d.month = dd.month;
		d.day = dd.day;
		d.weekday = dd.weekday;
		d.calendar = calendar;

		return d;
	};
	
	
/**
 * Dateobject:
 * - <i>name</i>.dateval1 (YYYY-MM-DD)
 * - <i>name</i>.dateval2 (YYYY-MM-DD)
 * - <i>name</i>.calendar ("GREGORIAN", "JULIAN", "JEWISH", "FRENCH")
 */
	var methods = {
		init: function (dateobj) {
			var $that = this;
			var d1;
			var d2;

			d1 = parse_datestr(dateobj.dateval1, dateobj.calendar, 'START');
			d2 = parse_datestr(dateobj.dateval2, dateobj.calendar, 'END');


			var datestr = '';
			if (d1.precision == d2.precision) {
				//
				// same precisions for start- and end-date
				//
				switch (d1.precision) {
					case 'DAY': {
						if ((d1.year == d2.year) && (d1.month == d2.month) && (d1.day == d2.day)) {
							datestr = weekday[d1.weekday] + ' ' + d1.day + '. ' + months[dateobj.calendar][d1.month] + ' ' + d1.year;
						}
						else {
							datestr = weekday[d1.weekday] + ' ' +  d1.day + '. ' + months[dateobj.calendar][d1.month] + ' ' + d1.year + '-' + d2.day + '. ' + months[dateobj.calendar][d2.month] + ' ' + d2.year;
						}
						break;
					}
					case 'MONTH': {
						if ((d1.year == d2.year) && (d1.month == d2.month)) {
							datestr = months[dateobj.calendar][d1.month] + ' ' + d1.year;
						}
						else {
							datestr = months[dateobj.calendar][d1.month] + ' ' + d1.year + '-' + months[dateobj.calendar][d2.month] + ' ' + d2.year;
						}
						break;
					}
					case 'YEAR': {
						if (d1.year == d2.year) {
							datestr = d1.year;
						}
						else {
							datestr = d1.year + ' - ' + d2.year;
						}
						break;
					}
				} // switch(precision1)
			}
			else {
				//
				// different precisions for start- and end-date
				//
				switch (d1.precision) {
					case 'DAY': {
						datestr = weekday[d1.weekday] + ' ' +  d1.day + '. ' + months[dateobj.calendar][d1.month] + ' ' + d1.year;
						break;
					}
					case 'MONTH': {
						datestr = months[dateobj.calendar][d1.month] + ' ' + d1.year;
						break;
					}
					case 'YEAR': {
						datestr = d1.year;
						break;
					}
				} // switch(propinfo.values[value_index].precision1)
				datestr += ' - ';
				switch (d2.precision) {
					case 'DAY': {
						datestr += weekday[d2.weekday] + ' ' +  d2.day + '. ' + months[dateobj.calendar][d2.month] + ' ' + d2.year;
						break;
					}
					case 'MONTH': {
						datestr += months[dateobj.calendar][d2.month] + ' ' + d2.year;
						break;
					}
					case 'YEAR': {
						datestr += d2.year;
						break;
					}
				} // switch(precision2)
			}
			datestr += ' (' + SALSAH.calendars[d1.calendar].name + ')';

			return this.each(function() {
				$(this).append(datestr);
			});
		},

	   /*
		* defvals: {
		*    current_cal: 'GREGORIAN' | 'JULIAN',
		*    date1: {
		*       day: <int>,
		*       month: <int>,
		*       year: <int>
		*    }
		*    date2: {
		*       day: <int>,
		*       month: <int>,
		*       year: <int>
		*    }
		*    no_calsel: true | false,
		*    no_day: true | false
		* }
		*/
		edit: function(dateobj, defvals) {
			var period = false;
			var d1 = {};
			var d2 = {};
			if (dateobj === undefined) {
				var jsdateobj = new Date();
				if (defvals === undefined) {
					d1.calendar = 'GREGORIAN';
					d1.day = jsdateobj.getDate();
					d1.month = jsdateobj.getMonth() + 1;
					d1.year = jsdateobj.getFullYear();
					d1.precision = 'DAY';
					d2 = $.extend({}, d1);
				}
				else {
					d1.calendar = (defvals.current_cal === undefined) ? 'GREGORIAN' : defvals.current_cal;
					if (defvals.date1 === undefined) {
						d1.day = jsdateobj.getDate();
						d1.month = jsdateobj.getMonth() + 1;
						d1.year = jsdateobj.getFullYear();
					}
					else {
						d1.day = defvals.date1.day === undefined ? jsdateobj.getDate() : defvals.date1.day;
						d1.month = defvals.date1.month === undefined ? jsdateobj.getMonth() + 1 : defvals.date1.month;
						d1.year = defvals.date1.year === undefined ? jsdateobj.getFullYear() : defvals.date1.year;
					}
					if (defvals.date2 === undefined) {
						d2 = $.extend({}, d1);
					}
					else {
						d2.day = defvals.date2.day === undefined ? jsdateobj.getDate() : defvals.date2.day;
						d2.month = defvals.date2.month === undefined ? jsdateobj.getMonth() + 1 : defvals.date2.month;
						d2.year = defvals.date2.year === undefined ? jsdateobj.getFullYear() : defvals.date2.year;
						d2.calendar = d1.calendar;
					}
					d1.precision = defvals.dateprecision1 === undefined ? 'DAY' : defvals.dateprecision1;
					d2.precision = defvals.dateprecision2 === undefined ? 'DAY' : defvals.dateprecision2;
				}
				d1.jdc = SALSAH.date_to_jdc(d1.day, d1.month, d1.year, d1.calendar, 'START');
				d2.jdc = SALSAH.date_to_jdc(d2.day, d2.month, d2.year, d2.calendar, 'END');
			}
			else {
				var current_cal = dateobj.calendar;
				if (dateobj.dateprecision1 !== undefined) {
					alert('OLD DATE FORMAT!!!')
					d1.jdc = dateobj.dateval1;
					d2.jdc = dateobj.dateval2;
					var date1 = SALSAH.jdc_to_date(dateval1, current_cal);
					var date2 = SALSAH.jdc_to_date(dateval2, current_cal);
					var dateprecision1 = dateobj.dateprecision1;
					var dateprecision2 = dateobj.dateprecision2;
				}
				else {
					d1 = parse_datestr(dateobj.dateval1, dateobj.calendar, 'START');
					d2 = parse_datestr(dateobj.dateval2, dateobj.calendar, 'END');
				}
			}

			var datecontainer1 = $('<span>').appendTo(this);
			var datecontainer2 = $('<span>').appendTo(this);

			if (d1.precision == d2.precision) {
				switch (d1.precision) {
					case 'DAY': {
						if ((d1.day != d2.day) || (d1.month != d2.month) || (d1.year != d2.year)) period = true;
						break;
					}
					case 'MONTH': {
						if ((d1.month != d2.month) || (d1.year != d2.year)) period = true;
						break;
					}
					case 'YEAR': {
						if (d1.year != d2.year) period = true;
						break;
					}
					default: {
						period = true;
					}
				}
			}
			else {
				period = true; // different date precisions imply a period!
			}
			
			var no_day = false;
			if ((defvals !== undefined) && (defvals.no_day !== undefined)) no_day = defvals.no_day
			create_date_entry(datecontainer1, d1.jdc, d1.calendar, d1.precision, no_day);
			if (period) {
				datecontainer2.append(' – ');
				create_date_entry(datecontainer2, d2.jdc, d2.calendar, d2.precision, no_day);
			}

			//
			// period...
			//
			var periodattr = {
				'class': 'propedit periodsel',
				type: 'checkbox'
			};
			if (period) periodattr.checked = 'checked';
			this.append(' ' + cal_strings._period + ':');
			var periodsel = $('<input>', periodattr).click(function(event) {
				if (event.target.checked) {
					datecontainer2.append(' - ');
					create_date_entry(datecontainer2, d2.jdc, d2.calendar, d2.precision);
					period = true;
				}
				else {
					datecontainer2.empty();
					period = false;
				}
			}).appendTo(this);

			this.append(' ');
			//
			// calendar selection
			//
			var calsel = $('<select>', {'class': 'propedit calsel'}).change(function(event) {
				//
				// calendar selection changed...
				//

				//
				// first dave the actual date into a calendar independant JDC
				//
				var day1 = datecontainer1.find('.daysel').val();
				var month1 = datecontainer1.find('.monthsel').val();
				var year1 = datecontainer1.find('.yearsel').val();
				if ((day1 == '-') && (month1 == 0)) {
					d1.precision = 'YEAR';
					day1 = 0;
				}
				else if (day1 == '-') {
					d1.precision = 'MONTH';
					day1 = 0;
				}
				else {
					d1.precision = 'DAY';
				}
				d1.jdc = SALSAH.date_to_jdc(day1, month1, year1, d1.calendar, 'START');
				if (period) {
					var day2 = datecontainer2.find('.daysel').val();
					var month2 = datecontainer2.find('.monthsel').val();
					var year2 = datecontainer2.find('.yearsel').val();
					if ((day2 == '-') && (month2 == 0)) {
						d2.precision = 'YEAR';
						day2 = 0;
					}
					else if (day2 == '-') {
						d2.precision = 'MONTH';
						day2 = 0;
					}
					else {
						d2.precision = 'DAY';
					}
					d2.jdc = SALSAH.date_to_jdc(day2, month2, year2, d2.calendar, 'END');
				}
				d1.calendar = event.target.value; // get new calendar value
				d2.calendar = event.target.value; // get new calendar value
				datecontainer1.empty();
				create_date_entry(datecontainer1, d1.jdc, d1.calendar, d1.precision);
				if (period) {
					datecontainer2.empty();
					datecontainer2.append(' - ');
					create_date_entry(datecontainer2, d2.jdc, d2.calendar, d2.precision);
				}
			}).appendTo(this);
			for (var i in SALSAH.calendars) {
				if (i == 'FRENCH') continue; // no french revolutionary at the moment!
				if (i == 'JEWISH') continue; // no hebrew  at the moment!
				var attributes = {Class: 'propedit'};
				if (i == d1.calendar) {
					attributes.selected = 'selected';
				}
				attributes.value = i;
				$('<option>', attributes).append(SALSAH.calendars[i].name).appendTo(calsel); // !!!!!!!!!!!!!!!!!!!!!!!!!!
			}
			if ((defvals !== undefined) && (defvals.no_calsel !== undefined) && defvals.no_calsel) {
				calsel.css('display', 'none');
			}
			return $(this);
		},

		value: function() {
			var dateobj = {}; 
			var datecontainer1 = $(this.children('span').get(0));
			var datecontainer2 = $(this.children('span').get(1));

			var period = this.find('.periodsel').prop('checked'); // attr() -> prop()
			dateobj.calendar = this.find('.calsel').val();

			dateobj.dateval1 = '';
			var year1 = datecontainer1.find('.yearsel').val();
			if (isNaN(year1) || (year1 == 0)) {
				alert('Date with invalid year! Assumed year 1');
				year1 = 1;
			}
			dateobj.dateval1 += year1;
			var month1 = datecontainer1.find('.monthsel').val();
			if (month1 > 0) {
				dateobj.dateval1 += '-' + month1;
				var day1 = datecontainer1.find('.daysel').val();
				if ((day1 != '-') && (day1 > 0)) {
					dateobj.dateval1 += '-' + day1;
				}
			}

			if (period) {
				dateobj.dateval2 = '';
				var year2 = datecontainer2.find('.yearsel').val();
				if (isNaN(year2) || (year2 == 0)) {
					alert('Period with invalid year! Assumed year ' + year1);
					year2 = year1;
				}
				dateobj.dateval2 += year2;
				var month2 = datecontainer2.find('.monthsel').val();
				if (month2 > 0) {
					dateobj.dateval2 += '-' + month2;
					var day2 = datecontainer2.find('.daysel').val();
					if ((day2 != '-') && (day2 > 0)) {
						dateobj.dateval2 += '-' + day2;
					}
				}
			}
			return dateobj;
		},
	}

	$.fn.dateobj = function(method) {

	    // Method calling logic
	    if ( methods[method] ) {
	      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
	    } else if ( typeof method === 'object' || ! method ) {
	      return methods.init.apply( this, arguments );
	    } else {
	      throw 'Method ' +  method + ' does not exist on jQuery.tooltip';
	    }

	};
})( jQuery );
