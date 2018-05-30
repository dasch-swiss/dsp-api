import {Basic} from "./Basic";

/**
 * This module contains request and response formats for the creation of a mapping (XML to standoff).
 */
export module MappingFormats {

    /**
     * Create a new mapping.
     *
     * HTTP POST http://host/v2/mapping
     *
     * This is the JSON part of a multipart request.
     * The part's name is "json".
     * The mapping must be sent as XML in another part with the name `xml`.
     *
     * On success, a [[AddMappingResponse]] will be returned.
     *
     */
    export interface AddMappingRequest {

        /**
         * The name of the mapping.
         * The name will be part of the mapping's IRI.
         */
        "http://api.knora.org/ontology/knora-api/v2#mappingHasName": string;

        /**
         * The project the mapping belongs to.
         */
        "http://api.knora.org/ontology/knora-api/v2#attachedToProject": Basic.KnoraIri;

        /**
         * The mapping's label.
         */
        "http://www.w3.org/2000/01/rdf-schema#label": string;
    }

    /**
     * Response to a mapping creation request [[AddMappingRequest]].
     */
    export interface AddMappingResponse  {

        /**
         * IRI of the mapping that was created.
         */
        "@id": Basic.KnoraIri;

        /**
         * Type of the created mapping.
         */
        "@type": Basic.KnoraIri;

        /**
         * The project the mapping belongs to.
         */
        "http://api.knora.org/ontology/knora-api/v2#attachedToProject": Basic.KnoraIri;

        /**
         * The created mapping's label.
         */
        "http://www.w3.org/2000/01/rdf-schema#label": string;

    }


}
