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

/**
 * @author Tobias Schweizer <t.schweizer@unibas.ch>
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @package jqplugins
 *
 */
(function($){
	/**
	 * This function draws the given object within the given context
	 * @params {CanvasRenderingContext2D} ctx The 2d context of the canvas
	 * @params {HTMLCanvasElement} canvas The HTML canvas to draw on
	 * @params {object} obj The object to draw
	 * @params {Boolean} open If set the drawn object will be left open (only polygon) 
	*/
	var drawObject = function(ctx, canvas, obj, open) {
		if (obj.status == 'hidden') return;
		var lineWidth = obj.lineWidth;
		if (obj.status == 'inactive') lineWidth = 0.5;
		if (obj.type == 'polygon') {			
			ctx.beginPath();
			// first point of the polygon
			ctx.moveTo(obj.points[0].x * canvas.width, obj.points[0].y * canvas.height);
			
			for (var i in obj.points) {
				// draw points 2 to n
				if (i > 0) ctx.lineTo(obj.points[i].x * canvas.width, obj.points[i].y * canvas.height);
			}
			ctx.strokeStyle = obj.lineColor;
			ctx.lineWidth = lineWidth;
			// close polygon if open is not set or false
			if (open === undefined || open === false) ctx.closePath();
			ctx.stroke();
			
		} else if (obj.type == 'rectangle'){			
			var x = Math.min(obj.points[0].x * canvas.width, obj.points[1].x * canvas.width);
			var y = Math.min(obj.points[0].y * canvas.height, obj.points[1].y * canvas.height);
			var w = Math.abs((obj.points[0].x * canvas.width) - (obj.points[1].x * canvas.width));
			var h = Math.abs((obj.points[0].y * canvas.height) - (obj.points[1].y * canvas.height));

			ctx.strokeStyle = obj.lineColor;
			ctx.lineWidth = lineWidth;
			ctx.strokeRect(x, y, w, h);

		} else if (obj.type == 'circle') {
			// calculate the absolute values for the x and y axis out of the relative radii
			var r_x = canvas.width * obj.radius.x;
			var r_y = canvas.height * obj.radius.y;
			
			// use Pythagoras to calculate the radius in absolute values
			var radius = Math.sqrt(Math.pow(r_x, 2) + Math.pow(r_y, 2)); 

			ctx.beginPath();
			ctx.strokeStyle = obj.lineColor;
			ctx.lineWidth = lineWidth;
			ctx.arc(obj.points[0].x * canvas.width, obj.points[0].y * canvas.height, radius, 0, 2*Math.PI, true);
			ctx.stroke();
		}
		return;
	};
	
	/**
	 * This function clears the given canvas
	 * @params {CanvasRenderingContext2D} ctx The 2d context of the canvas
	 * @params {HTMLCanvasElement} canvas The HTML canvas to be cleared
	 */
	var clearCanvas = function(ctx, canvas) {
		ctx.clearRect(0, 0, canvas.width, canvas.height);
		return;
	};
	
	/**
	 * This function redraws the existing objects on the background canvas
	 * @params {object} localdata The local data for this canvas
	 */
	var redrawObjects = function(localdata) {
		clearCanvas(localdata.bg_ctx, localdata.bg_canvas);
		// loop the array of existing objects and redraw them
		for (var i in localdata.drawn_objs) {
			drawObject(localdata.bg_ctx, localdata.bg_canvas, localdata.drawn_objs[i]);
		}
		return;
	};
	
	/**
	 * This function detects drawn objects when the cursor is positioned over them
	 * @params {event object} event The mousemove event
	 */
	var regionHovered = function(event) {
		// called after each mousemove
		event.stopPropagation();
		event.preventDefault();
		
		// 'this' refers to the foreground canvas which catches the events
		var $this = $(this);
		var localdata = $this.data('localdata');
		
		// get mouse position relative to the canvas' grid and convert it to relative values
		var canvas_offset = $this.offset();
		var pos_x = (event.pageX - canvas_offset.left)/this.width;
		var pos_y = (event.pageY - canvas_offset.top)/this.height;

		// stores the indizes of the active region, reinit here to undefined 
		localdata.selected_obj = undefined; 

		clearCanvas(localdata.fg_ctx, localdata.fg_canvas);

		// loop the existing objects
		for (var i in localdata.drawn_objs_ordered) {
			//
			// if the cursor is positioned within the current figure its index is pushed onto a stack -> this is wrong, there is only one active element at a specific time!!!!!
			//
			
			if (localdata.drawn_objs_ordered[i].type == 'rectangle') {
				var x1 = Math.min(localdata.drawn_objs_ordered[i].points[0].x, localdata.drawn_objs_ordered[i].points[1].x);
				var x2 = Math.max(localdata.drawn_objs_ordered[i].points[0].x, localdata.drawn_objs_ordered[i].points[1].x);
				var y1 = Math.min(localdata.drawn_objs_ordered[i].points[0].y, localdata.drawn_objs_ordered[i].points[1].y);
				var y2 = Math.max(localdata.drawn_objs_ordered[i].points[0].y, localdata.drawn_objs_ordered[i].points[1].y);
				
				if (pos_x >= x1 && pos_x <= x2 && pos_y >= y1 && pos_y <= y2) {
					// cursor is positioned within the rectangle, so region is hovered
					if (localdata.drawn_objs_ordered[i].status != 'active') continue;
					clearCanvas(localdata.fg_ctx, localdata.fg_canvas);
					drawObject(localdata.fg_ctx, localdata.fg_canvas, {
						points: localdata.drawn_objs_ordered[i].points,
						lineColor : localdata.settings.highlight_line_color, 
						lineWidth : localdata.settings.highlight_line_width,
						type : 'rectangle'
					});
					localdata.selected_obj = localdata.drawn_objs_ordered[i].original_index;
				}
			} else if (localdata.drawn_objs_ordered[i].type == 'polygon') {
				// use a rectangle to determine if the cursor is positioned within the polygon or not
				var min_x = 1, min_y = 1;
				var max_x = 0, max_y = 0;
				// identify the maximal and minimal values for x and y in order to create the rectangle
				for (var j in localdata.drawn_objs_ordered[i].points) {
					if (localdata.drawn_objs_ordered[i].points[j].x < min_x) min_x = localdata.drawn_objs_ordered[i].points[j].x;
					if (localdata.drawn_objs_ordered[i].points[j].x > max_x) max_x = localdata.drawn_objs_ordered[i].points[j].x;
				
					if (localdata.drawn_objs_ordered[i].points[j].y < min_y) min_y = localdata.drawn_objs_ordered[i].points[j].y;
					if (localdata.drawn_objs_ordered[i].points[j].y > max_y) max_y = localdata.drawn_objs_ordered[i].points[j].y;
				}
				
				if (pos_x >= min_x && pos_x <= max_x && pos_y >= min_y && pos_y <= max_y) {
					// cursor is positioned within the rectangle, so region is hovered
					if (localdata.drawn_objs_ordered[i].status != 'active') continue;
					clearCanvas(localdata.fg_ctx, localdata.fg_canvas);
					drawObject(localdata.fg_ctx, localdata.fg_canvas, {
						points : localdata.drawn_objs_ordered[i].points, 
						lineColor : localdata.settings.highlight_line_color, 
						lineWidth : localdata.settings.highlight_line_width,
						type : 'polygon'
					});
					localdata.selected_obj = localdata.drawn_objs_ordered[i].original_index;
				}
			} else if (localdata.drawn_objs_ordered[i].type == 'circle') {
				// use a rectangle to determine if the cursor is positioned within the circle or not
				var r_x = this.width * localdata.drawn_objs_ordered[i].radius.x;
				var r_y = this.height * localdata.drawn_objs_ordered[i].radius.y;
				var radius = Math.sqrt(Math.pow(r_x, 2) + Math.pow(r_y, 2));
				
				var x1 = ((localdata.drawn_objs_ordered[i].points[0].x * this.width) - radius)/this.width; 
				var y1 = ((localdata.drawn_objs_ordered[i].points[0].y * this.height) - radius)/this.height; 
				var x2 = ((localdata.drawn_objs_ordered[i].points[0].x * this.width) + radius)/this.width; 
				var y2 = ((localdata.drawn_objs_ordered[i].points[0].y * this.height) + radius)/this.height; 
				
				if (pos_x >= x1 && pos_x <= x2 && pos_y >= y1 && pos_y <= y2) {
					// cursor is positioned within the rectangle, so region is hovered
					if (localdata.drawn_objs_ordered[i].status != 'active') continue;
					clearCanvas(localdata.fg_ctx, localdata.fg_canvas);
					drawObject(localdata.fg_ctx, localdata.fg_canvas, {
						points: localdata.drawn_objs_ordered[i].points,
						radius: localdata.drawn_objs_ordered[i].radius,
						lineColor : localdata.settings.highlight_line_color, 
						lineWidth : localdata.settings.highlight_line_width,
						type : 'circle'
					});
					localdata.selected_obj = localdata.drawn_objs_ordered[i].original_index;
				}
			
			}	
		}
		
		/*
		// trigger hpover_lost_cb for previously hovered regions
		if (localdata.selected_objs !== undefined) {
			for (var i in localdata.selected_objs) {
				localdata.hover_cb(event, localdata.selected_objs[i]);
			}
		}*/
		
		// trigger the hover callback for the hovered region(s)
		if (localdata.selected_obj !== undefined) localdata.hover_cb(event, localdata.selected_obj);
		return;
	};
	
   /**
	* Highlight the given object (use the defined values to redraw the region on the fg canvas)
	* @params {object} localdata
	* @params {int} i index
	*/
	var highlightObject = function(localdata, i) {
		if (localdata.drawn_objs[i].type == 'rectangle') {
			drawObject(localdata.fg_ctx, localdata.fg_canvas, {
				points: localdata.drawn_objs[i].points,
				lineColor : localdata.settings.highlight_line_color, 
				lineWidth : localdata.settings.highlight_line_width,
				type : 'rectangle'
			});
		} else if (localdata.drawn_objs[i].type == 'polygon') {
			drawObject(localdata.fg_ctx, localdata.fg_canvas, {
				points : localdata.drawn_objs[i].points, 
				lineColor : localdata.settings.highlight_line_color, 
				lineWidth : localdata.settings.highlight_line_width,
				type : 'polygon'
			});				
		} else if (localdata.drawn_objs[i].type == 'circle') {
			drawObject(localdata.fg_ctx, localdata.fg_canvas, {
				points: localdata.drawn_objs[i].points,
				radius: localdata.drawn_objs[i].radius,
				lineColor : localdata.settings.highlight_line_color, 
				lineWidth : localdata.settings.highlight_line_width,
				type : 'circle'
			});
		}
		return;
	};
	
	/**
	 * This function is called when the user clicks on the foreground canvas
	 * @params {event object} event The click event
	 */
	var regionSelected = function(event) {
		// called after a click on a selected object
		event.stopPropagation();
		event.preventDefault();
		
		// 'this' refers to the foreground canvas
		var $this = $(this);
		var localdata = $this.data('localdata');
		
		// trigger the click callback for the clicked region(s)
		if (localdata.selected_obj !== undefined) localdata.clicked_cb(localdata.selected_obj);
		
		return;
	};
	
	/**
	 * This functions handles the drawing process
	 * @params {event object} event The mousedown event
	 */
	var startDrawing = function(event) {
		event.stopPropagation();
		event.preventDefault();
		
		// 'this' refers to the foreground canvas
		var $this = $(this);
		var localdata = $this.data('localdata');
		var canvas_offset = $this.offset();
		var start_x = (event.pageX - canvas_offset.left)/this.width; 
		var start_y = (event.pageY - canvas_offset.top)/this.height;
		var to_x, to_y, mouse_up = false;
		var radius, r_x, r_y;
		
		// initialize the tmp_obj with the given values
		var tmp_obj = {
			status: 'active',
			lineColor: localdata.tmp_obj.lineColor,
			lineWidth: localdata.tmp_obj.lineWidth,
			points: [{x: start_x, y: start_y}],
			type: localdata.tmp_obj.type,
		};
		
		// unbind this function
		$this.unbind('.regions_draw_init');
		
		$this.bind('mousemove.regions_draw_active', function(event) {
			event.stopPropagation();
			event.preventDefault();
			
			clearCanvas(localdata.fg_ctx, this);
			
			to_x = (event.pageX - canvas_offset.left)/this.width; 
			to_y = (event.pageY - canvas_offset.top)/this.height;
			
			if (tmp_obj.type == 'rectangle' || tmp_obj.type == 'polygon') {
				if (tmp_obj.points.length > 1 && mouse_up === false) {
					// replace the last point in the points array, drawing of current line is still in progress
					tmp_obj.points.splice(tmp_obj.points.length -1, 1, {x: to_x, y: to_y});
				} else {
					// push a new point on the array (rectangle: the second point setting the dimensions; polygon: a new line has been created)
					// this part has to be run one time at least
					tmp_obj.points.push({x: to_x, y: to_y});
				}
			}
			else if (tmp_obj.type == 'circle') {
				r_x = this.width * Math.abs(to_x - start_x);
				r_y = this.height * Math.abs(to_y - start_y);
				radius = Math.sqrt(Math.pow(r_x, 2) + Math.pow(r_y, 2));
				
				if ((start_x * this.width) - radius  > 0 && (start_x * this.width) + radius < this.width && (start_y * this.height) - radius > 0 && (start_y * this.height) + radius < this.height) {
					tmp_obj.radius = {};
					tmp_obj.radius.x = Math.abs(to_x - start_x);
					tmp_obj.radius.y = Math.abs(to_y - start_y);
				}
			}
			
			mouse_up = false;
			drawObject(localdata.fg_ctx, this, tmp_obj, true);
			return;
		});
		
		$this.bind('mouseup.regions_draw_active', function(event) {
			event.stopPropagation();
			event.preventDefault();
			
			if (tmp_obj.type == 'polygon') {
				// push this point on the stack on next mousemove
				mouse_up = true;
			} else {
				// terminate drawing mode for types circle and rectangle
				
				// check if rectangle has two points (topleft and downright)
				if (tmp_obj.type == 'rectangle' && (tmp_obj.points.length == 1 || (Math.abs(tmp_obj.points[0].x - tmp_obj.points[1].x) == 0 || (Math.abs(tmp_obj.points[0].y - tmp_obj.points[1].y) == 0)))) {
					// at least one point is not given: continue drawing
					return;
				} 
				
				$this.unbind('.regions_draw_active');
				
				// add tmp_obj to the ojects array and redraw
				localdata.drawn_objs.push(tmp_obj);
				clearCanvas(localdata.fg_ctx, this);
				redrawObjects(localdata);
				
				localdata.draw_cb(tmp_obj, localdata.drawn_objs.length -1);
				delete localdata.draw_cb;
			}
			return;
		});
		
		$this.bind('dblclick.regions_draw_active', function(event) {
			event.stopPropagation();
			event.preventDefault();
			
			if (tmp_obj.points[tmp_obj.points.length -1].x == tmp_obj.points[tmp_obj.points.length -2].x && tmp_obj.points[tmp_obj.points.length -1].y == tmp_obj.points[tmp_obj.points.length -2].y) {
				// delete the last point, it has been created while double clicking (known problem in Safari)
				tmp_obj.points.splice(tmp_obj.points.length -1, 1);
			}
			
			// prevent incomplete figures: a polygon needs to have at least three points
			if (tmp_obj.points.length < 3) return;
			
			// terminate drawing mode
			$this.unbind('.regions_draw_active');
			
			// add tmp_obj to the ojects array and redraw
			localdata.drawn_objs.push(tmp_obj);
			clearCanvas(localdata.fg_ctx, this);
			redrawObjects(localdata);
			
			localdata.draw_cb(tmp_obj, localdata.drawn_objs.length -1);
			delete localdata.draw_cb;
			return;
		});
		return;
	};
	
	/**
	 * This function edits an existing objects
	 * @params {object} localdata The local data bound to the foreground div
	 */
	var editObject = function(localdata) {
		// general vars for all types of objects
		var $this = $(localdata.fg_canvas);
		var canvas = localdata.fg_canvas;
		var canvas_offset = $this.offset();
		
		var square_dim = localdata.settings.default_square_dim;
		var border_dim = localdata.settings.default_border_dim;
		var border_color = localdata.settings.default_border_color;
		
		$this.bind('click.regions_edit', function(event) {
			// user has clicked BESIDES mover or handler: end edit mode
			$this.parent().find('.handler').remove();
			$this.parent().find('.mover').remove();
			$this.unbind('.regions_edit');
			localdata.edit_cb(localdata.drawn_objs[localdata.edit], localdata.edit);
			// set plugin back to detection mode
			start_detection_mode(localdata);
		});
		
		if (localdata.drawn_objs[localdata.edit].type == 'rectangle') {
			// editing for rectangle
			var rect = localdata.drawn_objs[localdata.edit];
			
			// identify the dimensions of the rectangle
			var x1 = Math.min(rect.points[0].x, rect.points[1].x) * canvas.width;
			var y1 = Math.min(rect.points[0].y, rect.points[1].y) * canvas.height;
			var x2 = Math.max(rect.points[0].x, rect.points[1].x) * canvas.width;
			var y2 = Math.max(rect.points[0].y, rect.points[1].y) * canvas.height;
			
			// create a mover div and a div for each corner of the rectangle with an event
			// here the handlers are positioned relative to the mover div
			var mover = $('<div>', {'class': 'mover'}).css({width: Math.round(x2 - x1) + 'px', height: Math.round(y2 - y1) + 'px', position: 'absolute', left: parseInt($this.css('left')) + Math.round(x1) + 'px', top: parseInt($this.css('top')) + Math.round(y1) + 'px'}).appendTo($this.parent());
			var upper_left = $('<div>', {'data-corner': 'upper_left', 'class': 'handler'}).css({width: square_dim + 'px', height: square_dim + 'px', position: 'absolute', left: -(square_dim + border_dim)/2 + 'px', top: -(square_dim + border_dim)/2, border: border_dim + 'px solid ' + border_color}).appendTo(mover);
			var upper_right = $('<div>', {'data-corner': 'upper_right', 'class': 'handler'}).css({width: square_dim + 'px', height: square_dim + 'px', position: 'absolute', right: -(square_dim + border_dim)/2 + 'px', top: -(square_dim + border_dim)/2, border: border_dim + 'px solid ' + border_color}).appendTo(mover);
			var lower_right = $('<div>', {'data-corner': 'lower_right', 'class': 'handler'}).css({width: square_dim + 'px', height: square_dim + 'px', position: 'absolute', right: -(square_dim + border_dim)/2 + 'px', bottom: -(square_dim + border_dim)/2, border: border_dim + 'px solid ' + border_color}).appendTo(mover);
			var lower_left = $('<div>', {'data-corner': 'lower_left', 'class': 'handler'}).css({width: square_dim + 'px', height: square_dim + 'px', position: 'absolute', left: -(square_dim + border_dim)/2 + 'px', bottom: -(square_dim + border_dim)/2, border: border_dim + 'px solid ' + border_color}).appendTo(mover);

			$('div[data-corner]').bind('mousedown.regions_corner_init', function(event) {
				// one of the four corners is activated
				
				event.stopPropagation();
				event.preventDefault();
				
				// identify this corner
				var corner = $(this).attr('data-corner');
				
				var start_x = (event.pageX - canvas_offset.left)/canvas.width; 
				var start_y = (event.pageY - canvas_offset.top)/canvas.height;
				var to_x, to_y, diff_x, diff_y;
				var ul_x, ul_y, lr_x, lr_y;
								
				$('#ecatcher').css({display: 'block'}).bind('mousemove.regions_corner', function(event) {	
					event.stopPropagation();
					event.preventDefault();
					
					to_x = (event.pageX - canvas_offset.left)/canvas.width; 
					to_y = (event.pageY - canvas_offset.top)/canvas.height;
					
					diff_x = to_x - start_x;
					diff_y = to_y - start_y;
					
					switch (corner) {
						case 'upper_left':						
							ul_x = (rect.points[0].x < rect.points[1].x) ? 0 : 1;
							lr_x = (ul_x == 0) ? 1 : 0;
							ul_y = (rect.points[0].y < rect.points[1].y) ? 0 : 1;
							lr_y = (ul_y == 0) ? 1 : 0;
														
							// stop if points are about to cross
							if ((rect.points[ul_x].x + diff_x) >= rect.points[lr_x].x) diff_x = 0;
							if ((rect.points[ul_y].y + diff_y) >= rect.points[lr_y].y) diff_y = 0;
							
							// prevent handler form leaving the canvas either on the left or on the top
							if ((rect.points[ul_x].x + diff_x) < 0) diff_x = 0;
							if ((rect.points[ul_y].y + diff_y) < 0) diff_y = 0;
							
							localdata.drawn_objs[localdata.edit].points[ul_x].x += diff_x;
							localdata.drawn_objs[localdata.edit].points[ul_y].y += diff_y;
									
							break;
						case 'upper_right':
							lr_x = (rect.points[0].x > rect.points[1].x) ? 0 : 1;
							ul_x = (lr_x == 0) ? 1 : 0;
							ul_y = (rect.points[0].y < rect.points[1].y) ? 0 : 1;
							lr_y = (ul_y == 0) ? 1 : 0;
							
							// stop if points are about to cross
							if ((rect.points[lr_x].x + diff_x) < rect.points[ul_x].x) diff_x = 0;
							if ((rect.points[ul_y].y + diff_y) >= rect.points[lr_y].y) diff_y = 0;
							
							// prevent handler form leaving the canvas either on the right or on the top
							if ((rect.points[lr_x].x + diff_x) > 1) diff_x = 0;
							if ((rect.points[ul_y].y + diff_y) < 0) diff_y = 0;
							
							localdata.drawn_objs[localdata.edit].points[lr_x].x += diff_x;
							localdata.drawn_objs[localdata.edit].points[ul_y].y += diff_y;
										
							break;
						case 'lower_right':
							lr_x = (rect.points[0].x > rect.points[1].x) ? 0 : 1;
							ul_x = (lr_x == 0) ? 1 : 0;
							lr_y = (rect.points[0].y > rect.points[1].y) ? 0 : 1;
							ul_y = (lr_y == 0) ? 1 : 0;
							
							// stop if points are about to cross
							if ((rect.points[lr_x].x + diff_x) <= rect.points[ul_x].x) diff_x = 0;
							if ((rect.points[lr_y].y + diff_y) <= rect.points[ul_y].y) diff_y = 0;
							
							// prevent handler form leaving the canvas either on the left or on the bottom
							if ((rect.points[lr_x].x + diff_x) > 1) diff_x = 0;
							if ((rect.points[lr_y].y + diff_y) > 1) diff_y = 0;
							
							localdata.drawn_objs[localdata.edit].points[lr_x].x += diff_x;
							localdata.drawn_objs[localdata.edit].points[lr_y].y += diff_y;
						
							break;
						case 'lower_left':
							ul_x = (rect.points[0].x < rect.points[1].x) ? 0 : 1;
							lr_x = (ul_x == 0) ? 1 : 0;
							lr_y = (rect.points[0].y > rect.points[1].y) ? 0 : 1;
							ul_y = (lr_y == 0) ? 1 : 0;
							
							// stop if points are about to cross
							if ((rect.points[ul_x].x + diff_x) >= rect.points[lr_x].x) diff_x = 0;
							if ((rect.points[lr_x].y + diff_y) <= rect.points[ul_y].y) diff_y = 0;
							
							// prevent handler form leaving the canvas either on the left or on the bottom
							if ((rect.points[ul_x].x + diff_x) < 0) diff_x = 0;
							if ((rect.points[lr_x].y + diff_y) > 1) diff_y = 0;
							
							localdata.drawn_objs[localdata.edit].points[ul_x].x += diff_x;
							localdata.drawn_objs[localdata.edit].points[lr_y].y += diff_y;
							
							break;
						default:
							break;	
					}
					
					// adjust the position and the dimensions of the mover (handlers are positioned relative to the mover)
					mover.css({
						width: Math.round((localdata.drawn_objs[localdata.edit].points[lr_x].x - localdata.drawn_objs[localdata.edit].points[ul_x].x) * canvas.width) + 'px',
						height: Math.round((localdata.drawn_objs[localdata.edit].points[lr_y].y - localdata.drawn_objs[localdata.edit].points[ul_y].y) * canvas.height) + 'px', 
						left: parseInt($(canvas).css('left')) + Math.round(localdata.drawn_objs[localdata.edit].points[ul_x].x * canvas.width) + 'px',
						top: parseInt($(canvas).css('top')) + Math.round(localdata.drawn_objs[localdata.edit].points[ul_y].y * canvas.height) + 'px'
					});
					
					redrawObjects(localdata);
					localdata.edit_cb(localdata.drawn_objs[localdata.edit], localdata.edit);
					
					start_x = to_x;
					start_y = to_y;
					
					return;
				});
				
				$('#ecatcher').bind('mouseup.regions_corner', function(event) {
					event.stopPropagation();
					event.preventDefault();
					
					$('#ecatcher').unbind('.regions_corner').css({display: 'none'});
					return;
				});
			});
			
		} else if (localdata.drawn_objs[localdata.edit].type == 'polygon') {
			var point;
			var min_x = 1, min_y = 1;
			var max_x = 0, max_y = 0;
			
			for (var j in localdata.drawn_objs[localdata.edit].points) {
				if (localdata.drawn_objs[localdata.edit].points[j].x < min_x) min_x = localdata.drawn_objs[localdata.edit].points[j].x;
				if (localdata.drawn_objs[localdata.edit].points[j].x > max_x) max_x = localdata.drawn_objs[localdata.edit].points[j].x;
			
				if (localdata.drawn_objs[localdata.edit].points[j].y < min_y) min_y = localdata.drawn_objs[localdata.edit].points[j].y;
				if (localdata.drawn_objs[localdata.edit].points[j].y > max_y) max_y = localdata.drawn_objs[localdata.edit].points[j].y;
			}
			
			var mover = $('<div>', {'class' : 'mover'}).css({position: 'absolute', left: parseInt($this.css('left')) + Math.round(min_x * canvas.width) + 'px', top: parseInt($this.css('top')) + Math.round(min_y * canvas.height) + 'px', width: (max_x - min_x) * canvas.width + 'px', height: (max_y - min_y) * canvas.height + 'px'}).appendTo($this.parent());
			
			for (var i in localdata.drawn_objs[localdata.edit].points) {
				point = localdata.drawn_objs[localdata.edit].points[i];
				
				$('<div>', {'data-index': i, 'class': 'handler'}).css({position: 'absolute', width: square_dim + 'px', height: square_dim + 'px', left: parseInt($this.css('left')) + Math.round(point.x * canvas.width) - (square_dim + border_dim)/2 + 'px', top: parseInt($this.css('top')) + Math.round(point.y * canvas.height) - (square_dim + border_dim)/2 + 'px', border: '1px solid red'}).appendTo($this.parent());
			}
			
			var handler = $('div[data-index]'); 
			
			$('div[data-index]').bind('mousedown.regions_init', function(event) {
				event.stopPropagation();
				event.preventDefault();
				
				var to_x, to_y, diff_x, diff_y, handler_left, handler_top;
				// get the index of the active handler
				var index = $(this).attr('data-index');
				var start_x = (event.pageX - canvas_offset.left)/canvas.width; 
				var start_y = (event.pageY - canvas_offset.top)/canvas.height;
				var active_handler = $(this);
				
				$('#ecatcher').css({display: 'block'}).bind('mousemove.regions_corner', function(event) {	
					event.stopPropagation();
					event.preventDefault();
					
					to_x = (event.pageX - canvas_offset.left)/canvas.width; 
					to_y = (event.pageY - canvas_offset.top)/canvas.height; 
					
					diff_x = to_x - start_x;
					diff_y = to_y - start_y;
					
					// prevent handler form leaving the canvas
					if ((localdata.drawn_objs[localdata.edit].points[index].x + diff_x) < 0 || (localdata.drawn_objs[localdata.edit].points[index].x + diff_x) > 1) diff_x = 0;
					if ((localdata.drawn_objs[localdata.edit].points[index].y + diff_y) < 0 || (localdata.drawn_objs[localdata.edit].points[index].y + diff_y) > 1) diff_y = 0;
					
					localdata.drawn_objs[localdata.edit].points[index].x += diff_x;
					localdata.drawn_objs[localdata.edit].points[index].y += diff_y;
					
					handler_left = parseInt(active_handler.css('left'));
					handler_top = parseInt(active_handler.css('top'));
					
					active_handler.css({
						left: handler_left + (diff_x * canvas.width),
						top: handler_top + (diff_y * canvas.height)
					});
					
					min_x = 1; 
					min_y = 1;
					max_x = 0;
					max_y = 0;
					
					for (var j in localdata.drawn_objs[localdata.edit].points) {
						if (localdata.drawn_objs[localdata.edit].points[j].x < min_x) min_x = localdata.drawn_objs[localdata.edit].points[j].x;
						if (localdata.drawn_objs[localdata.edit].points[j].x > max_x) max_x = localdata.drawn_objs[localdata.edit].points[j].x;

						if (localdata.drawn_objs[localdata.edit].points[j].y < min_y) min_y = localdata.drawn_objs[localdata.edit].points[j].y;
						if (localdata.drawn_objs[localdata.edit].points[j].y > max_y) max_y = localdata.drawn_objs[localdata.edit].points[j].y;
					}
					
					mover.css({
						left: parseInt($this.css('left')) + Math.round(min_x * canvas.width) + 'px', 
						top: parseInt($this.css('top')) + Math.round(min_y * canvas.height) + 'px', 
						width: (max_x - min_x) * canvas.width + 'px', 
						height: (max_y - min_y) * canvas.height + 'px'
					});
					
					redrawObjects(localdata);
					localdata.edit_cb(localdata.drawn_objs[localdata.edit], localdata.edit);
					
					start_x = to_x;
					start_y = to_y;
					
				});
				
				$('#ecatcher').bind('mouseup.regions_corner', function(event) {
					event.stopPropagation();
					event.preventDefault();

					$('#ecatcher').unbind('.regions_corner').css({display: 'none'});
					return;
				});
			});	
			
		} else if (localdata.drawn_objs[localdata.edit].type == 'circle') {
			var r_x = canvas.width * localdata.drawn_objs[localdata.edit].radius.x;
			var r_y = canvas.height * localdata.drawn_objs[localdata.edit].radius.y;
			var radius = Math.sqrt(Math.pow(r_x, 2) + Math.pow(r_y, 2));
			
			var x1 = ((localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) - radius); 
			var y1 = ((localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) - radius); 
			var x2 = ((localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) + radius); 
			var y2 = ((localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) + radius);
			
			var mover = $('<div>', {'class': 'mover'}).css({width: Math.round(x2 - x1) + 'px', height: Math.round(y2 - y1) + 'px', position: 'absolute', left: parseInt($this.css('left')) + Math.round(x1) + 'px', top: parseInt($this.css('top')) + Math.round(y1) + 'px'}).appendTo($this.parent());
			var handler = $('<div>', {'class' : 'handler'}).css({position: 'absolute', width : square_dim + 'px', height: square_dim + 'px', left: parseInt($this.css('left')) + (localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) + 'px', top: parseInt($this.css('top')) + (localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) - radius  - square_dim/2 + 'px', border: '1px solid red'}).appendTo($this.parent());
		
			$this.parent().find('.handler').bind('mousedown.regions_corner_init', function(event) {
				event.stopPropagation();
				event.preventDefault();
				
				var start_x = (event.pageX - canvas_offset.left)/canvas.width;
				var start_y = (event.pageY - canvas_offset.top)/canvas.height;
				var to_x, to_y, handler_left, handler_top, new_radius_x, new_radius_y, blind_diff_x = 0, blind_diff_y = 0;
			
				$('#ecatcher').css({display: 'block'}).bind('mousemove.regions_corner', function(event) {	
					event.stopPropagation();
					event.preventDefault();
					
					to_x = (event.pageX - canvas_offset.left)/canvas.width; 
					to_y = (event.pageY - canvas_offset.top)/canvas.height; 
					
					diff_x = (to_x - start_x);
					diff_y = (to_y - start_y);
					
					// prevent circle from crossing any border
					new_radius_x = Math.abs(to_x - localdata.drawn_objs[localdata.edit].points[0].x);
					new_radius_y = Math.abs(to_y - localdata.drawn_objs[localdata.edit].points[0].y);
					
					r_x = canvas.width * new_radius_x;
					r_y = canvas.height * new_radius_y;
					radius = Math.sqrt(Math.pow(r_x, 2) + Math.pow(r_y, 2));
					
					// presumably there is a 'blind' difference caused by the cursor moving while the changing of the figure is blocked
					if (((localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) - radius) < 0 || ((localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) + radius) > canvas.width || ((localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) - radius) < 0 || ((localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) + radius) > canvas.height) {
						blind_diff_x += diff_x;
						blind_diff_y += diff_y;
						diff_x = 0;
						diff_y = 0;
					} else {
						blind_diff_x = 0;
						blind_diff_y = 0;
						localdata.drawn_objs[localdata.edit].radius.x = Math.abs(to_x - localdata.drawn_objs[localdata.edit].points[0].x);
						localdata.drawn_objs[localdata.edit].radius.y = Math.abs(to_y - localdata.drawn_objs[localdata.edit].points[0].y);
					}
										
					// changes the handler's position relative to the movement			
					handler_left = parseInt(handler.css('left'));
					handler_top = parseInt(handler.css('top'));
								
					handler.css({
						top: handler_top + ((diff_y /*+ blind_diff_y*/) * canvas.height),
						left: handler_left + ((diff_x /*+ blind_diff_x*/) * canvas.width)
					});			
					
					// update the position of the mover
					r_x = canvas.width * localdata.drawn_objs[localdata.edit].radius.x;
					r_y = canvas.height * localdata.drawn_objs[localdata.edit].radius.y;
					radius = Math.sqrt(Math.pow(r_x, 2) + Math.pow(r_y, 2));
					
					x1 = ((localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) - radius); 
					y1 = ((localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) - radius); 
					x2 = ((localdata.drawn_objs[localdata.edit].points[0].x * canvas.width) + radius); 
					y2 = ((localdata.drawn_objs[localdata.edit].points[0].y * canvas.height) + radius);
					
					mover.css({width: Math.round((x2 - x1)) + 'px', height: Math.round((y2 - y1)) + 'px', position: 'absolute', left: parseInt($this.css('left')) + Math.round(x1) + 'px', top: parseInt($this.css('top')) + Math.round(y1) + 'px'});
								
					redrawObjects(localdata);
					localdata.edit_cb(localdata.drawn_objs[localdata.edit], localdata.edit);

					start_y = to_y;
					start_x = to_x;					
				});
				
				$('#ecatcher').bind('mouseup.regions_corner', function(event) {
					event.stopPropagation();
					event.preventDefault();

					$('#ecatcher').unbind('.regions_corner').css({display: 'none'});
					return;
				});
			});
		}
		
		// general moving function, mover has to exist!		
		/**
		 * This functions moves the object as a whole
		 * @params {event object} The mousedown event
		 */
		$(mover).bind('mousedown.edit_mover_init', function(event) {
			event.stopPropagation();
			event.preventDefault();
			
			var start_x = (event.pageX - canvas_offset.left)/canvas.width; 
			var start_y = (event.pageY - canvas_offset.top)/canvas.height;
			var to_x, to_y, diff_x, diff_y;
			var mover_top, mover_left, handler_top, handler_left;

			$('#ecatcher').css({display: 'block'}).bind('mousemove.regions_move_active', function(event) {
				event.stopPropagation();
				event.preventDefault();
				
				mover_top = parseInt(mover.css('top'));
				mover_left = parseInt(mover.css('left'));

				to_x = (event.pageX - canvas_offset.left)/canvas.width; 
				to_y = (event.pageY - canvas_offset.top)/canvas.height;

				diff_x = to_x - start_x;
				diff_y = to_y - start_y;

				// prevent border crossing
				if ((mover_left - parseInt($this.css('left')) + (diff_x * canvas.width)) < 0 || (mover_left - parseInt($this.css('left')) + parseInt(mover.css('width')) + (diff_x * canvas.width)) > canvas.width) diff_x = 0;
				if ((mover_top - parseInt($this.css('top')) + (diff_y * canvas.height)) < 0 || (mover_top - parseInt($this.css('top')) + parseInt(mover.css('height')) + (diff_y * canvas.height)) > canvas.height) diff_y = 0;
				
				// adjust als points to the new position
				for (var i in localdata.drawn_objs[localdata.edit].points) {
					localdata.drawn_objs[localdata.edit].points[i].x += diff_x;
					localdata.drawn_objs[localdata.edit].points[i].y += diff_y;
				}
								
				mover.css({
					top: Math.round(mover_top + (diff_y * canvas.height)) + 'px',
					left: Math.round(mover_left + (diff_x * canvas.width)) + 'px'
				});

				// check for existence of handler in order to to prevent a reference error (handler not needed with rectangle)
				if (typeof handler !== 'undefined') {
					handler.each(function() {
						handler_top = parseInt($(this).css('top'));
						handler_left = parseInt($(this).css('left'));
					
						$(this).css({
							top: handler_top + (diff_y * canvas.height) + 'px',
							left: handler_left + (diff_x * canvas.width) + 'px'
						});
					});
				}

				redrawObjects(localdata);
				localdata.edit_cb(localdata.drawn_objs[localdata.edit], localdata.edit);

				start_x = to_x;
				start_y = to_y;
				return;
			});

			$('#ecatcher').bind('mouseup.regions_move_active', function() {
				$(this).unbind('.regions_move_active').css({display: 'none'});
				return;
			});
		});
	};
	
	
	var create_geometrical_order = function(localdata) {
		// create a COPY of localdata.drawn_objects
		localdata.drawn_objs_ordered = localdata.drawn_objs.slice(0);
		
		// Consider the geometrical 'order' of the figures: 
		// if a small figure is positioned within the area of a bigger figure, it should be positioned 'on' (after) the latter one
		var geometrical_order = function(first_figure, second_figure) {
			// only compare rectangles for the moment, implement this functionality also for circle and polygon !!!!!!!!!
			if (first_figure.type != 'rectangle' || second_figure.type != 'rectangle') return 0; // keep order
			if (first_figure.points[0].x >= second_figure.points[0].x && first_figure.points[0].y >= second_figure.points[0].y && first_figure.points[1].x <= second_figure.points[1].x && first_figure.points[1].y <= second_figure.points[1].y) {
				// first_figure is 'contained' in second_figure, change this order!
				return 1;
			} else {
				// order is correct: first_second comes before second_figure
				return -1;
			}
		};
		
		// store the original index because the original order will possibly be changed
		for (var i = 0; i < localdata.drawn_objs_ordered.length; i++) {
			localdata.drawn_objs_ordered[i].original_index = i;
		}
		
		// sort the array using the function 'geometrical_order'
		localdata.drawn_objs_ordered.sort(geometrical_order);
		
	};
	
	/**
	 * Bind events for detection mode
	 * @params {object} localdata
	 */
	var start_detection_mode = function(localdata) {
		
		if (localdata.drawn_objs.length == 0) return;
		create_geometrical_order(localdata);
		
		$(localdata.fg_canvas).unbind('.regions_detect'); // clear all bindings before adding the new ones to prevent multiple bindings...
		$(localdata.fg_canvas).bind('mousemove.regions_detect', regionHovered);
		$(localdata.fg_canvas).bind('click.regions_detect', regionSelected);		
	};
	
	/**
	 * Remove all handlers and events
	 * @params {object} localdata
	 */
	var remove_handlers_and_events = function(localdata) {
		// unbind and remove everything
		localdata.selected_obj = undefined;
		delete localdata.tmp_obj;
		delete localdata.edit;

		$(localdata.fg_canvas).unbind('.regions_detect').unbind('.regions_draw_init').unbind('.regions_draw_active').unbind('.regions_edit');
		$(localdata.fg_canvas).parent().find('.handler').remove();
		$(localdata.fg_canvas).parent().find('.mover').remove();
		clearCanvas(localdata.fg_ctx, localdata.fg_canvas);
	};
	
	var methods = {
		/**
		 * Initializes the plugin
		 * @params {object} options Set the values for the plugin (optional)
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		*/
		init: function(options) { // $(element).regions('init', {settings: here,...});
			return this.each(function() {
				// $($this) refers to the fg canvas, the bg is its previous sibling
				var $this = $(this);
				var localdata = {};
				var i;
				localdata.settings = {};
				
				localdata.settings.highlight_line_color = '#fff';
				localdata.settings.highlight_line_width = 3;
				localdata.settings.default_line_color = '#0f0';
				localdata.settings.default_line_width = 2;
				localdata.settings.default_square_dim = 6;
				localdata.settings.default_border_dim = 2;
				localdata.settings.default_border_color = 'blue';
				
				// store drawn objects here
				if (options !== undefined && options.objs !== undefined) {
					if (options.objs instanceof Array) {
						for (i in options.objs) {
							if (typeof options.objs[i].status === 'undefined') options.objs[i].status = 'active'; // set the initial status to 'active' for all elements
						}
						localdata.drawn_objs = options.objs;
					} else {
						if (typeof options.objs.status === 'undefined') options.objs.status = 'active';
						localdata.drawn_objs = [options.objs]; // set the initial status to active
					}
					delete options.objs;
				}
				else {
					localdata.drawn_objs = [];
				}
				localdata.selected_obj = undefined;
				
				if (typeof options === 'object') $.extend(localdata.settings, options);
				
				// store the mere DOM elements (unwrap jQuery elements)
				localdata.bg_canvas = $this.prev().get(0);
				localdata.fg_canvas = $this.get(0);
				
				// get the contexts
				if (localdata.bg_canvas.getContext && localdata.fg_canvas.getContext) {
					localdata.bg_ctx = localdata.bg_canvas.getContext('2d');
					localdata.fg_ctx = localdata.fg_canvas.getContext('2d');
				} else {
					alert('ERROR');
					throw 'Unable to get contexts of canvas elements';
				}
			
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				redrawObjects(localdata);
			});
		},
		/**
		 * Reinitializes the plugin with the given objects
		 * @params {object} objs The objects to be drawn
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		reinit: function(objs) {
			return this.each(function() {var $this = $(this);
				var localdata = $this.data('localdata');
				var i;

				// remove all exisiting handlers and events
				remove_handlers_and_events(localdata);

				if (objs === undefined) {
					localdata.drawn_objs = [];
				}
				else if (objs instanceof Array) {
					for (i in objs) {
						if (typeof objs[i].status === 'undefined') objs[i].status = 'active'; // set the initial status to 'active' for all elements
					}
					localdata.drawn_objs = objs;
				} else {
					if (typeof objs.status === 'undefined') objs.status = 'active';
					localdata.drawn_objs = [objs];
				}
				redrawObjects(localdata);
			});
		},
		/**
		 * Add given objects
		 * {object} objs The objects to be added and drawn
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		drawObject: function(objs) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				var i;
				
				// check for array of objs or single obj
				if (objs instanceof Array) {
					for (i in objs) {
						if (typeof objs[i].status === 'undefined') objs[i].status = 'active'; // set the initial status to 'active' for all elements
					}
					localdata.drawn_objs = localdata.drawn_objs.concat(objs);
				} else {
					if (typeof objs.status === 'undefined') objs.status = 'active';
					localdata.drawn_objs.push(objs);
				}
				redrawObjects(localdata);
			});
		},
		/**
		 * Redraws the existing objects
		 * @param {boolean} keep_handlers If set event handlers will not be unbound
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		redrawObjects: function(keep_handlers) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (keep_handlers == undefined) {
					$(localdata.fg_canvas).unbind('.regions_detect').unbind('.regions_draw_init').unbind('.regions_draw_active');
				}
				$(localdata.fg_canvas).parent().find('.handler').remove();
				$(localdata.fg_canvas).parent().find('.mover').remove();
				clearCanvas(localdata.fg_ctx, localdata.fg_canvas);

				redrawObjects(localdata);
			});
		},
		/**
		 * Returns the drawn objects
		 * @params {int} index If index is given only the corresponding object will be returned
		 * @returns The drawn objects or the specified one (index) 
		 */
		returnObjects: function(index) { // if index === undefined, all objects are returned
			var $this = $(this);
			var localdata = $this.data('localdata');
			var tmpobjs = [];
			var filter;
			var i;
			if (isNaN(parseInt(index))) {
				filter = index;
				index = undefined;
			}
			else {
				filter = undefined;
			}
			if (index === undefined) {
				for (i in localdata.drawn_objs) {
					if (typeof filter === 'string') {
						if (localdata.drawn_objs[i].status == filter) tmpobjs.push(localdata.drawn_objs[i]);
					}
					else {
						tmpobjs.push(localdata.drawn_objs[i]);
					}
				}
				return tmpobjs;
			}
			else {
				return localdata.drawn_objs[index];
			}	 
		},
		/**
		 * Searches an object and returns it
		 * @params {string} attrname Name of the attribute to search for
		 * @params {string} value Value of the given attribute to match
		 * @returns The matched object(s) or void (undefined) if no match occurs
		 */
		searchObject: function(attrname, value) {
			var $this = $(this);
			var localdata = $this.data('localdata');
			for (var i in localdata.drawn_objs) {
				if ((typeof localdata.drawn_objs[i][attrname] !== 'undefined') && (localdata.drawn_objs[i][attrname] == value)) {
					return {index: i, obj: localdata.drawn_objs[i]};
				}
			}
			return undefined;
		},
		/**
		 * Sets the status of all or a specified object
		 * @params {string} status The status to be set: active || hidden || inactive
		 * @params {int} index If set only the status of the corresponding object will be changed
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		setObjectStatus: function(status, index) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				var i;
				if (index === undefined) {
					for (i in localdata.drawn_objs) {
						localdata.drawn_objs[i].status = status;
					}
				}
				else {
					localdata.drawn_objs[index].status = status;
				}
				redrawObjects(localdata);
			});
		},
		/**
		 * Sets the given attribute-value pair for one or several objects
		 * @params {string} attrname The attribute to be set
		 * @params {string} value The value to be assigned to the given attributename
		 * @params {string} p1 The index (in this case p2 is not needed) or attribute to search for (selects objects to be processed by this function)
		 * @params {string} p2 The attribute value to search for (selects objects to be processed by this function)
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		setObjectAttribute: function(attrname, value, p1, p2) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				var index, i;
				if (typeof p1 === 'undefined') { // apply to all objects
					for (i in localdata.drawn_objs) {
						localdata.drawn_objs[i][attrname] = value;
					}
				}
				else if (typeof p2 === 'undefined') { // apply to index (p1 is the index in this case)
					index = p1;
					localdata.drawn_objs[index][attrname] = value;
				}
				else { // apply for all obejcts with attr p1 and attr val p2
					index = [];
					for (i in localdata.drawn_objs) {
						if ((typeof localdata.drawn_objs[i][p1] !== 'undefined') && (localdata.drawn_objs[i][p1] == p2)) {
							index.push(i);
						}
					}
					for (i in index) {
					localdata.drawn_objs[index[i]][attrname] = value;
					}
				}
			});
		},
		/**
		 * Deletes one or several objects
		 * @params {string} p1 Either an index (in this case p2 is not needed) or the name of an attribute 
		 * @params {string} p2 The value of the given attribute p1
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		deleteObject: function(p1, p2) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				var index, i;
				if (typeof p2 === 'undefined') {
					// p1 is an index
					index = p1;
					// delete the corresponding object
					localdata.drawn_objs.splice(index, 1);
				}
				else {
					index = [];
					// search for all objects with attribute p1 with value p2
					for (i in localdata.drawn_objs) {
						if ((typeof localdata.drawn_objs[i][p1] !== 'undefined') && (localdata.drawn_objs[i][p1] == p2)) {
							index.push(i);
						}
					}
					// delete the corresponding objects one by one 
					for (i in index) {
						localdata.drawn_objs.splice(index[i], 1);
					}
				}
				redrawObjects(localdata);
			});
		},
		/**
		 * Sets the default line color for objects to be drawn
		 * @params {string} color The color to be set
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		setDefaultLineColor: function(color) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.default_line_color = color;
			});
		},
		/**
		 * Sets the default line width for objects to be drawn
		 * @params {int} wifth The width to be set
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		setDefaultLineWidth: function(width) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				localdata.settings.default_line_width = width;
			});
		},
		/**
		 * Clears the background canvas (all object are being effaced)
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		clearCanvas: function() {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				clearCanvas(localdata.bg_ctx, localdata.bg_canvas);
			});
		},
		/**
		 * Highlights the specified object
		 * @params {int} index The index of the object to be highlighted
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		highlightObject: function(index) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				highlightObject(localdata, index);
			});
		},
		/**
		 * Unhighlights the specified object
		 * @params {int} index The index of the object to be unhighlighted
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		*/
		unhighlightObjects: function() {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				clearCanvas(localdata.fg_ctx, localdata.fg_canvas);
			});
		},
		/**
		 * Returns the dran figures in a geometrical order
		 * @returns An array of figures with the additional property 'original_index' refering to the internal array localdata.drawn_objs 	
		 */
		returnGeometricalOrder: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');
				
			if (localdata.drawn_objs.length == 0) return false;
				
			create_geometrical_order(localdata);
			var ordered_figures = localdata.drawn_objs_ordered.slice(0);
			localdata.drawn_objs_ordered = undefined;
			
			return ordered_figures;
		},
		/**
		 * Remove all handlers and events
		 * @params {object} localdata
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		remove_handlers_and_events: function() {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				remove_handlers_and_events(localdata);
			});
		},
	    getEditIndex: function() {
		var localdata = this.data('localdata');

		if (localdata.edit !== undefined) {
		    return localdata.edit; // return index of the region being edited
		} else {
		    return false;
		}
	    },
		/**
		 * Changes the mode of the plugin
		 * @params {string} mode The mode to be set
		 * @params {object} The options for the desired mode 
		 * @returns The DOM element(s) associated with the plugin (for method chaining)
		 */
		setMode: function(mode, options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				// unbind and remove everything
				remove_handlers_and_events(localdata);
				
				/*localdata.selected_objs = [];
				delete localdata.tmp_obj;
				delete localdata.edit;

				$(localdata.fg_canvas).unbind('.regions_detect').unbind('.regions_draw_init').unbind('.regions_draw_active').unbind('.regions_edit');
				$(localdata.fg_canvas).parent().find('.handler').remove();
				$(localdata.fg_canvas).parent().find('.mover').remove();
				clearCanvas(localdata.fg_ctx, localdata.fg_canvas);*/
				
				if (mode === undefined) return;
					
				switch (mode) {
					case 'detect':
						if (localdata.drawn_objs.length == 0) break;
						if ((options !== undefined) && (typeof options.clicked_cb === 'function')) {
							localdata.clicked_cb = options.clicked_cb;
							delete options.clicked_cb;
						} else {
							// if no callback is given: create and empty function to prevent a reference error
							localdata.clicked_cb = function() {};
						}
						if ((options !== undefined) && (typeof options.hover_cb === 'function')) {
							localdata.hover_cb = options.hover_cb;
							delete options.hover_cb;
						} else {
							// if no callback is given: create and empty function to prevent a reference error
							localdata.hover_cb = function() {};
						}
						if ((options !== undefined) && (typeof options.hover_lost_cb === 'function')) {
							localdata.hover_lost_cb = options.hover_lost_cb;
							delete options.hover_lost_cb;
						} else {
							// if no callback is given: create and empty function to prevent a reference error
							localdata.hover_lost_cb = function() {};
						}
						
						start_detection_mode(localdata);
						break;
					case 'draw':
						// options is expected to have the following properties: type, lineWidth, lineColor
						if (options === undefined) break;
						if (typeof options.draw_cb === 'function') {
							localdata.draw_cb = options.draw_cb;
							delete options.draw_cb;
						} else {
							// if no callback is given: create and empty function to prevent a reference error
							localdata.draw_cb = function() {};
						}
						if (typeof options.lineColor === 'undefined') options.lineColor = localdata.settings.default_line_color;
						if (typeof options.lineWidth === 'undefined') options.lineWidth = localdata.settings.default_line_width;
						// localdata.tmp_obj contains the type, lineWidth and lineColor of the object to be drawn
						localdata.tmp_obj = options;
						$(localdata.fg_canvas).bind('mousedown.regions_draw_init', startDrawing);
						break;
					case 'edit':
						// prevent a reference error
						if ((options === undefined || options.index) > (localdata.drawn_objs.length - 1)) break;
						localdata.edit = options.index;
						if (typeof options.edit_cb === 'function') {
							localdata.edit_cb = options.edit_cb;
							delete options.edit_cb;
						} else {
							// if no callback is given: create and empty function to prevent a reference error
							localdata.edit_cb = function() {};
						}
						editObject(localdata);
						break;
					default:
						break;
				}
				return;
			});
		},
	};

	$.fn.regions = function(method) {
		// Method calling logic
		if ( methods[method] ) {
			return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
		} else if ( typeof method === 'object' || ! method ) {
			return methods.init.apply( this, arguments );
		} else {
			throw 'Method ' +  method + ' does not exist on jQuery.tooltip';
		}		
	};

})(jQuery);