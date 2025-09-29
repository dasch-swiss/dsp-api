/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.reflect.ClassTag

import org.knora.webapi.core.Db
import org.knora.webapi.core.LayersTest
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.util.Logger

abstract class E2EZSpec extends ZIOSpec[LayersTest.Environment] {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance
  // test data
  val rootUser: User = SharedTestDataADM.rootUser

  override val bootstrap: ULayer[LayersTest.Environment] = Logger.text >>> LayersTest.layer

  def rdfDataObjects: List[RdfDataObject] = List.empty

  type env = LayersTest.Environment with Scope

  private def prepare = Db.initWithTestData(rdfDataObjects) *> ZIO.serviceWithZIO[CacheManager](_.clearAll())

  def e2eSpec: Spec[env, Any]

  final override def spec: Spec[env, Any] =
    e2eSpec.provideSomeAuto(Scope.default)
      @@ TestAspect.beforeAll(prepare)
      @@ TestAspect.sequential
      @@ TestAspect.withLiveEnvironment
}

object E2EZSpec {
  def failsWithMessageEqualTo[A <: Throwable](messsage: String)(implicit
    tag: ClassTag[A],
  ): Assertion[Exit[Any, Any]] =
    fails(isSubtype[A](hasMessage(equalTo(messsage))))

  def failsWithMessageContaining[A <: Throwable](messsage: String)(implicit
    tag: ClassTag[A],
  ): Assertion[Exit[Any, Any]] =
    fails(isSubtype[A](hasMessage(containsString(messsage))))
}
