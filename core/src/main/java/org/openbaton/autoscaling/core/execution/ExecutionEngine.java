/*
 *
 *  *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.configuration.AutoScalingProperties;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.configuration.SpringProperties;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.monitoring.interfaces.MonitoringPluginCaller;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ExecutionEngine {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private ConfigurableApplicationContext context;

  private ExecutionManagement executionManagement;

  private ActionMonitor actionMonitor;

  @Autowired private NfvoProperties nfvoProperties;

  @Autowired private AutoScalingProperties autoScalingProperties;

  @Autowired private SpringProperties springProperties;

  private MonitoringPluginCaller client;

  @PostConstruct
  public void init() {
    //this.resourceManagement = context.getBean(ResourceManagement.class);
    this.executionManagement = context.getBean(ExecutionManagement.class);
  }

  private MonitoringPluginCaller getClient() {
    return (MonitoringPluginCaller)
        ((RabbitPluginBroker) context.getBean("rabbitPluginBroker"))
            .getMonitoringPluginCaller(
                autoScalingProperties.getRabbitmq().getBrokerIp(),
                springProperties.getRabbitmq().getUsername(),
                springProperties.getRabbitmq().getPassword(),
                springProperties.getRabbitmq().getPort(),
                "icinga-agent",
                "icinga",
                autoScalingProperties.getRabbitmq().getManagement().getPort());
  }

  public void setActionMonitor(ActionMonitor actionMonitor) {
    this.actionMonitor = actionMonitor;
  }

  public VirtualNetworkFunctionRecord scaleOut(
      String projectId, VirtualNetworkFunctionRecord vnfr, int numberOfInstances)
      throws NotFoundException {
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
    log.debug("Executing scale-out for VNFR with id: " + vnfr.getId());
    for (int i = 1; i <= numberOfInstances; i++) {
      if (actionMonitor.isTerminating(vnfr.getParent_ns_id())) {
        actionMonitor.finishedAction(
            vnfr.getParent_ns_id(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
        return vnfr;
      }
      log.debug("Adding new VNFCInstance -> number " + i);
      boolean scaled = false;
      for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
        if (scaled == true) break;
        if (vdu.getVnfc_instance().size() < vdu.getScale_in_out()
            && (vdu.getVnfc().iterator().hasNext())) {
          VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
          try {
            log.trace("Request NFVO to execute ScalingAction -> scale-out");
            nfvoRequestor
                .getNetworkServiceRecordAgent()
                .createVNFCInstance(
                    vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfComponent);
            log.trace("NFVO executed ScalingAction -> scale-out");
            log.info("Added new VNFCInstance to VDU " + vdu.getId());
            actionMonitor.finishedAction(
                vnfr.getParent_ns_id(), org.openbaton.autoscaling.catalogue.Action.SCALED);
            scaled = true;
            while (nfvoRequestor
                    .getNetworkServiceRecordAgent()
                    .findById(vnfr.getParent_ns_id())
                    .getStatus()
                == Status.SCALING) {
              log.debug(
                  "Waiting for NFVO to finish the ScalingAction of NSR " + vnfr.getParent_ns_id());
              try {
                Thread.sleep(5000);
              } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
              }
              if (actionMonitor.isTerminating(vnfr.getParent_ns_id())) {
                actionMonitor.finishedAction(
                    vnfr.getParent_ns_id(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                return vnfr;
              }
            }
            try {
              vnfr =
                  nfvoRequestor
                      .getNetworkServiceRecordAgent()
                      .getVirtualNetworkFunctionRecord(vnfr.getParent_ns_id(), vnfr.getId());
            } catch (SDKException e) {
              log.error(e.getMessage(), e);
              log.warn("Cannot execute ScalingAction. VNFR was not found or problems with the SDK");
              actionMonitor.finishedAction(vnfr.getParent_ns_id());
            }
            break;
          } catch (SDKException e) {
            log.warn(e.getMessage(), e);
          } catch (ClassNotFoundException e) {
            log.warn(e.getMessage(), e);
            break;
          }
        } else {
          log.warn("Maximum size of VDU with id: " + vdu.getId() + " reached...");
        }
      }
    }
    log.debug("Executed scale-out for VNFR with id: " + vnfr.getId());
    return vnfr;
  }

  public void scaleOutTo(String projectId, VirtualNetworkFunctionRecord vnfr, int value)
      throws SDKException, NotFoundException, VimException {
    int vnfci_counter = 0;
    for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
      vnfci_counter += vdu.getVnfc_instance().size();
    }
    for (int i = vnfci_counter + 1; i <= value; i++) {
      scaleOut(projectId, vnfr, 1);
    }
  }

  public void scaleOutToFlavour(
      String projectId, VirtualNetworkFunctionRecord vnfr, String flavour_id)
      throws SDKException, NotFoundException {
    throw new NotImplementedException();
  }

  public VirtualNetworkFunctionRecord scaleIn(
      String projectId, VirtualNetworkFunctionRecord vnfr, int numberOfInstances)
      throws NotFoundException {
    log.debug("Executing scale-in for VNFR with id: " + vnfr.getId());
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
    for (int i = 1; i <= numberOfInstances; i++) {
      VNFCInstance vnfcInstance_remove = null;
      if (actionMonitor.isTerminating(vnfr.getParent_ns_id())) {
        actionMonitor.finishedAction(
            vnfr.getParent_ns_id(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
        return vnfr;
      }
      log.debug("Removing VNFCInstance -> number " + i);
      boolean scaled = false;
      for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
        if (scaled == true) break;
        Set<VNFCInstance> vnfcInstancesToRemove = new HashSet<>();
        for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
          if (vnfcInstance.getState() == null || vnfcInstance.getState().equals("active")) {
            vnfcInstancesToRemove.add(vnfcInstance);
          }
        }
        if (vnfcInstancesToRemove.size() > 1 && vnfcInstancesToRemove.iterator().hasNext()) {
          try {
            vnfcInstance_remove = vnfcInstancesToRemove.iterator().next();
            if (vnfcInstance_remove == null) {
              log.warn("Not found VNFCInstance in VDU " + vdu.getId() + " that could be removed");
              break;
            }
            log.trace("Request NFVO to execute ScalingAction -> scale-in");
            nfvoRequestor
                .getNetworkServiceRecordAgent()
                .deleteVNFCInstance(
                    vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfcInstance_remove.getId());
            log.trace("NFVO executed ScalingAction -> scale-in");
            log.info("Removed VNFCInstance from VNFR " + vnfr.getId());
            actionMonitor.finishedAction(
                vnfr.getParent_ns_id(), org.openbaton.autoscaling.catalogue.Action.SCALED);
            scaled = true;
            while (nfvoRequestor
                    .getNetworkServiceRecordAgent()
                    .findById(vnfr.getParent_ns_id())
                    .getStatus()
                == Status.SCALING) {
              log.debug("Waiting for NFVO to finish the ScalingAction");
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
              }
              if (actionMonitor.isTerminating(vnfr.getParent_ns_id())) {
                actionMonitor.finishedAction(
                    vnfr.getParent_ns_id(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                return vnfr;
              }
            }
            try {
              vnfr =
                  nfvoRequestor
                      .getNetworkServiceRecordAgent()
                      .getVirtualNetworkFunctionRecord(vnfr.getParent_ns_id(), vnfr.getId());
            } catch (SDKException e) {
              log.error(e.getMessage(), e);
              log.warn("Cannot execute ScalingAction. VNFR was not found or problems with the SDK");
              actionMonitor.finishedAction(vnfr.getParent_ns_id());
            }
            break;
          } catch (SDKException e) {
            log.warn(e.getMessage(), e);
          } catch (ClassNotFoundException e) {
            log.warn(e.getMessage(), e);
            break;
          }

        } else {
          log.warn("Minimum size of VDU with id: " + vdu.getId() + " reached...");
        }
      }
    }
    log.debug("Executed scale-in for VNFR with id: " + vnfr.getId());
    return vnfr;
  }

  public void scaleInTo(String projectId, VirtualNetworkFunctionRecord vnfr, int value)
      throws SDKException, NotFoundException, VimException {
    int vnfci_counter = 0;
    for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
      vnfci_counter += vdu.getVnfc_instance().size();
    }
    for (int i = vnfci_counter; i > value; i--) {
      scaleIn(projectId, vnfr, 1);
    }
  }

  public void scaleInToFlavour(
      String projectId, VirtualNetworkFunctionRecord vnfr, String flavour_id)
      throws SDKException, NotFoundException {
    throw new NotImplementedException();
  }

  public void startCooldown(String projectId, String nsr_id, long cooldown) {
    executionManagement.executeCooldown(projectId, nsr_id, cooldown);
  }
}
