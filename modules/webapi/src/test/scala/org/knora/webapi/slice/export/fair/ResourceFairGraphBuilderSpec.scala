/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.Rendering
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.AssetAccess
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.MediaKind
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.api.v3.`export`.FileLink
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.IiifImageRequestUrl

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceFairGraphBuilderSpec extends ZIOSpecDefault {

  private given sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA")
  private val created     = Instant.parse("2020-01-01T00:00:00Z")
  private val modified    = Instant.parse("2021-01-01T00:00:00Z")
  private val ref         = ResourceLandingRef(resourceIri, None)
  private val appUrl      = "https://app.example.org"

  private val ccBy     = License.BUILT_IN.find(_.id == LicenseIri.CC_BY_4_0).get
  private val ccBySa   = License.BUILT_IN.find(_.id == LicenseIri.CC_BY_SA_4_0).get
  private val licenses = Map(ccBy.id -> ccBy, ccBySa.id -> ccBySa)

  private val grant    = AssetAccess.from(Some(ObjectAccess.View), MediaKind.RasterStillImage, None)
  private val withhold = AssetAccess.from(Some(ObjectAccess.RestrictedView), MediaKind.Document, None)

  private val project: KnoraProject = TestDataFactory.someProject.copy(dataLicense = Some(ccBy.id))

  private val fileValue = FileValueV2(
    internalFilename = "abc123.jp2",
    internalMimeType = "image/jp2",
    originalFilename = Some("a.tif"),
    originalMimeType = Some("image/tiff"),
    licenseIri = Some(ccBySa.id),
  )
  private val stillImage = StillImageFileValueContentV2(ApiV2Complex, fileValue, 1, 1)
  private val link       = FileLink(
    mimeType = Some("image/tiff"),
    url = "https://ingest.example.org/projects/0001/assets/abc123/original",
    checksum = None,
    checksumAlgorithm = None,
    fileName = Some("a.tif"),
    fileSize = Some(42L),
    dateCreated = None,
  )

  private def value(
    access: ObjectAccess,
    content: ValueContentV2,
    n: Int,
  ): (org.knora.webapi.messages.SmartIri, Seq[ReadValueV2]) =
    sf.toSmartIri(s"http://api.knora.org/ontology/knora-api/v2#prop$n") -> Seq(
      ReadOtherValueV2(
        valueIri = ValueIri.unsafeFrom(s"http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA/values/xB88vMy-Tc2ZCVh9Km7rV$n"),
        attachedToUser = "",
        permissions = "",
        userPermission = access,
        valueCreationDate = created,
        valueHasUUID = UUID.randomUUID(),
        valueContent = content,
        previousValueIri = None,
        deletionInfo = None,
      ),
    )

  private def resource(
    access: ObjectAccess = ObjectAccess.View,
    values: Seq[(ObjectAccess, ValueContentV2)] = Seq.empty,
    authorship: Seq[Authorship] = Seq.empty,
    deletion: Option[DeletionInfo] = None,
  ): ReadResourceV2 =
    ReadResourceV2(
      resourceIri = resourceIri,
      label = "A label",
      resourceClassIri = sf.toSmartIri("http://api.knora.org/ontology/knora-api/v2#Resource"),
      attachedToUser = "",
      projectADM = TestDataFactory.someProjectADM,
      permissions = "",
      userPermission = access,
      values = values.zipWithIndex.map { case ((a, c), i) => value(a, c, i) }.toMap,
      creationDate = created,
      lastModificationDate = Some(modified),
      versionDate = None,
      deletionInfo = deletion,
      resourceAuthorship = authorship,
    )

  private def seq(rs: ReadResourceV2*) = ReadResourcesSequenceV2(rs)

  private final case class Stub(
    read: Task[ReadResourcesSequenceV2],
    users: Ref[List[User]],
    access: Task[AssetAccess] = ZIO.succeed(grant),
    project: KnoraProject = project,
  ) extends FairGraphSources {
    def readResource(
      iri: ResourceIri,
      versionDate: Option[VersionDate],
      targetSchema: ApiV2Schema,
      schemaOptions: Set[Rendering],
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] =
      users.update(requestingUser :: _) *> ZIO.when(targetSchema != ApiV2Complex || schemaOptions.nonEmpty)(
        ZIO.dieMessage("unexpected schema or options"),
      ) *> read
    def findProject(id: ProjectIri): Task[Option[KnoraProject]]                        = ZIO.some(project)
    def findLicense(id: LicenseIri, sc: KnoraProject.Shortcode): Task[Option[License]] = ZIO.succeed(licenses.get(id))
    def assetAccess(user: User, filename: InternalFilename): Task[AssetAccess]         = access
    def fileLink(p: KnoraProject, fv: FileValueContentV2, d: Instant): UIO[FileLink]   = ZIO.succeed(link)
  }

  private def build(
    read: Task[ReadResourcesSequenceV2],
    ref: ResourceLandingRef = ref,
    url: String = appUrl,
    access: Task[AssetAccess] = ZIO.succeed(grant),
    project: KnoraProject = project,
  ) =
    for {
      users  <- Ref.make(List.empty[User])
      config <- ZIO.serviceWith[AppConfig](c => c.copy(dspApp = c.dspApp.copy(url = url)))
      graph  <- ResourceFairGraphBuilder(config, Stub(read, users, access, project), sf).build(ref)
      seen   <- users.get
    } yield (graph, seen)

  private def graphOf(
    rs: ReadResourceV2,
    access: Task[AssetAccess] = ZIO.succeed(grant),
    project: KnoraProject = project,
  ) =
    build(ZIO.succeed(seq(rs)), access = access, project = project).map(_._1.get)

  private val deletion = DeletionInfo(modified, None)

  val spec: Spec[Any, Any] = suite("ResourceFairGraphBuilder")(
    suite("publishes nothing")(
      test("Forbidden") {
        build(ZIO.fail(ForbiddenException("no"))).map { case (g, _) => assertTrue(g.isEmpty) }
      },
      test("NotFound") {
        build(ZIO.fail(NotFoundException("no"))).map { case (g, _) => assertTrue(g.isEmpty) }
      },
      test("deleted") {
        build(ZIO.succeed(seq(resource(deletion = Some(deletion))))).map { case (g, _) => assertTrue(g.isEmpty) }
      },
      test("deleted later, read at an older version") {
        val v = ref.copy(version = Some(VersionDate.fromInstant(created.plusSeconds(10))))
        build(ZIO.succeed(seq(resource(deletion = Some(deletion)))), v).map { case (g, _) => assertTrue(g.isEmpty) }
      },
      test("version before the creation date") {
        val v = ref.copy(version = Some(VersionDate.fromInstant(created.minusSeconds(10))))
        build(ZIO.succeed(seq(resource())), v).map { case (g, _) => assertTrue(g.isEmpty) }
      },
      test("empty result") {
        build(ZIO.succeed(seq())).map { case (g, _) => assertTrue(g.isEmpty) }
      },
      test("dsp-app url empty") {
        build(ZIO.succeed(seq(resource())), url = "").map { case (g, _) => assertTrue(g.isEmpty) }
      },
    ),
    test("any other failure propagates") {
      val boom = new RuntimeException("boom")
      build(ZIO.fail(boom)).exit.map(e => assertTrue(e == Exit.fail(boom)))
    },
    test("reads as the anonymous user") {
      build(ZIO.succeed(seq(resource()))).map { case (_, users) =>
        assertTrue(users == List(KnoraSystemInstances.Users.AnonymousUser))
      }
    },
    suite("access and file")(
      test("V resource, V file, Grant: full open with the file's own license") {
        graphOf(resource(values = Seq(ObjectAccess.View -> stillImage))).map { g =>
          assertTrue(
            g.accessLevel == AccessLevel.FullOpen,
            g.file == Some(
              FileFacts(link.url, Some("a.tif"), Some("image/tiff"), Some(42L), Some(ccBySa.uri.toString)),
            ),
            g.generalType == "Image",
          )
        }
      },
      test("Withhold: no file") {
        graphOf(resource(values = Seq(ObjectAccess.View -> stillImage)), access = ZIO.succeed(withhold)).map { g =>
          assertTrue(g.accessLevel == AccessLevel.FullOpen, g.file.isEmpty)
        }
      },
      test("failing asset decision: no file, graph still built") {
        graphOf(resource(values = Seq(ObjectAccess.View -> stillImage)), access = ZIO.fail(new Exception("x"))).map {
          g => assertTrue(g.file.isEmpty, g.title == "A label")
        }
      },
      test("RV file: restricted, no file") {
        graphOf(resource(values = Seq(ObjectAccess.RestrictedView -> stillImage))).map { g =>
          assertTrue(g.accessLevel == AccessLevel.Restricted, g.file.isEmpty)
        }
      },
      test("RV resource: restricted, no file") {
        graphOf(resource(ObjectAccess.RestrictedView, Seq(ObjectAccess.View -> stillImage))).map { g =>
          assertTrue(g.accessLevel == AccessLevel.Restricted, g.file.isEmpty)
        }
      },
      test("two V files: full open, no file, Dataset") {
        val other = AudioFileValueContentV2(ApiV2Complex, fileValue)
        graphOf(resource(values = Seq(ObjectAccess.View -> stillImage, ObjectAccess.View -> other))).map { g =>
          assertTrue(g.accessLevel == AccessLevel.FullOpen, g.file.isEmpty, g.generalType == "Dataset")
        }
      },
      test("two files, one RV: restricted, no file") {
        val other = AudioFileValueContentV2(ApiV2Complex, fileValue)
        graphOf(resource(values = Seq(ObjectAccess.View -> stillImage, ObjectAccess.RestrictedView -> other))).map { g =>
          assertTrue(g.accessLevel == AccessLevel.Restricted, g.file.isEmpty)
        }
      },
      test("external IIIF file: no file, Image") {
        val external = StillImageExternalFileValueContentV2(
          ApiV2Complex,
          fileValue,
          IiifImageRequestUrl.from("https://iiif.example.org/a/b/c/d/e/f.jpg").toOption.get,
        )
        graphOf(resource(values = Seq(ObjectAccess.View -> external))).map { g =>
          assertTrue(g.file.isEmpty, g.generalType == "Image")
        }
      },
      test("text value without a file: Dataset") {
        val text = TextValueContentV2(ApiV2Complex, Some("hello"), TextValueType.UnformattedText)
        graphOf(resource(values = Seq(ObjectAccess.View -> text))).map { g =>
          assertTrue(g.generalType == "Dataset", g.file.isEmpty)
        }
      },
    ),
    suite("creators")(
      test("resource authorship wins over the project default") {
        val p = project.copy(defaultDataAuthorship = List(Authorship.unsafeFrom("Project Person")))
        graphOf(resource(authorship = Seq(Authorship.unsafeFrom("Ada"))), project = p).map { g =>
          assertTrue(g.creators == Seq(Creator("Ada", CreatorKind.Person, None)))
        }
      },
      test("falls back to the project default; DaSCH is an organization") {
        val p = project.copy(defaultDataAuthorship = List(Authorship.unsafeFrom("DaSCH")))
        graphOf(resource(), project = p).map { g =>
          assertTrue(g.creators == Seq(Creator("DaSCH", CreatorKind.Organization, None)))
        }
      },
      test("an ORCID URI sets orcid; free text containing one does not") {
        val orcid = "https://orcid.org/0000-0002-1825-009X"
        val rs    = resource(authorship = Seq(Authorship.unsafeFrom(orcid), Authorship.unsafeFrom(s"Ada ($orcid)")))
        graphOf(rs).map { g =>
          assertTrue(g.creators.map(_.orcid) == Seq(Some(orcid), None))
        }
      },
    ),
    suite("license and copyright")(
      test("the project's data license is on the graph, not the file's") {
        graphOf(resource(values = Seq(ObjectAccess.View -> stillImage))).map { g =>
          assertTrue(g.license == Some(ccBy.uri.toString), g.file.flatMap(_.license) == Some(ccBySa.uri.toString))
        }
      },
      test("none when the project has no data license") {
        graphOf(resource(), project = project.copy(dataLicense = None)).map(g => assertTrue(g.license.isEmpty))
      },
      test("copyright holder comes from the project") {
        val p = project.copy(dataCopyrightHolder = Some(CopyrightHolder.unsafeFrom("Holder")))
        graphOf(resource(), project = p).map(g => assertTrue(g.copyrightHolder == Some("Holder")))
      },
    ),
    suite("identifiers")(
      test("a versioned ref versions the ARK but not the page URL") {
        val v = VersionDate.fromInstant(Instant.parse("2020-06-04T08:56:22Z"))
        build(ZIO.succeed(seq(resource())), ref.copy(version = Some(v))).map { case (g, _) =>
          val graph = g.get
          assertTrue(
            graph.ark == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20200604T085622Z",
            graph.pageUrl == s"$appUrl/resource/0001/cmfk1DMHRBiR4-_6HXpEFA",
          )
        }
      },
      test("project facts") {
        graphOf(resource()).map { g =>
          assertTrue(
            g.projectArk == "http://0.0.0.0:3336/ark:/72163/1/0001",
            g.projectShortcode == "0001",
            g.projectName == "shortname",
            g.resourceClassIri == "http://api.knora.org/ontology/knora-api/v2#Resource",
          )
        }
      },
    ),
  ).provideLayerShared(AppConfig.layer)
}
