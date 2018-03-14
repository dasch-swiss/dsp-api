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

/**
 * @author Tobias Schweizer <t.schweizer@unibas.ch>
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @package jQuery
 *
 */
(function($){
    
    var mymethod = function() {};
    
    var methods = {
        init: function(options) {
            return this.each(
                function() {
                    var $this = $(this);

                    // defaults
                    var localdata = {
                        settings: {
			    range: {min: 0, max: 591}, // can also be an array with specified values 
			    rowlen: 5,
			    clickedCB: function() {}
			}
                    };

                    if (typeof options === 'object') $.extend(localdata.settings, options);
		    
		    var html = '<table class="chartable"><tr>';
		    
		    var i, cellindex = 0;
		    if (localdata.settings.range instanceof Array) {
			// an array with specified values is given
			for (i in localdata.settings.range) {
			    if (i > 0 && (cellindex % localdata.settings.rowlen) == 0) html += "</tr><tr>";
			    html += "<td data-char=\"" + localdata.settings.range[i] + "\">" + String.fromCharCode(localdata.settings.range[i]) + "</td>";
			    cellindex++;
			}

		    } else {
			// a range with min and max is given
			for (i = localdata.settings.range.min; i <= localdata.settings.range.max; i++) {
			    if (i > 0 && (cellindex % localdata.settings.rowlen) == 0) html += "</tr><tr>";
			    html += "<td data-char=\"" + i + "\">" + String.fromCharCode(i) + "</td>";
			    cellindex++;
			}
		    }

		    html += '</tr><tr><td class="abort"><strong>Abort</strong></td></tr></table>';

		    var table = $(html, {'class': 'chartable'});
		    

		    if (localdata.settings.clickedCB instanceof Function) {
			table.find('td').on('click', function(){
			    $this = $(this);
			
			    if ($this.hasClass('abort')) {
				table.parent().remove();
				return;
			    }

			    localdata.settings.clickedCB($this.text(), $this.data('char'));
			    //alert($this.text() + ' has unicode: ' + $this.data('char'));
			});
		    }
		    $this.append(table);

                    $this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

                });
        }
    };

    /**
     * @memberOf jQuery.fn
     */
    $.fn.chartable = function(method) {
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