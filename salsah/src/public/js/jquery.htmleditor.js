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

/* jshint strict: true */

//=============================================================================
// jQuery plugin-template for SALSAH
//-----------------------------------------------------------------------------
// A jQuery-salsah plugin for editable values must implement the following methods:
//
// - init: This methods displays the content as pure HTML5 without editing possibilities
// - edit: This method displays the content in an editable way
// - value:
// this plugin handles taskbar symbols between different tabs



(function( $ ) {
    'use strict';

	/**
	 * This functions converts the HTML-representation of a text to a linear character stream with offset properties
	 * @param {DOM node} cur_node The node to start with (initially, the containing is given, it is recursively processed)
	 * @param {object} localdata The plugin's current data
	 * @return {object} An object containing the text's properties, example: {propname: [{start: 0, end: 5}, {start: 8, end: 10}]}
	 */
	var convert_html2lin = function(cur_node, localdata) {

		var props = {};
		var control_chars = [];
		var index;
		var text = '';

		var add_prop = function(type, start, length) {
			
            if (props[type] === undefined) {
				props[type] = [];
			}
			props[type].push({
				start: start,
				end: start + length
			});
		};

		var __convert_html2lin = function(cur_node, char_count) {
			var i;

			if (cur_node.nodeType == 3) { 
				// text node: count the chars (length)
				char_count += cur_node.nodeValue.length;
				text = text + cur_node.nodeValue;
			} else {
				// register the different tags in the global entities array
                
               
                
                if (cur_node.nodeName.toLowerCase() == 'br') {
					// add linebreak
					control_chars.push({pos: char_count, 'char': '\n'}); // add the current position to the array of control_chars
					char_count++; // increment by one for '\n' to be inserted later
				}
				else if (localdata.settings.matching[cur_node.nodeName.toLowerCase()] !== undefined) {
					// here comes a matching node

					// store the current car_count
					var tmp_char_count = char_count;
					
					for (i = 0; i < cur_node.childNodes['length']; i++) {
						// process the children of the current node
						char_count = __convert_html2lin(cur_node.childNodes[i], char_count); // recursion !!!!!!!!!!!!!!
					}

					if (cur_node.nodeName.toLowerCase() == 'p' || cur_node.nodeName.toLowerCase() == 'li' || cur_node.nodeName.toLowerCase()[0] == 'h') {
						// it is a block element, insert a \r later so that in utf8str their string representations are not attached to each other
						control_chars.push({pos: char_count, 'char': '\r'});
						char_count++; // increment by one for '\r' to be inserted
						
						// the children have been processed (we know the length now), add the prop
						add_prop(localdata.settings.matching[cur_node.nodeName.toLowerCase()], tmp_char_count, char_count - tmp_char_count);
					} else if (cur_node.nodeName.toLowerCase() == 'span') {
						add_prop('style', tmp_char_count, char_count - tmp_char_count);
						
						// add style information
						index = props['style'].length -1;
						props[localdata.settings.matching[cur_node.nodeName.toLowerCase()]][index].css = cur_node.getAttribute('style');
						
					} else if (cur_node.nodeName.toLowerCase() == 'a') {

						add_prop(localdata.settings.matching[cur_node.nodeName.toLowerCase()], tmp_char_count, char_count - tmp_char_count);

						index = props[localdata.settings.matching[cur_node.nodeName.toLowerCase()]].length -1;
						props[localdata.settings.matching[cur_node.nodeName.toLowerCase()]][index].href = cur_node.getAttribute('href');

						// check if it is a SALSAH-link
						if (cur_node.getAttribute('class') == 'salsah-link') {
							props[localdata.settings.matching[cur_node.nodeName.toLowerCase()]][index].resid = cur_node.getAttribute('href');

						}

					} else {
						// not a block element (except ul/ol)

						add_prop(localdata.settings.matching[cur_node.nodeName.toLowerCase()], tmp_char_count, char_count - tmp_char_count);
					}
					
				} else {
                        
					   // ignore tag without semantics: process its children
					   for (i = 0; i < cur_node.childNodes['length']; i++) {
						   char_count = __convert_html2lin(cur_node.childNodes[i], char_count); // recursion
					   }
				   }
			}
			return char_count;
		};

        
        
		__convert_html2lin(cur_node, 0);

		for (var jj in props) {
			props[jj].sort(sorting_func);
			if ((jj != '_link') && (jj != 'p') && (jj != 'li') && (jj[0] != 'h') && jj != 'style') handle_overlap(props[jj]); // do not collapse block structures (nor span)
		}

		props.control_chars = control_chars;
		props.text = text;
		
		return props;
	};

	/**
	 * This function sorts an array by its starting index (callback for the built-in sorting function)
	 * @param {array element} first_ele element
	 * @param {array element} second_ele element
	 * @return {int} A number indicating the result of the comparison
	 */
	var sorting_func = function(first_ele, second_ele) {
		// element with lower start value comes first
		if (first_ele.start < second_ele.start) {
			return -1;
		} else {
			return 1;
		}
	};

	/**
	 * This function removes overlapping structures within the array representing the character stream's properties (MUSTN'T be called on properties with lenth == 0,  because the would be removed)
	 * Overlapping structures will be combined to one single structure. Hierarchical markup can be ambiguous (two possibilities of nesting) where this linear structure is not
	 * @param {array} property_array Represents the character stream's properties (this argument is processed BY REFERENCE)
	 */
	var handle_overlap = function(property_array) {
		for (var j = 0; j < property_array.length; j++) {
			if (property_array[j].start == property_array[j].end) {
				// 'empty' prop
				property_array.splice(j,1);
				j--;
			} else if (property_array.length > j + 1) {
				if (property_array[j].end >= property_array[j+1].start && property_array[j].end <= property_array[j+1].end) {
					// end of current element matches start of the following: merge them and process this merged item (index is the current one)
					property_array[j].end = property_array[j+1].end;
					property_array.splice(j+1,1);
					j--;
				} else if (property_array[j].end > property_array[j+1].end) {
					// current element embraces the following totally: delete it
					property_array.splice(j+1,1);
					// process the current element another time
					j--;
				}
			}
		}
		return;
	};

	/**
	 * This function converts a linear character stream with offset properties to a HTML-representation
	 * @param assigned_props The object representing the text's properties
	 * @param txt The text (sequence of characters)
	 * @return {String} A string which can be converted to HTML
	 */
	var convert_lin2html = function(assigned_props, txt, localdata) {
		var html = '';
		var proparr;
		var stack;
		var pos;
		var tmp;
		var tmpstack;
		var propname;
		var idx;
		var i, j;
		var lbstack;

		// invert matching so that we can check for the offset names
		var matching_inv = {};
		for (var prop in localdata.settings.matching) {
			matching_inv[localdata.settings.matching[prop]] = prop;
		}

		//
		// sort keys (propnames) according to tag precedence
		//
		var propnames = Object.keys(assigned_props);
		
		propnames.sort(function(a, b) {
			return (tagPrecedence[matching_inv[a]] - tagPrecedence[matching_inv[b]]);
		});

		//
		// register props (their starting and ending point) for each position in the text 
		//
		proparr = [];
		for (propname in propnames) {
			// process propname by propname in the defined order (tag precedence)
			propname = propnames[propname]; // propname is numeric (array index), get the prop's name (string)
			for(idx in assigned_props[propname]) {
				// process the array of assigned objects for this propname
				pos = assigned_props[propname][idx].start;
				
				if (proparr[pos] === undefined) {
					proparr[pos] = [];
				}
				proparr[pos].push({
					propname: propname,
					proptype: 'start'
				});

				if (proparr[pos][proparr[pos].length - 1].propname == '_link') {
					proparr[pos][proparr[pos].length - 1].href = assigned_props[propname][idx].href; // add href to link property
					if (assigned_props[propname][idx].resid !== undefined) {
						proparr[pos][proparr[pos].length - 1].resid = assigned_props[propname][idx].resid; // add resid to link property
					}
				} else if (proparr[pos][proparr[pos].length - 1].propname == 'style') {
					proparr[pos][proparr[pos].length - 1].css = assigned_props[propname][idx].css; // add style
				}

				pos = assigned_props[propname][idx].end;
				
				if (proparr[pos] === undefined) {
					proparr[pos] = [];
				}
				proparr[pos].push({
					propname: propname,
					proptype: 'end'
				});
			}
		}

		//
		// go through the single chars of the text and create html tags according to proparr
		//
		stack = [];
		for (i = 0; i <= txt.length; i++) {
			if (proparr[i] !== undefined) {
				// there is an entry in proparr for the current pos
				tmpstack = [];
				lbstack = [];
				for (j = proparr[i].length - 1; j >= 0; j--) {
					// go through the array from back to front (it is a stack!!)
					// tags which have been opened later (lower precedence -> order in proparr[position]) have to be closed first
					// because no overlap is allowed
					if (proparr[i][j].proptype == 'end' && proparr[i][j].propname != 'linebreak') {
						while ((tmp = stack.pop()) !== undefined) {
							// close tag
							html += '</' + matching_inv[tmp] + '>';
							if (tmp == proparr[i][j].propname) {
								// tag ends here
								break; // leave while loop;
							} else {
								// tag had only to be closed temporarily
								tmpstack.push(tmp);
							}
						}
						while ((tmp = tmpstack.pop()) !== undefined) {
							stack.push(tmp);
							// reopen previously closed tags
							html += '<' + matching_inv[tmp] + '>';
						}
					}
				}
				for (j in proparr[i]) {
					// open tags here (according to tag precedence sorting)
					// add them to the stack -> the higher the index in the stack, the lower the precedence 
					// or the tag has been added at another position (later)
					if (proparr[i][j].proptype == 'start') {
						if (proparr[i][j].propname == 'linebreak') { // only due to backwards compatibility
							html += '<br/>';
						} else if (proparr[i][j].propname == '_link') {
							stack.push(proparr[i][j].propname);
							// create an anchor tag with href
							var href = proparr[i][j].href;
							if (href === undefined && proparr[i][j].resid !== undefined) {
								// backwards compatibility
								// before, no href was set
								href = proparr[i][j].resid;
							} 
							html += '<' + matching_inv[proparr[i][j].propname] + ' href="' + href;
							if (proparr[i][j].resid !== undefined) {
								html += '" class="salsah-link">';
							} else {
								html += '">';
							}
						} else if (proparr[i][j].propname == 'style') {
							stack.push(proparr[i][j].propname);
							html += '<' + matching_inv[proparr[i][j].propname] + ' style="' + proparr[i][j].css + '">';
							
						} 
						else {
							stack.push(proparr[i][j].propname);
							html += '<' + matching_inv[proparr[i][j].propname] + '>';
						}
					}
				}

			}
			if (i < txt.length) {
				html += txt.charAt(i);
			}
		}

		// replace '\n' with <br>
		html = html.replace(/\n/g, '<br/>');
		// remove \r since they are represented by block elements
		html = html.replace(/\r/g, '');


		return html;
	};

    var tagPrecedence = {
		p: 0,
		h1: 0,
		h2: 0,
		h3: 0,
		h4: 0,
		h5: 0,
		h6: 0,
		ol: 0,
		ul: 0,
		li: 1,
		a: 2,
		strong: 3,
		u: 3,
		s: 3,
		em: 3,
		span: 3,
		sup: 3,
		sub: 3
    };

	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = { // any initializations come here
					utf8str: '',
					textattr: {},
					css: {
						width: '100%',
						minHeight: '30px'
					},
					matching: { // match tagnames to offset labels
						strong: 'bold',
						u: 'underline',
						s: 'strikethrough',
						em: 'italic',
						'a': '_link',
						h1: 'h1',
						h2: 'h2',
						h3: 'h3',
						h4: 'h4',
						h5: 'h5',
						h6: 'h6',
						ol: 'ol',
						ul: 'ul',
						li: 'li',
						span: 'style',
						p: 'p',
						sup: 'sup',
						sub: 'sub'		
					}
				};
                
                    
//                console.log(options);
                delete options.matching; // ignore options from the db for the moment
				$.extend(localdata.settings, options);

				//
				// here we convert the standoff markup and	the textstring into a valid HTML5-string
				//
                var htmlstr = convert_lin2html(localdata.settings.textattr, localdata.settings.utf8str, localdata);
                
                //console.log(htmlstr);
                
                var reg = new RegExp('([^f][^=][^"])(http://[^<>\\s]+[\\w\\d])', 'g');     // replace URLs with anchor tags (but only for strings beginning with http://, not for already existing a-tags containing href="http://")
                htmlstr = htmlstr.replace(reg, '$1<a href="$2" target="_blank">$2</a>');     
                
                //console.log(htmlstr);
                
				$this
					.attr({contenteditable: false})
					.css(localdata.settings.css)
					.html(htmlstr);
					

				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		/*===========================================================================*/

		//
		// options:
		//	 options.htmlstring : content to be edited [optional parameter; if not used, the value from a previous "init"/"edit" is taken]
		//
		edit: function(options) { // $(element).pluginName('edit', {settings: here,...});
			return this.each(function() {
				var $this = $(this);
				var i;
				var localdata = {};
				localdata.settings = { // any initializations come here
					utf8str: '',
					textattr: {},
					css: {
						width: '100%',
						minHeight: '30px'
					},
					matching: { // match tagnames to offset labels
						strong: 'bold',
						u: 'underline',
						s: 'strikethrough',
						em: 'italic',
						'a': '_link',
						h1: 'h1',
						h2: 'h2',
						h3: 'h3',
						h4: 'h4',
						h5: 'h5',
						h6: 'h6',
						ol: 'ol',
						ul: 'ul',
						li: 'li',
						span: 'style',
						p: 'p',
						sup: 'sup',
						sub: 'sub'		
					} 
				};

                //console.log('options: ');    
                //console.log(options);
                
                delete options.matching; //ignore options from the db for the moment
				$.extend(localdata.settings, options);

				$this.css(localdata.settings.css).empty();

				var htmlstr = convert_lin2html(localdata.settings.textattr, localdata.settings.utf8str, localdata);
				
				//console.log(htmlstr);

				//
				// create a textarea here and create an instance of CKEDITOR
				//
				CKEDITOR.disableAutoInline = true;

				var textarea = $('<textarea>');
				$this.append(textarea);
				
				/*var div = $('<div>', {contenteditable: true});
				$this.append(div);*/
/*
				var config = {
					language: 'de', // customize language
					//extraAllowedContent: 'ins del[data-*]', // allow ins and del tags with atrributes
					allowedContent: true,
                    on: {
						instanceReady: function(event) {
							
                            event.editor.setData(htmlstr);

                            //console.log($this.find('iframe').contents().find('body'));
                            
							//
							// bind drop event to editor: a link to a SALSAH ref should be created
							//
							//$this.find('iframe').contents().find('body').dragndrop('makeDropable', function(ev, data, instance) {
                            $(event.editor.document.$.body).dragndrop('makeDropable', function(ev, data, instance) {    
							//div.dragndrop('makeDropable', function(ev, data, instance) {
                                
                                //console.log('drop');
                                
								var attributes = {'href': 'http://www.salsah.org/api/resources/' + data.resid, 'class': 'salsah-link'};
								var style = new CKEDITOR.style( { element : 'a', attributes : attributes } );
								style.type = CKEDITOR.STYLE_INLINE;
								style.apply(event.editor.document);

							});
							
						}
					},
					toolbar: [ ['Source', '-', 'Bold', 'Italic', 'Underline', 'Strike', 'Subscript', 'Superscript', '-','RemoveFormat', '-', 'NumberedList', 'BulletedList', 'Table', '-', 'Link', 'Unlink','-', 'TextColor', 'BGColor', '-', 'Styles', 'Inserttag', 'Deletetag', 'Subst'] ], // configuration for toolbar buttons
					stylesSet: [
					// Block-level styles
					
						{ name: 'header1' , element: 'h1'},
						{ name: 'header2' , element: 'h2'},
						{ name: 'header3' , element: 'h3'},
						{ name: 'header4' , element: 'h4'},
						{ name: 'header5' , element: 'h5'},
						{ name: 'header6' , element: 'h6'}
						
					],
					extraPlugins: 'inserttag,deletetag,subst' // load additional plugins
				 
				};
*/

				var config = {
					language: 'de', // customize language
					//extraAllowedContent: 'ins del[data-*]', // allow ins and del tags with atrributes
					allowedContent: true,
                    on: {
						instanceReady: function(event) {
							
                            event.editor.setData(htmlstr);

                            //console.log($this.find('iframe').contents().find('body'));
                            
							//
							// bind drop event to editor: a link to a SALSAH ref should be created
							//
							//$this.find('iframe').contents().find('body').dragndrop('makeDropable', function(ev, data, instance) {
                            $(event.editor.document.$.body).dragndrop('makeDropable', function(ev, data, instance) {    
							//div.dragndrop('makeDropable', function(ev, data, instance) {
                                
//                                console.log('drop');
								
								var attributes = {'href': data.resid, 'class': 'salsah-link'};
								var style = new CKEDITOR.style( { element : 'a', attributes : attributes } );
								style.type = CKEDITOR.STYLE_INLINE;
								style.apply(event.editor.document);

							});
							
						}
					},
					toolbar: [ ['Bold', 'Italic', 'Underline', 'Strike', 'Subscript', 'Superscript', '-','RemoveFormat', 'Link', 'Unlink'] ], // configuration for toolbar buttons
					stylesSet: [
					// Block-level styles
					
						/*{ name: 'header1' , element: 'h1'},
						{ name: 'header2' , element: 'h2'},
						{ name: 'header3' , element: 'h3'},
						{ name: 'header4' , element: 'h4'},
						{ name: 'header5' , element: 'h5'},
						{ name: 'header6' , element: 'h6'}
						*/
					]//,
					//extraPlugins: 'inserttag,deletetag,subst' // load additional plugins
				 
				};






				// init editor (textarea will be replaced by an iframe)
				localdata.editor = CKEDITOR.replace(textarea[0], config);

				//localdata.editor = CKEDITOR.inline(div[0], config);

				//
				// do stuff here to display and make the data editabl..., e.g. $this.append(…)
				//
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		/*===========================================================================*/

		value: function() { 
			var $this = $(this);
			var localdata = $this.data('localdata');

			//var html = $this.find('iframe').contents().find('body');
			
			var htmlstr = localdata.editor.getData();
			htmlstr = htmlstr.replace(/\n/g, ''); // remove newlines and carriage returns from html string
			htmlstr = htmlstr.replace(/\r/g, '');

			//htmlstr = localdata.editor.dataProcessor.toHtml(htmlstr);

            
			var html = $('<div>').append(htmlstr);	
            var textattr = convert_html2lin(html[0], localdata);
			var utf8str = textattr.text;
            
                
			html.remove();			

			// insert control chars into utf8str
			for (var i in textattr.control_chars) {
				var j = textattr.control_chars[i].pos;
				utf8str = utf8str.substring(0, j) + textattr.control_chars[i]['char'] + utf8str.substr(j);
			}
			
			delete textattr.control_chars;
			delete textattr.text;

            
            
			return {
				textattr: textattr,
				utf8str: utf8str
			};

		},
		destroy: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');

			// destroy instance of CKEDITOR
			if ((localdata !== undefined) && (localdata.editor !== undefined)) localdata.editor.destroy();
		}
		

		/*===========================================================================*/

		/*
		 anotherMethod: function(options) {
		 return this.each(function(){
		 var $this = $(this);
		 var localdata = $this.data('localdata');
		 });
		 }
		 */
		/*===========================================================================*/
	};

	$.fn.htmleditor = function(method) {
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
