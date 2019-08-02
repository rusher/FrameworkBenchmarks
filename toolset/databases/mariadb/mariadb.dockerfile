FROM ubuntu:16.04

RUN apt-get update > /dev/null
RUN apt-get install -yqq locales sudo > /dev/null

RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ADD create.sql create.sql
ADD my.cnf my.cnf
ADD mariadb.list mariadb.list

RUN apt-get -y install software-properties-common
RUN apt-key adv --recv-keys --keyserver hkp://keyserver.ubuntu.com:80 0xF1656F24C74CD1D8
RUN add-apt-repository 'deb [arch=amd64,arm64,i386,ppc64el] http://mariadb.mirrors.ovh.net/MariaDB/repo/10.4/ubuntu xenial main'
RUN apt-get update > /dev/null
RUN ["/bin/bash", "-c", "debconf-set-selections <<< \"mariadb-server-10.4 mariadb-server/root_password password secret\""]
RUN ["/bin/bash", "-c", "debconf-set-selections <<< \"mariadb-server-10.4 mariadb-server/root_password_again password secret\""]
RUN DEBIAN_FRONTEND=noninteractive apt -y install mariadb-server

RUN mv /etc/mysql/my.cnf /etc/mysql/my.cnf.orig
RUN cp my.cnf /etc/mysql/

RUN mkdir /var/run/mysqld
RUN ls -lrt /etc/mysql
RUN chown -R mysql:mysql /var/lib/mysql /var/log/mysql /var/run/mysqld /etc/mysql
RUN mysqld_safe  --user=mysql --skip-syslog & \
    sleep 15 && \
    tail -n500 /var/lib/mysql/*.err && \
    until sudo mysql -e "exit"; do sleep 1; done && \
    sudo mysql < create.sql && \
    sudo mysql -e "SELECT * FROM hello_world.world" && \
    sudo mysqladmin shutdown

CMD mysqld_safe  --user=mysql --skip-syslog
