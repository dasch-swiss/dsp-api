import {Component} from "angular2/core";
import {InstitutionService} from "./institution.service"

@Component({
    selector: 'institutions',
    template: `
                <h2>Institutions</h2>
                {{ title }}
                <ul>
                    <li *ngFor="#institution of institutions">
                    {{ institution }}
                    </li>
                </ul>
              `,
    providers: [InstitutionService]
})
export class InstitutionsComponent {
    title: string = "Title of Institutions Page";
    institutions: string[];

    constructor(institutionService: InstitutionService) {
        this.institutions = institutionService.getInstitutions()
    }
}