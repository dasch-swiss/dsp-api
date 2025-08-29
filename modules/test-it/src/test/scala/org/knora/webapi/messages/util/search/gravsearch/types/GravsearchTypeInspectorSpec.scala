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

  val QueryKnoraObjectTypeFromObjectRule: String =
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
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
      |
      |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |} ORDER BY ?date
        """.stripMargin

  val QueryTypeOfSubjectFromPropertyRule: String =
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
      |    ?letter beol:creationDate ?date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
      |
      |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |
      |
      |} ORDER BY ?date
        """.stripMargin

  val QueryPropertyVarTypeFromFilterRule: String =
    """
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:creationDate ?date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |} WHERE {
      |    ?letter beol:creationDate ?date .
      |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
      |
      |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |
      |} ORDER BY ?date
        """.stripMargin

  val QueryNonPropertyVarTypeFromFilterRule: String =
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
      |  FILTER(?pubdate = "JULIAN:1497-03-01"^^knora-api:Date) .
      |}
        """.stripMargin

  val QueryVarTypeFromFunction: String =
    """
      |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |    CONSTRUCT {
      |
      |        ?mainRes knora-api:isMainResource true .
      |
      |        ?mainRes incunabula:title ?propVal0 .
      |
      |     } WHERE {
      |
      |        ?mainRes a incunabula:book .
      |
      |        ?mainRes ?titleProp ?propVal0 .
      |
      |        FILTER knora-api:matchText(?propVal0, "Zeitglöcklein")
      |
      |     }
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

  val QueryIriTypeFromFunction: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:hasText ?text .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:hasText ?text .
      |    ?text knora-api:textValueHasStandoff ?standoffLinkTag .
      |    ?standoffLinkTag a knora-api:StandoffLinkTag .
      |
      |    FILTER knora-api:standoffLink(?letter, ?standoffLinkTag, <http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA>)
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

  val QueryComparingResourcesInSimpleSchema: String =
    """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:hasAuthor ?person1 .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:hasAuthor ?person1 .
      |    ?letter ?prop ?person2 .
      |    FILTER(?person1 != ?person2) .
      |}
      |OFFSET 0""".stripMargin

  val QueryComparingResourcesInSimpleSchemaResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
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
      TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
        isResourceType = true,
        isValueType = false,
      ),
      TypeableVariable(variableName = "prop") -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
      ),
    ),
    entitiesInferredFromProperties = Map(
      TypeableVariable(variableName = "person1") -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
      ),
    ),
  )

  val QueryComparingResourcesInComplexSchema: String =
    """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:hasAuthor ?person1 .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter ?prop ?person1 .
      |    ?letter beol:hasRecipient ?person2 .
      |    FILTER(?person2 != ?person1) .
      |}
      |OFFSET 0""".stripMargin

  val QueryComparingResourcesInComplexSchemaResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
        isResourceType = true,
        isValueType = false,
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
      TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
        isResourceType = true,
        isValueType = false,
      ),
      TypeableVariable(variableName = "prop") -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
      ),
    ),
    entitiesInferredFromProperties = Map(
      TypeableVariable(variableName = "person2") -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
          isValueType = false,
        ),
      ),
    ),
  )

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

  val PathologicalQuery: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |  ?linkObj knora-api:isMainResource true .
      |
      |  ?linkObj ?linkProp1 <http://rdfh.ch/8d3d8f94ab06> .
      |  ?linkObj ?linkProp1 <http://rdfh.ch/1749ad09ac06> .
      |  ?linkObj ?linkProp1 <http://rdfh.ch/52431ecfab06> .
      |  ?linkObj ?linkProp1 <http://rdfh.ch/dc4e3c44ac06> .
      |
      |  <http://rdfh.ch/8d3d8f94ab06> ?linkProp2 ?page1 .
      |  <http://rdfh.ch/1749ad09ac06> ?linkProp2 ?page2 .
      |  <http://rdfh.ch/52431ecfab06> ?linkProp2 ?page3 .
      |  <http://rdfh.ch/dc4e3c44ac06> ?linkProp2 ?page4 .
      |
      |  ?page1 ?partOfProp ?book1 .
      |  ?page2 ?partOfProp ?book2 .
      |  ?page3 ?partOfProp ?book3 .
      |  ?page4 ?partOfProp ?book4 .
      |
      |  ?book1 ?titleProp1 ?title1 .
      |  ?book2 ?titleProp2 ?title2 .
      |  ?book3 ?titleProp3 ?title3 .
      |  ?book4 ?titleProp4 ?title4 .
      |} WHERE {
      |  BIND(<http://rdfh.ch/a154cb7eac06> AS ?linkObj)
      |  ?linkObj knora-api:hasLinkTo <http://rdfh.ch/8d3d8f94ab06> .
      |
      |  ?linkObj ?linkProp1 <http://rdfh.ch/8d3d8f94ab06> .
      |  ?linkObj ?linkProp1 <http://rdfh.ch/1749ad09ac06> .
      |  ?linkObj ?linkProp1 <http://rdfh.ch/52431ecfab06> .
      |  ?linkObj ?linkProp1 <http://rdfh.ch/dc4e3c44ac06> .
      |
      |  <http://rdfh.ch/8d3d8f94ab06> knora-api:isRegionOf ?page1 .
      |
      |  <http://rdfh.ch/8d3d8f94ab06> ?linkProp2 ?page1 .
      |  <http://rdfh.ch/1749ad09ac06> ?linkProp2 ?page2 .
      |  <http://rdfh.ch/52431ecfab06> ?linkProp2 ?page3 .
      |  <http://rdfh.ch/dc4e3c44ac06> ?linkProp2 ?page4 .
      |
      |  ?page1 incunabula:partOf ?book1 .
      |
      |  ?page1 ?partOfProp ?book1 .
      |  ?page2 ?partOfProp ?book2 .
      |  ?page3 ?partOfProp ?book3 .
      |  ?page4 ?partOfProp ?book4 .
      |
      |  ?book1 ?titleProp1 ?title1 .
      |  ?book1 ?titleProp2 ?title1 .
      |  ?book2 ?titleProp2 ?title2 .
      |  ?book2 ?titleProp3 ?title2 .
      |  ?book3 ?titleProp3 ?title3 .
      |  ?book3 ?titleProp4 ?title3 .
      |  ?book4 ?titleProp4 ?title4 .
      |
      |  FILTER(?title4 = "[Das] Narrenschiff (lat.)" || ?title4 = "Stultifera navis (...)")
      |}
        """.stripMargin

  val PathologicalTypeInferenceResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "book4") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "titleProp1") -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsResourceType = false,
        objectIsValueType = true,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "page1") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "book1") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "titleProp2") -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsResourceType = false,
        objectIsValueType = true,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "page3") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOf".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
        objectIsStandoffTagType = false,
      ),
      TypeableIri(iri = "http://rdfh.ch/1749ad09ac06".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "linkObj") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "title2") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isResourceType = false,
        isValueType = true,
        isStandoffTagType = false,
      ),
      TypeableIri(iri = "http://rdfh.ch/52431ecfab06".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "title3") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isResourceType = false,
        isValueType = true,
        isStandoffTagType = false,
      ),
      TypeableIri(iri = "http://rdfh.ch/dc4e3c44ac06".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableIri(iri = "http://api.knora.org/ontology/knora-api/simple/v2#isRegionOf".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Representation".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "page2") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "page4") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableIri(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "titleProp4") -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsResourceType = false,
        objectIsValueType = true,
        objectIsStandoffTagType = false,
      ),
      TypeableIri(iri = "http://rdfh.ch/8d3d8f94ab06".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "title1") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isResourceType = false,
        isValueType = true,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "titleProp3") -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsResourceType = false,
        objectIsValueType = true,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "linkProp2") -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "partOfProp") -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "title4") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isResourceType = false,
        isValueType = true,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "book3") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "linkProp1") -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
        objectIsResourceType = true,
        objectIsValueType = false,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "book2") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
    ),
    entitiesInferredFromProperties = Map(
      TypeableVariable(variableName = "page1") -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          isResourceType = true,
          isValueType = false,
          isStandoffTagType = false,
        ),
      ),
      TypeableVariable(variableName = "linkObj") -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          isResourceType = true,
          isValueType = false,
          isStandoffTagType = false,
        ),
      ),
      TypeableIri(iri = "http://rdfh.ch/8d3d8f94ab06".toSmartIri) -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
          isResourceType = true,
          isValueType = false,
          isStandoffTagType = false,
        ),
      ),
      TypeableVariable(variableName = "book1") -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          isResourceType = true,
          isValueType = false,
          isStandoffTagType = false,
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

  val TypeInferenceResultNoSubject: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
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
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
        isResourceType = true,
      ),
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        objectIsResourceType = true,
      ),
    ),
  )

  val TypeInferenceResult2: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
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
      TypeableIri(iri = "http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
        isResourceType = true,
      ),
      TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
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

  val TypeInferenceResult4: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableIri(iri =
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
      ) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
      ),
      TypeableVariable(variableName = "pubdate") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
        isValueType = true,
      ),
      TypeableVariable(variableName = "title") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isValueType = true,
      ),
    ),
  )

  val TypeInferenceResult5: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "mainRes") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
      ),
      TypeableVariable(variableName = "titleProp") -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableVariable(variableName = "propVal0") -> NonPropertyTypeInfo(
        typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        isValueType = true,
      ),
    ),
  )

  val TypeInferenceResult6: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri,
        objectIsResourceType = false,
        objectIsValueType = true,
        objectIsStandoffTagType = false,
      ),
      TypeableVariable(variableName = "text") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri,
        isResourceType = false,
        isValueType = true,
        isStandoffTagType = false,
      ),
      TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableIri(iri = "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA".toSmartIri) -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
        isResourceType = true,
        isValueType = false,
        isStandoffTagType = false,
      ),
      TypeableIri(iri =
        "http://api.knora.org/ontology/knora-api/v2#textValueHasStandoff".toSmartIri,
      ) -> PropertyTypeInfo(
        objectTypeIri = "http://api.knora.org/ontology/knora-api/v2#StandoffTag".toSmartIri,
        objectIsResourceType = false,
        objectIsValueType = false,
        objectIsStandoffTagType = true,
      ),
      TypeableVariable(variableName = "standoffLinkTag") -> NonPropertyTypeInfo(
        typeIri = "http://api.knora.org/ontology/knora-api/v2#StandoffLinkTag".toSmartIri,
        isResourceType = false,
        isValueType = false,
        isStandoffTagType = true,
      ),
    ),
    entitiesInferredFromProperties = Map(
      TypeableVariable(variableName = "text") -> Set(
        NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri,
          isResourceType = false,
          isValueType = true,
          isStandoffTagType = false,
        ),
      ),
    ),
  )

  val QueryWithRdfsLabelAndLiteral: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
      |}
        """.stripMargin

  val QueryWithRdfsLabelAndVariable: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?label .
      |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
      |}
        """.stripMargin

  val RdfsLabelWithLiteralResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
      ),
      TypeableIri(iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
    ),
  )

  val RdfsLabelWithVariableResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
    entities = Map(
      TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(
        typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        isResourceType = true,
      ),
      TypeableIri(iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri) -> PropertyTypeInfo(
        objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
        objectIsValueType = true,
      ),
      TypeableVariable(variableName = "label") -> NonPropertyTypeInfo(
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

    "infer the knora-api:objectType of a property variable if it's used with an object whose type is known" in {
      val runZio = inspectTypes(QueryKnoraObjectTypeFromObjectRule)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResult1.entities)
    }

    "infer an entity's type if the entity is used as the subject of a statement, the predicate is an IRI, and the predicate's knora-api:subjectType is known" in {
      val runZio = inspectTypes(QueryTypeOfSubjectFromPropertyRule)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResultNoSubject.entities)
    }

    "infer the knora-api:objectType of a property variable if it's compared to a known property IRI in a FILTER" in {
      val runZio = inspectTypes(QueryPropertyVarTypeFromFilterRule)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResult2.entities)
    }

    "infer the type of a non-property variable if it's compared to an XSD literal in a FILTER" in {
      val runZio = inspectTypes(QueryNonPropertyVarTypeFromFilterRule)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResult4.entities)
    }

    "infer the type of a non-property variable used as the argument of a function in a FILTER" in {
      val runZio = inspectTypes(QueryVarTypeFromFunction)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResult5.entities)
    }

    "infer the type of a non-property IRI used as the argument of a function in a FILTER" in {
      val runZio = inspectTypes(QueryIriTypeFromFunction)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == TypeInferenceResult6.entities)
    }

    "infer the types in a query that requires 6 iterations" in {
      val runZio = inspectTypes(PathologicalQuery)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == PathologicalTypeInferenceResult.entities)
    }

    "know the object type of rdfs:label" in {
      val runZio = inspectTypes(QueryWithRdfsLabelAndLiteral)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == RdfsLabelWithLiteralResult.entities)
    }

    "infer the type of a variable used as the object of rdfs:label" in {
      val runZio = inspectTypes(QueryWithRdfsLabelAndVariable)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == RdfsLabelWithVariableResult.entities)
    }

    "infer the type of a variable when it is compared with another variable in a FILTER (in the simple schema)" in {
      val runZio = inspectTypes(QueryComparingResourcesInSimpleSchema)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == QueryComparingResourcesInSimpleSchemaResult.entities)
    }

    "infer the type of a variable when it is compared with another variable in a FILTER (in the complex schema)" in {
      val runZio = inspectTypes(QueryComparingResourcesInComplexSchema)
      assert(UnsafeZioRun.runOrThrow(runZio).entities == QueryComparingResourcesInComplexSchemaResult.entities)
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
