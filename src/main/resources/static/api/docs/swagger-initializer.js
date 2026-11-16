/* Same-origin session authentication; CSRF remains enabled on every API write. */
window.cinemaSwaggerRequest = async function (request) {
  const target = new URL(request.url, window.location.origin);
  if (
    target.origin !== window.location.origin ||
    !target.pathname.startsWith("/api/")
  ) {
    throw new Error(
      "La documentation ne peut appeler que l’API de cette origine.",
    );
  }
  request.credentials = "same-origin";
  const method = (request.method || "GET").toUpperCase();
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const response = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!response.ok)
      throw new Error("Impossible d’obtenir le jeton CSRF. Réessayez.");
    const csrf = await response.json();
    request.headers = request.headers || {};
    request.headers[csrf.headerName] = csrf.token;
  }
  return request;
};
window.addEventListener("load", function () {
  window.ui = SwaggerUIBundle({
    url: "/api/docs/openapi.yaml",
    dom_id: "#swagger-ui",
    deepLinking: true,
    validatorUrl: null,
    persistAuthorization: false,
    withCredentials: true,
    requestInterceptor: window.cinemaSwaggerRequest,
    presets: [SwaggerUIBundle.presets.apis],
    supportedSubmitMethods: ["get", "post", "put", "patch", "delete"],
  });
});
