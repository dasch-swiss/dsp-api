/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.sharedtestdata

import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionsDataADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}

import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI

/**
 * This object holds the same user which are loaded with 'test_data/all_data/admin-data.ttl'. Using this object
 * in tests, allows easier updating of details as they change over time.
 */
object SharedTestDataADM {

    /** ***********************************/
    /** System Admin Data                **/
    /** ***********************************/

    val SYSTEM_PROJECT_IRI: IRI = OntologyConstants.KnoraAdmin.SystemProject // built-in project

    val testPass: String = java.net.URLEncoder.encode("test", "utf-8")

    /* represents the user profile of 'root' as found in admin-data.ttl */
    def rootUser: UserADM = UserADM(
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
    def superUser: UserADM = UserADM(
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
    def normalUser: UserADM = UserADM(
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
    def inactiveUser: UserADM = UserADM(
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
    def multiuserUser: UserADM = UserADM(
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
    def systemProject: ProjectADM = ProjectADM(
        id = OntologyConstants.KnoraAdmin.SystemProject,
        shortname = "SystemProject",
        shortcode = "FFFF",
        longname = Some("Knora System Project"),
        description = Seq(StringLiteralV2(value = "Knora System Project", language = Some("en"))),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq(OntologyConstants.KnoraBase.KnoraBaseOntologyIri, OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri, OntologyConstants.SalsahGui.SalsahGuiOntologyIri, OntologyConstants.Standoff.StandoffOntologyIri),
        status = true,
        selfjoin = false
    )

    val DefaultSharedOntologiesProjectIri: IRI = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject // built-in project

    /* represents the full project info of the default shared ontologies project */
    def defaultSharedOntologiesProject: ProjectADM = ProjectADM(
        id = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
        shortname = "DefaultSharedOntologiesProject",
        shortcode = "0000",
        longname = Some("Default Knora Shared Ontologies Project"),
        description = Seq(StringLiteralV2(value = "Default Knora Shared Ontologies Project", language = Some("en"))),
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
    def imagesUser01: UserADM = UserADM(
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
    def imagesUser02: UserADM = UserADM(
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
    def imagesReviewerUser: UserADM = UserADM(
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
        description = Seq(StringLiteralV2(value = "A demo project of a collection of images", language = Some("en"))),
        keywords = Seq("images", "collection").sorted,
        logo = None,
        ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI),
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the images ProjectAdmin group */
    def imagesProjectAdminGroup: GroupADM = GroupADM(
        id = "-",
        name = "ProjectAdmin",
        description = "Default Project Admin Group",
        project = imagesProject,
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the images ProjectMember group */
    def imagesProjectMemberGroup: GroupADM = GroupADM(
        id = "-",
        name = "ProjectMember",
        description = "Default Project Member Group",
        project = imagesProject,
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the images project reviewer group */
    def imagesReviewerGroup: GroupADM = GroupADM(
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
    def incunabulaProjectAdminUser: UserADM = UserADM(
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
    def incunabulaMemberUser: UserADM = UserADM(
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
        description = Seq(StringLiteralV2(value = "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>", language = None)),
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

    val customResourceIRI: IRI = "http://rdfh.ch/0001/a-thing-with-IRI"
    val customResourceIRI_resourceWithValues: IRI = "http://rdfh.ch/0001/a-thing-with-value-IRI"
    val customValueIRI_withResourceIriAndValueIRIAndValueUUID: IRI = "http://rdfh.ch/0001/a-thing-with-value-IRI/values/a-value-with-IRI-and-UUID"
    val customValueUUID = "IN4R19yYR0ygi3K2VEHpUQ"
    val customValueIRI: IRI = "http://rdfh.ch/0001/a-thing-with-value-IRI/values/a-value-with-IRI"
    val customResourceCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
    val customValueCreationDate: Instant = Instant.parse("2020-06-09T17:04:54.502951Z")

    val customListIRI: IRI = "http://rdfh.ch/lists/0001/a-list-with-IRI"

    val customProjectIri: IRI = "http://rdfh.ch/projects/3333"

    def anythingAdminUser: UserADM = UserADM(
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
        description = Seq(StringLiteralV2(value = "Anything Project", language = None)),
        keywords = Seq("things", "arbitrary test data").sorted,
        logo = None,
        ontologies = Seq("http://www.knora.org/ontology/0001/anything",
            "http://www.knora.org/ontology/0001/something"),
        status = true,
        selfjoin = false
    )

    /* represents the full GroupInfoV1 of the Thing searcher group */
    def thingSearcherGroup: GroupADM = GroupADM(
        id = "http://rdfh.ch/groups/0001/thing-searcher",
        name = "Thing searcher",
        description = "A group for thing searchers.",
        project = anythingProject,
        status = true,
        selfjoin = true
    )

    /** **********************************/
    /** BEOL                           **/
    /** **********************************/

    val BEOL_PROJECT_IRI = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"

    def beolProject: ProjectADM = ProjectADM(
        id = BEOL_PROJECT_IRI,
        shortname = "beol",
        shortcode = "0801",
        longname = Some("Bernoulli-Euler Online"),
        description = Seq(StringLiteralV2(value = "Bernoulli-Euler Online", language = None)),
        keywords = Seq.empty[String],
        logo = None,
        ontologies = Seq("http://www.knora.org/ontology/0801/beol", "http://www.knora.org/ontology/0801/biblio",
            "http://www.knora.org/ontology/0801/leibniz", "http://www.knora.org/ontology/0801/newton"),
        status = true,
        selfjoin = false
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def beolUser: UserADM = UserADM(
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

    def dokubibProject: ProjectADM = ProjectADM(
        id = DOKUBIB_PROJECT_IRI,
        shortname = "dokubib",
        shortcode = "0804",
        longname = Some("Dokubib"),
        description = Seq(StringLiteralV2(value = "Dokubib", language = None)),
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

    val createGroupWithCustomIriRequest: String =
        s"""{   "id": "http://rdfh.ch/groups/00FF/group-with-customIri",
           |    "name": "NewGroupWithCustomIri",
           |    "description": "A new group with a custom Iri",
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

    val createProjectWithCustomIRIRequest: String =
        s"""{
           |    "id": "$customProjectIri",
           |    "shortname": "newprojectWithIri",
           |    "shortcode": "3333",
           |    "longname": "new project with a custom IRI",
           |    "description": [{"value": "a project created with a custom IRI", "language": "en"}],
           |    "keywords": ["projectIRI"],
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

    val createUserWithCustomIriRequest: String =
        s"""{
           |    "id": "http://rdfh.ch/users/userWithCustomIri",
           |    "username": "userWithCustomIri",
           |    "email": "userWithCustomIri@example.org",
           |    "givenName": "a user",
           |    "familyName": "with a custom Iri",
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

    val createListRequest: String =
        s"""{
           |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |    "labels": [{ "value": "Neue Liste", "language": "de"}],
           |    "comments": []
           |}""".stripMargin

    val createListWithCustomIriRequest: String =
        s"""{
           |    "id": "${SharedTestDataADM.customListIRI}",
           |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |    "labels": [{ "value": "New list with a custom IRI", "language": "en"}],
           |    "comments": []
           |}""".stripMargin


    val createAdministrativePermissionRequest: String =
        s"""{
           |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
           |    "forProject":"${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |	"hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
           |}""".stripMargin

    val createDefaultObjectAccessPermissionRequest: String =
        s"""{
           |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
           |    "forProject":"${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |    "forProperty":null,
           |    "forResourceClass":null,
           |    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
           |}""".stripMargin

    val createAdministrativePermissionWithCustomIriRequest: String =
        s"""{
           |    "id": "http://rdfh.ch/permissions/0001/AP-with-customIri",
           |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
           |    "forProject":"${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |	"hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
           |}""".stripMargin

    val createDefaultObjectAccessPermissionWithCustomIriRequest: String =
        s"""{
           |    "id": "http://rdfh.ch/permissions/00FF/DOAP-with-customIri",
           |    "forGroup":null,
           |    "forProject":"${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "forProperty":null,
           |    "forResourceClass":"${SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS}",
           |    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
           |}""".stripMargin

    def updateListInfoRequest(listIri: IRI): String = {
        s"""{
           |    "listIri": "$listIri",
           |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |    "labels": [{ "value": "Neue geänderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
           |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
           |}""".stripMargin
    }

    def addChildListNodeRequest(parentNodeIri: IRI,
                                name: String,
                                label: String,
                                comment: String): String = {
        s"""{
           |    "parentNodeIri": "$parentNodeIri",
           |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |    "name": "$name",
           |    "labels": [{ "value": "$label", "language": "en"}],
           |    "comments": [{ "value": "$comment", "language": "en"}]
           |}""".stripMargin
    }

    def createIntValueRequest(resourceIri: IRI, intValue: Int): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createIntValueWithCustomIRIRequest(resourceIri: IRI,
                                           intValue: Int,
                                           valueIri: IRI,
                                           valueUUID: String,
                                           valueCreationDate: Instant): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:valueHasUUID" : "$valueUUID",
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$valueCreationDate"
           |      }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
    }

    def createIntValueWithCustomUUIDRequest(resourceIri: IRI,
                                            intValue: Int,
                                            valueUUID: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:valueHasUUID" : "$valueUUID"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
    }

    def createIntValueWithCustomValueIriRequest(resourceIri: IRI, intValue: Int, valueIri: IRI): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
    }

    def createIntValueWithCustomCreationDateRequest(resourceIri: IRI, intValue: Int, creationDate: Instant): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$creationDate"
           |      }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
    }

    def createIntValueWithCustomPermissionsRequest(resourceIri: IRI, intValue: Int, permissions: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:hasPermissions" : "$permissions"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createTextValueWithoutStandoffRequest(resourceIri: IRI, valueAsString: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    val standardMappingIri: IRI = "http://rdfh.ch/standoff/mappings/StandardMapping"

    val textValue1AsXmlWithStandardMapping: String =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text documentType="html">
          |    <p>This an <span data-description="an &quot;event&quot;" data-date="GREGORIAN:2017-01-27 CE" class="event">event</span>.</p>
          |</text>""".stripMargin

    def createTextValueWithStandoffRequest(resourceIri: IRI, textValueAsXml: String, mappingIri: String)(implicit stringFormatter: StringFormatter): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(textValueAsXml)},
           |    "knora-api:textValueHasMapping" : {
           |      "@id": "$mappingIri"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createTextValueWithCommentRequest(resourceIri: IRI, valueAsString: String, valueHasComment: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString",
           |    "knora-api:valueHasComment" : "$valueHasComment"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createDecimalValueRequest(resourceIri: IRI, decimalValue: BigDecimal): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDecimal" : {
           |    "@type" : "knora-api:DecimalValue",
           |    "knora-api:decimalValueAsDecimal" : {
           |      "@type" : "xsd:decimal",
           |      "@value" : "$decimalValue"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createDateValueWithDayPrecisionRequest(resourceIri: IRI,
                                               dateValueHasCalendar: String,
                                               dateValueHasStartYear: Int,
                                               dateValueHasStartMonth: Int,
                                               dateValueHasStartDay: Int,
                                               dateValueHasStartEra: String,
                                               dateValueHasEndYear: Int,
                                               dateValueHasEndMonth: Int,
                                               dateValueHasEndDay: Int,
                                               dateValueHasEndEra: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
           |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
           |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
           |    "knora-api:dateValueHasEndDay" : $dateValueHasEndDay,
           |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createDateValueWithMonthPrecisionRequest(resourceIri: IRI,
                                                 dateValueHasCalendar: String,
                                                 dateValueHasStartYear: Int,
                                                 dateValueHasStartMonth: Int,
                                                 dateValueHasStartEra: String,
                                                 dateValueHasEndYear: Int,
                                                 dateValueHasEndMonth: Int,
                                                 dateValueHasEndEra: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
           |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
           |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createDateValueWithYearPrecisionRequest(resourceIri: IRI,
                                                dateValueHasCalendar: String,
                                                dateValueHasStartYear: Int,
                                                dateValueHasStartEra: String,
                                                dateValueHasEndYear: Int,
                                                dateValueHasEndEra: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createBooleanValueRequest(resourceIri: IRI,
                                  booleanValue: Boolean): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : $booleanValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    val geometryValue1 = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

    def createGeometryValueRequest(resourceIri: IRI,
                                   geometryValue: String)(implicit stringFormatter: StringFormatter): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasGeometry" : {
           |    "@type" : "knora-api:GeomValue",
           |    "knora-api:geometryValueAsGeometry" : ${stringFormatter.toJsonEncodedString(geometryValue)}
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createIntervalValueRequest(resourceIri: IRI,
                                   intervalStart: BigDecimal,
                                   intervalEnd: BigDecimal): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInterval" : {
           |    "@type" : "knora-api:IntervalValue",
           |    "knora-api:intervalValueHasStart" : {
           |      "@type" : "xsd:decimal",
           |      "@value" : "$intervalStart"
           |    },
           |    "knora-api:intervalValueHasEnd" : {
           |      "@type" : "xsd:decimal",
           |      "@value" : "$intervalEnd"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createTimeValueRequest(resourceIri: IRI,
                               timeStamp: Instant): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasTimeStamp" : {
           |    "@type" : "knora-api:TimeValue",
           |    "knora-api:timeValueAsTimeStamp" : {
           |      "@type" : "xsd:dateTimeStamp",
           |      "@value" : "$timeStamp"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createListValueRequest(resourceIri: IRI,
                               listNode: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasListItem" : {
           |    "@type" : "knora-api:ListValue",
           |    "knora-api:listValueAsListNode" : {
           |      "@id" : "$listNode"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createColorValueRequest(resourceIri: IRI,
                                color: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasColor" : {
           |    "@type" : "knora-api:ColorValue",
           |    "knora-api:colorValueAsColor" : "$color"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createUriValueRequest(resourceIri: IRI,
                              uri: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasUri" : {
           |    "@type" : "knora-api:UriValue",
           |    "knora-api:uriValueAsUri" : {
           |      "@type" : "xsd:anyURI",
           |      "@value" : "$uri"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createGeonameValueRequest(resourceIri: IRI,
                                  geonameCode: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasGeoname" : {
           |    "@type" : "knora-api:GeonameValue",
           |    "knora-api:geonameValueAsGeonameCode" : "$geonameCode"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createLinkValueRequest(resourceIri: IRI,
                               targetResourceIri: IRI,
                               valueHasComment: Option[String] = None): String = {
        valueHasComment match {
            case Some(comment) =>
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    },
                   |    "knora-api:valueHasComment" : "$comment"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            case None =>
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin
        }
    }

    def createLinkValueWithCustomIriRequest(resourceIri: IRI,
                                            targetResourceIri: IRI,
                                            valueIri: IRI,
                                            valueUUID: String,
                                            valueCreationDate: Instant): String = {
        s"""{
           | "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasOtherThingValue" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:LinkValue",
           |    "knora-api:valueHasUUID": "IN4R19yYR0ygi3K2VEHpUQ",
           |    "knora-api:linkValueHasTargetIri" : {
           |      "@id" : "$targetResourceIri"
           |    },
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$valueCreationDate"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

    }

    def updateIntValueRequest(resourceIri: IRI,
                              valueIri: IRI,
                              intValue: Int): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateIntValueWithCustomCreationDateRequest(resourceIri: IRI,
                                                    valueIri: IRI,
                                                    intValue: Int,
                                                    valueCreationDate: Instant): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$valueCreationDate"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
    }


    def updateIntValueWithCustomNewValueVersionIriRequest(resourceIri: IRI,
                                                          valueIri: IRI,
                                                          intValue: Int,
                                                          newValueVersionIri: IRI): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:newValueVersionIri" : {
           |      "@id" : "$newValueVersionIri"
           |    },
           |    "knora-api:intValueAsInt" : $intValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
           |  }
           |}""".stripMargin
    }

    def updateIntValueWithCustomPermissionsRequest(resourceIri: IRI,
                                                   valueIri: IRI,
                                                   intValue: Int,
                                                   permissions: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:intValueAsInt" : $intValue,
           |    "knora-api:hasPermissions" : "$permissions"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateIntValuePermissionsOnlyRequest(resourceIri: IRI,
                                             valueIri: IRI,
                                             permissions: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:hasPermissions" : "$permissions"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateTextValueWithoutStandoffRequest(resourceIri: IRI,
                                              valueIri: IRI,
                                              valueAsString: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    val textValue2AsXmlWithStandardMapping: String =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text>
          |   This updated text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
          |</text>""".stripMargin

    def updateTextValueWithStandoffRequest(resourceIri: IRI,
                                           valueIri: IRI,
                                           textValueAsXml: String,
                                           mappingIri: IRI)(implicit stringFormatter: StringFormatter): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(textValueAsXml)},
           |    "knora-api:textValueHasMapping" : {
           |      "@id": "$mappingIri"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateTextValueWithCommentRequest(resourceIri: IRI,
                                          valueIri: IRI,
                                          valueAsString: String,
                                          valueHasComment: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "$valueAsString",
           |    "knora-api:valueHasComment" : "$valueHasComment"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateDecimalValueRequest(resourceIri: IRI,
                                  valueIri: IRI,
                                  decimalValue: BigDecimal): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDecimal" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:DecimalValue",
           |    "knora-api:decimalValueAsDecimal" : {
           |      "@type" : "xsd:decimal",
           |      "@value" : "$decimalValue"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateDateValueWithDayPrecisionRequest(resourceIri: IRI,
                                               valueIri: IRI,
                                               dateValueHasCalendar: String,
                                               dateValueHasStartYear: Int,
                                               dateValueHasStartMonth: Int,
                                               dateValueHasStartDay: Int,
                                               dateValueHasStartEra: String,
                                               dateValueHasEndYear: Int,
                                               dateValueHasEndMonth: Int,
                                               dateValueHasEndDay: Int,
                                               dateValueHasEndEra: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
           |    "knora-api:dateValueHasStartDay" : $dateValueHasStartDay,
           |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
           |    "knora-api:dateValueHasEndDay" : $dateValueHasEndDay,
           |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateDateValueWithMonthPrecisionRequest(resourceIri: IRI,
                                                 valueIri: IRI,
                                                 dateValueHasCalendar: String,
                                                 dateValueHasStartYear: Int,
                                                 dateValueHasStartMonth: Int,
                                                 dateValueHasStartEra: String,
                                                 dateValueHasEndYear: Int,
                                                 dateValueHasEndMonth: Int,
                                                 dateValueHasEndEra: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartMonth" : $dateValueHasStartMonth,
           |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndMonth" : $dateValueHasEndMonth,
           |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateDateValueWithYearPrecisionRequest(resourceIri: IRI,
                                                valueIri: IRI,
                                                dateValueHasCalendar: String,
                                                dateValueHasStartYear: Int,
                                                dateValueHasStartEra: String,
                                                dateValueHasEndYear: Int,
                                                dateValueHasEndEra: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasDate" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:DateValue",
           |    "knora-api:dateValueHasCalendar" : "$dateValueHasCalendar",
           |    "knora-api:dateValueHasStartYear" : $dateValueHasStartYear,
           |    "knora-api:dateValueHasStartEra" : "$dateValueHasStartEra",
           |    "knora-api:dateValueHasEndYear" : $dateValueHasEndYear,
           |    "knora-api:dateValueHasEndEra" : "$dateValueHasEndEra"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateBooleanValueRequest(resourceIri: IRI,
                                  valueIri: IRI,
                                  booleanValue: Boolean): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasBoolean" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : $booleanValue
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    val geometryValue2 = """{"status":"active","lineColor":"#ff3344","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

    def updateGeometryValueRequest(resourceIri: IRI,
                                   valueIri: IRI,
                                   geometryValue: String)(implicit stringFormatter: StringFormatter): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasGeometry" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:GeomValue",
           |    "knora-api:geometryValueAsGeometry" : ${stringFormatter.toJsonEncodedString(geometryValue)}
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateIntervalValueRequest(resourceIri: IRI,
                                   valueIri: IRI,
                                   intervalStart: BigDecimal,
                                   intervalEnd: BigDecimal): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInterval" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntervalValue",
           |    "knora-api:intervalValueHasStart" : {
           |      "@type" : "xsd:decimal",
           |      "@value" : "$intervalStart"
           |    },
           |    "knora-api:intervalValueHasEnd" : {
           |      "@type" : "xsd:decimal",
           |      "@value" : "$intervalEnd"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateTimeValueRequest(resourceIri: IRI,
                               valueIri: IRI,
                               timeStamp: Instant): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasTimeStamp" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TimeValue",
           |    "knora-api:timeValueAsTimeStamp" : {
           |      "@type" : "xsd:dateTimeStamp",
           |      "@value" : "$timeStamp"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateListValueRequest(resourceIri: IRI,
                               valueIri: IRI,
                               listNode: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasListItem" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:ListValue",
           |    "knora-api:listValueAsListNode" : {
           |      "@id" : "$listNode"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateColorValueRequest(resourceIri: IRI,
                                valueIri: IRI,
                                color: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasColor" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:ColorValue",
           |    "knora-api:colorValueAsColor" : "$color"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateUriValueRequest(resourceIri: IRI,
                              valueIri: IRI,
                              uri: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasUri" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:UriValue",
           |    "knora-api:uriValueAsUri" : {
           |      "@type" : "xsd:anyURI",
           |      "@value" : "$uri"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateGeonameValueRequest(resourceIri: IRI,
                                  valueIri: IRI,
                                  geonameCode: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasGeoname" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:GeonameValue",
           |    "knora-api:geonameValueAsGeonameCode" : "$geonameCode"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateLinkValueRequest(resourceIri: IRI,
                               valueIri: IRI,
                               targetResourceIri: IRI,
                               comment: Option[String] = None): String = {
        comment match {
            case Some(definedComment) =>
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    },
                   |    "knora-api:valueHasComment" : "$definedComment"
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            case None =>
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasOtherThingValue" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:LinkValue",
                   |    "knora-api:linkValueHasTargetIri" : {
                   |      "@id" : "$targetResourceIri"
                   |    }
                   |  },
                   |  "@context" : {
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin
        }
    }

    def updateStillImageFileValueRequest(resourceIri: IRI,
                                         valueIri: IRI,
                                         internalFilename: String): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:ThingPicture",
           |  "knora-api:hasStillImageFileValue" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:StillImageFileValue",
           |    "knora-api:fileValueHasFilename" : "$internalFilename"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def deleteIntValueRequest(resourceIri: IRI,
                              valueIri: IRI,
                              maybeDeleteComment: Option[String]): String = {
        maybeDeleteComment match {
            case Some(deleteComment) =>
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:IntValue",
                   |    "knora-api:deleteComment" : "$deleteComment"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            case None =>
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "anything:hasInteger" : {
                   |    "@id" : "$valueIri",
                   |    "@type" : "knora-api:IntValue"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin
        }
    }

    def deleteIntValueRequestWithCustomDeleteDate(resourceIri: IRI,
                                                  valueIri: IRI,
                                                  deleteDate: Instant): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasInteger" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:IntValue",
           |    "knora-api:deleteDate" : {
           |      "@type" : "xsd:dateTimeStamp",
           |      "@value" : "$deleteDate"
           |    }
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def deleteLinkValueRequest(resourceIri: IRI,
                               valueIri: IRI): String = {
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "anything:Thing",
           |  "anything:hasOtherThingValue" : {
           |    "@id": "$valueIri",
           |    "@type" : "knora-api:LinkValue"
           |  },
           |  "@context" : {
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    val gravsearchComplexThingSmallerThanDecimal: String =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |     ?thing a anything:Thing .
          |     ?thing anything:hasDecimal ?decimal .
          |     ?decimal knora-api:decimalValueAsDecimal ?decimalDec .
          |     FILTER(?decimalDec < "3"^^xsd:decimal)
          |}""".stripMargin


    val gravsearchComplexRegionsForPage: String =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |    ?region knora-api:hasGeometry ?geom .
          |    ?region knora-api:hasComment ?comment .
          |    ?region knora-api:hasColor ?color .
          |} WHERE {
          |    ?region a knora-api:Region .
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |    ?region knora-api:hasGeometry ?geom .
          |    ?region knora-api:hasComment ?comment .
          |    ?region knora-api:hasColor ?color .
          |}""".stripMargin

    val gravsearchThingLinks: String =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasOtherThing <http://rdfh.ch/0001/start> .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasOtherThing <http://rdfh.ch/0001/start> .
          |}""".stripMargin

    val gravsearchThingsWithPaging: String =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |}""".stripMargin

    val createResourceWithValues: String =
        """{
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : {
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:booleanValueAsBoolean" : true
          |  },
          |  "anything:hasColor" : {
          |    "@type" : "knora-api:ColorValue",
          |    "knora-api:colorValueAsColor" : "#ff3333"
          |  },
          |  "anything:hasDate" : {
          |    "@type" : "knora-api:DateValue",
          |    "knora-api:dateValueHasCalendar" : "GREGORIAN",
          |    "knora-api:dateValueHasEndEra" : "CE",
          |    "knora-api:dateValueHasEndYear" : 1489,
          |    "knora-api:dateValueHasStartEra" : "CE",
          |    "knora-api:dateValueHasStartYear" : 1489
          |  },
          |  "anything:hasDecimal" : {
          |    "@type" : "knora-api:DecimalValue",
          |    "knora-api:decimalValueAsDecimal" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "100000000000000.000000000000001"
          |    }
          |  },
          |  "anything:hasGeometry" : {
          |    "@type" : "knora-api:GeomValue",
          |    "knora-api:geometryValueAsGeometry" : "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}"
          |  },
          |  "anything:hasGeoname" : {
          |    "@type" : "knora-api:GeonameValue",
          |    "knora-api:geonameValueAsGeonameCode" : "2661604"
          |  },
          |  "anything:hasInteger" : [ {
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
          |    "knora-api:intValueAsInt" : 5,
          |    "knora-api:valueHasComment" : "this is the number five"
          |  }, {
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:intValueAsInt" : 6
          |  } ],
          |  "anything:hasInterval" : {
          |    "@type" : "knora-api:IntervalValue",
          |    "knora-api:intervalValueHasEnd" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "3.4"
          |    },
          |    "knora-api:intervalValueHasStart" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "1.2"
          |    }
          |  },
          |  "anything:hasTimeStamp" : {
          |    "@type" : "knora-api:TimeValue",
          |    "knora-api:timeValueAsTimeStamp" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2020-01-24T08:47:10.307068Z"
          |    }
          |  },
          |  "anything:hasListItem" : {
          |    "@type" : "knora-api:ListValue",
          |    "knora-api:listValueAsListNode" : {
          |      "@id" : "http://rdfh.ch/lists/0001/treeList03"
          |    }
          |  },
          |  "anything:hasOtherThingValue" : {
          |    "@type" : "knora-api:LinkValue",
          |    "knora-api:linkValueHasTargetIri" : {
          |      "@id" : "http://rdfh.ch/0001/a-thing"
          |    }
          |  },
          |  "anything:hasRichtext" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
          |    "knora-api:textValueHasMapping" : {
          |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
          |    }
          |  },
          |  "anything:hasText" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "this is text without standoff"
          |  },
          |  "anything:hasUri" : {
          |    "@type" : "knora-api:UriValue",
          |    "knora-api:uriValueAsUri" : {
          |      "@type" : "xsd:anyURI",
          |      "@value" : "https://www.knora.org"
          |    }
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}""".stripMargin

    def createResourceWithCustomCreationDate(creationDate: Instant): String = {
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:creationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$creationDate"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createResourceWithCustomIRI(iri: IRI): String = {
        s"""{
           |  "@id" : "$iri",
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createResourceWithCustomValueIRI(valueIRI: IRI): String = {
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@id" : "$valueIRI",
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing with value IRI",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def createResourceWithCustomValueUUID(valueUUID: String): String = {
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true,
           |    "knora-api:valueHasUUID" : "$valueUUID"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin
    }

    def createResourceWithCustomValueCreationDate(creationDate: Instant): String = {
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : false,
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$creationDate"
           |    }
           |  },
           |  "rdfs:label" : "test thing with value has creation date",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin
    }


    def createResourceWithCustomResourceIriAndCreationDateAndValueWithCustomIRIAndUUID(resourceIRI: IRI,
                                                                                       creationDate: Instant,
                                                                                       valueIRI: IRI,
                                                                                       valueUUID: String): String = {
        s"""{
           |   "@id" : "$resourceIRI",
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@id": "$valueIRI",
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true,
           |    "knora-api:valueHasUUID" : "$valueUUID"
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:creationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$creationDate"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin
    }

    def createResourceAsUser(userADM: UserADM): String = {
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:attachedToUser" : {
           |    "@id" : "${userADM.id}"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
    }

    def updateResourceMetadata(resourceIri: IRI,
                               lastModificationDate: Option[Instant],
                               newLabel: String,
                               newPermissions: String,
                               newModificationDate: Instant): String = {
        lastModificationDate match {
            case Some(definedLastModificationDate) =>
                s"""|{
                    |  "@id" : "$resourceIri",
                    |  "@type" : "anything:Thing",
                    |  "rdfs:label" : "$newLabel",
                    |  "knora-api:hasPermissions" : "$newPermissions",
                    |  "knora-api:lastModificationDate" : {
                    |    "@type" : "xsd:dateTimeStamp",
                    |    "@value" : "$definedLastModificationDate"
                    |  },
                    |  "knora-api:newModificationDate" : {
                    |    "@type" : "xsd:dateTimeStamp",
                    |    "@value" : "$newModificationDate"
                    |  },
                    |  "@context" : {
                    |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                    |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                    |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                    |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                    |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                    |  }
                    |}""".stripMargin

            case None =>
                s"""|{
                    |  "@id" : "$resourceIri",
                    |  "@type" : "anything:Thing",
                    |  "rdfs:label" : "$newLabel",
                    |  "knora-api:hasPermissions" : "$newPermissions",
                    |  "knora-api:newModificationDate" : {
                    |    "@type" : "xsd:dateTimeStamp",
                    |    "@value" : "$newModificationDate"
                    |  },
                    |  "@context" : {
                    |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                    |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                    |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                    |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                    |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                    |  }
                    |}""".stripMargin

        }
    }

    def successResponse(message: String): String =
        s"""{
           |  "knora-api:result" : "$message",
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

    def deleteResource(resourceIri: IRI,
                       lastModificationDate: Instant): String = {
        s"""|{
            |  "@id" : "$resourceIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$lastModificationDate"
            |  },
            |  "knora-api:deleteComment" : "This resource is too boring.",
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
    }

    def deleteResourceWithCustomDeleteDate(resourceIri: IRI,
                                           deleteDate: Instant): String = {
        s"""|{
            |  "@id" : "$resourceIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:deleteComment" : "This resource is too boring.",
            |  "knora-api:deleteDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$deleteDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
    }

    def eraseResource(resourceIri: IRI,
                      lastModificationDate: Instant): String = {
        s"""|{
            |  "@id" : "$resourceIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$lastModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
    }

    def createOntology(projectIri: IRI, label: String): String = {
        s"""
           |{
           |    "knora-api:ontologyName": "foo",
           |    "knora-api:attachedToProject": {
           |      "@id": "$projectIri"
           |    },
           |    "rdfs:label": "$label",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin
    }

    def createOntologyWithComment(projectIri: IRI, label: String, comment: String): String = {
        s"""
           |{
           |    "knora-api:ontologyName": "bar",
           |    "knora-api:attachedToProject": {
           |      "@id": "$projectIri"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin
    }

    val createOntologyResponse: String =
        """{
          |  "@id" : "http://0.0.0.0:3333/ontology/00FF/foo/v2",
          |  "@type" : "owl:Ontology",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/00FF"
          |  },
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "2020-07-08T15:27:37.073695Z"
          |  },
          |  "rdfs:label" : "The foo ontology",
          |  "@context" : {
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "owl" : "http://www.w3.org/2002/07/owl#"
          |  }
          |}""".stripMargin

    def changeOntologyMetadata(ontologyIri: IRI, newLabel: String, modificationDate: Instant): String = {
        s"""
           |{
           |  "@id": "$ontologyIri",
           |  "rdfs:label": "$newLabel",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$modificationDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin
    }

    def createClassWithCardinalities(anythinOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythinOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:WildThing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "wild thing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A thing that is wild"
           |    },
           |    "rdfs:subClassOf" : [
           |      {
           |        "@id": "anything:Thing"
           |      },
           |      {
           |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
           |        "owl:maxCardinality": 1,
           |        "owl:onProperty": {
           |          "@id": "anything:hasName"
           |        },
           |        "salsah-gui:guiOrder": 1
           |      }
           |    ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}
            """.stripMargin
    }


    def createClassWithoutCardinalities(anythinOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythinOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "nothing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents nothing"
           |    },
           |    "rdfs:subClassOf" : {
           |      "@id" : "knora-api:Resource"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythinOntologyIri#"
           |  }
           |}
            """.stripMargin
    }

    val createClassWithoutCardinalitiesResponse: String = {
        """{
          |    "@graph": [
          |        {
          |            "@id": "anything:Nothing",
          |            "@type": "owl:Class",
          |            "knora-api:canBeInstantiated": true,
          |            "knora-api:isResourceClass": true,
          |            "rdfs:comment": {
          |                "@language": "en",
          |                "@value": "Represents nothing"
          |            },
          |            "rdfs:label": {
          |                "@language": "en",
          |                "@value": "nothing"
          |            },
          |            "rdfs:subClassOf": [
          |                {
          |                    "@id": "knora-api:Resource"
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:arkUrl"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:attachedToProject"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:attachedToUser"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:creationDate"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:maxCardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:deleteComment"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:maxCardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:deleteDate"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:maxCardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:deletedBy"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:minCardinality": 0,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:hasIncomingLinkValue"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:hasPermissions"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:minCardinality": 0,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:hasStandoffLinkTo"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:minCardinality": 0,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:hasStandoffLinkToValue"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:maxCardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:isDeleted"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:maxCardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:lastModificationDate"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:userHasPermission"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:versionArkUrl"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:maxCardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "knora-api:versionDate"
          |                    }
          |                },
          |                {
          |                    "@type": "owl:Restriction",
          |                    "knora-api:isInherited": true,
          |                    "owl:cardinality": 1,
          |                    "owl:onProperty": {
          |                        "@id": "rdfs:label"
          |                    }
          |                }
          |            ]
          |        }
          |    ],
          |    "@id": "http://0.0.0.0:3333/ontology/0001/anything/v2",
          |    "@type": "owl:Ontology",
          |    "knora-api:attachedToProject": {
          |        "@id": "http://rdfh.ch/projects/0001"
          |    },
          |    "knora-api:lastModificationDate": {
          |        "@type": "xsd:dateTimeStamp",
          |        "@value": "2020-07-24T11:24:29.824Z"
          |    },
          |    "rdfs:label": "The anything ontology",
          |    "@context": {
          |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
          |        "owl": "http://www.w3.org/2002/07/owl#",
          |        "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
          |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          |        "xsd": "http://www.w3.org/2001/XMLSchema#",
          |        "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |    }
          |}""".stripMargin
    }

    def addCardinality(anythinOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythinOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasOtherNothing"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythinOntologyIri#"
           |  }
           |}
            """.stripMargin
    }

    def createProperty(anythinOntologyIri: IRI): String = {
        s"""
           |{
           |  "@id" : "$anythinOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "2017-12-19T15:23:42.166Z"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:hasName",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:Thing"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      },
           |      "rdfs:comment" : [ {
           |        "@language" : "en",
           |        "@value" : "The name of a Thing"
           |      }, {
           |        "@language" : "de",
           |        "@value" : "Der Name eines Dinges"
           |      } ],
           |      "rdfs:label" : [ {
           |        "@language" : "en",
           |        "@value" : "has name"
           |      }, {
           |        "@language" : "de",
           |        "@value" : "hat Namen"
           |      } ],
           |      "rdfs:subPropertyOf" : [ {
           |        "@id" : "knora-api:hasValue"
           |      }, {
           |        "@id" : "http://schema.org/name"
           |      } ],
           |      "salsah-gui:guiElement" : {
           |        "@id" : "salsah-gui:SimpleText"
           |      },
           |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythinOntologyIri#"
           |  }
           |}
        """.stripMargin
    }

    def changeClassLabel(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : [ {
           |      "@language" : "en",
           |      "@value" : "nothing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "rien"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
        """.stripMargin
    }

    def changeClassComment(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:comment" : [ {
           |      "@language" : "en",
           |      "@value" : "Represents nothing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "ne représente rien"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
                """.stripMargin
    }

    def replaceClassCardinalities(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasEmptiness"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
            """.stripMargin
    }

    def removeCardinalityOfProperty(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
            """.stripMargin
    }

    def removeAllClassCardinalities(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
            """.stripMargin
    }

    def changePropertyComment(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "rdfs:comment" : [ {
           |      "@language" : "en",
           |      "@value" : "The name of a Thing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "Le nom d'une chose"
           |    }, {
           |      "@language" : "de",
           |      "@value" : "Der Name eines Dinges"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
          """.stripMargin
    }

    def changePropertyLabel(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""
           |{
           |  "@id" : "$anythingOntologyIri",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "rdfs:label" : [ {
           |      "@language" : "en",
           |      "@value" : "has name"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "a nom"
           |    }, {
           |      "@language" : "de",
           |      "@value" : "hat Namen"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "$anythingOntologyIri#"
           |  }
           |}
          """.stripMargin
    }

    def addCommentToPropertyThatHasNoComment(anythingOntologyIri: IRI, anythingLastModDate: Instant): String = {
        s"""{
           |    "@id": "$anythingOntologyIri",
           |    "@type": "owl:Ontology",
           |    "knora-api:lastModificationDate": {
           |        "@type": "xsd:dateTimeStamp",
           |        "@value": "$anythingLastModDate"
           |    },
           |    "@graph": [
           |        {
           |            "@id": "anything:hasBlueThing",
           |            "@type": "owl:ObjectProperty",
           |            "rdfs:comment": [
           |                {
           |                    "@language": "en",
           |                    "@value": "asdas asd as dasdasdas"
           |                }
           |            ]
           |        }
           |    ],
           |    "@context": {
           |        "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
           |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "owl": "http://www.w3.org/2002/07/owl#",
           |        "xsd": "http://www.w3.org/2001/XMLSchema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
           |        "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#"
           |    }
           |}""".stripMargin
    }

    object AThing {
        val iri: IRI = "http://rdfh.ch/0001/a-thing"
        val iriEncoded: String = URLEncoder.encode(iri, "UTF-8")
    }

    object AThingPicture {
        val iri: IRI = "http://rdfh.ch/0001/a-thing-picture"
        val iriEncoded: String = URLEncoder.encode(iri, "UTF-8")
        val stillImageFileValueUuid: IRI = "goZ7JFRNSeqF-dNxsqAS7Q"
    }

    object TestDing {
        val iri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw"
        val iriEncoded: String = URLEncoder.encode(iri, "UTF-8")

        val intValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg"
        val decimalValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg"
        val dateValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/-rG4F5FTTu2iB5mTBPVn5Q"
        val booleanValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/IN4R19yYR0ygi3K2VEHpUQ"
        val uriValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uBAmWuRhR-eo1u1eP7qqNg"
        val intervalValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/RbDKPKHWTC-0lkRKae-E6A"
        val timeValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/l6DhS5SCT9WhXSoYEZRTRw"
        val colorValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/TAziKNP8QxuyhC4Qf9-b6w"
        val geomValueIri: IRI = "http://rdfh.ch/0001/http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/we-ybmj-SRen-91n4RaDOQ"
        val geonameValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/hty-ONF8SwKN2RKU7rLKDg"
        val textValueWithStandoffIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/rvB4eQ5MTF-Qxq0YgkwaDg"
        val textValueWithoutStandoffIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ"
        val listValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/XAhEeE3kSVqM4JPGdLt4Ew"
        val linkValueIri: IRI = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uvRVxzL1RD-t9VIQ1TpfUw"

        val intValueUuid = "dJ1ES8QTQNepFKF5-EAqdg"
        val decimalValueUuid = "bXMwnrHvQH2DMjOFrGmNzg"
        val dateValueUuid = "-rG4F5FTTu2iB5mTBPVn5Q"
        val booleanValueUuid = "IN4R19yYR0ygi3K2VEHpUQ"
        val uriValueUuid = "uBAmWuRhR-eo1u1eP7qqNg"
        val intervalValueUuid = "RbDKPKHWTC-0lkRKae-E6A"
        val timeValueUuid = "l6DhS5SCT9WhXSoYEZRTRw"
        val colorValueUuid = "TAziKNP8QxuyhC4Qf9-b6w"
        val geomValueUuid = "we-ybmj-SRen-91n4RaDOQ"
        val geonameValueUuid = "hty-ONF8SwKN2RKU7rLKDg"
        val textValueWithStandoffUuid = "rvB4eQ5MTF-Qxq0YgkwaDg"
        val textValueWithoutStandoffUuid = "SZyeLLmOTcCCuS3B0VksHQ"
        val listValueUuid = "XAhEeE3kSVqM4JPGdLt4Ew"
        val linkValueUuid = "uvRVxzL1RD-t9VIQ1TpfUw"
    }

    val treeList: IRI = "http://rdfh.ch/lists/0001/treeList"

    val treeListNode: IRI = "http://rdfh.ch/lists/0001/treeList01"

    val otherTreeList: IRI = "http://rdfh.ch/lists/0001/otherTreeList"

    val testResponseValueIri: IRI = "http://rdfh.ch/0001/_GlNQXdYRTyQPhpdh76U1w/values/OGbYaSgNSUCKQtmn9suXlw"
    val testResponseValueUUID: UUID = UUID.fromString("84a3af57-ee99-486f-aa9c-e4ca1d19a57d")
    val testResponseValueCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
}
