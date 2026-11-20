import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
const source = readFileSync(
  new URL(
    "../src/main/resources/static/api/docs/swagger-initializer.js",
    import.meta.url,
  ),
  "utf8",
);
function setup(fetchImpl) {
  const window = {
    location: { origin: "http://localhost:8080" },
    addEventListener() {},
  };
  vm.runInNewContext(source, { window, URL, fetch: fetchImpl });
  return window.cinemaSwaggerRequest;
}
test("public GET uses the browser session without fetching a CSRF token", async () => {
  const interceptor = setup(() => assert.fail("Unexpected CSRF fetch"));
  const result = await interceptor({
    url: "/api/catalog/films",
    method: "GET",
  });
  assert.equal(result.credentials, "same-origin");
});
test("cinema home GET is allowed on the same origin without a CSRF token", async () => {
  const interceptor = setup(() => assert.fail("Unexpected CSRF fetch"));
  const result = await interceptor({ url: "/", method: "GET" });
  assert.equal(result.credentials, "same-origin");
});
test("the home exception does not allow root writes or another origin", async () => {
  const interceptor = setup(() => assert.fail("Unexpected CSRF fetch"));
  await assert.rejects(interceptor({ url: "/", method: "POST" }));
  await assert.rejects(
    interceptor({ url: "https://other.example/", method: "GET" }),
  );
});
test("login POST receives a fresh CSRF token and preserves the request body", async () => {
  let count = 0;
  const interceptor = setup(async (url, options) => {
    count++;
    assert.equal(url, "/api/auth/csrf");
    assert.equal(options.credentials, "same-origin");
    return {
      ok: true,
      json: async () => ({ headerName: "X-CSRF-TOKEN", token: "masked-token" }),
    };
  });
  const result = await interceptor({
    url: "http://localhost:8080/api/auth/login",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: '{"email":"demo"}',
  });
  assert.equal(result.headers["X-CSRF-TOKEN"], "masked-token");
  assert.equal(result.body, '{"email":"demo"}');
  assert.equal(count, 1);
});
test("cross-origin and non-API targets cannot receive the session or CSRF token", async () => {
  const interceptor = setup(() => assert.fail("Unexpected CSRF fetch"));
  await assert.rejects(
    interceptor({
      url: "https://other.example/api/reservations",
      method: "POST",
    }),
  );
  await assert.rejects(
    interceptor({ url: "/actuator/health", method: "POST" }),
  );
});
test("failed CSRF fetch prevents the API write", async () => {
  const interceptor = setup(async () => ({ ok: false }));
  await assert.rejects(
    interceptor({ url: "/api/reservations", method: "POST" }),
    /CSRF/,
  );
});
