/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import { sessionResponseFormats } from "../sessionResponseFormats"

let login: sessionResponseFormats.loginResponse = {
  "status": 0,
  "message": "credentials are OK",
  "sid": "1489417991855",
  "userProfile": {
    "userData": {
      "email": "multi.user@example.com",
      "firstname": "Multi",
      "user_id": "http://data.knora.org/users/multiuser",
      "lastname": "User",
      "isActiveUser": true,
      "token": null,
      "lang": "de",
      "password": null
    },
    "groups": [
      "http://data.knora.org/groups/images-reviewer"
    ],
    "projects_info": {
      "http://data.knora.org/projects/images": {
        "shortname": "images",
        "description": "A demo project of a collection of images",
        "belongsToInstitution": null,
        "logo": null,
        "dataNamedGraph": "http://www.knora.org/data/images",
        "id": "http://data.knora.org/projects/images",
        "status": true,
        "keywords": "images, collection",
        "longname": "Image Collection Demo",
        "ontologyNamedGraph": "http://www.knora.org/ontology/images",
        "hasSelfJoinEnabled": false
      },
      "http://data.knora.org/projects/77275339": {
        "shortname": "incunabula",
        "description": "<p>Das interdisziplin\u00e4re Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Fr\u00fchdrucke: Sp\u00e4tmittelalterliche Didaxe als Bild-Text-Lekt\u00fcre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bez\u00fcge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Best\u00e4nde der Universit\u00e4tsbibliothek und der Entwicklung einer elektronischen Edition.</p>",
        "belongsToInstitution": null,
        "logo": "incunabula_logo.png",
        "dataNamedGraph": "http://www.knora.org/data/incunabula",
        "id": "http://data.knora.org/projects/77275339",
        "status": true,
        "keywords": "Basler Fr\u00fchdrucke, Inkunabel, Narrenschiff, Wiegendrucke, Sebastian Brant, Bilderfolgen, early print, incunabula, ship of fools, Kunsthistorischs Seminar Universit\u00e4t Basel, Late Middle Ages, Letterpress Printing, Basel, Contectualisation of images",
        "longname": "Bilderfolgen Basler Fr\u00fchdrucke",
        "ontologyNamedGraph": "http://www.knora.org/ontology/incunabula",
        "hasSelfJoinEnabled": false
      }
    },
    "sessionId": null,
    "isSystemUser": false,
    "permissionData": {
      "groupsPerProject": {
        "http://data.knora.org/projects/77275339": [
          "http://www.knora.org/ontology/knora-base#ProjectMember",
          "http://www.knora.org/ontology/knora-base#ProjectAdmin"
        ],
        "http://data.knora.org/projects/images": [
          "http://data.knora.org/groups/images-reviewer",
          "http://www.knora.org/ontology/knora-base#ProjectMember",
          "http://www.knora.org/ontology/knora-base#ProjectAdmin"
        ]
      },
      "administrativePermissionsPerProject": {
        "http://data.knora.org/projects/77275339": [
          {
            "name": "ProjectAdminAllPermission",
            "additionalInformation": null,
            "v1Code": null
          },
          {
            "name": "ProjectResourceCreateAllPermission",
            "additionalInformation": null,
            "v1Code": null
          }
        ],
        "http://data.knora.org/projects/images": [
          {
            "name": "ProjectAdminAllPermission",
            "additionalInformation": null,
            "v1Code": null
          },
          {
            "name": "ProjectResourceCreateAllPermission",
            "additionalInformation": null,
            "v1Code": null
          }
        ]
      },
      "defaultObjectAccessPermissionsPerProject": {
        "http://data.knora.org/projects/77275339": [
          {
            "name": "RV",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#UnknownUser",
            "v1Code": 1
          },
          {
            "name": "CR",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#Creator",
            "v1Code": 8
          },
          {
            "name": "V",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#KnownUser",
            "v1Code": 2
          },
          {
            "name": "M",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#ProjectMember",
            "v1Code": 6
          }
        ],
        "http://data.knora.org/projects/images": [
          {
            "name": "CR",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#Creator",
            "v1Code": 8
          },
          {
            "name": "V",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#KnownUser",
            "v1Code": 2
          },
          {
            "name": "M",
            "additionalInformation": "http://www.knora.org/ontology/knora-base#ProjectMember",
            "v1Code": 6
          }
        ]
      }
    }
  }
}
