/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import zio.*
import zio.test.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.api.v3.*
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.domain.OntologyCacheDataBuilder
import org.knora.webapi.slice.ontology.domain.ReadClassInfoV2Builder
import org.knora.webapi.slice.ontology.domain.ReadOntologyV2Builder
import org.knora.webapi.slice.ontology.domain.ReadPropertyInfoV2Builder
import org.knora.webapi.slice.ontology.domain.SmartIriConversion.TestSmartIriFromString
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object OntologyMappingRestServiceSpec extends ZIOSpecDefault {

  // Test IRIs (internal schema)
  private val anythingOntologyIri = IriTestConstants.Anything.Ontology.value
  private val anythingClassIri    = IriTestConstants.Anything.Class.Thing.value
  private val anythingPropertyIri = IriTestConstants.Anything.Property.hasOtherThing.value
  private val testProjectIri      = IriTestConstants.Project.TestProject

  // A user that is system admin — passes any project auth check
  private val adminUser = SystemUser

  // Cache data: ontology without a project (system/built-in ontology)
  private val systemOntologyCacheData = OntologyCacheDataBuilder.builder
    .addOntology(ReadOntologyV2Builder.builder(anythingOntologyIri.smartIri))
    .build

  // Cache data: ontology assigned to testProjectIri + a class and a property in that ontology
  private val projectOntologyCacheData = OntologyCacheDataBuilder.builder
    .addOntology(
      ReadOntologyV2Builder
        .builder(anythingOntologyIri.smartIri)
        .addClassInfo(ReadClassInfoV2Builder.builder(anythingClassIri.smartIri))
        .addPropertyInfo(ReadPropertyInfoV2Builder.builder(anythingPropertyIri.smartIri))
        .assignToProject(testProjectIri),
    )
    .build

  // Helper: run putClassMapping and return the Exit
  private def putClass(
    ontologyIri: String,
    classIri: String,
    mappings: List[String],
  ) = ZIO
    .serviceWithZIO[OntologyMappingRestService](
      _.putClassMapping(adminUser)(ontologyIri, classIri, AddClassMappingsRequest(mappings)),
    )
    .exit

  // Helper: run deleteClassMapping and return the Exit
  private def deleteClass(
    ontologyIri: String,
    classIri: String,
    mappingOpt: Option[String],
  ) = ZIO
    .serviceWithZIO[OntologyMappingRestService](
      _.deleteClassMapping(adminUser)(ontologyIri, classIri, mappingOpt),
    )
    .exit

  // Helper: run putPropertyMapping and return the Exit
  private def putProperty(
    ontologyIri: String,
    propertyIri: String,
    mappings: List[String],
  ) = ZIO
    .serviceWithZIO[OntologyMappingRestService](
      _.putPropertyMapping(adminUser)(ontologyIri, propertyIri, AddPropertyMappingsRequest(mappings)),
    )
    .exit

  // Helper: run deletePropertyMapping and return the Exit
  private def deleteProperty(
    ontologyIri: String,
    propertyIri: String,
    mappingOpt: Option[String],
  ) = ZIO
    .serviceWithZIO[OntologyMappingRestService](
      _.deletePropertyMapping(adminUser)(ontologyIri, propertyIri, mappingOpt),
    )
    .exit

  override def spec: Spec[TestEnvironment, Any] =
    suite("OntologyMappingRestService")(
      suite("input validation — empty mappings list")(
        test("putClassMapping rejects empty list") {
          for {
            result <- putClass(anythingOntologyIri, anythingClassIri, Nil)
          } yield assertTrue(result == Exit.fail(BadRequest("'mappings' must contain at least one IRI.")))
        },
        test("putPropertyMapping rejects empty list") {
          for {
            result <- putProperty(anythingOntologyIri, anythingPropertyIri, Nil)
          } yield assertTrue(result == Exit.fail(BadRequest("'mappings' must contain at least one IRI.")))
        },
      ),
      suite("input validation — mappings list exceeds limit")(
        test("putClassMapping rejects more than 100 IRIs") {
          val tooMany = (1 to 101).map(i => s"https://schema.org/Thing$i").toList
          for {
            result <- putClass(anythingOntologyIri, anythingClassIri, tooMany)
          } yield assertTrue(result == Exit.fail(BadRequest("'mappings' must contain at most 100 IRIs (got 101).")))
        },
        test("putPropertyMapping rejects more than 100 IRIs") {
          val tooMany = (1 to 101).map(i => s"https://schema.org/Thing$i").toList
          for {
            result <- putProperty(anythingOntologyIri, anythingPropertyIri, tooMany)
          } yield assertTrue(result == Exit.fail(BadRequest("'mappings' must contain at most 100 IRIs (got 101).")))
        },
      ),
      suite("input validation — missing DELETE query parameter")(
        test("deleteClassMapping with absent mapping param returns BadRequest") {
          for {
            result <- deleteClass(anythingOntologyIri, anythingClassIri, None)
          } yield assertTrue(result == Exit.fail(BadRequest("Missing required query parameter 'mapping'.")))
        },
        test("deletePropertyMapping with absent mapping param returns BadRequest") {
          for {
            result <- deleteProperty(anythingOntologyIri, anythingPropertyIri, None)
          } yield assertTrue(result == Exit.fail(BadRequest("Missing required query parameter 'mapping'.")))
        },
      ),
      suite("authorization — system ontology cannot be modified")(
        test("putClassMapping returns Forbidden for an ontology with no projectIri") {
          for {
            _ <-
              OntologyCacheFake.set(systemOntologyCacheData)
            result <- putClass(anythingOntologyIri, anythingClassIri, List("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify a system ontology.")))
        },
        test("putPropertyMapping returns Forbidden for an ontology with no projectIri") {
          for {
            _ <-
              OntologyCacheFake.set(systemOntologyCacheData)
            result <- putProperty(anythingOntologyIri, anythingPropertyIri, List("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify a system ontology.")))
        },
      ),
      suite("authorization — project not found")(
        test("putClassMapping returns Forbidden when the ontology's project cannot be found") {
          for {
            _ <- OntologyCacheFake.set(projectOntologyCacheData)
            // Project repo is empty — findById returns None
            result <- putClass(anythingOntologyIri, anythingClassIri, List("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify this ontology.")))
        },
        test("putPropertyMapping returns Forbidden when the ontology's project cannot be found") {
          for {
            _ <- OntologyCacheFake.set(projectOntologyCacheData)
            // Project repo is empty — findById returns None
            result <- putProperty(anythingOntologyIri, anythingPropertyIri, List("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify this ontology.")))
        },
      ),
      suite("authorization — system ontology cannot be modified (DELETE)")(
        test("deleteClassMapping returns Forbidden for an ontology with no projectIri") {
          for {
            _      <- OntologyCacheFake.set(systemOntologyCacheData)
            result <- deleteClass(anythingOntologyIri, anythingClassIri, Some("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify a system ontology.")))
        },
        test("deletePropertyMapping returns Forbidden for an ontology with no projectIri") {
          for {
            _      <- OntologyCacheFake.set(systemOntologyCacheData)
            result <- deleteProperty(anythingOntologyIri, anythingPropertyIri, Some("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify a system ontology.")))
        },
      ),
      suite("authorization — project not found (DELETE)")(
        test("deleteClassMapping returns Forbidden when the ontology's project cannot be found") {
          for {
            _      <- OntologyCacheFake.set(projectOntologyCacheData)
            result <- deleteClass(anythingOntologyIri, anythingClassIri, Some("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify this ontology.")))
        },
        test("deletePropertyMapping returns Forbidden when the ontology's project cannot be found") {
          for {
            _      <- OntologyCacheFake.set(projectOntologyCacheData)
            result <- deleteProperty(anythingOntologyIri, anythingPropertyIri, Some("https://schema.org/Thing"))
          } yield assertTrue(result == Exit.fail(Forbidden("Cannot modify this ontology.")))
        },
      ),
      suite("IRI validation — Knora IRI rejected by DELETE")(
        test("deleteClassMapping rejects a Knora entity IRI as mapping") {
          val knoraIri = "http://www.knora.org/ontology/knora-base#TextValue"
          for {
            _ <- OntologyCacheFake.set(projectOntologyCacheData)
            _ <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](
                   _.save(org.knora.webapi.TestDataFactory.someProject.copy(id = testProjectIri)),
                 )
            result <- deleteClass(anythingOntologyIri, anythingClassIri, Some(knoraIri))
          } yield assertTrue(
            result match {
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(BadRequest(msg, _)) => msg.contains("Mapping IRI must be an external IRI")
                  case _                        => false
                }
              case _ => false
            },
          )
        },
        test("deletePropertyMapping rejects a Knora entity IRI as mapping") {
          val knoraIri = "http://www.knora.org/ontology/knora-base#TextValue"
          for {
            _ <- OntologyCacheFake.set(projectOntologyCacheData)
            _ <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](
                   _.save(org.knora.webapi.TestDataFactory.someProject.copy(id = testProjectIri)),
                 )
            result <- deleteProperty(anythingOntologyIri, anythingPropertyIri, Some(knoraIri))
          } yield assertTrue(
            result match {
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(BadRequest(msg, _)) => msg.contains("Mapping IRI must be an external IRI")
                  case _                        => false
                }
              case _ => false
            },
          )
        },
      ),
      suite("IRI validation — Knora IRI must not be used as a mapping target")(
        test("putClassMapping rejects a Knora entity IRI as mapping") {
          val knoraIri = "http://www.knora.org/ontology/knora-base#TextValue"
          for {
            _ <- OntologyCacheFake.set(projectOntologyCacheData)
            // Populate the project repo so auth passes
            _ <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](
                   _.save(
                     org.knora.webapi.TestDataFactory.someProject.copy(id = testProjectIri),
                   ),
                 )
            result <- putClass(anythingOntologyIri, anythingClassIri, List(knoraIri))
          } yield assertTrue(
            result match {
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(BadRequest(msg, _)) => msg.contains("Mapping IRI must be an external IRI")
                  case _                        => false
                }
              case _ => false
            },
          )
        },
        test("putPropertyMapping rejects a Knora entity IRI as mapping") {
          val knoraIri = "http://www.knora.org/ontology/knora-base#TextValue"
          for {
            _ <- OntologyCacheFake.set(projectOntologyCacheData)
            _ <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](
                   _.save(
                     org.knora.webapi.TestDataFactory.someProject.copy(id = testProjectIri),
                   ),
                 )
            result <- putProperty(anythingOntologyIri, anythingPropertyIri, List(knoraIri))
          } yield assertTrue(
            result match {
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(BadRequest(msg, _)) => msg.contains("Mapping IRI must be an external IRI")
                  case _                        => false
                }
              case _ => false
            },
          )
        },
      ),
    ).provide(
      AppConfig.layer,
      AuthorizationRestService.layer,
      CacheManager.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraGroupRepoInMemory.layer,
      KnoraGroupService.layer,
      KnoraProjectRepoInMemory.layer,
      KnoraProjectService.layer,
      KnoraUserRepoLive.layer,
      KnoraUserService.layer,
      LicenseRepo.layer,
      OntologyCacheFake.emptyCache,
      OntologyRepoLive.layer,
      PasswordService.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.emptyLayer,
      V3Authorizer.layer,
      OntologyMappingRestService.layer,
    )
}
