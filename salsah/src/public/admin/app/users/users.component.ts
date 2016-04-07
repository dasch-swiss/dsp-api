/**
 * Created by subotic on 06.04.16.
 */

import {Component} from "angular2/core";
import {UserService} from "./user.service"

@Component({
    selector: 'users',
    template: `
                <h2>Users</h2>
                {{ title }}
                <ul>
                    <li *ngFor="#user of users">
                    {{ user }}
                    </li>
                </ul>
              `,
    providers: [UserService]
})
export class UsersComponent {
    title: string = "Title of Users Page";
    users: string[];

    constructor(userService: UserService) {
        this.users = userService.getUsers()
    }
}