/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.lists

import sttp.tapir.*
import zio.*

import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

final class ListsEndpointsV2(base: BaseEndpoints) {
  private val publicReason      = "Lists are public."
  private val publicRequirement = Policy.Public(publicReason).requirement

  val listIri =
    path[ListIri]
      .name("listIri")
      .description("The iri to a list.")
      .example(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/" + UuidUtil.makeRandomBase64EncodedUuid))

  val getV2Lists = base
    .publicPolicyEndpointWithUser(publicReason)
    .get
    .in("v2" / "lists" / listIri)
    .in(ApiV2.Inputs.allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Returns a list (a graph with all list nodes). $publicRequirement")

  val getV2Node = base
    .publicPolicyEndpointWithUser(publicReason)
    .get
    .in("v2" / "node" / listIri)
    .in(ApiV2.Inputs.allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Returns a list node. $publicRequirement")
}

object ListsEndpointsV2 {
  val layer = ZLayer.derive[ListsEndpointsV2]
}
