package org.knora.webapi.slice.lists.api

import sttp.tapir.*
import zio.ZLayer

import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class ListsEndpointsV2(private val base: BaseEndpoints) {
  val listIri =
    path[ListIri](TapirCodec.listIri)
      .name("listIri")
      .description("The iri to a list.")
      .example(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/" + UuidUtil.makeRandomBase64EncodedUuid))

  val getV2Lists = base.withUserEndpoint
    .in("v2" / "lists" / listIri)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .description("Returns a list (a graph with all list nodes).")

  val getV2Node = base.withUserEndpoint
    .in("v2" / "node" / listIri)
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .description("Returns a list node.")

  val endpoints: Seq[AnyEndpoint] = Seq(
    getV2Lists,
    getV2Node,
  ).map(_.endpoint.tag("v2"))
}

object ListsEndpointsV2 {
  val layer = ZLayer.derive[ListsEndpointsV2]
}
