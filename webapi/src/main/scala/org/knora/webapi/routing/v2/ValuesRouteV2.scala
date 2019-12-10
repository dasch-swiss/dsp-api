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

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.SmartIri
import org.knora.webapi.util.clientapi.{ClientEndpoint, ClientFunction, SourceCodeFileContent, SourceCodeFilePath}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}

import scala.concurrent.{ExecutionContext, Future}

object ValuesRouteV2 {
    val ValuesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "values")
    val ValuesBasePathString = "/v2/values"
}

/**
 * Provides a routing function for API v2 routes that deal with values.
 */
class ValuesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ClientEndpoint {

    import ValuesRouteV2._

    // Definitions for ClientEndpoint
    override val name: String = "ValuesEndpoint"
    override val directoryName: String = "values"
    override val urlPath: String = "values"
    override val description: String = "An endpoint for working with Knora values."
    override val functions: Seq[ClientFunction] = Seq.empty

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getValue ~ createValue ~ updateValue ~ deleteValue

    private def getValue: Route = path(ValuesBasePath / Segment / Segment) { (resourceIriStr: IRI, valueUuidStr: String) =>
        get {
            requestContext => {
                val resourceIri: SmartIri = resourceIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))

                if (!resourceIri.isKnoraResourceIri) {
                    throw BadRequestException(s"Invalid resource IRI: $resourceIriStr")
                }

                val valueUuid: UUID = stringFormatter.decodeUuidWithErr(valueUuidStr, throw BadRequestException(s"Invalid value UUID: $valueUuidStr"))

                val params: Map[String, String] = requestContext.request.uri.query().toMap

                // Was a version date provided?
                val versionDate: Option[Instant] = params.get("version").map {
                    versionStr =>
                        def errorFun: Nothing = throw BadRequestException(s"Invalid version date: $versionStr")

                        // Yes. Try to parse it as an xsd:dateTimeStamp.
                        try {
                            stringFormatter.xsdDateTimeStampToInstant(versionStr, errorFun)
                        } catch {
                            // If that doesn't work, try to parse it as a Knora ARK timestamp.
                            case _: Exception => stringFormatter.arkTimestampToInstant(versionStr, errorFun)
                        }
                }

                val targetSchema: ApiV2Schema = RouteUtilV2.getOntologySchema(requestContext)
                val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptions(requestContext)

                val requestMessageFuture: Future[ResourcesGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ResourcesGetRequestV2(
                    resourceIris = Seq(resourceIri.toString),
                    valueUuid = Some(valueUuid),
                    versionDate = versionDate,
                    targetSchema = targetSchema,
                    requestingUser = requestingUser
                )

                RouteUtilV2.runRdfRouteWithFuture(
                    requestMessageF = requestMessageFuture,
                    requestContext = requestContext,
                    settings = settings,
                    responderManager = responderManager,
                    log = log,
                    targetSchema = targetSchema,
                    schemaOptions = schemaOptions
                )
            }
        }
    }

    // The UUIDs of values in SharedTestDataADM.TestDing.
    private val testDingValues: Map[String, String] = Map(
        "int-value" -> SharedTestDataADM.TestDing.intValueUuid,
        "decimal-value" -> SharedTestDataADM.TestDing.decimalValueUuid,
        "date-value" -> SharedTestDataADM.TestDing.dateValueUuid,
        "boolean-value" -> SharedTestDataADM.TestDing.booleanValueUuid,
        "uri-value" -> SharedTestDataADM.TestDing.uriValueUuid,
        "interval-value" -> SharedTestDataADM.TestDing.intervalValueUuid,
        "color-value" -> SharedTestDataADM.TestDing.colorValueUuid,
        "geom-value" -> SharedTestDataADM.TestDing.geomValueUuid,
        "geoname-value" -> SharedTestDataADM.TestDing.geonameValueUuid,
        "text-value-with-standoff" -> SharedTestDataADM.TestDing.textValueWithStandoffUuid,
        "text-value-without-standoff" -> SharedTestDataADM.TestDing.textValueWithoutStandoffUuid,
        "list-value" -> SharedTestDataADM.TestDing.listValueUuid,
        "link-value" -> SharedTestDataADM.TestDing.linkValueUuid
    )

    /**
     * Provides JSON-LD responses to requests for values, for use in tests of generated client code.
     */
    private def getValueTestResponses: Future[Set[SourceCodeFileContent]] = {
        val responseFutures: Iterable[Future[SourceCodeFileContent]] = testDingValues.map {
            case (valueTypeName, valueUuid) =>
                for {
                    responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ValuesBasePathString/${SharedTestDataADM.TestDing.iriEncoded}/$valueUuid") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingUser1.email, SharedTestDataADM.testPass)))
                } yield SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath(s"get-$valueTypeName-response"),
                    text = responseStr
                )
        }

        for {
            files: Iterable[SourceCodeFileContent] <- Future.sequence(responseFutures)

            getStillImageFileValueResponse: SourceCodeFileContent <- for {
                responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ValuesBasePathString/${SharedTestDataADM.AThingPicture.iriEncoded}/${SharedTestDataADM.AThingPicture.stillImageFileValueUuid}") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingUser1.email, SharedTestDataADM.testPass)))
            } yield SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath(s"get-still-image-file-value-response"),
                text = responseStr
            )
        } yield files.toSet + getStillImageFileValueResponse
    }

    private def createValue: Route = path(ValuesBasePath) {
        // #post-value-parse-jsonld
        post {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                    // #post-value-parse-jsonld

                    // #post-value-create-message
                    val requestMessageFuture: Future[CreateValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: CreateValueRequestV2 <- CreateValueRequestV2.fromJsonLD(
                            requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser,
                            responderManager = responderManager,
                            storeManager = storeManager,
                            settings = settings,
                            log = log
                        )
                    } yield requestMessage
                    // #post-value-create-message

                    // #specify-response-schema
                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                    // #specify-response-schema
                }
            }
        }
    }

    /**
     * Returns JSON-LD requests for creating values in tests of generated client code.
     */
    private def createValueTestRequests: Future[Set[SourceCodeFileContent]] = {
        FastFuture.successful(
            Set(
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-int-value-request"),
                    text = SharedTestDataADM.createIntValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        intValue = 4
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-int-value-with-custom-permissions-request"),
                    text = SharedTestDataADM.createIntValueWithCustomPermissionsRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        intValue = 4,
                        customPermissions = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-text-value-without-standoff-request"),
                    text = SharedTestDataADM.createTextValueWithoutStandoffRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        valueAsString = "How long is a piece of string?"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-text-value-with-standoff-request"),
                    text = SharedTestDataADM.createTextValueWithStandoffRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        textValueAsXml = SharedTestDataADM.textValue1AsXmlWithStandardMapping,
                        mappingIri = SharedTestDataADM.standardMappingIri
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-text-value-with-comment-request"),
                    text = SharedTestDataADM.createTextValueWithCommentRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        valueAsString = "This is the text.",
                        valueHasComment = "This is the comment on the text."
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-decimal-value-request"),
                    text = SharedTestDataADM.createDecimalValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        decimalValue = BigDecimal(4.3)
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-date-value-with-day-precision-request"),
                    text = SharedTestDataADM.createDateValueWithDayPrecisionRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        dateValueHasCalendar = "GREGORIAN",
                        dateValueHasStartYear = 2018,
                        dateValueHasStartMonth = 10,
                        dateValueHasStartDay = 5,
                        dateValueHasStartEra = "CE",
                        dateValueHasEndYear = 2018,
                        dateValueHasEndMonth = 10,
                        dateValueHasEndDay = 6,
                        dateValueHasEndEra = "CE"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-date-value-with-month-precision-request"),
                    text = SharedTestDataADM.createDateValueWithMonthPrecisionRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        dateValueHasCalendar = "GREGORIAN",
                        dateValueHasStartYear = 2018,
                        dateValueHasStartMonth = 10,
                        dateValueHasStartEra = "CE",
                        dateValueHasEndYear = 2018,
                        dateValueHasEndMonth = 10,
                        dateValueHasEndEra = "CE"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-date-value-with-year-precision-request"),
                    text = SharedTestDataADM.createDateValueWithYearPrecisionRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        dateValueHasCalendar = "GREGORIAN",
                        dateValueHasStartYear = 2018,
                        dateValueHasStartEra = "CE",
                        dateValueHasEndYear = 2019,
                        dateValueHasEndEra = "CE"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-boolean-value-request"),
                    text = SharedTestDataADM.createBooleanValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        booleanValue = true
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-geometry-value-request"),
                    text = SharedTestDataADM.createGeometryValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        geometryValue = SharedTestDataADM.geometryValue1
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-interval-value-request"),
                    text = SharedTestDataADM.createIntervalValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        intervalStart = BigDecimal("1.2"),
                        intervalEnd = BigDecimal("3.4")
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-list-value-request"),
                    text = SharedTestDataADM.createListValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        listNode = "http://rdfh.ch/lists/0001/treeList03"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-color-value-request"),
                    text = SharedTestDataADM.createColorValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        color = "#ff3333"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-uri-value-request"),
                    text = SharedTestDataADM.createUriValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        uri = "https://www.knora.org"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-geoname-value-request"),
                    text = SharedTestDataADM.createGeonameValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        geonameCode = "2661604"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("create-link-value-request"),
                    text = SharedTestDataADM.createLinkValueRequest(
                        resourceIri = SharedTestDataADM.AThing.iri,
                        targetResourceIri = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw"
                    )
                )
            )
        )
    }

    private def updateValue: Route = path(ValuesBasePath) {
        put {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[UpdateValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: UpdateValueRequestV2 <- UpdateValueRequestV2.fromJsonLD(
                            requestDoc,
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

    /**
     * Returns JSON-LD requests for updating values in tests of generated client code.
     */
    private def updateValueTestRequests: Future[Set[SourceCodeFileContent]] = {
        FastFuture.successful(
            Set(
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-int-value-request"),
                    text = SharedTestDataADM.updateIntValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.intValueIri,
                        intValue = 5
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-int-value-with-custom-permissions-request"),
                    text = SharedTestDataADM.updateIntValueWithCustomPermissionsRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.intValueIri,
                        intValue = 6,
                        customPermissions = "CR http://rdfh.ch/groups/0001/thing-searcher"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-int-value-permissions-only-request"),
                    text = SharedTestDataADM.updateIntValuePermissionsOnlyRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.intValueIri,
                        customPermissions = "CR http://rdfh.ch/groups/0001/thing-searcher|V knora-admin:KnownUser"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-text-value-without-standoff-request"),
                    text = SharedTestDataADM.updateTextValueWithoutStandoffRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.textValueWithoutStandoffIri,
                        valueAsString = "This is the updated text."
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-text-value-with-standoff-request"),
                    text = SharedTestDataADM.updateTextValueWithStandoffRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.textValueWithStandoffIri,
                        textValueAsXml = SharedTestDataADM.textValue2AsXmlWithStandardMapping,
                        mappingIri = SharedTestDataADM.standardMappingIri
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-text-value-with-comment-request"),
                    text = SharedTestDataADM.updateTextValueWithCommentRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.textValueWithoutStandoffIri,
                        valueAsString = "this is a text value that has an updated comment",
                        valueHasComment = "this is an updated comment"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-decimal-value-request"),
                    text = SharedTestDataADM.updateDecimalValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.decimalValueIri,
                        decimalValue = BigDecimal(5.6)
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-date-value-with-day-precision-request"),
                    text = SharedTestDataADM.updateDateValueWithDayPrecisionRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.dateValueIri,
                        dateValueHasCalendar = "GREGORIAN",
                        dateValueHasStartYear = 2018,
                        dateValueHasStartMonth = 10,
                        dateValueHasStartDay = 5,
                        dateValueHasStartEra = "CE",
                        dateValueHasEndYear = 2018,
                        dateValueHasEndMonth = 12,
                        dateValueHasEndDay = 6,
                        dateValueHasEndEra = "CE"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-date-value-with-month-precision-request"),
                    text = SharedTestDataADM.updateDateValueWithMonthPrecisionRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.dateValueIri,
                        dateValueHasCalendar = "GREGORIAN",
                        dateValueHasStartYear = 2018,
                        dateValueHasStartMonth = 9,
                        dateValueHasStartEra = "CE",
                        dateValueHasEndYear = 2018,
                        dateValueHasEndMonth = 12,
                        dateValueHasEndEra = "CE"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-date-value-with-year-precision-request"),
                    text = SharedTestDataADM.updateDateValueWithYearPrecisionRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.dateValueIri,
                        dateValueHasCalendar = "GREGORIAN",
                        dateValueHasStartYear = 2018,
                        dateValueHasStartEra = "CE",
                        dateValueHasEndYear = 2020,
                        dateValueHasEndEra = "CE"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-boolean-value-request"),
                    text = SharedTestDataADM.updateBooleanValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.booleanValueIri,
                        booleanValue = false
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-geometry-value-request"),
                    text = SharedTestDataADM.updateGeometryValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.geomValueIri,
                        geometryValue = SharedTestDataADM.geometryValue2
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-interval-value-request"),
                    text = SharedTestDataADM.updateIntervalValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.intervalValueIri,
                        intervalStart = BigDecimal("5.6"),
                        intervalEnd = BigDecimal("7.8")
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-list-value-request"),
                    text = SharedTestDataADM.updateListValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.listValueIri,
                        listNode = "http://rdfh.ch/lists/0001/treeList02"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-color-value-request"),
                    text = SharedTestDataADM.updateColorValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.colorValueIri,
                        color = "#ff3344"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-uri-value-request"),
                    text = SharedTestDataADM.updateUriValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.uriValueIri,
                        uri = "https://docs.knora.org"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-geoname-value-request"),
                    text = SharedTestDataADM.updateGeonameValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.geonameValueIri,
                        geonameCode = "2988507"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-link-value-request"),
                    text = SharedTestDataADM.updateLinkValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.linkValueIri,
                        targetResourceIri = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("update-still-image-file-value-request"),
                    text = SharedTestDataADM.updateStillImageFileValueRequest(
                        resourceIri = "http://rdfh.ch/0001/a-thing-picture",
                        valueIri = "http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q",
                        internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2"
                    )
                )
            )
        )
    }

    private def deleteValue: Route = path(ValuesBasePath / "delete") {
        post {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[DeleteValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: DeleteValueRequestV2 <- DeleteValueRequestV2.fromJsonLD(
                            requestDoc,
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

    /**
     * Returns JSON-LD requests for deleting values in tests of generated client code.
     */
    private def deleteValueTestRequests: Future[Set[SourceCodeFileContent]] = {
        FastFuture.successful(
            Set(
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("delete-int-value-request"),
                    text = SharedTestDataADM.deleteIntValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.intValueIri,
                        maybeDeleteComment = Some("this value was incorrect")
                    )
                ),
                SourceCodeFileContent(
                    filePath = SourceCodeFilePath.makeJsonPath("delete-link-value-request"),
                    text = SharedTestDataADM.deleteLinkValueRequest(
                        resourceIri = SharedTestDataADM.TestDing.iri,
                        valueIri = SharedTestDataADM.TestDing.linkValueIri
                    )
                )
            )
        )
    }

    override def getTestData(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer): Future[Set[SourceCodeFileContent]] = {
        for {
            getResponses: Set[SourceCodeFileContent] <- getValueTestResponses
            createRequests: Set[SourceCodeFileContent] <- createValueTestRequests
            updateRequests: Set[SourceCodeFileContent] <- updateValueTestRequests
            deleteRequests: Set[SourceCodeFileContent] <- deleteValueTestRequests
        } yield getResponses ++ createRequests ++ updateRequests ++ deleteRequests
    }
}
