// Fake Auth0 for local testing. No dependencies.
//
// HTTPS (443, inside the Docker network as https://auth.openbenches.test/):
//   GET /.well-known/jwks.json   the public key, which the website fetches to verify ID tokens
// HTTP (80, published to the host for the app and tests):
//   GET  /test/users              the test users
//   POST /test/token?user=ID      a signed ID token for that user, as Auth0 would issue it
//        &expired=1               ...already expired, to test expiry handling
const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const https = require("node:https");

const ISSUER = `https://${process.env.HOSTNAME_FOR_TLS}/`;
const AUDIENCE = process.env.CLIENT_ID;

// Users as Auth0 identifies them: "<connection>|<id>", or "oauth2|<connection>|<id>".
const USERS = [
  { id: "ada", sub: "github|7001", nickname: "ada-benches", name: "Ada Lovelace", provider: "GitHub" },
  { id: "osm", sub: "oidc|openstreetmap-openid|7002", nickname: "bench_mapper", name: "Bench Mapper", provider: "OpenStreetMap" },
  { id: "discord", sub: "oauth2|discord|7003", nickname: "parkbench", name: "Park Bench", provider: "Discord" },
];

const privateKey = crypto.createPrivateKey(fs.readFileSync("/certs/signing.key"));
const cert = new crypto.X509Certificate(fs.readFileSync("/certs/signing.crt"));
const KID = cert.fingerprint256.replace(/:/g, "").slice(0, 16);
const jwk = {
  ...cert.publicKey.export({ format: "jwk" }), kid: KID, use: "sig", alg: "RS256",
  x5c: [cert.raw.toString("base64")],
};

const b64url = (data) => Buffer.from(data).toString("base64url");

function idToken(user, expired) {
  const now = Math.floor(Date.now() / 1000);
  const iat = expired ? now - 7200 : now;
  const claims = {
    iss: ISSUER, aud: AUDIENCE, sub: user.sub, iat, exp: iat + 3600,
    nickname: user.nickname, name: user.name, picture: null,
  };
  const header = { alg: "RS256", typ: "JWT", kid: KID };
  const input = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  const signature = crypto.sign("RSA-SHA256", Buffer.from(input), privateKey).toString("base64url");
  return { token: `${input}.${signature}`, exp: claims.exp };
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

function handle(req, res) {
  const url = new URL(req.url, "http://x");
  if (url.pathname === "/.well-known/jwks.json") return send(res, 200, { keys: [jwk] });
  if (url.pathname === "/health") return send(res, 200, { ok: true });
  if (url.pathname === "/test/users") return send(res, 200, USERS.map(({ id, nickname, name, provider }) => ({ id, nickname, name, provider })));
  if (url.pathname === "/test/token" && req.method === "POST") {
    const user = USERS.find((u) => u.id === url.searchParams.get("user"));
    if (!user) return send(res, 404, { error: "unknown user" });
    const { token, exp } = idToken(user, url.searchParams.get("expired") === "1");
    return send(res, 200, { id_token: token, expires_at: exp, nickname: user.nickname, name: user.name, provider: user.provider });
  }
  send(res, 404, { error: "not found" });
}

http.createServer(handle).listen(80);
https.createServer({ key: fs.readFileSync("/certs/server.key"), cert: fs.readFileSync("/certs/server.crt") }, handle).listen(443);
console.log(`fake Auth0 up: issuer ${ISSUER}, audience ${AUDIENCE}`);
