
import {Basic} from "./basic";

export module ResourcesResponse {

    /**
     * Represents a value.
     */
    interface Value {
        /**
         * Iri of the value.
         */
        "@id": Basic.KnoraInstanceIri;

        /**
         * Class of the value.
         */
        "@type": Basic.KnoraEntityIri;

        /**
         * Assertions about the value.
         *
         * In case of a link value, this may again contain a resource as a nested structure.
         */
        [valueHasIri: string]: number | string | boolean | Resource;

    }

    /**
     * Represents an resource (instance).
     */
    interface Resource {
        /**
         * IRI of the resource.
         */
        "@id": Basic.KnoraInstanceIri;

        /**
         * IRI of the class of this resource.
         */
        "@type": Basic.KnoraEntityIri;

        /**
         * http://schema.org/name of this resource (corresponds to rdfs:label).
         */
        "http://schema.org/name": string;

        /**
         * Properties of this resource
         */
        [propertyIri: string]: Value | Array<Value> | string;
    }

    /**
     * Represents one or more resources (instances).
     *
     * This is a generic response format used in several routes (resource request, search etc.).
     *
     */
    export interface ResourcesSequence {
        /**
         * ResourcesSequence response format corresponds to schema.org/ItemList
         */
        "@type": "http://schema.org/ItemList";

        /**
         * A sequence of resources.
         */
        "http://schema.org/itemListElement": Resource | Array<Resource>;

        /**
         * Length of the sequence of resources.
         */
        "http://schema.org/numberOfElements": number;
    }

}