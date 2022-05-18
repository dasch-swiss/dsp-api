/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
package routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route

import java.util.UUID
import scala.concurrent.Future

import exceptions.BadRequestException
import messages.IriConversions._
import messages.util.rdf.{JsonLDDocument, JsonLDUtil}
import messages.v2.responder.ontologymessages._
import messages.{OntologyConstants, SmartIri}
import routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}

object OntologiesRouteV2 {
  val OntologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")
}

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
class OntologiesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  import OntologiesRouteV2._

  private val ALL_LANGUAGES          = "allLanguages"
  private val LAST_MODIFICATION_DATE = "lastModificationDate"

  /**
   * Returns the route.
   */
  override def makeRoute(): Route =
    dereferenceOntologyIri() ~
      getOntologyMetadata() ~
      updateOntologyMetadata() ~
      getOntologyMetadataForProjects() ~
      getOntology() ~
      createClass() ~
      updateClass() ~
      deleteClassComment() ~
      addCardinalities() ~
      canReplaceCardinalities() ~
      replaceCardinalities() ~
      canDeleteCardinalitiesFromClass() ~
      deleteCardinalitiesFromClass() ~
      changeGuiOrder() ~
      getClasses() ~
      canDeleteClass() ~
      deleteClass() ~
      deleteOntologyComment() ~
      createProperty() ~
      updatePropertyLabelsOrComments() ~
      deletePropertyComment() ~
      updatePropertyGuiElement() ~
      getProperties() ~
      canDeleteProperty() ~
      deleteProperty() ~
      createOntology() ~
      canDeleteOntology() ~
      deleteOntology()

  private def dereferenceOntologyIri(): Route = path("ontology" / Segments) { _: List[String] =>
    get { requestContext =>
      // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
      // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
      // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
      // project-specific API ontology, prefix it with settings.externalOntologyIriHostAndPort to get the
      // ontology IRI.

      val urlPath = requestContext.request.uri.path.toString

      val requestedOntologyStr: IRI = if (stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath)) {
        OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath
      } else if (stringFormatter.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
        "http://" + settings.externalOntologyIriHostAndPort + urlPath
      } else {
        throw BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath")
      }

      val requestedOntology = requestedOntologyStr.toSmartIriWithErr(
        throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
      )

      stringFormatter.checkExternalOntologyName(requestedOntology)

      val targetSchema = requestedOntology.getOntologySchema match {
        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
        case _                              => throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
      }

      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val allLanguagesStr             = params.get(ALL_LANGUAGES)
      val allLanguages = stringFormatter.optionStringToBoolean(
        allLanguagesStr,
        throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
      )

      val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext
                          )
      } yield OntologyEntitiesGetRequestV2(
        ontologyIri = requestedOntology,
        allLanguages = allLanguages,
        requestingUser = requestingUser
      )

      RouteUtilV2.runRdfRouteWithFuture(
        requestMessageF = requestMessageFuture,
        requestContext = requestContext,
        settings = settings,
        responderManager = responderManager,
        log = log,
        targetSchema = targetSchema,
        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
      )
    }
  }

  private def getOntologyMetadata(): Route =
    path(OntologiesBasePath / "metadata") {
      get { requestContext =>
        val maybeProjectIri: Option[SmartIri] = RouteUtilV2.getProject(requestContext)

        val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield OntologyMetadataGetByProjectRequestV2(
          projectIris = maybeProjectIri.toSet,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def updateOntologyMetadata(): Route =
    path(OntologiesBasePath / "metadata") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            val requestMessageFuture: Future[ChangeOntologyMetadataRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestMessage: ChangeOntologyMetadataRequestV2 <- ChangeOntologyMetadataRequestV2.fromJsonLD(
                                                                   jsonLDDocument = requestDoc,
                                                                   apiRequestID = UUID.randomUUID,
                                                                   requestingUser = requestingUser,
                                                                   responderManager = responderManager,
                                                                   storeManager = storeManager,
                                                                   settings = settings,
                                                                   log = log
                                                                 )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def getOntologyMetadataForProjects(): Route =
    path(OntologiesBasePath / "metadata" / Segments) { projectIris: List[IRI] =>
      get { requestContext =>
        val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )

          validatedProjectIris =
            projectIris
              .map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri")))
              .toSet
        } yield OntologyMetadataGetByProjectRequestV2(
          projectIris = validatedProjectIris,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def getOntology(): Route =
    path(OntologiesBasePath / "allentities" / Segment) { externalOntologyIriStr: IRI =>
      get { requestContext =>
        val requestedOntologyIri = externalOntologyIriStr.toSmartIriWithErr(
          throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
        )

        stringFormatter.checkExternalOntologyName(requestedOntologyIri)

        val targetSchema = requestedOntologyIri.getOntologySchema match {
          case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
          case _                              => throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap
        val allLanguagesStr             = params.get(ALL_LANGUAGES)
        val allLanguages = stringFormatter.optionStringToBoolean(
          params.get(ALL_LANGUAGES),
          throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
        )

        val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield OntologyEntitiesGetRequestV2(
          ontologyIri = requestedOntologyIri,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createClass(): Route = path(OntologiesBasePath / "classes") {
    post {
      // Create a new class.
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateClassRequestV2] = for {
            requestingUser <- getUserADM(requestContext)

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateClassRequestV2 <-
              CreateClassRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                settings = settings,
                log = log
              )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }

  private def updateClass(): Route =
    path(OntologiesBasePath / "classes") {
      put {
        // Change the labels or comments of a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeClassLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage <-
                ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  // delete the comment of a class definition
  private def deleteClassComment(): Route =
    path(OntologiesBasePath / "classes" / "comment" / Segment) { classIriStr: IRI =>
      delete { requestContext =>
        val classIri = classIriStr.toSmartIri

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))

        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteClassCommentRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield DeleteClassCommentRequestV2(
          classIri = classIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def addCardinalities(): Route =
    path(OntologiesBasePath / "cardinalities") {
      post {
        // Add cardinalities to a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[AddCardinalitiesToClassRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: AddCardinalitiesToClassRequestV2 <-
                AddCardinalitiesToClassRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def canReplaceCardinalities(): Route =
    path(OntologiesBasePath / "canreplacecardinalities" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanChangeCardinalitiesRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield CanChangeCardinalitiesRequestV2(
          classIri = classIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  // Replaces all cardinalities with what was sent. Deleting means send empty
  // replace request.
  private def replaceCardinalities(): Route =
    path(OntologiesBasePath / "cardinalities") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeCardinalitiesRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeCardinalitiesRequestV2 <-
                ChangeCardinalitiesRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def canDeleteCardinalitiesFromClass(): Route =
    path(OntologiesBasePath / "candeletecardinalities") {
      post {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CanDeleteCardinalitiesFromClassRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: CanDeleteCardinalitiesFromClassRequestV2 <-
                CanDeleteCardinalitiesFromClassRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  // delete a single cardinality from the specified class if the property is
  // not used in resources.
  private def deleteCardinalitiesFromClass(): Route =
    path(OntologiesBasePath / "cardinalities") {
      patch {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[DeleteCardinalitiesFromClassRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: DeleteCardinalitiesFromClassRequestV2 <-
                DeleteCardinalitiesFromClassRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def changeGuiOrder(): Route =
    path(OntologiesBasePath / "guiorder") {
      put {
        // Change a class's cardinalities.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeGuiOrderRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeGuiOrderRequestV2 <-
                ChangeGuiOrderRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def getClasses(): Route =
    path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      get { requestContext =>
        val classesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalResourceClassIris.map { classIriStr: IRI =>
          val requestedClassIri: SmartIri =
            classIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid class IRI: $classIriStr"))

          stringFormatter.checkExternalOntologyName(requestedClassIri)

          if (!requestedClassIri.isKnoraApiV2EntityIri) {
            throw BadRequestException(s"Invalid class IRI: $classIriStr")
          }

          val schema = requestedClassIri.getOntologySchema match {
            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
            case _                              => throw BadRequestException(s"Invalid class IRI: $classIriStr")
          }

          (requestedClassIri, schema)
        }.toSet

        val (classesForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = classesAndSchemas.unzip

        if (classesForResponder.map(_.getOntologyFromEntity).size != 1) {
          throw BadRequestException(s"Only one ontology may be queried per request")
        }

        // Decide which API schema to use for the response.
        val targetSchema = if (schemas.size == 1) {
          schemas.head
        } else {
          // The client requested different schemas.
          throw BadRequestException("The request refers to multiple API schemas")
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap
        val allLanguagesStr             = params.get(ALL_LANGUAGES)
        val allLanguages = stringFormatter.optionStringToBoolean(
          params.get(ALL_LANGUAGES),
          throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
        )

        val requestMessageFuture: Future[ClassesGetRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield ClassesGetRequestV2(
          classIris = classesForResponder,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def canDeleteClass(): Route =
    path(OntologiesBasePath / "candeleteclass" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanDeleteClassRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield CanDeleteClassRequestV2(
          classIri = classIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteClass(): Route =
    path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      delete { requestContext =>
        val classIriStr = externalResourceClassIris match {
          case List(str) => str
          case _         => throw BadRequestException(s"Only one class can be deleted at a time")
        }

        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteClassRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield DeleteClassRequestV2(
          classIri = classIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntologyComment(): Route =
    path(OntologiesBasePath / "comment" / Segment) { ontologyIriStr: IRI =>
      delete { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri

        if (!ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $ontologyIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))

        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteOntologyCommentRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield DeleteOntologyCommentRequestV2(
          ontologyIri = ontologyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createProperty(): Route =
    path(OntologiesBasePath / "properties") {
      post {
        // Create a new property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CreatePropertyRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: CreatePropertyRequestV2 <-
                CreatePropertyRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def updatePropertyLabelsOrComments(): Route =
    path(OntologiesBasePath / "properties") {
      put {
        // Change the labels or comments of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangePropertyLabelsOrCommentsRequestV2 <-
                ChangePropertyLabelsOrCommentsRequestV2
                  .fromJsonLD(
                    jsonLDDocument = requestDoc,
                    apiRequestID = UUID.randomUUID,
                    requestingUser = requestingUser,
                    responderManager = responderManager,
                    storeManager = storeManager,
                    settings = settings,
                    log = log
                  )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  // delete the comment of a property definition
  private def deletePropertyComment(): Route =
    path(OntologiesBasePath / "properties" / "comment" / Segment) { propertyIriStr: IRI =>
      delete { requestContext =>
        val propertyIri = propertyIriStr.toSmartIri

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid property IRI for request: $propertyIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))

        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeletePropertyCommentRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield DeletePropertyCommentRequestV2(
          propertyIri = propertyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def updatePropertyGuiElement(): Route =
    path(OntologiesBasePath / "properties" / "guielement") {
      put {
        // Change the salsah-gui:guiElement and/or salsah-gui:guiAttribute of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyGuiElementRequest] = for {
              requestingUser <- getUserADM(requestContext)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangePropertyGuiElementRequest <-
                ChangePropertyGuiElementRequest
                  .fromJsonLD(
                    jsonLDDocument = requestDoc,
                    apiRequestID = UUID.randomUUID,
                    requestingUser = requestingUser,
                    responderManager = responderManager,
                    storeManager = storeManager,
                    settings = settings,
                    log = log
                  )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              responderManager = responderManager,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def getProperties(): Route =
    path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      get { requestContext =>
        val propsAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalPropertyIris.map { propIriStr: IRI =>
          val requestedPropIri: SmartIri =
            propIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $propIriStr"))

          stringFormatter.checkExternalOntologyName(requestedPropIri)

          if (!requestedPropIri.isKnoraApiV2EntityIri) {
            throw BadRequestException(s"Invalid property IRI: $propIriStr")
          }

          val schema = requestedPropIri.getOntologySchema match {
            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
            case _                              => throw BadRequestException(s"Invalid property IRI: $propIriStr")
          }

          (requestedPropIri, schema)
        }.toSet

        val (propsForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = propsAndSchemas.unzip

        if (propsForResponder.map(_.getOntologyFromEntity).size != 1) {
          throw BadRequestException(s"Only one ontology may be queried per request")
        }

        // Decide which API schema to use for the response.
        val targetSchema = if (schemas.size == 1) {
          schemas.head
        } else {
          // The client requested different schemas.
          throw BadRequestException("The request refers to multiple API schemas")
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap
        val allLanguagesStr             = params.get(ALL_LANGUAGES)
        val allLanguages = stringFormatter.optionStringToBoolean(
          params.get(ALL_LANGUAGES),
          throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
        )

        val requestMessageFuture: Future[PropertiesGetRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield PropertiesGetRequestV2(
          propertyIris = propsForResponder,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def canDeleteProperty(): Route =
    path(OntologiesBasePath / "candeleteproperty" / Segment) { propertyIriStr: IRI =>
      get { requestContext =>
        val propertyIri = propertyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(propertyIri)

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $propertyIriStr")
        }

        val requestMessageFuture: Future[CanDeletePropertyRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield CanDeletePropertyRequestV2(
          propertyIri = propertyIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteProperty(): Route =
    path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      delete { requestContext =>
        val propertyIriStr = externalPropertyIris match {
          case List(str) => str
          case _         => throw BadRequestException(s"Only one property can be deleted at a time")
        }

        val propertyIri = propertyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(propertyIri)

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid property IRI for request: $propertyIri")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeletePropertyRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield DeletePropertyRequestV2(
          propertyIri = propertyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createOntology(): Route = path(OntologiesBasePath) {
    // Create a new, empty ontology.
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateOntologyRequestV2] = for {
            requestingUser <- getUserADM(requestContext)

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateOntologyRequestV2 <-
              CreateOntologyRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                settings = settings,
                log = log
              )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }

  private def canDeleteOntology(): Route =
    path(OntologiesBasePath / "candeleteontology" / Segment) { ontologyIriStr: IRI =>
      get { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(ontologyIri)

        if (!ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIriStr")
        }

        val requestMessageFuture: Future[CanDeleteOntologyRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield CanDeleteOntologyRequestV2(
          ontologyIri = ontologyIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntology(): Route = path(OntologiesBasePath / Segment) {
    ontologyIriStr =>
      delete { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(ontologyIri)

        if (
          !ontologyIri.isKnoraOntologyIri || ontologyIri.isKnoraBuiltInDefinitionIri || !ontologyIri.getOntologySchema
            .contains(ApiV2Complex)
        ) {
          throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIri")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteOntologyRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield DeleteOntologyRequestV2(
          ontologyIri = ontologyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
  }
}
