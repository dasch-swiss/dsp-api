package org.knora.webapi.slice.admin

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname

object FooSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Any] = suite("FooSpec$")(test("foo") {
    val shortname = Shortname.unsafeFrom("foo")
//    val notallowed = shortname.copy(value = "b")
    assertCompletes
  })
}
