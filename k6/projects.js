import http from "k6/http";
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import { check, sleep } from "k6";
import encoding from "k6/encoding";

export const options = {
  stages: [
    { duration: "30s", target: 1 },
    //    { duration: "10s", target: 10 },
  ],
  thresholds: {
    http_req_duration: ["p(90)<130", "p(95)<150"],
  },
};

const dspApiURL = __ENV.DSP_API_URL;

export default function () {
  const res = http.get(dspApiURL + "/admin/projects");
  //  console.log(JSON.stringify(res));
  check(res, {
    "status was 200": (r) => r.status == 200,
  });
  sleep(1);
}
