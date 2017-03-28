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

(function($){
	


	function knoraNode2Cytoscape(knoraNode) {
		return {
			data: {
				id: knoraNode.resourceIri,
				label: knoraNode.resourceLabel
			},
			classes: 'node'
		};
	}

	function knoraEdge2Cytoscape(knoraEdge) {
		return {
			data: {
				source: knoraEdge.source,
				target: knoraEdge.target,
				label: knoraEdge.propertyLabel
			},
			classes: 'edge'
		};
	}

	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					nodes: undefined,
					edges: undefined
				};
				$.extend(localdata.settings, options);
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object

				var cytoscapeNodes = R.map(knoraNode2Cytoscape, localdata.settings.nodes);
				var cytoscapeEdges = R.map(knoraEdge2Cytoscape, localdata.settings.edges);
				var elements = cytoscapeNodes.concat(cytoscapeEdges);

		        var cy = window.cy = cytoscape({
		          container: this,

		          boxSelectionEnabled: false,
		          autounselectify: true,

		          style: [
		            {
		              selector: 'node',
		              style: {
		                'label': 'data(label)',
		                'shape': 'rectangle',
		                'width': 'label',
		                'height': 'label',
		                'text-valign': 'center',
		                'text-halign': 'center',
		                'background-color': '#ffffff',
		                'border-width': 1,
		                'border-style': 'solid',
		                'border-color': '#9dbaea',
		                'font-size': 10
		              }
		            },

		            {
		              selector: 'edge',
		              style: {
		                'label': 'data(label)',
		                'width': 1,
		                'target-arrow-shape': 'triangle-backcurve',
		                'line-color': '#9dbaea',
		                'target-arrow-color': '#9dbaea',
		                'curve-style': 'bezier',
		                'text-opacity': 0.75,
		                'edge-text-rotation': 'autorotate',
		                'font-size': 8
		              }
		            },
		          ],

		          elements: elements
		        });

		        // Make a cytoscape-cola layout (https://github.com/cytoscape/cytoscape.js-cola)
		        var layout = cy.makeLayout({
		            name: 'cola',
		            // infinite: true,
		            maxSimulationTime: 4000,
		            fit: true
		        });

		        // Run the layout.
		        layout.run();

		        // Re-run the layout if the user drags a node.
		        cy.on('tapdrag', 'node', function(event){
		          layout.run();
		        });

		        /*
		        cy.on('click', 'node', function(event){
		          alert("You clicked on node " + event.cyTarget.data("id") + ", which has label " + event.cyTarget.data("label"));
		        });
		        */

			});
		},
	};
	
	$.fn.graph = function(method) {
		// Method calling logic
		if ( methods[method] ) {
			return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
		} else if ( typeof method === 'object' || ! method ) {
			return methods.init.apply( this, arguments );
		} else {
			throw 'Method ' +  method + ' does not exist on jQuery.tooltip';
		}
		return undefined;
	};

})(this.jQuery);