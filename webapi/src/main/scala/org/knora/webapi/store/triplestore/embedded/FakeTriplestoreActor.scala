/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

/*
package org.knora.rapier.store.triplestore.embedded

import akka.actor.Actor.Receive
import org.knora.rapier.Settings
import org.knora.rapier.messages.v1respondermessages.triplestoremessages._
import org.knora.rapier.util.ActorUtils._
import org.knora.rapier.util.ErrorHandlingMap
import org.knora.rapier.store._

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import akka.event.LoggingAdapter
import akka.pattern._
import org.apache.commons.io.FileUtils
import org.knora.rapier.Settings

import scala.collection.breakOut
import scala.concurrent.Future
import scala.io.{Codec, Source}

/**
  * A fake triplestore for use in performance testing. This feature is activated in `application.conf`.
  */
trait Fakeable {

    private var fakeTriplestoreDir: Option[File] = None

    private var queryNum = 0

    var data = Map.empty[String, Any]

    def init(dataDir: File) = {
        fakeTriplestoreDir = Some(dataDir)
    }

    def clear() = {
        FileUtils.deleteDirectory(fakeTriplestoreDir.get)
        fakeTriplestoreDir.get.mkdirs()
        ()
    }

    def load() = {
        val dataToWrap: Map[String, String] = fakeTriplestoreDir.get.listFiles.map {
            queryDir =>
                val sparql = readFile(queryDir.listFiles.filter(_.getName.endsWith(".rq")).head)
                val result = readFile(queryDir.listFiles.filter(_.getName.endsWith(".json")).head)
                (sparql, result)
        }(breakOut)
        data = new ErrorHandlingMap(dataToWrap, { key: String => s"No result has been stored in the fake triplestore for this query: $key" })
    }

    def add(sparql: String, result: String, log: LoggingAdapter): Unit = {
        this.synchronized {
            log.debug("Collecting data for fake triplestore")
            val paddedQueryNum = f"$queryNum%04d"
            val queryDir = new File(fakeTriplestoreDir.get, paddedQueryNum)
            queryDir.mkdirs()
            val sparqlFile = new File(queryDir, s"query-$paddedQueryNum.rq")
            writeFile(sparqlFile, sparql)
            val resultFile = new File(queryDir, s"response-$paddedQueryNum.json")
            writeFile(resultFile, result)
            queryNum += 1
        }
    }

    def dump(dataDir: File) = {
        fakeTriplestoreDir = Some(dataDir)
        this.clear()
    }

    private def writeFile(file: File, content: String) = {
        Files.write(Paths.get(file.getCanonicalPath), content.getBytes(StandardCharsets.UTF_8))
    }

    private def readFile(file: File) = {
        Source.fromFile(file)(Codec.UTF8).mkString
    }

    def fakeableReceive: Receive = {
        case FakeTriplestorePrepare(userProfile) => ()
        case FakeTriplestoreUse(userProfile) => ()
    }
}

/**
  * Created by subotic on 03.11.15.
  */
class FakeTriplestoreActor extends Actor with ActorLogging {

    private val system = context.system
    private implicit val executionContext = system.dispatcher
    private val settings = Settings(system)

    val preparing = false

    var jenaTdbRef: ActorRef = _

    override def preStart() = {
        FakeTriplestore.init(settings.fakeTriplestoreDataDir)

        if (settings.useFakeTriplestore) {
            FakeTriplestore.load()
            log.debug("Loaded fake triplestore")
        } else {
            log.debug(s"Using triplestore: ${settings.triplestoreType}")
        }

        if (settings.prepareFakeTriplestore) {
            FakeTriplestore.clear()
            log.debug("About to prepare fake triplestore")
        }

        jenaTdbRef = context.actorOf(Props[JenaTDBActor], EMBEDDED_JENA_ACTOR_NAME)
    }


    def fakeableReceive = {
        case FakeTriplestorePrepare(userProfile) => ()
        case FakeTriplestoreUse(userProfile) => ()
        case SparqlSelectRequest(sparqlSelectString) => future2Message(sender(), executeSparqlSelectQuery(sparqlSelectString), log)
        case SparqlUpdateRequest(sparqlUpdateString) => future2Message(sender(), executeSparqlUpdateQuery(sparqlUpdateString), log)
        case ResetTriplestoreContent(rdfDataObjects) => future2Message(sender(), resetTripleStoreContent(rdfDataObjects), log)
        case DropAllTriplestoreContent() => future2Message(sender(), dropAllTriplestoreContent(), log)
        case InsertTriplestoreContent(rdfDataObjects) => future2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)

        case Initialized() => sender ! InitializedResponse(true)
        case Hello("FakeTriplestore") => sender ! Hello("FakeTriplestore")
    }

    def prepare = {

    }

    def use = {

    }


    private def executeSparqlSelectQuery(sparqlSelectString: String): Future[SparqlSelectResponse] = ???

    private def executeSparqlUpdateQuery(sparqlString: String): Future[SparqlUpdateResponse] = ???

    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTriplestoreContentACK] = ???

    private def dropAllTriplestoreContent(): Future[DropAllTriplestoreContentACK] = ???

    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject]): Future[InsertTriplestoreContentACK] = ???
}
*/
