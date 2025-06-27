/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import java.time.Instant

import dsp.constants.SalsahGui
import org.knora.webapi.IRI
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Logo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Longname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

/**
 * This object holds the same user which are loaded with 'test_data/project_data/admin-data.ttl'. Using this object
 * in tests, allows easier updating of details as they change over time.
 */
object SharedTestDataADM {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  /**
   * **********************************
   */
  /** System Admin Data                * */
  /**
   * **********************************
   */
  val systemProjectIri: IRI = KnoraProjectRepo.builtIn.SystemProject.id.value // built-in project

  val testPass: String = java.net.URLEncoder.encode("test", "utf-8")

  /* represents the user profile of 'root' as found in admin-data.ttl */
  def rootUser: User =
    User(
      id = "http://rdfh.ch/users/root",
      username = "root",
      email = "root@example.com",
      givenName = "System",
      familyName = "Administrator",
      status = true,
      lang = "de",
      password = Option("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          systemProjectIri -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value),
        ),
        administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]],
      ),
    )

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def superUser: User =
    User(
      id = "http://rdfh.ch/users/superuser",
      username = "superuser",
      email = "super.user@example.com",
      givenName = "Super",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          systemProjectIri -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value),
        ),
      ),
    )

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def normalUser: User =
    User(
      id = "http://rdfh.ch/users/normaluser",
      username = "normaluser",
      email = "normal.user@example.com",
      givenName = "Normal",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(),
    )

  /* represents the user profile of 'inactive user' as found in admin-data.ttl */
  def inactiveUser: User =
    User(
      id = "http://rdfh.ch/users/inactiveuser",
      username = "inactiveuser",
      email = "inactive.user@example.com",
      givenName = "Inactive",
      familyName = "User",
      status = false,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq.empty[Project],
      permissions = PermissionsDataADM(),
    )

  /* represents an anonymous user */
  def anonymousUser: User = KnoraSystemInstances.Users.AnonymousUser

  /* represents the 'multiuser' as found in admin-data.ttl */
  def multiuserUser: User =
    User(
      id = "http://rdfh.ch/users/multiuser",
      username = "multiuser",
      email = "multi.user@example.com",
      givenName = "Multi",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq(imagesReviewerGroup),
      projects = Seq(incunabulaProject, imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri.value -> List(
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
            KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
          ),
          imagesProjectIri.value -> List(
            "http://rdfh.ch/groups/00FF/images-reviewer",
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
            KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
          ),
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectAdminAll),
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
          imagesProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectAdminAll),
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  /* represents the full ProjectADM of the Knora System project */
  def systemProject: Project = Project(
    id = KnoraProjectRepo.builtIn.SystemProject.id,
    shortname = Shortname.unsafeFrom("SystemProject"),
    shortcode = Shortcode.unsafeFrom("FFFF"),
    longname = Some(Longname.unsafeFrom("Knora System Project")),
    description = Seq(StringLiteralV2.from(value = "Knora System Project", language = Some("en"))),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq(
      OntologyConstants.KnoraBase.KnoraBaseOntologyIri,
      OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri,
      SalsahGui.SalsahGuiOntologyIri,
      OntologyConstants.Standoff.StandoffOntologyIri,
    ),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /* represents the full ProjectADM of the default shared ontologies project */
  def defaultSharedOntologiesProject: Project = Project(
    id = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
    shortname = Shortname.unsafeFrom("DefaultSharedOntologiesProject"),
    shortcode = Shortcode.unsafeFrom("0000"),
    longname = Some(Longname.unsafeFrom("Default Knora Shared Ontologies Project")),
    description = Seq(StringLiteralV2.from(value = "Default Knora Shared Ontologies Project", language = Some("en"))),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq.empty[IRI],
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /**
   * **********************************
   */
  /** Images Demo Project Admin Data  * */
  /**
   * **********************************
   */
  val imagesProjectIri: ProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/00FF")

  /* represents 'user01' as found in admin-data.ttl  */
  def imagesUser01: User =
    User(
      id = "http://rdfh.ch/users/c266a56709",
      username = "user01.user1",
      email = "user01.user1@example.com",
      givenName = "User01",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          imagesProjectIri.value -> List(
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
            KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
          ),
        ),
        administrativePermissionsPerProject = Map(
          imagesProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectAdminAll),
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  /* represents 'user02' as found in admin-data.ttl  */
  def imagesUser02: User =
    User(
      id = "http://rdfh.ch/users/97cec4000f",
      username = "user02.user",
      email = "user02.user@example.com",
      givenName = "User02",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          imagesProjectIri.value -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
        ),
        administrativePermissionsPerProject = Map(
          imagesProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  /* represents 'images-reviewer-user' as found in admin-data.ttl  */
  def imagesReviewerUser: User =
    User(
      id = "http://rdfh.ch/users/images-reviewer-user",
      username = "images-reviewer-user",
      email = "images-reviewer-user@example.com",
      givenName = "User03",
      familyName = "User",
      status = true,
      lang = "de",
      password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"),
      groups = Seq(imagesReviewerGroup),
      projects = Seq(imagesProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          imagesProjectIri.value -> List(
            "http://rdfh.ch/groups/00FF/images-reviewer",
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
          ),
        ),
        administrativePermissionsPerProject = Map(
          imagesProjectIri.value -> Set(
            PermissionADM.from(
              Permission.Administrative.ProjectResourceCreateRestricted,
              s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild",
            ),
            PermissionADM.from(
              Permission.Administrative.ProjectResourceCreateRestricted,
              s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bildformat",
            ),
          ),
        ),
      ),
    )

  /* represents the full ProjectADM of the images project */
  def imagesProject: Project = Project(
    id = imagesProjectIri,
    shortname = Shortname.unsafeFrom("images"),
    shortcode = Shortcode.unsafeFrom("00FF"),
    longname = Some(Longname.unsafeFrom("Image Collection Demo")),
    description = Seq(StringLiteralV2.from(value = "A demo project of a collection of images", language = Some("en"))),
    keywords = Seq("images", "collection").sorted,
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /* represents the full ProjectADM of the images project in the external format */
  def imagesProjectExternal: Project = Project(
    id = imagesProjectIri,
    shortname = Shortname.unsafeFrom("images"),
    shortcode = Shortcode.unsafeFrom("00FF"),
    longname = Some(Longname.unsafeFrom("Image Collection Demo")),
    description = Seq(StringLiteralV2.from(value = "A demo project of a collection of images", language = Some("en"))),
    keywords = Seq("images", "collection").sorted,
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI_LocalHost),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /* represents the full GroupADM of the images ProjectAdmin group */
  def imagesProjectAdminGroup: Group = Group(
    id = "-",
    name = "ProjectAdmin",
    descriptions = Seq(StringLiteralV2.from(value = "Default Project Admin Group", language = Some("en"))),
    project = Some(imagesProject),
    status = true,
    selfjoin = false,
  )

  /* represents the full GroupADM of the images ProjectMember group */
  def imagesProjectMemberGroup: Group = Group(
    id = "-",
    name = "ProjectMember",
    descriptions = Seq(StringLiteralV2.from(value = "Default Project Member Group", language = Some("en"))),
    project = Some(imagesProject),
    status = true,
    selfjoin = false,
  )

  /* represents the full GroupADM of the images project reviewer group */
  def imagesReviewerGroup: Group = Group(
    id = "http://rdfh.ch/groups/00FF/images-reviewer",
    name = "Image reviewer",
    descriptions = Seq(StringLiteralV2.from(value = "A group for image reviewers.", language = Some("en"))),
    project = Some(imagesProject),
    status = true,
    selfjoin = false,
  )

  /* represents the full GroupADM of the images project reviewer group in the external format*/
  def imagesReviewerGroupExternal: Group = Group(
    id = "http://rdfh.ch/groups/00FF/images-reviewer",
    name = "Image reviewer",
    descriptions = Seq(StringLiteralV2.from(value = "A group for image reviewers.", language = Some("en"))),
    project = Some(imagesProjectExternal),
    status = true,
    selfjoin = false,
  )

  /**
   * **********************************
   */
  /** Incunabula Project Admin Data   * */
  /**
   * **********************************
   */
  val incunabulaProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0803")

  /* represents 'testuser' (Incunabula ProjectAdmin) as found in admin-data.ttl  */
  def incunabulaProjectAdminUser: User =
    User(
      id = "http://rdfh.ch/users/b83acc5f05",
      username = "user.test",
      email = "user.test@example.com",
      givenName = "User",
      familyName = "Test",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(incunabulaProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri.value -> List(
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
            KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
          ),
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectAdminAll),
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  /* represents 'root_alt' (Incunabula ProjectMember) as found in admin-data.ttl  */
  def incunabulaCreatorUser: User =
    User(
      id = "http://rdfh.ch/users/91e19f1e01",
      username = "root_alt",
      email = "root-alt@example.com",
      givenName = "Administrator-alt",
      familyName = "Admin-alt",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(incunabulaProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri.value -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  /* represents 'root_alt' (Incunabula Creator and ProjectMember) as found in admin-data.ttl  */
  def incunabulaMemberUser: User =
    User(
      id = "http://rdfh.ch/users/incunabulaMemberUser",
      username = "incunabulaMemberUser",
      email = "test.user2@test.ch",
      givenName = "User",
      familyName = "Test2",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(incunabulaProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          incunabulaProjectIri.value -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
        ),
        administrativePermissionsPerProject = Map(
          incunabulaProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  /* represents the ProjectADM of the incunabula project */
  def incunabulaProject: Project = Project(
    id = incunabulaProjectIri,
    shortname = Shortname.unsafeFrom("incunabula"),
    shortcode = Shortcode.unsafeFrom("0803"),
    longname = Some(Longname.unsafeFrom("Bilderfolgen Basler Frühdrucke")),
    description = Seq(
      StringLiteralV2.from(
        value =
          "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>",
        language = None,
      ),
    ),
    keywords = Seq(
      "Basler Frühdrucke",
      "Inkunabel",
      "Narrenschiff",
      "Wiegendrucke",
      "Sebastian Brant",
      "Bilderfolgen",
      "early print",
      "incunabula",
      "ship of fools",
      "Kunsthistorisches Seminar Universität Basel",
      "Late Middle Ages",
      "Letterpress Printing",
      "Basel",
      "Contectualisation of images",
    ).sorted,
    logo = Some(Logo.unsafeFrom("incunabula_logo.png")),
    ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /* represents the ProjectADM of the incunabula project in the external format*/
  def incunabulaProjectExternal: Project = Project(
    id = incunabulaProjectIri,
    shortname = Shortname.unsafeFrom("incunabula"),
    shortcode = Shortcode.unsafeFrom("0803"),
    longname = Some(Longname.unsafeFrom("Bilderfolgen Basler Frühdrucke")),
    description = Seq(
      StringLiteralV2.from(
        value =
          "<p>Das interdisziplinäre Forschungsprojekt \"<b><em>Die Bilderfolgen der Basler Frühdrucke: Spätmittelalterliche Didaxe als Bild-Text-Lektüre</em></b>\" verbindet eine umfassende kunstwissenschaftliche Analyse der Bezüge zwischen den Bildern und Texten in den illustrierten Basler Inkunabeln mit der Digitalisierung der Bestände der Universitätsbibliothek und der Entwicklung einer elektronischen Edition in der Form einer neuartigen Web-0.2-Applikation.\n</p>\n<p>Das Projekt wird durchgeführt vom <a href=\"http://kunsthist.unibas.ch\">Kunsthistorischen Seminar</a> der Universität Basel (Prof. B. Schellewald) und dem <a href=\"http://www.dhlab.unibas.ch\">Digital Humanities Lab</a> der Universität Basel (PD Dr. L. Rosenthaler).\n</p>\n<p>\nDas Kernstück der digitalen Edition besteht aus rund zwanzig reich bebilderten Frühdrucken aus vier verschiedenen Basler Offizinen. Viele davon sind bereits vor 1500 in mehreren Ausgaben erschienen, einige fast gleichzeitig auf Deutsch und Lateinisch. Es handelt sich um eine ausserordentlich vielfältige Produktion; neben dem Heilsspiegel finden sich ein Roman, die Melusine,  die Reisebeschreibungen des Jean de Mandeville, einige Gebets- und Erbauungsbüchlein, theologische Schriften, Fastenpredigten, die Leben der Heiligen Fridolin und Meinrad, das berühmte Narrenschiff  sowie die Exempelsammlung des Ritters vom Thurn.\n</p>\nDie Internetpublikation macht das digitalisierte Korpus dieser Frühdrucke  durch die Möglichkeiten nichtlinearer Verknüpfung und Kommentierung der Bilder und Texte, für die wissenschaftliche Edition sowie für die Erforschung der Bilder und Texte nutzbar machen. Auch können bereits bestehende und entstehende Online-Editionen damit verknüpft  werden , wodurch die Nutzung von Datenbanken anderer Institutionen im Hinblick auf unser Corpus optimiert wird.\n</p>",
        language = None,
      ),
    ),
    keywords = Seq(
      "Basler Frühdrucke",
      "Inkunabel",
      "Narrenschiff",
      "Wiegendrucke",
      "Sebastian Brant",
      "Bilderfolgen",
      "early print",
      "incunabula",
      "ship of fools",
      "Kunsthistorisches Seminar Universität Basel",
      "Late Middle Ages",
      "Letterpress Printing",
      "Basel",
      "Contectualisation of images",
    ).sorted,
    logo = Some(Logo.unsafeFrom("incunabula_logo.png")),
    ontologies = Seq(SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI_LocalHost),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /**
   * *********************************
   */
  /** Anything Admin Data            * */
  /**
   * *********************************
   */
  val anythingProjectIri: ProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  val anythingShortcode: Shortcode   = Shortcode.unsafeFrom("0001")
  val anythingOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI.toSmartIri)

  val anythingRdfData: RdfDataObject = RdfDataObject(
    path = "test_data/project_data/anything-data.ttl",
    name = anythingOntologyIri.toInternalSchema.toIri,
  )
  val anythingRdfOntologyData: RdfDataObject = RdfDataObject(
    path = "test_data/project_ontologies/anything-onto.ttl",
    name = "http://www.knora.org/ontology/0001/anything",
  )

  val customResourceIRI: IRI                    = "http://rdfh.ch/0001/rYAMw7wSTbGw3boYHefByg"
  val customResourceIRI_resourceWithValues: IRI = "http://rdfh.ch/0001/4PnSvolsTEa86KJ2EG76SQ"
  val customValueIRI_withResourceIriAndValueIRIAndValueUUID: IRI =
    "http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/fdqCOaqT6dP19pWI84X1XQ"
  val customValueUUID                     = "fdqCOaqT6dP19pWI84X1XQ"
  val customValueIRI: IRI                 = "http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/tdWAtnWK2qUC6tr4uQLAHA"
  val customResourceCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
  val customValueCreationDate: Instant    = Instant.parse("2020-06-09T17:04:54.502951Z")

  val customListIRI: IRI = "http://rdfh.ch/lists/0001/qq54wdGKR0S5zsbR5-9wtg"

  def anythingAdminUser: User =
    User(
      id = "http://rdfh.ch/users/AnythingAdminUser",
      username = "AnythingAdminUser",
      email = "anything.admin@example.org",
      givenName = "Anything",
      familyName = "Admin",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(anythingProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          anythingProjectIri.value -> List(
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
            KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
          ),
        ),
        administrativePermissionsPerProject = Map(
          anythingProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectAdminAll),
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  def anythingUser1: User =
    User(
      id = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      username = "anything.user01",
      email = "anything.user01@example.org",
      givenName = "Anything",
      familyName = "User01",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(anythingProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          anythingProjectIri.value -> List(
            KnoraGroupRepo.builtIn.ProjectMember.id.value,
            "http://rdfh.ch/groups/0001/thing-searcher",
          ),
        ),
        administrativePermissionsPerProject = Map(
          anythingProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  def anythingUser2: User =
    User(
      id = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
      username = "anything.user02",
      email = "anything.user02@example.org",
      givenName = "Anything",
      familyName = "User02",
      status = true,
      lang = "de",
      password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
      groups = Seq.empty[Group],
      projects = Seq(anythingProject),
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          anythingProjectIri.value -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value),
        ),
        administrativePermissionsPerProject = Map(
          anythingProjectIri.value -> Set(
            PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
          ),
        ),
      ),
    )

  def anythingProject: Project = Project(
    id = anythingProjectIri,
    shortname = Shortname.unsafeFrom("anything"),
    shortcode = Shortcode.unsafeFrom("0001"),
    longname = Some(Longname.unsafeFrom("Anything Project")),
    description = Seq(StringLiteralV2.from(value = "Anything Project", language = None)),
    keywords = Seq("things", "arbitrary test data").sorted,
    logo = None,
    ontologies = Seq(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI, SharedOntologyTestDataADM.SomethingOntologyIri),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  def anythingProjectExternal: Project = Project(
    id = anythingProjectIri,
    shortname = Shortname.unsafeFrom("anything"),
    shortcode = Shortcode.unsafeFrom("0001"),
    longname = Some(Longname.unsafeFrom("Anything Project")),
    description = Seq(StringLiteralV2.from(value = "Anything Project", language = None)),
    keywords = Seq("things", "arbitrary test data").sorted,
    logo = None,
    ontologies = Seq(
      SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost,
      SharedOntologyTestDataADM.SomethingOntologyIriLocalhost,
    ),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /* represents the full GroupADM of the Thing searcher group */
  def thingSearcherGroup: Group = Group(
    id = "http://rdfh.ch/groups/0001/thing-searcher",
    name = "Thing searcher",
    descriptions = Seq(StringLiteralV2.from(value = "A group for thing searchers.", language = Some("en"))),
    project = Some(anythingProject),
    status = true,
    selfjoin = true,
  )

  /**
   * *********************************
   */
  /** BEOL                           * */
  /**
   * *********************************
   */
  val beolProjectIri: ProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF")

  def beolProject: Project = Project(
    id = beolProjectIri,
    shortname = Shortname.unsafeFrom("beol"),
    shortcode = Shortcode.unsafeFrom("0801"),
    longname = Some(Longname.unsafeFrom("Bernoulli-Euler Online")),
    description = Seq(StringLiteralV2.from(value = "Bernoulli-Euler Online", language = None)),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq(
      "http://www.knora.org/ontology/0801/beol",
      "http://www.knora.org/ontology/0801/biblio",
      "http://www.knora.org/ontology/0801/leibniz",
      "http://www.knora.org/ontology/0801/newton",
    ),
    status = Status.Active,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /* represents the user profile of 'superuser' as found in admin-data.ttl */
  def beolUser: User = User(
    id = "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE",
    username = "beol",
    email = "beol@example.com",
    givenName = "BEOL",
    familyName = "BEOL",
    status = true,
    lang = "en",
    password = Some("$2a$10$fTEr/xVjPq7UBAy1O6KWKOM1scLhKGeRQdR4GTA997QPqHzXv0MnW"),
    groups = Seq.empty[Group],
    projects = Seq(beolProject),
    permissions = PermissionsDataADM(
      groupsPerProject = Map(
        beolProjectIri.value -> List(
          KnoraGroupRepo.builtIn.ProjectMember.id.value,
          KnoraGroupRepo.builtIn.ProjectAdmin.id.value,
        ),
      ),
      administrativePermissionsPerProject = Map(
        beolProjectIri.value -> Set(
          PermissionADM.from(Permission.Administrative.ProjectAdminAll),
        ),
      ),
    ),
  )

  /**
   * *********************************
   */
  /** DOKUBIB                        * */
  /**
   * *********************************
   */
  val dokubibProjectIri: ProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0804")

  def dokubibProject: Project = Project(
    id = dokubibProjectIri,
    shortname = Shortname.unsafeFrom("dokubib"),
    shortcode = Shortcode.unsafeFrom("0804"),
    longname = Some(Longname.unsafeFrom("Dokubib")),
    description = Seq(StringLiteralV2.from(value = "Dokubib", language = None)),
    keywords = Seq.empty[String],
    logo = None,
    ontologies = Seq("http://www.knora.org/ontology/0804/dokubib"),
    status = Status.Inactive,
    selfjoin = SelfJoin.CannotJoin,
    allowedCopyrightHolders = Set.empty,
    enabledLicenses = Set.empty,
  )

  /**
   * ***********************************
   * FREETEST
   * **********************************
   */
  val freetestOntologyIri: OntologyIri = OntologyIri.unsafeFrom(FREETEST_ONTOLOGY_IRI.toSmartIri)

  val freetestRdfOntologyData: RdfDataObject = RdfDataObject(
    path = "test_data/project_ontologies/freetest-onto.ttl",
    name = "http://www.knora.org/ontology/0001/freetest",
  )
}
