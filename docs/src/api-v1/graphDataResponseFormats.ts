/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import { basicMessageComponents } from "./basicMessageComponents"

/**
 * This module contains interfaces that represent the response to a graph data request.
 */
export module graphDataResponseFormats {
    /**
     * Represents a node in the graph, i.e. a Knora resource that is reachable via links to or from the initial resource.
     */
    interface nodeItem {
        /**
         * The IRI of the node.
         */
        resourceIri: basicMessageComponents.KnoraIRI;

        /**
         * The label of the node.
         */
        resourceLabel: string;

        /**
         * The IRI of the node's resource class.
         */
        resourceClassIri: basicMessageComponents.KnoraIRI;

        /**
         * The label of the node's resource class.
         */
        resourceClassLabel: string;
    }

    /**
     * Represents an edge in the graph, i.e. a link between two resources.
     */
    interface edgeItem {
        /**
         * The IRI of the node that is the source of the link.
         */
        source: basicMessageComponents.KnoraIRI;

        /**
         * The IRI of the node that is the target of the link.
         */
        target: basicMessageComponents.KnoraIRI;

        /**
         * The IRI of the link property.
         */
        propertyIri: basicMessageComponents.KnoraIRI;

        /**
         * The label of the link property.
         */
        propertyLabel: string;
    }

    /**
     * Represents the response to a graph data query.
     *
     * HTTP GET to http://host/v1/search/graphdata/resourceIRI?depth=Integer
     */
    export interface graphDataResponse extends basicMessageComponents.basicResponse {
        /**
         * The nodes (i.e. the resources) that are visible in the graph.
         */
        nodes: Array<nodeItem>;

        /**
         * The edges (i.e. the links) that are visible in the graph.
         */
        edges: Array<edgeItem>;
    }
}
