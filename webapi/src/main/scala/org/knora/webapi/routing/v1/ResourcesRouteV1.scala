/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import zio._

import java.io._
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml._

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.KnoraBase.ObjectClassConstraint
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.StringFormatter.XmlImportNamespaceInfoV1
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.util.DateUtilV1
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1
import org.knora.webapi.routing.RouteUtilV1._
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.util.FileUtil

/**
 * Provides API routes that deal with resources.
 */
final case class ResourcesRouteV1()(
  private implicit val runtime: Runtime[
    Authenticator with IriConverter with KnoraProjectRepo with StringFormatter with MessageRelay
  ]
) extends LazyLogging {
  // A scala.xml.PrettyPrinter for formatting generated XML import schemas.
  private val xmlPrettyPrinter = new scala.xml.PrettyPrinter(width = 160, step = 4)

  def makeRoute: Route = {
    path("v1" / "resources") {
      get {
        // search for resources matching the given search string (searchstr) and return their Iris.
        requestContext =>
          val requestMessage = for {
            userProfile <- Authenticator.getUserADM(requestContext)
            params       = requestContext.request.uri.query().toMap
            searchstr <- ZIO
                           .fromOption(params.get("searchstr"))
                           .orElseFail(BadRequestException(s"required param searchstr is missing"))

            restype  = params.getOrElse("restype_id", "-1") // default -1 means: no restriction at all
            numprops = params.getOrElse("numprops", "1")
            limit    = params.getOrElse("limit", "11")

            // input validation
            searchString <- RouteUtilV1.toSparqlEncodedString(searchstr, s"Invalid search string: '$searchstr'")

            resourceTypeIri <- restype match {
                                 case "-1" => ZIO.none
                                 case restype: IRI =>
                                   RouteUtilZ
                                     .validateAndEscapeIri(restype, "Invalid param restype: $restype")
                                     .map(Some(_))
                               }

            numberOfProps <- ZIO
                               .fromOption(ValuesValidator.validateInt(numprops))
                               .mapBoth(
                                 _ => BadRequestException(s"Invalid param numprops: $numprops"),
                                 number => if (number < 1) 1 else number // numberOfProps must not be smaller than 1
                               )

            limitOfResults <- ZIO
                                .fromOption(ValuesValidator.validateInt(limit))
                                .orElseFail(BadRequestException(s"Invalid param limit: $limit"))
          } yield ResourceSearchGetRequestV1(searchString, resourceTypeIri, numberOfProps, limitOfResults, userProfile)
          runJsonRouteZ(requestMessage, requestContext)
      } ~ post {
        // Create a new resource with the given type and possibly a file.
        // The binary file is already managed by Sipi.
        // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
        entity(as[CreateResourceApiRequestV1]) { apiRequest => requestContext =>
          val requestMessageFuture = for {
            userProfile <- Authenticator.getUserADM(requestContext)
            request     <- makeCreateResourceRequestMessage(apiRequest, userProfile)
          } yield request
          runJsonRouteZ(requestMessageFuture, requestContext)
        }
      }
    } ~ path("v1" / "resources" / Segment) { resIri =>
      get {
        parameters("reqtype".?, "resinfo".as[Boolean].?) { (reqtypeParam, resinfoParam) => requestContext =>
          val requestMessage =
            Authenticator
              .getUserADM(requestContext)
              .flatMap(makeResourceRequestMessage(resIri, resinfoParam.getOrElse(false), reqtypeParam.getOrElse(""), _))
          runJsonRouteZ(requestMessage, requestContext)
        }
      } ~ delete {
        parameters("deleteComment".?) { deleteCommentParam => requestContext =>
          val requestMessage = Authenticator
            .getUserADM(requestContext)
            .flatMap(makeResourceDeleteMessage(resIri, deleteCommentParam, _))
          runJsonRouteZ(requestMessage, requestContext)
        }
      }
    } ~ path("v1" / "resources.html" / Segment) { iri =>
      get { requestContext =>
        val params      = requestContext.request.uri.query().toMap
        val requestType = params.getOrElse("reqtype", "")
        val requestTask = requestType match {
          case "properties" =>
            for {
              userADM <- Authenticator.getUserADM(requestContext)
              resIri  <- RouteUtilZ.validateAndEscapeIri(iri, s"Invalid param resource IRI: $iri")
            } yield ResourceFullGetRequestV1(resIri, userADM)
          case other => ZIO.fail(BadRequestException(s"Invalid request type: $other"))
        }
        runHtmlRoute(requestTask, requestContext)
      }
    } ~ path("v1" / "properties" / Segment) { iri =>
      get { requestContext =>
        val requestMessage = for {
          userADM <- Authenticator.getUserADM(requestContext)
          resIri  <- RouteUtilZ.validateAndEscapeIri(iri, s"Invalid param resource IRI: $iri")
        } yield PropertiesGetRequestV1(resIri, userADM)
        runJsonRouteZ(requestMessage, requestContext)
      }
    } ~ path("v1" / "resources" / "label" / Segment) { iri =>
      put {
        entity(as[ChangeResourceLabelApiRequestV1]) { apiRequest => requestContext =>
          val requestMessage = for {
            userADM <- Authenticator.getUserADM(requestContext)
            resIri  <- RouteUtilZ.validateAndEscapeIri(iri, s"Invalid param resource IRI: $iri")
            label   <- RouteUtilV1.toSparqlEncodedString(apiRequest.label, s"Invalid label: '${apiRequest.label}'")
            uuid    <- RouteUtilZ.randomUuid()
          } yield ChangeResourceLabelRequestV1(resIri, label, userADM, uuid)
          runJsonRouteZ(requestMessage, requestContext)
        }
      }
    } ~ path("v1" / "graphdata" / Segment) { iri =>
      get {
        parameters("depth".as[Int].?) { depth => requestContext =>
          val requestMessage = for {
            userADM     <- Authenticator.getUserADM(requestContext)
            resourceIri <- RouteUtilZ.validateAndEscapeIri(iri, s"Invalid param resource IRI: $iri")
          } yield GraphDataGetRequestV1(resourceIri, depth.getOrElse(4), userADM)
          runJsonRouteZ(requestMessage, requestContext)
        }
      }
    } ~ path("v1" / "error" / Segment) { errorType =>
      get { requestContext =>
        val msg = if (errorType == "unitMsg") {
          UnexpectedMessageRequest()
        } else if (errorType == "iseMsg") {
          InternalServerExceptionMessageRequest()
        } else {
          InternalServerExceptionMessageRequest()
        }

        runJsonRoute(msg, requestContext)
      }
    } ~ path("v1" / "resources" / "xmlimport" / Segment) { projectId =>
      post {
        entity(as[String]) { xml => requestContext =>
          val requestMessage = for {
            userADM <- Authenticator.getUserADM(requestContext)

            _ <-
              ZIO
                .fail(
                  ForbiddenException(
                    "You are not logged in, and only a system administrator or project administrator can perform a bulk import"
                  )
                )
                .when(userADM.isAnonymousUser)
            _ <-
              ZIO
                .fail(
                  ForbiddenException(
                    s"You are logged in as ${userADM.email}, but only a system administrator or project administrator can perform a bulk import"
                  )
                )
                .when(!(userADM.permissions.isSystemAdmin || userADM.permissions.isProjectAdmin(projectId)))

            // Parse the submitted XML.
            rootElement: Elem = XML.loadString(xml)

            _ <- // Make sure that the root element is knoraXmlImport:resources.
              ZIO
                .fail(BadRequestException(s"Root XML element must be ${OntologyConstants.KnoraXmlImportV1.Resources}"))
                .when(rootElement.namespace + rootElement.label != OntologyConstants.KnoraXmlImportV1.Resources)

            // Get the default namespace of the submitted XML. This should be the Knora XML import
            // namespace corresponding to the main internal ontology used in the import.
            defaultNamespace = rootElement.getNamespace(null)

            // Validate the XML using XML schemas.
            _ <- validateImportXml(xml, defaultNamespace, userADM)

            // Make a CreateResourceFromXmlImportRequestV1 for each resource to be created.
            resourcesToCreate <- importXmlToCreateResourceRequests(rootElement)

            // Make a MultipleResourceCreateRequestV1 for the creation of all the resources.
            apiRequestID  <- RouteUtilZ.randomUuid()
            updateRequest <- makeMultiResourcesRequestMessage(resourcesToCreate, projectId, apiRequestID, userADM)
          } yield updateRequest

          runJsonRouteZ(requestMessage, requestContext)
        }
      }
    } ~ path("v1" / "resources" / "xmlimportschemas" / Segment) { internalOntologyIri =>
      get {
        // Get the prefix label of the specified internal ontology.
        val internalOntologySmartIri = UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[StringFormatter] {
          implicit stringFormatter =>
            ZIO.attempt {
              val ontologySmartIri: SmartIri = internalOntologyIri.toSmartIriWithErr(
                throw BadRequestException(s"Invalid internal project-specific ontology IRI: $internalOntologyIri")
              )
              if (!ontologySmartIri.isKnoraOntologyIri || ontologySmartIri.isKnoraBuiltInDefinitionIri) {
                throw BadRequestException(s"Invalid internal project-specific ontology IRI: $internalOntologyIri")
              }
              ontologySmartIri
            }
        })

        val internalOntologyPrefixLabel: String = internalOntologySmartIri.getLongPrefixLabel

        // Respond with a Content-Disposition header specifying the filename of the generated Zip file.
        respondWithHeader(
          `Content-Disposition`(
            ContentDispositionTypes.attachment,
            Map("filename" -> (internalOntologyPrefixLabel + "-xml-schemas.zip"))
          )
        ) { requestContext =>
          val httpResponseFuture: Future[HttpResponse] = UnsafeZioRun.runToFuture(
            for {
              userProfile        <- Authenticator.getUserADM(requestContext)
              schemaZipFileBytes <- generateSchemaZipFile(internalOntologyIri, userProfile)
            } yield HttpResponse(
              status = StatusCodes.OK,
              entity = HttpEntity(bytes = schemaZipFileBytes)
            )
          )
          requestContext.complete(httpResponseFuture)
        }
      }
    }
  }

  private def makeCreateResourceRequestMessage(
    apiRequest: CreateResourceApiRequestV1,
    userADM: UserADM
  ): ZIO[MessageRelay with KnoraProjectRepo with StringFormatter, Throwable, ResourceCreateRequestV1] = {
    val projectIri = apiRequest.project_id
    for {
      resourceTypeIri <-
        RouteUtilZ.validateAndEscapeIri(apiRequest.restype_id, s"Invalid resource IRI: ${apiRequest.restype_id}")
      label   <- RouteUtilV1.toSparqlEncodedString(apiRequest.label, s"Invalid label: '${apiRequest.label}'")
      project <- getProjectByIri(projectIri)
      file <- apiRequest.file match {
                case Some(filename) =>
                  for {
                    tempFilePath <- ZIO.service[StringFormatter].map(_.makeSipiTempFilePath(filename))
                    fileMetadataResponse <-
                      ZIO.serviceWithZIO[MessageRelay](
                        _.ask[GetFileMetadataResponse](GetFileMetadataRequest(tempFilePath, userADM))
                      )
                    fileValue <- ZIO.attempt(makeFileValue(filename, fileMetadataResponse, project.shortcode))
                  } yield Some(fileValue)
                case None => ZIO.none
              }
      valuesToBeCreated <- valuesToCreate(apiRequest.properties, acceptStandoffLinksToClientIDs = false, userADM)
      uuid              <- RouteUtilZ.randomUuid()
    } yield ResourceCreateRequestV1(resourceTypeIri, label, valuesToBeCreated, file, projectIri, userADM, uuid)
  }

  private def createOneResourceRequestFromXmlImport(
    resourceRequest: CreateResourceFromXmlImportRequestV1,
    projectShortcode: String,
    userProfile: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, OneOfMultipleResourceCreateRequestV1] =
    for {
      valuesToBeCreated <- valuesToCreate(
                             resourceRequest.properties,
                             acceptStandoffLinksToClientIDs = true,
                             userProfile
                           )

      convertedFile <- resourceRequest.file match {
                         case Some(filename) =>
                           for {
                             tempFile <- ZIO.service[StringFormatter].map(_.makeSipiTempFilePath(filename))
                             fileMetadataResponse <-
                               ZIO.serviceWithZIO[MessageRelay](
                                 _.ask[GetFileMetadataResponse](GetFileMetadataRequest(tempFile, userProfile))
                               )
                           } yield Some(makeFileValue(filename, fileMetadataResponse, projectShortcode))
                         case None => ZIO.none
                       }
      label <- RouteUtilV1.toSparqlEncodedString(
                 resourceRequest.label,
                 s"The resource label is invalid: '${resourceRequest.label}'"
               )
    } yield OneOfMultipleResourceCreateRequestV1(
      resourceRequest.restype_id,
      resourceRequest.client_id,
      label,
      valuesToBeCreated,
      convertedFile,
      resourceRequest.creationDate
    )

  private def makeMultiResourcesRequestMessage(
    resourceRequest: Seq[CreateResourceFromXmlImportRequestV1],
    projectIri: IRI,
    apiRequestID: UUID,
    userProfile: UserADM
  ): ZIO[StringFormatter with MessageRelay with KnoraProjectRepo, Throwable, MultipleResourceCreateRequestV1] = {
    val duplicateClientIDs = resourceRequest.map(_.client_id).groupBy(identity).collect {
      case (clientID, occurrences) if occurrences.size > 1 => clientID
    }
    val duplicatesErrorMsg =
      s"One or more client resource IDs were used for multiple resources: ${duplicateClientIDs.mkString(", ")}"
    for {
      _       <- ZIO.fail(BadRequestException(duplicatesErrorMsg)).when(duplicateClientIDs.nonEmpty)
      project <- RouteUtilV1.getProjectByIri(projectIri)
      resourcesToCreate <-
        ZIO.foreach(resourceRequest)(createOneResourceRequestFromXmlImport(_, project.shortcode, userProfile))
    } yield MultipleResourceCreateRequestV1(resourcesToCreate, projectIri, userProfile, apiRequestID)
  }

  private def makeResourceDeleteMessage(
    resIri: IRI,
    deleteComment: Option[String],
    userADM: UserADM
  ): ZIO[StringFormatter, BadRequestException, ResourceDeleteRequestV1] =
    for {
      resourceIri <- RouteUtilZ.validateAndEscapeIri(resIri, s"Invalid resource IRI: $resIri")
      deleteComment <-
        ZIO.foreach(deleteComment)(comment =>
          RouteUtilV1.toSparqlEncodedString(comment, s"Invalid comment: '$comment'")
        )
      uuid <- RouteUtilZ.randomUuid()
    } yield ResourceDeleteRequestV1(resourceIri, deleteComment, userADM, uuid)

  /**
   * Given the IRI the main internal ontology to be used in an XML import, recursively gets instances of
   * [[NamedGraphEntityInfoV1]] for that ontology, for `knora-base`, and for any other ontologies containing
   * classes used in object class constraints in the main ontology.
   *
   * @param mainOntologyIri the IRI of the main ontology used in the XML import.
   * @param userProfile     the profile of the user making the request.
   * @return a map of internal ontology IRIs to [[NamedGraphEntityInfoV1]] objects.
   */
  private def getNamedGraphInfos(
    mainOntologyIri: IRI,
    userProfile: UserADM
  ): ZIO[IriConverter with MessageRelay, Throwable, Map[IRI, NamedGraphEntityInfoV1]] = {

    def toSmartIri(list: Iterable[IRI]): ZIO[IriConverter, Throwable, Iterable[SmartIri]] =
      ZIO.serviceWithZIO[IriConverter](converter => ZIO.foreach(list)(converter.asSmartIri))

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
    def getNamedGraphInfosRec(
      initialOntologyIri: IRI,
      intermediateResults: Map[IRI, NamedGraphEntityInfoV1],
      userProfile: UserADM
    ): ZIO[IriConverter with MessageRelay, Throwable, Map[IRI, NamedGraphEntityInfoV1]] = {
      assert(intermediateResults.contains(KnoraBase.KnoraBaseOntologyIri))

      for {

        // Get a NamedGraphEntityInfoV1 listing the IRIs of the classes and properties defined in the initial ontology.
        initialNamedGraphInfo <-
          MessageRelay.ask[NamedGraphEntityInfoV1](NamedGraphEntityInfoRequestV1(initialOntologyIri, userProfile))

        // Get details about those classes and properties.
        entityInfoResponse <- MessageRelay.ask[EntityInfoGetResponseV1](
                                EntityInfoGetRequestV1(
                                  initialNamedGraphInfo.resourceClasses,
                                  initialNamedGraphInfo.propertyIris,
                                  userProfile
                                )
                              )

        // Look at the base classes of all the resource classes in the initial ontology. Make a set of
        // the ontologies containing the definitions of those classes, not including including the initial ontology itself
        // or any other ontologies we've already looked at.
        subclassIris = entityInfoResponse.resourceClassInfoMap.values.flatMap(_.subClassOf)
        subclassOntologyIris <-
          toSmartIri(subclassIris).map(_.filter(_.isKnoraDefinitionIri).map(_.getOntologyFromEntity.toIri))
        ontologyIrisFromBaseClasses = subclassOntologyIris.toSet -- intermediateResults.keySet - initialOntologyIri

        // Look at the properties that have cardinalities in the resource classes in the initial ontology.
        // Make a set of the ontologies containing the definitions of those properties, not including the initial ontology itself
        // or any other ontologies we've already looked at.
        propertyIris                  = entityInfoResponse.resourceClassInfoMap.values.flatMap(_.knoraResourceCardinalities.keySet)
        propertyOntologyIris         <- toSmartIri(propertyIris).map(_.map(_.getOntologyFromEntity.toIri))
        ontologyIrisFromCardinalities = propertyOntologyIris.toSet -- intermediateResults.keySet - initialOntologyIri

        // Look at the object class constraints of the properties in the initial ontology. Make a set of the ontologies containing those classes,
        // not including the initial ontology itself or any other ontologies we've already looked at.
        propertyObjectClassConstraint =
          entityInfoResponse.propertyInfoMap.values.flatMap(_.getPredicateObject(ObjectClassConstraint))
        propertyObjectClassConstraintOntologyIris <-
          toSmartIri(propertyObjectClassConstraint).map(_.map(_.getOntologyFromEntity.toIri))
        ontologyIrisFromObjectClassConstraints =
          propertyObjectClassConstraintOntologyIris.toSet -- intermediateResults.keySet - initialOntologyIri

        // Make a set of all the ontologies referenced by the initial ontology.
        referencedOntologies: Set[IRI] =
          ontologyIrisFromBaseClasses ++ ontologyIrisFromCardinalities ++ ontologyIrisFromObjectClassConstraints

        // Recursively get NamedGraphEntityInfoV1 instances for each of those ontologies.
        initial: ZIO[MessageRelay with IriConverter, Throwable, Map[IRI, NamedGraphEntityInfoV1]] =
          ZIO.succeed(intermediateResults + (initialOntologyIri -> initialNamedGraphInfo))
        lastResults <- referencedOntologies.foldLeft(initial) { case (accZ, ontologyIri) =>
                         for {
                           acc <- accZ
                           // Has a previous recursion already dealt with this ontology?
                           nextResults <-
                             if (acc.contains(ontologyIri)) {
                               // Yes, so there's no need to get it again.
                               ZIO.succeed(acc)
                             } else {
                               // No. Recursively get it and the ontologies it depends on.
                               getNamedGraphInfosRec(
                                 initialOntologyIri = ontologyIri,
                                 intermediateResults = acc,
                                 userProfile = userProfile
                               )
                             }
                         } yield acc ++ nextResults
                       }
      } yield lastResults
    }

    for {
      // Get a NamedGraphEntityInfoV1 for the knora-base ontology.
      knoraBaseGraphEntityInfo <- MessageRelay.ask[NamedGraphEntityInfoV1](
                                    NamedGraphEntityInfoRequestV1(KnoraBase.KnoraBaseOntologyIri, userProfile)
                                  )

      // Recursively get NamedGraphEntityInfoV1 instances for the main ontology to be used in the XML import,
      // as well as any other project-specific ontologies it depends on.
      graphInfos <- getNamedGraphInfosRec(
                      mainOntologyIri,
                      Map(KnoraBase.KnoraBaseOntologyIri -> knoraBaseGraphEntityInfo),
                      userProfile
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
  private def generateSchemasFromOntologies(
    internalOntologyIri: IRI,
    userProfile: UserADM
  ): ZIO[IriConverter with MessageRelay with StringFormatter, Throwable, XmlImportSchemaBundleV1] = {

    /**
     * Called by the schema generation template to get the prefix label for an internal ontology
     * entity IRI. The schema generation template gets these IRIs from resource cardinalities
     * and property object class constraints, which we get from the ontology responder.
     *
     * @param internalEntityIri an internal ontology entity IRI.
     * @return the prefix label that Knora uses to refer to the ontology.
     */
    def getNamespacePrefixLabel(internalEntityIri: IRI): ZIO[IriConverter, Throwable, IRI] =
      IriConverter
        .asSmartIri(internalEntityIri)
        .map(_.getLongPrefixLabel)
        .map { prefixLabel =>
          // If the schema generation template asks for the prefix label of something in knora-base, return
          // the prefix label of the Knora XML import v1 namespace instead.
          if (prefixLabel == KnoraBase.KnoraBaseOntologyLabel) {
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
    def getEntityName(internalEntityIri: IRI): ZIO[IriConverter with StringFormatter, Throwable, IRI] =
      IriConverter.asSmartIri(internalEntityIri).map(_.getEntityName)

    for {
      // Get a NamedGraphEntityInfoV1 for each ontology that we need to generate an XML schema for.
      namedGraphInfos <- getNamedGraphInfos(internalOntologyIri, userProfile)

      // Get information about the resource classes and properties in each ontology.
      entityInfoResponseFutures =
        namedGraphInfos.map { case (ontologyIri: IRI, namedGraphInfo: NamedGraphEntityInfoV1) =>
          for {
            entityInfoResponse <- MessageRelay
                                    .ask[EntityInfoGetResponseV1](
                                      EntityInfoGetRequestV1(
                                        resourceClassIris = namedGraphInfo.resourceClasses,
                                        propertyIris = namedGraphInfo.propertyIris,
                                        userProfile = userProfile
                                      )
                                    )
          } yield ontologyIri -> entityInfoResponse
        }
      entityInfoResponsesMap <- ZIO.collectAll(entityInfoResponseFutures).map(_.toMap)

      // Collect all the property definitions in a single Map. Since any schema could use any property, we will
      // pass this Map to the schema generation template for every schema.
      propertyInfoMap: Map[IRI, PropertyInfoV1] = entityInfoResponsesMap.values.flatMap(_.propertyInfoMap).toMap

      // Make a map of internal ontology IRIs to XmlImportNamespaceInfoV1 objects describing the XML namespace
      // of each schema to be generated. Don't generate a schema for knora-base, because the built-in Knora
      // types are specified in the handwritten standard Knora XML import v1 schema.
      schemasToGenerate <-
        ZIO
          .foreach(namedGraphInfos.keySet - OntologyConstants.KnoraBase.KnoraBaseOntologyIri) { ontologyIri =>
            for {
              ontologySmartIri <- IriConverter.asSmartIri(ontologyIri)
              namespaceInfo <-
                ZIO.service[StringFormatter].map(_.internalOntologyIriToXmlNamespaceInfoV1(ontologySmartIri))
            } yield ontologyIri -> namespaceInfo
          }
          .map(_.toMap)

      // Make an XmlImportNamespaceInfoV1 for the standard Knora XML import v1 schema's namespace.
      knoraXmlImportSchemaNamespaceInfo: XmlImportNamespaceInfoV1 = XmlImportNamespaceInfoV1(
                                                                      namespace =
                                                                        OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespaceV1,
                                                                      prefixLabel =
                                                                        OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel
                                                                    )

      // Read the standard Knora XML import v1 schema from a file.
      knoraXmlImportSchemaXml: String =
        FileUtil.readTextResource(
          OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel + ".xsd"
        )

      // Construct an XmlImportSchemaV1 for the standard Knora XML import v1 schema.
      knoraXmlImportSchema: XmlImportSchemaV1 = XmlImportSchemaV1(
                                                  namespaceInfo = knoraXmlImportSchemaNamespaceInfo,
                                                  schemaXml = knoraXmlImportSchemaXml
                                                )

      // Generate a schema for each project-specific ontology.
      generatedSchemas: Map[IRI, XmlImportSchemaV1] = schemasToGenerate.map { case (ontologyIri, namespaceInfo) =>
                                                        // Each schema imports all the other generated schemas, plus the standard Knora XML import v1 schema.
                                                        // Sort the imports to make schema generation deterministic.
                                                        val importedNamespaceInfos: Seq[XmlImportNamespaceInfoV1] =
                                                          (schemasToGenerate - ontologyIri).values.toVector.sortBy {
                                                            importedNamespaceInfo =>
                                                              importedNamespaceInfo.prefixLabel
                                                          } :+ knoraXmlImportSchemaNamespaceInfo

                                                        // Generate the schema using a Twirl template.
                                                        val unformattedSchemaXml =
                                                          org.knora.webapi.messages.twirl.xsd.v1.xml
                                                            .xmlImport(
                                                              targetNamespaceInfo = namespaceInfo,
                                                              importedNamespaces = importedNamespaceInfos,
                                                              knoraXmlImportNamespacePrefixLabel =
                                                                OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel,
                                                              resourceClassInfoMap = entityInfoResponsesMap(
                                                                ontologyIri
                                                              ).resourceClassInfoMap,
                                                              propertyInfoMap = propertyInfoMap,
                                                              getNamespacePrefixLabel = internalEntityIri =>
                                                                UnsafeZioRun.runOrThrow(
                                                                  getNamespacePrefixLabel(internalEntityIri)
                                                                ),
                                                              getEntityName = internalEntityIri =>
                                                                UnsafeZioRun.runOrThrow(
                                                                  getEntityName(internalEntityIri)
                                                                )
                                                            )
                                                            .toString()
                                                            .trim

                                                        // Parse the generated XML schema.
                                                        val parsedSchemaXml =
                                                          try {
                                                            XML.loadString(unformattedSchemaXml)
                                                          } catch {
                                                            case parseEx: org.xml.sax.SAXParseException =>
                                                              throw AssertionException(
                                                                s"Generated XML schema for namespace ${namespaceInfo.namespace} is not valid XML. Please report this as a bug.",
                                                                parseEx,
                                                                logger
                                                              )
                                                          }

                                                        // Format the generated XML schema nicely.
                                                        val formattedSchemaXml =
                                                          xmlPrettyPrinter.format(parsedSchemaXml)

                                                        // Wrap it in an XmlImportSchemaV1 object along with its XML namespace information.
                                                        val schema = XmlImportSchemaV1(
                                                          namespaceInfo = namespaceInfo,
                                                          schemaXml = formattedSchemaXml
                                                        )

                                                        namespaceInfo.namespace -> schema
                                                      }

      // The schema bundle to be returned contains the generated schemas plus the standard Knora XML import v1 schema.
      allSchemasForBundle: Map[IRI, XmlImportSchemaV1] =
        generatedSchemas + (OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespaceV1 -> knoraXmlImportSchema)
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
  private def generateSchemaZipFile(
    internalOntologyIri: IRI,
    userProfile: UserADM
  ): ZIO[IriConverter with MessageRelay with StringFormatter, Throwable, Array[Byte]] =
    for {
      // Generate a bundle of XML schemas.
      schemaBundle <- generateSchemasFromOntologies(internalOntologyIri, userProfile)

      // Generate the contents of the Zip file: a Map of file names to file contents (byte arrays).
      zipFileContents: Map[String, Array[Byte]] = schemaBundle.schemas.values.map { schema: XmlImportSchemaV1 =>
                                                    val schemaFilename: String =
                                                      schema.namespaceInfo.prefixLabel + ".xsd"
                                                    val schemaXmlBytes: Array[Byte] =
                                                      schema.schemaXml.getBytes(StandardCharsets.UTF_8)
                                                    schemaFilename -> schemaXmlBytes
                                                  }.toMap
      byteArray <- ZIO.attempt(FileUtil.createZipFileBytes(zipFileContents))
    } yield byteArray

  /**
   * Validates bulk import XML using project-specific XML schemas and the Knora XML import schema v1.
   *
   * @param xml              the XML to be validated.
   * @param defaultNamespace the default namespace of the submitted XML. This should be the Knora XML import
   *                         namespace corresponding to the main internal ontology used in the import.
   * @param userADM          the profile of the user making the request.
   * @return a `Future` containing `()` if successful, otherwise a failed future.
   */
  private def validateImportXml(
    xml: String,
    defaultNamespace: IRI,
    userADM: UserADM
  ): ZIO[IriConverter with MessageRelay with StringFormatter, Throwable, Unit] =
    // Convert the default namespace of the submitted XML to an internal ontology IRI. This should be the
    // IRI of the main ontology used in the import.

    for {
      mainOntologyIri <-
        ZIO.serviceWithZIO[StringFormatter](sf =>
          ZIO.attempt(
            sf.xmlImportNamespaceToInternalOntologyIriV1(
              defaultNamespace,
              throw BadRequestException(s"Invalid XML import namespace: $defaultNamespace")
            )
          )
        )
      // Generate a bundle of XML schemas for validating the submitted XML.
      schemaBundle <- generateSchemasFromOntologies(mainOntologyIri.toString, userADM)
      _            <- validateXml(xml, schemaBundle)
    } yield ()

  private def validateXml(xml: IRI, schemaBundle: XmlImportSchemaBundleV1) =
    ZIO.attempt(getXmlValidator(schemaBundle).validate(new StreamSource(new StringReader(xml)))).mapError { e =>
      BadRequestException(s"XML import did not pass XML schema validation: $e")
    }

  private def getXmlValidator(schemaBundle: XmlImportSchemaBundleV1) = {
    // Make a javax.xml.validation.SchemaFactory for instantiating XML schemas.
    val schemaFactory: SchemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    // Tell the SchemaFactory to find additional schemas using our SchemaBundleResolver, which gets them
    // from the XmlImportSchemaBundleV1 we generated.
    schemaFactory.setResourceResolver(new SchemaBundleResolver(schemaBundle))

    // Use the SchemaFactory to instantiate a javax.xml.validation.Schema representing the main schema in
    // the bundle.
    val mainSchemaXml: String  = schemaBundle.schemas(schemaBundle.mainNamespace).schemaXml
    val schemaInstance: Schema = schemaFactory.newSchema(new StreamSource(new StringReader(mainSchemaXml)))
    schemaInstance.newValidator()
  }

  private def xmlImportElementNameToInternalOntologyIriV1(
    namespace: String,
    elementLabel: String,
    errorFun: => Nothing
  ): ZIO[StringFormatter, Throwable, IRI] =
    ZIO.serviceWithZIO[StringFormatter] { sf =>
      ZIO.attempt(sf.xmlImportElementNameToInternalOntologyIriV1(namespace, elementLabel, errorFun))
    }

  private def toPropertyIriFromOtherOntologyInXmlImport(
    prefixLabelAndLocalName: String
  ): ZIO[StringFormatter, Throwable, Option[IRI]] =
    ZIO.serviceWithZIO[StringFormatter] { sf =>
      ZIO.attempt(sf.toPropertyIriFromOtherOntologyInXmlImport(prefixLabelAndLocalName))
    }

  /**
   * Converts parsed import XML into a sequence of [[CreateResourceFromXmlImportRequestV1]] for each resource
   * described in the XML.
   *
   * @param rootElement the root element of an XML document describing multiple resources to be created.
   * @return Seq[CreateResourceFromXmlImportRequestV1] a collection of resource creation requests.
   */
  private def importXmlToCreateResourceRequests(
    rootElement: Elem
  ): ZIO[StringFormatter, Throwable, Seq[CreateResourceFromXmlImportRequestV1]] = ZIO.collectAll {
    rootElement.head.child
      .filter(node => node.label != "#PCDATA")
      .map { resourceNode =>
        // Get the client's unique ID for the resource.
        val clientIDForResource: String = (resourceNode \ "@id").toString

        // Get the optional resource creation date.
        val creationDate: Option[Instant] = resourceNode
          .attribute("creationDate")
          .map(creationDateNode =>
            ValuesValidator
              .xsdDateTimeStampToInstant(creationDateNode.text)
              .getOrElse(throw BadRequestException(s"Invalid resource creation date: ${creationDateNode.text}"))
          )

        // Convert the XML element's label and namespace to an internal resource class IRI.

        val elementNamespace: String = resourceNode.getNamespace(resourceNode.prefix)

        for {
          restype_id <- ZIO.serviceWithZIO[StringFormatter](sf =>
                          ZIO.attempt(
                            sf.xmlImportElementNameToInternalOntologyIriV1(
                              elementNamespace,
                              resourceNode.label,
                              throw BadRequestException(s"Invalid XML namespace: $elementNamespace")
                            )
                          )
                        )

          // Get the child elements of the resource element.
          childElements: Seq[Node] = resourceNode.child.filterNot(_.label == "#PCDATA")

          // The label must be the first child element of the resource element.
          errorLabelNotPresent =
            s"Resource '$clientIDForResource' contains no ${OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel}:label element"
          resourceLabel <- ZIO
                             .fromOption(childElements.headOption)
                             .mapBoth(_ => BadRequestException(errorLabelNotPresent), _.text)

          childElementsAfterLabel = childElements.tail

          // Get the name of the resource's file, if any. This represents a file that in Sipi's temporary storage.
          // If provided, it must be the second child element of the resource element.
          file: Option[String] = childElementsAfterLabel.headOption match {
                                   case Some(secondChildElem) =>
                                     if (secondChildElem.label == "file") {
                                       Some(secondChildElem.attribute("filename").get.text)
                                     } else {
                                       None
                                     }

                                   case None => None
                                 }

          // Any remaining child elements of the resource element represent property values.
          propertyElements = file.map(_ => childElementsAfterLabel.tail).getOrElse(childElementsAfterLabel)

          // Traverse the property value elements. This produces a sequence in which the same property IRI
          // can occur multiple times, once for each value.
          propertiesWithValues <- ZIO.foreach(propertyElements) { propertyNode =>
                                    for {
                                      propertyIriMaybe <-
                                        toPropertyIriFromOtherOntologyInXmlImport(propertyNode.label)
                                      propertyIri <- ZIO.fromOption(propertyIriMaybe).orElse {
                                                       val propertyNodeNamespace =
                                                         propertyNode.getNamespace(propertyNode.prefix)
                                                       xmlImportElementNameToInternalOntologyIriV1(
                                                         propertyNodeNamespace,
                                                         propertyNode.label,
                                                         throw BadRequestException(
                                                           s"Invalid XML namespace: $propertyNodeNamespace"
                                                         )
                                                       )
                                                     }
                                      valueNodes: Seq[Node] = propertyNode.child.filterNot(_.label == "#PCDATA")
                                      value <-
                                        if (valueNodes.size == 1 && valueNodes.head.attribute("knoraType").isDefined) {
                                          knoraDataTypeXml(valueNodes.head)
                                        } else {
                                          knoraDataTypeXml(propertyNode)
                                        }
                                    } yield propertyIri -> value
                                  }

          // Group the values by property IRI.
          groupedPropertiesWithValues =
            propertiesWithValues.groupBy { case (propertyIri: IRI, _) => propertyIri }.map {
              case (propertyIri: IRI, resultsForProperty: Seq[(IRI, CreateResourceValueV1)]) =>
                propertyIri -> resultsForProperty.map { case (_, propertyValue: CreateResourceValueV1) =>
                  propertyValue
                }
            }

        } yield CreateResourceFromXmlImportRequestV1(
          restype_id = restype_id,
          client_id = clientIDForResource,
          label = resourceLabel,
          properties = groupedPropertiesWithValues,
          file = file,
          creationDate = creationDate
        )
      }
      .toSeq
  }

  /**
   * Given an XML element representing a property value in an XML import, returns a [[CreateResourceValueV1]]
   * describing the value to be created.
   *
   * @param node the XML element.
   * @return a [[CreateResourceValueV1]] requesting the creation of the value described by the element.
   */
  private def knoraDataTypeXml(node: Node): ZIO[StringFormatter, Throwable, CreateResourceValueV1] = {
    val knoraType: Seq[Node] = node
      .attribute("knoraType")
      .getOrElse(throw BadRequestException(s"Attribute 'knoraType' missing in element '${node.label}'"))
    val elementValue = node.text

    if (knoraType.nonEmpty) {
      val language = node.attribute("lang").map(_.head.toString)
      knoraType.toString match {
        case "richtext_value" =>
          val maybeMappingID: Option[Seq[Node]] = node.attributes.get("mapping_id").map(_.toSeq)

          maybeMappingID match {
            case Some(mappingID) =>
              for {
                mappingIri <- RouteUtilZ.validateAndEscapeIri(
                                mappingID.toString(),
                                s"Invalid mapping ID in element '${node.label}: '$mappingID"
                              )
                childElements = node.child.filterNot(_.label == "#PCDATA")
                result <- if (childElements.nonEmpty) {
                            val embeddedXmlRootNode = childElements.head
                            val embeddedXmlDoc =
                              """<?xml version="1.0" encoding="UTF-8"?>""" + embeddedXmlRootNode.toString
                            ZIO.succeed(
                              CreateResourceValueV1(
                                richtext_value = Some(
                                  CreateRichtextV1(
                                    utf8str = None,
                                    language = language,
                                    xml = Some(embeddedXmlDoc),
                                    mapping_id = Some(mappingIri)
                                  )
                                )
                              )
                            )
                          } else {
                            ZIO.fail(
                              BadRequestException(
                                s"Element '${node.label}' provides a mapping_id, but its content is not XML"
                              )
                            )
                          }
              } yield result

            case None =>
              // We don't escape the input string here, because it will be escaped by valuesToCreate().
              ZIO.succeed(
                CreateResourceValueV1(richtext_value =
                  Some(CreateRichtextV1(utf8str = Some(elementValue), language = language))
                )
              )
          }

        case "link_value" =>
          ZIO
            .fromOption(node.attribute("linkType").flatMap(_.headOption))
            .mapBoth(_ => BadRequestException(s"Attribute 'linkType' missing in element '${node.label}'"), _.text)
            .flatMap { linkType =>
              ZIO
                .fromOption(node.attribute("target").flatMap(_.headOption))
                .orElseFail(BadRequestException(s"Attribute 'target' missing in element '${node.label}'"))
                .flatMap { targetNode =>
                  val target = targetNode.text
                  linkType match {
                    case "ref" => ZIO.succeed(CreateResourceValueV1(link_to_client_id = Some(target)))
                    case "iri" =>
                      RouteUtilZ
                        .validateAndEscapeIri(target, s"Invalid IRI in element '${node.label}': '$target'")
                        .map(it => CreateResourceValueV1(link_value = Some(it)))
                    case other =>
                      ZIO.fail(
                        BadRequestException(
                          s"Unrecognised value '$other' in attribute 'linkType' of element '${node.label}'"
                        )
                      )

                  }
                }
            }

        case "int_value" =>
          ZIO
            .fromOption(ValuesValidator.validateInt(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid integer value in element '${node.label}: '$elementValue'"),
              it => CreateResourceValueV1(int_value = Some(it))
            )

        case "decimal_value" =>
          ZIO
            .fromOption(ValuesValidator.validateBigDecimal(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid decimal value in element '${node.label}: '$elementValue'"),
              it => CreateResourceValueV1(decimal_value = Some(it))
            )

        case "boolean_value" =>
          ZIO
            .fromOption(ValuesValidator.validateBoolean(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid boolean value in element '${node.label}: '$elementValue'"),
              it => CreateResourceValueV1(boolean_value = Some(it))
            )

        case "uri_value" =>
          RouteUtilZ
            .validateAndEscapeIri(elementValue, s"Invalid URI value in element '${node.label}: '$elementValue'")
            .map(it => CreateResourceValueV1(uri_value = Some(it)))

        case "date_value" =>
          ZIO
            .fromOption(ValuesValidator.validateDate(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid date value in element '${node.label}: '$elementValue'"),
              it => CreateResourceValueV1(date_value = Some(it))
            )

        case "color_value" =>
          ZIO
            .fromOption(ValuesValidator.validateColor(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid date value in element '${node.label}: '$elementValue'"),
              it => CreateResourceValueV1(color_value = Some(it))
            )

        case "geom_value" =>
          ZIO
            .fromOption(ValuesValidator.validateGeometryString(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid geometry value in element '${node.label}: '$elementValue'"),
              it => CreateResourceValueV1(geom_value = Some(it))
            )

        case "hlist_value" =>
          RouteUtilZ
            .validateAndEscapeIri(elementValue, "Invalid hlist value in element '${node.label}: '$elementValue'")
            .map(it => CreateResourceValueV1(hlist_value = Some(it)))

        case "interval_value" =>
          Try(elementValue.split(",")) match {
            case Success(timeVals) =>
              if (timeVals.length != 2) {
                ZIO.fail(BadRequestException(s"Invalid interval value in element '${node.label}: '$elementValue'"))
              } else {
                ZIO
                  .foreach(timeVals) { timeVal =>
                    ZIO
                      .fromOption(ValuesValidator.validateBigDecimal(timeVal))
                      .orElseFail(BadRequestException(s"Invalid decimal value in element '${node.label}: '$timeVal'"))
                  }
                  .map(it => CreateResourceValueV1(interval_value = Some(it)))
              }
            case Failure(_) =>
              ZIO.fail(BadRequestException(s"Invalid interval value in element '${node.label}: '$elementValue'"))
          }

        case "time_value" =>
          ZIO
            .fromOption(ValuesValidator.xsdDateTimeStampToInstant(elementValue))
            .mapBoth(
              _ => BadRequestException(s"Invalid timestamp in element '${node.label}': $elementValue"),
              timeStamp => CreateResourceValueV1(time_value = Some(timeStamp.toString))
            )

        case "geoname_value" =>
          ZIO.succeed(CreateResourceValueV1(geoname_value = Some(elementValue)))
        case other => ZIO.fail(BadRequestException(s"Invalid 'knoraType' in element '${node.label}': '$other'"))
      }
    } else {
      ZIO.fail(BadRequestException(s"Attribute 'knoraType' missing in element '${node.label}'"))
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
  private class SchemaBundleResolver(schemaBundle: XmlImportSchemaBundleV1) extends LSResourceResolver {
    private val contents: Map[IRI, Array[Byte]] = schemaBundle.schemas.map { case (namespace, schema) =>
      namespace -> schema.schemaXml.getBytes(StandardCharsets.UTF_8)
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

    override def resolveResource(
      `type`: String,
      namespaceURI: String,
      publicId: String,
      systemId: String,
      baseURI: String
    ): LSInput =
      new ByteArrayLSInput(contents(namespaceURI))
  }

  private def makeResourceRequestMessage(
    resIri: String,
    resinfo: Boolean,
    requestType: String,
    userADM: UserADM
  ): ZIO[StringFormatter, BadRequestException, ResourcesResponderRequestV1] =
    for {
      validResIri <- RouteUtilZ.validateAndEscapeIri(resIri, s"Invalid resource IRI: $resIri")
      request <- requestType match {
                   case "info"    => ZIO.succeed(ResourceInfoGetRequestV1(validResIri, userADM))
                   case "rights"  => ZIO.succeed(ResourceRightsGetRequestV1(validResIri, userADM))
                   case "context" => ZIO.succeed(ResourceContextGetRequestV1(validResIri, userADM, resinfo))
                   case ""        => ZIO.succeed(ResourceFullGetRequestV1(validResIri, userADM))
                   case other     => ZIO.fail(BadRequestException(s"Invalid request type: $other"))
                 }
    } yield request

  def valuesToCreate(
    properties: Map[IRI, Seq[CreateResourceValueV1]],
    acceptStandoffLinksToClientIDs: Boolean,
    userProfile: UserADM
  ): ZIO[MessageRelay with StringFormatter, Throwable, Map[IRI, Seq[CreateValueV1WithComment]]] =
    ZIO.foreach(properties) { case (k, v) =>
      for {
        tk <- RouteUtilZ.validateAndEscapeIri(k, s"Invalid property IRI $k")
        tv <- toCreateResourceValues(v, acceptStandoffLinksToClientIDs, userProfile)
      } yield tk -> tv
    }

  private def toCreateResourceValues(
    seq: Seq[CreateResourceValueV1],
    acceptStandoffLinksToClientIDs: Boolean,
    userProfile: UserADM
  ): ZIO[StringFormatter with MessageRelay, Throwable, Seq[CreateValueV1WithComment]] =
    ZIO.foreach(seq) { (givenValue: CreateResourceValueV1) =>
      givenValue.getValueClassIri match {
        case KnoraBase.TextValue     => mapTextValue(acceptStandoffLinksToClientIDs, userProfile, givenValue)
        case KnoraBase.LinkValue     => mapLinkValue(givenValue)
        case KnoraBase.IntValue      => mapIntValue(givenValue)
        case KnoraBase.DecimalValue  => mapDecimalValue(givenValue)
        case KnoraBase.BooleanValue  => mapBooleanValue(givenValue)
        case KnoraBase.UriValue      => mapUriValue(givenValue)
        case KnoraBase.DateValue     => mapDateValue(givenValue)
        case KnoraBase.ColorValue    => mapColorValue(givenValue)
        case KnoraBase.GeomValue     => mapGeomValue(givenValue)
        case KnoraBase.ListValue     => mapListValue(givenValue)
        case KnoraBase.IntervalValue => mapIntervalValue(givenValue)
        case KnoraBase.TimeValue     => mapTimeValue(givenValue)
        case KnoraBase.GeonameValue  => mapGeonameValue(givenValue)
        case _                       => ZIO.fail(BadRequestException(s"No value submitted"))
      }
    }

  private def mapGeonameValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.geoname_value)
      .mapBoth(
        _ => BadRequestException("geoname_value is missing"),
        it => CreateValueV1WithComment(GeonameValueV1(it), givenValue.comment)
      )

  private def mapTimeValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.time_value)
      .orElseFail(BadRequestException("time_value is missing"))
      .flatMap { timeValStr =>
        ZIO
          .fromOption(ValuesValidator.xsdDateTimeStampToInstant(timeValStr))
          .mapBoth(_ => BadRequestException(s"Invalid timestamp: $timeValStr"), TimeValueV1)
          .map(CreateValueV1WithComment(_, givenValue.comment))
      }

  private def mapIntervalValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.interval_value)
      .orElseFail(BadRequestException("interval_value is missing"))
      .flatMap { timeVals =>
        if (timeVals.length != 2)
          ZIO.fail(BadRequestException("parameters for interval_value invalid"))
        else
          ZIO.attempt(
            CreateValueV1WithComment(IntervalValueV1(timeVals.head, timeVals(1)), givenValue.comment)
          )
      }

  private def mapListValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.hlist_value)
      .orElseFail(BadRequestException("hlist_value is missing"))
      .flatMap { it =>
        RouteUtilZ
          .validateAndEscapeIri(it, s"Invalid list value IRI: $it")
          .map(HierarchicalListValueV1)
          .map(CreateValueV1WithComment(_, givenValue.comment))
      }

  private def mapGeomValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.geom_value)
      .orElseFail(BadRequestException("geom_value is missing"))
      .flatMap(it =>
        RouteUtilZ
          .validateAndEscapeIri(it, s"Invalid geometry value: $it")
          .map(GeomValueV1)
          .map(CreateValueV1WithComment(_, givenValue.comment))
      )

  private def mapColorValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.color_value)
      .orElseFail(BadRequestException("color_value is missing"))
      .flatMap(it =>
        ZIO
          .fromOption(ValuesValidator.validateColor(it))
          .mapBoth(_ => BadRequestException(s"Invalid color value: $it"), ColorValueV1)
          .map(CreateValueV1WithComment(_, givenValue.comment))
      )

  private def mapDateValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.date_value)
      .orElseFail(BadRequestException("date_value is missing"))
      .flatMap(it =>
        ZIO
          .attempt(DateUtilV1.createJDNValueV1FromDateString(it))
          .map(CreateValueV1WithComment(_, givenValue.comment))
      )

  private def mapUriValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.uri_value)
      .orElseFail(BadRequestException("uri_value is missing"))
      .flatMap(it =>
        RouteUtilZ
          .validateAndEscapeIri(it, s"Invalid URI: $it")
          .map(UriValueV1)
          .map(CreateValueV1WithComment(_, givenValue.comment))
      )

  private def mapBooleanValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.boolean_value)
      .mapBoth(
        _ => BadRequestException("boolean_value is missing"),
        it => CreateValueV1WithComment(BooleanValueV1(it), givenValue.comment)
      )

  private def mapDecimalValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.decimal_value)
      .mapBoth(
        _ => BadRequestException("decimal_value is missing"),
        it => CreateValueV1WithComment(DecimalValueV1(it), givenValue.comment)
      )

  private def mapIntValue(givenValue: CreateResourceValueV1) =
    ZIO
      .fromOption(givenValue.int_value)
      .mapBoth(
        _ => BadRequestException("int_value is missing"),
        it => CreateValueV1WithComment(IntegerValueV1(it), givenValue.comment)
      )

  private def mapLinkValue(givenValue: CreateResourceValueV1) =
    (givenValue.link_value, givenValue.link_to_client_id) match {
      case (Some(targetIri: IRI), None) =>
        // This is a link to an existing Knora IRI, so make sure the IRI is valid.
        RouteUtilZ
          .validateAndEscapeIri(targetIri, s"Invalid Knora resource IRI: $targetIri")
          .map(it => CreateValueV1WithComment(LinkUpdateV1(it), givenValue.comment))

      case (None, Some(clientIDForTargetResource: IRI)) =>
        // This is a link to the client's ID for a resource that hasn't been created yet.
        ZIO.attempt(
          CreateValueV1WithComment(
            LinkToClientIDUpdateV1(clientIDForTargetResource),
            givenValue.comment
          )
        )

      case _ => ZIO.fail(AssertionException(s"Invalid link: $givenValue"))
    }

  private def mapTextValue(
    acceptStandoffLinksToClientIDs: Boolean,
    userProfile: UserADM,
    givenValue: CreateResourceValueV1
  ) =
    ZIO
      .fromOption(givenValue.richtext_value)
      .orElseFail(BadRequestException("richtext_value is missing"))
      .flatMap { richtext =>
        // check if text has markup
        if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
          // simple text
          toSparqlEncodedString(richtext.utf8str.get, s"Invalid text: '${richtext.utf8str.get}'")
            .map(it => CreateValueV1WithComment(TextValueSimpleV1(it, richtext.language), givenValue.comment))
        } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
          // XML: text with markup
          val richtextXml       = richtext.xml.get
          val richtextMappingId = richtext.mapping_id.get
          for {
            mappingIri <-
              RouteUtilZ.validateAndEscapeIri(richtextMappingId, s"mapping_id $richtextMappingId is invalid")
            textWithStandoffTags <-
              convertXMLtoStandoffTagV1(richtextXml, mappingIri, acceptStandoffLinksToClientIDs, userProfile)
            resourceReferences <- getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV2)
            utf8str <- toSparqlEncodedString(
                         textWithStandoffTags.text,
                         "utf8str for TextValue contains invalid characters"
                       )
          } yield CreateValueV1WithComment(
            TextValueWithStandoffV1(
              utf8str,
              richtext.language,
              textWithStandoffTags.standoffTagV2,
              resourceReferences,
              textWithStandoffTags.mapping.mappingIri,
              textWithStandoffTags.mapping.mapping
            ),
            givenValue.comment
          )
        } else {
          ZIO.fail(BadRequestException("invalid parameters given for TextValueV1"))
        }
      }
}
