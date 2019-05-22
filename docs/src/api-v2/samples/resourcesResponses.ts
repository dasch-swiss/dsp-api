import {ResourcesResponse} from "../ResourcesResponse";
import ApiV2WithValueObjects = ResourcesResponse.ApiV2WithValueObjects;
import ApiV2Simple = ResourcesResponse.ApiV2Simple

// To regenerate these samples, start Knora with the knora-test repository. Load the URL in the comment
// above each sample. Use https://json-ld.org/playground/ to compact Knora's response with an empty
// JSON-LD context. Copy and paste the resulting JSON-LD into this file as the value of the sample object.

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a
const Zeitgloecklein: ApiV2WithValueObjects.Resource = {
  "@id": "http://rdfh.ch/0803/c5058f3a",
  "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#citation": [
    {
      "@id": "http://rdfh.ch/0803/c5058f3a/values/184e99ca01",
      "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
      "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Schramm Bd. XXI, S. 27",
      "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      }
    },
    {
      "@id": "http://rdfh.ch/0803/c5058f3a/values/db77ec0302",
      "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
      "http://api.knora.org/ontology/knora-api/v2#valueAsString": "GW 4168",
      "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      }
    },
    {
      "@id": "http://rdfh.ch/0803/c5058f3a/values/9ea13f3d02",
      "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
      "http://api.knora.org/ontology/knora-api/v2#valueAsString": "ISTC ib00512000",
      "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      }
    }
  ],
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#location": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/92faf25701",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Universitäts- und Stadtbibliothek Köln, Sign: AD+S167",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#physical_desc": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/5524469101",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Dimension: 8°",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/cfd09f1e01",
    "@type": "http://api.knora.org/ontology/knora-api/v2#DateValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasCalendar": "JULIAN",
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndEra": "CE",
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndYear": 1492,
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartEra": "CE",
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartYear": 1492,
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "JULIAN:1492 CE",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publisher": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/497df9ab",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Johann Amerbach",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publoc": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/0ca74ce5",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Basel",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/c3295339",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Zeitglöcklein des Lebens und Leidens Christi",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/v2#url": {
    "@id": "http://rdfh.ch/0803/c5058f3a/values/10e00c7acc2704",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/91e19f1e01"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2016-03-02T15:05:10Z"
    }
  },
  "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
    "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
    "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
  },
  "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
    "@id": "http://rdfh.ch/projects/0803"
  },
  "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
    "@id": "http://rdfh.ch/users/91e19f1e01"
  },
  "http://api.knora.org/ontology/knora-api/v2#creationDate": {
    "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
    "@value": "2016-03-02T15:05:10Z"
  },
  "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
  "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
  "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
    "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
    "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
  },
  "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
};

// http://localhost:3333/v2/searchextended/%20%20%20PREFIX%20knora-api%3A%20%3Chttp%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-api%2Fsimple%2Fv2%23%3E%0A%0A%20%20%20CONSTRUCT%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisMainResource%20true%20.%20%23%20marking%20of%20the%20component%20searched%20for%20as%20the%20main%20resource%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20return%20the%20sequence%20number%20in%20the%20response%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20return%20the%20StillImageFile%20in%20the%20response%0A%20%20%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a%3E%20.%0A%20%20%20%7D%20WHERE%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AResource%20.%20%23%20explicit%20type%20annotation%20for%20the%20component%20searched%20for%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AStillImageRepresentation%20.%20%23%20additional%20restriction%20of%20the%20type%20of%20component%2C%20optional%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a%3E%20.%20%23%20component%20relates%20to%20compound%20resource%20via%20this%20property%0A%20%20%20%20%20%20knora-api%3AisPartOf%20knora-api%3AobjectType%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20linking%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20a%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20compound%20resource%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20component%20must%20have%20a%20sequence%20number%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3Aseqnum%20knora-api%3AobjectType%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Fseqnum%20a%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20sequence%20number%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20component%20must%20have%20a%20StillImageFile%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3AhasStillImageFileValue%20knora-api%3AobjectType%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Ffile%20a%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20StillImageFile%2C%20mandatory%0A%20%20%20%7D%0A%20%20%20ORDER%20BY%20ASC(%3Fseqnum)%20%23%20order%20by%20sequence%20number%2C%20ascending%0A%20%20%20OFFSET%200%20%23get%20first%20page%20of%20results
const pagesOfZeitgloecklein: ApiV2WithValueObjects.ResourcesSequence = {
  "@graph": [
    {
      "@id": "http://rdfh.ch/0803/8a0b1e75",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/8a0b1e75/values/ac9ddbf4-62a7-4cdc-b530-16cbbaa265bf",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/8a0b1e75/values/e71e39e902",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 1,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/8a0b1e75/values/7e4ba672",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000002.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2613,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 3505,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a1r, Titelblatt"
    },
    {
      "@id": "http://rdfh.ch/0803/4f11adaf",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/4f11adaf/values/0490c077-a754-460b-9633-c78bfe97c784",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/4f11adaf/values/f3c585ce03",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 2,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/4f11adafd"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/4f11adaf/values/fc964ce5",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000003.jp2/full/1870,2937/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000003.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1870,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2937,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/4f11adafd.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a1v, Titelblatt, Rückseite"
    },
    {
      "@id": "http://rdfh.ch/0803/14173cea",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/14173cea/values/31f0ac77-4966-4eda-b004-d1142a2b84c2",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/14173cea/values/ff6cd2b304",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 3,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/14173cea9"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/14173cea/values/7ae2f25701",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000004.jp2/full/2033,2835/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000004.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2033,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2835,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/14173cea9.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a2r"
    },
    {
      "@id": "http://rdfh.ch/0803/d91ccb2401",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/d91ccb2401/values/e62f9d58-fe66-468e-ba59-13ea81ef0ebb",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/d91ccb2401/values/0b141f9905",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 4,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/d91ccb2401I"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/d91ccb2401/values/f82d99ca01",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000005.jp2/full/1886,2903/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000005.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1886,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2903,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/d91ccb2401I.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a2v"
    },
    {
      "@id": "http://rdfh.ch/0803/9e225a5f01",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/9e225a5f01/values/9c480175-7509-4094-af0d-a1a4f6b5c570",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/9e225a5f01/values/17bb6b7e06",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 5,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/9e225a5f01V"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/9e225a5f01/values/76793f3d02",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000006.jp2/full/2053,2841/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000006.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2053,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2841,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/9e225a5f01V.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a3r"
    },
    {
      "@id": "http://rdfh.ch/0803/6328e99901",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/6328e99901/values/83b134d7-6d67-43e4-bc78-60fc2c7cf8aa",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/6328e99901/values/2362b86307",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 6,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/6328e99901r"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/6328e99901/values/f4c4e5af02",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000007.jp2/full/1907,2926/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000007.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1907,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2926,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/6328e99901r.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a3v"
    },
    {
      "@id": "http://rdfh.ch/0803/282e78d401",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/282e78d401/values/f8498d6d-bc39-4d6e-acda-09a1f35d256e",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/282e78d401/values/2f09054908",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 7,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/282e78d401E"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/282e78d401/values/72108c2203",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000008.jp2/full/2049,2825/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000008.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2049,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2825,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/282e78d401E.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a4r"
    },
    {
      "@id": "http://rdfh.ch/0803/ed33070f02",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/ed33070f02/values/f4246526-d730-4084-b792-0897ffa44d47",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/ed33070f02/values/3bb0512e09",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 8,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/ed33070f02X"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/ed33070f02/values/f05b329503",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000009.jp2/full/1896,2911/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000009.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1896,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/ed33070f02X.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a4v"
    },
    {
      "@id": "http://rdfh.ch/0803/b239964902",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/b239964902/values/7dfa406a-298a-4c7a-bdd8-9e9dddca7d25",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/b239964902/values/47579e130a",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 9,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/b239964902J"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/b239964902/values/6ea7d80704",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000010.jp2/full/2048,2830/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000010.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2048,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2830,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/b239964902J.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a5r"
    },
    {
      "@id": "http://rdfh.ch/0803/773f258402",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/773f258402/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/773f258402/values/53feeaf80a",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 10,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/773f258402e"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/773f258402/values/ecf27e7a04",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000011.jp2/full/1891,2880/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000011.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1891,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2880,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/773f258402e.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a5v"
    },
    {
      "@id": "http://rdfh.ch/0803/3c45b4be02",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/3c45b4be02/values/c0d9fcf9-9084-49ee-b929-5881703c670c",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/3c45b4be02/values/5fa537de0b",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 11,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/3c45b4be023"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/3c45b4be02/values/6a3e25ed04",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000012.jp2/full/2048,2840/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000012.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2048,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2840,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/3c45b4be023.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a6r"
    },
    {
      "@id": "http://rdfh.ch/0803/014b43f902",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/014b43f902/values/5e130352-d154-4edd-a13b-1795055c20ff",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/014b43f902/values/6b4c84c30c",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 12,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/014b43f9025"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/014b43f902/values/e889cb5f05",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000013.jp2/full/1860,2905/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000013.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1860,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2905,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/014b43f9025.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a6v"
    },
    {
      "@id": "http://rdfh.ch/0803/c650d23303",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/c650d23303/values/e6d75b14-35e5-4092-a5b6-7bc06a1f3847",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/c650d23303/values/77f3d0a80d",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 13,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c650d23303f"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/c650d23303/values/66d571d205",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000014.jp2/full/2053,2830/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000014.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2053,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2830,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c650d23303f.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a7r"
    },
    {
      "@id": "http://rdfh.ch/0803/8b56616e03",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/8b56616e03/values/4bbf4e7a-fb6f-48d5-9927-002f85286a44",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/8b56616e03/values/839a1d8e0e",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 14,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/8b56616e03V"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/8b56616e03/values/e420184506",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000015.jp2/full/1859,2911/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000015.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1859,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/8b56616e03V.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a7v"
    },
    {
      "@id": "http://rdfh.ch/0803/505cf0a803",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/505cf0a803/values/bc54a8a9-5ead-433a-b12f-7329aaa0d175",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/505cf0a803/values/8f416a730f",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 15,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/505cf0a803X"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/505cf0a803/values/626cbeb706",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000016.jp2/full/2052,2815/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000016.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2052,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2815,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/505cf0a803X.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a8r"
    },
    {
      "@id": "http://rdfh.ch/0803/15627fe303",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/15627fe303/values/cb451884-484c-4d1e-a546-6bd98ec4a391",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/15627fe303/values/9be8b65810",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 16,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/15627fe303y"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/15627fe303/values/e0b7642a07",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000017.jp2/full/1865,2901/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000017.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1865,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2901,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/15627fe303y.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "a8v"
    },
    {
      "@id": "http://rdfh.ch/0803/da670e1e04",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/da670e1e04/values/fd45b16a-6da5-4753-8e38-b3ee6378f89b",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/da670e1e04/values/a78f033e11",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 17,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/da670e1e04u"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/da670e1e04/values/5e030b9d07",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000018.jp2/full/2037,2820/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000018.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2037,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2820,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/da670e1e04u.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b1r"
    },
    {
      "@id": "http://rdfh.ch/0803/9f6d9d5804",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/9f6d9d5804/values/6b10ee30-d80e-4473-97dd-1b02dfb6f9ba",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/9f6d9d5804/values/b336502312",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 18,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/9f6d9d5804H"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/9f6d9d5804/values/dc4eb10f08",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000019.jp2/full/1871,2911/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000019.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1871,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/9f6d9d5804H.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b1v"
    },
    {
      "@id": "http://rdfh.ch/0803/64732c9304",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/64732c9304/values/78f6208c-38b0-4f3a-ac01-5cdc4fec1d3a",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/64732c9304/values/bfdd9c0813",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 19,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/64732c9304M"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/64732c9304/values/5a9a578208",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000020.jp2/full/2043,2815/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000020.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2043,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2815,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/64732c9304M.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b2r"
    },
    {
      "@id": "http://rdfh.ch/0803/2979bbcd04",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/2979bbcd04/values/f7512609-5839-4ca8-a5f0-c2189eaad2eb",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/2979bbcd04/values/cb84e9ed13",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 20,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/2979bbcd04m"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/2979bbcd04/values/d8e5fdf408",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000021.jp2/full/1865,2906/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000021.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1865,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2906,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/2979bbcd04m.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b2v"
    },
    {
      "@id": "http://rdfh.ch/0803/ee7e4a0805",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/ee7e4a0805/values/8345e64e-6ac5-4411-840e-50db0d0ec143",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/ee7e4a0805/values/d72b36d314",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 21,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/ee7e4a0805h"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/ee7e4a0805/values/5631a46709",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000022.jp2/full/2032,2825/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000022.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2032,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2825,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/ee7e4a0805h.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b3r"
    },
    {
      "@id": "http://rdfh.ch/0803/b384d94205",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/b384d94205/values/3673d715-2fef-47f7-b8dd-faa45d1295af",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/b384d94205/values/e3d282b815",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 22,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/b384d94205e"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/b384d94205/values/d47c4ada09",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000023.jp2/full/1869,2911/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000023.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1869,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/b384d94205e.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b3v"
    },
    {
      "@id": "http://rdfh.ch/0803/788a687d05",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/788a687d05/values/a9cd6b23-ef0a-497f-93a0-3210f1d92b9f",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/788a687d05/values/ef79cf9d16",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 23,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/788a687d05M"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/788a687d05/values/52c8f04c0a",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000024.jp2/full/2040,2816/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000024.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2040,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2816,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/788a687d05M.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b4r"
    },
    {
      "@id": "http://rdfh.ch/0803/3d90f7b705",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/3d90f7b705/values/d3eb0cba-a0ae-4cc5-958d-37f7a0d549ec",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/3d90f7b705/values/fb201c8317",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 24,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/3d90f7b705A"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/3d90f7b705/values/d01397bf0a",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000025.jp2/full/1866,2893/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000025.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1866,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2893,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/3d90f7b705A.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b4v"
    },
    {
      "@id": "http://rdfh.ch/0803/029686f205",
      "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
        "@id": "http://rdfh.ch/0803/029686f205/values/4f5d810d-12a7-4d28-b856-af5e7d04f1d2",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
          "@id": "http://rdfh.ch/0803/c5058f3a",
          "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
          "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
            "@id": "http://rdfh.ch/projects/0803"
          },
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
            "@id": "http://rdfh.ch/users/91e19f1e01"
          },
          "http://api.knora.org/ontology/knora-api/v2#creationDate": {
            "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
            "@value": "2016-03-02T15:05:10Z"
          },
          "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
          "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
            "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
            "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
          },
          "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
        "@id": "http://rdfh.ch/0803/029686f205/values/07c8686818",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 25,
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2016-03-02T15:05:10Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/029686f205y"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0803"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/91e19f1e01"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-03-02T15:05:10Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": {
        "@id": "http://rdfh.ch/0803/029686f205/values/4e5f3d320b",
        "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
        "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
          "@id": "http://rdfh.ch/users/91e19f1e01"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803/incunabula_0000000026.jp2/full/2048,2804/0/default.jpg"
        },
        "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000026.jp2",
        "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2048,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2804,
        "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": {
          "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
          "@value": "http://0.0.0.0:1024/0803"
        },
        "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
        "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
          "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
          "@value": "2012-11-21T16:49:36Z"
        }
      },
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/029686f205y.20160302T150510Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "b5r"
    }
  ]
};

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2FH6gBWUuJSuuO-CilHV8kQw
const Thing: ApiV2WithValueObjects.Resource = {
  "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw",
  "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/IN4R19yYR0ygi3K2VEHpUQ",
    "@type": "http://api.knora.org/ontology/knora-api/v2#BooleanValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#booleanValueAsBoolean": true,
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/TAziKNP8QxuyhC4Qf9-b6w",
    "@type": "http://api.knora.org/ontology/knora-api/v2#ColorValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#colorValueAsColor": "#ff3333",
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/-rG4F5FTTu2iB5mTBPVn5Q",
    "@type": "http://api.knora.org/ontology/knora-api/v2#DateValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasCalendar": "GREGORIAN",
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndDay": 13,
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndEra": "CE",
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndMonth": 5,
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndYear": 2018,
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartDay": 13,
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartEra": "CE",
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartMonth": 5,
    "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartYear": 2018,
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "GREGORIAN:2018-05-13 CE",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg",
    "@type": "http://api.knora.org/ontology/knora-api/v2#DecimalValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#decimalValueAsDecimal": {
      "@type": "http://www.w3.org/2001/XMLSchema#decimal",
      "@value": "1.5"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg",
    "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 1,
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/RbDKPKHWTC-0lkRKae-E6A",
    "@type": "http://api.knora.org/ontology/knora-api/v2#IntervalValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#intervalValueHasEnd": {
      "@type": "http://www.w3.org/2001/XMLSchema#decimal",
      "@value": "216000"
    },
    "http://api.knora.org/ontology/knora-api/v2#intervalValueHasStart": {
      "@type": "http://www.w3.org/2001/XMLSchema#decimal",
      "@value": "0"
    },
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/XAhEeE3kSVqM4JPGdLt4Ew",
    "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
      "@id": "http://rdfh.ch/lists/0001/treeList01"
    },
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherListItem": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/j8VQjbD0RsyxpyuvfFJCDA",
    "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
      "@id": "http://rdfh.ch/lists/0001/otherTreeList01"
    },
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uvRVxzL1RD-t9VIQ1TpfUw",
    "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
      "@id": "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
      "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
      "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
        "@id": "http://rdfh.ch/projects/0001"
      },
      "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
        "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
      },
      "http://api.knora.org/ontology/knora-api/v2#creationDate": {
        "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
        "@value": "2016-10-17T17:16:04.916Z"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
      "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "V",
      "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY.20161017T171604916Z"
      },
      "http://www.w3.org/2000/01/rdf-schema#label": "Sierra"
    },
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/rvB4eQ5MTF-Qxq0YgkwaDg",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p>test with <strong>markup</strong></p></text>",
    "http://api.knora.org/ontology/knora-api/v2#textValueHasMapping": {
      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
    },
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ",
    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "test",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri": {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uBAmWuRhR-eo1u1eP7qqNg",
    "@type": "http://api.knora.org/ontology/knora-api/v2#UriValue",
    "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
      "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "http://api.knora.org/ontology/knora-api/v2#uriValueAsUri": {
      "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
      "@value": "http://www.google.ch"
    },
    "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
    "http://api.knora.org/ontology/knora-api/v2#valueCreationDate": {
      "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
      "@value": "2018-05-28T15:52:03.897Z"
    }
  },
  "http://api.knora.org/ontology/knora-api/v2#arkUrl": {
    "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
    "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/H6gBWUuJSuuO=CilHV8kQwk"
  },
  "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
    "@id": "http://rdfh.ch/projects/0001"
  },
  "http://api.knora.org/ontology/knora-api/v2#attachedToUser": {
    "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
  },
  "http://api.knora.org/ontology/knora-api/v2#creationDate": {
    "@type": "http://www.w3.org/2001/XMLSchema#dateTimeStamp",
    "@value": "2018-05-28T15:52:03.897Z"
  },
  "http://api.knora.org/ontology/knora-api/v2#hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
  "http://api.knora.org/ontology/knora-api/v2#userHasPermission": "RV",
  "http://api.knora.org/ontology/knora-api/v2#versionArkUrl": {
    "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
    "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/H6gBWUuJSuuO=CilHV8kQwk.20180528T155203897Z"
  },
  "http://www.w3.org/2000/01/rdf-schema#label": "testding"
};

// http://0.0.0.0:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2F8a0b1e75?schema=simple
const PageOfZeitgloecklein: ApiV2Simple.Resource = {
  "@id": "http://rdfh.ch/0803/8a0b1e75",
  "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
  "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Titel: \"Das andechtig zitglo(e)gglyn | des lebens vnd lide(n)s christi nach | den xxiiij stunden vßgeteilt.\"\nHolzschnitt: Schlaguhr mit Zifferblatt für 24 Stunden, auf deren oberem Rand zu beiden Seiten einer Glocke die Verkündigungsszene mit Maria (links) und dem Engel (rechts) zu sehen ist.\nBordüre: Ranken mit Fabelwesen, Holzschnitt.\nKolorierung: Rot, Blau, Grün, Gelb, Braun.\nBeschriftung oben Mitte (Graphitstift) \"B 1\".",
  "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#origname": "ad+s167_druck1=0001.tif",
  "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page_comment": "Schramm, Bd. 21, Abb. 601.",
  "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum": "a1r, Titelblatt",
  "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOf": {
    "@id": "http://rdfh.ch/0803/c5058f3a"
  },
  "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum": 1,
  "http://api.knora.org/ontology/knora-api/simple/v2#arkUrl": {
    "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
    "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i"
  },
  "http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile": {
    "@type": "http://api.knora.org/ontology/knora-api/simple/v2#File",
    "@value": "http://0.0.0.0:1024/0803/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg"
  },
  "http://api.knora.org/ontology/knora-api/simple/v2#versionArkUrl": {
    "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
    "@value": "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i.20160302T150510Z"
  },
  "http://www.w3.org/2000/01/rdf-schema#label": "a1r, Titelblatt"
};
