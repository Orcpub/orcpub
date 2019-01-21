openssl req -subj "/C=PL/ST=Warsaw/L=Warsaw/O=Orcpub Web/OU=Orcpub/CN=*/" -x509 -nodes -days 365 -newkey rsa:2048 -keyout snakeoil.key -out snakeoil.crt
