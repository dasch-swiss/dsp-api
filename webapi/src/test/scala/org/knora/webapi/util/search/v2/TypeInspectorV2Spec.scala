package org.knora.webapi.util.search.v2

import org.scalatest.{Matchers, WordSpec}

/**
  * Tests [[TypeInspectorV2]].
  */
class TypeInspectorV2Spec extends WordSpec with Matchers {
    "TypeInspectorV2" should {
        "get type information from a simple query" in {
            val parsedQuery = SearchParserV2.parseSearchQuery(SearchParserV2Spec.SimpleSparqlConstructQueryWithExplicitTypeAnnotations)
            val typeInspector = new ExplicitTypeInspectorV2(ApiV2Schema.SIMPLE)
            val typeInspectionResult = typeInspector.inspectTypes(parsedQuery.whereClause)
            typeInspectionResult should ===(SimpleTypeInspectionResult)
        }
    }

    val SimpleTypeInspectionResult = TypeInspectionResultV2(typedEntities = Map(
        TypeableVariableV2(variableName = "linkingProp1") -> PropertyTypeInfoV2(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableIriV2(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA") -> NonPropertyTypeInfoV2(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableVariableV2(variableName = "letter") -> NonPropertyTypeInfoV2(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableVariableV2(variableName = "linkingProp2") -> PropertyTypeInfoV2(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"),
        TypeableIriV2(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA") -> NonPropertyTypeInfoV2(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource")
    ))
}
