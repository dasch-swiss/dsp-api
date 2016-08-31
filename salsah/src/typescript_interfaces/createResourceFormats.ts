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

/**
 * This module contains interfaces that represent requests to create a new resource
 * and the response to such a request.
 */
export module createResourceFormats {

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
                string: basicMessageComponents.KnoraIRI;
            }

            /**
             * The IRI of the person that created the value
             */
            person_id: {
                string: basicMessageComponents.KnoraIRI;
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
                string: basicMessageComponents.KnoraIRI;
            }
        }

        /**
         * The property value's IRI
         */
        id: basicMessageComponents.KnoraIRI;
    }

    /**
     * Represents a resource creation request without providing information about a digital representation.
     *
     * This definition describes the JSON to be sent as the HTTP body in a POST request to http://host/v1/resources
     *
     * However, this format may part of a HTTP Multipart request (in that case, do not set the content type to "application/json").
     *
     */
    export interface createResourceWithoutRepresentationRequest {

        /**
         * The IRI of the resource class the new resource belongs to.
         */
        restype_id: basicMessageComponents.KnoraIRI;

        /**
         * A map of property types to property values to be assigned to the new resource.
         * Each property type requests a specific value type. These assignments are defined in the project ontologies.
         *
         */
        properties:{
            [index:string]:Array<basicMessageComponents.richtextValue>|Array<basicMessageComponents.linkValue>|Array<basicMessageComponents.integerValue>
                |Array<basicMessageComponents.decimalValue>|Array<basicMessageComponents.booleanValue>|Array<basicMessageComponents.uriValue>
                |Array<basicMessageComponents.dateValue>|Array<basicMessageComponents.colorValue>|Array<basicMessageComponents.geometryValue>
                |Array<basicMessageComponents.hierarchicalListValue>|Array<basicMessageComponents.intervalValue>|Array<basicMessageComponents.geonameValue>;
        }

        /**
         * The IRI of the project the new resource belongs to.
         */
        project_id: basicMessageComponents.KnoraIRI;

        /**
         * The label describing the new resource.
         */
        label: string;

    }

    /**
     * Represents a resource creation request providing a digital representation (GUI-case).
     *
     * This definition describes the JSON to be sent as the HTTP body in a POST request to http://host/v1/resources
     */
    export interface createResourceWithRepresentationRequest extends createResourceWithoutRepresentationRequest, basicMessageComponents.createOrChangeFileValueRequest {}

    /**
     * Represents the answer to a create resource request.
     */
    export interface createResourceResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the new resource
         */
        res_id: basicMessageComponents.KnoraIRI;

        /**
         * A map of property types to property values
         */
        results: {
            [index:string]:Array<resultItem>
        }

    }


}