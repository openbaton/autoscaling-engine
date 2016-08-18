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

package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {ASBeanConfiguration.class})
public class ElasticityManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DetectionManagement detectionManagment;

    @Autowired
    private DecisionManagement decisionManagement;

    @Autowired
    private ExecutionManagement executionManagement;

    @PostConstruct
    public void init() throws SDKException {
    }

    @PreDestroy
    private void exit() throws SDKException {
    }

    public void activate(String projectId, String nsr_id) throws NotFoundException, VimException {
        log.debug("Activating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.start(projectId, nsr_id);
        log.info("Activated Elasticity for NSR with id: " + nsr_id);
    }

    public void activate(NetworkServiceRecord nsr) throws NotFoundException, VimException {
        log.debug("Activating Elasticity for NSR with id: " + nsr.getId());
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            for (AutoScalePolicy autoScalePolicy : vnfr.getAuto_scale_policy())
                detectionManagment.start(nsr.getProjectId(), nsr.getId(), vnfr.getId(), autoScalePolicy);
        }
        log.info("Activated Elasticity for NSR with id: " + nsr.getId());
    }

    @Async
    public void activate(String projectId, String nsr_id, String vnfr_id) throws NotFoundException, VimException {
        log.debug("Activating Elasticity for NSR with id: " + nsr_id);
        //log.info("[AUTOSCALING] Activating Elasticity " + System.currentTimeMillis());
        detectionManagment.start(projectId, nsr_id, vnfr_id);
        //log.info("[AUTOSCALING] Activated Elasticity " + System.currentTimeMillis());
        log.info("Activated Elasticity for NSR with id: " + nsr_id);
    }

    public void deactivate(String projectId, String nsr_id) {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        try {
            detectionManagment.stop(projectId, nsr_id);
        } catch (NotFoundException e) {
            log.warn(e.getMessage());
            if (log.isDebugEnabled()) {
                log.error(e.getMessage(), e);
            }
        }
        decisionManagement.stop(projectId, nsr_id);
        executionManagement.stop(projectId, nsr_id);
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
    }

    @Async
    public Future<Boolean> deactivate(String projectId, String nsr_id, String vnfr_id) {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        Set<Future<Boolean>> pendingTasks = new HashSet<>();
        try {
            pendingTasks.add(detectionManagment.stop(projectId, nsr_id, vnfr_id));
        } catch (NotFoundException e) {
            log.error(e.getMessage(), e);
        }
        pendingTasks.add(decisionManagement.stop(projectId, nsr_id));
        pendingTasks.add(executionManagement.stop(projectId, nsr_id));
        for (Future<Boolean> pendingTask : pendingTasks) {
            try {
                pendingTask.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (ExecutionException e) {
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (TimeoutException e) {
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
        return new AsyncResult<>(true);
    }

    @Async
    public Future<Boolean> deactivate(String projectId, String nsr_id, VirtualNetworkFunctionRecord vnfr) {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        Set<Future<Boolean>> pendingTasks = new HashSet<>();
        try {
            pendingTasks.add(detectionManagment.stop(projectId, nsr_id, vnfr));
        } catch (NotFoundException e) {
            log.error(e.getMessage(), e);
        }
        pendingTasks.add(decisionManagement.stop(projectId, nsr_id));
        pendingTasks.add(executionManagement.stop(projectId, nsr_id));
        for (Future<Boolean> pendingTask : pendingTasks) {
            try {
                pendingTask.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (ExecutionException e) {
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (TimeoutException e) {
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
        return new AsyncResult<>(true);
    }

}
