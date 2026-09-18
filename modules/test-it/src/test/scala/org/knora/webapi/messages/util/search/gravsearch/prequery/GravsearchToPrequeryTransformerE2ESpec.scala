/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.AssertionException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.GoldenTest
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchToPrequeryTransformerE2ESpec extends E2EZSpec with GoldenTest {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val inspectionRunner = ZIO.serviceWithZIO[GravsearchTypeInspectionRunner]
  private val queryTraverser   = ZIO.serviceWithZIO[QueryTraverser]

  private def transformQuery(
    query: String,
  ): ZIO[AppConfig & QueryTraverser & GravsearchTypeInspectionRunner, Throwable, SelectQuery] = for {
    query                <- ZIO.attempt(GravsearchParser.parseQuery(query))
    sanitizedWhereClause <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)
    typeInspectionResult <- inspectionRunner(_.inspectTypes(query.whereClause))
    _                    <- GravsearchQueryChecker.checkConstructClause(query.constructClause, typeInspectionResult)
    querySchema          <- ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))
    appConfig            <- ZIO.service[AppConfig]
    transformer          <- ZIO.attempt(
                     new GravsearchToPrequeryTransformer(
                       constructClause = query.constructClause,
                       typeInspectionResult = typeInspectionResult,
                       querySchema = querySchema,
                       appConfig = appConfig,
                     ),
                   )
    preQuery <- queryTraverser(
                  _.transformConstructToSelect(query.copy(whereClause = sanitizedWhereClause), transformer),
                )
  } yield preQuery

  /** See [[GravsearchInferencePipelineTestSupport]] for why the golden snapshot needs the full pipeline. */
  private def transformQueryWithInference(
    query: String,
    limitResultsToProject: Option[ProjectIri] = None,
  ): ZIO[
    AppConfig & QueryTraverser & GravsearchTypeInspectionRunner & OntologyInferencer & InferenceOptimizationService,
    Throwable,
    SelectQuery,
  ] =
    GravsearchInferencePipelineTestSupport.transformQueryWithInference(
      query,
      (constructClause, typeInspectionResult, querySchema, appConfig) =>
        new GravsearchToPrequeryTransformer(
          constructClause = constructClause,
          typeInspectionResult = typeInspectionResult,
          querySchema = querySchema,
          appConfig = appConfig,
        ),
      limitResultsToProject = limitResultsToProject,
    )

  val inputQueryWithDateNonOptionalSortCriterion: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |  onto:hasDate knora-api:objectType knora-api:Date .
      |  ?date a knora-api:Date .
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateNonOptionalSortCriterionComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateNonOptionalSortCriterionAndFilter: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |  onto:hasDate knora-api:objectType knora-api:Date .
      |  ?date a knora-api:Date .
      |
      |  FILTER(?date > "GREGORIAN:2012-01-01"^^knora-api:Date)
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateNonOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |
      |  FILTER(knora-api:toSimpleDate(?date) > "GREGORIAN:2012-01-01"^^knora-api-simple:Date)
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateOptionalSortCriterion: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |    onto:hasDate knora-api:objectType knora-api:Date .
      |    ?date a knora-api:Date .
      |
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateOptionalSortCriterionComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateOptionalSortCriterionAndFilter: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |    onto:hasDate knora-api:objectType knora-api:Date .
      |    ?date a knora-api:Date .
      |
      |    FILTER(?date > "GREGORIAN:2012-01-01"^^knora-api:Date)
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |
      |    FILTER(knora-api:toSimpleDate(?date) > "GREGORIAN:2012-01-01"^^knora-api-simple:Date)
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDecimalOptionalSortCriterion: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |        anything:hasDecimal knora-api:objectType xsd:decimal .
      |
      |        ?decimal a xsd:decimal .
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val inputQueryWithDecimalOptionalSortCriterionComplex: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val inputQueryWithDecimalOptionalSortCriterionAndFilter: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |        anything:hasDecimal knora-api:objectType xsd:decimal .
      |
      |        ?decimal a xsd:decimal .
      |
      |        FILTER(?decimal > "2"^^xsd:decimal)
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val inputQueryWithDecimalOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |
      |        ?decimal knora-api:decimalValueAsDecimal ?decimalVal .
      |
      |        FILTER(?decimalVal > "2"^^xsd:decimal)
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val InputQueryWithRdfsLabelAndLiteralInSimpleSchema: String =
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

  val InputQueryWithRdfsLabelAndLiteralInComplexSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
      |}
        """.stripMargin

  val InputQueryWithRdfsLabelAndVariableInSimpleSchema: String =
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

  val InputQueryWithRdfsLabelAndVariableInComplexSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
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

  val InputQueryWithRdfsLabelAndRegexInSimpleSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?bookLabel .
      |    FILTER regex(?bookLabel, "Zeit", "i")
      |}""".stripMargin

  val InputQueryWithRdfsLabelAndRegexInComplexSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?bookLabel .
      |    FILTER regex(?bookLabel, "Zeit", "i")
      |}""".stripMargin

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
      |
      |    OPTIONAL {
      |    ?document beol:hasRecipient ?recipient .
      |
      |    ?recipient beol:hasFamilyName ?familyName .
      |
      |    FILTER knora-api:matchText(?familyName, "Bernoulli")
      |}
      |}
                """.stripMargin

  val InputQueryWithUnionScopes: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |    ?thing onto:hasText ?text .
      |} WHERE {
      |    ?thing a onto:Thing .
      |    ?thing onto:hasText ?text .
      |
      |    {
      |        ?thing onto:hasInteger ?int .
      |        FILTER(?int = 1)
      |    } UNION {
      |        ?thing onto:hasText ?text .
      |        FILTER regex(?text, "Abel", "i") .
      |    }
      |}
      |ORDER BY ASC(?text)
      |OFFSET 0""".stripMargin

  val queryToReorder: String = """
                                 |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
                                 |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                                 |
                                 |CONSTRUCT {
                                 |  ?letter knora-api:isMainResource true .
                                 |  ?letter ?linkingProp1  ?person1 .
                                 |  ?letter ?linkingProp2  ?person2 .
                                 |  ?letter beol:creationDate ?date .
                                 |} WHERE {
                                 |  ?letter beol:creationDate ?date .
                                 |
                                 |  ?letter ?linkingProp1 ?person1 .
                                 |  FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
                                 |
                                 |  ?letter ?linkingProp2 ?person2 .
                                 |  FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
                                 |
                                 |  ?person1 beol:hasIAFIdentifier ?gnd1 .
                                 |  ?gnd1 knora-api:valueAsString "(DE-588)118531379" .
                                 |
                                 |  ?person2 beol:hasIAFIdentifier ?gnd2 .
                                 |  ?gnd2 knora-api:valueAsString "(DE-588)118696149" .
                                 |} ORDER BY ?date""".stripMargin

  val queryToReorderWithCycle: String = """
                                          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
                                          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                                          |
                                          |CONSTRUCT {
                                          |    ?thing knora-api:isMainResource true .
                                          |} WHERE {
                                          |  ?thing anything:hasOtherThing ?thing1 .
                                          |  ?thing1 anything:hasOtherThing ?thing2 .
                                          |  ?thing2 anything:hasOtherThing ?thing .
                                          |} """.stripMargin

  val queryToReorderWithMinus: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |} WHERE {
      |  ?thing a knora-api:Resource .
      |  ?thing a anything:Thing .
      |  MINUS {
      |    ?thing anything:hasInteger ?intVal .
      |    ?intVal a xsd:integer .
      |    FILTER(?intVal = 123454321 || ?intVal = 999999999)
      |  }
      |}""".stripMargin

  val queryToReorderWithUnion: String =
    s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
       |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
       |CONSTRUCT {
       |    ?thing knora-api:isMainResource true .
       |    ?thing anything:hasInteger ?int .
       |    ?thing anything:hasRichtext ?richtext .
       |    ?thing anything:hasText ?text .
       |} WHERE {
       |    ?thing a knora-api:Resource .
       |    ?thing a anything:Thing .
       |    ?thing anything:hasInteger ?int .
       |
       |    {
       |        ?thing anything:hasRichtext ?richtext .
       |        FILTER knora-api:matchText(?richtext, "test")
       |
       |		    ?thing anything:hasInteger ?int .
       |		    ?int knora-api:intValueAsInt 1 .
       |    }
       |    UNION
       |    {
       |        ?thing anything:hasText ?text .
       |        FILTER knora-api:matchText(?text, "test")
       |
       |		    ?thing anything:hasInteger ?int .
       |		    ?int knora-api:intValueAsInt 3 .
       |    }
       |}
       |ORDER BY (?int)""".stripMargin

  val queryWithStandoffTagHasStartAncestor: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |    ?thing anything:hasText ?text .
      |} WHERE {
      |    ?thing a anything:Thing .
      |    ?thing anything:hasText ?text .
      |    ?text knora-api:textValueHasStandoff ?standoffDateTag .
      |    ?standoffDateTag a knora-api:StandoffDateTag .
      |    FILTER(knora-api:toSimpleDate(?standoffDateTag) = "GREGORIAN:2016-12-24 CE"^^knora-api-simple:Date)
      |    ?standoffDateTag knora-api:standoffTagHasStartAncestor ?standoffParagraphTag .
      |    ?standoffParagraphTag a standoff:StandoffParagraphTag .
      |}""".stripMargin

  val queryClasslessMatchFulltext: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |CONSTRUCT {
      |    ?mainRes knora-api:isMainResource true .
      |} WHERE {
      |    ?mainRes a knora-api:Resource .
      |    FILTER knora-api:matchFulltext(?mainRes, "Zeitglöcklein")
      |}""".stripMargin

  val queryClassRestrictedMatchFulltext: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |} WHERE {
      |    ?thing a anything:Thing .
      |    FILTER knora-api:matchFulltext(?thing, "Zeitglöcklein")
      |}""".stripMargin

  val queryMatchFulltextInUnion: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |} WHERE {
      |    ?thing a anything:Thing .
      |    {
      |        ?thing anything:hasInteger ?int1 .
      |        FILTER knora-api:matchFulltext(?thing, "Zeitglöcklein")
      |    }
      |    UNION
      |    {
      |        ?thing anything:hasInteger ?int2 .
      |        FILTER(?int2 = 1)
      |    }
      |}""".stripMargin

  val queryListNodeAnchor: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |
      |CONSTRUCT {
      |  ?letter knora-api:isMainResource true .
      |  ?letter beol:hasSubject ?subj .
      |} WHERE {
      |  ?letter a beol:letter .
      |  ?letter beol:hasSubject ?subj .
      |  ?subj knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
      |}
        """.stripMargin

  val queryLinkTargetAnchor: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |
      |CONSTRUCT {
      |  ?letter knora-api:isMainResource true .
      |  ?letter beol:hasAuthor <http://rdfh.ch/0801/anchor-person> .
      |} WHERE {
      |  ?letter a beol:letter .
      |  ?letter beol:hasAuthor <http://rdfh.ch/0801/anchor-person> .
      |}
        """.stripMargin

  val queryClassValuesLabelFilterOrderBy: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |
      |CONSTRUCT {
      |  ?src knora-api:isMainResource true .
      |  ?src beol:title ?title .
      |} WHERE {
      |  ?src a beol:writtenSource .
      |  ?src rdfs:label ?label .
      |  ?src beol:title ?title .
      |  ?title knora-api:valueAsString ?titleStr .
      |  FILTER(?titleStr = "Basel"^^xsd:string)
      |} ORDER BY ASC(?label)
        """.stripMargin

  val queryFilterBeforeStatementsInUnion: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |} WHERE {
      |  ?thing a anything:Thing .
      |  {
      |    {
      |      ?thing anything:hasText ?text .
      |      FILTER(?intVal > 1)
      |    }
      |    ?thing anything:hasInteger ?int .
      |    ?int knora-api:intValueAsInt ?intVal .
      |  } UNION {
      |    ?thing anything:hasRichtext ?richtext .
      |  }
      |}
        """.stripMargin

  val queryDateFilterInUnionAndTopLevel: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |} WHERE {
      |  ?thing a anything:Thing .
      |  ?thing anything:hasDate ?date .
      |  FILTER(?date < "GREGORIAN:2000"^^knora-api:Date)
      |  {
      |    ?thing anything:hasDate ?date .
      |    FILTER(?date < "GREGORIAN:1900"^^knora-api:Date)
      |  } UNION {
      |    ?thing anything:hasInteger ?int .
      |  }
      |} ORDER BY ?date
        """.stripMargin

  val queryWithKnoraApiResource: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |CONSTRUCT {
      |    ?resource knora-api:isMainResource true .
      |    ?resource ?p ?text .
      |} WHERE {
      |    ?resource a knora-api:Resource .
      |    ?resource ?p ?text .
      |    ?p knora-api:objectType knora-api:TextValue .
      |    FILTER knora-api:matchText(?text, "der")
      |}""".stripMargin

  override val e2eSpec = suite("The NonTriplestoreSpecificGravsearchToPrequeryGenerator object")(
    test("transform an input query with an optional property criterion without removing the rdf:type statement") {
      transformQueryWithInference(queryWithOptional)
        .map(actual => assertGolden(actual.toSparql, "optional"))
    },
    test("transform an input query with a date as a non optional sort criterion") {
      transformQueryWithInference(inputQueryWithDateNonOptionalSortCriterion)
        .map(actual => assertGolden(actual.toSparql, "dateNonOptionalSortCriterion"))
    },
    test("transform an input query with a date as a non optional sort criterion (submitted in complex schema)") {
      transformQueryWithInference(inputQueryWithDateNonOptionalSortCriterionComplex)
        .map(actual => assertGolden(actual.toSparql, "dateNonOptionalSortCriterion"))
    },
    test("transform an input query with a date as non optional sort criterion and a filter") {
      transformQueryWithInference(inputQueryWithDateNonOptionalSortCriterionAndFilter)
        .map(actual => assertGolden(actual.toSparql, "dateNonOptionalSortCriterionAndFilter"))
    },
    test(
      "transform an input query with a date as non optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQueryWithInference(inputQueryWithDateNonOptionalSortCriterionAndFilterComplex)
        .map(actual => assertGolden(actual.toSparql, "dateNonOptionalSortCriterionAndFilter"))
    },
    test("transform an input query with a date as an optional sort criterion") {
      transformQueryWithInference(inputQueryWithDateOptionalSortCriterion)
        .map(actual => assertGolden(actual.toSparql, "dateOptionalSortCriterion"))
    },
    test("transform an input query with a date as an optional sort criterion (submitted in complex schema)") {
      transformQueryWithInference(inputQueryWithDateOptionalSortCriterionComplex)
        .map(actual => assertGolden(actual.toSparql, "dateOptionalSortCriterion"))
    },
    test("transform an input query with a date as an optional sort criterion and a filter") {
      transformQueryWithInference(inputQueryWithDateOptionalSortCriterionAndFilter)
        .map(actual => assertGolden(actual.toSparql, "dateOptionalSortCriterionAndFilter"))
    },
    test(
      "transform an input query with a date as an optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQueryWithInference(inputQueryWithDateOptionalSortCriterionAndFilterComplex)
        .map(actual => assertGolden(actual.toSparql, "dateOptionalSortCriterionAndFilter"))
    },
    test("transform an input query with a decimal as an optional sort criterion") {
      transformQueryWithInference(inputQueryWithDecimalOptionalSortCriterion)
        .map(actual => assertGolden(actual.toSparql, "decimalOptionalSortCriterion"))
    },
    test("transform an input query with a decimal as an optional sort criterion (submitted in complex schema)") {
      transformQueryWithInference(inputQueryWithDecimalOptionalSortCriterionComplex)
        .map(actual => assertGolden(actual.toSparql, "decimalOptionalSortCriterion"))
    },
    test("transform an input query with a decimal as an optional sort criterion and a filter") {
      transformQueryWithInference(inputQueryWithDecimalOptionalSortCriterionAndFilter)
        .map(actual => assertGolden(actual.toSparql, "decimalOptionalSortCriterionAndFilter"))
    },
    test(
      "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQueryWithInference(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex)
        .map(actual => assertGolden(actual.toSparql, "decimalOptionalSortCriterionAndFilterComplex"))
    },
    test("transform an input query using rdfs:label and a literal in the simple schema") {
      transformQueryWithInference(InputQueryWithRdfsLabelAndLiteralInSimpleSchema)
        .map(actual => assertGolden(actual.toSparql, "rdfsLabelAndLiteral"))
    },
    test("transform an input query using rdfs:label and a literal in the complex schema") {
      transformQueryWithInference(InputQueryWithRdfsLabelAndLiteralInComplexSchema)
        .map(actual => assertGolden(actual.toSparql, "rdfsLabelAndLiteral"))
    },
    test("transform an input query using rdfs:label and a variable in the simple schema") {
      transformQueryWithInference(InputQueryWithRdfsLabelAndVariableInSimpleSchema)
        .map(actual => assertGolden(actual.toSparql, "rdfsLabelAndVariable"))
    },
    test("transform an input query using rdfs:label and a variable in the complex schema") {
      transformQueryWithInference(InputQueryWithRdfsLabelAndVariableInComplexSchema)
        .map(actual => assertGolden(actual.toSparql, "rdfsLabelAndVariable"))
    },
    test("transform an input query using rdfs:label and a regex in the simple schema") {
      transformQueryWithInference(InputQueryWithRdfsLabelAndRegexInSimpleSchema)
        .map(actual => assertGolden(actual.toSparql, "rdfsLabelAndRegex"))
    },
    test("transform an input query using rdfs:label and a regex in the complex schema") {
      transformQueryWithInference(InputQueryWithRdfsLabelAndRegexInComplexSchema)
        .map(actual => assertGolden(actual.toSparql, "rdfsLabelAndRegex"))
    },
    test("transform an input query with UNION scopes in the simple schema") {
      transformQueryWithInference(InputQueryWithUnionScopes)
        .map(actual => assertGolden(actual.toSparql, "unionScopes"))
    },
    test("transform an input query with knora-api:standoffTagHasStartAncestor") {
      transformQueryWithInference(queryWithStandoffTagHasStartAncestor)
        .map(actual => assertGolden(actual.toSparql, "standoffTagHasStartAncestor"))
    },
    test("reorder query patterns in where clause") {
      transformQueryWithInference(queryToReorder)
        .map(actual => assertGolden(actual.toSparql, "reorder"))
    },
    test("reorder query patterns in where clause with union") {
      transformQueryWithInference(queryToReorderWithUnion)
        .map(actual => assertGolden(actual.toSparql, "reorderWithUnion"))
    },
    test("reorder query patterns in where clause with optional") {
      transformQueryWithInference(queryWithOptional)
        .map(actual => assertGolden(actual.toSparql, "optional"))
    },
    test("reorder query patterns with minus scope") {
      transformQueryWithInference(queryToReorderWithMinus)
        .map(actual => assertGolden(actual.toSparql, "reorderWithMinus"))
    },
    test("reorder a query with a cycle") {
      transformQueryWithInference(queryToReorderWithCycle)
        .map(actual => assertGolden(actual.toSparql, "reorderWithCycle"))
    },
    test(
      "generate the fulltext-index-anchored matchFulltext expansion for a classless query, hoisted ahead of the class-VALUES block",
    ) {
      transformQueryWithInference(queryClasslessMatchFulltext)
        .map(actual => assertGolden(actual.toSparql, "classlessMatchFulltext"))
    },
    test("generate the fulltext-index-anchored matchFulltext expansion for a class-restricted query") {
      transformQueryWithInference(queryClassRestrictedMatchFulltext)
        .map(actual => assertGolden(actual.toSparql, "classRestrictedMatchFulltext"))
    },
    test("generate the matchFulltext expansion when the FILTER is inside a UNION block") {
      transformQueryWithInference(queryMatchFulltextInUnion)
        .map(actual => assertGolden(actual.toSparql, "matchFulltextInUnion"))
    },
    test("transform a query anchored on a list-node value") {
      transformQueryWithInference(queryListNodeAnchor)
        .map(actual =>
          assertGolden(actual.toSparql, "listNodeAnchor") &&
            assertGolden(GravsearchInferencePipelineTestSupport.shapeSummary(actual), "listNodeAnchorShape"),
        )
    },
    test("transform a query anchored on a fixed link target") {
      transformQueryWithInference(queryLinkTargetAnchor)
        .map(actual =>
          assertGolden(actual.toSparql, "linkTargetAnchor") &&
            assertGolden(GravsearchInferencePipelineTestSupport.shapeSummary(actual), "linkTargetAnchorShape"),
        )
    },
    test("transform a query filtering on a class value's label and ordering by it") {
      transformQueryWithInference(queryClassValuesLabelFilterOrderBy)
        .map(actual =>
          assertGolden(actual.toSparql, "classValuesLabelFilterOrderBy") &&
            assertGolden(
              GravsearchInferencePipelineTestSupport.shapeSummary(actual),
              "classValuesLabelFilterOrderByShape",
            ),
        )
    },
    test(
      "transform a query filtering on a class value's label and ordering by it, limiting results to a project",
    ) {
      transformQueryWithInference(
        queryClassValuesLabelFilterOrderBy,
        limitResultsToProject = Some(ProjectIri.unsafeFrom("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF")),
      )
        .map(actual =>
          assertGolden(actual.toSparql, "classValuesLabelFilterOrderByProjectLimited") &&
            assertGolden(
              GravsearchInferencePipelineTestSupport.shapeSummary(actual),
              "classValuesLabelFilterOrderByProjectLimitedShape",
            ),
        )
    },
    test("transform a query whose FILTER precedes, in a nested group, the statements binding its variable") {
      transformQueryWithInference(queryFilterBeforeStatementsInUnion)
        .map(actual => assertGolden(actual.toSparql, "filterBeforeStatementsInUnion"))
    },
    test(
      "transform a query filtering the same date variable with the same operator inside a UNION branch and at the top level",
    ) {
      transformQueryWithInference(queryDateFilterInUnionAndTopLevel)
        .map(actual => assertGolden(actual.toSparql, "dateFilterInUnionAndTopLevel"))
    },
    test("not remove rdf:type knora-api:Resource if it's needed") {
      transformQuery(queryWithKnoraApiResource)
        .map(actual =>
          assertTrue(
            actual.whereClause.patterns.contains(
              StatementPattern(
                subj = QueryVariable(variableName = "resource"),
                pred = IriRef(
                  iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri,
                  propertyPathOperator = None,
                ),
              ),
            ),
          ),
        )
    },
  )
}
