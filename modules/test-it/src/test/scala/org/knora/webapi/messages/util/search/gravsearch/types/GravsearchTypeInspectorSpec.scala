/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import org.scalatest.matchers.must.Matchers.*
import zio.*

import dsp.errors.GravsearchException
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

class GravsearchTypeInspectorSpec extends E2ESpec {

  private implicit val sf: StringFormatter = StringFormatter.getGeneralInstance

  val QueryWithExplicitTypeAnnotations: String =
    """
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |
      |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
      |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
      |
      |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
      |    ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
      |
      |} WHERE {
      |    ?letter a knora-api:Resource .
      |    ?letter a beol:letter .
      |
      |    # Scheuchzer, Johann Jacob 1672-1733
      |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
      |    ?linkingProp1 knora-api:objectType knora-api:Resource .
      |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |
      |    <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .
      |
      |    # Hermann, Jacob 1678-1733
      |    ?letter ?linkingProp2 <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
      |    ?linkingProp2 knora-api:objectType knora-api:Resource .
      |
      |    FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient)
      |
      |    beol:hasAuthor knora-api:objectType knora-api:Resource .
      |    beol:hasRecipient knora-api:objectType knora-api:Resource .
      |
      |    <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
      |}
        """.stripMargin

  val WhereClauseWithoutAnnotations: WhereClause = WhereClause(
    patterns = Vector(
      StatementPattern(
        obj = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri),
        pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
        subj = QueryVariable(variableName = "letter"),
      ),
      StatementPattern(
        obj = IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri),
        pred = QueryVariable(variableName = "linkingProp1"),
        subj = QueryVariable(variableName = "letter"),
      ),
      StatementPattern(
        obj = IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri),
        pred = QueryVariable(variableName = "linkingProp2"),
        subj = QueryVariable(variableName = "letter"),
      ),
      FilterPattern(expression =
        OrExpression(
          rightArg = CompareExpression(
            rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
            operator = CompareExpressionOperator.EQUALS,
            leftArg = QueryVariable(variableName = "linkingProp2"),
          ),
          leftArg = CompareExpression(
            rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
            operator = CompareExpressionOperator.EQUALS,
            leftArg = QueryVariable(variableName = "linkingProp2"),
          ),
        ),
      ),
      FilterPattern(expression =
        OrExpression(
          rightArg = CompareExpression(
            rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
            operator = CompareExpressionOperator.EQUALS,
            leftArg = QueryVariable(variableName = "linkingProp1"),
          ),
          leftArg = CompareExpression(
            rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
            operator = CompareExpressionOperator.EQUALS,
            leftArg = QueryVariable(variableName = "linkingProp1"),
          ),
        ),
      ),
    ),
  )

  val QueryRdfTypeRule: String =
    """
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:creationDate ?date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:creationDate ?date .
      |    beol:creationDate knora-api:objectType knora-api:Date .
      |    ?date a knora-api:Date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
      |    ?linkingProp1 knora-api:objectType knora-api:Resource .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
      |    beol:hasFamilyName knora-api:objectType xsd:string .
      |    ?name a xsd:string .
      |
      |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |} ORDER BY ?date
        """.stripMargin

  val QueryNonKnoraTypeWithoutAnnotation: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX dcterms: <http://purl.org/dc/terms/>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true ;
      |        dcterms:title ?title .
      |
      |} WHERE {
      |
      |    ?book dcterms:title ?title .
      |}
        """.stripMargin

  val QueryNonKnoraTypeWithAnnotation: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX dcterms: <http://purl.org/dc/terms/>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true ;
      |        dcterms:title ?title .
      |
      |} WHERE {
      |
      |    ?book rdf:type incunabula:book ;
      |        dcterms:title ?title .
      |
      |    ?title a xsd:string .
      |}
        """.stripMargin

  val QueryWithInconsistentTypes1: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?page incunabula:title ?book .
      |}
        """.stripMargin

  val QueryWithInconsistentTypes2: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true ;
      |        incunabula:title ?title ;
      |        incunabula:pubdate ?pubdate .
      |} WHERE {
      |    ?book a incunabula:book ;
      |        incunabula:title ?title ;
      |        incunabula:pubdate ?pubdate .
      |
      |  FILTER(?pubdate = "JULIAN:1497-03-01") .
      |}
        """.stripMargin

  val QueryComparingResourceIriInSimpleSchema: String =
    """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:hasAuthor ?person1 .
      |    ?letter beol:hasRecipient ?person2 .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:hasAuthor ?person1 .
      |    ?letter beol:hasRecipient ?person2 .
      |    FILTER(?person1 != <http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA>) .
      |}
      |OFFSET 0""".stripMargin

  val QueryComparingResourceIriInSimpleSchemaResult: GravsearchTypeInspectionResult =
    GravsearchTypeInspectionResult(
      entities = Map(
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
        ) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
          objectIsValueType = false,
        ),
        TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableVariable(variableName = "person1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableIri(iri = "http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
          objectIsValueType = false,
        ),
      ),
      entitiesInferredFromProperties = Map(
        TypeableVariable(variableName = "person2") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
            isResourceType = true,
            isValueType = false,
          ),
        ),
        TypeableVariable(variableName = "person1") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
            isResourceType = true,
            isValueType = false,
          ),
        ),
      ),
    )

  val QueryComparingResourceIriInComplexSchema: String =
    """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:hasAuthor ?person1 .
      |    ?letter beol:hasRecipient ?person2 .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:hasAuthor ?person1 .
      |    ?letter beol:hasRecipient ?person2 .
      |    FILTER(?person1 != <http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA>) .
      |}
      |OFFSET 0""".stripMargin

  val QueryComparingResourceIriInComplexSchemaResult: GravsearchTypeInspectionResult =
    GravsearchTypeInspectionResult(
      entities = Map(
        TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          objectIsResourceType = true,
          objectIsValueType = false,
        ),
        TypeableVariable(variableName = "person1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          objectIsResourceType = true,
          objectIsValueType = false,
        ),
        TypeableIri(iri = "http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
      ),
      entitiesInferredFromProperties = Map(
        TypeableVariable(variableName = "person1") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
            isResourceType = true,
            isValueType = false,
          ),
        ),
        TypeableVariable(variableName = "person2") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
            isResourceType = true,
            isValueType = false,
          ),
        ),
      ),
    )

  val QueryWithFilterComparison: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |
      |CONSTRUCT {
      |  ?person knora-api:isMainResource true .
      |  ?document beol:hasAuthor ?person .
      |} WHERE {
      |  ?person a beol:person .
      |  ?document beol:hasAuthor ?person .
      |  FILTER(?document != <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g>)
      |}""".stripMargin

  val QueryWithFilterComparisonResult: GravsearchTypeInspectionResult =
    GravsearchTypeInspectionResult(
      entities = Map(
        TypeableVariable(variableName = "person") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableVariable(variableName = "document") -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
          objectIsValueType = false,
        ),
        TypeableIri(iri = "http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
      ),
      entitiesInferredFromProperties = Map(
        TypeableVariable(variableName = "document") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            isResourceType = true,
            isValueType = false,
          ),
        ),
        TypeableVariable(variableName = "person") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
            isResourceType = true,
            isValueType = false,
          ),
        ),
      ),
    )

  val TypeInferenceResult1: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
      TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
      TypeableVariable(variableName = "date") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
        isValueType = true,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableIri(iri =
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri,
      ) -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableIri(iri = "http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        isResourceType = true,
      ),
      TypeableVariable(variableName = "name") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isValueType = true,
      ),
      TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
        isResourceType = true,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
    ),
  )

  val TypeInferenceResult3: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
      ),
      TypeableIri(iri = "http://purl.org/dc/terms/title".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableVariable(variableName = "title") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isValueType = true,
      ),
    ),
  )

  val QueryWithInconsistentTypes3: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |
      |CONSTRUCT {
      |  ?person knora-api:isMainResource true .
      |  ?document beol:hasAuthor ?person .
      |} WHERE {
      |  ?person a knora-api:Resource .
      |  ?person a beol:person .
      |
      |  ?document beol:hasAuthor ?person .
      |  beol:hasAuthor knora-api:objectType knora-api:Resource .
      |  ?document a knora-api:Resource .
      |  { ?document a beol:manuscript . } UNION { ?document a beol:letter .}
      |}
            """.stripMargin

  val QueryWithGravsearchOptions: String =
    """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |} WHERE {
      |     knora-api:GravsearchOptions knora-api:useInference false .
      |     ?thing a anything:Thing .
      |}""".stripMargin

  val GravsearchOptionsResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "thing") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        isResourceType = true,
      ),
    ),
    entitiesInferredFromProperties = Map(),
  )

  private def inspectTypes(query: String) = for {
    parsedQuery <- ZIO.attempt(GravsearchParser.parseQuery(query))
    result <- ZIO.serviceWithZIO[GravsearchTypeInspectionRunner](
                _.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser),
              )
  } yield result

  "The type inspection utility" should {
    "remove the type annotations from a WHERE clause" in {
      val whereClauseWithoutAnnotations = UnsafeZioRun.runOrThrow {
        for {
          parsedQuery <- ZIO.attempt(GravsearchParser.parseQuery(QueryWithExplicitTypeAnnotations))
          result      <- GravsearchTypeInspectionUtil.removeTypeAnnotations(parsedQuery.whereClause)
        } yield result
      }
      whereClauseWithoutAnnotations should ===(whereClauseWithoutAnnotations)
    }
  }

  "The inferring type inspector" should {

    "refine the inspected types for each typeableEntity" in {
      val typeInspectionRunner = UnsafeZioRun.service[InferringGravsearchTypeInspector]
      val parsedQuery          = GravsearchParser.parseQuery(QueryRdfTypeRule)
      val (_, entityInfo) = UnsafeZioRun.runOrThrow(
        typeInspectionRunner.getUsageIndexAndEntityInfos(parsedQuery.whereClause, requestingUser = anythingAdminUser),
      )
      val multipleDetectedTypes: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "letter") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
        entitiesInferredFromPropertyIris = Map(
          TypeableVariable(variableName = "letter") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      val refinedIntermediateResults = typeInspectionRunner.refineDeterminedTypes(
        intermediateResult = multipleDetectedTypes,
        entityInfo = entityInfo,
      )

      assert(refinedIntermediateResults.entities.size == 1)
      refinedIntermediateResults.entities should contain(
        TypeableVariable(variableName = "letter") -> Set(
          NonPropertyTypeInfo(
            typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
            isResourceType = true,
          ),
        ),
      )
      assert(refinedIntermediateResults.entitiesInferredFromPropertyIris.isEmpty)
    }

    "sanitize inconsistent resource types that only have knora-base:Resource as base class in common" in {
      val typeInspectionRunner = UnsafeZioRun.service[InferringGravsearchTypeInspector]
      val parsedQuery          = GravsearchParser.parseQuery(QueryRdfTypeRule)
      val (usageIndex, entityInfo) = UnsafeZioRun.runOrThrow(
        typeInspectionRunner.getUsageIndexAndEntityInfos(parsedQuery.whereClause, requestingUser = anythingAdminUser),
      )
      val inconsistentTypes: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "letter") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              isResourceType = true,
            ),
          ),
          TypeableVariable(variableName = "linkingProp1") -> Set(
            PropertyTypeInfo(
              objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              objectIsResourceType = true,
            ),
            PropertyTypeInfo(
              objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              objectIsResourceType = true,
            ),
          ),
        ),
        entitiesInferredFromPropertyIris = Map(
          TypeableVariable(variableName = "letter") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      val refinedIntermediateResults =
        typeInspectionRunner.refineDeterminedTypes(intermediateResult = inconsistentTypes, entityInfo = entityInfo)

      val sanitizedResults = typeInspectionRunner.sanitizeInconsistentResourceTypes(
        lastResults = refinedIntermediateResults,
        usageIndex.querySchema,
        entityInfo = entityInfo,
      )

      val expectedResult: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "letter") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
              isResourceType = true,
            ),
          ),
          TypeableVariable(variableName = "linkingProp1") -> Set(
            PropertyTypeInfo(
              objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
              objectIsResourceType = true,
            ),
          ),
        ),
        entitiesInferredFromPropertyIris = Map(
          TypeableVariable(variableName = "letter") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      assert(sanitizedResults.entities == expectedResult.entities)
    }

    "sanitize inconsistent resource types that have common base classes other than knora-base:Resource" in {
      val typeInspectionRunner = UnsafeZioRun.service[InferringGravsearchTypeInspector]
      val parsedQuery          = GravsearchParser.parseQuery(QueryWithInconsistentTypes3)
      val (usageIndex, entityInfo) = UnsafeZioRun.runOrThrow(
        typeInspectionRunner.getUsageIndexAndEntityInfos(parsedQuery.whereClause, requestingUser = anythingAdminUser),
      )
      val inconsistentTypes: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "document") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#manuscript".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
        entitiesInferredFromPropertyIris = Map(
          TypeableVariable(variableName = "document") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      val refinedIntermediateResults =
        typeInspectionRunner.refineDeterminedTypes(intermediateResult = inconsistentTypes, entityInfo = entityInfo)

      val sanitizedResults = typeInspectionRunner.sanitizeInconsistentResourceTypes(
        lastResults = refinedIntermediateResults,
        usageIndex.querySchema,
        entityInfo = entityInfo,
      )

      val expectedResult: IntermediateTypeInspectionResult = IntermediateTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "document") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#writtenSource".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
        entitiesInferredFromPropertyIris = Map(
          TypeableVariable(variableName = "document") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      assert(sanitizedResults.entities == expectedResult.entities)
    }

    "infer the type of a resource IRI when it is compared with a variable in a FILTER (in the simple schema)" in {
      val runZio = inspectTypes(QueryComparingResourceIriInSimpleSchema)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == QueryComparingResourceIriInSimpleSchemaResult.entities)
    }

    "infer the type of a resource IRI when it is compared with a variable in a FILTER (in the complex schema)" in {
      val runZio = inspectTypes(QueryComparingResourceIriInComplexSchema)
      val result = UnsafeZioRun.runOrThrow(runZio)
      assert(result.entities == QueryComparingResourceIriInComplexSchemaResult.entities)
    }

    "infer knora-api:Resource as the subject type of a subproperty of knora-api:hasLinkTo" in {
      val runZio = inspectTypes(QueryWithFilterComparison)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == QueryWithFilterComparisonResult.entities)
    }

    "reject a query with a non-Knora property whose type cannot be inferred" in {
      val runZio = inspectTypes(QueryNonKnoraTypeWithoutAnnotation)
      UnsafeZioRun.run(runZio).causeOption.get.squash mustBe a[GravsearchException]
    }

    "accept a query with a non-Knora property whose type can be inferred" in {
      val runZio = inspectTypes(QueryNonKnoraTypeWithAnnotation)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResult3.entities)
    }

    "ignore Gravsearch options" in {
      val runZio = inspectTypes(QueryWithGravsearchOptions)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == GravsearchOptionsResult.entities)
    }

    "reject a query with inconsistent types inferred from statements" in {
      val runZio = inspectTypes(QueryWithInconsistentTypes1)
      UnsafeZioRun.run(runZio).causeOption.get.squash mustBe a[GravsearchException]
    }

    "reject a query with inconsistent types inferred from a FILTER" in {
      val runZio = inspectTypes(QueryWithInconsistentTypes2)
      UnsafeZioRun.run(runZio).causeOption.get.squash mustBe a[GravsearchException]
    }
  }
}
