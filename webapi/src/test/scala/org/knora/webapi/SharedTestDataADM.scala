/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoShortADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionsDataADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

/**
  * This object holds the same user which are loaded with '_test_data/all_data/admin-data.ttl'. Using this object
  * in tests, allows easier updating of details as they change over time.
  */
object SharedTestDataADM {

    /** ***********************************/
    /** System Admin Data                **/
    /** ***********************************/

    val SYSTEM_PROJECT_IRI = OntologyConstants.KnoraBase.SystemProject // built-in project

    /* represents the user profile of 'root' as found in admin-data.ttl */
    def rootUser = UserADM(
        id = "http://rdfh.ch/users/root",
        email = "root@example.com",
        password = Option("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "System",
        familyName = "Administrator",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                SYSTEM_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.SystemAdmin}")
            ),
            administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]]
        )
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def superUser = UserADM(
        id = "http://rdfh.ch/users/superuser",
        email = "super.user@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Super",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                SYSTEM_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.SystemAdmin}")
            )
        )
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def normalUser = UserADM(
        id = "http://rdfh.ch/users/normaluser",
        email = "normal.user@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Normal",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM()
    )

    /* represents the user profile of 'inactive user' as found in admin-data.ttl */
    def inactiveUser = UserADM(
        id = "http://rdfh.ch/users/inactiveuser",
        email = "inactive.user@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Inactive",
        familyName = "User",
        status = false,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM()
    )

    /* represents an anonymous user */
    def anonymousUser = KnoraSystemInstances.Users.AnonymousUser


    /* represents the 'multiuser' as found in admin-data.ttl */
    def multiuserUser = UserADM(
        id = "http://rdfh.ch/users/multiuser",
        email = "multi.user@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Multi",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq(imagesReviewerGroup),
        projects = Seq(incunabulaProject, imagesProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.ProjectMember}", s"${OntologyConstants.KnoraBase.ProjectAdmin}"),
                IMAGES_PROJECT_IRI -> List("http://rdfh.ch/groups/00FF/images-reviewer", s"${OntologyConstants.KnoraBase.ProjectMember}", s"${OntologyConstants.KnoraBase.ProjectAdmin}")
            ),
            administrativePermissionsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission

                ),
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    /* represents the full project info of the Knora System project */
    def systemProject = KnoraSystemInstances.Projects.SystemProject


    /*************************************/
    /** Images Demo Project Admin Data  **/
    /*************************************/

    val IMAGES_PROJECT_IRI = "http://rdfh.ch/projects/00FF"

    /* represents 'user01' as found in admin-data.ttl  */
    def imagesUser01 = UserADM(
        id = "http://rdfh.ch/users/c266a56709",
        email = "user01.user1@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "User01",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(imagesProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                IMAGES_PROJECT_IRI -> List(OntologyConstants.KnoraBase.ProjectMember, OntologyConstants.KnoraBase.ProjectAdmin)
            ),
            administrativePermissionsPerProject = Map(
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    /* represents 'user02' as found in admin-data.ttl  */
    def imagesUser02 = UserADM(
        id = "http://rdfh.ch/users/97cec4000f",
        email = "user02.user@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "User02",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(imagesProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                IMAGES_PROJECT_IRI -> List(OntologyConstants.KnoraBase.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    /* represents 'images-reviewer-user' as found in admin-data.ttl  */
    def imagesReviewerUser = UserADM(
        id = "http://rdfh.ch/users/images-reviewer-user",
        email = "images-reviewer-user@example.com",
        password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
        token = None,
        givenName = "User03",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq(imagesReviewerGroup),
        projects = Seq(imagesProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                IMAGES_PROJECT_IRI -> List("http://rdfh.ch/groups/00FF/images-reviewer", s"${OntologyConstants.KnoraBase.ProjectMember}")
            ),
            administrativePermissionsPerProject = Map(
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bild"),
                    PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bildformat")
                )
            )
        )
    )

    /* represents the full project info of the images project */
    def imagesProject: ProjectADM = ProjectADM(
        id = IMAGES_PROJECT_IRI,
        shortname = "images",
        shortcode = Some("00FF"),
        longname = Some("Image Collection Demo"),
        description = Some("A demo project of a collection of images"),
        keywords = Seq("images", "collection").sorted,
        logo = None,
        ontologies = Seq(OntologyInfoShortADM(
            ontologyIri = SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI,
            ontologyName = "images"
        )),
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the images ProjectAdmin group */
    def imagesProjectAdminGroup = GroupADM(
        id = "-",
        name = "ProjectAdmin",
        description = "Default Project Admin Group",
        project = imagesProject,
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the images ProjectMember group */
    def imagesProjectMemberGroup = GroupADM(
        id = "-",
        name = "ProjectMember",
        description = "Default Project Member Group",
        project = imagesProject,
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the images project reviewer group */
    def imagesReviewerGroup = GroupADM(
        id = "http://rdfh.ch/groups/00FF/images-reviewer",
        name = "Image reviewer",
        description = "A group for image reviewers.",
        project = imagesProject,
        status = true,
        selfjoin = false
    )


    /*************************************/
    /** Incunabula Project Admin Data   **/
    /*************************************/

    val INCUNABULA_PROJECT_IRI = "http://rdfh.ch/projects/77275339"

    /* represents 'testuser' (Incunabula ProjectAdmin) as found in admin-data.ttl  */
    def incunabulaProjectAdminUser = UserADM(
        id = "http://rdfh.ch/users/b83acc5f05",
        email = "user.test@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "User",
        familyName = "Test",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(incunabulaProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.ProjectMember}", s"${OntologyConstants.KnoraBase.ProjectAdmin}")
            ),
            administrativePermissionsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    /* represents 'root-alt' (Incunabula ProjectMember) as found in admin-data.ttl  */
    def incunabulaCreatorUser: UserADM = UserADM(
        id = "http://rdfh.ch/users/91e19f1e01",
        email = "root-alt@example.com",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Administrator-alt",
        familyName = "Admin-alt",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(incunabulaProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.ProjectMember}")
            ),
            administrativePermissionsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    /* represents 'root-alt' (Incunabula Creator and ProjectMember) as found in admin-data.ttl  */
    def incunabulaMemberUser = UserADM(
        id = "http://rdfh.ch/users/incunabulaMemberUser",
        email = "user.test2t@test.ch",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "User",
        familyName = "Test2",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(incunabulaProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.ProjectMember}")
            ),
            administrativePermissionsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    /* represents the ProjectInfoV1 of the incunabula project */
    def incunabulaProject: ProjectADM = ProjectADM(
        id = INCUNABULA_PROJECT_IRI,
        shortname = "incunabula",
        shortcode = None,
        longname = Some("Bilderfolgen Basler Frühdrucke"),
        description = Some("<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>"),
        keywords = Seq("Basler Frühdrucke", "Inkunabel", "Narrenschiff", "Wiegendrucke", "Sebastian Brant", "Bilderfolgen", "early print", "incunabula", "ship of fools", "Kunsthistorisches Seminar Universität Basel", "Late Middle Ages", "Letterpress Printing", "Basel", "Contectualisation of images").sorted,
        logo = Some("incunabula_logo.png"),
        ontologies = Seq(OntologyInfoShortADM(
            ontologyIri = SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI,
            ontologyName = "incunabula"
        )),
        status = true,
        selfjoin = false
    )

    /************************************/
    /** Anything Admin Data            **/
    /************************************/

    val ANYTHING_PROJECT_IRI = "http://rdfh.ch/projects/anything"

    def anythingUser1: UserADM = UserADM(
        id = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        email = "anything.user01@example.org",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Anything",
        familyName = "User01",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(anythingProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                ANYTHING_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.ProjectMember}")
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )

    )

    def anythingUser2: UserADM = UserADM(
        id = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
        email = "anything.user02@example.org",
        password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
        token = None,
        givenName = "Anything",
        familyName = "User02",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(anythingProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                ANYTHING_PROJECT_IRI -> List(s"${OntologyConstants.KnoraBase.ProjectMember}")
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    def anythingProject: ProjectADM = ProjectADM(
        id = ANYTHING_PROJECT_IRI,
        shortname = "anything",
        shortcode = None,
        longname = Some("Anything Project"),
        description = Some("Anything Project"),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq(OntologyInfoShortADM(
            ontologyIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI,
            ontologyName = "anything"
        )),
        status = true,
        selfjoin = false
    )


    /************************************/
    /** BEOL                           **/
    /************************************/

    val BEOL_PROJECT_IRI = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"

    def beolProject = ProjectADM(
        id = BEOL_PROJECT_IRI,
        shortname = "beol",
        shortcode = None,
        longname = Some("Bernoulli-Euler Online"),
        description = Some("Bernoulli-Euler Online"),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq(OntologyInfoShortADM(
            ontologyIri = "http://www.knora.org/ontology/beol",
            ontologyName = "beol"
        )),
        status = true,
        selfjoin = false
    )


    /************************************/
    /** BIBLIO                         **/
    /************************************/

    val BIBLIO_PROJECT_IRI = "http://rdfh.ch/projects/DczxPs-sR6aZN91qV92ZmQ"

    def biblioProject = ProjectADM(
        id = BIBLIO_PROJECT_IRI,
        shortname = "biblio",
        shortcode = None,
        longname = Some("Bibliography"),
        description = Some("Bibliography"),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq(OntologyInfoShortADM(
            ontologyIri = "http://www.knora.org/ontology/biblio",
            ontologyName = "biblio"
        )),
        status = true,
        selfjoin = false
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def biblioUser = UserADM(
        id = "http://rdfh.ch/users/Q-6Sssu8TBWrcCGuVJ0lVw",
        email = "biblio@example.com",
        password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
        token = None,
        givenName = "biblio",
        familyName = "biblio",
        status = true,
        lang = "en",
        groups = Seq.empty[GroupADM],
        projects = Seq(biblioProject),
        sessionId = None,
        permissions = PermissionsDataADM()
    )

    /************************************/
    /** DOKUBIB                        **/
    /************************************/

    val DOKUBIB_PROJECT_IRI = "http://rdfh.ch/projects/00FE"

    def dokubibProject = ProjectADM(
        id = DOKUBIB_PROJECT_IRI,
        shortname = "dokubib",
        shortcode = Some("00FE"),
        longname = Some("Dokubib"),
        description = Some("Dokubib"),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq(OntologyInfoShortADM(
                ontologyIri = "http://www.knora.org/ontology/00FE/dokubib",
                ontologyName = "dokubib"
        )),
        status = false,
        selfjoin = false
    )

}
