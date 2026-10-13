/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant
import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.IiifImageRequestUrl

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceMetadataRulesSpec extends ZIOSpecDefault {

  private given sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def fileValue(name: String): FileValueV2 = FileValueV2(name, "image/jp2")

  private def textContent: TextValueContentV2 =
    TextValueContentV2(InternalSchema, Some("hello"), TextValueType.UnformattedText)

  private def stillImage: StillImageFileValueContentV2 =
    StillImageFileValueContentV2(InternalSchema, fileValue("AbC123.jp2"), 10, 10)

  private def readValue(n: Int, content: ValueContentV2): ReadValueV2 =
    ReadOtherValueV2(
      valueIri = ValueIri.unsafeFrom(s"http://rdfh.ch/0001/res/values/v$n"),
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      permissions = "V knora-admin:UnknownUser",
      userPermission = Permission.ObjectAccess.View,
      valueCreationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
      valueHasUUID = UUID.fromString("c41f3cbc-ccbe-4dcd-9909-587d2a6eeb57"),
      valueContent = content,
      previousValueIri = None,
      deletionInfo = None,
    )

  private def resource(contents: ValueContentV2*): ReadResourceV2 = {
    val project = Project(
      id = KnoraProject.ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
      shortname = Shortname.unsafeFrom("anything"),
      shortcode = Shortcode.unsafeFrom("0001"),
      longname = None,
      description = Seq(PlainStringLiteralV2("Anything")),
      keywords = List.empty,
      logo = None,
      ontologies = List.empty,
      selfjoin = SelfJoin.CannotJoin,
      allowedCopyrightHolders = Set.empty,
      enabledLicenses = Set.empty,
    )
    val values = contents.zipWithIndex.map { (c, i) =>
      sf.toSmartIri(s"http://www.knora.org/ontology/0001/anything#prop$i") -> Seq(readValue(i, c))
    }.toMap
    ReadResourceV2(
      resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/res"),
      label = "Res",
      resourceClassIri = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#Thing"),
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      projectADM = project,
      permissions = "V knora-admin:UnknownUser",
      userPermission = Permission.ObjectAccess.View,
      values = values,
      creationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
      lastModificationDate = None,
      versionDate = None,
      deletionInfo = None,
    )
  }

  val spec = suite("ResourceMetadataRules")(
    suite("descriptionPropertyIri")(
      test("returns the configured property for known projects, matching the shortcode case-insensitively") {
        assertTrue(
          ResourceMetadataRules.descriptionPropertyIri(Shortcode.unsafeFrom("0803")) ==
            Some("http://www.knora.org/ontology/0803/incunabula#description"),
          ResourceMetadataRules.descriptionPropertyIri(Shortcode.unsafeFrom("081c")) ==
            Some("http://www.knora.org/ontology/081C/hdm#hasDescription"),
          ResourceMetadataRules.descriptionPropertyIri(Shortcode.unsafeFrom("0868")) ==
            Some("http://www.knora.org/ontology/0868/SolarEclipses#hasDescription"),
          ResourceMetadataRules.descriptionPropertyIri(Shortcode.unsafeFrom("1612")) ==
            Some("http://www.knora.org/ontology/1612/Data#TextShort"),
        )
      },
      test("returns None for an unknown project") {
        assertTrue(ResourceMetadataRules.descriptionPropertyIri(Shortcode.unsafeFrom("0001")).isEmpty)
      },
    ),
    suite("typeOfData")(
      test("maps each content type to its label") {
        val fv = fileValue("x.bin")
        assertTrue(
          ResourceMetadataRules.typeOfData(stillImage) == Some("Image"),
          ResourceMetadataRules.typeOfData(
            StillImageExternalFileValueContentV2(
              InternalSchema,
              fv,
              IiifImageRequestUrl.unsafeFrom("https://iiif.example.org/image.jp2/full/max/0/default.jpg"),
            ),
          ) == Some("Image"),
          ResourceMetadataRules.typeOfData(MovingImageFileValueContentV2(InternalSchema, fv)) == Some("Audiovisual"),
          ResourceMetadataRules.typeOfData(AudioFileValueContentV2(InternalSchema, fv)) == Some("Sound"),
          ResourceMetadataRules.typeOfData(TextFileValueContentV2(InternalSchema, fv)) == Some("Text"),
          ResourceMetadataRules.typeOfData(textContent) == Some("Text"),
          ResourceMetadataRules.typeOfData(IntegerValueContentV2(InternalSchema, 1)) == None,
        )
      },
      test("typeOfDataOf takes the first mapped value of the resource") {
        assertTrue(
          ResourceMetadataRules.typeOfDataOf(resource(IntegerValueContentV2(InternalSchema, 1), stillImage)) == Some(
            "Image",
          ),
          ResourceMetadataRules.typeOfDataOf(resource(IntegerValueContentV2(InternalSchema, 1))) == None,
        )
      },
    ),
    suite("selectFileValue")(
      test("skips non-file values and returns the file value itself") {
        val r      = resource(textContent, stillImage)
        val chosen = ResourceMetadataRules.selectFileValue(r)
        assertTrue(chosen.map(_._2) == Some(stillImage), chosen.exists(_._1.valueIri.value.endsWith("/v1")))
      },
      test("returns None when the resource has no file value") {
        assertTrue(ResourceMetadataRules.selectFileValue(resource(textContent)).isEmpty)
      },
    ),
    suite("assetId and url")(
      test("assetIdOf drops the filename extension") {
        assertTrue(ResourceMetadataRules.assetIdOf(stillImage) == "AbC123")
      },
      test("originalFileUrl addresses the ingest original endpoint") {
        assertTrue(
          ResourceMetadataRules.originalFileUrl("https://ingest.example.org", Shortcode.unsafeFrom("0803"), "AbC123") ==
            "https://ingest.example.org/projects/0803/assets/AbC123/original",
        )
      },
    ),
  )
}
