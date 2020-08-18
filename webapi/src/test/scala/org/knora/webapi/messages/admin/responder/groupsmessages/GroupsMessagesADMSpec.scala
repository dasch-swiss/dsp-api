/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.groupsmessages

import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object GroupsMessagesADMSpec {
    val config = ConfigFactory.parseString(
        """
              akka.loglevel = "DEBUG"
              akka.stdout-loglevel = "DEBUG"
            """.stripMargin)
}

/**
 * This spec is used to test 'GroupAdminMessages'.
 */
class GroupsMessagesADMSpec extends CoreSpec(GroupsMessagesADMSpec.config) {

    "The CreateGroupsApiRequestADM case class" should {

        "return 'BadRequest' if the supplied 'id' is not a valid IRI" in {

            val caught = intercept[BadRequestException](
                CreateGroupApiRequestADM(
                  id = Some("invalid-group-IRI"),
                  name = "NewGroupWithInvalidCustomIri",
                  description = Some("A new group with an invalid custom Iri"),
                  project = SharedTestDataADM.IMAGES_PROJECT_IRI,
                  status = true,
                  selfjoin = false
                )
            )
            assert(caught.getMessage === "Invalid group IRI")
        }
    }
}
