/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2
import org.knora.webapi.messages.SmartIri
import monocle.macros.*
import monocle.*
import monocle.Lens
import monocle.Optional
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.License
import org.knora.webapi.slice.admin.domain.model.Permission
import zio.test.*

import java.time.Instant
import java.util.UUID

object ReadResourceV2LensLearningSpec extends ZIOSpecDefault {

  type ReadValues = Map[SmartIri, Seq[ReadValueV2]]
  val valuesLens: Lens[ReadResourceV2, ReadValues] =
    GenLens[ReadResourceV2](_.values)

  val fileValueContentLens: Lens[ReadValueV2, ValueContentV2] =
    Lens[ReadValueV2, ValueContentV2](_.valueContent)(fc => {
      case rv: ReadLinkValueV2 =>
        fc match {
          case lv: LinkValueContentV2 => rv.copy(valueContent = lv)
          case _                      => rv
        }
      case rv: ReadTextValueV2 =>
        fc match {
          case tv: TextValueContentV2 => rv.copy(valueContent = tv)
          case _                      => rv
        }
      case ov: ReadOtherValueV2 => ov.copy(valueContent = fc)
    })

  val fileValueContentPrism: Prism[ValueContentV2, FileValueContentV2] =
    GenPrism[ValueContentV2, FileValueContentV2]

  val fileValueLens: Lens[FileValueContentV2, FileValueV2] =
    Lens[FileValueContentV2, FileValueV2](_.fileValue)(fv => {
      case vc: MovingImageFileValueContentV2        => vc.copy(fileValue = fv)
      case vc: StillImageFileValueContentV2         => vc.copy(fileValue = fv)
      case vc: AudioFileValueContentV2              => vc.copy(fileValue = fv)
      case vc: DocumentFileValueContentV2           => vc.copy(fileValue = fv)
      case vc: StillImageExternalFileValueContentV2 => vc.copy(fileValue = fv)
      case vc: ArchiveFileValueContentV2            => vc.copy(fileValue = fv)
      case vc: TextFileValueContentV2               => vc.copy(fileValue = fv)
    })

  val copyrightAttributionLens: Lens[FileValueV2, Option[CopyrightAttribution]] =
    GenLens[FileValueV2](_.copyrightAttribution)
  val licenseLens: Lens[FileValueV2, Option[License]] = GenLens[FileValueV2](_.license)

  val sf = StringFormatter.getInitializedTestInstance

  val aLicense       = License.unsafeFrom("CC-BY-4.0")
  val anotherLicense = License.unsafeFrom("Apache-2.0")

  val aCopyrightAttribution       = CopyrightAttribution.unsafeFrom("2020, John Doe")
  val anotherCopyrightAttribution = CopyrightAttribution.unsafeFrom("2024, Jane Doe")

  val fileValueWithoutLicenseOrCopyright =
    FileValueV2("internalFilename", "internalMimeType", None, None, None, None)
  val fileValueWithALicense =
    FileValueV2("internalFilename", "internalMimeType", None, None, None, Some(aLicense))
  val fileValueWithACopyrightAttribution =
    FileValueV2("internalFilename", "internalMimeType", None, None, Some(aCopyrightAttribution), None)

  val setLicenseIfMissing: Option[License] => ReadValueV2 => ReadValueV2 = newLicense =>
    value => {
      val composed = fileValueContentLens.andThen(fileValueContentPrism).andThen(fileValueLens).andThen(licenseLens)
      val existing: Option[License] =composed.getOption(value).flatten
      existing match {
        case Some(_) => value
        case None    => composed.replace(newLicense)(value)
      }
    }

  private val fileValueLensesSuite = suite("FileValueV2 lenses")(
    test("licenseLens should replace an empty license with a new one") {
      val actual = licenseLens.replace(Some(aLicense))(fileValueWithoutLicenseOrCopyright)
      assertTrue(actual.license == Some(aLicense))
    },
    test("licenseLens should replace an existing license with a new one") {
      val actual = licenseLens.replace(Some(anotherLicense))(fileValueWithALicense)
      assertTrue(actual.license == Some(anotherLicense))
    },
    test("copyrightAttributionLens should replace an empty copyrightAttribution with a new one") {
      val aCopyrightAttribution = CopyrightAttribution.unsafeFrom("2020, John Doe")
      val actual                = copyrightAttributionLens.replace(Some(aCopyrightAttribution))(fileValueWithoutLicenseOrCopyright)
      assertTrue(actual.copyrightAttribution == Some(aCopyrightAttribution))
    },
    test("copyrightAttributionLens should replace an existing copyrightAttribution with a new one") {
      val actual =
        copyrightAttributionLens.replace(Some(anotherCopyrightAttribution))(fileValueWithACopyrightAttribution)
      assertTrue(actual.copyrightAttribution == Some(anotherCopyrightAttribution))
    },
  )

  val spec = suite("ReadResourceV2LensLearning")(
    fileValueLensesSuite,
  )
}
