package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.messages.Interfaces.NFVMessage;
import org.openbaton.catalogue.nfvo.messages.OrVnfmGenericMessage;
import org.openbaton.catalogue.nfvo.messages.VnfmOrGenericMessage;
import org.openbaton.common.vnfm_sdk.VnfmHelper;
import org.openbaton.common.vnfm_sdk.amqp.VnfmSpringHelperRabbit;
import org.openbaton.common.vnfm_sdk.utils.VnfmUtils;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.openbaton.vnfm.catalogue.MediaServer;
import org.openbaton.vnfm.core.api.MediaServerManagement;
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

    private ResourceManagement resourceManagement;

    private Properties properties;

    @Autowired
    private ExecutionManagement executionManagement;

    @Autowired
    private PoolManagement poolManagement;

    @Autowired
    private VnfrMonitor vnfrMonitor;

    private VnfmHelper vnfmHelper;

    @Autowired
    private MediaServerManagement mediaServerManagement;

//    public ExecutionEngine(Properties properties) {
//        this.properties = properties;
//        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
//        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
//    }

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("nfvo.username"), properties.getProperty("nfvo.password"), properties.getProperty("nfvo.ip"), properties.getProperty("nfvo.port"), "1");
        this.resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
        this.vnfmHelper = (VnfmHelper) context.getBean("vnfmSpringHelperRabbit");
    }

    public void scaleOut(VirtualNetworkFunctionRecord vnfr) throws SDKException, NotFoundException, VimException, VimDriverException {
        //VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        VNFCInstance vnfcInstance = null;
        //vnfr.setStatus(Status.SCALING);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (properties.getProperty("autoscaling.pool.activate", "false").equals("false")) {
                if (vdu.getVnfc_instance().size() < vdu.getScale_in_out() && (vdu.getVnfc().iterator().hasNext())) {
                    VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
                    Map<String, String> floatgingIps = new HashMap<>();
                    for (VNFDConnectionPoint connectionPoint : vnfComponent.getConnection_point()) {
                        if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals(""))
                            floatgingIps.put(connectionPoint.getVirtual_link_reference(), connectionPoint.getFloatingIp());
                        try {
                            vnfcInstance = resourceManagement.allocate(vdu, vnfr, vnfComponent, "", floatgingIps).get();
                        } catch (InterruptedException e) {
                            log.warn(e.getMessage(), e);
                        } catch (ExecutionException e) {
                            log.warn(e.getMessage(), e);
                        }
                        //nfvoRequestor.getNetworkServiceRecordAgent().createVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfComponent_new);
                    }
                }
            } else {
                vnfcInstance = poolManagement.getReservedInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId());
            }
            if (vnfcInstance != null) {
                vdu.getVnfc_instance().add(vnfcInstance);
                log.debug("SCALING: Added new Component to VDU " + vdu.getId());
                break;
            }
        }
        if (vnfcInstance == null) {
            log.warn("Not found any VDU to scale out a VNFComponent. Limits are reached.");
            //throw new NotFoundException("Not found any VDU to scale out a VNFComponent. Limits are reached.");
        }
        //vnfr.setStatus(Status.ACTIVE);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        vnfr = updateVNFR(vnfr);
        //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            for (VNFCInstance vnfcInstance_new : vdu.getVnfc_instance()) {
                if (vnfcInstance_new.getHostname().equals(vnfcInstance.getHostname())) {
                    mediaServerManagement.add(vnfr.getId(), vnfcInstance);
                }
            }
        }
    }

    public void scaleOutTo(VirtualNetworkFunctionRecord vnfr, int value) throws SDKException, NotFoundException, VimException, VimDriverException {
        int vnfci_counter = 0;
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            vnfci_counter += vdu.getVnfc_instance().size();
        }
        for (int i = vnfci_counter + 1; i <= value; i++) {
            scaleOut(vnfr);
        }
    }

    public void scaleOutToFlavour(VirtualNetworkFunctionRecord vnfr, String flavour_id) throws SDKException, NotFoundException {
        throw new NotImplementedException();
    }

    public void scaleIn(VirtualNetworkFunctionRecord vnfr) throws SDKException, NotFoundException, VimException {
        //VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        VNFCInstance vnfcInstance_remove = null;
        //vnfr.setStatus(Status.SCALING);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (vdu.getVnfc_instance().size() > 1 && vdu.getVnfc_instance().iterator().hasNext()) {
                vnfcInstance_remove = vdu.getVnfc_instance().iterator().next();
                resourceManagement.release(vnfcInstance_remove, vdu.getVimInstance());
                //nfvoRequestor.getNetworkServiceRecordAgent().deleteVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfcInstance_remove.getId());
            }
            if (vnfcInstance_remove != null) {
                vdu.getVnfc_instance().remove((vnfcInstance_remove));
                log.debug("SCALING: Removed VNFCInstance " + vnfcInstance_remove.getId() + " from VDU " + vdu.getId());
                mediaServerManagement.delete(vnfr.getId(), vnfcInstance_remove.getHostname());
                break;
            }
        }
        if (vnfcInstance_remove == null) {
            log.warn("Not found any VDU to scale in a VNFComponent. Limits are reached.");
            //throw new NotFoundException("Not found any VDU to scale in a VNFComponent. Limits are reached.");
        }
        updateVNFR(vnfr);
    }

    public void scaleInTo(VirtualNetworkFunctionRecord vnfr, int value) throws SDKException, NotFoundException, VimException {
        int vnfci_counter = 0;
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            vnfci_counter += vdu.getVnfc_instance().size();
        }
        for (int i = vnfci_counter; i > value; i--) {
            scaleIn(vnfr);
        }
    }

    public void scaleInToFlavour(VirtualNetworkFunctionRecord vnfr, String flavour_id) throws SDKException, NotFoundException {
        throw new NotImplementedException();
    }

    public void waitForCooldown(String vnfr_id, long cooldown) {
        List<String> vnfrIds = new ArrayList<>();
        vnfrIds.add(vnfr_id);
        try {
            vnfrMonitor.startCooldown(vnfrIds);
            log.debug("Starting cooldown period (" + cooldown + "s) for VNFR: " + vnfr_id);
            Thread.sleep(cooldown * 1000);
            log.debug("Finished cooldown period (" + cooldown + "s) for VNFR: " + vnfr_id);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public VirtualNetworkFunctionRecord updateVNFRStatus(String nsr_id, String vnfr_id, Status status) throws SDKException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        vnfr.setStatus(status);
        return updateVNFR(vnfr);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
    }

    public boolean requestScaling(String vnfr_id) {
        List<String> vnfrIds = new ArrayList<>();
        vnfrIds.add(vnfr_id);
        return vnfrMonitor.requestScaling(vnfrIds);
    }

    public void finishedScaling(String vnfr_id) {
        List<String> vnfrIds = new ArrayList<>();
        vnfrIds.add(vnfr_id);
        vnfrMonitor.finishedScaling(vnfrIds);
        executionManagement.finish(vnfr_id);
    }

    public VirtualNetworkFunctionRecord updateVNFR(VirtualNetworkFunctionRecord vnfr) {
        OrVnfmGenericMessage response = null;
        try {
            response = (OrVnfmGenericMessage) vnfmHelper.sendAndReceive(VnfmUtils.getNfvMessage(Action.UPDATEVNFR, vnfr));
            //response = (OrVnfmGenericMessage) vnfmHelper.sendToNfvo(VnfmUtils.getNfvMessage(Action.UPDATEVNFR, vnfr));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr == null)
            return vnfr;
        return response.getVnfr();
    }
}
