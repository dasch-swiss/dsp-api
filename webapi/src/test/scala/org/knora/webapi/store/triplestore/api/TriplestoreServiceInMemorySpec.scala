/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api
import org.apache.jena.query.Dataset
import org.apache.jena.query.ReadWrite
import zio.Ref
import zio.ZIO
import zio.test.Assertion.hasSameElements
import zio.test.*

import java.nio.file.Files
import java.util.UUID

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.IriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.IriSubjectV2
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Biblio
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData

object TriplestoreServiceInMemorySpec extends ZIOSpecDefault {

  private val testDataSet =
    s"""
       |@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix owl:        <http://www.w3.org/2002/07/owl#> .
       |@prefix biblio:     <${Biblio.Ontology.value}> .
       |
       |<${Biblio.Class.Publication.value}>     rdf:type          owl:Class .
       |
       |<${Biblio.Class.Article.value}>         rdf:type          owl:Class ;
       |                                        rdfs:subClassOf   <${Biblio.Class.Publication.value}> .
       |
       |<${Biblio.Class.JournalArticle.value}>  rdf:type          owl:Class ;
       |                                        rdfs:subClassOf   <${Biblio.Class.Article.value}> .
       |
       |<http://anArticle>                      a                 <${Biblio.Class.Article.value}> .
       |<http://aJournalArticle>                a                 <${Biblio.Class.JournalArticle.value}> .
       |
       |""".stripMargin

  val spec: Spec[Any, Throwable] =
    suite("TriplestoreServiceFake")(
      suite("DROP")(
        test("dropDataGraphByGraph") {
          for {
            _ <- TriplestoreService.dropDataGraphByGraph()
            query = s"""
                       |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                       |                          
                       |ASK WHERE {
                       |  <http://anArticle> a <${Biblio.Class.Article.value}> .
                       |}
                       |""".stripMargin
            result <- TriplestoreService.query(Ask(query))
          } yield assertTrue(!result)
        }
      ),
      suite("CONSTRUCT")(
        test("sparqlHttpConstruct should return some values") {
          val query =
            s"""
               |CONSTRUCT {
               |  ?s ?p ?o
               |}
               |WHERE {
               |  ?s ?p ?o ;
               |     a  <${Biblio.Class.Article.value}> .
               |}
               |""".stripMargin
          for {
            response <- TriplestoreService.query(Construct(query))
          } yield assertTrue(
            response.statements ==
              Map(
                "http://anArticle" -> Seq(
                  (
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "http://www.knora.org/ontology/0801/biblio#Article"
                  )
                )
              )
          )
        },
        test("sparqlHttpExtendedConstruct should return some values") {
          val query =
            s"""
               |CONSTRUCT {
               |  ?s ?p ?o
               |}
               |WHERE {
               |  ?s ?p ?o ;
               |     a  <${Biblio.Class.Article.value}> .
               |}
               |""".stripMargin
          for {
            sf       <- ZIO.service[StringFormatter]
            response <- TriplestoreService.query(Construct(query)).flatMap(_.asExtended(sf))
          } yield {
            val subject: SubjectV2 = IriSubjectV2(value = "http://anArticle")
            assertTrue(
              response.statements == Map(
                subject -> Map(
                  "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri(sf) -> Seq(
                    IriLiteralV2(value = "http://www.knora.org/ontology/0801/biblio#Article")
                  )
                )
              )
            )
          }
        }
      ),
      suite("UPDATE")(test("update") {
        val updateQuery = s"""
                             |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                             |
                             |INSERT DATA {
                             |  GRAPH <http://rdfh.ch/1234> {
                             |    <http://aNewArticle> a <${Biblio.Class.Article.value}>
                             |  }
                             |}
                             |""".stripMargin
        val askQuery = s"""
                          |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                          |
                          |ASK WHERE {
                          |  <http://aNewArticle> a <${Biblio.Class.Article.value}> .
                          |}
                          |""".stripMargin
        for {
          _      <- TriplestoreService.query(Update(updateQuery))
          result <- TriplestoreService.query(Ask(askQuery))
        } yield assertTrue(result)
      }),
      suite("ASK")(
        test("should return true if anArticle exists") {
          val query = s"""
                         |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                         |                          
                         |ASK WHERE {
                         |  <http://anArticle> a <${Biblio.Class.Article.value}> .
                         |}
                         |""".stripMargin
          TriplestoreService
            .query(Ask(query))
            .map(result => assertTrue(result))
        },
        test("should return false if thing does not exist") {
          val query = """
                        |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        |PREFIX anything:    <http://www.knora.org/ontology/0001/anything#>
                        |
                        |ASK WHERE {
                        | <http://rdfh.ch/0001/nonexisting> a anything:Thing .
                        |}
                        |""".stripMargin
          for {
            result <- TriplestoreService.query(Ask(query)).negate
          } yield assertTrue(result)
        }
      ),
      suite("SELECT")(
        suite("subclass relation querying")(
          test("should find direct subclass (rdfs:subClassOf)") {
            val query = s"""
                           |PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                           |PREFIX rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
                           |PREFIX owl:    <http://www.w3.org/2002/07/owl#>
                           |
                           |SELECT ?entity
                           |WHERE {
                           |  ?entity    rdf:type          ?type .
                           |  ?type      rdfs:subClassOf  <${Biblio.Class.Publication.value}> .
                           |}
                           |""".stripMargin
            for {
              result <- TriplestoreService.query(Select(query))
            } yield assert(result.results.bindings.flatMap(_.rowMap.get("entity")))(
              hasSameElements(List("http://anArticle"))
            )
          },
          test("should find all subclasses (rdfs:subClassOf*)") {
            val query = s"""
                           |PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                           |PREFIX rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
                           |PREFIX owl:    <http://www.w3.org/2002/07/owl#>
                           |
                           |SELECT ?entity
                           |WHERE {
                           |  ?entity    rdf:type          ?type .
                           |  ?type      rdfs:subClassOf*  <${Biblio.Class.Publication.value}> .
                           |}
                           |""".stripMargin
            for {
              result <- TriplestoreService.query(Select(query))
            } yield assert(result.results.bindings.flatMap(_.rowMap.get("entity")))(
              hasSameElements(List("http://anArticle", "http://aJournalArticle"))
            )
          }
        ),
        test("not find non-existing thing") {
          val query = """
                        |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        |
                        |SELECT ?p ?o
                        |WHERE {
                        |  <http://rdfh.ch/0001/nonexisting> ?p ?o.
                        |}
                        |""".stripMargin
          for {
            result <- TriplestoreService.query(Select(query))
          } yield assertTrue(result.results.bindings.isEmpty)
        },
        test("find an existing thing") {
          val query = """
                        |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        |
                        |SELECT ?p ?o
                        |WHERE {
                        |  <http://aJournalArticle> ?p ?o.
                        |}
                        |""".stripMargin
          for {
            result <- TriplestoreService.query(Select(query))
          } yield assertTrue(
            result == SparqlSelectResult(
              SparqlSelectResultHeader(List("p", "o")),
              SparqlSelectResultBody(
                List(
                  VariableResultsRow(
                    Map(
                      "p" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                      "o" -> s"${Biblio.Class.JournalArticle.value}"
                    )
                  )
                )
              )
            )
          )
        }
      ),
      suite("insertDataIntoTriplestore")(
        test("given an empty list insertDataIntoTriplestore will insert the defaults") {
          for {
            _       <- TriplestoreService.insertDataIntoTriplestore(List.empty, prependDefaults = true)
            ds      <- ZIO.serviceWithZIO[Ref[Dataset]](_.get)
            contains = DefaultRdfData.data.map(_.name).map(namedModelExists(ds, _)).forall(_ == true)
          } yield assertTrue(contains)
        },
        test("given an empty list insertDataIntoTriplestore will insert the defauls") {
          for {
            _ <- TriplestoreService.insertDataIntoTriplestore(
                   List(
                     RdfDataObject(
                       path = "webapi/src/main/resources/knora-ontologies/knora-base.ttl",
                       name = "http://www.knora.org/ontology/knora-admin"
                     )
                   ),
                   prependDefaults = false
                 )
            ds                <- ZIO.serviceWithZIO[Ref[Dataset]](_.get)
            containsAdmin      = namedModelExists(ds, "http://www.knora.org/ontology/knora-admin")
            doesNotContainBase = !namedModelExists(ds, "http://www.knora.org/ontology/knora-base")
          } yield assertTrue(containsAdmin && doesNotContainBase)
        }
      ),
      suite("sparqlHttpGraphFile")(test("sparqlHttpGraphFile should create the file") {
        val tempDir = Files.createTempDirectory(UUID.randomUUID().toString)
        tempDir.toFile.deleteOnExit()
        val testFile = zio.nio.file.Path.fromJava(tempDir.toAbsolutePath.resolve("test.ttl"))
        ZIO.scoped {
          for {
            _ <- TriplestoreService.insertDataIntoTriplestore(
                   List(
                     RdfDataObject(
                       path = "webapi/src/main/resources/knora-ontologies/knora-base.ttl",
                       name = "http://www.knora.org/ontology/knora-base"
                     )
                   ),
                   prependDefaults = false
                 )
            _ <- TriplestoreService.downloadGraph(
                   InternalIri("http://www.knora.org/ontology/knora-base"),
                   testFile,
                   TriG
                 )
          } yield assertTrue({ val fileExists = Files.exists(testFile.toFile.toPath); fileExists })
        }
      })
    ).provide(TriplestoreServiceInMemory.layer, datasetLayerFromTurtle(testDataSet), StringFormatter.test)

  private def namedModelExists(ds: Dataset, name: String) = {
    ds.begin(ReadWrite.READ)
    try {
      ds.containsNamedModel(name)
    } finally {
      ds.end()
    }
  }
}
