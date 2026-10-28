# 示例站点的静态文件：html 是 build.sh 在开发机上构建好的（typescript/apps/demo 的 dist），这里只装进 nginx。
FROM nginx:1.27-alpine
COPY html /usr/share/nginx/html
COPY default.conf.template /etc/nginx/templates/default.conf.template
