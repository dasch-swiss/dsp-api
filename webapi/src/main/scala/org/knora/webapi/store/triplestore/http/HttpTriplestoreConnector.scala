/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.store.triplestore.http

import java.io._
import java.nio.file.{Files, Paths}
import java.util

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import org.apache.commons.lang3.StringUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.AuthCache
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.{BasicAuthCache, BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpEntity, HttpHost, NameValuePair}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{Resource, Statement}
import org.eclipse.rdf4j.rio.turtle._
import org.eclipse.rdf4j.rio.{RDFFormat, RDFHandler, RDFWriter, Rio}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.FakeTriplestore
import org.knora.webapi.util.SparqlResultProtocol._
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * Submits SPARQL queries and updates to a triplestore over HTTP. Supports different triplestores, which can be configured in
  * `application.conf`.
  */
class HttpTriplestoreConnector extends Actor with ActorLogging {

    // MIME type constants.
    private val mimeTypeApplicationJson = "application/json"
    private val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"
    private val mimeTypeTextTurtle = "text/turtle"
    private val mimeTypeApplicationSparqlUpdate = "application/sparql-update"

    private implicit val system: ActorSystem = context.system
    private val settings = Settings(system)
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
    private implicit val materializer: ActorMaterializer = ActorMaterializer()
    override val log: LoggingAdapter = akka.event.Logging(system, this.getClass.getName)

    private val triplestoreType = settings.triplestoreType

    private val targetHost: HttpHost = new HttpHost(settings.triplestoreHost, settings.triplestorePort, "http")

    private val credsProvider: BasicCredentialsProvider = new BasicCredentialsProvider
    credsProvider.setCredentials(new AuthScope(targetHost.getHostName, targetHost.getPort), new UsernamePasswordCredentials(settings.triplestoreUsername, settings.triplestorePassword))

    // Reading data should be quick.
    private val queryTimeoutMillis = settings.triplestoreQueryTimeout.toMillis.toInt

    private val queryRequestConfig = RequestConfig.custom()
        .setConnectTimeout(queryTimeoutMillis)
        .setConnectionRequestTimeout(queryTimeoutMillis)
        .setSocketTimeout(queryTimeoutMillis)
        .build

    private val queryHttpClient: CloseableHttpClient = HttpClients.custom
        .setDefaultCredentialsProvider(credsProvider)
        .setDefaultRequestConfig(queryRequestConfig)
        .build

    // An update for a bulk import could take a while.
    private val updateTimeoutMillis = settings.triplestoreUpdateTimeout.toMillis.toInt

    private val updateTimeoutConfig = RequestConfig.custom()
        .setConnectTimeout(updateTimeoutMillis)
        .setConnectionRequestTimeout(updateTimeoutMillis)
        .setSocketTimeout(updateTimeoutMillis)
        .build

    private val updateHttpClient: CloseableHttpClient = HttpClients.custom
        .setDefaultCredentialsProvider(credsProvider)
        .setDefaultRequestConfig(updateTimeoutConfig)
        .build

    private val queryPath: String = if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
        s"/repositories/${settings.triplestoreDatabaseName}"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
        if (settings.fusekiTomcat) {
            s"/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/query"
        } else {
            s"/${settings.triplestoreDatabaseName}/query"
        }
    } else {
        throw UnsuportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

    private val updatePath: String = if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
        s"/repositories/${settings.triplestoreDatabaseName}/statements"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki && settings.fusekiTomcat) {
        if (settings.fusekiTomcat) {
            s"/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/update"
        } else {
            s"/${settings.triplestoreDatabaseName}/update"
        }
    } else {
        throw UnsuportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

    private val checkRepositoryPath: String = if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
        "/rest/repositories"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
        if (settings.fusekiTomcat) {
            s"/${settings.fusekiTomcatContext}/$$/server"
        } else {
            "$/server"
        }
    } else {
        throw UnsuportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

    private val graphPath: String = if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
        s"/repositories/${settings.triplestoreDatabaseName}/statements"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
        if (settings.fusekiTomcat) {
            s"/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/get"
        } else {
            s"/${settings.triplestoreDatabaseName}/get"
        }
    } else {
        throw UnsuportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

    private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

    /**
      * Receives a message requesting a SPARQL select or update, and returns an appropriate response message or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive: PartialFunction[Any, Unit] = {
        case SparqlSelectRequest(sparql: String) => try2Message(sender(), sparqlHttpSelect(sparql), log)
        case SparqlConstructRequest(sparql: String) => try2Message(sender(), sparqlHttpConstruct(sparql), log)
        case SparqlExtendedConstructRequest(sparql: String) => try2Message(sender(), sparqlHttpExtendedConstruct(sparql), log)
        case SparqlConstructFileRequest(sparql: String, graphIri: IRI, outputFile: File) => try2Message(sender(), sparqlHttpConstructFile(sparql, graphIri, outputFile), log)
        case GraphFileRequest(graphIri: IRI, outputFile: File) => try2Message(sender(), sparqlHttpGraphFile(graphIri, outputFile), log)
        case SparqlUpdateRequest(sparql: String) => try2Message(sender(), sparqlHttpUpdate(sparql), log)
        case SparqlAskRequest(sparql: String) => try2Message(sender(), sparqlHttpAsk(sparql), log)
        case ResetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean) => try2Message(sender(), resetTripleStoreContent(rdfDataObjects, prependDefaults), log)
        case DropAllTriplestoreContent() => try2Message(sender(), dropAllTriplestoreContent(), log)
        case InsertTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]) => try2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
        case HelloTriplestore(msg: String) if msg == triplestoreType => sender ! HelloTriplestore(triplestoreType)
        case CheckRepositoryRequest() => try2Message(sender(), checkRepository(), log)
        case SearchIndexUpdateRequest(subjectIri: Option[String]) => try2Message(sender(), updateLuceneIndex(subjectIri), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResponse]].
      *
      * @param sparql the SPARQL SELECT query string.
      * @return a [[SparqlSelectResponse]].
      */
    private def sparqlHttpSelect(sparql: String): Try[SparqlSelectResponse] = {
        def parseJsonResponse(sparql: String, resultStr: String): Try[SparqlSelectResponse] = {
            val parseTry = Try {
                resultStr.parseJson.convertTo[SparqlSelectResponse]
            }

            parseTry match {
                case Success(parsed) => Success(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$resultStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Failure(TriplestoreResponseException("Couldn't parse JSON from triplestore", e, log))
            }
        }

        for {
            // Are we using the fake triplestore?
            resultStr <- if (settings.useFakeTriplestore) {
                // Yes: get the response from it.
                Try(FakeTriplestore.data(sparql))
            } else {
                // No: get the response from the real triplestore over HTTP.
                getSparqlHttpResponse(sparql, isUpdate = false)
            }

            // Are we preparing a fake triplestore?
            _ = if (settings.prepareFakeTriplestore) {
                // Yes: add the query and the response to it.
                FakeTriplestore.add(sparql, resultStr, log)
            }

            // _ = println(s"SPARQL: $logDelimiter$sparql")
            // _ = println(s"Result: $logDelimiter$resultStr")

            // Parse the response as a JSON object and generate a response message.
            responseMessage <- parseJsonResponse(sparql, resultStr)
        } yield responseMessage
    }

    /**
      * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlConstructResponse]].
      *
      * @param sparql the SPARQL CONSTRUCT query string.
      * @return a [[SparqlConstructResponse]]
      */
    private def sparqlHttpConstruct(sparql: String): Try[SparqlConstructResponse] = {
        // println(logDelimiter + sparql)

        /**
          * Converts a graph in parsed Turtle to a [[SparqlConstructResponse]].
          */
        class ConstructResponseTurtleHandler extends RDFHandler {
            /**
              * A collection of all the statements in the input file, grouped and sorted by subject IRI.
              */
            private var statements = Map.empty[IRI, Seq[(IRI, String)]]

            override def handleComment(comment: IRI): Unit = {}

            /**
              * Adds a statement to the collection `statements`.
              *
              * @param st the statement to be added.
              */
            override def handleStatement(st: Statement): Unit = {
                val subjectIri = st.getSubject.stringValue
                val predicateIri = st.getPredicate.stringValue
                val objectIri = st.getObject.stringValue
                val currentStatementsForSubject: Seq[(IRI, String)] = statements.getOrElse(subjectIri, Vector.empty[(IRI, String)])
                statements += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
            }

            override def endRDF(): Unit = {}

            override def handleNamespace(prefix: IRI, uri: IRI): Unit = {}

            override def startRDF(): Unit = {}

            def getConstructResponse: SparqlConstructResponse = {
                SparqlConstructResponse(statements)
            }
        }

        def parseTurtleResponse(sparql: String, turtleStr: String): Try[SparqlConstructResponse] = {
            val parseTry = Try {
                val turtleParser = new TurtleParser()
                val handler = new ConstructResponseTurtleHandler
                turtleParser.setRDFHandler(handler)
                turtleParser.parse(new StringReader(turtleStr), "")
                handler.getConstructResponse
            }

            parseTry match {
                case Success(parsed) => Success(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Failure(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
            }
        }

        for {
            turtleStr <- getSparqlHttpResponse(sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle)
            response <- parseTurtleResponse(sparql, turtleStr)
        } yield response
    }

    /**
      * Adds a named graph to CONSTRUCT query results.
      *
      * @param graphIri  the IRI of the named graph.
      * @param rdfWriter an [[RDFWriter]] for writing the result.
      */
    private class ConstructToGraphHandler(graphIri: IRI, rdfWriter: RDFWriter) extends RDFHandler {
        private val valueFactory: SimpleValueFactory = SimpleValueFactory.getInstance()
        private val context: Resource = valueFactory.createIRI(graphIri)

        override def startRDF(): Unit = rdfWriter.startRDF()

        override def endRDF(): Unit = rdfWriter.endRDF()

        override def handleNamespace(prefix: IRI, uri: IRI): Unit = rdfWriter.handleNamespace(prefix, uri)

        override def handleStatement(st: Statement): Unit = {
            val outputStatement = valueFactory.createStatement(
                st.getSubject,
                st.getPredicate,
                st.getObject,
                context
            )

            rdfWriter.handleStatement(outputStatement)
        }

        override def handleComment(comment: IRI): Unit = rdfWriter.handleComment(comment)
    }

    /**
      * Saves a graph in Turtle format to a file in TriG format.
      *
      * @param input      the Turtle data.
      * @param outputFile the output file.
      */
    private def turtleToTrig(input: Reader, graphIri: IRI, outputFile: File): Unit = {
        var maybeBufferedFileWriter: Option[BufferedWriter] = None

        try {
            maybeBufferedFileWriter = Some(new BufferedWriter(new FileWriter(outputFile)))
            val turtleParser = Rio.createParser(RDFFormat.TURTLE)
            val trigFileWriter: RDFWriter = Rio.createWriter(RDFFormat.TRIG, maybeBufferedFileWriter.get)
            val constructToGraphHandler = new ConstructToGraphHandler(graphIri = graphIri, rdfWriter = trigFileWriter)
            turtleParser.setRDFHandler(constructToGraphHandler)
            turtleParser.parse(input, "")
        } finally {
            maybeBufferedFileWriter.foreach(_.close)
            input.close()
        }
    }

    /**
      * Given a SPARQL CONSTRUCT query string, runs the query, saving the result as a TriG file.
      *
      * @param sparql     the SPARQL CONSTRUCT query string.
      * @param graphIri   the named graph IRI to be used in the TriG file.
      * @param outputFile the output file.
      * @return a [[FileWrittenResponse]].
      */
    private def sparqlHttpConstructFile(sparql: String, graphIri: IRI, outputFile: File): Try[FileWrittenResponse] = {
        for {
            turtleStr <- getSparqlHttpResponse(sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle)
            _ = turtleToTrig(input = new StringReader(turtleStr), graphIri = graphIri, outputFile = outputFile)
        } yield FileWrittenResponse()
    }

    /**
      * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlExtendedConstructResponse]].
      *
      * @param sparql the SPARQL CONSTRUCT query string.
      * @return a [[SparqlExtendedConstructResponse]]
      */
    private def sparqlHttpExtendedConstruct(sparql: String): Try[SparqlExtendedConstructResponse] = {
        val parseTry = for {
            turtleStr <- getSparqlHttpResponse(sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle)
            response <- SparqlExtendedConstructResponse.parseTurtleResponse(turtleStr, log)
        } yield response

        parseTry match {
            case Success(parsed) => Success(parsed)
            case Failure(e) =>
                Failure(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
        }
    }

    /**
      * Updates the Lucene full-text search index.
      */
    private def updateLuceneIndex(subjectIri: Option[IRI] = None): Try[SparqlUpdateResponse] = {
        if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
            val indexUpdateSparqlString = subjectIri match {
                case Some(definedSubjectIri) =>
                    // A subject's content has changed. Update the index for that subject.
                    s"""PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                       |INSERT DATA { luc:fullTextSearchIndex luc:addToIndex <$definedSubjectIri> . }
                     """.stripMargin

                case None =>
                    // Add new subjects to the index.
                    """PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                      |INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
                    """.stripMargin
            }

            for {
                _ <- getSparqlHttpResponse(indexUpdateSparqlString, isUpdate = true)
            } yield SparqlUpdateResponse()
        } else {
            Success(SparqlUpdateResponse())
        }
    }

    /**
      * Performs a SPARQL update operation.
      *
      * @param sparqlUpdate the SPARQL update.
      * @return a [[SparqlUpdateResponse]].
      */
    private def sparqlHttpUpdate(sparqlUpdate: String): Try[SparqlUpdateResponse] = {
        // println(logDelimiter + sparqlUpdate)

        for {
            // Send the request to the triplestore.
            _ <- getSparqlHttpResponse(sparqlUpdate, isUpdate = true)

            // If we're using GraphDB, update the full-text search index.
            _ = updateLuceneIndex()
        } yield SparqlUpdateResponse()
    }

    /**
      * Performs a SPARQL ASK query.
      *
      * @param sparql the SPARQL ASK query.
      * @return a [[SparqlAskResponse]].
      */
    def sparqlHttpAsk(sparql: String): Try[SparqlAskResponse] = {
        for {
            resultString <- getSparqlHttpResponse(sparql, isUpdate = false)
            _ = log.debug("sparqlHttpAsk - resultString: {}", resultString)

            result: Boolean = resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean]
        } yield SparqlAskResponse(result)
    }

    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean = true): Try[ResetTriplestoreContentACK] = {
        log.debug("resetTripleStoreContent")
        val resetTriplestoreResult = for {

            // drop old content
            _ <- dropAllTriplestoreContent()

            // insert new content
            _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)

            // any errors throwing exceptions until now are already covered so we can ACK the request
            result = ResetTriplestoreContentACK()
        } yield result

        resetTriplestoreResult
    }

    private def dropAllTriplestoreContent(): Try[DropAllTriplestoreContentACK] = {

        log.debug("==>> Drop All Data Start")

        val dropAllSparqlString =
            """
                DROP ALL
            """

        val response: Try[DropAllTriplestoreContentACK] = for {
            result: String <- getSparqlHttpResponse(dropAllSparqlString, isUpdate = true)
            _ = log.debug(s"==>> Drop All Data End, Result: $result")
        } yield DropAllTriplestoreContentACK()

        response.recover {
            case t: Exception => throw TriplestoreResponseException("Reset: Failed to execute DROP ALL", t, log)
        }

        response
    }

    /**
      * Inserts the data referenced inside the `rdfDataObjects` by appending it to a default set of `rdfDataObjects`
      * based on the list defined in `application.conf` under the `app.triplestore.default-rdf-data` key.
      *
      * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
      * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
      * @return [[InsertTriplestoreContentACK]]
      */
    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean = true): Try[InsertTriplestoreContentACK] = {
        try {
            log.debug("==>> Loading Data Start")

            val defaultRdfDataList = settings.tripleStoreConfig.getConfigList("default-rdf-data")
            val defaultRdfDataObjectList = defaultRdfDataList.asScala.map {
                config => RdfDataObjectFactory(config)
            }

            val completeRdfDataObjectList = if (prependDefaults) {
                defaultRdfDataObjectList ++ rdfDataObjects
            } else {
                rdfDataObjects
            }

            log.debug("insertDataIntoTriplestore - completeRdfDataObjectList: {}", completeRdfDataObjectList)

            for (elem <- completeRdfDataObjectList) {

                GraphProtocolAccessor.post(elem.name, elem.path)

                log.debug(s"added: ${elem.name}")
            }

            if (triplestoreType == TriplestoreTypes.HttpGraphDBSE || triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
                /* need to update the lucene index */
                updateLuceneIndex()
            }

            log.debug("==>> Loading Data End")
            Success(InsertTriplestoreContentACK())
        } catch {
            case e: TriplestoreUnsupportedFeatureException => Failure(e)
            case e: Exception => Failure(TriplestoreResponseException("Reset: Failed to execute insert into triplestore", e, log))
        }
    }

    /**
      * Checks connection to the triplestore.
      */
    private def checkRepository(): Try[CheckRepositoryResponse] = {
        if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
            checkGraphDBRepository()
        } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
            checkFusekiRepository()
        } else {
            throw UnsuportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
        }
    }

    /**
      * Checks the connection to a Fuseki triplestore.
      */
    private def checkFusekiRepository(): Try[CheckRepositoryResponse] = {
        import org.knora.webapi.messages.store.triplestoremessages.FusekiJsonProtocol._

        try {
            log.debug("checkFusekiRepository entered")

            // call endpoint returning all datasets

            val authCache: AuthCache = new BasicAuthCache
            val basicAuth: BasicScheme = new BasicScheme
            authCache.put(targetHost, basicAuth)

            val context: HttpClientContext = HttpClientContext.create
            context.setCredentialsProvider(credsProvider)
            context.setAuthCache(authCache)

            val httpGet = new HttpGet(checkRepositoryPath)
            httpGet.addHeader("Accept", mimeTypeApplicationJson)

            val responseStr = {
                var maybeResponse: Option[CloseableHttpResponse] = None

                try {
                    maybeResponse = Some(queryHttpClient.execute(targetHost, httpGet, context))
                    EntityUtils.toString(maybeResponse.get.getEntity)
                } finally {
                    maybeResponse match {
                        case Some(response) => response.close()
                        case None => ()
                    }
                }
            }

            val nameShouldBe = settings.triplestoreDatabaseName
            val fusekiServer: FusekiServer = JsonParser(responseStr).convertTo[FusekiServer]
            val neededDataset: Option[FusekiDataset] = fusekiServer.datasets.find(dataset => dataset.dsName == s"/$nameShouldBe" && dataset.dsState)

            if (neededDataset.nonEmpty) {
                // everything looks good
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceAvailable, msg = "Triplestore is available."))
            } else {
                // none of the available datasets meet our requirements
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.NotInitialized, msg = s"None of the active datasets meet our requirement of name: $nameShouldBe"))
            }
        } catch {
            case e: Exception =>
                // println("checkRepository - exception", e)
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceUnavailable, msg = s"Triplestore not available: ${e.getMessage}"))
        }
    }

    /**
      * Checks the connection to a GraphDB triplestore.
      */
    private def checkGraphDBRepository(): Try[CheckRepositoryResponse] = {
        // needs to be a local import or other things don't work (spray json black magic)
        import org.knora.webapi.messages.store.triplestoremessages.GraphDBJsonProtocol._

        try {
            log.debug("checkGraphDBRepository entered")

            // call endpoint returning all repositories

            val authCache: AuthCache = new BasicAuthCache
            val basicAuth: BasicScheme = new BasicScheme
            authCache.put(targetHost, basicAuth)

            val context: HttpClientContext = HttpClientContext.create
            context.setCredentialsProvider(credsProvider)
            context.setAuthCache(authCache)

            val httpGet = new HttpGet(checkRepositoryPath)
            httpGet.addHeader("Accept", mimeTypeApplicationJson)

            val responseStr = {
                var maybeResponse: Option[CloseableHttpResponse] = None

                try {
                    maybeResponse = Some(queryHttpClient.execute(targetHost, httpGet, context))
                    EntityUtils.toString(maybeResponse.get.getEntity)
                } finally {
                    maybeResponse match {
                        case Some(response) => response.close()
                        case None => ()
                    }
                }
            }

            val jsonArr = JsonParser(responseStr).asInstanceOf[JsArray]

            // parse json and check if the repository defined in 'application.conf' is present and correctly defined

            val repositories: Seq[GraphDBRepository] = jsonArr.elements.map(_.convertTo[GraphDBRepository])

            val idShouldBe = settings.triplestoreDatabaseName
            val sesameTypeForSEShouldBe = "owlim:MonitorRepository"
            val sesameTypeForFreeShouldBe = "graphdb:FreeSailRepository"

            val neededRepo: Option[GraphDBRepository] = repositories.find(repo => repo.id == idShouldBe && (repo.sesameType == sesameTypeForSEShouldBe || repo.sesameType == sesameTypeForFreeShouldBe))

            if (neededRepo.nonEmpty) {
                // everything looks good
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceAvailable, msg = "Triplestore is available."))
            } else {
                // none of the available repositories meet our requirements
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.NotInitialized, msg = s"None of the available repositories meet our requirements of id: $idShouldBe, sesameType: $sesameTypeForSEShouldBe or $sesameTypeForFreeShouldBe."))
            }
        } catch {
            case e: Exception =>
                // println("checkRepository - exception", e)
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceUnavailable, msg = s"Triplestore not available: ${e.getMessage}"))
        }
    }

    /**
      * Requests the contents of a named graph in TriG format, saving the response in a file.
      *
      * @param graphIri   the IRI of the named graph.
      * @param outputFile the file to be written.
      * @return a string containing the contents of the graph in TriG format.
      */
    private def sparqlHttpGraphFile(graphIri: IRI, outputFile: File): Try[FileWrittenResponse] = {
        val authCache: AuthCache = new BasicAuthCache
        val basicAuth: BasicScheme = new BasicScheme
        authCache.put(targetHost, basicAuth)

        val httpContext: HttpClientContext = HttpClientContext.create
        httpContext.setCredentialsProvider(credsProvider)
        httpContext.setAuthCache(authCache)

        val uriBuilder: URIBuilder = new URIBuilder(graphPath)

        if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
            uriBuilder.setParameter("infer", "false").setParameter("context", s"<$graphIri>")
        } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
            uriBuilder.setParameter("graph", s"$graphIri")
        } else {
            throw UnsuportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
        }

        val httpGet = new HttpGet(uriBuilder.build())
        httpGet.addHeader("Accept", mimeTypeTextTurtle)

        val responseTry = Try {
            val start = System.currentTimeMillis()

            var maybeResponse: Option[CloseableHttpResponse] = None

            try {
                maybeResponse = Some(queryHttpClient.execute(targetHost, httpGet, httpContext))

                val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
                val statusCategory: Int = statusCode / 100

                if (statusCategory != 2) {
                    log.error(s"Triplestore responded with HTTP code $statusCode to request for graph $graphIri")
                    throw TriplestoreResponseException(s"Triplestore responded with HTTP code $statusCode")
                }

                val took = System.currentTimeMillis() - start
                log.info(s"[$statusCode] GraphDB Query took: ${took}ms")

                Option(maybeResponse.get.getEntity) match {
                    case Some(responseEntity: HttpEntity) =>
                        // Stream the HTTP entity to the a temporary file.
                        val turtleFile = new File(outputFile.getCanonicalPath + ".ttl")
                        Files.copy(responseEntity.getContent, Paths.get(turtleFile.getCanonicalPath))

                        // Convert the Turtle to Trig.
                        val bufferedReader = new BufferedReader(new FileReader(turtleFile))
                        turtleToTrig(input = bufferedReader, graphIri = graphIri, outputFile = outputFile)
                        turtleFile.delete()
                        FileWrittenResponse()

                    case None =>
                        log.error(s"Triplestore returned no content for graph $graphIri")
                        throw TriplestoreResponseException(s"Triplestore returned no content")
                }
            } finally {
                maybeResponse.foreach(_.close)
            }
        }

        responseTry.recover {
            case tre: TriplestoreResponseException => throw tre

            case e: Exception =>
                log.error(e, s"Failed to connect to triplestore to get graph $graphIri")
                throw TriplestoreConnectionException(s"Failed to connect to triplestore", e, log)
        }
    }

    /**
      * Submits a SPARQL request to the triplestore and returns the response as a string.
      *
      * @param sparql         the SPARQL request to be submitted.
      * @param isUpdate       `true` if this is an update request.
      * @param acceptMimeType the MIME type to be provided in the HTTP Accept header.
      * @return the triplestore's response.
      */
    private def getSparqlHttpResponse(sparql: String, isUpdate: Boolean, acceptMimeType: String = mimeTypeApplicationSparqlResultsJson): Try[String] = {

        val authCache: AuthCache = new BasicAuthCache
        val basicAuth: BasicScheme = new BasicScheme
        authCache.put(targetHost, basicAuth)

        val httpContext: HttpClientContext = HttpClientContext.create
        httpContext.setCredentialsProvider(credsProvider)
        httpContext.setAuthCache(authCache)

        val (httpClient: CloseableHttpClient, httpPost: HttpPost) = if (isUpdate) {
            // Send updates as application/sparql-update (as per SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly").
            val requestEntity = new StringEntity(sparql, ContentType.create(mimeTypeApplicationSparqlUpdate, "UTF-8"))
            val updateHttpPost = new HttpPost(updatePath)
            updateHttpPost.setEntity(requestEntity)
            (updateHttpClient, updateHttpPost)
        } else {
            // Send queries as application/x-www-form-urlencoded (as per SPARQL 1.1 Protocol §2.1.2,
            // "query via POST with URL-encoded parameters"), so we can include the "infer" parameter when using GraphDB.

            val formParams = new util.ArrayList[NameValuePair]()

            if (triplestoreType == TriplestoreTypes.HttpGraphDBSE || triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
                formParams.add(new BasicNameValuePair("infer", "true"))
            }

            formParams.add(new BasicNameValuePair("query", sparql))
            val requestEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
            val queryHttpPost = new HttpPost(queryPath)
            queryHttpPost.setEntity(requestEntity)
            queryHttpPost.addHeader("Accept", acceptMimeType)
            (queryHttpClient, queryHttpPost)
        }

        val triplestoreResponseTry = Try {

            val start = System.currentTimeMillis()

            var maybeResponse: Option[CloseableHttpResponse] = None

            try {
                maybeResponse = Some(httpClient.execute(targetHost, httpPost, httpContext))

                val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
                    case Some(responseEntity) => EntityUtils.toString(responseEntity)
                    case None => ""
                }

                val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
                val statusCategory: Int = statusCode / 100

                if (statusCategory != 2) {
                    log.error(s"Triplestore responded with HTTP code $statusCode: $responseEntityStr,SPARQL query was:\n$sparql")
                    throw TriplestoreResponseException(s"Triplestore responded with HTTP code $statusCode: $responseEntityStr")
                }

                val took = System.currentTimeMillis() - start
                log.info(s"[$statusCode] Triplestore query took: ${took}ms")

                responseEntityStr
            } finally {
                maybeResponse.foreach(_.close)
            }
        }

        triplestoreResponseTry.recover {
            case tre: TriplestoreResponseException => throw tre

            case e: Exception =>
                log.error(e, s"Failed to connect to triplestore, SPARQL query was:\n$sparql")
                throw TriplestoreConnectionException(s"Failed to connect to triplestore", e, log)
        }
    }
}
