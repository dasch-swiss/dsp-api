import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  stages: [
    { duration: "10s", target: 2 },
    { duration: "10s", target: 100 },
  ],
  thresholds: {
    http_req_duration: ["p(90)<130", "p(95)<150"],
  },
};

export default function () {
  const res = http.get("https://api.dev.dasch.swiss/health", {
    tags: { what: "health" },
  });
  check(res, {
    "status was 200": (r) => r.status == 200,
    "duration was <=": (r) => r.timings.duration <= 10,
  });
  sleep(1);
}
