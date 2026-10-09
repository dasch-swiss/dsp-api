/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import org.junit.runner.RunWith
import zio.json.ast.Json
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.*

@RunWith(classOf[DspZTestJUnitRunner])
class WritersAgreementSpec extends ZIOSpecDefault {

  private val ccByUri = "https://creativecommons.org/licenses/by/4.0/"

  private val full = ResourceFacts(
    iri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/0b03b0a6e6be"),
    ark = "https://ark.example.org/ark:/72163/1/0803/0b03b0a6e6bX",
    pageUrl = "https://app.example.org/resource/0803/0b03b0a6e6be",
    title = "A resource",
    description = Some("A description"),
    creators = Creators(List("Ada Lovelace", "Alan Turing"), CreatorSource.Authorship),
    licence = Some(
      ResourceLicence(Some(ccByUri), "Creative Commons Attribution 4.0 International", Some("CC-BY-4.0")),
    ),
    dateCreated = Instant.parse("2024-01-02T03:04:05Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    typeOfData = Some("Image"),
    classIri = "http://api.example.org/ontology/0803/x/v2#Thing",
    classLabel = "Thing",
    file = Some(FileFacts("https://ingest.example.org/a/original", Some("image/jp2"), Some("a.jp2"), Some(42L))),
    accessLevel = AccessLevel.FullOpenAccess,
    projectArk = "https://ark.example.org/ark:/72163/1/0803",
    shortcode = Shortcode.unsafeFrom("0803"),
  )

  private val fallback = full.copy(
    description = None,
    licence = Some(ResourceLicence(None, "All rights reserved", None)),
    dateModified = None,
    typeOfData = None,
    file = None,
    creators = Creators(List("The Project"), CreatorSource.ProjectOrganisation),
  )

  private val notModified = full.copy(dateModified = None)
  private val noLicence   =
    full.copy(licence = None, creators = Creators(List("Default Author"), CreatorSource.ProjectDefault))
  private val textType = full.copy(typeOfData = Some("Text"))

  private def get(json: Json, key: String): Option[Json] = json match {
    case Json.Obj(fields) => fields.collectFirst { case (k, v) if k == key => v }
    case _                => None
  }
  private def string(json: Option[Json]): Option[String] = json.collect { case Json.Str(s) => s }
  private def items(json: Option[Json]): List[Json]      = json match {
    case Some(Json.Arr(xs)) => xs.toList
    case Some(other)        => List(other)
    case None               => Nil
  }
  private def first(json: Option[Json]): Option[Json] = items(json).headOption

  private def dc(tags: List[MetaTag], name: String): List[String] = tags.filter(_.name == name).map(_.content)

  private def dataciteDate(dj: Json, dateType: String): Option[String] =
    items(get(dj, "dates"))
      .find(d => string(get(d, "dateType")).contains(dateType))
      .flatMap(d => string(get(d, "date")))

  private def agree(label: String, f: ResourceFacts) = {
    val ld   = SchemaOrgJsonLd.render(f)
    val tags = DublinCoreMeta.render(f)
    val dj   = DataCiteJson.render(f)

    val ldCreators = items(get(ld, "creator")).flatMap(c => string(get(c, "name")))
    val dcCreators = dc(tags, "DC.creator")
    val djCreators = items(get(dj, "creators"))

    val ldLicense   = first(get(ld, "license")).flatMap(l => string(get(l, "@id")))
    val dcRights    = dc(tags, "DC.rights")
    val rights      = first(get(dj, "rightsList"))
    val djRightsUri = rights.flatMap(r => string(get(r, "rightsUri")))
    val djRights    = rights.flatMap(r => string(get(r, "rights")))
    val dcUri       = f.licence.flatMap(_.uri).flatMap(u => dcRights.find(_ == u))

    val types = get(dj, "types")

    suite(label)(
      test("title") {
        assertTrue(
          string(get(ld, "name")) == Some(f.title),
          dc(tags, "DC.title") == List(f.title),
          string(first(get(dj, "titles")).flatMap(get(_, "title"))) == Some(f.title),
        )
      },
      test("identifier") {
        assertTrue(
          string(get(ld, "@id")) == Some(f.ark),
          dc(tags, "DC.identifier") == List(f.ark),
          string(first(get(dj, "identifiers")).flatMap(get(_, "identifier"))) == Some(f.ark),
        )
      },
      test("creators") {
        val organisational = f.creators.source == CreatorSource.ProjectOrganisation
        if (organisational)
          assertTrue(
            get(ld, "creator").isEmpty,
            dcCreators.isEmpty,
            djCreators.flatMap(c => string(get(c, "name"))) == f.creators.names,
            djCreators.forall(c => string(get(c, "nameType")).contains("Organizational")),
          )
        else
          assertTrue(
            ldCreators == f.creators.names,
            dcCreators == f.creators.names,
            djCreators.flatMap(c => string(get(c, "name"))) == f.creators.names,
          )
      },
      test("licence") {
        f.licence match {
          case Some(ResourceLicence(Some(uri), lbl, _)) =>
            assertTrue(
              ldLicense == Some(uri),
              dcUri == Some(uri),
              djRightsUri == Some(uri),
              dcRights.contains(lbl),
              djRights == Some(lbl),
            )
          case Some(ResourceLicence(None, lbl, _)) =>
            assertTrue(
              get(ld, "license").isEmpty,
              dcRights == List(lbl),
              djRights == Some(lbl),
              djRightsUri.isEmpty,
            )
          case None =>
            assertTrue(get(ld, "license").isEmpty, dcRights.isEmpty, rights.isEmpty)
        }
      },
      test("type") {
        val general = string(types.flatMap(get(_, "resourceTypeGeneral")))
        assertTrue(
          dc(tags, "DC.type").headOption == general,
          string(get(ld, "additionalType")) == Some(f.classIri),
          string(types.flatMap(get(_, "resourceType"))) == Some(f.classLabel),
        )
      },
      test("dates") {
        val created  = f.dateCreated.toString
        val modified = f.dateModified.map(_.toString)
        assertTrue(
          string(get(ld, "dateCreated")) == Some(created),
          dc(tags, "DC.date") == List(created),
          dataciteDate(dj, "Created") == Some(created),
          string(get(ld, "dateModified")) == modified,
          dataciteDate(dj, "Updated") == modified,
        )
      },
    )
  }

  override def spec: Spec[TestEnvironment, Any] =
    suite("WritersAgreementSpec")(
      agree("full: file, CC licence, two authors, modified", full),
      agree("no file, declaration, organisation fallback", fallback),
      agree("not modified", notModified),
      agree("no licence, project default creators", noLicence),
      agree("Text type of data", textType),
    )
}
