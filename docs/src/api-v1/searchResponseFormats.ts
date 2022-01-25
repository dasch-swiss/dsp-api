/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import { basicMessageComponents } from "./basicMessageComponents"

/**
 * This module contains interfaces that represent the response to a search request (fulltext or extended search).
 */
export module searchResponseFormats {

    interface subjectItem {
        /**
         * Description of the resource's class
         */
        iconlabel: string;

        /**
         * X dimension of the preview representation
         */
        preview_nx: number;

        /**
         * Y dimension of the preview representation
         */
        preview_ny: number;

        /**
         * Description of the resource's class
         */
        icontitle: string;

        /**
         * Iri of the retrieved resource
         */
        obj_id: basicMessageComponents.KnoraIRI;

        /**
         * Icon representing the resource's class
         */
        iconsrc: string;

        /**
         * Path to a preview representation
         */
        preview_path: string;

        /**
         * The user's permission on the retrieved resource
         */
        rights: basicMessageComponents.KnoraRights;

        /**
         * Values of the retrieved resource
         */
        value: Array<string>;

        /**
         * IRIs of the value types of the resource's values
         */
        valuetype_id: Array<basicMessageComponents.KnoraIRI>;

        /**
         * Labels of the retrieved resource's values
         */
        valuelabel: Array<string>;

    }

    /**
     * Represents a page in a collection of pages.
     */
    interface pagingItem {

        /**
         * True if this item represents the current page of search results
         */
        current: Boolean;

        /**
         * The index of the first search result on the page
         */
        start_at: number;

        /**
         * The number of results shown on the page
         */
        show_nrows: number;

    }

    /**
     * Represents the response to a fulltext or an extended search
     *
     * HTTP GET to http://host/v1/search/searchTerm?searchtype=fulltext[&filter_by_restype=resourceClassIRI][&filter_by_project=projectIRI][&show_nrows=Integer]{[&start_at=Integer]
     *
     * HTTP GET to http://host/v1/search/?searchtype=extended[&filter_by_restype=resourceClassIRI][&filter_by_project=projectIRI][&filter_by_owner=userIRI](&property_id=propertyTypeIRI&compop=comparisonOperator&searchval=searchValue)+[&show_nrows=Integer]{[&start_at=Integer]
     */
    export interface searchResponse extends basicMessageComponents.basicResponse {

        /**
         * List of search result items
         */
        subjects: Array<subjectItem>;

        /**
         * maximal dimensions of preview representations
         */
        thumb_max: {
            nx: number;
            ny: number;
        }

        /**
         * Represents Information for paging.
         * Go through all the results page by page (by going through the items of the array).
         */
        paging: Array<pagingItem>;

        /**
         * Total number of hits
         */
        nhits: string;

    }
}
