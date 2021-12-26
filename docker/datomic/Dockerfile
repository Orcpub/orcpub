FROM openjdk:8u242-jre
MAINTAINER daemonsthere@gmail.com

ENV DATOMIC_VERSION 0.9.5703

ADD /deploy/datomic-free-${DATOMIC_VERSION} /

RUN mv /datomic-free-${DATOMIC_VERSION} datomic

WORKDIR /datomic

RUN cp config/samples/free-transactor-template.properties transactor.properties

RUN sed "s/host=localhost/host=datomic/" -i transactor.properties

RUN sed "s/# storage-access=local/storage-access=remote/" -i transactor.properties

RUN mkdir /data
RUN sed "s/# data-dir=data/data-dir=\/data/" -i transactor.properties

RUN mkdir /log
RUN sed "s/# log-dir=log/log-dir=\/log/" -i transactor.properties

ADD deploy/start.sh ./
RUN chmod +x start.sh

#EXPOSE 4334 4335 4336

CMD ["./start.sh"]
