/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import sttp.model.HeaderNames
import sttp.model.StatusCode
import sttp.tapir.*
import zio.ZLayer

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.NegotiatesOwnAccept
import org.knora.webapi.slice.common.api.OwnAcceptNegotiation
import org.knora.webapi.slice.common.api.Policy

/**
 * The FAIR landing-page routes: the landing route, called by dsp-app's server rather than by clients, and the two public
 * representations. None of them has a security input, and each answers any `Accept` header itself.
 */
final class FairEndpoints(baseEndpoints: BaseEndpoints) {
  import FairEndpoints.*

  private def resourceIn  = path[String]("shortcode") / path[String]("resourceId")
  private val landingBase =
    baseEndpoints
      .publicPolicyEndpoint(
        internalError(header(HeaderNames.Vary, "Accept").and(header(HeaderNames.CacheControl, "no-store"))),
        publicReason,
      )

  private val representationBase =
    baseEndpoints
      .publicPolicyEndpoint(internalError(emptyOutput), publicReason)
  private val landing  = "internal" / "landing-pages" / "resources" / resourceIn
  private val resource = "fair" / "resources" / resourceIn

  val getLanding = landingBase.get
    .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation)
    .in(landing)
    .in(acceptIn)
    .out(withBody)
    .tag(Tag)
    .description(
      s"The landing page of a public resource, or a 303 to a representation the `Accept` header asks for. $publicRequirement",
    )

  val headLanding = landingBase.head
    .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation)
    .in(landing)
    .in(acceptIn)
    .out(withoutBody)
    .tag(Tag)
    .description(s"As the landing GET, without the body. $publicRequirement")

  val getJsonLd = representationBase.get
    .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation)
    .in(resource / "metadata.jsonld")
    .out(withBody)
    .tag(Tag)
    .description(s"The schema.org JSON-LD description of a public resource. $publicRequirement")

  val getDataCite = representationBase.get
    .attribute(NegotiatesOwnAccept.key, OwnAcceptNegotiation)
    .in(resource / "metadata.datacite.json")
    .out(withBody)
    .tag(Tag)
    .description(s"The DataCite JSON description of a public resource. $publicRequirement")
}

object FairEndpoints {
  val layer = ZLayer.derive[FairEndpoints]

  private val Tag = "FAIR"

  private val publicReason      = "Serves only resources the anonymous user may see."
  private val publicRequirement = Policy.Public(publicReason).requirement

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
}
