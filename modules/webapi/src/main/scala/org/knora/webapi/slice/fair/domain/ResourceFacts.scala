/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.ports.*

final case class ResourceFacts(
  iri: ResourceIri,
  ark: String,
  pageUrl: String,
  title: String,
  description: Option[String],
  creators: Creators,
  licence: Option[ResourceLicence],
  dateCreated: Instant,
  dateModified: Option[Instant],
  typeOfData: Option[String],
  classIri: String,
  classLabel: String,
  file: Option[FileFacts],
  accessLevel: AccessLevel,
  projectArk: String,
  shortcode: Shortcode,
)

final case class Creators(names: List[String], source: CreatorSource)

enum CreatorSource {
  case Authorship
  case ProjectDefault

  /** The single name is the project's longname or shortname; used in DataCite only, as an organizational creator. */
  case ProjectOrganisation
}

/** `uri` is None for a declaration. */
final case class ResourceLicence(uri: Option[String], label: String, spdxId: Option[String])

final case class FileFacts(contentUrl: String, mimeType: Option[String], fileName: Option[String], size: Option[Long])

enum AccessLevel(val coarUri: String, val text: String) {
  case FullOpenAccess extends AccessLevel("http://purl.org/coar/access_right/c_abf2", "Full Open Access")
  case OpenAccessWithRestrictions
      extends AccessLevel("http://purl.org/coar/access_right/c_16ec", "Open Access with Restrictions")
}

/** Everything [[ResourceFacts.derive]] needs, already looked up. */
final case class FactsInputs(
  resource: FairResource,
  project: FairProject,
  fileLicence: LicenceLookup,
  projectLicence: LicenceLookup,
  fairClass: FairClass,
  sidecar: Option[FairAsset],
  ark: String,
  projectArk: String,
  appExternalBaseUrl: String,
)

/** The outcome of looking up a licence IRI. */
enum LicenceLookup {

  /** No licence IRI is recorded. */
  case NotRecorded

  /** An IRI is recorded but is not a built-in licence. */
  case Unresolved
  case Resolved(licence: FairLicence)
}

object ResourceFacts {

  /** The facts of the resource as the anonymous user sees it; None when the resource is not visible at all. */
  def derive(in: FactsInputs): Option[ResourceFacts] =
    Option.when(in.resource.userPermission >= ObjectAccess.RestrictedView)(build(in))

  private def build(in: FactsInputs): ResourceFacts = {
    val r = in.resource
    ResourceFacts(
      iri = r.iri,
      ark = in.ark,
      pageUrl = pageUrl(in),
      title = r.label,
      description = r.description,
      creators = creators(in),
      licence = licence(in),
      dateCreated = r.creationDate,
      dateModified = r.lastModificationDate,
      typeOfData = r.file.flatMap(_.typeOfData),
      classIri = r.classIri,
      classLabel = classLabel(in),
      file = fileFacts(in),
      accessLevel = accessLevel(in),
      projectArk = in.projectArk,
      shortcode = r.iri.shortcode,
    )
  }

  private def pageUrl(in: FactsInputs): String =
    s"${in.appExternalBaseUrl.stripSuffix("/")}/resource/${in.resource.iri.shortcode.value}/${in.resource.iri.resourceId.value}"

  private def classLabel(in: FactsInputs): String =
    in.fairClass.labels
      .get("en")
      .filter(_.nonEmpty)
      .orElse(in.fairClass.labels.minByOption(_._1).map(_._2))
      .getOrElse(in.resource.classIri)

  private def fileFacts(in: FactsInputs): Option[FileFacts] =
    in.resource.file
      .filter(f => f.userPermission >= ObjectAccess.View && f.kind == FairFileKind.Stored)
      .flatMap(_.originalUrl)
      .map(url =>
        FileFacts(url, in.sidecar.flatMap(_.mimeType), in.sidecar.flatMap(_.fileName), in.sidecar.flatMap(_.size)),
      )

  private def accessLevel(in: FactsInputs): AccessLevel = {
    val r          = in.resource
    val fileAllows = r.file match {
      case Some(f) => f.userPermission >= ObjectAccess.View
      case None    => !in.fairClass.requiresFile
    }
    if (r.userPermission >= ObjectAccess.View && fileAllows) AccessLevel.FullOpenAccess
    else AccessLevel.OpenAccessWithRestrictions
  }

  private def creators(in: FactsInputs): Creators = {
    val fromAuthorship = (in.resource.authorship ++ in.resource.file.toList.flatMap(_.authorship)).distinct
    lazy val default   = in.project.defaultDataAuthorship.distinct
    if (fromAuthorship.nonEmpty) Creators(fromAuthorship, CreatorSource.Authorship)
    else if (default.nonEmpty) Creators(default, CreatorSource.ProjectDefault)
    else
      Creators(
        List(in.project.longname.filter(_.nonEmpty).getOrElse(in.project.shortname)),
        CreatorSource.ProjectOrganisation,
      )
  }

  private def licence(in: FactsInputs): Option[ResourceLicence] = {
    val fileLookup = if (in.resource.file.isDefined) in.fileLicence else LicenceLookup.NotRecorded
    fileLookup match {
      case LicenceLookup.Unresolved                                                             => None
      case LicenceLookup.Resolved(l @ (FairLicence.Declaration(_) | FairLicence.Licence(_, _))) => toLicence(l)
      case _                                                                                    =>
        in.projectLicence match {
          case LicenceLookup.Resolved(l) => toLicence(l)
          case _                         => None
        }
    }
  }

  private def toLicence(l: FairLicence): Option[ResourceLicence] = l match {
    case FairLicence.Placeholder         => None
    case FairLicence.Declaration(label)  => Some(ResourceLicence(None, label, None))
    case FairLicence.Licence(uri, label) =>
      spdx.get(uri) match {
        case Some((id, spdxLabel)) => Some(ResourceLicence(Some(uri), spdxLabel, Some(id)))
        case None                  => Some(ResourceLicence(Some(uri), label, None))
      }
  }

  private val spdx: Map[String, (String, String)] = Map(
    "https://creativecommons.org/licenses/by/4.0/"    -> ("CC-BY-4.0", "Creative Commons Attribution 4.0 International"),
    "https://creativecommons.org/licenses/by-sa/4.0/" ->
      ("CC-BY-SA-4.0", "Creative Commons Attribution-ShareAlike 4.0 International"),
    "https://creativecommons.org/licenses/by-nc/4.0/" ->
      ("CC-BY-NC-4.0", "Creative Commons Attribution-NonCommercial 4.0 International"),
    "https://creativecommons.org/licenses/by-nc-sa/4.0/" ->
      ("CC-BY-NC-SA-4.0", "Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International"),
    "https://creativecommons.org/licenses/by-nd/4.0/" ->
      ("CC-BY-ND-4.0", "Creative Commons Attribution-NoDerivatives 4.0 International"),
    "https://creativecommons.org/licenses/by-nc-nd/4.0/" ->
      ("CC-BY-NC-ND-4.0", "Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International"),
    "https://creativecommons.org/publicdomain/zero/1.0/" -> ("CC0-1.0", "Creative Commons Public Domain Dedication"),
  )
}
