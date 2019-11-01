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

package org.knora.webapi.e2e.admin

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.{ITKnoraLiveSpec, SharedTestDataADM}

import scala.concurrent.duration._

object PermissionsADMITSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Test specification for testing the 'admin/permissions' route.
  */
class PermissionsADMITSpec extends ITKnoraLiveSpec(PermissionsADMITSpec.config) with TriplestoreJsonProtocol {

    val rootCreds = CredentialsV1(
        SharedTestDataADM.rootUser.id,
        SharedTestDataADM.rootUser.email,
        "test"
    )

    val projectAdminCreds = CredentialsV1(
        SharedTestDataADM.imagesUser01.id,
        SharedTestDataADM.imagesUser01.email,
        "test"
    )

    val normalUserCreds = CredentialsV1(
        SharedTestDataADM.normalUser.id,
        SharedTestDataADM.normalUser.email,
        "test"
    )

    "The Permissions Route ('admin/permissions/projectIri/groupIri')" should {

        "return all administrative permissions for the images project" in {
            val projectIri = java.net.URLEncoder.encode(SharedTestDataADM.imagesProject.id, "utf-8")

            val request = Get(baseApiUrl + s"/admin/permissions/$projectIri") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
            val response = singleAwaitingRequest(request, 3.seconds)
            logger.info("==>> " + response.toString)
            assert(response.status === StatusCodes.OK)
        }
    }
}