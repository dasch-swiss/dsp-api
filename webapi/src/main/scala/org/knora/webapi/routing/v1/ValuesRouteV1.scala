/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import zio._
import scala.collection.immutable.Seq

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.util.DateUtilV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceInfoGetRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceInfoResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1._

/**
 * Provides an Akka routing function for API routes that deal with values.
 */
final case class ValuesRouteV1()(
  private implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends LazyLogging {

  private case class UpdateValueAndComment[A](value: A, comment: Option[String])

  def makeRoute: Route =
    valuesHistory ~ values ~ valuecomments ~ links ~ filevalue

  private def valuesHistory =
    path("v1" / "values" / "history" / Segments) { iris =>
      get { requestContext =>
        val requestTask = Authenticator.getUserADM(requestContext).flatMap(makeVersionHistoryRequestMessage(iris, _))
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  private def values =
    path("v1" / "values" / Segment) { valueIriStr =>
      get { requestContext =>
        val requestTask = Authenticator.getUserADM(requestContext).flatMap(makeGetValueRequest(valueIriStr, _))
        runJsonRouteZ(requestTask, requestContext)
      } ~ post {
        entity(as[CreateValueApiRequestV1]) { apiRequest => requestContext =>
          val requestTask =
            Authenticator.getUserADM(requestContext).flatMap(makeCreateValueRequestMessage(apiRequest, _))
          runJsonRouteZ(requestTask, requestContext)
        }
      } ~ put {
        entity(as[ChangeValueApiRequestV1]) { apiRequest => requestContext =>
          // In API v1, you cannot change a value and its comment in a single request. So we know that here,
          // we are getting a request to change either the value or the comment, but not both.
          val requestTask = for {
            userADM <- Authenticator.getUserADM(requestContext)
            request <- apiRequest match {
                         case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, Some(comment)) =>
                           makeChangeCommentRequestMessage(valueIriStr, Some(comment), userADM)
                         case _ =>
                           makeAddValueVersionRequestMessage(valueIriStr, apiRequest, userADM)
                       }
          } yield request
          runJsonRouteZ(requestTask, requestContext)
        }
      } ~ delete { requestContext =>
        val requestTask = for {
          userADM      <- Authenticator.getUserADM(requestContext)
          params        = requestContext.request.uri.query().toMap
          deleteComment = params.get("deleteComment")
          task         <- makeDeleteValueRequest(valueIriStr, deleteComment, userADM)
        } yield task
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  private def valuecomments =
    path("v1" / "valuecomments" / Segment) { valueIriStr =>
      delete { requestContext =>
        val requestTask =
          Authenticator.getUserADM(requestContext).flatMap(makeChangeCommentRequestMessage(valueIriStr, None, _))
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  private def links =
    path("v1" / "links" / Segments) { iris =>
      // Link value request requires 3 URL path segments: subject IRI, predicate IRI, and object IRI
      get { requestContext =>
        val requestTask = Authenticator.getUserADM(requestContext).flatMap(makeLinkValueGetRequestMessage(iris, _))
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  private def filevalue =
    path("v1" / "filevalue" / Segment) { resIriStr: IRI =>
      put {
        entity(as[ChangeFileValueApiRequestV1]) { apiRequest => requestContext =>
          val requestTask = for {
            userADM              <- Authenticator.getUserADM(requestContext)
            resourceIri          <- validateAndEscapeIri(resIriStr, s"Invalid resource IRI: $resIriStr")
            msg                   = ResourceInfoGetRequestV1(resourceIri, userADM)
            resourceInfoResponse <- MessageRelay.ask[ResourceInfoResponseV1](msg)
            projectShortcode <-
              ZIO
                .fromOption(resourceInfoResponse.resource_info)
                .mapBoth(_ => NotFoundException(s"Resource not found: $resourceIri"), _.project_shortcode)
            request <- makeChangeFileValueRequest(resIriStr, projectShortcode, apiRequest, userADM)
          } yield request
          runJsonRouteZ(requestTask, requestContext)
        }
      }
    }

  private def makeVersionHistoryRequestMessage(
    iris: Seq[IRI],
    userADM: UserADM
  ): ZIO[StringFormatter, Throwable, ValueVersionHistoryGetRequestV1] = {
    val paramsError = "Version history request requires resource IRI, property IRI, and current value IRI"
    for {
      _                                                      <- verifyNumberOfParams(iris, paramsError, length = 3)
      Seq(resourceIriStr, propertyIriStr, currentValueIriStr) = iris
      resourceIri                                            <- validateAndEscapeIri(resourceIriStr, s"Invalid resource IRI: $resourceIriStr")
      propertyIri                                            <- validateAndEscapeIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
      currentValueIri                                        <- validateAndEscapeIri(currentValueIriStr, s"Invalid value IRI: $currentValueIriStr")
    } yield ValueVersionHistoryGetRequestV1(resourceIri, propertyIri, currentValueIri, userADM)
  }

  private def makeLinkValueGetRequestMessage(
    iris: Seq[IRI],
    userADM: UserADM
  ): ZIO[StringFormatter, BadRequestException, LinkValueGetRequestV1] =
    for {
      _                                                <- verifyNumberOfParams(iris, "Link value request requires subject IRI, predicate IRI, and object IRI", 3)
      Seq(subjectIriStr, predicateIriStr, objectIriStr) = iris
      subjectIri                                       <- validateAndEscapeIri(subjectIriStr, s"Invalid subject IRI: $subjectIriStr")
      predicateIri                                     <- validateAndEscapeIri(predicateIriStr, s"Invalid predicate IRI: $predicateIriStr")
      objectIri                                        <- validateAndEscapeIri(objectIriStr, s"Invalid object IRI: $objectIriStr")
    } yield LinkValueGetRequestV1(subjectIri, predicateIri, objectIri, userADM)

  private def makeCreateValueRequestMessage(
    apiRequest: CreateValueApiRequestV1,
    userADM: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, CreateValueRequestV1] =
    for {
      resourceIri <- validateAndEscapeIri(apiRequest.res_id, s"Invalid resource IRI ${apiRequest.res_id}")
      propertyIri <- validateAndEscapeIri(apiRequest.prop, s"Invalid property IRI ${apiRequest.prop}")
      valueAndComment <-
        apiRequest.getValueClassIri match {
          case OntologyConstants.KnoraBase.TextValue =>
            val richtext: CreateRichtextV1 = apiRequest.richtext_value.get
            // check if text has markup
            if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
              toSparqlEncodedString(richtext.utf8str.get, s"Invalid text: '${richtext.utf8str.get}'")
                .map(utf8str =>
                  UpdateValueAndComment(TextValueSimpleV1(utf8str, richtext.language), apiRequest.comment)
                )

            } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
              // XML: text with markup
              for {
                mappingIri <-
                  validateAndEscapeIri(richtext.mapping_id.get, s"Invalid mapping IRI: ${richtext.mapping_id.get}")
                textWithStandoffTags <-
                  convertXMLtoStandoffTagV1(
                    richtext.xml.get,
                    mappingIri,
                    acceptStandoffLinksToClientIDs = false,
                    userADM,
                    logger
                  )
                resourceReferences <- getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV2)
                utf8str <- toSparqlEncodedString(
                             textWithStandoffTags.text,
                             "utf8str for for TextValue contains invalid characters"
                           )
              } yield UpdateValueAndComment(
                TextValueWithStandoffV1(
                  utf8str,
                  textWithStandoffTags.language,
                  textWithStandoffTags.standoffTagV2,
                  resourceReferences,
                  textWithStandoffTags.mapping.mappingIri,
                  textWithStandoffTags.mapping.mapping
                ),
                apiRequest.comment
              )
            } else {
              ZIO.fail(BadRequestException("invalid parameters given for TextValueV1"))
            }
          case OntologyConstants.KnoraBase.LinkValue =>
            validateAndEscapeIri(apiRequest.link_value.get, s"Invalid resource IRI: ${apiRequest.link_value.get}")
              .map(iri => UpdateValueAndComment(LinkUpdateV1(iri), apiRequest.comment))
          case OntologyConstants.KnoraBase.IntValue =>
            ZIO.succeed(UpdateValueAndComment(IntegerValueV1(apiRequest.int_value.get), apiRequest.comment))
          case OntologyConstants.KnoraBase.DecimalValue =>
            ZIO.succeed(UpdateValueAndComment(DecimalValueV1(apiRequest.decimal_value.get), apiRequest.comment))
          case OntologyConstants.KnoraBase.BooleanValue =>
            ZIO.succeed(UpdateValueAndComment(BooleanValueV1(apiRequest.boolean_value.get), apiRequest.comment))
          case OntologyConstants.KnoraBase.UriValue =>
            validateAndEscapeIri(apiRequest.uri_value.get, s"Invalid URI: ${apiRequest.uri_value.get}")
              .map(iri => UpdateValueAndComment(UriValueV1(iri), apiRequest.comment))
          case OntologyConstants.KnoraBase.DateValue =>
            ZIO
              .attempt(DateUtilV1.createJDNValueV1FromDateString(apiRequest.date_value.get))
              .map(UpdateValueAndComment(_, apiRequest.comment))
          case OntologyConstants.KnoraBase.ColorValue =>
            ZIO
              .fromOption(ValuesValidator.validateColor(apiRequest.color_value.get))
              .mapBoth(
                _ => BadRequestException(s"Invalid color value: ${apiRequest.color_value.get}"),
                c => UpdateValueAndComment(ColorValueV1(c), apiRequest.comment)
              )
          case OntologyConstants.KnoraBase.GeomValue =>
            ZIO
              .fromOption(ValuesValidator.validateGeometryString(apiRequest.geom_value.get))
              .mapBoth(
                _ => BadRequestException(s"Invalid geometry value: ${apiRequest.geom_value.get}"),
                geom => UpdateValueAndComment(GeomValueV1(geom), apiRequest.comment)
              )
          case OntologyConstants.KnoraBase.ListValue =>
            validateAndEscapeIri(apiRequest.hlist_value.get, s"Invalid value IRI: ${apiRequest.hlist_value.get}")
              .map(iri => UpdateValueAndComment(HierarchicalListValueV1(iri), apiRequest.comment))

          case OntologyConstants.KnoraBase.IntervalValue =>
            val timeValues: Seq[BigDecimal] = apiRequest.interval_value.get
            ZIO
              .fail(BadRequestException("parameters for interval_value invalid"))
              .when(timeValues.length != 2)
              .as(UpdateValueAndComment(IntervalValueV1(timeValues.head, timeValues(1)), apiRequest.comment))

          case OntologyConstants.KnoraBase.TimeValue =>
            xsdDateTimeStampToInstant(apiRequest.time_value.get, s"Invalid timestamp: ${apiRequest.time_value.get}")
              .map(timeStamp => UpdateValueAndComment(TimeValueV1(timeStamp), apiRequest.comment))
          case OntologyConstants.KnoraBase.GeonameValue =>
            ZIO.succeed(UpdateValueAndComment(GeonameValueV1(apiRequest.geoname_value.get), apiRequest.comment))
          case _ =>
            ZIO.fail(BadRequestException(s"No value submitted"))
        }
      comment <- sparqlEncodeComment(valueAndComment.comment)
      uuid    <- ZIO.random.flatMap(_.nextUUID)
    } yield CreateValueRequestV1(0, resourceIri, propertyIri, valueAndComment.value, comment, userADM, uuid)

  private def makeAddValueVersionRequestMessage(
    valueIriStr: IRI,
    apiRequest: ChangeValueApiRequestV1,
    userADM: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, ChangeValueRequestV1] =
    for {
      valueIri <- validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr")

      valueAndComment <-
        apiRequest.getValueClassIri match {
          case OntologyConstants.KnoraBase.TextValue =>
            val richtext: CreateRichtextV1 = apiRequest.richtext_value.get
            // check if text has markup
            if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
              // simple text
              toSparqlEncodedString(richtext.utf8str.get, s"Invalid text: '${richtext.utf8str.get}'")
                .map(t => UpdateValueAndComment(TextValueSimpleV1(t, richtext.language), apiRequest.comment))
            } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
              // XML: text with markup
              for {
                mappingIri <-
                  validateAndEscapeIri(richtext.mapping_id.get, s"Invalid mapping IRI: ${richtext.mapping_id.get}")
                textWithStandoffTags <-
                  convertXMLtoStandoffTagV1(
                    richtext.xml.get,
                    mappingIri,
                    acceptStandoffLinksToClientIDs = false,
                    userADM,
                    logger
                  )
                resourceReferences <- getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV2)
                utf8str <- toSparqlEncodedString(
                             textWithStandoffTags.text,
                             "utf8str for for TextValue contains invalid characters"
                           )
              } yield UpdateValueAndComment(
                TextValueWithStandoffV1(
                  utf8str,
                  richtext.language,
                  textWithStandoffTags.standoffTagV2,
                  resourceReferences,
                  textWithStandoffTags.mapping.mappingIri,
                  textWithStandoffTags.mapping.mapping
                ),
                apiRequest.comment
              )
            } else {
              ZIO.fail(BadRequestException("invalid parameters given for TextValueV1"))
            }
          case OntologyConstants.KnoraBase.LinkValue =>
            validateAndEscapeIri(apiRequest.link_value.get, s"Invalid resource IRI: ${apiRequest.link_value.get}")
              .map(resourceIri =>
                UpdateValueAndComment(LinkUpdateV1(targetResourceIri = resourceIri), apiRequest.comment)
              )
          case OntologyConstants.KnoraBase.IntValue =>
            ZIO.succeed(UpdateValueAndComment(IntegerValueV1(apiRequest.int_value.get), apiRequest.comment))
          case OntologyConstants.KnoraBase.DecimalValue =>
            ZIO.succeed(UpdateValueAndComment(DecimalValueV1(apiRequest.decimal_value.get), apiRequest.comment))
          case OntologyConstants.KnoraBase.BooleanValue =>
            ZIO.succeed(UpdateValueAndComment(BooleanValueV1(apiRequest.boolean_value.get), apiRequest.comment))
          case OntologyConstants.KnoraBase.UriValue =>
            validateAndEscapeIri(apiRequest.uri_value.get, s"Invalid URI: ${apiRequest.uri_value.get}")
              .map(uri => UpdateValueAndComment(UriValueV1(uri), apiRequest.comment))
          case OntologyConstants.KnoraBase.DateValue =>
            ZIO
              .attempt(DateUtilV1.createJDNValueV1FromDateString(apiRequest.date_value.get))
              .map(UpdateValueAndComment(_, apiRequest.comment))
          case OntologyConstants.KnoraBase.ColorValue =>
            ZIO
              .fromOption(ValuesValidator.validateColor(apiRequest.color_value.get))
              .mapBoth(
                _ => BadRequestException(s"Invalid color value: ${apiRequest.color_value.get}"),
                c => UpdateValueAndComment(ColorValueV1(c), apiRequest.comment)
              )
          case OntologyConstants.KnoraBase.GeomValue =>
            ZIO
              .fromOption(ValuesValidator.validateGeometryString(apiRequest.geom_value.get))
              .mapBoth(
                _ => BadRequestException(s"Invalid geometry value: ${apiRequest.geom_value.get}"),
                geom => UpdateValueAndComment(GeomValueV1(geom), apiRequest.comment)
              )
          case OntologyConstants.KnoraBase.ListValue =>
            validateAndEscapeIri(apiRequest.hlist_value.get, s"Invalid value IRI: ${apiRequest.hlist_value.get}")
              .map(listNodeIri => UpdateValueAndComment(HierarchicalListValueV1(listNodeIri), apiRequest.comment))
          case OntologyConstants.KnoraBase.IntervalValue =>
            val timeValues: Seq[BigDecimal] = apiRequest.interval_value.get
            ZIO
              .fail(BadRequestException("parameters for interval_value invalid"))
              .when(timeValues.length != 2)
              .as(UpdateValueAndComment(IntervalValueV1(timeValues.head, timeValues(1)), apiRequest.comment))
          case OntologyConstants.KnoraBase.TimeValue =>
            xsdDateTimeStampToInstant(apiRequest.time_value.get, "Invalid timestamp: ${apiRequest.time_value.get}")
              .map(time => UpdateValueAndComment(TimeValueV1(time), apiRequest.comment))
          case OntologyConstants.KnoraBase.GeonameValue =>
            ZIO.succeed(UpdateValueAndComment(GeonameValueV1(apiRequest.geoname_value.get), apiRequest.comment))
          case _ =>
            ZIO.fail(BadRequestException(s"No value submitted"))
        }
      comment <- sparqlEncodeComment(valueAndComment.comment)
      uuid    <- ZIO.random.flatMap(_.nextUUID)
    } yield ChangeValueRequestV1(valueIri, valueAndComment.value, comment, userADM, uuid)

  private def sparqlEncodeComment(comment: Option[String]) =
    comment.map(c => toSparqlEncodedString(c, s"Invalid comment: '$c'").map(Some(_))).getOrElse(ZIO.succeed(None))

  private def makeChangeCommentRequestMessage(
    valueIriStr: IRI,
    comment: Option[String],
    userADM: UserADM
  ): ZIO[StringFormatter, BadRequestException, ChangeCommentRequestV1] =
    for {
      valueIri <- validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr")
      comment  <- sparqlEncodeComment(comment)
      uuid     <- ZIO.random.flatMap(_.nextUUID)
    } yield ChangeCommentRequestV1(valueIri, comment, userADM, uuid)

  private def makeDeleteValueRequest(
    valueIriStr: IRI,
    deleteComment: Option[String],
    userADM: UserADM
  ): ZIO[StringFormatter, BadRequestException, DeleteValueRequestV1] =
    for {
      valueIri <- validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr")
      comment  <- sparqlEncodeComment(deleteComment)
      uuid     <- ZIO.random.flatMap(_.nextUUID)
    } yield DeleteValueRequestV1(valueIri, comment, userADM, uuid)

  private def makeGetValueRequest(
    valueIriStr: IRI,
    userADM: UserADM
  ): ZIO[StringFormatter, BadRequestException, ValueGetRequestV1] =
    validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr").map(ValueGetRequestV1(_, userADM))

  private def makeChangeFileValueRequest(
    resIriStr: IRI,
    projectShortcode: String,
    apiRequest: ChangeFileValueApiRequestV1,
    userADM: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, ChangeFileValueRequestV1] =
    for {
      resourceIri          <- validateAndEscapeIri(resIriStr, s"Invalid resource IRI: $resIriStr")
      tempFilePath         <- ZIO.serviceWithZIO[StringFormatter](sf => ZIO.attempt(sf.makeSipiTempFilePath(apiRequest.file)))
      msg                   = GetFileMetadataRequest(tempFilePath, userADM)
      fileMetadataResponse <- MessageRelay.ask[GetFileMetadataResponse](msg)
      fileValue            <- ZIO.attempt(makeFileValue(apiRequest.file, fileMetadataResponse, projectShortcode))
      uuid                 <- ZIO.random.flatMap(_.nextUUID)
    } yield ChangeFileValueRequestV1(resourceIri, fileValue, uuid, userADM)
}
