import {JsonConverter, JsonCustomConvert} from "json2typescript";

import {Permission} from "../permission";

@JsonConverter
export class AdministrativePermissionsPerProjectConverter implements JsonCustomConvert<{ [key: string]: Permission[] }> {

    serialize(permissions: { [key: string]: Permission[] }): any {
        return {};
    }

    deserialize(item: any): { [key: string]: Permission[] } {
        return {};
    }
}
