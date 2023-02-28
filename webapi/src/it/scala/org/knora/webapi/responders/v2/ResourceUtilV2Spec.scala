/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.util.Timeout
import zio.ZIO
import scala.concurrent.ExecutionContextExecutor

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.UnsafeZioRun

object ResourceUtilV2Spec {}

class ResourceUtilV2Spec extends CoreSpec {
  implicit val timeout: Timeout             = appConfig.defaultTimeoutAsDuration
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "ResourceUtil" when {
    "called `checkListNodeExistsAndIsRootNode` method" should {
      "return FALSE for list child node" in {
        val nodeIri = "http://rdfh.ch/lists/0001/otherTreeList01"
        val checkNode = UnsafeZioRun.runToFuture(
          ZIO
            .service[ResourceUtilV2]
            .map(_.checkListNodeExistsAndIsRootNode(nodeIri))
        )

        checkNode.onComplete(_.get should be(Right(false)))
      }

      "return TRUE for list root node" in {
        val nodeIri = "http://rdfh.ch/lists/0001/otherTreeList"
        val checkNode = UnsafeZioRun.runToFuture(
          ZIO
            .service[ResourceUtilV2]
            .map(_.checkListNodeExistsAndIsRootNode(nodeIri))
        )

        checkNode.onComplete(_.get should be(Right(true)))
      }

      "should return NONE for nonexistent list" in {
        val nodeIri = "http://rdfh.ch/lists/0001/otherTreeList77"
        val checkNode = UnsafeZioRun.runToFuture(
          ZIO
            .service[ResourceUtilV2]
            .map(_.checkListNodeExistsAndIsRootNode(nodeIri))
        )

        checkNode.onComplete(_.get should be(Left(None)))
      }
    }
  }
}
