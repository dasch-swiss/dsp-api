package dsp.valueobjects

import zio.prelude.Validation
import zio.test._

import java.util.UUID

import dsp.errors.ValidationException

object IdSpec extends ZIOSpecDefault {

  private val shortCode = Project.ShortCode.make("0001").fold(e => throw e.head, v => v)
  private val uuid      = UUID.randomUUID()
  private val iri = Iri.ProjectIri
    .make(s"http://rdfh.ch/projects/${UUID.randomUUID()}")
    .fold(e => throw e.head, v => v)
  private val invalidIri = Iri.ProjectIri
    .make(s"http://rdfh.ch/projects/anything")
    .fold(e => throw e.head, v => v)

  override def spec = suite("ID Specs")(projectIdTests)

  // TODO: should have tests for other IDs too
  val projectIdTests = suite("ProjectId")(
    test("should create an ID from only a shortcode") {
      (for {
        projectId <- ProjectId.make(shortCode)
      } yield assertTrue(projectId.shortCode == shortCode) &&
        assertTrue(!projectId.iri.value.isEmpty()) &&
        assertTrue(!projectId.uuid.toString().isEmpty())).toZIO
    },
    test("should create an ID from a shortcode and a UUID") {
      val expectedIri = Iri.ProjectIri.make(s"http://rdfh.ch/projects/${uuid}").fold(e => throw e.head, v => v)
      (for {
        projectId <- ProjectId.fromUuid(uuid, shortCode)
      } yield assertTrue(projectId.shortCode == shortCode) &&
        assertTrue(projectId.iri == expectedIri) &&
        assertTrue(projectId.uuid == uuid)).toZIO
    },
    test("should create an ID from a shortcode and an IRI") {
      (for {
        projectId <- ProjectId.fromIri(iri, shortCode)
      } yield assertTrue(projectId.shortCode == shortCode) &&
        assertTrue(projectId.iri == iri) &&
        assertTrue(projectId.uuid.toString().length() == 36)).toZIO
    },
    test("should not create an ID from a shortcode and an IRI if the IRI does not contain a valid UUID") {
      val idFromInvalidIri = ProjectId.fromIri(invalidIri, shortCode)
      val expectedResult   = Validation.fail(ValidationException(IdErrorMessages.IriDoesNotContainUuid(invalidIri)))
      assertTrue(idFromInvalidIri == expectedResult)
    }
  )
}
