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

package org.openbaton.autoscaling.core.decision.task;

import org.openbaton.autoscaling.core.decision.DecisionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.common.ScalingActionType;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("prototype")
public class DecisionTask implements Runnable {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private String nsr_id;

  private AutoScalePolicy autoScalePolicy;

  private String name;

  private String projectId;

  private DecisionEngine decisionEngine;

  private ActionMonitor actionMonitor;

  public DecisionTask(
      String projectId,
      String nsr_id,
      AutoScalePolicy autoScalePolicy,
      DecisionEngine decisionEngine,
      ActionMonitor actionMonitor) {
    this.projectId = projectId;
    this.nsr_id = nsr_id;
    this.autoScalePolicy = autoScalePolicy;
    this.decisionEngine = decisionEngine;
    this.actionMonitor = actionMonitor;
    this.name = "DecisionTask#" + nsr_id;
  }

  @Override
  public void run() {
    log.info("Requested decison-making for NSR with id " + nsr_id);
    log.debug(
        "Requested Decision-making for AutoScalePolicy with id: "
            + autoScalePolicy.getId()
            + "of NSR with id: "
            + nsr_id);
    Map<String, String> actionVnfrMap = new HashMap<>();
    if (decisionEngine.getStatus(projectId, nsr_id) == Status.ACTIVE) {
      log.debug(
          "Status is ACTIVE. So continue with Decision-maikng. Next step is to check if scale-out or scale-in is possible based on numbers of already deployed VNFCInstances and limits");
      if (actionMonitor.isTerminating(nsr_id)) {
        return;
      }
      try {
        Set<ScalingAction> filteredActions = new HashSet<>();
        for (ScalingAction action : autoScalePolicy.getActions()) {
          List<VirtualNetworkFunctionRecord> vnfrsTarget = new ArrayList<>();
          if (action.getTarget() == null || action.getTarget().isEmpty()) {
            vnfrsTarget =
                decisionEngine.getVNFRsOfTypeX(projectId, nsr_id, null, autoScalePolicy.getId());
          } else {
            vnfrsTarget =
                decisionEngine.getVNFRsOfTypeX(
                    projectId, nsr_id, action.getTarget(), autoScalePolicy.getId());
          }
          log.debug(
              "Decision-Maker checks if it possible to execute Action of type "
                  + action.getType()
                  + " for VNFRs of type "
                  + action.getTarget());
          for (VirtualNetworkFunctionRecord vnfr : vnfrsTarget) {
            if (action.getType() == ScalingActionType.SCALE_OUT) {
              for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (vdu.getVnfc_instance().size() < vdu.getScale_in_out()) {
                  log.debug(
                      "VDU with id "
                          + vdu.getId()
                          + " allows a scale-out. At least one more VNFCInstance is possible");
                  filteredActions.add(action);
                  actionVnfrMap.put(action.getId(), vnfr.getId());
                  break;
                }
                log.debug(
                    "VDU with id "
                        + vdu.getId()
                        + " reached already the maximum number of VNFCInstances. So no scale-out possible on this VDU.");
              }
            } else if (action.getType() == ScalingActionType.SCALE_IN) {
              for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                Set<VNFCInstance> vnfcInstancesToRemove = new HashSet<>();
                for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                  if (vnfcInstance.getState() == null
                      || vnfcInstance.getState().toLowerCase().equals("active")) {
                    vnfcInstancesToRemove.add(vnfcInstance);
                  }
                }
                if (vnfcInstancesToRemove.size() > 1) {
                  log.debug(
                      "VDU with id "
                          + vdu.getId()
                          + " allows a scale-in. At least one less VNFCInstance is possible");
                  filteredActions.add(action);
                  actionVnfrMap.put(action.getId(), vnfr.getId());
                  break;
                }
                log.debug(
                    "VDU with id "
                        + vdu.getId()
                        + " reached already the minimum number of VNFCInstances. So no scale-in possible on this VDU.");
              }
            }
            if (actionVnfrMap.containsKey(action.getId())) {
              log.debug(
                  "Found VNFR of type "
                      + action.getTarget()
                      + " that allows proposed scaling operation to be executed");
              break;
            }
          }
          if (filteredActions.contains(action)) {
            log.info(
                "Decision-Maker accepted the execution of ScalingAction "
                    + action
                    + " because conditions are met.");
          } else {
            log.info(
                "Decision-Maker rejected the execution of ScalingAction "
                    + action
                    + " because conditions are not met.");
          }
        }
        if (filteredActions.size() > 0) {
          log.info("Send actions to ExecutionEngine -> " + filteredActions);
          decisionEngine.sendDecision(
              projectId, nsr_id, actionVnfrMap, filteredActions, autoScalePolicy.getCooldown());
        }
      } catch (SDKException e) {
        log.warn(
            "Not able to fetch the VNFR from the NFVO. Stop here and wait for the next request of decision-making....",
            e);
      } catch (Exception e) {
        log.error("Error while using NfvoRequestor -> " + e.getMessage(), e);
      } finally {
        actionMonitor.finishedAction(nsr_id);
      }
    } else {
      log.debug("Status is not ACTIVE. So do not send actions to ExecutionEngine. Do nothing!");
    }
  }
}
