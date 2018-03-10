/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
 * This module contains interfaces that represent requests to change a value (add a new version to a value)
 * and the response to such a request.
 */
export module changeValueFormats {

    /**
     * Basic components of an add value request
     */
    interface changeValueRequestBase {

        /**
         * The IRI of the project the new value belongs to.
         */
        project_id:basicMessageComponents.KnoraIRI;

    }

    /**
     * Represents a request to change a value of type richtext.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeRichtextValueRequest extends changeValueRequestBase, basicMessageComponents.richtextValue {}

    /**
     * Represents a request to change a value of type link.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeLinkValueRequest extends changeValueRequestBase, basicMessageComponents.linkValue {}

    /**
     * Represents a request to change a value of type integer.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeIntegerValueRequest extends changeValueRequestBase, basicMessageComponents.integerValue {}

    /**
     * Represents a request to change a value of type decimal.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeDecimalValueRequest extends changeValueRequestBase, basicMessageComponents.decimalValue {}

    /**
     * Represents a request to change a value of type boolean.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeBooleanValueRequest extends changeValueRequestBase, basicMessageComponents.booleanValue {}

    /**
     * Represents a request to change a value of type URI.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeUriValueRequest extends changeValueRequestBase, basicMessageComponents.uriValue {}

    /**
     * Represents a request to change a value of type date.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeDateValueRequest extends changeValueRequestBase, basicMessageComponents.dateValue {}

    /**
     * Represents a request to change a value of type color.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeColorValueRequest extends changeValueRequestBase, basicMessageComponents.colorValue {}

    /**
     * Represents a request to change a value of type gepometry.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeGeometryValueRequest extends changeValueRequestBase, basicMessageComponents.geometryValue {}

    /**
     * Represents a request to change a value of type hierarchical list.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeHierarchicalListValueRequest extends changeValueRequestBase, basicMessageComponents.hierarchicalListValue {}

    /**
     * Represents a request to change a value of type interval.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeIntervalValueRequest extends changeValueRequestBase, basicMessageComponents.intervalValue {}


    /**
     * Represents a request to change a value of type geoname.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeGeonameValueRequest extends changeValueRequestBase, basicMessageComponents.geonameValue {}

    /**
     * Represents a request to change a value's comment without changing the value itself.
     *
     * HTTP PUT to http://host/v1/values/valueIRI
     *
     */
    export interface changeValueCommentRequest extends changeValueRequestBase {

        /**
         * Comment on the existing value
         */
        comment:string;

    }

    /**
     * Represents a request to change the file value (digital representation) of a resource (GUI-case).
     *
     * HTTP PUT to http://host/v1/filevalue/resourceIRI
     *
     */
    export interface changeFileValueRequest extends basicMessageComponents.createOrChangeFileValueRequest {}

    /**
     * Represents the response to a change value request.
     */
    export interface changeValueResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the new value
         */
        id: basicMessageComponents.KnoraIRI;

        /**
         * The value that has been added
         */
        value: basicMessageComponents.knoraValue;

        /**
         * The value's comment (if given)
         */
        comment:string | null;

        /**
         * The user's permissions on the new value
         */
        rights: basicMessageComponents.KnoraRights;

    }

    /**
     * Represents the answer to a file value change request.
     */
    export interface changeFileValueResponse extends basicMessageComponents.basicResponse {

        /**
         * Represents the new file values.
         */
        locations: Array<basicMessageComponents.locationItem>;


    }

}