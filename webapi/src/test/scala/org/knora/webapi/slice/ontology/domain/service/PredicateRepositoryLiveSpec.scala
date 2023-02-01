package org.knora.webapi.slice.ontology.domain.service
import org.apache.jena.query.Dataset
import zio.Ref
import zio.ZLayer
import zio.test.Assertion._
import zio.test._

import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Biblio
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataSet
import org.knora.webapi.store.triplestore.api.TriplestoreServiceFake

object PredicateRepositoryLiveSpec extends ZIOSpecDefault {
  private val usedOnce: String =
    s"""
       |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
       |
       |<http://aPublication>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}> "A Single Publication Title" .
       |
       |""".stripMargin
  private val usedTwice: String =
    s"""
       |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
       |
       |<http://aPublicationWithOne>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}> "A Single Publication Title" .
       |
       |<http://aPublicationWithTwo>
       |  a <${Biblio.Class.Publication.value}> ;
       |  <${Biblio.Property.hasTitle.value}> "The first Another Publication Title" ;
       |  <${Biblio.Property.hasTitle.value}> "The second Another Publication Title" .
       |
       |<http://aPublicationWithZero>
       |  a <${Biblio.Class.Publication.value}> ;
       |
       |""".stripMargin

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], PredicateRepositoryLive](
    PredicateRepositoryLive.layer,
    TriplestoreServiceFake.layer
  )

  val spec: Spec[Any, Throwable] = suite("PredicateRepositoryLive")(
    suite("getCountForPropertyUseNumberOfTimesWithClass given not use")(
      test("given a property is not used by any instance of the class then return empty List") {
        for {
          result <-
            PredicateRepository.getCountForPropertyUsedNumberOfTimesWithClass(
              Biblio.Property.hasTitle,
              Biblio.Class.Publication
            )
        } yield assertTrue(result == List.empty)
      }
    ).provide(commonLayers, emptyDataSet),
    suite("getCountForPropertyUseNumberOfTimesWithClass given used once")(
      test("given a property is in use by a single instance of the class return this instance with a count of one") {
        for {
          result <-
            PredicateRepository.getCountForPropertyUsedNumberOfTimesWithClass(
              Biblio.Property.hasTitle,
              Biblio.Class.Publication
            )
        } yield assertTrue(result == List((InternalIri("http://aPublication"), 1)))
      }
    ).provide(commonLayers, datasetLayerFromTurtle(usedOnce)),
    suite("getCountForPropertyUseNumberOfTimesWithClass given used twice")(
      test("given a property is in use by multiple instances of the class return each instance and the times used") {
        for {
          result <-
            PredicateRepository.getCountForPropertyUsedNumberOfTimesWithClass(
              Biblio.Property.hasTitle,
              Biblio.Class.Publication
            )
        } yield assert(result)(
          hasSameElements(
            List(
              (InternalIri("http://aPublicationWithZero"), 0),
              (InternalIri("http://aPublicationWithOne"), 1),
              (InternalIri("http://aPublicationWithTwo"), 2)
            )
          )
        )
      }
    ).provide(commonLayers, datasetLayerFromTurtle(usedTwice))
  )
}
