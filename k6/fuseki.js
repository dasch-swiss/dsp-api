import http from "k6/http";
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import { check, sleep } from "k6";
import encoding from "k6/encoding";

export const options = {
  //  stages: [{ duration: "30s", target: 1 }],
  thresholds: {
    http_req_duration: ["p(90)<400", "p(95)<370"],
  },
};

const fusekiUrl = __ENV.FUSEKI_URL;
const credentials = __ENV.FUSEKI_USER + ":" + __ENV.FUSEKI_PASSWORD;
const encodedCredentials = encoding.b64encode(credentials);
const query = `
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

CONSTRUCT {
  ?projectIri ?projP ?projO .
  ?projectIri knora-base:belongsToOntology ?ontologyIri .
}
WHERE {
  ?ontologyIri a                            owl:Ontology ;
               knora-base:attachedToProject ?projectIri .
  ?projectIri  a                            knora-admin:knoraProject .
  ?projectIri  ?projP                       ?projO .
}
`;

const auth = {
  headers: {
    Authorization: `Basic ${encodedCredentials}`,
    Accept: `application/trig`,
  },
};

export default function () {
  const url = new URL(fusekiUrl);
  url.searchParams.append("query", query);
  const res = http.get(url.toString(), auth);
  console.log(res.body);
  //  console.log({
  //    length: res.body.split("\n").length,
  //    out: res,
  //  });
  check(res, {
    "status was 200": (r) => r.status == 200,
    "body contains xxx lines": (r) => res.body.split("\n").length == 415,
  });
  sleep(1);
}
