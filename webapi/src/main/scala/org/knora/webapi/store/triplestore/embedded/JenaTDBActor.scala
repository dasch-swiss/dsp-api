/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.embedded

import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Status}
import org.apache.commons.io.FileUtils
import org.apache.jena.graph.Node
import org.apache.jena.query._
import org.apache.jena.query.text._
import org.apache.jena.rdf.model.{Literal, ResourceFactory}
import org.apache.jena.sparql.core.Quad
import org.apache.jena.tdb.{TDB, TDBFactory}
import org.apache.jena.update.{UpdateAction, UpdateFactory, UpdateRequest}
import org.apache.lucene.store._
import org.knora.webapi.exceptions.{
  TriplestoreInternalException,
  TriplestoreResponseException,
  UnexpectedMessageException
}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.{
  SparqlSelectResult,
  SparqlSelectResultBody,
  SparqlSelectResultHeader,
  VariableResultsRow
}
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.ActorUtil._

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Performs SPARQL queries and updates using an embedded Jena TDB triplestore.
 */
class JenaTDBActor extends Actor with ActorLogging {

  private val system = context.system
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private val settings = KnoraSettings(system)

  private val persist = settings.tripleStoreConfig.getBoolean("embedded-jena-tdb.persisted")
  private val loadExistingData = settings.tripleStoreConfig.getBoolean("embedded-jena-tdb.loadExistingData")
  private val storagePath = Paths.get(settings.tripleStoreConfig.getString("embedded-jena-tdb.storage-path"))
  private val tdbStoragePath = Paths.get(storagePath.toString + "/db")

  private val tsType = settings.triplestoreType

  private val reloadDataOnStart = if (settings.tripleStoreConfig.getBoolean("reload-on-start")) {
    true
  } else if (tsType == "fake-triplestore") {
    // always reload if run as part of the fake-triplestore
    true
  } else {
    false
  }

  // Jena prefers to have 1 global dataset
  lazy val dataset: Dataset = getDataset

  var initialized: Boolean = false

  /**
   * The actor waits with processing messages until preStart is finished, so that the loading of data can finish
   * before any requests are processed.
   */
  override def preStart(): Unit = {
    if (persist) {
      if (reloadDataOnStart || !loadExistingData) {
        log.debug(s"Disk backed store. Delete and Create: ${tdbStoragePath.toAbsolutePath}")

        // only allowed, because the dataset gets created later on.
        FileUtils.deleteQuietly(tdbStoragePath.toFile)
      } else {
        log.debug(s"Disk backed store. Using: ${tdbStoragePath.toAbsolutePath}")
      }

      Files.createDirectories(tdbStoragePath)
    } else {
      log.debug(s"In-memory store: will reload data")
    }

    if (reloadDataOnStart) {

      val configList = settings.tripleStoreConfig.getConfigList("rdf-data")
      val rdfDataObjectList = configList.asScala.map { config =>
        RdfDataObjectFactory(config)
      }

      // remove everything
      dropAllTriplestoreContent()

      // insert data
      insertDataIntoTriplestore(rdfDataObjectList.toSeq)
    }
    this.initialized = true
  }

  /**
   * Receives a message requesting a SPARQL query or update, and returns an appropriate response message or
   * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
   * method first returns `Failure` to the sender, then throws an exception.
   */
  def receive: Receive = {
    case SparqlSelectRequest(sparqlSelectString) =>
      future2Message(sender(), executeSparqlSelectQuery(sparqlSelectString), log)
    case SparqlUpdateRequest(sparqlUpdateString) =>
      future2Message(sender(), executeSparqlUpdateQuery(sparqlUpdateString), log)
    case ResetRepositoryContent(rdfDataObjects, prependDefaults) =>
      future2Message(sender(), resetTripleStoreContent(rdfDataObjects, prependDefaults), log)
    case DropAllTRepositoryContent() => future2Message(sender(), Future(dropAllTriplestoreContent()), log)
    case InsertRepositoryContent(rdfDataObjects) =>
      future2Message(sender(), Future(insertDataIntoTriplestore(rdfDataObjects)), log)
    case HelloTriplestore(msg) if msg == tsType => sender() ! HelloTriplestore(tsType)
    case other =>
      sender() ! Status.Failure(
        UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}")
      )
  }

  /**
   * Submits a SPARQL query to the embedded Jena TDB store and returns the response as a [[SparqlSelectResult]].
   *
   * @param queryString the SPARQL request to be submitted.
   * @return [[SparqlSelectResult]].
   */
  private def executeSparqlSelectQuery(queryString: String): Future[SparqlSelectResult] = {

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
      val variableResultsRows = resultSet.asScala.foldLeft(Vector.empty[VariableResultsRow]) { (rowAcc, row) =>
        val mapToWrap = resultVars.asScala.foldLeft(Map.empty[String, String]) { case (varAcc, varName) =>
          Option(row.get(varName)) match {
            case Some(literal: Literal) if literal.getLexicalForm.isEmpty =>
              varAcc // Omit variables with empty values.

            case Some(literal: Literal) =>
              varAcc + (varName -> literal.getLexicalForm)

            case Some(otherValue) =>
              varAcc + (varName -> otherValue.toString)

            case None => varAcc
          }
        }

        // Omit empty rows.
        if (mapToWrap.nonEmpty) {
          rowAcc :+ VariableResultsRow(
            new ErrorHandlingMap(
              mapToWrap,
              { key: String =>
                s"No value found for SPARQL query variable '$key' in query result row"
              }
            )
          )
        } else {
          rowAcc
        }
      }

      qExec.close()

      // Commit transaction
      this.dataset.commit()

      //println("==>> SparqlSelect End")

      Future.successful(
        SparqlSelectResult(
          SparqlSelectResultHeader(resultVars.asScala.toSeq),
          SparqlSelectResultBody(variableResultsRows)
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
   * Submits a SPARQL update request to the embedded Jena TDB store, and returns a [[SparqlUpdateResponse]] if the
   * operation completed successfully.
   *
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
   *
   * @param rdfDataObjects a list of [[RdfDataObject]] instances describing the files to be loaded.
   * @return an [[ResetRepositoryContentACK]] indicating that the operation completed successfully.
   */
  private def resetTripleStoreContent(
    rdfDataObjects: Seq[RdfDataObject],
    prependDefaults: Boolean = true
  ): Future[ResetRepositoryContentACK] = {

    val resetTriplestoreResult = for {

      // drop old content
      dropResult <- Future(dropAllTriplestoreContent())

      // insert new content
      insertResult <- Future(insertDataIntoTriplestore(rdfDataObjects))

      // manually rebuild the Lucene index
      indexUpdateResult <- Future(updateIndex())

      // any errors throwing exceptions until now are already covered so we can ACK the request
      result = ResetRepositoryContentACK()
    } yield result

    resetTriplestoreResult
  }

  /**
   * Drops all content from the triplestore.
   *
   * @return a [[DropAllRepositoryContentACK]]
   */
  private def dropAllTriplestoreContent(): DropAllRepositoryContentACK = {

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

      DropAllRepositoryContentACK()
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
   *
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
   *
   * @return a [[Boolean]] denoting if the update was successful
   */
  private def updateIndex(): Boolean = {

    this.dataset.begin(ReadWrite.WRITE)
    try {
      // get index.
      val dgt: DatasetGraphText = dataset.asDatasetGraph().asInstanceOf[DatasetGraphText]
      val textIndex = dgt.getTextIndex

      // get entity definitions from index
      val entityDefinition = textIndex.getDocDef

      // get the indexed fields
      val fields: List[String] = entityDefinition.fields().asScala.toList

      // go over all properties and find them in the dataset and add them to the index
      for (field: String <- fields) {
        for (prop: Node <- entityDefinition.getPredicates(field).asScala) {
          val quadIter: Iterator[Quad] = dgt.find(Node.ANY, Node.ANY, prop, Node.ANY).asScala
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
   *
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

      val ds = TDBFactory.createDataset(tdbStoragePath.toAbsolutePath.toString)

      // Lucene, on disk
      //val indexDirectory: Directory = new SimpleFSDirectory(new File(luceneIndexPath.getAbsolutePath))
      val indexDirectory: Directory = new ByteBuffersDirectory()

      TextDatasetFactory.createLucene(ds, indexDirectory, entDef, null)
    } else {

      // Set UnionDefaultGraph globally
      TDB.getContext.set(TDB.symUnionDefaultGraph, true)

      val ds = TDBFactory.createDataset()

      // Lucene, in memory.
      val indexDirectory: Directory = new ByteBuffersDirectory()

      TextDatasetFactory.createLucene(ds, indexDirectory, entDef, null)
    }
  }
}
