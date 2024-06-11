package org.knora.webapi.slice.admin.repo.service
import org.eclipse.rdf4j.common.net.ParsedIRI
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.repo.rdf.Errors
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import zio.IO
import zio.ZIO
import zio.ZLayer
import zio.test.*
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.NonEmptyChunk

object CachingEntityRepoSpec extends ZIOSpecDefault {

  private val repo    = ZIO.serviceWithZIO[TestRepo]
  private val cache   = ZIO.serviceWith[EntityCache[TestId, TestEntity]]
  private val entity1 = TestEntity(TestId("https://example.com/1"), "someName1")
  private val entity2 = TestEntity(TestId("https://example.com/2"), "someName2")
  private val entity3 = TestEntity(TestId("https://example.com/3"), "someName3")

  val spec = suite("CachingEntityRepo")(
    test("findById returns None") {
      for {
        actual <- repo(_.findById(TestId("https://example.com/1")))
      } yield assertTrue(actual.isEmpty)
    },
    test("save") {
      for {
        _           <- repo(_.save(entity1))
        actual      <- repo(_.findById(entity1.id))
        actualCache <- cache(_.get(entity1.id))
      } yield assertTrue(actual.contains(entity1), actualCache.contains(entity1))
    },
    test("delete") {
      for {
        _          <- repo(_.save(entity2))
        created    <- repo(_.findById(entity2.id)).someOrFail(IllegalStateException("Entity Missing"))
        _          <- repo(_.delete(created))
        actual     <- repo(_.findById(entity2.id))
        cacheEmpty <- checkAllEntityRemovedFromCache(entity2)
      } yield assertTrue(actual.isEmpty, cacheEmpty)
    },
    test("deleteById") {
      for {
        _          <- repo(_.save(entity3))
        _          <- repo(_.findById(entity3.id)).someOrFail(IllegalStateException("Entity Missing"))
        _          <- repo(_.deleteById(entity3.id))
        actual     <- repo(_.findById(entity3.id))
        cacheEmpty <- checkAllEntityRemovedFromCache(entity3)
      } yield assertTrue(actual.isEmpty, cacheEmpty)
    },
    test("deleteAll") {
      val entities = Seq(entity1, entity2)
      for {
        _          <- repo(_.saveAll(entities))
        _          <- repo(_.deleteAll(entities))
        actual1    <- repo(_.findById(entity1.id))
        actual2    <- repo(_.findById(entity2.id))
        cacheEmpty <- checkAllEntitiesRemovedFromCache(entities)
      } yield assertTrue(actual1.isEmpty, actual2.isEmpty, cacheEmpty)
    },
    test("deleteAllById") {
      val entities = Seq(entity2, entity3)
      for {
        _          <- repo(_.saveAll(entities))
        _          <- repo(_.deleteAllById(entities.map(_.id)))
        actual2    <- repo(_.findById(entity2.id))
        actual3    <- repo(_.findById(entity3.id))
        cacheEmpty <- checkAllEntitiesRemovedFromCache(entities)
      } yield assertTrue(actual2.isEmpty, actual3.isEmpty, cacheEmpty)
    },
  ).provide(
    TriplestoreServiceInMemory.emptyLayer,
    CacheManager.layer,
    EntityCache.layer[TestId, TestEntity]("testEntity"),
    StringFormatter.test,
    TestRepo.layer,
  )

  private def checkAllEntityRemovedFromCache(entity: TestEntity) = checkAllEntitiesRemovedFromCache(Seq(entity))
  private def checkAllEntitiesRemovedFromCache(entities: Seq[TestEntity]) =
    cache(c => entities.map(_.id).map(id => c.cache.get(id).isEmpty).forall(b => b))
}

final case class TestId(value: String)                extends StringValue
final case class TestEntity(id: TestId, name: String) extends EntityWithId[TestId]
final case class TestMapper() extends RdfEntityMapper[TestEntity] {
  override def toTriples(entity: TestEntity): TriplePattern =
    Rdf
      .iri(entity.id.value)
      .isA(Rdf.iri(TestRepo.resourceClass))
      .andHas(TestRepo.propertyIri, entity.name)

  override def toEntity(resource: RdfResource): IO[Errors.RdfError, TestEntity] =
    for {
      id   <- resource.getSubjectIri
      name <- resource.getStringLiteralOrFail[String](TestRepo.property)(Right(_))
    } yield TestEntity(TestId(id.value), name)
}

final case class TestRepo(
  triplestore: TriplestoreService,
  cache: EntityCache[TestId, TestEntity],
) extends CachingEntityRepo(triplestore, TestMapper(), cache) {
  override protected def resourceClass: ParsedIRI           = ParsedIRI.create(TestRepo.resourceClass)
  override protected def namedGraphIri: Iri                 = Rdf.iri("https://example.com/namedGraph")
  override protected def entityProperties: EntityProperties = EntityProperties(NonEmptyChunk(TestRepo.propertyIri))
}

object TestRepo {
  val property: String      = "https://example.com/prop/#name"
  val propertyIri: Iri      = Rdf.iri(property)
  val resourceClass: String = "https://example.com/class/test-entity"
  val layer                 = ZLayer.derive[TestRepo]
}
