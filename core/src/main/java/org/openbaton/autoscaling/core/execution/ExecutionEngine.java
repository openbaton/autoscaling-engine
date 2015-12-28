package org.openbaton.autoscaling.core.execution;

import org.openbaton.autoscaling.core.management.VnfrMonitor;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ExecutionEngine {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private NFVORequestor nfvoRequestor;

    private Properties properties;

    public ExecutionEngine(Properties properties) {
        this.properties = properties;
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
    }

    public void scaleOut(String nsr_id, String vnfr_id) throws SDKException, NotFoundException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
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
                nfvoRequestor.getNetworkServiceRecordAgent().createVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfComponent_new);
                log.debug("SCALING: Added new Component to VDU " + vdu.getId());
                return;
            } else {
                continue;
            }
        }
        //log.debug("Not found any VDU to scale out a VNFComponent. Limits are reached.");
        throw new NotFoundException("Not found any VDU to scale out a VNFComponent. Limits are reached.");

    }

    public void scaleOutTo(String nsr_id, String vnfr_id, int value) throws SDKException, NotFoundException {
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

    public void scaleIn(String nsr_id, String vnfr_id) throws SDKException, NotFoundException {
        VirtualNetworkFunctionRecord vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (vdu.getVnfc_instance().size() > 1 && vdu.getVnfc_instance().iterator().hasNext()) {
                VNFCInstance vnfcInstance_remove = vdu.getVnfc_instance().iterator().next();
                nfvoRequestor.getNetworkServiceRecordAgent().deleteVNFCInstance(vnfr.getParent_ns_id(), vnfr.getId(), vdu.getId(), vnfcInstance_remove.getId());
                log.debug("SCALING: Removed VNFCInstance " + vnfcInstance_remove.getId() + " from VDU " + vdu.getId());
                return;
            } else {
                continue;
            }
        }
        //log.debug("Not found any VDU to scale in a VNFComponent. Limits are reached.");
        throw new NotFoundException("Not found any VDU to scale in a VNFComponent. Limits are reached.");
    }

    public void scaleInTo(String nsr_id, String vnfr_id, int value) throws SDKException, NotFoundException {
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

}
