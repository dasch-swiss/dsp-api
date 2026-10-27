/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.ZLayer

final class IngestProjectsServerEndpoints(endpoints: IngestProjectsEndpoints, rest: IngestProjectsRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getProjects.serverLogic(p => _ => rest.listProjects(p)),
    endpoints.getProjectByShortcode.serverLogic(rest.getProject),
    endpoints.getProjectChecksumReport.serverLogic(rest.getChecksumReport),
    endpoints.deleteProjectErase.serverLogic(rest.eraseProject),
    endpoints.getAssetInfo.serverLogic(p => rest.getAssetInfo(p)(_, _)),
    endpoints.getAssetOriginal.serverLogic(p => rest.getAssetOriginal(p)(_, _)),
    endpoints.postAssetIngest.serverLogic(p => rest.ingestAsset(p)(_, _, _)),
    endpoints.postBulkIngest.serverLogic(rest.startBulkIngest),
    endpoints.postBulkIngestFinalize.serverLogic(rest.finalizeBulkIngest),
    endpoints.getBulkIngestMappingCsv.serverLogic(rest.getBulkIngestMappingCsv),
    endpoints.postBulkIngestUpload.serverLogic(p => rest.uploadBulkIngestFile(p)(_, _, _)),
    endpoints.postExport.serverLogic(rest.exportProject),
    endpoints.postImport.serverLogic(p => rest.importProject(p)(_, _)),
  )
}

object IngestProjectsServerEndpoints {
  val layer = ZLayer.derive[IngestProjectsServerEndpoints]
}
