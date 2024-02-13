/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.RIO
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.TestDataFactory
import org.knora.webapi.TestDataFactory.User.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object KnoraUserRepoLiveSpec extends ZIOSpecDefault {

  // Accessor functions of the UserRepo service which are tested
  def save(user: KnoraUser): ZIO[KnoraUserRepo, Throwable, KnoraUser] = ZIO.serviceWithZIO[KnoraUserRepo](_.save(user))
  def findById(id: UserIri): ZIO[KnoraUserRepo, Throwable, Option[KnoraUser]] =
    ZIO.serviceWithZIO[KnoraUserRepo](_.findById(id))
  def findByEmail(id: Email): ZIO[KnoraUserRepo, Throwable, Option[KnoraUser]] =
    ZIO.serviceWithZIO[KnoraUserRepo](_.findByEmail(id))
  def findByUsername(id: Username): ZIO[KnoraUserRepo, Throwable, Option[KnoraUser]] =
    ZIO.serviceWithZIO[KnoraUserRepo](_.findByUsername(id))
  def findAll(): ZIO[KnoraUserRepo, Throwable, List[KnoraUser]] = ZIO.serviceWithZIO[KnoraUserRepo](_.findAll())

  private def createUserQuery(u: KnoraUser): Update = {
    val triples = Rdf
      .iri(u.id.value)
      .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
      .andHas(Vocabulary.KnoraAdmin.username, Rdf.literalOf(u.username.value))
      .andHas(Vocabulary.KnoraAdmin.email, Rdf.literalOf(u.email.value))
      .andHas(Vocabulary.KnoraAdmin.givenName, Rdf.literalOf(u.givenName.value))
      .andHas(Vocabulary.KnoraAdmin.familyName, Rdf.literalOf(u.familyName.value))
      .andHas(Vocabulary.KnoraAdmin.preferredLanguage, Rdf.literalOf(u.preferredLanguage.value))
      .andHas(Vocabulary.KnoraAdmin.status, Rdf.literalOf(u.status.value))
      .andHas(Vocabulary.KnoraAdmin.password, Rdf.literalOf(u.password.value))
      .andHas(Vocabulary.KnoraAdmin.isInSystemAdminGroup, Rdf.literalOf(u.isInSystemAdminGroup.value))
    u.isInProject.foreach(prjIri => triples.andHas(Vocabulary.KnoraAdmin.isInProject, Rdf.iri(prjIri.value)))
    u.isInGroup.foreach(grpIri => triples.andHas(Vocabulary.KnoraAdmin.isInGroup, Rdf.iri(grpIri.value)))
    u.isInProjectAdminGroup.foreach(admGrp =>
      triples.andHas(Vocabulary.KnoraAdmin.isInProjectAdminGroup, Rdf.iri(admGrp.value))
    )
    Update(
      Queries
        .INSERT_DATA(triples)
        .into(Rdf.iri(adminDataNamedGraph.value))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
    )
  }

  def storeUsersInTripleStore(users: KnoraUser*): RIO[TriplestoreService, Unit] =
    ZIO.foreach(users)(user => ZIO.serviceWithZIO[TriplestoreService](_.query(createUserQuery(user)))).unit

  val spec: Spec[Any, Any] = suite("UserRepoLiveSpec")(
    suite("findById")(
      test("findById given an  existing user should return that user") {
        for {
          _    <- storeUsersInTripleStore(testUser)
          user <- findById(testUser.id)
        } yield assertTrue(user.contains(testUser))
      },
      test("findById given a non existing user should return None") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- findById(UserIri.unsafeFrom("http://rdfh.ch/users/doesNotExist"))
        } yield assertTrue(user.isEmpty)
      }
    ),
    suite("findByEmail")(
      test("findByEmail given an existing user should return that user") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- findByEmail(testUser.email)
        } yield assertTrue(user.contains(testUser))
      },
      test("findByEmail given a non existing user should return None") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- findByEmail(Email.unsafeFrom("doesNotExist@example.com"))
        } yield assertTrue(user.isEmpty)
      }
    ),
    suite("findByUsername")(
      test("findByUsername given an existing user should return that user") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- findByUsername(testUserWithoutAnyGroups.username)
        } yield assertTrue(user.contains(testUserWithoutAnyGroups))
      },
      test("findByUsername given a non existing user should return None") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- findByUsername(Username.unsafeFrom("doesNotExistUsername"))
        } yield assertTrue(user.isEmpty)
      }
    ),
    suite("findAll")(
      test("given existing users should return all user") {
        for {
          _     <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          users <- findAll()
        } yield assertTrue(users.sortBy(_.id.value) == List(testUser, testUserWithoutAnyGroups).sortBy(_.id.value))
      },
      test("given no users present should return empty result") {
        for {
          users <- findAll()
        } yield assertTrue(users.isEmpty)
      }
    ),
    suite("save")(
      test("should create and find user") {
        for {
          savedUser <- save(testUser)
          foundUser <- findById(savedUser.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(savedUser == testUser, foundUser == testUser)
      },
      test("should update an existing user's username find user with new username") {
        for {
          _           <- save(testUser)                                                     // create the user
          updated     <- save(testUser.copy(username = Username.unsafeFrom("newUsername"))) // update the username
          updatedUser <- findByUsername(updated.username).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.username == Username.unsafeFrom("newUsername"))
      },
      test("should update an existing user isInProject ") {
        for {
          _           <- save(testUserWithoutAnyGroups) // create the user
          _           <- save(testUserWithoutAnyGroups.copy(isInProject = Chunk(TestDataFactory.someProject.id)))
          updatedUser <- findById(testUserWithoutAnyGroups.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProject == Chunk(TestDataFactory.someProject.id))
      },
      test("should update an existing user isInProject and remove them") {
        for {
          _           <- save(testUserWithoutAnyGroups) // create the user
          _           <- save(testUserWithoutAnyGroups.copy(isInProject = Chunk(TestDataFactory.someProject.id)))
          _           <- save(testUserWithoutAnyGroups.copy(isInProject = Chunk.empty))
          updatedUser <- findById(testUserWithoutAnyGroups.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProject.isEmpty)
      },
      test("should update an existing user isInProjectAdminGroup ") {
        for {
          _           <- save(testUserWithoutAnyGroups) // create the user
          _           <- save(testUserWithoutAnyGroups.copy(isInProjectAdminGroup = Chunk(TestDataFactory.someProject.id)))
          updatedUser <- findById(testUserWithoutAnyGroups.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProjectAdminGroup == Chunk(TestDataFactory.someProject.id))
      },
      test("should update an existing user isInProjectAdminGroup and remove them") {
        for {
          _           <- save(testUserWithoutAnyGroups) // create the user
          _           <- save(testUserWithoutAnyGroups.copy(isInProjectAdminGroup = Chunk(TestDataFactory.someProject.id)))
          _           <- save(testUserWithoutAnyGroups.copy(isInProjectAdminGroup = Chunk.empty))
          updatedUser <- findById(testUserWithoutAnyGroups.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProjectAdminGroup.isEmpty)
      },
      test("should update an existing user isInGroup ") {
        val groupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001")
        for {
          _           <- save(testUserWithoutAnyGroups) // create the user
          _           <- save(testUserWithoutAnyGroups.copy(isInGroup = Chunk(groupIri)))
          updatedUser <- findById(testUserWithoutAnyGroups.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInGroup == Chunk(groupIri))
      },
      test("should update an existing user isInGroup and remove them") {
        val groupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001")
        for {
          _           <- save(testUserWithoutAnyGroups) // create the user
          _           <- save(testUserWithoutAnyGroups.copy(isInGroup = Chunk(groupIri)))
          _           <- save(testUserWithoutAnyGroups.copy(isInGroup = Chunk.empty))
          updatedUser <- findById(testUserWithoutAnyGroups.id).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInGroup.isEmpty)
      }
    )
  ).provide(KnoraUserRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
