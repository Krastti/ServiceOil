# nginx 1.31.7, pinned to the verified official image.
FROM nginx@sha256:332d0beea97cff8c0d5d27976c0778a4b00345b9d74c2feaaf8efde548314948
RUN rm -rf /usr/share/nginx/html/*
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY src/main/resources/static/ /usr/share/nginx/html/
EXPOSE 80
