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

export module addValueFormats {

    /**
     * Basic components of an add value request
     */
    interface addValueRequestBase {

        /**
         * The IRI of the project the new value belongs to.
         */
        project_id:string;

        /**
         * The IRI of the property type the new value belongs to.
         */
        prop:string;

        /**
         * The IRI of the resource the new value is attached to.
         */
        res_id: string;

        /**
         * Comment on the value to be created.
         */
        comment?:string;
    }

    /**
     * Represents a request to add a value of type richtext to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addRichtextValueRequest extends addValueRequestBase, basicMessageComponents.richtextValue {}

    /**
     * Represents a request to add a value of type link to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addLinkValueRequest extends addValueRequestBase, basicMessageComponents.linkValue {}

    /**
     * Represents a request to add a value of type integer to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addIntegerValueRequest extends addValueRequestBase, basicMessageComponents.integerValue {}

    /**
     * Represents a request to add a value of type decimal to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addDecimalValueRequest extends addValueRequestBase, basicMessageComponents.decimalValue {}

    /**
     * Represents a request to add a value of type boolean to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addBooleanValueRequest extends addValueRequestBase, basicMessageComponents.booleanValue {}

    /**
     * Represents a request to add a value of type URI to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addUriValueRequest extends addValueRequestBase, basicMessageComponents.uriValue {}

    /**
     * Represents a request to add a value of type date to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addDateValueRequest extends addValueRequestBase, basicMessageComponents.dateValue {}

    /**
     * Represents a request to add a value of type color to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addColorValueRequest extends addValueRequestBase, basicMessageComponents.colorValue {}

    /**
     * Represents a request to add a value of type geometry to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addGeometryValueRequest extends addValueRequestBase, basicMessageComponents.geometryValue {}

    /**
     * Represents a request to add a value of type hierarchical list to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addHierarchicalListValueRequest extends addValueRequestBase, basicMessageComponents.hierarchicalListValue {}

    /**
     * Represents a request to add a value of type interval list to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addIntervalValueRequest extends addValueRequestBase, basicMessageComponents.intervalValue {}


    /**
     * Represents a request to add a value of type geoname list to a resource.
     *
     * HTTP POST request to http://www.knora.org/v1/values
     *
     */
    export interface addGeonameValueRequest extends addValueRequestBase, basicMessageComponents.geonameValue {}

    /**
     * Represents the response to an add value request.
     */
    export interface addValueResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the new value
         */
        id: string;

        /**
         * The value that has been added
         */
        value: basicMessageComponents.knoraValue;

        /**
         * The value's comment (if given)
         */
        comment:string;

        /**
         * The user's permissions on the new value
         */
        rights: number;

    }



}