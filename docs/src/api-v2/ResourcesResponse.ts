import {Basic} from "./Basic";
import IriObject = Basic.IriObject;

/**
 * This module contains response formats for resource instances.
 */
export module ResourcesResponse {

    /**
     * This module contains resource response format definitions for API Schema V2 complex schema (with value objects).
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
             * Assertions about the value (object).
             *
             * In case of a link value, this may again contain a `Resource` as a nested structure.
             */
            [valueHasPropertyIri: string]: number | string | boolean | Resource | IriObject;

        }

        /**
         * Represents a resource (instance).
         */
        export interface Resource {
            /**
             * IRI of the resource instance.
             */
            "@id": Basic.KnoraInstanceIri;

            /**
             * IRI of the class of this resource instance.
             */
            "@type": Basic.KnoraEntityIri;

            /**
             * Short description of this resource.
             */
            "http://www.w3.org/2000/01/rdf-schema#label": string;

            /**
             * Properties of this resource.
             *
             * For a property, a `Value` or an Array of `Value` is given.
             *
             * Please note that the type `string` is not a valid value for a property.
             * This is a mere requirement of TypeScript to make the index signature return types consistent with other members of this interface.
             */
            [propertyIri: string]: Value | Array<Value> | string ;

        }

        /**
         * Represents one or more resources (instances).
         *
         * This is a generic response format used in several routes (resource request, search etc.).
         *
         */
        export interface ResourcesSequence {
            /**
             * A resource or a sequence of resources (instances).
             */
            "@graph": Array<Resource>;
        }
    }

    /**
     * This module contains This module contains resource response format definitions for Api V2 simple schema.
     */
    export module ApiV2Simple {

        /**
            Represents a value with the indication of its type.
         */
        interface ValueWithType {

            /**
             * The type of the value.
             */
            "@type": string;

            /**
             * String representation of the value.
             */
            "@value": string | Basic.DateLiteral;
        }

        /**
         * Represents a resource (instance).
         */
        export interface Resource {
            /**
             * IRI of the resource instance.
             */
            "@id": Basic.KnoraInstanceIri;

            /**
             * IRI of the class of this resource instance.
             */
            "@type": Basic.KnoraEntityIri;

            /**
             * Short description of this resource.
             */
            "http://www.w3.org/2000/01/rdf-schema#label": string;

            /**
             * Properties of this resource (values are literals or an [[IriObject]]).
             *
             */
            [propertyIri: string]: string | Array<string> | number | Array<number> | boolean | Array<boolean> | IriObject | Array<IriObject> | ValueWithType | Array<ValueWithType>;
        }

        /**
         * Represents one or more resources (instances).
         *
         * This is a generic response format used in several routes (resource request, search etc.).
         *
         */
        export interface ResourcesSequence {
            /**
             * A resource or a sequence of resources (instances).
             */
            "@graph": Array<Resource>;

        }

    }

}
