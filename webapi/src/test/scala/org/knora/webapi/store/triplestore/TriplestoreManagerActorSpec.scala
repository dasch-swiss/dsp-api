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

package org.knora.webapi.store.triplestore

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.store._
import org.knora.webapi.{CoreSpec, TestProbeMaker}


/*
 * Custom config used in the test. Here you can define ond override everything which you would
 * normaly write in application.conf
 */
object TriplestoreManagerActorSpec {
    val configGraphDB = ConfigFactory.parseString(
        """
        app {
            triplestore {
                dbtype = "graphdb"
            }
        }
        """.stripMargin)

    val configFuseki = ConfigFactory.parseString(
        """
        app {
            triplestore {
                dbtype = "fuseki"
            }
        }
        """.stripMargin)


    /*
    val configEmbeddedJenaTDB = ConfigFactory.parseString(
        """
        app {
            triplestore {
                dbtype = "embedded-jena-tdb"
            }
        }
        """.stripMargin)

    val configEmbeddedGraphDB = ConfigFactory.parseString(
        """
        app {
            triplestore {
                dbtype = "embedded-jena-graphdb"
            }
        }
        """.stripMargin)

    val configUnknownTriplestore = ConfigFactory.parseString(
        """
        app {
            triplestore {
                dbtype = "unknown-triplestore"
            }
        }
        """.stripMargin)
    */
}

/*
 * In this simple test case, we start our actor under test, send it a message, and test if the message
 * we got in response is the one we expexted.
 *
 * The naming is usualy the class name appended by the word 'spec' all in camel case.
 *
 * All test cases are subclasses of CoreSpec and need to provide parameters
 * providing the actor system name and config.
 *
 * to execute, type 'test' in sbt
 */
class TriplestoreManagerActorSpec01 extends CoreSpec("TriplestoreManagerActorTestSystem", TriplestoreManagerActorSpec.configGraphDB) with ImplicitSender {

    // here we start the actor under test with the TestProbeMaker trait
    val actorUnderTest = TestActorRef(Props(new TriplestoreManager with TestProbeMaker), name = TRIPLESTORE_MANAGER_ACTOR_NAME)

    // here we get the ActorRef to a subactor with the name 'triplestoreRouter' (ability provided by TestProbeMaker trait)
    val mockEmbeddedStoreActorCorrect = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(HTTP_TRIPLESTORE_ACTOR_NAME, null)
    val mockEmbeddedStoreActorWrong = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(EMBEDDED_JENA_ACTOR_NAME, null)

    /*
    * Here are a few test which can be undertaken.
    * We use ScalaTest (http://www.scalatest.org),
    * the FlatSpec or WordSpec style of writing tests
    * (http://www.scalatest.org/user_guide/selecting_a_style)
    * depending on the need for verbosity in the test output
    */

    "The 'TriplestoreManagerActor', depending on the configuration, " should {
        "start 'GraphDB' " in {
            mockEmbeddedStoreActorCorrect.isInstanceOf[TestProbe] should ===(true)
        }

        "not start 'EmbeddedJenaTDB' " in {
            mockEmbeddedStoreActorWrong.isInstanceOf[TestProbe] should ===(false)
        }
    }


}

class TriplestoreManagerActorSpec02 extends CoreSpec("TriplestoreManagerActorTestSystem", TriplestoreManagerActorSpec.configFuseki) with ImplicitSender {

    // here we start the actor under test with the TestProbeMaker trait
    val actorUnderTest = TestActorRef(Props(new TriplestoreManager with TestProbeMaker), name = TRIPLESTORE_MANAGER_ACTOR_NAME)

    // here we get the ActorRef to a subactor with the name 'triplestoreRouter' (ability provided by TestProbeMaker trait)
    val mockEmbeddedStoreActorCorrect = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(HTTP_TRIPLESTORE_ACTOR_NAME, null)
    val mockEmbeddedStoreActorWrong = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(EMBEDDED_JENA_ACTOR_NAME, null)

    /*
    * Here are a few test which can be undertaken.
    * We use ScalaTest (http://www.scalatest.org),
    * the FlatSpec or WordSpec style of writing tests
    * (http://www.scalatest.org/user_guide/selecting_a_style)
    * depending on the need for verbosity in the test output
    */

    "The 'TriplestoreManagerActor', depending on the configuration, " should {
        "start 'Fuseki' " in {
            mockEmbeddedStoreActorCorrect.isInstanceOf[TestProbe] should ===(true)
        }

        "not start 'EmbeddedJenaTDB' " in {
            mockEmbeddedStoreActorWrong.isInstanceOf[TestProbe] should ===(false)
        }
    }


}

/**
  * Supporting code is removed (for now).
  */
/*
class TriplestoreManagerActorSpec05 extends CoreSpec("TriplestoreManagerActorTestSystem", TriplestoreManagerActorSpec.configEmbeddedJenaTDB) with ImplicitSender {

    // here we start the actor under test with the TestProbeMaker trait
    val actorUnderTest = TestActorRef(Props(new TriplestoreManagerActor with TestProbeMaker), name = TRIPLESTORE_MANAGER_ACTOR_NAME)

    // here we get the ActorRef to a subactor with the name 'triplestoreRouter' (ability provided by TestProbeMaker trait)
    val mockEmbeddedStoreActorCorrect = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(EMBEDDED_JENA_ACTOR_NAME, null)
    val mockEmbeddedStoreActorWrong = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(EMBEDDED_GRAPH_DB_ACTOR_NAME, null)

    /*
    * Here are a few test which can be undertaken.
    * We use ScalaTest (http://www.scalatest.org),
    * the FlatSpec or WordSpec style of writing tests
    * (http://www.scalatest.org/user_guide/selecting_a_style)
    * depending on the need for verbosity in the test output
    */

    "The 'TriplestoreManagerActor', depending on the configuration, " should {
        "start 'EmbeddedJenaTDB' " in {
            mockEmbeddedStoreActorCorrect.isInstanceOf[TestProbe] should ===(true)
        }

        "not start 'EmbeddedGraphDB' " in {
            mockEmbeddedStoreActorWrong.isInstanceOf[TestProbe] should ===(false)
        }
    }


}
*/

/**
  * Supporting code is removed (for now).
  */

/*
class TriplestoreManagerActorSpec06 extends CoreSpec("TriplestoreManagerActorTestSystem", TriplestoreManagerActorSpec.configEmbeddedGraphDB) with ImplicitSender {

    // here we start the actor under test with the TestProbeMaker trait
    val actorUnderTest = TestActorRef(Props(new TriplestoreManagerActor with TestProbeMaker), name = TRIPLESTORE_MANAGER_ACTOR_NAME)

    // here we get the ActorRef to a subactor with the name 'triplestoreRouter' (ability provided by TestProbeMaker trait)
    val mockEmbeddedStoreActorCorrect = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(EMBEDDED_GRAPH_DB_ACTOR_NAME, null)
    val mockEmbeddedStoreActorWrong = actorUnderTest.underlyingActor.asInstanceOf[TestProbeMaker].probes.getOrElse(EMBEDDED_JENA_ACTOR_NAME, null)

    /*
    * Here are a few test which can be undertaken.
    * We use ScalaTest (http://www.scalatest.org),
    * the FlatSpec or WordSpec style of writing tests
    * (http://www.scalatest.org/user_guide/selecting_a_style)
    * depending on the need for verbosity in the test output
    */

    "The 'TriplestoreManagerActor', depending on the configuration, " should {
        "start 'EmbeddedGraphDB' " in {
            mockEmbeddedStoreActorCorrect.isInstanceOf[TestProbe] should ===(true)
        }

        "not start 'EmbeddedJenaTDB' " in {
            mockEmbeddedStoreActorWrong.isInstanceOf[TestProbe] should ===(false)
        }
    }


}
*/

/*
class TriplestoreManagerActorSpec07 extends CoreSpec("TriplestoreManagerActorTestSystem", TriplestoreManagerActorSpec.configUnknownTriplestore) with ImplicitSender {

    // here we start the actor under test with the TestProbeMaker trait
    val actorUnderTest = TestActorRef(Props(new TriplestoreManagerActor with TestProbeMaker), name = TRIPLESTORE_MANAGER_ACTOR_NAME)


    /*
    * Here are a few test which can be undertaken.
    * We use ScalaTest (http://www.scalatest.org),
    * the FlatSpec or WordSpec style of writing tests
    * (http://www.scalatest.org/user_guide/selecting_a_style)
    * depending on the need for verbosity in the test output
    */

    "The 'TriplestoreManagerActor', depending on the configuration, " must {
        "return an error if the triple store type is not supported" in {
            actorUnderTest ! Hello("test message")
            expectMsg(Status.Failure(UnsuportedTriplestoreException(s"unknown-triplestore type not supported")))
        }
    }


}
*/
