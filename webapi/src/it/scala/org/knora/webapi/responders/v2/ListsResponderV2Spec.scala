/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.testkit.ImplicitSender

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetRequestV2
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetRequestV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * Tests [[ListsResponderV2]].
 */
class ListsResponderV2Spec extends CoreSpec with ImplicitSender {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val listsResponderV2SpecFullData              = new ListsResponderV2SpecFullData

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "The lists responder v2" should {
    "return a list" in {
      appActor ! ListGetRequestV2(
        listIri = "http://rdfh.ch/lists/0001/treeList",
        requestingUser = SharedTestDataADM.anythingUser2
      )

      expectMsgPF(timeout) { case response: ListGetResponseV2 =>
        assert(response == listsResponderV2SpecFullData.treeList)
      }
    }

    "return a node" in {
      appActor ! NodeGetRequestV2(
        nodeIri = "http://rdfh.ch/lists/0001/treeList11",
        requestingUser = SharedTestDataADM.anythingUser2
      )

      expectMsgPF(timeout) { case response: NodeGetResponseV2 =>
        assert(response == listsResponderV2SpecFullData.treeNode)
      }
    }
  }
}
