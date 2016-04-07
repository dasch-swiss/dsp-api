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