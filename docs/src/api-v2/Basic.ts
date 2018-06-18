/**
 * This module contains basic definitions that can be reused in other modules.
 */
export module Basic {

    /**
     * IRI representing a Knora entity definition (resource or value class).
     */
    export type KnoraEntityIri = string;

    /**
     * IRI representing an instance of a Knora entity (resource or value).
     */
    export type KnoraInstanceIri = string;

    /**
     * IRI representing an instance or entity in Knora that is neither a resource or a value (e.g., a project, a user, or a mapping).
     */
    export type KnoraIri = string;

    /**
     * Formal description of a Knora date literal.
     * (GREGORIAN|JULIAN):\d{1,4}(-\d{1,2}(-\d{1,2})?)?( BC| AD| BCE| CE)?(:\d{1,4}(-\d{1,2}(-\d{1,2})?)?( BC| AD| BCE| CE)?)?
     */
    export type DateLiteral = string;

    /**
     * Represents an Iri in JSON-LD (if not a value of the key @id or @type).
     */
    export interface IriObject {

        "@id": string;

    }

}