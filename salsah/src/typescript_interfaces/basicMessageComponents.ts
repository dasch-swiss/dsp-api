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

export module basicMessageComponents {

    /**
     * Basic members of the Knora API V1 response format.
     */
    export interface basicResponse {
        /**
         * Knora status code
         */
        status:number;

        /**
         * The current user's data
         */
        userdata:userdata;
    }

    /**
     * Represents a Knora project
     */
    interface projectItem {
        /**
         * Path to the project's files
         */
        basepath:string;

        /**
         * Project's short name
         */
        shortname:string;

        /**
         * Description of the project
         */
        description:string;

        /**
         * The project's logo
         */
        logo:string;

        /**
         * The project's IRI
         */
        id:string;

        /**
         * Keywords describing the project
         */
        keywords:string;

        /**
         * obsolete
         */
        rights:string;

        /**
         * Project's long name
         */
        longname:string;
    }

    /**
     * Represents the current user's data
     */
    interface userdata {
        /**
         * User's email address
         */
        email:string;

        /**
         * User's unique name
         */
        username:string;

        /**
         * User's first name
         */
        firstname:string;

        /**
         * User's last name
         */
        lastname:string;

        /**
         * List of project descriptions the user is member of
         */
        projects_info:Array<projectItem>;

        /**
         * User's IRI
         */
        user_id:string;

        /**
         * User's preferred language
         */
        lang:string;

        /**
         * User's active project
         */
        active_project:string;

        /**
         * Session token
         */
        token:string;

        /**
         * List of project IRIs the user is member of
         */
        projects:Array<string>;

        /**
         * obsolete
         */
        password:string;
    }

    /**
     * Represents a rich text value
     */
    interface richtext {
        /**
         * Mere string representation
         */
        utf8str:string;

        /**
         * Markup information in standoff format
         */
        textattr:string;

        /**
         * References to Knora resources from the text
         */
        resource_reference:Array<string>
    }

    /**
     * Represents a date value
     */
    interface date {
        /**
         * Start date in string format
         */
        dateval1:string;

        /**
         * End end in string format
         */
        dateval2:string;

        /**
         * Calendar used
         */
        calendar:string;

    }

    /**
     * Represents an interval value
     */
    interface interval {

        /**
         * Begin of the interval in seconds
         */
        timeval1: number;

        /**
         * End ofg the interval in seconds
         */
        timeval2: number;

    }

    /**
     * Describes a Knora Value.
     * Either a simple type or a complex represented by an interface.
     */
    export type knoraValue = number|string|boolean|richtext|interval|date;

    export interface richtextValue {

        /**
         * A richtext value
         */
        richtext_value: richtext;

    }


    export interface linkValue {

        /**
         * A link to another Knora resource. Value must be a Knora IRI.
         */
        link_value: string;

    }


    export interface integerValue {

        /**
         * An integer value
         */
        int_value: number;

    }


    export interface decimalValue {

        /**
         * A decimal value (floating point)
         */
        decimal_value: number;

    }

    export interface booleanValue {

        /**
         * A boolean value
         */
        boolean_value: boolean;

    }

    export interface uriValue {

        /**
         * A URI value
         */
        uri_value: string;

    }

    export interface dateValue {

        /**
         * A date value. This must have the following format: (GREGORIAN|JULIAN):YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]
         * E.g. an exact date like GREGORIAN:2015-12-03 or a period like GREGORIAN:2015-12-03-2015-12-04.
         * Dates may also have month or year precision, e.g. GREGORIAN:2015-12 (the whole month of december) or GREGORIAN:2015 (the whole year 2015).
         */
        date_value: string;
    }

    export interface colorValue {

        /**
         * A color value
         * Value must be a hexadecimal RGB color code, e.g. "#4169E1"
         */
        color_value: string;
    }

    export interface geometryValue {

        /**
         * A geometry value representing a region on a 2D surface.
         */
        geom_value: string;
    }


    export interface hierarchicalListValue {

        /**
         * A list node IRI
         */
        hlist_value: string;

    }

    export interface intervalValue {

        /**
         * An interval value consisting of two time values
         */
        interval_value: Array<number>;

    }


    export interface geonameValue {

        /**
         * A geoname value
         */
        geoname_value: string;

    }

    /**
     * Describes a file value (for GUI-case)
     */
    export interface createOrChangeFileValueRequest {

        /**
         * Describes a file value (for GUI-case)
         */
        file: {

            /**
             * The file's original name
             */
            originalFilename: string;

            /**
             * The original mime type of the file
             */
            originalMimeType: string;

            /**
             * The file's temporary name
             */
            filename: string;

        }
    }

    /**
     * Binary representation of a resource (location)
     */
    export interface locationItem {
        /**
         * Duration of a movie or an audio file
         */
        duration:number;

        /**
         * X dimension of an image representation
         */
        nx:number;

        /**
         * Y dimension of an image representation
         */
        ny:number;

        /**
         * Path to the binary representation
         */
        path:string;

        /**
         * Frames per second (movie)
         */
        fps:number;

        /**
         * Format of the binary representation
         */
        format_name:string;

        /**
         * Original file name of the binary representation (before import to Knora)
         */
        origname:string;

        /**
         * Protocol used
         */
        protocol:protocolOptions;
    }

    /**
     * Represents how a binary representation (location) can be accessed.
     * Either locally stored (file) or referenced from an external location (url)
     */
    type protocolOptions = "file" | "url";

}