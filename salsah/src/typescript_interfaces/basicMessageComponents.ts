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
    export interface richtext {
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
    export interface date {
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
    export interface interval {

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
     * A richtext value
     */
    export interface richtextValue {

        richtext_value: basicMessageComponents.richtext;

    }

    /**
     * A link to another Knora resource. Value must be a Knora IRI.
     */
    export interface linkValue {

        link_value: string;

    }

    /**
     * An integer value
     */
    export interface integerValue {

        int_value: number;

    }

    /**
     * A decimal value (floating point)
     */
    export interface decimalValue {

        decimal_value: number;

    }

    /**
     * A boolean value
     */
    export interface booleanValue {

        boolean_value: boolean;

    }

    /**
     * A URI value
     */
    export interface uriValue {

        uri_value: string;

    }

    /**
     * A date value. This must have the following format: (GREGORIAN|JULIAN):YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]
     * E.g. an exact date like GREGORIAN:2015-12-03 or a period like GREGORIAN:2015-12-03-2015-12-04.
     * Dates may also have month or year precision, e.g. GREGORIAN:2015-12 (the whole month of december) or GREGORIAN:2015 (the whole year 2015).
     */
    export interface dateValue {

        date_value: string;
    }

    /**
     * A color value
     * Value must be a hexadecimal RGB color code, e.g. "#4169E1"
     */
    export interface colorValue {

        color_value: string;
    }

    /**
     * A geometry value representing a region on a 2D surface.
     */
    export interface geometryValue {

        geom_value: string;
    }

    /**
     * A list node IRI
     */
    export interface hierarchicalListValue {

        hlist_value: string;

    }

    /**
     * An interval value consisting of two time values
     */
    export interface intervalValue {

        interval_value: Array<number>;

    }

    /**
     * A geoname value
     */
    export interface geonameValue {

        geoname_value: string;

    }

}