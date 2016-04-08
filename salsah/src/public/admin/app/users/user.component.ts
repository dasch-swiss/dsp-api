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

import {Component} from "angular2/core";
import {User} from "./user";

@Component({
    selector: 'user',
    template: `
        <div>
            <div>
                <label for="user-name">Username:</label>
                <input [(ngModel)]="user.userName" type="text">
            </div>
            <div>
                <label for="given-name">Given Name:</label>
                <input [(ngModel)]="user.givenName" type="text">
            </div>
            <div>
                <label for="family-name">Family Name:</label>
                <input [(ngModel)]="user.familyName" type="text">
            </div>
            <div>
                <label for="email">Email:</label>
                <input [(ngModel)]="user.email" type="text">
            </div>
            <div>
                <label for="phone">Phone:</label>
                <input [(ngModel)]="user.phone" type="text">
            </div>
            <div>
                <label for="system-name-status">System Admin:</label>
                <input [(ngModel)]="user.isSystemAdmin" type="checkbox">
            </div>
            <div>
                <label for="user-status">Active User:</label>
                <input [(ngModel)]="user.isActiveUser" type="checkbox">
            </div>
        </div>
    `,
    inputs: ["user"],
    styles: [`
        label {
            display: inline-block;
            width: 110px;
        }
        input {
            width: 250px;
        }
    `]
})
export class UserComponent {
    public user: User;
}