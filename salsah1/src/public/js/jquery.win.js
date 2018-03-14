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

(function( $ ){
	
	var win_id = 0;
	var win_w, win_h;
	var dock, fader, next_window;
    var lock_focus = false; // global boolean that controls whether the focus of a win can be changed or not (due to problems with insertAfter in setFocus)
	var lock_focus_cnt = 0; // no lock, no window holding lock
	//
	// 
	//
	var setFocus = function(localdata) {
		var win = localdata.elements.win_ele;
		var class_focus = localdata.settings.class_focus;
/*
		$('.' + class_focus).removeClass(class_focus);
		win.addClass(class_focus);
		
		var win_array = $('.win').get();
		var last_index = win_array.length - 1;
		
		// only move element if its position is not already the last in the DOM
		if (win_array[last_index] !== win[0]) win.insertAfter(win_array[last_index]);
*/
        if (lock_focus === true) return; // if focus is locked, leave this method here

		win.parent().find('.' + class_focus).removeClass(class_focus);
		win.addClass(class_focus);
		
		var win_array = win.parent().find('.win').get();
		var last_index = win_array.length - 1;
	
		// only move element if its position is not already the last in the DOM
		if (win_array[last_index] !== win[0]) win.insertAfter(win_array[last_index]);
		
	};

	var setPosition = function(localdata, check) {
		
		if (check === true) {
			var total_border_w = localdata.elements.win_ele.outerWidth() - localdata.elements.win_ele.width();
			var total_border_h = localdata.elements.win_ele.outerHeight() - localdata.elements.win_ele.height();

			getWindowDimensions();
			if (localdata.settings.pos_x < 0) {
				localdata.settings.pos_x = 0;
			} else if (localdata.settings.pos_x + localdata.settings.dim_x + total_border_w > win_w) {
				localdata.settings.pos_x = win_w - localdata.settings.dim_x - total_border_w;
			} 
		
			if (localdata.settings.pos_y < 0) {
				localdata.settings.pos_y = 0;
			} else if (localdata.settings.pos_y + localdata.settings.dim_y + total_border_h > win_h) {
				localdata.settings.pos_y = win_h - localdata.settings.dim_y - total_border_h;
			}
		}
		
		localdata.elements.win_ele.css({
			left: localdata.settings.pos_x + 'px',
			top: localdata.settings.pos_y + 'px'
		});
	};
	
	var setDimensions = function(localdata, check) {
		
		if (check === true) {
			
			var total_border_w = localdata.elements.win_ele.outerWidth() - localdata.elements.win_ele.width();
			var total_border_h = localdata.elements.win_ele.outerHeight() - localdata.elements.win_ele.height();
			
			getWindowDimensions();
			
			if (localdata.settings.pos_x + localdata.settings.dim_x + total_border_w > win_w) {
				localdata.settings.dim_x = win_w - localdata.settings.pos_x - total_border_w;
			}
			
			if (localdata.settings.pos_y + localdata.settings.dim_y + total_border_h > win_h) {
				localdata.settings.dim_y = win_h - localdata.settings.pos_y - total_border_h;
			}
		}
		
		if (localdata.settings.dim_x < localdata.settings.min_dim_x) localdata.settings.min_dim_x = localdata.settings.dim_x;
		if (localdata.settings.dim_y < localdata.settings.min_dim_y) localdata.settings.min_dim_y = localdata.settings.dim_y;
		
		localdata.elements.win_ele.css({
			width: localdata.settings.dim_x + 'px',
			height: localdata.settings.dim_y + 'px'
		});
		
		localdata.settings.outer_dim_x = localdata.elements.win_ele.outerWidth();
		localdata.settings.outer_dim_y = localdata.elements.win_ele.outerHeight();
		
	};
	
	var adjustPosition = function(localdata, diff_x, diff_y) {
		
		// store left and top positions
		var left = localdata.settings.pos_x;
		var top = localdata.settings.pos_y;
		
		var pos_x = left + diff_x;
		var pos_y = top + diff_y;
		
		// prevent moving outside of document.window dimensions but allow moving back (in case document.window has been reduced)
		if (pos_x > 0 && (pos_x + localdata.settings.outer_dim_x <= win_w) || (pos_x - diff_x + localdata.settings.outer_dim_x > win_w) && diff_x < 1) localdata.settings.pos_x = pos_x;
		if (pos_y >= 0 && (pos_y + localdata.settings.outer_dim_y <= win_h) || (pos_y - diff_y + localdata.settings.outer_dim_y > win_h) && diff_y < 1) localdata.settings.pos_y = pos_y;
		
		setPosition(localdata);
	};
	
	var adjustDimensions = function(localdata, diff_x, diff_y) {
		
		var dim_x = localdata.settings.dim_x + diff_x;
		var dim_y = localdata.settings.dim_y + diff_y;
		var new_diff = {};
		
		if (dim_x > localdata.settings.min_dim_x && localdata.settings.pos_x + dim_x <= win_w) {
			localdata.settings.dim_x = dim_x;
			new_diff.x = diff_x;
		}
		else {
			new_diff.x = 0;
		}
		if (dim_y > localdata.settings.min_dim_y && localdata.settings.pos_y + dim_y <= win_h) {
			localdata.settings.dim_y = dim_y;
			new_diff.y = diff_y;
		}
		else {
			new_diff.y = 0;
		}
		
		setDimensions(localdata);
		
		return new_diff;
	};
	
	var setVisibility = function(localdata) {
		if (localdata.settings.visible) {
			localdata.elements.win_ele.css({display: 'block'});
		} else {
			localdata.elements.win_ele.css({display: 'none'});
		}
	};
	
	var getWindowDimensions = function() {
		var win_dims = innerdim();
		
//		win_w = win_dims.width;
//		win_h = win_dims.height;
		win_w = $('.workwin_content').width();
		win_h = $('.workwin_content').height();
	};
	
	var methods = {
		
		init: function(options) { 
			return this.each(function(){
				// $this points to the main div of the win
				var $this = $(this);
				var localdata = {};
				localdata.elements = {}; // an object which will contain the window elements
				localdata.focus_locks = 0;
				localdata.elements.win_ele = $this;
				var css;
				var content_classes = ['content']; // basic class for content div
				
				localdata.settings = {
					fullscreen: false,
					pos_x: 100,
					pos_y: 50,
					dim_x: 500,
					dim_y: 500,
					min_dim_x: 400,
					min_dim_y: 400,
					outer_dim_x: -1,
					outer_dim_y: -1,
					window_icon: SITE_URL + '/app/icons/16x16/window.png',
					title: 'qTITLEp',
					content: '',
					visible: true,
					movable: true,
					resizable: true,
					maximizable: true,
					minimizable: true,
					closable: true,
					taskbar: true,
					whenSizedCB: undefined,
					whenMovedCB: undefined,
					whenToggledCB: undefined,
					whenMaximized: undefined,
					overflow_y: 'visible',
					class_window: [],
					class_focus: 'focus',
					class_hidden: 'hidden',
					is_busy: false,
					data_hook: undefined
				};
				$.extend(localdata.settings, options);
				
				localdata.settings.win_id = win_id++; // each 'init' absolutely must get a new win_id !!!! This can not been overridden by the settings!

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				//
				// setting the class and css-property of the main-div of the window
				//
				if (localdata.settings.fullscreen) {
					localdata.settings.class_window.push('contentTaskbar');
				}
				localdata.settings.class_window.push('win'); // must have the win class!!!
				
				$this.addClass(localdata.settings.class_window.join(' ')).css({
					position: 'absolute'
//					overflow: 'hidden',  // SAFARI BUG
				}).attr('id', localdata.settings.win_id);
				
				if (localdata.settings.fullscreen) {
					//
					// this is only for fulscreen mode (maximized version of window!)
					//.css({position: 'absolute', left: 0, top: 0, right: 0, bottom: 0})
					css = {
						position: 'absolute',
						left: 0,
						top: 0,
						right: 0,
						bottom: 0
					};
					$this.css(css);
					css = {};
					if (localdata.settings.taskbar) {
						css.display = 'block';
						content_classes.push('contentWithTaskbar');
					}
					else {
						css.display = 'none';
					}
					localdata.elements.taskbar = $('<div>').addClass('taskbar').css({
						position: 'absolute',
						left: 0,
						right: 0,
						'-moz-user-select': 'none',
						'-khtml-user-select': 'none',
						'-webkit-user-select': 'none',
						'user-select': 'none',
						display: css.display
					}).appendTo($this);
					var cc;
					
					localdata.elements.content = $('<div>').addClass(content_classes.join(' ')).css({
						position: 'absolute',
						left: 0,
						right: 0,
						bottom: 0,
						backgroundColor: '#fff'
					}).appendTo($this);

					if (typeof localdata.settings.content === 'function') {
						localdata.settings.content(localdata.elements.content);
					}
					else if (typeof localdata.settings.content === 'string') {
						localdata.elements.content.append(localdata.settings.content.replace(/<WINID>/, localdata.settings.win_id));
					}
					else {
						localdata.elements.content.append(localdata.settings.content);
					}

					return true;
				}
				
				setPosition(localdata, true);
				setDimensions(localdata, true);
				
				setVisibility(localdata);
				
				//
				// Adding a titlebar which will contain the title and the controls to minimize, maximize and close the window
				//
				localdata.elements.titlebar = $('<div>').addClass('titlebar').css({
					position: 'absolute',
					left: 0,
					right: 0,
					top: 0
				}).appendTo($this);
				
				//
				// icon and title
				//
				localdata.elements.title = $('<div>').addClass('title').css({
					'float': 'left',
					'-moz-user-select': 'none',
					'-khtml-user-select': 'none',
					'-webkit-user-select': 'none',
					'user-select': 'none'
				})
				.append($('<img>', {src: localdata.settings.window_icon}).css({'vertical-align': 'middle'}))
				.append(localdata.settings.title)
				.appendTo(localdata.elements.titlebar);

				//
				// controls
				//
				localdata.elements.controls = $('<div>').addClass('controls').css({
					'float': 'right',
					top: 0,
					bottom: 0
				})
				.appendTo(localdata.elements.titlebar);
				
				// css props for control buttons
				css = {
					'float' : 'left',
					'cursor' : 'pointer'
				};
				
				//
				// maximize button
				//
				if (localdata.settings.maximizable) {
					css.display = 'block';
				}
				else {
					css.display = 'none';
				}
				localdata.elements.max = $('<div>').addClass('maximize').css(css).appendTo(localdata.elements.controls);

				//
				// minimize button
				//
				if (localdata.settings.minimizable) {
					css.display = 'block';
				}
				else {
					css.display = 'none';
				}
				localdata.elements.toggle = $('<div>').addClass('toggle').css(css).appendTo(localdata.elements.controls);

				//
				// close button
				//
				if (localdata.settings.closable) {
					css.display = 'block';
				}
				else {
					css.display = 'none';
				}
				localdata.elements.close = $('<div>').addClass('close').css(css).appendTo(localdata.elements.controls);

				//
				// taskbar
				//
				css = {};
				if (localdata.settings.taskbar) {
					css.display = 'block';
					content_classes.push('contentWithTaskbar');
				}
				else {
					css.display = 'none';
				}
				localdata.elements.taskbar = $('<div>').addClass('taskbar').css({
					position: 'absolute',
					left: 0,
					right: 0,
					'-moz-user-select': 'none',
					'-khtml-user-select': 'none',
					'-webkit-user-select': 'none',
					'user-select': 'none',
					display: css.display
				}).appendTo($this);
				
				//
				// content
				//
				localdata.elements.content = $('<div>').addClass(content_classes.join(' ')).css({
					position: 'absolute',
					left: 0,
					right: 0,
					bottom: 0,
					backgroundColor: '#fff',
					'overflow-y': localdata.settings.overflow_y
				}).appendTo($this);

				if (typeof localdata.settings.content === 'function') {
					localdata.settings.content(localdata.elements.content);
				}
				else if (typeof localdata.settings.content === 'string') {
					localdata.elements.content.append(localdata.settings.content.replace(/<WINID>/, localdata.settings.win_id));
				}
				else {
					localdata.elements.content.append(localdata.settings.content);
				}
				
				localdata.elements.resize = $('<div>').addClass('resize').css({
					position: 'absolute',
					right: 0,
					bottom: 0,
					cursor: 'nwse-resize'
				}).appendTo($this);
				
				// add and additonal class if win is not resizable
				if (!localdata.settings.resizable) localdata.elements.resize.addClass('inactive'); 
				
				//
				// overlay: displayed when loading by methods setBusy
				//
				localdata.elements.overlay = $('<div>').addClass('overlay').css({
					position: 'absolute',
					left: 0,
					bottom: 0,
					right: 0,
					backgroundColor: '#fff',
					display: 'none'
				}).appendTo($this);
				if (localdata.settings.taskbar) {
					localdata.elements.overlay.addClass('contentWithTaskbar');
				}
				
				
				//
				// create dock
				//
				if (dock === undefined) dock = $('<div>').addClass('dock');
				setFocus(localdata);
				// the dock is always at the last position in the DOM
				dock.insertAfter($this);
				
				// create a dock representation				
				localdata.elements.dock = $('<div>', {'data-dock_winid': localdata.settings.win_id}).addClass('dockElement')
					.append($('<img>', {src: localdata.settings.window_icon}).css({'vertical-align': 'middle'}))
					.append(localdata.settings.title).bind('click', function() {
					// this window has been selected: the focus remains to it (therefore unbind mouseout event)
					fader.remove();
					$(this).unbind('mouseout');
					
					localdata.settings.visible = true;
					setVisibility(localdata);
					
					dock.find('[data-dock_winid=' + localdata.settings.win_id + ']').removeClass('toggled');
					
				}).bind('mouseover', function() {
					var win_array = $('.win').get();
					var visible = localdata.settings.visible;
					
					if (!visible) {
						localdata.settings.visible = true;
						setVisibility(localdata);
					}
					
					if (win_array[win_array.length-1] == $this[0]) {
						// this window is already at the last position in the DOM
						next_window = false;
					} else {
						for (var i = 0; i < win_array.length - 1; i++) {
							// next_window points to the next sibling of this window's current position
							if (win_array[i] == $this[0]) next_window = win_array[i+1];
						}
					}
					
					// set the focus to this window
					setFocus(localdata);
					fader = $('<div>').addClass('fader').insertBefore($this);
					
					$(this).bind('mouseout', function() {
						if (!visible) {
							localdata.settings.visible = false;
							setVisibility(localdata);
						}
					
						fader.remove();
					
						if (!(next_window === undefined || next_window === false)) {
							// equal to: next_window !== undefined && next_window !== false
							// restore the original order in the DOM
							var focus_window = $this.prev();
							$this.insertBefore(next_window);
							next_window = undefined;
							setFocus(focus_window.data('localdata'));
						}
					});
				}).appendTo(dock);
				

				//
				// set the focus, if there is a mousedown event within the window
				// do *not* prevent the event bubbling! 
				//
				$this.bind('mousedown', function(event) {
					setFocus(localdata);
				});
				
				
				//
				// set procedures for event handling: move and resize win
				//
				
				localdata.elements.titlebar.bind('mousedown', function(event) {
					event.preventDefault();
					
					if (!localdata.settings.movable) return;
				
					getWindowDimensions();
					
					// store the position on click 
					var clicked_x = event.pageX;
					var clicked_y = event.pageY;
					
					$('#ecatcher').css({display: 'block', cursor: 'move'});
				
					$('#ecatcher').bind('mousemove.moving', function(event) {
						event.preventDefault();
						
						var diff_x = event.pageX - clicked_x;
						var diff_y = event.pageY - clicked_y;
						
						adjustPosition(localdata, diff_x, diff_y);
						
						clicked_x = event.pageX;
						clicked_y = event.pageY;
						
					});
					
					$('#ecatcher').bind('mouseup.moving mouseout.moving', function(event) {
						event.preventDefault();
						
						$('#ecatcher').unbind('.moving');
						$('#ecatcher').css({display: 'none', cursor: 'inherit'});

						if (localdata.settings.whenMovedCB instanceof Function) {
							localdata.settings.whenMovedCB();
						}
					});
				});
				
				localdata.elements.resize.bind('mousedown', function(event) {
					event.preventDefault();
					if (!localdata.settings.resizable) return;
					
					getWindowDimensions();
					
					// store the position on click 
					var clicked_x = event.pageX;
					var clicked_y = event.pageY;
					
					$('#ecatcher').css({display: 'block'});
					$('#ecatcher').bind('mousemove.resizing', function(event) {
						
						// calculate the new width and height
						var diff_x = event.pageX - clicked_x;
						var diff_y = event.pageY - clicked_y;
						
						var new_diff = adjustDimensions(localdata, diff_x, diff_y);
						
						if (localdata.settings.whenSizedCB instanceof Function) {
							localdata.settings.whenSizedCB(new_diff.x, new_diff.y);
						}
						
						clicked_x = event.pageX;
						clicked_y = event.pageY;
								
					});
					
					$('#ecatcher').bind('mouseup.resizing mouseout.resizing', function(event) {

						$('#ecatcher').unbind('.resizing');
						$('#ecatcher').css('display', 'none');
						
						if (localdata.settings.whenSizedCB instanceof Function) {
							localdata.settings.whenSizedCB(0, 0);
						}

					});
					
				});
				
				//
				// controller events
				//
				localdata.elements.close.bind('mousedown', function(event) {
					// stop propagation of this event in order to allow click event
					event.stopPropagation();
				});
				
				//
				// close button pressed
				//
				localdata.elements.close.bind('click', function() {
					dock.find('[data-dock_winid=' + localdata.settings.win_id + ']').remove();

					lock_focus_cnt -= localdata.focus_locks;
					if (lock_focus_cnt < 0) lock_focus_cnt = 0; // just to be sure....
					localdata.focus_locks = 0; // not necessary, but to be clear...
					if (lock_focus_cnt == 0) lock_focus = false;

					$this.remove();
				});
				
				//
				// minimize button pressed
				//
				localdata.elements.toggle.bind('mousedown', function(event) {
					// stop propagation of this event in order to allow click event
					event.stopPropagation();
				});
				localdata.elements.toggle.bind('click', function() {
					dock.find('[data-dock_winid=' + localdata.settings.win_id + ']').addClass('toggled');
					localdata.settings.visible = false;
					setVisibility(localdata);
					if ($this.hasClass(localdata.settings.class_focus)) setFocus($this.prev().data('localdata'));
				});
				
				//
				// maximized button pressed
				//
				localdata.elements.max.bind('mousedown', function(event) {
					// stop propagation of this event in order to allow click event
					event.stopPropagation();
				});
				localdata.elements.max.bind('click', function() {
//					dock.find('[data-dock_winid=' + localdata.settings.win_id + ']').addClass('toggled');
//					localdata.settings.visible = false;
//					setVisibility(localdata);
//					if ($this.hasClass(localdata.settings.class_focus)) setFocus($this.prev().data('localdata'));
					if (localdata.settings.whenMaximizedCB instanceof Function) {
						localdata.settings.whenMaximizedCB($this);
					}
				});
				
			});
		},
		/*===========================================================================*/


		setFocus: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.fullscreen) return true;
				setFocus(localdata);
			});
		},
        
		setFocusLock: function(lock) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (Boolean(lock)) {
					lock_focus_cnt++;
					lock_focus = true;
					localdata.focus_locks++;
				}
				else {
					if (lock_focus_cnt > 0) lock_focus_cnt--;
					if (lock_focus_cnt == 0) lock_focus = false;
					if (localdata.focus_locks > 0) localdata.focus_locks--;
				}
			});
		},
        
		getId: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');
			
			return localdata.settings.win_id;
		},
		
		setBusy: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return true; // may be changed in the future
			
				localdata.settings.is_busy = true;
				localdata.elements.overlay.css('display', 'block');
			
				// img is 84px x 84px
				$('<img>', {src: SITE_URL + '/app/icons/busy.gif'}).css({position: 'absolute', left: (localdata.settings.dim_x - 84)/2 + 'px', top: (localdata.settings.dim_y - 84)/2 + 'px'}).appendTo(localdata.elements.overlay);
			});
		},
		
		unsetBusy: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return true; // may be changed in the future
			
				if (!localdata.settings.is_busy) return true;
			
				localdata.elements.overlay.css('display', 'none');
				localdata.elements.overlay.find('img').remove();
			
				localdata.settings.is_busy = false;
			});
		},
		
		position: function(pos) {
			if (pos === undefined) {
				if (localdata.settings.fullscreen) return {pos_x: 0, pos_y: 0};
				
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				return {
					pos_x: localdata.settings.pos_x,
					pos_y: localdata.settings.pos_y
				};
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				if (localdata.settings.fullscreen) return true;

				// break loop if argument is not given correctly
				if (pos.pos_x === undefined || pos.pos_y === undefined) return false;
				
				localdata.settings.pos_x = pos.pos_x;
				localdata.settings.pos_y = pos.pos_y;

				setPosition(localdata, true);
			});
		},
		
		dimensions: function(dim) {
			if (dim === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.fullscreen) return {dim_x: 0, dim_y: 0};
				
				return {
					dim_x: localdata.settings.dim_x,
					pos_y: localdata.settings.dim_y
				};
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				if (localdata.settings.fullscreen) return true;

				if (dim.dim_x === undefined || dim.dim_y === undefined) return false;
				
				localdata.settings.dim_x = dim.dim_x;
				localdata.settings.dim_y = dim.dim_y;

				setDimensions(localdata, true);
			});
		},
		
		visibility: function(visible) {
			if (visible === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return true;
				
				return localdata.settings.visible;
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				if (localdata.settings.fullscreen) return true;

				localdata.settings.visible = Boolean(visible);
				setVisibility(localdata);
			});
		},
		
		movability: function(movable) {
			if (movable === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return false;
				
				return localdata.settings.movable;
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return true;
				
				localdata.settings.movable = Boolean(movable);
			});
		},
		
		resizability: function(resizable) {
			if (resizable === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return false;
				
				return localdata.settings.resizable;
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				if (localdata.settings.fullscreen) return true;

				localdata.settings.resizable = Boolean(resizable);
				
				if (!localdata.settings.resizable) {
					localdata.elements.resize.addClass('inactive');
				} else {
					localdata.elements.resize.removeClass('inactive');
				} 
			});
		},
		
		content: function(content) {
			if (content === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				return localdata.elements.content;
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			
				localdata.elements.content.empty();
				if (typeof content === 'function') {
					content(localdata.elements.content);
				}
				else if (typeof localdata.settings.content === 'string') {
					localdata.elements.content.append(content.replace(/<WINID>/, localdata.settings.win_id));
				}
				else {
					localdata.elements.content.append(content);
				}
			});			
		},
		
		contentElement: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');
			
			return localdata.elements.content;
		},
		
		title: function(title, iconsrc) {
			if (title === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				
				return localdata.settings.title;
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return true;

				localdata.settings.title = title;
				if (iconsrc !== undefined) {
					localdata.settings.window_icon = iconsrc;
				}
				
				localdata.elements.title.empty().append($('<img>', {src: localdata.settings.window_icon}).css({'vertical-align': 'middle'})).append(localdata.settings.title);
				localdata.elements.dock.empty().append($('<img>', {src: localdata.settings.window_icon}).css({'vertical-align': 'middle'})).append(localdata.settings.title);
			});			
		},
		
		taskbar: function(taskbar) {
			if (taskbar === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (!localdata.settings.taskbar) return false;
				
				return localdata.elements.taskbar;
			}
			
			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			
				if (!localdata.settings.taskbar) return false;
			
				localdata.elements.taskbar.append(taskbar);
			});
		},
		
		dataHook: function(data) {
			if (data === undefined) {
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (localdata.settings.data_hook === undefined) return false;

				return localdata.settings.data_hook;
			}

			// argument is given
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			
				if (localdata.settings.data_hook === undefined) return false;
			
				localdata.settings.data_hook = data;
			});
		},
		
		currentWindowSettings: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');

			return localdata.settings;
		},
		
		deleteWindow: function() {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');

				if (localdata.settings.fullscreen) return true;

				lock_focus_cnt -= localdata.focus_locks;
				if (lock_focus_cnt < 0) lock_focus_cnt = 0; // just to be sure....
				localdata.focus_locks = 0; // not necessary, but to be clear...
				if (lock_focus_cnt == 0) lock_focus = false;
				
				dock.find('[data-dock_winid=' + localdata.settings.win_id + ']').remove();
				$this.remove();
			});
		}
		
		/*===========================================================================*/

	};
	
	$.fn.win = function(method) {
		// Method calling logic
		if ( methods[method] ) {
			return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
		} else if ( typeof method === 'object' || ! method ) {
			return methods.init.apply( this, arguments );
		} else {
			throw 'Method ' +  method + ' does not exist on jQuery.tabs';
		}		
	};
	/*===========================================================================*/
	
})( jQuery );