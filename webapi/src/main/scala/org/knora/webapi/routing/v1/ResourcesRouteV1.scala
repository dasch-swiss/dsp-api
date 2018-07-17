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


package org.knora.webapi.routing.v1

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory, Validator}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.v1.ResourcesRouteV1.getUserADM
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter.XmlImportNamespaceInfoV1
import org.knora.webapi.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.util.{DateUtilV1, FileUtil, SmartIri, StringFormatter}
import org.knora.webapi.viewhandlers.ResourceHtmlView
import org.slf4j.LoggerFactory
import org.w3c.dom.ls.{LSInput, LSResourceResolver}
import org.xml.sax.SAXException
import spray.json._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.xml._

/**
  * Provides a spray-routing function for API routes that deal with resources.
  */
object ResourcesRouteV1 extends Authenticator {
    // A scala.xml.PrettyPrinter for formatting generated XML import schemas.
    private val xmlPrettyPrinter = new scala.xml.PrettyPrinter(width = 160, step = 4)

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, loggingAdapter: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val log = Logger(LoggerFactory.getLogger(this.getClass))

        def makeResourceRequestMessage(resIri: String,
                                       resinfo: Boolean,
                                       requestType: String,
                                       userADM: UserADM): ResourcesResponderRequestV1 = {
            val validResIri = stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: $resIri"))

            requestType match {
                case "info" => ResourceInfoGetRequestV1(iri = validResIri, userProfile = userADM)
                case "rights" => ResourceRightsGetRequestV1(validResIri, userADM)
                case "context" => ResourceContextGetRequestV1(validResIri, userADM, resinfo)
                case "" => ResourceFullGetRequestV1(validResIri, userADM)
                case other => throw BadRequestException(s"Invalid request type: $other")
            }
        }

        def makeResourceSearchRequestMessage(searchString: String,
                                             resourceTypeIri: Option[IRI],
                                             numberOfProps: Int, limitOfResults: Int,
                                             userProfile: UserADM): ResourceSearchGetRequestV1 = {
            ResourceSearchGetRequestV1(searchString = searchString, resourceTypeIri = resourceTypeIri, numberOfProps = numberOfProps, limitOfResults = limitOfResults, userProfile = userProfile)
        }


        def valuesToCreate(properties: Map[IRI, Seq[CreateResourceValueV1]],
                           acceptStandoffLinksToClientIDs: Boolean,
                           userProfile: UserADM): Map[IRI, Future[Seq[CreateValueV1WithComment]]] = {
            properties.map {
                case (propIri: IRI, values: Seq[CreateResourceValueV1]) =>
                    (stringFormatter.validateAndEscapeIri(propIri, throw BadRequestException(s"Invalid property IRI $propIri")), values.map {
                        case (givenValue: CreateResourceValueV1) =>

                            givenValue.getValueClassIri match {
                                // create corresponding UpdateValueV1

                                case OntologyConstants.KnoraBase.TextValue =>
                                    val richtext: CreateRichtextV1 = givenValue.richtext_value.get

                                    // check if text has markup
                                    if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
                                        // simple text

                                        Future(CreateValueV1WithComment(TextValueSimpleV1(utf8str = stringFormatter.toSparqlEncodedString(richtext.utf8str.get, throw BadRequestException(s"Invalid text: '${richtext.utf8str.get}'")),
                                            language = richtext.language), givenValue.comment))

                                    } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
                                        // XML: text with markup

                                        val mappingIri = stringFormatter.validateAndEscapeIri(richtext.mapping_id.get, throw BadRequestException(s"mapping_id ${richtext.mapping_id.get} is invalid"))


                                        for {

                                            textWithStandoffTags: TextWithStandoffTagsV2 <- RouteUtilV1.convertXMLtoStandoffTagV1(
                                                xml = richtext.xml.get,
                                                mappingIri = mappingIri,
                                                acceptStandoffLinksToClientIDs = acceptStandoffLinksToClientIDs,
                                                userProfile = userProfile,
                                                settings = settings,
                                                responderManager = responderManager,
                                                log = loggingAdapter
                                            )

                                            // collect the resource references from the linking standoff nodes
                                            resourceReferences: Set[IRI] = stringFormatter.getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV2)

                                        } yield CreateValueV1WithComment(TextValueWithStandoffV1(
                                            utf8str = stringFormatter.toSparqlEncodedString(textWithStandoffTags.text, throw InconsistentTriplestoreDataException("utf8str for TextValue contains invalid characters")),
                                            language = richtext.language,
                                            resource_reference = resourceReferences,
                                            standoff = textWithStandoffTags.standoffTagV2,
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
                                            val validatedTargetIri = stringFormatter.validateAndEscapeIri(targetIri, throw BadRequestException(s"Invalid Knora resource IRI: $targetIri"))
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
                                    val uriValue = stringFormatter.validateAndEscapeIri(givenValue.uri_value.get, throw BadRequestException(s"Invalid URI: ${givenValue.uri_value.get}"))
                                    Future(CreateValueV1WithComment(UriValueV1(uriValue), givenValue.comment))

                                case OntologyConstants.KnoraBase.DateValue =>
                                    val dateVal: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(givenValue.date_value.get)
                                    Future(CreateValueV1WithComment(dateVal, givenValue.comment))

                                case OntologyConstants.KnoraBase.ColorValue =>
                                    val colorValue = stringFormatter.validateColor(givenValue.color_value.get, throw BadRequestException(s"Invalid color value: ${givenValue.color_value.get}"))
                                    Future(CreateValueV1WithComment(ColorValueV1(colorValue), givenValue.comment))

                                case OntologyConstants.KnoraBase.GeomValue =>
                                    val geometryValue = stringFormatter.validateGeometryString(givenValue.geom_value.get, throw BadRequestException(s"Invalid geometry value: ${givenValue.geom_value.get}"))
                                    Future(CreateValueV1WithComment(GeomValueV1(geometryValue), givenValue.comment))

                                case OntologyConstants.KnoraBase.ListValue =>
                                    val listNodeIri = stringFormatter.validateAndEscapeIri(givenValue.hlist_value.get, throw BadRequestException(s"Invalid value IRI: ${givenValue.hlist_value.get}"))
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


        def makeCreateResourceRequestMessage(apiRequest: CreateResourceApiRequestV1, multipartConversionRequest: Option[SipiResponderConversionPathRequestV1] = None, userADM: UserADM): Future[ResourceCreateRequestV1] = {
            val projectIri = stringFormatter.validateAndEscapeIri(apiRequest.project_id, throw BadRequestException(s"Invalid project IRI: ${apiRequest.project_id}"))
            val resourceTypeIri = stringFormatter.validateAndEscapeIri(apiRequest.restype_id, throw BadRequestException(s"Invalid resource IRI: ${apiRequest.restype_id}"))
            val label = stringFormatter.toSparqlEncodedString(apiRequest.label, throw BadRequestException(s"Invalid label: '${apiRequest.label}'"))

            // for GUI-case:
            // file has already been stored by Sipi.
            // TODO: in the old SALSAH, the file params were sent as a property salsah:__location__ -> the GUI has to be adapated
            val paramConversionRequest: Option[SipiResponderConversionFileRequestV1] = apiRequest.file match {
                case Some(createFile: CreateFileV1) => Some(SipiResponderConversionFileRequestV1(
                    originalFilename = stringFormatter.toSparqlEncodedString(createFile.originalFilename, throw BadRequestException(s"The original filename is invalid: '${createFile.originalFilename}'")),
                    originalMimeType = stringFormatter.toSparqlEncodedString(createFile.originalMimeType, throw BadRequestException(s"The original MIME type is invalid: '${createFile.originalMimeType}'")),
                    filename = stringFormatter.toSparqlEncodedString(createFile.filename, throw BadRequestException(s"Invalid filename: '${createFile.filename}'")),
                    userProfile = userADM.asUserProfileV1
                ))
                case None => None
            }

            val valuesToBeCreatedWithFuture: Map[IRI, Future[Seq[CreateValueV1WithComment]]] = valuesToCreate(
                properties = apiRequest.properties,
                acceptStandoffLinksToClientIDs = false,
                userProfile = userADM
            )

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
                userProfile = userADM,
                apiRequestID = UUID.randomUUID
            )
        }

        def createOneResourceRequestFromXmlImport(resourceRequest: CreateResourceFromXmlImportRequestV1, userProfile: UserADM): Future[OneOfMultipleResourceCreateRequestV1] = {
            val values: Map[IRI, Future[Seq[CreateValueV1WithComment]]] = valuesToCreate(
                properties = resourceRequest.properties,
                acceptStandoffLinksToClientIDs = true,
                userProfile = userProfile
            )

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
                values = valuesToBeCreated.toMap,
                file = resourceRequest.file.map {
                    fileToRead =>
                        SipiResponderConversionPathRequestV1(
                            originalFilename = stringFormatter.toSparqlEncodedString(fileToRead.file.getName, throw BadRequestException(s"The filename is invalid: '${fileToRead.file.getName}'")),
                            originalMimeType = stringFormatter.toSparqlEncodedString(fileToRead.mimeType, throw BadRequestException(s"The MIME type is invalid: '${fileToRead.mimeType}'")),
                            source = fileToRead.file,
                            userProfile = userProfile.asUserProfileV1
                        )
                }
            )
        }

        def makeMultiResourcesRequestMessage(resourceRequest: Seq[CreateResourceFromXmlImportRequestV1], projectId: IRI, apiRequestID: UUID, userProfile: UserADM): Future[MultipleResourceCreateRequestV1] = {
            // Make sure there are no duplicate client resource IDs.

            val duplicateClientIDs: immutable.Iterable[String] = resourceRequest.map(_.client_id).groupBy(identity).collect { case (clientID, occurrences) if occurrences.size > 1 => clientID }

            if (duplicateClientIDs.nonEmpty) {
                throw BadRequestException(s"One or more client resource IDs were used for multiple resources: ${duplicateClientIDs.mkString(", ")}")
            }

            val resourcesToCreate: Seq[Future[OneOfMultipleResourceCreateRequestV1]] =
                resourceRequest.map(createResourceRequest => createOneResourceRequestFromXmlImport(createResourceRequest, userProfile))

            for {
                resToCreateCollection: Seq[OneOfMultipleResourceCreateRequestV1] <- Future.sequence(resourcesToCreate)
            } yield MultipleResourceCreateRequestV1(resToCreateCollection, projectId, userProfile, apiRequestID)
        }

        def makeGetPropertiesRequestMessage(resIri: IRI, userADM: UserADM) = {
            PropertiesGetRequestV1(resIri, userADM)
        }

        def makeResourceDeleteMessage(resIri: IRI, deleteComment: Option[String], userADM: UserADM) = {
            ResourceDeleteRequestV1(
                resourceIri = stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: $resIri")),
                deleteComment = deleteComment.map(comment => stringFormatter.toSparqlEncodedString(comment, throw BadRequestException(s"Invalid comment: '$comment'"))),
                userADM = userADM,
                apiRequestID = UUID.randomUUID
            )
        }

        /**
          * Given the IRI the main internal ontology to be used in an XML import, recursively gets instances of
          * [[NamedGraphEntityInfoV1]] for that ontology, for `knora-base`, and for any other ontologies containing
          * classes used in object class constraints in the main ontology.
          *
          * @param mainOntologyIri the IRI of the main ontology used in the XML import.
          * @param userProfile     the profile of the user making the request.
          * @return a map of internal ontology IRIs to [[NamedGraphEntityInfoV1]] objects.
          */
        def getNamedGraphInfos(mainOntologyIri: IRI, userProfile: UserADM): Future[Map[IRI, NamedGraphEntityInfoV1]] = {
            /**
              * Does the actual recursion for `getNamedGraphInfos`, loading only information about project-specific
              * ontologies (i.e. ontologies other than `knora-base`).
              *
              * @param initialOntologyIri  the IRI of the internal project-specific ontology to start with.
              * @param intermediateResults the intermediate results collected so far (a map of internal ontology IRIs to
              *                            [[NamedGraphEntityInfoV1]] objects). When this method is first called, this
              *                            collection must already contain a [[NamedGraphEntityInfoV1]] for
              *                            the `knora-base` ontology. This is an optimisation to avoid getting
              *                            information about `knora-base` repeatedly, since every project-specific
              *                            ontology depends on `knora-base`.
              * @param userProfile         the profile of the user making the request.
              * @return a map of internal ontology IRIs to [[NamedGraphEntityInfoV1]] objects.
              */
            def getNamedGraphInfosRec(initialOntologyIri: IRI, intermediateResults: Map[IRI, NamedGraphEntityInfoV1], userProfile: UserADM): Future[Map[IRI, NamedGraphEntityInfoV1]] = {
                assert(intermediateResults.contains(OntologyConstants.KnoraBase.KnoraBaseOntologyIri))

                for {
                    // Get a NamedGraphEntityInfoV1 listing the IRIs of the classes and properties defined in the initial ontology.
                    initialNamedGraphInfo: NamedGraphEntityInfoV1 <- (responderManager ? NamedGraphEntityInfoRequestV1(initialOntologyIri, userProfile)).mapTo[NamedGraphEntityInfoV1]

                    // Get details about those classes and properties.
                    entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                        resourceClassIris = initialNamedGraphInfo.resourceClasses,
                        propertyIris = initialNamedGraphInfo.propertyIris,
                        userProfile = userProfile
                    )).mapTo[EntityInfoGetResponseV1]

                    // Look at the properties that have cardinalities in the resource classes in the initial ontology.
                    // Make a set of the ontologies containing the definitions of those properties, not including the initial ontology itself
                    // or any other ontologies we've already looked at.
                    ontologyIrisFromCardinalities: Set[IRI] = entityInfoResponse.resourceClassInfoMap.foldLeft(Set.empty[IRI]) {
                        case (acc, (resourceClassIri, resourceClassInfo)) =>
                            val resourceCardinalityOntologies: Set[IRI] = resourceClassInfo.knoraResourceCardinalities.map {
                                case (propertyIri, _) => propertyIri.toSmartIri.getOntologyFromEntity.toString
                            }.toSet

                            acc ++ resourceCardinalityOntologies
                    } -- intermediateResults.keySet - initialOntologyIri

                    // Look at the object class constraints of the properties in the initial ontology. Make a set of the ontologies containing those classes,
                    // not including the initial ontology itself or any other ontologies we've already looked at.
                    ontologyIrisFromObjectClassConstraints: Set[IRI] = entityInfoResponse.propertyInfoMap.map {
                        case (propertyIri, propertyInfo) =>
                            val propertyObjectClassConstraint = propertyInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse {
                                throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
                            }

                            propertyObjectClassConstraint.toSmartIri.getOntologyFromEntity.toString
                    }.toSet -- intermediateResults.keySet - initialOntologyIri

                    // Make a set of all the ontologies referenced by the initial ontology.
                    referencedOntologies: Set[IRI] = ontologyIrisFromCardinalities ++ ontologyIrisFromObjectClassConstraints

                    // Recursively get NamedGraphEntityInfoV1 instances for each of those ontologies.
                    futuresOfNamedGraphInfosForReferencedOntologies: Set[Future[Map[IRI, NamedGraphEntityInfoV1]]] = referencedOntologies.map {
                        ontologyIri =>
                            getNamedGraphInfosRec(
                                initialOntologyIri = ontologyIri,
                                intermediateResults = intermediateResults + (initialOntologyIri -> initialNamedGraphInfo),
                                userProfile = userProfile
                            )
                    }

                    namedGraphInfosFromReferencedOntologies: Set[Map[IRI, NamedGraphEntityInfoV1]] <- Future.sequence(futuresOfNamedGraphInfosForReferencedOntologies)

                    // Return the previous intermediate results, plus the information about the initial ontology
                    // and the other referenced ontologies.
                } yield namedGraphInfosFromReferencedOntologies.flatten.toMap ++ intermediateResults + (initialOntologyIri -> initialNamedGraphInfo)
            }

            for {
                // Get a NamedGraphEntityInfoV1 for the knora-base ontology.
                knoraBaseGraphEntityInfo <- (responderManager ? NamedGraphEntityInfoRequestV1(OntologyConstants.KnoraBase.KnoraBaseOntologyIri, userProfile)).mapTo[NamedGraphEntityInfoV1]

                // Recursively get NamedGraphEntityInfoV1 instances for the main ontology to be used in the XML import,
                // as well as any other project-specific ontologies it depends on.
                graphInfos <- getNamedGraphInfosRec(
                    initialOntologyIri = mainOntologyIri,
                    intermediateResults = Map(OntologyConstants.KnoraBase.KnoraBaseOntologyIri -> knoraBaseGraphEntityInfo),
                    userProfile = userProfile
                )
            } yield graphInfos
        }

        /**
          * Given the IRI of an internal project-specific ontology, generates an [[XmlImportSchemaBundleV1]] for validating
          * XML imports for that ontology and any other ontologies it depends on.
          *
          * @param internalOntologyIri the IRI of the main internal project-specific ontology to be used in the XML import.
          * @param userProfile         the profile of the user making the request.
          * @return an [[XmlImportSchemaBundleV1]] for validating the import.
          */
        def generateSchemasFromOntologies(internalOntologyIri: IRI, userProfile: UserADM): Future[XmlImportSchemaBundleV1] = {
            /**
              * Called by the schema generation template to get the prefix label for an internal ontology
              * entity IRI. The schema generation template gets these IRIs from resource cardinalities
              * and property object class constraints, which we get from the ontology responder.
              *
              * @param internalEntityIri an internal ontology entity IRI.
              * @return the prefix label that Knora uses to refer to the ontology.
              */
            def getNamespacePrefixLabel(internalEntityIri: IRI): String = {
                val prefixLabel = internalEntityIri.toSmartIri.getLongPrefixLabel

                // If the schema generation template asks for the prefix label of something in knora-base, return
                // the prefix label of the Knora XML import v1 namespace instead.
                if (prefixLabel == OntologyConstants.KnoraBase.KnoraBaseOntologyLabel) {
                    OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel
                } else {
                    prefixLabel
                }
            }

            /**
              * Called by the schema generation template to get the entity name (i.e. the local name part) of an
              * internal ontology entity IRI. The schema generation template gets these IRIs from resource cardinalities
              * and property object class constraints, which we get from the ontology responder.
              *
              * @param internalEntityIri an internal ontology entity IRI.
              * @return the local name of the entity.
              */
            def getEntityName(internalEntityIri: IRI): String = {
                internalEntityIri.toSmartIri.getEntityName
            }

            for {
                // Get a NamedGraphEntityInfoV1 for each ontology that we need to generate an XML schema for.
                namedGraphInfos: Map[IRI, NamedGraphEntityInfoV1] <- getNamedGraphInfos(mainOntologyIri = internalOntologyIri, userProfile = userProfile)

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

                // Sequence the futures of entity info responses.
                entityInfoResponses: immutable.Iterable[(IRI, EntityInfoGetResponseV1)] <- Future.sequence(entityInfoResponseFutures)

                // Make a Map of internal ontology IRIs to EntityInfoGetResponseV1 objects.
                entityInfoResponsesMap: Map[IRI, EntityInfoGetResponseV1] = entityInfoResponses.toMap

                // Collect all the property definitions in a single Map. Since any schema could use any property, we will
                // pass this Map to the schema generation template for every schema.
                propertyInfoMap: Map[IRI, PropertyInfoV1] = entityInfoResponsesMap.values.flatMap(_.propertyInfoMap).toMap

                // Make a map of internal ontology IRIs to XmlImportNamespaceInfoV1 objects describing the XML namespace
                // of each schema to be generated. Don't generate a schema for knora-base, because the built-in Knora
                // types are specified in the handwritten standard Knora XML import v1 schema.
                schemasToGenerate: Map[IRI, XmlImportNamespaceInfoV1] = (namedGraphInfos.keySet - OntologyConstants.KnoraBase.KnoraBaseOntologyIri).map {
                    ontologyIri => ontologyIri -> stringFormatter.internalOntologyIriToXmlNamespaceInfoV1(ontologyIri.toSmartIri)
                }.toMap

                // Make an XmlImportNamespaceInfoV1 for the standard Knora XML import v1 schema's namespace.
                knoraXmlImportSchemaNamespaceInfo: XmlImportNamespaceInfoV1 = XmlImportNamespaceInfoV1(
                    namespace = OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespaceV1,
                    prefixLabel = OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel
                )

                // Read the standard Knora XML import v1 schema from a file.
                knoraXmlImportSchemaXml: String = FileUtil.readTextResource(OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel + ".xsd")

                // Construct an XmlImportSchemaV1 for the standard Knora XML import v1 schema.
                knoraXmlImportSchema: XmlImportSchemaV1 = XmlImportSchemaV1(
                    namespaceInfo = knoraXmlImportSchemaNamespaceInfo,
                    schemaXml = knoraXmlImportSchemaXml
                )

                // Generate a schema for each project-specific ontology.
                generatedSchemas: Map[IRI, XmlImportSchemaV1] = schemasToGenerate.map {
                    case (ontologyIri, namespaceInfo) =>
                        // Each schema imports all the other generated schemas, plus the standard Knora XML import v1 schema.
                        // Sort the imports to make schema generation deterministic.
                        val importedNamespaceInfos: Seq[XmlImportNamespaceInfoV1] = (schemasToGenerate - ontologyIri).values.toVector.sortBy {
                            importedNamespaceInfo => importedNamespaceInfo.prefixLabel
                        } :+ knoraXmlImportSchemaNamespaceInfo

                        // Generate the schema using a Twirl template.
                        val unformattedSchemaXml = xsd.v1.xml.xmlImport(
                            targetNamespaceInfo = namespaceInfo,
                            importedNamespaces = importedNamespaceInfos,
                            knoraXmlImportNamespacePrefixLabel = OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel,
                            resourceClassInfoMap = entityInfoResponsesMap(ontologyIri).resourceClassInfoMap,
                            propertyInfoMap = propertyInfoMap,
                            getNamespacePrefixLabel = internalEntityIri => getNamespacePrefixLabel(internalEntityIri),
                            getEntityName = internalEntityIri => getEntityName(internalEntityIri)
                        ).toString().trim

                        // Parse the generated XML schema.
                        val parsedSchemaXml = try {
                            XML.loadString(unformattedSchemaXml)
                        } catch {
                            case parseEx: org.xml.sax.SAXParseException => throw AssertionException(s"Generated XML schema for namespace ${namespaceInfo.namespace} is not valid XML. Please report this as a bug.", parseEx, loggingAdapter)
                        }

                        // Format the generated XML schema nicely.
                        val formattedSchemaXml = xmlPrettyPrinter.format(parsedSchemaXml)

                        // Wrap it in an XmlImportSchemaV1 object along with its XML namespace information.
                        val schema = XmlImportSchemaV1(
                            namespaceInfo = namespaceInfo,
                            schemaXml = formattedSchemaXml
                        )

                        namespaceInfo.namespace -> schema
                }

                // The schema bundle to be returned contains the generated schemas plus the standard Knora XML import v1 schema.
                allSchemasForBundle: Map[IRI, XmlImportSchemaV1] = generatedSchemas + (OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespaceV1 -> knoraXmlImportSchema)
            } yield XmlImportSchemaBundleV1(
                mainNamespace = schemasToGenerate(internalOntologyIri).namespace,
                schemas = allSchemasForBundle
            )
        }

        /**
          * Generates a byte array representing a Zip file containing XML schemas for validating XML import data.
          *
          * @param internalOntologyIri the IRI of the main internal ontology for which data will be imported.
          * @param userProfile         the profile of the user making the request.
          * @return a byte array representing a Zip file containing XML schemas.
          */
        def generateSchemaZipFile(internalOntologyIri: IRI, userProfile: UserADM): Future[Array[Byte]] = {
            for {
                // Generate a bundle of XML schemas.
                schemaBundle: XmlImportSchemaBundleV1 <- generateSchemasFromOntologies(
                    internalOntologyIri = internalOntologyIri,
                    userProfile = userProfile
                )

                // Generate the contents of the Zip file: a Map of file names to file contents (byte arrays).
                zipFileContents: Map[String, Array[Byte]] = schemaBundle.schemas.values.map {
                    schema: XmlImportSchemaV1 =>
                        val schemaFilename: String = schema.namespaceInfo.prefixLabel + ".xsd"
                        val schemaXmlBytes: Array[Byte] = schema.schemaXml.getBytes(StandardCharsets.UTF_8)
                        schemaFilename -> schemaXmlBytes
                }.toMap
            } yield FileUtil.createZipFileBytes(zipFileContents)
        }

        /**
          * Validates bulk import XML using project-specific XML schemas and the Knora XML import schema v1.
          *
          * @param xml              the XML to be validated.
          * @param defaultNamespace the default namespace of the submitted XML. This should be the Knora XML import
          *                         namespace corresponding to the main internal ontology used in the import.
          * @param userADM          the profile of the user making the request.
          * @return a `Future` containing `()` if successful, otherwise a failed future.
          */
        def validateImportXml(xml: String, defaultNamespace: IRI, userADM: UserADM): Future[Unit] = {
            // Convert the default namespace of the submitted XML to an internal ontology IRI. This should be the
            // IRI of the main ontology used in the import.

            val mainOntologyIri: SmartIri = stringFormatter.xmlImportNamespaceToInternalOntologyIriV1(
                defaultNamespace, throw BadRequestException(s"Invalid XML import namespace: $defaultNamespace")
            )

            val validationFuture: Future[Unit] = for {
                // Generate a bundle of XML schemas for validating the submitted XML.
                schemaBundle: XmlImportSchemaBundleV1 <- generateSchemasFromOntologies(mainOntologyIri.toString, userADM)

                // Make a javax.xml.validation.SchemaFactory for instantiating XML schemas.
                schemaFactory: SchemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

                // Tell the SchemaFactory to find additional schemas using our SchemaBundleResolver, which gets them
                // from the XmlImportSchemaBundleV1 we generated.
                _ = schemaFactory.setResourceResolver(new SchemaBundleResolver(schemaBundle))

                // Use the SchemaFactory to instantiate a javax.xml.validation.Schema representing the main schema in
                // the bundle.
                mainSchemaXml: String = schemaBundle.schemas(schemaBundle.mainNamespace).schemaXml
                schemaInstance: Schema = schemaFactory.newSchema(new StreamSource(new StringReader(mainSchemaXml)))

                // Validate the submitted XML using a validator based on the main schema.
                schemaValidator: Validator = schemaInstance.newValidator()
                _ = schemaValidator.validate(new StreamSource(new StringReader(xml)))
            } yield ()

            // If the XML fails schema validation, return a failed Future containing a BadRequestException.
            validationFuture.recover {
                case e@(_: IllegalArgumentException | _: SAXException) =>
                    throw BadRequestException(s"XML import did not pass XML schema validation: $e")
            }
        }

        /**
          * Converts parsed import XML into a sequence of [[CreateResourceFromXmlImportRequestV1]] for each resource
          * described in the XML.
          *
          * @param rootElement the root element of an XML document describing multiple resources to be created.
          * @return Seq[CreateResourceFromXmlImportRequestV1] a collection of resource creation requests.
          */
        def importXmlToCreateResourceRequests(rootElement: Elem): Seq[CreateResourceFromXmlImportRequestV1] = {
            rootElement.head.child
                    .filter(node => node.label != "#PCDATA")
                    .map(resourceNode => {
                        // Get the client's unique ID for the resource.
                        val clientIDForResource: String = (resourceNode \ "@id").toString

                        // Convert the XML element's label and namespace to an internal resource class IRI.

                        val elementNamespace: String = resourceNode.getNamespace(resourceNode.prefix)

                        val restype_id = stringFormatter.xmlImportElementNameToInternalOntologyIriV1(
                            namespace = elementNamespace,
                            elementLabel = resourceNode.label,
                            errorFun = throw BadRequestException(s"Invalid XML namespace: $elementNamespace")
                        )

                        // Get the child elements of the resource element.
                        val childElements: Seq[Node] = resourceNode.child.filterNot(_.label == "#PCDATA")

                        // The label must be the first child element of the resource element.
                        val resourceLabel: String = childElements.headOption match {
                            case Some(firstChildElem) => firstChildElem.text
                            case None => throw BadRequestException(s"Resource '$clientIDForResource' contains no ${OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel}:label element")
                        }

                        val childElementsAfterLabel = childElements.tail

                        // Get the resource's file metadata, if any. This represents a file that has already been stored by Sipi.
                        // If provided, it must be the second child element of the resource element.
                        val file: Option[ReadFileV1] = childElementsAfterLabel.headOption match {
                            case Some(secondChildElem) =>
                                if (secondChildElem.label == "file") {
                                    val path = Paths.get(secondChildElem.attribute("path").get.text)

                                    if (!path.isAbsolute) {
                                        throw BadRequestException(s"File path $path in resource '$clientIDForResource' is not absolute")
                                    }

                                    Some(ReadFileV1(
                                        file = path.toFile,
                                        mimeType = secondChildElem.attribute("mimetype").get.text
                                    ))
                                } else {
                                    None
                                }

                            case None => None
                        }

                        // Any remaining child elements of the resource element represent property values.
                        val propertyElements = if (file.isDefined) {
                            childElementsAfterLabel.tail
                        } else {
                            childElementsAfterLabel
                        }

                        // Traverse the property value elements. This produces a sequence in which the same property IRI
                        // can occur multiple times, once for each value.
                        val propertiesWithValues: Seq[(IRI, CreateResourceValueV1)] = propertyElements.map {
                            propertyNode =>
                                // Is this a property from another ontology (in the form prefixLabel__localName)?
                                val propertyIri = stringFormatter.toPropertyIriFromOtherOntologyInXmlImport(propertyNode.label) match {
                                    case Some(iri) =>
                                        // Yes. Use the corresponding entity IRI for it.
                                        iri

                                    case None =>
                                        // No. Convert the XML element's label and namespace to an internal property IRI.

                                        val propertyNodeNamespace = propertyNode.getNamespace(propertyNode.prefix)

                                        stringFormatter.xmlImportElementNameToInternalOntologyIriV1(
                                            namespace = propertyNodeNamespace,
                                            elementLabel = propertyNode.label,
                                            errorFun = throw BadRequestException(s"Invalid XML namespace: $propertyNodeNamespace"))
                                }

                                // If the property element has one child element with a knoraType attribute, it's a link
                                // property, otherwise it's an ordinary value property.

                                val valueNodes: Seq[Node] = propertyNode.child.filterNot(_.label == "#PCDATA")

                                if (valueNodes.size == 1 && valueNodes.head.attribute("knoraType").isDefined) {
                                    propertyIri -> knoraDataTypeXml(valueNodes.head)
                                } else {
                                    propertyIri -> knoraDataTypeXml(propertyNode)
                                }
                        }

                        // Group the values by property IRI.
                        val groupedPropertiesWithValues: Map[IRI, Seq[CreateResourceValueV1]] = propertiesWithValues.groupBy {
                            case (propertyIri: IRI, _) => propertyIri
                        }.map {
                            case (propertyIri: IRI, resultsForProperty: Seq[(IRI, CreateResourceValueV1)]) =>
                                propertyIri -> resultsForProperty.map {
                                    case (_, propertyValue: CreateResourceValueV1) => propertyValue
                                }
                        }

                        CreateResourceFromXmlImportRequestV1(
                            restype_id = restype_id,
                            client_id = clientIDForResource,
                            label = resourceLabel,
                            properties = groupedPropertiesWithValues,
                            file = file
                        )
                    })
        }

        /**
          * Given an XML element representing a property value in an XML import, returns a [[CreateResourceValueV1]]
          * describing the value to be created.
          *
          * @param node the XML element.
          * @return a [[CreateResourceValueV1]] requesting the creation of the value described by the element.
          */
        def knoraDataTypeXml(node: Node): CreateResourceValueV1 = {
            val knoraType: Seq[Node] = node.attribute("knoraType").getOrElse(throw BadRequestException(s"Attribute 'knoraType' missing in element '${node.label}'"))
            val elementValue = node.text

            if (knoraType.nonEmpty) {
                val language = node.attribute("lang").map(s => s.head.toString)
                knoraType.toString match {
                    case "richtext_value" =>
                        val maybeMappingID: Option[Seq[Node]] = node.attributes.get("mapping_id")

                        maybeMappingID match {
                            case Some(mappingID) =>
                                val mappingIri: Option[IRI] = Some(stringFormatter.validateAndEscapeIri(mappingID.toString, throw BadRequestException(s"Invalid mapping ID in element '${node.label}: '$mappingID")))
                                val childElements = node.child.filterNot(_.label == "#PCDATA")

                                if (childElements.nonEmpty) {
                                    val embeddedXmlRootNode = childElements.head
                                    val embeddedXmlDoc = """<?xml version="1.0" encoding="UTF-8"?>""" + embeddedXmlRootNode.toString
                                    CreateResourceValueV1(richtext_value = Some(CreateRichtextV1(utf8str = None, language = language, xml = Some(embeddedXmlDoc), mapping_id = mappingIri)))
                                } else {
                                    throw BadRequestException(s"Element '${node.label}' provides a mapping_id, but its content is not XML")
                                }

                            case None =>
                                CreateResourceValueV1(richtext_value = Some(CreateRichtextV1(utf8str = Some(elementValue), language = language)))
                        }

                    case "link_value" =>
                        val linkType = node.attribute("linkType").get.headOption match {
                            case Some(linkTypeNode: Node) => linkTypeNode.text
                            case None => throw BadRequestException(s"Attribute 'linkType' missing in element '${node.label}'")
                        }

                        node.attribute("target").get.headOption match {
                            case Some(targetNode: Node) =>
                                val target = targetNode.text

                                linkType match {
                                    case "ref" => CreateResourceValueV1(link_to_client_id = Some(target))
                                    case "iri" => CreateResourceValueV1(link_value = Some(stringFormatter.validateAndEscapeIri(target, throw BadRequestException(s"Invalid IRI in element '${node.label}': '$target'"))))
                                    case other => throw BadRequestException(s"Unrecognised value '$other' in attribute 'linkType' of element '${node.label}'")
                                }

                            case None => throw BadRequestException(s"Attribute 'ref' missing in element '${node.label}'")
                        }

                    case "int_value" =>
                        CreateResourceValueV1(int_value = Some(stringFormatter.validateInt(elementValue, throw BadRequestException(s"Invalid integer value in element '${node.label}: '$elementValue'"))))

                    case "decimal_value" =>
                        CreateResourceValueV1(decimal_value = Some(stringFormatter.validateBigDecimal(elementValue, throw BadRequestException(s"Invalid decimal value in element '${node.label}: '$elementValue'"))))

                    case "boolean_value" =>
                        CreateResourceValueV1(boolean_value = Some(stringFormatter.validateBoolean(elementValue, throw BadRequestException(s"Invalid boolean value in element '${node.label}: '$elementValue'"))))

                    case "uri_value" =>
                        CreateResourceValueV1(uri_value = Some(stringFormatter.validateAndEscapeIri(elementValue, throw BadRequestException(s"Invalid URI value in element '${node.label}: '$elementValue'"))))

                    case "date_value" =>
                        CreateResourceValueV1(date_value = Some(stringFormatter.validateDate(elementValue, throw BadRequestException(s"Invalid date value in element '${node.label}: '$elementValue'"))))

                    case "color_value" =>
                        CreateResourceValueV1(color_value = Some(stringFormatter.validateColor(elementValue, throw BadRequestException(s"Invalid date value in element '${node.label}: '$elementValue'"))))

                    case "geom_value" =>
                        CreateResourceValueV1(geom_value = Some(stringFormatter.validateGeometryString(elementValue, throw BadRequestException(s"Invalid geometry value in element '${node.label}: '$elementValue'"))))

                    case "hlist_value" =>
                        CreateResourceValueV1(hlist_value = Some(stringFormatter.validateAndEscapeIri(elementValue, throw BadRequestException(s"Invalid hlist value in element '${node.label}: '$elementValue'"))))

                    case "interval_value" =>
                        Try(elementValue.split(",")) match {
                            case Success(timeVals) =>
                                if (timeVals.length != 2) throw BadRequestException(s"Invalid interval value in element '${node.label}: '$elementValue'")

                                val tVals: Seq[BigDecimal] = timeVals.map {
                                    timeVal =>
                                        stringFormatter.validateBigDecimal(timeVal, throw BadRequestException(s"Invalid decimal value in element '${node.label}: '$timeVal'"))
                                }

                                CreateResourceValueV1(interval_value = Some(tVals))

                            case Failure(_) =>
                                throw BadRequestException(s"Invalid interval value in element '${node.label}: '$elementValue'")
                        }

                    case "geoname_value" =>
                        CreateResourceValueV1(geoname_value = Some(elementValue))
                    case other => throw BadRequestException(s"Invalid 'knoraType' in element '${node.label}': '$other'")
                }
            } else {
                throw BadRequestException(s"Attribute 'knoraType' missing in element '${node.label}'")
            }
        }

        path("v1" / "resources") {
            get {
                // search for resources matching the given search string (searchstr) and return their Iris.
                requestContext =>


                    val requestMessage = for {
                        userProfile <- getUserADM(requestContext)
                        params = requestContext.request.uri.query().toMap
                        searchstr = params.getOrElse("searchstr", throw BadRequestException(s"required param searchstr is missing"))

                        // default -1 means: no restriction at all
                        restype = params.getOrElse("restype_id", "-1")

                        numprops = params.getOrElse("numprops", "1")
                        limit = params.getOrElse("limit", "11")

                        // input validation

                        searchString = stringFormatter.toSparqlEncodedString(searchstr, throw BadRequestException(s"Invalid search string: '$searchstr'"))

                        resourceTypeIri: Option[IRI] = restype match {
                            case ("-1") => None
                            case (restype: IRI) => Some(stringFormatter.validateAndEscapeIri(restype, throw BadRequestException(s"Invalid param restype: $restype")))
                        }

                        numberOfProps: Int = stringFormatter.validateInt(numprops, throw BadRequestException(s"Invalid param numprops: $numprops")) match {
                            case (number: Int) => if (number < 1) 1 else number // numberOfProps must not be smaller than 1
                        }

                        limitOfResults = stringFormatter.validateInt(limit, throw BadRequestException(s"Invalid param limit: $limit"))

                    } yield makeResourceSearchRequestMessage(
                        searchString = searchString,
                        resourceTypeIri = resourceTypeIri,
                        numberOfProps = numberOfProps,
                        limitOfResults = limitOfResults,
                        userProfile = userProfile
                    )

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessageF = requestMessage,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = loggingAdapter
                    )
            } ~ post {
                // Create a new resource with he given type and possibly a file (GUI-case).
                // The binary file is already managed by Sipi.
                // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
                entity(as[CreateResourceApiRequestV1]) { apiRequest =>
                    requestContext =>
                        val requestMessageFuture = for {
                            userProfile <- getUserADM(requestContext)
                            request <- makeCreateResourceRequestMessage(apiRequest = apiRequest, userADM = userProfile)
                        } yield request

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
                        )
                }
            } ~ post {
                // Create a new resource with the given type, properties, and binary data (file) (non GUI-case).
                // The binary data are contained in the request and have to be temporarily stored by Knora.
                // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
                entity(as[Multipart.FormData]) { formdata: Multipart.FormData =>
                    requestContext =>

                        log.debug("/v1/resources - POST - Multipart.FormData - Route")

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
                                val tmpFile = FileUtil.createTempFile(settings)
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

                            userADM <- getUserADM(requestContext)
                            userProfile = userADM.asUserProfileV1

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
                                originalFilename = stringFormatter.toSparqlEncodedString(originalFilename, throw BadRequestException(s"Original filename is invalid: '$originalFilename'")),
                                originalMimeType = stringFormatter.toSparqlEncodedString(originalMimeType, throw BadRequestException(s"Original MIME type is invalid: '$originalMimeType'")),
                                source = sourcePath,
                                userProfile = userProfile
                            )

                            requestMessage <- makeCreateResourceRequestMessage(
                                apiRequest = apiRequest,
                                multipartConversionRequest = Some(sipiConvertPathRequest),
                                userADM = userADM
                            )
                        } yield requestMessage

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "resources" / Segment) { resIri =>
            get {
                parameters("reqtype".?, "resinfo".as[Boolean].?) { (reqtypeParam, resinfoParam) =>
                    requestContext =>

                        val requestMessage =
                            for {
                                userADM <- getUserADM(requestContext)
                                requestType = reqtypeParam.getOrElse("")
                                resinfo = resinfoParam.getOrElse(false)
                            } yield makeResourceRequestMessage(resIri = resIri, resinfo = resinfo, requestType = requestType, userADM = userADM)

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessage,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
                        )
                }
            } ~ delete {
                parameters("deleteComment".?) { deleteCommentParam =>
                    requestContext =>

                        val requestMessage = for {
                            userADM <- getUserADM(requestContext)
                        } yield makeResourceDeleteMessage(resIri = resIri, deleteComment = deleteCommentParam, userADM = userADM)

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessage,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "resources.html" / Segment) { iri =>
            get {
                requestContext =>

                    val params = requestContext.request.uri.query().toMap
                    val requestType = params.getOrElse("reqtype", "")

                    val requestMessage = requestType match {
                        case "properties" =>
                            for {
                                userADM <- getUserADM(requestContext)
                                resIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param resource IRI: $iri"))
                            } yield ResourceFullGetRequestV1(resIri, userADM)
                        case other => throw BadRequestException(s"Invalid request type: $other")
                    }

                    RouteUtilV1.runHtmlRoute[ResourcesResponderRequestV1, ResourceFullResponseV1](
                        requestMessageF = requestMessage,
                        viewHandler = ResourceHtmlView.propertiesHtmlView,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = loggingAdapter
                    )
            }
        } ~ path("v1" / "properties" / Segment) { iri =>
            get {
                requestContext =>
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                        resIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param resource IRI: $iri"))
                    } yield makeGetPropertiesRequestMessage(resIri, userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessageF = requestMessage,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = loggingAdapter
                    )

            }
        } ~ path("v1" / "resources" / "label" / Segment) { iri =>
            put {
                entity(as[ChangeResourceLabelApiRequestV1]) { apiRequest =>
                    requestContext =>
                        val requestMessage = for {
                            userADM <- getUserADM(requestContext)
                            resIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param resource IRI: $iri"))
                            label = stringFormatter.toSparqlEncodedString(apiRequest.label, throw BadRequestException(s"Invalid label: '${apiRequest.label}'"))
                        } yield ChangeResourceLabelRequestV1(
                            resourceIri = resIri,
                            label = label,
                            apiRequestID = UUID.randomUUID,
                            userADM = userADM
                        )

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessage,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "graphdata" / Segment) { iri =>
            get {
                parameters("depth".as[Int].?) { depth =>
                    requestContext =>
                        val requestMessage = for {
                            userADM <- getUserADM(requestContext)
                            resourceIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param resource IRI: $iri"))
                        } yield GraphDataGetRequestV1(resourceIri, depth.getOrElse(4), userADM)

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessage,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
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
                        requestMessage = msg,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = loggingAdapter
                    )
            }
        } ~ path("v1" / "resources" / "xmlimport" / Segment) { projectId =>
            post {
                entity(as[String]) { xml =>
                    requestContext =>
                        val requestMessage = for {
                            userADM <- getUserADM(requestContext)

                            _ = if (userADM.isAnonymousUser) {
                                throw BadRequestException("You are not logged in, and only a system administrator or project administrator can perform a bulk import")
                            }

                            _ = if (!(userADM.permissions.isSystemAdmin || userADM.permissions.isProjectAdmin(projectId))) {
                                throw BadRequestException(s"You are logged in as ${userADM.email}, but only a system administrator or project administrator can perform a bulk import")
                            }

                            // Parse the submitted XML.
                            rootElement: Elem = XML.loadString(xml)

                            // Make sure that the root element is knoraXmlImport:resources.
                            _ = if (rootElement.namespace + rootElement.label != OntologyConstants.KnoraXmlImportV1.Resources) {
                                throw BadRequestException(s"Root XML element must be ${OntologyConstants.KnoraXmlImportV1.Resources}")
                            }

                            // Get the default namespace of the submitted XML. This should be the Knora XML import
                            // namespace corresponding to the main internal ontology used in the import.
                            defaultNamespace = rootElement.getNamespace(null)

                            // Validate the XML using XML schemas.
                            _ <- validateImportXml(
                                xml = xml,
                                defaultNamespace = defaultNamespace,
                                userADM = userADM
                            )

                            // Make a CreateResourceFromXmlImportRequestV1 for each resource to be created.
                            resourcesToCreate: Seq[CreateResourceFromXmlImportRequestV1] = importXmlToCreateResourceRequests(rootElement)

                            // Make a MultipleResourceCreateRequestV1 for the creation of all the resources.
                            apiRequestID: UUID = UUID.randomUUID
                            updateRequest: MultipleResourceCreateRequestV1 <- makeMultiResourcesRequestMessage(resourcesToCreate, projectId, apiRequestID, userADM)
                        } yield updateRequest

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageF = requestMessage,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = loggingAdapter
                        )(timeout = 1.hour, executionContext = executionContext)
                }

            }
        } ~ path("v1" / "resources" / "xmlimportschemas" / Segment) { internalOntologyIri =>
            get {
                // Get the prefix label of the specified internal ontology.
                val internalOntologySmartIri: SmartIri = internalOntologyIri.toSmartIriWithErr(throw BadRequestException(s"Invalid internal project-specific ontology IRI: $internalOntologyIri"))

                if (!internalOntologySmartIri.isKnoraOntologyIri || internalOntologySmartIri.isKnoraBuiltInDefinitionIri) {
                    throw BadRequestException(s"Invalid internal project-specific ontology IRI: $internalOntologyIri")
                }

                val internalOntologyPrefixLabel: String = internalOntologySmartIri.getLongPrefixLabel

                // Respond with a Content-Disposition header specifying the filename of the generated Zip file.
                respondWithHeader(`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> (internalOntologyPrefixLabel + "-xml-schemas.zip")))) {
                    requestContext =>
                        val httpResponseFuture: Future[HttpResponse] = for {
                            userProfile <- getUserADM(requestContext)
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
      * Represents an XML import schema corresponding to an ontology.
      *
      * @param namespaceInfo information about the schema's namespace.
      * @param schemaXml     the XML text of the schema.
      */
    case class XmlImportSchemaV1(namespaceInfo: XmlImportNamespaceInfoV1, schemaXml: String)

    /**
      * Represents a bundle of XML import schemas corresponding to ontologies.
      *
      * @param mainNamespace the XML namespace corresponding to the main ontology to be used in the XML import.
      * @param schemas       a map of XML namespaces to schemas.
      */
    case class XmlImportSchemaBundleV1(mainNamespace: IRI, schemas: Map[IRI, XmlImportSchemaV1])

    /**
      * An implementation of [[LSResourceResolver]] that resolves resources from a [[XmlImportSchemaBundleV1]].
      * This is used to allow the XML schema validator to load additional schemas during XML import data validation.
      *
      * @param schemaBundle an [[XmlImportSchemaBundleV1]].
      */
    class SchemaBundleResolver(schemaBundle: XmlImportSchemaBundleV1) extends LSResourceResolver {
        private val contents: Map[IRI, Array[Byte]] = schemaBundle.schemas.map {
            case (namespace, schema) => namespace -> schema.schemaXml.getBytes(StandardCharsets.UTF_8)
        }

        private class ByteArrayLSInput(content: Array[Byte]) extends LSInput {
            override def getSystemId: String = null

            override def setEncoding(encoding: String): Unit = ()

            override def getCertifiedText: Boolean = false

            override def setStringData(stringData: String): Unit = ()

            override def setPublicId(publicId: String): Unit = ()

            override def getByteStream: InputStream = new ByteArrayInputStream(content)

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
            new ByteArrayLSInput(contents(namespaceURI))
        }
    }

}
