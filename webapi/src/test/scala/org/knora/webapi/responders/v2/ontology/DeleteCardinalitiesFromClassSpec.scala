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
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.triplestore.http.HttpTriplestoreConnector
import org.knora.webapi.{IntegrationSpec, InternalSchema, TestContainerFuseki}

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.DeleteCardinalitiesFromClass]].
 * Adding the [[TestContainerFuseki.PortConfig]] config will start the Fuseki container and make it
 * available to the test.
 */
class DeleteCardinalitiesFromClassSpec extends IntegrationSpec(TestContainerFuseki.PortConfig) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val additionalTestData = List(
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest")
  )

  // start fuseki http connector actor
  private val fusekiActor = system.actorOf(
    Props(new HttpTriplestoreConnector()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = "httpTriplestoreConnector"
  )

  override def beforeAll(): Unit = {
    waitForReadyTriplestore(fusekiActor)
    loadTestData(fusekiActor, additionalTestData)
  }

  // use started actor in tests instead of the store manager
  "DeleteCardinalitiesFromClass" should {
    "detect that property is in use, when used in an instance of parent class" in {
      val FreetestOntologyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri
      val internalPropertyIri = FreetestOntologyIri.makeEntityIri("hasText").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = DeleteCardinalitiesFromClass.isPropertyUsedInResources(settings, fusekiActor, internalPropertyIri)
      resF map { res => println(res); assert(res, "property is used in instance of parent class") }
    }

    "detect that property is in use, when used in an instance of subclass" in {
      val FreetestOntologyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri
      val internalPropertyIri = FreetestOntologyIri.makeEntityIri("hasDecimal").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = DeleteCardinalitiesFromClass.isPropertyUsedInResources(settings, fusekiActor, internalPropertyIri)
      resF map { res => println(res); assert(res, "property is used in instance of subclass") }
    }

    "detect that property is not in use, when not used in any instance" in {
      val FreetestOntologyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri
      val internalPropertyIri = FreetestOntologyIri.makeEntityIri("hasInteger").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = DeleteCardinalitiesFromClass.isPropertyUsedInResources(settings, fusekiActor, internalPropertyIri)
      resF map { res => println(res); assert(!res, "property is not used") }
    }
  }
}
