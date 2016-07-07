

export module basicResponseComponents {

    /**
     * Basic members of the Knora API V1 response format.
     */
    export interface basicResponse {
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
    export interface userdata {
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
        active_project?:string;

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

}