/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.Props
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.triplestore.http.HttpTriplestoreConnector
import org.knora.webapi.{IntegrationSpec, TestContainerFuseki}

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.DeleteCardinalitiesFromClass]].
 * Adding the [[TestContainerFuseki.PortConfig]] config will start the Fuseki container and make it
 * available to the test.
 */
class DeleteCardinalitiesFromClassSpec extends IntegrationSpec(TestContainerFuseki.PortConfig) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  // start fuseki http connector actor
  private val fusekiActor = system.actorOf(
    Props(new HttpTriplestoreConnector()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = "httpTriplestoreConnector"
  )

  override def beforeAll(): Unit = {
    waitForReadyTriplestore(fusekiActor)
    loadTestData(fusekiActor)
  }

  // use started actor in tests instead of the store manager
  "DeleteCardinalitiesFromClass" should {
    "check if a property entity is used in instances of TBD ..." in {
      val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
      val propertyIri         = AnythingOntologyIri.makeEntityIri("hasName")

      val resF = DeleteCardinalitiesFromClass.isPropertyUsedInClassesAndSubclasses(settings, fusekiActor, propertyIri)
      resF map { res => res should equal(true) }
    }
  }
}
