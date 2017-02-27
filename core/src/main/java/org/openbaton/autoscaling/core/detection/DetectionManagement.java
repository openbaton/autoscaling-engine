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

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DetectionManagement {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private ThreadPoolTaskScheduler taskScheduler;

  private Map<String, Map<String, Map<String, ScheduledFuture>>> detectionTasks;

  @Autowired private DetectionEngine detectionEngine;

  @Autowired private DecisionManagement decisionManagement;

  private ActionMonitor actionMonitor;

  @Autowired private NfvoProperties nfvoProperties;

  @PostConstruct
  public void init() {
    this.actionMonitor = new ActionMonitor();
    this.detectionTasks = new HashMap<>();
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

  public void start(String projectId, String nsr_id) throws NotFoundException {
    log.debug("Activating Alarm Detection for NSR with id: " + nsr_id);
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
    NetworkServiceRecord nsr = null;
    try {
      nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
    } catch (SDKException e) {
      log.error("Error while requesting NSR " + nsr_id, e);
      return;
    } catch (Exception e) {
      log.error("Error while using NfvoRequestor -> " + e.getMessage(), e);
      return;
    }
    if (nsr == null) {
      throw new NotFoundException("Not Found NetworkServiceDescriptor with id: " + nsr_id);
    }
    for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
      for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
        start(projectId, nsr_id, vnfr.getId(), autoScalePolicy);
      }
    }
    log.info("Activated Alarm Detection for NSR with id: " + nsr_id);
  }

  public void start(String projectId, String nsr_id, String vnfr_id) throws NotFoundException {
    log.debug("Activating Alarm Detection for VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
    VirtualNetworkFunctionRecord vnfr = null;
    try {
      vnfr =
          nfvoRequestor
              .getNetworkServiceRecordAgent()
              .getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
    } catch (SDKException e) {
      log.error("Error while requesting NSR " + nsr_id, e);
      return;
    } catch (Exception e) {
      log.error("Error while using NfvoRequestor -> " + e.getMessage(), e);
      return;
    }
    if (vnfr == null) {
      //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
      log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
      return;
    }
    for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
      start(projectId, nsr_id, vnfr.getId(), autoScalePolicy);
    }
    log.info("Activated Alarm Detection for VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
  }

  public void start(
      String projectId, String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy)
      throws NotFoundException {
    log.debug(
        "Activating Alarm Detection for AutoScalePolicy with id: "
            + autoScalePolicy.getId()
            + " of VNFR "
            + vnfr_id
            + " of NSR with id: "
            + nsr_id);
    if (actionMonitor.requestAction(autoScalePolicy.getId(), Action.INACTIVE)) {
      if (!detectionTasks.containsKey(nsr_id)) {
        detectionTasks.put(nsr_id, new HashMap<String, Map<String, ScheduledFuture>>());
      }
      if (!detectionTasks.get(nsr_id).containsKey(vnfr_id)) {
        detectionTasks.get(nsr_id).put(vnfr_id, new HashMap<String, ScheduledFuture>());
      }
      if (detectionTasks.get(nsr_id).get(vnfr_id).containsKey(autoScalePolicy.getId())) {
        //                log.debug("Restarting Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        //                detectionTasks.get(nsr_id).get(vnfr_id).get(autoScalePolicy.getId()).cancel(false);
        log.warn(
            "Got new request for starting DetectionTask for AutoScalePolicy "
                + autoScalePolicy.getId()
                + " but it was already running. So do nothing");
        return;
      }
      log.debug(
          "Creating new DetectionTask for AutoScalingPolicy "
              + autoScalePolicy.getName()
              + " with id: "
              + autoScalePolicy.getId()
              + " of VNFR with id: "
              + vnfr_id);
      DetectionTask detectionTask =
          new DetectionTask(
              projectId,
              nsr_id,
              vnfr_id,
              autoScalePolicy,
              detectionEngine,
              nfvoProperties,
              actionMonitor);
      ScheduledFuture scheduledFuture =
          taskScheduler.scheduleAtFixedRate(detectionTask, autoScalePolicy.getPeriod() * 1000);
      detectionTasks.get(nsr_id).get(vnfr_id).put(autoScalePolicy.getId(), scheduledFuture);
      log.info(
          "Activated Alarm Detection for AutoScalePolicy with id: "
              + autoScalePolicy.getId()
              + " of VNFR "
              + vnfr_id
              + " of NSR with id: "
              + nsr_id);
    } else {
      log.debug(
          "Alarm Detection for AutoScalePolicy with id: "
              + autoScalePolicy.getId()
              + " of VNFR "
              + vnfr_id
              + " of NSR with id: "
              + nsr_id
              + " were already activated");
    }
  }

  public void stop(String projectId, String nsr_id) throws NotFoundException {
    log.debug("Deactivating Alarm Detection of NSR with id: " + nsr_id);
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
    NetworkServiceRecord nsr = null;
    try {
      nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
    } catch (SDKException e) {
      log.error("Error while requesting NSR " + nsr_id, e);
      return;
    } catch (Exception e) {
      log.error("Error while using NfvoRequestor -> " + e.getMessage(), e);
      return;
    }
    for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
      stop(projectId, nsr_id, vnfr.getId());
    }
    log.info("Deactivated Alarm Detection of NSR with id: " + nsr_id);
  }

  @Async
  public Future<Boolean> stop(String projectId, String nsr_id, String vnfr_id)
      throws NotFoundException {
    log.debug(
        "Deactivating Alarm Detection of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            projectId,
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
    VirtualNetworkFunctionRecord vnfr = null;
    Set<Future<Boolean>> futureTasks = new HashSet<>();
    Set<Boolean> tasks = new HashSet<>();
    try {
      vnfr =
          nfvoRequestor
              .getNetworkServiceRecordAgent()
              .getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
    } catch (SDKException e) {
      log.error("Error while requesting NSR " + nsr_id, e);
      return null;
    } catch (Exception e) {
      log.error("Error while using NfvoRequestor -> " + e.getMessage(), e);
      return null;
    }
    if (vnfr == null) {
      //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
      log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
      return new AsyncResult<>(false);
    }
    log.warn("Deactivating AlarmDetection for VNFR: " + vnfr);
    if (vnfr.getAuto_scale_policy() == null) {
      log.debug("No AutoScalePolicies defined for VNFR " + vnfr_id + ". So cannot stop them...");
      return new AsyncResult<>(false);
    }
    for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
      futureTasks.add(stop(projectId, nsr_id, vnfr_id, autoScalePolicy));
    }
    for (Future<Boolean> futureTask : futureTasks) {
      try {
        tasks.add(futureTask.get());
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
    log.info(
        "Deactivated Alarm Detection for VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
    if (tasks.contains(false)) {
      return new AsyncResult<>(false);
    }
    return new AsyncResult<>(true);
  }

  @Async
  public Future<Boolean> stop(String projectId, String nsr_id, VirtualNetworkFunctionRecord vnfr)
      throws NotFoundException {
    log.debug(
        "Deactivating Alarm Detection of VNFR with id: "
            + vnfr.getId()
            + " of NSR with id: "
            + nsr_id);
    Set<Future<Boolean>> futureTasks = new HashSet<>();
    Set<Boolean> tasks = new HashSet<>();
    if (vnfr == null) {
      //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
      log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr.getId());
      return new AsyncResult<>(false);
    }
    if (vnfr.getAuto_scale_policy() == null) {
      log.debug(
          "No AutoScalePolicies defined for VNFR " + vnfr.getId() + ". So cannot stop them...");
      return new AsyncResult<>(false);
    }
    for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
      futureTasks.add(stop(projectId, nsr_id, vnfr.getId(), autoScalePolicy));
    }
    for (Future<Boolean> futureTask : futureTasks) {
      try {
        tasks.add(futureTask.get());
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
    log.info(
        "Deactivated Alarm Detection for VNFR with id: "
            + vnfr.getId()
            + " of NSR with id: "
            + nsr_id);
    if (tasks.contains(false)) {
      return new AsyncResult<>(false);
    }
    return new AsyncResult<>(true);
  }

  @Async
  public Future<Boolean> stop(
      String projectId, String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
    log.debug(
        "Deactivating Alarm Detection for AutoScalePolicy with id: "
            + autoScalePolicy.getId()
            + " for VNFR with id"
            + vnfr_id);
    if (detectionTasks.containsKey(nsr_id)) {
      if (detectionTasks.get(nsr_id).containsKey(vnfr_id)) {
        if (detectionTasks.get(nsr_id).get(vnfr_id).containsKey(autoScalePolicy.getId())) {
          detectionTasks.get(nsr_id).get(vnfr_id).get(autoScalePolicy.getId()).cancel(false);
          int i = 30;
          while (!actionMonitor.isTerminated(autoScalePolicy.getId())
              && actionMonitor.getAction(autoScalePolicy.getId()) != Action.INACTIVE
              && i >= 0) {
            actionMonitor.terminate(autoScalePolicy.getId());
            log.debug(
                "Waiting for finishing DetectionTask for AutoScalePolicy with id: "
                    + autoScalePolicy.getId()
                    + " of VNFR with id: "
                    + vnfr_id
                    + " ("
                    + i
                    + "s)");
            //log.debug(actionMonitor.toString());
            if (i <= 0) {
              log.warn(
                  "Forced deactivation of DetectionTask for AutoScalePolicy with id: "
                      + autoScalePolicy.getId());
              detectionTasks.get(nsr_id).get(vnfr_id).get(autoScalePolicy.getId()).cancel(true);
              detectionTasks.get(nsr_id).get(vnfr_id).remove(autoScalePolicy.getId());
              actionMonitor.removeId(vnfr_id);
              return new AsyncResult<>(false);
            }
            try {
              Thread.sleep(1_000);
            } catch (InterruptedException e) {
              log.error(e.getMessage(), e);
            }
            i--;
          }
          actionMonitor.removeId(vnfr_id);
          detectionTasks.get(nsr_id).get(vnfr_id).remove(autoScalePolicy.getId());
          log.debug(
              "Deactivated Alarm Detection for AutoScalePolicy with id: "
                  + autoScalePolicy.getId()
                  + " of VNFR with id: "
                  + vnfr_id
                  + " of NSR with id: "
                  + nsr_id);
        } else {
          log.debug(
              "Not Found DetectionTask for AutoScalePolicy with id: "
                  + autoScalePolicy.getId()
                  + " of VNFR with id: "
                  + vnfr_id
                  + " of NSR with id: "
                  + nsr_id);
        }
      } else {
        log.debug(
            "Not Found any DetectionTasks for VNFR with id: "
                + vnfr_id
                + " of NSR with id: "
                + nsr_id);
      }
    } else {
      log.debug("Not Found any DetectionTasks for NSR with id: " + nsr_id);
    }
    return new AsyncResult<>(true);
  }

  public void sendAlarm(
      String projectId, String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
    log.info("Sending alarm to Decision-maker for VNFR with id: " + vnfr_id);
    if (actionMonitor.isTerminating(autoScalePolicy.getId())) {
      actionMonitor.finishedAction(autoScalePolicy.getId(), Action.TERMINATED);
      return;
    }
    decisionManagement.decide(projectId, nsr_id, autoScalePolicy);
  }
}
