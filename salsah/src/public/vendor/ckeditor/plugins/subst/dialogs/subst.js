/**
 * Copyright (c) 2014, CKSource - Frederico Knabben. All rights reserved.
 * Licensed under the terms of the MIT License (see LICENSE.md).
 *
 * The abbr plugin dialog window definition.
 *
 * Created out of the CKEditor Plugin SDK:
 * http://docs.ckeditor.com/#!/guide/plugin_sdk_sample_1
 */

// Our dialog definition.
CKEDITOR.dialog.add( 'substDialog', function( editor ) {
	return {

		// Basic properties of the dialog window: title, minimum size.
		title: 'Substitution Properties',
		minWidth: 400,
		minHeight: 200,

		// Dialog window content definition.
		contents: [
			{
				// Definition of the Basic Settings dialog tab (page).
				id: 'tab-basic',
				label: 'Basic Settings',

				// The tab content.
				elements: [
					{
						// Text input field for the abbreviation text.
						type: 'text',
						id: 'del',
						label: 'Deleted Char',

						// Validation checking whether the field is not empty.
						validate: CKEDITOR.dialog.validate.notEmpty( "Deletion field cannot be empty." ),

						// Called by the main setupContent method call on dialog initialization.
						setup: function( element ) {
							//this.setValue( element.getText() );
                                              
                            var children = element.getChildren();
                            var del;
                            
                            for (var i = 0; i < children.count(); i++) {
                                if (children.getItem(i).$.nodeName.toLowerCase() == 'del') {
                                    del = children.getItem(i)
                                }
                            }
                            
                            if (del !== undefined) {
                                this.setValue( del.getText() );
                            }
						},

						// Called by the main commitContent method call on dialog confirmation.
						commit: function( element ) {
							//element.setText( this.getValue() );
                            var children = element.getChildren();
                            var del;
                            
                            for (var i = 0; i < children.count(); i++) {
                                if (children.getItem(i).$.nodeName.toLowerCase() == 'del') {
                                    del = children.getItem(i)
                                }
                            }
                            
                            if (del !== undefined) {
                                del.setText(this.getValue());
                            } else {
                                del = editor.document.createElement( 'del' );
                                del.setText(this.getValue());
                                element.append(del);
                            }
						}
					},
					{
						// Text input field for the abbreviation title (explanation).
						type: 'text',
						id: 'ins',
						label: 'Inserted Char',
						validate: CKEDITOR.dialog.validate.notEmpty( "Explanation field cannot be empty." ),

						// Called by the main setupContent method call on dialog initialization.
						setup: function( element ) {
							//this.setValue( element.getAttribute( "title" ) );
                            
                            var children = element.getChildren();
                            var ins;
                            
                            for (var i = 0; i < children.count(); i++) {
                                if (children.getItem(i).$.nodeName.toLowerCase() == 'ins') {
                                    ins = children.getItem(i)
                                }
                            }
                            
                            if (ins !== undefined) {
                                this.setValue( ins.getText() );
                            }
						},

						// Called by the main commitContent method call on dialog confirmation.
						commit: function( element ) {
							//element.setAttribute( "title", this.getValue() );
                            var children = element.getChildren();
                            var ins;
                            
                            for (var i = 0; i < children.count(); i++) {
                                if (children.getItem(i).$.nodeName.toLowerCase() == 'ins') {
                                    ins = children.getItem(i)
                                }
                            }
                            
                            if (ins !== undefined) {
                                ins.setText(this.getValue());
                            } else {
                                ins = editor.document.createElement( 'ins' );
                                ins.setText(this.getValue());
                                element.append(ins);
                            }
						}
					}
				]
			},
            /*
			// Definition of the Advanced Settings dialog tab (page).
			{
				id: 'tab-adv',
				label: 'Advanced Settings',
				elements: [
					{
						// Another text field for the abbr element id.
						type: 'text',
						id: 'id',
						label: 'Id',

						// Called by the main setupContent method call on dialog initialization.
						setup: function( element ) {
							this.setValue( element.getAttribute( "id" ) );
						},

						// Called by the main commitContent method call on dialog confirmation.
						commit: function ( element ) {
							var id = this.getValue();
							if ( id )
								element.setAttribute( 'id', id );
							else if ( !this.insertMode )
								element.removeAttribute( 'id' );
						}
					}
				]
			}*/
		],

		// Invoked when the dialog is loaded.
		onShow: function() {
            
			// Get the selection from the editor.
			var selection = editor.getSelection();

			// Get the element at the start of the selection.
			var element = selection.getStartElement();

			// Get the <abbr> element closest to the selection, if it exists.
			if ( element )
				element = element.getAscendant(function(el) {
                    if (el.$ instanceof HTMLElement && el.$.nodeName.toLowerCase() == 'span') {
                        return el.$.getAttribute('class') == 'subst';
                    }
                    return false;    
                }, true);

            
			// Create a new <abbr> element if it does not exist.
			if ( !element || element.getName() != 'span' ) {
				element = editor.document.createElement( 'span' );
                element.setAttribute('class', 'subst');
                
				// Flag the insertion mode for later use.
				this.insertMode = true;
			}
			else
				this.insertMode = false;

			// Store the reference to the <abbr> element in an internal property, for later use.
			this.element = element;

			// Invoke the setup methods of all dialog window elements, so they can load the element attributes.
			if ( !this.insertMode )
				this.setupContent( this.element );
		},

		// This method is invoked once a user clicks the OK button, confirming the dialog.
		onOk: function() {
            
			// The context of this function is the dialog object itself.
			// http://docs.ckeditor.com/#!/api/CKEDITOR.dialog
			var dialog = this;
            
			// Create a new <abbr> element.
			var abbr = this.element;

			// Invoke the commit methods of all dialog window elements, so the <abbr> element gets modified.
			this.commitContent( abbr );

			// Finally, if in insert mode, insert the element into the editor at the caret position.
			if ( this.insertMode )
				editor.insertElement( abbr );
		}
	};
});
