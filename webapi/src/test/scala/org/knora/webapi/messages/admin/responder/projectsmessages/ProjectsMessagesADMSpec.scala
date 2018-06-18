/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test subclasses of the [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]] class.
  */
class ProjectsMessagesADMSpec extends WordSpecLike with Matchers {

    private val id = SharedTestDataADM.rootUser.id
    private val email = SharedTestDataADM.rootUser.email
    private val password = SharedTestDataADM.rootUser.password
    private val token = SharedTestDataADM.rootUser.token
    private val givenName = SharedTestDataADM.rootUser.givenName
    private val familyName = SharedTestDataADM.rootUser.familyName
    private val status = SharedTestDataADM.rootUser.status
    private val lang = SharedTestDataADM.rootUser.lang
    private val groups = SharedTestDataADM.rootUser.groups
    private val projects = SharedTestDataADM.rootUser.projects
    private val sessionId = SharedTestDataADM.rootUser.sessionId
    private val permissions = SharedTestDataADM.rootUser.permissions

    "The CreateProjectApiRequestADM case class" should {

        "return a 'BadRequest' when project description is not supplied" in {
            assertThrows[BadRequestException](
                CreateProjectApiRequestADM(
                    shortname = "newproject5",
                    shortcode = "1114",
                    longname = Some("project longname"),
                    description = Seq.empty[StringLiteralV2],
                    keywords = Seq("keywords"),
                    logo = Some("/fu/bar/baz.jpg"),
                    status = true,
                    selfjoin = false
                )
            )
        }
    }

    "The ChangeProjectApiRequestADM case class" should {

        "return a 'BadRequest' when everything is 'None" in {
            assertThrows[BadRequestException](
                ChangeProjectApiRequestADM(
                    shortname = None,
                    longname = None,
                    description = None,
                    keywords = None,
                    logo = None,
                    status = None,
                    selfjoin = None
                )
            )
        }
    }

    "The ProjectADM case class" should {

        "return a 'OntologyConstraintException' when project description is not supplied" in {
            assertThrows[OntologyConstraintException](
                ProjectADM(
                    id = "id",
                    shortcode = "1111",
                    shortname = "shortname",
                    longname = None,
                    description = Seq.empty[StringLiteralV2],
                    keywords = Seq.empty[String],
                    logo = None,
                    ontologies = Seq.empty[IRI],
                    status = true,
                    selfjoin = false
                )
            )
        }
    }
}
