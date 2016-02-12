/*
 *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.openbaton.vnfm.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

/**
 * Created by mpa on 25.01.16.
 */
@Service
@ConfigurationProperties(prefix="spring")
@PropertySource("classpath:application.properties")
public class SpringProperties {

    private Rabbitmq rabbitmq;

    private Datasource datasource;

    private JPA jpa;

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(Rabbitmq rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    public JPA getJpa() {
        return jpa;
    }

    public void setJpa(JPA jpa) {
        this.jpa = jpa;
    }

    @Override
    public String toString() {
        return "SpringProperties{" +
                "rabbitmq=" + rabbitmq +
                ", dataSource=" + datasource +
                ", jpa=" + jpa +
                '}';
    }

    public static class Rabbitmq {
        private String host;
        private String username;
        private String password;
        private int port;
        private Listener listener;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public Listener getListener() {
            return listener;
        }

        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public String toString() {
            return "Rabbitmq{" +
                    "host='" + host + '\'' +
                    ", username='" + username + '\'' +
                    ", password='" + password + '\'' +
                    ", port='" + port + '\'' +
                    ", listener=" + listener +
                    '}';
        }

        public static class Listener {
            private String concurrency;
            private String maxConcurrency;

            public String getConcurrency() {
                return concurrency;
            }

            public void setConcurrency(String concurrency) {
                this.concurrency = concurrency;
            }

            public String getMaxConcurrency() {
                return maxConcurrency;
            }

            public void setMaxConcurrency(String maxConcurrency) {
                this.maxConcurrency = maxConcurrency;
            }

            @Override
            public String toString() {
                return "Listener{" +
                        "concurrency='" + concurrency + '\'' +
                        ", maxConcurrency='" + maxConcurrency + '\'' +
                        '}';
            }
        }
    }

    public static class Datasource {
        private String username;
        private String password;
        private String url;
        private String driverClassName;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        @Override
        public String toString() {
            return "Datasource{" +
                    "username='" + username + '\'' +
                    ", password='" + password + '\'' +
                    ", url='" + url + '\'' +
                    ", driverClassName='" + driverClassName + '\'' +
                    '}';
        }
    }

    public static class JPA {
        private String databasePlatform;
        private String showSql;
        private Hibernate hibernate;

        public String getDatabasePlatform() {
            return databasePlatform;
        }

        public void setDatabasePlatform(String databasePlatform) {
            this.databasePlatform = databasePlatform;
        }

        public String getShowSql() {
            return showSql;
        }

        public void setShowSql(String showSql) {
            this.showSql = showSql;
        }

        public Hibernate getHibernate() {
            return hibernate;
        }

        public void setHibernate(Hibernate hibernate) {
            this.hibernate = hibernate;
        }

        @Override
        public String toString() {
            return "JPA{" +
                    "databasePlatform='" + databasePlatform + '\'' +
                    ", showSql='" + showSql + '\'' +
                    ", hibernate=" + hibernate +
                    '}';
        }

        public static class Hibernate {
            private String ddlAuto;

            public String getDdlAuto() {
                return ddlAuto;
            }

            public void setDdlAuto(String ddlAuto) {
                this.ddlAuto = ddlAuto;
            }

            @Override
            public String toString() {
                return "Hibernate{" +
                        "ddlAuto='" + ddlAuto + '\'' +
                        '}';
            }
        }
    }
}
