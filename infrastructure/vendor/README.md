# Pinned MinIO runtime artifact

`minio-linux-amd64.gz` is a generated, compressed executable built from the exact
official `RELEASE.2024-09-22T00-33-43Z` source commit:
`03e996320ebb887112fb2a15c6f27936e5f124a0`.

Source: https://github.com/minio/minio/tree/03e996320ebb887112fb2a15c6f27936e5f124a0

Corresponding source archive:
https://codeload.github.com/minio/minio/tar.gz/03e996320ebb887112fb2a15c6f27936e5f124a0

MinIO copyright 2015–2024 MinIO, Inc. Licensed under GNU AGPL version 3:
https://github.com/minio/minio/blob/03e996320ebb887112fb2a15c6f27936e5f124a0/LICENSE

The source-build recipe is `infrastructure/minio-source.Dockerfile` (Go 1.23.12,
CGO disabled, linux/amd64). Rebuild using:

```sh
docker build -f infrastructure/minio-source.Dockerfile -t nascon-minio-source .
```

The runtime Dockerfile verifies the compressed artifact's SHA256 before unpacking.
Bundling the build output avoids unavailable historical image/binary hosts and a
multi-minute Go compilation on the grading machine. It adds a binary artifact to
the repository; this is third-party infrastructure, not competition application code.
No organizer credentials are included.
