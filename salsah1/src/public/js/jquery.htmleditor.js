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

/* jshint strict: true */

//=============================================================================
// jQuery plugin-template for SALSAH
//-----------------------------------------------------------------------------
// A jQuery-salsah plugin for editable values must implement the following methods:
//
// - init: This methods displays the content as pure HTML5 without editing possibilities
// - edit: This method displays the content in an editable way
// - value: Returns the HTML as XML
// this plugin handles taskbar symbols between different tabs



(function( $ ) {
    'use strict';

    var xmlDoctype = '<?xml version="1.0" encoding="UTF-8"?>\n';
    var rootTagOpen = "<text>";
	var rootTagClose = "</text>";

    /**
	 * Strips XML doctype and XML root tags (defined in the standard mapping) from XML and returns a HTML string in XML syntax
	 *
     * @param xmlstr the xml to be processed.
	 * @return HTML as a string
     */
	var stripXMLWrapper = function(xmlstr) {

		var htmlstr = xmlstr.replace(xmlDoctype, '').replace(rootTagOpen, '').replace(rootTagClose, '');

		return htmlstr;
	};

    /**
	 * Wraps the given HTML string in an XML doctype and the root tags defined in the standard mapping.
	 *
     * @param htmlstr
     * @returns {string}
     */
	var addXMLWrapper = function(htmlstr) {

		// TODO: if htmlstr is empty, return false

		var xmlstr = xmlDoctype + rootTagOpen + htmlstr + rootTagClose;

		return xmlstr;

	};

	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};

                localdata.settings = {
                    utf8str: "",
                	css: {
                        width: '100%',
                        minHeight: '30px'
                    }
				};
                    
				$.extend(localdata.settings, options);

                var htmlstr;
				if (localdata.settings.xml !== undefined) {
					// xml is given
					htmlstr = localdata.settings.xml.replace('<?xml version="1.0" encoding="UTF-8"?>', "").replace("<text>", "").replace("</text>", "")
					$this
						.attr({contenteditable: false})
						.css(localdata.settings.css)
						.html(htmlstr);
				} else  {
					// text without markup is given
					htmlstr = localdata.settings.utf8str;
					$this
						.attr({contenteditable: false})
						.css(localdata.settings.css)
						.text(htmlstr);
					// above '.text' encode '<strong>' in '&lt;strong&gt;'
					// below replaces the remaining '\n' with '<br>'
					$this.html($this.html().replace(/\n/g, '<br>'));
                }

                //var reg = new RegExp('([^f][^=][^"])(http://[^<>\\s]+[\\w\\d])', 'g');     // replace URLs with anchor tags (but only for strings beginning with http://, not for already existing a-tags containing href="http://")
                //htmlstr = htmlstr.replace(reg, '$1<a href="$2" target="_blank">$2</a>');
                
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		/*===========================================================================*/

		edit: function(options) { // $(element).pluginName('edit', {settings: here,...});
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
                localdata.settings = {
                    utf8str: "",
                    css: {
                        width: '100%',
                        minHeight: '30px'
                    }
                };

                $.extend(localdata.settings, options);

                var htmlstr;
                if (localdata.settings.xml !== undefined) {
                    // xml is given
                    htmlstr = stripXMLWrapper(localdata.settings.xml);
                } else  {
                    // text without markup is given
                    htmlstr = localdata.settings.utf8str;
                }

				$this.css(localdata.settings.css).empty();

				//
				// create a textarea here and create an instance of CKEDITOR
				//
				CKEDITOR.disableAutoInline = true;

				var textarea = $('<textarea>');
				$this.append(textarea);

                // specify allowed elements, attributes, and classes
                // this must conform to the `STANDARD_MAPPING`
                // CKEditor guide to filters: https://docs.ckeditor.com/#!/guide/dev_advanced_content_filter-section-custom-mode-example
                var filter = ' p em strong strike u sub sup hr h1 h2 h3 h4 h5 h6 pre table tbody tr td ol ul li cite blockquote code; a[!href](salsah-link) ';

				var config = {
					language: (SALSAH.userprofile && SALSAH.userprofile.userData && SALSAH.userprofile.userData.lang) ? SALSAH.userprofile.userData.lang : 'en' ,
                    allowedContent: filter,
                    pasteFilter: filter,
                    format_tags: 'p;h1;h2;h3;h4;h5;h6;pre',
					entities: false, // do not use entities (e.g. for Umlaut)
                    coreStyles_strike: { element : 'strike' }, // defines the output for some tags, `s`-> `strike`
                    on: {
						instanceReady: function(event) {

							// init editor with the given html
                            event.editor.setData(htmlstr);
                            
							//
							// bind drop event to editor: a link to a SALSAH ref should be created
							//
                            $(event.editor.document.$.body).dragndrop('makeDropable', function(ev, data, instance) {    

								var attributes = {'href': data.resid, 'class': 'salsah-link'};
								var style = new CKEDITOR.style( { element : 'a', attributes : attributes } );
								style.type = CKEDITOR.STYLE_INLINE;
								style.apply(event.editor.document);

							});
							
						}
					},
                    // ways to build CKEditor:
                    // 1. go to https://ckeditor.com/builder and start from the `basic` option
                    // 2. add plugins: 
                    //    - "Blockquote" : for cite
                    //    - "Format" : for the drop-down format menu - h1, h2, ...
                    //    - "Horizontal rule" : for tage <hr>
                    //    - "Editor resize" : handy resize handle at the corner of the editor
                    //    - "Maximize" : to enjoy a bigger editing zone
                    //    - "Remove Format" : to remove format we added
                    //    - "Soure editing area" : to check what's in (deactivated on prod)
                    //    - "Table" : table editor
                    //  3. choose a skin, add languages french, german, italian
                    //  4. download and uncompress
                    //  5. go to "ckeditor/samples/toolbarconfigurator/index.html" to work-out an editor tool bar
                    //  documentation entry point: http://sdk.ckeditor.com/samples/toolbar.html
                    toolbar: [
                		{ name: 'basicstyles', items: [ 'Format', 'Bold', 'Italic', 'Strike', 'Underline', 'Subscript', 'Superscript', '-', 'RemoveFormat' ] },
                		{ name: 'paragraph', items: [ 'NumberedList', 'BulletedList', '-', 'Blockquote' ] },
                		{ name: 'links', items: [ 'Link', 'Unlink' ] },
                		{ name: 'insert', items: [ 'Table', 'HorizontalRule'] },
                		{ name: 'tools', items: [ 'Maximize' ] }
	               ],
                   
                   // apply the same css as used in the rest of Salsah
                   contentsCss: '/ckeditor.css',
                   customConfig: '/js/empty_config.js'
				};
                
				// init editor (textarea will be replaced by an iframe)
				localdata.editor = CKEDITOR.replace(textarea[0], config);

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

			// get html from the editor instance
			var htmlstr = localdata.editor.getData();

			// return false if there is no content
			if (htmlstr.length == 0) return false;

			// replace non breakable spaces by
			htmlstr = htmlstr.replace(/&nbsp;/g, String.fromCharCode(160));

            // return required params for xml
			return {
				xml: addXMLWrapper(htmlstr),
				mapping_id: STANDARD_MAPPING
			};

		},
		/*===========================================================================*/

		destroy: function() {
			var $this = $(this);
			var localdata = $this.data('localdata');

			// destroy instance of CKEDITOR
			if ((localdata !== undefined) && (localdata.editor !== undefined)) localdata.editor.destroy();
		}

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
