/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import monocle.*
import monocle.macros.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.License

object ValueMessagesV2Optics {

  object FileValueV2Optics {

    val copyrightAttributionLens: Lens[FileValueV2, Option[CopyrightAttribution]] =
      GenLens[FileValueV2](_.copyrightAttribution)

    val licenseLens: Lens[FileValueV2, Option[License]] =
      GenLens[FileValueV2](_.license)

  }

  object FileValueContentV2Optics {

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

  }

  object ReadValueV2Optics {

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

    val fileValueFromReadValue: Optional[ReadValueV2, FileValueV2] =
      ReadValueV2Optics.fileValueContentLens
        .andThen(ValueContentV2Optics.fileValueContentPrism)
        .andThen(FileValueContentV2Optics.fileValueLens)

  }

  object ValueContentV2Optics {

    val fileValueContentPrism: Prism[ValueContentV2, FileValueContentV2] = GenPrism[ValueContentV2, FileValueContentV2]

  }
}
