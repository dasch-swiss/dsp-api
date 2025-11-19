/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.v3.*
import org.knora.webapi.slice.common.domain.LanguageCode.*
import org.knora.webapi.slice.ontology.domain.model.RepresentationClass
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object ResourcesPerOntologyEndpointsE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  private def getResourcesPerOntology(projectIri: String) =
    TestApiClient.getJson[List[OntologyAndResourceClasses]](uri"/v3/projects/$projectIri/resourcesPerOntology")

  override val e2eSpec = suite("ResourcesPerOntology Endpoints E2E")(
    suite("GET /v3/projects/{projectIri}/resourcesPerOntology")(
      test("should return resources per ontology for anything project") {
        val projectIri = anythingProjectIri.value
        getResourcesPerOntology(projectIri)
          .flatMap(_.assert200)
          .map { response =>
            assertTrue(
              response == List(
                OntologyAndResourceClasses(
                  ontology = OntologyDto(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/v2",
                    label = "The anything ontology",
                    comment = "",
                  ),
                  classesAndCount = List(
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingWithRepresentation",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Thing with representation",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A thing with a representation",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 0,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingWithRegion",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Thing with region",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A thing with a region",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 0,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingArchive",
                        representationClass = RepresentationClass.ArchiveRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Archive",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "An archive about a thing",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 1,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#BlueThing",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Blaues Ding",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Chose bleue",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Cosa azzurra",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Blue thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "Diese Resource-Klasse beschreibt ein blaues Ding",
                            language = DE,
                          ),
                        ),
                      ),
                      itemCount = 1,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture",
                        representationClass = RepresentationClass.StillImageRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Dingbild",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Image d'une chose",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Immagine di una cosa",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Picture of a thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "Diese Resource-Klasse beschreibt ein Bild eines Dinges",
                            language = DE,
                          ),
                        ),
                      ),
                      itemCount = 4,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#AudioThing",
                        representationClass = RepresentationClass.AudioRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Audio Thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A Resource representing an audio",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 1,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingDocument",
                        representationClass = RepresentationClass.DocumentRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Document",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A document about a thing",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 1,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingWithSeqnum",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Ding mit Sequenznummer",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Chose avec numéro de séquence",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Cosa con numero di sequenza",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Thing with sequence number",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "Diese Resource-Klasse beschreibt ein Ding mit einer Sequenznummer",
                            language = DE,
                          ),
                        ),
                      ),
                      itemCount = 0,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingWithRequiredInt",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Ding",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Chose",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Cosa",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "This thing requires an integer.",
                            language = DE,
                          ),
                        ),
                      ),
                      itemCount = 0,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#VideoThing",
                        representationClass = RepresentationClass.MovingImageRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Video Thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A Resource representing a video",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 1,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Ding",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Chose",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Cosa",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value =
                              "'The whole world is full of things, which means there's a real need for someone to go searching for them. And that's exactly what a thing-searcher does.' --Pippi Longstocking",
                            language = DE,
                          ),
                        ),
                      ),
                      itemCount = 44,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#TrivialThing",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Unbedeutendes Ding",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Chose sans importance",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Cosa senza importanza",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Trivial thing",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "Diese Resource-Klasse beschreibt ein unbedeutendes Ding",
                            language = DE,
                          ),
                        ),
                      ),
                      itemCount = 0,
                    ),
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingText",
                        representationClass = RepresentationClass.TextRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Text",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A text about a thing",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 1,
                    ),
                  ),
                ),
                OntologyAndResourceClasses(
                  ontology = OntologyDto(
                    iri = "http://0.0.0.0:3333/ontology/0001/something/v2",
                    label = "The something ontology",
                    comment = "",
                  ),
                  classesAndCount = List(
                    ResourceClassAndCountDto(
                      resourceClass = ResourceClassDto(
                        iri = "http://0.0.0.0:3333/ontology/0001/something/v2#Something",
                        representationClass = RepresentationClass.WithoutRepresentation,
                        label = List(
                          LanguageStringDto(
                            value = "Etwas",
                            language = DE,
                          ),
                          LanguageStringDto(
                            value = "Quelque chose",
                            language = FR,
                          ),
                          LanguageStringDto(
                            value = "Qualcosa",
                            language = IT,
                          ),
                          LanguageStringDto(
                            value = "Something",
                            language = EN,
                          ),
                        ),
                        comment = List(
                          LanguageStringDto(
                            value = "A something is a thing.",
                            language = EN,
                          ),
                        ),
                      ),
                      itemCount = 0,
                    ),
                  ),
                ),
              ),
            )
          }
      },
      test("should return 404 for non-existent project") {
        val nonExistentProjectIri = "http://rdfh.ch/projects/nonexistent"
        getResourcesPerOntology(nonExistentProjectIri)
          .map(response => assertTrue(response.code == StatusCode.NotFound))
      },
    ),
  )
}
