import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = "http://localhost:8080/api/test";

export const allowedRequests = new Counter("allowed_requests");
export const rateLimitedRequests = new Counter("rate_limited_requests");


export const options = {
    stages: [
        { duration: "1m", target: 10 },
        { duration: "1m", target: 50 },
        { duration: "1m", target: 100 },
        { duration: "2m", target: 200 },
        { duration: "1m", target: 0 },
    ],

    thresholds: {
        http_req_duration: [
            "p(95)<100",
        ],
    },
};


export default function () {

    const params = {
        headers: {
            "X-Client-Id": "67890"
        },
    };


    const response = http.get(
        BASE_URL,
        params
    );


    if (response.status === 200) {
        allowedRequests.add(1);
    }


    if (response.status === 429) {
        rateLimitedRequests.add(1);
    }


    check(response, {

        "valid status code": (r) =>
            r.status === 200 ||
            r.status === 429,

    });

}