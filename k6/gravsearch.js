import http from "k6/http";
import { sleep, group } from "k6";
export const options = {
    vus: 20,
    duration: "60s",
    thresholds: {
        // 'iteration_duration{scenario:default}': [`max>=0`,],
        'http_req_duration{group:::warm-up-1}': [`max>=0`],
        'http_req_duration{group:::gravsearch}': [`max>=0`],
        'http_req_duration{group:::gravsearch-no-inference}': [`max>=0`],
    },
};
export default function () {
    const payload =
        "PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>" +
        "PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>" +
        "CONSTRUCT {" +
        "    ?r knora-api:isMainResource true ." +
        "} WHERE {" +
        "    ?r a anything:Thing ." +
        "}" +
        "OFFSET 0";

    const payload2 =
        "PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>" +
        "CONSTRUCT {" +
        "    ?mainRes knora-api:isMainResource true ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#urheber> ?propVal0 ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#erfassungsdatum> ?propVal1 ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#hatBildformat> ?linkedRes02 ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#bildnr> ?propVal3 ." +
        "} WHERE {" +
        "    ?mainRes a knora-api:Resource ." +
        "    ?mainRes a <http://0.0.0.0:3333/ontology/00FF/images/v2#bild> ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#urheber> ?propVal0 ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#erfassungsdatum> ?propVal1 ." +
        "    FILTER(knora-api:toSimpleDate(?propVal1) < \"GREGORIAN:2023-5-8:2023-5-8\"^^<http://api.knora.org/ontology/knora-api/simple/v2#Date>)" +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#hatBildformat> ?linkedRes02 ." +
        "    ?linkedRes02 a <http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat> ." +
        "    ?mainRes <http://0.0.0.0:3333/ontology/00FF/images/v2#bildnr> ?propVal3 ." +
        "    ?propVal3 <http://api.knora.org/ontology/knora-api/v2#valueAsString> ?propVal3Literal" +
        "    FILTER(?propVal3Literal != \"foobar\"^^<http://www.w3.org/2001/XMLSchema#string>)" +
        "}" +
        "OFFSET 0";

    const url = "http://0.0.0.0:3333/v2/searchextended";

    group("warm-up-1", function () {
        http.post(url, payload2);
    });
    group("gravsearch", function () {
        http.post(url, payload2);
    });
    group("gravsearch-no-inference", function () {
        http.post(url, payload2, { useInference: false });
    });
    sleep(1);
}
