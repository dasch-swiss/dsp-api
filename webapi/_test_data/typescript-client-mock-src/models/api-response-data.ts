import {AjaxResponse} from "rxjs/ajax";
import {JsonConvert} from "json2typescript";

export class ApiResponseData<T> {

    static fromAjaxResponse<T>(ajaxResponse: AjaxResponse,
                               dataType?: { new(): T },
                               jsonConvert?: JsonConvert): ApiResponseData<T> {

        return new ApiResponseData<T>();

    }

}