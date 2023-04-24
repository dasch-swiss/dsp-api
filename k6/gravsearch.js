import http from "k6/http";
import { sleep, group } from "k6";
export const options = {
    vus: 10,
    duration: "30s",
    thresholds: {
        // 'iteration_duration{scenario:default}': [`max>=0`,],
        'http_req_duration{group:::gravsearch}': [`max>=0`],
        'http_req_duration{group:::gravsearch2}': [`max>=0`],
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
    const url = "http://0.0.0.0:3333/v2/searchextended";
    const headers = {};
    group("gravsearch", function () {
        http.post(url, payload, { headers });
    });
    group("gravsearch2", function () {
        http.post(url, payload, { headers });
    });
    sleep(1);
}
