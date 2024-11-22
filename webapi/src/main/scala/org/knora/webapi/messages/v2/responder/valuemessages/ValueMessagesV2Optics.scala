/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import monocle.*
import monocle.Optional
import monocle.macros.*

import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.License

object ValueMessagesV2Optics {

  object FileValueV2Optics {

    val copyrightAttributionOption: Lens[FileValueV2, Option[CopyrightAttribution]] =
      GenLens[FileValueV2](_.copyrightAttribution)

    val licenseOption: Lens[FileValueV2, Option[License]] =
      GenLens[FileValueV2](_.license)

  }

  object FileValueContentV2Optics {

    val fileValueV2: Lens[FileValueContentV2, FileValueV2] =
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

  object LinkValueContentV2Optics {

    val nestedResource: Optional[LinkValueContentV2, ReadResourceV2] =
      Optional[LinkValueContentV2, ReadResourceV2](_.nestedResource)(rr => lv => lv.copy(nestedResource = Some(rr)))

  }

  object ReadValueV2Optics {

    val fileValueContentV2: Optional[ReadValueV2, FileValueContentV2] =
      Optional[ReadValueV2, FileValueContentV2](_.valueContent.asOpt[FileValueContentV2])(fc => {
        case rv: ReadLinkValueV2  => rv
        case rv: ReadTextValueV2  => rv
        case ov: ReadOtherValueV2 => ov.copy(valueContent = fc)
      })

    val fileValueV2: Optional[ReadValueV2, FileValueV2] =
      ReadValueV2Optics.fileValueContentV2.andThen(FileValueContentV2Optics.fileValueV2)

    val linkValueContentV2: Optional[ReadValueV2, LinkValueContentV2] =
      Optional[ReadValueV2, LinkValueContentV2](_.valueContent.asOpt[LinkValueContentV2])(lv => {
        case rv: ReadLinkValueV2  => rv.copy(valueContent = lv)
        case rv: ReadOtherValueV2 => rv.copy(valueContent = lv)
        case rv: ReadTextValueV2  => rv
      })

    val nestedResourceOfLinkValueContent: Optional[ReadValueV2, ReadResourceV2] =
      ReadValueV2Optics.linkValueContentV2.andThen(LinkValueContentV2Optics.nestedResource)

    def elements(predicate: ReadValueV2 => Boolean): Optional[Seq[ReadValueV2], ReadValueV2] =
      Optional[Seq[ReadValueV2], ReadValueV2](_.find(predicate))(newValue =>
        values =>
          values.map {
            case v if predicate(v) => newValue
            case other             => other
          },
      )
  }
}
