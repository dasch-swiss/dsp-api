// basic response members
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
    userdata: any;
}

// resource full response
interface resourceFullResponse extends basicResponse {
    /**
     * Description of the resource and its class
     */
    resinfo: resinfo;
}

interface prop {
    valuetype: string;
}

interface region {
    [index: string]: prop;
}

// digital representation / location of a resource
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
    path: URL;
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
     * Protocol
     */
    protocol: protocol;
}

// resource info
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
    person_id: URL;
    /**
     * Points to the parent resource in case the resource depends on it
     */
    value_of: URL|number;
    /**
     * The given user's permissions on the resource
     */
    permissions: any;
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
    regions?: Array<region>;
    /**
     * Description of the resource type
     */
    restype_description: string;
    /**
     * The project IRI the resource belongs to
     */
    project_id: URL;
    /**
     * Full quality representation of the resource
     */
    locdata: locationItem;
    /**
     * The Knora IRI identifying the resource
     */
    restype_id: URL;
}

enum protocol {
    /**
     * Local file in Knora
     */
    file,
    /**
     * Reference to external location
     */
    url
}

