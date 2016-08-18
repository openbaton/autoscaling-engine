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

package org.openbaton.autoscaling.core.decision;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.configuration.NfvoProperties;
import org.openbaton.autoscaling.core.decision.task.DecisionTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
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
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class DecisionManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DecisionEngine decisionEngine;

    private ThreadPoolTaskScheduler taskScheduler;

    private ActionMonitor actionMonitor;

    @Autowired
    private NfvoProperties nfvoProperties;

    @PostConstruct
    public void init() {
        this.actionMonitor = new ActionMonitor();
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

    public void decide(String projectId, String nsr_id, AutoScalePolicy autoScalePolicy) {
        //log.info("[DECISION_MAKER] DECISION_REQUESTED " + new Date().getTime());
        log.debug("Processing decision request of AutoScalePolicy with id " + autoScalePolicy.getId() + " of NSR with id: " + nsr_id);
        log.trace("Creating new DecisionTask for AutoScalePolicy with id " + autoScalePolicy.getId() + " of NSR with id: " + nsr_id);
        actionMonitor.requestAction(nsr_id, Action.DECIDE);
        DecisionTask decisionTask = new DecisionTask(projectId, nsr_id, autoScalePolicy, decisionEngine, actionMonitor);
        taskScheduler.execute(decisionTask);
    }

//    public void stop(String nsr_id) {
//        log.debug("Stopping DecisionTask for NSR with id: " + nsr_id);
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
//                stop(nsr_id, vnfr.getId());
//            }
//        }
//    }

    @Async
    public Future<Boolean> stop(String projectId, String nsr_id) {
        log.debug("Invoking termination of DecisionTask for NSR with id: " + nsr_id);
        actionMonitor.removeId(nsr_id);
        return new AsyncResult<Boolean>(true);
    }
}
