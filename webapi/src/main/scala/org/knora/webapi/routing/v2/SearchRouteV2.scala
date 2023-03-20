/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV2

/**
 * Provides a function for API routes that deal with search.
 */
final case class SearchRouteV2(
  private val routeData: KnoraRouteData,
  override protected val runtime: zio.Runtime[Authenticator]
) extends KnoraRoute(routeData, runtime) {

  private val LIMIT_TO_PROJECT        = "limitToProject"
  private val LIMIT_TO_RESOURCE_CLASS = "limitToResourceClass"
  private val OFFSET                  = "offset"
  private val LIMIT_TO_STANDOFF_CLASS = "limitToStandoffClass"
  private val RETURN_FILES            = "returnFiles"

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    fullTextSearchCount() ~
      fullTextSearch() ~
      gravsearchCountGet() ~
      gravsearchCountPost() ~
      gravsearchGet() ~
      gravsearchPost() ~
      searchByLabelCount() ~
      searchByLabel()

  /**
   * Gets the requested offset. Returns zero if no offset is indicated.
   *
   * @param params the GET parameters.
   * @return the offset to be used for paging.
   */
  private def getOffsetFromParams(params: Map[String, String]): Int = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    val offsetStr                                 = params.get(OFFSET)

    offsetStr match {
      case Some(offset: String) =>
        val offsetInt: Int = stringFormatter.validateInt(
          offset,
          throw BadRequestException(s"offset is expected to be an Integer, but $offset given")
        )

        if (offsetInt < 0) throw BadRequestException(s"offset must be an Integer >= 0, but $offsetInt given.")

        offsetInt

      case None => 0
    }
  }

  /**
   * Gets the the project the search should be restricted to, if any.
   *
   * @param params the GET parameters.
   * @return the project Iri, if any.
   */
  private def getProjectFromParams(params: Map[String, String]): Option[IRI] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    val limitToProjectIriStr                      = params.get(LIMIT_TO_PROJECT)

    val limitToProjectIri: Option[IRI] = limitToProjectIriStr match {

      case Some(projectIriStr: String) =>
        val projectIri = stringFormatter.validateAndEscapeIri(
          projectIriStr,
          throw BadRequestException(s"$projectIriStr is not a valid Iri")
        )

        Some(projectIri)

      case None => None

    }

    limitToProjectIri

  }

  /**
   * Gets the resource class the search should be restricted to, if any.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getResourceClassFromParams(params: Map[String, String]): Option[SmartIri] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    val limitToResourceClassIriStr                = params.get(LIMIT_TO_RESOURCE_CLASS)

    limitToResourceClassIriStr match {
      case Some(resourceClassIriStr: String) =>
        val externalResourceClassIri = resourceClassIriStr.toSmartIriWithErr(
          throw BadRequestException(s"Invalid resource class IRI: $resourceClassIriStr")
        )

        if (!externalResourceClassIri.isKnoraApiV2EntityIri) {
          throw BadRequestException(s"$resourceClassIriStr is not a valid knora-api resource class IRI")
        }

        Some(externalResourceClassIri.toOntologySchema(InternalSchema))

      case None => None
    }
  }

  /**
   * Gets the standoff class the search should be restricted to.
   *
   * @param params the GET parameters.
   * @return the internal standoff class, if any.
   */
  private def getStandoffClass(params: Map[String, String]): Option[SmartIri] = {
    implicit val stringFormatter: StringFormatter  = StringFormatter.getGeneralInstance
    val limitToStandoffClassIriStr: Option[String] = params.get(LIMIT_TO_STANDOFF_CLASS)

    limitToStandoffClassIriStr match {
      case Some(standoffClassIriStr: String) =>
        val externalStandoffClassIri = standoffClassIriStr.toSmartIriWithErr(
          throw BadRequestException(s"Invalid standoff class IRI: $limitToStandoffClassIriStr")
        )

        if (!externalStandoffClassIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"$externalStandoffClassIri is not a valid standoff class IRI")
        }

        Some(externalStandoffClassIri.toOntologySchema(InternalSchema))

      case None => None
    }
  }

  private def fullTextSearchCount(): Route =
    path("v2" / "search" / "count" / Segment) {
      searchStr => // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          if (searchStr.contains(OntologyConstants.KnoraApi.ApiOntologyHostname)) {
            throw BadRequestException(
              "It looks like you are submitting a Gravsearch request to a full-text search route"
            )
          }

          val escapedSearchStr =
            stringFormatter.toSparqlEncodedString(
              searchStr,
              throw BadRequestException(s"Invalid search string: '$searchStr'")
            )

          if (escapedSearchStr.length < routeData.appConfig.v2.fulltextSearch.searchValueMinLength) {
            throw BadRequestException(
              s"A search value is expected to have at least length of ${routeData.appConfig.v2.fulltextSearch.searchValueMinLength}, but '$escapedSearchStr' given of length ${escapedSearchStr.length}."
            )
          }

          val params: Map[String, String] = requestContext.request.uri.query().toMap

          val limitToProject: Option[IRI] = getProjectFromParams(params)

          val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

          val limitToStandoffClass: Option[SmartIri] = getStandoffClass(params)

          val requestMessage: Future[FullTextSearchCountRequestV2] = for {
            requestingUser <- getUserADM(requestContext)
          } yield FullTextSearchCountRequestV2(
            searchValue = escapedSearchStr,
            limitToProject = limitToProject,
            limitToResourceClass = limitToResourceClass,
            limitToStandoffClass = limitToStandoffClass,
            requestingUser = requestingUser
          )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appConfig = routeData.appConfig,
            appActor = appActor,
            log = log,
            targetSchema = RouteUtilV2.getOntologySchema(requestContext),
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
    }

  private def fullTextSearch(): Route = path("v2" / "search" / Segment) {
    searchStr => // TODO: if a space is encoded as a "+", this is not converted back to a space
      get { requestContext =>
        if (searchStr.contains(OntologyConstants.KnoraApi.ApiOntologyHostname)) {
          throw BadRequestException("It looks like you are submitting a Gravsearch request to a full-text search route")
        }

        val escapedSearchStr =
          stringFormatter.toSparqlEncodedString(
            searchStr,
            throw BadRequestException(s"Invalid search string: '$searchStr'")
          )

        if (escapedSearchStr.length < routeData.appConfig.v2.fulltextSearch.searchValueMinLength) {
          throw BadRequestException(
            s"A search value is expected to have at least length of ${routeData.appConfig.v2.fulltextSearch.searchValueMinLength}, but '$escapedSearchStr' given of length ${escapedSearchStr.length}."
          )
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap

        val offset = getOffsetFromParams(params)

        val limitToProject: Option[IRI] = getProjectFromParams(params)

        val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

        val limitToStandoffClass: Option[SmartIri] = getStandoffClass(params)

        val returnFiles: Boolean = stringFormatter.optionStringToBoolean(
          params.get(RETURN_FILES),
          throw BadRequestException(s"Invalid boolean value for '$RETURN_FILES'")
        )

        val targetSchema: ApiV2Schema        = RouteUtilV2.getOntologySchema(requestContext)
        val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptions(requestContext)

        val requestMessage: Future[FulltextSearchRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
        } yield FulltextSearchRequestV2(
          searchValue = escapedSearchStr,
          offset = offset,
          limitToProject = limitToProject,
          limitToResourceClass = limitToResourceClass,
          limitToStandoffClass = limitToStandoffClass,
          returnFiles = returnFiles,
          requestingUser = requestingUser,
          targetSchema = targetSchema,
          schemaOptions = schemaOptions
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = schemaOptions
        )
      }
  }

  private def gravsearchCountGet(): Route =
    path("v2" / "searchextended" / "count" / Segment) {
      gravsearchQuery => // Segment is a URL encoded string representing a Gravsearch query
        get { requestContext =>
          val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)

          val requestMessage: Future[GravsearchCountRequestV2] = for {
            requestingUser <- getUserADM(requestContext)
          } yield GravsearchCountRequestV2(
            constructQuery = constructQuery,
            requestingUser = requestingUser
          )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appConfig = routeData.appConfig,
            appActor = appActor,
            log = log,
            targetSchema = RouteUtilV2.getOntologySchema(requestContext),
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
    }

  private def gravsearchCountPost(): Route =
    path("v2" / "searchextended" / "count") {
      post {
        entity(as[String]) { gravsearchQuery => requestContext =>
          {
            val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)
            val requestMessage: Future[GravsearchCountRequestV2] = for {
              requestingUser <- getUserADM(requestContext)
            } yield GravsearchCountRequestV2(
              constructQuery = constructQuery,
              requestingUser = requestingUser
            )

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessage,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = RouteUtilV2.getOntologySchema(requestContext),
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def gravsearchGet(): Route = path(
    "v2" / "searchextended" / Segment
  ) { sparql => // Segment is a URL encoded string representing a Gravsearch query
    get { requestContext =>
      val constructQuery                   = GravsearchParser.parseQuery(sparql)
      val targetSchema: ApiV2Schema        = RouteUtilV2.getOntologySchema(requestContext)
      val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptions(requestContext)

      val requestMessage: Future[GravsearchRequestV2] = for {
        requestingUser <- getUserADM(requestContext)
      } yield GravsearchRequestV2(
        constructQuery = constructQuery,
        targetSchema = targetSchema,
        schemaOptions = schemaOptions,
        requestingUser = requestingUser
      )

      RouteUtilV2.runRdfRouteWithFuture(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        appConfig = routeData.appConfig,
        appActor = appActor,
        log = log,
        targetSchema = targetSchema,
        schemaOptions = schemaOptions
      )
    }
  }

  private def gravsearchPost(): Route = path("v2" / "searchextended") {
    post {
      entity(as[String]) { gravsearchQuery => requestContext =>
        {
          val constructQuery                   = GravsearchParser.parseQuery(gravsearchQuery)
          val targetSchema: ApiV2Schema        = RouteUtilV2.getOntologySchema(requestContext)
          val schemaOptions: Set[SchemaOption] = RouteUtilV2.getSchemaOptions(requestContext)

          val requestMessage: Future[GravsearchRequestV2] = for {
            requestingUser <- getUserADM(requestContext)
          } yield GravsearchRequestV2(
            constructQuery = constructQuery,
            targetSchema = targetSchema,
            schemaOptions = schemaOptions,
            requestingUser = requestingUser
          )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appConfig = routeData.appConfig,
            appActor = appActor,
            log = log,
            targetSchema = targetSchema,
            schemaOptions = schemaOptions
          )
        }
      }
    }
  }

  private def searchByLabelCount(): Route =
    path("v2" / "searchbylabel" / "count" / Segment) {
      searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          val searchString =
            stringFormatter.toSparqlEncodedString(
              searchval,
              throw BadRequestException(s"Invalid search string: '$searchval'")
            )

          if (searchString.length < routeData.appConfig.v2.fulltextSearch.searchValueMinLength) {
            throw BadRequestException(
              s"A search value is expected to have at least length of ${routeData.appConfig.v2.fulltextSearch.searchValueMinLength}, but '$searchString' given of length ${searchString.length}."
            )
          }

          val params: Map[String, String] = requestContext.request.uri.query().toMap

          val limitToProject: Option[IRI] = getProjectFromParams(params)

          val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

          val requestMessage: Future[SearchResourceByLabelCountRequestV2] = for {
            requestingUser <- getUserADM(requestContext)
          } yield SearchResourceByLabelCountRequestV2(
            searchValue = searchString,
            limitToProject = limitToProject,
            limitToResourceClass = limitToResourceClass,
            requestingUser = requestingUser
          )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appConfig = routeData.appConfig,
            appActor = appActor,
            log = log,
            targetSchema = RouteUtilV2.getOntologySchema(requestContext),
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
    }

  private def searchByLabel(): Route = path(
    "v2" / "searchbylabel" / Segment
  ) { searchval =>
    get { requestContext =>
      val sparqlEncodedSearchString =
        stringFormatter.toSparqlEncodedString(
          searchval,
          throw BadRequestException(s"Invalid search string: '$searchval'")
        )

      if (sparqlEncodedSearchString.length < routeData.appConfig.v2.fulltextSearch.searchValueMinLength) {
        throw BadRequestException(
          s"A search value is expected to have at least length of ${routeData.appConfig.v2.fulltextSearch.searchValueMinLength}, but '$sparqlEncodedSearchString' given of length ${sparqlEncodedSearchString.length}."
        )
      }

      val params: Map[String, String] = requestContext.request.uri.query().toMap

      val offset = getOffsetFromParams(params)

      val limitToProject: Option[IRI] = getProjectFromParams(params)

      val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

      val targetSchema: ApiV2Schema = RouteUtilV2.getOntologySchema(requestContext)

      val requestMessage: Future[SearchResourceByLabelRequestV2] = for {
        requestingUser <- getUserADM(requestContext)
      } yield SearchResourceByLabelRequestV2(
        searchValue = sparqlEncodedSearchString,
        offset = offset,
        limitToProject = limitToProject,
        limitToResourceClass = limitToResourceClass,
        targetSchema = targetSchema,
        requestingUser = requestingUser
      )

      RouteUtilV2.runRdfRouteWithFuture(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        appConfig = routeData.appConfig,
        appActor = appActor,
        log = log,
        targetSchema = RouteUtilV2.getOntologySchema(requestContext),
        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
      )
    }
  }

}
