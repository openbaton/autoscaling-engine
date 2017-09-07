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

package org.openbaton.autoscaling.core.decision;

import org.openbaton.autoscaling.configuration.AutoScalingProperties;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DecisionEngine {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private ConfigurableApplicationContext context;

  //@Autowired
  private ExecutionManagement executionManagement;

  @Autowired private NfvoProperties nfvoProperties;

  @Autowired private AutoScalingProperties autoScalingProperties;

  @PostConstruct
  public void init() {
    this.executionManagement = context.getBean(ExecutionManagement.class);
  }

  public void sendDecision(
      String projectId, String nsr_id, Map actionVnfrMap, Set<ScalingAction> actions, long cooldown)
      throws SDKException {
    //log.info("[DECISION_MAKER] DECIDED_ABOUT_ACTIONS " + new Date().getTime());
    log.debug("Send actions to Executor: " + actions.toString());
    executionManagement.executeActions(projectId, nsr_id, actionVnfrMap, actions, cooldown);
  }

  public Status getStatus(String projectId, String nsr_id) throws SDKException {
    log.debug("Check Status of NSR with id: " + nsr_id);
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            "autoscaling-engine",
            "",
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1",
            nfvoProperties.getSsl().isEnabled(),
            autoScalingProperties.getKey().getFile().getPath());
    NetworkServiceRecord networkServiceRecord = null;
    try {
      networkServiceRecord = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
    } catch (SDKException e) {
      log.warn(e.getMessage(), e);
      return Status.NULL;
    } catch (ClassNotFoundException e) {
      log.warn(e.getMessage(), e);
      return Status.NULL;
    } catch (FileNotFoundException e) {
      log.error("Key file not found");
      return Status.NULL;
    }
    if (networkServiceRecord == null || networkServiceRecord.getStatus() == null) {
      return Status.NULL;
    }
    return networkServiceRecord.getStatus();
  }

  public VirtualNetworkFunctionRecord getVNFR(String projectId, String nsr_id, String vnfr_id)
      throws SDKException, FileNotFoundException {
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            "autoscaling-engine",
            "",
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1",
            nfvoProperties.getSsl().isEnabled(),
            autoScalingProperties.getKey().getFile().getPath());
    try {
      VirtualNetworkFunctionRecord vnfr =
          nfvoRequestor
              .getNetworkServiceRecordAgent()
              .getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
      return vnfr;
    } catch (SDKException e) {
      log.error("Error while requesting NSR " + nsr_id, e);
      throw e;
    } catch (Exception e) {
      log.error("Error while using NfvoRequestor -> " + e.getMessage(), e);
      throw e;
    }
  }

  public List<VirtualNetworkFunctionRecord> getVNFRsOfTypeX(
      String projectId, String nsr_id, String type, String policyId) throws Exception {
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            "autoscaling-engine",
            "",
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1",
            nfvoProperties.getSsl().isEnabled(),
            autoScalingProperties.getKey().getFile().getPath());
    List<VirtualNetworkFunctionRecord> vnfrsOfTypeX = new ArrayList<>();
    List<VirtualNetworkFunctionRecord> vnfrsAll = new ArrayList<>();
    vnfrsAll.addAll(
        nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecords(nsr_id));

    if (type != null && !type.isEmpty()) {
      for (VirtualNetworkFunctionRecord vnfr : vnfrsAll) {
        if (vnfr.getType().equals(type)) {
          vnfrsOfTypeX.add(vnfr);
        }
      }
    } else {
      for (VirtualNetworkFunctionRecord vnfr : vnfrsAll) {
        for (AutoScalePolicy policy : vnfr.getAuto_scale_policy()) {
          if (policy.getId().equals(policyId)) {
            vnfrsOfTypeX.add(vnfr);
          }
        }
      }
    }
    return vnfrsOfTypeX;
  }
}
