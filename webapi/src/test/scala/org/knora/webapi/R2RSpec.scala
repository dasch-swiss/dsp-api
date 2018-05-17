/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi

import java.io.StringReader

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.util.{CacheUtil, StringFormatter}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by subotic on 08.12.15.
  */
class R2RSpec extends Suite with ScalatestRouteTest with WordSpecLike with Matchers with BeforeAndAfterAll {

    def actorRefFactory = system

    val settings = Settings(system)
    val logger = akka.event.Logging(system, this.getClass)
    StringFormatter.initForTest()

    implicit val log = logger

    implicit val knoraExceptionHandler = KnoraExceptionHandler(settings, log)

    override def beforeAll {
        CacheUtil.createCaches(settings.caches)
    }

    override def afterAll {
        CacheUtil.removeAllCaches()
    }

    protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
        val responseBodyFuture: Future[String] = response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
        val responseBodyStr = Await.result(responseBodyFuture, 5.seconds)
        JsonLDUtil.parseJsonLD(responseBodyStr)
    }

    protected def parseTurtle(turtleStr: String): Model = {
        Rio.parse(new StringReader(turtleStr), "", RDFFormat.TURTLE)
    }

    protected def parseRdfXml(rdfXmlStr: String): Model = {
        Rio.parse(new StringReader(rdfXmlStr), "", RDFFormat.RDFXML)
    }
}
