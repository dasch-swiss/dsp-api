
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
         * IRI of the resource instance.
         */
        "@id": Basic.KnoraInstanceIri;

        /**
         * IRI of the class of this resource instance.
         */
        "@type": Basic.KnoraEntityIri;

        /**
         * Short description of this resource (corresponds to rdfs:label).
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
         * ResourcesSequence response format corresponds to http://schema.org/ItemList
         */
        "@type": "http://schema.org/ItemList";

        /**
         * A resource or a sequence of resources (instances).
         */
        "http://schema.org/itemListElement": Resource | Array<Resource>;

        /**
         * Length of the sequence of resources.
         */
        "http://schema.org/numberOfElements": number;
    }

}