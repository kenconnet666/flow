# 演示数据：seed.mjs 是 build.sh 在开发机上把 typescript/apps/demo/seed/seed.ts 打成的单个文件，files 是它要的种子文件。
FROM node:24-alpine
WORKDIR /seed
COPY seed.mjs seeder.sh ./
COPY files files
RUN chmod +x seeder.sh
ENV FLOW_SEED_DIR=/seed/files
ENTRYPOINT ["/seed/seeder.sh"]
