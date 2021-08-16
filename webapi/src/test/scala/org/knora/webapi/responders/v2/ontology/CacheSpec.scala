/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.Props
import org.knora.webapi.feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig}
import org.knora.webapi.messages.{SmartIri, StringFormatter}
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.store.triplestore.http.HttpTriplestoreConnector
import org.knora.webapi.util.cache.CacheUtil
import org.knora.webapi.{IntegrationSpec, TestContainerFuseki}

import java.time.Instant
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.Cache]].
 */
class CacheSpec extends IntegrationSpec(TestContainerFuseki.PortConfig) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
//  private implicit val ontologyResponder = ResponderManager.

  val additionalTestData = List(
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(
      path = "test_data/all_data/freetest-data.ttl",
      name = "http://www.knora.org/data/0001/freetest"
    )
  )

  val defaultFeatureFactoryConfig: FeatureFactoryConfig = new KnoraSettingsFeatureFactoryConfig(settings)

  // start fuseki http connector actor
  private val fusekiActor = system.actorOf(
    Props(new HttpTriplestoreConnector()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = "httpTriplestoreConnector"
  )

  override def beforeAll(): Unit = {
    CacheUtil.createCaches(settings.caches)
    waitForReadyTriplestore(fusekiActor)
    loadTestData(fusekiActor, additionalTestData)
  }

  "The Ontology Cache" should {

    "successfully load all ontologies" in {
      // val resFuture = inMemCache.putUserADM(user)
      // resFuture map { res => res should equal(true) }
      val ontologiesFromCacheFuture: Future[Map[SmartIri, ReadOntologyV2]] = for {
        _ <- Cache.loadOntologies(
               settings,
               fusekiActor,
               defaultFeatureFactoryConfig,
               KnoraSystemInstances.Users.SystemUser
             )
        cacheData: Cache.OntologyCacheData       <- Cache.getCacheData
        ontologies: Map[SmartIri, ReadOntologyV2] = cacheData.ontologies
      } yield ontologies

      ontologiesFromCacheFuture map { res: Map[SmartIri, ReadOntologyV2] => res.size should equal(13) }
    }

    "successfully load back all ontologies from cache" in {
      val ontologiesFromCacheFuture: Future[Map[SmartIri, ReadOntologyV2]] = for {
        cacheData: Cache.OntologyCacheData       <- Cache.getCacheData
        ontologies: Map[SmartIri, ReadOntologyV2] = cacheData.ontologies
      } yield ontologies

      ontologiesFromCacheFuture map { ontologies: Map[SmartIri, ReadOntologyV2] =>
        ontologies.size should equal(13)
      // TODO: check loaded data for correctness
      }
      assert(true)
    }

    "update cache data correctly" in {
      val previousCacheData = for {
        cacheData <- Cache.getCacheData
      } yield cacheData

      val iri: SmartIri = stringFormatter.toSmartIri(additionalTestData.head.name)
      val previousFreetestFuture = previousCacheData.map { cache =>
        cache.ontologies.get(iri)
      }
      previousFreetestFuture.map(previousFreetestMaybe =>
        previousFreetestMaybe match {
          case Some(previousFreetest) => {
            val hasTextPropertyIri =
              stringFormatter.toSmartIri(s"${additionalTestData.head.name}#hasText")
//            val textPropertyMaybe = previousFreetest.properties.get(propertyIri)
//            textPropertyMaybe match {
//              case Some(textProperty) => {
//                println(textProperty)
//              }
//            }

            // copy freetext-onto but remove :hasText property
            val newFreetest = previousFreetest.copy(
              ontologyMetadata = previousFreetest.ontologyMetadata.copy(
                lastModificationDate = Some(Instant.now())
              ),
              properties = previousFreetest.properties -- Set(hasTextPropertyIri)
            )

//            println(newFreetest)

            // cache updated freetext-onto
//            _ = Cache.storeCacheData(
            previousCacheData.map { prev =>
              val newCacheData = prev.copy(
                ontologies = prev.ontologies + (iri -> newFreetest)
//                subPropertyOfRelations =
//                  cacheData.subPropertyOfRelations + (internalPropertyIri -> allKnoraSuperPropertyIris)
              )
              Cache.storeCacheData(newCacheData)

            // TODO: continue here
            }
            //            )
          }
        }
      )

//      val updatedOntology: ReadOntologyV2 = ???
//
//      val newCacheData = previousCacheData.map { cacheData: Cache.OntologyCacheData =>
//        cacheData.copy(
//          ontologies = cacheData.ontologies + (iri -> updatedOntology)
//        )
//      }
      // TODO: stuff
      assert(true)
    }

//    "contain stuff" in {
////      ResponderManager.
//
//      val anythingFuture = for {
//        cached      <- Cache.getCacheData
//        ontos        = cached.ontologies
//        anythingOnto = ontos.get(stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/anything"))
//      } yield anythingOnto
//
//      anythingFuture andThen {
//        case Success(anythingOption) => {
//          anythingOption match {
//            case Some(anything: ReadOntologyV2) => {
//              // got ontology
//              println(anything)
//              // do stuff
////              Cache.storeCacheData(
////                Cache.OntologyCacheData.
////              )
//            }
//          }
//        }
//      }
////      match {
////        case Some(ontology) => {}
////      }
////      c.onComplete {
////        case Success(v) => {
////          val vv = v
////          println(vv)
////        }
////        case Failure(e) => println(e)
////      }
//      assert(true)
//    }
  }
}
