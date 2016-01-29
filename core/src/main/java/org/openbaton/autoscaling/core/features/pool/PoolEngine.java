package org.openbaton.autoscaling.core.features.pool;

import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimDriverException;
import org.openbaton.exceptions.VimException;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.NfvoProperties;
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
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class PoolEngine {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ConfigurableApplicationContext context;

    private NFVORequestor nfvoRequestor;

    @Autowired
    private MediaServerResourceManagement mediaServerResourceManagement;

    @Autowired
    private PoolManagement poolManagement;

    @Autowired
    private NfvoProperties nfvoProperties;

//    public PoolEngine(Properties properties) {
//        this.properties = properties;
//        this.resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
//        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
//    }

    @PostConstruct
    public void init() {
        //this.resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");    }

    public VNFCInstance allocateNewInstance(String nsr_id, String vnfr_id, String vdu_id) throws NotFoundException {
        VirtualNetworkFunctionRecord vnfr = null;
        VirtualDeploymentUnit vdu = null;
        //Find NSR
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        //Find VDU
        if (vnfr != null) {
            for (VirtualDeploymentUnit vduFind : vnfr.getVdu()) {
                if (vduFind.getId().equals(vdu_id)) {
                    vdu = vduFind;
                    break;
                }
            }
        } else {
            throw new NotFoundException("Not found VNFR with id: " + vnfr_id);
        }
        if (vdu == null) {
            throw new NotFoundException("Not found VDU with id: " + vdu_id);
        }
        return allocateNewInstance(nsr_id, vnfr, vdu);
    }

    public VNFCInstance allocateNewInstance(String nsr_id, VirtualNetworkFunctionRecord vnfr, VirtualDeploymentUnit vdu) throws NotFoundException {
        VNFCInstance vnfcInstance = null;
        Future<VNFCInstance> vnfcInstanceFuture = null;
        log.debug("Allocating new VNFCInstance on NSR::VNFR::VDU -> " + nsr_id + "::" + vnfr.getId() + "::" + vdu.getId());
        int reservedInstances = getNumberOfReservedInstances(nsr_id, vnfr.getId(), vdu.getId());
        if ((vdu.getVnfc_instance().size() + reservedInstances < vdu.getScale_in_out()) && vdu.getVnfc().iterator().hasNext()) {
            VimInstance vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), nfvoRequestor);
            VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
            try {
                vnfcInstanceFuture = mediaServerResourceManagement.allocate(vimInstance, vdu, vnfr, vnfComponent);
            } catch (VimException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            log.warn("Not able to allocate new VNFCInstance for the Pool. Maximum number of VNFCInstances for VDU with id: " + vdu.getId() + " is reached");
        }
        if (vnfcInstanceFuture != null) {
            try {
                vnfcInstance = vnfcInstanceFuture.get();
                log.debug("Allocated new VNFCInstance on NSR::VNFR::VDU -> " + nsr_id + "::" + vnfr.getId() + "::" + vdu.getId() + " -> " + vnfcInstance );
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            log.debug("Not able to allocate new VNFCInstance on NSR::VNFR::VDU -> " + nsr_id + "::" + vnfr.getId() + "::" + vdu.getId() + " -> " + vnfcInstance );
        }

        return vnfcInstance;
    }

    public Set<VNFCInstance> allocateNewInstance(String nsr_id, VirtualNetworkFunctionRecord vnfr, VirtualDeploymentUnit vdu, int numberOfInstances) throws NotFoundException {
        Set<VNFCInstance> vnfcInstances = new HashSet<>();
        Set<Future<VNFCInstance>> vnfcFutureInstances = new HashSet<>();
        log.debug("Allocating new VNFCInstance on NSR::VNFR::VDU -> " + nsr_id + "::" + vnfr.getId() + "::" + vdu.getId());
        int reservedInstances = getNumberOfReservedInstances(nsr_id, vnfr.getId(), vdu.getId());
        for (int i = 1; i <= numberOfInstances; i++) {
            if ((vdu.getVnfc_instance().size() + reservedInstances + i <= vdu.getScale_in_out()) && vdu.getVnfc().iterator().hasNext()) {
                VimInstance vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), nfvoRequestor);
                VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
                Future<VNFCInstance> vnfcInstanceFuture = null;
                try {
                    vnfcInstanceFuture = mediaServerResourceManagement.allocate(vimInstance, vdu, vnfr, vnfComponent);
                    vnfcFutureInstances.add(vnfcInstanceFuture);
                } catch (VimException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                log.warn("Not able to allocate new VNFCInstance for the Pool. Maximum number of VNFCInstances for VDU with id: " + vdu.getId() + " is reached");
            }
        }
        for (Future<VNFCInstance> vnfcFutureInstance : vnfcFutureInstances) {
            try {
                VNFCInstance vnfcInstance = vnfcFutureInstance.get();
                vnfcInstances.add(vnfcInstance);
                log.debug("Allocated new VNFCInstance on NSR::VNFR::VDU -> " + nsr_id + "::" + vnfr.getId() + "::" + vdu.getId() + " -> " + vnfcInstance);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
        }
        return vnfcInstances;
    }

    public void releaseReservedInstances(String nsr_id) throws NotFoundException, VimException {
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (nsr != null) {
            releaseReservedInstances(nsr);
        } else {
            throw new NotFoundException("Not found NSR with id: " + nsr_id);
        }
    }

    public void releaseReservedInstances(String nsr_id, String vnfr_id) throws NotFoundException, VimException {
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr != null) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                releaseReservedInstances(nsr_id, vnfr_id, vdu.getId());
            }
        } else {
            throw new NotFoundException("Not found NSR with id: " + nsr_id);
        }
    }

    public void releaseReservedInstances(String nsr_id, String vnfr_id, String vdu_id) throws NotFoundException, VimException {
        NetworkServiceRecord nsr = null;
        VirtualNetworkFunctionRecord vnfr = null;
        VirtualDeploymentUnit vdu = null;
        //Find NSR
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        //Find VNFR
        if (nsr != null) {
            for (VirtualNetworkFunctionRecord vnfrFind : nsr.getVnfr()) {
                if (vnfrFind.getId().equals(vnfr_id)) {
                    vnfr = vnfrFind;
                    break;
                }
            }
        } else {
            throw new NotFoundException("Not found NSR with id: " + nsr_id);
        }
        //Find VDU
        if (vnfr != null) {
            for (VirtualDeploymentUnit vduFind : vnfr.getVdu()) {
                if (vduFind.getId().equals(vdu_id)) {
                    vdu = vduFind;
                    break;
                }
            }
        } else {
            throw new NotFoundException("Not found VNFR with id: " + vnfr_id);
        }
        if (vdu == null) {
            throw new NotFoundException("Not found VDU with id: " + vdu_id);
        }
        releaseReservedInstances(nsr, vnfr, vdu);
    }

    public void releaseReservedInstances(NetworkServiceRecord nsr) throws NotFoundException, VimException {
        if (!poolManagement.getReservedInstances(nsr.getId()).isEmpty()) {
            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
                releaseReservedInstances(nsr, vnfr);
            }
            poolManagement.removeReservedInstances(nsr.getId());
        } else {
            log.warn("Not found any reserved Instances for NSR with id: " + nsr.getId());
        }
    }

    public void releaseReservedInstances(NetworkServiceRecord nsr, VirtualNetworkFunctionRecord vnfr) throws VimException {
        if (!poolManagement.getReservedInstances(nsr.getId()).isEmpty()) {
            if (poolManagement.getReservedInstances(nsr.getId()).containsKey(vnfr.getId())) {
                for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                    try {
                        releaseReservedInstances(nsr, vnfr, vdu);
                    } catch (NotFoundException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                poolManagement.getReservedInstances(nsr.getId()).remove(vnfr.getId());
            } else {
                log.warn("Not found any reserved Instances for VNFR with id: " + vnfr.getId() + " of NSR with id: " + nsr.getId());
            }
        } else {
            log.warn("Not found any reserved Instances for NSR with id: " + nsr.getId());
        }

    }

    public void releaseReservedInstances(NetworkServiceRecord nsr, VirtualNetworkFunctionRecord vnfr, VirtualDeploymentUnit vdu) throws VimException, NotFoundException {
        log.info("Releasing reserved Instances of NSR with id: " + nsr.getId() + " of VNFR with id: " + vnfr.getId() + " of VDU with id: " + vdu.getId());
        Set<Future<Void>> releasingInstances = new HashSet<>();
        if (!poolManagement.getReservedInstances(nsr.getId()).isEmpty()) {
            if (poolManagement.getReservedInstances(nsr.getId()).containsKey(vnfr.getId())) {
                if (poolManagement.getReservedInstances(nsr.getId()).get(vnfr.getId()).containsKey(vdu.getId())) {
                    if (poolManagement.getReservedInstances(nsr.getId()).get(vnfr.getId()).get(vdu.getId()) != null) {
                        Set<VNFCInstance> vnfcInstances = poolManagement.getReservedInstances(nsr.getId()).get(vnfr.getId()).get(vdu.getId());
                        VimInstance vimInstance = Utils.getVimInstance(vdu.getVimInstanceName(), nfvoRequestor);
                        for (VNFCInstance vnfcInstance : vnfcInstances) {
                            releasingInstances.add(mediaServerResourceManagement.release(vnfcInstance, vimInstance));
                        }
                        poolManagement.getReservedInstances(nsr.getId()).get(vnfr.getId()).remove(vdu.getId());
                    }
                } else {
                    log.warn("Not found any reserved Instances for VDU with id: " + vdu.getId() + " of VNFR with id: " + vnfr.getId() + " of NSR with id: " + nsr.getId());
                }
            } else {
                log.warn("Not found any reserved Instances for VNFR with id: " + vnfr.getId() + " of NSR with id: " + nsr.getId());
            }
        } else {
            log.warn("Not found any reserved Instances for NSR with id: " + nsr.getId());
        }
        for (Future<Void> releasingInstance : releasingInstances) {
            try {
                releasingInstance.get();
                log.debug("Removed reserved VNFInstance");
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public Map<String, Map<String, Set<VNFCInstance>>> getReservedInstances(String nsr_id) {
        return poolManagement.getReservedInstances(nsr_id);
    }

    public int getNumberOfReservedInstances(String nsr_id, String vnfr_id, String vdu_id) {
        Map<String, Map<String, Set<VNFCInstance>>> reservedInstances = getReservedInstances(nsr_id);
        if (reservedInstances.containsKey(vnfr_id)) {
            if (reservedInstances.get(vnfr_id).containsKey(vdu_id)) {
                return reservedInstances.get(vnfr_id).get(vdu_id).size();
            }
        }
        return 0;
    }
}
