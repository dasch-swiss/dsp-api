package org.knora.webapi.util.search.v2

import org.knora.webapi.ApiV2Simple
import org.scalatest.{Matchers, WordSpec}

/**
  * Tests [[TypeInspectorV2]].
  */
class TypeInspectorV2Spec extends WordSpec with Matchers {
    "TypeInspectorV2" should {
        "get type information from a simple query" in {
            val parsedQuery = SearchParserV2.parseSearchQuery(SearchParserV2Spec.SimpleSparqlConstructQueryWithExplicitTypeAnnotations)
            val typeInspector = new ExplicitTypeInspectorV2(ApiV2Simple)
            val typeInspectionResult = typeInspector.inspectTypes(parsedQuery.whereClause)
            typeInspectionResult should ===(SimpleTypeInspectionResult)
        }

        "remove the type annotations from a WHERE clause" in {
            val parsedQuery = SearchParserV2.parseSearchQuery(SearchParserV2Spec.SimpleSparqlConstructQueryWithExplicitTypeAnnotations)
            val typeInspector = new ExplicitTypeInspectorV2(ApiV2Simple)
            val whereClauseWithoutAnnotations = typeInspector.removeTypeAnnotations(parsedQuery.whereClause)
            whereClauseWithoutAnnotations should ===(whereClauseWithoutAnnotations)
        }
    }

    val SimpleTypeInspectionResult = TypeInspectionResultV2(typedEntities = Map(
        TypeableVariableV2(variableName = "linkingProp1") -> PropertyTypeInfoV2(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableIriV2(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA") -> NonPropertyTypeInfoV2(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableVariableV2(variableName = "letter") -> NonPropertyTypeInfoV2(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableVariableV2(variableName = "linkingProp2") -> PropertyTypeInfoV2(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableIriV2(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA") -> NonPropertyTypeInfoV2(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource")
    ))

    val WhereClauseWithoutAnnotations = SimpleWhereClause(patterns = Vector(
        StatementPattern(
            obj = IriRef(iri = "http://api.knora.org/ontology/beol/simple/v2#letter"),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            subj = QueryVariable(variableName = "letter")
        ),
        StatementPattern(
            obj = IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA"),
            pred = QueryVariable(variableName = "linkingProp1"),
            subj = QueryVariable(variableName = "letter")
        ),
        StatementPattern(
            obj = IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA"),
            pred = QueryVariable(variableName = "linkingProp2"),
            subj = QueryVariable(variableName = "letter")
        ),
        FilterPattern(expression = OrExpression(
            rightArg = CompareExpression(
                rightArg = IriRef(iri = "http://api.knora.org/ontology/beol/simple/v2#hasRecipient"),
                operator = "=",
                leftArg = QueryVariable(variableName = "linkingProp2")
            ),
            leftArg = CompareExpression(
                rightArg = IriRef(iri = "http://api.knora.org/ontology/beol/simple/v2#hasAuthor"),
                operator = "=",
                leftArg = QueryVariable(variableName = "linkingProp2")
            )
        )),
        FilterPattern(expression = OrExpression(
            rightArg = CompareExpression(
                rightArg = IriRef(iri = "http://api.knora.org/ontology/beol/simple/v2#hasRecipient"),
                operator = "=",
                leftArg = QueryVariable(variableName = "linkingProp1")
            ),
            leftArg = CompareExpression(
                rightArg = IriRef(iri = "http://api.knora.org/ontology/beol/simple/v2#hasAuthor"),
                operator = "=",
                leftArg = QueryVariable(variableName = "linkingProp1")
            )
        ))
    ))
}
