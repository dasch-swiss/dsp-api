/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object GravsearchTypeInspectionRunnerSpec extends E2EZSpec {

  private def inspectTypes(query: String) = for {
    parsedQuery <- ZIO.attempt(GravsearchParser.parseQuery(query))
    result <- ZIO.serviceWithZIO[GravsearchTypeInspectionRunner](
                _.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser),
              )
  } yield result

  private val queryWithInconsistentTypes3: String =
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
      |""".stripMargin

  private val queryRdfTypeRule: String =
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
      |""".stripMargin

  private val typeInferenceResult1: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
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

  override val e2eSpec = suite("GravsearchTypeInspectionRunner.inspectTypes")(
    test("sanitize inconsistent types resulted from a union") {
      val expectedResult: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "person") ->
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              isResourceType = true,
            ),
          TypeableVariable(variableName = "document") ->
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#writtenSource".toSmartIri,
              isResourceType = true,
            ),
          TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) ->
            PropertyTypeInfo(
              objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              objectIsResourceType = true,
            ),
        ),
        entitiesInferredFromProperties = Map(
          TypeableVariable(variableName = "person") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              isResourceType = true,
            ),
          ),
        ),
      )

      inspectTypes(queryWithInconsistentTypes3)
        .map(actual => assertTrue(actual.entities == expectedResult.entities))
    },
    test("types resulted from a query with optional") {
      val queryWithOptional: String =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?document knora-api:isMainResource true .
          |} WHERE {
          |    ?document rdf:type beol:writtenSource .
          |    OPTIONAL {
          |      ?document beol:hasRecipient ?recipient .
          |      ?recipient beol:hasFamilyName ?familyName .
          |      FILTER knora-api:matchText(?familyName, "Bernoulli")
          |    }
          |}
          |""".stripMargin

      // From property "beol:hasRecipient" the type of ?document is inferred to be beol:basicLetter, and
      // since writtenSource is a base class of basicLetter, it is ignored and type "beol:basicLetter" is considered for ?document.
      // The OPTIONAL would become meaningless here. Therefore, in cases where property is in OPTIONAL block,
      // the rdf:type statement for ?document must not be removed from query even though ?document is in entitiesInferredFromProperties.
      val expected: GravsearchTypeInspectionResult = GravsearchTypeInspectionResult(
        entities = Map(
          TypeableVariable(variableName = "document") ->
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
              isResourceType = true,
            ),
          TypeableVariable(variableName = "familyName") ->
            NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri, isValueType = true),
          TypeableVariable(variableName = "recipient") ->
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              isResourceType = true,
            ),
          TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri) ->
            PropertyTypeInfo(
              objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
              objectIsResourceType = true,
            ),
          TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri) ->
            PropertyTypeInfo(
              objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
              objectIsValueType = true,
            ),
        ),
        entitiesInferredFromProperties = Map(
          TypeableVariable(variableName = "document") -> Set(
            NonPropertyTypeInfo(
              typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#basicLetter".toSmartIri,
              isResourceType = true,
            ),
          ),
          TypeableVariable(variableName = "familyName") ->
            Set(
              NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri, isValueType = true),
            ),
          TypeableVariable(variableName = "recipient") ->
            Set(
              NonPropertyTypeInfo(
                typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
                isResourceType = true,
              ),
            ),
        ),
      )
      inspectTypes(queryWithOptional)
        .map(actual =>
          assertTrue(
            actual.entities == expected.entities,
            actual.entitiesInferredFromProperties == expected.entitiesInferredFromProperties,
          ),
        )
    },
    test("infer the most specific type from redundant ones given in a query") {
      val queryWithRedundantTypes: String =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?author knora-api:isMainResource true .
          |} WHERE {
          |    ?letter rdf:type beol:writtenSource .
          |    ?letter rdf:type beol:letter .
          |    ?letter beol:hasAuthor ?author .
          |
          |    ?author beol:hasFamilyName ?familyName .
          |
          |    FILTER knora-api:matchText(?familyName, "Bernoulli")
          |
          |} ORDER BY ?date
          |""".stripMargin
      inspectTypes(queryWithRedundantTypes)
        .map(result =>
          assertTrue(
            result.entities.size == 5,
            !result.entitiesInferredFromProperties.contains(TypeableVariable("letter")),
          ),
        )
    },
    test(
      "infer that an entity is a knora-api:Resource if there is an rdf:type statement about it and the specified type is a Knora resource class",
    ) {
      inspectTypes(queryRdfTypeRule)
        .map(result =>
          assertTrue(
            result.entities == typeInferenceResult1.entities,
            result.entitiesInferredFromProperties.contains(TypeableVariable("date")),
            result.entitiesInferredFromProperties.contains(
              TypeableIri("http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA".toSmartIri),
            ),
            !result.entitiesInferredFromProperties.contains(TypeableVariable("linkingProp1")),
          ),
        )
    },
    test("infer a property's knora-api:objectType if the property's IRI is used as a predicate") {
      val queryKnoraObjectTypeFromPropertyIriRule: String =
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
          |    ?date a knora-api:Date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |    ?name a xsd:string .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
              """.stripMargin
      inspectTypes(queryKnoraObjectTypeFromPropertyIriRule).flatMap(actual =>
        assertTrue(actual.entities == typeInferenceResult1.entities),
      )
    },
    test(
      "infer an entity's type if the entity is used as the object of a statement and the predicate's knora-api:objectType is known",
    ) {
      val queryTypeOfObjectFromPropertyRule: String =
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
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
          |""".stripMargin
      inspectTypes(queryTypeOfObjectFromPropertyRule).map(actual =>
        assertTrue(actual.entities == typeInferenceResult1.entities),
      )
    },
    test("infer the knora-api:objectType of a property variable if it's used with an object whose type is known") {
      val queryKnoraObjectTypeFromObjectRule: String =
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
          |""".stripMargin
      inspectTypes(queryKnoraObjectTypeFromObjectRule).map(actual =>
        assertTrue(actual.entities == typeInferenceResult1.entities),
      )
    },
    test(
      "infer an entity's type if the entity is used as the subject of a statement, the predicate is an IRI, and the predicate's knora-api:subjectType is known",
    ) {
      val queryTypeOfSubjectFromPropertyRule: String =
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
          |""".stripMargin

      val expected = Map(
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
        ) -> PropertyTypeInfo(
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
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri,
        ) -> PropertyTypeInfo(
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
      )
      inspectTypes(queryTypeOfSubjectFromPropertyRule)
        .map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the knora-api:objectType of a property variable if it's compared to a known property IRI in a FILTER") {
      val queryPropertyVarTypeFromFilterRule: String =
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
          |""".stripMargin
      val expected = Map(
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
        ) -> PropertyTypeInfo(
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
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri,
        ) -> PropertyTypeInfo(
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
      )
      inspectTypes(queryPropertyVarTypeFromFilterRule).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a non-property variable if it's compared to an XSD literal in a FILTER") {
      val queryNonPropertyVarTypeFromFilterRule: String =
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
          |""".stripMargin
      val expected = Map(
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
        ) -> PropertyTypeInfo(
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
      )
      inspectTypes(queryNonPropertyVarTypeFromFilterRule)
        .map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a non-property variable used as the argument of a function in a FILTER") {
      val queryVarTypeFromFunction: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?mainRes knora-api:isMainResource true .
          |    ?mainRes incunabula:title ?propVal0 .
          |} WHERE {
          |    ?mainRes a incunabula:book .
          |    ?mainRes ?titleProp ?propVal0 .
          |
          |    FILTER knora-api:matchText(?propVal0, "Zeitglöcklein")
          |}
          |""".stripMargin
      val expected = Map(
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
      )
      inspectTypes(queryVarTypeFromFunction).map(actual => assertTrue(actual.entities == expected))
    },
  )
}
