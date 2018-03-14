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
 * @author Lukas Rosenthaler <lukas.rosenthaler@unibas.ch>
 * @package jqplugins
 *
 * This plugin creates an edit-form for the properties of a resource
 *
 * <pre>
 *   <em>Title:</em><div class="propedit" data-propname="title" />
 *   <em>Autor:</em><div class="propedit" data-propname="author" />
 * </pre>
 *
 * <pre>
 *   <script type="text/javascript">
 *     $('div.propedit').propedit(resdata, propdata);
 *   </script>
 * </pre>
 */
(function( $ ) {

	var methods = {
		init: function(options) {
			return this.each(function() {
				var $this = $(this);
				var localdata = {};
				localdata.settings = {'value': 1};
				$.extend(localdata.settings, options);
				
				$this.append(String(localdata.settings.value));
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});			
		},
		
		edit: function(options) { // $(element).regions('init', {settings: here,...});
			return this.each(function() {
				var $this = $(this);
				var i;
				var localdata = {};
				localdata.settings = {
				};
				$.extend(localdata.settings, options);

				$this.append($('<input>').attr({'type': 'file', 'accept': 'image/*|video/*'}).addClass('location').addClass('fileToUpload').change(function(event) {
					//
					// a new file has been selected
					//
					$this.find('.thumbNail').remove(); // remove thumbail, if there is already one there!
					$this.find('.fileData').remove(); // remove thumbail, if there is already one there!
					
					var file = this.files[0];
					if (file) {
						var fileSize = 0;
						if (file.size > 1024 * 1024) {
							fileSize = (Math.round(file.size * 100 / (1024 * 1024)) / 100).toString() + 'MB';
						}
						else {
							fileSize = (Math.round(file.size * 100 / 1024) / 100).toString() + 'KB';
						}
						$this.append($('<div>').addClass('fileName').text(' Name: ' + file.name))
						.append($('<div>').addClass('fileType').text(' Size: ' + fileSize))
						.append($('<div>').addClass('fileSize').text(' Type: ' + file.type))
						.append($('<div>').addClass('progressNumber'))
						.append($('<input>').attr({'type': 'button'}).addClass('uploadButton').val('UPLOAD').click(function(event) {
							//
							// upload clicked....
							//
							//alertObjectContent($this.find('.fileToUpload').get(0).files[0]);
							var fd = new FormData();
							//fd.append('fileToUpload', $this.find('.fileToUpload').get(0).files[0]);
							//fd.append('MAX_FILE_SIZE', '268435456');

							fd.append('file', $this.find('.fileToUpload').get(0).files[0]);

							$.ajax({
								type:'POST',
								url: SIPI_URL + "/make_thumbnail",
								data: fd,
								cache: false,
								contentType: false,
								processData: false,
								success:function(data) {

									$this.find('.fileName').remove();
									$this.find('.fileType').remove();
									$this.find('.fileSize').remove();
									$this.find('.progressNumber').remove();
									$this.find('.uploadButton').remove();

									$this.append($('<div>').addClass('thumbNail')
										.append($('<img>', {src: data.preview_path}))
										.append($('<br>'))
										.append(data.original_filename)
									);

									//$this.append($('<input>').attr({'type': 'hidden'}).addClass('fileData').val(event.target.responseText));


									localdata.sipi_response = data;
								},
								error: function(jqXHR, textStatus, errorThrown) {
									if (errorThrown !== undefined && jqXHR !== undefined && jqXHR.responseJSON !== undefined) {
										alert("Sipi returned error " + errorThrown + " with message: " + jqXHR.responseJSON['message']);
									} else {
										alert("Call to Sipi failed")
									}
								}
							});

							/*var xhr = new XMLHttpRequest();
							
							//
							// on progress...
							//
							xhr.upload.addEventListener('progress', function (event) {
								if (event.lengthComputable) {
									var percentComplete = Math.round(event.loaded * 100 / event.total);
									$this.find('.progressNumber').text('Upload in progress: ' + percentComplete.toString() + '%');
								}
								else {
									$this.find('.progressNumber').text('Upload in progress: ??%');
								}
							}, false);
							
							//
							// on complete
							//
							xhr.addEventListener('load', function(event) {
								if (event.target.status != 200) {
									alert(event.target.statusText + '\n' + event.target.responseText);
									return;
								}

								var res;
								try {
									res = $.parseJSON(event.target.responseText);
								}
								catch(e) {
									alert(event.target.responseText);
									return;
								}
								if (res.CODE != 'OK') {
									alert(res.MSG);
									return;
								}

								$this.find('.fileName').remove();
								$this.find('.fileType').remove();
								$this.find('.fileSize').remove();
								$this.find('.progressNumber').remove();
								$this.find('.uploadButton').remove();
								$this.append($('<div>').addClass('thumbNail')
									.append($('<img>', {src: SITE_URL + '/core/showthumb.php?project_id=' + SALSAH.userdata.active_project + '&file=' + res.tmp_thumb_path}))
									.append($('<br>'))
									.append(res.orig_fname)
								);
								$this.append($('<input>').attr({'type': 'hidden'}).addClass('fileData').val(event.target.responseText));
							}, false);
							
							//
							// on an error...
							//
							xhr.addEventListener('error', function(event) {
							  alert("There was an error attempting to upload the file.");
							}, false);
							
							//
							// on cancel...
							//
							xhr.addEventListener('abort', function(event) {
							  alert("The upload has been canceled by the user or the browser dropped the connection.");
							}, false);

							xhr.withCredentials = true;

							xhr.open('POST', SIPI_URL + "/make_thumbnail");
							xhr.send(fd);*/
						}));
					}
				}));
				$this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		/*===========================================================================*/
		
		value: function(options) {
			var $this = $(this);
			var localdata = $this.data('localdata');

			/*var jsonstr = $(this).find('.fileData').val();
			var res;
			try {
				res = $.parseJSON(jsonstr);
			}
			catch(event) {
				alert(event.target.responseText);
				return;
			}*/

			if (localdata.sipi_response !== undefined) {
				return localdata.sipi_response;
			} else {
				return false;
			}
		},
		/*===========================================================================*/

		anotherMethod: function(options) {
			return this.each(function(){
				var $this = $(this);
				var localdata = $this.data('localdata');
			});
		},
		/*===========================================================================*/
	}
	
	
	$.fn.location = function(method) {
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