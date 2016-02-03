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
// expects the positions without the 'px' !!!!
//
function infoWin(startX, startY, width, height, content, ele) {
	//var browserDims = innerdim();
	var instance = this;
	var workwinDimY = $(RESVIEW.winclass).height();
	var workwinDimX = $(RESVIEW.winclass).width();

	var correct_position = function(xpos, ypos) {
		var height = infoWin.prototype.winobj.outerHeight(true)
		if((xpos + width) >= workwinDimX) {
			xpos += workwinDimX - (xpos + width);
		}
		if((ypos + height) >= workwinDimY) {
			ypos += workwinDimY - (ypos + height);
		}
		return {xpos: xpos, ypos: ypos};
	}

	this.moveTo = function(xpos, ypos) {
		if (typeof infoWin.prototype.winobj === 'undefined') return;
		
		var cpos = correct_position(xpos, ypos);
		
		infoWin.prototype.winobj.css({left : cpos.xpos + 'px', top : cpos.ypos + 'px'});
	}
	
	this.killInfoWin = function() {
		if (typeof infoWin.prototype.winobj == 'undefined') return;
		infoWin.prototype.winobj.remove();
		delete infoWin.prototype.winobj;
	};

	//browserDimY = browserDims.height;
	//browserDimX = browserDims.width;
	

	if (typeof infoWin.prototype.winobj !== 'undefined') {
		infoWin.prototype.winobj.remove();
		delete infoWin.prototype.winobj;
	}

	infoWin.prototype.winobj = $('<div>', {
		'class': 'searchinfowin',
		mousemove: function(event) {
			instance.moveTo(event.pageX + 5, event.pageY + 5); // will only occur if moving window is slow
		}
	}).width(width).css({maxHeight: '600px'}).html(content).appendTo(ele);
	if (height > 0) {
		infoWin.prototype.winobj.height(height)
	}
	else {
		height = infoWin.prototype.winobj.outerHeight(true);
	}
	var cpos = correct_position(startX, startY);
	infoWin.prototype.winobj.css({position : 'absolute', left : cpos.xpos + 'px', top: cpos.ypos + 'px'})

	return this;
} 
