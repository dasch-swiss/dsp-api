/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import scala.language.postfixOps

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.searchmessages.ExtendedSearchGetRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.FulltextSearchGetRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchComparisonOperatorV1
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1

// slash after path without following segment

/**
 * Provides a spray-routing function for API routes that deal with search.
 */
final case class SearchRouteV1()(
  private implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) {

  def makeRoute: Route =
    path("v1" / "search" /) {
      // in the original API, there is a slash after "search": "http://www.salsah.org/api/search/?searchtype=extended"
      get { requestContext =>
        val requestTask = for {
          user  <- Authenticator.getUserADM(requestContext)
          params = requestContext.request.uri.query().toMultiMap
          msg   <- makeExtendedSearchRequestMessage(user, params)
        } yield msg
        RouteUtilV1.runJsonRouteZ(requestTask, requestContext)
      }
    } ~
      path("v1" / "search" / Segment) { searchval =>
        // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          val requestTask = for {
            user  <- Authenticator.getUserADM(requestContext)
            params = requestContext.request.uri.query().toMap
          } yield makeFulltextSearchRequestMessage(user, searchval, params)
          RouteUtilV1.runJsonRouteZ(requestTask, requestContext)
        }
      }

  /**
   * The default number of rows to show in search results.
   */
  private val defaultShowNRows = 25

  private def makeExtendedSearchRequestMessage(
    userADM: UserADM,
    reverseParams: Map[String, Seq[String]]
  ): ZIO[StringFormatter, Throwable, ExtendedSearchGetRequestV1] = {

    // Spray returns the parameters in reverse order, so reverse them before processing, because the JavaScript GUI expects the order to be preserved.
    val params = reverseParams.map { case (key, value) => key -> value.reverse }

    // only one value is expected
    val filterByResType: Option[String] = params.get("filter_by_restype").flatMap(_.headOption)
    val filterByProject: Option[String] = params.get("filter_by_project").flatMap(_.headOption)
    val filterByOwner: Option[String]   = params.get("filter_by_owner").flatMap(_.headOption)
    val searchType: Option[String]      = params.get("searchtype").flatMap(_.headOption)
    // known default value
    val showNRows: String = params.get("show_nrows").flatMap(_.headOption).getOrElse(defaultShowNRows.toString())
    val startAt: String   = params.get("start_at").flatMap(_.headOption).getOrElse("0")

    // can be multiple values
    val propertyId: Seq[String]         = params.get("property_id").toSeq.flatten
    val searchval: Seq[String]          = params.get("searchval").toSeq.flatten
    val comparisonOperator: Seq[String] = params.get("compop").toSeq.flatten

    def errorMessage(name: String, value: String): String =
      s"Value for param '$name' for extended search $value is not a valid IRI. Please make sure that it was correctly URL encoded."

    for {
      _ <- ZIO
             .fail(BadRequestException(s"Unexpected searchtype param for extended search: $searchType"))
             .unless(searchType.contains("extended"))

      restypeIri <-
        ZIO.foreach(filterByResType)(value =>
          RouteUtilV1.validateAndEscapeIri(value, errorMessage("filter_by_restype", value))
        )
      projectIri <-
        ZIO.foreach(filterByProject)(value =>
          RouteUtilV1.validateAndEscapeIri(value, errorMessage("filter_by_project", value))
        )
      ownerIri <-
        ZIO.foreach(filterByOwner)(value =>
          RouteUtilV1.validateAndEscapeIri(value, errorMessage("filter_by_owner", value))
        )
      propertyIri <-
        ZIO.foreach(propertyId)(prop => RouteUtilV1.validateAndEscapeIri(prop, errorMessage("property_id", prop)))
      compop <- ZIO.foreach(comparisonOperator)(SearchComparisonOperatorV1.lookup)

      // propertyId, compop, and searchval are parallel structures (parallel arrays): they have to be of the same length
      // in case of "compop" set to "EXISTS", also "searchval" has to be given as a param with an empty value (parallel arrays)
      _ <- ZIO
             .fail(BadRequestException(s"propertyId, compop, and searchval are not given parallelly"))
             .unless((propertyIri.length == compop.length) && (compop.length == searchval.length))

      nRows <-
        ZIO
          .fromOption(ValuesValidator.validateInt(showNRows))
          .map {
            case -1 => defaultShowNRows
            case v  => v
          }
          .orElseFail(
            BadRequestException(s"Can't parse integer parameter 'show_nrows' for extended search: $showNRows")
          )
      start <-
        ZIO
          .fromOption(ValuesValidator.validateInt(startAt))
          .orElseFail(BadRequestException(s"Can't parse integer parameter 'start_at' for extended search: $startAt"))

    } yield ExtendedSearchGetRequestV1(
      filterByRestype = restypeIri,
      filterByProject = projectIri,
      filterByOwner = ownerIri,
      propertyIri = propertyIri,
      compareProps = compop,
      searchValue = searchval, // not processed (escaped) yet
      userProfile = userADM,
      showNRows = nRows,
      startAt = start
    )
  }

  def makeFulltextSearchRequestMessage(
    userADM: UserADM,
    searchval: String,
    params: Map[String, String]
  ): FulltextSearchGetRequestV1 = {
    val stringFormatter = StringFormatter.getGeneralInstance

    params.get("searchtype") match {
      case Some("fulltext") => ()
      case _                => throw BadRequestException(s"Unexpected searchtype param for fulltext search")
    }

    val restypeIri: Option[IRI] = params.get("filter_by_restype") match {
      case Some(restype: IRI) =>
        Some(
          stringFormatter.validateAndEscapeIri(
            restype,
            throw BadRequestException(s"Unexpected param 'filter_by_restype' for extended search: $restype")
          )
        )
      case _ => None
    }
    val projectIri: Option[IRI] = params.get("filter_by_project") match {
      case Some(project: IRI) =>
        Some(
          stringFormatter.validateAndEscapeIri(
            project,
            throw BadRequestException(s"Unexpected param 'filter_by_project' for extended search: $project")
          )
        )
      case _ => None
    }

    val searchString = stringFormatter.toSparqlEncodedString(
      searchval,
      throw BadRequestException(s"Invalid search string: '$searchval'")
    )

    val showNRows: Int = params.get("show_nrows") match {
      case Some(showNRowsStr) =>
        val showNRowsVal = ValuesValidator
          .validateInt(showNRowsStr)
          .getOrElse(
            throw BadRequestException(s"Can't parse integer parameter 'show_nrows' for extended search: $showNRowsStr")
          )
        showNRowsVal match {
          case -1 => defaultShowNRows
          case _  => showNRowsVal
        }
      case None => defaultShowNRows
    }

    val startAt: Int = params.get("start_at") match {
      case Some(startAtStr) =>
        ValuesValidator
          .validateInt(startAtStr)
          .getOrElse(
            throw BadRequestException(s"Can't parse integer parameter 'start_at' for extended search: $startAtStr")
          )
      case None => 0
    }

    FulltextSearchGetRequestV1(
      searchValue = searchString, // save
      filterByRestype = restypeIri,
      filterByProject = projectIri,
      userProfile = userADM,
      showNRows = showNRows,
      startAt = startAt
    )
  }
}
