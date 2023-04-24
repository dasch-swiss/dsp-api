/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import zio._
import zio.prelude.Validation

import java.time.Instant

import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.CreatePropertyCommand
import dsp.valueobjects.Iri._
import dsp.valueobjects.LangString
import dsp.valueobjects.Schema._
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilV2.completeResponse
import org.knora.webapi.routing.RouteUtilV2.getStringQueryParam
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
final case class OntologiesRouteV2()(
  private implicit val runtime: Runtime[
    AppConfig with Authenticator with IriConverter with MessageRelay with RestCardinalityService with StringFormatter
  ]
) {

  private val ontologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")

  private val allLanguagesKey         = "allLanguages"
  private val lastModificationDateKey = "lastModificationDate"

  def makeRoute: Route =
    dereferenceOntologyIri() ~
      getOntologyMetadata() ~
      updateOntologyMetadata() ~
      getOntologyMetadataForProjects() ~
      getOntology() ~
      createClass() ~
      updateClass() ~
      deleteClassComment() ~
      addCardinalities() ~
      canReplaceCardinalities ~
      replaceCardinalities() ~
      canDeleteCardinalitiesFromClass() ~
      deleteCardinalitiesFromClass() ~
      changeGuiOrder() ~
      getClasses() ~
      canDeleteClass() ~
      deleteClass() ~
      deleteOntologyComment() ~
      createProperty() ~
      updatePropertyLabelsOrComments() ~
      deletePropertyComment() ~
      updatePropertyGuiElement() ~
      getProperties() ~
      canDeleteProperty() ~
      deleteProperty() ~
      createOntology() ~
      canDeleteOntology() ~
      deleteOntology()

  private def dereferenceOntologyIri(): Route = path("ontology" / Segments) { _: List[String] =>
    get { requestContext =>
      // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
      // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
      // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
      // project-specific API ontology, prefix it with routeData.appConfig.externalOntologyIriHostAndPort to get the
      // ontology IRI.
      val ontologyIriTask = getOntologySmartIri(requestContext)

      val requestTask = for {
        ontologyIri          <- ontologyIriTask
        params: Map[IRI, IRI] = requestContext.request.uri.query().toMap
        allLanguagesStr       = params.get(allLanguagesKey)
        allLanguages          = ValuesValidator.optionStringToBoolean(allLanguagesStr, fallback = false)
        user                 <- Authenticator.getUserADM(requestContext)
      } yield OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, user)

      val targetSchemaTask = ontologyIriTask.flatMap(getTargetSchemaFromOntology)

      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
    }
  }

  private def getTargetSchemaFromOntology(iri: SmartIri) =
    ZIO.fromOption(iri.getOntologySchema).orElseFail(BadRequestException(s"Invalid ontology IRI: $iri"))

  private def getOntologySmartIri(
    requestContext: RequestContext
  ): ZIO[AppConfig with IriConverter with StringFormatter, BadRequestException, SmartIri] = {
    val urlPath = requestContext.request.uri.path.toString
    ZIO.serviceWithZIO[AppConfig] { appConfig =>
      val externalOntologyIriHostAndPort = appConfig.knoraApi.externalOntologyIriHostAndPort
      ZIO.serviceWithZIO[StringFormatter] { sf =>
        for {
          iri <- if (sf.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                   ZIO.succeed(OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath)
                 } else if (sf.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                   ZIO.succeed("http://" + externalOntologyIriHostAndPort + urlPath)
                 } else {
                   ZIO.fail(BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath"))
                 }
          smartIri <- validateOntologyIri(iri)
        } yield smartIri
      }
    }
  }

  private def validateOntologyIri(iri: String): ZIO[IriConverter with StringFormatter, BadRequestException, SmartIri] =
    RouteUtilZ.toSmartIri(iri, s"Invalid ontology IRI: $iri").flatMap(RouteUtilZ.ensureExternalOntologyName)

  private def getOntologyMetadata(): Route =
    path(ontologiesBasePath / "metadata") {
      get { requestContext =>
        val requestTask = for {
          maybeProjectIri <- RouteUtilV2.getProjectIri(requestContext)
          requestingUser  <- Authenticator.getUserADM(requestContext)
        } yield OntologyMetadataGetByProjectRequestV2(maybeProjectIri.toSet, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def updateOntologyMetadata(): Route =
    path(ontologiesBasePath / "metadata") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestTask = for {
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              requestingUser <- Authenticator.getUserADM(requestContext)
              apiRequestId   <- RouteUtilZ.randomUuid()
              requestMessage <-
                ZIO.attempt(ChangeOntologyMetadataRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))
            } yield requestMessage
            RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
          }
        }
      }
    }

  private def getOntologyMetadataForProjects(): Route =
    path(ontologiesBasePath / "metadata" / Segments) { projectIris: List[IRI] =>
      get { requestContext =>
        val requestTask = for {
          requestingUser <- Authenticator.getUserADM(requestContext)
          validatedProjectIris <- ZIO.foreach(projectIris) { iri =>
                                    RouteUtilZ.toSmartIri(iri, s"Invalid project IRI: $iri")
                                  }
        } yield OntologyMetadataGetByProjectRequestV2(validatedProjectIris.toSet, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getOntology(): Route =
    path(ontologiesBasePath / "allentities" / Segment) { externalOntologyIriStr: IRI =>
      get { requestContext =>
        val ontologyIriTask = validateOntologyIri(externalOntologyIriStr)
        val requestMessageTask = for {
          ontologyIri    <- ontologyIriTask
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield OntologyEntitiesGetRequestV2(ontologyIri, getLanguages(requestContext), requestingUser)
        val targetSchema = ontologyIriTask.flatMap(getTargetSchemaFromOntology)
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext, targetSchema)
      }
    }

  private def getLanguages(requestContext: RequestContext) = {
    val params: Map[IRI, IRI] = requestContext.request.uri.query().toMap
    ValuesValidator.optionStringToBoolean(params.get(allLanguagesKey), fallback = false)
  }

  private def createClass(): Route = path(ontologiesBasePath / "classes") {
    post {
      // Create a new class.
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageTask = for {
            requestingUser <- Authenticator.getUserADM(requestContext)
            requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <- ZIO.attempt(CreateClassRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }
  }

  private def updateClass(): Route =
    path(ontologiesBasePath / "classes") {
      put {
        // Change the labels or comments of a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageTask = for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              apiRequestId   <- RouteUtilZ.randomUuid()
              requestMessage <-
                ZIO.attempt(
                  ChangeClassLabelsOrCommentsRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser)
                )
            } yield requestMessage
            RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
          }
        }
      }
    }

  // delete the comment of a class definition
  private def deleteClassComment(): Route =
    path(ontologiesBasePath / "classes" / "comment" / Segment) { classIriStr: IRI =>
      delete { requestContext =>
        val requestMessageFuture = for {
          classIri <- RouteUtilZ
                        .toSmartIri(classIriStr, s"Invalid class IRI for request: $classIriStr")
                        .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(
                          BadRequestException(s"Invalid class IRI for request: $classIriStr")
                        )
          lastModificationDate <- getLastModificationDate(requestContext)
          requestingUser       <- Authenticator.getUserADM(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
        } yield DeleteClassCommentRequestV2(classIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestMessageFuture, requestContext)
      }
    }

  private def addCardinalities(): Route =
    path(ontologiesBasePath / "cardinalities") {
      post {
        // Add cardinalities to a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageTask = for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              apiRequestId   <- RouteUtilZ.randomUuid()
              requestMessage <-
                ZIO.attempt(AddCardinalitiesToClassRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))
            } yield requestMessage
            RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
          }
        }
      }
    }

  private def canReplaceCardinalities: Route =
    // GET basePath/{iriEncode} or
    // GET basePath/{iriEncode}?propertyIri={iriEncode}&newCardinality=[0-1|1|1-n|0-n]
    path(ontologiesBasePath / "canreplacecardinalities" / Segment) { classIri: IRI =>
      get { requestContext =>
        val response = for {
          user           <- Authenticator.getUserADM(requestContext)
          property       <- ZIO.attempt(getStringQueryParam(requestContext, RestCardinalityService.propertyIriKey))
          newCardinality <- ZIO.attempt(getStringQueryParam(requestContext, RestCardinalityService.newCardinalityKey))
          canChange      <- RestCardinalityService.canChangeCardinality(classIri, user, property, newCardinality)
        } yield canChange
        completeResponse(response, requestContext)
      }
    }

  private def replaceCardinalities(): Route =
    path(ontologiesBasePath / "cardinalities") {
      put {
        entity(as[String]) { reqBody => requestContext =>
          {
            val messageTask = for {
              user         <- Authenticator.getUserADM(requestContext)
              document     <- RouteUtilV2.parseJsonLd(reqBody)
              apiRequestId <- RouteUtilZ.randomUuid()
              msg          <- ZIO.attempt(ReplaceClassCardinalitiesRequestV2.fromJsonLd(document, apiRequestId, user))
            } yield msg
            RouteUtilV2.runRdfRouteZ(messageTask, requestContext)
          }
        }
      }
    }

  private def canDeleteCardinalitiesFromClass(): Route =
    path(ontologiesBasePath / "candeletecardinalities") {
      post {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val messageTask = for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              apiRequestId   <- RouteUtilZ.randomUuid()
              msg <- ZIO.attempt(
                       CanDeleteCardinalitiesFromClassRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser)
                     )
            } yield msg
            RouteUtilV2.runRdfRouteZ(messageTask, requestContext)
          }
        }
      }
    }

  // delete a single cardinality from the specified class if the property is
  // not used in resources.
  private def deleteCardinalitiesFromClass(): Route =
    path(ontologiesBasePath / "cardinalities") {
      patch {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageTask = for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              apiRequestId   <- RouteUtilZ.randomUuid()
              msg <-
                ZIO.attempt(DeleteCardinalitiesFromClassRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))
            } yield msg
            RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
          }
        }
      }
    }

  private def changeGuiOrder(): Route =
    path(ontologiesBasePath / "guiorder") {
      put {
        // Change a class's cardinalities.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageTask = for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              apiRequestId   <- RouteUtilZ.randomUuid()
              msg            <- ZIO.attempt(ChangeGuiOrderRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))
            } yield msg
            RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
          }
        }
      }
    }

  private def getClasses(): Route =
    path(ontologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      get { requestContext =>
        val classSmartIrisTask: ZIO[IriConverter with StringFormatter, BadRequestException, Set[SmartIri]] =
          ZIO
            .foreach(externalResourceClassIris)(iri =>
              RouteUtilZ
                .toSmartIri(iri, s"Invalid class IRI: $iri")
                .flatMap(RouteUtilZ.ensureExternalOntologyName)
                .flatMap(RouteUtilZ.ensureIsNotKnoraOntologyIri)
            )
            .map(_.toSet)

        val targetSchemaTask: ZIO[IriConverter with StringFormatter, BadRequestException, OntologySchema] =
          classSmartIrisTask
            .flatMap(iriSet =>
              ZIO.foreach(iriSet)(iri =>
                ZIO
                  .fromOption(iri.getOntologySchema)
                  .orElseFail(BadRequestException(s"Class IRI does not have an ontology schema: $iri"))
              )
            )
            .filterOrFail(_.size == 1)(BadRequestException(s"Only one ontology may be queried per request"))
            .map(_.head)

        val requestMessageTask = for {
          classSmartIris <- classSmartIrisTask
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield ClassesGetRequestV2(classSmartIris, getLanguages(requestContext), requestingUser)

        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext, targetSchemaTask)
      }
    }

  private def canDeleteClass(): Route =
    path(ontologiesBasePath / "candeleteclass" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val requestTask = for {
          classSmartIri <-
            RouteUtilZ
              .toSmartIri(classIriStr, s"Invalid class IRI: $classIriStr")
              .flatMap(RouteUtilZ.ensureExternalOntologyName)
              .filterOrFail(_.isKnoraApiV2EntityIri)(BadRequestException(s"Invalid class IRI: $classIriStr"))
              .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(
                BadRequestException(s"Invalid class IRI for request: $classIriStr")
              )
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield CanDeleteClassRequestV2(classSmartIri, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def deleteClass(): Route =
    path(ontologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      delete { requestContext =>
        val requestTask = for {
          classIri <- ZIO
                        .succeed(externalResourceClassIris)
                        .filterOrFail(_.size == 1)(BadRequestException(s"Only one class can be deleted at a time"))
                        .map(_.head)
                        .flatMap(iri => RouteUtilZ.toSmartIri(iri, s"Invalid class IRI: $iri"))
                        .flatMap(RouteUtilZ.ensureExternalOntologyName)
                        .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- Authenticator.getUserADM(requestContext)
        } yield DeleteClassRequestV2(classIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def getLastModificationDate(ctx: RequestContext): IO[BadRequestException, Instant] =
    ZIO
      .fromOption(ctx.request.uri.query().toMap.get(lastModificationDateKey))
      .mapBoth(
        _ => BadRequestException(s"Missing parameter: $lastModificationDateKey"),
        ValuesValidator.xsdDateTimeStampToInstant
      )
      .flatMap(it => ZIO.fromOption(it).orElseFail(BadRequestException(s"Invalid timestamp: $it")))

  private def deleteOntologyComment(): Route =
    path(ontologiesBasePath / "comment" / Segment) { ontologyIriStr: IRI =>
      delete { requestContext =>
        val requestMessageTask = for {
          ontologyIri <- RouteUtilZ
                           .toSmartIri(ontologyIriStr, s"Invalid ontology IRI: $ontologyIriStr")
                           .flatMap(RouteUtilZ.ensureExternalOntologyName)
                           .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- Authenticator.getUserADM(requestContext)
        } yield DeleteOntologyCommentRequestV2(ontologyIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }

  private def createProperty(): Route =
    path(ontologiesBasePath / "properties") {
      post {
        // Create a new property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageTask = for {
              requestingUser                            <- Authenticator.getUserADM(requestContext)
              requestDoc                                <- RouteUtilV2.parseJsonLd(jsonRequest)
              inputOntology                             <- getInputOntology(requestDoc)
              propertyUpdateInfo                        <- getPropertyDef(inputOntology)
              propertyInfoContent: PropertyInfoContentV2 = propertyUpdateInfo.propertyInfoContent
              _                                         <- PropertyIri.make(propertyInfoContent.propertyIri.toString).toZIO

              // get gui related values from request and validate them by making value objects from it
              // get the (optional) gui element from the request
              maybeGuiElement <- getGuiElement(propertyInfoContent)
              // get the gui attribute(s) from the request
              maybeGuiAttributes <- getGuiAttributes(propertyInfoContent)
              _ <- GuiObject
                     .makeFromStrings(maybeGuiAttributes, maybeGuiElement)
                     .toZIO
                     .mapError(error => BadRequestException(error.msg))

              apiRequestId <- RouteUtilZ.randomUuid()
              requestMessage <-
                ZIO.attempt(CreatePropertyRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))

              // get gui related values from request and validate them by making value objects from it

              // get ontology info from request
              inputOntology       <- getInputOntology(requestDoc)
              propertyInfoContent <- getPropertyDef(inputOntology).map(_.propertyInfoContent)

              // get the (optional) gui element
              // get the (optional) gui element from the request
              maybeGuiElement <- getGuiElement(propertyInfoContent)

              // validate the gui element by creating value object
              validatedGuiElement = maybeGuiElement match {
                                      case Some(guiElement) => GuiElement.make(guiElement).map(Some(_))
                                      case None             => Validation.succeed(None)
                                    }
              maybeGuiAttributes <- getGuiAttributes(propertyInfoContent)

              // validate the gui attributes by creating value objects
              guiAttributes = maybeGuiAttributes.map(guiAttribute => GuiAttribute.make(guiAttribute))

              validatedGuiAttributes = Validation.validateAll(guiAttributes).map(_.toSet)

              // validate the combination of gui element and gui attribute by creating a GuiObject value object
              guiObject = Validation
                            .validate(validatedGuiAttributes, validatedGuiElement)
                            .flatMap(values => GuiObject.make(values._1, values._2))

              ontologyIri         <- IriConverter.asSmartIri(inputOntology.ontologyMetadata.ontologyIri.toString)
              lastModificationDate = Validation.succeed(propertyUpdateInfo.lastModificationDate)
              propertyIri         <- IriConverter.asSmartIri(propertyInfoContent.propertyIri.toString)
              subClassConstraintSmartIri <-
                RouteUtilZ.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint, "Should not happen")
              subjectType <-
                propertyInfoContent.predicates.get(subClassConstraintSmartIri) match {
                  case None => ZIO.succeed(None)
                  case Some(value) =>
                    value.objects.head match {
                      case objectType: SmartIriLiteralV2 =>
                        IriConverter
                          .asSmartIri(
                            objectType.value.toOntologySchema(InternalSchema).toString
                          )
                          .map(Some(_))
                      case other =>
                        ZIO.fail(ValidationException(s"Unexpected subject type for $other"))
                    }
                }
              objectTypeSmartIri <- RouteUtilZ
                                      .toSmartIri(OntologyConstants.KnoraApiV2Complex.ObjectType, "Should not happen")
              objectType <-
                propertyInfoContent.predicates.get(objectTypeSmartIri) match {
                  case None =>
                    ZIO.fail(ValidationException(s"Object type cannot be empty."))
                  case Some(value) =>
                    value.objects.head match {
                      case objectType: SmartIriLiteralV2 =>
                        IriConverter.asSmartIri(objectType.value.toOntologySchema(InternalSchema).toString)
                      case other =>
                        ZIO.fail(ValidationException(s"Unexpected object type for $other"))
                    }
                }
              labelSmartIri <- RouteUtilZ.toSmartIri(OntologyConstants.Rdfs.Label, "Should not happen")
              label = propertyInfoContent.predicates.get(labelSmartIri) match {
                        case None => Validation.fail(ValidationException("Label missing"))
                        case Some(value) =>
                          value.objects.head match {
                            case StringLiteralV2(value, Some(language)) => LangString.makeFromStrings(language, value)
                            case StringLiteralV2(_, None) =>
                              Validation.fail(ValidationException("Label missing the language tag"))
                            case _ => Validation.fail(ValidationException("Unexpected Type for Label"))
                          }
                      }
              commentSmartIri <- RouteUtilZ.toSmartIri(OntologyConstants.Rdfs.Comment, "Should not happen")
              comment = propertyInfoContent.predicates.get(commentSmartIri) match {
                          case None => Validation.succeed(None)
                          case Some(value) =>
                            value.objects.head match {
                              case StringLiteralV2(value, Some(language)) =>
                                LangString.makeFromStrings(language, value).map(Some(_))
                              case StringLiteralV2(_, None) =>
                                Validation.fail(ValidationException("Comment missing the language tag"))
                              case _ => Validation.fail(ValidationException("Unexpected Type for Comment"))
                            }
                        }
              superProperties =
                propertyInfoContent.subPropertyOf.toList match {
                  case Nil        => Validation.fail(ValidationException("SuperProperties cannot be empty."))
                  case superProps => Validation.succeed(superProps)
                }

              _ <-
                Validation
                  .validate(
                    lastModificationDate,
                    label,
                    comment,
                    superProperties,
                    guiObject
                  )
                  .flatMap(v =>
                    CreatePropertyCommand
                      .make(ontologyIri, v._1, propertyIri, subjectType, objectType, v._2, v._3, v._4, v._5)
                  )
                  .toZIO
            } yield requestMessage

            RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
          }
        }
      }
    }

  private def getGuiAttributes(propertyInfoContent: PropertyInfoContentV2) =
    RouteUtilZ
      .toSmartIri(SalsahGui.External.GuiAttribute, "Should not happen")
      .map(propertyInfoContent.predicates.get)
      .flatMap(infoMaybe =>
        ZIO
          .foreach(infoMaybe) { predicateInfoV2: PredicateInfoV2 =>
            ZIO.foreach(predicateInfoV2.objects) {
              case guiAttribute: StringLiteralV2 => ZIO.succeed(guiAttribute.value)
              case other =>
                ZIO.fail(BadRequestException(s"Unexpected object for salsah-gui:guiAttribute: $other"))
            }
          }
          .map(_.toSet.flatten)
      )

  private def getGuiElement(propertyInfoContent: PropertyInfoContentV2) =
    RouteUtilZ
      .toSmartIri(SalsahGui.External.GuiElementProp, "Should not happen")
      .map(propertyInfoContent.predicates.get)
      .flatMap(infoMaybe =>
        ZIO.foreach(infoMaybe) { predicateInfoV2: PredicateInfoV2 =>
          predicateInfoV2.objects.head match {
            case guiElement: SmartIriLiteralV2 =>
              ZIO.succeed(guiElement.value.toOntologySchema(InternalSchema).toString)
            case other =>
              ZIO.fail(BadRequestException(s"Unexpected object for salsah-gui:guiElement: $other"))
          }
        }
      )

  private def getPropertyDef(inputOntology: InputOntologyV2) =
    ZIO.attempt(OntologyUpdateHelper.getPropertyDef(inputOntology))

  private def getInputOntology(requestDoc: JsonLDDocument) =
    ZIO.attempt(InputOntologyV2.fromJsonLD(requestDoc))

  private def updatePropertyLabelsOrComments(): Route =
    path(ontologiesBasePath / "properties") {
      put {
        // Change the labels or comments of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageTask = for {
              requestingUser <- Authenticator.getUserADM(requestContext)
              requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
              apiRequestId   <- RouteUtilZ.randomUuid()
              requestMessage <-
                ZIO.attempt(
                  ChangePropertyLabelsOrCommentsRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser)
                )
            } yield requestMessage
            RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
          }
        }
      }
    }

  // delete the comment of a property definition
  private def deletePropertyComment(): Route =
    path(ontologiesBasePath / "properties" / "comment" / Segment) { propertyIriStr: IRI =>
      delete { requestContext =>
        val requestTask = for {
          propertyIri <- RouteUtilZ
                           .toSmartIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
                           .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- Authenticator.getUserADM(requestContext)
        } yield DeletePropertyCommentRequestV2(propertyIri, lastModificationDate, apiRequestId, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def updatePropertyGuiElement(): Route =
    path(ontologiesBasePath / "properties" / "guielement") {
      put {
        // Change the salsah-gui:guiElement and/or salsah-gui:guiAttribute of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestTask = for {
              requestingUser      <- Authenticator.getUserADM(requestContext)
              requestDoc          <- RouteUtilV2.parseJsonLd(jsonRequest)
              inputOntology       <- getInputOntology(requestDoc)
              propertyUpdateInfo  <- getPropertyDef(inputOntology)
              propertyInfoContent  = propertyUpdateInfo.propertyInfoContent
              lastModificationDate = propertyUpdateInfo.lastModificationDate
              propertyIri         <- PropertyIri.make(propertyInfoContent.propertyIri.toString).toZIO
              newGuiElement       <- getGuiElement(propertyInfoContent)
              newGuiAttributes    <- getGuiAttributes(propertyInfoContent)
              guiObject <-
                GuiObject
                  .makeFromStrings(newGuiAttributes, newGuiElement)
                  .toZIO
                  .mapError(e => BadRequestException(e.msg))
              apiRequestId <- RouteUtilZ.randomUuid()
            } yield ChangePropertyGuiElementRequest(
              propertyIri,
              guiObject,
              lastModificationDate,
              apiRequestId,
              requestingUser
            )
            RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
          }
        }
      }
    }

  private def getProperties(): Route =
    path(ontologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      get { requestContext =>
        val propertyIrisTask = for {
          propertyIris <- ZIO.foreach(externalPropertyIris) { propertyIriStr: IRI =>
                            RouteUtilZ
                              .toSmartIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
                              .flatMap(RouteUtilZ.ensureIsNotKnoraOntologyIri)
                              .flatMap(RouteUtilZ.ensureExternalOntologyName)
                          }
        } yield propertyIris.toSet

        val targetSchemaTask = for {
          schemas <- propertyIrisTask.map(_.flatMap(_.getOntologySchema))
          targetSchema <-
            ZIO
              .succeed(schemas)
              .filterOrFail(_.size == 1)(BadRequestException(s"Only one ontology may be queried per request"))
              .map(_.head)
        } yield targetSchema

        val requestTask = for {
          propertyIris   <- propertyIrisTask
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield PropertiesGetRequestV2(propertyIris, getLanguages(requestContext), requestingUser)

        RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
      }
    }

  private def canDeleteProperty(): Route =
    path(ontologiesBasePath / "candeleteproperty" / Segment) { propertyIriStr: IRI =>
      get { requestContext =>
        val requestMessageTask = for {
          propertyIri <- RouteUtilZ
                           .toSmartIri(propertyIriStr, s"Invalid property IRI: $propertyIriStr")
                           .flatMap(RouteUtilZ.ensureExternalOntologyName)
                           .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield CanDeletePropertyRequestV2(propertyIri, requestingUser)
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }

  private def deleteProperty(): Route =
    path(ontologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      delete { requestContext =>
        val requestMessageTask = for {
          propertyIri <-
            ZIO
              .succeed(externalPropertyIris)
              .filterOrFail(_.size == 1)(BadRequestException(s"Only one property can be deleted at a time"))
              .map(_.head)
              .flatMap(iri => RouteUtilZ.toSmartIri(iri, s"Invalid property IRI: $iri"))
              .flatMap(RouteUtilZ.ensureExternalOntologyName)
              .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
          lastModificationDate <- getLastModificationDate(requestContext)
          apiRequestId         <- RouteUtilZ.randomUuid()
          requestingUser       <- Authenticator.getUserADM(requestContext)
        } yield DeletePropertyRequestV2(
          propertyIri,
          lastModificationDate,
          apiRequestId,
          requestingUser
        )
        RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
      }
    }

  private def createOntology(): Route = path(ontologiesBasePath) {
    // Create a new, empty ontology.
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestTask = for {
            requestingUser <- Authenticator.getUserADM(requestContext)
            requestDoc     <- RouteUtilV2.parseJsonLd(jsonRequest)
            apiRequestId   <- RouteUtilZ.randomUuid()
            requestMessage <- ZIO.attempt(CreateOntologyRequestV2.fromJsonLd(requestDoc, apiRequestId, requestingUser))
          } yield requestMessage
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
      }
    }
  }

  private def canDeleteOntology(): Route =
    path(ontologiesBasePath / "candeleteontology" / Segment) { ontologyIriStr: IRI =>
      get { requestContext =>
        val requestTask = for {
          ontologyIri <- RouteUtilZ
                           .toSmartIri(ontologyIriStr, s"Invalid ontology IRI for request $ontologyIriStr")
                           .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
                           .flatMap(RouteUtilZ.ensureExternalOntologyName)
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield CanDeleteOntologyRequestV2(ontologyIri, requestingUser)

        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def deleteOntology(): Route = path(ontologiesBasePath / Segment) { ontologyIriStr =>
    delete { requestContext =>
      val requestMessageTask = for {
        ontologyIri <- RouteUtilZ
                         .toSmartIri(ontologyIriStr, s"Invalid ontology IRI for request $ontologyIriStr")
                         .flatMap(RouteUtilZ.ensureExternalOntologyName)
                         .flatMap(RouteUtilZ.ensureIsKnoraOntologyIri)
                         .flatMap(RouteUtilZ.ensureApiV2ComplexSchema)
        lastModificationDate <- getLastModificationDate(requestContext)
        apiRequestId         <- RouteUtilZ.randomUuid()
        requestingUser       <- Authenticator.getUserADM(requestContext)
      } yield DeleteOntologyRequestV2(ontologyIri, lastModificationDate, apiRequestId, requestingUser)
      RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
    }
  }
}
