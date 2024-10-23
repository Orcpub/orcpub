FROM openjdk:8u242-jre

ENV DATOMIC_VERSION 0.9.5703

ADD ./lib/datomic-free-0.9.5703.zip /tmp/

RUN mv /tmp/datomic-free-0.9.5703 /datomic

#RUN wget https://my.datomic.com/downloads/free/${DATOMIC_VERSION} -qO /tmp/datomic.zip \
#  && unzip /tmp/datomic.zip \
#  && rm /tmp/datomic.zip \
#  && mv /datomic-free-${DATOMIC_VERSION} /datomic

WORKDIR /datomic

RUN cp /datomic/config/samples/free-transactor-template.properties /datomic/config/transactor.properties

RUN mkdir /data
RUN sed "s/# data-dir=data/data-dir=\/data/" -i /datomic/config/transactor.properties

RUN mkdir /log
RUN sed "s/# log-dir=log/log-dir=\/log/" -i /datomic/config/transactor.properties

ADD deploy/start.sh /datomic/
RUN chmod +x /datomic/start.sh

#EXPOSE 4334 4335 4336
CMD ["/datomic/start.sh"]
