/*
 * Copyright © 2015-2020 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package webapi.src.test.scala.org.knora.webapi.http.version

import akka.http.scaladsl.model.headers.Server
import org.knora.webapi.http.version.ServerVersion
import org.scalatest.PrivateMethodTester
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class ServerVersionSpec extends AnyWordSpecLike with Matchers {

    "The server version header" should {

        "contain the necessary information" in {
            val header: Server = ServerVersion.serverVersionHeader()
            header.toString() should include("webapi/")
            header.toString() should include("akka-http/")
        }
    }
}

