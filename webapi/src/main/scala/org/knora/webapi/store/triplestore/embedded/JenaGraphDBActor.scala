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

package org.knora.webapi.store.triplestore.embedded

import java.io.File

import akka.actor.{Actor, ActorLogging, Status}
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.HelloTriplestore
import org.knora.webapi.{Settings, UnexpectedMessageException}


sealed trait GraphDBMessage

case class HelloGraphDB(msg: String) extends GraphDBMessage


class JenaGraphDBActor extends Actor with ActorLogging {

    private val system = context.system
    private implicit val executionContext = system.dispatcher
    private val settings = Settings(system)


    private val persist = settings.tripleStoreConfig.getBoolean("embedded-jena-graphdb.graphdb-persisted-storage")
    private val storagePath = new File(settings.tripleStoreConfig.getString("embedded-jena-graphdb.graphdb-storage-path"))
    private val reloadDataOnStart = settings.tripleStoreConfig.getBoolean("reload-on-start")

    // Jena prefers to have 1 global dataset
    //lazy val dataset = getDataset

    /**
      * Receives a message requesting a SPARQL query or update, and returns an appropriate response message or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        //case SparqlSelectRequest(sparqlSelectString) => future2Message(sender(), executeSparqlSelectQuery(sparqlSelectString), log)
        //case SparqlUpdateRequest(sparqlUpdateString) => future2Message(sender(), executeSparqlUpdateQuery(sparqlUpdateString), log)
        //case ResetTripleStoreContent(rdfDataObjects) => future2Message(sender(), resetTripleStoreContent(rdfDataObjects), log)
        //case DropAllTripleStoreContent() => future2Message(sender(), dropAllTriplestoreContent(), log)
        //case InsertTripleStoreContent(rdfDataObjects) => future2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
        case HelloTriplestore("GraphDB") ⇒ sender ! HelloTriplestore("GraphDB")
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /*
     * Does not work. Probably because we are using Jena 3.0 and GraphDB is only supporting up to 2.7.
     * Will revisit this at a later date.

    /**
     * Submits a SPARQL query to the embedded Jena TDB store and returns the response as a [[SparqlSelectResponse]].
     * @param queryString the SPARQL request to be submitted.
     * @return [[SparqlSelectResponse]].
     */
    private def executeSparqlSelectQuery(queryString: String): Future[SparqlSelectResponse] = {

        // Start read transaction
        this.dataset.begin(ReadWrite.READ)

        try {
            //println("==>> SparqlSelect Start")
            //println("# Content of dataset at beginning of query")
            //SSE.write(this.dataset)

            val query: Query = QueryFactory.create(queryString)
            val qExec: QueryExecution = QueryExecutionFactory.create(query, this.dataset)
            val resultSet: ResultSet = qExec.execSelect()

            /*
            Attention: the ResultSet can be only used once, i.e. it is not there anymore after use!
            println(s"# Query string: $queryString")
            println("# Content of ResultSet: ")
            println(ResultSetFormatter.asText(resultSet))
            println(ResultSetFormatter.asText(resultSet))
            */

            val resultVarList = resultSet.getResultVars.toList

            val variableResultsRows = resultSet.map {
                row =>
                    val mapToWrap = resultVarList.foldLeft(Map.empty[String, String]) {
                        case (acc, varName) =>
                            Option(row.get(varName)) match {
                                case Some(literal: Literal) =>
                                    acc + (varName -> literal.getLexicalForm)

                                case Some(otherValue) =>
                                    acc + (varName -> otherValue.toString)

                                case None => acc
                            }
                    }

                    VariableResultsRow(new ErrorHandlingMap(mapToWrap, { key: String => s"No value found for SPARQL query variable '$key' in query result row" }))
            }.toList

            qExec.close()

            // Commit transaction
            this.dataset.commit()

            //println("==>> SparqlSelect End")

            Future.successful(
                SparqlSelectResponse(
                    SparqlSelectResponseHeader(resultVarList),
                    SparqlSelectResponseBody(variableResultsRows)
                )
            )
        } catch {
            case ex: Throwable =>
                this.dataset.abort()
                Future.failed(TriplestoreResponseException("Triplestore transaction failed", ex, log))
        } finally {
            this.dataset.end()
        }
    }

    /**
     * Submits a SPARQL update request to the embedded Jena TDB store, and returns a [[SparqlUpdateResponse]] if the
     * operation completed successfully.
     * @param updateString the SPARQL update to be submitted.
     * @return a [[SparqlUpdateResponse]].
     */
    private def executeSparqlUpdateQuery(updateString: String): Future[SparqlUpdateResponse] = {
        // println("=============================")
        // println(updateString)
        // println()

        // Start write transaction
        this.dataset.begin(ReadWrite.WRITE)

        try {
            //println("==>> SparqlUpdate Start")

            //println("#state of dataset BEFORE update")
            //SSE.write(this.dataset)

            //println(s"executing update for: $updateString")
            UpdateAction.parseExecute(updateString, this.dataset)

            // Commit transaction
            this.dataset.commit()

            //println("#state of dataset AFTER update")
            //SSE.write(this.dataset)

            //println("==>> SparqlUpdate End")

            Future.successful(SparqlUpdateResponse())
        } catch {
            case ex: Throwable =>
                this.dataset.abort()
                Future.failed(TriplestoreResponseException("Triplestore transaction failed", ex, log))
        } finally {
            this.dataset.end()
        }
    }

    /**
     * Reloads the contents of the triplestore from RDF data files.
     * @param rdfDataObjects a list of [[RdfDataObject]] instances describing the files to be loaded.
     * @return an [[ResetTripleStoreContentACK]] indicating that the operation completed successfully.
     */
    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTripleStoreContentACK] = {

        val resetTriplestoreResult = for {

        // drop old content
            dropResult <- dropAllTriplestoreContent()

            // insert new content
            insertResult <- insertDataIntoTriplestore(rdfDataObjects)

            // any errors throwing exceptions until now are already covered so we can ACK the request
            result = ResetTripleStoreContentACK()
        } yield result

        resetTriplestoreResult
    }

    private def dropAllTriplestoreContent(): Future[DropAllTripleStoreContentACK] = {
        Future {
            // log.debug("ResetTripleStoreContent ...")

            // Start write transaction
            this.dataset.begin(ReadWrite.WRITE)
            try {
                //println("==>> dropAllTriplestoreContent Start")

                // instantiate update request
                val updateRequest: UpdateRequest = UpdateFactory.create()

                // add: drop everything
                updateRequest.add("DROP ALL")

                // perform the operations.
                UpdateAction.execute(updateRequest, this.dataset)

                //println("# State of dataset after reset")
                //SSE.write(this.dataset)
                //println("==>> dropAllTriplestoreContent End")

                // Commit transaction
                this.dataset.commit()

                DropAllTripleStoreContentACK()
            } catch {
                case ex: Throwable =>
                    this.dataset.abort()
                    throw TriplestoreResponseException("Triplestore transaction failed", ex, log)
            } finally {
                this.dataset.end()
            }
        }
    }

    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject]): Future[InsertTripleStoreContentACK] = {

        Future {
            // log.debug("ResetTripleStoreContent ...")

            // Start write transaction
            this.dataset.begin(ReadWrite.WRITE)
            try {
                //println("==>> ResetTriplesStore Start")

                // instantiate update request
                val updateRequest: UpdateRequest = UpdateFactory.create()

                // add: data
                for (elem <- rdfDataObjects) {
                    log.debug(s"Adding: ${elem.name} - ${elem.path}")
                    updateRequest.add(s"CREATE GRAPH <${elem.name}>")
                    updateRequest.add(s"LOAD <file:${elem.path}> INTO GRAPH <${elem.name}>")
                }

                // perform the operations.
                UpdateAction.execute(updateRequest, this.dataset)

                //println("# State of dataset after reset")
                //SSE.write(this.dataset)
                //println("==>> ResetTriplesStore End")

                // Commit transaction
                this.dataset.commit()

                InsertTripleStoreContentACK()
            } catch {
                case ex: Throwable =>
                    this.dataset.abort()
                    throw TriplestoreResponseException("Triplestore transaction failed", ex, log)
            } finally {
                this.dataset.end()
            }
        }

    }

    private def getDataset: SesameDataset = {

        val schema: OwlimSchemaRepository = new OwlimSchemaRepository()

        // set the data folder where GraphDB-SE will persist its data
        schema.setDataDir(new File(storagePath.getAbsolutePath))

        // configure GraphDB-SE with some parameters
        schema.setParameter("storage-folder", "./")
        schema.setParameter("repository-type", "file-repository")
        schema.setParameter("ruleset", "rdfs")

        // wrap it into a Sesame SailRepository
        val repository: SailRepository = new SailRepository(schema)

        // initialize
        repository.initialize()
        val connection: RepositoryConnection = repository.getConnection()

        // finally, create the DatasetGraph instance
        new SesameDataset(connection)
    }
    */
}
