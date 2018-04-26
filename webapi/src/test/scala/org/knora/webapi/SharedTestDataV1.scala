/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionsDataADM}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}

/**
  * This object holds the same user which are loaded with '_test_data/all_data/admin-data.ttl'. Using this object
  * in tests, allows easier updating of details as they change over time.
  */
object SharedTestDataV1 {

    /** ***********************************/
    /** System Admin Data                **/
    /** ***********************************/

    val SYSTEM_PROJECT_IRI: IRI = OntologyConstants.KnoraBase.SystemProject // built-in project

    /* represents the user profile of 'root' as found in admin-data.ttl */
    def rootUser: UserProfileV1 = SharedTestDataADM.rootUser.asUserProfileV1

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def superUser: UserProfileV1 = SharedTestDataADM.superUser.asUserProfileV1

    /* represents the user profile of 'normal user' as found in admin-data.ttl */
    def normalUser: UserProfileV1 = SharedTestDataADM.normalUser.asUserProfileV1

    /* represents the user profile of 'inactive user' as found in admin-data.ttl */
    def inactiveUser: UserProfileV1 = SharedTestDataADM.inactiveUser.asUserProfileV1

    /* represents an anonymous user */
    def anonymousUser: UserProfileV1 = SharedTestDataADM.anonymousUser.asUserProfileV1


    /* represents the 'multiuser' as found in admin-data.ttl */
    def multiuserUser = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/multiuser"),
            firstname = Some("Multi"),
            lastname = Some("User"),
            email = Some("multi.user@example.com"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = List("http://rdfh.ch/groups/00FF/images-reviewer"),
        projects_info = Map(INCUNABULA_PROJECT_IRI -> incunabulaProjectInfo, IMAGES_PROJECT_IRI -> imagesProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def systemProjectInfo: ProjectInfoV1 = SharedTestDataADM.systemProject.asProjectInfoV1.copy(ontologies = Seq(OntologyConstants.KnoraBase.KnoraBaseOntologyIri))


    /** ***********************************/
    /** Images Demo Project Admin Data   **/
    /** ***********************************/

    val IMAGES_PROJECT_IRI = "http://rdfh.ch/projects/00FF"

    /* represents 'user01' as found in admin-data.ttl  */
    def imagesUser01 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/c266a56709"),
            firstname = Some("User01"),
            lastname = Some("User"),
            email = Some("user01.user1@example.com"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = List.empty[IRI],
        projects_info = Map(IMAGES_PROJECT_IRI -> imagesProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def imagesUser02 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/97cec4000f"),
            firstname = Some("User02"),
            lastname = Some("User"),
            email = Some("user02.user@example.com"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = List.empty[IRI],
        projects_info = Map(IMAGES_PROJECT_IRI -> imagesProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def imagesReviewerUser = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/images-reviewer-user"),
            firstname = Some("User03"),
            lastname = Some("User"),
            email = Some("images-reviewer-user@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = List("http://rdfh.ch/groups/00FF/images-reviewer"),
        projects_info = Map(IMAGES_PROJECT_IRI -> imagesProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def imagesProjectInfo = ProjectInfoV1(
        id = IMAGES_PROJECT_IRI,
        shortname = "images",
        shortcode = "00FF",
        longname = Some("Image Collection Demo"),
        description = Some("A demo project of a collection of images"),
        keywords = Some("collection, images"),
        logo = None,
        institution = Some("http://rdfh.ch/institutions/dhlab-basel"),
        ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI),
        status = true,
        selfjoin = false
    )


    /** ***********************************/
    /** Incunabula Project Admin Data    **/
    /** ***********************************/

    val INCUNABULA_PROJECT_IRI = "http://rdfh.ch/projects/0803"

    /* represents 'testuser' (Incunabula ProjectAdmin) as found in admin-data.ttl  */
    def incunabulaProjectAdminUser = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/b83acc5f05"),
            firstname = Some("User"),
            lastname = Some("Test"),
            email = Some("user.test@example.com"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects_info = Map(INCUNABULA_PROJECT_IRI -> incunabulaProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def incunabulaCreatorUser = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/91e19f1e01"),
            firstname = Some("Administrator-alt"),
            lastname = Some("Admin-alt"),
            email = Some("root-alt@example.com"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects_info = Map(INCUNABULA_PROJECT_IRI -> incunabulaProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def incunabulaMemberUser = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/incunabulaMemberUser"),
            firstname = Some("User"),
            lastname = Some("Test2"),
            email = Some("test.user2@test.ch"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects_info = Map(INCUNABULA_PROJECT_IRI -> incunabulaProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
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
    def incunabulaProjectInfo = ProjectInfoV1(
        id = INCUNABULA_PROJECT_IRI,
        shortname = "incunabula",
        shortcode = "0803",
        longname = Some("Bilderfolgen Basler Frühdrucke"),
        description = Some("<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>"),
        keywords = Some("Basel, Basler Frühdrucke, Bilderfolgen, Contectualisation of images, Inkunabel, Kunsthistorisches Seminar Universität Basel, Late Middle Ages, Letterpress Printing, Narrenschiff, Sebastian Brant, Wiegendrucke, early print, incunabula, ship of fools"),
        logo = Some("incunabula_logo.png"),
        institution = None,
        ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI),
        status = true,
        selfjoin = false
    )

    /** **********************************/
    /** Anything Admin Data             **/
    /** **********************************/

    val ANYTHING_PROJECT_IRI = "http://rdfh.ch/projects/0001"

    def anythingAdminUser = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/AnythingAdminUser"),
            firstname = Some("Anything"),
            lastname = Some("Admin"),
            email = Some("anything.admin@example.org"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = Seq.empty[IRI],
        projects_info = Map(ANYTHING_PROJECT_IRI -> anythingProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
            groupsPerProject = Map(
                ANYTHING_PROJECT_IRI -> List(OntologyConstants.KnoraBase.ProjectMember, OntologyConstants.KnoraBase.ProjectAdmin)
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    def anythingUser1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"),
            firstname = Some("Anything"),
            lastname = Some("User01"),
            email = Some("anything.user01@example.org"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = Seq.empty[IRI],
        projects_info = Map(ANYTHING_PROJECT_IRI -> anythingProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
            groupsPerProject = Map(
                ANYTHING_PROJECT_IRI -> List(OntologyConstants.KnoraBase.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    def anythingUser2 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"),
            firstname = Some("Anything"),
            lastname = Some("User02"),
            email = Some("anything.user02@example.org"),
            password = Some("$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="), // -> "test"
            token = None,
            status = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects_info = Map(ANYTHING_PROJECT_IRI -> anythingProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM(
            groupsPerProject = Map(
                ANYTHING_PROJECT_IRI -> List(OntologyConstants.KnoraBase.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        )
    )

    def anythingProjectInfo = ProjectInfoV1(
        id = ANYTHING_PROJECT_IRI,
        shortname = "anything",
        shortcode = "0001",
        longname = Some("Anything Project"),
        description = Some("Anything Project"),
        keywords = None,
        logo = None,
        institution = None,
        ontologies = Seq("http://www.knora.org/ontology/0001/anything", "http://www.knora.org/ontology/0001/something"),
        status = true,
        selfjoin = false
    )


    /** **********************************/
    /** BEOL                            **/
    /** **********************************/

    val BEOL_PROJECT_IRI = "http://rdfh.ch/projects/0801"

    def beolProjectInfo = ProjectInfoV1(
        id = BEOL_PROJECT_IRI,
        shortname = "beol",
        shortcode = "0801",
        longname = Some("Bernoulli-Euler Online"),
        description = Some("Bernoulli-Euler Online"),
        keywords = None,
        logo = None,
        institution = None,
        ontologies = Seq("http://www.knora.org/ontology/0801/beol"),
        status = true,
        selfjoin = false
    )


    /** **********************************/
    /** BIBLIO                          **/
    /** **********************************/

    val BIBLIO_PROJECT_IRI = "http://rdfh.ch/projects/DczxPs-sR6aZN91qV92ZmQ"

    def biblioProjectInfo = ProjectInfoV1(
        id = BIBLIO_PROJECT_IRI,
        shortname = "biblio",
        shortcode = "0802",
        longname = Some("Bibliography"),
        description = Some("Bibliography"),
        keywords = None,
        logo = None,
        institution = None,
        ontologies = Seq("http://www.knora.org/ontology/0802/biblio"),
        status = true,
        selfjoin = false
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def biblioUser = UserProfileV1(
        UserDataV1(
            user_id = Some("http://rdfh.ch/users/Q-6Sssu8TBWrcCGuVJ0lVw"),
            firstname = Some("biblio"),
            lastname = Some("biblio"),
            email = Some("biblio@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            status = Some(true),
            lang = "en"
        ),
        groups = Vector.empty[IRI],
        projects_info = Map(BIBLIO_PROJECT_IRI -> biblioProjectInfo),
        sessionId = None,
        permissionData = PermissionsDataADM()
    )

    /** **********************************/
    /** DOKUBIB                         **/
    /** **********************************/

    val DOKUBIB_PROJECT_IRI = "http://rdfh.ch/projects/00FE"

    def dokubibProjectInfo = ProjectInfoV1(
        id = DOKUBIB_PROJECT_IRI,
        shortname = "dokubib",
        shortcode = "0804",
        longname = Some("Dokubib"),
        description = Some("Dokubib"),
        keywords = None,
        logo = None,
        institution = None,
        ontologies = Seq("http://www.knora.org/ontology/0804/dokubib"),
        status = false,
        selfjoin = false
    )

    /** **********************************/
    /** WEBERN                          **/
    /** **********************************/

    val WEBERN_PROJECT_IRI = "http://rdfh.ch/projects/08AE"

    def webernProjectInfo = ProjectInfoV1(
        id = WEBERN_PROJECT_IRI,
        shortname = "webern",
        shortcode = "08AE",
        longname = Some("Anton Webern Gesamtausgabe"),
        description = Some("Historisch-kritische Edition des Gesamtschaffens von Anton Webern."),
        keywords = None,
        logo = None,
        institution = None,
        ontologies = Seq("http://www.knora.org/ontology/08AE/webern"),
        status = false,
        selfjoin = false
    )

}
