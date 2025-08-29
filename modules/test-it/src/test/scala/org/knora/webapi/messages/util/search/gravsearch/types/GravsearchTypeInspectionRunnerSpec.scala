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
    test("infer the type of a non-property IRI used as the argument of a function in a FILTER") {
      val queryIriTypeFromFunction: String =
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
          |""".stripMargin
      val expected = Map(
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri,
          objectIsValueType = true,
        ),
        TypeableVariable(variableName = "text") -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri,
          isValueType = true,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri =
          "http://api.knora.org/ontology/knora-api/v2#textValueHasStandoff".toSmartIri,
        ) -> PropertyTypeInfo(
          objectTypeIri = "http://api.knora.org/ontology/knora-api/v2#StandoffTag".toSmartIri,
          objectIsStandoffTagType = true,
        ),
        TypeableVariable(variableName = "standoffLinkTag") -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/v2#StandoffLinkTag".toSmartIri,
          isStandoffTagType = true,
        ),
      )
      inspectTypes(queryIriTypeFromFunction).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the types in a query that requires 6 iterations") {
      val pathologicalQuery: String =
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
          |""".stripMargin
      val expected = Map(
        TypeableVariable(variableName = "book4") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "titleProp1") -> PropertyTypeInfo(
          objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          objectIsValueType = true,
        ),
        TypeableVariable(variableName = "page1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "book1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "titleProp2") -> PropertyTypeInfo(
          objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          objectIsValueType = true,
        ),
        TypeableVariable(variableName = "page3") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOf".toSmartIri,
        ) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/1749ad09ac06".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "linkObj") -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "title2") -> NonPropertyTypeInfo(
          typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          isValueType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/52431ecfab06".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "title3") -> NonPropertyTypeInfo(
          typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          isValueType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/dc4e3c44ac06".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri =
          "http://api.knora.org/ontology/knora-api/simple/v2#isRegionOf".toSmartIri,
        ) -> PropertyTypeInfo(
          objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Representation".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "page2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "page4") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "titleProp4") -> PropertyTypeInfo(
          objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          objectIsValueType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/8d3d8f94ab06".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "title1") -> NonPropertyTypeInfo(
          typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          isValueType = true,
        ),
        TypeableVariable(variableName = "titleProp3") -> PropertyTypeInfo(
          objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          objectIsValueType = true,
        ),
        TypeableVariable(variableName = "linkProp2") -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "partOfProp") -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "title4") -> NonPropertyTypeInfo(
          typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          isValueType = true,
        ),
        TypeableVariable(variableName = "book3") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "linkProp1") -> PropertyTypeInfo(
          objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Region".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "book2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          isResourceType = true,
        ),
      )
      inspectTypes(pathologicalQuery).map(actual => assertTrue(actual.entities == expected))
    },
    test("know the object type of rdfs:label") {
      val queryWithRdfsLabelAndLiteral: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
          |}
          |""".stripMargin
      val expected = Map(
        TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          objectIsValueType = true,
        ),
      )
      inspectTypes(queryWithRdfsLabelAndLiteral).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a variable used as the object of rdfs:label") {
      val queryWithRdfsLabelAndVariable: String =
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
          |""".stripMargin
      val expected = Map(
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
      )
      inspectTypes(queryWithRdfsLabelAndVariable).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a variable when it is compared with another variable in a FILTER (in the simple schema)") {
      val queryComparingResourcesInSimpleSchema: String =
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
          |} OFFSET 0
          |""".stripMargin
      val expected = Map(
        TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "person1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "prop") -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
      )
      inspectTypes(queryComparingResourcesInSimpleSchema).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a variable when it is compared with another variable in a FILTER (in the complex schema)") {
      val queryComparingResourcesInComplexSchema: String =
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
          |} OFFSET 0
          |""".stripMargin
      val expected = Map(
        TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "person1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "prop") -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
      )
      inspectTypes(queryComparingResourcesInComplexSchema).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a resource IRI when it is compared with a variable in a FILTER (in the simple schema)") {
      val queryComparingResourceIriInSimpleSchema: String =
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
          |OFFSET 0
          |""".stripMargin
      val expected = Map(
        TypeableIri(iri =
          "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
        ) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "person1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
      )
      inspectTypes(queryComparingResourceIriInSimpleSchema).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer the type of a resource IRI when it is compared with a variable in a FILTER (in the complex schema)") {
      val queryComparingResourceIriInComplexSchema: String =
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
          |} OFFSET 0
          |""".stripMargin
      val expected = Map(
        TypeableVariable(variableName = "person2") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableVariable(variableName = "person1") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
          isResourceType = true,
        ),
      )
      inspectTypes(queryComparingResourceIriInComplexSchema).map(actual => assertTrue(actual.entities == expected))
    },
    test("infer knora-api:Resource as the subject type of a subproperty of knora-api:hasLinkTo") {
      val queryWithFilterComparison: String =
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
          |}
          |""".stripMargin
      val expected = Map(
        TypeableVariable(variableName = "person") -> NonPropertyTypeInfo(
          typeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          isResourceType = true,
        ),
        TypeableVariable(variableName = "document") -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          isResourceType = true,
        ),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(
          objectTypeIri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#person".toSmartIri,
          objectIsResourceType = true,
        ),
        TypeableIri(iri = "http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g".toSmartIri) -> NonPropertyTypeInfo(
          typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          isResourceType = true,
        ),
      )
      inspectTypes(queryWithFilterComparison).map(actual => assertTrue(actual.entities == expected))
    },
  )
}
