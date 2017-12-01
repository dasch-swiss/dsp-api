/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
    "status":0,
    "message":"credentials are OK",
    "sid":"bb8e83ce-353d-43da-9913-5d76a0f03f6c",
    "userProfile":{
        "userData":{
            "email":"multi.user@example.com",
            "firstname":"Multi",
            "user_id":"http://data.knora.org/users/multiuser",
            "lastname":"User",
            "status":true,
            "token":null,
            "lang":"de",
            "password":null
        },
        "groups":[
            "http://data.knora.org/groups/images-reviewer"
        ],
        "projects_info":{
            "http://data.knora.org/projects/images":{
                "shortname":"images",
                "description":"A demo project of a collection of images",
                "institution":null,
                "logo":null,
                "id":"http://data.knora.org/projects/images",
                "status":true,
                "selfjoin":false,
                "keywords":"images, collection",
                "longname":"Image Collection Demo",
                "ontologies":["http://www.knora.org/ontology/images"]
            },
            "http://data.knora.org/projects/77275339":{
                "shortname":"incunabula",
                "description":"<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>",
                "institution":null,
                "logo":"incunabula_logo.png",
                "id":"http://data.knora.org/projects/77275339",
                "status":true,
                "selfjoin":false,
                "keywords":"Basler Frühdrucke, Inkunabel, Narrenschiff, Wiegendrucke, Sebastian Brant, Bilderfolgen, early print, incunabula, ship of fools, Kunsthistorischs Seminar Universität Basel, Late Middle Ages, Letterpress Printing, Basel, Contectualisation of images",
                "longname":"Bilderfolgen Basler Frühdrucke",
                "ontologies": [ "http://www.knora.org/ontology/incunabula" ]
            },
            "http://data.knora.org/projects/anything":{
                "shortname":"anything",
                "description":"Anything Project",
                "institution":null,
                "logo":null,
                "id":"http://data.knora.org/projects/anything",
                "status":true,
                "selfjoin":false,
                "keywords":null,
                "longname":"Anything Project",
                "ontologies":["http://www.knora.org/ontology/anything"]
            }
        },
        "sessionId":null,
        "isSystemUser":false,
        "permissionData":{
            "groupsPerProject":{
                "http://data.knora.org/projects/77275339":[
                    "http://www.knora.org/ontology/knora-base#ProjectMember",
                    "http://www.knora.org/ontology/knora-base#ProjectAdmin"
                ],
                "http://data.knora.org/projects/images":[
                    "http://data.knora.org/groups/images-reviewer",
                    "http://www.knora.org/ontology/knora-base#ProjectMember",
                    "http://www.knora.org/ontology/knora-base#ProjectAdmin"
                ],
                "http://data.knora.org/projects/anything":[
                    "http://www.knora.org/ontology/knora-base#ProjectMember",
                    "http://www.knora.org/ontology/knora-base#ProjectAdmin"
                ]
            },
            "administrativePermissionsPerProject":{
                "http://data.knora.org/projects/77275339":[
                    {
                        "name":"ProjectAdminAllPermission",
                        "additionalInformation":null,
                        "v1Code":null
                    },
                    {
                        "name":"ProjectResourceCreateAllPermission",
                        "additionalInformation":null,
                        "v1Code":null
                    }
                ],
                "http://data.knora.org/projects/images":[
                    {
                        "name":"ProjectAdminAllPermission",
                        "additionalInformation":null,
                        "v1Code":null
                    },
                    {
                        "name":"ProjectResourceCreateAllPermission",
                        "additionalInformation":null,
                        "v1Code":null
                    }
                ],
                "http://data.knora.org/projects/anything":[
                    {
                        "name":"ProjectAdminAllPermission",
                        "additionalInformation":null,
                        "v1Code":null
                    },
                    {
                        "name":"ProjectResourceCreateAllPermission",
                        "additionalInformation":null,
                        "v1Code":null
                    }
                ]
            },
            "anonymousUser":false
        }
    }
};