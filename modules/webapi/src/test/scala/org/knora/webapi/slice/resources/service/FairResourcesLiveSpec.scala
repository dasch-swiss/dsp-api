/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.InternalSchema
import org.knora.webapi.Rendering
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.fair.ports.FairFileKind
import org.knora.webapi.slice.resources.IiifImageRequestUrl

@RunWith(classOf[DspZTestJUnitRunner])
class FairResourcesLiveSpec extends ZIOSpecDefault {

  private given sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ingest = "https://ingest.example.org"

  private def readValue(n: Int, content: ValueContentV2): ReadValueV2 =
    ReadOtherValueV2(
      valueIri = ValueIri.unsafeFrom(s"http://rdfh.ch/0803/res/values/v$n"),
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      permissions = "V knora-admin:UnknownUser",
      userPermission = Permission.ObjectAccess.Modify,
      valueCreationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
      valueHasUUID = UUID.fromString("c41f3cbc-ccbe-4dcd-9909-587d2a6eeb57"),
      valueContent = content,
      previousValueIri = None,
      deletionInfo = None,
    )

  // Keys are as an ApiV2Complex read returns them.
  private def complex(internal: String): String = sf.toSmartIri(internal).toComplexSchema.toString
  private val descriptionKey                    = complex("http://www.knora.org/ontology/0803/incunabula#description")
  private val otherKey                          = complex("http://www.knora.org/ontology/0803/incunabula#other")
  private val bookClass                         = complex("http://www.knora.org/ontology/0803/incunabula#book")
  private val descriptionProperty               =
    PropertyIri.unsafeFrom(sf.toSmartIri("http://www.knora.org/ontology/0803/incunabula#description"))

  private def text(s: String): TextValueContentV2 =
    TextValueContentV2(InternalSchema, Some(s), TextValueType.UnformattedText)

  private def resource(values: (String, ValueContentV2)*): ReadResourceV2 = {
    val project = Project(
      id = KnoraProject.ProjectIri.unsafeFrom("http://rdfh.ch/projects/Lw3FC39BSzCwvmdOaTyLqQ"),
      shortname = Shortname.unsafeFrom("incunabula"),
      shortcode = Shortcode.unsafeFrom("0803"),
      longname = None,
      description = Seq(PlainStringLiteralV2("Incunabula")),
      keywords = List.empty,
      logo = None,
      ontologies = List.empty,
      selfjoin = SelfJoin.CannotJoin,
      allowedCopyrightHolders = Set.empty,
      enabledLicenses = Set.empty,
    )
    ReadResourceV2(
      resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/res"),
      label = "Res",
      resourceClassIri = sf.toSmartIri(bookClass),
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      projectADM = project,
      permissions = "V knora-admin:UnknownUser",
      userPermission = Permission.ObjectAccess.View,
      values = values.zipWithIndex.map { case ((key, c), i) => sf.toSmartIri(key) -> Seq(readValue(i, c)) }.toMap,
      creationDate = Instant.parse("2016-10-17T17:16:04.916Z"),
      lastModificationDate = None,
      versionDate = None,
      deletionInfo = None,
      resourceAuthorship = Seq(Authorship.unsafeFrom("Jane Doe")),
    )
  }

  private val legal = FileValueV2(
    "AbC123.jp2",
    "image/jp2",
    authorship = Some(List(Authorship.unsafeFrom("Photographer"))),
    licenseIri = Some(LicenseIri.CC_BY_4_0),
  )

  private val toFairResourceSuite = suite("toFairResource")(
    test("takes the description and the stored file from a resource with a text and a still image value") {
      val r = resource(
        descriptionKey -> text("A book"),
        otherKey       -> StillImageFileValueContentV2(InternalSchema, legal, 10, 10),
      )
      val fair = FairResourcesLive.toFairResource(r, Some(descriptionProperty), ingest)
      val file = fair.file.get
      assertTrue(
        fair.description == Some("A book"),
        fair.label == "Res",
        fair.classIri == complex(bookClass),
        fair.authorship == List("Jane Doe"),
        file.kind == FairFileKind.Stored,
        file.typeOfData == Some("Image"),
        file.assetId == Some("AbC123"),
        file.originalUrl == Some(s"$ingest/projects/0803/assets/AbC123/original"),
        file.userPermission == Permission.ObjectAccess.Modify,
      )
    },
    test("carries the legal fields of the file value") {
      val r    = resource(otherKey -> StillImageFileValueContentV2(InternalSchema, legal, 10, 10))
      val file = FairResourcesLive.toFairResource(r, None, ingest).file.get
      assertTrue(
        file.licenseIri == Some(LicenseIri.CC_BY_4_0.value),
        file.authorship == List("Photographer"),
      )
    },
    test("describes an external IIIF image without asset id or original URL") {
      val r = resource(
        otherKey -> StillImageExternalFileValueContentV2(
          InternalSchema,
          FileValueV2("external", "image/jp2"),
          IiifImageRequestUrl.unsafeFrom("https://iiif.example.org/image.jp2/full/max/0/default.jpg"),
        ),
      )
      val file = FairResourcesLive.toFairResource(r, None, ingest).file.get
      assertTrue(
        file.kind == FairFileKind.ExternalIiif,
        file.assetId.isEmpty,
        file.originalUrl.isEmpty,
        file.typeOfData == Some("Image"),
      )
    },
    test("a blank description is no description") {
      val fair = FairResourcesLive.toFairResource(
        resource(descriptionKey -> text("  \n ")),
        Some(descriptionProperty),
        ingest,
      )
      assertTrue(fair.description.isEmpty)
    },
    test("has no file and no description when the resource has neither") {
      val fair = FairResourcesLive.toFairResource(resource(otherKey -> text("x")), Some(descriptionProperty), ingest)
      assertTrue(fair.file.isEmpty, fair.description.isEmpty)
    },
  )

  private final case class ReadCall(
    iris: Seq[ResourceIri],
    requestingUser: User,
    withDeleted: Boolean,
    skipRetrievalChecks: Boolean,
  )

  // Only readResourcesSequence is used by FairResourcesLive.
  private final class StubReadResources(
    result: Task[ReadResourcesSequenceV2],
    calls: Ref[List[ReadCall]],
  ) extends ReadResourcesService {
    def readResourcesSequence(
      resourceIris: Seq[ResourceIri],
      propertyIri: Option[SmartIri],
      valueUuid: Option[UUID],
      preview: Boolean,
      targetSchema: ApiV2Schema,
      requestingUser: User,
      withDeleted: Boolean,
      queryStandoff: Boolean,
      skipRetrievalChecks: Boolean,
      standoffTagFilter: Option[SmartIri],
    ): Task[ReadResourcesSequenceV2] =
      calls.update(ReadCall(resourceIris, requestingUser, withDeleted, skipRetrievalChecks) :: _) *> result

    def readResourcesSequencePar(
      resourceIris: Seq[ResourceIri],
      propertyIri: Option[SmartIri],
      valueUuid: Option[UUID],
      preview: Boolean,
      targetSchema: ApiV2Schema,
      requestingUser: User,
      withDeleted: Boolean,
      queryStandoff: Boolean,
      skipRetrievalChecks: Boolean,
      standoffTagFilter: Option[SmartIri],
    ): Task[ReadResourcesSequenceV2] = ZIO.die(new UnsupportedOperationException)

    def getResources(
      resourceIris: Seq[ResourceIri],
      propertyIri: Option[SmartIri],
      targetSchema: ApiV2Schema,
      schemaOptions: Set[Rendering],
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = ZIO.die(new UnsupportedOperationException)

    def getResourcesWithDeletedResource(
      resourceIris: Seq[ResourceIri],
      propertyIri: Option[SmartIri],
      valueUuid: Option[UUID],
      versionDate: Option[VersionDate],
      withDeleted: Boolean,
      showDeletedValues: Boolean,
      targetSchema: ApiV2Schema,
      schemaOptions: Set[Rendering],
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = ZIO.die(new UnsupportedOperationException)

    def getResourcePreviewWithDeletedResource(
      resourceIris: Seq[ResourceIri],
      withDeleted: Boolean,
      targetSchema: ApiV2Schema,
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = ZIO.die(new UnsupportedOperationException)

    def getResourcePreview(
      resourceIris: Seq[ResourceIri],
      withDeleted: Boolean,
      targetSchema: ApiV2Schema,
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = ZIO.die(new UnsupportedOperationException)
  }

  private def readAsAnonymous(result: Task[ReadResourcesSequenceV2]) =
    for {
      calls <- Ref.make(List.empty[ReadCall])
      live   = FairResourcesLive(
               new StubReadResources(result, calls),
               IriConverter(sf),
               DspIngestConfig("http://ingest", ingest, "audience", "/opt/images"),
             )
      iri   = ResourceIri.unsafeFrom("http://rdfh.ch/0803/res")
      out  <- live.readAsAnonymous(iri).exit
      seen <- calls.get
    } yield (out, seen, iri)

  private val readAsAnonymousSuite = suite("readAsAnonymous")(
    test("answers None for a NotFoundException") {
      readAsAnonymous(ZIO.fail(NotFoundException("missing"))).map { case (out, _, _) =>
        assertTrue(out == Exit.succeed(None))
      }
    },
    test("answers None for a ForbiddenException") {
      readAsAnonymous(ZIO.fail(ForbiddenException("hidden"))).map { case (out, _, _) =>
        assertTrue(out == Exit.succeed(None))
      }
    },
    test("keeps every other failure a failure") {
      val boom = new RuntimeException("boom")
      readAsAnonymous(ZIO.fail(boom)).map { case (out, _, _) => assertTrue(out == Exit.fail(boom)) }
    },
    test("reads the resource as the anonymous user, without deleted resources and with retrieval checks") {
      readAsAnonymous(ZIO.succeed(ReadResourcesSequenceV2(Seq(resource()), Set.empty, false))).map {
        case (out, seen, iri) =>
          assertTrue(
            out.isSuccess,
            seen == List(ReadCall(Seq(iri), KnoraSystemInstances.Users.AnonymousUser, false, false)),
          )
      }
    },
  )

  val spec = suite("FairResourcesLive")(toFairResourceSuite, readAsAnonymousSuite)
}
