/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
