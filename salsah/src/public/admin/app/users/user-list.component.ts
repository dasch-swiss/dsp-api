/*
 Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 Tobias Schweizer, André Kilchenmann, and André Fatton.

 This file is part of Knora.

 Knora is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published
 by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Knora is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public
 License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import {Component, OnInit} from "angular2/core";
import {UserService} from "./user.service";
import {User} from "./user";
import {UserComponent} from "./user.component";

@Component({
    template: `
                <h2>Users</h2>
                {{ title }}
                <ul>
                    <li *ngFor="#user of users"
                        (click)="onSelect(user)"
                        [class.clicked]="selectedUser===user"
                    >
                    {{ user.userId }}
                    </li>
                </ul>
                <user *ngIf="selectedUser !== null" [user]="selectedUser"></user>
              `,
    directives: [UserComponent],
    providers: [UserService],
    styleUrls: ["../../assets/user-list.css"]
})
export class UserListComponent implements OnInit {
    title: string = "List of Users";
    public users: User[];
    public selectedUser = null;

    constructor(private _userService: UserService) {}

    onSelect(user) {
        this.selectedUser = user;
    }

    getUsers() {
        this._userService.getUsers().then( (users: User[]) => this.users = users );
    }


    ngOnInit():any {
        this.getUsers();
    }
}