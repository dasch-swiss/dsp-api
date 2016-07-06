export module resourceResponseFormats {

    /**
     * Basic members of the Knora API V1 response format.
     */
    interface basicResponse {
        /**
         * Knora status code
         */
        status:number;

        /**
         * The current user's data
         */
        userdata:userdata;
    }

    /**
     * Represents a Knora project
     */
    interface projectItem {
        /**
         * Path to the project's files
         */
        basepath:string;

        /**
         * Project's short name
         */
        shortname:string;

        /**
         * Description of the project
         */
        description:string;

        /**
         * The project's logo
         */
        logo:string;

        /**
         * The project's IRI
         */
        id:string;

        /**
         * Keywords describing the project
         */
        keywords:string;

        /**
         * obsolete
         */
        rights:string;

        /**
         * Project's long name
         */
        longname:string;
    }

    /**
     * Represents the current user's data
     */
    interface userdata {
        /**
         * User's email address
         */
        email:string;

        /**
         * User's unique name
         */
        username:string;

        /**
         * User's first name
         */
        firstname:string;

        /**
         * User's last name
         */
        lastname:string;

        /**
         * List of project descriptions the user is member of
         */
        projects_info:Array<projectItem>;

        /**
         * User's IRI
         */
        user_id:string;

        /**
         * User's preferred language
         */
        lang:string;

        /**
         * User's active project
         */
        activeProject?:string;

        /**
         * Session token
         */
        token:string;

        /**
         * List of project IRIs the user is member of
         */
        projects?:Array<string>;

        /**
         * obsolete
         */
        password:string;
    }

//
// Resource full response
//

    /**
     * Represents a rich text value
     */
    interface richtext {
        /**
         * Mere string representation
         */
        utf8str:string;

        /**
         * Markup information in standoff format
         */
        textattr:string;

        /**
         * References to Knora resources from the text
         */
        resource_reference:Array<string>
    }

    /**
     * Represents a property value (no parallel arrays)
     */
    interface propval {
        /**
         * Textual representation of the value.
         */
        textval:string;

        /**
         * Owner of the value.
         */
        person_id?:string;

        /**
         * Date of last modification of the value.
         */
        lastmod?:string;

        /**
         * IRI of the value.
         */
        id:string;

        /**
         * Comment on the value.
         */
        comment:string;

        /**
         * date of last modification of the value as UTC.
         */
        lastmod_utc?:string;

        /**
         * typed representation of the value.
         */
        value:string|number|richtext;
    }

    /**
     * Represents a property (no parallel arrays)
     */
    interface prop {
        /**
         * type of the value as a string
         */
        valuetype:string;

        /**
         * obsolete
         */
        is_annotation:string;

        /**
         * IRI of the value type.
         */
        valuetype_id:string;

        /**
         * Label of the property type
         */
        label:string;

        /**
         * GUI element of the property
         */
        guielement:string;

        /**
         * HTML attributes for the GUI element used to render this property
         */
        attributes:string;

        /**
         * IRI of the property type
         */
        pid:string;

        /**
         * the property's values
         */
        values:Array<propval>;
    }

    /**
     * Represents a property (parallel arrays)
     */
    interface property {
        /**
         * obsolete
         */
        regular_property:number;
        /**
         * If the property's value is another resource, contains the `rdfs:label` of the OWL class
         * of each resource referred to.
         */
        value_restype?:Array<string>;
        /**
         * Order of property type in GUI
         */
        guiorder:number;
        /**
         * If the property's value is another resource, contains the `rdfs:label` of each resource
         * referred to.
         */
        value_firstprops?:Array<string>;
        /**
         * obsolote
         */
        is_annotation:string;
        /**
         * The type of this property's values
         */
        valuetype_id:string;
        /**
         * The label of thi property type
         */
        label:string;
        /**
         * if the property's value is another resource, contains the icon representing the OWL
         * class of each resource referred to.
         */
        value_iconsrcs?:Array<string>;
        /**
         * the type of GUI element used to render this property.
         */
        guielement:string;
        /**
         * HTML attributes for the GUI element used to render this property
         */
        attributes:string;
        /**
         * The cardinality of this property type for the given resource class
         */
        occurrence:string;
        /**
         * The IRIs of the value objects representing the property's values for this resource
         */
        value_ids?:Array<string>;
        /**
         * The given user's permissions on the value objects.
         */
        value_rights?:Array<number>;
        /**
         * The IRI of the property type
         */
        pid:string;
        /**
         * The property's values
         */
        values?:Array<string|number|richtext>;
        /**
         * Comments on the property's values
         */
        comments?:Array<string>;
    }


    /**
     * Binary representation of a resource (location)
     */
    interface locationItem {
        /**
         * Duration of a movie or an audio file
         */
        duration:number;

        /**
         * X dimension of an image representation
         */
        nx:number;

        /**
         * Y dimension of an image representation
         */
        ny:number;

        /**
         * Path to the binary representation
         */
        path:string;

        /**
         * Frames per second (movie)
         */
        fps:number;

        /**
         * Format of the binary representation
         */
        format_name:string;

        /**
         * Original file name of the binary representation (before import to Knora)
         */
        origname:string;

        /**
         * Protocol used
         */
        protocol:protocolOptions;
    }

    /**
     * Represents how a binary representation (location) can be accessed.
     * Either locally stored (file) or referenced from an external location (url)
     */
    type protocolOptions = "file" | "url";

    /**
     * Represents a permission assertion for the current user
     */
    interface permissionItem {
        /**
         * Permission level
         */
        permission:string;

        /**
         * User group that the permission level is granted to
         */
        granted_to:string;
    }

    /**
     * Represents the regions attached to a resource
     */
    interface region {
        /**
         * A map of propety types to property values and res_id and iconsrc
         */
        [index:string]:prop|string;
    }

    /**
     * Represents information about a resource and its class
     */
    interface resinfo {
        /**
         * Digital representations of the resource
         */
        locations:Array<locationItem>;

        /**
         * Label of the resource's class
         */
        restype_label:string;

        /**
         * Indicates if there is a location (digital representation) attached
         */
        resclass_has_location:boolean;

        /**
         * Preview representation of the resource: Thumbnail or Icon
         */
        preview:locationItem;

        /**
         * The owner of the resource
         */
        person_id:string;

        /**
         * Points to the parent resource in case the resource depends on it
         */
        value_of:string|number;

        /**
         * The given user's permissions on the resource
         */
        permissions:Array<permissionItem>;

        /**
         * Date of last modification
         */
        lastmod:string;

        /**
         * The resource class's name
         */
        resclass_name:string;

        /**
         * Regions if there are any
         */
        regions?:Array<region>

        /**
         * Description of the resource type
         */
        restype_description:string;

        /**
         * The project IRI the resource belongs to
         */
        project_id:string;

        /**
         * Full quality representation of the resource
         */
        locdata:locationItem;

        /**
         * The Knora IRI identifying the resource's class
         */
        restype_id:string;

        /**
         * The resource's label
         */
        firstproperty:string;

        /**
         * The URL of an icon for the resource class
         */
        restype_iconsrc:string;

        /**
         * The Knora IRI identifying the resource's class
         */
        restype_name:string;
    }

    /**
     * Represents information about a resource
     */
    interface resdata {
        /**
         * IRI of the resource
         */
        res_id:string;

        /**
         * IRI of the resource's class.
         */
        restype_name:string;

        /**
         * Label of the resource's class
         */
        restype_label:string;

        /**
         * Icon of the resource's class.
         */
        iconsrc:string;

        /**
         * The given user's permissions on the resource
         */
        rights:number;
    }

    /**
     * Represents a resource referring to the requested resource.
     */
    interface incomingItem {
        /**
         * Representation of the referring resource
         */
        ext_res_id:{
            /**
             * The Iri of the referring resource
             */
            id:string;

            /**
             * The IRI of the referring property type
             */
            pid:string;
        };

        /**
         * Resinfo of the referring resource
         */
        resinfo:resinfo;

        /**
         * First property of the referring resource
         */
        value:string;
    }

    /**
     * Represents the context of a resource
     */
    interface context {
        /**
         * Context code: 0 for none, 1 for is partOf (e.g. a page of a book), 2 for isCompound (e.g. a book that has pages)
         */
        context:number;

        /**
         * The Iri of the resource
         */
        canonical_res_id:string;

        /**
         * IRO of the parent resource
         */
        parent_res_id?:string;

        /**
         * Resinfo of the parent resource (if the requested resource is a dependent resource like a page that belongs to a book)
         */
        parent_resinfo?:resinfo;

        /**
         * Resinfo of the requested resource (if requested)
         */
        resinfo?:resinfo;

        /**
         * Locations of depending resources (e.g. representation of pages of a book)
         */
        locations?:Array<Array<locationItem>>;

        /**
         * Preview locations of depending resources (e.g. representation of pages of a book)
         */
        preview?:Array<locationItem>

        /**
         * First properties of depending resources (e.g. of pages of a book)
         */
        firstprop?:Array<string>;

        /**
         * obsolete
         */
        region?:Array<string>;

        /**
         * obsolete
         */
        resclass_name?:string;

        /**
         * Iris of dependent resources (e.g. pages of a book)
         */
        res_id?:Array<string>;
    }

    /**
     * Represents the Knora API V1 response to a full resource request
     */
    export interface resourceFullResponse extends basicResponse {
        /**
         * Description of the resource and its class
         */
        resinfo:resinfo;

        /**
         * Additional information about the requested resource (no parameters)
         */
        resdata:resdata;

        /**
         * The resource's properties
         */
        props:{
            [index:string]:property|Array<locationItem>;
        }

        /**
         * Resources referring to the requested resource
         */
        incoming:Array<incomingItem>

        /**
         * The given user's permissions on the resource
         */
        access:string;

    }

    /**
     * Represents the Knora API V1 response to a resource info request (reqtype=info)
     */
    export interface resourceInfoResponse extends basicResponse {
        /**
         * The current user's permissions on the resource
         */
        rights:number;

        /**
         * Description of the resource and its class
         */
        resource_info:resinfo;

    }

    /**
     * Represents the Knora API V1 response to a resource rights request (reqtype=rights)
     */
    export interface resourceRightsResponse extends basicResponse {
        /**
         * The current user's permissions on the resource
         */
        rights:number;

    }

    /**
     * Represents the Knora API V1 response to a context request (reqtype=context) with or without resinfo (resinfo=true)
     */
    export interface resourceContextResponse extends basicResponse {
        /**
         * Context of the requested resource
         */
        resource_context:context;

    }

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

    export interface searchResponse extends basicResponse {

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
