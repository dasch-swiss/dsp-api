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

package org.knora.webapi.util

import java.io.File

import org.apache.jena.graph._
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.v2.responder.RdfRequestParser
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.JavaConverters._

/**
 * Tests [[RdfRequestParser]].
 */
class RdfRequestParserSpec extends AnyWordSpecLike with Matchers {

    private def checkForRdfTypeBook(graph: Graph): Unit = {
        val statements: Seq[Triple] = graph.find(
            NodeFactory.createURI("http://rdfh.ch/0803/2a6221216701"),
            NodeFactory.createURI(OntologyConstants.Rdf.Type),
            Node.ANY
        ).asScala.toSeq

        assert(statements.size == 1)
        assert(statements.head.getObject == NodeFactory.createURI("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"))
    }

    "RdfRequestParser" should {
        "parse RDF in Turtle format, producing a Jena Graph" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))
            val graph: Graph = RdfRequestParser.requestToJenaGraph(entityStr = inputTurtle, contentType = RdfMediaTypes.`text/turtle`)
            checkForRdfTypeBook(graph)
        }

        "parse RDF in JSON-LD format, producing a Jena Graph" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))
            val graph: Graph = RdfRequestParser.requestToJenaGraph(entityStr = inputTurtle, contentType = RdfMediaTypes.`application/ld+json`)
            checkForRdfTypeBook(graph)
        }
    }
}
