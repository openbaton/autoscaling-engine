/*
 *
 *  *
 *  *  * Copyright (c) 2016 Open Baton (http://www.openbaton.org)
 *  *  *
 *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  * you may not use this file except in compliance with the License.
 *  *  * You may obtain a copy of the License at
 *  *  *
 *  *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  * See the License for the specific language governing permissions and
 *  *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.autoscaling.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Created by mpa on 25.01.16.
 */
@Service
@ConfigurationProperties(prefix = "ase")
//@PropertySource("classpath:openbaton-ase.properties")
public class AutoScalingProperties {

  private Rabbitmq rabbitmq;

  private Server server;

  private Management management;

  private Plugin plugin;

  private Service service;

  public Rabbitmq getRabbitmq() {
    return rabbitmq;
  }

  public void setRabbitmq(Rabbitmq rabbitmq) {
    this.rabbitmq = rabbitmq;
  }

  public Server getServer() {
    return server;
  }

  public void setServer(Server server) {
    this.server = server;
  }

  public Management getManagement() {
    return management;
  }

  public void setManagement(Management management) {
    this.management = management;
  }

  public Plugin getPlugin() {
    return plugin;
  }

  public void setPlugin(Plugin plugin) {
    this.plugin = plugin;
  }

  public Service getService() {
    return service;
  }

  public void setService(Service service) {
    this.service = service;
  }

  @Override
  public String toString() {
    return "AutoScalingProperties{"
        + "rabbitmq="
        + rabbitmq
        + ", server="
        + server
        + ", management="
        + management
        + ", plugin="
        + plugin
        + ", service="
        + service
        + '}';
  }

  public static class Rabbitmq {
    private String brokerIp;
    private Management management;
    private boolean autodelete;
    private boolean durable;
    private boolean exclusive;
    private int minConcurrency;
    private int maxConcurrency;

    public String getBrokerIp() {
      return brokerIp;
    }

    public void setBrokerIp(String brokerIp) {
      this.brokerIp = brokerIp;
    }

    public Management getManagement() {
      return management;
    }

    public void setManagement(Management management) {
      this.management = management;
    }

    public boolean isAutodelete() {
      return autodelete;
    }

    public void setAutodelete(boolean autodelete) {
      this.autodelete = autodelete;
    }

    public boolean isDurable() {
      return durable;
    }

    public void setDurable(boolean durable) {
      this.durable = durable;
    }

    public boolean isExclusive() {
      return exclusive;
    }

    public void setExclusive(boolean exclusive) {
      this.exclusive = exclusive;
    }

    public int getMinConcurrency() {
      return minConcurrency;
    }

    public void setMinConcurrency(int minConcurrency) {
      this.minConcurrency = minConcurrency;
    }

    public int getMaxConcurrency() {
      return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
      this.maxConcurrency = maxConcurrency;
    }

    @Override
    public String toString() {
      return "RabbitMQ{"
          + "brokerIp='"
          + brokerIp
          + '\''
          + ", management="
          + management
          + ", autodelete="
          + autodelete
          + ", durable="
          + durable
          + ", exclusive="
          + exclusive
          + ", minConcurrency="
          + minConcurrency
          + ", maxConcurrency="
          + maxConcurrency
          + '}';
    }
  }

  public static class Server {
    private String ip;

    private String port;

    public String getIp() {
      return ip;
    }

    public void setIp(String ip) {
      this.ip = ip;
    }

    public String getPort() {
      return port;
    }

    public void setPort(String port) {
      this.port = port;
    }

    @Override
    public String toString() {
      return "Server{" + "ip='" + ip + '\'' + ", port='" + port + '\'' + '}';
    }
  }

  public static class Management {
    private String port;

    public String getPort() {
      return port;
    }

    public void setPort(String port) {
      this.port = port;
    }

    @Override
    public String toString() {
      return "Management{" + "port='" + port + '\'' + '}';
    }
  }

  public static class Plugin {
    private String dir;

    private boolean startup;

    private Log log;

    public String getDir() {
      return dir;
    }

    public void setDir(String dir) {
      this.dir = dir;
    }

    public boolean isStartup() {
      return startup;
    }

    public void setStartup(boolean startup) {
      this.startup = startup;
    }

    public Log getLog() {
      return log;
    }

    public void setLog(Log log) {
      this.log = log;
    }

    @Override
    public String toString() {
      return "Plugin{" + "dir='" + dir + '\'' + ", startup=" + startup + ", log=" + log + '}';
    }
  }

  public static class Log {
    private String dir;

    public String getDir() {
      return dir;
    }

    public void setDir(String dir) {
      this.dir = dir;
    }

    @Override
    public String toString() {
      return "Log{" + "dir='" + dir + '\'' + '}';
    }
  }

  public static class Service {
    private String key;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    @Override
    public String toString() {
      return "Service{" + "key=" + key + '}';
    }
  }

  public static class File {
    private String path;

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    @Override
    public String toString() {
      return "File{" + "path='" + path + '\'' + '}';
    }
  }
}
