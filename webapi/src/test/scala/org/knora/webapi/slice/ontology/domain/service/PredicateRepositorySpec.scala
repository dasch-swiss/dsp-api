package org.knora.webapi.slice.ontology.domain.service
import org.apache.jena.query.Dataset
import zio.Ref
import zio.ZLayer
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Anything
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataSet
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake

object PredicateRepositorySpec extends ZIOSpecDefault {
  private val usedOnce: String =
    s"""
       |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
       |
       |<http://aThing>
       |  a <${Anything.Class.Thing.value}> ;
       |  <${Anything.Property.hasOtherThing.value}> true.
       |""".stripMargin
  private val usedTwice: String =
    s"""
       |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
       |
       |<http://aThing>
       |  a <${Anything.Class.Thing.value}> ;
       |  <${Anything.Property.hasOtherThing.value}> true.
       |
       |<http://anotherThing>
       |  a <${Anything.Class.Thing.value}> ;
       |  <${Anything.Property.hasOtherThing.value}> false.
       |
       |""".stripMargin

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], PredicateRepositoryLive](
    PredicateRepositoryLive.layer,
    TriplestoreServiceFake.layer
  )

  val spec: Spec[Any, Throwable] = suite("CardinalityServiceLive")(
    suite("getCountForPropertyUseNumberOfTimesWithClass given not use")(
      test("given a property is not used by the class => return 0") {
        for {
          result <-
            PredicateRepository.getCountForPropertyUseNumberOfTimesWithClass(
              Anything.Class.Thing,
              Anything.Property.hasOtherThing
            )
        } yield assertTrue(result == 0)
      }
    ).provide(commonLayers, emptyDataSet),
    suite("getCountForPropertyUseNumberOfTimesWithClass given used once")(
      test("given a property is in use by the class => return 1") {
        for {
          result <-
            PredicateRepository.getCountForPropertyUseNumberOfTimesWithClass(
              Anything.Class.Thing,
              Anything.Property.hasOtherThing
            )
        } yield assertTrue(result == 1)
      }
    ).provide(commonLayers, datasetLayerFromTurtle(usedOnce)),
    suite("getCountForPropertyUseNumberOfTimesWithClass given used twice")(
      test("given a property is in use by the class => return 2") {
        for {
          result <-
            PredicateRepository.getCountForPropertyUseNumberOfTimesWithClass(
              Anything.Class.Thing,
              Anything.Property.hasOtherThing
            )
        } yield assertTrue(result == 2)
      }
    ).provide(commonLayers, datasetLayerFromTurtle(usedTwice))
  )
}
