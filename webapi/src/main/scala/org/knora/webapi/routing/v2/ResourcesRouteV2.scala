/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import zio.Exit.Failure
import zio.Exit.Success
import zio._
import zio.json._

import java.time.Instant

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.GraphRoute
import org.knora.webapi.config.Sipi
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.ValuesValidator.arkTimestampToInstant
import org.knora.webapi.messages.ValuesValidator.xsdDateTimeStampToInstant
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResourcesByProjectAndClassRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.ASC
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.Order
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.OrderBy
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoServiceLive.lastModificationDate
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

/**
 * Provides a routing function for API v2 routes that deal with resources.
 */
final case class ResourcesRouteV2(appConfig: AppConfig)(
  private implicit val runtime: Runtime[
    AppConfig with Authenticator with StringFormatter with IriConverter with MessageRelay with RestResourceInfoService
  ]
) extends LazyLogging {
  private val sipiConfig: Sipi             = appConfig.sipi
  private val resultsPerPage: Int          = appConfig.v2.resourcesSequence.resultsPerPage
  private val graphRouteConfig: GraphRoute = appConfig.v2.graphRoute

  private val resourcesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "resources")

  private val Text_Property          = "textProperty"
  private val Mapping_Iri            = "mappingIri"
  private val GravsearchTemplate_Iri = "gravsearchTemplateIri"
  private val TEIHeader_XSLT_IRI     = "teiHeaderXSLTIri"
  private val Depth                  = "depth"
  private val ExcludeProperty        = "excludeProperty"
  private val Direction              = "direction"
  private val Inbound                = "inbound"
  private val Outbound               = "outbound"
  private val Both                   = "both"

  def makeRoute: Route =
    getIIIFManifest() ~
      createResource() ~
      updateResourceMetadata() ~
      getResourcesInProject() ~
      getResourceHistory() ~
      getResourceHistoryEvents() ~
      getProjectResourceAndValueHistory() ~
      getResourcesInfo ~
      getResources() ~
      getResourcesPreview() ~
      getResourcesTei() ~
      getResourcesGraph() ~
      deleteResource() ~
      eraseResource()

  private def getIIIFManifest(): Route =
    path(resourcesBasePath / "iiifmanifest" / Segment) { resourceIriStr: IRI =>
      get { requestContext =>
        val requestTask = for {
          resourceIri <- StringFormatter
                           .validateAndEscapeIri(resourceIriStr)
                           .toZIO
                           .orElseFail(BadRequestException(s"Invalid resource IRI: $resourceIriStr"))
          user <- Authenticator.getUserADM(requestContext)
        } yield ResourceIIIFManifestGetRequestV2(resourceIri, user)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def createResource(): Route = path(resourcesBasePath) {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
            requestingUser <- Authenticator.getUserADM(requestContext)
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <- CreateResourceRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser)
            // check for each value which represents a file value if the file's MIME type is allowed
            _ <- checkMimeTypesForFileValueContents(requestMessage.createResource.flatValues)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }

  private def updateResourceMetadata(): Route = path(resourcesBasePath) {
    put {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture = for {
            requestDoc     <- ZIO.attempt(JsonLDUtil.parseJsonLD(jsonRequest))
            requestingUser <- Authenticator.getUserADM(requestContext)
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <- UpdateResourceMetadataRequestV2.fromJsonLD(requestDoc, requestingUser, apiRequestId)
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestMessageFuture, requestContext)
        }
      }
    }
  }

  private def getResourcesInProject(): Route = path(resourcesBasePath) {
    get { requestContext =>
      val params: Map[String, String] = requestContext.request.uri.query().toMap

      val getResourceClass = ZIO
        .fromOption(params.get("resourceClass"))
        .orElseFail(BadRequestException(s"This route requires the parameter 'resourceClass'"))
        .flatMap(iri =>
          IriConverter.asSmartIri(iri).orElseFail(BadRequestException(s"Invalid resource class IRI: $iri"))
        )
        .filterOrElseWith(it => it.isKnoraApiV2EntityIri && it.isApiV2ComplexSchema)(it =>
          ZIO.fail(BadRequestException(s"Invalid resource class IRI: $it"))
        )
        .flatMap(IriConverter.asInternalSmartIri)

      val getOrderByProperty: ZIO[IriConverter, Throwable, Option[SmartIri]] =
        ZIO.foreach(params.get("orderByProperty")) { orderByPropertyStr =>
          IriConverter
            .asSmartIri(orderByPropertyStr)
            .orElseFail(BadRequestException(s"Invalid property IRI: $orderByPropertyStr"))
            .filterOrFail(iri => iri.isKnoraApiV2EntityIri && iri.isApiV2ComplexSchema)(
              BadRequestException(s"Invalid property IRI: $orderByPropertyStr")
            )
            .flatMap(IriConverter.asInternalSmartIri)
        }

      val getPage = ZIO
        .fromOption(params.get("page"))
        .orElseFail(BadRequestException(s"This route requires the parameter 'page'"))
        .flatMap(pageStr =>
          ZIO
            .fromOption(ValuesValidator.validateInt(pageStr))
            .orElseFail(BadRequestException(s"Invalid page number: $pageStr"))
        )

      val getProjectIri = RouteUtilV2
        .getProjectIri(requestContext)
        .some
        .orElseFail(BadRequestException(s"This route requires the request header ${RouteUtilV2.PROJECT_HEADER}"))

      val targetSchemaTask = RouteUtilV2.getOntologySchema(requestContext)
      val requestTask = for {
        maybeOrderByProperty <- getOrderByProperty
        resourceClass        <- getResourceClass
        projectIri           <- getProjectIri
        page                 <- getPage
        targetSchema         <- targetSchemaTask
        schemaOptions        <- RouteUtilV2.getSchemaOptions(requestContext)
        requestingUser       <- Authenticator.getUserADM(requestContext)
      } yield SearchResourcesByProjectAndClassRequestV2(
        projectIri,
        resourceClass,
        maybeOrderByProperty,
        page,
        targetSchema,
        schemaOptions,
        requestingUser
      )

      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
    }
  }

  private def getResourceHistory(): Route =
    path(resourcesBasePath / "history" / Segment) { resourceIriStr: IRI =>
      get { requestContext =>
        val getResourceIri = StringFormatter
          .validateAndEscapeIri(resourceIriStr)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: $resourceIriStr"))
        val params = requestContext.request.uri.query().toMap
        val getStartDate =
          getInstantFromParams(params, "startDate", "start date", xsdDateTimeStampToInstant)
        val getEndDate =
          getInstantFromParams(params, "endDate", "end date", xsdDateTimeStampToInstant)
        val requestTask = for {
          resourceIri    <- getResourceIri
          startDate      <- getStartDate
          endDate        <- getEndDate
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield ResourceVersionHistoryGetRequestV2(
          resourceIri,
          withDeletedResource = false,
          startDate,
          endDate,
          requestingUser
        )
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getInstantFromParams(
    params: Map[String, String],
    key: String,
    name: String,
    dateParser: String => Option[Instant]
  ): IO[BadRequestException, Option[Instant]] =
    params
      .get(key)
      .map(dateStr =>
        ZIO
          .fromOption(dateParser(dateStr))
          .mapBoth(_ => BadRequestException(s"Invalid $name: $dateStr"), Some(_))
      )
      .getOrElse(ZIO.none)

  private def getResourceHistoryEvents(): Route =
    path(resourcesBasePath / "resourceHistoryEvents" / Segment) { resourceIri: IRI =>
      get { requestContext =>
        val requestTask = Authenticator
          .getUserADM(requestContext)
          .map(ResourceHistoryEventsGetRequestV2(resourceIri, _))
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getProjectResourceAndValueHistory(): Route =
    path(resourcesBasePath / "projectHistoryEvents" / Segment) { projectIri: IRI =>
      get { requestContext =>
        val requestTask =
          Authenticator.getUserADM(requestContext).map(ProjectResourcesWithHistoryGetRequestV2(projectIri, _))
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getQueryParamsMap(requestContext: RequestContext): Map[String, String] =
    requestContext.request.uri.query().toMap

  private def getStringQueryParam(requestContext: RequestContext, key: String): Option[String] =
    getQueryParamsMap(requestContext).get(key)

  private def unsafeRunZioAndMapJsonResponse[R, E, A](
    zioAction: ZIO[R, E, A]
  )(implicit r: Runtime[R], encoder: JsonEncoder[A]) =
    unsafeRunZio(zioAction) match {
      case Failure(cause) => logger.error(cause.prettyPrint); HttpResponse(InternalServerError)
      case Success(dto)   => HttpResponse(status = OK, entity = HttpEntity(`application/json`, dto.toJson))
    }

  private def unsafeRunZio[R, E, A](zioAction: ZIO[R, E, A])(implicit r: Runtime[R]): Exit[E, A] =
    Unsafe.unsafe(implicit u => r.unsafe.run(zioAction))

  private def getResourcesInfo: Route = path(resourcesBasePath / "info") {
    get { ctx =>
      val getResourceClassIri = ZIO
        .fromOption(getStringQueryParam(ctx, "resourceClass"))
        .orElseFail(BadRequestException(s"This route requires the parameter 'resourceClass'"))
      val getOrderBy: ZIO[Any, BadRequestException, OrderBy] = getStringQueryParam(ctx, "orderBy") match {
        case None => ZIO.succeed(lastModificationDate)
        case Some(s) =>
          ZIO.fromOption(OrderBy.make(s)).orElseFail(BadRequestException(s"Invalid value '$s', for orderBy"))
      }
      val getOrder: IO[BadRequestException, Order] = getStringQueryParam(ctx, "order") match {
        case None => ZIO.succeed(ASC)
        case Some(s) =>
          ZIO.fromOption(Order.make(s)).orElseFail(BadRequestException(s"Invalid value '$s', for order"))
      }
      val action = for {
        resourceClassIri <- getResourceClassIri
        orderBy          <- getOrderBy
        order            <- getOrder
        projectIri       <- RouteUtilV2.getRequiredProjectIri(ctx)
        result <-
          RestResourceInfoService.findByProjectAndResourceClass(projectIri.toIri, resourceClassIri, (orderBy, order))
      } yield result
      ctx.complete(unsafeRunZioAndMapJsonResponse(action))
    }
  }
  private def getResources(): Route = path(resourcesBasePath / Segments) { resIris: Seq[String] =>
    get { requestContext =>
      val targetSchemaTask      = RouteUtilV2.getOntologySchema(requestContext)
      val schemaOptionsTask     = RouteUtilV2.getSchemaOptions(requestContext)
      val params: Map[IRI, IRI] = requestContext.request.uri.query().toMap
      val versionDateParser     = (s: String) => xsdDateTimeStampToInstant(s).orElse(arkTimestampToInstant(s))
      val requestTask = for {
        resourceIris   <- getResourceIris(resIris)
        versionDate    <- getInstantFromParams(params, "version", "version date", versionDateParser)
        targetSchema   <- targetSchemaTask
        requestingUser <- Authenticator.getUserADM(requestContext)
        schemaOptions  <- schemaOptionsTask
      } yield ResourcesGetRequestV2(
        resourceIris,
        versionDate = versionDate,
        targetSchema = targetSchema,
        schemaOptions = schemaOptions,
        requestingUser = requestingUser
      )
      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask, schemaOptionsTask.map(Some(_)))
    }
  }

  private def getResourceIris(resIris: Seq[IRI]): IO[BadRequestException, Seq[IRI]] =
    ZIO
      .fail(BadRequestException(s"List of provided resource Iris exceeds limit of $resultsPerPage"))
      .when(resIris.size > resultsPerPage) *>
      ZIO.foreach(resIris) { resIri: IRI =>
        StringFormatter
          .validateAndEscapeIri(resIri)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: <$resIri>"))
      }

  private def getResourcesPreview(): Route =
    path("v2" / "resourcespreview" / Segments) { resIris: Seq[String] =>
      get { requestContext =>
        val targetSchemaTask = RouteUtilV2.getOntologySchema(requestContext)
        val requestTask = for {
          resourceIris <- getResourceIris(resIris)
          targetSchema <- targetSchemaTask
          user         <- Authenticator.getUserADM(requestContext)
        } yield ResourcesPreviewGetRequestV2(resourceIris, withDeletedResource = true, targetSchema, user)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
      }
    }

  private def getResourcesTei(): Route = path("v2" / "tei" / Segment) { resIri: String =>
    get { requestContext =>
      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val getResourceIri =
        StringFormatter
          .validateAndEscapeIri(resIri)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: <$resIri>"))
      val requestTask = for {
        resourceIri           <- getResourceIri
        mappingIri            <- getMappingIriFromParams(params)
        textProperty          <- getTextPropertyFromParams(params)
        gravsearchTemplateIri <- getGravsearchTemplateIriFromParams(params)
        headerXSLTIri         <- getHeaderXSLTIriFromParams(params)
        user                  <- Authenticator.getUserADM(requestContext)
      } yield ResourceTEIGetRequestV2(resourceIri, textProperty, mappingIri, gravsearchTemplateIri, headerXSLTIri, user)
      RouteUtilV2.runTEIXMLRoute(requestTask, requestContext)
    }
  }

  private def getResourcesGraph(): Route = path("v2" / "graph" / Segment) { resIriStr: String =>
    get { requestContext =>
      val getResourceIri =
        StringFormatter
          .validateAndEscapeIri(resIriStr)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: <$resIriStr>"))
      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val getDepth: IO[BadRequestException, Int] =
        ZIO
          .succeed(params.get(Depth).flatMap(ValuesValidator.validateInt).getOrElse(graphRouteConfig.defaultGraphDepth))
          .filterOrFail(_ >= 1)(BadRequestException(s"$Depth must be at least 1"))
          .filterOrFail(_ <= graphRouteConfig.maxGraphDepth)(
            BadRequestException(s"$Depth cannot be greater than ${graphRouteConfig.maxGraphDepth}")
          )

      val getExcludeProperty: ZIO[IriConverter, BadRequestException, Option[SmartIri]] = params
        .get(ExcludeProperty)
        .map(propIriStr =>
          IriConverter
            .asSmartIri(propIriStr)
            .mapBoth(_ => BadRequestException(s"Invalid property IRI: <$propIriStr>"), Some(_))
        )
        .getOrElse(ZIO.none)

      val getInboundOutbound: IO[BadRequestException, (Boolean, Boolean)] =
        params.getOrElse(Direction, Outbound) match {
          case Inbound  => ZIO.succeed((true, false))
          case Outbound => ZIO.succeed((false, true))
          case Both     => ZIO.succeed((true, true))
          case other    => ZIO.fail(BadRequestException(s"Invalid direction: $other"))
        }

      val requestTask = for {
        resourceIri        <- getResourceIri
        depth              <- getDepth
        excludeProperty    <- getExcludeProperty
        t                  <- getInboundOutbound
        (inbound, outbound) = t
        requestingUser     <- Authenticator.getUserADM(requestContext)
      } yield GraphDataGetRequestV2(resourceIri, depth, inbound, outbound, excludeProperty, requestingUser)
      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, RouteUtilV2.getOntologySchema(requestContext))
    }
  }

  private def deleteResource(): Route = path(resourcesBasePath / "delete") {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestDoc     <- ZIO.attempt(JsonLDUtil.parseJsonLD(jsonRequest))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestingUser <- Authenticator.getUserADM(requestContext)
            msg            <- DeleteOrEraseResourceRequestV2.fromJsonLD(requestDoc, requestingUser, apiRequestId)
          } yield msg
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }

  private def eraseResource(): Route = path(resourcesBasePath / "erase") {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestDoc     <- ZIO.attempt(JsonLDUtil.parseJsonLD(jsonRequest))
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestingUser <- Authenticator.getUserADM(requestContext)
            requestMessage <- DeleteOrEraseResourceRequestV2.fromJsonLD(requestDoc, requestingUser, apiRequestId)
          } yield requestMessage.copy(erase = true)
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }

  /**
   * Gets the Iri of the property that represents the text of the resource.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getTextPropertyFromParams(params: Map[String, String]): ZIO[IriConverter, Throwable, SmartIri] =
    ZIO
      .fromOption(params.get(Text_Property))
      .orElseFail(BadRequestException(s"param $Text_Property not set"))
      .flatMap { textPropIriStr =>
        IriConverter
          .asSmartIri(textPropIriStr)
          .orElseFail(BadRequestException(s"Invalid property IRI: <$textPropIriStr>"))
          .filterOrFail(_.isKnoraApiV2EntityIri)(
            BadRequestException(s"<$textPropIriStr> is not a valid knora-api property IRI")
          )
          .mapAttempt(_.toOntologySchema(InternalSchema))
      }

  /**
   * Gets the Iri of the mapping to be used to convert standoff to XML.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getMappingIriFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(Mapping_Iri)
      .map { mapping =>
        StringFormatter
          .validateAndEscapeIri(mapping)
          .toZIO
          .mapBoth(_ => BadRequestException(s"Invalid mapping IRI: <$mapping>"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Gets the Iri of Gravsearch template to be used to query for the resource's metadata.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getGravsearchTemplateIriFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(GravsearchTemplate_Iri)
      .map { gravsearch =>
        StringFormatter
          .validateAndEscapeIri(gravsearch)
          .toZIO
          .mapBoth(_ => BadRequestException(s"Invalid template IRI: <$gravsearch>"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Gets the Iri of the XSL transformation to be used to convert the TEI header's metadata.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getHeaderXSLTIriFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(TEIHeader_XSLT_IRI)
      .map { xslt =>
        StringFormatter
          .validateAndEscapeIri(xslt)
          .toZIO
          .mapBoth(_ => BadRequestException(s"Invalid XSLT IRI: <$xslt>"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Checks if the MIME types of the given values are allowed by the configuration
   *
   * @param values the values to be checked.
   */
  private def checkMimeTypesForFileValueContents(
    values: Iterable[CreateValueInNewResourceV2]
  ): Task[Unit] = {
    def failBadRequest(fileValueContent: FileValueContentV2): IO[BadRequestException, Unit] = {
      val msg =
        s"File ${fileValueContent.fileValue.internalFilename} has MIME type ${fileValueContent.fileValue.internalMimeType}, which is not supported for still image files"
      ZIO.fail(BadRequestException(msg))
    }
    ZIO
      .foreach(values) { value =>
        value.valueContent match {
          case fileValueContent: StillImageFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.imageMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: DocumentFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.documentMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: ArchiveFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.archiveMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: TextFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.textMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: AudioFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.audioMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: MovingImageFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.videoMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case _ => ZIO.unit
        }
      }
      .unit
  }
}
