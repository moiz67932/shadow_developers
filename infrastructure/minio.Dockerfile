# The historical competition container tag is no longer published. Fetch the
# identical official, pinned upstream server binary during the allowed build.
FROM node:20-alpine
# The source remains ready for the official archived binary when it is available
# in the organizer's supplied bundle.
ADD --chmod=755 https://dl.min.io/server/minio/release/linux-amd64/archive/minio.RELEASE.2024-09-22T00-33-43Z /usr/local/bin/minio
ENTRYPOINT ["/usr/local/bin/minio"]
