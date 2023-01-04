package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import zio.test._

import org.knora.webapi.IRI

object IriConverterLiveSpec extends ZIOSpecDefault {

  private val someInternalIri: IRI = "http://www.knora.org/ontology/0001/anything#Thing"
  private val someExternalIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"

  def spec: Spec[Any, Throwable] =
    suite("IriConverter")(
      suite("asInternalIri(IRI)")(
        test("should not convert an already internal iri") {
          for {
            actual <- IriConverter.asInternalIri(someInternalIri)
          } yield assertTrue(actual == InternalIri(someInternalIri))
        },
        test("should convert an external resourceClassIri to the internal representation") {
          for {
            actual <- IriConverter.asInternalIri(someExternalIri)
          } yield assertTrue(actual == InternalIri(someInternalIri))
        },
        test("should fail if String is no IRI") {
          for {
            actual <- IriConverter.asInternalIri("notAnIRI").exit
          } yield assertTrue(actual.isFailure)
        }
      ),
      suite("asSmartIri(IRI)")(
        test("when provided an internal Iri should return correct SmartIri") {
          for {
            actual <- IriConverter.asSmartIri(someInternalIri)
          } yield assertTrue(actual.toIri == someInternalIri)
        },
        test("when provided an external Iri should return correct SmartIri") {
          for {
            actual <- IriConverter.asSmartIri(someExternalIri)
          } yield assertTrue(actual.toIri == someExternalIri)
        }
      ),
      suite("asInternalSmartIri(InternalIri)")(
        test("should return correct SmartIri") {
          for {
            actual <- IriConverter.asInternalSmartIri(someInternalIri)
          } yield assertTrue(actual.toIri == someInternalIri)
        },
        test("should fail if if not an InternalIri") {
          for {
            actual <-
              IriConverter.asInternalSmartIri(someExternalIri).exit
          } yield assertTrue(actual.isFailure)
        }
      )
    ).provide(IriConverter.layer, StringFormatter.test)
}
