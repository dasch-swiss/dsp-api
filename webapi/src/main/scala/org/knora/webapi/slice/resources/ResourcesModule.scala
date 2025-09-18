/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources

import zio.URLayer

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.repo.ResourceInfoRepoLive
import org.knora.webapi.slice.resources.service.*
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ResourcesModule { self =>
  type Dependencies = IriConverter & KnoraProjectService & LegalInfoService & StringFormatter & TriplestoreService
  type Provided     = MetadataService & ResourceInfoRepoLive & ValueContentValidator
  val layer: URLayer[Dependencies, Provided] =
    MetadataService.layer >+> ResourceInfoRepoLive.layer >+> ValueContentValidator.layer
}
