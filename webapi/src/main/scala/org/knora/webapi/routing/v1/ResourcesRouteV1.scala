/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import java.io._
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory}

import akka.pattern._
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.Logger
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.standoff.StandoffTagUtilV1.TextWithStandoffTagsV1
import org.knora.webapi.util.{DateUtilV1, FileUtil, InputValidation}
import org.knora.webapi.util.InputValidation.XmlImportNamespaceInfoV1
import org.knora.webapi.viewhandlers.ResourceHtmlView
import org.slf4j.LoggerFactory
import org.w3c.dom.ls.{LSInput, LSResourceResolver}
import spray.json._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, Utility, XML, PrettyPrinter}


/**
  * Provides a spray-routing function for API routes that deal with resources.
  */
object ResourcesRouteV1 extends Authenticator {
    // A scala.xml.PrettyPrinter for formatting generated XML import schemas.
    private val xmlPrettyPrinter = new PrettyPrinter(width = 80, step = 4)

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


        def valuesToCreate(properties: Map[IRI, Seq[CreateResourceValueV1]], userProfile: UserProfileV1): Map[IRI, Future[Seq[CreateValueV1WithComment]]] = {
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
                                    (givenValue.link_value, givenValue.link_to_client_id) match {
                                        case (Some(targetIri: IRI), None) =>
                                            // This is a link to an existing Knora IRI, so make sure the IRI is valid.
                                            val validatedTargetIri = InputValidation.toIri(targetIri, () => throw BadRequestException(s"Invalid Knora resource IRI: $targetIri"))
                                            Future(CreateValueV1WithComment(LinkUpdateV1(validatedTargetIri), givenValue.comment))

                                        case (None, Some(clientIDForTargetResource: String)) =>
                                            // This is a link to the client's ID for a resource that hasn't been created yet.
                                            Future(CreateValueV1WithComment(LinkToClientIDUpdateV1(clientIDForTargetResource), givenValue.comment))

                                        case (_, _) => throw AssertionException(s"Invalid link: $givenValue")
                                    }

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
                                    Future(CreateValueV1WithComment(IntervalValueV1(timeVals.head, timeVals(1)), givenValue.comment))

                                case OntologyConstants.KnoraBase.GeonameValue =>
                                    Future(CreateValueV1WithComment(GeonameValueV1(givenValue.geoname_value.get), givenValue.comment))

                                case _ => throw BadRequestException(s"No value submitted")

                            }

                    })
            }.map {
                // transform Seq of Futures to a Future of a Seq
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

        def createOneResourceRequest(resourceRequest: CreateResourceRequestV1, userProfile: UserProfileV1): Future[OneOfMultipleResourceCreateRequestV1] = {
            val values: Map[IRI, Future[Seq[CreateValueV1WithComment]]] = valuesToCreate(resourceRequest.properties, userProfile)

            // make the whole Map a Future

            for {
                valuesToBeCreated: Iterable[(IRI, Seq[CreateValueV1WithComment])] <- Future.traverse(values) {
                    case (propIri: IRI, valuesFuture: Future[Seq[CreateValueV1WithComment]]) =>
                        for {
                            values <- valuesFuture
                        } yield propIri -> values
                }
            } yield OneOfMultipleResourceCreateRequestV1(
                resourceTypeIri = resourceRequest.restype_id,
                clientResourceID = resourceRequest.client_id,
                label = resourceRequest.label,
                values = valuesToBeCreated.toMap
            )
        }

        def makeMultiResourcesRequestMessage(resourceRequest: Seq[CreateResourceRequestV1], projectId: IRI, apiRequestID: UUID, userProfile: UserProfileV1): Future[MultipleResourceCreateRequestV1] = {
            val resourcesToCreate: Seq[Future[OneOfMultipleResourceCreateRequestV1]] =
                resourceRequest.map(createResourceRequest => createOneResourceRequest(createResourceRequest, userProfile))

            for {
                resToCreateCollection: Seq[OneOfMultipleResourceCreateRequestV1] <- Future.sequence(resourcesToCreate)
            } yield MultipleResourceCreateRequestV1(resToCreateCollection, projectId, userProfile, apiRequestID)
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
          * knoraDataTypeXMl gets the knoraType specified as attribute in xml element
          * validates the specified type
          *
          * @param node the xml element
          * @return
          *
          */
        def knoraDataTypeXML(node: Node): CreateResourceValueV1 = {

            val knoraType: Seq[Node] = node.attribute("knoraType").get
            val element_value = node.text
            if (knoraType.nonEmpty) {
                knoraType.toString match {
                    case "richtext_value" =>
                        val mapping_id: Option[Seq[Node]] = node.attributes.get("mapping_id")

                        if (mapping_id.nonEmpty) {
                            val mapping_IRI: Option[IRI] = Some(InputValidation.toIri(mapping_id.toString, () => throw BadRequestException(s"Invalid mapping ID in element '${node.label}: '$mapping_id")))
                            CreateResourceValueV1(richtext_value = Some(CreateRichtextV1(None, Some(element_value), mapping_IRI)))
                        } else {
                            CreateResourceValueV1(richtext_value = Some(CreateRichtextV1(Some(element_value))))
                        }

                    case "link_value" =>
                        node.attribute("ref").get.headOption match {
                            case Some(refNode: Node) => CreateResourceValueV1(link_to_client_id = Some(refNode.text))
                            case None => throw BadRequestException(s"Attribute 'ref' missing in element '${node.label}'")
                        }

                    case "int_value" =>
                        CreateResourceValueV1(int_value = Some(InputValidation.toInt(element_value, () => throw BadRequestException(s"Invalid integer value in element '${node.label}: '$element_value'"))))

                    case "decimal_value" =>
                        CreateResourceValueV1(decimal_value = Some(InputValidation.toBigDecimal(element_value, () => throw BadRequestException(s"Invalid decimal value in element '${node.label}: '$element_value'"))))

                    case "boolean_value" =>
                        CreateResourceValueV1(boolean_value = Some(InputValidation.toBoolean(element_value, () => throw BadRequestException(s"Invalid boolean value in element '${node.label}: '$element_value'"))))

                    case "uri_value" =>
                        CreateResourceValueV1(uri_value = Some(InputValidation.toIri(element_value, () => throw BadRequestException(s"Invalid URI value in element '${node.label}: '$element_value'"))))

                    case "date_value" =>
                        CreateResourceValueV1(date_value = Some(InputValidation.toDate(element_value, () => throw BadRequestException(s"Invalid date value in element '${node.label}: '$element_value'"))))

                    case "color_value" =>
                        CreateResourceValueV1(color_value = Some(InputValidation.toColor(element_value, () => throw BadRequestException(s"Invalid date value in element '${node.label}: '$element_value'"))))

                    case "geom_value" =>
                        CreateResourceValueV1(geom_value = Some(InputValidation.toGeometryString(element_value, () => throw BadRequestException(s"Invalid geometry value in element '${node.label}: '$element_value'"))))

                    case "hlist_value" =>
                        CreateResourceValueV1(hlist_value = Some(InputValidation.toIri(element_value, () => throw BadRequestException(s"Invalid hlist value in element '${node.label}: '$element_value'"))))

                    case "interval_value" =>
                        Try(element_value.split(",")) match {
                            case Success(timeVals) =>
                                if (timeVals.length != 2) throw BadRequestException(s"Invalid interval value in element '${node.label}: '$element_value'")

                                val tVals: Seq[BigDecimal] = timeVals.map {
                                    timeVal =>
                                        InputValidation.toBigDecimal(timeVal, () => throw BadRequestException(s"Invalid decimal value in element '${node.label}: '$timeVal'"))
                                }

                                CreateResourceValueV1(interval_value = Some(tVals))

                            case Failure(f) =>
                                throw BadRequestException(s"Invalid interval value in element '${node.label}: '$element_value'")
                        }

                    case "geoname_value" =>
                        CreateResourceValueV1(geoname_value = Some(element_value))
                    case other => throw BadRequestException(s"Invalid 'knoraType' in element '${node.label}': '$other'")
                }
            } else {
                throw BadRequestException(s"Attribute 'knoraType' missing in element '${node.label}'")

            }
        }

        /**
          * Represents an XML import schema generated from a project-specific ontology.
          *
          * @param namespaceInfo information about the schema's namespace.
          * @param schemaXml     the XML text of the schema.
          */
        case class XmlImportSchemaV1(namespaceInfo: XmlImportNamespaceInfoV1, schemaXml: String)

        /**
          * Represents a bundle of XML import schemas generated from a set of project-specific ontologies.
          *
          * @param mainNamespace        the XML namespace corresponding to the main ontology to be used in the XML import.
          * @param knoraXmlImportSchema the standard Knora XML import V1 schema.
          * @param generatedSchemas     a map of XML namespaces to generated schemas.
          */
        case class XmlImportSchemaBundleV1(mainNamespace: IRI, knoraXmlImportSchema: XmlImportSchemaV1, generatedSchemas: Map[IRI, XmlImportSchemaV1])

        /**
          * Given the IRI of an internal project-specific ontology, recursively gets a [[NamedGraphEntityInfoV1]] for that ontology
          * and for any other ontologies containing class definitions used as property objects in the initial ontology.
          *
          * @param startOntologyIri     the IRI of the internal project-specific ontology to start with.
          * @param userProfile          the profile of the user making the request.
          * @param namedGraphInfosSoFar intermediate results.
          * @return a map of internal ontology IRIs to [[NamedGraphEntityInfoV1]] objects.
          */
        def getNamedGraphInfos(startOntologyIri: IRI, userProfile: UserProfileV1, namedGraphInfosSoFar: Map[IRI, NamedGraphEntityInfoV1] = Map.empty[IRI, NamedGraphEntityInfoV1]): Future[Map[IRI, NamedGraphEntityInfoV1]] = {
            for {
                startNamedGraphEntityInfo <- (responderManager ? NamedGraphEntityInfoRequestV1(startOntologyIri, userProfile)).mapTo[NamedGraphEntityInfoV1]
                propertyInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(propertyIris = startNamedGraphEntityInfo.propertyIris, userProfile = userProfile)).mapTo[EntityInfoGetResponseV1]

                ontologyIrisFromObjectClassConstraints: Set[IRI] = propertyInfoResponse.propertyEntityInfoMap.map {
                    case (propertyIri, propertyInfo) =>
                        val propertyObjectClassConstraint = propertyInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse {
                            throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
                        }

                        InputValidation.getInternalOntologyIriFromInternalEntityIri(
                            internalEntityIri = propertyObjectClassConstraint,
                            errorFun = () => throw InconsistentTriplestoreDataException(s"Property $propertyIri has an invalid knora-base:objectClassConstraint: $propertyObjectClassConstraint")
                        )
                }.toSet

                namedGraphInfoFutures: Set[Future[Map[IRI, NamedGraphEntityInfoV1]]] = ontologyIrisFromObjectClassConstraints.map {
                    ontologyIri =>
                        if (!namedGraphInfosSoFar.contains(ontologyIri)) {
                            getNamedGraphInfos(
                                startOntologyIri = ontologyIri,
                                userProfile = userProfile,
                                namedGraphInfosSoFar = namedGraphInfosSoFar + (ontologyIri -> startNamedGraphEntityInfo)
                            )
                        } else {
                            Future(Map.empty[IRI, NamedGraphEntityInfoV1])
                        }
                }

                setOfNewNamedGraphInfos: Set[Map[IRI, NamedGraphEntityInfoV1]] <- Future.sequence(namedGraphInfoFutures)
                setOfNamedGraphInfoSoFar: Set[Map[IRI, NamedGraphEntityInfoV1]] = setOfNewNamedGraphInfos + namedGraphInfosSoFar
            } yield setOfNamedGraphInfoSoFar.flatten.toMap
        }

        /**
          * Given the IRI of an internal project-specific ontology, generates an [[XmlImportSchemaBundleV1]] for validating
          * XML imports for that ontology and any other ontologies it depends on.
          *
          * @param internalOntologyIri the IRI of the main internal project-specific ontology to be used in the XML import.
          * @param userProfile         the profile of the user making the request.
          * @return an [[XmlImportSchemaBundleV1]] for validating the import.
          */
        def generateSchemasFromOntology(internalOntologyIri: IRI, userProfile: UserProfileV1): Future[XmlImportSchemaBundleV1] = {
            /**
              * Called by the schema generation template to get the prefix label for an internal ontology
              * entity IRI.
              *
              * @param internalEntityIri an internal ontology entity IRI.
              * @return the prefix label that Knora uses to refer to the ontology.
              */
            def getNamespacePrefix(internalEntityIri: IRI): String = {
                InputValidation.getOntologyPrefixFromInternalEntityIri(
                    internalEntityIri = internalEntityIri,
                    errorFun = () => throw InconsistentTriplestoreDataException(s"Invalid entity IRI: $internalEntityIri")
                )
            }

            /**
              * Called by the schema generation template to get the entity name (i.e. the local name part) of an
              * internal ontology entity IRI.
              *
              * @param internalEntityIri an internal ontology entity IRI.
              * @return the local name of the entity.
              */
            def getEntityName(internalEntityIri: IRI): String = {
                InputValidation.getEntityNameFromInternalEntityIri(
                    internalEntityIri = internalEntityIri,
                    errorFun = () => throw InconsistentTriplestoreDataException(s"Invalid entity IRI: $internalEntityIri")
                )
            }

            for {
            // Get a NamedGraphEntityInfoV1 for each ontology that we need to generate an XML schema for.
                namedGraphInfos: Map[IRI, NamedGraphEntityInfoV1] <- getNamedGraphInfos(startOntologyIri = internalOntologyIri, userProfile = userProfile)

                // Get information about the resource classes and properties in each ontology.
                entityInfoResponseFutures: immutable.Iterable[Future[(IRI, EntityInfoGetResponseV1)]] = namedGraphInfos.map {
                    case (ontologyIri: IRI, namedGraphInfo: NamedGraphEntityInfoV1) =>
                        for {
                            entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                                resourceClassIris = namedGraphInfo.resourceClasses,
                                propertyIris = namedGraphInfo.propertyIris,
                                userProfile = userProfile
                            )).mapTo[EntityInfoGetResponseV1]
                        } yield ontologyIri -> entityInfoResponse
                }

                entityInfoResponses: immutable.Iterable[(IRI, EntityInfoGetResponseV1)] <- Future.sequence(entityInfoResponseFutures)
                entityInfoResponsesMap: Map[IRI, EntityInfoGetResponseV1] = entityInfoResponses.toMap

                // Collect all the property defintions in a single Map, because any schema could use any property.
                propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1] = entityInfoResponsesMap.values.flatMap(_.propertyEntityInfoMap).toMap

                // Make a map of ontology IRIs to XmlImportNamespaceInfoV1 objects describing the XML namespace that
                // will be used for the XML schema corresponding to each ontology.
                ontologyIrisToNamespaceInfos: Map[IRI, XmlImportNamespaceInfoV1] = namedGraphInfos.keySet.map {
                    ontologyIri =>
                        val namespaceInfo = InputValidation.internalOntologyIriToXmlNamespaceInfoV1(
                            internalOntologyIri = internalOntologyIri,
                            errorFun = () => throw BadRequestException(s"Invalid ontology IRI: $internalOntologyIri")
                        )

                        ontologyIri -> namespaceInfo
                }.toMap

                // Construct an XmlImportSchemaV1 for the standard Knora XML import schema.

                knoraXmlImportSchemaNamespaceInfo: XmlImportNamespaceInfoV1 = XmlImportNamespaceInfoV1(
                    namespace = OntologyConstants.KnoraXmlImportV1.KnoraXmlImportV1PrefixExpansion,
                    prefix = "knoraXmlImport"
                )

                knoraXmlImportSchemaXml: String = FileUtil.readTextFile(new File("src/main/resources/knora-xml-import-v1.xsd"))

                knoraXmlImportSchema: XmlImportSchemaV1 = XmlImportSchemaV1(
                    namespaceInfo = knoraXmlImportSchemaNamespaceInfo,
                    schemaXml = knoraXmlImportSchemaXml
                )

                // Generate a schema for each ontology.
                generatedSchemas: Map[IRI, XmlImportSchemaV1] = ontologyIrisToNamespaceInfos.map {
                    case (ontologyIri, namespaceInfo) =>
                        // Each schema imports all the other schemas.
                        val importedNamespaces: Seq[XmlImportNamespaceInfoV1] = (ontologyIrisToNamespaceInfos - ontologyIri).map {
                            case (_, importedNamespaceInfo: XmlImportNamespaceInfoV1) => importedNamespaceInfo
                        }.toVector.sortBy {
                            importedNamespaceInfo => importedNamespaceInfo.prefix
                        }

                        // Generate the schema using a Twirl template.
                        val unformattedSchemaXml = xsd.v1.xml.xmlImport(
                            targetNamespaceInfo = namespaceInfo,
                            importedNamespaces = importedNamespaces,
                            resourceEntityInfoMap = entityInfoResponsesMap(ontologyIri).resourceEntityInfoMap,
                            propertyEntityInfoMap = propertyEntityInfoMap,
                            getNamespacePrefix = internalEntityIri => getNamespacePrefix(internalEntityIri),
                            getEntityName = internalEntityIri => getEntityName(internalEntityIri)
                        ).toString().trim

                        // Parse the generated XML schema.
                        val parsedSchemaXml = XML.loadString(unformattedSchemaXml)

                        // Format the generated XML schema nicely.
                        val formattedSchemaXml = xmlPrettyPrinter.format(parsedSchemaXml)

                        // Wrap it in an XmlImportSchemaV1 object along with its XML namespace information.
                        val schema = XmlImportSchemaV1(
                            namespaceInfo = namespaceInfo,
                            schemaXml = formattedSchemaXml
                        )

                        namespaceInfo.namespace -> schema
                }
            } yield XmlImportSchemaBundleV1(
                mainNamespace = ontologyIrisToNamespaceInfos(internalOntologyIri).namespace,
                knoraXmlImportSchema = knoraXmlImportSchema,
                generatedSchemas = generatedSchemas
            )
        }

        /**
          * Generates a byte array representing a Zip file containing XML schemas for validating XML import data whose
          * internal RDF representation should conform to the specified internal ontology.
          *
          * @param internalOntologyIri the IRI of the internal ontology.
          * @param userProfile         the profile of the user making the request.
          * @return a byte array representing a Zip file containing XML schemas.
          */
        def generateSchemaZipFile(internalOntologyIri: IRI, userProfile: UserProfileV1): Future[Array[Byte]] = {
            for {
                schemaBundle: XmlImportSchemaBundleV1 <- generateSchemasFromOntology(
                    internalOntologyIri = internalOntologyIri,
                    userProfile = userProfile
                )

                zipFileContents: Map[String, Array[Byte]] = schemaBundle.generatedSchemas.values.map {
                    schema: XmlImportSchemaV1 =>
                        val schemaFilename: String = schema.namespaceInfo.prefix + ".xsd"
                        val schemaXmlBytes: Array[Byte] = schema.schemaXml.getBytes(StandardCharsets.UTF_8)
                        schemaFilename -> schemaXmlBytes
                }.toMap + ("knora-xml-import-v1.xsd" -> schemaBundle.knoraXmlImportSchema.schemaXml.getBytes(StandardCharsets.UTF_8))
            } yield FileUtil.createZipFileBytes(contents = zipFileContents)
        }

        /**
          * Validates bulk import XML using project-specific XML schemas and the Knora XML import schema version 1.
          *
          * @param xml the XML to be validated.
          */
        def validateImportXml(xml: String, defaultNamespace: IRI, otherApiNamespaces: Set[IRI]): Unit = {
            // TODO: call generateSchemasFromOntology() and use the resulting schemas.

            // The schema for the project into which we're importing data.
            val mainSchemaFile = new File("_test_data/xsd_test/biblio.xsd")

            // A map of XML namespaces to files containing XML schemas that are imported by the main schema.
            val importedSchemaFiles = Map(
                "http://api.knora.org/ontology/beol/xml-import/v1#" -> new File("_test_data/xsd_test/beol.xsd"),
                "http://api.knora.org/ontology/knora-xml-import/v1#" -> new File("_test_data/xsd_test/knora-xml-import-v1.xsd")
            )

            val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            schemaFactory.setResourceResolver(new FileResourceResolver(importedSchemaFiles))
            val schemaInstance: Schema = schemaFactory.newSchema(new StreamSource(new FileInputStream(mainSchemaFile)))
            val schemaValidator = schemaInstance.newValidator()
            schemaValidator.validate(new StreamSource(new StringReader(xml)))
        }

        /**
          * Converts parsed import XML into a sequence of [[CreateResourceRequestV1]] for each resource
          * described in the XML.
          *
          * @param rootElement the root element of an XML document describing multiple resources to be created.
          * @return Seq[CreateResourceRequestV1] a collection of resource creation requests.
          */
        def importXmlToCreateResourceRequests(rootElement: Elem): Seq[CreateResourceRequestV1] = {
            rootElement.head.child
                .filter(node => node.label != "#PCDATA")
                .map(resourceNode => {
                    // Get the client's ID for the resource
                    val clientIDForResource: String = (resourceNode \ "@id").toString

                    // Get the resource's rdfs:label
                    val resourceLabel: String = (resourceNode \ "@label").toString

                    // Convert the XML element's label and namespace to an internal resource class IRI.

                    val elementNamespace: String = resourceNode.getNamespace(resourceNode.prefix)

                    val restype_id = InputValidation.xmlImportElementNameToInternalOntologyIriV1(
                        namespace = elementNamespace,
                        elementLabel = resourceNode.label,
                        errorFun = () => throw BadRequestException(s"Invalid XML namespace: $elementNamespace")
                    )

                    // Traverse the child elements to collect the values of the resource
                    val propertiesWithValues: Map[IRI, Seq[CreateResourceValueV1]] = resourceNode.child
                        .filter(childNode => childNode.label != "#PCDATA")
                        .map {
                            propertyNode =>
                                // Convert the XML element's label and namespace to an internal property IRI.

                                val propertyNodeNamespace = propertyNode.getNamespace(propertyNode.prefix)

                                val propertyIri = InputValidation.xmlImportElementNameToInternalOntologyIriV1(
                                    namespace = propertyNodeNamespace,
                                    elementLabel = propertyNode.label,
                                    errorFun = () => throw BadRequestException(s"Invalid XML namespace: $propertyNodeNamespace"))

                                val valueNodes: Seq[Node] = scala.xml.Utility.trim(propertyNode).descendant

                                if (propertyNode.descendant.size == 1) {
                                    propertyIri -> List(knoraDataTypeXML(propertyNode))
                                } else {
                                    propertyIri ->
                                        valueNodes.map {
                                            descendant: Node => knoraDataTypeXML(descendant)
                                        }
                                }
                        }.toMap

                    CreateResourceRequestV1(
                        restype_id = restype_id,
                        client_id = clientIDForResource,
                        label = resourceLabel,
                        properties = propertiesWithValues
                    )
                })
        }

        path("v1" / "resources") {
            get {
                // search for resources matching the given search string (searchstr) and return their Iris.
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val searchstr = params.getOrElse("searchstr", throw BadRequestException(s"required param searchstr is missing"))

                    // default -1 means: no restriction at all
                    val restype = params.getOrElse("restype_id", "-1")

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

                        val receivedFile = Promise[File]

                        log.debug(s"receivedFile is completed before: ${receivedFile.isCompleted}")

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
                                    receivedFile.success(tmpFile)
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

                        val requestMessageFuture: Future[ResourceCreateRequestV1] = for {
                            allParts <- allPartsFuture
                            // get the json params and turn them into a case class
                            apiRequest: CreateResourceApiRequestV1 = try {
                                allParts.getOrElse(JSON_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")).asInstanceOf[JsValue].convertTo[CreateResourceApiRequestV1]
                            } catch {
                                case e: DeserializationException => throw BadRequestException("JSON params structure is invalid: " + e.toString)
                            }

                            // check if the API request contains file information: this is illegal for this route
                            _ = if (apiRequest.file.nonEmpty) throw BadRequestException("param 'file' is set for a post multipart request. This is not allowed.")

                            sourcePath <- receivedFile.future

                            // get the file info containing the original filename and content type.
                            fileInfo = allParts.getOrElse(FILE_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$FILE_PART' part!")).asInstanceOf[FileInfo]
                            originalFilename = fileInfo.fileName
                            originalMimeType = fileInfo.contentType.toString


                            sipiConvertPathRequest = SipiResponderConversionPathRequestV1(
                                originalFilename = InputValidation.toSparqlEncodedString(originalFilename, () => throw BadRequestException(s"Original filename is invalid: '$originalFilename'")),
                                originalMimeType = InputValidation.toSparqlEncodedString(originalMimeType, () => throw BadRequestException(s"Original MIME type is invalid: '$originalMimeType'")),
                                source = sourcePath,
                                userProfile = userProfile
                            )

                            requestMessageFuture: Future[ResourceCreateRequestV1] = makeCreateResourceRequestMessage(
                                apiRequest = apiRequest,
                                multipartConversionRequest = Some(sipiConvertPathRequest),
                                userProfile = userProfile
                            )

                            requestMessage <- requestMessageFuture
                        } yield requestMessage

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

        } ~ path("v1" / "resources" / "xml" / Segment) { projectId =>
            post {
                entity(as[String]) { xml =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)

                        val rootElement: Elem = XML.loadString(xml)

                        if (rootElement.namespace + rootElement.label != OntologyConstants.KnoraXmlImportV1.Resources) {
                            throw BadRequestException(s"Root XML element must be ${OntologyConstants.KnoraXmlImportV1.Resources}")
                        }

                        val defaultNamespace = rootElement.getNamespace(null)
                        val otherApiNamespaces = Utility.collectNamespaces(rootElement).toSet.filter {
                            namespace => namespace != defaultNamespace && namespace.startsWith(OntologyConstants.KnoraApi.ApiOntologyStart)
                        }

                        // Validate and parse the XML.
                        validateImportXml(
                            xml = xml,
                            defaultNamespace = defaultNamespace,
                            otherApiNamespaces = otherApiNamespaces
                        )

                        val resourcesToCreate = importXmlToCreateResourceRequests(rootElement)

                        val apiRequestID = UUID.randomUUID
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
        } ~ path("v1" / "xml-schemas" / Segment) { internalOntologyIri =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    val httpResponseFuture = for {
                        schemaZipFileBytes: Array[Byte] <- generateSchemaZipFile(
                            internalOntologyIri = internalOntologyIri,
                            userProfile = userProfile
                        )
                    } yield HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(bytes = schemaZipFileBytes)
                    )

                    requestContext.complete(httpResponseFuture)
            }
        }
    }
}

/**
  * An implementation of [[LSResourceResolver]] that resolves resources from a map of XML namespace URIs to XML files.
  *
  * @param resourceFiles a map of XML namespace URIs to XML files.
  */
class FileResourceResolver(resourceFiles: Map[IRI, File]) extends LSResourceResolver {

    private class FileLSInput(file: File) extends LSInput {
        override def getSystemId: String = null

        override def setEncoding(encoding: String): Unit = ()

        override def getCertifiedText: Boolean = false

        override def setStringData(stringData: String): Unit = ()

        override def setPublicId(publicId: String): Unit = ()

        override def getByteStream: InputStream = new FileInputStream(file)

        override def getEncoding: String = null

        override def setCharacterStream(characterStream: Reader): Unit = ()

        override def setByteStream(byteStream: InputStream): Unit = ()

        override def getBaseURI: String = null

        override def setCertifiedText(certifiedText: Boolean): Unit = ()

        override def getStringData: String = null

        override def getCharacterStream: Reader = null

        override def getPublicId: String = null

        override def setBaseURI(baseURI: String): Unit = ()

        override def setSystemId(systemId: String): Unit = ()
    }

    override def resolveResource(`type`: String, namespaceURI: String, publicId: String, systemId: String, baseURI: String): LSInput = {
        new FileLSInput(resourceFiles(namespaceURI))
    }
}
