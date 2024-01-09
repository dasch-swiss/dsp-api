/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.{MaintenanceEndpointsHandler, MonitoringEndpointsHandler, ProjectsEndpointsHandler}
import swiss.dasch.version.BuildInfo
import zio.{Task, ZLayer}

final case class Endpoints(
  private val monitoring: MonitoringEndpointsHandler,
  private val projects: ProjectsEndpointsHandler,
  private val maintenance: MaintenanceEndpointsHandler
) {
  val api = monitoring.endpoints ++ projects.endpoints ++ maintenance.endpoints

  val endpoints: List[ZServerEndpoint[Any, ZioStreams]] = {
    val docs = createDocs(api)
    api ++ docs
  }

  private def createDocs(apiEndpoints: List[ZServerEndpoint[Any, ZioStreams]]): List[ZServerEndpoint[Any, ZioStreams]] =
    SwaggerInterpreter().fromServerEndpoints[Task](apiEndpoints, BuildInfo.name, BuildInfo.version)
}

object Endpoints {
  val layer = ZLayer.derive[Endpoints]
}
