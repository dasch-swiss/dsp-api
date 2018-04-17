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

(function( S ) {

	'use strict';

	S.calendars = {
		GREGORIAN: {name: 'Gregorian', n_months: 12},
		JULIAN: {name: 'Julian', n_months: 12},
		JEWISH: {name: 'Jewish', n_months: 13},
		FRENCH: {name: 'Revol.', n_months: 13}
	};
	
	S.calendarnames = [];
	S.calendarnames.push('ZERO');
	for (var i in S.calendars) {
		S.calendarnames.push(i);
	}

	//
	// adapted from "http://www.fourmilab.ch/documents/calendar/"
	//
	// Please note the these functions originally asume that the gregorian  calender
	// has a year 0. In PHP however, the gregorian year 0 does not exist!
	//
	
	var J0000 = 1721424.5;                // Julian date of Gregorian epoch: 0000-01-01
	var J1970 = 2440587.5;                // Julian date at Unix epoch: 1970-01-01
	var JMJD  = 2400000.5;                // Epoch of Modified Julian Date system
	var J1900 = 2415020.5;                // Epoch (day 1) of Excel 1900 date system (PC)
	var J1904 = 2416480.5;                // Epoch (day 0) of Excel 1904 date system (Mac)

	var GREGORIAN_EPOCH = 1721425.5;
	var JULIAN_EPOCH = 1721423.5;
	var HEBREW_EPOCH = 347995.5;
	var FRENCH_REVOLUTIONARY_EPOCH = 2375839.5;

	var NormLeap = new Array("Normal year", "Leap year");
	
	function mod(a, b) {
		return a - (b * Math.floor(a / b));
	}

	function jwday(j) {
		j = Number(j);
		return mod(Math.floor((j + 1.5)), 7);
	}

	function leap_gregorian(year) {
		year = parseInt(year);
	    return ((year % 4) == 0) && (!(((year % 100) == 0) && ((year % 400) != 0)));
	}

	//  GREGORIAN_TO_JD  --  Determine Julian day number from Gregorian calendar date


	S.gregorian_to_jd = function(year, month, day) {
        // console.log("gregorian_to_jd got year " + year + ", month " + month + ", day " + day);

		year = parseInt(year);
		month = parseInt(month);
		day = parseInt(day);
		//if (year < 0) year++; // correction for PHP
		var jd = (GREGORIAN_EPOCH - 1) +
			(365 * (year - 1)) +
			Math.floor((year - 1) / 4) +
			(-Math.floor((year - 1) / 100)) +
			Math.floor((year - 1) / 400) +
			Math.floor((((367 * month) - 362) / 12) +
			((month <= 2) ? 0 : (leap_gregorian(year) ? -1 : -2)) + day);

		// console.log("gregorian_to_jd calculated JDN " + jd);
		return jd;
	};

	//  JD_TO_GREGORIAN  --  Calculate Gregorian calendar date from Julian day

	S.jd_to_gregorian = function(jd) {
	    var wjd, depoch, quadricent, dqc, cent, dcent, quad, dquad,
	        yindex, dyindex, year, yearday, leapadj;


		//var jsd = parseInt(jd);

		// if a Julian Day has a fraction of 0.5 or higher, it refers to midnight (0h) or later
		// if it is has a fraction below 0.5, it refers to a time before midnight which is the day before
		// 2457498.5 -> 2016-04-20 0h
		// 2457498.4 -> 2016-04-19
	    wjd = Math.floor(jd - 0.5) + 0.5;
	    depoch = wjd - GREGORIAN_EPOCH;
	    quadricent = Math.floor(depoch / 146097);
	    dqc = mod(depoch, 146097);
	    cent = Math.floor(dqc / 36524);
	    dcent = mod(dqc, 36524);
	    quad = Math.floor(dcent / 1461);
	    dquad = mod(dcent, 1461);
	    yindex = Math.floor(dquad / 365);
	    year = (quadricent * 400) + (cent * 100) + (quad * 4) + yindex;
	    if (!((cent == 4) || (yindex == 4))) {
	        year++;
	    }
	    yearday = wjd - S.gregorian_to_jd(year, 1, 1);
	    leapadj = ((wjd < S.gregorian_to_jd(year, 3, 1)) ? 0 : (leap_gregorian(year) ? 1 : 2));
	    var month = Math.floor((((yearday + leapadj) * 12) + 373) / 367);

	    // console.log("jd_to_gregorian calculated month " + month);

	    var day = (wjd - S.gregorian_to_jd(year, month, 1)) + 1;

		// if (year <= 0) year--; // correction for PHPvar JULIAN_EPOCH = 1721423.5;

	    return new Array(Math.round(year), Math.round(month), Math.round(day));
	};
	
	
	function leap_julian(year) {
		year = parseInt(year);
	    return mod(year, 4) == ((year > 0) ? 0 : 3);
	}

	S.julian_to_jd = function(year, month, day) {
		year = parseInt(year);
		month = parseInt(month);
		day = parseInt(day);

	    /* Adjust negative common era years to the zero-based notation we use.  */

	    if (year < 1) {
	        year++;
	    }

	    /* Algorithm as given in Meeus, Astronomical Algorithms, Chapter 7, page 61 */

	    if (month <= 2) {
	        year--;
	        month += 12;
	    }

	    return ((Math.floor((365.25 * (year + 4716))) +
	            Math.floor((30.6001 * (month + 1))) +
	            day) - 1524.5);
	}

	//  JD_TO_JULIAN  --  Calculate Julian calendar date from Julian day

	S.jd_to_julian = function(td) {
	    var z, a, alpha, b, c, d, e, year, month, day;
		td = parseInt(td);
		
	    td += 0.5;
	    z = Math.floor(td);

	    a = z;
	    b = a + 1524;
	    c = Math.floor((b - 122.1) / 365.25);
	    d = Math.floor(365.25 * c);
	    e = Math.floor((b - d) / 30.6001);

	    month = Math.floor((e < 14) ? (e - 1) : (e - 13));
	    year = Math.floor((month > 2) ? (c - 4716) : (c - 4715));
	    day = b - d - Math.floor(30.6001 * e);

	    /*  If year is less than 1, subtract one to convert from
	        a zero based date system to the common era system in
	        which the year -1 (1 B.C.E) is followed by year 1 (1 C.E.).  */

	    if (year < 1) {
	        year--;
	    }

	    return new Array(Math.round(year), Math.round(month), Math.round(day));
	};
	
	function hebrew_leap(year) {
		year = parseInt(year);
	    return mod(((year * 7) + 1), 19) < 7;
	}

	//  How many months are there in a Hebrew year (12 = normal, 13 = leap)

	function hebrew_year_months(year) {
		year = parseInt(year);
	    return hebrew_leap(year) ? 13 : 12;
	}

	//  Test for delay of start of new year and to avoid
	//  Sunday, Wednesday, and Friday as start of the new year.

	function hebrew_delay_1(year) {
		var months, days, parts;
		year = parseInt(year);

	    months = Math.floor(((235 * year) - 234) / 19);
	    parts = 12084 + (13753 * months);
	    days = (months * 29) + Math.floor(parts / 25920);

	    if (mod((3 * (days + 1)), 7) < 3) {
	        days++;
	    }

	    return days;
	}

	//  Check for delay in start of new year due to length of adjacent years

	function hebrew_delay_2(year) {
	    var last, present, next;
		year = parseInt(year);

	    last = hebrew_delay_1(year - 1);
	    present = hebrew_delay_1(year);
	    next = hebrew_delay_1(year + 1);

	    return ((next - present) == 356) ? 2 :
	                                     (((present - last) == 382) ? 1 : 0);
	}

	//  How many days are in a Hebrew year ?

	function hebrew_year_days(year) {
		year = parseInt(year);
	    return S.hebrew_to_jd(year + 1, 7, 1) - S.hebrew_to_jd(year, 7, 1);
	}

	//  How many days are in a given month of a given year

	function hebrew_month_days(year, month) {
		year = parseInt(year);
		month = parseInt(month);
	    //  First of all, dispose of fixed-length 29 day months

	    if (month == 2 || month == 4 || month == 6 ||
	        month == 10 || month == 13) {
	        return 29;
	    }

	    //  If it's not a leap year, Adar has 29 days

	    if (month == 12 && !hebrew_leap(year)) {
	        return 29;
	    }

	    //  If it's Heshvan, days depend on length of year

	    if (month == 8 && !(mod(hebrew_year_days(year), 10) == 5)) {
	        return 29;
	    }

	    //  Similarly, Kislev varies with the length of year

	    if (month == 9 && (mod(hebrew_year_days(year), 10) == 3)) {
	        return 29;
	    }

	    //  Nope, it's a 30 day month

	    return 30;
	}

	//  Finally, wrap it all up into...

	S.hebrew_to_jd = function(year, month, day) {

		year = parseInt(year);
		month = parseInt(month);
		day = parseInt(day);

	    var jd, mon, months;

	    months = hebrew_year_months(year);
	    jd = HEBREW_EPOCH + hebrew_delay_1(year) +
	         hebrew_delay_2(year) + day + 1;

	    if (month < 7) {
	        for (mon = 7; mon <= months; mon++) {
	            jd += hebrew_month_days(year, mon);
	        }
	        for (mon = 1; mon < month; mon++) {
	            jd += hebrew_month_days(year, mon);
	        }
	    } else {
	        for (mon = 7; mon < month; mon++) {
	            jd += hebrew_month_days(year, mon);
	        }
	    }

	    return jd;
	}

	/*  JD_TO_HEBREW  --  Convert Julian date to Hebrew date
	                      This works by making multiple calls to
	                      the inverse function, and is this very
	                      slow.  */

	S.jd_to_hebrew = function(jd) {
	    var year, month, day, i, count, first;
		jd = parseInt(jd);

	    jd = Math.floor(jd) + 0.5;
	    count = Math.floor(((jd - HEBREW_EPOCH) * 98496.0) / 35975351.0);
	    year = count - 1;
	    for (i = count; jd >= S.hebrew_to_jd(i, 7, 1); i++) {
	        year++;
	    }
	    first = (jd < S.hebrew_to_jd(year, 1, 1)) ? 7 : 1;
	    month = first;
	    for (i = first; jd > S.hebrew_to_jd(year, i, hebrew_month_days(year, i)); i++) {
	        month++;
	    }
	    day = (jd - S.hebrew_to_jd(year, month, 1)); // + 1;
	    return new Array(Math.round(year), Math.round(month), Math.round(day));
	}
	
	function annee_da_la_revolution(jd) {
		jd = parseInt(jd);
	    var guess = S.jd_to_gregorian(jd)[0] - 2,
	        lasteq, nexteq, adr;

	    lasteq = paris_equinoxe_jd(guess);
	    while (lasteq > jd) {
	        guess--;
	        lasteq = paris_equinoxe_jd(guess);
	    }
	    nexteq = lasteq - 1;
	    while (!((lasteq <= jd) && (jd < nexteq))) {
	        lasteq = nexteq;
	        guess++;
	        nexteq = paris_equinoxe_jd(guess);
	    }
	    adr = Math.round((lasteq - FRENCH_REVOLUTIONARY_EPOCH) / TropicalYear) + 1;

	    return new Array(adr, lasteq);
	}

	/*  JD_TO_FRENCH_REVOLUTIONARY  --  Calculate date in the French Revolutionary
	                                    calendar from Julian day.  The five or six
	                                    "sansculottides" are considered a thirteenth
	                                    month in the results of this function.  */

	S.jd_to_french_revolutionary = function(jd) {
	    var an, mois, decade, jour,
	        adr, equinoxe;
		jd = parseInt(jd);
	    jd = Math.floor(jd) + 0.5;
	    adr = annee_da_la_revolution(jd);
	    an = adr[0];
	    equinoxe = adr[1];
	    mois = Math.floor((jd - equinoxe) / 30) + 1;
	    jour = (jd - equinoxe) % 30;
	    decade = Math.floor(jour / 10) + 1;
	    jour = (jour % 10) + 1;

	    return new Array(an, mois, decade, jour);
	};

	/*  FRENCH_REVOLUTIONARY_TO_JD  --  Obtain Julian day from a given French
	                                    Revolutionary calendar date.  */

	S.french_revolutionary_to_jd = function(an, mois, decade, jour) {
	    var adr, equinoxe, guess, jd;
		an = parseInt(an);
		mois = parseInt(mois);
		decade = parseInt(decade);
		jour = parseInt(jour);

	    guess = FRENCH_REVOLUTIONARY_EPOCH + (TropicalYear * ((an - 1) - 1));
	    adr = new Array(an - 1, 0);

	    while (adr[0] < an) {
	        adr = annee_da_la_revolution(guess);
	        guess = adr[1] + (TropicalYear + 2);
	    }
	    equinoxe = adr[1];

	    jd = equinoxe + (30 * (mois - 1)) + (10 * (decade - 1)) + (jour - 1);
	    return jd;
	}
	
	S.daycnt = function(cal, year, month) {
		year = parseInt(year);
		month = parseInt(month);

		var dc1, dc2, days;
		switch (cal) {
			case 'GREGORIAN':
			case 'gregorian': {
				dc1 = Math.round(S.gregorian_to_jd(year, month, 1));
				if ((month + 1) > S.calendars[cal].n_months) {
					month = 1;
					year++; 
					dc2 = Math.round(S.gregorian_to_jd(year, month, 1));
				}
				else {
					dc2 = Math.round(S.gregorian_to_jd(year, month + 1, 1));
				}
				days = dc2 - dc1;
				break;
			}
			case 'JULIAN':
			case 'julian': {
				dc1 = Math.round(S.julian_to_jd(year, month, 1));
				if ((month + 1) > S.calendars[cal].n_months) {
					month = 1;
					year++; 
					dc2 = Math.round(S.julian_to_jd(year, month, 1));
				}
				else {
					dc2 = Math.round(S.julian_to_jd(year, month + 1, 1));
				}
				days = dc2 - dc1;
				break;
			}
			case 'JEWISH':
			case 'jewish': {
				dc1 = Math.round(S.hebrew_to_jd(year, month, 1));
				if ((month + 1) > S.calendars[cal].n_months) {
					month = 1;
					year++; 
					dc2 = Math.round(S.hebrew_to_jd(year, month, 1));
				}
				else {
					dc2 = Math.round(S.hebrew_to_jd(year, month + 1, 1));
				}
				days = dc2 - dc1;
				break;
			}
			case 'FRENCH':
			case 'french': {
				break;
			}
		}
		return {
			days: days,
			weekday_first: jwday(dc1)
		}
	};

	S.jdc_to_date = function (jdc, cal) {
		jdc = parseInt(jdc);
		var dateobj = {};
		var tmparr;
		switch (cal) {
			case 'GREGORIAN':
			case 'gregorian': {
				tmparr = S.jd_to_gregorian(jdc);
				break;
			}
			case 'JULIAN':
			case 'julian': {
				tmparr = S.jd_to_julian(jdc);
				break;
			}
			case 'JEWISH':
			case 'jewish': {
				tmparr = S.jd_to_hebrew(jdc);
				break;
			}
			case 'FRENCH':
			case 'french': {
				tmparr = S.jd_to_french_revolutionary(jdc);
				break;
			}
		}
		dateobj.year = tmparr[0];
		dateobj.month = tmparr[1];
		dateobj.day = tmparr[2];
		dateobj.weekday = jwday(jdc);

		return dateobj;
	}
	
	S.date_to_jdc = function(day, month, year, cal, periodpart) {
		var jdc = 0;

		var i_day = parseInt(day);
		var i_month = parseInt(month);
		var i_year = parseInt(year);

		if (periodpart == 'END') {
			if (i_month == 0) i_month = S.calendars[cal].n_months;
			if (i_day == 0) {
				var tmp = SALSAH.daycnt(cal, i_year, i_month);
				i_day = tmp.days;
			}
		}
		else {
			if (month == 0) i_month = 1;
			if (day == 0) i_day = 1;
		}

		switch (cal) {
			case 'GREGORIAN':
			case 'gregorian': {
				jdc = Math.round(S.gregorian_to_jd(i_year, i_month, i_day));
				break;
			}
			case 'JULIAN':
			case 'julian': {
				jdc = Math.round(S.julian_to_jd(i_year, i_month, i_day));
				break;
			}
			case 'JEWISH':
			case 'jewish': {
				jdc = Math.round(S.hebrew_to_jd(i_year, i_month, i_day));
				break;
			}
			case 'FRENCH':
			case 'french': {
				jdc = -1;
				break;
			}
			default: {
				jdc = -2;
			}
		}
		return jdc;
	}


}) ( SALSAH );