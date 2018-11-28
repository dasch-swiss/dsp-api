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

package org.knora.webapi.responders.v2.search.gravsearch.types

import akka.testkit.ImplicitSender
import org.knora.webapi._
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Tests Gravsearch type inspection.
  */
class GravsearchTypeInspectorSpec extends CoreSpec() with ImplicitSender {

    private val searchParserV2Spec = new GravsearchParserSpec

    private val anythingAdminUser = SharedTestDataADM.anythingAdminUser

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val timeout = 10.seconds

    "The type inspection utility" should {
        "remove the type annotations from a WHERE clause" in {
            val parsedQuery = GravsearchParser.parseQuery(QueryWithExplicitTypeAnnotations)
            val whereClauseWithoutAnnotations = GravsearchTypeInspectionUtil.removeTypeAnnotations(parsedQuery.whereClause)
            whereClauseWithoutAnnotations should ===(whereClauseWithoutAnnotations)
        }
    }

    "The annotation-reading type inspector" should {
        "get type information from a simple query" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = false)
            val parsedQuery = GravsearchParser.parseQuery(QueryWithExplicitTypeAnnotations)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == SimpleTypeInspectionResult)
        }
    }

    "The inferring type inspector" should {
        "infer that an entity is a knora-api:Resource if there is an rdf:type statement about it and and the specified type is a Knora resource class" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryRdfTypeRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult1)
        }

        "infer a property's knora-api:objectType if the property's IRI is used as a predicate" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryPropertyIriObjectTypeRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult1)
        }

        "infer an entity's type if the entity is used as the object of a statement and the predicate's knora-api:objectType is known" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryTypeOfObjectFromPropertyRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult1)
        }

        "infer the knora-api:objectType of a property variable if it's used with an object whose type is known" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryPropertyTypeFromObjectRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult1)
        }

        "infer an entity's type if the entity is used as the subject of a statement, the predicate is an IRI, and the predicate's knora-api:subjectType is known" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryTypeOfSubjectFromPropertyRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult1)
        }

        "infer the knora-api:objectType of a property variable if it's compared to a known property IRI in a FILTER" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryPropertyVarTypeFromFilterRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult2)
        }

        "infer the type of a non-property variable if it's compared to an XSD literal in a FILTER" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryNonPropertyVarTypeFromFilterRule)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult4)
        }

        "infer the type of a non-property variable used as the argument of a function in a FILTER" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryVarTypeFromFunction)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult5)
        }

        "infer the type of a non-property IRI used as the argument of a function in a FILTER" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryIriTypeFromFunction)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult6)
        }

        "infer the types in a query that requires 6 iterations" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(PathologicalQuery)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == PathologicalTypeInferenceResult)
        }

        "reject a query with a non-Knora property whose type cannot be inferred" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryNonKnoraTypeWithoutAnnotation)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            assertThrows[GravsearchException] {
                Await.result(resultFuture, timeout)
            }
        }

        "accept a query with a non-Knora property whose type can be inferred" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryNonKnoraTypeWithAnnotation)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            val result = Await.result(resultFuture, timeout)
            assert(result == TypeInferenceResult3)
        }

        "reject a query with inconsistent types inferred from statements" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryWithInconsistentTypes1)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            assertThrows[GravsearchException] {
                Await.result(resultFuture, timeout)
            }
        }

        "reject a query with inconsistent types inferred from a FILTER" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = true)
            val parsedQuery = GravsearchParser.parseQuery(QueryWithInconsistentTypes2)
            val resultFuture: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)
            assertThrows[GravsearchException] {
                Await.result(resultFuture, timeout)
            }
        }
    }


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

    val SimpleTypeInspectionResult = GravsearchTypeInspectionResult(entities = Map(
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkingProp2") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
    ))

    val WhereClauseWithoutAnnotations = WhereClause(patterns = Vector(
        StatementPattern(
            obj = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            subj = QueryVariable(variableName = "letter")
        ),
        StatementPattern(
            obj = IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri),
            pred = QueryVariable(variableName = "linkingProp1"),
            subj = QueryVariable(variableName = "letter")
        ),
        StatementPattern(
            obj = IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri),
            pred = QueryVariable(variableName = "linkingProp2"),
            subj = QueryVariable(variableName = "letter")
        ),
        FilterPattern(expression = OrExpression(
            rightArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp2")
            ),
            leftArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp2")
            )
        )),
        FilterPattern(expression = OrExpression(
            rightArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp1")
            ),
            leftArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp1")
            )
        ))
    ))

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

    val QueryPropertyIriObjectTypeRule: String =
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

    val QueryTypeOfObjectFromPropertyRule: String =
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
        """.stripMargin

    val QueryPropertyTypeFromObjectRule: String =
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
          |        FILTER knora-api:match(?propVal0, "Zeitglöcklein")
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

    val PathologicalTypeInferenceResult = GravsearchTypeInspectionResult(entities = Map(
        TypeableVariable(variableName = "book4") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "titleProp1") -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "page1") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "book1") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "titleProp2") -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "page3") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOf".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/1749ad09ac06".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkObj") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "title2") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/52431ecfab06".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "title3") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/dc4e3c44ac06".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://api.knora.org/ontology/knora-api/simple/v2#isRegionOf".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "page2") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "page4") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "titleProp4") -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/8d3d8f94ab06".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "title1") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "titleProp3") -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "linkProp2") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "partOfProp") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "title4") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "book3") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkProp1") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "book2") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
    ))


    val TypeInferenceResult1 = GravsearchTypeInspectionResult(entities = Map(
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "date") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "name") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
    ))

    val TypeInferenceResult2 = GravsearchTypeInspectionResult(entities = Map(
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "date") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
    ))

    val TypeInferenceResult3 = GravsearchTypeInspectionResult(entities = Map(
        TypeableVariable(variableName = "title") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableIri(iri = "http://purl.org/dc/terms/title".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
    ))

    val TypeInferenceResult4 = GravsearchTypeInspectionResult(entities = Map(
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
        TypeableVariable(variableName = "book") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "pubdate") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
        TypeableVariable(variableName = "title") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri)
    ))

    val TypeInferenceResult5 = GravsearchTypeInspectionResult(entities = Map(
        TypeableVariable(variableName = "mainRes") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "titleProp") -> PropertyTypeInfo(objectTypeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri),
        TypeableVariable(variableName = "propVal0") -> NonPropertyTypeInfo(typeIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri)
    ))

    val TypeInferenceResult6 = GravsearchTypeInspectionResult(entities = Map(
        TypeableIri(iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri),
        TypeableVariable(variableName = "text") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://api.knora.org/ontology/knora-api/v2#textValueHasStandoff".toSmartIri) -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/v2#StandoffTag".toSmartIri),
        TypeableVariable(variableName = "standoffLinkTag") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/v2#StandoffTag".toSmartIri)
    ))
}
