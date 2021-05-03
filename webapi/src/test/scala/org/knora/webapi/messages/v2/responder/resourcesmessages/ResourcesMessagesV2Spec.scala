package org.knora.webapi.messages.v2.responder.resourcesmessages

import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.sharedtestdata._

/**
  * Tests [[ResourceMessagesV2]].
  */
class ResourcesMessagesV2Spec extends CoreSpec() {
  "All Resources of a Project With History Get Requests" should {
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
          projectIri = "http://rdfh.ch/0001/thing-with-history", // resource IRI instead of project IRI
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01
        )
      )
      assert(caught.getMessage === "Given IRI is not a project IRI.")
    }
  }
}
