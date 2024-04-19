/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Chunk
import zio.NonEmptyChunk
import zio.ZIO
import zio.test.Gen
import zio.test.Spec
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.AdminConstants
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
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.EntityCache.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object KnoraProjectRepoLiveSpec extends ZIOSpecDefault {

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
  )

  private val someProjectTrig =
    s"""|@prefix owl: <http://www.w3.org/2002/07/owl#> .
        |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
        |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
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
        |    knora-admin:projectRestrictedViewSize "!128,128" .
        |}
        |""".stripMargin

  private val KnoraProjectRepo = ZIO.serviceWithZIO[KnoraProjectRepo]
  private val builtInProjects  = org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.all

  override def spec: Spec[Any, Any] = suite("KnoraProjectRepoLive")(
    suite("save")(
      test("save a project") {
        for {
          saved   <- KnoraProjectRepo(_.save(someProject))
          project <- KnoraProjectRepo(_.findById(someProject.id))
        } yield assertTrue(project.contains(someProject), saved == someProject)
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
            _ <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            project <- KnoraProjectRepo(
                         _.findById(ProjectIdentifierADM.IriIdentifier.unsafeFrom("http://rdfh.ch/projects/1234")),
                       )
          } yield assertTrue(project.contains(someProject))
        },
        test("return None if project does not exist") {
          for {
            project <-
              KnoraProjectRepo(
                _.findById(ProjectIdentifierADM.IriIdentifier.unsafeFrom("http://rdfh.ch/projects/unknown-project")),
              )
          } yield assertTrue(project.isEmpty)
        },
        test("should find all built in projects") {
          check(Gen.fromIterable(builtInProjects)) { project =>
            for {
              found <- KnoraProjectRepo(_.findById(project.id))
            } yield assertTrue(found.contains(project))
          }
        },
      ),
      suite("find by Shortcode")(
        test("return project if it exists") {
          for {
            _       <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            project <- KnoraProjectRepo(_.findById(ProjectIdentifierADM.ShortcodeIdentifier.unsafeFrom("1234")))
          } yield assertTrue(project.contains(someProject))
        },
        test("return None if project does not exist") {
          for {
            project <- KnoraProjectRepo(_.findById(ProjectIdentifierADM.ShortcodeIdentifier.unsafeFrom("1234")))
          } yield assertTrue(project.isEmpty)
        },
        test("should find all built in projects") {
          check(Gen.fromIterable(builtInProjects)) { project =>
            for {
              found <- KnoraProjectRepo(_.findByShortcode(project.shortcode))
            } yield assertTrue(found.contains(project))
          }
        },
      ),
      suite("find by Shortname")(
        test("return project if it exists") {
          for {
            _       <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            project <- KnoraProjectRepo(_.findById(ProjectIdentifierADM.ShortnameIdentifier.unsafeFrom("project1")))
          } yield assertTrue(project.contains(someProject))
        },
        test("return None if project does not exist") {
          for {
            project <- KnoraProjectRepo(_.findById(ProjectIdentifierADM.ShortnameIdentifier.unsafeFrom("project1")))
          } yield assertTrue(project.isEmpty)
        },
        test("should find all built in projects") {
          check(Gen.fromIterable(builtInProjects)) { project =>
            for {
              found <- KnoraProjectRepo(_.findByShortname(project.shortname))
            } yield assertTrue(found.contains(project))
          }
        },
      ),
    ),
  ).provide(KnoraProjectRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, CacheManager.layer, StringFormatter.test)
}
