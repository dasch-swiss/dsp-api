

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

function innerdim() {
	var w;
	if (window.innerWidth) {
		w = window.innerWidth;
	}
	else if (document.documentElement && document.documentElement.clientWidth) {
		w = document.documentElement.clientWidth;
	}	
	else if (document.body) {
		w = document.body.clientWidth;
	}
	var h;
	if (window.innerHeight) {
		h = window.innerHeight;
	}
	else if (document.documentElement && document.documentElement.clientHeight) {
		h = document.documentElement.clientHeight;
	}	
	else if (document.body) {
		h = document.body.clientHeight;
	}
	return {width: w, height: h};
}