import { basicResponseComponents } from "./basicResponseComponents"

export module searchResponseFormats {

    interface subjectItem {
        /**
         * Description of the resource's class
         */
        iconlabel: string;

        /**
         * IRI of the resource's class
         */
        valuetype_id: Array<string>;

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
        obj_id: string;

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
        rights: number;

        /**
         * Values of the retrieved resource
         */
        value: Array<string>;

        /**
         * Labels of the retrieved resource's values
         */
        valuelabel: Array<string>;

    }

    interface pagingItem {

        /**
         * Current page
         */
        current: Boolean;

        /**
         * The index of the first search result on the page
         */
        start_at: number;

        /**
         * The number of results on the page
         */
        show_nrows: number;

    }

    export interface searchResponse extends basicResponseComponents.basicResponse {

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
         * Information for paging
         */
        paging: Array<pagingItem>;

        /**
         * Total number of hits
         */
        nhits: string;

    }
}