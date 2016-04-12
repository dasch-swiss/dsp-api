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

//
// this function creates an overlay with a login window where the user can
// enter the username and password
//
function open_login_dialog(width, height) {
	//
	// first we calculate the dimension of the shade element and create it
	//
	var w = window.innerWidth;
	var h = window.innerHeight;
	var shade = $.create("div", {attributes: {id : 'shade'}});
	shade.css({'position' : 'fixed',
		left: '0px',
		top: '0px',
		width: String(w) + 'px',
		height: String(h) + 'px',
		opacity: '0.7', 
		backgroundColor: 'rgb(0, 0, 0)'});
	shade.appendTo("body");

	//
	// now we calculate the position of the login window
	//
	xpos = (w - width) / 2;
	ypos = (h - height) / 2;
	var dialog = $.create("div", {attributes: {'id' : 'dbox'}});
	dialog.css({'position' : 'fixed',
		left: String(xpos) + 'px',
		top: String(ypos) + 'px',
		width: String(width) + 'px',
		height: String(height) + 'px',
		backgroundColor: 'rgb(50, 200, 255)',
		border: '10px',
		padding: '10px'});
		
	//
	// now we create the login window content which contains a form, a fieldset
	// and the input fields
	//
	dialog.appendTo("body");
	var form = $.create("form", {attributes: {id: 'loginform'}}).appendTo("#dbox");
	var fs = $.create('fieldset', {attributes: {Class: 'loginwin'}}).appendTo("#loginform");
	$.create("legend", {attributes: {Class: 'loginwin'}}, 'Login Information').appendTo(fs);
	var table = $.create("table", {attributes: {border: '0', Class: 'loginwin'}}).appendTo(fs);
	var tablerow = $.create("tr", {attributes: {Class: 'loginwin'}}).appendTo(table);
	var rowcell = $.create("td", {attributes: {Class: 'loginwin'}}).appendTo(tablerow);
	$.create("label", {attributes: {For: 'username', Class: 'loginwin'}}, 'Username:').appendTo(rowcell);
	rowcell = $.create("td", {attributes: {Class: 'loginwin'}}).appendTo(tablerow);
	$.create("input", {attributes: {type: 'text', id: 'username', Class: 'loginwin'}}).appendTo(rowcell);

	tablerow = $.create("tr", {attributes: {Class: 'loginwin'}}).appendTo(table);
	rowcell = $.create("td", {attributes: {Class: 'loginwin'}}).appendTo(tablerow);
	$.create("label", {attributes: {For: 'password', Class: 'loginwin'}}, 'Password:').appendTo(rowcell);
	rowcell = $.create("td", {attributes: {Class: 'loginwin'}}).appendTo(tablerow);
	var tmpele = $.create("input", {attributes: {type: 'password', id: 'password', Class: 'loginwin'}}).appendTo(rowcell);

	$(table).after("| ");
	var login = $.create("a", {attributes: {href: '#', Class: 'loginwin'}}, '>>login').appendTo(fs);
	
	//
	// if we clock on "login", we make an ajax-call to the server to check for
	// username/password
	//
	$(login).after (" | ");
	var cancel = $.create("a", {attributes: {href: '#', Class: 'loginwin'}}, '>>cancel').appendTo(fs);
	//
	// if we click "cancel", we destroy the shade and the login window
	//
	$(cancel).click(function() {
		$('#shade').remove();
		$('#dbox').remove();
	});
	$(cancel).after (" |");
		
}
