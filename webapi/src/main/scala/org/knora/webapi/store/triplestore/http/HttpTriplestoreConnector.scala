/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import java.io.BufferedInputStream
import java.net.URI
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.nio.charset.StandardCharsets
import java.util

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import akka.event.LoggingAdapter
import org.apache.commons.lang3.StringUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.AuthCache
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost, HttpPut}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.{ContentType, FileEntity, StringEntity}
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.{BasicAuthCache, BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpEntity, HttpHost, HttpRequest, NameValuePair}
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.store.triplestoremessages.SparqlResultProtocol._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.FakeTriplestore
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, TriplestoreTypes}
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.FileUtil
import spray.json._

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * Submits SPARQL queries and updates to a triplestore over HTTP. Supports different triplestores, which can be configured in
 * `application.conf`.
 */
class HttpTriplestoreConnector extends Actor with ActorLogging with InstrumentationSupport {

  // MIME type constants.
  private val mimeTypeApplicationJson              = "application/json"
  private val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"
  private val mimeTypeTextTurtle                   = "text/turtle"
  private val mimeTypeApplicationSparqlUpdate      = "application/sparql-update"
  private val mimeTypeApplicationNQuads            = "application/n-quads"

  private implicit val system: ActorSystem        = context.system
  private val settings                            = KnoraSettings(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
  override val log: LoggingAdapter                = akka.event.Logging(system, this.getClass.getName)

  private val triplestoreType = settings.triplestoreType

  private val targetHost: HttpHost = new HttpHost(settings.triplestoreHost, settings.triplestorePort, "http")

  private val credsProvider: BasicCredentialsProvider = new BasicCredentialsProvider
  credsProvider.setCredentials(
    new AuthScope(targetHost.getHostName, targetHost.getPort),
    new UsernamePasswordCredentials(settings.triplestoreUsername, settings.triplestorePassword)
  )

  // Reading data should be quick, except when it is not ;-)
  private val queryTimeoutMillis = settings.triplestoreQueryTimeout.toMillis.toInt

  private val queryRequestConfig = RequestConfig
    .custom()
    .setConnectTimeout(queryTimeoutMillis)
    .setConnectionRequestTimeout(queryTimeoutMillis)
    .setSocketTimeout(queryTimeoutMillis)
    .build

  private val queryHttpClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(queryRequestConfig)
    .build

  // Some updates could take a while.
  private val updateTimeoutMillis = settings.triplestoreUpdateTimeout.toMillis.toInt

  private val updateTimeoutConfig = RequestConfig
    .custom()
    .setConnectTimeout(updateTimeoutMillis)
    .setConnectionRequestTimeout(updateTimeoutMillis)
    .setSocketTimeout(updateTimeoutMillis)
    .build

  private val updateHttpClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(updateTimeoutConfig)
    .build

  // For updates that could take a very long time.
  private val longTimeoutMillis = settings.triplestoreUpdateTimeout.toMillis.toInt * 10

  private val longRequestConfig = RequestConfig
    .custom()
    .setConnectTimeout(longTimeoutMillis)
    .setConnectionRequestTimeout(longTimeoutMillis)
    .setSocketTimeout(longTimeoutMillis)
    .build

  private val longRequestClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(longRequestConfig)
    .build

  private val queryPath: String =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      s"/repositories/${settings.triplestoreDatabaseName}"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      s"/${settings.triplestoreDatabaseName}/query"
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

  private val sparqlUpdatePath: String =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      s"/repositories/${settings.triplestoreDatabaseName}/statements"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      s"/${settings.triplestoreDatabaseName}/update"
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

  private val checkRepositoryPath: String =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      "/rest/repositories"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      "/$/server"
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

  private val graphPath: String =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      s"/repositories/${settings.triplestoreDatabaseName}/statements"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      s"/${settings.triplestoreDatabaseName}/get"
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

  private val repositoryDownloadPath =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      s"/repositories/${settings.triplestoreDatabaseName}/statements"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      s"/${settings.triplestoreDatabaseName}"
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

  private val repositoryUploadPath = repositoryDownloadPath

  private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

  private val dataInsertPath =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      s"/repositories/${settings.triplestoreDatabaseName}/rdf-graphs/service"
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      s"/${settings.triplestoreDatabaseName}/data"
    } else {
      throw TriplestoreUnsupportedFeatureException(s"$triplestoreType is not supported!")
    }

  /**
   * Receives a message requesting a SPARQL select or update, and returns an appropriate response message or
   * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
   * method first returns `Failure` to the sender, then throws an exception.
   */
  def receive: PartialFunction[Any, Unit] = {
    case SparqlSelectRequest(sparql: String) => try2Message(sender(), sparqlHttpSelect(sparql), log)
    case sparqlConstructRequest: SparqlConstructRequest =>
      try2Message(sender(), sparqlHttpConstruct(sparqlConstructRequest), log)
    case sparqlExtendedConstructRequest: SparqlExtendedConstructRequest =>
      try2Message(sender(), sparqlHttpExtendedConstruct(sparqlExtendedConstructRequest), log)
    case SparqlConstructFileRequest(
          sparql: String,
          graphIri: IRI,
          outputFile: Path,
          outputFormat: QuadFormat,
          featureFactoryConfig: FeatureFactoryConfig
        ) =>
      try2Message(
        sender(),
        sparqlHttpConstructFile(sparql, graphIri, outputFile, outputFormat, featureFactoryConfig),
        log
      )
    case NamedGraphFileRequest(
          graphIri: IRI,
          outputFile: Path,
          outputFormat: QuadFormat,
          featureFactoryConfig: FeatureFactoryConfig
        ) =>
      try2Message(sender(), sparqlHttpGraphFile(graphIri, outputFile, outputFormat, featureFactoryConfig), log)
    case NamedGraphDataRequest(graphIri: IRI) => try2Message(sender(), sparqlHttpGraphData(graphIri), log)
    case SparqlUpdateRequest(sparql: String)  => try2Message(sender(), sparqlHttpUpdate(sparql), log)
    case SparqlAskRequest(sparql: String)     => try2Message(sender(), sparqlHttpAsk(sparql), log)
    case ResetRepositoryContent(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean) =>
      try2Message(sender(), resetTripleStoreContent(rdfDataObjects, prependDefaults), log)
    case DropAllTRepositoryContent() => try2Message(sender(), dropAllTriplestoreContent(), log)
    case InsertRepositoryContent(rdfDataObjects: Seq[RdfDataObject]) =>
      try2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
    case HelloTriplestore(msg: String) if msg == triplestoreType => sender ! HelloTriplestore(triplestoreType)
    case CheckTriplestoreRequest()                               => try2Message(sender(), checkTriplestore(), log)
    case SearchIndexUpdateRequest(subjectIri: Option[String]) =>
      try2Message(sender(), updateLuceneIndex(subjectIri), log)
    case DownloadRepositoryRequest(outputFile: Path, featureFactoryConfig: FeatureFactoryConfig) =>
      try2Message(sender(), downloadRepository(outputFile, featureFactoryConfig), log)
    case UploadRepositoryRequest(inputFile: Path) => try2Message(sender(), uploadRepository(inputFile), log)
    case InsertGraphDataContentRequest(graphContent: String, graphName: String) =>
      try2Message(sender(), insertDataGraphRequest(graphContent, graphName), log)
    case SimulateTimeoutRequest() => try2Message(sender(), doSimulateTimeout(), log)
    case other =>
      sender ! Status.Failure(
        UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}")
      )
  }

  /**
   * Simulates a read timeout.
   */
  private def doSimulateTimeout(): Try[SparqlSelectResult] = {
    val sparql = """SELECT ?foo WHERE {
                   |    BIND("foo" AS ?foo)
                   |}""".stripMargin

    sparqlHttpSelect(sparql = sparql, simulateTimeout = true)
  }

  /**
   * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResult]].
   *
   * @param sparql the SPARQL SELECT query string.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @return a [[SparqlSelectResult]].
   */
  private def sparqlHttpSelect(sparql: String, simulateTimeout: Boolean = false): Try[SparqlSelectResult] = {
    def parseJsonResponse(sparql: String, resultStr: String): Try[SparqlSelectResult] = {
      val parseTry = Try {
        resultStr.parseJson.convertTo[SparqlSelectResult]
      }

      parseTry match {
        case Success(parsed) => Success(parsed)
        case Failure(e) =>
          log.error(
            e,
            s"Couldn't parse response from triplestore:$logDelimiter$resultStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql"
          )
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
                     getSparqlHttpResponse(sparql, isUpdate = false, simulateTimeout = simulateTimeout)
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
   * @param sparqlConstructRequest the request message.
   * @return a [[SparqlConstructResponse]]
   */
  private def sparqlHttpConstruct(sparqlConstructRequest: SparqlConstructRequest): Try[SparqlConstructResponse] = {
    // println(logDelimiter + sparql)

    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(sparqlConstructRequest.featureFactoryConfig)

    def parseTurtleResponse(
      sparql: String,
      turtleStr: String,
      rdfFormatUtil: RdfFormatUtil
    ): Try[SparqlConstructResponse] = {
      val parseTry = Try {
        val rdfModel: RdfModel                                 = rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
        val statementMap: mutable.Map[IRI, Seq[(IRI, String)]] = mutable.Map.empty

        for (st: Statement <- rdfModel) {
          val subjectIri   = st.subj.stringValue
          val predicateIri = st.pred.stringValue
          val objectIri    = st.obj.stringValue
          val currentStatementsForSubject: Seq[(IRI, String)] =
            statementMap.getOrElse(subjectIri, Vector.empty[(IRI, String)])
          statementMap += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
        }

        SparqlConstructResponse(statementMap.toMap)
      }

      parseTry match {
        case Success(parsed) => Success(parsed)
        case Failure(e) =>
          log.error(
            e,
            s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql"
          )
          Failure(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
      }
    }

    for {
      turtleStr <-
        getSparqlHttpResponse(sparqlConstructRequest.sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle)

      response <- parseTurtleResponse(
                    sparql = sparqlConstructRequest.sparql,
                    turtleStr = turtleStr,
                    rdfFormatUtil = rdfFormatUtil
                  )
    } yield response
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, saving the result in a file.
   *
   * @param sparql       the SPARQL CONSTRUCT query string.
   * @param graphIri     the named graph IRI to be used in the output file.
   * @param outputFile   the output file.
   * @param outputFormat the output file format.
   * @return a [[FileWrittenResponse]].
   */
  private def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat,
    featureFactoryConfig: FeatureFactoryConfig
  ): Try[FileWrittenResponse] = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)

    for {
      turtleStr <- getSparqlHttpResponse(sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle)

      _ = rdfFormatUtil.turtleToQuadsFile(
            rdfSource = RdfStringSource(turtleStr),
            graphIri = graphIri,
            outputFile = outputFile,
            outputFormat = outputFormat
          )
    } yield FileWrittenResponse()
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returns the result as a [[SparqlExtendedConstructResponse]].
   *
   * @param sparqlExtendedConstructRequest the request message.
   * @return a [[SparqlExtendedConstructResponse]]
   */
  private def sparqlHttpExtendedConstruct(
    sparqlExtendedConstructRequest: SparqlExtendedConstructRequest
  ): Try[SparqlExtendedConstructResponse] = {
    // println(sparql)
    val rdfFormatUtil: RdfFormatUtil =
      RdfFeatureFactory.getRdfFormatUtil(sparqlExtendedConstructRequest.featureFactoryConfig)

    val parseTry = for {
      turtleStr <- getSparqlHttpResponse(
                     sparqlExtendedConstructRequest.sparql,
                     isUpdate = false,
                     acceptMimeType = mimeTypeTextTurtle
                   )

      response <- SparqlExtendedConstructResponse.parseTurtleResponse(
                    turtleStr = turtleStr,
                    rdfFormatUtil = rdfFormatUtil,
                    log = log
                  )
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
  private def updateLuceneIndex(subjectIri: Option[IRI] = None): Try[SparqlUpdateResponse] =
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

  /**
   * Performs a SPARQL update operation.
   *
   * @param sparqlUpdate the SPARQL update.
   * @return a [[SparqlUpdateResponse]].
   */
  private def sparqlHttpUpdate(sparqlUpdate: String): Try[SparqlUpdateResponse] =
    // println(logDelimiter + sparqlUpdate)
    for {
      // Send the request to the triplestore.
      _ <- getSparqlHttpResponse(sparqlUpdate, isUpdate = true)

      // If we're using GraphDB, update the full-text search index.
      _ = updateLuceneIndex()
    } yield SparqlUpdateResponse()

  /**
   * Performs a SPARQL ASK query.
   *
   * @param sparql the SPARQL ASK query.
   * @return a [[SparqlAskResponse]].
   */
  def sparqlHttpAsk(sparql: String): Try[SparqlAskResponse] =
    for {
      resultString <- getSparqlHttpResponse(sparql, isUpdate = false)
      _             = log.debug("sparqlHttpAsk - resultString: {}", resultString)

      result: Boolean = resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean]
    } yield SparqlAskResponse(result)

  private def resetTripleStoreContent(
    rdfDataObjects: Seq[RdfDataObject],
    prependDefaults: Boolean = true
  ): Try[ResetRepositoryContentACK] = {
    log.debug("resetTripleStoreContent")
    val resetTriplestoreResult = for {

      // drop old content
      _ <- dropAllTriplestoreContent()

      // insert new content
      _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)

      // any errors throwing exceptions until now are already covered so we can ACK the request
      result = ResetRepositoryContentACK()
    } yield result

    resetTriplestoreResult
  }

  private def dropAllTriplestoreContent(): Try[DropAllRepositoryContentACK] = {

    log.debug("==>> Drop All Data Start")

    val dropAllSparqlString =
      """
                DROP ALL
            """

    val response: Try[DropAllRepositoryContentACK] = for {
      result: String <- getSparqlHttpResponse(dropAllSparqlString, isUpdate = true)
      _               = log.debug(s"==>> Drop All Data End, Result: $result")
    } yield DropAllRepositoryContentACK()

    response.recover { case t: Exception =>
      throw TriplestoreResponseException("Reset: Failed to execute DROP ALL", t, log)
    }
  }

  /**
   * Inserts the data referenced inside the `rdfDataObjects` by appending it to a default set of `rdfDataObjects`
   * based on the list defined in `application.conf` under the `app.triplestore.default-rdf-data` key.
   *
   * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   * @return [[InsertTriplestoreContentACK]]
   */
  private def insertDataIntoTriplestore(
    rdfDataObjects: Seq[RdfDataObject],
    prependDefaults: Boolean = true
  ): Try[InsertTriplestoreContentACK] = {
    val httpContext: HttpClientContext = makeHttpContext

    try {
      log.debug("==>> Loading Data Start")

      val defaultRdfDataList = settings.tripleStoreConfig.getConfigList("default-rdf-data")
      val defaultRdfDataObjectList = defaultRdfDataList.asScala.map { config =>
        RdfDataObjectFactory(config)
      }

      val completeRdfDataObjectList = if (prependDefaults) {
        //prepend default data objects like those of knora-base, knora-admin, etc.
        defaultRdfDataObjectList ++ rdfDataObjects
      } else {
        rdfDataObjects
      }

      log.debug("insertDataIntoTriplestore - completeRdfDataObjectList: {}", completeRdfDataObjectList)

      // Iterate over the list of graphs and try inserting each one.
      for (elem <- completeRdfDataObjectList) {
        val graphName: String = elem.name

        if (graphName.toLowerCase == "default") {
          throw TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported")
        }

        val uriBuilder: URIBuilder = new URIBuilder(dataInsertPath)
        uriBuilder.addParameter("graph", graphName) //Note: addParameter encodes the graphName URL

        val httpPost: HttpPost = new HttpPost(uriBuilder.build())

        // Add the input file to the body of the request.
        val inputFile = Paths.get(elem.path)

        if (!Files.exists(inputFile)) {
          throw BadRequestException(s"File ${inputFile.toAbsolutePath} does not exist")
        }

        val fileEntity = new FileEntity(inputFile.toFile, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
        httpPost.setEntity(fileEntity)
        val makeResponse: CloseableHttpResponse => InsertGraphDataContentResponse = returnInsertGraphDataResponse(
          graphName
        )

        // Do the post request for the graph.
        doHttpRequest(
          client = longRequestClient,
          request = httpPost,
          context = httpContext,
          processResponse = makeResponse
        )

        log.debug(s"added: $graphName")
      }

      if (triplestoreType == TriplestoreTypes.HttpGraphDBSE || triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
        updateLuceneIndex()
      }

      log.debug("==>> Loading Data End")

      // Return success if all graphs are inserted successfully.
      Success(InsertTriplestoreContentACK())
    } catch {
      case e: TriplestoreUnsupportedFeatureException => Failure(e)
      case e: Exception =>
        Failure(TriplestoreResponseException("Reset: Failed to execute insert into triplestore", e, log))
    }
  }

  /**
   * Checks connection to the triplestore.
   */
  private def checkTriplestore(): Try[CheckTriplestoreResponse] =
    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      checkGraphDBTriplestore()
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      checkFusekiTriplestore()
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

  /**
   * Checks the Fuseki triplestore if it is available and configured correctly. If the it is not
   * configured, tries to automatically configure (initialize) the required dataset.
   */
  private def checkFusekiTriplestore(afterAutoInit: Boolean = false): Try[CheckTriplestoreResponse] = {
    import org.knora.webapi.messages.store.triplestoremessages.FusekiJsonProtocol._

    try {
      log.debug("checkFusekiRepository entered")

      // Call an endpoint that returns all datasets.

      val context: HttpClientContext = makeHttpContext

      val httpGet = new HttpGet(checkRepositoryPath)
      httpGet.addHeader("Accept", mimeTypeApplicationJson)

      val responseStr: String = {
        var maybeResponse: Option[CloseableHttpResponse] = None

        val responseTry: Try[String] = Try {
          maybeResponse = Some(queryHttpClient.execute(targetHost, httpGet, context))
          EntityUtils.toString(maybeResponse.get.getEntity, StandardCharsets.UTF_8)
        }

        maybeResponse.foreach(_.close())
        responseTry.get
      }

      val nameShouldBe               = settings.triplestoreDatabaseName
      val fusekiServer: FusekiServer = JsonParser(responseStr).convertTo[FusekiServer]
      val neededDataset: Option[FusekiDataset] =
        fusekiServer.datasets.find(dataset => dataset.dsName == s"/$nameShouldBe" && dataset.dsState)

      if (neededDataset.nonEmpty) {
        // everything looks good
        Success(
          CheckTriplestoreResponse(
            triplestoreStatus = TriplestoreStatus.ServiceAvailable,
            msg = "Triplestore is available."
          )
        )
      } else {
        // none of the available datasets meet our requirements
        log.info(s"None of the active datasets meet our requirement of name: $nameShouldBe")
        if (settings.triplestoreAutoInit) {
          // try to auto-init if we didn't tried it already
          if (afterAutoInit) {
            // we already tried to auto-init but it wasn't successful
            Success(
              CheckTriplestoreResponse(
                triplestoreStatus = TriplestoreStatus.NotInitialized,
                msg =
                  s"Sorry, we tried to auto-initialize and still none of the active datasets meet our requirement of name: $nameShouldBe"
              )
            )
          } else {
            // try to auto-init
            log.info("Triplestore auto-init is set. Trying to auto-initialize.")
            initJenaFusekiTriplestore()
          }
        } else {
          Success(
            CheckTriplestoreResponse(
              triplestoreStatus = TriplestoreStatus.NotInitialized,
              msg = s"None of the active datasets meet our requirement of name: $nameShouldBe"
            )
          )
        }
      }
    } catch {
      case e: Exception =>
        // println("checkRepository - exception", e)
        Success(
          CheckTriplestoreResponse(
            triplestoreStatus = TriplestoreStatus.ServiceUnavailable,
            msg = s"Triplestore not available: ${e.getMessage}"
          )
        )
    }
  }

  /**
   * Initialize the Jena Fuseki triplestore. Currently only works for
   * 'knora-test' and 'knora-test-unit' repository names. To be used, the
   * API needs to be started with 'KNORA_WEBAPI_TRIPLESTORE_AUTOINIT' set
   * to 'true' (settings.triplestoreAutoInit). This is set to `true` for tests
   * (`test/resources/test.conf`).Usage is only recommended for automated
   * testing and not for production use.
   */
  private def initJenaFusekiTriplestore(): Try[CheckTriplestoreResponse] = {

    val configFileName = s"webapi/scripts/fuseki-repository-config.ttl.template"

    val triplestoreConfig: String =
      try {
        // take config from the classpath and write to triplestore
        FileUtil.readTextResource(configFileName).replace("@REPOSITORY@", settings.triplestoreDatabaseName)
      } catch {
        case _: NotFoundException =>
          log.error(s"Cannot initialize repository. Config $configFileName not found.")
          ""
      }

    val httpContext: HttpClientContext = makeHttpContext
    val httpPost: HttpPost             = new HttpPost("/$/datasets")
    val stringEntity                   = new StringEntity(triplestoreConfig, ContentType.create(mimeTypeTextTurtle))
    httpPost.setEntity(stringEntity)

    doHttpRequest(
      client = updateHttpClient,
      request = httpPost,
      context = httpContext,
      processResponse = returnUploadResponse
    )

    // do the check again
    checkFusekiTriplestore(true)
  }

  /**
   * Checks the connection to a GraphDB triplestore.
   */
  private def checkGraphDBTriplestore(): Try[CheckTriplestoreResponse] = {
    // needs to be a local import or other things don't work (spray json black magic)
    import org.knora.webapi.messages.store.triplestoremessages.GraphDBJsonProtocol._

    try {
      log.debug("checkGraphDBRepository entered")

      // call endpoint returning all repositories

      val context: HttpClientContext = makeHttpContext

      val httpGet = new HttpGet(checkRepositoryPath)
      httpGet.addHeader("Accept", mimeTypeApplicationJson)

      val responseStr: String = {
        var maybeResponse: Option[CloseableHttpResponse] = None

        val responseTry: Try[String] = Try {
          maybeResponse = Some(queryHttpClient.execute(targetHost, httpGet, context))
          EntityUtils.toString(maybeResponse.get.getEntity, StandardCharsets.UTF_8)
        }

        maybeResponse.foreach(_.close())
        responseTry.get
      }

      val jsonArr: JsArray = JsonParser(responseStr).asInstanceOf[JsArray]

      // parse json and check if the repository defined in 'application.conf' is present and correctly defined

      val repositories: Seq[GraphDBRepository] = jsonArr.elements.map(_.convertTo[GraphDBRepository])

      val idShouldBe: String        = settings.triplestoreDatabaseName
      val sesameTypeForSEShouldBe   = "owlim:MonitorRepository"
      val sesameTypeForFreeShouldBe = "graphdb:FreeSailRepository"

      val neededRepo: Option[GraphDBRepository] = repositories.find(repo =>
        repo.id == idShouldBe && (repo.sesameType == sesameTypeForSEShouldBe || repo.sesameType == sesameTypeForFreeShouldBe)
      )

      if (neededRepo.nonEmpty) {
        // everything looks good
        Success(
          CheckTriplestoreResponse(
            triplestoreStatus = TriplestoreStatus.ServiceAvailable,
            msg = "Triplestore is available."
          )
        )
      } else {
        // none of the available repositories meet our requirements
        Success(
          CheckTriplestoreResponse(
            triplestoreStatus = TriplestoreStatus.NotInitialized,
            msg =
              s"None of the available repositories meet our requirements of id: $idShouldBe, sesameType: $sesameTypeForSEShouldBe or $sesameTypeForFreeShouldBe."
          )
        )
      }
    } catch {
      case e: Exception =>
        // println("checkRepository - exception", e)
        Success(
          CheckTriplestoreResponse(
            triplestoreStatus = TriplestoreStatus.ServiceUnavailable,
            msg = s"Triplestore not available: ${e.getMessage}"
          )
        )
    }
  }

  /**
   * Makes a triplestore URI for downloading a named graph.
   *
   * @param graphIri the IRI of the named graph.
   * @return a triplestore-specific URI for downloading the named graph.
   */
  private def makeNamedGraphDownloadUri(graphIri: IRI): URI = {
    val uriBuilder: URIBuilder = new URIBuilder(graphPath)

    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      uriBuilder.setParameter("infer", "false").setParameter("context", s"<$graphIri>")
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      uriBuilder.setParameter("graph", s"$graphIri")
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

    uriBuilder.build()
  }

  /**
   * Requests the contents of a named graph, saving the response in a file.
   *
   * @param graphIri             the IRI of the named graph.
   * @param outputFile           the file to be written.
   * @param outputFormat         the output file format.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  private def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat,
    featureFactoryConfig: FeatureFactoryConfig
  ): Try[FileWrittenResponse] = {
    val httpContext: HttpClientContext = makeHttpContext
    val httpGet                        = new HttpGet(makeNamedGraphDownloadUri(graphIri))
    httpGet.addHeader("Accept", mimeTypeTextTurtle)

    val makeResponse: CloseableHttpResponse => FileWrittenResponse = writeResponseFile(
      outputFile = outputFile,
      featureFactoryConfig = featureFactoryConfig,
      maybeGraphIriAndFormat = Some(GraphIriAndFormat(graphIri = graphIri, quadFormat = outputFormat))
    )

    doHttpRequest(
      client = queryHttpClient,
      request = httpGet,
      context = httpContext,
      processResponse = makeResponse
    )
  }

  /**
   * Requests the contents of a named graph, returning the response as Turtle.
   *
   * @param graphIri the IRI of the named graph.
   * @return a string containing the contents of the graph in Turtle format.
   */
  private def sparqlHttpGraphData(graphIri: IRI): Try[NamedGraphDataResponse] = {
    val httpContext: HttpClientContext = makeHttpContext
    val httpGet                        = new HttpGet(makeNamedGraphDownloadUri(graphIri))
    httpGet.addHeader("Accept", mimeTypeTextTurtle)
    val makeResponse: CloseableHttpResponse => NamedGraphDataResponse = returnGraphDataAsTurtle(graphIri)

    doHttpRequest(
      client = queryHttpClient,
      request = httpGet,
      context = httpContext,
      processResponse = makeResponse
    )
  }

  /**
   * Submits a SPARQL request to the triplestore and returns the response as a string.
   *
   * @param sparql         the SPARQL request to be submitted.
   * @param isUpdate       `true` if this is an update request.
   * @param acceptMimeType the MIME type to be provided in the HTTP Accept header.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @return the triplestore's response.
   */
  private def getSparqlHttpResponse(
    sparql: String,
    isUpdate: Boolean,
    acceptMimeType: String = mimeTypeApplicationSparqlResultsJson,
    simulateTimeout: Boolean = false
  ): Try[String] = {

    val httpContext: HttpClientContext = makeHttpContext

    val (httpClient: CloseableHttpClient, httpPost: HttpPost) = if (isUpdate) {
      // Send updates as application/sparql-update (as per SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly").
      val requestEntity  = new StringEntity(sparql, ContentType.create(mimeTypeApplicationSparqlUpdate, "UTF-8"))
      val updateHttpPost = new HttpPost(sparqlUpdatePath)
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
      val requestEntity: UrlEncodedFormEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
      val queryHttpPost: HttpPost             = new HttpPost(queryPath)
      queryHttpPost.setEntity(requestEntity)
      queryHttpPost.addHeader("Accept", acceptMimeType)
      (queryHttpClient, queryHttpPost)
    }

    doHttpRequest(
      client = httpClient,
      request = httpPost,
      context = httpContext,
      processResponse = returnResponseAsString,
      simulateTimeout = simulateTimeout
    )
  }

  /**
   * Dumps the whole repository in N-Quads format, saving the response in a file.
   *
   * @param outputFile           the output file.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  private def downloadRepository(
    outputFile: Path,
    featureFactoryConfig: FeatureFactoryConfig
  ): Try[FileWrittenResponse] = {
    val httpContext: HttpClientContext = makeHttpContext

    val uriBuilder: URIBuilder = new URIBuilder(repositoryDownloadPath)

    if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
      uriBuilder.setParameter("infer", "false")
    } else if (triplestoreType == TriplestoreTypes.HttpFuseki) {
      // do nothing
    } else {
      throw UnsupportedTriplestoreException(s"Unsupported triplestore type: $triplestoreType")
    }

    val httpGet = new HttpGet(uriBuilder.build())
    httpGet.addHeader("Accept", mimeTypeApplicationNQuads)
    val queryTimeoutMillis = settings.triplestoreQueryTimeout.toMillis.toInt * 10

    val queryRequestConfig = RequestConfig
      .custom()
      .setConnectTimeout(queryTimeoutMillis)
      .setConnectionRequestTimeout(queryTimeoutMillis)
      .setSocketTimeout(queryTimeoutMillis)
      .build

    val queryHttpClient: CloseableHttpClient = HttpClients.custom
      .setDefaultCredentialsProvider(credsProvider)
      .setDefaultRequestConfig(queryRequestConfig)
      .build

    val makeResponse: CloseableHttpResponse => FileWrittenResponse = writeResponseFile(
      outputFile = outputFile,
      featureFactoryConfig = featureFactoryConfig
    )

    doHttpRequest(
      client = queryHttpClient,
      request = httpGet,
      context = httpContext,
      processResponse = makeResponse
    )

  }

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  private def uploadRepository(inputFile: Path): Try[RepositoryUploadedResponse] = {
    val httpContext: HttpClientContext = makeHttpContext
    val httpPost: HttpPost             = new HttpPost(repositoryUploadPath)
    val fileEntity                     = new FileEntity(inputFile.toFile, ContentType.create(mimeTypeApplicationNQuads, "UTF-8"))
    httpPost.setEntity(fileEntity)

    doHttpRequest(
      client = longRequestClient,
      request = httpPost,
      context = httpContext,
      processResponse = returnUploadResponse
    )
  }

  /**
   * Puts a data graph into the repository.
   *
   * @param graphContent a data graph in Turtle format to be inserted into the repository.
   * @param graphName    the name of the graph.
   */
  private def insertDataGraphRequest(graphContent: String, graphName: String): Try[InsertGraphDataContentResponse] = {
    val httpContext: HttpClientContext = makeHttpContext
    val uriBuilder: URIBuilder         = new URIBuilder(dataInsertPath)
    uriBuilder.addParameter("graph", graphName)
    val httpPut: HttpPut = new HttpPut(uriBuilder.build())
    val requestEntity    = new StringEntity(graphContent, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
    httpPut.setEntity(requestEntity)
    val makeResponse: CloseableHttpResponse => InsertGraphDataContentResponse = returnInsertGraphDataResponse(graphName)

    doHttpRequest(
      client = updateHttpClient,
      request = httpPut,
      context = httpContext,
      processResponse = makeResponse
    )
  }

  /**
   * Formulate HTTP context.
   *
   * @return httpContext with credentials and authorization
   */
  private def makeHttpContext: HttpClientContext = {
    val authCache: AuthCache   = new BasicAuthCache
    val basicAuth: BasicScheme = new BasicScheme
    authCache.put(targetHost, basicAuth)

    val httpContext: HttpClientContext = HttpClientContext.create
    httpContext.setCredentialsProvider(credsProvider)
    httpContext.setAuthCache(authCache)
    httpContext
  }

  /**
   * Makes an HTTP connection to the triplestore, and delegates processing of the response
   * to a function.
   *
   * @param client          the HTTP client to be used for the request.
   * @param request         the request to be sent.
   * @param context         the request context to be used.
   * @param processResponse a function that processes the HTTP response.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @tparam T the return type of `processResponse`.
   * @return the return value of `processResponse`.
   */
  private def doHttpRequest[T](
    client: CloseableHttpClient,
    request: HttpRequest,
    context: HttpClientContext,
    processResponse: CloseableHttpResponse => T,
    simulateTimeout: Boolean = false
  ): Try[T] = {
    // Make an Option wrapper for the response, so we can close it if we get one,
    // even if an error occurs.
    var maybeResponse: Option[CloseableHttpResponse] = None

    val triplestoreResponseTry = Try {
      if (simulateTimeout) {
        throw new java.net.SocketTimeoutException("Simulated read timeout")
      }

      val start    = System.currentTimeMillis()
      val response = client.execute(targetHost, request, context)
      maybeResponse = Some(response)
      val statusCode: Int = response.getStatusLine.getStatusCode

      if (statusCode == 404) {
        throw NotFoundException("The requested data was not found")
      } else {
        val statusCategory: Int = statusCode / 100

        if (statusCategory != 2) {
          Option(response.getEntity)
            .map(responseEntity => EntityUtils.toString(responseEntity, StandardCharsets.UTF_8)) match {
            case Some(responseEntityStr) =>
              log.error(s"Triplestore responded with HTTP code $statusCode: $responseEntityStr")
              throw TriplestoreResponseException(
                s"Triplestore responded with HTTP code $statusCode: $responseEntityStr"
              )

            case None =>
              log.error(s"Triplestore responded with HTTP code $statusCode")
              throw TriplestoreResponseException(s"Triplestore responded with HTTP code $statusCode")
          }
        }
      }

      val took = System.currentTimeMillis() - start
      metricsLogger.info(s"[$statusCode] Triplestore query took: ${took}ms")
      processResponse(response)
    }

    maybeResponse.foreach(_.close)

    // TODO: Can we make Fuseki abandon the query if it takes too long?

    triplestoreResponseTry.recover {
      case tre: TriplestoreResponseException => throw tre

      case socketTimeoutException: java.net.SocketTimeoutException =>
        val message =
          "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
        log.error(socketTimeoutException, message)
        throw TriplestoreTimeoutException(message = message, e = socketTimeoutException, log = log)

      case notFound: NotFoundException => throw notFound

      case e: Exception =>
        val message = "Failed to connect to triplestore"
        log.error(e, message)
        throw TriplestoreConnectionException(message = message, e = e, log = log)
    }
  }

  def returnResponseAsString(response: CloseableHttpResponse): String =
    Option(response.getEntity) match {
      case None => ""

      case Some(responseEntity) =>
        EntityUtils.toString(responseEntity, StandardCharsets.UTF_8)
    }

  def returnGraphDataAsTurtle(graphIri: IRI)(response: CloseableHttpResponse): NamedGraphDataResponse =
    Option(response.getEntity) match {
      case None =>
        log.error(s"Triplestore returned no content for graph $graphIri")
        throw TriplestoreResponseException(s"Triplestore returned no content for graph $graphIri")

      case Some(responseEntity: HttpEntity) =>
        NamedGraphDataResponse(
          turtle = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8)
        )
    }

  def returnUploadResponse: CloseableHttpResponse => RepositoryUploadedResponse = { _ =>
    RepositoryUploadedResponse()
  }

  def returnInsertGraphDataResponse(
    graphName: String
  )(response: CloseableHttpResponse): InsertGraphDataContentResponse =
    Option(response.getEntity) match {
      case None =>
        log.error(s"$graphName could not be inserted into Triplestore.")
        throw TriplestoreResponseException(s"$graphName could not be inserted into Triplestore.")

      case Some(_) =>
        InsertGraphDataContentResponse()
    }

  /**
   * Represents a named graph IRI and the file format that the graph should be written in.
   *
   * @param graphIri   the named graph IRI.
   * @param quadFormat the file format.
   */
  case class GraphIriAndFormat(graphIri: IRI, quadFormat: QuadFormat)

  /**
   * Writes an HTTP response to a file.
   *
   * @param outputFile             the output file.
   * @param featureFactoryConfig   the feature factory configuration.
   * @param maybeGraphIriAndFormat a graph IRI and quad format for the output file. If defined, the response
   *                               is parsed as Turtle and converted to the output format, with the graph IRI
   *                               added to each statement. Otherwise, the response is written as-is to the
   *                               output file.
   * @param response               the response to be read.
   * @return a [[FileWrittenResponse]].
   */
  def writeResponseFile(
    outputFile: Path,
    featureFactoryConfig: FeatureFactoryConfig,
    maybeGraphIriAndFormat: Option[GraphIriAndFormat] = None
  )(response: CloseableHttpResponse): FileWrittenResponse =
    Option(response.getEntity) match {
      case Some(responseEntity: HttpEntity) =>
        // Are we converting the response to a quad format?
        maybeGraphIriAndFormat match {
          case Some(GraphIriAndFormat(graphIri, quadFormat)) =>
            // Yes. Stream the HTTP entity to a temporary Turtle file.
            val turtleFile = Paths.get(outputFile.toString + ".ttl")
            Files.copy(responseEntity.getContent, turtleFile, StandardCopyOption.REPLACE_EXISTING)

            // Convert the Turtle to the output format.

            val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)

            val processFileTry: Try[Unit] = Try {
              rdfFormatUtil.turtleToQuadsFile(
                rdfSource = RdfInputStreamSource(new BufferedInputStream(Files.newInputStream(turtleFile))),
                graphIri = graphIri,
                outputFile = outputFile,
                outputFormat = quadFormat
              )
            }

            Files.delete(turtleFile)

            processFileTry match {
              case Success(_)  => ()
              case Failure(ex) => throw ex
            }

          case None =>
            // No. Stream the HTTP entity directly to the output file.
            Files.copy(responseEntity.getContent, outputFile)
        }

        FileWrittenResponse()

      case None =>
        maybeGraphIriAndFormat match {
          case Some(GraphIriAndFormat(graphIri, _)) =>
            log.error(s"Triplestore returned no content for graph $graphIri")
            throw TriplestoreResponseException(s"Triplestore returned no content for graph $graphIri")

          case None =>
            log.error(s"Triplestore returned no content for repository dump")
            throw TriplestoreResponseException(s"Triplestore returned no content for for repository dump")
        }
    }
}
