/**
 * Copyright (c) 2014, CKSource - Frederico Knabben. All rights reserved.
 * Licensed under the terms of the MIT License (see LICENSE.md).
 *
 * Basic sample plugin inserting current date and time into the CKEditor editing area.
 *
 * Created out of the CKEditor Plugin SDK:
 * http://docs.ckeditor.com/#!/guide/plugin_sdk_intro
 */

'use strict';

// Register the plugin within the editor.
CKEDITOR.plugins.add( 'deletetag', {

	// Register the icons. They must match command names.
	icons: 'deletetag',

	// The plugin initialization logic goes inside this method.
	init: function( editor ) {
		
		var attributes = {'class': 'single'};
		var style = new CKEDITOR.style( { element : 'del', attributes : attributes } );
		style.type = CKEDITOR.STYLE_INLINE;

		editor.attachStyleStateChange( style, function( state ) {
			!editor.readOnly && editor.getCommand('applyDeleteTag').setState(state);
		} );

		// Define the editor command that inserts a timestamp.
		editor.addCommand( 'applyDeleteTag', {

			// Define the function that will be fired when the command is executed.
			exec: function( editor ) {	
				
				if(!style.checkActive(editor.elementPath(), editor)) {
					style.apply(editor.document);	
				} else {
					style.remove(editor.document);
				}
			}
		});

		
		// Create the toolbar button that executes the above command.
		editor.ui.addButton( 'Deletetag', {
			label: 'Apply Delete Tag',
			command: 'applyDeleteTag'
		});
	}
});

