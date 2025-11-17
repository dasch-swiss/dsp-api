/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api

import sttp.tapir.*
import zio.*

import dsp.valueobjects.UuidUtil
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages.ListADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListChildNodeADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.slice.common.domain.LanguageCode.EN

final case class ListsEndpointsV2(private val base: BaseEndpoints) {
  val listIri =
    path[ListIri](TapirCodec.listIri)
      .name("listIri")
      .description("The iri to a list.")
      .example(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/" + UuidUtil.makeRandomBase64EncodedUuid))

  val getV2Lists = base.withUserEndpoint.get
    .in("v2" / "lists" / listIri)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Returns a list (a graph with all list nodes).")

  val getV2Node = base.withUserEndpoint.get
    .in("v2" / "node" / listIri)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Returns a list node.")
}

object ListsEndpointsV2 {
  val layer = ZLayer.derive[ListsEndpointsV2]
}

private object Examples {

  val appConfig: AppConfig = UnsafeZioRun.runOrThrow(for {
    config <- AppConfig.parseConfig
    _       = StringFormatter.init(config)
  } yield config)(Runtime.default)

  val listGetResponseV2: ListGetResponseV2 = ListGetResponseV2(
    list = ListADM(
      listinfo = ListRootNodeInfoADM(
        id = "http://rdfh.ch/lists/0001/treeList",
        projectIri = "http://rdfh.ch/projects/0001",
        name = Some("Tree list root"),
        labels = StringLiteralSequenceV2(
          Vector(
            StringLiteralV2.from("Tree list root", language = EN),
            StringLiteralV2.from("Listenwurzel", language = DE),
          ),
        ),
        comments = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Anything Tree List", EN))),
      ).sorted,
      children = Seq(
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList01",
          name = Some("Tree list node 01"),
          labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 01", EN))),
          comments = StringLiteralSequenceV2.empty,
          children = Seq.empty[ListChildNodeADM],
          position = 0,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList02",
          name = Some("Tree list node 02"),
          labels = StringLiteralSequenceV2(
            Vector(
              StringLiteralV2.from("Baumlistenknoten 02", DE),
              StringLiteralV2.from("Tree list node 02", EN),
            ),
          ),
          comments = StringLiteralSequenceV2.empty,
          children = Seq.empty[ListChildNodeADM],
          position = 1,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList03",
          name = Some("Tree list node 03"),
          labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 03", EN))),
          comments = StringLiteralSequenceV2.empty,
          children = Seq(
            ListChildNodeADM(
              id = "http://rdfh.ch/lists/0001/treeList10",
              name = Some("Tree list node 10"),
              labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 10", EN))),
              comments = StringLiteralSequenceV2.empty,
              children = Seq.empty[ListChildNodeADM],
              position = 0,
              hasRootNode = "http://rdfh.ch/lists/0001/treeList",
            ),
            ListChildNodeADM(
              id = "http://rdfh.ch/lists/0001/treeList11",
              name = Some("Tree list node 11"),
              labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 11", EN))),
              comments = StringLiteralSequenceV2.empty,
              children = Seq.empty[ListChildNodeADM],
              position = 1,
              hasRootNode = "http://rdfh.ch/lists/0001/treeList",
            ),
          ),
          position = 2,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
      ),
    ),
    "de",
    "en",
  )

  val nodeGetResponseV2: NodeGetResponseV2 = NodeGetResponseV2(
    node = listsmessages.ListChildNodeInfoADM(
      name = Some("Tree list node 11"),
      id = "http://rdfh.ch/lists/0001/treeList11",
      labels = StringLiteralSequenceV2(
        stringLiterals = Vector(
          StringLiteralV2.from(
            value = "Tree list node 11",
            EN,
          ),
        ),
      ),
      position = 1,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList",
      comments = StringLiteralSequenceV2.empty,
    ),
    userLang = "de",
    fallbackLang = "en",
  )
}
