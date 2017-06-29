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
 * jQuery plugin searchbox
 *
 * This plugin allows to enter a resource pointer (instead of using drag'n'drop).
 * It opens a text field where the user is able to type. For each character, the
 * system performs a fulltext search and shows the gits in a selectionn box.
 *
 * Parameters:
 *
 *              restype_id: Restrict the selection to the given resource type. In order to seach all resource types use "-1" [Default: -1]
 *              nshow: Maxmium number of selections shown [Default: 10]
 *              numprops: Number of properties shown in selections [Default: 1],
 *              clickCB: Callback when a selection has been clicked [Default: undefined],
 *              newEntryAllowed: Include the "new entry" selection [Default: true],
 *              newEntrySelectedCB: Callback called if "new entry" is being clicked [Default: undefined],
 *
 *=============================================================================
 */
(function( $ ){

		var new_entry_item = function(localdata, $this, new_dialog_title) {
			localdata.ele.sel.append($('<div>').addClass('searchboxItem').css({width: '100%'}).text(new_dialog_title).on('click', function(event) {
				localdata.ele.sel.dialog({
					title: new_dialog_title,
					removeCB: function(reason) {
						if ((reason !== undefined) && (reason == 'submit')) {
							localdata.ele.sel.hide();
						}
						else {
							$this.focus();
						}
					}
				});
				var content = localdata.ele.sel.dialog('content');
				content.addClass('propedit_frame');
				if (localdata.settings.restype_id == -1) {
					content.resadd({
						on_submit_cb: function(data) {
							var new_resid = data.res_id;
							localdata.settings.clickCB(new_resid);
							localdata.ele.sel.dialog('remove', 'submit');
						},
						on_cancel_cb: function() {
							localdata.ele.sel.dialog('remove', 'cancel');
							$this.focus();
						},
						on_error_cb: function(data) {
							alert(data.errormsg);
							localdata.ele.sel.dialog('remove', 'error');
						}
					});
				}
				else {
					SALSAH.ApiGet('resourcetypes', localdata.settings.restype_id, function(data) {
						if (data.status == ApiErrors.OK) {
							content.resadd({
								rtinfo: data.restype_info,
								on_submit_cb: function(data) {
									var new_resid = data.res_id;
									localdata.settings.clickCB(new_resid);
									localdata.ele.sel.dialog('remove', 'submit');
								},
								on_cancel_cb: function() {
									localdata.ele.sel.dialog('remove', 'cancel');
									$this.focus();
								},
								on_error_cb: function(data) {
									alert(data.errormsg);
									localdata.ele.sel.dialog('remove', 'error');
								}
							});
						}
						else {
							alert(data.errormsg);
						}
					});
					/* NOT YET TESTED
					$.__post(SITE_URL + '/helper/restypeinfo.php', {
						restype: localdata.settings.restype_id
					}, function(data) {
						content.resadd({
							rtinfo: data,
							on_submit_cb: function(data) {
								var new_resid = data.res_id;
								localdata.settings.clickCB(new_resid);
								localdata.ele.sel.dialog('remove', 'submit');
							},
							on_cancel_cb: function() {
								localdata.ele.sel.dialog('remove', 'cancel');
								$this.focus();
							},
							on_error_cb: function(data) {
								alert(data.errormsg);
								localdata.ele.sel.dialog('remove', 'error');
							},
						});
					}, 'json');
					*/
				}
			}));
		};

		var initialize_searchboxlist = function($this, new_dialog_title, restype) {
			localdata = $this.data('localdata');
			localdata.ele.sel.empty();
			if (localdata.settings.newEntryAllowed) {
				new_entry_item(localdata, $this, new_dialog_title);
			}
		};

		var methods = {
			/**
			* $(element).selradio({'value': 12});
			*
			* Parameter: {
			*   value:     integer value [default: 1]
			* }
			*/
			init: function(options) {
				return this.each(function() {
					var $this = $(this);
					var localdata = {};
					localdata.settings = {
						restype_id: -1, // negative means no restype_id known
						nshow: 10,
						numprops: 1,
						clickCB: undefined,
						newEntryAllowed: true,
						newEntrySelectedCB: undefined,
						minimumChars: 3 // minimum length of search string
					};
					localdata.ele = {};

					var pos = $this.position();
					if (!$this.hasClass('propedit_frame')) {
						pos.top += $this.closest('.propedit_frame').scrollTop() + $this.outerHeight(); // was closest('tabContent')
					}
					var keyup_xhr = undefined;
					$.extend(localdata.settings, options);

					$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

					var new_dialog_title = strings._new_entry;

					//
					// Get here the restype info
					//
					if (localdata.settings.restype_id > 0) {
						SALSAH.ApiGet('resourcetypes', localdata.settings.restype_id, function(data){
							if (data.status == ApiErrors.OK) {
								new_dialog_title += ' [' + data.restype_info.label + ']';
							}
							else {
								alert(data.errormsg);
							}
						});
						/*
						$.__ajax({
							url: SITE_URL + '/helper/restypeinfo.php',
							data: {restype: localdata.settings.restype_id},
							dataType: 'json'
						}).success(function(data) {
							new_dialog_title += ' [' + data.label + ']';
						});
						*/
					}
					var ignore_blur; // this variable is used to prevent the searchbox to be removed by the blur event, if an item in the searchbox is clicked
					localdata.ele.sel = $('<div>').addClass('searchbox').attr({size: localdata.settings.nshow}).css({
//						position: 'absolute',
						left: pos.left,
						top: pos.top +$this.outerHeight(),
						background: 'white',
						'z-index': 2, /* :( i don't like black magic numbers or const */
						display: 'none'
					}).hover(
						function(event) {
							ignore_blur = true; // The mouse is in the search box – ignore the blur event
						},
						function(event) {
							ignore_blur = false; // the mouse is outside again – remove the searchbox on a blur event

						}
					).appendTo($this.parent());

					$this.on('focusin.searchbox', function() {
						initialize_searchboxlist($this, new_dialog_title);
						pos = $this.position();
						if (!$this.hasClass('propedit_frame')) {
							pos.top +=  $this.closest('.propedit_frame').scrollTop() + $this.outerHeight();
						}
						localdata.ele.sel.css({
							left: pos.left,
							top: pos.top
						}).show();
						// if the input already has a value and it is valid, back it up
						if ($this.attr("isValid") === "true") {
							$this.attr("prevVal", $this.val());
						}
						$this.val("");
						$this.attr("isValid", "false");
					});


					$this.on('blur', function(event) {pos = $this.position();
						if (!ignore_blur) {
							if (keyup_xhr !== undefined) {
								keyup_xhr.abort();
								$this.css({cursor: 'text'});
							}
							localdata.ele.sel.hide();
							// when leaving this input, if the value is empty, but we had a previous valid input
							// set back the previous input
							if ($this.attr("isValid") === "false") {
								if ($this.attr("prevVal")) {
									$this.val($this.attr("prevVal"));
									$this.attr("prevVal", "");
									$this.attr("isValid", "true");
								}
							}
						}
					});

					$this.on('keyup', function(event) {
						if (keyup_xhr !== undefined) {
							keyup_xhr.abort();
							$this.css({cursor: 'text'});
						}
						var searchstr = $(this).val();

						if (searchstr.length < localdata.settings.minimumChars) {
							// search string is not long enough
							localdata.ele.sel.empty();
							return;
						}

						$this.css({cursor: 'wait'});

						window.status =  localdata.settings.restype_id + ' ' + searchstr;

						keyup_xhr = SALSAH.ApiGet(
							'resources',
							{
								restype_id: localdata.settings.restype_id,
								searchstr: searchstr,
								numprops: localdata.settings.numprops,
								limit: localdata.settings.nshow + 1
							},
							function(data) {
								keyup_xhr = undefined;
								$this.css({cursor: 'text'});
								if (data.status == ApiErrors.OK) {
									localdata.ele.sel.empty();
									if (localdata.settings.newEntryAllowed) {
										new_entry_item(localdata, $this, new_dialog_title);
									}
									for (var j in data.resources) {
										if (j > localdata.settings.nshow) {
											localdata.ele.sel.append($('<div>').addClass('searchboxItem').css({width: '100%'}).text('…'));
											break;
										}
										var itemstr = '';
										for (var jj = 0; jj < localdata.settings.numprops; jj++) {
											if (jj > 0) itemstr += ', ';
											itemstr += data.resources[j].value[jj];
										}
										localdata.ele.sel.append($('<div>').addClass('searchboxItem').css({width: '100%'}).text(itemstr).data('id', data.resources[j].id).on('click', function(event) {
											if (typeof localdata.settings.clickCB === 'function') {
												localdata.settings.clickCB($(this).data('id'), event.target);
												$this.data('value', $(this).data('id'));
											}
											localdata.ele.sel.hide();
										}));
									}
								}
								else {
									alert(data.errormsg);
								}
							},
							function(xhr, status) {
								// window.status = status;
								keyup_xhr = undefined;
								$this.css({cursor: 'text'});
								return true;
							}
						);

						/*
						keyup_xhr = $.__ajax({
							type: 'POST',
							url: SITE_URL + '/ajax/search_resource_ajax.php',
							timeout: 1000,
							data: {
								restype_id: localdata.settings.restype_id,
								searchstr: searchstr,
								numprops: localdata.settings.numprops,
								limit: localdata.settings.nshow + 1
							},
							dataType: 'json',
							success: function(data) {
								keyup_xhr = undefined;
								$this.css({cursor: 'text'});
								if (data.CODE == 'OK') {
									localdata.ele.sel.empty();
									if (localdata.settings.newEntryAllowed) {
										new_entry_item(localdata, $this, new_dialog_title);
									}
									for (var j in data.DATA) {
										if (j > localdata.settings.nshow) {
											localdata.ele.sel.append($('<div>').addClass('searchboxItem').css({width: '100%'}).text('…'));
											break;
										}
										var itemstr = '';
										for (var jj = 0; jj < localdata.settings.numprops; jj++) {
											if (jj > 0) itemstr += ', ';
											itemstr += data.DATA[j].value[jj];
										}
										localdata.ele.sel.append($('<div>').addClass('searchboxItem').css({width: '100%'}).text(itemstr).data('id', data.DATA[j].id).on('click', function(event) {
											if (typeof localdata.settings.clickCB === 'function') {
												localdata.settings.clickCB($(this).data('id'), event.target);
												$this.data('value', $(this).data('id'));
											}
											localdata.ele.sel.hide();
										}));
									}
								}
								else {
									alert(data.MSG);
								}
							},
							error: function(xhr, status) {
								//                                                  window.status = status;
								keyup_xhr = undefined;
								$this.css({cursor: 'text'});
								return true;
							}
						});
						*/
					});
				});
			},

			setFocus: function () {
				return this.each(function(){
					var $this = $(this);
					var localdata = $this.data('localdata');
					$this.focus();
				});
			},

			hide: function() {
				return this.each(function(){
					var $this = $(this);
					var localdata = $this.data('localdata');
					localdata.ele.sel.hide();
				});
			},

			remove: function() {
				return this.each(function(){
					var $this = $(this);
					var localdata = $this.data('localdata');
					localdata.ele.sel.remove();
				});
			},

			value: function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				return $this.data('value');
			},

			anotherMethod: function(options) {
				return this.each(function(){
					var $this = $(this);
					var localdata = $this.data('localdata');
				});
			}
			/*===========================================================================*/
		};


		$.fn.searchbox = function(method) {
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
