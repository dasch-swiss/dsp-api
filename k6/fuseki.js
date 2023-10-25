import http from "k6/http";
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import { check, sleep } from "k6";
import encoding from "k6/encoding";

export const options = {
  stages: [
    { duration: "10s", target: 1 },
    { duration: "10s", target: 10 },
  ],
  thresholds: {
    http_req_duration: ["p(90)<130", "p(95)<150"],
  },
};

const fusekiUrl = __ENV.FUSEKI_URL;
const credentials = __ENV.FUSEKI_USER + ":" + __ENV.FUSEKI_PASSWORD;
const encodedCredentials = encoding.b64encode(credentials);
const query = `
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

SELECT ?valueIri ?dimX ?dimY
  FROM <http://www.knora.org/data/0801/beol>
WHERE {
  ?valueIri a knora-base:StillImageFileValue .
  ?valueIri knora-base:internalFilename ?filename .
  FILTER (strstarts(str(?filename), "Jdjk3ZU01nt-GKOaaSJ3Ahx"))
  ?valueIri knora-base:dimX ?dimX .
  ?valueIri knora-base:dimY ?dimY .
}
`;
const auth = {
  headers: {
    Authorization: `Basic ${encodedCredentials}`,
  },
};

export default function () {
  const url = new URL(fusekiUrl);
  url.searchParams.append("query", query);
  const res = http.get(url.toString(), auth);
  // console.log(JSON.stringify(res));
  check(res, {
    "status was 200": (r) => r.status == 200,
  });
  sleep(1);
}
