import { KnoraApiConfig } from "./knora-api-config";

import { AdminApi } from "./api/admin/admin-api-endpoint";

/**
 * Offers methods for JavaScript developers to interact with the Knora API.
 */
export class KnoraApiConnection {
    
    /**
     * A client API for administering Knora.
     */
    readonly adminApi: AdminApi;
    
    /**
     * Constructor.
     * Sets up all endpoints for the Knora API.
     *
     * @param knoraApiConfig
     */
    constructor(knoraApiConfig: KnoraApiConfig) {

        // Instantiate the endpoints
        
        this.adminApi = new AdminApi(knoraApiConfig, "/admin");
        

    }
}
