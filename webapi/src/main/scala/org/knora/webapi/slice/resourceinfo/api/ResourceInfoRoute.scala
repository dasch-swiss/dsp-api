/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.IRI
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.slice.resourceinfo.api.LiveRestResourceInfoService.{ASC, Order, OrderBy, lastModificationDate}
import zhttp.http.HttpError.BadRequest
import zhttp.http._
import zio.ZLayer
import zio.json.EncoderOps
import zio.prelude.Validation

final case class ResourceInfoRoute(restService: RestResourceInfoService) {

  val route: HttpApp[Any, Nothing] =
    Http.collectZIO[Request] { case req @ Method.GET -> !! / "v2" / "resources" / "info" =>
      (for {
        p      <- getParameters(req)
        result <- restService.findByProjectAndResourceClass(p._1, p._2, (p._3, p._4))
      } yield result).fold(err => Response.fromHttpError(err), suc => Response.json(suc.toJson))
    }

  private def getParameters(req: Request) = {
    val queryParams = req.url.queryParams
    val headers     = req.headers
    Validation
      .validate(
        getProjectIri(headers),
        getResourceClass(queryParams),
        getOrderBy(queryParams),
        getOrder(queryParams)
      )
      .toZIO
  }

  private def getOrder(params: Map[String, List[String]]) = {
    val order: Validation[BadRequest, Order] = params.get("order") match {
      case Some(s :: Nil) =>
        Order.make(s).map(Validation.succeed).getOrElse(Validation.fail(BadRequest(s"Invalid order param $s")))
      case Some(_ :: _ :: _) => Validation.fail(BadRequest(s"orderBy param may only be a single value"))
      case _                 => Validation.succeed(ASC)
    }
    order
  }

  private def getOrderBy(params: Map[String, List[String]]) = {
    val orderBy: Validation[BadRequest, OrderBy] = params.get("orderBy") match {
      case Some(s :: Nil) =>
        OrderBy
          .make(s)
          .map(o => Validation.succeed(o))
          .getOrElse(Validation.fail(BadRequest(s"Invalid orderBy param $s")))
      case Some(_ :: _ :: _) => Validation.fail(BadRequest(s"orderBy param is mandatory with a single value"))
      case _                 => Validation.succeed(lastModificationDate)
    }
    orderBy
  }

  private def getResourceClass(params: Map[String, List[String]]) = {
    val resourceClassIri: Validation[BadRequest, IRI] = params.get("resourceClass") match {
      case Some(s :: Nil) => Validation.succeed(s)
      case _              => Validation.fail(BadRequest(s"resourceClass param is mandatory with a single value"))
    }
    resourceClassIri
  }

  private def getProjectIri(headers: Headers) = {
    val projectIri: Validation[BadRequest, IRI] = headers.header(RouteUtilV2.PROJECT_HEADER) match {
      case None             => Validation.fail(BadRequest(s"Header ${RouteUtilV2.PROJECT_HEADER} may not be empty"))
      case Some((_, value)) => Validation.succeed(value.toString)
    }
    projectIri
  }
}

object ResourceInfoRoute {
  val layer = ZLayer.fromFunction(ResourceInfoRoute(_))
}
