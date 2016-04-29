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
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ExecutionEngine {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ConfigurableApplicationContext context;

    private NFVORequestor nfvoRequestor;

    private ExecutionManagement executionManagement;

    private ActionMonitor actionMonitor;

    @Autowired
    private NfvoProperties nfvoProperties;

    @Autowired
    private AutoScalingProperties autoScalingProperties;

    @Autowired
    private SpringProperties springProperties;

    private MonitoringPluginCaller client;

    @PostConstruct
    public void init() {
        //this.resourceManagement = context.getBean(ResourceManagement.class);
        this.executionManagement = context.getBean(ExecutionManagement.class);
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
    }

    private MonitoringPluginCaller getClient() {
        return (MonitoringPluginCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getMonitoringPluginCaller(autoScalingProperties.getRabbitmq().getBrokerIp(), springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), springProperties.getRabbitmq().getPort(), "icinga-agent", "icinga", autoScalingProperties.getRabbitmq().getManagement().getPort());
    }

    public void setActionMonitor(ActionMonitor actionMonitor) {
        this.actionMonitor = actionMonitor;
    }

    public VirtualNetworkFunctionRecord scaleOut(VirtualNetworkFunctionRecord vnfr, int numberOfInstances) throws NotFoundException {
        log.debug("Executing scale-out for VNFR with id: " + vnfr.getId());
        for (int i = 1; i <= numberOfInstances; i++) {
            if (actionMonitor.isTerminating(vnfr.getId())) {
                actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                return vnfr;
            }
            log.debug("Adding new VNFCInstance -> number " + i);
            boolean scaled = false;
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (scaled == true) break;
                if (vdu.getVnfc_instance().size() < vdu.getScale_in_out() && (vdu.getVnfc().iterator().hasNext())) {
                    VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
                    try {
                        log.trace("Request NFVO to execute ScalingAction -> scale-out");
                        nfvoRequestor.getNetworkServiceRecordAgent().createVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfComponent);
                        log.trace("NFVO executed ScalingAction -> scale-out");
                        log.info("Added new VNFCInstance to VDU " + vdu.getId());
                        actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.SCALED);
                        scaled = true;
                        while (nfvoRequestor.getNetworkServiceRecordAgent().findById(vnfr.getParent_ns_id()).getStatus() == Status.SCALING) {
                            log.debug("Waiting for NFVO to finish the ScalingAction of VNFR " + vnfr.getId());
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                log.error(e.getMessage(), e);
                            }
                            if (actionMonitor.isTerminating(vnfr.getId())) {
                                actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                                return vnfr;
                            }
                        }
                        try {
                            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(vnfr.getParent_ns_id(), vnfr.getId());
                        } catch (SDKException e) {
                            log.error(e.getMessage(), e);
                            log.warn("Cannot execute ScalingAction. VNFR was not found or problems with the SDK");
                            actionMonitor.finishedAction(vnfr.getId());
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

    public void scaleOutTo(VirtualNetworkFunctionRecord vnfr, int value) throws SDKException, NotFoundException, VimException {
        int vnfci_counter = 0;
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            vnfci_counter += vdu.getVnfc_instance().size();
        }
        for (int i = vnfci_counter + 1; i <= value; i++) {
            scaleOut(vnfr, 1);
        }
    }

    public void scaleOutToFlavour(VirtualNetworkFunctionRecord vnfr, String flavour_id) throws SDKException, NotFoundException {
        throw new NotImplementedException();
    }

    public VirtualNetworkFunctionRecord scaleIn(VirtualNetworkFunctionRecord vnfr, int numberOfInstances) throws NotFoundException {
        log.debug("Executing scale-in for VNFR with id: " + vnfr.getId());
        for (int i = 1; i <= numberOfInstances; i++) {
            VNFCInstance vnfcInstance_remove = null;
            if (actionMonitor.isTerminating(vnfr.getId())) {
                actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                return vnfr;
            }
            log.debug("Removing VNFCInstance -> number " + i);
            boolean scaled = false;
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (scaled == true) break;
                if (vdu.getVnfc_instance().size() > 1 && vdu.getVnfc_instance().iterator().hasNext()) {
                    try {
                        log.trace("Request NFVO to execute ScalingAction -> scale-in");
                        nfvoRequestor.getNetworkServiceRecordAgent().deleteVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId());
                        log.trace("NFVO executed ScalingAction -> scale-in");
                        log.info("Removed VNFCInstance from VNFR " + vnfr.getId());
                        actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.SCALED);
                        scaled = true;
                        while (nfvoRequestor.getNetworkServiceRecordAgent().findById(vnfr.getParent_ns_id()).getStatus() == Status.SCALING) {
                            log.debug("Waiting for NFVO to finish the ScalingAction");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                log.error(e.getMessage(), e);
                            }
                            if (actionMonitor.isTerminating(vnfr.getId())) {
                                actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                                return vnfr;
                            }
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

    public void scaleInTo(VirtualNetworkFunctionRecord vnfr, int value) throws SDKException, NotFoundException, VimException {
        int vnfci_counter = 0;
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            vnfci_counter += vdu.getVnfc_instance().size();
        }
        for (int i = vnfci_counter; i > value; i--) {
            scaleIn(vnfr, 1);
        }
    }

    public void scaleInToFlavour(VirtualNetworkFunctionRecord vnfr, String flavour_id) throws SDKException, NotFoundException {
        throw new NotImplementedException();
    }

    public void startCooldown(String nsr_id, String vnfr_id, long cooldown) {
        List<String> vnfrIds = new ArrayList<>();
        vnfrIds.add(vnfr_id);
        executionManagement.executeCooldown(nsr_id, vnfr_id, cooldown);
    }

    public NFVORequestor getNfvoRequestor() {
        return nfvoRequestor;
    }

}