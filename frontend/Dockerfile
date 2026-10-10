FROM public.ecr.aws/docker/library/node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ENV VITE_API_BASE_URL=""
RUN npm test && npm run build

FROM public.ecr.aws/docker/library/nginx:1.28-alpine
COPY nginx/15-railyatra-resolver.envsh /docker-entrypoint.d/15-railyatra-resolver.envsh
RUN chmod +x /docker-entrypoint.d/15-railyatra-resolver.envsh
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
