import {MappingFormats} from "../MappingFormats";
import AddMappingRequest = MappingFormats.AddMappingRequest;
import AddMappingResponse = MappingFormats.AddMappingResponse;

// see test in StandoffRouteV2R2RSpec.scala
const mappingCreation: AddMappingRequest = {
    "http://api.knora.org/ontology/knora-api/v2#mappingHasName": "LetterMapping",
    "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
      "@id": "http://rdfh.ch/projects/0001"
    },
    "http://www.w3.org/2000/01/rdf-schema#label": "letter mapping"
};

// see webapi/src/test/resources/test-data/standoffR2RV2/mappingCreationResponse.jsonld
const mappingCreationResponse: AddMappingResponse = {
    "@id": "http://rdfh.ch/projects/0001/mappings/LetterMapping",
    "@type": "http://api.knora.org/ontology/knora-api/v2#XMLToStandoffMapping",
    "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
      "@id": "http://rdfh.ch/projects/0001"
    },
    "http://www.w3.org/2000/01/rdf-schema#label": "letter mapping"
};
