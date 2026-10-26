FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_USE_MOCKS=true
ARG NEXT_PUBLIC_CLIENT_AUTH=client:password
ARG NEXT_PUBLIC_MANAGER_AUTH=manager:password
ARG NEXT_PUBLIC_DEMO_INN=
ARG BACKEND_URL=http://app:8080
ENV NEXT_PUBLIC_USE_MOCKS=$NEXT_PUBLIC_USE_MOCKS \
    NEXT_PUBLIC_CLIENT_AUTH=$NEXT_PUBLIC_CLIENT_AUTH \
    NEXT_PUBLIC_MANAGER_AUTH=$NEXT_PUBLIC_MANAGER_AUTH \
    NEXT_PUBLIC_DEMO_INN=$NEXT_PUBLIC_DEMO_INN \
    BACKEND_URL=$BACKEND_URL \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine AS run
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
COPY --from=build --chown=app:app /app/public ./public
USER app
EXPOSE 3000
CMD ["node", "server.js"]
