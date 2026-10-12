/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import sttp.model.HeaderNames
import sttp.model.StatusCode
import sttp.tapir.*
import zio.ZLayer

import org.knora.webapi.slice.common.api.NegotiatesOwnAccept
import org.knora.webapi.slice.common.api.OwnAcceptNegotiation
import org.knora.webapi.slice.common.api.RequiresMethod

/**
 * The FAIR landing-page routes: the landing route, called by dsp-app's server rather than by clients, and the two public
 * representations. None of them has a security input, and each answers any `Accept` header itself.
 */
final class FairEndpoints {
  import FairEndpoints.*

  private def resourceIn = path[String]("shortcode") / path[String]("resourceId")
  private val landing    = "internal" / "landing-pages" / "resources" / resourceIn
  private val resource   = "fair" / "resources" / resourceIn

  val getLanding = landingBase.get
    .in(landing)
    .in(acceptIn)
    .out(withBody)
    .tag(Tag)
    .description("The landing page of a public resource, or a 303 to a representation the `Accept` header asks for.")

  val headLanding = landingBase.head
    .in(landing)
    .in(acceptIn)
    .out(withoutBody)
    .tag(Tag)
    .description("As the landing GET, without the body.")

  val getJsonLd = representationBase.get
    .in(resource / "metadata.jsonld")
    .out(withBody)
    .tag(Tag)
    .description("The schema.org JSON-LD description of a public resource.")

  val getDataCite = representationBase.get
    .in(resource / "metadata.datacite.json")
    .out(withBody)
    .tag(Tag)
    .description("The DataCite JSON description of a public resource.")
}

object FairEndpoints {
  val layer = ZLayer.derive[FairEndpoints]

  private val Tag = "FAIR"

  private val acceptIn = header[List[String]](HeaderNames.Accept)
    .description("Repeated header lines are combined into one comma-separated value.")

  // A missing body writes nothing at all, so a 303 or 502 gets no Content-Type from the codec either.
  private val optionalBody: EndpointOutput[Option[String]] = oneOf[Option[String]](
    oneOfVariantValueMatcher(stringBody.map(Mapping.from[String, Option[String]](Some(_))(_.getOrElse("")))) {
      case Some(_) => true
    },
    oneOfVariantValueMatcher(emptyOutput.map(Mapping.from[Unit, Option[String]](_ => None)(_ => ()))) { case None =>
      true
    },
  )

  // The Content-Type comes from the headers output and only from there: tapir adds the body codec's own media type
  // only when the output carries no Content-Type header.
  private val withBody: EndpointOutput[FairResponse] =
    statusCode.and(headers).and(optionalBody).mapTo[FairResponse]

  private val withoutBody: EndpointOutput[FairResponse] =
    statusCode
      .and(headers)
      .map(Mapping.from[(StatusCode, List[sttp.model.Header]), FairResponse] { case (s, h) =>
        FairResponse(s, h, None)
      }(r => (r.status, r.headers)))

  // Never carries the cause; it is logged where it happens.
  private def internalError(constantHeaders: EndpointOutput[Unit]): EndpointOutput[FairInternalError.type] =
    statusCode(StatusCode.InternalServerError)
      .and(constantHeaders)
      .map(Mapping.from[Unit, FairInternalError.type](_ => FairInternalError)(_ => ()))

  private val landingBase =
    RequiresMethod(
      endpoint
        .errorOut(internalError(header(HeaderNames.Vary, "Accept").and(header(HeaderNames.CacheControl, "no-store"))))
        .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation),
      _.method(_),
    )

  private val representationBase =
    RequiresMethod(
      endpoint
        .errorOut(internalError(emptyOutput))
        .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation),
      _.method(_),
    )
}
