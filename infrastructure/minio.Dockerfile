# Generated from the exact historical source; see vendor/README.md and source recipe.
FROM node:20-alpine
COPY infrastructure/vendor/minio-linux-amd64.gz /tmp/minio.gz
COPY infrastructure/vendor/MINIO-LICENSE /usr/share/licenses/minio/LICENSE
RUN echo '4d913d8d5d83766b5903d1b95bfa40744e278573739bd05bb4dd447a25fe8319  /tmp/minio.gz' | sha256sum -c - \
    && gzip -d /tmp/minio.gz && mv /tmp/minio /usr/local/bin/minio && chmod 755 /usr/local/bin/minio
ENTRYPOINT ["/usr/local/bin/minio"]
