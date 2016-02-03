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

(function($){
	
	var draw_edge = function(context, pt1, pt2, label) {
		var headlen = 10;   // length of head in pixels
		var angle = Math.atan2(pt2.y - pt1.y, pt2.x - pt1.x);
		context.moveTo(pt1.x, pt1.y);
		context.lineTo(pt2.x - headlen*Math.cos(angle), pt2.y - headlen*Math.sin(angle));
		context.lineTo(pt2.x - headlen*Math.cos(angle) - headlen*Math.cos(angle-Math.PI/6), pt2.y - headlen*Math.sin(angle) - headlen*Math.sin(angle-Math.PI/6));
		context.moveTo(pt2.x - headlen*Math.cos(angle), pt2.y - headlen*Math.sin(angle));
		context.lineTo(pt2.x - headlen*Math.cos(angle) - headlen*Math.cos(angle+Math.PI/6), pt2.y - headlen*Math.sin(angle) - headlen*Math.sin(angle+Math.PI/6));
		if (label !== undefined) {
			context.fillStyle = 'red';
			context.font = "11px Arial";
			context.textAlign = "center";
			var w = context.measureText(label).width;
			context.fillText(label, pt1.x + (pt2.x - pt1.x)/2, pt1.y + (pt2.y - pt1.y)/2);
		}
		return;
	}
	
	var Renderer = function($this){
		var localdata = $this.data('localdata');
		var canvas = $this.get(0);
		var ctx = canvas.getContext("2d");

		var that = {
			init:function(system){
				//
				// the particle system will call the init function once, right before the
				// first frame is to be drawn. it's a good place to set up the canvas and
				// to pass the canvas size to the particle system
				//
				// save a reference to the particle system for use in the .redraw() loop
				localdata.particleSystem = system;

				// inform the system of the screen dimensions so it can map coords for us.
				// if the canvas is ever resized, screenSize should be called again with
				// the new dimensions
				localdata.particleSystem.screenSize(canvas.width, canvas.height);
				localdata.particleSystem.screenPadding(80); // leave an extra 80px of whitespace per side
        
				// set up some event handlers to allow for node-dragging
				that.initMouseHandling();
			},
      
			redraw:function() {
				// 
				// redraw will be called repeatedly during the run whenever the node positions
				// change. the new positions for the nodes can be accessed by looking at the
				// .p attribute of a given node. however the p.x & p.y values are in the coordinates
				// of the particle system rather than the screen. you can either map them to
				// the screen yourself, or use the convenience iterators .eachNode (and .eachEdge)
				// which allow you to step through the actual node objects but also pass an
				// x,y point in the screen's coordinate system
				// 
				ctx.fillStyle = "white";
				ctx.fillRect(0,0, canvas.width, canvas.height);
        
				localdata.particleSystem.eachEdge(function(edge, pt1, pt2){
					// edge: {source:Node, target:Node, length:#, data:{}}
					// pt1:  {x:#, y:#}  source position in screen coords
					// pt2:  {x:#, y:#}  target position in screen coords

					// draw a line from pt1 to pt2
					if ((edge.source.name.charAt(0) == 'R') && (edge.target.name.charAt(0) == 'R')) {
						ctx.strokeStyle = "rgba(255,0,0, 0.666)";
					}
					else {
						ctx.strokeStyle = "rgba(0,0,0, .333)";
					}
					ctx.lineWidth = edge.data.cnt;
					ctx.beginPath();
					if ((edge.source.name.charAt(0) == 'R') && (edge.target.name.charAt(0) == 'R')) {
						draw_edge(ctx, pt1, pt2, edge.data.label);
					}
					else {
						ctx.moveTo(pt1.x, pt1.y);
						ctx.lineTo(pt2.x, pt2.y);
					}
					ctx.stroke();
				});

				localdata.particleSystem.eachNode(function(node, pt){
					// node: {mass:#, p:{x,y}, name:"", data:{}}
					// pt:   {x:#, y:#}  node position in screen coords

					// draw a rectangle centered at pt

					var nn = node.name.split('_');
					var w = 10;
					var valstr;
					if (nn[0] == 'RES') {
						// ctx.drawImage(node.data.icon, pt.x - node.data.icon.width/2, pt.y - node.data.icon.height/2);
						var res_id = node.data.res_id;
						var nodedata = localdata.settings.nodes[res_id];
						if (nodedata.collapsed) {
							ctx.drawImage(node.data.icon, pt.x - node.data.icon.width/2, pt.y - node.data.icon.height);
							
							ctx.fillStyle = 'green';
							ctx.font = "11px Arial";
							ctx.textAlign = "center";
							valstr = (node.data.firstproperty.length > 32) ?  node.data.firstproperty.substr(0, 31) + '…' : node.data.firstproperty;
							var w = ctx.measureText(valstr).width;
							ctx.fillText(valstr, pt.x, pt.y + 14);
						}
						else {
							ctx.drawImage(node.data.icon, pt.x - node.data.icon.width/2, pt.y - node.data.icon.height/2);
						}
					}
					else if (nn[0] == 'PROP') {
						var label = [];
						valstr = node.data.label + ':';
						var tmp_w = ctx.measureText(valstr).width + 6;
						var i;
						if (tmp_w > w) w = tmp_w;
						label.push(valstr);
						for (i in node.data.values) {
							valstr = (node.data.values[i].length > 20) ?  node.data.values[i].substr(0, 19) + '…' : node.data.values[i];
							tmp_w = ctx.measureText(valstr).width + 6;
							if (tmp_w > w) w = tmp_w;
							label.push(valstr);
						}
						ctx.clearRect(pt.x - w / 2, pt.y - label.length*7, w, label.length*14);
						ctx.strokeStyle = "rgba(0,0,0, 0.333)";
						ctx.strokeRect(pt.x - w / 2, pt.y - label.length*7, w, label.length*14);
						ctx.fillStyle = 'orange';
						ctx.font = "11px Arial";
						ctx.textAlign = "center";
						for (i in label) {
							ctx.fillText(label[i], pt.x, pt.y - label.length*7 + i*14 + 11);
						}
					}
				});
			},
      
			initMouseHandling:function(){
				// no-nonsense drag and drop (thanks springy.js)
				var dragged = null;

				// set up a handler object that will initially listen for mousedowns then
				// for moves and mouseups while dragging
				var handler = {
					clicked:function(e){
						if (localdata.settings.mouseaction == 'move') {
							var pos = $(canvas).offset();
							_mouseP = arbor.Point(e.pageX-pos.left, e.pageY-pos.top);
							dragged = localdata.particleSystem.nearest(_mouseP);

							if (dragged && dragged.node !== null){
								// while we're dragging, don't let physics move the node
								dragged.node.fixed = true;
							}

							$(canvas).bind('mousemove', handler.dragged);
							$(window).bind('mouseup', handler.dropped);
						}
						else if (localdata.settings.mouseaction == 'nodeaction') {
							var pos = $(canvas).offset();
							_mouseP = arbor.Point(e.pageX-pos.left, e.pageY-pos.top);
							var clickobj = localdata.particleSystem.nearest(_mouseP);
							var res_id = clickobj.node.data.res_id;
							var node = localdata.settings.nodes[res_id];
							if (node.collapsed) {
								for (var j in node.properties) {
									propnode = localdata.particleSystem.addNode('PROP_' + node.properties[j].value_ids[0], node.properties[j]);
									localdata.particleSystem.addEdge(propnode, clickobj.node);
								}
								node.collapsed = false;
							}
							else {
								for (var j in node.properties) {
									localdata.particleSystem.pruneNode('PROP_' + node.properties[j].value_ids[0]);
								}
								node.collapsed = true;
							}
						}

						return false;
					},
					dragged:function(e){
						var pos = $(canvas).offset();
						var s = arbor.Point(e.pageX-pos.left, e.pageY-pos.top);

						if (dragged && dragged.node !== null){
							var p = localdata.particleSystem.fromScreen(s);
							dragged.node.p = p;
						}

						return false;
					},

					dropped:function(e){
						if (dragged===null || dragged.node===undefined) return;
						if (dragged.node !== null) dragged.node.fixed = false;
						dragged.node.tempMass = 1000;
						dragged = null;
						$(canvas).unbind('mousemove', handler.dragged);
						$(window).unbind('mouseup', handler.dropped);
						_mouseP = null;
						return false;
					}
				};
        
				// start listening
				$(canvas).mousedown(handler.clicked);
				$(canvas).contextmenu(handler.contextmenu);

			}
      
		};
		return that;
	};

	var build_node = function(sys, res_id, node) {
		node.resinfo.res_id = res_id;
		node.resinfo.icon = new Image();
		node.resinfo.icon.src = node.resinfo.iconsrc;
		node.resinfo.collapsed = false;
		/*
		node.resinfo.propnodes = [];
		for (var j in node.properties) {
			node.resinfo.propnodes.push('PROP_' + node.properties[j].value_ids[0]);
		}
		*/
		var newnode = sys.addNode('RES_' + res_id, node.resinfo);
		var propnode;
		for (var j in node.properties) {
			propnode = sys.addNode('PROP_' + node.properties[j].value_ids[0], node.properties[j]);
			sys.addEdge(propnode, newnode);
		}
		return;
	};
	
	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {
					nodes: undefined,
					edges: undefined,
					repulsion: 500,
					stiffness: 600,
					friction: 0.5,
					show_props: true,
					mouseaction: 'move'
				};
				$.extend(localdata.settings, options);
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
				__nodes = localdata.settings.nodes;
				__edges = localdata.settings.edges;
				var sys = arbor.ParticleSystem(localdata.settings.repulsion, localdata.settings.stiffness, localdata.settings.friction); // create the system with sensible repulsion/stiffness/friction
				sys.parameters({gravity:true}); // use center-gravity to make the graph settle nicely (ymmv)
				sys.renderer = Renderer($this); // our newly created renderer will have its .init() method called shortly by sys...
				
				sys.screenSize($this.innerWidth(), $this.innerHeight());
		
				for (var res_id in localdata.settings.nodes) {
					build_node(sys, res_id, localdata.settings.nodes[res_id]);
				}

				var node_ids;
				for (var edge in localdata.settings.edges) {
					//node_ids = edge.split(';');
					//sys.addEdge('RES_' + node_ids[0], 'RES_' + node_ids[1], edges[edge]);
					sys.addEdge('RES_' + localdata.settings.edges[edge].from, 'RES_' + localdata.settings.edges[edge].to, localdata.settings.edges[edge]);
				}
			});
		},
		
		showAttributes: function(val) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				var nodes = localdata.settings.nodes;
				var edges = localdata.settings.edges;
				
				if (!val && localdata.settings.show_props) {
					localdata.particleSystem.eachNode(function(node, pt){
						if (node.name.charAt(0) == 'P') {
							localdata.particleSystem.pruneNode(node);
						}
						else {
							var res_id = node.data.res_id;
							var nodedata = localdata.settings.nodes[res_id];
							nodedata.collapsed = true;
						}
					});
					localdata.settings.show_props = false;
				}
				else if (val && !localdata.settings.show_props) {
					for (var res_id in nodes) {
						for (var j in nodes[res_id].properties) {
							propnode = localdata.particleSystem.addNode('PROP_' + nodes[res_id].properties[j].value_ids[0], nodes[res_id].properties[j]);
							localdata.particleSystem.addEdge(propnode, 'RES_' + res_id);
						}
						nodes[res_id].collapsed = false;
					}
					localdata.settings.show_props = true;
				}
			});
		},
		
		setMouseAction: function(val) {
			return this.each(function() {
				var $this = $(this);
				var localdata = $this.data('localdata');
				if (val == 'MOVE') {
					localdata.settings.mouseaction = 'move';
				}
				else if (val == 'NODEACTION') {
					localdata.settings.mouseaction = 'nodeaction';
				}
			});
		}
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