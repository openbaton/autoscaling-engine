package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
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

//    public ExecutionEngine(Properties properties) {
//        this.properties = properties;
//        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
//        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
//    }

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
    }

    public void scaleOut(String nsr_id, String vnfr_id) throws SDKException, NotFoundException, VimException, VimDriverException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        VNFCInstance vnfcInstance = null;
        //vnfr.setStatus(Status.SCALING);
        //nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        //vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (properties.getProperty("pool_activated", "false").equals("false")) {
                if (vdu.getVnfc().size() < vdu.getScale_in_out() && (vdu.getVnfc().iterator().hasNext())) {
                    VNFComponent vnfComponent_copy = vdu.getVnfc().iterator().next();
                    VNFComponent vnfComponent_new = new VNFComponent();
                    vnfComponent_new.setConnection_point(new HashSet<VNFDConnectionPoint>());
                    for (VNFDConnectionPoint vnfdConnectionPoint_copy : vnfComponent_copy.getConnection_point()) {
                        VNFDConnectionPoint vnfdConnectionPoint_new = new VNFDConnectionPoint();
                        vnfdConnectionPoint_new.setVirtual_link_reference(vnfdConnectionPoint_copy.getVirtual_link_reference());
                        vnfdConnectionPoint_new.setType(vnfdConnectionPoint_copy.getType());
                        vnfdConnectionPoint_new.setFloatingIp(vnfdConnectionPoint_copy.getFloatingIp());
                        vnfComponent_new.getConnection_point().add(vnfdConnectionPoint_new);
                    }
                    Map<String, String> floatgingIps = new HashMap<>();
                    for (VNFDConnectionPoint connectionPoint : vnfComponent_new.getConnection_point()) {
                        if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals(""))
                            floatgingIps.put(connectionPoint.getVirtual_link_reference(), connectionPoint.getFloatingIp());
                        try {
                            vnfcInstance = resourceManagement.allocate(vdu, vnfr, vnfComponent_new, "", floatgingIps).get();
                        } catch (InterruptedException e) {
                            log.warn(e.getMessage(), e);
                        } catch (ExecutionException e) {
                            log.warn(e.getMessage(), e);
                        }
                        //nfvoRequestor.getNetworkServiceRecordAgent().createVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfComponent_new);
                    }
                }
            } else {
                vnfcInstance = poolManagement.getReservedInstance(nsr_id, vnfr_id, vdu.getId());
            }
            if (vnfcInstance != null) {
                vdu.getVnfc().add(vnfcInstance.getVnfComponent());
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
    }

    public void scaleOutTo(String nsr_id, String vnfr_id, int value) throws SDKException, NotFoundException, VimException, VimDriverException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        int vnfci_counter = 0;
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            vnfci_counter += vdu.getVnfc_instance().size();
        }
        for (int i = vnfci_counter + 1; i <= value; i++) {
            scaleOut(nsr_id, vnfr_id);
        }
    }

    public void scaleOutToFlavour(String nsr_id, String vnfr_id, String flavour_id) throws SDKException, NotFoundException {
        throw new NotImplementedException();
    }

    public void scaleIn(String nsr_id, String vnfr_id) throws SDKException, NotFoundException, VimException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        VNFCInstance vnfcInstance_remove = null;
        vnfr.setStatus(Status.SCALING);
        nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
        vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (vdu.getVnfc_instance().size() > 1 && vdu.getVnfc_instance().iterator().hasNext()) {
                vnfcInstance_remove = vdu.getVnfc_instance().iterator().next();
                resourceManagement.release(vnfcInstance_remove, vdu.getVimInstance());
                //nfvoRequestor.getNetworkServiceRecordAgent().deleteVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfcInstance_remove.getId());
            }
            if (vnfcInstance_remove != null) {
                vdu.getVnfc().remove(vnfcInstance_remove.getVnfComponent());
                vdu.getVnfc_instance().remove((vnfcInstance_remove));
                log.debug("SCALING: Removed VNFCInstance " + vnfcInstance_remove.getId() + " from VDU " + vdu.getId());
                break;
            }
        }
        if (vnfcInstance_remove == null) {
            log.warn("Not found any VDU to scale in a VNFComponent. Limits are reached.");
            //throw new NotFoundException("Not found any VDU to scale in a VNFComponent. Limits are reached.");
        }
        vnfr.setStatus(Status.ACTIVE);
        nfvoRequestor.getNetworkServiceRecordAgent().updateVNFR(nsr_id, vnfr_id, vnfr);
    }

    public void scaleInTo(String nsr_id, String vnfr_id, int value) throws SDKException, NotFoundException, VimException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        int vnfci_counter = 0;
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            vnfci_counter += vdu.getVnfc_instance().size();
        }
        for (int i = vnfci_counter; i > value; i--) {
            scaleIn(nsr_id, vnfr_id);
        }
    }

    public void scaleInToFlavour(String nsr_id, String vnfr_id, String flavour_id) throws SDKException, NotFoundException {
        throw new NotImplementedException();
    }

    public void finish(String vnfr_id) {
        executionManagement.finish(vnfr_id);
    }
}
