/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.`export`

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

// lang_string: map from ISO language code to localized string, e.g. {"en": "...", "de": "..."}
type LangString = Map[String, String]

final case class License(
  licenseIdentifier: String,
  licenseDate: String,
  licenseUri: String,
)

object License {
  implicit val codec: JsonCodec[License] = DeriveJsonCodec.gen[License]
}

final case class LegalInfo(
  license: License,
  copyrightHolder: String,
  authorship: List[String],
)

object LegalInfo {
  implicit val codec: JsonCodec[LegalInfo] = DeriveJsonCodec.gen[LegalInfo]

  // All metadata is considered public domain per the deposit agreement.
  val publicDomain: LegalInfo = LegalInfo(
    license = License(
      licenseIdentifier = "public domain",
      licenseDate = "2023-01-01",
      licenseUri = "https://creativecommons.org/publicdomain/zero/1.0/",
    ),
    copyrightHolder = "DaSCH",
    authorship = List("DaSCH"),
  )
}

final case class MetadataRecord(
  id: String,
  pid: String,
  label: LangString,
  accessRights: String,
  legalInfo: LegalInfo = LegalInfo.publicDomain,
  howToCite: String,
  publisher: String,
  source: Option[String],
  description: Option[LangString],
  dateCreated: Option[String],
  dateModified: Option[String],
  datePublished: Option[String],
  typeOfData: Option[String],
  size: Option[String],
  keywords: List[LangString],
)

object MetadataRecord {
  implicit val codec: JsonCodec[MetadataRecord] = DeriveJsonCodec.gen[MetadataRecord]
}
