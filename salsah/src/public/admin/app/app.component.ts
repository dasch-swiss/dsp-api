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
import {UserListComponent} from "./users/user-list.component";
import {ROUTER_DIRECTIVES, RouteConfig} from "angular2/router";
import {NewUserComponent} from "./users/new-user.component";

@Component({
    selector: 'my-app',
    template: `
        <div class="main">
            <h1>KNORA Administration Interface</h1>
        </div>
        <header>
            <nav>
                <a [routerLink]="['Users']">Users</a>
                <a [routerLink]="['NewUser']">New User</a>
            </nav>
        </header>
        <div class="main">
            <router-outlet></router-outlet>
        </div>
    `,
    directives: [ROUTER_DIRECTIVES]
})
@RouteConfig([
    {path: "/users", name: "Users", component: UserListComponent, useAsDefault: true},
    {path: "/newusers", name: "NewUser", component: NewUserComponent}
])
export class AppComponent { }