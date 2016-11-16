/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoV1, GroupPermissionV1}
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionProfileV1, PermissionV1}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoType, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}

/**
  * This object holds the same user which are loaded with '_test_data/all_data/admin-data.ttl'. Using this object
  * in tests, allows easier updating of user details as they change over time.
  */
object SharedTestData {

    /* represents the user profile of 'root' as found in admin-data.ttl */
    def rootUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/root"),
            username = Some("root"),
            firstname = Some("System"),
            lastname = Some("Administrator"),
            email = Some("root@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = List("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images"),
        sessionId = None,
        permissionProfile = PermissionProfileV1(
            projectInfos = List(
                SharedTestData.incunabulaProjectInfoV1.ofType(ProjectInfoType.SHORT),
                SharedTestData.imagesProjectInfoV1.ofType(ProjectInfoType.SHORT),
                SharedTestData.systemProjectInfoV1.ofType(ProjectInfoType.SHORT)
            ),
            groupsPerProject = Map(
                "http://data.knora.org/projects/77275339" -> List(s"${OntologyConstants.KnoraBase.ProjectMember}"),
                "http://www.knora.org/ontology/knora-base#SystemProject" -> List(s"${OntologyConstants.KnoraBase.SystemAdmin}"),
                "http://data.knora.org/projects/images" -> List(s"${OntologyConstants.KnoraBase.ProjectMember}")
            )
        )
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def superuserUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/superuser"),
            username = Some("superuser"),
            firstname = Some("Super"),
            lastname = Some("User"),
            email = Some("super.user@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = Vector.empty[IRI],
        sessionId = None,
        permissionProfile = PermissionProfileV1(
            groupsPerProject = Map(
                s"${OntologyConstants.KnoraBase.SystemProject}" -> List(s"${OntologyConstants.KnoraBase.SystemAdmin}")
            )
        )
    )

    /* represents the user profile of 'superuser' as found in admin-data.ttl */
    def normaluserUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/normaluser"),
            username = Some("normaluser"),
            firstname = Some("Normal"),
            lastname = Some("User"),
            email = Some("normal.user@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = Vector.empty[IRI],
        sessionId = None,
        permissionProfile = PermissionProfileV1()
    )

    /* represents an anonymous user */
    def anonymousUserProfileV1 = UserProfileV1(
        UserDataV1(
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = Vector.empty[IRI],
        sessionId = None,
        permissionProfile = PermissionProfileV1()
    )

    /* represents 'user01' as found in admin-data.ttl  */
    def user01UserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/c266a56709"),
            username = Some("user01"),
            firstname = Some("User01"),
            lastname = Some("User"),
            email = Some("user01.user1@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = List("http://data.knora.org/groups/images-reviewer"),
        projects = List("http://data.knora.org/projects/images"),
        sessionId = None,
        permissionProfile = PermissionProfileV1(
            groupsPerProject = Map(
                "http://data.knora.org/projects/images" -> List(s"${OntologyConstants.KnoraBase.ProjectAdmin}", s"${OntologyConstants.KnoraBase.ProjectMember}", "http://data.knora.org/groups/images-reviewer")
            )
        )
    )

    /* represents 'user02' as found in admin-data.ttl  */
    def user02UserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/97cec4000f"),
            username = Some("user02"),
            firstname = Some("User02"),
            lastname = Some("User"),
            email = Some("user02.user@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = List("http://data.knora.org/groups/images-reviewer"),
        projects = List("http://data.knora.org/projects/images"),
        sessionId = None,
        permissionProfile = PermissionProfileV1(
            groupsPerProject = Map(
                "http://data.knora.org/projects/images" -> List(s"${OntologyConstants.KnoraBase.ProjectMember}", "http://data.knora.org/groups/images-reviewer")
            )
        )
    )

    /* represents 'testuser' as found in admin-data.ttl  */
    def testuserUserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            username = Some("testuser"),
            firstname = Some("User"),
            lastname = Some("Test"),
            email = Some("user.test@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = Vector.empty[IRI],
        projects = List("http://data.knora.org/projects/77275339"),
        sessionId = None,
        permissionProfile = PermissionProfileV1(
            groupsPerProject = Map(
                "http://data.knora.org/projects/77275339" -> List(s"${OntologyConstants.KnoraBase.ProjectMember}")
            )
        )
    )

    /* represents the 'multiuser' as found in admin-data.ttl */
    def multiuserUserProfileV1 = UserProfileV1(
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/multiuser"),
            username = Some("multiuser"),
            firstname = Some("Multi"),
            lastname = Some("User"),
            email = Some("multi.user@example.com"),
            password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
            token = None,
            isActiveUser = Some(true),
            lang = "de"
        ),
        groups = List("http://data.knora.org/groups/images-reviewer"),
        projects = List("http://data.knora.org/projects/77275339", "http://data.knora.org/projects/images", "http://data.knora.org/projects/666"),
        sessionId = None,
        permissionProfile = PermissionProfileV1(
            groupsPerProject = Map(
                "http://data.knora.org/projects/77275339" -> List(s"${OntologyConstants.KnoraBase.ProjectAdmin}", s"${OntologyConstants.KnoraBase.ProjectMember}"),
                "http://data.knora.org/projects/images" -> List(s"${OntologyConstants.KnoraBase.ProjectAdmin}", s"${OntologyConstants.KnoraBase.ProjectMember}", "http://data.knora.org/groups/images-reviewer"),
                "http://data.knora.org/projects/666" -> List(s"${OntologyConstants.KnoraBase.ProjectAdmin}", s"${OntologyConstants.KnoraBase.ProjectMember}")
            ),
            administrativePermissionsPerProject = Map(
                "http://data.knora.org/projects/77275339" -> Set(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                ),
                "http://data.knora.org/projects/images" -> Set(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                ),
                "http://data.knora.org/projects/666" -> Set(
                    PermissionV1.ProjectResourceCreateAllPermission,
                    PermissionV1.ProjectAdminAllPermission
                )
            ),
            defaultObjectAccessPermissionsPerProject = Map.empty[IRI, Set[PermissionV1]]
        )
    )

    /* represents the full project info of the Knora System project */
    def systemProjectInfoV1 = ProjectInfoV1(
        id = "http://www.knora.org/ontology/knora-base#SystemProject",
        shortname = "SystemProject",
        longname = Some("Knora System Project"),
        description = None,
        projectOntologyGraph = "-",
        projectDataGraph = "-",
        basepath = Some("-"),
        isActiveProject = Some(true),
        hasSelfJoinEnabled = Some(false),
        rights = None
    )

    /* represents the full project info of the images project */
    def imagesProjectInfoV1 = ProjectInfoV1(
        id = "http://data.knora.org/projects/images",
        shortname = "images",
        longname = Some("Image Collection Demo"),
        description = Some("A demo project of a collection of images"),
        keywords = Some("images, collection"),
        projectOntologyGraph = "http://www.knora.org/ontology/images",
        projectDataGraph = "http://www.knora.org/data/images",
        logo = None,
        basepath = Some("/imldata/SALSAH-TEST-01/images"),
        isActiveProject = Some(true),
        hasSelfJoinEnabled = Some(false),
        rights = None
    )

    /* represents the ProjectInfoV1 of the incunabula project */
    def incunabulaProjectInfoV1 = ProjectInfoV1(
        id = "http://data.knora.org/projects/77275339",
        shortname = "incunabula",
        longname = Some("Bilderfolgen Basler Frühdrucke"),
        description = Some("<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>"),
        keywords = Some("Basler Frühdrucke, Inkunabel, Narrenschiff, Wiegendrucke, Sebastian Brant, Bilderfolgen, early print, incunabula, ship of fools, Kunsthistorischs Seminar Universität Basel, Late Middle Ages, Letterpress Printing, Basel, Contectualisation of images"),
        projectOntologyGraph = "http://www.knora.org/ontology/incunabula",
        projectDataGraph = "http://www.knora.org/data/incunabula",
        logo = Some("incunabula_logo.png"),
        basepath = Some("/imldata/SALSAH-TEST-01/Incunabula"),
        isActiveProject = Some(true),
        hasSelfJoinEnabled = Some(false),
        rights = None
    )

    /* represents the ProjectInfoV1of the testproject */
    def testprojectProjectInfoV1 = ProjectInfoV1(
        id = "http://data.knora.org/projects/666",
        shortname = "testproject",
        longname = Some("Test Project"),
        description = Some("A test project"),
        keywords = None,
        projectOntologyGraph = "http://www.knora.org/ontology/testproject",
        projectDataGraph = "http://www.knora.org/data/testproject",
        logo = None,
        basepath = Some("/imldata/testproject"),
        isActiveProject = Some(true),
        hasSelfJoinEnabled = Some(false),
        rights = None
    )

    /* represents the full GroupInfoV1 of the images ProjectAdmin group */
    def imagesProjectAdminGroupInfoV1 = GroupInfoV1(
        id = "-",
        name = "ProjectAdmin",
        description = Some("Default Project Admin Group"),
        belongsToProject = "http://data.knora.org/projects/images",
        isActiveGroup = true,
        hasSelfJoinEnabled = false,
        hasPermissions = Vector.empty[GroupPermissionV1]
    )

    /* represents the full GroupInfoV1 of the images ProjectMember group */
    def imagesProjectMemberGroupInfoV1 = GroupInfoV1(
        id = "-",
        name = "ProjectMember",
        description = Some("Default Project Member Group"),
        belongsToProject = "http://data.knora.org/projects/images",
        isActiveGroup = true,
        hasSelfJoinEnabled = false,
        hasPermissions = Vector.empty[GroupPermissionV1]
    )

    /* represents the full GroupInfoV1 of the images project reviewer group */
    def imageReviewerGroupInfoV1 = GroupInfoV1(
        id = "http://data.knora.org/groups/images-reviewer",
        name = "Image reviewer",
        description = Some("A group for image reviewers."),
        belongsToProject = "http://data.knora.org/projects/images",
        isActiveGroup = true,
        hasSelfJoinEnabled = false,
        hasPermissions = Vector.empty[GroupPermissionV1]
    )
}
