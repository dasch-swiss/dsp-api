package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain
import org.knora.webapi.slice.resourceinfo.domain.{InternalIri, IriConverter}
import zio.test._

object IriConverterLiveSpec extends ZIOSpecDefault {

  def spec = suite("IriConverterLive")(
    test("should not convert the projectIri") {
      for {
        internal <- IriConverter.asInternalIri("http://project-iri")
      } yield assertTrue(internal == domain.InternalIri("http://project-iri"))
    },
    test("should convert a resourceClassIri") {
      for {
        internal <- IriConverter.asInternalIri("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
      } yield assertTrue(internal == InternalIri("http://www.knora.org/ontology/0001/anything#Thing"))
    }
  ).provide(IriConverter.layer, StringFormatter.test)
}
