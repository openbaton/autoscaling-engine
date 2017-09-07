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

package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.configuration.AutoScalingProperties;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.core.execution.task.CooldownTask;
import org.openbaton.autoscaling.core.execution.task.ExecutionTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.ErrorHandler;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ExecutionManagement {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private ThreadPoolTaskScheduler taskScheduler;

  @Autowired private ExecutionEngine executionEngine;

  private ActionMonitor actionMonitor;

  @Autowired private NfvoProperties nfvoProperties;
  @Autowired private AutoScalingProperties autoScalingProperties;

  @PostConstruct
  public void init() {
    this.actionMonitor = new ActionMonitor();
    this.executionEngine.setActionMonitor(actionMonitor);
    this.taskScheduler = new ThreadPoolTaskScheduler();
    this.taskScheduler.setPoolSize(10);
    this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
    this.taskScheduler.setRemoveOnCancelPolicy(true);
    this.taskScheduler.setErrorHandler(
        new ErrorHandler() {
          protected Logger log = LoggerFactory.getLogger(this.getClass());

          @Override
          public void handleError(Throwable t) {
            log.error(t.getMessage(), t);
          }
        });
    this.taskScheduler.initialize();
  }

  public void executeActions(
      String projectId, String nsr_id, Map actionVnfrMap, Set<ScalingAction> actions, long cooldown)
      throws SDKException {
    //log.info("[EXECUTOR] RECEIVED_ACTION " + new Date().getTime());
    if (actionMonitor.requestAction(nsr_id, Action.SCALE)) {
      log.info("Executing scaling actions for NSR " + nsr_id + " -> " + actions);
      log.debug(
          "Creating new ExecutionTask of ScalingActions: "
              + actions
              + " for NSR with id: "
              + nsr_id);
      ExecutionTask executionTask =
          new ExecutionTask(
              projectId,
              nsr_id,
              actionVnfrMap,
              actions,
              cooldown,
              executionEngine,
              actionMonitor,
              nfvoProperties,
              autoScalingProperties);
      taskScheduler.execute(executionTask);
    } else {
      if (actionMonitor.getAction(nsr_id) == Action.SCALE) {
        log.debug(
            "Processing already an execution request for NSR with id: "
                + nsr_id
                + ". Cannot create another ExecutionTask for NSR with id: "
                + nsr_id);
      } else if (actionMonitor.getAction(nsr_id) == Action.COOLDOWN) {
        log.debug(
            "Waiting for Cooldown for NSR with id: "
                + nsr_id
                + ". Cannot create another ExecutionTask for NSR with id: "
                + nsr_id);
      } else {
        log.warn(
            "Problem while starting ExecutionThread. Internal Status is: "
                + actionMonitor.getAction(nsr_id));
      }
    }
  }

  public void executeCooldown(String projectId, String nsr_id, long cooldown) {
    if (actionMonitor.isTerminating(nsr_id)) {
      actionMonitor.finishedAction(nsr_id, Action.TERMINATED);
      return;
    }
    log.info("Starting COOLDOWN (" + cooldown + "s) for NSR with id: " + nsr_id);
    if (actionMonitor.requestAction(nsr_id, Action.COOLDOWN)) {
      log.debug("Creating new CooldownTask for NSR with id: " + nsr_id);
      CooldownTask cooldownTask =
          new CooldownTask(nsr_id, cooldown, executionEngine, actionMonitor);
      taskScheduler.execute(cooldownTask);
    } else {
      if (actionMonitor.getAction(nsr_id) == Action.COOLDOWN) {
        log.info(
            "Waiting already for Cooldown for NSR with id: "
                + nsr_id
                + ". Cannot create another ExecutionTask for NSR with id: "
                + nsr_id);
      } else if (actionMonitor.getAction(nsr_id) == Action.SCALE) {
        log.info("NSR with id: " + nsr_id + " is still in Scaling.");
      } else {
        log.debug(actionMonitor.toString());
      }
    }
  }

  //    public void stop(String nsr_id) {
  //        log.debug("Stopping ExecutionTask for all VNFRs of NSR with id: " + nsr_id);
  //        NetworkServiceRecord nsr = null;
  //        try {
  //            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
  //        } catch (SDKException e) {
  //            log.error(e.getMessage(), e);
  //        } catch (ClassNotFoundException e) {
  //            log.error(e.getMessage(), e);
  //        }
  //        if (nsr != null && nsr.getVnfr() != null) {
  //            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
  //                stop(nsr_id);
  //            }
  //        }
  //        log.debug("Stopped all ExecutionTasks for NSR with id: " + nsr_id);
  //    }

  @Async
  public Future<Boolean> stop(String projectId, String nsr_id) {
    log.debug("Stopping ExecutionTask/CooldownTask for VNFR with id: " + nsr_id);
    int i = 60;
    while (!actionMonitor.isTerminated(nsr_id)
        && actionMonitor.getAction(nsr_id) != Action.INACTIVE
        && i >= 0) {
      actionMonitor.terminate(nsr_id);
      log.debug(
          "Waiting for finishing ExecutionTask/Cooldown for NSR with id: "
              + nsr_id
              + " ("
              + i
              + "s)");
      log.debug(actionMonitor.toString());
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        log.error(e.getMessage(), e);
      }
      i--;
      if (i <= 0) {
        actionMonitor.removeId(nsr_id);
        log.error("Were not able to wait until ExecutionTask finished for NSR with id: " + nsr_id);
        return new AsyncResult<>(false);
      }
    }
    actionMonitor.removeId(nsr_id);
    log.debug("Stopped ExecutionTask for VNFR with id: " + nsr_id);
    return new AsyncResult<>(true);
  }
}
