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

package org.openbaton.autoscaling.core.detection;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;


import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.NfvoProperties;
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
import java.util.*;
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

    private NFVORequestor nfvoRequestor;

    @Autowired
    private DetectionEngine detectionEngine;

    @Autowired
    private DecisionManagement decisionManagement;

    private ActionMonitor actionMonitor;

    @Autowired
    private NfvoProperties nfvoProperties;

    @PostConstruct
    public void init() {
        this.actionMonitor = new ActionMonitor();
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
        this.detectionTasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setRemoveOnCancelPolicy(true);
        this.taskScheduler.setErrorHandler(new ErrorHandler() {
            protected Logger log = LoggerFactory.getLogger(this.getClass());

            @Override
            public void handleError(Throwable t) {
                log.error(t.getMessage(), t);
            }
        });
        this.taskScheduler.initialize();
    }

    public void start(String nsr_id) throws NotFoundException {
        log.debug("Activating Alarm Detection for NSR with id: " + nsr_id);
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (nsr == null) {
            throw new NotFoundException("Not Found NetworkServiceDescriptor with id: " + nsr_id);
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
                start(nsr_id, vnfr.getId(), autoScalePolicy);
            }
        }
        log.info("Activated Alarm Detection for NSR with id: " + nsr_id);
    }

    public void start(String nsr_id, String vnfr_id) throws NotFoundException {
        log.debug("Activating Alarm Detection for VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr == null) {
            //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            return;
        }
        for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
                    start(nsr_id, vnfr.getId(), autoScalePolicy);
        }
        log.debug("Activated Alarm Detection for VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
    }

    public void start(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) throws NotFoundException {
        log.debug("Activating Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        if (actionMonitor.requestAction(autoScalePolicy.getId(), Action.INACTIVE)) {
            if (!detectionTasks.containsKey(nsr_id)) {
                detectionTasks.put(nsr_id, new HashMap<String, Map<String, ScheduledFuture>>());
            }
            if (!detectionTasks.get(nsr_id).containsKey(vnfr_id)) {
                detectionTasks.get(nsr_id).put(vnfr_id, new HashMap<String, ScheduledFuture>());
            }
            if (detectionTasks.get(nsr_id).get(vnfr_id).containsKey(autoScalePolicy.getId())) {
                log.debug("Restarting Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
                detectionTasks.get(nsr_id).get(vnfr_id).get(autoScalePolicy.getId()).cancel(false);
            }
            log.debug("Creating new DetectionTask for AutoScalingPolicy " + autoScalePolicy.getName() + " with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);
            DetectionTask detectionTask = new DetectionTask(nsr_id, vnfr_id, autoScalePolicy, detectionEngine, nfvoProperties, actionMonitor);
            ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(detectionTask, autoScalePolicy.getPeriod() * 1000);
            detectionTasks.get(nsr_id).get(vnfr_id).put(autoScalePolicy.getId(), scheduledFuture);
            log.info("Activated Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id);
        } else {
            log.debug("Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR " + vnfr_id + " of NSR with id: " + nsr_id + " were already activated");
        }
    }

    public void stop(String nsr_id) throws NotFoundException {
        log.debug("Deactivating Alarm Detection of NSR with id: " + nsr_id);
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            stop(nsr_id, vnfr.getId());
        }
        log.info("Deactivated Alarm Detection of NSR with id: " + nsr_id);
    }

    @Async
    public Future<Boolean> stop(String nsr_id, String vnfr_id) throws NotFoundException {
        log.debug("Deactivating Alarm Detection of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        VirtualNetworkFunctionRecord vnfr = null;
        Set<Future<Boolean>> futureTasks = new HashSet<>();
        Set<Boolean> tasks = new HashSet<>();
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr == null) {
            //throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            log.warn("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
            return new AsyncResult<>(false);
        }
        for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy()) {
            futureTasks.add(stop(nsr_id, vnfr_id, autoScalePolicy));
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
        log.debug("Deactivated Alarm Detection for VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        if (tasks.contains(false)) {
            return new AsyncResult<>(false);
        }
        return new AsyncResult<>(true);
    }

    @Async
    public Future<Boolean> stop(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
        log.debug("Deactivating Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " for VNFR with id" + vnfr_id);
        if (detectionTasks.containsKey(nsr_id)) {
            if (detectionTasks.get(nsr_id).containsKey(vnfr_id)) {
                if (detectionTasks.get(nsr_id).get(vnfr_id).containsKey(autoScalePolicy.getId())) {
                    detectionTasks.get(nsr_id).get(vnfr_id).get(autoScalePolicy.getId()).cancel(false);
                    int i = 60;
                    while (!actionMonitor.isTerminated(autoScalePolicy.getId()) && actionMonitor.getAction(autoScalePolicy.getId()) != Action.INACTIVE && i >= 0) {
                        actionMonitor.terminate(autoScalePolicy.getId());
                        log.debug("Waiting for finishing DetectionTask for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id);

                        log.debug("Waiting for finishing ExecutionTask/Cooldown for VNFR with id: " + vnfr_id + " (" + i + "s)");
                        log.debug(actionMonitor.toString());
                        if (i <= 0) {
                            log.error("Forced deactivation of DetectionTask for AutoScalePolicy with id: " + autoScalePolicy.getId());
                            detectionTasks.get(nsr_id).get(vnfr_id).get(autoScalePolicy.getId()).cancel(true);
                            detectionTasks.get(nsr_id).get(vnfr_id).remove(autoScalePolicy.getId());
                            return new AsyncResult<>(false);
                        }
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException e) {
                            log.error(e.getMessage(), e);
                        }
                        i--;
                    }
                    detectionTasks.get(nsr_id).get(vnfr_id).remove(autoScalePolicy.getId());
                    log.debug("Deactivated Alarm Detection for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
                } else {
                    log.debug("Not Found DetectionTask for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
                }
            } else {
                log.debug("Not Found any DetectionTasks for VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
            }
        } else {
            log.debug("Not Found any DetectionTasks for NSR with id: " + nsr_id);
        }
        return new AsyncResult<>(true);
    }

    public void sendAlarm(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy) {
        if (actionMonitor.isTerminating(autoScalePolicy.getId())) {
            actionMonitor.finishedAction(autoScalePolicy.getId(), Action.TERMINATED);
            return;
        }
        decisionManagement.decide(nsr_id, vnfr_id, autoScalePolicy);
    }

}
