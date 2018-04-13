package org.knora.webapi.util.search.v2


import org.knora.webapi.CoreSpec
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.search._

/**
  * Tests [[TypeInspector]].
  */
class TypeInspectorSpec extends CoreSpec() {
    val searchParserV2Spec = new SearchParserV2Spec
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "TypeInspector" should {
        "get type information from a simple query" in {
            val parsedQuery = SearchParserV2.parseSearchQuery(searchParserV2Spec.QueryWithExplicitTypeAnnotations)
            val typeInspector = new ExplicitTypeInspectorV2()
            val typeInspectionResult = typeInspector.inspectTypes(parsedQuery.whereClause)
            typeInspectionResult should ===(SimpleTypeInspectionResult)
        }

        "remove the type annotations from a WHERE clause" in {
            val parsedQuery = SearchParserV2.parseSearchQuery(searchParserV2Spec.QueryWithExplicitTypeAnnotations)
            val typeInspector = new ExplicitTypeInspectorV2()
            val whereClauseWithoutAnnotations = typeInspector.removeTypeAnnotations(parsedQuery.whereClause)
            whereClauseWithoutAnnotations should ===(whereClauseWithoutAnnotations)
        }
    }

    val SimpleTypeInspectionResult = TypeInspectionResult(typedEntities = Map(
        TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkingProp2") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
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
}
