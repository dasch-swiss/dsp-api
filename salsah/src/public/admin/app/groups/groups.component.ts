

import {Component} from "angular2/core";
import {GroupService} from "./group.service"

@Component({
    selector: 'groups',
    template: `
                <h2>Groups</h2>
                {{ title }}
                <ul>
                    <li *ngFor="#group of groups">
                    {{ group }}
                    </li>
                </ul>
              `,
    providers: [GroupService]
})
export class GroupsComponent {
    title: string = "Title of Groups Page";
    groups: string[];

    constructor(groupService: GroupService) {
        this.groups = groupService.getGroups()
    }
}