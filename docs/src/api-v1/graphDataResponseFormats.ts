/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
