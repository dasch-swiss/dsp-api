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

import java.io._
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Status}
import org.apache.commons.io.FileUtils
import org.apache.jena.graph.Node
import org.apache.jena.query._
import org.apache.jena.query.text.{DatasetGraphText, Entity, EntityDefinition, TextDatasetFactory, TextQueryFuncs}
import org.apache.jena.rdf.model.{Literal, ResourceFactory}
import org.apache.jena.sparql.core.Quad
import org.apache.jena.tdb.{TDB, TDBFactory}
import org.apache.jena.update.{UpdateAction, UpdateFactory, UpdateRequest}
import org.apache.lucene.store._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.ErrorHandlingMap

import scala.collection.JavaConversions._
import scala.concurrent.Future


/**
  * Performs SPARQL queries and updates using an embedded Jena TDB triplestore.
  */
class JenaTDBActor extends Actor with ActorLogging {

    private val system = context.system
    private implicit val executionContext = system.dispatcher
    private val settings = Settings(system)


    private val persist = settings.tripleStoreConfig.getBoolean("embedded-jena-tdb.persisted")
    private val loadExistingData = settings.tripleStoreConfig.getBoolean("embedded-jena-tdb.loadExistingData")
    private val storagePath = new File(settings.tripleStoreConfig.getString("embedded-jena-tdb.storage-path"))
    private val tdbStoragePath = new File(storagePath + "/db")
    private val luceneIndexPath = new File(storagePath + "/lucene")

    private val tsType = settings.triplestoreType

    private val reloadDataOnStart = if (settings.tripleStoreConfig.getBoolean("reload-on-start")) {
        true
    } else if (tsType == "fake-triplestore") {
        // always reload if run as part of the fake-triplestore
        true
    } else {
        false
    }

    private val unionGraphModelName = "urn:x-arq:UnionGraph"


    // Jena prefers to have 1 global dataset
    lazy val dataset = getDataset

    var initialized: Boolean = false

    /**
      * The actor waits with processing messages until preStart is finished, so that the loading of data can finish
      * before any requests are processed.
      */
    override def preStart(): Unit = {
        if (persist) {
            if (reloadDataOnStart || !loadExistingData) {
                log.debug(s"Disk backed store. Delete and Create: ${tdbStoragePath.getAbsolutePath}")

                // only allowed, because the dataset gets created later on.
                FileUtils.deleteQuietly(tdbStoragePath)
            } else {
                log.debug(s"Disk backed store. Using: ${tdbStoragePath.getAbsolutePath}")
            }
            tdbStoragePath.mkdirs()
        } else {
            log.debug(s"In-memory store: will reload data")
        }

        if (reloadDataOnStart) {

            val configList = settings.tripleStoreConfig.getConfigList("rdf-data")
            val rdfDataObjectList = configList.map {
                config => RdfDataObjectFactory(config)
            }

            // remove everything
            dropAllTriplestoreContent()

            // insert data
            insertDataIntoTriplestore(rdfDataObjectList)
        }
        this.initialized = true
    }

    /**
      * Receives a message requesting a SPARQL query or update, and returns an appropriate response message or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case SparqlSelectRequest(sparqlSelectString) => future2Message(sender(), executeSparqlSelectQuery(sparqlSelectString), log)
        case BeginUpdateTransaction() => future2Message(sender(), beginUpdateTransaction(), log)
        case CommitUpdateTransaction(transactionID) => future2Message(sender(), commitUpdateTransaction(transactionID), log)
        case RollbackUpdateTransaction(transactionID) => future2Message(sender(), rollbackUpdateTransaction(transactionID), log)
        case SparqlUpdateRequest(transactionID, sparqlUpdateString) => future2Message(sender(), executeSparqlUpdateQuery(transactionID, sparqlUpdateString), log)
        case ResetTriplestoreContent(rdfDataObjects) => future2Message(sender(), resetTripleStoreContent(rdfDataObjects), log)
        case DropAllTriplestoreContent() => future2Message(sender(), Future(dropAllTriplestoreContent()), log)
        case InsertTriplestoreContent(rdfDataObjects) => future2Message(sender(), Future(insertDataIntoTriplestore(rdfDataObjects)), log)
        case Initialized() => future2Message(sender(), Future.successful(InitializedResponse(this.initialized)), log)
        case HelloTriplestore(msg) if msg == tsType => sender ! HelloTriplestore(tsType)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

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

            val resultVars = resultSet.getResultVars

            // Convert the results to a list of VariableResultsRows.
            val variableResultsRows = resultSet.foldLeft(Vector.empty[VariableResultsRow]) {
                (rowAcc, row) =>
                    val mapToWrap = resultVars.foldLeft(Map.empty[String, String]) {
                        case (varAcc, varName) =>
                            Option(row.get(varName)) match {
                                case Some(literal: Literal) if literal.getLexicalForm.isEmpty => varAcc // Omit variables with empty values.

                                case Some(literal: Literal) =>
                                    varAcc + (varName -> literal.getLexicalForm)

                                case Some(otherValue) =>
                                    varAcc + (varName -> otherValue.toString)

                                case None => varAcc
                            }
                    }

                    // Omit empty rows.
                    if (mapToWrap.nonEmpty) {
                        rowAcc :+ VariableResultsRow(new ErrorHandlingMap(mapToWrap, { key: String => s"No value found for SPARQL query variable '$key' in query result row" }))
                    } else {
                        rowAcc
                    }
            }

            qExec.close()

            // Commit transaction
            this.dataset.commit()

            //println("==>> SparqlSelect End")

            Future.successful(
                SparqlSelectResponse(
                    SparqlSelectResponseHeader(resultVars),
                    SparqlSelectResponseBody(variableResultsRows)
                )
            )
        } catch {
            case ex: Throwable =>
                this.dataset.abort()
                /*
                println("================")
                println(queryString)
                println("================")
                */
                Future.failed(TriplestoreResponseException("Triplestore transaction failed", ex, log))
        } finally {
            this.dataset.end()
        }
    }

    /**
      * Begins a SPARQL Update transaction.
      * @return an [[UpdateTransactionBegun]].
      */
    private def beginUpdateTransaction(): Future[UpdateTransactionBegun] = {
        // TODO: support transaction management in this actor.
        Future(UpdateTransactionBegun(UUID.randomUUID()))
    }

    /**
      * Commits a SPARQL update transaction.
      * @param transactionID the transaction ID.
      * @return an [[UpdateTransactionCommitted]].
      */
    private def commitUpdateTransaction(transactionID: UUID): Future[UpdateTransactionCommitted] = {
        // TODO: support transaction management in this actor.
        Future(UpdateTransactionCommitted(transactionID))
    }

    /**
      * Rolls back a SPARQL update transaction.
      * @param transactionID the transaction ID.
      * @return an [[UpdateTransactionRolledBack]].
      */
    private def rollbackUpdateTransaction(transactionID: UUID): Future[UpdateTransactionRolledBack] = {
        // TODO: support transaction management in this actor.
        Future(UpdateTransactionRolledBack(transactionID))
    }

    /**
      * Submits a SPARQL update request to the embedded Jena TDB store, and returns a [[SparqlUpdateResponse]] if the
      * operation completed successfully.
      * @param transactionID the transaction ID. // TODO: support transaction management in this actor.
      * @param updateString the SPARQL update to be submitted.
      * @return a [[SparqlUpdateResponse]].
      */
    private def executeSparqlUpdateQuery(transactionID: UUID, updateString: String): Future[SparqlUpdateResponse] = {
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

            Future.successful(SparqlUpdateResponse(transactionID))
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
      * @return an [[ResetTriplestoreContentACK]] indicating that the operation completed successfully.
      */
    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTriplestoreContentACK] = {

        val resetTriplestoreResult = for {

        // drop old content
            dropResult <- Future(dropAllTriplestoreContent())

            // insert new content
            insertResult <- Future(insertDataIntoTriplestore(rdfDataObjects))

            // manually rebuild the Lucene index
            indexUpdateResult <- Future(updateIndex)

            // any errors throwing exceptions until now are already covered so we can ACK the request
            result = ResetTriplestoreContentACK()
        } yield result

        resetTriplestoreResult
    }

    /**
      * Drops all content from the triplestore.
      * @return a [[DropAllTriplestoreContentACK]]
      */
    private def dropAllTriplestoreContent(): DropAllTriplestoreContentACK = {

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

            DropAllTriplestoreContentACK()
        } catch {
            case ex: Throwable =>
                this.dataset.abort()
                throw TriplestoreResponseException("Triplestore transaction failed", ex, log)
        } finally {
            this.dataset.end()
        }

    }

    /**
      * Inserts the data referenced in each [[RdfDataObject]]
      * @param rdfDataObjects a sequence holding [[RdfDataObject]]
      * @return a [[InsertTriplestoreContentACK]]
      */
    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject]): InsertTriplestoreContentACK = {

        // log.debug("ResetTripleStoreContent ...")

        // Start write transaction
        this.dataset.begin(ReadWrite.WRITE)
        try {

            log.debug("==>> Loading Data Start")

            // instantiate update request
            val updateRequest: UpdateRequest = UpdateFactory.create()

            // add: data
            for (elem <- rdfDataObjects) {
                log.debug(s"Adding: ${elem.name} - ${elem.path}")
                updateRequest.add(s"CREATE GRAPH <${elem.name}>")
                updateRequest.add(s"LOAD <file:${elem.path}> INTO GRAPH <${elem.name}>")
            }

            // perform the operations.
            log.debug("===>> Executing")
            UpdateAction.execute(updateRequest, this.dataset)

            //println("# State of dataset after reset")
            //SSE.write(this.dataset)
            log.debug("==>> Loading Data End")

            // Commit transaction
            this.dataset.commit()

            InsertTriplestoreContentACK()
        } catch {
            case ex: Throwable =>
                this.dataset.abort()
                throw TriplestoreResponseException("Triplestore transaction failed", ex, log)
        } finally {
            this.dataset.end()
        }

    }

    /**
      * Used to manually refresh the Lucene index after changing data in the triplestore.
      * @return a [[Boolean]] denoting if the update was successful
      */
    private def updateIndex: Boolean = {

        this.dataset.begin(ReadWrite.WRITE)
        try {
            // get index.
            val dgt: DatasetGraphText = dataset.asDatasetGraph().asInstanceOf[DatasetGraphText]
            val textIndex = dgt.getTextIndex()

            // get entity definitions from index
            val entityDefinition = textIndex.getDocDef()

            // get the indexed fields
            val fields: List[String] = entityDefinition.fields().toList

            // go over all properties and find them in the dataset and add them to the index
            for (field: String <- fields) {
                for (prop: Node <- entityDefinition.getPredicates(field)) {
                    val quadIter: Iterator[Quad] = dgt.find(Node.ANY, Node.ANY, prop, Node.ANY)
                    while (quadIter.hasNext) {
                        val quad: Quad = quadIter.next()
                        val entity: Entity = TextQueryFuncs.entityFromQuad(entityDefinition, quad)
                        if (entity != null) {
                            textIndex.addEntity(entity)
                        }
                    }
                }
            }
            textIndex.commit()
            this.dataset.commit()
            true
        } catch {
            case ex: Throwable =>
                this.dataset.abort()
                throw TriplestoreInternalException("Exception thrown during Lucene index update", ex, log)
                false
        } finally {
            this.dataset.end()
        }
    }

    /**
      * Creates the dataset with a Lucene index attached. The triplestore dataset is either disk-backed or in-memory,
      * depending on the settings. The Lucene index is always in-memory.
      * @return a [[Dataset]]
      */
    private def getDataset: Dataset = {

        // Define which fields should be indexed by lucene
        val knoraBase = "http://www.knora.org/ontology/knora-base#"
        val rdfs = "http://www.w3.org/2000/01/rdf-schema#"
        val entDef = new EntityDefinition("uri", "text", ResourceFactory.createProperty(knoraBase, "valueHasString"))
        entDef.setPrimaryPredicate(ResourceFactory.createProperty(rdfs, "label"))
        entDef.setPrimaryPredicate(ResourceFactory.createProperty(knoraBase, "valueHasComment"))

        // Add a UUID for each entry so that when a triple is removed then also the index entry can be removed
        entDef.setUidField("uid")

        if (persist) {

            // Set UnionDefaultGraph globally
            TDB.getContext.set(TDB.symUnionDefaultGraph, true)

            val ds = TDBFactory.createDataset(tdbStoragePath.getAbsolutePath)

            // Lucene, on disk
            //val indexDirectory: Directory = new SimpleFSDirectory(new File(luceneIndexPath.getAbsolutePath))
            val indexDirectory: Directory = new RAMDirectory()

            TextDatasetFactory.createLucene(ds, indexDirectory, entDef, null)
        } else {

            // Set UnionDefaultGraph globally
            TDB.getContext.set(TDB.symUnionDefaultGraph, true)

            val ds = TDBFactory.createDataset()

            // Lucene, in memory.
            val indexDirectory: Directory = new RAMDirectory()

            TextDatasetFactory.createLucene(ds, indexDirectory, entDef, null)
        }
    }
}
