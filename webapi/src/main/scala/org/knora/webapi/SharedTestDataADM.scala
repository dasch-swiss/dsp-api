/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionsDataADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralWithLanguageV2

/**
  * This object holds the same user which are loaded with '_test_data/all_data/admin-data.ttl'. Using this object
  * in tests, allows easier updating of details as they change over time.
  */
object SharedTestDataADM {

    /** ***********************************/
    /** System Admin Data                **/
    /** ***********************************/

    val SYSTEM_PROJECT_IRI: IRI = OntologyConstants.KnoraAdmin.SystemProject // built-in project

    val testPass: String = java.net.URLEncoder.encode("test", "utf-8")

    /* represents the user profile of 'root' as found in admin-data.ttl */
    def rootUser = UserADM(
        id = "http://rdfh.ch/users/root",
        username = "root",
        email = "root@example.com",
        password = Option("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        token = None,
        givenName = "System",
        familyName = "Administrator",
        status = true, lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                SYSTEM_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.SystemAdmin)
            ),
            administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]]
        ))

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def superUser = UserADM(
        id = "http://rdfh.ch/users/superuser",
        username = "superuser",
        email = "super.user@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                SYSTEM_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.SystemAdmin)
            )
        ))

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def normalUser = UserADM(
        id = "http://rdfh.ch/users/normaluser",
        username = "normaluser",
        email = "normal.user@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        token = None,
        givenName = "Normal",
        familyName = "User",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM())

    /* represents the user profile of 'inactive user' as found in admin-data.ttl */
    def inactiveUser = UserADM(
        id = "http://rdfh.ch/users/inactiveuser",
        username = "inactiveuser",
        email = "inactive.user@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        token = None,
        givenName = "Inactive",
        familyName = "User",
        status = false,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM())

    /* represents an anonymous user */
    def anonymousUser: UserADM = KnoraSystemInstances.Users.AnonymousUser


    /* represents the 'multiuser' as found in admin-data.ttl */
    def multiuserUser = UserADM(
        id = "http://rdfh.ch/users/multiuser",
        username = "multiuser",
        email = "multi.user@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                INCUNABULA_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember, OntologyConstants.KnoraAdmin.ProjectAdmin),
                IMAGES_PROJECT_IRI -> List("http://rdfh.ch/groups/00FF/images-reviewer", OntologyConstants.KnoraAdmin.ProjectMember, OntologyConstants.KnoraAdmin.ProjectAdmin)
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
        ))

    /* represents the full project info of the Knora System project */
    def systemProject = ProjectADM(
        id = OntologyConstants.KnoraAdmin.SystemProject,
        shortname = "SystemProject",
        shortcode = "FFFF",
        longname = Some("Knora System Project"),
        description = Seq(StringLiteralWithLanguageV2(value = "Knora System Project", language = "en")),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq(OntologyConstants.KnoraBase.KnoraBaseOntologyIri, OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri, OntologyConstants.SalsahGui.SalsahGuiOntologyIri, OntologyConstants.Standoff.StandoffOntologyIri),
        status = true,
        selfjoin = false
    )

    val DefaultSharedOntologiesProjectIri: IRI = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject // built-in project

    /* represents the full project info of the default shared ontologies project */
    def defaultSharedOntologiesProject = ProjectADM(
        id = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
        shortname = "DefaultSharedOntologiesProject",
        shortcode = "0000",
        longname = Some("Default Knora Shared Ontologies Project"),
        description = Seq(StringLiteralWithLanguageV2(value = "Default Knora Shared Ontologies Project", language = "en")),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq.empty[IRI],
        status = true,
        selfjoin = false
    )


    /** ***********************************/
    /** Images Demo Project Admin Data  **/
    /** ***********************************/

    val IMAGES_PROJECT_IRI = "http://rdfh.ch/projects/00FF"

    /* represents 'user01' as found in admin-data.ttl  */
    def imagesUser01 = UserADM(
        id = "http://rdfh.ch/users/c266a56709",
        username = "user01.user1", email = "user01.user1@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                IMAGES_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember, OntologyConstants.KnoraAdmin.ProjectAdmin)
            ),
            administrativePermissionsPerProject = Map(
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    /* represents 'user02' as found in admin-data.ttl  */
    def imagesUser02 = UserADM(
        id = "http://rdfh.ch/users/97cec4000f",
        username = "user02.user",
        email = "user02.user@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                IMAGES_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    /* represents 'images-reviewer-user' as found in admin-data.ttl  */
    def imagesReviewerUser = UserADM(
        id = "http://rdfh.ch/users/images-reviewer-user",
        username = "images-reviewer-user",
        email = "images-reviewer-user@example.com",
        password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"),
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
                IMAGES_PROJECT_IRI -> List("http://rdfh.ch/groups/00FF/images-reviewer", OntologyConstants.KnoraAdmin.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                IMAGES_PROJECT_IRI -> Set(
                    PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bild"),
                    PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bildformat")
                )
            )
        ))

    /* represents the full project info of the images project */
    def imagesProject: ProjectADM = ProjectADM(
        id = IMAGES_PROJECT_IRI,
        shortname = "images",
        shortcode = "00FF",
        longname = Some("Image Collection Demo"),
        description = Seq(StringLiteralWithLanguageV2(value = "A demo project of a collection of images", language = "en")),
        keywords = Seq("images", "collection").sorted,
        logo = None,
        ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI),
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


    /** ***********************************/
    /** Incunabula Project Admin Data   **/
    /** ***********************************/

    val INCUNABULA_PROJECT_IRI = "http://rdfh.ch/projects/0803"

    /* represents 'testuser' (Incunabula ProjectAdmin) as found in admin-data.ttl  */
    def incunabulaProjectAdminUser = UserADM(
        id = "http://rdfh.ch/users/b83acc5f05",
        username = "user.test",
        email = "user.test@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                INCUNABULA_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember, OntologyConstants.KnoraAdmin.ProjectAdmin)
            ),
            administrativePermissionsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    /* represents 'root-alt' (Incunabula ProjectMember) as found in admin-data.ttl  */
    def incunabulaCreatorUser: UserADM = UserADM(
        id = "http://rdfh.ch/users/91e19f1e01",
        username = "root-alt",
        email = "root-alt@example.com",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                INCUNABULA_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                INCUNABULA_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    /* represents 'root-alt' (Incunabula Creator and ProjectMember) as found in admin-data.ttl  */
    def incunabulaMemberUser = UserADM(
        id = "http://rdfh.ch/users/incunabulaMemberUser",
        username = "incunabulaMemberUser"
        , email = "test.user2@test.ch", password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), token = None, givenName = "User", familyName = "Test2", status = true, lang = "de", groups = Seq.empty[GroupADM], projects = Seq(incunabulaProject), sessionId = None, permissions = PermissionsDataADM(
                groupsPerProject = Map(
                    INCUNABULA_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember)
                ),
                administrativePermissionsPerProject = Map(
                    INCUNABULA_PROJECT_IRI -> Set(
                        PermissionADM.ProjectResourceCreateAllPermission
                    )
                )
            ))

    /* represents the ProjectInfoV1 of the incunabula project */
    def incunabulaProject: ProjectADM = ProjectADM(
        id = INCUNABULA_PROJECT_IRI,
        shortname = "incunabula",
        shortcode = "0803",
        longname = Some("Bilderfolgen Basler Frühdrucke"),
        description = Seq(StringLiteralWithLanguageV2(value = "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>", language = "de")),
        keywords = Seq("Basler Frühdrucke", "Inkunabel", "Narrenschiff", "Wiegendrucke", "Sebastian Brant", "Bilderfolgen", "early print", "incunabula", "ship of fools", "Kunsthistorisches Seminar Universität Basel", "Late Middle Ages", "Letterpress Printing", "Basel", "Contectualisation of images").sorted,
        logo = Some("incunabula_logo.png"),
        ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI),
        status = true,
        selfjoin = false
    )

    /** **********************************/
    /** Anything Admin Data            **/
    /** **********************************/

    val ANYTHING_PROJECT_IRI = "http://rdfh.ch/projects/0001"

    def anythingAdminUser = UserADM(
        id = "http://rdfh.ch/users/AnythingAdminUser",
        username = "AnythingAdminUser",
        email = "anything.admin@example.org",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        token = None,
        givenName = "Anything",
        familyName = "Admin",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq(anythingProject),
        sessionId = None,
        permissions = PermissionsDataADM(
            groupsPerProject = Map(
                ANYTHING_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember, OntologyConstants.KnoraAdmin.ProjectAdmin)
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectAdminAllPermission,
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    def anythingUser1: UserADM = UserADM(
        id = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        username = "anything.user01",
        email = "anything.user01@example.org",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                ANYTHING_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember, "http://rdfh.ch/groups/0001/thing-searcher")
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    def anythingUser2: UserADM = UserADM(
        id = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
        username = "anything.user02",
        email = "anything.user02@example.org",
        password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
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
                ANYTHING_PROJECT_IRI -> List(OntologyConstants.KnoraAdmin.ProjectMember)
            ),
            administrativePermissionsPerProject = Map(
                ANYTHING_PROJECT_IRI -> Set(
                    PermissionADM.ProjectResourceCreateAllPermission
                )
            )
        ))

    def anythingProject: ProjectADM = ProjectADM(
        id = ANYTHING_PROJECT_IRI,
        shortname = "anything",
        shortcode = "0001",
        longname = Some("Anything Project"),
        description = Seq(StringLiteralWithLanguageV2(value = "Anything Project", language = "en")),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq("http://www.knora.org/ontology/0001/anything", "http://www.knora.org/ontology/0001/something"),
        status = true,
        selfjoin = false
    )


    /** **********************************/
    /** BEOL                           **/
    /** **********************************/

    val BEOL_PROJECT_IRI = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"

    def beolProject = ProjectADM(
        id = BEOL_PROJECT_IRI,
        shortname = "beol",
        shortcode = "0801",
        longname = Some("Bernoulli-Euler Online"),
        description = Seq(StringLiteralWithLanguageV2(value = "Bernoulli-Euler Online", language = "en")),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq("http://www.knora.org/ontology/0801/beol"),
        status = true,
        selfjoin = false
    )
    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def beolUser = UserADM(
        id = "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE",
        username = "beol",
        email = "beol@example.com",
        password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"),
        token = None,
        givenName = "BEOL",
        familyName = "BEOL",
        status = true,
        lang = "en",
        groups = Seq.empty[GroupADM],
        projects = Seq(beolProject),
        sessionId = None,
        permissions = PermissionsDataADM()
    )


    /** **********************************/
    /** DOKUBIB                        **/
    /** **********************************/

    val DOKUBIB_PROJECT_IRI = "http://rdfh.ch/projects/0804"

    def dokubibProject = ProjectADM(
        id = DOKUBIB_PROJECT_IRI,
        shortname = "dokubib",
        shortcode = "0804",
        longname = Some("Dokubib"),
        description = Seq(StringLiteralWithLanguageV2(value = "Dokubib", language = "de")),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq("http://www.knora.org/ontology/0804/dokubib"),
        status = false,
        selfjoin = false
    )

    /** **********************************/
    /** Test requests                   **/
    /** **********************************/

    val createGroupRequest: String =
        s"""{
           |    "name": "NewGroup",
           |    "description": "NewGroupDescription",
           |    "project": "$IMAGES_PROJECT_IRI",
           |    "status": true,
           |    "selfjoin": false
           |}""".stripMargin

    val updateGroupRequest: String =
        s"""{
           |    "name": "UpdatedGroupName",
           |    "description": "UpdatedGroupDescription"
           |}""".stripMargin

    val changeGroupStatusRequest: String =
        s"""{
           |    "status": true
           |}""".stripMargin

    val createProjectRequest: String =
        s"""{
           |    "shortname": "newproject",
           |    "shortcode": "1111",
           |    "longname": "project longname",
           |    "description": [{"value": "project description", "language": "en"}],
           |    "keywords": ["keywords"],
           |    "logo": "/fu/bar/baz.jpg",
           |    "status": true,
           |    "selfjoin": false
           |}""".stripMargin

    val updateProjectRequest: String =
        s"""{
           |    "shortname": "newproject",
           |    "longname": "updated project longname",
           |    "description": [{"value": "updated project description", "language": "en"}],
           |    "keywords": ["updated", "keywords"],
           |    "logo": "/fu/bar/baz-updated.jpg",
           |    "status": true,
           |    "selfjoin": true
           |}""".stripMargin

    val createUserRequest: String =
        s"""{
           |    "username": "donald.duck",
           |    "email": "donald.duck@example.org",
           |    "givenName": "Donald",
           |    "familyName": "Duck",
           |    "password": "test",
           |    "status": true,
           |    "lang": "en",
           |    "systemAdmin": false
           |}""".stripMargin

    val updateUserRequest: String =
        s"""{
           |    "username": "donald.big.duck",
           |    "email": "donald.big.duck@example.org",
           |    "givenName": "Big Donald",
           |    "familyName": "Duckmann",
           |    "lang": "de"
           |}""".stripMargin

    val changeUserPasswordRequest: String =
        s"""{
           |    "requesterPassword": "test",
           |    "newPassword": "test123456"
           |}""".stripMargin

    val changeUserStatusRequest: String =
        s"""{
           |    "status": false
           |}""".stripMargin

    val changeUserSystemAdminMembershipRequest: String =
        s"""{
           |    "systemAdmin": true
           |}""".stripMargin
}
