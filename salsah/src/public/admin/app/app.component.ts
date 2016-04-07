import {Component} from 'angular2/core';
import {UsersComponent} from "./users/users.component";
import {GroupsComponent} from "./groups/groups.component";
import {ProjectsComponent} from "./projects/projects.component";
import {InstitutionsComponent} from "./institutions/institutions.component";

@Component({
    selector: 'my-app',
    template: '<h1>KNORA Administration Interface</h1><users></users><groups></groups><projects></projects><institutions></institutions>',
    directives: [UsersComponent, GroupsComponent, ProjectsComponent, InstitutionsComponent]
})
export class AppComponent { }