FROM ubuntu

RUN apt-get update

RUN apt-get install -y libxaw7-dev libxt-dev libxmu-dev libx11-dev libxext-dev make gcc

RUN apt install -y default-jre default-jdk

COPY tsim /tmp/tsim

WORKDIR /tmp/tsim

RUN ./configure

RUN make

RUN make install