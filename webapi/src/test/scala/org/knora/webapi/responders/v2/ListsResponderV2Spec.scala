/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.testkit.ImplicitSender
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.listsmessages.{
  ListGetRequestV2,
  ListGetResponseV2,
  NodeGetRequestV2,
  NodeGetResponseV2
}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.duration._

object ListsResponderV2Spec {
  private val userProfile = SharedTestDataADM.anythingUser2
}

/**
 * Tests [[ListsResponderV2]].
 */
class ListsResponderV2Spec extends CoreSpec() with ImplicitSender {

  import ListsResponderV2Spec._

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val listsResponderV2SpecFullData = new ListsResponderV2SpecFullData

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  // The default timeout for receiving reply messages from actors.
  private val timeout = 10.seconds

  "The lists responder v2" should {

    "return a list" in {

      responderManager ! ListGetRequestV2(
        listIri = "http://rdfh.ch/lists/0001/treeList",
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = userProfile
      )

      expectMsgPF(timeout) { case response: ListGetResponseV2 =>
        assert(response == listsResponderV2SpecFullData.treeList)

      }
    }

    "return a node" in {

      responderManager ! NodeGetRequestV2(
        nodeIri = "http://rdfh.ch/lists/0001/treeList11",
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = userProfile
      )

      expectMsgPF(timeout) { case response: NodeGetResponseV2 =>
        assert(response == listsResponderV2SpecFullData.treeNode)
      }

    }

  }

}
