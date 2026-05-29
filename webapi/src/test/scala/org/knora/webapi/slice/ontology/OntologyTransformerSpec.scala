package org.knora.webapi.slice.ontology

import org.apache.jena.riot.Lang
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.slice.common.jena.ModelOps
import zio.ZIO
import zio.test.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object OntologyTransformerSpec extends ZIOSpecDefault {

  private val transformer = ZIO.serviceWithZIO[OntologyTransformer]

  private def writeTempFile(suffix: String, content: String): ZIO[Any, Throwable, Path] =
    ZIO.attemptBlocking {
      val p = Files.createTempFile("onto-transformer-test-", suffix)
      Files.write(p, content.getBytes(StandardCharsets.UTF_8))
      p
    }

  private def deleteIfExists(p: Path): ZIO[Any, Nothing, Unit] =
    ZIO.attempt(Files.deleteIfExists(p)).ignore.unit

  override def spec = suite("OntologyTransformerSpec")(
    test("OntologyTransformer should read a JSON-LD file and produce an NQUAD file") {
      val jsonLd = """
                     |[{
                     |    "rdfs:label": "test",
                     |    "@id": "http://rdfh.ch/9999/Td9Y3a-cSjueXwn9pK1KYQ",
                     |    "@context": {
                     |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
                     |    }
                     |}]""".stripMargin

      val expectedStr = """
                          | PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
                          | PREFIX knora-base:  <http://www.knora.org/ontology/knora-base#>
                          | PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                          | PREFIX rdfs:        <http://www.w3.org/2000/01/rdf-schema#>
                          |
                          | <http://rdfh.ch/9999/Td9Y3a-cSjueXwn9pK1KYQ>
                          |         rdfs:label                    "test" .
                          |""".stripMargin

      ZIO.scoped {
        for {
          inputPath  <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
          outputPath <- transformer(_.toKnoraBase(inputPath))
          _          <- ZIO.addFinalizer(deleteIfExists(outputPath))
          actualNQ   <- ZIO.attempt(new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8))
          actual     <- ModelOps.from(actualNQ, Lang.NTRIPLES)
          expected   <- ModelOps.fromTurtle(expectedStr)
          iso         = actual.isIsomorphicWith(expected)
        } yield assertTrue(iso)
      }
    },
  ).provide(OntologyTransformer.layer, TestAppConfig.layer())

}
