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

package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.AutoScalingProperties;
import org.openbaton.vnfm.configuration.NfvoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ElasticityManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DetectionManagement detectionManagment;

    @Autowired
    private DecisionManagement decisionManagement;

    @Autowired
    private ExecutionManagement executionManagement;

    @Autowired
    private PoolManagement poolManagement;

    private NFVORequestor nfvoRequestor;

    private List<String> subscriptionIds;

    @Autowired
    private NfvoProperties nfvoProperties;

    @Autowired
    private AutoScalingProperties autoScalingProperties;

    @PostConstruct
    public void init() throws SDKException {
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
//        detectionManagment.init(properties);
//        decisionManagement.init(properties);
//        executionManagement.init(properties);
//        if (properties.getProperty("pool_activated").equals(true)) {
//            poolManagement = new PoolManagement();
//            poolManagement.init(properties);
//        }

        subscriptionIds = new ArrayList<>();
        //startPlugins();

        //waitForNfvo();
        //subscribe(Action.INSTANTIATE_FINISH);
        //subscribe(Action.RELEASE_RESOURCES_FINISH);
        //subscribe(Action.ERROR);

        //fetchNSRsFromNFVO();
    }

    @PreDestroy
    private void exit() throws SDKException {
        //unsubscribe();
        //destroyPlugins();
    }

    public void activate(String nsr_id) throws NotFoundException, VimException {
        log.debug("Activating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.start(nsr_id);
        if (autoScalingProperties.getPool().isActivate()) {
            log.debug("Activating pool mechanism");
            poolManagement.activate(nsr_id);
        } else {
            log.debug("pool mechanism is disabled");
        }
        log.info("Activated Elasticity for NSR with id: " + nsr_id);
    }

    @Async
    public void activate(String nsr_id, String vnfr_id) throws NotFoundException, VimException {
        log.debug("Activating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.start(nsr_id, vnfr_id);
        if (autoScalingProperties.getPool().isActivate()) {
            log.debug("Activating pool mechanism");
            poolManagement.activate(nsr_id, vnfr_id);
        } else {
            log.debug("pool mechanism is disabled");
        }
        log.info("Activated Elasticity for NSR with id: " + nsr_id);
    }

    public void deactivate(String nsr_id) {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        if (autoScalingProperties.getPool().isActivate()) {
            try {
                poolManagement.deactivate(nsr_id);
            } catch (NotFoundException e) {
                log.warn(e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (VimException e) {
                log.warn(e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        try {
            detectionManagment.stop(nsr_id);
        } catch (NotFoundException e) {
            log.warn(e.getMessage());
            if (log.isDebugEnabled()) {
                log.error(e.getMessage(), e);
            }
        }
        decisionManagement.stop(nsr_id);
        executionManagement.stop(nsr_id);
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
    }

    @Async
    public Future<Boolean> deactivate(String nsr_id, String vnfr_id) {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        if (autoScalingProperties.getPool().isActivate()) {
            try {
                poolManagement.deactivate(nsr_id, vnfr_id);
            } catch (NotFoundException e) {
                log.warn(e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            } catch (VimException e) {
                log.warn(e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        try {
            detectionManagment.stop(nsr_id, vnfr_id);
        } catch (NotFoundException e) {
            log.error(e.getMessage(), e);
        }
        decisionManagement.stop(nsr_id, vnfr_id);
        executionManagement.stop(nsr_id, vnfr_id);
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
        return new AsyncResult<>(true);
    }

    private void subscribe(Action action) throws SDKException {
        log.debug("Subscribing to all NSR Events with Action " + action);
        EventEndpoint eventEndpoint = new EventEndpoint();
        eventEndpoint.setName("Subscription:" + action);
        eventEndpoint.setEndpoint("http://localhost:9999/event/" + action);
        eventEndpoint.setEvent(action);
        eventEndpoint.setType(EndpointType.REST);
        this.subscriptionIds.add(nfvoRequestor.getEventAgent().create(eventEndpoint).getId());
    }

    private void unsubscribe() throws SDKException {
        for (String subscriptionId : subscriptionIds) {
            nfvoRequestor.getEventAgent().delete(subscriptionId);
        }
    }

    private void waitForNfvo() {
        if (!Utils.isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
            log.error("After 150 sec the Nfvo is not started yet. Is there an error?");
            System.exit(1); // 1 stands for the error in running nfvo TODO define error codes (doing)
        }
    }

}
