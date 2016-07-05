/**
 * Basic members of the Knora API V1 response format.
 */
interface basicResponse {
    /**
     * Knora status code
     */
    status: number;

    /**
     * The given user's permissions on the resource
     */
    access: string;

    /**
     * The current user's data
     */
    userdata: userdata;
}

/**
 * Represents a Knora project
 */
interface projectItem {
    /**
     * Path to the project's files
     */
    basepath: string;

    /**
     * Project's short name
     */
    shortname: string;

    /**
     * Description of the project
     */
    description: string;

    /**
     * The project's logo
     */
    logo: string;

    /**
     * The project's IRI
     */
    id: string;

    /**
     * Keywords describing the project
     */
    keywords: string;

    /**
     * obsolete
     */
    rights: string;

    /**
     * Project's long name
     */
    longname: string;
}

/**
 * Represents the current user's data
 */
interface userdata {
    /**
     * User's email address
     */
    email: string;

    /**
     * User's unique name
     */
    username: string;

    /**
     * User's first name
     */
    firstname: string;

    /**
     * User's last name
     */
    lastname: string;

    /**
     * List of project descriptions the user is member of
     */
    projects_info:Array<projectItem>;

    /**
     * User's IRI
     */
    user_id: string;

    /**
     * User's preferred language
     */
    lang: string;

    /**
     * User's active project
     */
    activeProject?: string;

    /**
     * Session token
     */
    token: string;

    /**
     * List of project IRIs the user is member of
     */
    projects?: Array<string>;

    /**
     * obsolete
     */
    password: string;
}

/**
 * Represents a rich text value
 */
interface richtext {
    /**
     * Mere string representation
     */
    utf8str: string;

    /**
     * Markup information in standoff format
     */
    textattr: string;

    /**
     * References to Knora resources from the text
     */
    resource_reference: Array<string>
}

/**
 * Represents a property value (no parallel arrays)
 */
interface propval {
    /**
     * Textual representation of the value.
     */
    textval: string;

    /**
     * Owner of the value.
     */
    person_id?: string;

    /**
     * Date of last modification of the value.
     */
    lastmod?: string;

    /**
     * IRI of the value.
     */
    id: string;

    /**
     * Comment on the value.
     */
    comment: string;

    /**
     * date of last modification of the value as UTC.
     */
    lastmod_utc?: string;

    /**
     * typed representation of the value.
     */
    value: string|number|richtext;
}

/**
 * Represents a property (no parallel arrays)
 */
interface prop {
    /**
     * type of the value as a string
     */
    valuetype: string;

    /**
     * obsolete
     */
    is_annotation: string;

    /**
     * IRI of the value type.
     */
    valuetype_id: string;

    /**
     * Label of the property type
    */
    label: string;

    /**
     * GUI element of the property
     */
    guielement: string;

    /**
     * HTML attributes for the GUI element used to render this property
     */
    attributes: string;

    /**
     * IRI of the property type
     */
    pid: string;

    /**
     * the property's values
     */
    values: Array<propval>;
}

/**
 * Represents a property (parallel arrays)
 */
interface property {
    /**
     * obsolete
     */
    regular_property: number;
    /**
     * If the property's value is another resource, contains the `rdfs:label` of the OWL class
     * of each resource referred to.
     */
    value_restype?: Array<string>;
    /**
     * Order of property type in GUI
     */
    guiorder: number;
    /**
     * If the property's value is another resource, contains the `rdfs:label` of each resource
     * referred to.
     */
    value_firstprops?: Array<string>;
    /**
     * obsolote
     */
    is_annotation: string;
    /**
     * The type of this property's values
     */
    valuetype_id: string;
    /**
     * The label of thi property type
     */
    label: string;
    /**
     * if the property's value is another resource, contains the icon representing the OWL
     * class of each resource referred to.
     */
    value_iconsrcs?: Array<string>;
    /**
     * the type of GUI element used to render this property.
     */
    guielement: string;
    /**
     * HTML attributes for the GUI element used to render this property
     */
    attributes: string;
    /**
     * The cardinality of this property type for the given resource class
     */
    occurrence: string;
    /**
     * The IRIs of the value objects representing the property's values for this resource
     */
    value_ids?: Array<string>;
    /**
     * The given user's permissions on the value objects.
     */
    value_rights?: Array<number>;
    /**
     * The IRI of the property type
     */
    pid: string;
    /**
     * The property's values
     */
    values?: Array<string|number|richtext>;
    /**
     * Comments on the property's values
     */
    comments?: Array<string>;
}


/**
 * Binary representation of a resource (location)
 */
interface locationItem {
    /**
     * Duration of a movie or an audio file
     */
    duration: number;

    /**
     * X dimension of an image representation
     */
    nx: number;

    /**
     * Y dimension of an image representation
     */
    ny: number;

    /**
     * Path to the binary representation
     */
    path: string;

    /**
     * Frames per second (movie)
     */
    fps: number;

    /**
     * Format of the binary representation
     */
    format_name: string;

    /**
     * Original file name of the binary representation (before import to Knora)
     */
    origname: string;

    /**
     * Protocol used
     */
    protocol: protocolOptions;
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
    permission: string;

    /**
     * User group that the permission level is granted to
     */
    granted_to: string;
}

/**
 * Represents information about a resource and its class
 */
interface resinfo {
    /**
     * Digital representations of the resource
     */
    locations: Array<locationItem>;

    /**
     * Label of the resource's class
     */
    restype_label: string;

    /**
     * Indicates if there is a location (digital representation) attached
     */
    resclass_has_location: boolean;

    /**
     * Preview representation of the resource: Thumbnail or Icon
     */
    preview: locationItem;

    /**
     * The owner of the resource
     */
    person_id: string;

    /**
     * Points to the parent resource in case the resource depends on it
     */
    value_of: string|number;

    /**
     * The given user's permissions on the resource
     */
    permissions: Array<permissionItem>;

    /**
     * Date of last modification
     */
    lastmod: string;

    /**
     * The resource class's name
     */
    resclass_name: string;

    /**
     * Regions if there are any
     */
    regions?: {
        [index: string]: prop;
    }

    /**
     * Description of the resource type
     */
    restype_description: string;

    /**
     * The project IRI the resource belongs to
     */
    project_id: string;

    /**
     * Full quality representation of the resource
     */
    locdata: locationItem;

    /**
     * The Knora IRI identifying the resource's class
     */
    restype_id: string;

    /**
     * The resource's label
     */
    firstproperty: string;

    /**
     * The URL of an icon for the resource class
     */
    restype_iconsrc: string;

    /**
     * The Knora IRI identifying the resource's class
     */
    restype_name: string;
}

interface resdata {
    /**
     * IRI of the resource
     */
    res_id: string;

    /**
     * IRI of the resource's class.
     */
    restype_name: string;

    /**
     * Label of the resource's class
     */
    restype_label: string;

    /**
     * Icon of the resource's class.
     */
    iconsrc: string;

    /**
     * The given user's permissions on the resource
     */
    rights: number;
}

/**
 * Represents a resource referring to the requested resource.
 */
interface incomingItem {
    /**
     * Representation of the referring resource
     */
    ext_res_id: {
        /**
         * The Iri of the referring resource
         */
        id: string;

        /**
         * The IRI of the referring property type
         */
        pid: string;
    };

    /**
     * Resinfo of the referring resource
     */
    resinfo: resinfo;

    /**
     * First property of the referring resource
     */
    value: string;
}

/**
 * Represents the Knora API V1response to a full resource request.
 */
interface resourceFullResponse extends basicResponse {
    /**
     * Description of the resource and its class
     */
    resinfo: resinfo;

    /**
     * Additional information about the requested resource
     */
    resdata: resdata;

    /**
     * The resource's properties
     */
    props: {
        [index: string]: property|Array<locationItem>;
    }

    /**
     * Resources referring to the requested resource
     */
    incoming: Array<incomingItem>
}

//var res: resourceFullResponse = {"userdata":{"email":"test@test.ch","username":"root","firstname":"Administrator","projects_info":[{"basepath":null,"shortname":"incunabula","description":null,"logo":"incunabula_logo.png","id":"http://data.knora.org/projects/77275339","keywords":null,"rights":null,"longname":"Bilderfolgen Basler Frühdrucke"},{"basepath":null,"shortname":"images","description":null,"logo":null,"id":"http://data.knora.org/projects/images","keywords":null,"rights":null,"longname":"Images Collection Demo"},{"basepath":null,"shortname":"anything","description":null,"logo":null,"id":"http://data.knora.org/projects/anything","keywords":null,"rights":null,"longname":"Anything Project"}],"user_id":"http://data.knora.org/users/91e19f1e01","activeProject":null,"lastname":"Admin","token":null,"projects":["http://data.knora.org/projects/77275339","http://data.knora.org/projects/images","http://data.knora.org/projects/anything"],"lang":"de","password":null},"resinfo":{"locations":[{"duration":0,"nx":86,"path":"http://localhost:1024/knora/incunabula_0000002130.jpg/full/full/0/default.jpg","ny":128,"fps":0,"format_name":"JPEG","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":43,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/43,63/0/default.jpg","ny":63,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":85,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/85,127/0/default.jpg","ny":127,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":170,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/170,254/0/default.jpg","ny":254,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":340,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/340,507/0/default.jpg","ny":507,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":680,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/680,1014/0/default.jpg","ny":1014,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":1360,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/1360,2028/0/default.jpg","ny":2028,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"}],"restype_label":"Seite","resclass_has_location":true,"preview":{"duration":0,"nx":86,"path":"http://localhost:1024/knora/incunabula_0000002130.jpg/full/full/0/default.jpg","ny":128,"fps":0,"format_name":"JPEG","origname":"IBB_1_002647120_0219.tif","protocol":"file"},"person_id":"http://data.knora.org/users/91e19f1e01","value_of":0,"permissions":[{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#ProjectMember"},{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#Owner"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#KnownUser"},{"permission":"http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#UnknownUser"}],"lastmod":"0000-00-00 00:00:00","resclass_name":"object","firstproperty":"o6r","restype_iconsrc":"http://localhost:3335/project-icons/incunabula/page.gif","restype_name":"http://www.knora.org/ontology/incunabula#page","regions":null,"restype_description":"Eine Seite ist ein Teil eines Buchs","project_id":"http://data.knora.org/projects/77275339","locdata":{"duration":0,"nx":1360,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/1360,2028/0/default.jpg","ny":2028,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},"restype_id":"http://www.knora.org/ontology/incunabula#page"},"incoming":[{"ext_res_id":{"id":"http://data.knora.org/2357e0d64407","pid":"http://www.knora.org/ontology/knora-base#isRegionOf"},"resinfo":{"locations":null,"restype_label":"Region","resclass_has_location":false,"preview":null,"person_id":"http://data.knora.org/users/b83acc5f05","value_of":0,"permissions":[{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#ProjectMember"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#UnknownUser"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#KnownUser"}],"lastmod":"0000-00-00 00:00:00","resclass_name":"object","firstproperty":"Kolorierung in Rot","restype_iconsrc":"http://localhost:3335/project-icons/knora-base/region.gif","restype_name":"http://www.knora.org/ontology/knora-base#Region","regions":null,"restype_description":"Represents a geometric region of a resource. The geometry is represented currently as JSON string.","project_id":"http://data.knora.org/projects/77275339","locdata":null,"restype_id":"http://www.knora.org/ontology/knora-base#Region"},"value":"Kolorierung in Rot"},{"ext_res_id":{"id":"http://data.knora.org/5e51519c4407","pid":"http://www.knora.org/ontology/knora-base#isRegionOf"},"resinfo":{"locations":null,"restype_label":"Region","resclass_has_location":false,"preview":null,"person_id":"http://data.knora.org/users/b83acc5f05","value_of":0,"permissions":[{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#ProjectMember"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#UnknownUser"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#KnownUser"}],"lastmod":"0000-00-00 00:00:00","resclass_name":"object","firstproperty":"Defekter Druckstock","restype_iconsrc":"http://localhost:3335/project-icons/knora-base/region.gif","restype_name":"http://www.knora.org/ontology/knora-base#Region","regions":null,"restype_description":"Represents a geometric region of a resource. The geometry is represented currently as JSON string.","project_id":"http://data.knora.org/projects/77275339","locdata":null,"restype_id":"http://www.knora.org/ontology/knora-base#Region"},"value":"Defekter Druckstock"}],"resdata":{"restype_label":"Seite","restype_name":"http://www.knora.org/ontology/incunabula#page","iconsrc":"http://localhost:3335/project-icons/incunabula/page.gif","rights":8,"res_id":"http://data.knora.org/1a01fe39e701"},"status":0,"props":{"http://www.knora.org/ontology/knora-base#hasRepresentation":{"regular_property":1,"guiorder":null,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"hat Repräsentation","guielement":null,"attributes":"restypeid=http://www.knora.org/ontology/knora-base#Representation","occurrence":"0-n","pid":"http://www.knora.org/ontology/knora-base#hasRepresentation"},"http://www.knora.org/ontology/incunabula#citation":{"regular_property":1,"guiorder":5,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Verweis","guielement":"textarea","attributes":"cols=60;wrap=soft;rows=3","occurrence":"0-n","pid":"http://www.knora.org/ontology/incunabula#citation"},"http://www.knora.org/ontology/incunabula#partOf":{"regular_property":1,"value_restype":["Buch"],"guiorder":2,"value_firstprops":["Bereitung zu dem Heiligen Sakrament"],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"ist ein Teil von","value_iconsrcs":["http://localhost:3335/project-icons/incunabula/book.gif"],"guielement":"searchbox","attributes":"restypeid=http://www.knora.org/ontology/incunabula#book","occurrence":"1","value_ids":["http://data.knora.org/1a01fe39e701/values/a749ba73-336a-4b7d-b40a-9d89a67844a1"],"value_rights":[8],"pid":"http://www.knora.org/ontology/incunabula#partOf","values":["http://data.knora.org/9311a421b501"],"comments":[""]},"http://www.knora.org/ontology/incunabula#pagenum":{"regular_property":1,"value_restype":[null],"guiorder":1,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Seitenbezeichnung","value_iconsrcs":[null],"guielement":"text","attributes":"min=4;max=8","occurrence":"0-1","value_ids":["http://data.knora.org/1a01fe39e701/values/a0ad26e19507"],"value_rights":[8],"pid":"http://www.knora.org/ontology/incunabula#pagenum","values":[{"utf8str":"o6r","textattr":"{}","resource_reference":[]}],"comments":[""]},"http://www.knora.org/ontology/incunabula#transcription":{"regular_property":1,"guiorder":12,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Transkription","guielement":"pulldown","attributes":"hlist=<http://data.knora.org/lists/4b6d86ce03>","occurrence":"0-n","pid":"http://www.knora.org/ontology/incunabula#transcription"},"__location__":{"locations":[{"duration":0,"nx":86,"path":"http://localhost:1024/knora/incunabula_0000002130.jpg/full/full/0/default.jpg","ny":128,"fps":0,"format_name":"JPEG","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":43,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/43,63/0/default.jpg","ny":63,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":85,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/85,127/0/default.jpg","ny":127,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":170,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/170,254/0/default.jpg","ny":254,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":340,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/340,507/0/default.jpg","ny":507,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":680,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/680,1014/0/default.jpg","ny":1014,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":1360,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/1360,2028/0/default.jpg","ny":2028,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"}],"regular_property":1,"guiorder":2147483647,"is_annotation":"0","valuetype_id":"-1","label":null,"guielement":"fileupload","attributes":"","occurrence":null,"value_ids":["0"],"pid":"__location__","values":[0],"comments":["0"]},"http://www.knora.org/ontology/incunabula#hasRightSideband":{"regular_property":1,"guiorder":11,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"Randleistentyp rechts","guielement":"searchbox","attributes":"numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband","occurrence":"0-1","pid":"http://www.knora.org/ontology/incunabula#hasRightSideband"},"http://www.knora.org/ontology/incunabula#hasLeftSideband":{"regular_property":1,"guiorder":10,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"Randleistentyp links","guielement":"searchbox","attributes":"numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband","occurrence":"0-1","pid":"http://www.knora.org/ontology/incunabula#hasLeftSideband"},"http://www.knora.org/ontology/incunabula#description":{"regular_property":1,"value_restype":[null],"guiorder":2,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Beschreibung (Richtext)","value_iconsrcs":[null],"guielement":"richtext","attributes":"","occurrence":"0-1","value_ids":["http://data.knora.org/1a01fe39e701/values/883493f1ce25"],"value_rights":[6],"pid":"http://www.knora.org/ontology/incunabula#description","values":[{"utf8str":"Text.\nHolzschnitt:\nVerzweiflung des Petrus, 6.7 x 5.2 cm, unkoloriert.\nRubrizierung in Rot.\nFehlstelle im Druckstock vom rechten Rand bis ins Gesicht. \nKolorierung in Rot im Gesicht. \nBildnummerierung (Graphitstift) rechts unter dem Holzschnitt: 36.\nBlattnummerierung (Graphitstift) oben rechts: \"110 resp. 109\".","textattr":"{}","resource_reference":[]}],"comments":[""]},"http://www.knora.org/ontology/incunabula#page_comment":{"regular_property":1,"value_restype":[null,null],"guiorder":6,"value_firstprops":[null,null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Kommentar","value_iconsrcs":[null,null],"guielement":"textarea","attributes":"wrap=soft;width=95%;rows=7","occurrence":"0-n","value_ids":["http://data.knora.org/1a01fe39e701/values/4b5ee62acf25","http://data.knora.org/1a01fe39e701/values/7758ab1b0829"],"value_rights":[7,7],"pid":"http://www.knora.org/ontology/incunabula#page_comment","values":[{"utf8str":"Das Rot der Kolorierung im Gesicht stimmt mit der Farbe der Rubrizierung überein. ","textattr":"{}","resource_reference":[]},{"utf8str":"Schramm, Bd. 22, Abb. 455.","textattr":"{}","resource_reference":[]}],"comments":["",""]},"http://www.knora.org/ontology/incunabula#origname":{"regular_property":1,"value_restype":[null],"guiorder":7,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Ursprünglicher Dateiname","value_iconsrcs":[null],"guielement":"text","attributes":"size=54;maxlength=128","occurrence":"1","value_ids":["http://data.knora.org/1a01fe39e701/values/e92a208d9607"],"value_rights":[8],"pid":"http://www.knora.org/ontology/incunabula#origname","values":[{"utf8str":"IBB_1_002647120_0219.tif","textattr":"{}","resource_reference":[]}],"comments":[""]},"http://www.knora.org/ontology/incunabula#seqnum":{"regular_property":1,"value_restype":[null],"guiorder":3,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#IntValue","label":"Sequenznummer","value_iconsrcs":[null],"guielement":"spinbox","attributes":"min=0;max=-1","occurrence":"0-1","value_ids":["http://data.knora.org/1a01fe39e701/values/2601cd539607"],"value_rights":[8],"pid":"http://www.knora.org/ontology/incunabula#seqnum","values":[219],"comments":[""]}},"access":"OK"};

//var res2: resourceFullResponse = {"userdata":{"email":null,"username":null,"firstname":null,"projects_info":[],"user_id":null,"activeProject":null,"lastname":null,"token":null,"projects":null,"lang":"en","password":null},"resinfo":{"locations":[{"duration":0,"nx":86,"path":"http://localhost:1024/knora/incunabula_0000002130.jpg/full/full/0/default.jpg","ny":128,"fps":0,"format_name":"JPEG","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":43,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/43,63/0/default.jpg","ny":63,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":85,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/85,127/0/default.jpg","ny":127,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":170,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/170,254/0/default.jpg","ny":254,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":340,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/340,507/0/default.jpg","ny":507,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":680,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/680,1014/0/default.jpg","ny":1014,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":1360,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/1360,2028/0/default.jpg","ny":2028,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"}],"restype_label":"Page","resclass_has_location":true,"preview":{"duration":0,"nx":86,"path":"http://localhost:1024/knora/incunabula_0000002130.jpg/full/full/0/default.jpg","ny":128,"fps":0,"format_name":"JPEG","origname":"IBB_1_002647120_0219.tif","protocol":"file"},"person_id":"http://data.knora.org/users/91e19f1e01","value_of":0,"permissions":[{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#ProjectMember"},{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#Owner"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#KnownUser"},{"permission":"http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#UnknownUser"}],"lastmod":"0000-00-00 00:00:00","resclass_name":"object","firstproperty":"o6r","restype_iconsrc":"http://localhost:3335/project-icons/incunabula/page.gif","restype_name":"http://www.knora.org/ontology/incunabula#page","regions":null,"restype_description":"A page is a part of a book","project_id":"http://data.knora.org/projects/77275339","locdata":{"duration":0,"nx":1360,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/1360,2028/0/default.jpg","ny":2028,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},"restype_id":"http://www.knora.org/ontology/incunabula#page"},"incoming":[{"ext_res_id":{"id":"http://data.knora.org/2357e0d64407","pid":"http://www.knora.org/ontology/knora-base#isRegionOf"},"resinfo":{"locations":null,"restype_label":"Region","resclass_has_location":false,"preview":null,"person_id":"http://data.knora.org/users/b83acc5f05","value_of":0,"permissions":[{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#ProjectMember"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#UnknownUser"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#KnownUser"}],"lastmod":"0000-00-00 00:00:00","resclass_name":"object","firstproperty":"Kolorierung in Rot","restype_iconsrc":"http://localhost:3335/project-icons/knora-base/region.gif","restype_name":"http://www.knora.org/ontology/knora-base#Region","regions":null,"restype_description":"Represents a geometric region of a resource. The geometry is represented currently as JSON string.","project_id":"http://data.knora.org/projects/77275339","locdata":null,"restype_id":"http://www.knora.org/ontology/knora-base#Region"},"value":"Kolorierung in Rot"},{"ext_res_id":{"id":"http://data.knora.org/5e51519c4407","pid":"http://www.knora.org/ontology/knora-base#isRegionOf"},"resinfo":{"locations":null,"restype_label":"Region","resclass_has_location":false,"preview":null,"person_id":"http://data.knora.org/users/b83acc5f05","value_of":0,"permissions":[{"permission":"http://www.knora.org/ontology/knora-base#hasModifyPermission","granted_to":"http://www.knora.org/ontology/knora-base#ProjectMember"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#UnknownUser"},{"permission":"http://www.knora.org/ontology/knora-base#hasViewPermission","granted_to":"http://www.knora.org/ontology/knora-base#KnownUser"}],"lastmod":"0000-00-00 00:00:00","resclass_name":"object","firstproperty":"Defekter Druckstock","restype_iconsrc":"http://localhost:3335/project-icons/knora-base/region.gif","restype_name":"http://www.knora.org/ontology/knora-base#Region","regions":null,"restype_description":"Represents a geometric region of a resource. The geometry is represented currently as JSON string.","project_id":"http://data.knora.org/projects/77275339","locdata":null,"restype_id":"http://www.knora.org/ontology/knora-base#Region"},"value":"Defekter Druckstock"}],"resdata":{"restype_label":"Page","restype_name":"http://www.knora.org/ontology/incunabula#page","iconsrc":"http://localhost:3335/project-icons/incunabula/page.gif","rights":1,"res_id":"http://data.knora.org/1a01fe39e701"},"status":0,"props":{"http://www.knora.org/ontology/knora-base#hasRepresentation":{"regular_property":1,"guiorder":null,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"has Representation","guielement":null,"attributes":"restypeid=http://www.knora.org/ontology/knora-base#Representation","occurrence":"0-n","pid":"http://www.knora.org/ontology/knora-base#hasRepresentation"},"http://www.knora.org/ontology/incunabula#citation":{"regular_property":1,"guiorder":5,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Citation/reference","guielement":"textarea","attributes":"cols=60;wrap=soft;rows=3","occurrence":"0-n","pid":"http://www.knora.org/ontology/incunabula#citation"},"http://www.knora.org/ontology/incunabula#partOf":{"regular_property":1,"value_restype":["Book"],"guiorder":2,"value_firstprops":["Bereitung zu dem Heiligen Sakrament"],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"is a part of","value_iconsrcs":["http://localhost:3335/project-icons/incunabula/book.gif"],"guielement":"searchbox","attributes":"restypeid=http://www.knora.org/ontology/incunabula#book","occurrence":"1","value_ids":["http://data.knora.org/1a01fe39e701/values/a749ba73-336a-4b7d-b40a-9d89a67844a1"],"value_rights":[1],"pid":"http://www.knora.org/ontology/incunabula#partOf","values":["http://data.knora.org/9311a421b501"],"comments":[""]},"http://www.knora.org/ontology/incunabula#pagenum":{"regular_property":1,"value_restype":[null],"guiorder":1,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Page identifier","value_iconsrcs":[null],"guielement":"text","attributes":"min=4;max=8","occurrence":"0-1","value_ids":["http://data.knora.org/1a01fe39e701/values/a0ad26e19507"],"value_rights":[2],"pid":"http://www.knora.org/ontology/incunabula#pagenum","values":[{"utf8str":"o6r","textattr":"{}","resource_reference":[]}],"comments":[""]},"http://www.knora.org/ontology/incunabula#transcription":{"regular_property":1,"guiorder":12,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Transkription","guielement":"pulldown","attributes":"hlist=<http://data.knora.org/lists/4b6d86ce03>","occurrence":"0-n","pid":"http://www.knora.org/ontology/incunabula#transcription"},"__location__":{"locations":[{"duration":0,"nx":86,"path":"http://localhost:1024/knora/incunabula_0000002130.jpg/full/full/0/default.jpg","ny":128,"fps":0,"format_name":"JPEG","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":43,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/43,63/0/default.jpg","ny":63,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":85,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/85,127/0/default.jpg","ny":127,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":170,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/170,254/0/default.jpg","ny":254,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":340,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/340,507/0/default.jpg","ny":507,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":680,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/680,1014/0/default.jpg","ny":1014,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"},{"duration":0,"nx":1360,"path":"http://localhost:1024/knora/incunabula_0000002130.jp2/full/1360,2028/0/default.jpg","ny":2028,"fps":0,"format_name":"JPEG2000","origname":"IBB_1_002647120_0219.tif","protocol":"file"}],"regular_property":1,"guiorder":2147483647,"is_annotation":"0","valuetype_id":"-1","label":null,"guielement":"fileupload","attributes":"","occurrence":null,"value_ids":["0"],"pid":"__location__","values":[0],"comments":["0"]},"http://www.knora.org/ontology/incunabula#hasRightSideband":{"regular_property":1,"guiorder":11,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"Randleistentyp rechts","guielement":"searchbox","attributes":"numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband","occurrence":"0-1","pid":"http://www.knora.org/ontology/incunabula#hasRightSideband"},"http://www.knora.org/ontology/incunabula#hasLeftSideband":{"regular_property":1,"guiorder":10,"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#LinkValue","label":"Randleistentyp links","guielement":"searchbox","attributes":"numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband","occurrence":"0-1","pid":"http://www.knora.org/ontology/incunabula#hasLeftSideband"},"http://www.knora.org/ontology/incunabula#description":{"regular_property":1,"value_restype":[null],"guiorder":2,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Beschreibung (Richtext)","value_iconsrcs":[null],"guielement":"richtext","attributes":"","occurrence":"0-1","value_ids":["http://data.knora.org/1a01fe39e701/values/883493f1ce25"],"value_rights":[2],"pid":"http://www.knora.org/ontology/incunabula#description","values":[{"utf8str":"Text.\nHolzschnitt:\nVerzweiflung des Petrus, 6.7 x 5.2 cm, unkoloriert.\nRubrizierung in Rot.\nFehlstelle im Druckstock vom rechten Rand bis ins Gesicht. \nKolorierung in Rot im Gesicht. \nBildnummerierung (Graphitstift) rechts unter dem Holzschnitt: 36.\nBlattnummerierung (Graphitstift) oben rechts: \"110 resp. 109\".","textattr":"{}","resource_reference":[]}],"comments":[""]},"http://www.knora.org/ontology/incunabula#page_comment":{"regular_property":1,"value_restype":[null,null],"guiorder":6,"value_firstprops":[null,null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Comment","value_iconsrcs":[null,null],"guielement":"textarea","attributes":"wrap=soft;width=95%;rows=7","occurrence":"0-n","value_ids":["http://data.knora.org/1a01fe39e701/values/4b5ee62acf25","http://data.knora.org/1a01fe39e701/values/7758ab1b0829"],"value_rights":[2,2],"pid":"http://www.knora.org/ontology/incunabula#page_comment","values":[{"utf8str":"Das Rot der Kolorierung im Gesicht stimmt mit der Farbe der Rubrizierung überein. ","textattr":"{}","resource_reference":[]},{"utf8str":"Schramm, Bd. 22, Abb. 455.","textattr":"{}","resource_reference":[]}],"comments":["",""]},"http://www.knora.org/ontology/incunabula#origname":{"regular_property":1,"value_restype":[null],"guiorder":7,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#TextValue","label":"Ursprünglicher Dateiname","value_iconsrcs":[null],"guielement":"text","attributes":"size=54;maxlength=128","occurrence":"1","value_ids":["http://data.knora.org/1a01fe39e701/values/e92a208d9607"],"value_rights":[2],"pid":"http://www.knora.org/ontology/incunabula#origname","values":[{"utf8str":"IBB_1_002647120_0219.tif","textattr":"{}","resource_reference":[]}],"comments":[""]},"http://www.knora.org/ontology/incunabula#seqnum":{"regular_property":1,"value_restype":[null],"guiorder":3,"value_firstprops":[null],"is_annotation":"0","valuetype_id":"http://www.knora.org/ontology/knora-base#IntValue","label":"Sequence number","value_iconsrcs":[null],"guielement":"spinbox","attributes":"min=0;max=-1","occurrence":"0-1","value_ids":["http://data.knora.org/1a01fe39e701/values/2601cd539607"],"value_rights":[2],"pid":"http://www.knora.org/ontology/incunabula#seqnum","values":[219],"comments":[""]}},"access":"OK"};