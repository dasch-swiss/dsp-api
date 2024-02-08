package org.knora.webapi.slice.admin.repo.service

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.UserRepo
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import zio.Chunk
import zio.RIO
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import org.knora.webapi.TestDataFactory.User.*

object UserRepoLiveSpec extends ZIOSpecDefault {

  private val adminGraphIri = AdminConstants.adminDataNamedGraph.value

  // Accessor functions of the UserRepo service which are tested
  def save(user: KnoraUser): ZIO[UserRepo, Throwable, KnoraUser]         = ZIO.serviceWithZIO[UserRepo](_.save(user))
  def findById(id: UserIri): ZIO[UserRepo, Throwable, Option[KnoraUser]] = ZIO.serviceWithZIO[UserRepo](_.findById(id))
  def findAll(): ZIO[UserRepo, Throwable, List[KnoraUser]]               = ZIO.serviceWithZIO[UserRepo](_.findAll())

  // Test data
  val unknownUserIri: UserIri = UserIri.unsafeFrom("http://rdfh.ch/users/doesNotExist")

  // Conversion functions from user to triples
  val userToTriples: KnoraUser => String = u => {
    s"""
       |<${u.id.value}> a knora-admin:User ;
       |knora-admin:username "${u.username.value}"^^xsd:string ;
       |knora-admin:email "${u.email.value}"^^xsd:string ;
       |knora-admin:password "${u.passwordHash.value}"^^xsd:string ;
       |knora-admin:givenName "${u.givenName.value}"^^xsd:string ;
       |knora-admin:familyName "${u.familyName.value}"^^xsd:string ;
       |knora-admin:status "${u.status.value}"^^xsd:boolean ;
       |knora-admin:preferredLanguage "${u.preferredLanguage.value}"^^xsd:string ;
       |knora-admin:isInSystemAdminGroup "${u.isInSystemAdminGroup.value}"^^xsd:boolean .
       |""".stripMargin
  }

  def usersToTrig(users: KnoraUser*): String =
    s"""
       |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
       |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |
       |GRAPH <$adminGraphIri>
       |{ ${users.map(userToTriples).mkString("\n")} }
       |""".stripMargin

  def storeUsersInTripleStore(users: KnoraUser*): RIO[TestTripleStore, Unit] =
    TriplestoreServiceInMemory.setDataSetFromTriG(usersToTrig(users*)).orDie

  val spec: Spec[Any, Any] = suite("UserRepoLiveSpec")(
    test("findById given an  existing user should return that user") {
      for {
        _    <- storeUsersInTripleStore(testUser, testUser2)
        user <- findById(testUser.id)
      } yield assertTrue(user.contains(testUser))
    },
    test("findAll given existing users should return all user") {
      for {
        _    <- storeUsersInTripleStore(testUser, testUser2)
        user <- findAll()
      } yield assertTrue(user.sortBy(_.id.value) == List(testUser, testUser2).sortBy(_.id.value))
    },
    test("findById given a non existing user should return None") {
      for {
        _    <- storeUsersInTripleStore(testUser, testUser2)
        user <- findById(unknownUserIri)
      } yield assertTrue(user.isEmpty)
    },
    test("should create and find user") {
      for {
        savedUser <- save(testUser)
        foundUser <- findById(savedUser.id).someOrFail(new Exception("User not found"))
      } yield assertTrue(savedUser == testUser, foundUser == testUser)
    }
  )
    .provide(UserRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
