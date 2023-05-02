package org.knora.webapi.slice.admin.domain.service

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.valueobjects.V2.StringLiteralV2
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants

object ProjectADMServiceSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] =
    suite("projectDataNamedGraphV2 should return the data named graph of a project with short code for")(
      test("a ProjectADM") {
        val shortname = "someProject"
        val shortcode = "0001"
        val p = ProjectADM(
          id = IriTestConstants.Project.TestProject,
          shortname = shortname,
          shortcode = shortcode,
          longname = None,
          description = List(StringLiteralV2("description not used in test but is required by constructor", None)),
          keywords = List.empty,
          logo = None,
          ontologies = List.empty,
          status = true,
          selfjoin = true
        )
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(p).value == s"http://www.knora.org/data/$shortcode/$shortname"
        )
      },
      test("a KnoraProject") {
        val shortcode = "0002"
        val shortname = "someOtherProject"
        val p: KnoraProject = KnoraProject(
          id = InternalIri(IriTestConstants.Project.TestProject),
          shortname = shortname,
          shortcode = shortcode,
          longname = None,
          description = List(StringLiteralV2("description not used in test but is required by constructor", None)),
          keywords = List.empty,
          logo = None,
          status = true,
          selfjoin = true
        )
        assertTrue(
          ProjectADMService.projectDataNamedGraphV2(p).value == s"http://www.knora.org/data/$shortcode/$shortname"
        )
      }
    )
}
