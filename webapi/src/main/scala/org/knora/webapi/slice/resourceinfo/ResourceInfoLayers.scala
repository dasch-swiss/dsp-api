/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo

import zio.ZLayer

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoutes
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepoLive
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ResourceInfoLayers {

  val live: ZLayer[
    TriplestoreService & IriConverter & BaseEndpoints & HandlerMapper & TapirToPekkoInterpreter,
    Nothing,
    RestResourceInfoService & ResourceInfoEndpoints & ResourceInfoRoutes,
  ] =
    ResourceInfoRepoLive.layer >>> RestResourceInfoService.layer >+> ResourceInfoEndpoints.layer >+> ResourceInfoRoutes.layer

}
