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


package org.knora.webapi.routing.v1

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.Logger
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.util.standoff.StandoffTagUtilV1.TextWithStandoffTagsV1
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.viewhandlers.ResourceHtmlView
import org.slf4j.LoggerFactory
import spray.json._

import scala.collection.immutable.Iterable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.xml.{Node, NodeSeq, Text}



/**
  * Provides a spray-routing function for API routes that deal with resources.
  */
object ResourcesRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, loggingAdapter: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        val log = Logger(LoggerFactory.getLogger(this.getClass))

        def makeResourceRequestMessage(resIri: String,
                                       resinfo: Boolean,
                                       requestType: String,
                                       userProfile: UserProfileV1): ResourcesResponderRequestV1 = {
            val validResIri = InputValidation.toIri(resIri, () => throw BadRequestException(s"Invalid resource IRI: $resIri"))

            requestType match {
                case "info" => ResourceInfoGetRequestV1(iri = validResIri, userProfile = userProfile)
                case "rights" => ResourceRightsGetRequestV1(validResIri, userProfile)
                case "context" => ResourceContextGetRequestV1(validResIri, userProfile, resinfo)
                case "" => ResourceFullGetRequestV1(validResIri, userProfile)
                case other => throw BadRequestException(s"Invalid request type: $other")
            }
        }

        def makeResourceSearchRequestMessage(searchString: String,
                                             resourceTypeIri: Option[IRI],
                                             numberOfProps: Int, limitOfResults: Int,
                                             userProfile: UserProfileV1): ResourceSearchGetRequestV1 = {
            ResourceSearchGetRequestV1(searchString = searchString, resourceTypeIri = resourceTypeIri, numberOfProps = numberOfProps, limitOfResults = limitOfResults, userProfile = userProfile)
        }


        def valuesToCreate(properties : Map[IRI, Seq[CreateResourceValueV1]],  userProfile: UserProfileV1): Map[IRI, Future[Seq[CreateValueV1WithComment]]] = {
            properties.map {
                case (propIri: IRI, values: Seq[CreateResourceValueV1]) =>
                    (InputValidation.toIri(propIri, () => throw BadRequestException(s"Invalid property IRI $propIri")), values.map {
                        case (givenValue: CreateResourceValueV1) =>

                            givenValue.getValueClassIri match {
                                // create corresponding UpdateValueV1

                                case OntologyConstants.KnoraBase.TextValue =>
                                    val richtext: CreateRichtextV1 = givenValue.richtext_value.get

                                    // check if text has markup
                                    if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
                                        // simple text
                                        Future(CreateValueV1WithComment(TextValueSimpleV1(InputValidation.toSparqlEncodedString(richtext.utf8str.get, () => throw BadRequestException(s"Invalid text: '${richtext.utf8str.get}'"))),
                                            givenValue.comment))
                                    } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
                                        // XML: text with markup

                                        val mappingIri = InputValidation.toIri(richtext.mapping_id.get, () => throw BadRequestException(s"mapping_id ${richtext.mapping_id.get} is invalid"))

                                        for {

                                            textWithStandoffTags: TextWithStandoffTagsV1 <- RouteUtilV1.convertXMLtoStandoffTagV1(
                                                xml = richtext.xml.get,
                                                mappingIri = mappingIri,
                                                userProfile = userProfile,
                                                settings = settings,
                                                responderManager = responderManager,
                                                log = loggingAdapter
                                            )

                                            // collect the resource references from the linking standoff nodes
                                            resourceReferences: Set[IRI] = InputValidation.getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV1)

                                        } yield CreateValueV1WithComment(TextValueWithStandoffV1(
                                            utf8str = InputValidation.toSparqlEncodedString(textWithStandoffTags.text, () => throw InconsistentTriplestoreDataException("utf8str for for TextValue contains invalid characters")),
                                            resource_reference = resourceReferences,
                                            standoff = textWithStandoffTags.standoffTagV1,
                                            mappingIri = textWithStandoffTags.mapping.mappingIri,
                                            mapping = textWithStandoffTags.mapping.mapping
                                        ), givenValue.comment)

                                    }
                                    else {
                                        throw BadRequestException("invalid parameters given for TextValueV1")
                                    }


                                case OntologyConstants.KnoraBase.LinkValue =>
                                    val linkVal = InputValidation.toIri(givenValue.link_value.get, () => throw BadRequestException(s"Invalid Knora resource IRI: $givenValue.link_value.get"))
                                    Future(CreateValueV1WithComment(LinkUpdateV1(linkVal), givenValue.comment))

                                case OntologyConstants.KnoraBase.IntValue =>
                                    Future(CreateValueV1WithComment(IntegerValueV1(givenValue.int_value.get), givenValue.comment))

                                case OntologyConstants.KnoraBase.DecimalValue =>
                                    Future(CreateValueV1WithComment(DecimalValueV1(givenValue.decimal_value.get), givenValue.comment))

                                case OntologyConstants.KnoraBase.BooleanValue =>
                                    Future(CreateValueV1WithComment(BooleanValueV1(givenValue.boolean_value.get), givenValue.comment))

                                case OntologyConstants.KnoraBase.UriValue =>
                                    val uriValue = InputValidation.toIri(givenValue.uri_value.get, () => throw BadRequestException(s"Invalid URI: ${givenValue.uri_value.get}"))
                                    Future(CreateValueV1WithComment(UriValueV1(uriValue), givenValue.comment))

                                case OntologyConstants.KnoraBase.DateValue =>
                                    val dateVal: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(givenValue.date_value.get)
                                    Future(CreateValueV1WithComment(dateVal, givenValue.comment))

                                case OntologyConstants.KnoraBase.ColorValue =>
                                    val colorValue = InputValidation.toColor(givenValue.color_value.get, () => throw BadRequestException(s"Invalid color value: ${givenValue.color_value.get}"))
                                    Future(CreateValueV1WithComment(ColorValueV1(colorValue), givenValue.comment))

                                case OntologyConstants.KnoraBase.GeomValue =>
                                    val geometryValue = InputValidation.toGeometryString(givenValue.geom_value.get, () => throw BadRequestException(s"Invalid geometry value: ${givenValue.geom_value.get}"))
                                    Future(CreateValueV1WithComment(GeomValueV1(geometryValue), givenValue.comment))

                                case OntologyConstants.KnoraBase.ListValue =>
                                    val listNodeIri = InputValidation.toIri(givenValue.hlist_value.get, () => throw BadRequestException(s"Invalid value IRI: ${givenValue.hlist_value.get}"))
                                    Future(CreateValueV1WithComment(HierarchicalListValueV1(listNodeIri), givenValue.comment))

                                case OntologyConstants.KnoraBase.IntervalValue =>
                                    val timeVals: Seq[BigDecimal] = givenValue.interval_value.get

                                    if (timeVals.length != 2) throw BadRequestException("parameters for interval_value invalid")

                                    Future(CreateValueV1WithComment(IntervalValueV1(timeVals(0), timeVals(1)), givenValue.comment))

                                case OntologyConstants.KnoraBase.GeonameValue =>
                                    Future(CreateValueV1WithComment(GeonameValueV1(givenValue.geoname_value.get), givenValue.comment))

                                case _ => throw BadRequestException(s"No value submitted")

                            }

                    })
            }.map { // transform Seq of Futures to a Future of a Seq
                case (propIri: IRI, values: Seq[Future[CreateValueV1WithComment]]) =>
                    (propIri, Future.sequence(values))
            }

        }


        def makeCreateResourceRequestMessage(apiRequest: CreateResourceApiRequestV1, multipartConversionRequest: Option[SipiResponderConversionPathRequestV1] = None, userProfile: UserProfileV1): Future[ResourceCreateRequestV1] = {

            val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI: ${apiRequest.project_id}"))
            val resourceTypeIri = InputValidation.toIri(apiRequest.restype_id, () => throw BadRequestException(s"Invalid resource IRI: ${apiRequest.restype_id}"))
            val label = InputValidation.toSparqlEncodedString(apiRequest.label, () => throw BadRequestException(s"Invalid label: '${apiRequest.label}'"))

            // for GUI-case:
            // file has already been stored by Sipi.
            // TODO: in the old SALSAH, the file params were sent as a property salsah:__location__ -> the GUI has to be adapated
            val paramConversionRequest: Option[SipiResponderConversionFileRequestV1] = apiRequest.file match {
                case Some(createFile: CreateFileV1) => Some(SipiResponderConversionFileRequestV1(
                    originalFilename = InputValidation.toSparqlEncodedString(createFile.originalFilename, () => throw BadRequestException(s"The original filename is invalid: '${createFile.originalFilename}'")),
                    originalMimeType = InputValidation.toSparqlEncodedString(createFile.originalMimeType, () => throw BadRequestException(s"The original MIME type is invalid: '${createFile.originalMimeType}'")),
                    filename = InputValidation.toSparqlEncodedString(createFile.filename, () => throw BadRequestException(s"Invalid filename: '${createFile.filename}'")),
                    userProfile = userProfile
                ))
                case None => None
            }

            val valuesToBeCreatedWithFuture: Map[IRI, Future[Seq[CreateValueV1WithComment]]] = valuesToCreate(apiRequest.properties, userProfile)


            // since this function `makeCreateResourceRequestMessage` is called by the POST multipart route receiving the binaries (non GUI-case)
            // and by the other POST route, either multipartConversionRequest or paramConversionRequest is set if a file should be attached to the resource, but not both.
            if (multipartConversionRequest.nonEmpty && paramConversionRequest.nonEmpty) throw BadRequestException("Binaries sent and file params set to route. This is illegal.")

            for {

                // make the whole Map a Future
                valuesToBeCreated: Iterable[(IRI, Seq[CreateValueV1WithComment])] <- Future.traverse(valuesToBeCreatedWithFuture) {
                    case (propIri: IRI, valuesFuture: Future[Seq[CreateValueV1WithComment]]) =>

                        for {

                            values <- valuesFuture

                        } yield propIri -> values
                }


                // since this function `makeCreateResourceRequestMessage` is called by the POST multipart route receiving the binaries (non GUI-case)
                // and by the other POST route, either multipartConversionRequest or paramConversionRequest is set if a file should be attached to the resource, but not both.
                _ = if (multipartConversionRequest.nonEmpty && paramConversionRequest.nonEmpty) throw BadRequestException("Binaries sent and file params set to route. This is illegal.")

            } yield ResourceCreateRequestV1(
                resourceTypeIri = resourceTypeIri,
                label = label,
                projectIri = projectIri,
                values = valuesToBeCreated.toMap,
                file = if (multipartConversionRequest.nonEmpty) // either multipartConversionRequest or paramConversionRequest might be given, but never both
                    multipartConversionRequest // Non GUI-case
                else if (paramConversionRequest.nonEmpty)
                    paramConversionRequest // GUI-case
                else None, // no file given
                userProfile = userProfile,
                apiRequestID = UUID.randomUUID
            )
        }


        def formOneResourceRequest(resourceRequest: CreateResourceRequestV1, userProfile: UserProfileV1): Future[OneOfMultipleResourceCreateRequestV1] = {
            val values= valuesToCreate(resourceRequest.properties, userProfile)
            // make the whole Map a Future

            for {
                valuesToBeCreated <- Future.traverse(values) {
                    case (propIri: IRI, valuesFuture: Future[Seq[CreateValueV1WithComment]]) =>

                        for {

                            values <- valuesFuture

                        } yield propIri -> values
                }


            } yield OneOfMultipleResourceCreateRequestV1(resourceRequest.restype_id, resourceRequest.label, valuesToBeCreated.toMap)
        }

        def makeMultiResourcesRequestMessage(resourceRequest: Seq[CreateResourceRequestV1], projectId: IRI,  apiRequestID: UUID, userProfile: UserProfileV1): Future[MultipleResourceCreateRequestV1]= {
            val resourcesToCreate : Seq[Future[OneOfMultipleResourceCreateRequestV1]] =
                resourceRequest.map(x => formOneResourceRequest(x, userProfile))
            for {
                    resToCreateCollection: Seq[OneOfMultipleResourceCreateRequestV1] <- Future.sequence(resourcesToCreate)

                } yield  MultipleResourceCreateRequestV1 (resToCreateCollection, projectId, userProfile, apiRequestID)
        }

        def makeGetPropertiesRequestMessage(resIri: IRI, userProfile: UserProfileV1) = {
            PropertiesGetRequestV1(resIri, userProfile)
        }

        def makeResourceDeleteMessage(resIri: IRI, deleteComment: Option[String], userProfile: UserProfileV1) = {
            ResourceDeleteRequestV1(
                resourceIri = InputValidation.toIri(resIri, () => throw BadRequestException(s"Invalid resource IRI: $resIri")),
                deleteComment = deleteComment.map(comment => InputValidation.toSparqlEncodedString(comment, () => throw BadRequestException(s"Invalid comment: '$comment'"))),
                userProfile = userProfile,
                apiRequestID = UUID.randomUUID
            )
        }

        /**
          * parses the xml and for each xml element creates a resource request
          * @param xml : a simple xml
          * @return Seq[CreateResourceRequestV1] collection of resource creation requests
          */
        def parseXml(xml:NodeSeq):Seq[CreateResourceRequestV1]={

            xml.head.child
              .filter(node => node.label != "#PCDATA")
              .map( node => {
                  val entityType = node.label
                  // the id attribute of the xml element is the resource label
                  val resLabel = (node \"@id").toString
                  // namespaces of xml
                  val elemNS = node.getNamespace(node.prefix)
                  //element namespace + # + element tag gives the resource class Id
                  val restype_id = elemNS + "#" + entityType
                  //traversing the subelements to collect the values of resource
                  val properties :Seq[(IRI, Seq[CreateResourceValueV1])] = node.child
                    .filter(child => child.label != "#PCDATA")
                    .map {
                        case (child) =>
                            val subnodes = scala.xml.Utility.trim(child).descendant

                            if (child.descendant.size != 1) {

                                (child.getNamespace(child.prefix) + "#" + child.label ->
                                  subnodes.map {
                                      case (subnode) =>
                                          //xml elements with ref attribute are links
                                          val ref_att = subnode.attribute("ref").get
                                          if (ref_att!=None) {
                                              CreateResourceValueV1(None, Some(subnode.getNamespace(subnode.prefix) + "/" + subnode.label+"#"+ ref_att))
                                          } else {
                                              CreateResourceValueV1(Some(CreateRichtextV1(Some(subnode.text))))
                                          }
                                  }
                                  )

                            } else {

                                if (child.label.contains("Date") || child.label.contains("date") ) {
                                    println(child.label, child.text)
                                    (child.getNamespace(child.prefix) + "#" + child.label ->
                                    List(CreateResourceValueV1(date_value=Some(child.text))))
                                } else {
                                    (child.getNamespace(child.prefix) + "#" + child.label ->
                                     List(CreateResourceValueV1(Some(CreateRichtextV1(Some(child.text))))))
                                }

                            }

                    }
                  CreateResourceRequestV1(restype_id , resLabel, properties.toMap)
              })
        }

        path("v1" / "resources") {
            get {
                // search for resources matching the given search string (searchstr) and return their Iris.
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val searchstr = params.getOrElse("searchstr", throw BadRequestException(s"required param searchstr is missing"))
                    val restype = params.getOrElse("restype_id", "-1")
                    // default -1 means: no restriction at all
                    val numprops = params.getOrElse("numprops", "1")
                    val limit = params.getOrElse("limit", "11")

                    // input validation

                    val searchString = InputValidation.toSparqlEncodedString(searchstr, () => throw BadRequestException(s"Invalid search string: '$searchstr'"))

                    val resourceTypeIri: Option[IRI] = restype match {
                        case ("-1") => None
                        case (restype: IRI) => Some(InputValidation.toIri(restype, () => throw BadRequestException(s"Invalid param restype: $restype")))
                    }

                    val numberOfProps: Int = InputValidation.toInt(numprops, () => throw BadRequestException(s"Invalid param numprops: $numprops")) match {
                        case (number: Int) => if (number < 1) 1 else number // numberOfProps must not be smaller than 1
                    }

                    val limitOfResults = InputValidation.toInt(limit, () => throw BadRequestException(s"Invalid param limit: $limit"))

                    val requestMessage = makeResourceSearchRequestMessage(
                        searchString = searchString,
                        resourceTypeIri = resourceTypeIri,
                        numberOfProps = numberOfProps,
                        limitOfResults = limitOfResults,
                        userProfile = userProfile
                    )

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
            } ~ post {
                // Create a new resource with he given type and possibly a file (GUI-case).
                // The binary file is already managed by Sipi.
                // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
                entity(as[CreateResourceApiRequestV1]) { apiRequest =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val requestMessageFuture = makeCreateResourceRequestMessage(apiRequest = apiRequest, userProfile = userProfile)

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            } ~ post {
                // Create a new resource with the given type, properties, and binary data (file) (non GUI-case).
                // The binary data are contained in the request and have to be temporarily stored by Knora.
                // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
                entity(as[Multipart.FormData]) { formdata: Multipart.FormData =>
                    requestContext =>

                        log.debug("/v1/resources - POST - Multipart.FormData - Route")

                        val userProfile = getUserProfileV1(requestContext)

                        type Name = String

                        val JSON_PART = "json"
                        val FILE_PART = "file"

                        /* TODO: refactor to remove the need for this var */
                        /* makes sure that variables updated in another thread return the latest version */
                        @volatile var receivedFile: Option[File] = None

                        log.debug(s"receivedFile is defined before: ${receivedFile.isDefined}")

                        // collect all parts of the multipart as it arrives into a map
                        val allPartsFuture: Future[Map[Name, Any]] = formdata.parts.mapAsync[(Name, Any)](1) {
                            case b: BodyPart if b.name == JSON_PART => {
                                log.debug(s"inside allPartsFuture - processing $JSON_PART")
                                b.toStrict(2.seconds).map(strict => (b.name, strict.entity.data.utf8String.parseJson))
                            }
                            case b: BodyPart if b.name == FILE_PART => {
                                log.debug(s"inside allPartsFuture - processing $FILE_PART")
                                val filename = b.filename.getOrElse(throw BadRequestException(s"Filename is not given"))
                                val tmpFile = InputValidation.createTempFile(settings)
                                val written = b.entity.dataBytes.runWith(FileIO.toPath(tmpFile.toPath))
                                written.map { written =>
                                    //println(s"written result: ${written.wasSuccessful}, ${b.filename.get}, ${tmpFile.getAbsolutePath}")
                                    receivedFile = Some(tmpFile)
                                    (b.name, FileInfo(b.name, b.filename.get, b.entity.contentType))
                                }
                            }
                            case b: BodyPart if b.name.isEmpty => throw BadRequestException("part of HTTP multipart request has no name")
                            case b: BodyPart => throw BadRequestException(s"multipart contains invalid name: ${b.name}")
                        }.runFold(Map.empty[Name, Any])((map, tuple) => map + tuple)

                        // this file will be deleted by Knora once it is not needed anymore
                        // TODO: add a script that cleans files in the tmp location that have a certain age
                        // TODO  (in case they were not deleted by Knora which should not happen -> this has also to be implemented for Sipi for the thumbnails)
                        // TODO: how to check if the user has sent multiple files?

                        val requestMessageFuture: Future[ResourceCreateRequestV1] = allPartsFuture.flatMap { allParts => // use flatMap to get rid of nested Futures

                            log.debug(s"allParts: $allParts")
                            log.debug(s"receivedFile is defined: ${receivedFile.isDefined}")

                            // get the json params and turn them into a case class
                            val apiRequest: CreateResourceApiRequestV1 = try {
                                allParts.getOrElse(JSON_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")).asInstanceOf[JsValue].convertTo[CreateResourceApiRequestV1]
                            } catch {
                                case e: DeserializationException => throw BadRequestException("JSON params structure is invalid: " + e.toString)
                            }

                            // check if the API request contains file information: this is illegal for this route
                            if (apiRequest.file.nonEmpty) throw BadRequestException("param 'file' is set for a post multipart request. This is not allowed.")

                            val sourcePath = receivedFile.getOrElse(throw FileUploadException())

                            // get the file info containing the original filename and content type.
                            val fileInfo = allParts.getOrElse(FILE_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$FILE_PART' part!")).asInstanceOf[FileInfo]
                            val originalFilename = fileInfo.fileName
                            val originalMimeType = fileInfo.contentType.toString


                            val sipiConvertPathRequest = SipiResponderConversionPathRequestV1(
                                originalFilename = InputValidation.toSparqlEncodedString(originalFilename, () => throw BadRequestException(s"Original filename is invalid: '$originalFilename'")),
                                originalMimeType = InputValidation.toSparqlEncodedString(originalMimeType, () => throw BadRequestException(s"Original MIME type is invalid: '$originalMimeType'")),
                                source = sourcePath,
                                userProfile = userProfile
                            )

                            val requestMessageFuture: Future[ResourceCreateRequestV1] = makeCreateResourceRequestMessage(
                                apiRequest = apiRequest,
                                multipartConversionRequest = Some(sipiConvertPathRequest),
                                userProfile = userProfile
                            )
                            requestMessageFuture
                        }


                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "resources" / Segment) { resIri =>
            get {
                parameters("reqtype".?, "resinfo".as[Boolean].?) { (reqtypeParam, resinfoParam) =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val params = parameterMap
                        val requestType = reqtypeParam.getOrElse("")
                        val resinfo = resinfoParam.getOrElse(false)
                        val requestMessage = makeResourceRequestMessage(resIri = resIri, resinfo = resinfo, requestType = requestType, userProfile = userProfile)

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            } ~ delete {
                parameters("deleteComment".?) { deleteCommentParam =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val requestMessage = makeResourceDeleteMessage(resIri = resIri, deleteComment = deleteCommentParam, userProfile = userProfile)

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "resources.html" / Segment) { iri =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val requestType = params.getOrElse("reqtype", "")
                    val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))

                    val requestMessage = requestType match {
                        case "properties" => ResourceFullGetRequestV1(resIri, userProfile)
                        case other => throw BadRequestException(s"Invalid request type: $other")
                    }

                    RouteUtilV1.runHtmlRoute[ResourcesResponderRequestV1, ResourceFullResponseV1](
                        requestMessage,
                        ResourceHtmlView.propertiesHtmlView,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
            }
        } ~ path("v1" / "properties" / Segment) { iri =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))
                    val requestMessage = makeGetPropertiesRequestMessage(resIri, userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )

            }
        } ~ path("v1" / "resources" / "label" / Segment) { iri =>
            put {
                entity(as[ChangeResourceLabelApiRequestV1]) { apiRequest =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)

                        val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))

                        val label = InputValidation.toSparqlEncodedString(apiRequest.label, () => throw BadRequestException(s"Invalid label: '${apiRequest.label}'"))

                        val requestMessage = ChangeResourceLabelRequestV1(
                            resourceIri = resIri,
                            label = label,
                            apiRequestID = UUID.randomUUID,
                            userProfile = userProfile)


                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "graphdata" / Segment) { iri =>
            get {
                parameters("depth".as[Int].?) { depth =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val resourceIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))
                        val requestMessage = GraphDataGetRequestV1(resourceIri, depth.getOrElse(4), userProfile)

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }

        } ~ path("v1" / "error" / Segment) { errorType =>
            get {
                requestContext =>

                    val msg = if (errorType == "unitMsg") {
                        UnexpectedMessageRequest()
                    } else if (errorType == "iseMsg") {
                        InternalServerExceptionMessageRequest()
                    } else {
                        InternalServerExceptionMessageRequest()
                    }

                    RouteUtilV1.runJsonRoute(
                        msg,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
            }

        } ~  path("v1" / "resources" / "xml" ) {
            post {
                entity(as[NodeSeq]) {
                    xml => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    val projectId = "http://data.knora.org/projects/DczxPs-sR6aZN91qV92ZmQ"

                    val apiRequestID = UUID.randomUUID
                    val resourcesToCreate = parseXml(xml)
                    val request1 = makeMultiResourcesRequestMessage(resourcesToCreate, projectId, apiRequestID, userProfile)

                    RouteUtilV1.runJsonRouteWithFuture(
                        request1,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }

            }
        }

    }
}