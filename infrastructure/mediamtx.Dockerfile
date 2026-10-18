FROM bluenviron/mediamtx:1.9.1 AS media
FROM node:20-alpine
COPY --from=media /mediamtx /usr/local/bin/mediamtx
COPY infrastructure/mediamtx.yml /mediamtx.yml
ENTRYPOINT ["mediamtx", "/mediamtx.yml"]
