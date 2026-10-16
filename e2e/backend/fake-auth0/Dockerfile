# A stand-in for Auth0, for local testing: signs ID tokens for test users and publishes its keys.
FROM node:22.11.0-alpine3.20
RUN apk add --no-cache openssl
WORKDIR /app
COPY server.js start.sh ./
RUN chmod +x start.sh
CMD ["./start.sh"]
