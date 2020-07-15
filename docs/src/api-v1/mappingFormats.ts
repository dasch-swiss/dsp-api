/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import {basicMessageComponents} from "./basicMessageComponents"


/**
 * This module contains interfaces that represent the request to add a new mapping
 * and the response to such a request.
 */
export module mappingFormats {

    /**
     * Create a new mapping.
     *
     * HTTP POST http://host/v1/mapping
     *
     * This is the JSON part of a multipart request.
     * The part's name is "json".
     * The mapping must be sent as XML in another part with the name `xml`.
     *
     */
    export interface addMappingRequest {

        /**
         * The IRI of the project the mapping belongs to.
         */
        project_id:basicMessageComponents.KnoraIRI;

        /**
         * A label describing the mapping.
         */
        label:string;

        /**
         * A name that is going to be part of the mapping's IRI.
         * A mapping IRI consist of the project IRI, the segment "mappings"
         * and the `mappingName`.
         */
        mappingName:string;

    }

    export interface addMappingResponse extends basicMessageComponents.basicResponse {

        /**
         * The Iri of the new mapping.
         */
        mappingIRI:basicMessageComponents.KnoraIRI;

    }


}