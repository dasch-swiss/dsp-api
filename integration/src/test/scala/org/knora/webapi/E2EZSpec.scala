/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*
import zio.http.*
import zio.test.*

import org.knora.webapi.core.Db
import org.knora.webapi.core.LayersTest
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.domain.model.User

abstract class E2EZSpec extends ZIOSpecDefault with TestStartupUtils {

  // test data
  val rootUser: User = org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser

  private val testLayers = util.Logger.text() >>> LayersTest.layer

  def rdfDataObjects: List[RdfDataObject] = List.empty[RdfDataObject]

  type env = LayersTest.Environment with Client with Scope

  private def prepare = Db.initWithTestData(rdfDataObjects)

  def e2eSpec: Spec[env, Any]

  override def spec = (
    e2eSpec
      @@ TestAspect.beforeAll(prepare)
      @@ TestAspect.sequential
  ).provideShared(testLayers, Client.default, Scope.default)
    @@ TestAspect.withLiveEnvironment
}
