/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.*

@RunWith(classOf[DspZTestJUnitRunner])
class DublinCoreMetaSpec extends ZIOSpecDefault {

  private val ark        = "https://ark.example.org/ark:/72163/1/0803/0b03b0a6e6bX"
  private val projectArk = "https://ark.example.org/ark:/72163/1/0803"
  private val ccByUri    = "https://creativecommons.org/licenses/by/4.0/"
  private val Ellipsis   = "\u2026"

  private val file    = FileFacts("https://ingest.example.org/a/original", Some("image/jp2"), Some("a.jp2"), Some(42L))
  private val licence =
    ResourceLicence(Some(ccByUri), "Creative Commons Attribution 4.0 International", Some("CC-BY-4.0"))

  private val base = ResourceFacts(
    iri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/0b03b0a6e6be"),
    ark = ark,
    pageUrl = "https://app.example.org/resource/0803/0b03b0a6e6be",
    title = "A resource",
    description = Some("A description"),
    creators = Creators(List("Ada Lovelace", "Alan Turing"), CreatorSource.Authorship),
    licence = Some(licence),
    dateCreated = Instant.parse("2024-01-02T03:04:05Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    typeOfData = Some("Image"),
    classIri = "http://api.example.org/ontology/0803/x/v2#Thing",
    classLabel = "Thing",
    file = Some(file),
    accessLevel = AccessLevel.FullOpenAccess,
    projectArk = projectArk,
    shortcode = Shortcode.unsafeFrom("0803"),
  )

  private def values(facts: ResourceFacts, name: String): List[String] =
    DublinCoreMeta.render(facts).filter(_.name == name).map(_.content)

  private def description(text: String): String = values(base.copy(description = Some(text)), "DC.description").head

  override def spec: Spec[Any, Any] = suite("DublinCoreMeta")(
    test("the core elements are emitted in order") {
      assertTrue(
        DublinCoreMeta.render(base) == List(
          MetaTag("DC.title", "A resource"),
          MetaTag("DC.creator", "Ada Lovelace"),
          MetaTag("DC.creator", "Alan Turing"),
          MetaTag("DC.publisher", "DaSCH"),
          MetaTag("DC.identifier", ark),
          MetaTag("DC.type", "Image"),
          MetaTag("DC.date", "2024-01-02T03:04:05Z"),
          MetaTag("DC.description", "A description"),
          MetaTag("DC.format", "image/jp2"),
          MetaTag("DC.relation", projectArk),
          MetaTag("DC.rights", "Creative Commons Attribution 4.0 International"),
          MetaTag("DC.rights", ccByUri),
          MetaTag("DC.accessRights", "http://purl.org/coar/access_right/c_abf2"),
        ),
      )
    },
    test("the type is Dataset without a type of data") {
      assertTrue(values(base.copy(typeOfData = None), "DC.type") == List("Dataset"))
    },
    test("accessRights is the COAR URI of each level") {
      assertTrue(
        values(base.copy(accessLevel = AccessLevel.FullOpenAccess), "DC.accessRights") ==
          List("http://purl.org/coar/access_right/c_abf2"),
        values(base.copy(accessLevel = AccessLevel.OpenAccessWithRestrictions), "DC.accessRights") ==
          List("http://purl.org/coar/access_right/c_16ec"),
      )
    },
    test("a licence yields its label then its URI") {
      assertTrue(values(base, "DC.rights") == List(licence.label, ccByUri))
    },
    test("a declaration yields only its label") {
      val declaration = ResourceLicence(None, "All rights reserved", None)
      assertTrue(values(base.copy(licence = Some(declaration)), "DC.rights") == List("All rights reserved"))
    },
    test("no licence yields no rights tag") {
      assertTrue(values(base.copy(licence = None), "DC.rights").isEmpty)
    },
    test("a ProjectOrganisation creator yields no creator tag") {
      val facts = base.copy(creators = Creators(List("Some Project"), CreatorSource.ProjectOrganisation))
      assertTrue(values(facts, "DC.creator").isEmpty)
    },
    test("no recorded mime type yields no format tag") {
      assertTrue(
        values(base.copy(file = Some(file.copy(mimeType = None))), "DC.format").isEmpty,
        values(base.copy(file = None), "DC.format").isEmpty,
      )
    },
    test("no description yields no description tag") {
      assertTrue(values(base.copy(description = None), "DC.description").isEmpty)
    },
    test("a description of 1001 characters is cut to 1000 plus an ellipsis") {
      val cut = description("a" * 1001)
      assertTrue(cut == "a" * 1000 + Ellipsis, cut.codePointCount(0, cut.length) == 1001)
    },
    test("a description of exactly 1000 characters is not cut") {
      val text = "a" * 1000
      assertTrue(description(text) == text)
    },
    test("a long description is cut on a code point boundary") {
      val cut = description("😀" * 1001)
      assertTrue(
        cut == "😀" * 1000 + Ellipsis,
        cut.codePointCount(0, cut.length) == 1001,
        cut.codePoints.toArray.forall(cp => cp < 0xd800 || cp > 0xdfff),
      )
    },
    test("a description of 1000 non-BMP characters is not cut") {
      val text = "😀" * 1000
      assertTrue(description(text) == text)
    },
  )
}
