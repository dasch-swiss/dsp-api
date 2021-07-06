package org.knora.webapi.messages.v2.responder.resourcesmessages

import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.sharedtestdata._

/**
  * Tests [[ResourceMessagesV2]].
  */
class ResourcesMessagesV2Spec extends CoreSpec() {
  "Get history events of all resources of a project" should {
    "fail if given project IRI is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        ProjectResourcesWithHistoryGetRequestV2(
          projectIri = projectIri,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Invalid project IRI: $projectIri")
    }

    "fail if given IRI is not a project Iri" in {
      val caught = intercept[BadRequestException](
        ProjectResourcesWithHistoryGetRequestV2(
          projectIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", // resource IRI instead of project IRI
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === "Given IRI is not a project IRI.")
    }
  }

  "Get history events of a single resource" should {
    "fail if given resource IRI is not valid" in {
      val resourceIri = "invalid-resource-IRI"
      val caught = intercept[BadRequestException](
        ResourceHistoryEventsGetRequestV2(
          resourceIri = resourceIri,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Invalid resource IRI: $resourceIri")
    }

    "fail if given IRI is not a resource IRI" in {
      val resourceIri = "http://rdfh.ch/projects/0001"
      val caught = intercept[BadRequestException](
        ResourceHistoryEventsGetRequestV2(
          resourceIri = resourceIri,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === s"Given IRI is not a resource IRI: $resourceIri")
    }
  }
}
