import { JsonCustomConvert } from "../../../json2typescript";

export class GroupsPerProjectConverter implements JsonCustomConvert<{ [key: string]: string[] }> {
    serialize(groups: { [key: string]: string[] }): any {
        return {};
    }

    deserialize(item: any): { [key: string]: string[] } {
        return {};
    }
}
