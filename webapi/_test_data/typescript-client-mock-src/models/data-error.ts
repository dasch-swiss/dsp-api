import { ApiResponseError } from "./api-response-error";

/**
 * Generic error class for API responses where the format was incorrect.
 */
export class DataError extends Error {

    /**
     * Constructor.
     */
    constructor(message: string, public response: ApiResponseError) {

        super(message);

    }

}
