/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v1

import java.util

import akka.actor.Status
import akka.pattern._
import akka.stream.ActorMaterializer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpHost, NameValuePair}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.messages.v1.responder.sipimessages.RepresentationV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.sipimessages.SipiConstants.FileType
import org.knora.webapi.messages.v1.responder.sipimessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.{FileValueV1, StillImageFileValueV1, TextFileValueV1}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{PermissionUtilADM, SipiUtil}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class SipiResponderV1 extends Responder {

    implicit override val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
    implicit private val materializer: ActorMaterializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    private val targetHost: HttpHost = new HttpHost(settings.internalSipiHost, settings.internalSipiPort, "http")

    private val sipiTimeoutMillis = settings.sipiTimeout.toMillis.toInt

    private val sipiRequestConfig = RequestConfig.custom
        .setConnectTimeout(sipiTimeoutMillis)
        .setConnectionRequestTimeout(sipiTimeoutMillis)
        .setSocketTimeout(sipiTimeoutMillis)
        .build

    private val httpClient: CloseableHttpClient = HttpClients.custom.setDefaultRequestConfig(sipiRequestConfig).build

    /**
      * Receives a message of type [[SipiResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive: PartialFunction[Any, Unit] = {
        case SipiFileInfoGetRequestV1(fileValueIri, userProfile) => future2Message(sender(), getFileInfoForSipiV1(fileValueIri, userProfile), log)
        case convertPathRequest: SipiResponderConversionPathRequestV1 => try2Message(sender(), convertPathV1(convertPathRequest), log)
        case convertFileRequest: SipiResponderConversionFileRequestV1 => try2Message(sender(), convertFileV1(convertFileRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete responses.

    /**
      * Returns a [[SipiFileInfoGetResponseV1]] containing the permissions and path for a file.
      *
      * @param filename the iri of the resource.
      * @return a [[SipiFileInfoGetResponseV1]].
      */
    private def getFileInfoForSipiV1(filename: String, userProfile: UserProfileV1): Future[SipiFileInfoGetResponseV1] = {

        log.debug(s"SipiResponderV1 - getFileInfoForSipiV1: filename: $filename, user: ${userProfile.userData.email}")

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getFileValue(
                triplestore = settings.triplestoreType,
                filename = filename
            ).toString())

            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = queryResponse.results.bindings
            // check if rows were found for the given filename
            _ = if (rows.isEmpty) throw BadRequestException(s"No file value was found for filename $filename")

            // check that only one file value was found (by grouping by file value IRI)
            groupedByResourceIri = rows.groupBy {
                row: VariableResultsRow =>
                    row.rowMap("fileValue")
            }
            _ = if (groupedByResourceIri.size > 1) throw InconsistentTriplestoreDataException(s"filename $filename is referred to from more than one file value")

            valueProps = valueUtilV1.createValueProps(filename, rows)

            maybePermissionCode: Option[Int] = PermissionUtilADM.getUserPermissionWithValuePropsV1(
                valueIri = filename,
                valueProps = valueProps,
                entityProject = None, // no need to specify this here, because it's in valueProps
                userProfile = userProfile
            )

            _ = log.debug(s"SipiResponderV1 - getFileInfoForSipiV1 - maybePermissionCode: $maybePermissionCode, requestingUser: ${userProfile.userData.email}")

            permissionCode: Int = maybePermissionCode.getOrElse(0) // Sipi expects a permission code from 0 to 8

        } yield SipiFileInfoGetResponseV1(
            permissionCode = permissionCode
        )
    }


    /**
      * Convert a file that has been sent to Knora (non GUI-case).
      *
      * @param conversionRequest the information about the file (uploaded by Knora).
      * @return a [[SipiResponderConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertPathV1(conversionRequest: SipiResponderConversionPathRequestV1): Try[SipiResponderConversionResponseV1] = {
        val url = s"${settings.internalSipiImageConversionUrlV1}/${settings.sipiPathConversionRouteV1}"

        callSipiConvertRoute(url, conversionRequest)

    }


    /**
      * Convert a file that is already managed by Sipi (GUI-case).
      *
      * @param conversionRequest the information about the file (managed by Sipi).
      * @return a [[SipiResponderConversionResponseV1]] representing the file values to be added to the triplestore.
      */
    private def convertFileV1(conversionRequest: SipiResponderConversionFileRequestV1): Try[SipiResponderConversionResponseV1] = {
        val url = s"${settings.internalSipiImageConversionUrlV1}/${settings.sipiFileConversionRouteV1}"

        callSipiConvertRoute(url, conversionRequest)
    }

    /**
      * Makes a conversion request to Sipi and creates a [[SipiResponderConversionResponseV1]]
      * containing the file values to be added to the triplestore.
      *
      * @param urlPath           the Sipi route to be called.
      * @param conversionRequest the message holding the information to make the request.
      * @return a [[SipiResponderConversionResponseV1]].
      */
    private def callSipiConvertRoute(urlPath: String, conversionRequest: SipiResponderConversionRequestV1): Try[SipiResponderConversionResponseV1] = {
        val context: HttpClientContext = HttpClientContext.create

        val formParams = new util.ArrayList[NameValuePair]()

        for ((key, value) <- conversionRequest.toFormData()) {
            formParams.add(new BasicNameValuePair(key, value))
        }

        val postEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
        val httpPost = new HttpPost(urlPath)
        httpPost.setEntity(postEntity)

        val conversionResultTry: Try[String] = Try {
            var maybeResponse: Option[CloseableHttpResponse] = None

            try {
                maybeResponse = Some(httpClient.execute(targetHost, httpPost, context))

                val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
                    case Some(responseEntity) => EntityUtils.toString(responseEntity)
                    case None => ""
                }

                val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
                val statusCategory: Int = statusCode / 100

                if (statusCategory != 2) {
                    val sipiErrorMsg = SipiUtil.getSipiErrorMessage(responseEntityStr)

                    if (statusCategory == 4) {
                        throw BadRequestException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
                    } else {
                        throw SipiException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
                    }
                } else {
                    responseEntityStr
                }
            } finally {
                maybeResponse match {
                    case Some(response) => response.close()
                    case None => ()
                }
            }
        }

        //
        // handle unsuccessful requests to Sipi
        //
        val recoveredConversionResultTry = conversionResultTry.recoverWith {
            case badRequestException: BadRequestException => throw badRequestException
            case sipiException: SipiException => throw sipiException
            case e: Exception => throw SipiException("Failed to connect to Sipi", e, log)
        }

        for {
            responseAsStr: String <- recoveredConversionResultTry

            /* get json from response body */
            responseAsJson: JsValue = JsonParser(responseAsStr)

            // get file type from Sipi response
            fileType: String = responseAsJson.asJsObject.fields.getOrElse("file_type", throw SipiException(message = "Sipi did not return a file type")) match {
                case JsString(ftype: String) => ftype
                case other => throw SipiException(message = s"Sipi returned an invalid file type: $other")
            }

            // turn fileType returned by Sipi (a string) into an enum
            fileTypeEnum: FileType.Value = SipiConstants.FileType.lookup(fileType)

            // create the apt case class depending on the file type returned by Sipi
            fileValuesV1: Vector[FileValueV1] = fileTypeEnum match {
                case SipiConstants.FileType.IMAGE =>
                    // parse response as a [[SipiImageConversionResponse]]
                    val imageConversionResult = try {
                        responseAsJson.convertTo[SipiImageConversionResponse]
                    } catch {
                        case e: DeserializationException => throw SipiException(message = "JSON response returned by Sipi is invalid, it cannot be turned into a SipiImageConversionResponse", e = e, log = log)
                    }

                    // create two StillImageFileValueV1s
                    Vector(StillImageFileValueV1(// full representation
                        internalMimeType = stringFormatter.toSparqlEncodedString(imageConversionResult.mimetype_full, throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${imageConversionResult.mimetype_full}")),
                        originalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.original_filename, throw BadRequestException(s"The original filename returned by Sipi is invalid: '${imageConversionResult.original_filename}")),
                        originalMimeType = Some(stringFormatter.toSparqlEncodedString(imageConversionResult.original_mimetype, throw BadRequestException(s"The original MIME type returned by Sipi is invalid: '${imageConversionResult.original_mimetype}"))),
                        dimX = imageConversionResult.nx_full,
                        dimY = imageConversionResult.ny_full,
                        internalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.filename_full, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${imageConversionResult.filename_full}")),
                        qualityLevel = 100,
                        qualityName = Some(SipiConstants.StillImage.fullQuality)
                    ),
                        StillImageFileValueV1(// thumbnail representation
                            internalMimeType = stringFormatter.toSparqlEncodedString(imageConversionResult.mimetype_thumb, throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${imageConversionResult.mimetype_full}")),
                            originalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.original_filename, throw BadRequestException(s"The original filename returned by Sipi is invalid: '${imageConversionResult.original_filename}")),
                            originalMimeType = Some(stringFormatter.toSparqlEncodedString(imageConversionResult.original_mimetype, throw BadRequestException(s"The original MIME type returned by Sipi is invalid: '${imageConversionResult.original_mimetype}"))),
                            dimX = imageConversionResult.nx_thumb,
                            dimY = imageConversionResult.ny_thumb,
                            internalFilename = stringFormatter.toSparqlEncodedString(imageConversionResult.filename_thumb, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${imageConversionResult.filename_thumb}")),
                            qualityLevel = 10,
                            qualityName = Some(SipiConstants.StillImage.thumbnailQuality),
                            isPreview = true
                        ))

                case SipiConstants.FileType.TEXT =>

                    // parse response as a [[SipiTextResponse]]
                    val textStoreResult = try {
                        responseAsJson.convertTo[SipiTextResponse]
                    } catch {
                        case e: DeserializationException => throw SipiException(message = "JSON response returned by Sipi is invalid, it cannot be turned into a SipiTextResponse", e = e, log = log)
                    }

                    Vector(TextFileValueV1(
                        internalMimeType = stringFormatter.toSparqlEncodedString(textStoreResult.mimetype, throw BadRequestException(s"The internal MIME type returned by Sipi is invalid: '${textStoreResult.mimetype}")),
                        internalFilename = stringFormatter.toSparqlEncodedString(textStoreResult.filename, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${textStoreResult.filename}")),
                        originalFilename = stringFormatter.toSparqlEncodedString(textStoreResult.original_filename, throw BadRequestException(s"The internal filename returned by Sipi is invalid: '${textStoreResult.original_filename}")),
                        originalMimeType = Some(stringFormatter.toSparqlEncodedString(textStoreResult.mimetype, throw BadRequestException(s"The orignal MIME type returned by Sipi is invalid: '${textStoreResult.original_mimetype}")))
                    ))

                case unknownType => throw NotImplementedException(s"Could not handle file type $unknownType")

                // TODO: add missing file types
            }

        } yield SipiResponderConversionResponseV1(fileValuesV1, file_type = fileTypeEnum)
    }

}