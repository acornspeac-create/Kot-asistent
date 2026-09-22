FROM node:20-alpine

WORKDIR /app

COPY server/server.mjs ./server.mjs

ENV NODE_ENV=production

EXPOSE 8787

CMD ["node", "server.mjs"]
