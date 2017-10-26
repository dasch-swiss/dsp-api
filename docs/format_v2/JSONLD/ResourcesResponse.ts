import {Basic} from "./Basic";

/**
 * This module contains response formats for resource instances.
 */
export module ResourcesResponse {

    /**
     * This module contains resource response format definitions for API Schema V2 with value objects.
     */
    export module ApiV2WithValueObjects {
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
             * In case of a link value, this may again contain a `Resource` as a nested structure.
             */
            [valueHasPropertyIri: string]: number | string | boolean | Resource;

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
             * Properties of this resource.
             *
             * For a property, a `Value` or an Array of `Value` is given.
             *
             * Please note that the type `string` is not a valid value for a property.
             * This is a mere requirement of Typescript to make the index signature return types consistent with other members of this interface.
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
            "http://schema.org/numberOfItems": number;

        }
    }

    /**
     * This module contains This module contains resource response format definitions for Api V2 simplified.
     */
    export module ApiV2Simple {


    }

}