package org.knora.webapi.slice.admin.repo.service

import zio.Chunk
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

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
import org.knora.webapi.store.triplestore.TestDatasetBuilder
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object UserRepoLiveSpec extends ZIOSpecDefault {

  private val adminGraphIri                                              = AdminConstants.adminDataNamedGraph.value
  def save(user: KnoraUser): ZIO[UserRepo, Throwable, KnoraUser]         = ZIO.serviceWithZIO[UserRepo](_.save(user))
  def findById(id: UserIri): ZIO[UserRepo, Throwable, Option[KnoraUser]] = ZIO.serviceWithZIO[UserRepo](_.findById(id))

  val unknownUserIri: UserIri = UserIri.unsafeFrom("http://rdfh.ch/users/doesNotExist")
  val knownUserIri: UserIri   = UserIri.unsafeFrom("http://rdfh.ch/users/exists")
  val testUser: KnoraUser = KnoraUser(
    knownUserIri,
    Username.unsafeFrom("testuser"),
    Email.unsafeFrom("jane@example.com"),
    FamilyName.unsafeFrom("Doe"),
    GivenName.unsafeFrom("Jane"),
    Password.unsafeFrom("hashedPassword"),
    LanguageCode.en,
    UserStatus.Active,
    projects = Chunk.empty,
    groups = Chunk.empty,
    isInSystemAdminGroup = SystemAdmin.from(false)
  )
  val userTriG =
    s"""
       |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
       |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |
       |GRAPH <$adminGraphIri>
       |{
       |  <${testUser.id.value}> a knora-admin:User ;
       |    knora-admin:username "${testUser.username.value}"^^xsd:string ;
       |    knora-admin:email "${testUser.email.value}"^^xsd:string ;
       |    knora-admin:password "${testUser.passwordHash.value}"^^xsd:string ;
       |    knora-admin:givenName "${testUser.givenName.value}"^^xsd:string ;
       |    knora-admin:familyName "${testUser.familyName.value}"^^xsd:string ;
       |    knora-admin:status "${testUser.status.value}"^^xsd:boolean ;
       |    knora-admin:preferredLanguage "${testUser.preferredLanguage.value}"^^xsd:string ;
       |    knora-admin:isInSystemAdminGroup "${testUser.isInSystemAdminGroup.value}"^^xsd:boolean .
       |}
       |""".stripMargin

  val spec: Spec[Any, Any] = suite("UserRepoLiveSpec")(
    test("findById given an  existing user should return that user") {
      for {
        _    <- TestDatasetBuilder.datasetFromTriG(userTriG).flatMap(TriplestoreServiceInMemory.setDataSet)
        user <- findById(testUser.id)
      } yield assertTrue(user.contains(testUser))
    },
    test("findById given a non existing user should return None") {
      for {
        user <- findById(unknownUserIri)
      } yield assertTrue(user.isEmpty)
    }
//    test("should create and find user") {
//      for {
//        savedUser <- save(testUser)
//        foundUser <- findById(savedUser.id).someOrFail(new Exception("User not found"))
//      } yield assertTrue(savedUser == testUser, foundUser == testUser)
//    }
  )
    .provide(UserRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
