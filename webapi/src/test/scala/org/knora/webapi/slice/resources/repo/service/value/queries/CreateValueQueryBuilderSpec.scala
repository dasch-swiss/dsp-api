/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service.value.queries

import org.apache.jena.sparql.modify.request.UpdateModify
import org.apache.jena.sparql.modify.request.UpdateVisitorBase
import org.apache.jena.update.Update
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateRequest
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagIntegerAttributeV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagIriAttributeV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagTimeAttributeV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.common.domain.InternalIri

import org.knora.webapi.GoldenTest

object CreateValueQueryBuilderTestSupport {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  object TestDataFactory {

    val testDataGraph   = InternalIri("http://0.0.0.0:3333/data/0001/thing")
    val testResourceIri = InternalIri("http://0.0.0.0:3333/0001/thing/resource")
    val testPropertyIri = SmartIri("http://www.knora.org/ontology/0001/anything#hasText")
    val testValueIri    = InternalIri("http://0.0.0.0:3333/0001/thing/value")
    val testValueUUID   = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef")
    val testUserIri     = InternalIri("http://0.0.0.0:3333/users/9XBCrDV3SRa7kS1WwynB4Q")
    val testPermissions =
      "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
    val testCreationDate = Instant.parse("2023-08-01T10:30:00Z")

    def createTwirlQuery(
      value: ValueContentV2,
      linkUpdates: Seq[SparqlTemplateLinkUpdate] = Seq.empty,
    ): String =
      sparql.v2.txt
        .createValue(
          dataNamedGraph = testDataGraph.value,
          resourceIri = testResourceIri.value,
          propertyIri = testPropertyIri,
          newValueIri = testValueIri.value,
          newValueUUID = testValueUUID,
          value = value,
          linkUpdates = linkUpdates,
          valueCreator = testUserIri.value,
          valuePermissions = testPermissions,
          creationDate = testCreationDate,
          stringFormatter = sf,
        )
        .toString()
        .strip

    def createBuilderQuery(
      value: ValueContentV2,
      linkUpdates: Seq[SparqlTemplateLinkUpdate] = Seq.empty,
      newUuidOrCurrentIri: Either[UUID, InternalIri] = Left(testValueUUID),
    ): String =
      CreateValueQueryBuilder
        .createValueQuery(
          dataNamedGraph = testDataGraph,
          resourceIri = testResourceIri,
          propertyIri = testPropertyIri,
          newValueIri = testValueIri,
          newUuidOrCurrentIri = newUuidOrCurrentIri,
          value = value,
          linkUpdates = linkUpdates,
          valueCreator = testUserIri,
          valuePermissions = testPermissions,
          creationDate = testCreationDate,
        )
        .sparql

    def createTextValue(withComment: Boolean = false, withLanguage: Boolean = false): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Test text value"),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = Option.when(withLanguage)("en"),
        standoff = Vector.empty,
        mappingIri = None,
        comment = Option.when(withComment)("Test comment"),
      )

    def createIntegerValue(withComment: Boolean = false): IntegerValueContentV2 =
      IntegerValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasInteger = 42,
        comment = Option.when(withComment)("Test integer comment"),
      )

    def createDecimalValue(withComment: Boolean = false): DecimalValueContentV2 =
      DecimalValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasDecimal = BigDecimal("3.14159"),
        comment = Option.when(withComment)("Test decimal comment"),
      )

    def createBooleanValue(withComment: Boolean = false): BooleanValueContentV2 =
      BooleanValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasBoolean = true,
        comment = Option.when(withComment)("Test boolean comment"),
      )

    def createUriValue(withComment: Boolean = false): UriValueContentV2 =
      UriValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasUri = "https://example.com/test",
        comment = Option.when(withComment)("Test URI comment"),
      )

    def createDateValue(withComment: Boolean = false): DateValueContentV2 =
      DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasStartJDN = 2460189,
        valueHasEndJDN = 2460189,
        valueHasStartPrecision = DatePrecisionDay,
        valueHasEndPrecision = DatePrecisionDay,
        valueHasCalendar = CalendarNameGregorian,
        comment = Option.when(withComment)("Test date comment"),
      )

    def createColorValue(withComment: Boolean = false): ColorValueContentV2 =
      ColorValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasColor = "#FF0000",
        comment = Option.when(withComment)("Test color comment"),
      )

    def createGeomValue(withComment: Boolean = false): GeomValueContentV2 =
      GeomValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasGeometry =
          """|
             |{"type":"rectangle","lineColor":"#FF0000","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"original_index":0}
             |""".stripMargin,
        comment = Option.when(withComment)("Test geometry comment"),
      )

    def createIntervalValue(withComment: Boolean = false): IntervalValueContentV2 =
      IntervalValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasIntervalStart = BigDecimal("0.0"),
        valueHasIntervalEnd = BigDecimal("100.0"),
        comment = Option.when(withComment)("Test interval comment"),
      )

    def createTimeValue(withComment: Boolean = false): TimeValueContentV2 =
      TimeValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasTimeStamp = Instant.parse("2023-08-01T14:30:00Z"),
        comment = Option.when(withComment)("Test time comment"),
      )

    def createGeonameValue(withComment: Boolean = false): GeonameValueContentV2 =
      GeonameValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasGeonameCode = "2661604",
        comment = Option.when(withComment)("Test geoname comment"),
      )

    def createHierarchicalListValue(withComment: Boolean = false): HierarchicalListValueContentV2 =
      HierarchicalListValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasListNode = "http://rdfh.ch/lists/0001/treeList03",
        listNodeLabel = Some("Test List Node"),
        comment = Option.when(withComment)("Test list comment"),
      )

    def createTextValueWithStandoff(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffBoldTag),
          startPosition = 0,
          endPosition = 4,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 0,
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 23,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 1,
        ),
      )

      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Bold text with standoff"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("Test standoff comment"),
      )
    }

    def createTextValueWithStandoffLink(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffLinkTag),
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          startPosition = 0,
          endPosition = 4,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 0,
          attributes = Vector(
            StandoffTagIriAttributeV2(
              standoffPropertyIri = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffTagHasLink),
              value = "http://0.0.0.0:3333/0001/thing/linkedResource",
            ),
          ),
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 28,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 1,
        ),
      )

      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Link text with standoff link"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("Test standoff link comment"),
      )
    }

    def createSparqlTemplateLinkUpdate(
      newReferenceCount: Int = 1,
    ): SparqlTemplateLinkUpdate =
      SparqlTemplateLinkUpdate(
        linkPropertyIri = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        directLinkExists = false,
        insertDirectLink = true,
        deleteDirectLink = false,
        linkValueExists = false,
        linkTargetExists = true,
        newLinkValueIri = "http://0.0.0.0:3333/0001/thing/linkValue",
        linkTargetIri = "http://0.0.0.0:3333/0001/thing/linkedResource",
        currentReferenceCount = 0,
        newReferenceCount = newReferenceCount,
        newLinkValueCreator = testUserIri.value,
        newLinkValuePermissions = testPermissions,
      )

    def createTextValueWithVirtualHierarchyStandoff(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffBoldTag),
          startPosition = 8,
          endPosition = 14,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 0,
          endIndex = Some(2),
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffItalicTag),
          startPosition = 15,
          endPosition = 21,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 1,
          endIndex = Some(3),
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 26,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 4,
        ),
      )
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Complex nested italic bold"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("Virtual hierarchy standoff comment"),
      )
    }

    def createTextValueWithHierarchicalStandoff(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 35,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 0,
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffBoldTag),
          startPosition = 5,
          endPosition = 15,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 1,
          startParentIndex = Some(0),
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffItalicTag),
          startPosition = 20,
          endPosition = 30,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 2,
          startParentIndex = Some(0),
          endParentIndex = Some(0),
        ),
      )
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Text bold text and italic text end"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("Hierarchical standoff comment"),
      )
    }

    def createTextValueWithXMLIDStandoff(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffBoldTag),
          startPosition = 0,
          endPosition = 4,
          uuid = testValueUUID,
          originalXMLID = Some("bold-tag-1"),
          startIndex = 0,
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffItalicTag),
          startPosition = 10,
          endPosition = 16,
          uuid = testValueUUID,
          originalXMLID = Some("italic-tag-2"),
          startIndex = 1,
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 21,
          uuid = testValueUUID,
          originalXMLID = Some("paragraph-1"),
          startIndex = 2,
        ),
      )
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Bold text italic text"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("XML ID standoff comment"),
      )
    }

    def createTextValueWithStandoffInteger(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffIntegerTag),
          dataType = Some(StandoffDataTypeClasses.StandoffIntegerTag),
          startPosition = 0,
          endPosition = 2,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 0,
          attributes = Vector(
            StandoffTagIntegerAttributeV2(
              standoffPropertyIri = sf.toSmartIri(OntologyConstants.KnoraBase.ValueHasInteger),
              value = 42,
            ),
          ),
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 22,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 1,
        ),
      )
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("42 is the answer here"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("Standoff integer attribute comment"),
      )
    }

    def createTextValueWithStandoffTime(withComment: Boolean = false): TextValueContentV2 = {
      val standoffTags = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffTimeTag),
          dataType = Some(StandoffDataTypeClasses.StandoffTimeTag),
          startPosition = 0,
          endPosition = 10,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 0,
          attributes = Vector(
            StandoffTagTimeAttributeV2(
              standoffPropertyIri = sf.toSmartIri(OntologyConstants.KnoraBase.ValueHasTimeStamp),
              value = Instant.parse("2023-08-01T14:30:00Z"),
            ),
          ),
        ),
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffParagraphTag),
          startPosition = 0,
          endPosition = 24,
          uuid = testValueUUID,
          originalXMLID = None,
          startIndex = 1,
        ),
      )
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("2023-08-01 is the date"),
        textValueType = TextValueType.FormattedText,
        valueHasLanguage = None,
        standoff = standoffTags,
        mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
        comment = Option.when(withComment)("Standoff time attribute comment"),
      )
    }

    // Edge case test data factories
    def createTextValueWithEmptyString(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some(""),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = None,
        standoff = Vector.empty,
        mappingIri = None,
        comment = None,
      )

    def createTextValueWithUnicode(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Zürich 🇨🇭 Test with émojis and àccénts"),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = Some("de"),
        standoff = Vector.empty,
        mappingIri = None,
        comment = Some("Unicode test comment with 中文"),
      )

    def createTextValueWithVeryLongString(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("A" * 10000),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = None,
        standoff = Vector.empty,
        mappingIri = None,
        comment = None,
      )

    def createIntegerValueWithMaxValue(): IntegerValueContentV2 =
      IntegerValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasInteger = Int.MaxValue,
        comment = None,
      )

    def createIntegerValueWithMinValue(): IntegerValueContentV2 =
      IntegerValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasInteger = Int.MinValue,
        comment = None,
      )

    def createDecimalValueWithZero(): DecimalValueContentV2 =
      DecimalValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasDecimal = BigDecimal("0.0"),
        comment = None,
      )

    def createDecimalValueWithVeryPrecise(): DecimalValueContentV2 =
      DecimalValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasDecimal = BigDecimal("3.141592653589793238462643383279502884197169399375105820974944592307816406286"),
        comment = None,
      )

    def createUriValueWithSpecialChars(): UriValueContentV2 =
      UriValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasUri = "https://example.com/path?query=test&param=value#fragment",
        comment = None,
      )

    def createColorValueWithTransparent(): ColorValueContentV2 =
      ColorValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasColor = "#00000000",
        comment = None,
      )

    def createIntervalValueWithZeroRange(): IntervalValueContentV2 =
      IntervalValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasIntervalStart = BigDecimal("50.0"),
        valueHasIntervalEnd = BigDecimal("50.0"),
        comment = None,
      )

    // Security test data factories - SPARQL injection attempts
    def createTextValueWithSparqlInjection(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some(
          """'; DELETE DATA { ?s ?p ?o } ; INSERT DATA { <http://malicious.com/resource> <http://malicious.com/property> "injected" } ; SELECT * WHERE { ?s ?p ?o } #""",
        ),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = None,
        standoff = Vector.empty,
        mappingIri = None,
        comment = None,
      )

    def createTextValueWithQuotesAndNewlines(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("""Text with "quotes" and 'apostrophes' and
        newlines and \n escaped chars"""),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = None,
        standoff = Vector.empty,
        mappingIri = None,
        comment = None,
      )

    def createTextValueWithControlChars(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Text with\ttabs\rand\ncarriage\u0000returns\u001fand control chars"),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = None,
        standoff = Vector.empty,
        mappingIri = None,
        comment = None,
      )

    def createUriValueWithSparqlKeywords(): UriValueContentV2 =
      UriValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasUri = "http://example.com/SELECT?WHERE=FILTER&INSERT=DELETE",
        comment = None,
      )

    def createTextValueWithCommentInjection(): TextValueContentV2 =
      TextValueContentV2(
        ontologySchema = ApiV2Complex,
        maybeValueHasString = Some("Normal text"),
        textValueType = TextValueType.UnformattedText,
        valueHasLanguage = None,
        standoff = Vector.empty,
        mappingIri = None,
        comment = Some("""'; DROP ALL; INSERT MALICIOUS DATA { <evil> <prop> "value" } ; #"""),
      )

    def createGeonameValueWithMaliciousCode(): GeonameValueContentV2 =
      GeonameValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasGeonameCode = """2661604'; DELETE WHERE { ?s ?p ?o } ; INSERT DATA { <hack> <prop> "bad" } #""",
        comment = None,
      )

    def createUriValueWithDataUri(): UriValueContentV2 =
      UriValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasUri = "data:text/plain;base64,U0VMRUNUICoKV0hFUkUgeyA/cyA/cCA/byB9", // "SELECT * WHERE { ?s ?p ?o }"
        comment = None,
      )
  }

  // Helper to compare SPARQL queries for strict equivalence
  def compareSparqlQueries(twirlQuery: String, builderQuery: String) =
    for {
      parsedTwirlUpdate   <- ZIO.attempt(UpdateFactory.create(twirlQuery))
      parsedBuilderUpdate <- ZIO.attempt(UpdateFactory.create(builderQuery))
      normalizedTwirl      = replaceUuidPatterns(parsedTwirlUpdate.toString)
      normalizedBuilder    = replaceUuidPatterns(parsedBuilderUpdate.toString)
      assertEqual          = assertTrue(normalizedBuilder == normalizedTwirl)
    } yield assertEqual

  def replaceUuidPatterns(sparqlQuery: String): String = {
    val uuidPattern = """knora-base:valueHasUUID\s+"[^"]+"\s*[.;]""".r
    uuidPattern.replaceAllIn(sparqlQuery, """knora-base:valueHasUUID "0000000000000000000000" .""")
  }
}

object CreateValueQueryBuilderSpec extends ZIOSpecDefault with GoldenTest {
  val rewriteAll: Boolean = false

  import CreateValueQueryBuilderTestSupport.*

  private def validateLiteralContains(
    parsedQuery: UpdateRequest,
    predicate: String,
    validator: String => Boolean,
    errorMessage: String,
  ): Task[Unit] =
    ZIO.attempt {
      parsedQuery.getOperations.forEach(_.visit(new UpdateVisitorBase {
        override def visit(update: UpdateModify): Unit =
          update
            .getInsertQuads()
            .asScala
            .toList
            .find(_.getPredicate.toString == predicate)
            .fold(throw new AssertionError("Expected property not found")) { quad =>
              val value = quad.getObject.getLiteralValue.toString
              if (!validator(value)) {
                throw new AssertionError(errorMessage)
              }
            }
      }))
    }

  private def validateUriContains(
    parsedQuery: UpdateRequest,
    validator: String => Boolean,
    errorMessage: String,
  ): Task[Unit] =
    ZIO.attempt {
      parsedQuery.getOperations.forEach(_.visit(new UpdateVisitorBase {
        override def visit(update: UpdateModify): Unit =
          update
            .getInsertQuads()
            .asScala
            .toList
            .find(_.getPredicate.toString == OntologyConstants.KnoraBase.ValueHasUri)
            .fold(throw new AssertionError("Expected property not found")) { quad =>
              val value = quad.getObject.toString
              if (!validator(value)) {
                throw new AssertionError(errorMessage)
              }
            }
      }))
    }

  // Comparison test suites per value type

  def spec = suite("CreateValueQueryBuilderSpec")(
    suite("Builder vs Twirl template comparison")(
      suite("TextValueContentV2")(
        test("without comment or language") {
          val testValue = TestDataFactory.createTextValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with comment") {
          val testValue = TestDataFactory.createTextValue(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with language") {
          val testValue = TestDataFactory.createTextValue(withLanguage = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with comment and language") {
          val testValue = TestDataFactory.createTextValue(withComment = true, withLanguage = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff") {
          val testValue = TestDataFactory.createTextValueWithStandoff()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff and comment") {
          val testValue = TestDataFactory.createTextValueWithStandoff(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff link") {
          val testValue   = TestDataFactory.createTextValueWithStandoffLink()
          val linkUpdates = Seq(TestDataFactory.createSparqlTemplateLinkUpdate())
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue, linkUpdates))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue, linkUpdates))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with virtual hierarchy standoff") {
          val testValue = TestDataFactory.createTextValueWithVirtualHierarchyStandoff()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with virtual hierarchy standoff and comment") {
          val testValue = TestDataFactory.createTextValueWithVirtualHierarchyStandoff(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with hierarchical standoff") {
          val testValue = TestDataFactory.createTextValueWithHierarchicalStandoff()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with hierarchical standoff and comment") {
          val testValue = TestDataFactory.createTextValueWithHierarchicalStandoff(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with XML ID standoff") {
          val testValue = TestDataFactory.createTextValueWithXMLIDStandoff()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with XML ID standoff and comment") {
          val testValue = TestDataFactory.createTextValueWithXMLIDStandoff(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff integer attribute") {
          val testValue = TestDataFactory.createTextValueWithStandoffInteger()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff integer attribute and comment") {
          val testValue = TestDataFactory.createTextValueWithStandoffInteger(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff time attribute") {
          val testValue = TestDataFactory.createTextValueWithStandoffTime()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with standoff time attribute and comment") {
          val testValue = TestDataFactory.createTextValueWithStandoffTime(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("IntegerValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createIntegerValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
        test("with comment") {
          val testValue = TestDataFactory.createIntegerValue(withComment = true)
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("DecimalValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createDecimalValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("BooleanValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createBooleanValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("UriValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createUriValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("DateValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createDateValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("ColorValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createColorValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("GeomValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createGeomValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("IntervalValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createIntervalValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("TimeValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createTimeValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("GeonameValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createGeonameValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
      suite("HierarchicalListValueContentV2")(
        test("without comment") {
          val testValue = TestDataFactory.createHierarchicalListValue()
          for {
            twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
            builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
            assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
          } yield assertion
        },
      ),
    ),
    suite("Query structure validation")(
      test("Generated queries are valid SPARQL") {
        val testValue = TestDataFactory.createIntegerValue()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          exit         <- ZIO.attempt(UpdateFactory.create(builderQuery)).exit
        } yield assert(exit)(succeeds(anything))
      },
      test("Generated queries have correct structure (DELETE/INSERT/WHERE)") {
        val testValue = TestDataFactory.createTextValue()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
        } yield assertTrue(
          builderQuery.contains("DELETE") &&
            builderQuery.contains("INSERT") &&
            builderQuery.contains("WHERE"),
        )
      },
    ),
    suite("Edge cases")(
      test("Text value with empty string") {
        val testValue = TestDataFactory.createTextValueWithEmptyString()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Text value with Unicode characters") {
        val testValue = TestDataFactory.createTextValueWithUnicode()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Text value with very long string") {
        val testValue = TestDataFactory.createTextValueWithVeryLongString()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Integer value with maximum value") {
        val testValue = TestDataFactory.createIntegerValueWithMaxValue()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Integer value with minimum value") {
        val testValue = TestDataFactory.createIntegerValueWithMinValue()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Decimal value with zero") {
        val testValue = TestDataFactory.createDecimalValueWithZero()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Decimal value with very high precision") {
        val testValue = TestDataFactory.createDecimalValueWithVeryPrecise()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("URI value with special characters") {
        val testValue = TestDataFactory.createUriValueWithSpecialChars()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Color value with transparency") {
        val testValue = TestDataFactory.createColorValueWithTransparent()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
      test("Interval value with zero range") {
        val testValue = TestDataFactory.createIntervalValueWithZeroRange()
        for {
          twirlQuery   <- ZIO.attempt(TestDataFactory.createTwirlQuery(testValue))
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          assertion    <- compareSparqlQueries(twirlQuery, builderQuery)
        } yield assertion
      },
    ),
    suite("Security - Input sanitization and injection prevention")(
      test("Text value with SPARQL injection attempt is properly sanitized") {
        val testValue = TestDataFactory.createTextValueWithSparqlInjection()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateLiteralContains(
                                parsedQuery,
                                OntologyConstants.KnoraBase.ValueHasString,
                                _.startsWith(
                                  """'; DELETE DATA { ?s ?p ?o } ; INSERT DATA { <http://malicious.com/resource> <http://malicious.com/property> "injected" } ; SELECT * WHERE { ?s ?p ?o } #""",
                                ),
                                "SPARQL injection succeeded - malicious string not found as literal in valueHasString",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
      test("Text value with quotes and newlines are properly escaped") {
        val testValue = TestDataFactory.createTextValueWithQuotesAndNewlines()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateLiteralContains(
                                parsedQuery,
                                OntologyConstants.KnoraBase.ValueHasString,
                                value =>
                                  value.contains("""Text with "quotes" and 'apostrophes'""")
                                    && value.contains("and\n        newlines and \\n escaped chars"),
                                "Quotes and newlines not properly preserved in valueHasString literal",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
      test("Text value with control characters are safely handled") {
        val testValue = TestDataFactory.createTextValueWithControlChars()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateLiteralContains(
                                parsedQuery,
                                OntologyConstants.KnoraBase.ValueHasString,
                                value => value.contains("Text with\ttabs\rand\ncarriage\u0000returns\u001fand"),
                                "Control characters not properly preserved in valueHasString literal",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
      test("URI value with SPARQL keywords is treated as literal data") {
        val testValue = TestDataFactory.createUriValueWithSparqlKeywords()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateUriContains(
                                parsedQuery,
                                value =>
                                  value.contains("SELECT") && value.contains("WHERE") && value.contains("INSERT"),
                                "URI with SPARQL keywords not properly preserved in valueHasUri",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
      test("Comment with injection attempt is properly sanitized") {
        val testValue = TestDataFactory.createTextValueWithCommentInjection()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateLiteralContains(
                                parsedQuery,
                                OntologyConstants.KnoraBase.ValueHasComment,
                                value => value.contains("DROP ALL") && value.contains("INSERT MALICIOUS DATA"),
                                "Malicious comment content not properly preserved in valueHasComment literal",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
      test("Geoname value with malicious code is sanitized") {
        val testValue = TestDataFactory.createGeonameValueWithMaliciousCode()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateLiteralContains(
                                parsedQuery,
                                OntologyConstants.KnoraBase.ValueHasGeonameCode,
                                value => value.contains("DELETE WHERE") && value.contains("INSERT DATA"),
                                "Malicious geoname code not properly preserved in valueHasGeonameCode literal",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
      test("Data URI with encoded SPARQL is treated as literal") {
        val testValue = TestDataFactory.createUriValueWithDataUri()
        for {
          builderQuery <- ZIO.attempt(TestDataFactory.createBuilderQuery(testValue))
          parsedQuery  <- ZIO.attempt(UpdateFactory.create(builderQuery))
          validationResult <- validateUriContains(
                                parsedQuery,
                                _.contains("data:text/plain;base64"),
                                "Data URI not properly preserved in valueHasUri",
                              ).exit
          assertOnlyOneOperation = assertTrue(parsedQuery.getOperations.size == 1)
          assertNoMaliciousOps   = assertTrue(validationResult.isSuccess)
        } yield assertOnlyOneOperation && assertNoMaliciousOps
      },
    ),
    suite("With currentValue")(
      test("Basic case") {
        for {
          testValue <- ZIO.succeed(TestDataFactory.createTextValue())
          builderQuery <- ZIO.attempt(
                            TestDataFactory.createBuilderQuery(
                              testValue,
                              newUuidOrCurrentIri =
                                Right(InternalIri.from("http://rdfh.ch/0803/861b5644b302").toOption.get),
                            ),
                          )
        } yield assertGolden(builderQuery, "currentValue")
      },
      test("With link updates") {
        for {
          testValue   <- ZIO.succeed(TestDataFactory.createTextValue())
          linkUpdates <- ZIO.succeed(Seq(TestDataFactory.createSparqlTemplateLinkUpdate()))
          builderQuery <- ZIO.attempt(
                            TestDataFactory.createBuilderQuery(
                              testValue,
                              linkUpdates = linkUpdates,
                              newUuidOrCurrentIri =
                                Right(InternalIri.from("http://rdfh.ch/0803/861b5644b302").toOption.get),
                            ),
                          )
        } yield assertGolden(replaceUuidPatterns(builderQuery), "currentValue__withLinkUpdates")
      },
      test("With a deleted link update") {
        for {
          testValue   <- ZIO.succeed(TestDataFactory.createTextValue())
          linkUpdates <- ZIO.succeed(Seq(TestDataFactory.createSparqlTemplateLinkUpdate(newReferenceCount = 0)))
          builderQuery <- ZIO.attempt(
                            TestDataFactory.createBuilderQuery(
                              testValue,
                              linkUpdates = linkUpdates,
                              newUuidOrCurrentIri =
                                Right(InternalIri.from("http://rdfh.ch/0803/861b5644b302").toOption.get),
                            ),
                          )
        } yield assertGolden(replaceUuidPatterns(builderQuery), "currentValue__withLinkUpdatesDeleted")
      },
    ),
  )
}
