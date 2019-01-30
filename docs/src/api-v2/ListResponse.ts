import {Basic} from "./Basic";
import IriObject = Basic.IriObject;


/**
 * This module contains request and response formats for the creation of a mapping (XML to standoff).
 */
export module ListResponse {

    /**
     * Represents a hierarchical list.
     *
     * HTTP GET request to http://host/v2/lists/rootNodeIri
     */
    export interface List {

        /**
         * The Iri of the list's root node.
         */
        "@id": Basic.KnoraIri;

        /**
         * The list's type.
         */
        "@type": Basic.KnoraIri;

        /**
         * The list's rdfs:label.
         */
        "http://www.w3.org/2000/01/rdf-schema#label": string;

        /**
         * The list's rdfs:comment.
         */
        "http://www.w3.org/2000/01/rdf-schema#comment"?: string;

        /**
         * The project the list belongs to.
         */
        "http://api.knora.org/ontology/knora-api/v2#attachedToProject": Basic.IriObject;

        /**
         * Indicates that this is the list's root node.
         */
        "http://api.knora.org/ontology/knora-api/v2#isRootNode": Boolean;

        /**
         * The list's sub nodes.
         */
        "http://api.knora.org/ontology/knora-api/v2#hasSubListNode"?: SubListNode | Array<SubListNode>;

    }

    /**
     * Represents a sub node of a list (not the root node).
     */
    interface SubListNode extends ListNode {

        /**
         * The node's sub nodes.
         */
        "http://api.knora.org/ontology/knora-api/v2#hasSubListNode"?: SubListNode | Array<SubListNode>;

    }

    /**
     * Represents a list node.
     *
     * HTTP GET request to http://host/v2/lists/nodeIri
     */
    export interface ListNode {

        /**
         * The Iri of the list node.
         */
        "@id": Basic.KnoraIri;

        /**
         * The list node's type.
         */
        "@type": Basic.KnoraIri;

        /**
         * The list node's rdfs:label.
         */
        "http://www.w3.org/2000/01/rdf-schema#label": string;

        /**
         * The list's root node.
         */
        "http://api.knora.org/ontology/knora-api/v2#hasRootNode": Basic.IriObject;

        /**
         * Indicates the position of the list node.
         */
        "http://api.knora.org/ontology/knora-api/v2#listNodePosition": number;


    }




}
