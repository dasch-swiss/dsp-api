/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import {basicMessageComponents} from "./basicMessageComponents"

/**
 * This module contains interfaces that represent represent the response to a GET request of a hierarchical list.
 */
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

    /**
     * Represents the response to a node path request.
     *
     * HTTP GET to http://host/v1/hlists/nodeIRI?reqtype=node
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
     * HTTP GET to http://host/v1/hlists/listIRI
     */
    export interface hierarchicalListResponse extends basicMessageComponents.basicResponse {

        /**
         * Represents the elements of the requested hierarchical list.
         */
        hlist:Array<listNode>;


    }

}
