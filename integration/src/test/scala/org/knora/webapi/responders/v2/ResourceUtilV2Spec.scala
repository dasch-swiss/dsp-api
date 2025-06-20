/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*
import zio.test.*
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object ResourceUtilV2Spec extends E2EZSpec {

  private val resourceUtil = ZIO.serviceWithZIO[ResourceUtilV2]

  override lazy val rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  override val e2eSpec = suite("ResourceUtil")(
    suite("checkListNodeExistsAndIsRootNode should")(
      test("return FALSE for list child node") {
        resourceUtil(_.checkListNodeExistsAndIsRootNode("http://rdfh.ch/lists/0001/otherTreeList01"))
          .map(actual => assertTrue(actual == Right(false)))
      },
      test("return TRUE for list root node") {
        resourceUtil(_.checkListNodeExistsAndIsRootNode("http://rdfh.ch/lists/0001/otherTreeList"))
          .map(actual => assertTrue(actual == Right(true)))
      },
      test("should return NONE for nonexistent list") {
        resourceUtil(_.checkListNodeExistsAndIsRootNode("http://rdfh.ch/lists/0001/otherTreeList77"))
          .map(actual => assertTrue(actual == Left(None)))
      },
    ),
  )
}
