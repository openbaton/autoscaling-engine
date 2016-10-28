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

package org.openbaton.autoscaling.core.execution.task;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.core.execution.ExecutionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("prototype")
public class ExecutionTask implements Runnable {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private String nsr_id;

  private Set<ScalingAction> actions;

  private Map<String, String> actionVnfrMap;

  private String name;

  private String projectId;

  private ExecutionEngine executionEngine;

  private NFVORequestor nfvoRequestor;

  private long cooldown;

  private ActionMonitor actionMonitor;

  public ExecutionTask(
      String projectId,
      String nsr_id,
      Map actionVnfrMap,
      Set<ScalingAction> actions,
      long cooldown,
      ExecutionEngine executionEngine,
      ActionMonitor actionMonitor,
      NfvoProperties nfvoProperties) {
    this.actionMonitor = actionMonitor;
    log.debug("Initializing ExecutionTask for NSR with id: " + nsr_id + ". Actions: " + actions);
    this.projectId = projectId;
    this.nsr_id = nsr_id;
    this.actionVnfrMap = actionVnfrMap;
    this.actions = actions;
    this.cooldown = cooldown;
    this.executionEngine = executionEngine;
    this.name = "ExecutionTask#" + nsr_id;
    this.nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
  }

  @Override
  public void run() {
    log.info("Executing scaling actions for NSR " + nsr_id);
    VirtualNetworkFunctionRecord vnfr = null;
    //        try {
    //            vnfr = executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.SCALING);
    //        } catch (SDKException e) {
    //            log.error("Problems with SDK. Cannot update the VNFR. Scaling will not be executed");
    //            if (log.isDebugEnabled()) {
    //                log.error(e.getMessage(), e);
    //            }
    //            actionMonitor.finishedAction(vnfr_id);
    //            return;
    //        }
    try {
      for (ScalingAction action : actions) {
        nfvoRequestor.setProjectId(projectId);
        vnfr =
            nfvoRequestor
                .getNetworkServiceRecordAgent()
                .getVirtualNetworkFunctionRecord(nsr_id, actionVnfrMap.get(action.getId()));
        if (vnfr == null) {
          log.warn("Cannot execute ScalingAction. VNFR was not found or problems with the SDK");
          actionMonitor.finishedAction(nsr_id);
          return;
        }
        switch (action.getType()) {
          case SCALE_OUT:
            //log.info("[EXECUTOR] START_SCALE_OUT " + new Date().getTime());
            vnfr = executionEngine.scaleOut(projectId, vnfr, Integer.parseInt(action.getValue()));
            //log.info("[EXECUTOR] FINISH_SCALE_OUT " + new Date().getTime());
            break;
          case SCALE_OUT_TO:
            executionEngine.scaleOutTo(projectId, vnfr, Integer.parseInt(action.getValue()));
            break;
          case SCALE_OUT_TO_FLAVOUR:
            executionEngine.scaleOutToFlavour(projectId, vnfr, action.getValue());
            break;
          case SCALE_IN:
            vnfr = executionEngine.scaleIn(projectId, vnfr, Integer.parseInt(action.getValue()));
            break;
          case SCALE_IN_TO:
            executionEngine.scaleInTo(projectId, vnfr, Integer.parseInt(action.getValue()));
            break;
          case SCALE_IN_TO_FLAVOUR:
            executionEngine.scaleInToFlavour(projectId, vnfr, action.getValue());
            break;
          default:
            break;
        }
      }
    } catch (SDKException e) {
      log.error(e.getMessage(), e);
    } catch (NotFoundException e) {
      log.error(e.getMessage(), e);
    } catch (VimException e) {
      log.error(e.getMessage(), e);
    } finally {
      //            try {
      //                executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.ACTIVE);
      //            } catch (SDKException e) {
      //                log.error("Problems with the SDK. Cannot Update VNFR. VNFR status remains in SCALE");
      //                if (log.isDebugEnabled()) {
      //                    log.error(e.getMessage(), e);
      //                }
      //                actionMonitor.finishedAction(vnfr_id);
      //            }
      log.info("Executed scaling actions for NSR " + vnfr.getId());
      if (actionMonitor.getAction(nsr_id) == Action.SCALED) {
        //log.info("[EXECUTOR] START_COOLDOWN " + new Date().getTime());
        executionEngine.startCooldown(projectId, nsr_id, cooldown);
      } else {
        actionMonitor.finishedAction(nsr_id);
      }
    }
  }
}
