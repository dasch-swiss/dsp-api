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

import java.util.UUID
import scala.collection.immutable.Seq

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants.KnoraBase._
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
import org.knora.webapi.routing.RouteUtilZ.validateAndEscapeIri

/**
 * Provides an Akka routing function for API routes that deal with values.
 */
final case class ValuesRouteV1()(
  private implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends LazyLogging {

  def makeRoute: Route = path("v1" / "values" / "history" / Segments) { iris =>
    get { requestContext =>
      val requestTask = Authenticator.getUserADM(requestContext).flatMap(makeVersionHistoryRequestMessage(iris, _))
      runJsonRouteZ(requestTask, requestContext)
    }
  } ~ path("v1" / "values") {
    post {
      entity(as[CreateValueApiRequestV1]) { apiRequest => requestContext =>
        val requestTask =
          Authenticator.getUserADM(requestContext).flatMap(makeCreateValueRequestMessage(apiRequest, _))
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  } ~ path("v1" / "values" / Segment) { valueIriStr =>
    get { requestContext =>
      val requestTask = Authenticator.getUserADM(requestContext).flatMap(makeGetValueRequest(valueIriStr, _))
      runJsonRouteZ(requestTask, requestContext)
    } ~ put {
      entity(as[ChangeValueApiRequestV1]) { apiRequest => requestContext =>
        // In API v1, you cannot change a value and its comment in a single request. So we know that here,
        // we are getting a request to change either the value or the comment, but not both.
        val requestTask = for {
          userADM <- Authenticator.getUserADM(requestContext)
          request <- apiRequest match {
                       case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, Some(comment)) =>
                         makeChangeCommentRequest(valueIriStr, Some(comment), userADM)
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
        task         <- makeDeleteDeleteValueRequest(valueIriStr, deleteComment, userADM)
      } yield task
      runJsonRouteZ(requestTask, requestContext)
    }
  } ~ path("v1" / "valuecomments" / Segment) { valueIriStr =>
    delete { requestContext =>
      val requestTask =
        Authenticator.getUserADM(requestContext).flatMap(makeChangeCommentRequest(valueIriStr, None, _))
      runJsonRouteZ(requestTask, requestContext)
    }
  } ~ path("v1" / "links" / Segments) { iris =>
    // Link value request requires 3 URL path segments: subject IRI, predicate IRI, and object IRI
    get { requestContext =>
      val requestTask = Authenticator.getUserADM(requestContext).flatMap(makeLinkValueGetRequestMessage(iris, _))
      runJsonRouteZ(requestTask, requestContext)
    }
  } ~ path("v1" / "filevalue" / Segment) { resIriStr: IRI =>
    put {
      entity(as[ChangeFileValueApiRequestV1]) { apiRequest => requestContext =>
        val requestTask =
          Authenticator.getUserADM(requestContext).flatMap(makeChangeFileValueRequest(resIriStr, apiRequest, _))
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
      value <- apiRequest.getValueClassIri match {
                 case TextValue     => makeRichTextValueCreate(apiRequest, userADM)
                 case LinkValue     => makeLinkValueCreate(apiRequest)
                 case IntValue      => makeIntValueCreate(apiRequest)
                 case DecimalValue  => makeDecimalValueCreate(apiRequest)
                 case BooleanValue  => makeBooleanValueCreate(apiRequest)
                 case UriValue      => makeUriValueCreate(apiRequest)
                 case DateValue     => makeDateValueCreate(apiRequest)
                 case ColorValue    => makeColorValueCreate(apiRequest)
                 case GeomValue     => makeGeomValueCreate(apiRequest)
                 case ListValue     => makeListValueCreate(apiRequest)
                 case IntervalValue => makeIntervalValueCreate(apiRequest)
                 case TimeValue     => makeTimeValueCreate(apiRequest)
                 case GeonameValue  => makeGeonameValueCreate(apiRequest)
                 case _             => ZIO.fail(BadRequestException(s"No value submitted"))
               }
      comment <- sparqlEncodeComment(apiRequest.comment)
      uuid    <- ZIO.random.flatMap(_.nextUUID)
    } yield CreateValueRequestV1(0, resourceIri, propertyIri, value, comment, userADM, uuid)

  private def makeRichTextValueCreate(apiRequest: CreateValueApiRequestV1, userADM: UserADM) =
    makeRichTextValue(apiRequest.richtext_value, userADM)
  private def makeRichTextValueUpdate(apiRequest: ChangeValueApiRequestV1, userADM: UserADM) =
    makeRichTextValue(apiRequest.richtext_value, userADM)
  private def makeRichTextValue(value: Option[CreateRichtextV1], userADM: UserADM) = for {
    value     <- getValueOrFail(value, "Not present 'richtext_value'")
    textValue <- makeTextValue(value, userADM)
  } yield textValue

  private def makeTextValue(richtext: CreateRichtextV1, userADM: UserADM) =
    // check if text has markup
    if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
      toSparqlEncodedString(richtext.utf8str.get, s"Invalid text: '${richtext.utf8str.get}'")
        .map(utf8str => TextValueSimpleV1(utf8str, richtext.language))

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
      } yield TextValueWithStandoffV1(
        utf8str,
        textWithStandoffTags.language,
        textWithStandoffTags.standoffTagV2,
        resourceReferences,
        textWithStandoffTags.mapping.mappingIri,
        textWithStandoffTags.mapping.mapping
      )
    } else {
      ZIO.fail(BadRequestException("invalid parameters given for TextValueV1"))
    }

  private def getValueOrFail[A](maybe: Option[A], errorMsg: String): IO[BadRequestException, A] =
    ZIO.fromOption(maybe).orElseFail(BadRequestException(errorMsg))

  private def makeGeonameValueCreate(apiRequest: CreateValueApiRequestV1) = makeGeonameValue(apiRequest.geoname_value)
  private def makeGeonameValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeGeonameValue(apiRequest.geoname_value)
  private def makeGeonameValue(value: Option[IRI]) =
    getValueOrFail(value, "Not present 'geoname_value'").map(GeonameValueV1)

  private def makeGeomValueCreate(apiRequest: CreateValueApiRequestV1) = makeGeomValue(apiRequest.geom_value)
  private def makeGeomValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeGeomValue(apiRequest.geom_value)
  private def makeGeomValue(value: Option[IRI]) = for {
    value <- getValueOrFail(value, "Not present 'geom_value'")
    geom <- ZIO
              .fromOption(ValuesValidator.validateGeometryString(value))
              .orElseFail(BadRequestException(s"Invalid 'geom_value': $value"))
  } yield GeomValueV1(geom)

  private def makeTimeValueCreate(apiRequest: CreateValueApiRequestV1) = makeTimeValue(apiRequest.time_value)
  private def makeTimeValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeTimeValue(apiRequest.time_value)
  private def makeTimeValue(value: Option[IRI]) = for {
    value <- getValueOrFail(value, "Not present 'time_value'")
    ts    <- xsdDateTimeStampToInstant(value, s"Invalid 'time_value': $value")
  } yield TimeValueV1(ts)

  private def makeListValueCreate(apiRequest: CreateValueApiRequestV1) = makeListValue(apiRequest.hlist_value)
  private def makeListValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeListValue(apiRequest.hlist_value)
  private def makeListValue(value: Option[IRI]) =
    for {
      value <- getValueOrFail(value, "Not present 'hlist_value'")
      iri   <- validateAndEscapeIri(value, s"Invalid 'hlist_value': $value")
    } yield HierarchicalListValueV1(iri)

  private def makeIntervalValueCreate(apiRequest: CreateValueApiRequestV1) =
    makeIntervalValue(apiRequest.interval_value)
  private def makeIntervalValueUpdate(apiRequest: ChangeValueApiRequestV1) =
    makeIntervalValue(apiRequest.interval_value)
  private def makeIntervalValue(timeValueMaybe: Option[Seq[BigDecimal]]) = for {
    timeValues <- getValueOrFail(timeValueMaybe, "Not present 'interval_value'")
    intervalValue <- ZIO
                       .fail(BadRequestException("Parameters for 'interval_value' invalid"))
                       .when(timeValues.length != 2)
                       .as(IntervalValueV1(timeValues.head, timeValues(1)))
  } yield intervalValue

  private def makeColorValueCreate(apiRequest: CreateValueApiRequestV1) = makeColorValue(apiRequest.color_value)
  private def makeColorValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeColorValue(apiRequest.color_value)
  private def makeColorValue(value: Option[IRI]) = for {
    value <- getValueOrFail(value, "Not present 'color_value'")
    color <- ZIO
               .fromOption(ValuesValidator.validateColor(value))
               .orElseFail(BadRequestException(s"Invalid 'color_value': $value"))
  } yield ColorValueV1(color)

  private def makeDateValueCreate(apiRequest: CreateValueApiRequestV1) = makeDateValue(apiRequest.date_value)
  private def makeDateValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeDateValue(apiRequest.date_value)
  private def makeDateValue(value: Option[IRI]) =
    for {
      value <- getValueOrFail(value, "Not present 'date_value'")
      date <- ZIO
                .attempt(DateUtilV1.createJDNValueV1FromDateString(value))
                .orElseFail(BadRequestException(s"Invalid 'date_value': $value"))
    } yield date

  private def makeUriValueCreate(apiRequest: CreateValueApiRequestV1) = makeUriValue(apiRequest.uri_value)
  private def makeUriValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeUriValue(apiRequest.uri_value)
  private def makeUriValue(value: Option[IRI]) =
    for {
      value <- getValueOrFail(value, "Not present 'uri_value'")
      iri   <- validateAndEscapeIri(value, s"Invalid 'uri_value': $value")
    } yield UriValueV1(iri)

  private def makeBooleanValueCreate(apiRequest: CreateValueApiRequestV1) = makeBooleanValue(apiRequest.boolean_value)
  private def makeBooleanUpdateValue(apiRequest: ChangeValueApiRequestV1) = makeBooleanValue(apiRequest.boolean_value)
  private def makeBooleanValue(value: Option[Boolean]) =
    getValueOrFail(value, "Not present 'boolean_value'").map(BooleanValueV1)

  private def makeDecimalValueCreate(apiRequest: CreateValueApiRequestV1) = makeDecimalValue(apiRequest.decimal_value)
  private def makeDecimalValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeDecimalValue(apiRequest.decimal_value)
  private def makeDecimalValue(value: Option[BigDecimal]) =
    getValueOrFail(value, "Not present 'decimal_value'").map(DecimalValueV1)

  private def makeIntValueCreate(apiRequest: CreateValueApiRequestV1) = makeIntValue(apiRequest.int_value)
  private def makeIntValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeIntValue(apiRequest.int_value)
  private def makeIntValue(value: Option[Int])                        = getValueOrFail(value, "Not present 'int_value'").map(IntegerValueV1)

  private def makeLinkValueCreate(apiRequest: CreateValueApiRequestV1) = makeLinkValue(apiRequest.link_value)
  private def makeLinkValueUpdate(apiRequest: ChangeValueApiRequestV1) = makeLinkValue(apiRequest.link_value)
  private def makeLinkValue(value: Option[String]) = for {
    value <- getValueOrFail(value, "Not present 'link_value'")
    iri   <- validateAndEscapeIri(value, s"Invalid 'link_value': $value")
  } yield LinkUpdateV1(iri)

  private def makeAddValueVersionRequestMessage(
    valueIriStr: IRI,
    apiRequest: ChangeValueApiRequestV1,
    userADM: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, ChangeValueRequestV1] =
    for {
      valueIri <- validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr")
      value <- apiRequest.getValueClassIri match {
                 case BooleanValue  => makeBooleanUpdateValue(apiRequest)
                 case ColorValue    => makeColorValueUpdate(apiRequest)
                 case DateValue     => makeDateValueUpdate(apiRequest)
                 case DecimalValue  => makeDecimalValueUpdate(apiRequest)
                 case GeomValue     => makeGeomValueUpdate(apiRequest)
                 case GeonameValue  => makeGeonameValueUpdate(apiRequest)
                 case IntValue      => makeIntValueUpdate(apiRequest)
                 case IntervalValue => makeIntervalValueUpdate(apiRequest)
                 case LinkValue     => makeLinkValueUpdate(apiRequest)
                 case ListValue     => makeListValueUpdate(apiRequest)
                 case TextValue     => makeRichTextValueUpdate(apiRequest, userADM)
                 case TimeValue     => makeTimeValueUpdate(apiRequest)
                 case UriValue      => makeUriValueUpdate(apiRequest)
                 case _             => ZIO.fail(BadRequestException(s"No value submitted"))
               }
      comment <- sparqlEncodeComment(apiRequest.comment)
      uuid    <- ZIO.random.flatMap(_.nextUUID)
    } yield ChangeValueRequestV1(valueIri, value, comment, userADM, uuid)

  private def sparqlEncodeComment(comment: Option[String]) =
    comment.map(c => toSparqlEncodedString(c, s"Invalid comment: '$c'").map(Some(_))).getOrElse(ZIO.none)

  private def makeChangeCommentRequest(valueIriStr: IRI, comment: Option[String], userADM: UserADM) =
    getIriCommentUuid(valueIriStr, comment)
      .map(r => ChangeCommentRequestV1(r.validIri, r.encodedComment, userADM, r.uuid))

  private case class IriCommentUuid(validIri: IRI, encodedComment: Option[String], uuid: UUID)
  private def getIriCommentUuid(
    valueIriStr: IRI,
    comment: Option[String]
  ): ZIO[StringFormatter, BadRequestException, IriCommentUuid] = for {
    validIri       <- validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr")
    encodedComment <- sparqlEncodeComment(comment)
    uuid           <- ZIO.random.flatMap(_.nextUUID)
  } yield IriCommentUuid(validIri, encodedComment, uuid)

  private def makeDeleteDeleteValueRequest(valueIriStr: IRI, deleteComment: Option[String], userADM: UserADM) =
    getIriCommentUuid(valueIriStr, deleteComment)
      .map(r => DeleteValueRequestV1(r.validIri, r.encodedComment, userADM, r.uuid))

  private def makeGetValueRequest(valueIriStr: IRI, userADM: UserADM) =
    validateAndEscapeIri(valueIriStr, s"Invalid value IRI: $valueIriStr").map(ValueGetRequestV1(_, userADM))

  private def makeChangeFileValueRequest(
    resIriStr: IRI,
    apiRequest: ChangeFileValueApiRequestV1,
    userADM: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, ChangeFileValueRequestV1] =
    for {
      resourceIri <- validateAndEscapeIri(resIriStr, s"Invalid resource IRI: $resIriStr")
      projectShortcode <- MessageRelay
                            .ask[ResourceInfoResponseV1](ResourceInfoGetRequestV1(resourceIri, userADM))
                            .flatMap(response => ZIO.fromOption(response.resource_info))
                            .mapBoth(_ => NotFoundException(s"Resource not found: $resourceIri"), _.project_shortcode)
      tempFilePath         <- ZIO.serviceWithZIO[StringFormatter](sf => ZIO.attempt(sf.makeSipiTempFilePath(apiRequest.file)))
      msg                   = GetFileMetadataRequest(tempFilePath, userADM)
      fileMetadataResponse <- MessageRelay.ask[GetFileMetadataResponse](msg)
      fileValue            <- ZIO.attempt(makeFileValue(apiRequest.file, fileMetadataResponse, projectShortcode))
      uuid                 <- ZIO.random.flatMap(_.nextUUID)
    } yield ChangeFileValueRequestV1(resourceIri, fileValue, uuid, userADM)
}
