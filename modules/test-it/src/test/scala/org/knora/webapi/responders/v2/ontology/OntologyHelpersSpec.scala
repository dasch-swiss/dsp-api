/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

object OntologyHelpersSpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getGeneralInstance

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
  )

  private val getStrictestCardinalitiesFromClassesSuite = {
    val freetestOntologyIri = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri)
    // define the classes
    val unbounded  = freetestOntologyIri.makeClass("PubMayHaveMany")  // 0-n
    val atLeastOne = freetestOntologyIri.makeClass("PubMustHaveSome") // 1-n
    val zeroOrOne  = freetestOntologyIri.makeClass("PubMayHaveOne")   // 0-1
    val exactlyOne = freetestOntologyIri.makeClass("PubMustHaveOne")  // 1

    // define all test cases and the expected results
    val testCases: List[(Set[ResourceClassIri], Cardinality)] = List(
      (Set(unbounded), Unbounded),
      (Set(atLeastOne), AtLeastOne),
      (Set(zeroOrOne), ZeroOrOne),
      (Set(exactlyOne), ExactlyOne),
      (Set(unbounded, atLeastOne), AtLeastOne),
      (Set(unbounded, zeroOrOne), ZeroOrOne),
      (Set(unbounded, exactlyOne), ExactlyOne),
      (Set(atLeastOne, zeroOrOne), ExactlyOne),
      (Set(atLeastOne, exactlyOne), ExactlyOne),
      (Set(zeroOrOne, exactlyOne), ExactlyOne),
      (Set(unbounded, atLeastOne, zeroOrOne), ExactlyOne),
      (Set(unbounded, atLeastOne, exactlyOne), ExactlyOne),
      (Set(unbounded, zeroOrOne, exactlyOne), ExactlyOne),
      (Set(atLeastOne, zeroOrOne, exactlyOne), ExactlyOne),
      (Set(unbounded, atLeastOne, zeroOrOne, exactlyOne), ExactlyOne),
    )
    // define the property we are interested in
    val hasPublicationDate = freetestOntologyIri.makeProperty("hasPublicationDate")

    def getStrictestCardinalitiesFromClasses(classes: Set[ResourceClassIri]): RIO[OntologyCache, Option[Cardinality]] =
      ZIO.serviceWithZIO[OntologyCache](_.getCacheData).map { cacheData =>
        val internalClasses = classes.map(_.toInternalSchema)
        OntologyHelpers
          .getStrictestCardinalitiesFromClasses(internalClasses, cacheData)
          .get(hasPublicationDate.toInternalSchema)
          .map(_.cardinality)
      }

    suite("getStrictestCardinalitiesFromClasses")(testCases.map {
      case (classes: Set[ResourceClassIri], expected: Cardinality) =>
        test(
          s"given property '${hasPublicationDate.toShortString}' and classes" +
            s"classes '${classes.map(_.toShortString).mkString(", ")}' expect cardinality $expected",
        )(getStrictestCardinalitiesFromClasses(classes).map(actual => assertTrue(actual.contains(expected))))
    })
  }

  override val e2eSpec = suite("OntologyHelpers")(getStrictestCardinalitiesFromClassesSuite)
}
