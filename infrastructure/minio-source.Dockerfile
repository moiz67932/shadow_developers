# Both the historical image and upstream binary are unavailable. Build the exact
# official RELEASE.2024-09-22T00-33-43Z source commit instead of changing versions.
FROM golang:1.23-alpine AS build
RUN apk add --no-cache git ca-certificates
ADD https://codeload.github.com/minio/minio/tar.gz/03e996320ebb887112fb2a15c6f27936e5f124a0 /tmp/minio.tar.gz
RUN mkdir /src && tar -xzf /tmp/minio.tar.gz -C /src --strip-components=1
WORKDIR /src
ENV CGO_ENABLED=0 GOMAXPROCS=2 GOMEMLIMIT=1GiB
RUN go build -p 2 -tags kqueue -trimpath -ldflags="-s -w -X github.com/minio/minio/cmd.Version=2024-09-22T00:33:43Z -X github.com/minio/minio/cmd.ReleaseTag=RELEASE.2024-09-22T00-33-43Z -X github.com/minio/minio/cmd.CommitID=03e996320ebb887112fb2a15c6f27936e5f124a0" -o /minio .
FROM node:20-alpine
COPY --from=build /minio /usr/local/bin/minio
ENTRYPOINT ["/usr/local/bin/minio"]
