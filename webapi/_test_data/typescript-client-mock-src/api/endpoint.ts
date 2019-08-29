import {KnoraApiConfig} from "../knora-api-config";
import {Observable, throwError} from "rxjs";
import {ajax, AjaxError, AjaxResponse} from "rxjs/ajax";
import {JsonConvert, OperationMode, ValueCheckingMode} from "json2typescript";
import {PropertyMatchingRule} from "json2typescript/src/json2typescript/json-convert-enums";
import {ApiResponseError} from "../models/api-response-error";
import {DataError} from "../models/data-error";

export class Endpoint {

    jsonConvert: JsonConvert = new JsonConvert(
        OperationMode.ENABLE,
        ValueCheckingMode.DISALLOW_NULL,
        false,
        PropertyMatchingRule.CASE_STRICT
    );

    constructor(protected readonly knoraApiConfig: KnoraApiConfig, protected readonly path: string) {
    }

    protected httpGet(path?: string): Observable<AjaxResponse> {

        if (path === undefined) path = '';

        return ajax.get(path);

    }

    protected httpPost(path?: string, body?: any): Observable<AjaxResponse> {

        if (path === undefined) path = '';

        return ajax.post(path, body);

    }

    /**
     * Performs a general PUT request.
     *
     * @param path the relative URL for the request
     * @param body the body of the request
     */
    protected httpPut(path?: string, body?: any): Observable<AjaxResponse> {

        if (path === undefined) path = '';

        return ajax.put(path, body);

    }

    /**
     * Performs a general PATCH request.
     *
     * @param path the relative URL for the request
     * @param body the body of the request
     */
    protected httpPatch(path?: string, body?: any): Observable<AjaxResponse> {

        if (path === undefined) path = '';

        return ajax.patch(path, body);

    }

    /**
     * Performs a general PUT request.
     *
     * @param path the relative URL for the request
     */
    protected httpDelete(path?: string): Observable<AjaxResponse> {

        if (path === undefined) path = '';

        return ajax.delete(path);

    }

    protected handleError(error: AjaxError | DataError): Observable<ApiResponseError> {
        return throwError(new ApiResponseError());
    }

}