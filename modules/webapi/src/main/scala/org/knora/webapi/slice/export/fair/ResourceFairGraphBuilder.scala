/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import zio.*

import java.time.Instant

import dsp.errors.ForbiddenException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.Rendering
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.AudioFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.DocumentFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.MovingImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextFileValueContentV2
import org.knora.webapi.responders.admin.AssetPermissionsResponder
import org.knora.webapi.slice.admin.domain.model.AssetAccess
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.OriginalAccess
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.api.v3.`export`.FileLink
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.service.ReadResourcesService

/** The effectful reads the builder needs, narrowed so tests need no triplestore. */
trait FairGraphSources {
  def readResource(
    resourceIri: ResourceIri,
    versionDate: Option[VersionDate],
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def findProject(id: ProjectIri): Task[Option[KnoraProject]]

  def findLicense(id: LicenseIri, shortcode: KnoraProject.Shortcode): Task[Option[License]]

  def assetAccess(user: User, filename: InternalFilename): Task[AssetAccess]

  def fileLink(project: KnoraProject, fileValue: FileValueContentV2, resourceCreationDate: Instant): UIO[FileLink]
}

final case class FairGraphSourcesLive(
  readResources: ReadResourcesService,
  projects: KnoraProjectService,
  legalInfo: LegalInfoService,
  assetPermissions: AssetPermissionsResponder,
  assetDownloadLinks: AssetDownloadLinks,
) extends FairGraphSources {

  def readResource(
    resourceIri: ResourceIri,
    versionDate: Option[VersionDate],
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] =
    readResources.getResourcesWithDeletedResource(
      resourceIris = Seq(resourceIri),
      versionDate = versionDate,
      targetSchema = targetSchema,
      schemaOptions = schemaOptions,
      requestingUser = requestingUser,
    )

  def findProject(id: ProjectIri): Task[Option[KnoraProject]] = projects.findById(id)

  def findLicense(id: LicenseIri, shortcode: KnoraProject.Shortcode): Task[Option[License]] =
    legalInfo.findAvailableLicenseByIdAndShortcode(id, shortcode)

  def assetAccess(user: User, filename: InternalFilename): Task[AssetAccess] =
    assetPermissions.getAssetAccess(user)(filename)

  def fileLink(project: KnoraProject, fileValue: FileValueContentV2, resourceCreationDate: Instant): UIO[FileLink] =
    assetDownloadLinks.linkOf(project, fileValue, resourceCreationDate)
}

/** What the effectful gather hands to the pure [[ResourceFairGraphBuilder.assemble]]. */
final case class FairGraphInputs(
  ref: ResourceLandingRef,
  resource: ReadResourceV2,
  project: KnoraProject,
  projectLicense: Option[License],
  grantedFile: Option[GrantedFile],
)

final case class GrantedFile(link: FileLink, license: Option[License])

final case class ResourceFairGraphBuilder(appConfig: AppConfig, sources: FairGraphSources, sf: StringFormatter) {

  /** `None` means publish nothing. */
  def build(ref: ResourceLandingRef): Task[Option[ResourceFairGraph]] =
    if (appConfig.dspApp.url.isEmpty) ZIO.none
    else
      readVisible(ref).flatMap {
        case None           => ZIO.none
        case Some(resource) => gather(ref, resource).map(i => Some(assemble(appConfig.dspApp.url, i)))
      }

  private def readVisible(ref: ResourceLandingRef): Task[Option[ReadResourceV2]] =
    sources
      .readResource(
        resourceIri = ref.resourceIri,
        versionDate = ref.version,
        targetSchema = ApiV2Complex,
        schemaOptions = Set.empty,
        requestingUser = KnoraSystemInstances.Users.AnonymousUser,
      )
      .map(seq => ResourceFairGraphBuilder.publicResource(ref, seq))
      .catchSome { case _: ForbiddenException | _: NotFoundException => ZIO.none }

  private def gather(ref: ResourceLandingRef, resource: ReadResourceV2): Task[FairGraphInputs] =
    for {
      project <- sources
                   .findProject(resource.projectADM.id)
                   .someOrFail(InconsistentRepositoryDataException(s"No project for resource ${ref.resourceIri}"))
      projectLicense <- ZIO.foreach(project.dataLicense)(sources.findLicense(_, project.shortcode)).map(_.flatten)
      granted        <- grantedFile(project, resource)
    } yield FairGraphInputs(ref, resource, project, projectLicense, granted)

  private def grantedFile(project: KnoraProject, resource: ReadResourceV2): Task[Option[GrantedFile]] = {
    val files = ResourceFairGraphBuilder.fileValues(resource)
    if (files.size > 1)
      ZIO.logWarning(s"Resource ${resource.resourceIri} has ${files.size} file values; no file is advertised") *>
        ZIO.none
    else
      files.headOption match {
        case Some((_, content))
            if ResourceFairGraphBuilder.accessLevel(resource) == AccessLevel.FullOpen &&
              !content.isInstanceOf[StillImageExternalFileValueContentV2] =>
          advertise(project, resource, content)
        case _ => ZIO.none
      }
  }

  private def advertise(
    project: KnoraProject,
    resource: ReadResourceV2,
    content: FileValueContentV2,
  ): Task[Option[GrantedFile]] = {
    val granted = for {
      filename <- ZIO.fromEither(InternalFilename.from(content.fileValue.internalFilename)).mapError(new Exception(_))
      access   <- sources.assetAccess(KnoraSystemInstances.Users.AnonymousUser, filename)
    } yield access.original == OriginalAccess.Grant
    granted
      .catchAll(e =>
        ZIO.logWarning(s"Could not decide asset access of ${resource.resourceIri}: ${e.getMessage}").as(false),
      )
      .flatMap {
        case false => ZIO.none
        case true  =>
          for {
            link    <- sources.fileLink(project, content, resource.creationDate)
            license <-
              ZIO.foreach(content.fileValue.licenseIri)(sources.findLicense(_, project.shortcode)).map(_.flatten)
          } yield Some(GrantedFile(link, license))
      }
  }

  def assemble(dspAppUrl: String, in: FairGraphInputs): ResourceFairGraph = {
    val r = in.resource
    ResourceFairGraph(
      ark = sf.resourceIriToArkUrl(in.ref.resourceIri, in.ref.version.map(_.value)),
      pageUrl = s"$dspAppUrl/resource/${in.ref.resourceIri.shortcode.value}/${in.ref.resourceIri.resourceId.value}",
      title = r.label,
      creators = ResourceFairGraphBuilder.creators(r.resourceAuthorship, in.project.defaultDataAuthorship),
      dateCreated = r.creationDate,
      dateModified = r.lastModificationDate,
      license = in.projectLicense.map(_.uri.toString),
      copyrightHolder = in.project.dataCopyrightHolder.map(_.value),
      generalType = ResourceFairGraphBuilder.generalType(r),
      accessLevel = ResourceFairGraphBuilder.accessLevel(r),
      file = in.grantedFile.map { g =>
        FileFacts(
          contentUrl = g.link.url,
          name = g.link.fileName,
          encodingFormat = g.link.mimeType,
          contentSize = g.link.fileSize,
          license = g.license.map(_.uri.toString),
        )
      },
      projectArk = sf.projectIriToArkUrl(in.project.shortcode),
      projectShortcode = in.project.shortcode.value,
      projectName = in.project.longname.map(_.value).getOrElse(in.project.shortname.value),
      resourceClassIri = r.resourceClassIri.toString,
    )
  }
}

object ResourceFairGraphBuilder {

  val layer: URLayer[
    AppConfig & StringFormatter & ReadResourcesService & KnoraProjectService & LegalInfoService &
      AssetPermissionsResponder & AssetDownloadLinks,
    ResourceFairGraphBuilder,
  ] =
    ZLayer.derive[FairGraphSourcesLive].project[FairGraphSources](identity) >>> ZLayer.derive[ResourceFairGraphBuilder]

  private val OrcidUri = """^https://orcid\.org/\d{4}-\d{4}-\d{4}-\d{3}[\dX]$""".r

  /** The resource to publish, if the read returned one that is current, not deleted and exists at the version. */
  private[fair] def publicResource(ref: ResourceLandingRef, result: ReadResourcesSequenceV2): Option[ReadResourceV2] =
    result.resources
      .find(_.resourceIri == ref.resourceIri)
      .filter(r => r.deletionInfo.isEmpty && !ref.version.exists(_.value.isBefore(r.creationDate)))

  private[fair] def fileValues(r: ReadResourceV2) =
    r.values.values.flatten
      .flatMap(v =>
        v.valueContent match {
          case fc: FileValueContentV2 => Some((v, fc))
          case _                      => None
        },
      )
      .toSeq

  private[fair] def accessLevel(r: ReadResourceV2): AccessLevel =
    if (
      r.userPermission >= Permission.ObjectAccess.View &&
      fileValues(r).forall(_._1.userPermission >= Permission.ObjectAccess.View)
    ) AccessLevel.FullOpen
    else AccessLevel.Restricted

  private[fair] def generalType(r: ReadResourceV2): String =
    fileValues(r) match {
      case Seq((_, fc)) =>
        fc match {
          case _: StillImageFileValueContentV2 | _: StillImageExternalFileValueContentV2 => "Image"
          case _: MovingImageFileValueContentV2                                          => "Audiovisual"
          case _: AudioFileValueContentV2                                                => "Sound"
          case _: TextFileValueContentV2 | _: DocumentFileValueContentV2                 => "Text"
          case _                                                                         => "Dataset"
        }
      case _ => "Dataset"
    }

  private[fair] def creators(fromResource: Seq[Authorship], fromProject: Seq[Authorship]): Seq[Creator] =
    (if (fromResource.nonEmpty) fromResource else fromProject).map { a =>
      val name = a.value
      Creator(
        name = name,
        kind = if (name == "DaSCH") CreatorKind.Organization else CreatorKind.Person,
        orcid = Some(name).filter(OrcidUri.matches),
      )
    }
}
