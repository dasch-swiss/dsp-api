/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.v2

import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}
import org.knora.webapi.sharedtestdata.{SharedOntologyTestDataADM, SharedTestDataADM}
import org.knora.webapi.util.{ClientEndpoint, TestDataFileContent, TestDataFilePath}

import scala.concurrent.{ExecutionContext, Future}

object OntologiesRouteV2 {
    val OntologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")
    val OntologiesBasePathString = "/v2/ontologies"
}

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
class OntologiesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ClientEndpoint {

    import OntologiesRouteV2._

    // Directory name for generated test data
    override val directoryName: String = "ontologies"

    private val ALL_LANGUAGES = "allLanguages"
    private val LAST_MODIFICATION_DATE = "lastModificationDate"

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = dereferenceOntologyIri ~ getOntologyMetadata ~ updateOntologyMetadata ~ getOntologyMetadataForProjects ~
        getOntology ~ createClass ~ updateClass ~ addCardinalities ~ replaceCardinalities ~ getClasses ~
        deleteClass ~ createProperty ~ updateProperty ~ getProperties ~ deleteProperty ~ createOntology ~
        deleteOntology

    private def dereferenceOntologyIri: Route = path("ontology" / Segments) { _: List[String] =>
        get {
            requestContext => {
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

                val requestedOntology = requestedOntologyStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr"))

                val targetSchema = requestedOntology.getOntologySchema match {
                    case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                    case _ => throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
                }

                val params: Map[String, String] = requestContext.request.uri.query().toMap
                val allLanguagesStr = params.get(ALL_LANGUAGES)
                val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
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
    }

    private def getOntologyMetadata: Route = path(OntologiesBasePath / "metadata") {
        get {
            requestContext => {
                val maybeProjectIri: Option[SmartIri] = RouteUtilV2.getProject(requestContext)

                val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield OntologyMetadataGetByProjectRequestV2(projectIris = maybeProjectIri.toSet, requestingUser = requestingUser)

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

    private def getOntologyMetadataTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$OntologiesBasePathString/metadata"))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("all-ontology-metadata-response"),
            text = responseStr
        )
    }

    private def updateOntologyMetadata: Route = path(OntologiesBasePath / "metadata") {
        put {
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[ChangeOntologyMetadataRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
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

    private def updateOntologyMetadataTestRequest: Future[TestDataFileContent] = {
        val ontologyIri = SharedOntologyTestDataADM.FOO_ONTOLOGY_IRI_LocalHost

        val newLabel = "The modified foo ontology"
        val newModificationDate = Instant.now
        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("update-ontology-metadata-request"),
                text = SharedTestDataADM.changeOntologyMetadata(
                    ontologyIri, newLabel, newModificationDate
                )
            )
        )
    }

    private def getOntologyMetadataForProjects: Route = path(OntologiesBasePath / "metadata" / Segments) { projectIris: List[IRI] =>
        get {
            requestContext => {

                val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                    validatedProjectIris = projectIris.map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri"))).toSet
                } yield OntologyMetadataGetByProjectRequestV2(projectIris = validatedProjectIris, requestingUser = requestingUser)

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

    // project ontologies to return in test data.
    private val testProjectOntologies: Map[String, IRI] = Map(
        "get-ontologies-project-anything-response" -> SharedTestDataADM.ANYTHING_PROJECT_IRI,
        "get-ontologies-project-incunabula-response" -> SharedTestDataADM.INCUNABULA_PROJECT_IRI,
        "get-ontologies-project-beol-response" -> SharedTestDataADM.BEOL_PROJECT_IRI
    )

    /**
     * Provides JSON-LD responses to requests for ontologies of projects, for use in tests of generated client code.
     */
    private def getOntologyMetadataForProjectsTestResponses: Future[Set[TestDataFileContent]] = {
        val responseFutures: Iterable[Future[TestDataFileContent]] = testProjectOntologies.map {
            case (filename, projectIri) =>
                val encodedProjectIri = URLEncoder.encode(projectIri, "UTF-8")

                for {
                    responseStr <- doTestDataRequest(Get(s"$baseApiUrl$OntologiesBasePathString/metadata/$encodedProjectIri"))
                } yield TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath(filename),
                    text = responseStr
                )
        }

        Future.sequence(responseFutures).map(_.toSet)
    }

    private def getOntology: Route = path(OntologiesBasePath / "allentities" / Segment) { externalOntologyIriStr: IRI =>
        get {
            requestContext => {
                val requestedOntologyIri = externalOntologyIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr"))

                val targetSchema = requestedOntologyIri.getOntologySchema match {
                    case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                    case _ => throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
                }

                val params: Map[String, String] = requestContext.request.uri.query().toMap
                val allLanguagesStr = params.get(ALL_LANGUAGES)
                val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
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
    }

    // Ontologies to return in test data.
    private val testOntologies: Map[String, IRI] = Map(
        "knora-api-ontology" -> OntologyConstants.KnoraApiV2Complex.KnoraApiOntologyIri,
        "anything-ontology" -> SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost,
        "minimal-ontology" -> SharedOntologyTestDataADM.MINIMAL_ONTOLOGY_IRI_LocalHost,
        "incunabula-ontology" -> SharedOntologyTestDataADM.INCUNABULA_ONTOLOGY_IRI_LocalHost
    )

    /**
     * Provides JSON-LD responses to requests for ontologies, for use in tests of generated client code.
     */
    private def getOntologyTestResponses: Future[Set[TestDataFileContent]] = {
        val responseFutures: Iterable[Future[TestDataFileContent]] = testOntologies.map {
            case (filename, ontologyIri) =>
                val encodedOntologyIri = URLEncoder.encode(ontologyIri, "UTF-8")

                for {
                    responseStr <- doTestDataRequest(Get(s"$baseApiUrl$OntologiesBasePathString/allentities/$encodedOntologyIri"))
                } yield TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath(filename),
                    text = responseStr
                )
        }

        Future.sequence(responseFutures).map(_.toSet)
    }

    private def createClass: Route = path(OntologiesBasePath / "classes") {
        post {
            // Create a new class.
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[CreateClassRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage: CreateClassRequestV2 <- CreateClassRequestV2.fromJsonLD(
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

    private def createClassTestRequest: Future[Set[TestDataFileContent]] = {
        val anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-class-with-cardinalities-request"),
                    text = SharedTestDataADM.createClassWithCardinalities(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-class-without-cardinalities-request"),
                    text = SharedTestDataADM.createClassWithoutCardinalities(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                )
            )
        )
    }

    private def updateClass: Route = path(OntologiesBasePath / "classes") {
        put {
            // Change the labels or comments of a class.
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[ChangeClassLabelsOrCommentsRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage <- ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
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

    private def updateClassTestRequest: Future[Set[TestDataFileContent]] = {
        val anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("change-class-label-request"),
                    text = SharedTestDataADM.changeClassLabel(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate
                    )
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("change-class-comment-request"),
                    text = SharedTestDataADM.changeClassComment(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate
                    )
                )
            )
        )
    }

    private def addCardinalities: Route = path(OntologiesBasePath / "cardinalities") {
        post {
            // Add cardinalities to a class.
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[AddCardinalitiesToClassRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage: AddCardinalitiesToClassRequestV2 <- AddCardinalitiesToClassRequestV2.fromJsonLD(
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

    private def addCardinalitiesTestRequest: Future[TestDataFileContent] = {
        val anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("add-cardinalities-to-class-nothing-request"),
                text = SharedTestDataADM.addCardinality(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
            )
        )
    }

    private def replaceCardinalities: Route = path(OntologiesBasePath / "cardinalities") {
        put {
            // Change a class's cardinalities.
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[ChangeCardinalitiesRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage: ChangeCardinalitiesRequestV2 <- ChangeCardinalitiesRequestV2.fromJsonLD(
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

    private def replaceCardinalitiesTestRequest: Future[Set[TestDataFileContent]] = {
        val anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("replace-class-cardinalities-request"),
                    text = SharedTestDataADM.replaceClassCardinalities(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("remove-property-cardinality-request"),
                    text = SharedTestDataADM.removeCardinalityOfProperty(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("remove-class-cardinalities-request"),
                    text = SharedTestDataADM.removeAllClassCardinalities(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                )
            )
        )
    }

    private def getClasses: Route = path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
        get {
            requestContext => {

                val classesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalResourceClassIris.map {
                    classIriStr: IRI =>
                        val requestedClassIri: SmartIri = classIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid class IRI: $classIriStr"))

                        if (!requestedClassIri.isKnoraApiV2EntityIri) {
                            throw BadRequestException(s"Invalid class IRI: $classIriStr")
                        }

                        val schema = requestedClassIri.getOntologySchema match {
                            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                            case _ => throw BadRequestException(s"Invalid class IRI: $classIriStr")
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
                val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

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
    }

    // Classes to return in test data.
    private val testClasses: Map[String, IRI] = Map(
        "get-class-anything-thing-response" -> SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost,
        "get-class-image-bild-response" -> SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS_LocalHost,
        "get-class-incunabula-book-response" -> SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS_LocalHost,
        "get-class-incunabula-page-response" -> SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS_LocalHost
    )

    /**
     * Provides JSON-LD responses to requests for classes, for use in tests of generated client code.
     */
    private def getClassesTestResponses: Future[Set[TestDataFileContent]] = {
        val responseFutures: Iterable[Future[TestDataFileContent]] = testClasses.map {
            case (filename, classIri) =>
                val encodedClassIri = URLEncoder.encode(classIri, "UTF-8")

                for {
                    responseStr <- doTestDataRequest(Get(s"$baseApiUrl$OntologiesBasePathString/classes/$encodedClassIri"))
                } yield TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath(filename),
                    text = responseStr
                )
        }

        Future.sequence(responseFutures).map(_.toSet)
    }

    private def deleteClass: Route = path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
        delete {
            requestContext => {

                val classIriStr = externalResourceClassIris match {
                    case List(str) => str
                    case _ => throw BadRequestException(s"Only one class can be deleted at a time")
                }

                val classIri = classIriStr.toSmartIri

                if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
                    throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
                }

                val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

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
    }

    private def createProperty: Route = path(OntologiesBasePath / "properties") {
        post {
            // Create a new property.
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[CreatePropertyRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage: CreatePropertyRequestV2 <- CreatePropertyRequestV2.fromJsonLD(
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

    private def createPropertyTestRequest: Future[TestDataFileContent] = {
        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("create-property-hasName-request"),
                text = SharedTestDataADM.createProperty(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost)
            )
        )
    }

    private def updateProperty: Route = path(OntologiesBasePath / "properties") {
        put {
            // Change the labels or comments of a property.
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[ChangePropertyLabelsOrCommentsRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage: ChangePropertyLabelsOrCommentsRequestV2 <- ChangePropertyLabelsOrCommentsRequestV2.fromJsonLD(
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

    private def updatePropertyTestRequest: Future[Set[TestDataFileContent]] = {
        val anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("change-property-comment-request"),
                    text = SharedTestDataADM.changePropertyComment(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("change-property-label-request"),
                    text = SharedTestDataADM.changePropertyLabel(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost, anythingLastModDate)
                )
            )
        )
    }

    private def getProperties: Route = path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
        get {
            requestContext => {

                val propsAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalPropertyIris.map {
                    propIriStr: IRI =>
                        val requestedPropIri: SmartIri = propIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $propIriStr"))

                        if (!requestedPropIri.isKnoraApiV2EntityIri) {
                            throw BadRequestException(s"Invalid property IRI: $propIriStr")
                        }

                        val schema = requestedPropIri.getOntologySchema match {
                            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                            case _ => throw BadRequestException(s"Invalid property IRI: $propIriStr")
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
                val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

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
    }

    // Classes to return in test data.
    private val testProperties: Map[String, IRI] = Map(
        "get-property-listValue-response" -> SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost,
        "get-property-DateValue-response" -> SharedOntologyTestDataADM.ANYTHING_HasDate_PROPERTY_LocalHost,
        "get-property-textValue-response" -> SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost,
        "get-property-linkvalue-response" -> SharedOntologyTestDataADM.INCUNABULA_PartOf_Property_LocalHost
    )


    /**
     * Provides JSON-LD responses to requests for classes, for use in tests of generated client code.
     */
    private def getPropertiesTestResponses: Future[Set[TestDataFileContent]] = {
        val responseFutures: Iterable[Future[TestDataFileContent]] = testProperties.map {
            case (filename, propertyIri) =>
                val encodedPropertyIri = URLEncoder.encode(propertyIri, "UTF-8")

                for {
                    responseStr <- doTestDataRequest(Get(s"$baseApiUrl$OntologiesBasePathString/properties/$encodedPropertyIri"))
                } yield TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath(filename),
                    text = responseStr
                )
        }

        Future.sequence(responseFutures).map(_.toSet)
    }

    private def deleteProperty: Route = path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
        delete {
            requestContext => {

                val propertyIriStr = externalPropertyIris match {
                    case List(str) => str
                    case _ => throw BadRequestException(s"Only one property can be deleted at a time")
                }

                val propertyIri = propertyIriStr.toSmartIri

                if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
                    throw BadRequestException(s"Invalid property IRI for request: $propertyIri")
                }

                val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

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
    }

    private def createOntology: Route = path(OntologiesBasePath) {
        // Create a new, empty ontology.
        post {
            entity(as[String]) { jsonRequest =>
                requestContext => {

                    val requestMessageFuture: Future[CreateOntologyRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        requestMessage: CreateOntologyRequestV2 <- CreateOntologyRequestV2.fromJsonLD(
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

    private def createOntologyTestRequest: Future[TestDataFileContent] = {
        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("create-empty-foo-ontology-request"),
                text = SharedTestDataADM.createOntology(SharedTestDataADM.IMAGES_PROJECT_IRI, "The foo ontology")
            )
        )
    }

    private def createOntologyTestResponse: Future[TestDataFileContent] = {
        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("create-empty-foo-ontology-response"),
                text = SharedTestDataADM.createOntologyResponse
            )
        )
    }

    private def deleteOntology: Route = path(OntologiesBasePath / Segment) { ontologyIriStr =>
        delete {
            requestContext => {

                val ontologyIri = ontologyIriStr.toSmartIri

                if (!ontologyIri.isKnoraOntologyIri || ontologyIri.isKnoraBuiltInDefinitionIri || !ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
                    throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIri")
                }

                val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

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

    private def deleteOntologyTestResponse: Future[TestDataFileContent] = {
        val responseStr = SharedTestDataADM.successResponse("Ontology http://0.0.0.0:3333/ontology/00FF/foo/v2 has been deleted")

        Future.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("delete-ontology-response"),
                text = responseStr
            )
        )
    }

    override def getTestData(implicit executionContext: ExecutionContext,
                             actorSystem: ActorSystem,
                             materializer: Materializer): Future[Set[TestDataFileContent]] = {
        for {
            ontologyResponses: Set[TestDataFileContent] <- getOntologyTestResponses
            ontologyMetadataResponses: TestDataFileContent <- getOntologyMetadataTestResponse
            projectOntologiesResponses: Set[TestDataFileContent] <- getOntologyMetadataForProjectsTestResponses
            ontologyClassResponses: Set[TestDataFileContent] <- getClassesTestResponses
            ontologyPropertyResponses: Set[TestDataFileContent] <- getPropertiesTestResponses
            createOntologyRequest: TestDataFileContent <- createOntologyTestRequest
            createOntologyResponse: TestDataFileContent <- createOntologyTestResponse
            updateOntologyMetadataRequest: TestDataFileContent <- updateOntologyMetadataTestRequest
            createClassRequest: Set[TestDataFileContent] <- createClassTestRequest
            addCardinalitiesRequest: TestDataFileContent <- addCardinalitiesTestRequest
            createPropertyRequest: TestDataFileContent <- createPropertyTestRequest
            updateClassRequest: Set[TestDataFileContent] <- updateClassTestRequest
            replaceCardinalitiesRequest: Set[TestDataFileContent] <- replaceCardinalitiesTestRequest
            updatePropertyRequest: Set[TestDataFileContent] <- updatePropertyTestRequest
            deleteOntologyResponse: TestDataFileContent <- deleteOntologyTestResponse
        } yield ontologyResponses + ontologyMetadataResponses ++ projectOntologiesResponses ++ ontologyClassResponses ++
            ontologyPropertyResponses + createOntologyRequest + createOntologyResponse + updateOntologyMetadataRequest ++
            createClassRequest + addCardinalitiesRequest + createPropertyRequest ++
            updateClassRequest ++ replaceCardinalitiesRequest ++ updatePropertyRequest + deleteOntologyResponse
    }
}
