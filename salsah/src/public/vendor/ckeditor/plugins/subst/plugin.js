/**
 * Copyright (c) 2014, CKSource - Frederico Knabben. All rights reserved.
 * Licensed under the terms of the MIT License (see LICENSE.md).
 *
 * Basic sample plugin inserting abbreviation elements into the CKEditor editing area.
 *
 * Created out of the CKEditor Plugin SDK:
 * http://docs.ckeditor.com/#!/guide/plugin_sdk_sample_1
 */

// Register the plugin within the editor.
CKEDITOR.plugins.add( 'subst', {

	// Register the icons.
	icons: 'subst',

	// The plugin initialization logic goes inside this method.
	init: function( editor ) {

		// Define an editor command that opens our dialog window.
		editor.addCommand( 'subst', new CKEDITOR.dialogCommand( 'substDialog' ) );

		// Create a toolbar button that executes the above command.
		editor.ui.addButton( 'Subst', {

			// The text part of the button (if available) and the tooltip.
			label: 'Insert Substitution',

			// The command to execute on click.
			command: 'subst',

			// The button placement in the toolbar (toolbar group name).
			toolbar: 'insert'
		});


		// Register our dialog file -- this.path is the plugin folder path.
		CKEDITOR.dialog.add( 'substDialog', this.path + 'dialogs/subst.js' );
	}
});
