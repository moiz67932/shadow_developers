FROM node:20-alpine
WORKDIR /app/backend
COPY backend/package.json backend/package-lock.json ./
RUN npm ci --omit=dev
COPY backend ./
EXPOSE 8080
CMD ["npm", "start"]
