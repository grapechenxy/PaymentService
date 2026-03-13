const http = require("http");
const url = require("url");

const port = process.env.PORT || 8090;
const mode = process.env.MOCK_MODE || "alternate";
const randomSuccessPct = Number(process.env.MOCK_RANDOM_SUCCESS_PCT || "50");

let toggle = false;

const respondJson = (res, status, body) => {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
};

const shouldTimeout = () => mode === "timeout";

const pickStatus = () => {
  if (mode === "success") return 200;
  if (mode === "fail4xx") return 400;
  if (mode === "fail5xx") return 503;
  if (mode === "alternate") {
    toggle = !toggle;
    return toggle ? 200 : 503;
  }
  if (mode === "random") {
    const roll = Math.random() * 100;
    return roll < randomSuccessPct ? 200 : 503;
  }
  return 200;
};

const server = http.createServer((req, res) => {
  const parsed = url.parse(req.url, true);
  const forceStatus = parsed.query.forceStatus;

  if (parsed.pathname.endsWith("/health")) {
    return respondJson(res, 200, { status: "ok" });
  }

  if (!parsed.pathname.endsWith("/payment") || req.method !== "POST") {
    return respondJson(res, 404, { status: "error", message: "Not found" });
  }

  let body = "";
  req.on("data", (chunk) => (body += chunk));
  req.on("end", () => {
    const requestId = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
    const status = forceStatus === "timeout" ? null : Number(forceStatus || pickStatus());

    if (forceStatus && forceStatus !== "timeout") {
      // stay as provided
    }

    if (forceStatus === "timeout" || shouldTimeout()) {
      return setTimeout(() => {
        res.destroy();
      }, 15000);
    }

    if (status === 200) {
      return respondJson(res, 200, { status: "accepted", traceId: requestId });
    }
    if (status >= 400 && status < 500) {
      return respondJson(res, status, {
        status: "error",
        code: "INVALID_PAYMENT_INFO",
        message: "Payment info format is invalid",
        traceId: requestId,
      });
    }
    return respondJson(res, status, {
      status: "error",
      code: "SERVICE_UNAVAILABLE",
      message: "Upstream unavailable, try again later",
      traceId: requestId,
    });
  });
});

server.listen(port, () => {
  console.log(`Mock API listening on ${port} in mode ${mode}`);
});
