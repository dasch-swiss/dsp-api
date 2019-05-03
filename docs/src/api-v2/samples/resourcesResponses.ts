import {ResourcesResponse} from "../ResourcesResponse";
import ApiV2WithValueObjects = ResourcesResponse.ApiV2WithValueObjects;
import ApiV2Simple = ResourcesResponse.ApiV2Simple

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a
const Zeitgloecklein: ApiV2WithValueObjects.Resource = {
  "@id" : "http://rdfh.ch/0803/c5058f3a",
  "@type" : "incunabula:book",
  "incunabula:citation" : [ {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/184e99ca01",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Schramm Bd. XXI, S. 27",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  }, {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/db77ec0302",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "GW 4168",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  }, {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/9ea13f3d02",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "ISTC ib00512000",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  } ],
  "incunabula:location" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/92faf25701",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Universitäts- und Stadtbibliothek Köln, Sign: AD+S167",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "incunabula:physical_desc" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/5524469101",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Dimension: 8°",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "incunabula:pubdate" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/cfd09f1e01",
    "@type" : "knora-api:DateValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:dateValueHasCalendar" : "JULIAN",
    "knora-api:dateValueHasEndEra" : "CE",
    "knora-api:dateValueHasEndYear" : 1492,
    "knora-api:dateValueHasStartEra" : "CE",
    "knora-api:dateValueHasStartYear" : 1492,
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "JULIAN:1492 CE",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "incunabula:publisher" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/497df9ab",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Johann Amerbach",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "incunabula:publoc" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/0ca74ce5",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Basel",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "incunabula:title" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/c3295339",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Zeitglöcklein des Lebens und Leidens Christi",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "incunabula:url" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a/values/10e00c7acc2704",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    }
  },
  "knora-api:arkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0803"
  },
  "knora-api:attachedToUser" : {
    "@id" : "http://rdfh.ch/users/91e19f1e01"
  },
  "knora-api:creationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2016-03-02T15:05:10Z"
  },
  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
  "knora-api:userHasPermission" : "RV",
  "knora-api:versionArkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
  },
  "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
};

// http://localhost:3333/v2/searchextended/%20%20%20PREFIX%20knora-api%3A%20%3Chttp%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-api%2Fsimple%2Fv2%23%3E%0A%0A%20%20%20CONSTRUCT%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisMainResource%20true%20.%20%23%20marking%20of%20the%20component%20searched%20for%20as%20the%20main%20resource%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20return%20the%20sequence%20number%20in%20the%20response%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20return%20the%20StillImageFile%20in%20the%20response%0A%20%20%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a%3E%20.%0A%20%20%20%7D%20WHERE%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AResource%20.%20%23%20explicit%20type%20annotation%20for%20the%20component%20searched%20for%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AStillImageRepresentation%20.%20%23%20additional%20restriction%20of%20the%20type%20of%20component%2C%20optional%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a%3E%20.%20%23%20component%20relates%20to%20compound%20resource%20via%20this%20property%0A%20%20%20%20%20%20knora-api%3AisPartOf%20knora-api%3AobjectType%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20linking%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20a%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20compound%20resource%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20component%20must%20have%20a%20sequence%20number%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3Aseqnum%20knora-api%3AobjectType%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Fseqnum%20a%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20sequence%20number%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20component%20must%20have%20a%20StillImageFile%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3AhasStillImageFileValue%20knora-api%3AobjectType%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Ffile%20a%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20StillImageFile%2C%20mandatory%0A%20%20%20%7D%0A%20%20%20ORDER%20BY%20ASC(%3Fseqnum)%20%23%20order%20by%20sequence%20number%2C%20ascending%0A%20%20%20OFFSET%200%20%23get%20first%20page%20of%20results
const pagesOfZeitgloecklein: ApiV2WithValueObjects.ResourcesSequence = {
  "@graph" : [ {
    "@id" : "http://rdfh.ch/0803/8a0b1e75",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/8a0b1e75/values/ac9ddbf4-62a7-4cdc-b530-16cbbaa265bf",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/8a0b1e75/values/e71e39e902",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 1,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/8a0b1e75/values/7e4ba672",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000002.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2613,
      "knora-api:stillImageFileValueHasDimY" : 3505,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i.20160302T150510Z"
    },
    "rdfs:label" : "a1r, Titelblatt"
  }, {
    "@id" : "http://rdfh.ch/0803/4f11adaf",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/4f11adaf/values/0490c077-a754-460b-9633-c78bfe97c784",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/4f11adaf/values/f3c585ce03",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 2,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/4f11adafd"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/4f11adaf/values/fc964ce5",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000003.jp2/full/1870,2937/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000003.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1870,
      "knora-api:stillImageFileValueHasDimY" : 2937,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/4f11adafd.20160302T150510Z"
    },
    "rdfs:label" : "a1v, Titelblatt, Rückseite"
  }, {
    "@id" : "http://rdfh.ch/0803/14173cea",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/14173cea/values/31f0ac77-4966-4eda-b004-d1142a2b84c2",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/14173cea/values/ff6cd2b304",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 3,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/14173cea9"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/14173cea/values/7ae2f25701",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000004.jp2/full/2033,2835/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000004.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2033,
      "knora-api:stillImageFileValueHasDimY" : 2835,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/14173cea9.20160302T150510Z"
    },
    "rdfs:label" : "a2r"
  }, {
    "@id" : "http://rdfh.ch/0803/d91ccb2401",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/d91ccb2401/values/e62f9d58-fe66-468e-ba59-13ea81ef0ebb",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/d91ccb2401/values/0b141f9905",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 4,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/d91ccb2401I"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/d91ccb2401/values/f82d99ca01",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000005.jp2/full/1886,2903/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000005.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1886,
      "knora-api:stillImageFileValueHasDimY" : 2903,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/d91ccb2401I.20160302T150510Z"
    },
    "rdfs:label" : "a2v"
  }, {
    "@id" : "http://rdfh.ch/0803/9e225a5f01",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/9e225a5f01/values/9c480175-7509-4094-af0d-a1a4f6b5c570",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/9e225a5f01/values/17bb6b7e06",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 5,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/9e225a5f01V"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/9e225a5f01/values/76793f3d02",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000006.jp2/full/2053,2841/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000006.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2053,
      "knora-api:stillImageFileValueHasDimY" : 2841,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/9e225a5f01V.20160302T150510Z"
    },
    "rdfs:label" : "a3r"
  }, {
    "@id" : "http://rdfh.ch/0803/6328e99901",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/6328e99901/values/83b134d7-6d67-43e4-bc78-60fc2c7cf8aa",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/6328e99901/values/2362b86307",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 6,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/6328e99901r"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/6328e99901/values/f4c4e5af02",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000007.jp2/full/1907,2926/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000007.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1907,
      "knora-api:stillImageFileValueHasDimY" : 2926,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/6328e99901r.20160302T150510Z"
    },
    "rdfs:label" : "a3v"
  }, {
    "@id" : "http://rdfh.ch/0803/282e78d401",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/282e78d401/values/f8498d6d-bc39-4d6e-acda-09a1f35d256e",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/282e78d401/values/2f09054908",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 7,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/282e78d401E"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/282e78d401/values/72108c2203",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000008.jp2/full/2049,2825/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000008.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2049,
      "knora-api:stillImageFileValueHasDimY" : 2825,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/282e78d401E.20160302T150510Z"
    },
    "rdfs:label" : "a4r"
  }, {
    "@id" : "http://rdfh.ch/0803/ed33070f02",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/ed33070f02/values/f4246526-d730-4084-b792-0897ffa44d47",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/ed33070f02/values/3bb0512e09",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 8,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/ed33070f02X"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/ed33070f02/values/f05b329503",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000009.jp2/full/1896,2911/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000009.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1896,
      "knora-api:stillImageFileValueHasDimY" : 2911,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/ed33070f02X.20160302T150510Z"
    },
    "rdfs:label" : "a4v"
  }, {
    "@id" : "http://rdfh.ch/0803/b239964902",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/b239964902/values/7dfa406a-298a-4c7a-bdd8-9e9dddca7d25",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/b239964902/values/47579e130a",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 9,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/b239964902J"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/b239964902/values/6ea7d80704",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000010.jp2/full/2048,2830/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000010.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2048,
      "knora-api:stillImageFileValueHasDimY" : 2830,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/b239964902J.20160302T150510Z"
    },
    "rdfs:label" : "a5r"
  }, {
    "@id" : "http://rdfh.ch/0803/773f258402",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/773f258402/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/773f258402/values/53feeaf80a",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 10,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/773f258402e"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/773f258402/values/ecf27e7a04",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000011.jp2/full/1891,2880/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000011.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1891,
      "knora-api:stillImageFileValueHasDimY" : 2880,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/773f258402e.20160302T150510Z"
    },
    "rdfs:label" : "a5v"
  }, {
    "@id" : "http://rdfh.ch/0803/3c45b4be02",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/3c45b4be02/values/c0d9fcf9-9084-49ee-b929-5881703c670c",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/3c45b4be02/values/5fa537de0b",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 11,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/3c45b4be023"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/3c45b4be02/values/6a3e25ed04",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000012.jp2/full/2048,2840/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000012.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2048,
      "knora-api:stillImageFileValueHasDimY" : 2840,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/3c45b4be023.20160302T150510Z"
    },
    "rdfs:label" : "a6r"
  }, {
    "@id" : "http://rdfh.ch/0803/014b43f902",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/014b43f902/values/5e130352-d154-4edd-a13b-1795055c20ff",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/014b43f902/values/6b4c84c30c",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 12,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/014b43f9025"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/014b43f902/values/e889cb5f05",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000013.jp2/full/1860,2905/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000013.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1860,
      "knora-api:stillImageFileValueHasDimY" : 2905,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/014b43f9025.20160302T150510Z"
    },
    "rdfs:label" : "a6v"
  }, {
    "@id" : "http://rdfh.ch/0803/c650d23303",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/c650d23303/values/e6d75b14-35e5-4092-a5b6-7bc06a1f3847",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/c650d23303/values/77f3d0a80d",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 13,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c650d23303f"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/c650d23303/values/66d571d205",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000014.jp2/full/2053,2830/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000014.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2053,
      "knora-api:stillImageFileValueHasDimY" : 2830,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c650d23303f.20160302T150510Z"
    },
    "rdfs:label" : "a7r"
  }, {
    "@id" : "http://rdfh.ch/0803/8b56616e03",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/8b56616e03/values/4bbf4e7a-fb6f-48d5-9927-002f85286a44",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/8b56616e03/values/839a1d8e0e",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 14,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/8b56616e03V"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/8b56616e03/values/e420184506",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000015.jp2/full/1859,2911/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000015.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1859,
      "knora-api:stillImageFileValueHasDimY" : 2911,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/8b56616e03V.20160302T150510Z"
    },
    "rdfs:label" : "a7v"
  }, {
    "@id" : "http://rdfh.ch/0803/505cf0a803",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/505cf0a803/values/bc54a8a9-5ead-433a-b12f-7329aaa0d175",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/505cf0a803/values/8f416a730f",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 15,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/505cf0a803X"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/505cf0a803/values/626cbeb706",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000016.jp2/full/2052,2815/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000016.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2052,
      "knora-api:stillImageFileValueHasDimY" : 2815,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/505cf0a803X.20160302T150510Z"
    },
    "rdfs:label" : "a8r"
  }, {
    "@id" : "http://rdfh.ch/0803/15627fe303",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/15627fe303/values/cb451884-484c-4d1e-a546-6bd98ec4a391",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/15627fe303/values/9be8b65810",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 16,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/15627fe303y"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/15627fe303/values/e0b7642a07",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000017.jp2/full/1865,2901/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000017.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1865,
      "knora-api:stillImageFileValueHasDimY" : 2901,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/15627fe303y.20160302T150510Z"
    },
    "rdfs:label" : "a8v"
  }, {
    "@id" : "http://rdfh.ch/0803/da670e1e04",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/da670e1e04/values/fd45b16a-6da5-4753-8e38-b3ee6378f89b",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/da670e1e04/values/a78f033e11",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 17,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/da670e1e04u"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/da670e1e04/values/5e030b9d07",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000018.jp2/full/2037,2820/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000018.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2037,
      "knora-api:stillImageFileValueHasDimY" : 2820,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/da670e1e04u.20160302T150510Z"
    },
    "rdfs:label" : "b1r"
  }, {
    "@id" : "http://rdfh.ch/0803/9f6d9d5804",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/9f6d9d5804/values/6b10ee30-d80e-4473-97dd-1b02dfb6f9ba",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/9f6d9d5804/values/b336502312",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 18,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/9f6d9d5804H"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/9f6d9d5804/values/dc4eb10f08",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000019.jp2/full/1871,2911/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000019.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1871,
      "knora-api:stillImageFileValueHasDimY" : 2911,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/9f6d9d5804H.20160302T150510Z"
    },
    "rdfs:label" : "b1v"
  }, {
    "@id" : "http://rdfh.ch/0803/64732c9304",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/64732c9304/values/78f6208c-38b0-4f3a-ac01-5cdc4fec1d3a",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/64732c9304/values/bfdd9c0813",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 19,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/64732c9304M"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/64732c9304/values/5a9a578208",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000020.jp2/full/2043,2815/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000020.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2043,
      "knora-api:stillImageFileValueHasDimY" : 2815,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/64732c9304M.20160302T150510Z"
    },
    "rdfs:label" : "b2r"
  }, {
    "@id" : "http://rdfh.ch/0803/2979bbcd04",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/2979bbcd04/values/f7512609-5839-4ca8-a5f0-c2189eaad2eb",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/2979bbcd04/values/cb84e9ed13",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 20,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/2979bbcd04m"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/2979bbcd04/values/d8e5fdf408",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000021.jp2/full/1865,2906/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000021.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1865,
      "knora-api:stillImageFileValueHasDimY" : 2906,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/2979bbcd04m.20160302T150510Z"
    },
    "rdfs:label" : "b2v"
  }, {
    "@id" : "http://rdfh.ch/0803/ee7e4a0805",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/ee7e4a0805/values/8345e64e-6ac5-4411-840e-50db0d0ec143",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/ee7e4a0805/values/d72b36d314",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 21,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/ee7e4a0805h"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/ee7e4a0805/values/5631a46709",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000022.jp2/full/2032,2825/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000022.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2032,
      "knora-api:stillImageFileValueHasDimY" : 2825,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/ee7e4a0805h.20160302T150510Z"
    },
    "rdfs:label" : "b3r"
  }, {
    "@id" : "http://rdfh.ch/0803/b384d94205",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/b384d94205/values/3673d715-2fef-47f7-b8dd-faa45d1295af",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/b384d94205/values/e3d282b815",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 22,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/b384d94205e"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/b384d94205/values/d47c4ada09",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000023.jp2/full/1869,2911/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000023.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1869,
      "knora-api:stillImageFileValueHasDimY" : 2911,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/b384d94205e.20160302T150510Z"
    },
    "rdfs:label" : "b3v"
  }, {
    "@id" : "http://rdfh.ch/0803/788a687d05",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/788a687d05/values/a9cd6b23-ef0a-497f-93a0-3210f1d92b9f",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/788a687d05/values/ef79cf9d16",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 23,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/788a687d05M"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/788a687d05/values/52c8f04c0a",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000024.jp2/full/2040,2816/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000024.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2040,
      "knora-api:stillImageFileValueHasDimY" : 2816,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/788a687d05M.20160302T150510Z"
    },
    "rdfs:label" : "b4r"
  }, {
    "@id" : "http://rdfh.ch/0803/3d90f7b705",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/3d90f7b705/values/d3eb0cba-a0ae-4cc5-958d-37f7a0d549ec",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/3d90f7b705/values/fb201c8317",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 24,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/3d90f7b705A"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/3d90f7b705/values/d01397bf0a",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000025.jp2/full/1866,2893/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000025.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 1866,
      "knora-api:stillImageFileValueHasDimY" : 2893,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/3d90f7b705A.20160302T150510Z"
    },
    "rdfs:label" : "b4v"
  }, {
    "@id" : "http://rdfh.ch/0803/029686f205",
    "@type" : "incunabula:page",
    "incunabula:partOfValue" : {
      "@id" : "http://rdfh.ch/0803/029686f205/values/4f5d810d-12a7-4d28-b856-af5e7d04f1d2",
      "@type" : "knora-api:LinkValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:linkValueHasTarget" : {
        "@id" : "http://rdfh.ch/0803/c5058f3a",
        "@type" : "incunabula:book",
        "knora-api:arkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5"
        },
        "knora-api:attachedToProject" : {
          "@id" : "http://rdfh.ch/projects/0803"
        },
        "knora-api:attachedToUser" : {
          "@id" : "http://rdfh.ch/users/91e19f1e01"
        },
        "knora-api:creationDate" : {
          "@type" : "xsd:dateTimeStamp",
          "@value" : "2016-03-02T15:05:10Z"
        },
        "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        "knora-api:userHasPermission" : "RV",
        "knora-api:versionArkUrl" : {
          "@type" : "xsd:anyURI",
          "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/c5058f3a5.20160302T150510Z"
        },
        "rdfs:label" : "Zeitglöcklein des Lebens und Leidens Christi"
      },
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "incunabula:seqnum" : {
      "@id" : "http://rdfh.ch/0803/029686f205/values/07c8686818",
      "@type" : "knora-api:IntValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
      "knora-api:intValueAsInt" : 25,
      "knora-api:userHasPermission" : "V",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-03-02T15:05:10Z"
      }
    },
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/029686f205y"
    },
    "knora-api:attachedToProject" : {
      "@id" : "http://rdfh.ch/projects/0803"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:creationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:10Z"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:hasStillImageFileValue" : {
      "@id" : "http://rdfh.ch/0803/029686f205/values/4e5f3d320b",
      "@type" : "knora-api:StillImageFileValue",
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/91e19f1e01"
      },
      "knora-api:fileValueAsUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000026.jp2/full/2048,2804/0/default.jpg"
      },
      "knora-api:fileValueHasFilename" : "incunabula_0000000026.jp2",
      "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
      "knora-api:stillImageFileValueHasDimX" : 2048,
      "knora-api:stillImageFileValueHasDimY" : 2804,
      "knora-api:stillImageFileValueHasIIIFBaseUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:1024/0803"
      },
      "knora-api:userHasPermission" : "RV",
      "knora-api:valueCreationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2012-11-21T16:49:36Z"
      }
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/029686f205y.20160302T150510Z"
    },
    "rdfs:label" : "b5r"
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
};

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2FH6gBWUuJSuuO-CilHV8kQw
const Thing: ApiV2WithValueObjects.Resource = {
  "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw",
  "@type" : "anything:Thing",
  "anything:hasBoolean" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/IN4R19yYR0ygi3K2VEHpUQ",
    "@type" : "knora-api:BooleanValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:booleanValueAsBoolean" : true,
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasColor" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/TAziKNP8QxuyhC4Qf9-b6w",
    "@type" : "knora-api:ColorValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:colorValueAsColor" : "#ff3333",
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasDate" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/-rG4F5FTTu2iB5mTBPVn5Q",
    "@type" : "knora-api:DateValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:dateValueHasCalendar" : "GREGORIAN",
    "knora-api:dateValueHasEndDay" : 13,
    "knora-api:dateValueHasEndEra" : "CE",
    "knora-api:dateValueHasEndMonth" : 5,
    "knora-api:dateValueHasEndYear" : 2018,
    "knora-api:dateValueHasStartDay" : 13,
    "knora-api:dateValueHasStartEra" : "CE",
    "knora-api:dateValueHasStartMonth" : 5,
    "knora-api:dateValueHasStartYear" : 2018,
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueAsString" : "GREGORIAN:2018-05-13 CE",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasDecimal" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg",
    "@type" : "knora-api:DecimalValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:decimalValueAsDecimal" : {
      "@type" : "xsd:decimal",
      "@value" : "1.5"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasInteger" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg",
    "@type" : "knora-api:IntValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:intValueAsInt" : 1,
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasInterval" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/RbDKPKHWTC-0lkRKae-E6A",
    "@type" : "knora-api:IntervalValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:intervalValueHasEnd" : {
      "@type" : "xsd:decimal",
      "@value" : "216000"
    },
    "knora-api:intervalValueHasStart" : {
      "@type" : "xsd:decimal",
      "@value" : "0"
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasListItem" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/XAhEeE3kSVqM4JPGdLt4Ew",
    "@type" : "knora-api:ListValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:listValueAsListNode" : {
      "@id" : "http://rdfh.ch/lists/0001/treeList01"
    },
    "knora-api:listValueAsListNodeLabel" : "Tree list node 01",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasOtherListItem" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/j8VQjbD0RsyxpyuvfFJCDA",
    "@type" : "knora-api:ListValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:listValueAsListNode" : {
      "@id" : "http://rdfh.ch/lists/0001/otherTreeList01"
    },
    "knora-api:listValueAsListNodeLabel" : "Other Tree list node 01",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasOtherThingValue" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uvRVxzL1RD-t9VIQ1TpfUw",
    "@type" : "knora-api:LinkValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:linkValueHasTarget" : {
      "@id" : "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
      "@type" : "anything:Thing",
      "knora-api:arkUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY"
      },
      "knora-api:attachedToProject" : {
        "@id" : "http://rdfh.ch/projects/0001"
      },
      "knora-api:attachedToUser" : {
        "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
      },
      "knora-api:creationDate" : {
        "@type" : "xsd:dateTimeStamp",
        "@value" : "2016-10-17T17:16:04.916Z"
      },
      "knora-api:hasPermissions" : "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
      "knora-api:userHasPermission" : "V",
      "knora-api:versionArkUrl" : {
        "@type" : "xsd:anyURI",
        "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY.20161017T171604916Z"
      },
      "rdfs:label" : "Sierra"
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasRichtext" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/rvB4eQ5MTF-Qxq0YgkwaDg",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p>test with <strong>markup</strong></p></text>",
    "knora-api:textValueHasMapping" : {
      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasText" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ",
    "@type" : "knora-api:TextValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueAsString" : "test",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "anything:hasUri" : {
    "@id" : "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uBAmWuRhR-eo1u1eP7qqNg",
    "@type" : "knora-api:UriValue",
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    "knora-api:uriValueAsUri" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://www.google.ch"
    },
    "knora-api:userHasPermission" : "RV",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2018-05-28T15:52:03.897Z"
    }
  },
  "knora-api:arkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/H6gBWUuJSuuO=CilHV8kQwk"
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0001"
  },
  "knora-api:attachedToUser" : {
    "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
  },
  "knora-api:creationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2018-05-28T15:52:03.897Z"
  },
  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
  "knora-api:userHasPermission" : "RV",
  "knora-api:versionArkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0001/H6gBWUuJSuuO=CilHV8kQwk.20180528T155203897Z"
  },
  "rdfs:label" : "testding",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
};

// http://0.0.0.0:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2F8a0b1e75?schema=simple
const PageOfZeitgloecklein: ApiV2Simple.Resource = {
  "@id" : "http://rdfh.ch/0803/8a0b1e75",
  "@type" : "incunabula:page",
  "incunabula:description" : "Titel: \"Das andechtig zitglo(e)gglyn | des lebens vnd lide(n)s christi nach | den xxiiij stunden vßgeteilt.\"\nHolzschnitt: Schlaguhr mit Zifferblatt für 24 Stunden, auf deren oberem Rand zu beiden Seiten einer Glocke die Verkündigungsszene mit Maria (links) und dem Engel (rechts) zu sehen ist.\nBordüre: Ranken mit Fabelwesen, Holzschnitt.\nKolorierung: Rot, Blau, Grün, Gelb, Braun.\nBeschriftung oben Mitte (Graphitstift) \"B 1\".",
  "incunabula:origname" : "ad+s167_druck1=0001.tif",
  "incunabula:page_comment" : "Schramm, Bd. 21, Abb. 601.",
  "incunabula:pagenum" : "a1r, Titelblatt",
  "incunabula:partOf" : {
    "@id" : "http://rdfh.ch/0803/c5058f3a"
  },
  "incunabula:seqnum" : 1,
  "knora-api:arkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i"
  },
  "knora-api:hasStillImageFile" : {
    "@type" : "knora-api:File",
    "@value" : "http://0.0.0.0:1024/0803/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg"
  },
  "knora-api:versionArkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://0.0.0.0:3336/ark:/72163/1/0803/8a0b1e75i.20160302T150510Z"
  },
  "rdfs:label" : "a1r, Titelblatt",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
};
