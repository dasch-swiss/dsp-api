/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.StatusCode
import zio.*
import zio.json.*
import zio.json.ast.Json

import org.knora.webapi.config.FairConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.fair.domain.Decision
import org.knora.webapi.slice.fair.domain.FairUrls
import org.knora.webapi.slice.fair.domain.Link
import org.knora.webapi.slice.fair.domain.Negotiation
import org.knora.webapi.slice.fair.domain.OaiShortcodes
import org.knora.webapi.slice.fair.domain.ResourceFacts
import org.knora.webapi.slice.fair.domain.Signposting
import org.knora.webapi.slice.fair.domain.service.AppShell
import org.knora.webapi.slice.fair.domain.service.ResourceFactsBuilder
import org.knora.webapi.slice.fair.domain.writers.DataCiteJson
import org.knora.webapi.slice.fair.domain.writers.DublinCoreMeta
import org.knora.webapi.slice.fair.domain.writers.Html
import org.knora.webapi.slice.fair.domain.writers.SchemaOrgJsonLd
import org.knora.webapi.slice.fair.domain.writers.ScriptSafeJson

/** What the endpoint layer sends: status, headers and an optional body. */
final case class FairResponse(status: StatusCode, headers: List[Header], body: Option[String])

/** An infrastructure failure. It never carries a message for the client; the cause is logged where it happens. */
final case class FairInternalError(cause: Throwable)

final class FairRestService(
  builder: ResourceFactsBuilder,
  shell: AppShell,
  fair: FairConfig,
  api: KnoraApi,
) {
  import FairRestService.*

  /** The landing answer for GET and HEAD: a 303 to a representation, or the shell (with the head block when visible). */
  def landing(shortcode: String, resourceId: String, accept: List[String]): IO[FairInternalError, FairResponse] =
    for {
      facts    <- buildFacts(shortcode, resourceId)
      redirect <- facts.fold(ZIO.none)(f => redirectFor(f, accept))
      response <- redirect.fold(htmlAnswer(facts))(ZIO.succeed(_))
    } yield response

  def jsonLd(shortcode: String, resourceId: String): IO[FairInternalError, FairResponse] =
    representation(shortcode, resourceId, FairUrls.JsonLd, SchemaOrgJsonLd.render)

  def dataCite(shortcode: String, resourceId: String): IO[FairInternalError, FairResponse] =
    representation(shortcode, resourceId, FairUrls.DataCite, DataCiteJson.render)

  private def buildFacts(shortcode: String, resourceId: String): IO[FairInternalError, Option[ResourceFacts]] =
    builder
      .build(shortcode, resourceId)
      .tapErrorCause(c => ZIO.logErrorCause(s"FAIR landing: building the resource facts failed", c))
      .mapError(FairInternalError(_))

  private def redirectFor(facts: ResourceFacts, accept: List[String]): UIO[Option[FairResponse]] = {
    val candidates = FairUrls.representations(api.externalKnoraApiBaseUrl, facts)
    Negotiation.decide(Option.when(accept.nonEmpty)(accept.mkString(",")), candidates) match {
      case Decision.Html          => ZIO.none
      case Decision.Redirect(url) =>
        Header.safeApply(HeaderNames.Location, url) match {
          case Right(location) =>
            ZIO.some(FairResponse(StatusCode.SeeOther, location :: landingHeaders, None))
          case Left(error) =>
            ZIO.logWarning(s"FAIR landing: the Location header was rejected ($error); answering HTML").as(None)
        }
    }
  }

  private def htmlAnswer(facts: Option[ResourceFacts]): IO[FairInternalError, FairResponse] =
    shell.get.flatMap {
      case None =>
        ZIO
          .logWarning("FAIR landing: the page shell is unavailable")
          .as(
            FairResponse(StatusCode.BadGateway, landingHeaders, None),
          )
      case Some(html) =>
        val htmlType = Header(HeaderNames.ContentType, HtmlContentType)
        facts match {
          case None    => ZIO.succeed(FairResponse(StatusCode.Ok, htmlType :: landingHeaders, Some(html)))
          case Some(f) =>
            val links = landingLinks(f)
            linkHeader(Signposting.header(links)).map { link =>
              FairResponse(
                StatusCode.Ok,
                htmlType :: link.toList ::: landingHeaders,
                Some(splice(html, headBlock(f, links))),
              )
            }
        }
    }

  private def landingLinks(facts: ResourceFacts): List[Link] =
    Signposting.landingLinks(
      facts,
      FairUrls.representations(api.externalKnoraApiBaseUrl, facts),
      fair.dpeOaiBaseUrl,
      OaiShortcodes.parse(fair.dpeOaiShortcodes),
    )

  private def representation(
    shortcode: String,
    resourceId: String,
    mediaType: String,
    render: ResourceFacts => Json,
  ): IO[FairInternalError, FairResponse] =
    buildFacts(shortcode, resourceId).flatMap {
      case None        => ZIO.succeed(NotVisible)
      case Some(facts) =>
        linkHeader(Signposting.header(Signposting.representationLinks(facts.pageUrl))).map { link =>
          FairResponse(
            StatusCode.Ok,
            Header(HeaderNames.ContentType, mediaType) :: link.toList,
            Some(render(facts).toJson),
          )
        }
    }

  /** A `Link` header, or None (with a warning) when its value is rejected; the page is still served. */
  private def linkHeader(value: String): UIO[Option[Header]] =
    Header.safeApply(HeaderNames.Link, value) match {
      case Right(h)    => ZIO.some(h)
      case Left(error) => ZIO.logWarning(s"FAIR landing: the Link header was rejected ($error); omitting it").as(None)
    }
}

object FairRestService {
  val layer: URLayer[ResourceFactsBuilder & AppShell & FairConfig & KnoraApi, FairRestService] =
    ZLayer.derive[FairRestService]

  private val HtmlContentType = "text/html; charset=UTF-8"
  private val HeadClose       = "</head>"

  private val landingHeaders: List[Header] =
    List(Header(HeaderNames.Vary, "Accept"), Header(HeaderNames.CacheControl, "no-store"))

  /** The same answer for a missing, deleted, invisible or malformed resource. */
  private val NotVisible: FairResponse =
    FairResponse(StatusCode.NotFound, List(Header(HeaderNames.ContentType, "text/plain")), None)

  /** Meta elements, link elements and the JSON-LD script, each on its own line. */
  private def headBlock(facts: ResourceFacts, links: List[Link]): String = {
    val script =
      s"""<script type="application/ld+json">${ScriptSafeJson.render(SchemaOrgJsonLd.render(facts))}</script>"""
    (DublinCoreMeta.render(facts).map(Html.metaElement) ::: Signposting.linkElements(links) ::: List(script))
      .mkString("", "\n", "\n")
  }

  /** Inserts the block immediately before the first `</head>`. */
  private def splice(shell: String, block: String): String = {
    val at = shell.indexOf(HeadClose)
    if (at < 0) shell else shell.substring(0, at) + block + shell.substring(at)
  }
}
