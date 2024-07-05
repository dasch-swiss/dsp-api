/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.apache.pekko
import org.apache.pekko.testkit.ImplicitSender
import zio.*

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * Tests [[ListsResponderV2]].
 */
class ListsResponderV2Spec extends CoreSpec with ImplicitSender {
  private val listsResponderV2SpecFullData = new ListsResponderV2SpecFullData
  private val responder                    = ZIO.serviceWith[ListsResponderV2]

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  "The lists responder v2" should {
    "return a list" in {
      val response = UnsafeZioRun.runOrThrow(
        responder(_.getList("http://rdfh.ch/lists/0001/treeList", SharedTestDataADM.anythingUser2)),
      )
      assert(response == listsResponderV2SpecFullData.treeList)
    }

    "return a node" in {
      val response = UnsafeZioRun.runOrThrow(
        responder(_.getNode("http://rdfh.ch/lists/0001/treeList11", SharedTestDataADM.anythingUser2)),
      )
      assert(response == listsResponderV2SpecFullData.treeNode)
    }
  }
}
