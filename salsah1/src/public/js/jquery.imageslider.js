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
 * @package jQuery
 *
 */
(function($){

    var methods = {
        init: function(options) {
            return this.each(function() {
                var $this = $(this);
                var localdata = {
                    settings: {
                        slider_width: '400px',
                        slider_height: '130px',
                        image_width: '60px',
                        image_height: '90px',
                        images: [], // array of image urls
                        min_speed: 1,
                        max_speed: 9,
                        hover_cb: function() {}, // cb for image hover event
                        hover_lost_cb: function() {}, // cb when image hover is lost
                        clicked_cb: function() {} // cb for image click event
                    }
                };

                if (typeof options === 'object') $.extend(localdata.settings, options);

                //
                // init procedure
                //

                var interval;

                // sliding function
                var slide = function(dir, pix_msec) {
                    var factor = (dir == 'right' ? -1 : 1);

                    if (interval !== undefined) clearInterval(interval);
                    interval = setInterval(function() {
                        var left = parseInt(slider_div.css('left'));
                        var new_left = left + (factor * pix_msec);

                        if (new_left > (-1 * (slider_div.width() - $this.width())) && new_left <= 0) {
                            slider_div.css({
                                left: new_left
                            });
                        }

                    }, 1);
                };

                // visible area
                $this.css({
                    width: localdata.settings.slider_width,
                    height: localdata.settings.slider_height,
                    overflowX: 'scroll',
                    overflowY: 'hidden',
                    position: 'relative',
                    border: '1px solid black'
                }).on('mouseover', function(event) {

                    mover_left.css({
                        display: 'block'
                    });

                    mover_right.css({
                        display: 'block'
                    });
                }).on('mouseout', function(event) {

                    mover_left.css({
                        display: 'none'
                    });

                    mover_right.css({
                        display: 'none'
                    });

                }).addClass('imageslider');

                // image slider div
                var slider_div = $('<div>').css({
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    height: localdata.settings.image_height,
                    width: ((parseInt(localdata.settings.image_width) + 5) * localdata.settings.images.length) + 'px'
                }).addClass('slider_div').appendTo($this);

                //alert(localdata.settings.images.length);

                var create_image = function(src) {
                    $('<div>').append(
                        $('<img>', {src: src}).css({
                            width: '100%',
                            height: '100%'
                        })
                    ).css({
                        'float': 'left',
                        height: localdata.settings.image_height,
                        width: localdata.settings.image_width,
                        marginRight: '5px'
                    }).on('mouseover', function(event){
                        localdata.settings.hover_cb($(this), event);
                        $(this).on('mouseout', function() {
                            localdata.settings.hover_lost_cb($(this), event);
                            $(this).off('mouseout');
                        });
                    }).on('click', function(event) {
                        localdata.settings.clicked_cb($(this), event);
                    }).appendTo(slider_div);

                };

                // images within slider div
                var timer_id;
                for (var i = 0; i < localdata.settings.images.length; i++) {
                    // timer_id = setTimeout(function() {
                    create_image(localdata.settings.images[i]);
                    // }, 1);
                }

                // create areas for moving the slider
                var mover_right = $('<div>').css({
                    position: 'absolute',
                    right: 0,
                    top: 0,
                    height: '100%',
                    width: '25%',
                    backgroundColor: '#000000',
                    opacity: 0.4,
                    display: 'none'
                }).addClass('moverRight').on('mousemove', function(event) {
                    // determine speed
                    var x_pos = event.pageX - $this.offset().left - (0.75 * $this.width());
                    var speed_fac = x_pos/$(this).width();
                    //window.status = speed_fac;

                    var speed = parseInt(speed_fac * localdata.settings.max_speed);
                    if (speed < localdata.settings.min_speed) {
                        speed = localdata.settings.min_speed;
                    }

                    slide('right', speed);

                    $(this).on('mouseout', function(event) {
                        clearInterval(interval);
                        $(this).off('mouseout');
                    });
                }).appendTo($this);

                var mover_left = $('<div>').css({
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    height: '100%',
                    width: '25%',
                    backgroundColor: '#000000',
                    opacity: 0.4,
                    display: 'none'
                }).on('mousemove', function(event) {
                    // determine speed
                    var x_pos = event.pageX - $this.offset().left;
                    var speed_fac = 1 -(x_pos/$(this).width());
                    //window.status = speed_fac;

                    var speed = parseInt(speed_fac * localdata.settings.max_speed);
                    if (speed < localdata.settings.min_speed) {
                        speed = localdata.settings.min_speed;
                    }

                    slide('left', speed);

                    $(this).on('mouseout', function(event) {
                        clearInterval(interval);
                        $(this).off('mouseout');
                    });
                }).appendTo($this);


                $this.data('localdata', localdata); // initialize a local data object which is attached to the DOM object
            });
        },

        myfunc: function() {
            return this.each(function() {
                var $this = $(this);
                var localdata = $this.data('localdata');

            });
        }
    };

    /**
     * @memberOf jQuery.fn
     */
    $.fn.imageslider = function(method) {
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

