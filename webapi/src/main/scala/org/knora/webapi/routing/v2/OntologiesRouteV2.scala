/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import java.time.Instant

import dsp.errors.BadRequestException
import dsp.valueobjects.Schema.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilV2.completeResponse
import org.knora.webapi.routing.RouteUtilV2.getStringQueryParam
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.api.OntologyV2RequestParser
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.security.Authenticator

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
final case class OntologiesRouteV2()(
  private implicit val runtime: Runtime[
    AppConfig & Authenticator & IriConverter & MessageRelay & OntologyV2RequestParser & RestCardinalityService &
      StringFormatter,
  ],
) {

  private val ontologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")
  private val requestParser                         = ZIO.serviceWithZIO[OntologyV2RequestParser]

  private val allLanguagesKey         = "allLanguages"
  private val lastModificationDateKey = "lastModificationDate"

  def makeRoute: Route =
    dereferenceOntologyIri() ~
      getOntologyMetadata ~
      updateOntologyMetadata() ~
      getOntologyMetadataForProjects ~
      getOntology ~
      createClass() ~
      updateClass() ~
      deleteClassComment() ~
      addCardinalities() ~
      canReplaceCardinalities ~
      replaceCardinalities() ~
      canDeleteCardinalitiesFromClass ~
      deleteCardinalitiesFromClass() ~
      changeGuiOrder() ~
      getClasses ~
      canDeleteClass ~
      deleteClass() ~
      deleteOntologyComment() ~
      createProperty() ~
      updatePropertyLabelsOrComments() ~
      deletePropertyComment() ~
      updatePropertyGuiElement() ~
      getProperties ~
      canDeleteProperty ~
      deleteProperty() ~
      createOntology() ~
      canDeleteOntology ~
      deleteOntology()

  private def dereferenceOntologyIri(): Route = path("ontology" / Segments) { (_: List[String]) =>
    get { requestContext =>
      // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
      // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
      // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
      // project-specific API ontology, prefix it with routeData.appConfig.externalOntologyIriHostAndPort to get the
      // ontology IRI.
      val ontologyIriTask = getOntologySmartIri(requestContext)

      val requestTask = for {
        ontologyIri    <- ontologyIriTask
        params          = requestContext.request.uri.query().toMap
        allLanguagesStr = params.get(allLanguagesKey)
        allLanguages    = ValuesValidator.optionStringToBoolean(allLanguagesStr, fallback = false)
        user           <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
      } yield OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, user)

      val targetSchemaTask = ontologyIriTask.flatMap(getTargetSchemaFromOntology)

      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
    }
  }

  private def getTargetSchemaFromOntology(iri: OntologyIri) =
    ZIO.fromOption(iri.smartIri.getOntologySchema).orElseFail(BadRequestException(s"Invalid ontology IRI: $iri"))

  private def getOntologySmartIri(
    requestContext: RequestContext,
  ): ZIO[AppConfig & IriConverter & StringFormatter, BadRequestException, OntologyIri] = {
    val urlPath = requestContext.request.uri.path.toString
    ZIO.serviceWithZIO[AppConfig] { appConfig =>
      val externalOntologyIriHostAndPort = appConfig.knoraApi.externalOntologyIriHostAndPort
      ZIO.serviceWithZIO[StringFormatter] { sf =>
        for {
          iri <- if (sf.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                   ZIO.succeed(OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath)
                 } else if (sf.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                   ZIO.succeed("http://" + externalOntologyIriHostAndPort + urlPath)
                 } else {
                   ZIO.fail(BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath"))
                 }
          ontologyIri <- RouteUtilZ.ontologyIri(iri)
        } yield ontologyIri
      }
    }
  }

  private def getOntologyMetadata: Route =
    path(ontologiesBasePath / "metadata") {
      get { requestContext =>
        val requestTask = RouteUtilV2
          .getProjectIri(requestContext)
          .map(_.toSet)
          .map(OntologyMetadataGetByProjectRequestV2(_))
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def updateOntologyMetadata(): Route =
    path(ontologiesBasePath / "metadata") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          val requestTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <-
              requestParser(_.changeOntologyMetadataRequestV2(jsonRequest, apiRequestId, requestingUser))
                .mapError(BadRequestException.apply)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }

  private def getOntologyMetadataForProjects: Route =
    path(ontologiesBasePath / "metadata" / Segments) { (projectIris: List[IRI]) =>
      get { requestContext =>
        val requestTask = ZIO
          .foreach(projectIris)(iri => RouteUtilZ.toSmartIri(iri, s"Invalid project IRI: $iri"))
          .map(_.toSet)
          .map(OntologyMetadataGetByProjectRequestV2(_))
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getOntology: Route =
    path(ontologiesBasePath / "allentities" / Segment) { (externalOntologyIriStr: IRI) =>
      get { requestContext =>
        val ontologyIriTask = RouteUtilZ.externalOntologyIri(externalOntologyIriStr)
        val requestMessageTask = for {
          ontologyIri    <- ontologyIriTask
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield OntologyEntitiesGetRequestV2(ontologyIri, getLanguages(requestContext), requestingUser)
        val targetSchema = ontologyIriTask.flatMap(getTargetSchemaFromOntology)
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext, targetSchema)
      }
    }

  private def getLanguages(requestContext: RequestContext) = {
    val params: Map[IRI, IRI] = requestContext.request.uri.query().toMap
    ValuesValidator.optionStringToBoolean(params.get(allLanguagesKey), fallback = false)
  }

  private def createClass(): Route = path(ontologiesBasePath / "classes") {
    post {
      // Create a new class.
      entity(as[String]) { jsonRequest => requestContext =>
        val requestMessageTask = for {
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          apiRequestId   <- RouteUtilZ.randomUuid()
          requestMessage <- requestParser(_.createClassRequestV2(jsonRequest, apiRequestId, requestingUser))
                              .mapError(BadRequestException.apply)
        } yield requestMessage
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }
  }

  private def updateClass(): Route =
    path(ontologiesBasePath / "classes") {
      put {
        // Change the labels or comments of a class.
        entity(as[String]) { jsonRequest => requestContext =>
          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <-
              requestParser(_.changeClassLabelsOrCommentsRequestV2(jsonRequest, apiRequestId, requestingUser))
                .mapError(BadRequestException.apply)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }

  // delete the comment of a class definition
  private def deleteClassComment(): Route =
    path(ontologiesBasePath / "classes" / "comment" / Segment) { (classIriStr: IRI) =>
      delete { requestContext =>
        val requestMessageFuture = for {
          classIri <- RouteUtilZ
                        .toSmartIri(classIriStr, s"Invalid class IRI for request: $classIriStr")
                        .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(
                          BadRequestException(s"Invalid class IRI for request: $classIriStr"),
                        )
          lastModificationDate <- getLastModificationDate(requestContext)
          requestingUser       <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          apiRequestId         <- RouteUtilZ.randomUuid()
        } yield DeleteClassCommentRequestV2(classIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestMessageFuture, requestContext)
      }
    }

  private def addCardinalities(): Route =
    path(ontologiesBasePath / "cardinalities") {
      post {
        // Add cardinalities to a class.
        entity(as[String]) { jsonRequest => requestContext =>
          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <-
              requestParser(_.addCardinalitiesToClassRequestV2(jsonRequest, apiRequestId, requestingUser))
                .mapError(BadRequestException.apply)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }

  private def canReplaceCardinalities: Route =
    // GET basePath/{iriEncode} or
    // GET basePath/{iriEncode}?propertyIri={iriEncode}&newCardinality=[0-1|1|1-n|0-n]
    path(ontologiesBasePath / "canreplacecardinalities" / Segment) { (classIri: IRI) =>
      get { requestContext =>
        val response = for {
          user           <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          property       <- ZIO.attempt(getStringQueryParam(requestContext, RestCardinalityService.propertyIriKey))
          newCardinality <- ZIO.attempt(getStringQueryParam(requestContext, RestCardinalityService.newCardinalityKey))
          canChange <-
            ZIO.serviceWithZIO[RestCardinalityService](_.canChangeCardinality(classIri, user, property, newCardinality))
        } yield canChange
        completeResponse(response, requestContext)
      }
    }

  private def replaceCardinalities(): Route =
    path(ontologiesBasePath / "cardinalities") {
      put {
        entity(as[String]) { reqBody => requestContext =>
          val messageTask = for {
            user         <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId <- RouteUtilZ.randomUuid()
            msg <- requestParser(_.replaceClassCardinalitiesRequestV2(reqBody, apiRequestId, user))
                     .mapError(BadRequestException.apply)
          } yield msg
          RouteUtilV2.runRdfRouteZ(messageTask, requestContext)
        }
      }
    }

  private def canDeleteCardinalitiesFromClass: Route =
    path(ontologiesBasePath / "candeletecardinalities") {
      post {
        entity(as[String]) { jsonRequest => requestContext =>
          val messageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            msg <- requestParser(_.canDeleteCardinalitiesFromClassRequestV2(jsonRequest, apiRequestId, requestingUser))
                     .mapError(BadRequestException.apply)
          } yield msg
          RouteUtilV2.runRdfRouteZ(messageTask, requestContext)
        }
      }
    }

  // delete a single cardinality from the specified class if the property is
  // not used in resources.
  private def deleteCardinalitiesFromClass(): Route =
    path(ontologiesBasePath / "cardinalities") {
      patch {
        entity(as[String]) { jsonRequest => requestContext =>
          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            msg <- requestParser(_.deleteCardinalitiesFromClassRequestV2(jsonRequest, apiRequestId, requestingUser))
                     .mapError(BadRequestException.apply)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }

  private def changeGuiOrder(): Route =
    path(ontologiesBasePath / "guiorder") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            msg <- requestParser(_.changeGuiOrderRequestV2(jsonRequest, apiRequestId, requestingUser))
                     .mapError(BadRequestException.apply)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }

  private def getClasses: Route =
    path(ontologiesBasePath / "classes" / Segments) { (externalResourceClassIris: List[IRI]) =>
      get { requestContext =>
        val classSmartIrisTask: ZIO[IriConverter & StringFormatter, BadRequestException, Set[SmartIri]] =
          ZIO
            .foreach(externalResourceClassIris)(iri =>
              RouteUtilZ
                .toSmartIri(iri, s"Invalid class IRI: $iri")
                .flatMap(RouteUtilZ.ensureExternalOntologyName)
                .flatMap(RouteUtilZ.ensureIsNotKnoraOntologyIri),
            )
            .map(_.toSet)

        val targetSchemaTask: ZIO[IriConverter & StringFormatter, BadRequestException, OntologySchema] =
          classSmartIrisTask
            .flatMap(iriSet =>
              ZIO.foreach(iriSet)(iri =>
                ZIO
                  .fromOption(iri.getOntologySchema)
                  .orElseFail(BadRequestException(s"Class IRI does not have an ontology schema: $iri")),
              ),
            )
            .filterOrFail(_.size == 1)(BadRequestException(s"Only one ontology may be queried per request"))
            .map(_.head)

        val requestMessageTask = for {
          classSmartIris <- classSmartIrisTask
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield ClassesGetRequestV2(classSmartIris, getLanguages(requestContext), requestingUser)

        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext, targetSchemaTask)
      }
    }

  private def canDeleteClass: Route =
    path(ontologiesBasePath / "candeleteclass" / Segment) { (classIriStr: IRI) =>
      get { requestContext =>
        val requestTask = for {
          classSmartIri <-
            RouteUtilZ
              .toSmartIri(classIriStr, s"Invalid class IRI: $classIriStr")
              .flatMap(RouteUtilZ.ensureExternalOntologyName)
              .filterOrFail(_.isKnoraApiV2EntityIri)(BadRequestException(s"Invalid class IRI: $classIriStr"))
              .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(
                BadRequestException(s"Invalid class IRI for request: $classIriStr"),
              )
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield CanDeleteClassRequestV2(classSmartIri, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def deleteClass(): Route =
    path(ontologiesBasePath / "classes" / Segments) { (externalResourceClassIris: List[IRI]) =>
      delete { requestContext =>
        val requestTask = for {
          classIri <- ZIO
                        .succeed(externalResourceClassIris)
                        .filterOrFail(_.size == 1)(BadRequestException(s"Only one class can be deleted at a time"))
                        .map(_.head)
                        .flatMap(iri => RouteUtilZ.toSmartIri(iri, s"Invalid class IRI: $iri"))
                        .flatMap(RouteUtilZ.ensureExternalOntologyName)
                        .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield DeleteClassRequestV2(classIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getLastModificationDate(ctx: RequestContext): IO[BadRequestException, Instant] =
    ZIO
      .fromOption(ctx.request.uri.query().toMap.get(lastModificationDateKey))
      .mapBoth(
        _ => BadRequestException(s"Missing parameter: $lastModificationDateKey"),
        ValuesValidator.xsdDateTimeStampToInstant,
      )
      .flatMap(it => ZIO.fromOption(it).orElseFail(BadRequestException(s"Invalid timestamp: $it")))

  private def deleteOntologyComment(): Route =
    path(ontologiesBasePath / "comment" / Segment) { (ontologyIriStr: IRI) =>
      delete { requestContext =>
        val requestMessageTask = for {
          ontologyIri          <- RouteUtilZ.externalApiV2ComplexOntologyIri(ontologyIriStr)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield DeleteOntologyCommentRequestV2(ontologyIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }

  private def createProperty(): Route =
    path(ontologiesBasePath / "properties") {
      post {
        entity(as[String]) { jsonRequest => requestContext =>
          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <- requestParser(_.createPropertyRequestV2(jsonRequest, apiRequestId, requestingUser))
                                .mapError(BadRequestException.apply)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }

  private def updatePropertyLabelsOrComments(): Route =
    path(ontologiesBasePath / "properties") {
      put {
        // Change the labels or comments of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <-
              requestParser(_.changePropertyLabelsOrCommentsRequestV2(jsonRequest, apiRequestId, requestingUser))
                .mapError(BadRequestException.apply)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }

  // delete the comment of a property definition
  private def deletePropertyComment(): Route =
    path(ontologiesBasePath / "properties" / "comment" / Segment) { (propertyIriStr: IRI) =>
      delete { requestContext =>
        val requestTask = for {
          propertyIri <- RouteUtilZ
                           .toSmartIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
                           .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield DeletePropertyCommentRequestV2(propertyIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def updatePropertyGuiElement(): Route =
    path(ontologiesBasePath / "properties" / "guielement") {
      put {
        // Change the salsah-gui:guiElement and/or salsah-gui:guiAttribute of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          val requestTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            apiRequestId   <- RouteUtilZ.randomUuid()
            msg <- requestParser(_.changePropertyGuiElementRequest(jsonRequest, apiRequestId, requestingUser))
                     .mapError(BadRequestException.apply)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }

  private def getProperties: Route =
    path(ontologiesBasePath / "properties" / Segments) { (externalPropertyIris: List[IRI]) =>
      get { requestContext =>
        val propertyIrisTask = for {
          propertyIris <- ZIO.foreach(externalPropertyIris) { (propertyIriStr: IRI) =>
                            RouteUtilZ
                              .toSmartIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
                              .flatMap(RouteUtilZ.ensureIsNotKnoraOntologyIri)
                              .flatMap(RouteUtilZ.ensureExternalOntologyName)
                          }
        } yield propertyIris.toSet

        val targetSchemaTask = for {
          schemas <- propertyIrisTask.map(_.flatMap(_.getOntologySchema))
          targetSchema <-
            ZIO
              .succeed(schemas)
              .filterOrFail(_.size == 1)(BadRequestException(s"Only one ontology may be queried per request"))
              .map(_.head)
        } yield targetSchema

        val requestTask = for {
          propertyIris   <- propertyIrisTask
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield PropertiesGetRequestV2(propertyIris, getLanguages(requestContext), requestingUser)

        RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
      }
    }

  private def canDeleteProperty: Route =
    path(ontologiesBasePath / "candeleteproperty" / Segment) { (propertyIriStr: IRI) =>
      get { requestContext =>
        val requestMessageTask = for {
          propertyIri <- RouteUtilZ
                           .toSmartIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
                           .flatMap(RouteUtilZ.ensureExternalOntologyName)
                           .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield CanDeletePropertyRequestV2(propertyIri, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }

  private def deleteProperty(): Route =
    path(ontologiesBasePath / "properties" / Segments) { (externalPropertyIris: List[IRI]) =>
      delete { requestContext =>
        val requestMessageTask = for {
          propertyIri <-
            ZIO
              .succeed(externalPropertyIris)
              .filterOrFail(_.size == 1)(BadRequestException(s"Only one property can be deleted at a time"))
              .map(_.head)
              .flatMap(iri => RouteUtilZ.toSmartIri(iri, s"Invalid property IRI: $iri"))
              .flatMap(RouteUtilZ.ensureExternalOntologyName)
              .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield DeletePropertyRequestV2(
          propertyIri,
          lastModificationDate,
          apiRequestId,
          requestingUser,
        )
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }

  private def createOntology(): Route = path(ontologiesBasePath) {
    // Create a new, empty ontology.
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        val t = for {
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          apiRequestId   <- RouteUtilZ.randomUuid()
          requestMessage <- requestParser(_.createOntologyRequestV2(jsonRequest, apiRequestId, requestingUser))
                              .mapError(BadRequestException.apply)
        } yield requestMessage
        RouteUtilV2.runRdfRouteZ(t, requestContext)
      }
    }
  }

  private def canDeleteOntology: Route =
    path(ontologiesBasePath / "candeleteontology" / Segment) { (ontologyIriStr: IRI) =>
      get { requestContext =>
        val requestTask = for {
          ontologyIri    <- RouteUtilZ.externalApiV2ComplexOntologyIri(ontologyIriStr)
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield CanDeleteOntologyRequestV2(ontologyIri, requestingUser)

        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def deleteOntology(): Route = path(ontologiesBasePath / Segment) { ontologyIriStr =>
    delete { requestContext =>
      val requestMessageTask = for {
        ontologyIri          <- RouteUtilZ.externalApiV2ComplexOntologyIri(ontologyIriStr)
        lastModificationDate <- getLastModificationDate(requestContext)
        apiRequestId         <- RouteUtilZ.randomUuid()
        requestingUser       <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
      } yield DeleteOntologyRequestV2(ontologyIri, lastModificationDate, apiRequestId, requestingUser)
      RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
    }
  }
}
