/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi
package routing.v2

import exceptions.BadRequestException
import feature.FeatureFactoryConfig
import messages.IriConversions._
import messages.util.rdf.{JsonLDDocument, JsonLDUtil}
import messages.v2.responder.ontologymessages._
import messages.{OntologyConstants, SmartIri}
import routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}

import java.util.UUID
import scala.concurrent.Future

object OntologiesRouteV2 {
  val OntologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")
}

/**
  * Provides a routing function for API v2 routes that deal with ontologies.
  */
class OntologiesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  import OntologiesRouteV2._

  private val ALL_LANGUAGES = "allLanguages"
  private val LAST_MODIFICATION_DATE = "lastModificationDate"

  /**
    * Returns the route.
    */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    dereferenceOntologyIri(featureFactoryConfig) ~
      getOntologyMetadata(featureFactoryConfig) ~
      updateOntologyMetadata(featureFactoryConfig) ~
      getOntologyMetadataForProjects(featureFactoryConfig) ~
      getOntology(featureFactoryConfig) ~
      createClass(featureFactoryConfig) ~
      updateClass(featureFactoryConfig) ~
      addCardinalities(featureFactoryConfig) ~
      canReplaceCardinalities(featureFactoryConfig) ~
      replaceCardinalities(featureFactoryConfig) ~
      changeGuiOrder(featureFactoryConfig) ~
      getClasses(featureFactoryConfig) ~
      canDeleteClass(featureFactoryConfig) ~
      deleteClass(featureFactoryConfig) ~
      deleteOntologyComment(featureFactoryConfig) ~
      createProperty(featureFactoryConfig) ~
      updatePropertyLabelsOrComments(featureFactoryConfig) ~
      updatePropertyGuiElement(featureFactoryConfig) ~
      getProperties(featureFactoryConfig) ~
      canDeleteProperty(featureFactoryConfig) ~
      deleteProperty(featureFactoryConfig) ~
      createOntology(featureFactoryConfig) ~
      canDeleteOntology(featureFactoryConfig) ~
      deleteOntology(featureFactoryConfig)

  private def dereferenceOntologyIri(featureFactoryConfig: FeatureFactoryConfig): Route = path("ontology" / Segments) {
    _: List[String] =>
      get { requestContext =>
        {
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
            throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr"))

          stringFormatter.checkExternalOntologyName(requestedOntology)

          val targetSchema = requestedOntology.getOntologySchema match {
            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
            case _                              => throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
          }

          val params: Map[String, String] = requestContext.request.uri.query().toMap
          val allLanguagesStr = params.get(ALL_LANGUAGES)
          val allLanguages = stringFormatter.optionStringToBoolean(
            allLanguagesStr,
            throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

          val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            OntologyEntitiesGetRequestV2(
              ontologyIri = requestedOntology,
              allLanguages = allLanguages,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = targetSchema,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
  }

  private def getOntologyMetadata(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "metadata") {
      get { requestContext =>
        {
          val maybeProjectIri: Option[SmartIri] = RouteUtilV2.getProject(requestContext)

          val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            OntologyMetadataGetByProjectRequestV2(projectIris = maybeProjectIri.toSet, requestingUser = requestingUser)

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def updateOntologyMetadata(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "metadata") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            val requestMessageFuture: Future[ChangeOntologyMetadataRequestV2] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestMessage: ChangeOntologyMetadataRequestV2 <- ChangeOntologyMetadataRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                featureFactoryConfig = featureFactoryConfig,
                settings = settings,
                log = log
              )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def getOntologyMetadataForProjects(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "metadata" / Segments) { projectIris: List[IRI] =>
      get { requestContext =>
        {

          val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )

            validatedProjectIris = projectIris
              .map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri")))
              .toSet
          } yield
            OntologyMetadataGetByProjectRequestV2(projectIris = validatedProjectIris, requestingUser = requestingUser)

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def getOntology(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "allentities" / Segment) { externalOntologyIriStr: IRI =>
      get { requestContext =>
        {
          val requestedOntologyIri = externalOntologyIriStr.toSmartIriWithErr(
            throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr"))

          stringFormatter.checkExternalOntologyName(requestedOntologyIri)

          val targetSchema = requestedOntologyIri.getOntologySchema match {
            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
            case _                              => throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
          }

          val params: Map[String, String] = requestContext.request.uri.query().toMap
          val allLanguagesStr = params.get(ALL_LANGUAGES)
          val allLanguages = stringFormatter.optionStringToBoolean(
            params.get(ALL_LANGUAGES),
            throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

          val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            OntologyEntitiesGetRequestV2(
              ontologyIri = requestedOntologyIri,
              allLanguages = allLanguages,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = targetSchema,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def createClass(featureFactoryConfig: FeatureFactoryConfig): Route = path(OntologiesBasePath / "classes") {
    post {
      // Create a new class.
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateClassRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateClassRequestV2 <- CreateClassRequestV2.fromJsonLD(
              jsonLDDocument = requestDoc,
              apiRequestID = UUID.randomUUID,
              requestingUser = requestingUser,
              responderManager = responderManager,
              storeManager = storeManager,
              featureFactoryConfig = featureFactoryConfig,
              settings = settings,
              log = log
            )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
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

  private def updateClass(featureFactoryConfig: FeatureFactoryConfig): Route = path(OntologiesBasePath / "classes") {
    put {
      // Change the labels or comments of a class.
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[ChangeClassLabelsOrCommentsRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage <- ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
              jsonLDDocument = requestDoc,
              apiRequestID = UUID.randomUUID,
              requestingUser = requestingUser,
              responderManager = responderManager,
              storeManager = storeManager,
              featureFactoryConfig = featureFactoryConfig,
              settings = settings,
              log = log
            )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
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

  private def addCardinalities(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "cardinalities") {
      post {
        // Add cardinalities to a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[AddCardinalitiesToClassRequestV2] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: AddCardinalitiesToClassRequestV2 <- AddCardinalitiesToClassRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                featureFactoryConfig = featureFactoryConfig,
                settings = settings,
                log = log
              )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def canReplaceCardinalities(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "canreplacecardinalities" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanChangeCardinalitiesRequestV2] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          CanChangeCardinalitiesRequestV2(
            classIri = classIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
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
  private def replaceCardinalities(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "cardinalities") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeCardinalitiesRequestV2] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeCardinalitiesRequestV2 <- ChangeCardinalitiesRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                featureFactoryConfig = featureFactoryConfig,
                settings = settings,
                log = log
              )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def changeGuiOrder(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "guiorder") {
      put {
        // Change a class's cardinalities.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeGuiOrderRequestV2] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeGuiOrderRequestV2 <- ChangeGuiOrderRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                featureFactoryConfig = featureFactoryConfig,
                settings = settings,
                log = log
              )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def getClasses(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      get { requestContext =>
        {
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
          val allLanguagesStr = params.get(ALL_LANGUAGES)
          val allLanguages = stringFormatter.optionStringToBoolean(
            params.get(ALL_LANGUAGES),
            throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

          val requestMessageFuture: Future[ClassesGetRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            ClassesGetRequestV2(
              classIris = classesForResponder,
              allLanguages = allLanguages,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = targetSchema,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def canDeleteClass(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "candeleteclass" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanDeleteClassRequestV2] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          CanDeleteClassRequestV2(
            classIri = classIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteClass(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      delete { requestContext =>
        {

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
            throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

          val requestMessageFuture: Future[DeleteClassRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            DeleteClassRequestV2(
              classIri = classIri,
              lastModificationDate = lastModificationDate,
              apiRequestID = UUID.randomUUID,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def deleteOntologyComment(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "comment" / Segment) { ontologyIriStr: IRI =>
      delete { requestContext =>
        {

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
            throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

          val requestMessageFuture: Future[DeleteOntologyCommentRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            DeleteOntologyCommentRequestV2(
              ontologyIri = ontologyIri,
              lastModificationDate = lastModificationDate,
              apiRequestID = UUID.randomUUID,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def createProperty(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "properties") {
      post {
        // Create a new property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CreatePropertyRequestV2] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: CreatePropertyRequestV2 <- CreatePropertyRequestV2.fromJsonLD(
                jsonLDDocument = requestDoc,
                apiRequestID = UUID.randomUUID,
                requestingUser = requestingUser,
                responderManager = responderManager,
                storeManager = storeManager,
                featureFactoryConfig = featureFactoryConfig,
                settings = settings,
                log = log
              )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def updatePropertyLabelsOrComments(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "properties") {
      put {
        // Change the labels or comments of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangePropertyLabelsOrCommentsRequestV2 <- ChangePropertyLabelsOrCommentsRequestV2
                .fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  featureFactoryConfig = featureFactoryConfig,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def updatePropertyGuiElement(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "properties" / "guielement") {
      put {
        // Change the salsah-gui:guiElement and/or salsah-gui:guiAttribute of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyGuiElementRequest] = for {
              requestingUser <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangePropertyGuiElementRequest <- ChangePropertyGuiElementRequest
                .fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  responderManager = responderManager,
                  storeManager = storeManager,
                  featureFactoryConfig = featureFactoryConfig,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig,
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

  private def getProperties(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      get { requestContext =>
        {
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
          val allLanguagesStr = params.get(ALL_LANGUAGES)
          val allLanguages = stringFormatter.optionStringToBoolean(
            params.get(ALL_LANGUAGES),
            throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

          val requestMessageFuture: Future[PropertiesGetRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            PropertiesGetRequestV2(
              propertyIris = propsForResponder,
              allLanguages = allLanguages,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = targetSchema,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def canDeleteProperty(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "candeleteproperty" / Segment) { propertyIriStr: IRI =>
      get { requestContext =>
        val propertyIri = propertyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(propertyIri)

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $propertyIriStr")
        }

        val requestMessageFuture: Future[CanDeletePropertyRequestV2] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          CanDeletePropertyRequestV2(
            propertyIri = propertyIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteProperty(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      delete { requestContext =>
        {
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
            throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

          val requestMessageFuture: Future[DeletePropertyRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            DeletePropertyRequestV2(
              propertyIri = propertyIri,
              lastModificationDate = lastModificationDate,
              apiRequestID = UUID.randomUUID,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }

  private def createOntology(featureFactoryConfig: FeatureFactoryConfig): Route = path(OntologiesBasePath) {
    // Create a new, empty ontology.
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateOntologyRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateOntologyRequestV2 <- CreateOntologyRequestV2.fromJsonLD(
              jsonLDDocument = requestDoc,
              apiRequestID = UUID.randomUUID,
              requestingUser = requestingUser,
              responderManager = responderManager,
              storeManager = storeManager,
              featureFactoryConfig = featureFactoryConfig,
              settings = settings,
              log = log
            )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
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

  private def canDeleteOntology(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(OntologiesBasePath / "candeleteontology" / Segment) { ontologyIriStr: IRI =>
      get { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(ontologyIri)

        if (!ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIriStr")
        }

        val requestMessageFuture: Future[CanDeleteOntologyRequestV2] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          CanDeleteOntologyRequestV2(
            ontologyIri = ontologyIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntology(featureFactoryConfig: FeatureFactoryConfig): Route = path(OntologiesBasePath / Segment) {
    ontologyIriStr =>
      delete { requestContext =>
        {
          val ontologyIri = ontologyIriStr.toSmartIri
          stringFormatter.checkExternalOntologyName(ontologyIri)

          if (!ontologyIri.isKnoraOntologyIri || ontologyIri.isKnoraBuiltInDefinitionIri || !ontologyIri.getOntologySchema
                .contains(ApiV2Complex)) {
            throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIri")
          }

          val lastModificationDateStr = requestContext.request.uri
            .query()
            .toMap
            .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
          val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
            lastModificationDateStr,
            throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

          val requestMessageFuture: Future[DeleteOntologyRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            DeleteOntologyRequestV2(
              ontologyIri = ontologyIri,
              lastModificationDate = lastModificationDate,
              apiRequestID = UUID.randomUUID,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser
            )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
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
