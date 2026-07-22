/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.junit.runner.RunWith
import zio.Chunk
import zio.NonEmptyChunk
import zio.ZIO
import zio.test.Gen
import zio.test.Spec
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Description
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Keyword
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Logo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Longname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class KnoraProjectRepoLiveSpec extends ZIOSpecDefault {

  private val someProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/1234"),
    Shortname.unsafeFrom("project1"),
    Shortcode.unsafeFrom("1234"),
    Some(Longname.unsafeFrom("Project 1")),
    NonEmptyChunk(Description.unsafeFrom("A project", Some("en"))),
    List(Keyword.unsafeFrom("project1")),
    Some(Logo.unsafeFrom("logo.png")),
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set("foo", "bar").map(CopyrightHolder.unsafeFrom),
    Set(LicenseIri.CC_BY_4_0, LicenseIri.CC_BY_NC_4_0),
    Some(LicenseIri.CC_BY_4_0),
    Some(CopyrightHolder.unsafeFrom("University of Basel")),
    List("Hilma af Klint", "Lotte Reiniger").map(Authorship.unsafeFrom), // read back sorted by value
  )

  private val someProjectTrig =
    s"""|@prefix owl: <http://www.w3.org/2002/07/owl#> .
        |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
        |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
        |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
        |
        |<${AdminConstants.adminDataNamedGraph.value}> {
        |  <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
        |    knora-admin:projectShortcode "1234" ;
        |    knora-admin:projectShortname "project1" ;
        |    knora-admin:projectLongname "Project 1" ;
        |    knora-admin:projectDescription "A project"@en ;
        |    knora-admin:projectKeyword "project1" ;
        |    knora-admin:projectLogo "logo.png" ;
        |    knora-admin:status true ;
        |    knora-admin:hasSelfJoinEnabled false ;
        |    knora-admin:projectRestrictedViewSize "!128,128" ;
        |    knora-admin:hasAllowedCopyrightHolder "foo", "bar" ;
        |    knora-admin:hasEnabledLicense <${LicenseIri.CC_BY_4_0}>, <${LicenseIri.CC_BY_NC_4_0}> ;
        |    knora-admin:hasDataLicense <${LicenseIri.CC_BY_4_0}> ;
        |    knora-admin:hasDataCopyrightHolder "University of Basel" ;
        |    knora-admin:hasDefaultDataAuthorship "Lotte Reiniger", "Hilma af Klint" .
        |}
        |""".stripMargin

  private val KnoraProjectRepo = ZIO.serviceWithZIO[KnoraProjectRepo]
  private val builtInProjects  = org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.all

  override def spec: Spec[Any, Any] = suite("KnoraProjectRepoLive")(
    suite("save")(
      test("save a project") {
        for {
          saved   <- KnoraProjectRepo(_.save(someProject))
          project <- KnoraProjectRepo(_.findById(someProject.id)).someOrFail(Exception("Project not found"))
        } yield assertTrue(project == someProject, saved == someProject)
      },
      test("die for built in projects") {
        check(Gen.fromIterable(builtInProjects)) { project =>
          for {
            exit <- KnoraProjectRepo(_.save(project)).exit
          } yield assertTrue(exit.isFailure)
        }
      },
    ) @@ TestAspect.sequential @@ TestAspect.before(ZIO.serviceWith[CacheManager](_.clearAll())),
    suite("findAll")(
      test("return all projects if some exist") {
        for {
          _        <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
          projects <- KnoraProjectRepo(_.findAll())
        } yield assertTrue(
          projects.sortBy(_.id.value) == (Chunk(someProject) ++ builtInProjects).sortBy(_.id.value),
        )
      },
      test("return all built in projects") {
        for {
          projects <- KnoraProjectRepo(_.findAll())
        } yield assertTrue(projects.sortBy(_.id.value) == builtInProjects.sortBy(_.id.value))
      },
    ),
    suite("findBy ...")(
      suite("findById")(
        test("return project if it exists") {
          for {
            _      <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            actual <- KnoraProjectRepo(_.findById(ProjectIri.unsafeFrom("http://rdfh.ch/projects/1234")))
                        .someOrFail(Exception("Project not found"))
          } yield assertTrue(actual == someProject)
        },
        test("return None if project does not exist") {
          for {
            project <- KnoraProjectRepo(_.findById(ProjectIri.unsafeFrom("http://rdfh.ch/projects/unknown-project")))
          } yield assertTrue(project.isEmpty)
        },
        test("should find all built in projects") {
          check(Gen.fromIterable(builtInProjects)) { project =>
            for {
              actual <- KnoraProjectRepo(_.findById(project.id)).someOrFail(Exception("Project not found"))
            } yield assertTrue(actual == project)
          }
        },
      ),
      suite("find by Shortcode")(
        test("return project if it exists") {
          for {
            _      <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            actual <- KnoraProjectRepo(_.findByShortcode(Shortcode.unsafeFrom("1234")))
                        .someOrFail(Exception("Project not found"))
          } yield assertTrue(actual == someProject)
        },
        test("return None if project does not exist") {
          for {
            project <- KnoraProjectRepo(_.findByShortcode(Shortcode.unsafeFrom("1234")))
          } yield assertTrue(project.isEmpty)
        },
        test("should find all built in projects") {
          check(Gen.fromIterable(builtInProjects)) { project =>
            for {
              actual <- KnoraProjectRepo(_.findByShortcode(project.shortcode))
                          .someOrFail(Exception("Project not found"))
            } yield assertTrue(actual == project)
          }
        },
      ),
      suite("find by Shortname")(
        test("return project if it exists") {
          for {
            _      <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            actual <- KnoraProjectRepo(_.findByShortname(Shortname.unsafeFrom("project1")))
                        .someOrFail(Exception("Project not found"))
          } yield assertTrue(actual == someProject)
        },
        test("return None if project does not exist") {
          for {
            project <- KnoraProjectRepo(_.findByShortname(Shortname.unsafeFrom("project1")))
          } yield assertTrue(project.isEmpty)
        },
        test("should find all built in projects") {
          check(Gen.fromIterable(builtInProjects)) { project =>
            for {
              actual <-
                KnoraProjectRepo(_.findByShortname(project.shortname)).someOrFail(Exception("Project not found"))
            } yield assertTrue(actual == project)
          }
        },
      ),
    ),
    suite("findByPatternQuery")(
      // Pins the generated SPARQL. The selective pattern must precede the OPTIONAL blocks: OPTIONALs are
      // left-joins evaluated in document order, so a trailing pattern makes the triplestore compute them
      // for every entity of the class first (DEV-6796, prod tile-loading regression).
      test("place the selective pattern before the OPTIONAL blocks") {
        for {
          repo    <- ZIO.service[KnoraProjectRepoLive]
          query    = repo.findByPatternQuery(_.has(Vocabulary.KnoraAdmin.projectShortcode, "1234"))
          expected =
            """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
              |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |CONSTRUCT { ?s a knora-admin:knoraProject ;
              |    knora-admin:hasSelfJoinEnabled ?n0 ;
              |    knora-admin:projectDescription ?n1 ;
              |    knora-admin:projectShortcode ?n2 ;
              |    knora-admin:projectShortname ?n3 ;
              |    knora-admin:status ?n4 ;
              |    knora-admin:projectKeyword ?n5 ;
              |    knora-admin:projectLogo ?n6 ;
              |    knora-admin:projectLongname ?n7 ;
              |    knora-admin:projectRestrictedViewSize ?n8 ;
              |    knora-admin:projectRestrictedViewWatermark ?n9 ;
              |    knora-admin:hasAllowedCopyrightHolder ?n10 ;
              |    knora-admin:hasEnabledLicense ?n11 ;
              |    knora-admin:hasDataLicense ?n12 ;
              |    knora-admin:hasDataCopyrightHolder ?n13 ;
              |    knora-admin:hasDefaultDataAuthorship ?n14 . }
              |WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:projectShortcode "1234" .
              |?s a knora-admin:knoraProject ;
              |    knora-admin:hasSelfJoinEnabled ?n0 ;
              |    knora-admin:projectDescription ?n1 ;
              |    knora-admin:projectShortcode ?n2 ;
              |    knora-admin:projectShortname ?n3 ;
              |    knora-admin:status ?n4 .
              |OPTIONAL { ?s knora-admin:projectKeyword ?n5 . }
              |OPTIONAL { ?s knora-admin:projectLogo ?n6 . }
              |OPTIONAL { ?s knora-admin:projectLongname ?n7 . }
              |OPTIONAL { ?s knora-admin:projectRestrictedViewSize ?n8 . }
              |OPTIONAL { ?s knora-admin:projectRestrictedViewWatermark ?n9 . }
              |OPTIONAL { ?s knora-admin:hasAllowedCopyrightHolder ?n10 . }
              |OPTIONAL { ?s knora-admin:hasEnabledLicense ?n11 . }
              |OPTIONAL { ?s knora-admin:hasDataLicense ?n12 . }
              |OPTIONAL { ?s knora-admin:hasDataCopyrightHolder ?n13 . }
              |OPTIONAL { ?s knora-admin:hasDefaultDataAuthorship ?n14 . } } }
              |""".stripMargin
        } yield assertTrue(query.sparql == expected)
      },
    ),
  ).provide(KnoraProjectRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, CacheManager.layer, StringFormatter.test)
}
