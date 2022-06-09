/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.util.DateUtilV1
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceInfoGetRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceInfoResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV1

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future

/**
 * Provides an Akka routing function for API routes that deal with values.
 */
class ValuesRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {

    def makeVersionHistoryRequestMessage(iris: Seq[IRI], userADM: UserADM): ValueVersionHistoryGetRequestV1 = {
      if (iris.length != 3)
        throw BadRequestException("Version history request requires resource IRI, property IRI, and current value IRI")

      val Seq(resourceIriStr, propertyIriStr, currentValueIriStr) = iris

      val resourceIri = stringFormatter.validateAndEscapeIri(
        resourceIriStr,
        throw BadRequestException(s"Invalid resource IRI: $resourceIriStr")
      )
      val propertyIri = stringFormatter.validateAndEscapeIri(
        propertyIriStr,
        throw BadRequestException(s"Invalid property IRI: $propertyIriStr")
      )
      val currentValueIri = stringFormatter.validateAndEscapeIri(
        currentValueIriStr,
        throw BadRequestException(s"Invalid value IRI: $currentValueIriStr")
      )

      ValueVersionHistoryGetRequestV1(
        resourceIri = resourceIri,
        propertyIri = propertyIri,
        currentValueIri = currentValueIri,
        userProfile = userADM
      )
    }

    def makeLinkValueGetRequestMessage(iris: Seq[IRI], userADM: UserADM): LinkValueGetRequestV1 = {
      if (iris.length != 3)
        throw BadRequestException("Link value request requires subject IRI, predicate IRI, and object IRI")

      val Seq(subjectIriStr, predicateIriStr, objectIriStr) = iris

      val subjectIri = stringFormatter.validateAndEscapeIri(
        subjectIriStr,
        throw BadRequestException(s"Invalid subject IRI: $subjectIriStr")
      )
      val predicateIri = stringFormatter.validateAndEscapeIri(
        predicateIriStr,
        throw BadRequestException(s"Invalid predicate IRI: $predicateIriStr")
      )
      val objectIri = stringFormatter.validateAndEscapeIri(
        objectIriStr,
        throw BadRequestException(s"Invalid object IRI: $objectIriStr")
      )

      LinkValueGetRequestV1(
        subjectIri = subjectIri,
        predicateIri = predicateIri,
        objectIri = objectIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM
      )
    }

    def makeCreateValueRequestMessage(
      apiRequest: CreateValueApiRequestV1,
      userADM: UserADM
    ): Future[CreateValueRequestV1] = {
      val resourceIri = stringFormatter.validateAndEscapeIri(
        apiRequest.res_id,
        throw BadRequestException(s"Invalid resource IRI ${apiRequest.res_id}")
      )
      val propertyIri = stringFormatter.validateAndEscapeIri(
        apiRequest.prop,
        throw BadRequestException(s"Invalid property IRI ${apiRequest.prop}")
      )

      for {
        (value: UpdateValueV1, commentStr: Option[String]) <- apiRequest.getValueClassIri match {

                                                                case OntologyConstants.KnoraBase.TextValue =>
                                                                  val richtext: CreateRichtextV1 =
                                                                    apiRequest.richtext_value.get

                                                                  // check if text has markup
                                                                  if (
                                                                    richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty
                                                                  ) {
                                                                    // simple text
                                                                    Future(
                                                                      (
                                                                        TextValueSimpleV1(
                                                                          stringFormatter.toSparqlEncodedString(
                                                                            richtext.utf8str.get,
                                                                            throw BadRequestException(
                                                                              s"Invalid text: '${richtext.utf8str.get}'"
                                                                            )
                                                                          ),
                                                                          richtext.language
                                                                        ),
                                                                        apiRequest.comment
                                                                      )
                                                                    )
                                                                  } else if (
                                                                    richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty
                                                                  ) {
                                                                    // XML: text with markup

                                                                    val mappingIri =
                                                                      stringFormatter.validateAndEscapeIri(
                                                                        richtext.mapping_id.get,
                                                                        throw BadRequestException(
                                                                          s"mapping_id ${richtext.mapping_id.get} is invalid"
                                                                        )
                                                                      )

                                                                    for {

                                                                      textWithStandoffTags: TextWithStandoffTagsV2 <-
                                                                        RouteUtilV1.convertXMLtoStandoffTagV1(
                                                                          xml = richtext.xml.get,
                                                                          mappingIri = mappingIri,
                                                                          acceptStandoffLinksToClientIDs = false,
                                                                          userProfile = userADM,
                                                                          featureFactoryConfig = featureFactoryConfig,
                                                                          settings = settings,
                                                                          appActor = appActor,
                                                                          log = log
                                                                        )

                                                                      // collect the resource references from the linking standoff nodes
                                                                      resourceReferences: Set[IRI] =
                                                                        stringFormatter.getResourceIrisFromStandoffTags(
                                                                          textWithStandoffTags.standoffTagV2
                                                                        )

                                                                    } yield (
                                                                      TextValueWithStandoffV1(
                                                                        utf8str = stringFormatter.toSparqlEncodedString(
                                                                          textWithStandoffTags.text,
                                                                          throw InconsistentRepositoryDataException(
                                                                            "utf8str for for TextValue contains invalid characters"
                                                                          )
                                                                        ),
                                                                        language = textWithStandoffTags.language,
                                                                        resource_reference = resourceReferences,
                                                                        standoff = textWithStandoffTags.standoffTagV2,
                                                                        mappingIri =
                                                                          textWithStandoffTags.mapping.mappingIri,
                                                                        mapping = textWithStandoffTags.mapping.mapping
                                                                      ),
                                                                      apiRequest.comment
                                                                    )

                                                                  } else {
                                                                    throw BadRequestException(
                                                                      "invalid parameters given for TextValueV1"
                                                                    )
                                                                  }

                                                                case OntologyConstants.KnoraBase.LinkValue =>
                                                                  val resourceIRI =
                                                                    stringFormatter.validateAndEscapeIri(
                                                                      apiRequest.link_value.get,
                                                                      throw BadRequestException(
                                                                        s"Invalid resource IRI: ${apiRequest.link_value.get}"
                                                                      )
                                                                    )
                                                                  Future(
                                                                    LinkUpdateV1(targetResourceIri = resourceIRI),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.IntValue =>
                                                                  Future(
                                                                    (
                                                                      IntegerValueV1(apiRequest.int_value.get),
                                                                      apiRequest.comment
                                                                    )
                                                                  )

                                                                case OntologyConstants.KnoraBase.DecimalValue =>
                                                                  Future(
                                                                    (
                                                                      DecimalValueV1(apiRequest.decimal_value.get),
                                                                      apiRequest.comment
                                                                    )
                                                                  )

                                                                case OntologyConstants.KnoraBase.BooleanValue =>
                                                                  Future(
                                                                    BooleanValueV1(apiRequest.boolean_value.get),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.UriValue =>
                                                                  Future(
                                                                    (
                                                                      UriValueV1(
                                                                        stringFormatter.validateAndEscapeIri(
                                                                          apiRequest.uri_value.get,
                                                                          throw BadRequestException(
                                                                            s"Invalid URI: ${apiRequest.uri_value.get}"
                                                                          )
                                                                        )
                                                                      ),
                                                                      apiRequest.comment
                                                                    )
                                                                  )

                                                                case OntologyConstants.KnoraBase.DateValue =>
                                                                  Future(
                                                                    DateUtilV1.createJDNValueV1FromDateString(
                                                                      apiRequest.date_value.get
                                                                    ),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.ColorValue =>
                                                                  val colorValue = stringFormatter.validateColor(
                                                                    apiRequest.color_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid color value: ${apiRequest.color_value.get}"
                                                                    )
                                                                  )
                                                                  Future(ColorValueV1(colorValue), apiRequest.comment)

                                                                case OntologyConstants.KnoraBase.GeomValue =>
                                                                  val geometryValue = stringFormatter.validateGeometryString(
                                                                    apiRequest.geom_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid geometry value: ${apiRequest.geom_value.get}"
                                                                    )
                                                                  )
                                                                  Future(GeomValueV1(geometryValue), apiRequest.comment)

                                                                case OntologyConstants.KnoraBase.ListValue =>
                                                                  val listNodeIri = stringFormatter.validateAndEscapeIri(
                                                                    apiRequest.hlist_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid value IRI: ${apiRequest.hlist_value.get}"
                                                                    )
                                                                  )
                                                                  Future(
                                                                    HierarchicalListValueV1(listNodeIri),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.IntervalValue =>
                                                                  val timeVals: Seq[BigDecimal] =
                                                                    apiRequest.interval_value.get

                                                                  if (timeVals.length != 2)
                                                                    throw BadRequestException(
                                                                      "parameters for interval_value invalid"
                                                                    )

                                                                  Future(
                                                                    IntervalValueV1(timeVals.head, timeVals(1)),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.TimeValue =>
                                                                  val timeStamp
                                                                    : Instant = stringFormatter.xsdDateTimeStampToInstant(
                                                                    apiRequest.time_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid timestamp: ${apiRequest.time_value.get}"
                                                                    )
                                                                  )
                                                                  Future(TimeValueV1(timeStamp), apiRequest.comment)

                                                                case OntologyConstants.KnoraBase.GeonameValue =>
                                                                  Future(
                                                                    GeonameValueV1(apiRequest.geoname_value.get),
                                                                    apiRequest.comment
                                                                  )

                                                                case _ =>
                                                                  throw BadRequestException(s"No value submitted")
                                                              }
      } yield CreateValueRequestV1(
        resourceIri = resourceIri,
        propertyIri = propertyIri,
        value = value,
        comment = commentStr.map(str =>
          stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid comment: '$str'"))
        ),
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM,
        apiRequestID = UUID.randomUUID
      )
    }

    def makeAddValueVersionRequestMessage(
      valueIriStr: IRI,
      apiRequest: ChangeValueApiRequestV1,
      userADM: UserADM
    ): Future[ChangeValueRequestV1] = {
      val valueIri =
        stringFormatter.validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr"))

      for {
        (value: UpdateValueV1, commentStr: Option[String]) <- apiRequest.getValueClassIri match {

                                                                case OntologyConstants.KnoraBase.TextValue =>
                                                                  val richtext: CreateRichtextV1 =
                                                                    apiRequest.richtext_value.get

                                                                  // check if text has markup
                                                                  if (
                                                                    richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty
                                                                  ) {
                                                                    // simple text
                                                                    Future(
                                                                      (
                                                                        TextValueSimpleV1(
                                                                          stringFormatter.toSparqlEncodedString(
                                                                            richtext.utf8str.get,
                                                                            throw BadRequestException(
                                                                              s"Invalid text: '${richtext.utf8str.get}'"
                                                                            )
                                                                          ),
                                                                          richtext.language
                                                                        ),
                                                                        apiRequest.comment
                                                                      )
                                                                    )
                                                                  } else if (
                                                                    richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty
                                                                  ) {
                                                                    // XML: text with markup

                                                                    val mappingIri =
                                                                      stringFormatter.validateAndEscapeIri(
                                                                        richtext.mapping_id.get,
                                                                        throw BadRequestException(
                                                                          s"mapping_id ${richtext.mapping_id.get} is invalid"
                                                                        )
                                                                      )

                                                                    for {

                                                                      textWithStandoffTags: TextWithStandoffTagsV2 <-
                                                                        RouteUtilV1.convertXMLtoStandoffTagV1(
                                                                          xml = richtext.xml.get,
                                                                          mappingIri = mappingIri,
                                                                          acceptStandoffLinksToClientIDs = false,
                                                                          userProfile = userADM,
                                                                          featureFactoryConfig = featureFactoryConfig,
                                                                          settings = settings,
                                                                          appActor = appActor,
                                                                          log = log
                                                                        )

                                                                      // collect the resource references from the linking standoff nodes
                                                                      resourceReferences: Set[IRI] =
                                                                        stringFormatter.getResourceIrisFromStandoffTags(
                                                                          textWithStandoffTags.standoffTagV2
                                                                        )

                                                                    } yield (
                                                                      TextValueWithStandoffV1(
                                                                        utf8str = stringFormatter.toSparqlEncodedString(
                                                                          textWithStandoffTags.text,
                                                                          throw InconsistentRepositoryDataException(
                                                                            "utf8str for for TextValue contains invalid characters"
                                                                          )
                                                                        ),
                                                                        language = richtext.language,
                                                                        resource_reference = resourceReferences,
                                                                        standoff = textWithStandoffTags.standoffTagV2,
                                                                        mappingIri =
                                                                          textWithStandoffTags.mapping.mappingIri,
                                                                        mapping = textWithStandoffTags.mapping.mapping
                                                                      ),
                                                                      apiRequest.comment
                                                                    )

                                                                  } else {
                                                                    throw BadRequestException(
                                                                      "invalid parameters given for TextValueV1"
                                                                    )
                                                                  }

                                                                case OntologyConstants.KnoraBase.LinkValue =>
                                                                  val resourceIRI =
                                                                    stringFormatter.validateAndEscapeIri(
                                                                      apiRequest.link_value.get,
                                                                      throw BadRequestException(
                                                                        s"Invalid resource IRI: ${apiRequest.link_value.get}"
                                                                      )
                                                                    )
                                                                  Future(
                                                                    LinkUpdateV1(targetResourceIri = resourceIRI),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.IntValue =>
                                                                  Future(
                                                                    (
                                                                      IntegerValueV1(apiRequest.int_value.get),
                                                                      apiRequest.comment
                                                                    )
                                                                  )

                                                                case OntologyConstants.KnoraBase.DecimalValue =>
                                                                  Future(
                                                                    (
                                                                      DecimalValueV1(apiRequest.decimal_value.get),
                                                                      apiRequest.comment
                                                                    )
                                                                  )

                                                                case OntologyConstants.KnoraBase.BooleanValue =>
                                                                  Future(
                                                                    BooleanValueV1(apiRequest.boolean_value.get),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.UriValue =>
                                                                  Future(
                                                                    (
                                                                      UriValueV1(
                                                                        stringFormatter.validateAndEscapeIri(
                                                                          apiRequest.uri_value.get,
                                                                          throw BadRequestException(
                                                                            s"Invalid URI: ${apiRequest.uri_value.get}"
                                                                          )
                                                                        )
                                                                      ),
                                                                      apiRequest.comment
                                                                    )
                                                                  )

                                                                case OntologyConstants.KnoraBase.DateValue =>
                                                                  Future(
                                                                    DateUtilV1.createJDNValueV1FromDateString(
                                                                      apiRequest.date_value.get
                                                                    ),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.ColorValue =>
                                                                  val colorValue = stringFormatter.validateColor(
                                                                    apiRequest.color_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid color value: ${apiRequest.color_value.get}"
                                                                    )
                                                                  )
                                                                  Future(ColorValueV1(colorValue), apiRequest.comment)

                                                                case OntologyConstants.KnoraBase.GeomValue =>
                                                                  val geometryValue = stringFormatter.validateGeometryString(
                                                                    apiRequest.geom_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid geometry value: ${apiRequest.geom_value.get}"
                                                                    )
                                                                  )
                                                                  Future(GeomValueV1(geometryValue), apiRequest.comment)

                                                                case OntologyConstants.KnoraBase.ListValue =>
                                                                  val listNodeIri = stringFormatter.validateAndEscapeIri(
                                                                    apiRequest.hlist_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid value IRI: ${apiRequest.hlist_value.get}"
                                                                    )
                                                                  )
                                                                  Future(
                                                                    HierarchicalListValueV1(listNodeIri),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.IntervalValue =>
                                                                  val timeVals: Seq[BigDecimal] =
                                                                    apiRequest.interval_value.get

                                                                  if (timeVals.length != 2)
                                                                    throw BadRequestException(
                                                                      "parameters for interval_value invalid"
                                                                    )

                                                                  Future(
                                                                    IntervalValueV1(timeVals.head, timeVals(1)),
                                                                    apiRequest.comment
                                                                  )

                                                                case OntologyConstants.KnoraBase.TimeValue =>
                                                                  val timeStamp
                                                                    : Instant = stringFormatter.xsdDateTimeStampToInstant(
                                                                    apiRequest.time_value.get,
                                                                    throw BadRequestException(
                                                                      s"Invalid timestamp: ${apiRequest.time_value.get}"
                                                                    )
                                                                  )
                                                                  Future(TimeValueV1(timeStamp), apiRequest.comment)

                                                                case OntologyConstants.KnoraBase.GeonameValue =>
                                                                  Future(
                                                                    GeonameValueV1(apiRequest.geoname_value.get),
                                                                    apiRequest.comment
                                                                  )

                                                                case _ =>
                                                                  throw BadRequestException(s"No value submitted")
                                                              }
      } yield ChangeValueRequestV1(
        valueIri = valueIri,
        value = value,
        comment = commentStr.map(str =>
          stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid comment: '$str'"))
        ),
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM,
        apiRequestID = UUID.randomUUID
      )
    }

    def makeChangeCommentRequestMessage(
      valueIriStr: IRI,
      comment: Option[String],
      userADM: UserADM
    ): ChangeCommentRequestV1 =
      ChangeCommentRequestV1(
        valueIri = stringFormatter
          .validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
        comment = comment.map(str =>
          stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid comment: '$str'"))
        ),
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM,
        apiRequestID = UUID.randomUUID
      )

    def makeDeleteValueRequest(
      valueIriStr: IRI,
      deleteComment: Option[String],
      userADM: UserADM
    ): DeleteValueRequestV1 =
      DeleteValueRequestV1(
        valueIri = stringFormatter
          .validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
        deleteComment = deleteComment.map(comment =>
          stringFormatter.toSparqlEncodedString(comment, throw BadRequestException(s"Invalid comment: '$comment'"))
        ),
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM,
        apiRequestID = UUID.randomUUID
      )

    def makeGetValueRequest(valueIriStr: IRI, userADM: UserADM): ValueGetRequestV1 =
      ValueGetRequestV1(
        valueIri = stringFormatter
          .validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM
      )

    def makeChangeFileValueRequest(
      resIriStr: IRI,
      projectShortcode: String,
      apiRequest: ChangeFileValueApiRequestV1,
      userADM: UserADM
    ): Future[ChangeFileValueRequestV1] = {
      val resourceIri =
        stringFormatter.validateAndEscapeIri(resIriStr, throw BadRequestException(s"Invalid resource IRI: $resIriStr"))
      val tempFilePath = stringFormatter.makeSipiTempFilePath(settings, apiRequest.file)

      for {
        fileMetadataResponse: GetFileMetadataResponse <- appActor
                                                           .ask(
                                                             GetFileMetadataRequest(
                                                               filePath = tempFilePath,
                                                               requestingUser = userADM
                                                             )
                                                           )
                                                           .mapTo[GetFileMetadataResponse]
      } yield ChangeFileValueRequestV1(
        resourceIri = resourceIri,
        file = RouteUtilV1.makeFileValue(
          filename = apiRequest.file,
          fileMetadataResponse = fileMetadataResponse,
          projectShortcode = projectShortcode
        ),
        apiRequestID = UUID.randomUUID,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userADM
      )
    }

    // Version history request requires 3 URL path segments: resource IRI, property IRI, and current value IRI
    path("v1" / "values" / "history" / Segments) { iris =>
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
                       requestContext = requestContext,
                       featureFactoryConfig = featureFactoryConfig
                     )
        } yield makeVersionHistoryRequestMessage(iris = iris, userADM = userADM)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          appActor,
          log
        )
      }
    } ~ path("v1" / "values") {
      post {
        entity(as[CreateValueApiRequestV1]) { apiRequest => requestContext =>
          val requestMessageFuture = for {
            userADM <- getUserADM(
                         requestContext = requestContext,
                         featureFactoryConfig = featureFactoryConfig
                       )
            request <- makeCreateValueRequestMessage(apiRequest = apiRequest, userADM = userADM)
          } yield request

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessageFuture,
            requestContext,
            settings,
            appActor,
            log
          )
        }
      }
    } ~ path("v1" / "values" / Segment) { valueIriStr =>
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
                       requestContext = requestContext,
                       featureFactoryConfig = featureFactoryConfig
                     )
        } yield makeGetValueRequest(valueIriStr = valueIriStr, userADM = userADM)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          appActor,
          log
        )
      } ~ put {
        entity(as[ChangeValueApiRequestV1]) { apiRequest => requestContext =>
          // In API v1, you cannot change a value and its comment in a single request. So we know that here,
          // we are getting a request to change either the value or the comment, but not both.
          val requestMessageFuture = for {
            userADM <- getUserADM(
                         requestContext = requestContext,
                         featureFactoryConfig = featureFactoryConfig
                       )
            request <- apiRequest match {
                         case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, _, Some(comment)) =>
                           FastFuture.successful(
                             makeChangeCommentRequestMessage(
                               valueIriStr = valueIriStr,
                               comment = Some(comment),
                               userADM = userADM
                             )
                           )
                         case _ =>
                           makeAddValueVersionRequestMessage(
                             valueIriStr = valueIriStr,
                             apiRequest = apiRequest,
                             userADM = userADM
                           )
                       }
          } yield request

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessageFuture,
            requestContext,
            settings,
            appActor,
            log
          )
        }
      } ~ delete { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
                       requestContext = requestContext,
                       featureFactoryConfig = featureFactoryConfig
                     )
          params        = requestContext.request.uri.query().toMap
          deleteComment = params.get("deleteComment")
        } yield makeDeleteValueRequest(valueIriStr = valueIriStr, deleteComment = deleteComment, userADM = userADM)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          appActor,
          log
        )
      }
    } ~ path("v1" / "valuecomments" / Segment) { valueIriStr =>
      delete { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
                       requestContext = requestContext,
                       featureFactoryConfig = featureFactoryConfig
                     )
        } yield makeChangeCommentRequestMessage(valueIriStr = valueIriStr, comment = None, userADM = userADM)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          appActor,
          log
        )
      }
    } ~ path("v1" / "links" / Segments) { iris =>
      // Link value request requires 3 URL path segments: subject IRI, predicate IRI, and object IRI
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
                       requestContext = requestContext,
                       featureFactoryConfig = featureFactoryConfig
                     )
        } yield makeLinkValueGetRequestMessage(iris = iris, userADM = userADM)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          appActor,
          log
        )
      }
    } ~ path("v1" / "filevalue" / Segment) { resIriStr: IRI =>
      put {
        entity(as[ChangeFileValueApiRequestV1]) { apiRequest => requestContext =>
          val requestMessage = for {
            userADM <- getUserADM(
                         requestContext = requestContext,
                         featureFactoryConfig = featureFactoryConfig
                       )
            resourceIri = stringFormatter.validateAndEscapeIri(
                            resIriStr,
                            throw BadRequestException(s"Invalid resource IRI: $resIriStr")
                          )

            resourceInfoResponse <- appActor
                                      .ask(
                                        ResourceInfoGetRequestV1(
                                          iri = resourceIri,
                                          featureFactoryConfig = featureFactoryConfig,
                                          userProfile = userADM
                                        )
                                      )
                                      .mapTo[ResourceInfoResponseV1]

            projectShortcode = resourceInfoResponse.resource_info
                                 .getOrElse(throw NotFoundException(s"Resource not found: $resourceIri"))
                                 .project_shortcode

            request <- makeChangeFileValueRequest(
                         resIriStr = resIriStr,
                         projectShortcode = projectShortcode,
                         apiRequest = apiRequest,
                         userADM = userADM
                       )
          } yield request

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessage,
            requestContext,
            settings,
            appActor,
            log
          )
        }
      }
    }
  }
}
