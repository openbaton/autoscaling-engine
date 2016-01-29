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

package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.nfvo.messages.OrVnfmGenericMessage;
import org.openbaton.common.vnfm_sdk.VnfmHelper;
import org.openbaton.common.vnfm_sdk.utils.VnfmUtils;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimDriverException;
import org.openbaton.exceptions.VimException;
import org.openbaton.monitoring.interfaces.MonitoringPluginCaller;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.VimDriverCaller;
import org.openbaton.vnfm.configuration.*;
import org.openbaton.vnfm.core.api.MediaServerManagement;
import org.openbaton.vnfm.core.api.MediaServerResourceManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;

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

    @Autowired
    private MediaServerResourceManagement mediaServerResourceManagement;

    @Autowired
    private ExecutionManagement executionManagement;

    @Autowired
    private PoolManagement poolManagement;

    private ActionMonitor actionMonitor;

    private VnfmHelper vnfmHelper;

    @Autowired
    private MediaServerManagement mediaServerManagement;

    @Autowired
    private NfvoProperties nfvoProperties;

    @Autowired
    private AutoScalingProperties autoScalingProperties;

    @Autowired
    private SpringProperties springProperties;

    @Autowired
    private VnfmProperties vnfmProperties;

    private MonitoringPluginCaller client;


//    public ExecutionEngine(Properties properties) {
//        this.properties = properties;
//        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
//        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
//    }

    @PostConstruct
    public void init() {
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
        //this.resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
        this.vnfmHelper = (VnfmHelper) context.getBean("vnfmSpringHelperRabbit");
    }

    private MonitoringPluginCaller getClient() {
        return (MonitoringPluginCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getMonitoringPluginCaller(vnfmProperties.getRabbitmq().getBrokerIp(), springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), springProperties.getRabbitmq().getPort(),"icinga-agent", "icinga", vnfmProperties.getRabbitmq().getManagement().getPort());
    }

    public void setActionMonitor(ActionMonitor actionMonitor) {
        this.actionMonitor = actionMonitor;
    }

    public VirtualNetworkFunctionRecord scaleOut(VirtualNetworkFunctionRecord vnfr, int numberOfInstances) throws SDKException, NotFoundException {
        //VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        //vnfr.setStatus(Status.SCALE);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (int i = 1; i <= numberOfInstances ; i++) {
            if (actionMonitor.isTerminating(vnfr.getId())) {
                actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                return vnfr;
            }
            VNFCInstance vnfcInstance = null;
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (autoScalingProperties.getPool().isActivate()) {
                    log.debug("Getting VNFCInstance from pool");
                    vnfcInstance = poolManagement.getReservedInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId());
                    if (vnfcInstance != null) {
                      log.debug("Got VNFCInstance from pool -> " + vnfcInstance);
                    } else {
                      log.debug("No VNFCInstance available in pool");
                    }
                } else {
                    log.debug("Pool is deactivated");
                }
                if (vnfcInstance == null) {
                    VimInstance vimInstance = null;
                    if (vdu.getVnfc_instance().size() < vdu.getScale_in_out() && (vdu.getVnfc().iterator().hasNext())) {
                        if (vimInstance == null) {
                            vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), nfvoRequestor);
                        }
                        VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
                        try {
                            vnfcInstance = mediaServerResourceManagement.allocate(vimInstance, vdu, vnfr, vnfComponent).get();
                        } catch (InterruptedException e) {
                            log.warn(e.getMessage(), e);
                        } catch (ExecutionException e) {
                            log.warn(e.getMessage(), e);
                        } catch (VimException e) {
                            log.warn(e.getMessage(), e);
                        }
                        //nfvoRequestor.getNetworkServiceRecordAgent().createVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfComponent_new);

                    } else {
                        log.warn("Maximum size of VDU with id: " + vdu.getId() + " reached...");
                    }
                }
                if (vnfcInstance != null) {
                    vdu.getVnfc_instance().add(vnfcInstance);
                    log.debug("SCALE: Added new Component to VDU " + vdu.getId());
                    actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.SCALED);
                    break;
                }
            }
            if (vnfcInstance == null) {
                log.warn("Not found any VDU to scale out a VNFComponent. Limits are reached.");
                return vnfr;
                //throw new NotFoundException("Not found any VDU to scale out a VNFComponent. Limits are reached.");
            }
            //vnfr.setStatus(Status.ACTIVE);
            //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
            vnfr = updateVNFR(vnfr);
            //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                for (VNFCInstance vnfcInstance_new : vdu.getVnfc_instance()) {
                    if (vnfcInstance_new.getHostname().equals(vnfcInstance.getHostname())) {
                        mediaServerManagement.add(vnfr.getId(), vnfcInstance_new);
                    }
                }
            }
        }
        return vnfr;
    }

    public void scaleOutTo(VirtualNetworkFunctionRecord vnfr, int value) throws SDKException, NotFoundException, VimException, VimDriverException {
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

    public VirtualNetworkFunctionRecord scaleIn(VirtualNetworkFunctionRecord vnfr, int numberOfInstances) throws SDKException, NotFoundException, VimException {
        //VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        //vnfr.setStatus(Status.SCALE);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (int i = 1; i <= numberOfInstances; i++) {
            VNFCInstance vnfcInstance_remove = null;
            if (actionMonitor.isTerminating(vnfr.getId())) {
                actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.TERMINATED);
                return vnfr;
            }
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                VimInstance vimInstance = null;
                if (vdu.getVnfc_instance().size() > 1 && vdu.getVnfc_instance().iterator().hasNext()) {
                    if (vimInstance == null) {
                        vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), nfvoRequestor);
                    }
                    if (autoScalingProperties.getTerminationRule().isActivate()) {
                        if (client==null) client = getClient();
                        log.debug("Search for VNFCInstance that meets the termination rule");
                        List<String> hostnames = new ArrayList<>();
                        for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                            hostnames.add(vnfcInstance.getHostname());
                        }
                        List<String> metrics = new ArrayList<>();
                        metrics.add(autoScalingProperties.getTerminationRule().getMetric());
                        List<Item> items = null;
                        try {
                            items = client.queryPMJob(hostnames, metrics, "15");
                        } catch (MonitoringException e) {
                            log.error(e.getMessage(), e);
                        }
                        log.debug("Processing measurement results...");
                        for (Item item : items) {
                            if (item.getLastValue().equals(autoScalingProperties.getTerminationRule().getValue())) {
                                log.debug("Found VNFCInstance that meets termination-rule.");
                                vnfcInstance_remove = vdu.getVnfc_instance().iterator().next();
                                break;
                            }
                        }
                    } else {
                        log.debug("Scale-in the first VNFCInstance found");
                        vnfcInstance_remove = vdu.getVnfc_instance().iterator().next();
                    }
                    //nfvoRequestor.getNetworkServiceRecordAgent().deleteVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfcInstance_remove.getId());
                }
                if (vnfcInstance_remove != null) {
                    mediaServerResourceManagement.release(vnfcInstance_remove, vimInstance);
                    vdu.getVnfc_instance().remove((vnfcInstance_remove));
                    for (Ip ip : vnfcInstance_remove.getIps()) {
                        vnfr.getVnf_address().remove(ip.getIp());
                    }
                    for (Ip ip : vnfcInstance_remove.getFloatingIps()) {
                        vnfr.getVnf_address().remove(ip.getIp());
                    }
                    actionMonitor.finishedAction(vnfr.getId(), org.openbaton.autoscaling.catalogue.Action.SCALED);
                    log.debug("Removed VNFCInstance " + vnfcInstance_remove.getId() + " from VDU " + vdu.getId());
                    mediaServerManagement.delete(vnfr.getId(), vnfcInstance_remove.getHostname());
                    break;
                } else {
                    log.debug("Not found VNFCInstance in VDU with id: " + vdu.getId() + "to scale in");
                }
            }
            if (vnfcInstance_remove == null) {
                log.warn("Not found any VDU to scale in a VNFInstance.");
                //throw new NotFoundException("Not found any VDU to scale in a VNFComponent. Limits are reached.");
            } else {
                vnfr = updateVNFR(vnfr);
            }
        }
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
//        List<String> vnfrIds = new ArrayList<>();
//        vnfrIds.add(vnfr_id);
//        try {
//            vnfrMonitor.startCooldown(vnfrIds);
//            log.debug("Starting cooldown period (" + cooldown + "s) for VNFR: " + vnfr_id);
//            Thread.sleep(cooldown * 1000);
//            log.debug("Finished cooldown period (" + cooldown + "s) for VNFR: " + vnfr_id);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

    public VirtualNetworkFunctionRecord updateVNFRStatus(String nsr_id, String vnfr_id, Status status) throws SDKException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        vnfr.setStatus(status);
        return updateVNFR(vnfr);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
    }

    public VirtualNetworkFunctionRecord updateVNFR(VirtualNetworkFunctionRecord vnfr) {
        OrVnfmGenericMessage response = null;
        log.debug("Updating VNFR on NFVO: " + vnfr);
        try {
            response = (OrVnfmGenericMessage) vnfmHelper.sendAndReceive(VnfmUtils.getNfvMessage(Action.UPDATEVNFR, vnfr));
            //response = (OrVnfmGenericMessage) vnfmHelper.sendToNfvo(VnfmUtils.getNfvMessage(Action.UPDATEVNFR, vnfr));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (response.getVnfr() == null) {
            log.error("Problems while updating VNFR on NFVO. Returned VNFR is null.");
            return vnfr;
        } else {
            vnfr = response.getVnfr();
        }
        log.debug("Updated VNFR on NFVO: " + vnfr);
        return vnfr;
    }

}
