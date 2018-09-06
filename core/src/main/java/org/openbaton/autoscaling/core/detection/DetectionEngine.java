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

package org.openbaton.autoscaling.core.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.openbaton.autoscaling.configuration.AutoScalingProperties;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.configuration.SpringProperties;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.catalogue.nfvo.viminstances.OpenstackVimInstance;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimDriverException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.NfvoRequestorBuilder;
import org.openbaton.sdk.api.exception.SDKException;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.core.transport.Config;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Region;
import org.openstack4j.model.telemetry.Sample;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** Created by mpa on 27.10.15. */
@Service
@Scope("singleton")
public class DetectionEngine {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private ConfigurableApplicationContext context;

  //  private MonitoringPlugin monitor;

  // @Autowired
  private DetectionManagement detectionManagement;

  @Autowired private SpringProperties springProperties;

  @Autowired private NfvoProperties nfvoProperties;

  @Autowired private AutoScalingProperties autoScalingProperties;

  @PostConstruct
  public void init() {
    this.detectionManagement = context.getBean(DetectionManagement.class);
  }

  public void initializeMonitor() throws NotFoundException {
    //    log.info(
    //        "Get monitoring plugin with following parameters: "
    //            + autoScalingProperties.getRabbitmq().getBrokerIp(),
    //        springProperties.getRabbitmq().getUsername(),
    //        springProperties.getRabbitmq().getPassword(),
    //        springProperties.getRabbitmq().getPort(),
    //        "zabbix-plugin",
    //        "zabbix",
    //        autoScalingProperties.getRabbitmq().getManagement().getPort());
    //    try {
    //      monitor =
    //          new MonitoringPluginCaller(
    //              autoScalingProperties.getRabbitmq().getBrokerIp(),
    //              springProperties.getRabbitmq().getUsername(),
    //              springProperties.getRabbitmq().getPassword(),
    //              springProperties.getRabbitmq().getPort(),
    //              "/",
    //              "zabbix-plugin",
    //              "zabbix",
    //              autoScalingProperties.getRabbitmq().getManagement().getPort(),
    //              120000);
    //    } catch (TimeoutException e) {
    //      log.error(e.getMessage(), e);
    //    } catch (IOException e) {
    //      log.error(e.getMessage(), e);
    //    }
  }

  public synchronized List<Item> getRawMeasurementResults(
      VirtualNetworkFunctionRecord vnfr, String metric, String period)
      throws MonitoringException, NotFoundException, SDKException, VimDriverException {
    //    if (monitor == null) {
    //      initializeMonitor();
    //    }
    ArrayList<Item> measurementResults = new ArrayList<Item>();
    ArrayList<String> hostnames = new ArrayList<String>();
    ArrayList<String> metrics = new ArrayList<String>();
    metrics.add(metric);
    log.debug(
        "Getting all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + ".");
    for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
      for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
        if (vnfcInstance.getState() == null
            || vnfcInstance.getState().toLowerCase().equals("active")) {
          hostnames.add(vnfcInstance.getHostname());
        }
      }
    }
    log.trace(
        "Getting all measurement results for hostnames "
            + hostnames
            + " on metric "
            + metric
            + ".");

    NFVORequestor nfvoRequestor =
        NfvoRequestorBuilder.create()
            .nfvoIp(nfvoProperties.getIp())
            .nfvoPort(Integer.parseInt(nfvoProperties.getPort()))
            .serviceName("autoscaling-engine")
            .serviceKey(autoScalingProperties.getService().getKey())
            .sslEnabled(nfvoProperties.getSsl().isEnabled())
            .version("1")
            .projectId(vnfr.getProjectId())
            .build();
    List<BaseVimInstance> allVims = nfvoRequestor.getVimInstanceAgent().findAll();

    for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
      for (String vduName : vdu.getVimInstanceName()) {
        for (BaseVimInstance vim : allVims) {
          if (vim.getType().startsWith("openstack")) {
            OpenstackVimInstance osVim = (OpenstackVimInstance) vim;
            if (osVim.getName().equals(vduName)) {
              OSClient os = authenticate(osVim);
              List<? extends Sample> samples = os.telemetry().samples().list();
              log.debug("Retrieved list of samples: " + samples);
              for (Sample sample : samples) {
                log.debug("Sample to check if requested: " + sample);
                for (String hostname : hostnames) {
                  log.debug("Looking for measurements of " + hostname);
                  if (sample.getMetadata().containsKey("display_name")) {
                    if (sample.getMetadata().get("display_name").equals(hostname)
                        && metrics.contains(sample.getMeter())) {
                      log.info("Found requested measurement: " + sample);
                      Item item = new Item();
                      item.setHostname(hostname);
                      item.setHostId(sample.getMetadata().get("instance_id").toString());
                      item.setValue(Float.toString(sample.getVolume()));
                      item.setLastValue(Float.toString(sample.getVolume()));
                      item.setMetric(sample.getMeter());
                      measurementResults.add(item);
                      log.info("Added new item:" + item);
                      break;
                    }
                  }
                }
              }
            }
          } else {
            log.warn("VimInstance type " + vim.getType() + " is not supported...");
          }
        }
      }
    }
    //    measurementResults.addAll(monitor.queryPMJob(hostnames, metrics, period));

    if (hostnames.size() != measurementResults.size()) {
      throw new MonitoringException(
          "Requested amount of measurements is greater than the received amount of measurements -> "
              + hostnames.size()
              + ">"
              + measurementResults.size());
    }
    log.debug(
        "Got all measurement results for vnfr "
            + vnfr.getId()
            + " on metric "
            + metric
            + " -> "
            + measurementResults
            + ".");
    return measurementResults;
  }

  public double calculateMeasurementResult(ScalingAlarm alarm, List<Item> measurementResults) {
    log.debug("Calculating final measurement result...");
    double result;
    List<Double> consideredResults = new ArrayList<>();
    for (Item measurementResult : measurementResults) {
      consideredResults.add(Double.parseDouble(measurementResult.getValue()));
    }
    switch (alarm.getStatistic()) {
      case "avg":
        double sum = 0;
        for (Double consideredResult : consideredResults) {
          sum += consideredResult;
        }
        result = sum / measurementResults.size();
        break;
      case "min":
        result = Collections.min(consideredResults);
        break;
      case "max":
        result = Collections.max(consideredResults);
        break;
      default:
        result = -1;
        break;
    }
    return result;
  }

  public boolean checkThreshold(String comparisonOperator, double threshold, double result) {
    log.debug("Checking Threshold...");
    switch (comparisonOperator) {
      case ">":
        if (result > threshold) {
          return true;
        }
        break;
      case ">=":
        if (result >= threshold) {
          return true;
        }
        break;
      case "<":
        if (result < threshold) {
          return true;
        }
        break;
      case "<=":
        if (result <= threshold) {
          return true;
        }
        break;
      case "=":
        if (result == threshold) {
          return true;
        }
        break;
      case "!=":
        if (result != threshold) {
          return true;
        }
        break;
      default:
        return false;
    }
    return false;
  }

  public void sendAlarm(
      String projectId, String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
    detectionManagement.sendAlarm(projectId, nsr_id, vnfr_id, autoScalePolicy);
  }

  public OSClient authenticate(OpenstackVimInstance vimInstance) throws VimDriverException {

    OSClient os;
    Config cfg = Config.DEFAULT;
    cfg = cfg.withConnectionTimeout(10000);
    try {
      if (isV3API(vimInstance)) {

        Identifier domain =
            vimInstance.getDomain() == null || vimInstance.getDomain().equals("")
                ? Identifier.byName("Default")
                : Identifier.byName(vimInstance.getDomain());
        Identifier project = Identifier.byId(vimInstance.getTenant());

        //        String[] domainProjectSplit = vimInstance.getTenant().split(Pattern.quote(":"));
        //        if (domainProjectSplit.length == 2) {
        //          log.trace("Found domain name and project id: " +
        // Arrays.toString(domainProjectSplit));
        //          domain = Identifier.byName(domainProjectSplit[0]);
        //          project = Identifier.byId(domainProjectSplit[1]);
        //        }

        log.trace("Domain id: " + domain.getId());
        log.trace("Project id: " + project.getId());

        os =
            OSFactory.builderV3()
                .endpoint(vimInstance.getAuthUrl())
                .scopeToProject(project)
                .credentials(vimInstance.getUsername(), vimInstance.getPassword(), domain)
                .withConfig(cfg)
                .authenticate();
        if (vimInstance.getLocation() != null
            && vimInstance.getLocation().getName() != null
            && !vimInstance.getLocation().getName().isEmpty()) {
          try {
            Region region =
                ((OSClient.OSClientV3) os)
                    .identity()
                    .regions()
                    .get(vimInstance.getLocation().getName());

            if (region != null) {
              ((OSClient.OSClientV3) os).useRegion(vimInstance.getLocation().getName());
            }
          } catch (Exception ignored) {
            log.warn(
                "Not found region '"
                    + vimInstance.getLocation().getName()
                    + "'. Use default one...");
            return os;
          }
        }
      } else {
        os =
            OSFactory.builderV2()
                .endpoint(vimInstance.getAuthUrl())
                .credentials(vimInstance.getUsername(), vimInstance.getPassword())
                .tenantName(vimInstance.getTenant())
                .withConfig(cfg)
                .authenticate();
        if (vimInstance.getLocation() != null
            && vimInstance.getLocation().getName() != null
            && !vimInstance.getLocation().getName().isEmpty()) {
          try {
            ((OSClient.OSClientV2) os).useRegion(vimInstance.getLocation().getName());
            ((OSClient.OSClientV2) os).identity().listTokenEndpoints();
          } catch (Exception e) {
            log.warn(
                "Not found region '"
                    + vimInstance.getLocation().getName()
                    + "'. Use default one...");
            ((OSClient.OSClientV2) os).removeRegion();
          }
        }
      }
    } catch (AuthenticationException e) {
      throw new VimDriverException(e.getMessage(), e);
    }

    return os;
  }

  private boolean isV3API(BaseVimInstance vimInstance) {
    return vimInstance.getAuthUrl().endsWith("/v3")
        || vimInstance.getAuthUrl().endsWith("/v3/")
        || vimInstance.getAuthUrl().endsWith("/v3.0");
  }
}
