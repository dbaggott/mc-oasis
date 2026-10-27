# The API as a container image for Lambda, through the Lambda Web Adapter,
# which forwards each invocation to the server on $PORT as ordinary HTTP.
# https://github.com/awslabs/aws-lambda-web-adapter
#
# Built from the repository root, which holds shared/ as well as api/:
#   docker build --platform linux/arm64 -f api/Dockerfile .
#
# CI passes NODE_VERSION from .tool-versions; the default serves a bare build.
ARG NODE_VERSION=24

FROM node:${NODE_VERSION}-slim AS builder
WORKDIR /repo
COPY api/package.json api/package-lock.json api/.npmrc api/
RUN npm ci --prefix api
COPY shared shared
COPY api api
RUN npm run build --prefix api

FROM node:${NODE_VERSION}-slim
# Upstream's image as copied to GHCR, digest unchanged, by
# .github/workflows/mirror-lambda-adapter.yml, which says why and how to bump.
COPY --from=ghcr.io/dbaggott/aws-lambda-adapter:1.0.1@sha256:1e5ab4d9242167500ed8a7bed8a79b448228aaa51cf382fb51fe4bf8a5f9a811 /lambda-adapter /opt/extensions/lambda-adapter
WORKDIR /app
COPY --from=builder /repo/api/dist/index.mjs /repo/api/dist/index.mjs.map ./

ENV PORT=8080
# The adapter counts a 404 as ready, so a mistyped path here passes silently;
# nothing tests it.
ENV AWS_LWA_READINESS_CHECK_PATH=/api/ping
ENV NODE_ENV=production
ENV NODE_OPTIONS=--enable-source-maps

# Build identity for GET /api/. Declared last so a new commit rebuilds nothing
# above.
ARG BUILD_BRANCH=""
ARG BUILD_COMMIT=""
ARG BUILD_TIME=""
ENV BUILD_BRANCH=$BUILD_BRANCH \
    BUILD_COMMIT=$BUILD_COMMIT \
    BUILD_TIME=$BUILD_TIME

CMD ["node", "index.mjs"]
