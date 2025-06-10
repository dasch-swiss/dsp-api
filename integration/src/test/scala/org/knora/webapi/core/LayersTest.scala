/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor.ActorSystem
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.routing.*
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.service.ProjectExportStorageService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.testservices.TestDspIngestClient

object LayersTest { self =>

  type Environment =
    // format: off
    LayersLive.Environment&
    TestContainerLayers.Environment &
    MessageRelayActorRef &
    TestClientService &
    TestDspIngestClient
    // format: on

  /**
   * Provides a layer for integration tests which depend on Fuseki and Sipi as Testcontainers.
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithSipi]]
   */
  val layer: ULayer[self.Environment] =
    ZLayer.make[self.Environment](
      HttpClientZioBackend.layer().orDie,
      MessageRelayActorRef.layer,
      TestClientService.layer,
      TestContainerLayers.all,
      TestDspIngestClient.layer,
      /// common
      LayersLive.layer,
    )
}
