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
import {ProjectService} from "./Project.service"

@Component({
    selector: 'projects',
    template: `
                <h2>Projects</h2>
                {{ title }}
                <ul>
                    <li *ngFor="#project of projects">
                    {{ project }}
                    </li>
                </ul>
              `,
    providers: [ProjectService]
})
export class ProjectsComponent {
    title: string = "Title of Projects Page";
    projects: string[];

    constructor(projectService: ProjectService) {
        this.projects = projectService.getProjects()
    }
}