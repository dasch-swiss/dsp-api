/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import { basicMessageComponents } from "./basicMessageComponents"

/**
 * This module contains interfaces that represent the answers to a DELETE request of a value or a resource.
 */
export module deleteResponseFormats {

    /**
     * Represents the answer to a value delete request.
     *
     * HTTP DELETE to http://host/v1/values/valueIRI?deleteComment=String
     *
     */
    export interface deleteValueResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the value that has been marked as deleted.
         */
        id: basicMessageComponents.KnoraIRI;

    }

    /**
     * Represents the answer to a resource delete request.
     *
     * HTTP DELETE to http://host/v1/resources/resourceIRI?deleteComment=String
     */
    export interface deleteResourceResponse extends basicMessageComponents.basicResponse {

        /**
         * The IRI of the resource that has been marked as deleted.
         */
        id: basicMessageComponents.KnoraIRI;

    }


}
