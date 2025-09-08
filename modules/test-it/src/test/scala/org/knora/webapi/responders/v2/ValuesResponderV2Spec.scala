/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import scala.reflect.ClassTag

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionYear
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.models.filemodels.FileModelUtil
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.sharedtestdata.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.MutableTestIri

object ValuesResponderV2Spec extends E2EZSpec { self =>

  private val searchResponder = ZIO.serviceWithZIO[SearchResponderV2]
  private val valuesResponder = ZIO.serviceWithZIO[ValuesResponderV2]

  private val zeitgloeckleinIri = "http://rdfh.ch/0803/c5058f3a"
  private val generationeIri    = "http://rdfh.ch/0803/c3f913666f"
  private val aThingIri         = "http://rdfh.ch/0001/a-thing"
  private val freetestWithAPropertyFromAnythingOntologyIri =
    "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology"
  private val aThingPictureIri     = "http://rdfh.ch/0001/a-thing-picture"
  private val sierraIri            = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
  private val thingPictureClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"

  private val mimeTypeTIFF = "image/tiff"
  private val mimeTypeJP2  = "image/jp2"

  private def asInstanceOf[A](value: Any)(implicit tag: ClassTag[A]): IO[AssertionException, A] =
    ZIO.succeed(value).collect(AssertionException(s"Expected value to be of type $tag")) { case v: A => v }

  override val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(path = "test_data/project_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/generated_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
  )

  private val zeitgloeckleinCommentWithoutStandoffIri    = new MutableTestIri
  private val zeitgloeckleinCommentWithStandoffIri       = new MutableTestIri
  private val zeitgloeckleinCommentWithCommentIri        = new MutableTestIri
  private val zeitgloeckleinSecondCommentWithStandoffIri = new MutableTestIri
  private val lobComment1Iri                             = new MutableTestIri
  private val lobComment2Iri                             = new MutableTestIri
  private val decimalValueIri                            = new MutableTestIri
  private val timeValueIri                               = new MutableTestIri
  private val dateValueIri                               = new MutableTestIri
  private val booleanValueIri                            = new MutableTestIri
  private val geometryValueIri                           = new MutableTestIri
  private val intervalValueIri                           = new MutableTestIri
  private val listValueIri                               = new MutableTestIri
  private val colorValueIri                              = new MutableTestIri
  private val uriValueIri                                = new MutableTestIri
  private val geonameValueIri                            = new MutableTestIri
  private val linkValueIri                               = new MutableTestIri
  private val standoffLinkValueIri                       = new MutableTestIri
  private val stillImageFileValueIri                     = new MutableTestIri

  private var linkValueUUID = randomUUID

  private val sampleStandoff: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 0,
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 1,
    ),
  )

  private val sampleStandoffModified: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 1,
      endPosition = 7,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 0,
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 1,
    ),
  )

  private val sampleStandoffWithLink: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
      dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
      startPosition = 0,
      endPosition = 7,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 0,
      attributes = Vector(
        StandoffTagIriAttributeV2(
          standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
          value = aThingIri,
        ),
      ),
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 1,
    ),
  )

  private var standardMapping: Option[MappingXMLtoStandoff] = None

  private def getResourceWithValues(
    resourceIri: IRI,
    propertyIrisForGravsearch: Seq[SmartIri],
    requestingUser: User,
  ): ZIO[SearchResponderV2, Throwable, ReadResourceV2] = {
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch,
      )
      .toString
    for {
      query  <- ZIO.attempt(GravsearchParser.parseQuery(gravsearchQuery))
      schema  = SchemaRendering.apiV2SchemaWithOption(MarkupRendering.Xml)
      result <- searchResponder(_.gravsearchV2(query, schema, requestingUser))
    } yield result.toResource(resourceIri).toOntologySchema(ApiV2Complex)
  }

  private def getValuesFromResource(resource: ReadResourceV2, propertyIriInResult: SmartIri): Seq[ReadValueV2] =
    resource.values.getOrElse(
      propertyIriInResult,
      throw AssertionException(s"Resource <${resource.resourceIri}> does not have property <$propertyIriInResult>"),
    )

  private def getValueFromResource(
    resource: ReadResourceV2,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
  ): ReadValueV2 = {
    val propertyValues: Seq[ReadValueV2] =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)
    propertyValues
      .find(_.valueIri == expectedValueIri)
      .getOrElse(
        throw AssertionException(
          s"Property <$propertyIriInResult> of resource <${resource.resourceIri}> does not have value <$expectedValueIri>",
        ),
      )
  }

  private def checkValueIsDeleted(
    resourceIri: ResourceIri,
    maybePreviousLastModDate: Option[Instant],
    valueIri: ValueIri,
    customDeleteDate: Option[Instant] = None,
    deleteComment: Option[String] = None,
    requestingUser: User,
    isLinkValue: Boolean = false,
  ) = for {
    getResponse <- ZIO.serviceWithZIO[ResourcesResponderV2](
                     _.getResourcesWithDeletedResource(
                       resourceIris = Seq(resourceIri.toString),
                       targetSchema = ApiV2Complex,
                       schemaOptions = Set.empty,
                       requestingUser = requestingUser,
                     ),
                   )
    resource = getResponse.toResource(resourceIri.toString)
    //  ensure the resource was not deleted
    _ <- ZIO.fail(AssertionException("resource was deleted")).when(resource.deletionInfo.nonEmpty)

    deletedValues = resource.values.getOrElse(
                      OntologyConstants.KnoraBase.DeletedValue.toSmartIri,
                      throw AssertionException(
                        s"Resource <$resourceIri> does not have any deleted values, even though value <$valueIri>==  deleted.",
                      ),
                    )
    _ <- if (!isLinkValue) {
           // not a LinkValue, so the value==  a DeletedValue of the resource
           for {
             deletedValue <-
               ZIO.attempt(
                 deletedValues.collectFirst { case v if v.valueIri == valueIri.toString => v }
                   .getOrElse(throw AssertionException(s"Value <$valueIri> was not among the deleted resources")),
               )
             _ <- checkLastModDate(
                    resourceIri.toString,
                    maybePreviousLastModDate,
                    resource.lastModificationDate,
                  )
             deletionInfo <- ZIO.attempt(
                               deletedValue.deletionInfo.getOrElse(
                                 throw AssertionException(s"Value <$valueIri> does not have deletion information"),
                               ),
                             )
             _ <- customDeleteDate match {
                    case Some(deleteDate) =>
                      ZIO.unless(deletionInfo.deleteDate == deleteDate)(
                        ZIO.fail(AssertionException(s"Value <$valueIri> was not deleted at the expected time.")),
                      )
                    case None => ZIO.unit
                  }
             _ <- deleteComment match {
                    case Some(comment) =>
                      ZIO.unless(deletionInfo.maybeDeleteComment.contains(comment))(
                        ZIO.fail(AssertionException(s"Value <$valueIri> was not deleted with the expected comment.")),
                      )
                    case None => ZIO.unit
                  }
           } yield ()
         } else {
           // The value is a LinkValue, so there==  a DeletedValue having a PreviousValue with the IRI of the value.
           ZIO
             .fail(AssertionException(s"ListValue <$valueIri> was not deleted correctly."))
             .when(
               !deletedValues.exists(v =>
                 v.previousValueIri match {
                   case Some(previousValueIRI) => previousValueIRI == valueIri.toString
                   case None                   => false
                 },
               ),
             )
             .unit
         }
  } yield ()

  private def checkLastModDate(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    maybeUpdatedLastModDate: Option[Instant],
  ): ZIO[Any, AssertionException, Unit] =
    ZIO
      .fromOption(maybeUpdatedLastModDate)
      .orElseFail(AssertionException(s"Resource $resourceIri has no knora-base:lastModificationDate"))
      .flatMap(updatedLastModDate =>
        maybePreviousLastModDate.map { previousLastModDate =>
          ZIO
            .fail(AssertionException(s"Resource $resourceIri knora-base:lastModificationDate was not updated"))
            .unless(updatedLastModDate.isAfter(previousLastModDate))
            .unit
        }.getOrElse(ZIO.unit),
      )

  private def getValue(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
    requestingUser: User,
    checkLastModDateChanged: Boolean = true,
  ): ZIO[SearchResponderV2, Throwable, ReadValueV2] = for {
    resource <- getResourceWithValues(resourceIri, Seq(propertyIriForGravsearch), requestingUser)
    _ <- checkLastModDate(resourceIri, maybePreviousLastModDate, resource.lastModificationDate).when(
           checkLastModDateChanged,
         )
  } yield getValueFromResource(resource, propertyIriInResult, expectedValueIri)

  private def getResourceLastModificationDate(resourceIri: IRI, requestingUser: User) = for {
    previewResponse <- ZIO.serviceWithZIO[ResourcesResponderV2](
                         _.getResourcePreviewWithDeletedResource(
                           Seq(resourceIri),
                           targetSchema = ApiV2Complex,
                           requestingUser = requestingUser,
                         ),
                       )
    resourcePreview = previewResponse.toResource(resourceIri)
  } yield resourcePreview.lastModificationDate

  private def getValueUUID(valueIri: IRI): ZIO[TriplestoreService, Throwable, Option[UUID]] = {
    val sparqlQuery =
      s"""
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |
         |SELECT ?valueUUID WHERE {
         |    <$valueIri> knora-base:valueHasUUID ?valueUUID .
         |}
         |""".stripMargin
    for {
      result  <- ZIO.serviceWithZIO[TriplestoreService](_.query(Select(sparqlQuery)))
      firstRow = result.results.bindings.headOption
    } yield firstRow.flatMap(_.rowMap.get("valueUUID")).flatMap(uuidStr => UuidUtil.base64Decode(uuidStr).toOption)
  }

  private def getValuePermissions(valueIri: IRI): ZIO[TriplestoreService, Throwable, Option[UUID]] = {
    val sparqlQuery =
      s"""
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |
         |SELECT ?valuePermissions WHERE {
         |    <$valueIri> knora-base:hasPermissions ?valuePermissions .
         |}""".stripMargin
    for {
      result  <- ZIO.serviceWithZIO[TriplestoreService](_.query(Select(sparqlQuery)))
      firstRow = result.results.bindings.headOption
    } yield firstRow
      .flatMap(_.rowMap.get("valuePermissions"))
      .flatMap(uuidStr => UuidUtil.base64Decode(uuidStr).toOption)
  }

  object Anything {
    val ontologyIri = anythingOntologyIri.asComplex
    // classes
    val thingClass = ontologyIri.makeClass("Thing")

    // properties
    val hasInteger                      = ontologyIri.makeProperty("hasInteger")
    val hasIntegerUsedByOtherOntologies = ontologyIri.makeProperty("hasIntegerUsedByOtherOntologies")
  }

  private val integerValueSuite = {
    val firstIntValueVersionIri          = new MutableTestIri
    val intValueIri                      = new MutableTestIri
    val intValueIriForFreetest           = new MutableTestIri
    val intValueIriWithCustomPermissions = new MutableTestIri
    val intValueForRsyncIri              = new MutableTestIri

    var integerValueUUID = randomUUID

    suite("Integer Values")(
      test("create an integer value") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 4
        val createParams = CreateValueV2(
          resourceIri,
          Anything.thingClass.smartIri,
          propertyIri,
          IntegerValueContentV2(ApiV2Complex, intValue),
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
          _                         = intValueIri.set(createValueResponse.valueIri)
          _                         = firstIntValueVersionIri.set(createValueResponse.valueIri)
          _                         = { integerValueUUID = createValueResponse.valueUUID }
          valueFromTriplestore <- getValue(
                                    resourceIri,
                                    maybeResourceLastModDate,
                                    propertyIri,
                                    propertyIri,
                                    intValueIri.get,
                                    anythingUser1,
                                  )
          actual <- asInstanceOf[IntegerValueContentV2](valueFromTriplestore.valueContent)
        } yield assertTrue(actual.valueHasInteger == intValue)
      },
      test("update an integer value") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 5

        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          previousValueFromTriplestore <- getValue(
                                            resourceIri = resourceIri,
                                            maybePreviousLastModDate = maybeResourceLastModDate,
                                            propertyIriForGravsearch = propertyIri,
                                            propertyIriInResult = propertyIri,
                                            expectedValueIri = intValueIri.get,
                                            requestingUser = anythingUser1,
                                            checkLastModDateChanged = false,
                                          )
          // Update the value.
          updateParams = UpdateValueContentV2(
                           resourceIri = resourceIri,
                           resourceClassIri = Anything.thingClass.smartIri,
                           propertyIri = propertyIri,
                           valueIri = intValueIri.get,
                           valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
                         )
          updateValueResponse <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
          _                    = intValueIri.set(updateValueResponse.valueIri)
          _ <- ZIO
                 .fail(AssertionException("The value UUID was not preserved"))
                 .unless(updateValueResponse.valueUUID == integerValueUUID)
          updatedValueFromTriplestore <- getValue(
                                           resourceIri = resourceIri,
                                           maybePreviousLastModDate = maybeResourceLastModDate,
                                           propertyIriForGravsearch = propertyIri,
                                           propertyIriInResult = propertyIri,
                                           expectedValueIri = intValueIri.get,
                                           requestingUser = anythingUser1,
                                         )
          actualInteger        <- asInstanceOf[IntegerValueContentV2](updatedValueFromTriplestore.valueContent)
          prevValueUuid        <- getValueUUID(previousValueFromTriplestore.valueIri)
          prevValuePermissions <- getValuePermissions(previousValueFromTriplestore.valueIri)
        } yield assertTrue(
          actualInteger.valueHasInteger == intValue,
          updatedValueFromTriplestore.permissions == previousValueFromTriplestore.permissions,
          updatedValueFromTriplestore.valueHasUUID == previousValueFromTriplestore.valueHasUUID,
          // Check that the permissions and UUID were deleted from the previous version of the value.
          prevValueUuid.isEmpty,
          prevValuePermissions.isEmpty,
        )
      },
      test("create an integer value that belongs to a property of another ontology") {
        val resourceIri = freetestWithAPropertyFromAnythingOntologyIri
        val propertyIri = Anything.hasIntegerUsedByOtherOntologies.smartIri
        // Create the value.
        val intValue = 40
        val createParams = CreateValueV2(
          resourceIri,
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
          propertyIri,
          IntegerValueContentV2(ApiV2Complex, intValue),
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
          _                         = intValueIriForFreetest.set(createValueResponse.valueIri)
          _                         = { integerValueUUID = createValueResponse.valueUUID }
          valueFromTriplestore <- getValue(
                                    resourceIri = resourceIri,
                                    maybePreviousLastModDate = maybeResourceLastModDate,
                                    propertyIriForGravsearch = propertyIri,
                                    propertyIriInResult = propertyIri,
                                    expectedValueIri = intValueIriForFreetest.get,
                                    requestingUser = anythingUser1,
                                  )
          actual <- asInstanceOf[IntegerValueContentV2](valueFromTriplestore.valueContent)
        } yield assertTrue(actual.valueHasInteger == intValue)
      },
      test("update an integer value that belongs to a property of another ontology") {
        val resourceIri   = freetestWithAPropertyFromAnythingOntologyIri
        val propertyIri   = Anything.hasIntegerUsedByOtherOntologies.smartIri
        val intValue: Int = 50

        val updateParams = UpdateValueContentV2(
          resourceIri,
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
          propertyIri,
          intValueIriForFreetest.get,
          IntegerValueContentV2(ApiV2Complex, intValue),
        )
        for {
          // Get the value before update.
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser2)
          previousValueFromTriplestore <- getValue(
                                            resourceIri = resourceIri,
                                            maybePreviousLastModDate = maybeResourceLastModDate,
                                            propertyIriForGravsearch = propertyIri,
                                            propertyIriInResult = propertyIri,
                                            expectedValueIri = intValueIriForFreetest.get,
                                            requestingUser = anythingUser2,
                                            checkLastModDateChanged = false,
                                          )
          updateValueResponse <- valuesResponder(_.updateValueV2(updateParams, anythingUser2, randomUUID))
          _                    = intValueIriForFreetest.set(updateValueResponse.valueIri)
          actualValue <- getValue(
                           resourceIri = resourceIri,
                           maybePreviousLastModDate = maybeResourceLastModDate,
                           propertyIriForGravsearch = propertyIri,
                           propertyIriInResult = propertyIri,
                           expectedValueIri = intValueIriForFreetest.get,
                           requestingUser = anythingUser2,
                         )
          actual        <- asInstanceOf[IntegerValueContentV2](actualValue.valueContent)
          prevValueUuid <- getValueUUID(previousValueFromTriplestore.valueIri)
          prevValuePerm <- getValuePermissions(previousValueFromTriplestore.valueIri)
        } yield assertTrue(
          actualValue.permissions == previousValueFromTriplestore.permissions,
          actualValue.valueHasUUID == previousValueFromTriplestore.valueHasUUID,
          actual.valueHasInteger == intValue,
          updateValueResponse.valueUUID == previousValueFromTriplestore.valueHasUUID,
          // Check that the permissions and UUID were deleted from the previous version of the value.
          prevValueUuid.isEmpty,
          prevValuePerm.isEmpty,
        )
      },
      test("delete an integer value that belongs to a property of another ontology") {
        val resourceIri = ResourceIri.unsafeFrom(freetestWithAPropertyFromAnythingOntologyIri.toSmartIri)
        val propertyIri = Anything.hasIntegerUsedByOtherOntologies
        val resourceClassIri = ResourceClassIri.unsafeFrom(
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
        )
        val deleteParams = DeleteValueV2(
          resourceIri,
          resourceClassIri,
          propertyIri,
          intValueIriForFreetest.asValueIri,
          KA.IntValue.toSmartIri,
          Some("this value was incorrect"),
          apiRequestId = randomUUID,
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri.toString, anythingUser2)
          _                        <- valuesResponder(_.deleteValueV2(deleteParams, anythingUser2))
          _ <- checkValueIsDeleted(
                 resourceIri,
                 maybeResourceLastModDate,
                 intValueIriForFreetest.asValueIri,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("update an integer value, adding a comment") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val comment     = "Added a comment"

        val intValue = 5
        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue, Some(comment)),
        )

        // Get the value before update.
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          previousValueFromTriplestore <- getValue(
                                            resourceIri = resourceIri,
                                            maybePreviousLastModDate = maybeResourceLastModDate,
                                            propertyIriForGravsearch = propertyIri,
                                            propertyIriInResult = propertyIri,
                                            expectedValueIri = intValueIri.get,
                                            requestingUser = anythingUser1,
                                            checkLastModDateChanged = false,
                                          )

          updateValueResponse <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
          _                    = intValueIri.set(updateValueResponse.valueIri)
          actualValue <- getValue(
                           resourceIri = resourceIri,
                           maybePreviousLastModDate = maybeResourceLastModDate,
                           propertyIriForGravsearch = propertyIri,
                           propertyIriInResult = propertyIri,
                           expectedValueIri = intValueIri.get,
                           requestingUser = anythingUser1,
                         )
          actualContent <- asInstanceOf[IntegerValueContentV2](actualValue.valueContent)
          prevValueUuid <- getValueUUID(previousValueFromTriplestore.valueIri)
          prevPerms     <- getValuePermissions(previousValueFromTriplestore.valueIri)
        } yield assertTrue(
          actualContent.valueHasInteger == intValue,
          actualValue.permissions == previousValueFromTriplestore.permissions,
          actualValue.valueHasUUID == previousValueFromTriplestore.valueHasUUID,
          actualValue.valueContent.comment.contains(comment),
          // Check that the permissions and UUID were deleted from the previous version of the value.
          prevValueUuid.isEmpty,
          prevPerms.isEmpty,
        )
      },
      test("update an integer value with a comment, changing only the comment") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val comment     = "An updated comment"

        val intValue = 5
        val updateValueParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue, Some(comment)),
        )

        // Get the value before update.
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          previousValueFromTriplestore <- getValue(
                                            resourceIri = resourceIri,
                                            maybePreviousLastModDate = maybeResourceLastModDate,
                                            propertyIriForGravsearch = propertyIri,
                                            propertyIriInResult = propertyIri,
                                            expectedValueIri = intValueIri.get,
                                            requestingUser = anythingUser1,
                                            checkLastModDateChanged = false,
                                          )
          updateValueResponse <- valuesResponder(_.updateValueV2(updateValueParams, anythingUser1, randomUUID))
          _                    = intValueIri.set(updateValueResponse.valueIri)
          actualValue <- getValue(
                           resourceIri = resourceIri,
                           maybePreviousLastModDate = maybeResourceLastModDate,
                           propertyIriForGravsearch = propertyIri,
                           propertyIriInResult = propertyIri,
                           expectedValueIri = intValueIri.get,
                           requestingUser = anythingUser1,
                         )
          actualContent   <- asInstanceOf[IntegerValueContentV2](actualValue.valueContent)
          prevValueUuid   <- getValueUUID(previousValueFromTriplestore.valueIri)
          prevPermissions <- getValuePermissions(previousValueFromTriplestore.valueIri)
        } yield assertTrue(
          actualContent.valueHasInteger == intValue,
          actualValue.permissions == previousValueFromTriplestore.permissions,
          actualValue.valueHasUUID == previousValueFromTriplestore.valueHasUUID,
          actualValue.valueContent.comment.contains(comment),
          // Check that the permissions and UUID were deleted from the previous version of the value.
          prevValueUuid.isEmpty,
          prevPermissions.isEmpty,
        )
      },
      test("create an integer value with a comment") {
        val resourceIri = aThingIri
        val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
        val intValue    = 8
        val comment     = "Initial comment"
        val createValueParams = CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
            comment = Some(comment),
          ),
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          createValueResponse      <- valuesResponder(_.createValueV2(createValueParams, anythingUser1, randomUUID))
          _                         = intValueIri.set(createValueResponse.valueIri)
          actualValue <- getValue(
                           resourceIri = resourceIri,
                           maybePreviousLastModDate = maybeResourceLastModDate,
                           propertyIriForGravsearch = propertyIri,
                           propertyIriInResult = propertyIri,
                           expectedValueIri = intValueIri.get,
                           requestingUser = anythingUser1,
                         )
          actualValueContent <- asInstanceOf[IntegerValueContentV2](actualValue.valueContent)
        } yield assertTrue(
          actualValueContent.valueHasInteger == intValue,
          actualValueContent.comment.contains(comment),
        )
      },
      test("create an integer value with custom permissions") {
        val resourceIri = aThingIri
        val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
        val intValue    = 1
        val permissions = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"

        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          createValueResponse <- valuesResponder(
                                   _.createValueV2(
                                     CreateValueV2(
                                       resourceIri = resourceIri,
                                       resourceClassIri = Anything.thingClass.smartIri,
                                       propertyIri = propertyIri,
                                       valueContent = IntegerValueContentV2(
                                         ontologySchema = ApiV2Complex,
                                         valueHasInteger = intValue,
                                       ),
                                       permissions = Some(permissions),
                                     ),
                                     requestingUser = anythingUser1,
                                     apiRequestID = randomUUID,
                                   ),
                                 )
          _ = intValueIriWithCustomPermissions.set(createValueResponse.valueIri)
          valueFromTriplestore <- getValue(
                                    resourceIri = resourceIri,
                                    maybePreviousLastModDate = maybeResourceLastModDate,
                                    propertyIriForGravsearch = propertyIri,
                                    propertyIriInResult = propertyIri,
                                    expectedValueIri = intValueIriWithCustomPermissions.get,
                                    requestingUser = anythingUser1,
                                  )
          actualValueContent <- asInstanceOf[IntegerValueContentV2](valueFromTriplestore.valueContent)
        } yield assertTrue(
          actualValueContent.valueHasInteger == intValue,
          PermissionUtilADM.parsePermissions(valueFromTriplestore.permissions) == PermissionUtilADM.parsePermissions(
            permissions,
          ),
        )
      },
      test("not create an integer value with syntactically invalid custom permissions") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 1024
        val permissions = "M knora-admin:Creator,V knora-admin:KnownUser"

        val createValueParams = CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
          ),
          permissions = Some(permissions),
        )
        valuesResponder(
          _.createValueV2(createValueParams, requestingUser = anythingUser1, apiRequestID = randomUUID),
        ).exit
          .map(err => assert(err)(failsWithA[BadRequestException]))
      },
      test("not create an integer value with custom permissions referring to a nonexistent group") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 1024
        val permissions = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"
        val createValueParams = CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
          permissions = Some(permissions),
        )
        valuesResponder(
          _.createValueV2(createValueParams, requestingUser = anythingUser1, apiRequestID = randomUUID),
        ).exit
          .map(err => assert(err)(failsWithA[NotFoundException]))
      },
      test("create an integer value with custom UUID and creation date") {
        // Add the value.
        val resourceIri       = aThingIri
        val propertyIri       = Anything.hasInteger.smartIri
        val intValue          = 987
        val valueUUID         = randomUUID
        val valueCreationDate = Instant.now
        val createValueParams = CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
          valueUUID = Some(valueUUID),
          valueCreationDate = Some(valueCreationDate),
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          createValueResponse      <- valuesResponder(_.createValueV2(createValueParams, anythingUser1, randomUUID))
          _                         = intValueForRsyncIri.set(createValueResponse.valueIri)
          valueFromTriplestore <- getValue(
                                    resourceIri = resourceIri,
                                    maybePreviousLastModDate = maybeResourceLastModDate,
                                    propertyIriForGravsearch = propertyIri,
                                    propertyIriInResult = propertyIri,
                                    expectedValueIri = intValueForRsyncIri.get,
                                    requestingUser = anythingUser1,
                                  )
          savedValue <- asInstanceOf[IntegerValueContentV2](valueFromTriplestore.valueContent)
        } yield assertTrue(
          savedValue.valueHasInteger == intValue,
          valueFromTriplestore.valueHasUUID == valueUUID,
          valueFromTriplestore.valueCreationDate == valueCreationDate,
        )
      },
      test(
        "not update an integer value with a custom creation date that is earlier than the date of the current version",
      ) {
        val resourceIri       = aThingIri
        val propertyIri       = Anything.hasInteger.smartIri
        val intValue          = 989
        val valueCreationDate = Instant.parse("2019-11-29T10:00:00Z")

        val updateValueParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueForRsyncIri.get,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
          valueCreationDate = Some(valueCreationDate),
        )
        valuesResponder(_.updateValueV2(updateValueParams, anythingUser1, randomUUID)).exit.map(err =>
          assert(err)(failsWithA[BadRequestException]),
        )
      },
      test("update an integer value with a custom creation date") {
        val resourceIri       = aThingIri
        val propertyIri       = Anything.hasInteger.smartIri
        val intValue          = 988
        val valueCreationDate = Instant.now

        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          // Update the value.
          updateValueResponse <-
            valuesResponder(
              _.updateValueV2(
                UpdateValueContentV2(
                  resourceIri = resourceIri,
                  resourceClassIri = Anything.thingClass.smartIri,
                  propertyIri = propertyIri,
                  valueIri = intValueForRsyncIri.get,
                  valueContent = IntegerValueContentV2(
                    ontologySchema = ApiV2Complex,
                    valueHasInteger = intValue,
                  ),
                  valueCreationDate = Some(valueCreationDate),
                ),
                anythingUser1,
                randomUUID,
              ),
            )
          _ = intValueForRsyncIri.set(updateValueResponse.valueIri)
          updatedValueFromTriplestore <- getValue(
                                           resourceIri = resourceIri,
                                           maybePreviousLastModDate = maybeResourceLastModDate,
                                           propertyIriForGravsearch = propertyIri,
                                           propertyIriInResult = propertyIri,
                                           expectedValueIri = intValueForRsyncIri.get,
                                           requestingUser = anythingUser1,
                                         )
          savedValue <- asInstanceOf[IntegerValueContentV2](updatedValueFromTriplestore.valueContent)
        } yield assertTrue(
          savedValue.valueHasInteger == intValue,
          updatedValueFromTriplestore.valueCreationDate == valueCreationDate,
        )
      },
      test("update an integer value with a custom new version IRI") {
        val resourceIri        = aThingIri
        val propertyIri        = Anything.hasInteger.smartIri
        val intValue           = 1000
        val newValueVersionIri = sf.makeRandomValueIri(resourceIri)

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueForRsyncIri.get,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
          newValueVersionIri = Some(newValueVersionIri.toSmartIri),
        )

        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          // Update the value.
          updateValueResponse <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
          _                    = intValueForRsyncIri.set(updateValueResponse.valueIri)
          updatedValueFromTriplestore <- getValue(
                                           resourceIri = resourceIri,
                                           maybePreviousLastModDate = maybeResourceLastModDate,
                                           propertyIriForGravsearch = propertyIri,
                                           propertyIriInResult = propertyIri,
                                           expectedValueIri = intValueForRsyncIri.get,
                                           requestingUser = anythingUser1,
                                         )
          savedValue <- asInstanceOf[IntegerValueContentV2](updatedValueFromTriplestore.valueContent)
        } yield assertTrue(
          savedValue.valueHasInteger == intValue,
          updatedValueFromTriplestore.valueIri == (newValueVersionIri),
        )
      },
      test("not create a value if the user does not have modify permission on the resource") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 5

        val createParams = CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
        )
        valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID)).exit.map { actual =>
          assert(actual)(failsWithA[ForbiddenException])
        }
      },
      test("not update an integer value if an outdated value IRI is given") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 3

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = firstIntValueVersionIri.get,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
        )
        valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[NotFoundException]))
      },
      test("not update an integer value if the user does not have modify permission on the value") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val intValue    = 9

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
        )
        valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test("update an integer value with custom permissions") {
        val resourceIri = aThingIri
        val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
        val permissions = "CR knora-admin:Creator|V knora-admin:ProjectMember"
        val intValue    = 6

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
          ),
          permissions = Some(permissions),
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
          _                         = intValueIri.set(updateValueResponse.valueIri)
          updatedValueFromTriplestore <- getValue(
                                           resourceIri = resourceIri,
                                           maybePreviousLastModDate = maybeResourceLastModDate,
                                           propertyIriForGravsearch = propertyIri,
                                           propertyIriInResult = propertyIri,
                                           expectedValueIri = intValueIri.get,
                                           requestingUser = anythingUser1,
                                         )
          savedValue <- asInstanceOf[IntegerValueContentV2](updatedValueFromTriplestore.valueContent)
        } yield assertTrue(
          savedValue.valueHasInteger == intValue,
          updatedValueFromTriplestore.permissions == permissions,
        )
      },
      test(
        "not update an integer value with custom permissions if the requesting user does not have Permission.ObjectAccess.ChangeRights on the value",
      ) {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val permissions = "CR knora-admin:Creator"
        val intValue    = 10

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
          ),
          permissions = Some(permissions),
        )
        valuesResponder(_.updateValueV2(updateParams, anythingUser2, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test("not update an integer value with syntactically invalid custom permissions") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val permissions = "M knora-admin:Creator,V knora-admin:KnownUser"
        val intValue    = 7

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
          ),
          permissions = Some(permissions),
        )
        valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[BadRequestException]))
      },
      test("not update an integer value with custom permissions referring to a nonexistent group") {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val permissions = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"
        val intValue    = 8

        val updateParams = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
          ),
          permissions = Some(permissions),
        )
        valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[NotFoundException]))
      },
      test("update an integer value, changing only its permissions") {
        val resourceIri = aThingIri
        val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
        val permissions = "CR knora-admin:Creator|V knora-admin:KnownUser"

        val updatePermissionParams = UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = KA.IntValue.toSmartIri,
          permissions = permissions,
        )
        for {
          oldValueFromTriplestore <- getValue(
                                       resourceIri = resourceIri,
                                       maybePreviousLastModDate = None,
                                       propertyIriForGravsearch = propertyIri,
                                       propertyIriInResult = propertyIri,
                                       expectedValueIri = intValueIri.get,
                                       requestingUser = anythingUser1,
                                       checkLastModDateChanged = false,
                                     )
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
          updateValueResponse      <- valuesResponder(_.updateValueV2(updatePermissionParams, anythingUser1, randomUUID))
          _                         = intValueIri.set(updateValueResponse.valueIri)
          updatedValueFromTriplestore <- getValue(
                                           resourceIri = resourceIri,
                                           maybePreviousLastModDate = maybeResourceLastModDate,
                                           propertyIriForGravsearch = propertyIri,
                                           propertyIriInResult = propertyIri,
                                           expectedValueIri = intValueIri.get,
                                           requestingUser = anythingUser1,
                                         )
        } yield assertTrue(
          updatedValueFromTriplestore.valueContent == oldValueFromTriplestore.valueContent,
          updatedValueFromTriplestore.permissions == permissions,
        )
      },
      test(
        "not update an integer value, changing only its permissions, if the requesting user does not have Permission.ObjectAccess.ChangeRights on the value",
      ) {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val permissions = "CR knora-admin:Creator"

        val updatePermissionsParams = UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = KA.IntValue.toSmartIri,
          permissions = permissions,
        )
        valuesResponder(_.updateValueV2(updatePermissionsParams, anythingUser2, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test(
        "not update an integer value, changing only its permissions, with syntactically invalid custom permissions",
      ) {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val permissions = "M knora-admin:Creator,V knora-admin:KnownUser"

        val updatePermissionParams = UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = KA.IntValue.toSmartIri,
          permissions = permissions,
        )
        valuesResponder(_.updateValueV2(updatePermissionParams, anythingUser1, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[BadRequestException]))
      },
      test(
        "not update an integer value, changing only its permissions, with permissions referring to a nonexistent group",
      ) {
        val resourceIri = aThingIri
        val propertyIri = Anything.hasInteger.smartIri
        val permissions = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

        val updatePermissionsParams = UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = Anything.thingClass.smartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = KA.IntValue.toSmartIri,
          permissions = permissions,
        )
        valuesResponder(_.updateValueV2(updatePermissionsParams, anythingUser1, randomUUID)).exit
          .map(actual => assert(actual)(failsWithA[NotFoundException]))
      },
      test("not delete an integer value if the requesting user does not have DeletePermission on the value") {
        val resourceIri      = ResourceIri.unsafeFrom(aThingIri.toSmartIri)
        val propertyIri      = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri)
        val resourceClassIri = ResourceClassIri.unsafeFrom(Anything.thingClass.smartIri)
        val deleteParams = DeleteValueV2(
          resourceIri,
          resourceClassIri,
          propertyIri,
          intValueIri.asValueIri,
          valueTypeIri = KA.IntValue.toSmartIri,
          deleteComment = Some("this value was incorrect"),
          apiRequestId = randomUUID,
        )
        valuesResponder(_.deleteValueV2(deleteParams, anythingUser2)).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test("delete an integer value") {
        val resourceIri      = ResourceIri.unsafeFrom(aThingIri.toSmartIri)
        val propertyIri      = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri)
        val resourceClassIri = ResourceClassIri.unsafeFrom(Anything.thingClass.smartIri)
        val deleteParams = DeleteValueV2(
          resourceIri,
          resourceClassIri,
          propertyIri,
          intValueIri.asValueIri,
          valueTypeIri = KA.IntValue.toSmartIri,
          deleteComment = Some("this value was incorrect"),
          apiRequestId = randomUUID,
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri.toString, anythingUser1)
          _                        <- valuesResponder(_.deleteValueV2(deleteParams, anythingUser1))
          _ <- checkValueIsDeleted(
                 resourceIri,
                 maybeResourceLastModDate,
                 intValueIri.asValueIri,
                 requestingUser = anythingUser1,
               )
        } yield assertCompletes
      },
      test("delete an integer value, specifying a custom delete date") {
        val resourceIri         = ResourceIri.unsafeFrom(aThingIri.toSmartIri)
        val propertyIri         = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri)
        val resourceClassIri    = ResourceClassIri.unsafeFrom(Anything.thingClass.smartIri)
        val deleteDate: Instant = Instant.now
        val deleteComment       = Some("this value was incorrect")

        val deleteParams = DeleteValueV2(
          resourceIri,
          resourceClassIri,
          propertyIri,
          ValueIri.unsafeFrom(intValueForRsyncIri.get.toSmartIri),
          valueTypeIri = KA.IntValue.toSmartIri,
          deleteComment = deleteComment,
          deleteDate = Some(deleteDate),
          apiRequestId = randomUUID,
        )
        for {
          maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri.toString, anythingUser1)
          _                        <- valuesResponder(_.deleteValueV2(deleteParams, anythingUser1))
          _ <- checkValueIsDeleted(
                 resourceIri,
                 maybeResourceLastModDate,
                 ValueIri.unsafeFrom(intValueForRsyncIri.get.toSmartIri),
                 customDeleteDate = Some(deleteDate),
                 deleteComment = deleteComment,
                 requestingUser = anythingUser1,
               )
        } yield assertCompletes
      },
    )
  }

  override val e2eSpec = suite("ValuesResponderV2")(
    test("Load test data") {
      ZIO
        .serviceWithZIO[StandoffResponderV2](_.getMappingV2("http://rdfh.ch/standoff/mappings/StandardMapping"))
        .tap(r => ZIO.succeed(self.standardMapping = Some(r.mapping)))
        .as(assertCompletes)
    },
    integerValueSuite,
    test("create a text value without standoff") {
      val valueHasString = "Comment 1a"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val createParams = CreateValueV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          textValueType = TextValueType.UnformattedText,
        ),
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinCommentWithoutStandoffIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinCommentWithoutStandoffIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasString.contains(valueHasString))
    },
    test("create a text value with a comment") {
      val valueHasString  = "this is a text value that has a comment"
      val valueHasComment = "this is a comment"
      val propertyIri     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val createParams = CreateValueV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          comment = Some(valueHasComment),
          textValueType = TextValueType.UnformattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinCommentWithCommentIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinCommentWithCommentIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasString.contains(valueHasString),
        savedValue.comment.contains(valueHasComment),
      )
    },
    test("create a text value with standoff") {
      val valueHasString = "Comment 1aa"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val createParams = CreateValueV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          standoff = sampleStandoff,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinCommentWithStandoffIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinCommentWithStandoffIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasString.contains(valueHasString),
        savedValue.standoff == sampleStandoff,
        savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"),
        savedValue.mapping == standardMapping,
      )
    },
    test("create a decimal value") {
      val resourceIri     = aThingIri
      val propertyIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal = BigDecimal("4.3")

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = DecimalValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasDecimal = valueHasDecimal,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
        _                         = decimalValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = decimalValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[DecimalValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasDecimal == valueHasDecimal)
    },
    test("create a time value") {
      val resourceIri       = aThingIri
      val propertyIri       = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp = Instant.parse("2019-08-28T15:59:12.725007Z")

      val createValue = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = TimeValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasTimeStamp = valueHasTimeStamp,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createValue, anythingUser1, randomUUID))
        _                         = timeValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = timeValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[TimeValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasTimeStamp == valueHasTimeStamp)
    },
    test("create a date value") {
      val resourceIri = aThingIri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264907,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265271,
        valueHasEndPrecision = DatePrecisionYear,
      )
      val createValuParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = submittedValueContent,
      )

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createValuParams, anythingUser1, randomUUID))
        _                         = dateValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = dateValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[DateValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasCalendar == (submittedValueContent.valueHasCalendar),
        savedValue.valueHasStartJDN == (submittedValueContent.valueHasStartJDN),
        savedValue.valueHasStartPrecision == (submittedValueContent.valueHasStartPrecision),
        savedValue.valueHasEndJDN == (submittedValueContent.valueHasEndJDN),
        savedValue.valueHasEndPrecision == (submittedValueContent.valueHasEndPrecision),
      )
    },
    test("create a boolean value") {
      val resourceIri     = aThingIri
      val propertyIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean = true
      val createValueParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = BooleanValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasBoolean = valueHasBoolean,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createValueParams, anythingUser1, randomUUID))
        _                         = booleanValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = booleanValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[BooleanValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasBoolean == valueHasBoolean)
    },
    test("create a geometry value") {
      // Add the value.

      val resourceIri = aThingIri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

      val createValueParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = GeomValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasGeometry = valueHasGeometry,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createValueParams, anythingUser1, randomUUID))
        _                         = geometryValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = geometryValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[GeomValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasGeometry == valueHasGeometry)
    },
    test("create an interval value") {
      val resourceIri           = aThingIri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.2")
      val valueHasIntervalEnd   = BigDecimal("3")

      val createValueParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = IntervalValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasIntervalStart = valueHasIntervalStart,
          valueHasIntervalEnd = valueHasIntervalEnd,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createValueParams, anythingUser1, randomUUID))
        _                         = intervalValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = intervalValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[IntervalValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasIntervalStart == valueHasIntervalStart,
        savedValue.valueHasIntervalEnd == valueHasIntervalEnd,
      )
    },
    test("create a list value") {
      val resourceIri      = aThingIri
      val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
        _                         = listValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = listValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[HierarchicalListValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasListNode == valueHasListNode)
    },
    test("not create a list value referring to a nonexistent list node") {
      val resourceIri      = aThingIri
      val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent"

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
      )
      valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[NotFoundException]))
    },
    test("not create a list value that is a root list node") {
      val resourceIri      = "http://rdfh.ch/0001/a-blue-thing"
      val resourceClassIri = "http://www.knora.org/ontology/0001/anything#BlueThing".toSmartIri
      val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/otherTreeList"
      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = resourceClassIri,
        propertyIri = propertyIri,
        valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
      )
      valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("create a color value") {
      val resourceIri   = aThingIri
      val propertyIri   = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor = "#ff3333"

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = ColorValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasColor = valueHasColor,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
        _                         = colorValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = colorValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[ColorValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasColor == valueHasColor)
    },
    test("create a URI value") {
      val resourceIri = aThingIri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri = "https://www.knora.org"

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = UriValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasUri = valueHasUri,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
        _                         = uriValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = uriValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[UriValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasUri == valueHasUri)
    },
    test("create a geoname value") {
      val resourceIri         = aThingIri
      val propertyIri         = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode = "2661604"

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = GeonameValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasGeonameCode = valueHasGeonameCode,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
        _                         = geonameValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = geonameValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[GeonameValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasGeonameCode == valueHasGeonameCode)
    },
    test("create a link between two resources") {
      val resourceIri                    = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri                = KA.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = KA.HasLinkToValue.toSmartIri

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        propertyIri = linkValuePropertyIri,
        resourceClassIri = KA.LinkObj.toSmartIri,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = zeitgloeckleinIri,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = linkValueIri.set(createValueResponse.valueIri)
        _                         = { self.linkValueUUID = createValueResponse.valueUUID }
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = linkPropertyIri,
                                  propertyIriInResult = linkValuePropertyIri,
                                  expectedValueIri = linkValueIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[ReadLinkValueV2](valueFromTriplestore)
      } yield assertTrue(
        savedValue.valueContent.referredResourceIri == zeitgloeckleinIri,
        savedValue.valueHasRefCount == 1,
      )
    },
    test("not accept a link property in a request to create a link between two resources") {
      val resourceIri     = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri = KA.HasLinkTo.toSmartIri

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = KA.LinkObj.toSmartIri,
        propertyIri = linkPropertyIri,
        valueContent = LinkValueContentV2(ApiV2Complex, zeitgloeckleinIri),
      )
      valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a standoff link directly") {
      val createParams = CreateValueV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = KA.HasStandoffLinkToValue.toSmartIri,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = generationeIri,
        ),
      )
      valuesResponder(_.createValueV2(createParams, superUser, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not add a new value to a nonexistent resource") {
      val resourceIri = "http://rdfh.ch/0001/nonexistent"
      val propertyIri = Anything.hasInteger.smartIri
      val intValue    = 6

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueContent = IntegerValueContentV2(ApiV2Complex, intValue),
      )
      valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID)).exit
        .map(actual => assert(actual)(diesWithA[NotFoundException]))
    },
    test("not add a new value to a deleted resource") {
      val resourceIri    = "http://rdfh.ch/0803/9935159f67"
      val valueHasString = "Comment 2"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          textValueType = TextValueType.UnformattedText,
        ),
      )
      valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID)).exit
        .map(actual => assert(actual)(diesWithA[NotFoundException]))
    },
    test("not add a new value if the resource's rdf:type is not correctly given") {
      val resourceIri = aThingIri
      val propertyIri = Anything.hasInteger.smartIri
      val intValue    = 2048

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = IntegerValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasInteger = intValue,
        ),
      )
      valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not add a new value of the wrong type") {
      val resourceIri = "http://rdfh.ch/0803/21abac2162"
      val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate".toSmartIri

      val createParms = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some("this is not a date"),
          textValueType = TextValueType.UnformattedText,
        ),
      )
      valuesResponder(_.createValueV2(createParms, incunabulaMemberUser, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
    },
    test(
      "not add a new value that would violate a cardinality restriction: " +
        "The cardinality of incunabula:partOf in incunabula:page is 1, and page http://rdfh.ch/0803/4f11adaf is already part of a book.",
    ) {
      val resourceIri = "http://rdfh.ch/0803/4f11adaf"
      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
        propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue".toSmartIri,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = "http://rdfh.ch/0803/e41ab5695c",
        ),
      )
      valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
    },
    test(
      "not add a new value that would violate a cardinality restriction: " +
        "The cardinality of incunabula:seqnum in incunabula:page is 0-1, and page http://rdfh.ch/0803/4f11adaf already has a seqnum.",
    ) {
      val createParams = CreateValueV2(
        resourceIri = "http://rdfh.ch/0803/4f11adaf",
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
        propertyIri = "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri,
        valueContent = IntegerValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasInteger = 1,
        ),
      )
      valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
    },
    test(
      "add a new text value containing a Standoff resource reference, and create a hasStandoffLinkTo direct link and a corresponding LinkValue",
    ) {
      val resourceIri    = "http://rdfh.ch/0803/21abac2162"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val valueHasString = "This comment refers to another resource"

      val standoff = Seq(
        StandoffTagV2(
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          startPosition = 31,
          endPosition = 39,
          attributes = Vector(
            StandoffTagIriAttributeV2(
              standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
              value = zeitgloeckleinIri,
            ),
          ),
          uuid = randomUUID(),
          originalXMLID = None,
          startIndex = 0,
        ),
      )

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          standoff = standoff,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = lobComment1Iri.set(createValueResponse.valueIri)
        updatedResource <- getResourceWithValues(
                             resourceIri = resourceIri,
                             propertyIrisForGravsearch = Seq(propertyIri, KA.HasStandoffLinkTo.toSmartIri),
                             requestingUser = incunabulaMemberUser,
                           )
        _ <- checkLastModDate(resourceIri, maybeResourceLastModDate, updatedResource.lastModificationDate)
        textValueFromTriplestore = getValueFromResource(
                                     resource = updatedResource,
                                     propertyIriInResult = propertyIri,
                                     expectedValueIri = lobComment1Iri.get,
                                   )
        savedTextValue <- asInstanceOf[TextValueContentV2](textValueFromTriplestore.valueContent)

        // Since this is the first Standoff resource reference between the source and target resources, we should
        // now have version 1 of a LinkValue (it should have no previous version), with a reference count of 1.
        linkValuesFromTriplestore = getValuesFromResource(
                                      resource = updatedResource,
                                      propertyIriInResult = KA.HasStandoffLinkToValue.toSmartIri,
                                    )
        linkValueFromTriplestore <- asInstanceOf[ReadLinkValueV2](linkValuesFromTriplestore.head)
        _                         = standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
      } yield assertTrue(
        savedTextValue.valueHasString.contains(valueHasString),
        savedTextValue.standoff == standoff,
        savedTextValue.mapping == standardMapping,
        savedTextValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"),
        linkValuesFromTriplestore.size == 1,
        linkValueFromTriplestore.previousValueIri.isEmpty,
        linkValueFromTriplestore.valueHasRefCount == 1,
        linkValueFromTriplestore.valueContent.referredResourceIri == zeitgloeckleinIri,
      )
    },
    test(
      "add another new text value containing a Standoff resource reference, and make a new version of the LinkValue",
    ) {
      val resourceIri    = "http://rdfh.ch/0803/21abac2162"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val valueHasString = "This remark refers to another resource"

      val standoff = Seq(
        StandoffTagV2(
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          startPosition = 30,
          endPosition = 38,
          attributes = Vector(
            StandoffTagIriAttributeV2(
              standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
              value = zeitgloeckleinIri,
            ),
          ),
          uuid = randomUUID(),
          originalXMLID = None,
          startIndex = 0,
        ),
      )

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          standoff = standoff,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = lobComment2Iri.set(createValueResponse.valueIri)
        updatedResource <- getResourceWithValues(
                             resourceIri = resourceIri,
                             propertyIrisForGravsearch = Seq(propertyIri, KA.HasStandoffLinkTo.toSmartIri),
                             requestingUser = incunabulaMemberUser,
                           )
        _ <- checkLastModDate(
               resourceIri = resourceIri,
               maybePreviousLastModDate = maybeResourceLastModDate,
               maybeUpdatedLastModDate = updatedResource.lastModificationDate,
             )
        textValueFromTriplestore = getValueFromResource(
                                     resource = updatedResource,
                                     propertyIriInResult = propertyIri,
                                     expectedValueIri = lobComment2Iri.get,
                                   )
        savedTextValue <- asInstanceOf[TextValueContentV2](textValueFromTriplestore.valueContent)
        // Now that we've added a different TextValue that refers to the same resource, we should have version 2
        // of the LinkValue, with a reference count of 2. It should have a previousValue pointing to the previous
        // version.
        linkValuesFromTriplestore = getValuesFromResource(
                                      resource = updatedResource,
                                      propertyIriInResult = KA.HasStandoffLinkToValue.toSmartIri,
                                    )
        linkValueFromTriplestore    <- asInstanceOf[ReadLinkValueV2](linkValuesFromTriplestore.head)
        previousStandoffLinkValueIri = standoffLinkValueIri.get
        _                            = standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
      } yield assertTrue(
        savedTextValue.valueHasString.contains(valueHasString),
        savedTextValue.standoff == (standoff),
        savedTextValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"),
        savedTextValue.mapping == (standardMapping),
        linkValuesFromTriplestore.size == 1,
        linkValueFromTriplestore.previousValueIri.contains(previousStandoffLinkValueIri),
        linkValueFromTriplestore.valueHasRefCount == 2,
        linkValueFromTriplestore.valueContent.referredResourceIri == (zeitgloeckleinIri),
      )
    },
    test("update a text value (without submitting standoff)") {
      val valueHasString = "This updated comment has no standoff"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val updateParams = UpdateValueContentV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueIri = zeitgloeckleinCommentWithoutStandoffIri.get,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          textValueType = TextValueType.UnformattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinCommentWithoutStandoffIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinCommentWithoutStandoffIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasString.contains(valueHasString))
    },
    test("update a text value (submitting standoff)") {
      val valueHasString = "Comment 1ab"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val updateParams = UpdateValueContentV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueIri = zeitgloeckleinCommentWithStandoffIri.get,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          standoff = sampleStandoffWithLink,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinCommentWithStandoffIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinCommentWithStandoffIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)

        // There==  a link value for a standoff link.
        resource <- getResourceWithValues(
                      resourceIri = zeitgloeckleinIri,
                      propertyIrisForGravsearch = Seq(KA.HasStandoffLinkTo.toSmartIri),
                      requestingUser = incunabulaMemberUser,
                    )
        standoffLinkValues = getValuesFromResource(
                               resource = resource,
                               propertyIriInResult = KA.HasStandoffLinkToValue.toSmartIri,
                             )
        standoffLinkValueFromTriplestore = standoffLinkValues.head
        _                                = standoffLinkValueIri.set(standoffLinkValueFromTriplestore.valueIri)
        linkValueContentV2              <- asInstanceOf[LinkValueContentV2](standoffLinkValueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasString.contains(valueHasString),
        savedValue.standoff == sampleStandoffWithLink,
        savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"),
        savedValue.mapping == standardMapping,
        standoffLinkValues.size == 1,
        linkValueContentV2.referredResourceIri == aThingIri,
      )
    },
    test("create a second text value with standoff") {
      val valueHasString = "Comment 1ac"

      val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val createParams = CreateValueV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          standoff = sampleStandoff,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinSecondCommentWithStandoffIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinSecondCommentWithStandoffIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasString.contains(valueHasString),
        savedValue.standoff == (sampleStandoff),
        savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"),
        savedValue.mapping == (standardMapping),
      )
    },
    test("update a text value, changing only the standoff") {
      val valueHasString = "Comment 1ac"
      val propertyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val updateParams = UpdateValueContentV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueIri = zeitgloeckleinSecondCommentWithStandoffIri.get,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some(valueHasString),
          standoff = sampleStandoffModified,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID))
        _                         = zeitgloeckleinSecondCommentWithStandoffIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = zeitgloeckleinIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = zeitgloeckleinSecondCommentWithStandoffIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        savedValue <- asInstanceOf[TextValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasString.contains(valueHasString),
        savedValue.standoff == sampleStandoffModified,
        savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"),
        savedValue.mapping == standardMapping,
      )
    },
    test("update a decimal value") {
      val resourceIri     = aThingIri
      val propertyIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal = BigDecimal("3.1415926")

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = decimalValueIri.get,
        valueContent = DecimalValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasDecimal = valueHasDecimal,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = decimalValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = decimalValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[DecimalValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasDecimal == valueHasDecimal)
    },
    test("update a time value") {
      val resourceIri       = aThingIri
      val propertyIri       = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp = Instant.parse("2019-08-28T16:01:46.952237Z")

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = timeValueIri.get,
        valueContent = TimeValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasTimeStamp = valueHasTimeStamp,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = timeValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = timeValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[TimeValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasTimeStamp == valueHasTimeStamp)
    },
    test("update a date value") {
      val resourceIri = aThingIri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264908,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265272,
        valueHasEndPrecision = DatePrecisionYear,
      )

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = dateValueIri.get,
        valueContent = submittedValueContent,
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = dateValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = dateValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[DateValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasCalendar == submittedValueContent.valueHasCalendar,
        savedValue.valueHasStartJDN == submittedValueContent.valueHasStartJDN,
        savedValue.valueHasStartPrecision == submittedValueContent.valueHasStartPrecision,
        savedValue.valueHasEndJDN == submittedValueContent.valueHasEndJDN,
        savedValue.valueHasEndPrecision == submittedValueContent.valueHasEndPrecision,
      )
    },
    test("update a boolean value") {
      val resourceIri     = aThingIri
      val propertyIri     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean = false

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = booleanValueIri.get,
        valueContent = BooleanValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasBoolean = valueHasBoolean,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = booleanValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = booleanValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[BooleanValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasBoolean == valueHasBoolean)
    },
    test("update a geometry value") {
      val resourceIri = aThingIri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = geometryValueIri.get,
        valueContent = GeomValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasGeometry = valueHasGeometry,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = geometryValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = geometryValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[GeomValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasGeometry == valueHasGeometry)
    },
    test("update an interval value") {
      val resourceIri           = aThingIri
      val propertyIri           = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.23")
      val valueHasIntervalEnd   = BigDecimal("3.45")

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = intervalValueIri.get,
        valueContent = IntervalValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasIntervalStart = valueHasIntervalStart,
          valueHasIntervalEnd = valueHasIntervalEnd,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = intervalValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = intervalValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[IntervalValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(
        savedValue.valueHasIntervalStart == valueHasIntervalStart,
        savedValue.valueHasIntervalEnd == valueHasIntervalEnd,
      )
    },
    test("update a list value") {
      val resourceIri      = aThingIri
      val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/treeList02"

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = listValueIri.get,
        valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = listValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = listValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[HierarchicalListValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasListNode == valueHasListNode)
    },
    test("not update a list value with the IRI of a nonexistent list node") {
      val resourceIri      = aThingIri
      val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent"

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = listValueIri.get,
        valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
      )
      valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[NotFoundException]))
    },
    test("update a color value") {
      val resourceIri   = aThingIri
      val propertyIri   = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor = "#ff3334"

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = colorValueIri.get,
        valueContent = ColorValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasColor = valueHasColor,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = colorValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = colorValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[ColorValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasColor == valueHasColor)
    },
    test("update a URI value") {
      val resourceIri = aThingIri
      val propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri = "https://en.wikipedia.org"

      val updateParms = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = uriValueIri.get,
        valueContent = UriValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasUri = valueHasUri,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParms, anythingUser1, randomUUID))
        _                         = uriValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = uriValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[UriValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasUri == valueHasUri)
    },
    test("update a geoname value") {
      val resourceIri         = aThingIri
      val propertyIri         = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode = "2988507"

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = Anything.thingClass.smartIri,
        propertyIri = propertyIri,
        valueIri = geonameValueIri.get,
        valueContent = GeonameValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasGeonameCode = valueHasGeonameCode,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                         = geonameValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = None,
                                  propertyIriForGravsearch = propertyIri,
                                  propertyIriInResult = propertyIri,
                                  expectedValueIri = geonameValueIri.get,
                                  requestingUser = anythingUser1,
                                )
        savedValue <- asInstanceOf[GeonameValueContentV2](valueFromTriplestore.valueContent)
      } yield assertTrue(savedValue.valueHasGeonameCode == valueHasGeonameCode)
    },
    test("update a link between two resources") {
      val resourceIri                    = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri                = KA.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = KA.HasLinkToValue.toSmartIri

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = KA.LinkObj.toSmartIri,
        propertyIri = linkValuePropertyIri,
        valueIri = linkValueIri.get,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = generationeIri,
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID))
        // When you change a link value's target, it gets a new UUID.
        oldLinkValueUUID = linkValueUUID
        _                = linkValueIri.set(updateValueResponse.valueIri)
        _                = { self.linkValueUUID = updateValueResponse.valueUUID }
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = linkPropertyIri,
                                  propertyIriInResult = linkValuePropertyIri,
                                  expectedValueIri = linkValueIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        readLinkValueV2 <- asInstanceOf[ReadLinkValueV2](valueFromTriplestore)
      } yield assertTrue(
        updateValueResponse.valueUUID != oldLinkValueUUID,
        readLinkValueV2.valueContent.referredResourceIri == generationeIri,
        readLinkValueV2.valueHasRefCount == 1,
      )
    },
    test("update a link, adding a comment") {
      val resourceIri          = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri      = KA.HasLinkTo.toSmartIri
      val linkValuePropertyIri = KA.HasLinkToValue.toSmartIri
      val comment              = "Adding a comment"

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = KA.LinkObj.toSmartIri,
        propertyIri = linkValuePropertyIri,
        valueIri = linkValueIri.get,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = generationeIri,
          comment = Some(comment),
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID))
        _                         = linkValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = linkPropertyIri,
                                  propertyIriInResult = linkValuePropertyIri,
                                  expectedValueIri = linkValueIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        readLinkValueV2 <- asInstanceOf[ReadLinkValueV2](valueFromTriplestore)
      } yield assertTrue(
        // Since we only changed metadata, the link should have the same UUID.
        updateValueResponse.valueUUID == self.linkValueUUID,
        readLinkValueV2.valueContent.referredResourceIri == generationeIri,
        readLinkValueV2.valueHasRefCount == 1,
        readLinkValueV2.valueContent.comment.contains(comment),
      )
    },
    test("update a link with a comment, changing only the comment") {
      val resourceIri                    = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri                = KA.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = KA.HasLinkToValue.toSmartIri
      val comment                        = "An updated comment"

      val updateParams = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri = KA.LinkObj.toSmartIri,
        propertyIri = linkValuePropertyIri,
        valueIri = linkValueIri.get,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = generationeIri,
          comment = Some(comment),
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        updateValueResponse      <- valuesResponder(_.updateValueV2(updateParams, incunabulaMemberUser, randomUUID))
        _                         = linkValueIri.set(updateValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = linkPropertyIri,
                                  propertyIriInResult = linkValuePropertyIri,
                                  expectedValueIri = linkValueIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        readLinkValueV2 <- asInstanceOf[ReadLinkValueV2](valueFromTriplestore)
      } yield assertTrue(
        // Since we only changed metadata, the link should have the same UUID.
        updateValueResponse.valueUUID == self.linkValueUUID,
        readLinkValueV2.valueContent.referredResourceIri == generationeIri,
        readLinkValueV2.valueHasRefCount == 1,
        readLinkValueV2.valueContent.comment.contains(comment),
      )
    },
    test("create a link with a comment") {
      val resourceIri                    = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri                = KA.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = KA.HasLinkToValue.toSmartIri
      val comment                        = "Initial comment"

      val createParams = CreateValueV2(
        resourceIri = resourceIri,
        propertyIri = linkValuePropertyIri,
        resourceClassIri = KA.LinkObj.toSmartIri,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = zeitgloeckleinIri,
          comment = Some(comment),
        ),
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, incunabulaMemberUser)
        createValueResponse      <- valuesResponder(_.createValueV2(createParams, incunabulaMemberUser, randomUUID))
        _                         = linkValueIri.set(createValueResponse.valueIri)
        valueFromTriplestore <- getValue(
                                  resourceIri = resourceIri,
                                  maybePreviousLastModDate = maybeResourceLastModDate,
                                  propertyIriForGravsearch = linkPropertyIri,
                                  propertyIriInResult = linkValuePropertyIri,
                                  expectedValueIri = linkValueIri.get,
                                  requestingUser = incunabulaMemberUser,
                                )
        readLinkValueV2 <- asInstanceOf[ReadLinkValueV2](valueFromTriplestore)
      } yield assertTrue(
        readLinkValueV2.valueContent.referredResourceIri == zeitgloeckleinIri,
        readLinkValueV2.valueHasRefCount == 1,
        readLinkValueV2.valueContent.comment.contains(comment),
      )
    },
    test("not update a standoff link directly") {
      val updateParams = UpdateValueContentV2(
        resourceIri = zeitgloeckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = KA.HasStandoffLinkToValue.toSmartIri,
        valueIri = zeitgloeckleinCommentWithStandoffIri.get,
        valueContent = LinkValueContentV2(
          ontologySchema = ApiV2Complex,
          referredResourceIri = generationeIri,
        ),
      )
      valuesResponder(_.updateValueV2(updateParams, superUser, randomUUID)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("update a still image file value") {
      stillImageFileValueIri.set("http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q")
      val resourceIri = aThingPictureIri
      val propertyIri = KA.HasStillImageFileValue.toSmartIri

      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri, anythingUser1)
        // Get the value before update.
        previousValueFromTriplestore <- getValue(
                                          resourceIri = resourceIri,
                                          maybePreviousLastModDate = maybeResourceLastModDate,
                                          propertyIriForGravsearch = propertyIri,
                                          propertyIriInResult = propertyIri,
                                          expectedValueIri = stillImageFileValueIri.get,
                                          requestingUser = anythingUser1,
                                          checkLastModDateChanged = false,
                                        )

        // Update the value.
        dimX             = 512
        dimY             = 256
        internalFilename = "updated-filename.jp2"
        internalMimeType = mimeTypeJP2
        originalFilename = Some("test.tiff")
        originalMimeType = Some(mimeTypeTIFF)
        updateParams = UpdateValueContentV2(
                         resourceIri,
                         thingPictureClassIri.toSmartIri,
                         propertyIri,
                         stillImageFileValueIri.get,
                         FileModelUtil.getFileValueContent(
                           FileType.StillImageFile(dimX = dimX, dimY = dimY),
                           internalFilename,
                           Some(internalMimeType),
                           originalFilename,
                           originalMimeType,
                         ),
                       )
        updateValueResponse <- valuesResponder(_.updateValueV2(updateParams, anythingUser1, randomUUID))
        _                    = stillImageFileValueIri.set(updateValueResponse.valueIri)
        updatedValueFromTriplestore <- getValue(
                                         resourceIri = resourceIri,
                                         maybePreviousLastModDate = maybeResourceLastModDate,
                                         propertyIriForGravsearch = propertyIri,
                                         propertyIriInResult = propertyIri,
                                         expectedValueIri = stillImageFileValueIri.get,
                                         requestingUser = anythingUser1,
                                       )
        savedValue <- asInstanceOf[StillImageFileValueContentV2](updatedValueFromTriplestore.valueContent)
      } yield assertTrue(
        updatedValueFromTriplestore.valueIri == (stillImageFileValueIri.get),
        savedValue.comment == (None),
        savedValue.dimX == (dimX),
        savedValue.dimY == (dimY),
        savedValue.fileValue.internalFilename == (internalFilename),
        savedValue.fileValue.internalMimeType == (internalMimeType),
        savedValue.fileValue.originalMimeType == (originalMimeType),
        savedValue.fileValue.originalFilename == (originalFilename),
        updatedValueFromTriplestore.permissions == (previousValueFromTriplestore.permissions),
      )
    },
    test("not delete a standoff link directly") {
      val deleteParams = DeleteValueV2(
        ResourceIri.unsafeFrom(zeitgloeckleinIri.toSmartIri),
        ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri),
        PropertyIri.unsafeFrom(KA.HasStandoffLinkToValue.toSmartIri),
        standoffLinkValueIri.asValueIri,
        valueTypeIri = KA.LinkValue.toSmartIri,
        apiRequestId = randomUUID,
      )
      valuesResponder(_.deleteValueV2(deleteParams, superUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("delete a text value with a standoff link") {
      val propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val deleteParams = DeleteValueV2(
        ResourceIri.unsafeFrom(zeitgloeckleinIri.toSmartIri),
        ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri),
        PropertyIri.unsafeFrom(propertyIri),
        ValueIri.unsafeFrom(zeitgloeckleinCommentWithStandoffIri.get.toSmartIri),
        valueTypeIri = KA.TextValue.toSmartIri,
        deleteComment = Some("this value was incorrect"),
        apiRequestId = randomUUID,
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(zeitgloeckleinIri, incunabulaMemberUser)
        _                        <- valuesResponder(_.deleteValueV2(deleteParams, incunabulaMemberUser))
        _ <- checkValueIsDeleted(
               ResourceIri.unsafeFrom(zeitgloeckleinIri.toSmartIri),
               maybeResourceLastModDate,
               ValueIri.unsafeFrom(zeitgloeckleinCommentWithStandoffIri.get.toSmartIri),
               requestingUser = incunabulaMemberUser,
             )
        // There==  no standoff link values left in the resource.
        resource <- getResourceWithValues(
                      resourceIri = zeitgloeckleinIri,
                      propertyIrisForGravsearch = Seq(KA.HasStandoffLinkTo.toSmartIri),
                      requestingUser = incunabulaMemberUser,
                    )
      } yield assertTrue(!resource.values.contains(KA.HasStandoffLinkToValue.toSmartIri))
    },
    test("delete a link between two resources") {
      val resourceIri          = ResourceIri.unsafeFrom("http://rdfh.ch/0803/cb1a74e3e2f6".toSmartIri)
      val linkValuePropertyIri = PropertyIri.unsafeFrom(KA.HasLinkToValue.toSmartIri)
      val linkValueIRI         = ValueIri.unsafeFrom(linkValueIri.get.toSmartIri)
      val deleteParams = DeleteValueV2(
        resourceIri,
        ResourceClassIri.unsafeFrom(KA.LinkObj.toSmartIri),
        linkValuePropertyIri,
        linkValueIRI,
        valueTypeIri = KA.LinkValue.toSmartIri,
        apiRequestId = randomUUID,
      )
      for {
        maybeResourceLastModDate <- getResourceLastModificationDate(resourceIri.toString, anythingUser1)
        _                        <- valuesResponder(_.deleteValueV2(deleteParams, incunabulaMemberUser))
        _ <- checkValueIsDeleted(
               resourceIri = resourceIri,
               maybePreviousLastModDate = maybeResourceLastModDate,
               valueIri = linkValueIRI,
               requestingUser = anythingUser1,
               isLinkValue = true,
             )
      } yield assertCompletes
    },
    test("not delete a value if the property's cardinality doesn't allow it") {
      val propertyIri = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri)
      val deleteParams = DeleteValueV2(
        ResourceIri.unsafeFrom(zeitgloeckleinIri.toSmartIri),
        ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri),
        propertyIri,
        ValueIri.unsafeFrom("http://rdfh.ch/0803/c5058f3a/values/c3295339".toSmartIri),
        valueTypeIri = KA.TextValue.toSmartIri,
        apiRequestId = randomUUID,
      )
      valuesResponder(_.deleteValueV2(deleteParams, incunabulaCreatorUser)).exit
        .map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
    },
    test(
      "not accept custom value permissions that would give the requesting user a higher permission on a value than the default",
    ) {
      val resourceIri = sf.makeRandomResourceIri(imagesProject.shortcode)
      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = imagesProject,
        permissions = Some("M knora-admin:ProjectMember"),
      )
      for {
        _ <- ZIO.serviceWithZIO[ResourcesResponderV2](
               _.createResource(CreateResourceRequestV2(inputResource, imagesUser01, randomUUID)),
             )
        propertyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri
        actual <-
          valuesResponder(
            _.createValueV2(
              CreateValueV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
                propertyIri = propertyIri,
                valueContent = IntegerValueContentV2(
                  ontologySchema = ApiV2Complex,
                  valueHasInteger = 5,
                  comment = Some("this is the number five"),
                ),
                permissions = Some("CR knora-admin:Creator"),
              ),
              requestingUser = imagesReviewerUser,
              apiRequestID = randomUUID,
            ),
          ).exit
      } yield assert(actual)(failsWithA[ForbiddenException])
    },
    test(
      "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a system admin",
    ) {
      val resourceIri = sf.makeRandomResourceIri(imagesProject.shortcode)
      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = imagesProject,
        permissions = Some("M knora-admin:ProjectMember"),
      )
      for {
        _ <- ZIO.serviceWithZIO[ResourcesResponderV2](
               _.createResource(CreateResourceRequestV2(inputResource, imagesUser01, randomUUID)),
             )
        propertyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri
        _ <- valuesResponder(
               _.createValueV2(
                 CreateValueV2(
                   resourceIri = resourceIri,
                   resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
                   propertyIri = propertyIri,
                   valueContent = IntegerValueContentV2(
                     ontologySchema = ApiV2Complex,
                     valueHasInteger = 5,
                     comment = Some("this is the number five"),
                   ),
                   permissions = Some("CR knora-admin:Creator"),
                 ),
                 requestingUser = rootUser,
                 apiRequestID = randomUUID,
               ),
             )
      } yield assertCompletes
    },
    test(
      "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a project admin",
    ) {
      val resourceIri = sf.makeRandomResourceIri(imagesProject.shortcode)

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = imagesProject,
        permissions = Some("M knora-admin:ProjectMember"),
      )
      for {
        _ <- ZIO.serviceWithZIO[ResourcesResponderV2](
               _.createResource(CreateResourceRequestV2(inputResource, imagesUser01, randomUUID)),
             )
        propertyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri
        _ <- valuesResponder(
               _.createValueV2(
                 CreateValueV2(
                   resourceIri = resourceIri,
                   resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
                   propertyIri = propertyIri,
                   valueContent = IntegerValueContentV2(
                     ontologySchema = ApiV2Complex,
                     valueHasInteger = 5,
                     comment = Some("this is the number five"),
                   ),
                   permissions = Some("CR knora-admin:Creator"),
                 ),
                 requestingUser = imagesUser01,
                 apiRequestID = randomUUID,
               ),
             )
      } yield assertCompletes
    },
    test("create and update text values with standoff links, managing value UUIDs correctly") {
      val resourceClassIri: SmartIri = Anything.thingClass.smartIri
      val propertyIri                = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri

      // Create a text value with a standoff link.
      val createParams = CreateValueV2(
        resourceIri = sierraIri,
        resourceClassIri = resourceClassIri,
        propertyIri = propertyIri,
        valueContent = TextValueContentV2(
          ontologySchema = ApiV2Complex,
          maybeValueHasString = Some("Comment 1 for UUID checking"),
          standoff = sampleStandoffWithLink,
          mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
          mapping = standardMapping,
          textValueType = TextValueType.FormattedText,
        ),
      )
      for {
        createValueResponse1 <- valuesResponder(_.createValueV2(createParams, anythingUser1, randomUUID))
        resourceVersion1 <- getResourceWithValues(
                              resourceIri = sierraIri,
                              propertyIrisForGravsearch = Seq(
                                propertyIri,
                                KA.HasStandoffLinkTo.toSmartIri,
                              ),
                              requestingUser = anythingUser1,
                            )
        // Get the UUIDs of the text value and of the standoff link value.
        textValue1 = resourceVersion1.values(propertyIri).head
        standoffLinkValueVersion1 <-
          asInstanceOf[ReadLinkValueV2](resourceVersion1.values(KA.HasStandoffLinkToValue.toSmartIri).head)
        standoffLinkValueVersion1ValueUuid <- getValueUUID(standoffLinkValueVersion1.valueIri)
        // Create a second text value with the same standoff link.
        createValueResponse2 <-
          valuesResponder(
            _.createValueV2(
              CreateValueV2(
                resourceIri = sierraIri,
                resourceClassIri = resourceClassIri,
                propertyIri = propertyIri,
                valueContent = TextValueContentV2(
                  ontologySchema = ApiV2Complex,
                  maybeValueHasString = Some("Comment 2 for UUID checking"),
                  standoff = sampleStandoffWithLink,
                  mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                  mapping = standardMapping,
                  textValueType = TextValueType.FormattedText,
                ),
              ),
              requestingUser = anythingUser1,
              apiRequestID = randomUUID,
            ),
          )
        resourceVersion2 <- getResourceWithValues(
                              resourceIri = sierraIri,
                              propertyIrisForGravsearch = Seq(
                                propertyIri,
                                KA.HasStandoffLinkTo.toSmartIri,
                              ),
                              requestingUser = anythingUser1,
                            )
        // Get the second text value's UUID.
        textValue2Version1 = resourceVersion2
                               .values(propertyIri)
                               .find(_.valueIri == createValueResponse2.valueIri)
                               .getOrElse(throw AssertionException("Value not found"))
        textValue2Version1ValueUuid <- getValueUUID(textValue2Version1.valueIri)
        // We should have a new version of the standoff link value, containing the UUID that was in the previous version.
        standoffLinkValueVersion2: ReadLinkValueV2 = resourceVersion2
                                                       .values(KA.HasStandoffLinkToValue.toSmartIri)
                                                       .head
                                                       .asInstanceOf[ReadLinkValueV2]
        // Update the second text value.
        updateValueResponse <-
          valuesResponder(
            _.updateValueV2(
              UpdateValueContentV2(
                resourceIri = sierraIri,
                resourceClassIri = resourceClassIri,
                propertyIri = propertyIri,
                valueIri = createValueResponse2.valueIri,
                valueContent = TextValueContentV2(
                  ontologySchema = ApiV2Complex,
                  maybeValueHasString = Some("Comment 3 for UUID checking"),
                  standoff = sampleStandoffWithLink,
                  mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                  mapping = standardMapping,
                  textValueType = TextValueType.FormattedText,
                ),
              ),
              anythingUser1,
              randomUUID,
            ),
          )

        resourceVersion3 <- getResourceWithValues(
                              resourceIri = sierraIri,
                              propertyIrisForGravsearch = Seq(
                                propertyIri,
                                KA.HasStandoffLinkTo.toSmartIri,
                              ),
                              requestingUser = anythingUser1,
                            )

        // We should now have a new version of the second text value, containing the UUID that was in the previous version.
        textValue2Version2 = resourceVersion3
                               .values(propertyIri)
                               .find(_.valueIri == updateValueResponse.valueIri)
                               .getOrElse(throw AssertionException("Value not found"))
        standoffLinkValueVersion2ValueUuid     <- getValueUUID(standoffLinkValueVersion2.valueIri)
        standoffLinkValueVersion1ValueIri      <- getValueUUID(standoffLinkValueVersion1.valueIri)
        textValue1ValueUuid                    <- getValueUUID(textValue1.valueIri)
        textValue2Version2ValueUuid            <- getValueUUID(textValue2Version2.valueIri)
        textValue2Version1ValueUuidAfterUpdate <- getValueUUID(textValue2Version1.valueIri)
      } yield assertTrue(
        textValue2Version1ValueUuid.contains(textValue2Version1.valueHasUUID),
        standoffLinkValueVersion1ValueUuid.contains(standoffLinkValueVersion1.valueHasUUID),
        standoffLinkValueVersion1.valueHasRefCount == 1,
        standoffLinkValueVersion2.previousValueIri.contains(standoffLinkValueVersion1.valueIri),
        standoffLinkValueVersion2.valueHasUUID == standoffLinkValueVersion1.valueHasUUID,
        standoffLinkValueVersion2ValueUuid.contains(standoffLinkValueVersion2.valueHasUUID),
        standoffLinkValueVersion2.valueHasRefCount == 2,

        // The previous version of the standoff link value should have no UUID.
        standoffLinkValueVersion1ValueIri.isEmpty,
        textValue1.valueIri == createValueResponse1.valueIri,
        textValue1ValueUuid.contains(textValue1.valueHasUUID),
        textValue2Version2ValueUuid.contains(textValue2Version2.valueHasUUID),
        textValue2Version2.previousValueIri.contains(textValue2Version1.valueIri),
        // The previous version of the second text value should have no UUID.
        textValue2Version1ValueUuidAfterUpdate.isEmpty,
        // We should not have a new version of the standoff link value.
        resourceVersion3
          .values(KA.HasStandoffLinkToValue.toSmartIri)
          .head
          .valueIri == standoffLinkValueVersion2.valueIri,
      )
    },
  )
}
