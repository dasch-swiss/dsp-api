/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.*


import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

/**
 * Provides a function for API routes that deal with search.
 */
final case class SearchRouteV2(searchValueMinLength: Int)(
  private implicit val runtime: Runtime[AppConfig & Authenticator & IriConverter & SearchResponderV2 & MessageRelay]
) {

  private val LIMIT_TO_PROJECT        = "limitToProject"
  private val LIMIT_TO_RESOURCE_CLASS = "limitToResourceClass"
  private val OFFSET                  = "offset"
  private val LIMIT_TO_STANDOFF_CLASS = "limitToStandoffClass"
  private val RETURN_FILES            = "returnFiles"

  def makeRoute: Route =
    fullTextSearchCount() ~
      fullTextSearch() ~
      searchByLabelCount() ~
      searchByLabel()

  /**
   * Gets the requested offset. Returns zero if no offset is indicated.
   *
   * @param params the GET parameters.
   * @return the offset to be used for paging.
   */
  private def getOffsetFromParams(params: Map[String, String]): IO[BadRequestException, Int] =
    params
      .get(OFFSET)
      .map { offsetStr =>
        ZIO
          .fromOption(ValuesValidator.validateInt(offsetStr))
          .orElseFail(BadRequestException(s"offset is expected to be an Integer, but $offsetStr given"))
          .filterOrFail(_ >= 0)(
            BadRequestException(s"offset is expected to be a positive Integer, but $offsetStr given")
          )
      }
      .getOrElse(ZIO.succeed(0))

  private def getProjectIri(params: Map[String, String]): IO[BadRequestException, Option[ProjectIri]] =
    ZIO
      .fromOption(params.get(LIMIT_TO_PROJECT))
      .flatMap(iri => ProjectIri.from(iri).toZIO.orElseFail(Some(BadRequestException(s"$iri is not a valid Iri"))))
      .unsome

  /**
   * Gets the resource class the search should be restricted to, if any.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getResourceClassFromParams(
    params: Map[String, String]
  ): ZIO[IriConverter, BadRequestException, Option[SmartIri]] =
    params
      .get(LIMIT_TO_RESOURCE_CLASS)
      .map { resourceClassIriStr =>
        IriConverter
          .asSmartIri(resourceClassIriStr)
          .orElseFail(BadRequestException(s"Invalid resource class IRI: $resourceClassIriStr"))
          .filterOrFail(_.isKnoraApiV2EntityIri)(
            BadRequestException(s"$resourceClassIriStr is not a valid knora-api resource class IRI")
          )
          .map(asSomeIriWithInternalSchema)
      }
      .getOrElse(ZIO.none)

  private def asSomeIriWithInternalSchema(iri: SmartIri): Some[SmartIri] = Some(iri.toOntologySchema(InternalSchema))

  /**
   * Gets the standoff class the search should be restricted to.
   *
   * @param params the GET parameters.
   * @return the internal standoff class, if any.
   */
  private def getStandoffClass(params: Map[String, String]): ZIO[IriConverter, BadRequestException, Option[SmartIri]] =
    params
      .get(LIMIT_TO_STANDOFF_CLASS)
      .map { standoffClassIriStr =>
        IriConverter
          .asSmartIri(standoffClassIriStr)
          .orElseFail(BadRequestException(s"Invalid standoff class IRI: $standoffClassIriStr"))
          .filterOrFail(_.isApiV2ComplexSchema)(
            BadRequestException(s"$standoffClassIriStr is not a valid knora-api standoff class IRI")
          )
          .map(asSomeIriWithInternalSchema)
      }
      .getOrElse(ZIO.none)

  private def fullTextSearchCount(): Route =
    path("v2" / "search" / "count" / Segment) {
      searchStr => // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          val params: Map[String, String] = requestContext.request.uri.query().toMap
          val response = for {
            _                    <- ensureIsNotFullTextSearch(searchStr)
            escapedSearchStr     <- validateSearchString(searchStr)
            limitToProject       <- getProjectIri(params)
            limitToResourceClass <- getResourceClassFromParams(params)
            limitToStandoffClass <- getStandoffClass(params)
            response <- SearchResponderV2.fulltextSearchCountV2(
                          escapedSearchStr,
                          limitToProject,
                          limitToResourceClass,
                          limitToStandoffClass
                        )
          } yield response
          RouteUtilV2.completeResponse(response, requestContext)
        }
    }

  private def validateSearchString(searchStr: String) =
    ZIO
      .fromOption(Iri.toSparqlEncodedString(searchStr))
      .orElseFail(throw BadRequestException(s"Invalid search string: '$searchStr'"))
      .filterOrElseWith(_.length >= searchValueMinLength) { it =>
        val errorMsg =
          s"A search value is expected to have at least length of $searchValueMinLength, but '$it' given of length ${it.length}."
        ZIO.fail(BadRequestException(errorMsg))
      }

  private def ensureIsNotFullTextSearch(searchStr: String) =
    ZIO
      .fail(
        BadRequestException(
          "It looks like you are submitting a Gravsearch request to a full-text search route"
        )
      )
      .when(searchStr.contains(OntologyConstants.KnoraApi.ApiOntologyHostname))

  private def fullTextSearch(): Route = path("v2" / "search" / Segment) {
    searchStr => // TODO: if a space is encoded as a "+", this is not converted back to a space
      get { requestContext =>
        val targetSchemaTask  = RouteUtilV2.getOntologySchema(requestContext)
        val schemaOptionsTask = RouteUtilV2.getSchemaOptions(requestContext)

        val params: Map[String, String] = requestContext.request.uri.query().toMap
        val requestTask = for {
          _                    <- ensureIsNotFullTextSearch(searchStr)
          escapedSearchStr     <- validateSearchString(searchStr)
          offset               <- getOffsetFromParams(params)
          limitToProject       <- getProjectIri(params)
          limitToResourceClass <- getResourceClassFromParams(params)
          limitToStandoffClass <- getStandoffClass(params)
          returnFiles           = ValuesValidator.optionStringToBoolean(params.get(RETURN_FILES), fallback = false)
          requestingUser       <- Authenticator.getUserADM(requestContext)
          schemaAndOptions     <- targetSchemaTask.zip(schemaOptionsTask).map { case (s, o) => SchemaRendering(s, o) }
          response <- SearchResponderV2.fulltextSearchV2(
                        escapedSearchStr,
                        offset,
                        limitToProject,
                        limitToResourceClass,
                        limitToStandoffClass,
                        returnFiles,
                        schemaAndOptions,
                        requestingUser
                      )
        } yield response
        RouteUtilV2.completeResponse(requestTask, requestContext, targetSchemaTask, schemaOptionsTask.map(Some(_)))
      }
  }

  private def searchByLabelCount(): Route =
    path("v2" / "searchbylabel" / "count" / Segment) {
      searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          val params: Map[String, String] = requestContext.request.uri.query().toMap
          val response = for {
            searchString         <- validateSearchString(searchval)
            limitToProject       <- getProjectIri(params)
            limitToResourceClass <- getResourceClassFromParams(params)
            response <-
              SearchResponderV2.searchResourcesByLabelCountV2(searchString, limitToProject, limitToResourceClass)
          } yield response
          RouteUtilV2.completeResponse(response, requestContext, RouteUtilV2.getOntologySchema(requestContext))
        }
    }

  private def searchByLabel(): Route = path(
    "v2" / "searchbylabel" / Segment
  ) { searchval =>
    get { requestContext =>
      val targetSchemaTask            = RouteUtilV2.getOntologySchema(requestContext)
      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val response = for {
        sparqlEncodedSearchString <- validateSearchString(searchval)
        offset                    <- getOffsetFromParams(params)
        limitToProject            <- getProjectIri(params)
        limitToResourceClass      <- getResourceClassFromParams(params)
        targetSchema              <- targetSchemaTask
        requestingUser            <- Authenticator.getUserADM(requestContext)
        response <- SearchResponderV2.searchResourcesByLabelV2(
                      searchValue = sparqlEncodedSearchString,
                      offset = offset,
                      limitToProject = limitToProject,
                      limitToResourceClass = limitToResourceClass,
                      targetSchema = targetSchema,
                      requestingUser = requestingUser
                    )
      } yield response
      RouteUtilV2.completeResponse(response, requestContext, targetSchemaTask)
    }
  }
}
