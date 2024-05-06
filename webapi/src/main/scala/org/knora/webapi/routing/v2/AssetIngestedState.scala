/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko.http.scaladsl.model.HttpHeader

sealed trait AssetIngestState

object AssetIngestState {
  case object AssetIngested extends AssetIngestState
  case object AssetInTemp   extends AssetIngestState

  def headerAssetIngestState(headers: Seq[HttpHeader]): AssetIngestState =
    if (headers.exists(_.name == "X-Asset-Ingested")) AssetIngested
    else AssetInTemp
}
