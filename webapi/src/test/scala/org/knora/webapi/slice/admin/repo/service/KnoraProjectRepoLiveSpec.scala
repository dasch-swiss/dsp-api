/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.apache.jena.tdb.TDB
import zio.NonEmptyChunk
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
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
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object KnoraProjectRepoLiveSpec extends ZIOSpecDefault {

  private val someProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/1234"),
    Shortname.unsafeFrom("project1"),
    Shortcode.unsafeFrom("1234"),
    Some(Longname.unsafeFrom("Project 1")),
    NonEmptyChunk(Description.unsafeFrom("A project")),
    List(Keyword.unsafeFrom("project1")),
    Some(Logo.unsafeFrom("logo.png")),
    Status.Active,
    SelfJoin.CannotJoin,
    List(InternalIri("http://rdfh.ch/projects/1234/onto1"))
  )

  private val someProjectTrig =
    s"""|@prefix owl: <http://www.w3.org/2002/07/owl#> .
        |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
        |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
        |
        |<graph1> {
        |  <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
        |    knora-admin:projectShortcode "1234" ;
        |    knora-admin:projectShortname "project1" ;
        |    knora-admin:projectLongname "Project 1" ;
        |    knora-admin:projectDescription "A project"@en ;
        |    knora-admin:projectKeyword "project1" ;
        |    knora-admin:projectLogo "logo.png" ;
        |    knora-admin:status true ;
        |    knora-admin:hasSelfJoinEnabled false .
        |
        |  <http://rdfh.ch/projects/1234/onto1> a owl:Ontology ;
        |    knora-base:attachedToProject <http://rdfh.ch/projects/1234> .
        |}
        |""".stripMargin

  private def findAll: ZIO[KnoraProjectRepoLive, Throwable, List[KnoraProject]] =
    ZIO.serviceWithZIO[KnoraProjectRepoLive](_.findAll())

  private def findById(id: ProjectIdentifierADM): ZIO[KnoraProjectRepoLive, Throwable, Option[KnoraProject]] =
    ZIO.serviceWithZIO[KnoraProjectRepoLive](_.findById(id))

  override def spec: Spec[Any, Any] = suite("KnoraProjectRepoLive")(
    suite("findAll")(
      test("return all projects if some exist") {
        for {
          _        <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
          _         = TDB.getContext.set(TDB.symUnionDefaultGraph, true)
          projects <- findAll
        } yield assertTrue(projects == List(someProject))
      },
      test("return empty list if no projects exist") {
        for {
          projects <- findAll
        } yield assertTrue(projects.isEmpty)
      }
    ),
    suite("findById")(
      suite("find by IRI")(
        test("return project if it exists") {
          for {
            _       <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            _        = TDB.getContext.set(TDB.symUnionDefaultGraph, true)
            project <- findById(ProjectIdentifierADM.IriIdentifier.unsafeFrom("http://rdfh.ch/projects/1234"))
          } yield assertTrue(project.contains(someProject))
        },
        test("return None if project does not exist") {
          for {
            project <- findById(ProjectIdentifierADM.IriIdentifier.unsafeFrom("http://rdfh.ch/projects/1234"))
          } yield assertTrue(project.isEmpty)
        }
      ),
      suite("find by Shortcode")(
        test("return project if it exists") {
          for {
            _       <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            _        = TDB.getContext.set(TDB.symUnionDefaultGraph, true)
            project <- findById(ProjectIdentifierADM.ShortcodeIdentifier.unsafeFrom("1234"))
          } yield assertTrue(project.contains(someProject))
        },
        test("return None if project does not exist") {
          for {
            project <- findById(ProjectIdentifierADM.ShortcodeIdentifier.unsafeFrom("1234"))
          } yield assertTrue(project.isEmpty)
        }
      ),
      suite("find by Shortname")(
        test("return project if it exists") {
          for {
            _       <- TriplestoreServiceInMemory.setDataSetFromTriG(someProjectTrig)
            _        = TDB.getContext.set(TDB.symUnionDefaultGraph, true)
            project <- findById(ProjectIdentifierADM.ShortnameIdentifier.unsafeFrom("project1"))
          } yield assertTrue(project.contains(someProject))
        },
        test("return None if project does not exist") {
          for {
            project <- findById(ProjectIdentifierADM.ShortnameIdentifier.unsafeFrom("project1"))
          } yield assertTrue(project.isEmpty)
        }
      )
    )
  ).provide(KnoraProjectRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
