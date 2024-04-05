/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.sharedtestdata
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo

/**
 * This object holds the same user which are loaded with 'test_data/project_data/admin-data.ttl'. Using this object
 * in tests, allows easier updating of details as they change over time.
 */
object SharedTestDataADM2 {

  /**
   * **********************************
   *
   * System Admin Data
   *
   * **********************************
   */

  /* represents the user profile of 'root' as found in admin-data.ttl */
  def rootUser: UserProfile = UserProfile.from(SharedTestDataADM.rootUser)

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def superUser: UserProfile = UserProfile.from(SharedTestDataADM.superUser)

  /* represents the user profile of 'normal user' as found in admin-data.ttl */
  def normalUser: UserProfile = UserProfile.from(SharedTestDataADM.normalUser)

  /* represents the user profile of 'inactive user' as found in admin-data.ttl */
  def inactiveUser: UserProfile = UserProfile.from(SharedTestDataADM.inactiveUser)

  /* represents an anonymous user */
  def anonymousUser: UserProfile = UserProfile.from(SharedTestDataADM.anonymousUser)

  /* represents the 'multiuser' as found in admin-data.ttl */
  def multiuserUser = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/multiuser"),
      firstname = Some("Multi"),
      lastname = Some("User"),
      email = Some("multi.user@example.com"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = List("http://rdfh.ch/groups/00FF/images-reviewer"),
    projects_info = Map(incunabulaProjectIri -> incunabulaProjectInfo, imagesProjectIri -> imagesProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        incunabulaProjectIri -> List(
          s"${KnoraGroupRepo.builtIn.ProjectMember.id.value}",
          s"${KnoraGroupRepo.builtIn.ProjectAdmin.id.value}",
        ),
        imagesProjectIri -> List(
          "http://rdfh.ch/groups/00FF/images-reviewer",
          s"${KnoraGroupRepo.builtIn.ProjectMember.id.value}",
          s"${KnoraGroupRepo.builtIn.ProjectAdmin.id.value}",
        ),
      ),
      administrativePermissionsPerProject = Map(
        incunabulaProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectAdminAll),
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
        imagesProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectAdminAll),
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  /**
   * **********************************
   */
  /** Images Demo Project Admin Data   * */
  /**
   * **********************************
   */
  val imagesProjectIri = "http://rdfh.ch/projects/00FF"

  /* represents 'user01' as found in admin-data.ttl  */
  def imagesUser01 = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/c266a56709"),
      firstname = Some("User01"),
      lastname = Some("User"),
      email = Some("user01.user1@example.com"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = List.empty[IRI],
    projects_info = Map(imagesProjectIri -> imagesProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        imagesProjectIri -> List(
          KnoraGroupRepo.builtIn.ProjectMember.id.value,
          KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
        ),
      ),
      administrativePermissionsPerProject = Map(
        imagesProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectAdminAll),
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  /* represents 'user02' as found in admin-data.ttl  */
  def imagesUser02 = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/97cec4000f"),
      firstname = Some("User02"),
      lastname = Some("User"),
      email = Some("user02.user@example.com"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = List.empty[IRI],
    projects_info = Map(imagesProjectIri -> imagesProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        imagesProjectIri -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
      ),
      administrativePermissionsPerProject = Map(
        imagesProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  /* represents 'images-reviewer-user' as found in admin-data.ttl  */
  def imagesReviewerUser = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/images-reviewer-user"),
      firstname = Some("User03"),
      lastname = Some("User"),
      email = Some("images-reviewer-user@example.com"),
      password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = List("http://rdfh.ch/groups/00FF/images-reviewer"),
    projects_info = Map(imagesProjectIri -> imagesProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        imagesProjectIri -> List(
          "http://rdfh.ch/groups/00FF/images-reviewer",
          s"${KnoraGroupRepo.builtIn.ProjectMember.id.value}",
        ),
      ),
      administrativePermissionsPerProject = Map(
        imagesProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateRestricted, s"$IMAGES_ONTOLOGY_IRI#bild"),
          PermissionADM.from(
            Permission.Administrative.ProjectResourceCreateRestricted,
            s"$IMAGES_ONTOLOGY_IRI#bildformat",
          ),
        ),
      ),
    ),
  )

  /* represents the full project info of the images project */
  def imagesProjectInfo = sharedtestdata.ProjectInfo(
    id = imagesProjectIri,
    shortname = "images",
    shortcode = "00FF",
    longname = Some("Image Collection Demo"),
    description = Some("A demo project of a collection of images"),
    keywords = Some("collection, images"),
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI),
    status = true,
    selfjoin = false,
  )

  /**
   * **********************************
   */
  /** Incunabula Project Admin Data    * */
  /**
   * **********************************
   */
  val incunabulaProjectIri = "http://rdfh.ch/projects/0803"

  /* represents 'testuser' (Incunabula ProjectAdmin) as found in admin-data.ttl  */
  def incunabulaProjectAdminUser = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/b83acc5f05"),
      firstname = Some("User"),
      lastname = Some("Test"),
      email = Some("user.test@example.com"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = Vector.empty[IRI],
    projects_info = Map(incunabulaProjectIri -> incunabulaProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        incunabulaProjectIri -> List(
          s"${KnoraGroupRepo.builtIn.ProjectMember.id.value}",
          s"${KnoraGroupRepo.builtIn.ProjectAdmin.id.value}",
        ),
      ),
      administrativePermissionsPerProject = Map(
        incunabulaProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectAdminAll),
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  /* represents 'root_alt' (Incunabula ProjectMember) as found in admin-data.ttl  */
  def incunabulaCreatorUser = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/91e19f1e01"),
      firstname = Some("Administrator-alt"),
      lastname = Some("Admin-alt"),
      email = Some("root-alt@example.com"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = Vector.empty[IRI],
    projects_info = Map(incunabulaProjectIri -> incunabulaProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        incunabulaProjectIri -> List(s"${KnoraGroupRepo.builtIn.ProjectMember.id.value}"),
      ),
      administrativePermissionsPerProject = Map(
        incunabulaProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  /* represents 'root_alt' (Incunabula Creator and ProjectMember) as found in admin-data.ttl  */
  def incunabulaMemberUser = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/incunabulaMemberUser"),
      firstname = Some("User"),
      lastname = Some("Test2"),
      email = Some("test.user2@test.ch"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = Vector.empty[IRI],
    projects_info = Map(incunabulaProjectIri -> incunabulaProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        incunabulaProjectIri -> List(s"${KnoraGroupRepo.builtIn.ProjectMember.id.value}"),
      ),
      administrativePermissionsPerProject = Map(
        incunabulaProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  /* represents the ProjectInfoV1 of the incunabula project */
  def incunabulaProjectInfo = sharedtestdata.ProjectInfo(
    id = incunabulaProjectIri,
    shortname = "incunabula",
    shortcode = "0803",
    longname = Some("Bilderfolgen Basler Frühdrucke"),
    description = Some(
      "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>",
    ),
    keywords = Some(
      "Basel, Basler Frühdrucke, Bilderfolgen, Contectualisation of images, Inkunabel, Kunsthistorisches Seminar Universität Basel, Late Middle Ages, Letterpress Printing, Narrenschiff, Sebastian Brant, Wiegendrucke, early print, incunabula, ship of fools",
    ),
    logo = Some("incunabula_logo.png"),
    ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI),
    status = true,
    selfjoin = false,
  )

  /**
   * *********************************
   */
  /** Anything Admin Data             * */
  /**
   * *********************************
   */
  val anythingProjectIri = "http://rdfh.ch/projects/0001"

  def anythingAdminUser = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/AnythingAdminUser"),
      firstname = Some("Anything"),
      lastname = Some("Admin"),
      email = Some("anything.admin@example.org"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = Seq.empty[IRI],
    projects_info = Map(anythingProjectIri -> anythingProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        anythingProjectIri -> List(
          KnoraGroupRepo.builtIn.ProjectMember.id.value,
          KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
        ),
      ),
      administrativePermissionsPerProject = Map(
        anythingProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectAdminAll),
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  def anythingUser1 = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"),
      firstname = Some("Anything"),
      lastname = Some("User01"),
      email = Some("anything.user01@example.org"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = Seq.empty[IRI],
    projects_info = Map(anythingProjectIri -> anythingProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        anythingProjectIri -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
      ),
      administrativePermissionsPerProject = Map(
        anythingProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  def anythingUser2 = sharedtestdata.UserProfile(
    userData = UserData(
      user_id = Some("http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"),
      firstname = Some("Anything"),
      lastname = Some("User02"),
      email = Some("anything.user02@example.org"),
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"), // -> "test"
      token = None,
      status = Some(true),
      lang = "de",
    ),
    groups = Vector.empty[IRI],
    projects_info = Map(anythingProjectIri -> anythingProjectInfo),
    permissionData = PermissionsDataADM(
      groupsPerProject = Map(
        anythingProjectIri -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
      ),
      administrativePermissionsPerProject = Map(
        anythingProjectIri -> Set(
          PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
        ),
      ),
    ),
  )

  def anythingProjectInfo = sharedtestdata.ProjectInfo(
    id = anythingProjectIri,
    shortname = "anything",
    shortcode = "0001",
    longname = Some("Anything Project"),
    description = Some("Anything Project"),
    keywords = None,
    logo = None,
    ontologies = Seq("http://www.knora.org/ontology/0001/anything", "http://www.knora.org/ontology/0001/something"),
    status = true,
    selfjoin = false,
  )

  /**
   * *********************************
   */
  /** BEOL                            * */
  /**
   * *********************************
   */
  val beolProjectIri = "http://rdfh.ch/projects/0801"

  def beolProjectInfo = sharedtestdata.ProjectInfo(
    id = beolProjectIri,
    shortname = "beol",
    shortcode = "0801",
    longname = Some("Bernoulli-Euler Online"),
    description = Some("Bernoulli-Euler Online"),
    keywords = None,
    logo = None,
    ontologies = Seq("http://www.knora.org/ontology/0801/beol"),
    status = true,
    selfjoin = false,
  )
  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def beolUser = sharedtestdata.UserProfile(
    UserData(
      user_id = Some("http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE"),
      firstname = Some("BEOL"),
      lastname = Some("BEOL"),
      email = Some("beol@example.com"),
      password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"), // -> "test"
      token = None,
      status = Some(true),
      lang = "en",
    ),
    groups = Vector.empty[IRI],
    projects_info = Map(beolProjectIri -> beolProjectInfo),
    permissionData = PermissionsDataADM(),
  )

  /**
   * *********************************
   */
  /** DOKUBIB                         * */
  /**
   * *********************************
   */
  val dokubibProjectIri = "http://rdfh.ch/projects/0804"

  def dokubibProjectInfo = sharedtestdata.ProjectInfo(
    id = dokubibProjectIri,
    shortname = "dokubib",
    shortcode = "0804",
    longname = Some("Dokubib"),
    description = Some("Dokubib"),
    keywords = None,
    logo = None,
    ontologies = Seq("http://www.knora.org/ontology/0804/dokubib"),
    status = false,
    selfjoin = false,
  )

  /**
   * *********************************
   */
  /** WEBERN                          * */
  /**
   * *********************************
   */
  val webernProjectIIri = "http://rdfh.ch/projects/0806"

  def webernProjectInfo = sharedtestdata.ProjectInfo(
    id = webernProjectIIri,
    shortname = "webern",
    shortcode = "0806",
    longname = Some("Anton Webern Gesamtausgabe"),
    description = Some("Historisch-kritische Edition des Gesamtschaffens von Anton Webern."),
    keywords = None,
    logo = None,
    ontologies = Seq("http://www.knora.org/ontology/0806/webern"),
    status = false,
    selfjoin = false,
  )
}
