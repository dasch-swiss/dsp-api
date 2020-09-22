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

/**
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @author Tobias Schwizer <t.schweizer@unibas.ch>
 * @package jqplugins
 *
 */
(function( $ ){

    var node_path = function(hlist, hlist_node_id) {
        var node_list = [];

        var find_node = function(hlist, hlist_node_id, level) {
            var i;
            for (i in hlist) {
                if (hlist[i].id == hlist_node_id) {
                    node_list[level] = hlist[i].id;
                    return true;
                }
                if (hlist[i].children) {
                    if (find_node(hlist[i].children, hlist_node_id, level + 1)) {
                        node_list[level] = hlist[i].id;
                        return true;
                    }
                }
            }
            return false;
        };

        find_node(hlist, hlist_node_id, 0);

        return node_list;
    };

	var selection_changed = function(seldiv, hlist, ele, vsize) {
		var i;
		var level = parseInt(ele.data('level'));
		var node_id = ele.val();
		var node_list;

		var find_node = function(hlist, hlist_node_id, level) {
			var i;
			var nnode;
			for (i in hlist) {
				if (hlist[i].id == hlist_node_id) {
					return hlist[i];
				}
				if (hlist[i].children) {
					if ((node = find_node(hlist[i].children, hlist_node_id, level + 1)) !== undefined) {
						return node;
					}
				}
			}
			return undefined;
		};

		for (i = level + 1; i < seldiv.length; i++) {
			seldiv[i].remove();
		}
		seldiv.splice(level + 1);

		var selected = find_node(hlist, node_id, 0);


		var selele;
		if ((selected !== undefined) && selected.children) {
			level = level + 1;
			if ((vsize !== undefined) && (vsize > 1)) {
				seldiv[level] = $('<span>');
				seldiv[0].parent().append(seldiv[level]);
				selele = $('<select>').attr('size', vsize).addClass('form-control propedit').data('level', level);
			}
			else {
				seldiv[level] = $('<div>');
				seldiv[0].parent().append(seldiv[level]);
				selele = $('<select>').addClass('form-control propedit').data('level', level);
			}
			$('<option>', {value: 0, selected: 'selected'}).text(' ').appendTo(selele);
			for (i in selected.children) {
				$('<option>', {value: selected.children[i].id}).text(selected.children[i].label).appendTo(selele);
			}
			selele.on('change', function(event) {
				selection_changed(seldiv, hlist, $(this), vsize);
			});
			seldiv[level].append(selele);
		}

	};

    var methods = {
        /**
         * $(element).hlist({'value': 12});
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
					hlist_id: undefined, // e.g. webern:textcolor, or numeric selection_id
					value: undefined // inital value, if not overridden by options!
				};
				localdata.ele = {};
				$.extend(localdata.settings, options);

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				SALSAH.ApiGet('hlists', localdata.settings.value, {reqtype: 'node'}, function(data) {
					if (data.status == ApiErrors.OK) {
						for (var i in data.nodelist) {
							$this.append($('<div>').text('->' + String(data.nodelist[i].label)));
						}
					}
					else {
						alert(data.errormsg);
					}
				});
			});
        },

        edit: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata;
				if ((options !== undefined) && (options instanceof Object)) {
					localdata = {};
					localdata.settings = {
						hlist_id: undefined, // e.g. webern:textcolor, or numeric selection_id
						value: undefined, // inital value, if not overridden by options!
						vsize: 1
					};
					$.extend(localdata.settings, options); // initialize a local data object which is attached to the DOM object
					localdata.ele = {};
					$this.data('localdata', localdata);
				}
				else {
					localdata = $this.data('localdata');
					if (options !== undefined) {
						localdata.settings.value = options; // options is the actual value!
					}
				}

				SALSAH.ApiGet('hlists', localdata.settings.hlist_id, {}, function(data) {
					if (data.status == ApiErrors.OK) {
						var hlist = data.hlist;
						var tmp_hlist;
						var selected_node_ids = node_path(hlist, localdata.settings.value);
						var selele;

						if (selected_node_ids.length == 0) selected_node_ids[0] = hlist[0].id;
						localdata.ele.seldiv = [];
						for (var sid in selected_node_ids) {
							if (localdata.settings.vsize > 1) {
								$this.append(localdata.ele.seldiv[sid] = $('<span>'));
								localdata.ele.seldiv[sid].append(selele = $('<select>').attr('size', localdata.settings.vsize).addClass('form-control propedit').data('level', sid).on('change', function(event) {
									selection_changed(localdata.ele.seldiv, data.hlist, $(this), localdata.settings.vsize);
								}));
							}
							else {
								$this.append(localdata.ele.seldiv[sid] = $('<div>'));
								localdata.ele.seldiv[sid].append(selele = $('<select>').addClass('form-control propedit').data('level', sid).on('change', function(event) {
									selection_changed(localdata.ele.seldiv, data.hlist, $(this));
								}));
							}
							if (sid > 0) {
								$('<option>', {value: 0}).text(' ').appendTo(selele);
							}
							for (var i in hlist) {
								if (selected_node_ids[sid] == hlist[i].id) {
									$('<option>', {value: hlist[i].id, selected: 'selected'}).text(hlist[i].label).appendTo(selele);
									tmp_hlist = hlist[i].children;
								}
								else {
									$('<option>', {value: hlist[i].id}).text(hlist[i].label).appendTo(selele);
								}
							}
							hlist = tmp_hlist;
						}
						if (hlist) { //the last has children
							sid++;
							if (localdata.settings.vsize > 1) {
								$this.append(localdata.ele.seldiv[sid] = $('<span>'));
								localdata.ele.seldiv[sid].append(selele = $('<select>').attr('size', localdata.settings.vsize).addClass('form-control propedit').data('level', sid).on('change', function(event) {
									selection_changed(localdata.ele.seldiv, data.hlist, $(this), localdata.settings.vsize);
								}));
							}
							else {
								$this.append(localdata.ele.seldiv[sid] = $('<div>'));
								localdata.ele.seldiv[sid].append(selele = $('<select>').addClass('form-control propedit').data('level', sid).on('change', function(event) {
									selection_changed(localdata.ele.seldiv, data.hlist, $(this));
								}));
							}
							$('<option>', {value: 0, selected: 'selected'}).text(' ').appendTo(selele);
							for (i in hlist) {
								$('<option>', {value: hlist[i].id}).text(hlist[i].label).appendTo(selele);
							}
						}
					}
					else {
						alert(data.errormsg);
					}
				});
            });
        },

        value: function(options) {
            var $this = $(this);
            var localdata = $this.data('localdata');
            var val = localdata.ele.seldiv[localdata.ele.seldiv.length - 1].find('select').val();
            if ((val == 0) && (localdata.ele.seldiv.length > 1)) {
                val = localdata.ele.seldiv[localdata.ele.seldiv.length - 2].find('select').val();
            }
            return val;
        },
        /*===========================================================================*/

        anotherMethod: function(options) {
            return this.each(function(){
                var $this = $(this);
                var localdata = $this.data('localdata');
            });
        },
        /*===========================================================================*/
    };


    $.fn.hlist = function(method) {
        // Method calling logic
        if ( methods[method] ) {
            return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
        } else if ( typeof method === 'object' || ! method ) {
            return methods.init.apply( this, arguments );
        } else {
            throw 'Method ' +  method + ' does not exist on jQuery.hlist';
        }
    };
})( jQuery );
