/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import {basicMessageComponents} from "./basicMessageComponents"

export module hierarchicalListResponseFormats {

    /**
     * Represents a node in a hierarchical list.
     */
    interface listNode {

        /**
         * The internal name of the list node
         */
        name:string;

        /**
         * The label og the list node
         */
        label:string;

        /**
         * The IRI of the list node
         */
        id:basicMessageComponents.KnoraListNodeIRI;

        /**
         * The level the list node is on (first level below root node is 0)
         */
        level:number;

        /**
         * The children of the list node
         */
        children?:Array<listNode>;


    }

    /**
     * Represents a node in a path.
     */
    interface nodeInPath {

        /**
         * The internal name of the list node
         */
        name:string;

        /**
         * The label og the list node
         */
        label:string;

        /**
         * The IRI of the list node
         */
        id:basicMessageComponents.KnoraListNodeIRI;


    }

    /** Represents the response to a node path request.
     *
     * http://www.knora.org/v1/hlists/nodeIRI?reqtype=node
     *
     */
    export interface nodePathResponse extends basicMessageComponents.basicResponse {

        /**
         * Represents the path to the requested node.
         * The array's first element is the highest level, the array's last element is the requested node.
         */
        nodelist: Array<nodeInPath>;

    }

    /**
     * Represents a hierarchical list. Selections are also represented as hierarchical lists.
     *
     * http://www.knora.org/v1/hlists/listIRI

     */
    export interface hierarchicalListResponse extends basicMessageComponents.basicResponse {

        /**
         * Represents the elements of the requested hierarchical list.
         */
        hlist:Array<listNode>;


    }

}