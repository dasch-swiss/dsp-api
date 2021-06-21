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

import org.knora.webapi.{TestContainerFuseki, UnitSpec}
import org.knora.webapi.messages.StringFormatter

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.DeleteCardinalitiesFromClass]].
 * Adding the [[TestContainerFuseki.PortConfig]] config will start the Fuseki container and make it
 * available to the test.
 */
class DeleteCardinalitiesFromClassSpec extends UnitSpec(TestContainerFuseki.PortConfig) {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: scala.concurrent.ExecutionContext      = scala.concurrent.ExecutionContext.global

}
