import {ListResponse} from "../ListResponse";
import List = ListResponse.List;
import ListNode = ListResponse.ListNode;

// http://0.0.0.0:3333/v2/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList
const treeList: List = {
  "@id": "http://rdfh.ch/lists/0001/treeList",
  "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
  "http://api.knora.org/ontology/knora-api/v2#attachedToProject": {
    "@id": "http://rdfh.ch/projects/0001"
  },
  "http://api.knora.org/ontology/knora-api/v2#hasSubListNode": [
    {
      "@id": "http://rdfh.ch/lists/0001/treeList01",
      "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
      "http://api.knora.org/ontology/knora-api/v2#hasRootNode": {
        "@id": "http://rdfh.ch/lists/0001/treeList"
      },
      "http://api.knora.org/ontology/knora-api/v2#listNodePosition": 0,
      "http://www.w3.org/2000/01/rdf-schema#label": "Tree list node 01"
    },
    {
      "@id": "http://rdfh.ch/lists/0001/treeList02",
      "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
      "http://api.knora.org/ontology/knora-api/v2#hasRootNode": {
        "@id": "http://rdfh.ch/lists/0001/treeList"
      },
      "http://api.knora.org/ontology/knora-api/v2#listNodePosition": 1,
      "http://www.w3.org/2000/01/rdf-schema#label": "Tree list node 02"
    },
    {
      "@id": "http://rdfh.ch/lists/0001/treeList03",
      "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
      "http://api.knora.org/ontology/knora-api/v2#hasRootNode": {
        "@id": "http://rdfh.ch/lists/0001/treeList"
      },
      "http://api.knora.org/ontology/knora-api/v2#hasSubListNode": [
        {
          "@id": "http://rdfh.ch/lists/0001/treeList10",
          "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
          "http://api.knora.org/ontology/knora-api/v2#hasRootNode": {
            "@id": "http://rdfh.ch/lists/0001/treeList"
          },
          "http://api.knora.org/ontology/knora-api/v2#listNodePosition": 0,
          "http://www.w3.org/2000/01/rdf-schema#label": "Tree list node 10"
        },
        {
          "@id": "http://rdfh.ch/lists/0001/treeList11",
          "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
          "http://api.knora.org/ontology/knora-api/v2#hasRootNode": {
            "@id": "http://rdfh.ch/lists/0001/treeList"
          },
          "http://api.knora.org/ontology/knora-api/v2#listNodePosition": 1,
          "http://www.w3.org/2000/01/rdf-schema#label": "Tree list node 11"
        }
      ],
      "http://api.knora.org/ontology/knora-api/v2#listNodePosition": 2,
      "http://www.w3.org/2000/01/rdf-schema#label": "Tree list node 03"
    }
  ],
  "http://api.knora.org/ontology/knora-api/v2#isRootNode": true,
  "http://www.w3.org/2000/01/rdf-schema#comment": "Anything Tree List",
  "http://www.w3.org/2000/01/rdf-schema#label": "Tree list root"
};

// http://0.0.0.0:3333/v2/node/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList02
const listNode: ListNode = {
  "@id": "http://rdfh.ch/lists/0001/treeList02",
  "@type": "http://api.knora.org/ontology/knora-api/v2#ListNode",
  "http://api.knora.org/ontology/knora-api/v2#hasRootNode": {
    "@id": "http://rdfh.ch/lists/0001/treeList"
  },
  "http://api.knora.org/ontology/knora-api/v2#listNodePosition": 1,
  "http://www.w3.org/2000/01/rdf-schema#label": "Tree list node 02"
};
