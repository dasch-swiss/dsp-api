package org.knora.webapi.slice.ontology.repo.service

import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import zio.test.ZIOSpecDefault
import zio.test._
import zio.Scope

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2

object OntologyRepoLiveSpec extends ZIOSpecDefault {

  private val sf                           = { StringFormatter.initForTest(); StringFormatter.getGeneralInstance }
  private val anUnknownInternalOntologyIri = InternalIri("http://www.knora.org/ontology/0001/anything")
  private val anUnknownClassIri            = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")

  private val aKnownClassIri: InternalIri   = InternalIri("http://www.knora.org/ontology/0001/gizmo#Gizmo")
  private val aKnownClassSmartIri: SmartIri = sf.toSmartIri(aKnownClassIri.value)
  private val ontologySmartIri: SmartIri    = aKnownClassSmartIri.getOntologyFromEntity

  val spec: Spec[TestEnvironment with Scope, Any] =
    suite("OntologyRepoLive")(
      suite("findOntologyBy(InternalIri)")(
        test("when searching for unknown iri => return None") {
          for {
            actual <- OntologyRepo.findOntologyBy(anUnknownInternalOntologyIri)
          } yield assertTrue(actual.isEmpty)
        },
        test("when searching for known iri => return ReadOntology") {
          val ontologyData = ReadOntologyV2(OntologyMetadataV2(ontologySmartIri))
          val cacheData    = OntologyCacheFake.emptyData.copy(ontologies = Map(ontologySmartIri -> ontologyData))
          for {
            _      <- OntologyCacheFake.set(cacheData)
            actual <- OntologyRepo.findOntologyBy(ontologySmartIri.toInternalIri)
          } yield assertTrue(actual.contains(ontologyData))
        }
      ),
      suite("findClassBy(InternalIri)")(
        test("when searching for unknown iri => return None") {
          val classData = ReadClassInfoV2(
            ClassInfoContentV2(aKnownClassSmartIri, ontologySchema = ApiV2Complex),
            allBaseClasses = List.empty
          )
          val ontologyData =
            ReadOntologyV2(OntologyMetadataV2(ontologySmartIri), classes = Map(aKnownClassSmartIri -> classData))
          val cacheData = OntologyCacheFake.emptyData.copy(ontologies = Map(ontologySmartIri -> ontologyData))
          for {
            _      <- OntologyCacheFake.set(cacheData)
            actual <- OntologyRepo.findClassBy(aKnownClassIri)
          } yield assertTrue(actual.contains(classData))
        },
        test("when searching for unknown iri => return None") {
          for {
            actual <- OntologyRepo.findClassBy(anUnknownClassIri)
          } yield assertTrue(actual.isEmpty)
        }
      )
    ).provide(OntologyRepoLive.layer, OntologyCacheFake.emptyCache, IriConverter.layer, StringFormatter.test)
}
