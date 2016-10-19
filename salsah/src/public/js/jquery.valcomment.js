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

(function( $ ) {
	
    var save_icon = new Image();
    save_icon.src = SITE_URL + '/app/icons/16x16/save.png';

    var cancel_icon = new Image();
    cancel_icon.src = SITE_URL + '/app/icons/16x16/delete.png';

	var setup_mouseevents = function($this, localdata) {
		$this.on('mouseout.valcomment', function(event){
			event.preventDefault();
			localdata.ele.css({'display': 'none'});
			$this.off('mousemove.valcomment');
			return false;
		});
		$this.on('mouseover.valcomment', function(event){
			event.preventDefault();
			localdata.ele.css({'display': 'block', opacity: '1', 'left': (event.pageX + 10) + 'px', 'top': (event.pageY + 10) + 'px'});
			$this.on('mousemove.valcomment', function(event){
				event.preventDefault();
				localdata.ele.css({'left': (event.pageX + 10) + 'px', 'top': (event.pageY + 10) + 'px'});
				return false;
			});					
			return false;
		});
	}

    var methods = {
		init: function(options) {
            return this.each(function() {
                var $this = $(this);
                var localdata = {};
                localdata.settings = {
                    value_id: -1, // negative means no restype_id known
					comment: null
                };
                localdata.ele = {};
				/*
                var pos = $this.position();
                if (!$this.hasClass('propedit_frame')) {
                    pos.top += $this.closest('.propedit_frame').scrollTop() + $this.outerHeight(); // was closest('tabContent')
                }
				*/
                $.extend(localdata.settings, options);
				$this.css({'cursor': 'pointer'});
				localdata.ele = $('<div>').addClass('value_comment tooltip').css({'display': 'none', opacity: '1', 'position': 'fixed', 'z-index': 1000}).text(localdata.settings.comment).appendTo('body');
				if (localdata.settings.comment != null) {
					setup_mouseevents($this, localdata);
				}
				$this.on('click', function(event){
					$this.valcomment('edit');
				});
                $this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
			});
		},
		
		edit: function() {
            return this.each(function(){
                var $this = $(this);
                var localdata = $this.data('localdata');
				$this.off('.valcomment');
				localdata.ele.empty();
				var btn_toolbar = $('<div>').addClass('btn-toolbar');
				var btn_group = $('<div>').addClass('btn-group btn-group-xs');



				localdata.ele.append($('<textarea>').append(localdata.settings.comment))
					.append(btn_toolbar.append(btn_group));

				btn_group.append($('<button>').addClass('btn btn-default btn-xs').append(
					$('<span>').addClass('glyphicon glyphicon-save'))
					.attr({title: strings._save})
					.on('click', function(event) {
						localdata.settings.comment = localdata.ele.find('textarea').val();
						SALSAH.ApiPut('values/' + encodeURIComponent(localdata.settings.value_id),
							{
								project_id: SALSAH.userdata.projects[0], // TODO: how to get this information in a correct way? https://github.com/dhlab-basel/Knora/issues/118
								comment: localdata.settings.comment
							},
							function(data) {
								if (data.status == ApiErrors.OK) {
									localdata.ele.empty().css({'display': 'none'});
									localdata.ele.text(localdata.settings.comment);
									setup_mouseevents($this, localdata);
								}
								else {
									alert(data.errormsg);
								}
							}
						);
						/*
						 $.__post(SITE_URL + '/ajax/update_value_comment.php', {
						 val_id: localdata.settings.value_id,
						 comment: localdata.settings.comment
						 }, function(data){
						 localdata.ele.empty().css({'display': 'none'});
						 localdata.ele.text(localdata.settings.comment);
						 setup_mouseevents($this, localdata);
						 }, 'json');
						 */
					}));
				btn_group.append(
					$('<button>').addClass('btn btn-default btn-xs')
						.attr({title: strings._cancel})
						.append($('<span>').addClass('glyphicon glyphicon-remove'))
						.on('click', function (event) {
							localdata.ele.empty().css({'display': 'none'});
							localdata.ele.text(localdata.settings.comment);
							setup_mouseevents($this, localdata);
						})
				);
                btn_group.append(
                    $('<button>').addClass('btn btn-default btn-xs')
                        .attr({title: strings._delete})
                        .append($('<span>').addClass('glyphicon glyphicon-trash'))
                        .on('click', function (event) {
                        SALSAH.ApiDelete('valuecomments/' + encodeURIComponent(localdata.settings.value_id),
                            {
                                project_id: SALSAH.userdata.projects[0], // TODO: how to get this information in a correct way? https://github.com/dhlab-basel/Knora/issues/118
                            },
                            function(data) {
                                if (data.status == ApiErrors.OK) {
                                    localdata.ele.empty().css({'display': 'none'});
                                }
                                else {
                                    alert(data.errormsg);
                                }
                            }
                        );
                    }));

//				$('<textarea>').append(localdata.settings.comment).appendTo(localdata.ele);
//				$('<br>').appendTo(localdata.ele);
				/*
				$('<img>', {src: save_icon.src, title: strings._save}).on('click', function(event) {
					localdata.settings.comment = localdata.ele.find('textarea').val();
					SALSAH.ApiPut('values/' + localdata.settings.value_id,
						{comment: localdata.settings.comment},
						function(data) {
							if (data.status == ApiErrors.OK) {
								localdata.ele.empty().css({'display': 'none'});
								localdata.ele.text(localdata.settings.comment);
								setup_mouseevents($this, localdata);
							}
							else {
								alert(data.errormsg);
							}
						}
					);
					/*
					$.__post(SITE_URL + '/ajax/update_value_comment.php', {
						val_id: localdata.settings.value_id,
						comment: localdata.settings.comment
					}, function(data){
						localdata.ele.empty().css({'display': 'none'});
						localdata.ele.text(localdata.settings.comment);
						setup_mouseevents($this, localdata);						
					}, 'json');
					*/
				//}).appendTo(localdata.ele);
/*
				$('<img>', {src: cancel_icon.src, title: strings._cancel}).on('click', function(event){
					localdata.ele.empty().css({'display': 'none'});
					localdata.ele.text(localdata.settings.comment);
					setup_mouseevents($this, localdata);						
				}).appendTo(localdata.ele);
				*/
				var offs = $this.offset();
				localdata.ele.css({'display': 'block'});
				localdata.ele.css({'left': (offs.left + 10) + 'px', 'top': (offs.top + 10) + 'px'});
            });			
		}
	};

    $.fn.valcomment = function(method) {
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