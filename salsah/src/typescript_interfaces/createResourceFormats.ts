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

import {basicMessageComponents} from "./basicMessageComponents"

export module createResourceFormats {

    /**
     * A richtext value
     */
    interface richtextValue {

        richtext_value: basicMessageComponents.richtext;

    }

    /**
     * A link to another Knora resource. Value must be a Knora IRI.
     */
    interface linkValue {

        link_value: string;

    }

    /**
     * An integer value
     */
    interface integerValue {

        int_value: number;

    }

    /**
     * A decimal value (floating point)
     */
    interface decimalValue {

        decimal_value: number;

    }

    /**
     * A boolean value
     */
    interface booleanValue {

        boolean_value: boolean;

    }

    /**
     * A URI value
     */
    interface uriValue {

        uri_value: string;

    }

    /**
     * A date value. This must have the following format: (GREGORIAN|JULIAN):YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]
     * E.g. an exact date like GREGORIAN:2015-12-03 or a period like GREGORIAN:2015-12-03-2015-12-04.
     * Dates may also have month or year precision, e.g. GREGORIAN:2015-12 (the whole month of december) or GREGORIAN:2015 (the whole year 2015).
     */
    interface dateValue {

        date_value: string;
    }

    /**
     * A color value
     * Value must be a hexadecimal RGB color code "#4169E1", e.g. ""
     */
    interface colorValue {

        color_value: string;
    }

    /**
     * A geometry value representing a region on a 2D surface.
     */
    interface geometryValue {

        geom_value: string;
    }

    /**
     * A list node IRI
     */
    interface hierarchicalListValue {

        hlist_value: string;

    }

    /**
     * An interval value consisting of two time values
     */
    interface intervalValue {

        interval_value: Array<number>;

    }

    /**
     * A geoname value
     */
    interface geonameValue {

        geoname_value: string;

    }

    /**
     * Represents a property value in the response of a newly created resource.
     */
    interface resultItem {

        /**
         * The property's value
         */
        value:{
            /**
             * Text representation of the value
             */
            textval: {
                string: string;
            }

            /**
             * Set if value is of type integer value
             */
            ival?: {
                integer: number;
            }

            /**
             * Set if value is of type decimal value (floating point number)
             */
            dval?: {
                decimal: number;
            }

            /**
             * Set if value is of type date value.
             * Represents the start date.
             */
            dateval1?: {
                string: string;
            }

            /**
             * Set if value is of type date value.
             * Represents the end date.
             */
            dateval2?: {
                string: string;
            }

            /**
             * Set if value is of type date value.
             * Represents the precision of the start date.
             */
            dateprecision1?: {
                string: string;
            }

            /**
             * Set if value is of type date value.
             * Represents the precision of the end date.
             */
            dateprecision2?: {
                string: string;
            }

            /**
             * Set if value is of type date value.
             * Represents the date's calendar.
             */
            calendar?: {
                string: string;
            }

            /**
             * Set if value is of type interval value.
             * Represents the start of the interval.
             */
            timeval1?:{
                decimal: number;
            }

            /**
             * Set if value is of type interval value.
             * Represents the end of the interval.
             */
            timeval2?:{
                decimal: number;
            }

            /**
             * The IRI of the property type the value belongs to
             */
            property_id: {
                string: string;
            }

            /**
             * The IRI of the person that created the value
             */
            person_id: {
                string: string;
            }

            /**
             * The order of the value
             */
            order: {
                integer: number;
            }

            /**
             * The IRI of the resource the value belongs to.
             */
            resource_id: {
                string: string;
            }
        }

        /**
         * The property value's IRI
         */
        id: string;
    }

    /**
     * Represents a resource creation request without providing information about a digital representation.
     * However, this format may part of a HTTP Multipart request (in that case, do not set the content type to "application/json").
     *
     * This definition describes the JSON to be sent as the HTTP body in a POST request to http://www.knora.org/v1/resources
     *
     */
    export interface createResourceWithoutRepresentationRequest {

        /**
         * The IRI of the resource class the new resource belongs to.
         */
        restype_id: string;

        /**
         * A map of property types to property values to be assigned to the new resource.
         * Each property type requests a specific value type. These assignments are defined in the project ontologies.
         *
         */
        properties:{
            [index:string]:Array<richtextValue>|Array<linkValue>|Array<integerValue>
                |Array<decimalValue>|Array<booleanValue>|Array<uriValue>
                |Array<dateValue>|Array<colorValue>|Array<geometryValue>
                |Array<hierarchicalListValue>|Array<intervalValue>|Array<geonameValue>;
        }

        /**
         * The IRI of the project the new resource belongs to.
         */
        project_id: string;

        /**
         * The label describing the new resource.
         */
        label: string;

    }

    /**
     * Represents a resource creation request .
     *
     * This definition describes the JSON to be sent as the HTTP body in a POST request to http://www.knora.org/v1/resources
     */
    export interface createResourceWithRepresentationRequest extends createResourceWithoutRepresentationRequest {

        file: {

            originalFilename: string;

            originalMimeType: string;

            filename: string;

        }

    }

    export interface createResourceResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the new resource
         */
        res_id: string;

        /**
         * A map of property types to property values
         */
        results: {
            [index:string]:Array<resultItem>
        }

    }


}