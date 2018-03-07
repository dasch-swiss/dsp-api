/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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