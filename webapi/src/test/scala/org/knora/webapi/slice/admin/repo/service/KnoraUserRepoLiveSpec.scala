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
import zio.test.Gen
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import dsp.valueobjects.LanguageCode
import org.knora.webapi.TestDataFactory
import org.knora.webapi.TestDataFactory.User._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service._
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object KnoraUserRepoLiveSpec extends ZIOSpecDefault {

  private val KnoraUserRepo = ZIO.serviceWithZIO[KnoraUserRepo]

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
      triples.andHas(Vocabulary.KnoraAdmin.isInProjectAdminGroup, Rdf.iri(admGrp.value)),
    )
    Update(
      Queries
        .INSERT_DATA(triples)
        .into(Rdf.iri(adminDataNamedGraph.value))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS)),
    )
  }

  def storeUsersInTripleStore(users: KnoraUser*): RIO[TriplestoreService, Unit] =
    ZIO.foreach(users)(user => ZIO.serviceWithZIO[TriplestoreService](_.query(createUserQuery(user)))).unit

  val builtInUsers: Chunk[KnoraUser] = Chunk(
    KnoraUser(
      UserIri.unsafeFrom("http://www.knora.org/ontology/knora-admin#SystemUser"),
      Username.unsafeFrom("System".toLowerCase),
      Email.unsafeFrom(s"${"System".toLowerCase}@localhost"),
      FamilyName.unsafeFrom("System"),
      GivenName.unsafeFrom("Knora"),
      PasswordHash.unsafeFrom("youcannotloginwiththispassword"),
      LanguageCode.en,
      UserStatus.Active,
      Chunk.empty[ProjectIri],
      Chunk.empty[GroupIri],
      SystemAdmin.IsNotSystemAdmin,
      Chunk.empty[ProjectIri],
    ),
    KnoraUser(
      UserIri.unsafeFrom("http://www.knora.org/ontology/knora-admin#AnonymousUser"),
      Username.unsafeFrom("Anonymous".toLowerCase),
      Email.unsafeFrom(s"${"Anonymous".toLowerCase}@localhost"),
      FamilyName.unsafeFrom("Anonymous"),
      GivenName.unsafeFrom("Knora"),
      PasswordHash.unsafeFrom("youcannotloginwiththispassword"),
      LanguageCode.en,
      UserStatus.Active,
      Chunk.empty[ProjectIri],
      Chunk.empty[GroupIri],
      SystemAdmin.IsNotSystemAdmin,
      Chunk.empty[ProjectIri],
    ),
  )

  val spec: Spec[Any, Any] = suite("UserRepoLiveSpec")(
    suite("findById")(
      test("findById given an existing user should return that user") {
        for {
          _    <- storeUsersInTripleStore(testUser)
          user <- KnoraUserRepo(_.findById(testUser.id))
        } yield assertTrue(user.contains(testUser))
      },
      test("findById given a non existing user should return None") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- KnoraUserRepo(_.findById(UserIri.unsafeFrom("http://rdfh.ch/users/doesNotExist")))
        } yield assertTrue(user.isEmpty)
      },
      test("find all built in users") {
        check(Gen.fromIterable(builtInUsers)) { user =>
          for {
            found <- KnoraUserRepo(_.findById(user.id))
          } yield assertTrue(found.contains(user))
        }
      },
    ),
    suite("findByEmail")(
      test("findByEmail given an existing user should return that user") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- KnoraUserRepo(_.findByEmail(testUser.email))
        } yield assertTrue(user.contains(testUser))
      },
      test("findByEmail given a non existing user should return None") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- KnoraUserRepo(_.findByEmail(Email.unsafeFrom("doesNotExist@example.com")))
        } yield assertTrue(user.isEmpty)
      },
      test("find a built in users") {
        check(Gen.fromIterable(builtInUsers)) { user =>
          for {
            found <- KnoraUserRepo(_.findByEmail(user.email))
          } yield assertTrue(found.contains(user))
        }
      },
    ),
    suite("findByUsername")(
      test("findByUsername given an existing user should return that user") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- KnoraUserRepo(_.findByUsername(testUserWithoutAnyGroups.username))
        } yield assertTrue(user.contains(testUserWithoutAnyGroups))
      },
      test("findByUsername given a non existing user should return None") {
        for {
          _    <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          user <- KnoraUserRepo(_.findByUsername(Username.unsafeFrom("doesNotExistUsername")))
        } yield assertTrue(user.isEmpty)
      },
      test("find all built in user") {
        check(Gen.fromIterable(builtInUsers)) { user =>
          for {
            found <- KnoraUserRepo(_.findByUsername(user.username))
          } yield assertTrue(found.contains(user))
        }
      },
    ),
    suite("findAll")(
      test("given existing users should return all user") {
        for {
          _     <- storeUsersInTripleStore(testUser, testUserWithoutAnyGroups)
          users <- KnoraUserRepo(_.findAll())
        } yield assertTrue(
          users.sortBy(_.id.value) ==
            (builtInUsers ++ Chunk(testUser, testUserWithoutAnyGroups)).toList.sortBy(_.id.value),
        )
      },
      test("given no users present should return only built in users") {
        for {
          users <- KnoraUserRepo(_.findAll())
        } yield assertTrue(users.sortBy(_.id.value) == builtInUsers.toList.sortBy(_.id.value))
      },
    ),
    suite("save")(
      test("should create and find user") {
        for {
          savedUser <- KnoraUserRepo(_.save(testUser))
          foundUser <- KnoraUserRepo(_.findById(savedUser.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(savedUser == testUser, foundUser == testUser)
      },
      test("should update an existing user's username find user with new username") {
        for {
          _ <- KnoraUserRepo(_.save(testUser)) // create the user
          updated <-
            KnoraUserRepo(_.save(testUser.copy(username = Username.unsafeFrom("newUsername")))) // update the username
          updatedUser <- KnoraUserRepo(_.findByUsername(updated.username)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.username == Username.unsafeFrom("newUsername"))
      },
      test("should update an existing user isInProject ") {
        for {
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups)) // create the user
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInProject = Chunk(TestDataFactory.someProject.id))))
          updatedUser <-
            KnoraUserRepo(_.findById(testUserWithoutAnyGroups.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProject == Chunk(TestDataFactory.someProject.id))
      },
      test("should update an existing user isInProject and remove them") {
        for {
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups)) // create the user
          _ <- TestTripleStore.printDataset("After creating user: ")
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInProject = Chunk(TestDataFactory.someProject.id))))
          _ <- TestTripleStore.printDataset("After adding isInProject: ")
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInProject = Chunk.empty)))
          _ <- TestTripleStore.printDataset("After removing isInProject: ")
          updatedUser <-
            KnoraUserRepo(_.findById(testUserWithoutAnyGroups.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProject.isEmpty)
      },
      test("should update an existing user isInProjectAdminGroup") {
        for {
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups)) // create the user
          _ <- KnoraUserRepo(
                 _.save(testUserWithoutAnyGroups.copy(isInProjectAdminGroup = Chunk(TestDataFactory.someProject.id))),
               )
          updatedUser <-
            KnoraUserRepo(_.findById(testUserWithoutAnyGroups.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProjectAdminGroup == Chunk(TestDataFactory.someProject.id))
      },
      test("should update an existing user isInProjectAdminGroup and remove them") {
        for {
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups)) // create the user
          _ <- KnoraUserRepo(
                 _.save(testUserWithoutAnyGroups.copy(isInProjectAdminGroup = Chunk(TestDataFactory.someProject.id))),
               )
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInProjectAdminGroup = Chunk.empty)))
          updatedUser <-
            KnoraUserRepo(_.findById(testUserWithoutAnyGroups.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInProjectAdminGroup.isEmpty)
      },
      test("should update an existing user isInGroup ") {
        val groupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234")
        for {
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups)) // create the user
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInGroup = Chunk(groupIri))))
          updatedUser <-
            KnoraUserRepo(_.findById(testUserWithoutAnyGroups.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInGroup == Chunk(groupIri))
      },
      test("should update an existing user isInGroup and remove them") {
        val groupIri = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234")
        for {
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups)) // create the user
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInGroup = Chunk(groupIri))))
          _ <- KnoraUserRepo(_.save(testUserWithoutAnyGroups.copy(isInGroup = Chunk.empty)))
          updatedUser <-
            KnoraUserRepo(_.findById(testUserWithoutAnyGroups.id)).someOrFail(new Exception("User not found"))
        } yield assertTrue(updatedUser.isInGroup.isEmpty)
      },
      test("die for built in users") {
        check(Gen.fromIterable(builtInUsers)) { user =>
          for {
            exit <- KnoraUserRepo(_.save(user)).exit
          } yield assertTrue(exit.isFailure)
        }
      },
    ),
    suite("find members")(
      test("find project members given a project should return all members") {
        for {
          _     <- storeUsersInTripleStore(testUser, testUser3, testUserWithoutAnyGroups)
          users <- KnoraUserRepo(_.findByProjectMembership(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002")))
        } yield assertTrue(users.sortBy(_.id.value) == Chunk(testUser, testUser3).sortBy(_.id.value))
      },
      test("not find project members given no members") {
        for {
          _     <- storeUsersInTripleStore(testUserWithoutAnyGroups)
          users <- KnoraUserRepo(_.findByProjectMembership(testUser.isInProject.head))
        } yield assertTrue(users == Chunk.empty)
      },
      test("find project admin members given a project should return all members") {
        for {
          _     <- storeUsersInTripleStore(testUser, testUser3, testUserWithoutAnyGroups)
          users <- KnoraUserRepo(_.findByProjectAdminMembership(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0003")))
        } yield assertTrue(users == Chunk(testUser3))
      },
      test("not find project admin members given no members") {
        for {
          _     <- storeUsersInTripleStore(testUserWithoutAnyGroups)
          users <- KnoraUserRepo(_.findByProjectAdminMembership(testUser.isInProjectAdminGroup.head))
        } yield assertTrue(users == Chunk.empty)
      },
    ),
  ).provide(
    KnoraUserRepoLive.layer,
    TriplestoreServiceInMemory.emptyLayer,
    StringFormatter.test,
    CacheService.layer,
  )
}
