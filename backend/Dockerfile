FROM node:20-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app/backend
COPY backend/package.json backend/package-lock.json ./
RUN npm ci --omit=dev
COPY backend ./
EXPOSE 8080
CMD ["npm", "start"]
