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

import { basicMessageComponents } from "./basicMessageComponents"

/**
 * This module contains interfaces that represent responses to a GET value request.
 */
export module valueResponseFormats {

    /**
     * Represents an answer to a value get request.
     *
     * HTTP GET http://www.knora.org/values/valueIRI
     */
    export interface valueResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the value's type
         */
        valuetype: basicMessageComponents.KnoraIRI;

        /**
         * The user's permissions on the value
         */
        rights: basicMessageComponents.KnoraRights;

        /**
         * The value's value
         */
        value: basicMessageComponents.knoraValue;

    }

    /**
     * Represents an item in the list of value versions.
     */
    interface valueVersionItem {

        /**
         * The value's IRI
         */
        valueObjectIri: basicMessageComponents.KnoraIRI;

        /**
         * The date when the value was created
         */
        valueCreationDate: string;

        /**
         * The value's previous version (its value IRI).
         * This member is not set for the first version of the value since it has no predecessor.
         */
        previousValue?: basicMessageComponents.KnoraIRI;

    }

    /**
     * Represents the answer to a value version request.
     *
     * HTTP GET to http://host/v1/values/history/resourceIRI/propertyTypeIRI/valueIRI
     */
    export interface valueVersionsResponse extends basicMessageComponents.basicResponse {

        /**
         * The versions of this value
         */
        valueVersions:Array<valueVersionItem>;

    }

    /**
     * Represents a link value.
     */
    interface linkValue {

        /**
         * The IRI of the source object
         */
        subjectIri: basicMessageComponents.KnoraIRI;

        /**
         * The IRI of the linking property type
         */
        predicateIri: basicMessageComponents.KnoraIRI;

        /**
         * The IRI of the target object
         */
        objectIri:basicMessageComponents.KnoraIRI;

        /**
         * Number of instances of this link
         */
        referenceCount: number;

    }

    /**
     * Represents the answer to a link request.
     *
     * HTTP GET to http://host/links/sourceObjectIRI/linkingPropertyIRI/targetObjectIRI
     *
     */
    export interface linkResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the type of the linking property
         */
        valuetype:basicMessageComponents.KnoraIRI;

        /**
         * The user's permissions on the link
         */
        rights:basicMessageComponents.KnoraRights;

        /**
         * Description of the link
         */
        value: linkValue

    }


}