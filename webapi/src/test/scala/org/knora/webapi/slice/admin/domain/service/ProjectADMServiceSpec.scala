package org.knora.webapi.slice.admin.domain.service

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object ProjectADMServiceSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] =
    suite("projectDataNamedGraphV2 should return the data named graph of a project with short code for")(
      test("anythingProject") {
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(SharedTestDataADM.anythingProject).value ==
            SharedOntologyTestDataADM.ANYTHING_DATA_IRI
        )
      },
      test("imagesProject") {
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(SharedTestDataADM.imagesProject).value ==
            SharedOntologyTestDataADM.IMAGES_DATA_IRI
        )
      },
      test("anythingProject") {
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(SharedTestDataADM.beolProject).value ==
            SharedOntologyTestDataADM.BEOL_DATA_IRI
        )
      },
      test("anythingProject") {
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(SharedTestDataADM.incunabulaProject).value ==
            SharedOntologyTestDataADM.INCUNABULA_DATA_IRI
        )
      },
      test("anythingProject") {
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(SharedTestDataADM.dokubibProject).value ==
            SharedOntologyTestDataADM.DOKUBIB_DATA_IRI
        )
      }
    )
}
