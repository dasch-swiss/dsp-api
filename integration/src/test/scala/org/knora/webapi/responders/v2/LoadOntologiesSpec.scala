/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.apache.pekko
import zio.ZIO

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

import pekko.actor.Status

/**
 * Tests that the [[OntologyCache.refreshCache]] method does not load invalid data into the cache.
 */
class LoadOntologiesSpec extends E2ESpec {
  private val INVALID_ONTO_NAME = "http://www.knora.org/ontology/invalid"

  /**
   * Asserts that after having reset the repository with the provided invalid
   * data, the data is not loaded into the cache.
   *
   * @param rdfDataObjs       the RDF data object with invalid data
   * @return                  SuccessResponseV2
   */
  private def resetRepositoryAndLoadDataIntoCache(
    rdfDataObjs: List[RdfDataObject],
  ): Either[Status.Failure, SuccessResponseV2] = {
    UnsafeZioRun.runOrThrow(
      ZIO
        .serviceWithZIO[TriplestoreService](_.resetTripleStoreContent(rdfDataObjs))
        .timeout(java.time.Duration.ofMinutes(5)),
    )

    UnsafeZioRun
      .run(ZIO.serviceWithZIO[OntologyCache](_.refreshCache()))
      .toEither
      .map(_ => SuccessResponseV2("OK"))
      .left
      .map(e => Status.Failure(e))
  }

  "The LoadOntologiesRequestV2 request sent to the OntologyResponderV2" should {

    "not load an ontology that has no knora-base:attachedToProject" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/onto-without-project.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a class that's missing a cardinality for a link value property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/missing-link-value-cardinality-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a class that's missing a cardinality for a link property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/missing-link-cardinality-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a class with a cardinality whose subject class constraint is incompatible with the class" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-incompatible-with-scc-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class without an rdfs:label" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-without-label-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource property without an rdfs:label" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/property-without-label-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class that is also a standoff class" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/resource-class-is-standoff-class-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class with a cardinality on an undefined property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-missing-property-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class with a directly defined cardinality on a non-resource property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-non-resource-prop-cardinality-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class with a cardinality on knora-base:resourceProperty" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-cardinality-on-kbresprop-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class with a cardinality on knora-base:hasValue" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-cardinality-on-kbhasvalue-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource class with a base class that has a Knora IRI but isn't a resource class" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/resource-class-with-invalid-base-class-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a standoff class with a cardinality on a resource property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/standoff-class-with-resprop-cardinality-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a standoff class with a base class that's not a standoff class" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/standoff-class-with-invalid-base-class-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a property with a subject class constraint of foaf:Person" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-non-knora-scc-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a Knora value property with a subject class constraint of knora-base:TextValue" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-value-scc-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a property with a subject class constraint of salsah-gui:Guielement" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-guielement-scc-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a property with an object class constraint of foaf:Person" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-non-knora-occ-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a property whose object class constraint is incompatible with its base property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-incompatible-occ-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a class with cardinalities for a link property and a matching link value property, except that the link property isn't really a link property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-misdefined-link-property-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a class with cardinalities for a link property and a matching link value property, except that the link value property isn't really a link value property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-misdefined-link-value-property-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource property with no rdfs:label" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/resource-prop-without-label-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a property that's a subproperty of both knora-base:hasValue and knora-base:hasLinkTo" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-both-value-and-link-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a property that's a subproperty of knora-base:hasFileValue" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/filevalue-prop-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a resource property with a base property that has a Knora IRI but isn't a resource property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/resource-prop-wrong-base-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a cardinality that contains salsah-gui:guiElement" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/cardinality-with-guielement-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load an ontology containing a cardinality that contains salsah-gui:guiAttribute" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/cardinality-with-guiattribute-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing an owl:TransitiveProperty" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/transitive-prop.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology with a class that has cardinalities both on property P and on a subproperty of P" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-inherits-prop-and-subprop-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing mismatched cardinalities for a link property and a link value property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-mismatched-link-cardinalities-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing an invalid cardinality on a boolean property" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/invalid-card-on-boolean-prop.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing a class with a cardinality on a property from a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-non-shared-cardinality.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing a class with a base class defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/class-with-non-shared-base-class.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing a property with a base property defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-non-shared-base-prop.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing a property whose subject class constraint is defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-non-shared-scc.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing a property whose object class constraint is defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/prop-with-non-shared-occ.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }

    "not load a project-specific ontology containing a class with two cardinalities that override the same base class cardinality of 1 or 0-1" in {
      val invalidOnto = List(
        RdfDataObject(
          path =
            "test_data/generated_test_data/responders.v2.LoadOntologiesRequestV2Spec/conflicting-cardinalities-onto.ttl",
          name = INVALID_ONTO_NAME,
        ),
      )
      val result = resetRepositoryAndLoadDataIntoCache(invalidOnto)
      assert(result.isLeft)
    }
  }
}
