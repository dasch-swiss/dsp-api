/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.junit.runner.RunWith
import zio.Chunk
import zio.Ref
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.test.Gen
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory.UserGroup.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.service.AbstractInMemoryCrudRepository
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

final case class KnoraGroupRepoInMemory(groups: Ref[Chunk[KnoraGroup]])
    extends AbstractInMemoryCrudRepository[KnoraGroup, GroupIri](groups, _.id)
    with KnoraGroupRepo {
  override def findByName(name: GroupName): Task[Option[KnoraGroup]] =
    groups.get.map(_.find(_.groupName == name))

  override def findByProjectIri(projectIri: KnoraProject.ProjectIri): Task[Chunk[KnoraGroup]] =
    groups.get.map(_.filter(_.belongsToProject.contains(projectIri)))
}

object KnoraGroupRepoInMemory {
  val layer = ZLayer.fromZIO(Ref.make(Chunk.empty[KnoraGroup])) >>>
    ZLayer.derive[KnoraGroupRepoInMemory]
}

@RunWith(classOf[DspZTestJUnitRunner])
class KnoraGroupRepoLiveSpec extends ZIOSpecDefault {

  private val KnoraGroupRepo = ZIO.serviceWithZIO[KnoraGroupRepo]
  private val builtInGroups  = org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.all

  private val findById = suite("findById")(
    test("given a non existing user should return None") {
      for {
        userGroup <- KnoraGroupRepo(_.findById(GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234")))
      } yield assertTrue(userGroup.isEmpty)
    },
    test("given an existing user should return that user") {
      for {
        _         <- KnoraGroupRepo(_.save(testUserGroup))
        userGroup <- KnoraGroupRepo(_.findById(testUserGroup.id))
      } yield assertTrue(userGroup.contains(testUserGroup))
    },
    test("should find all builtIn users") {
      check(Gen.fromIterable(builtInGroups)) { group =>
        for {
          actual <- KnoraGroupRepo(_.findById(group.id))
        } yield assertTrue(actual.contains(group))
      }
    },
  )

  private val findAll = suite("findAll")(
    test("should return built in users") {
      for {
        userGroup <- KnoraGroupRepo(_.findAll())
      } yield assertTrue(userGroup.sortBy(_.id.value) == builtInGroups.sortBy(_.id.value))
    },
    test("skip a group subject missing a required property (groupName), and still return the valid ones") {
      // "missingGroupName" has no knora-admin:groupName triple, so the mapper fails with a
      // (non-defect) LiteralNotPresent RdfError. findAllResilient must skip it, not fail the whole batch.
      val brokenTriples = Rdf
        .iri("http://rdfh.ch/groups/0001/missingGroupName")
        .has(RDF.TYPE, Vocabulary.KnoraAdmin.UserGroup)
        .andHas(Vocabulary.KnoraAdmin.status, Rdf.literalOf(true))
        .andHas(Vocabulary.KnoraAdmin.hasSelfJoinEnabled, Rdf.literalOf(false))
      val brokenQuery = Update(
        Queries
          .INSERT_DATA(brokenTriples)
          .into(Rdf.iri(adminDataNamedGraph.value))
          .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS)),
      )
      for {
        _         <- KnoraGroupRepo(_.save(testUserGroup))
        _         <- ZIO.serviceWithZIO[TriplestoreService](_.query(brokenQuery))
        userGroup <- KnoraGroupRepo(_.findAll())
      } yield assertTrue(
        userGroup.sortBy(_.id.value) == (builtInGroups ++ Chunk(testUserGroup)).sortBy(_.id.value),
      )
    },
  )

  private val save: Spec[KnoraGroupRepo, Throwable] = suite("save")(
    test("should update fields") {
      for {
        _ <- KnoraGroupRepo(_.save(testUserGroup))

        testUserGroupModified =
          testUserGroup.copy(
            groupName = GroupName.unsafeFrom("another"),
            belongsToProject = None,
          )
        _         <- KnoraGroupRepo(_.save(testUserGroupModified))
        userGroup <- KnoraGroupRepo(_.findById(testUserGroup.id))
      } yield assertTrue(userGroup.contains(testUserGroupModified))
    },
    test("should die for built in groups") {
      check(Gen.fromIterable(builtInGroups)) { group =>
        for {
          exit <- KnoraGroupRepo(_.save(group)).exit
        } yield assertTrue(exit.isFailure)
      }
    },
  )
  val spec: Spec[Any, Any] = suite("KnoraUserGroupRepoLive")(findById, findAll, save)
    .provide(
      KnoraGroupRepoLive.layer,
      CacheManager.layer,
      TriplestoreServiceInMemory.emptyLayer,
      StringFormatter.test,
    )
}
