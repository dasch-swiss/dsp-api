/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.fair.domain.writers.Html

/** One typed link. */
final case class Link(href: String, rel: String, mediaType: Option[String])

/** FAIR Signposting level 1: the links a landing page carries as a `Link` header and as `<link>` elements. */
object Signposting {

  private val SchemaOrgDataset   = "https://schema.org/Dataset"
  private val SchemaOrgAboutPage = "https://schema.org/AboutPage"
  private val Xml                = "application/xml"

  /** The landing page's links, in emission order. */
  def landingLinks(
    facts: ResourceFacts,
    representations: List[Candidate],
    oaiBaseUrl: String,
    oaiShortcodes: Set[Shortcode],
  ): List[Link] = {
    val oai =
      if (oaiShortcodes.contains(facts.shortcode))
        List("oai_datacite", "oai_dc").map(p => Link(oaiRecordUrl(oaiBaseUrl, facts.ark, p), "describedby", Some(Xml)))
      else Nil
    List(
      Link(facts.ark, "cite-as", None),
      Link(SchemaOrgDataset, "type", None),
      Link(SchemaOrgAboutPage, "type", None),
    ) ++
      representations.map(c => Link(c.url, "describedby", Some(c.mediaType))) ++
      oai ++
      facts.licence.flatMap(_.uri).map(Link(_, "license", None)).toList
  }

  /** The link a representation carries: it describes the landing page. */
  def representationLinks(pageUrl: String): List[Link] = List(Link(pageUrl, "describes", None))

  /** An RFC 8288 `Link` field value. CR and LF are left for the HTTP layer to reject. */
  def header(links: List[Link]): String = links.map(fieldValue).mkString(", ")

  /** `<link>` elements for the document head, carrying the raw href. */
  def linkElements(links: List[Link]): List[String] =
    links.map { l =>
      val tpe = l.mediaType.fold("")(t => s""" type="${Html.escapeAttribute(t)}"""")
      s"""<link rel="${Html.escapeAttribute(l.rel)}" href="${Html.escapeAttribute(l.href)}"$tpe>"""
    }

  // The identifier must equal the DPE OAI server's record identifier: "oai:dasch.swiss:" + the ARK from "ark:/".
  private def oaiRecordUrl(oaiBaseUrl: String, ark: String, prefix: String): String = {
    val i          = ark.indexOf("ark:/")
    val identifier = "oai:dasch.swiss:" + (if (i >= 0) ark.substring(i) else ark)
    s"$oaiBaseUrl?verb=GetRecord&identifier=${percentEncode(identifier)}&metadataPrefix=$prefix"
  }

  // Matches the Rust `urlencoding` crate: unreserved bytes (A-Z a-z 0-9 - _ . ~) stay, every other UTF-8 byte is %XX.
  private def percentEncode(s: String): String = {
    val sb = new StringBuilder
    s.getBytes(StandardCharsets.UTF_8).foreach { b =>
      val c = (b & 0xff).toChar
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-_.~".indexOf(c) >= 0)
        sb.append(c)
      else sb.append('%').append(f"${b & 0xff}%02X")
    }
    sb.toString
  }

  private def fieldValue(l: Link): String = {
    val tpe = l.mediaType.fold("")(t => s"""; type="$t"""")
    s"""<${escapeDelimiters(l.href)}>; rel="${l.rel}"$tpe"""
  }

  /** Percent-encodes the characters that are field syntax in RFC 8288, so an href cannot forge another link value. */
  private def escapeDelimiters(href: String): String =
    href.flatMap {
      case '<'   => "%3C"
      case '>'   => "%3E"
      case '"'   => "%22"
      case ';'   => "%3B"
      case ','   => "%2C"
      case ' '   => "%20"
      case other => other.toString
    }
}
