package org.openbaton.autoscaling.core.features.pool;

import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.autoscaling.core.features.pool.task.PoolTask;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

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
public class PoolManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ConfigurableApplicationContext context;

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> tasks;

    @Autowired
    private NFVORequestor nfvoRequestor;

    private ResourceManagement resourceManagement;

    private Map<String, Map<String, Map<String, Set<VNFCInstance>>>> reservedInstances;

    private Properties properties;

    private int pool_size;

    private int pool_check_period;

    //@PostConstruct
    public void init(Properties properties) {
        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
        this.properties = properties;
        this.pool_size = Integer.parseInt(properties.getProperty("pool_size"));
        this.pool_check_period = Integer.parseInt(properties.getProperty("pool_check_period"));
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
    }


    public void activate(String nsr_id) throws NotFoundException, VimDriverException, VimException {
        log.debug("Activating pool mechanism for VNFR " + nsr_id);
        log.info("AutoScaling: Pool Size for nsr with: " + nsr_id + " -> " + pool_size);
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
        //Prepare data structure
        Map<String, Map<String, Set<VNFCInstance>>> vnfrMap = new HashMap<String, Map<String, Set<VNFCInstance>>>();
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            vnfrMap.put(vnfr.getId(), new HashMap<String, Set<VNFCInstance>>());
            Map<String, Set<VNFCInstance>> vduMap = new HashMap<String, Set<VNFCInstance>>();
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                Set<VNFCInstance> vnfcInstances = new HashSet<>();
                for (int i = 1; i <= pool_size ; i++) {
                    vnfcInstances.add(allocateNewInstance(nsr, vnfr, vdu));
                }
                vduMap.put(vdu.getId(), vnfcInstances);
            }
            vnfrMap.put(vnfr.getId(), vduMap);
        }
        reservedInstances.put(nsr_id, vnfrMap);
        startPoolCheck(nsr_id);
    }

    public void deactivate(String nsr_id) throws NotFoundException, VimException {
        log.debug("Deactivating pool mechanism for NSR " + nsr_id);
        stopPoolCheck(nsr_id);
        releaseReservedInstances(nsr_id);
        log.debug("Deactivated pool mechanism for NSR " + nsr_id);
    }

    public void deactivate(NetworkServiceRecord nsr) throws NotFoundException, VimException {
        log.debug("Deactivating pool mechanism for NSR " + nsr.getId());
        stopPoolCheck(nsr.getId());
        releaseReservedInstances(nsr);
        log.debug("Deactivated pool mechanism for NSR " + nsr.getId());
    }

    public Map<String, Map<String, Set<VNFCInstance>>> getReservedInstances(String nsr_id) {
        if (reservedInstances.containsKey(nsr_id)) {
            if (reservedInstances.get(nsr_id) != null) {
                return reservedInstances.get(nsr_id);
            }
        } else {
            log.warn("Not found any reserved VNFCInstance for NSR with id: " + nsr_id);
        }
        return new HashMap<>();
    }

    public VNFCInstance allocateReservedInstance(String nsr_id, String vnfr_id, String vdu_id) throws NotFoundException, VimException {
        VNFCInstance returnedInstance = null;
        if (reservedInstances.containsKey(nsr_id)) {
            if (reservedInstances.get(nsr_id).containsKey(vnfr_id)) {
                if (reservedInstances.get(nsr_id).get(vnfr_id).containsKey(vdu_id)) {
                    if (reservedInstances.get(nsr_id).get(vnfr_id).get(vdu_id).iterator().hasNext()) {
                        returnedInstance = reservedInstances.get(nsr_id).get(vnfr_id).get(vdu_id).iterator().next();
                        reservedInstances.get(nsr_id).get(vnfr_id).get(vdu_id).remove(returnedInstance);
                    } else {
                        //Allocate new Instance if no one was found
                        returnedInstance = allocateNewInstance(nsr_id, vnfr_id, vdu_id);
                    }
                } else {
                    log.warn("Reserved instances for VDU with id: " + vdu_id + " were not initialized properly");
                }
            } else {
                log.warn("Reserved instances for VNFR with id: " + vnfr_id + " were not initialized properly");
            }
        } else {
            log.warn("Reserved instances for NSR with id: " + nsr_id + " were not initialized properly");
        }
        return returnedInstance;
    }

    public VNFCInstance allocateNewInstance(String nsr_id, String vnfr_id, String vdu_id) throws NotFoundException, VimException {
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
        return allocateNewInstance(nsr, vnfr, vdu);
    }

    public VNFCInstance allocateNewInstance(NetworkServiceRecord nsr, VirtualNetworkFunctionRecord vnfr, VirtualDeploymentUnit vdu) throws VimException {
        VNFCInstance vnfcInstance = null;
        if (vdu.getVnfc().iterator().hasNext()) {
            VNFComponent vnfComponentCopy = vdu.getVnfc().iterator().next();
            VNFComponent vnfComponentNew = new VNFComponent();
            vnfComponentNew.setConnection_point(new HashSet<VNFDConnectionPoint>());
            for (VNFDConnectionPoint vnfdConnectionPointCopy : vnfComponentCopy.getConnection_point()) {
                VNFDConnectionPoint vnfdConnectionPointNew = new VNFDConnectionPoint();
                vnfdConnectionPointNew.setFloatingIp(vnfdConnectionPointCopy.getFloatingIp());
                vnfdConnectionPointNew.setVirtual_link_reference(vnfdConnectionPointCopy.getVirtual_link_reference());
                vnfdConnectionPointNew.setType(vnfdConnectionPointCopy.getType());
                vnfComponentNew.getConnection_point().add(vnfdConnectionPointNew);
            }
            Map<String, String> floatgingIps = new HashMap<>();
            for (VNFDConnectionPoint connectionPoint : vnfComponentNew.getConnection_point()){
                if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals(""))
                    floatgingIps.put(connectionPoint.getVirtual_link_reference(),connectionPoint.getFloatingIp());
            }
            try {
                Future<VNFCInstance> vnfcInstanceFuture = resourceManagement.allocate(vdu, vnfr, vnfComponentNew, "", floatgingIps);
                vnfcInstance = vnfcInstanceFuture.get();
            } catch (VimException e) {
                log.error(e.getMessage(), e);
            } catch (VimDriverException e) {
                log.error(e.getMessage(), e);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
            return vnfcInstance;
        }
        throw new VimException("Not able to allocate new VNFCInstance for the Pool");
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
        //Find VNFR
        if (nsr != null) {
            releaseReservedInstances(nsr);
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
        if (reservedInstances.containsKey(nsr.getId())) {
            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
                releaseReservedInstances(nsr, vnfr);
            }
            reservedInstances.remove(nsr.getId());
        } else {
            log.warn("Not found any reserved Instances for NSR with id: " + nsr.getId());
        }
    }

    public void releaseReservedInstances(NetworkServiceRecord nsr, VirtualNetworkFunctionRecord vnfr) throws VimException {
        if (reservedInstances.containsKey(nsr.getId())) {
            if (reservedInstances.get(nsr.getId()).containsKey(vnfr.getId())) {
                for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                    releaseReservedInstances(nsr, vnfr, vdu);
                }
                reservedInstances.get(nsr.getId()).remove(vnfr.getId());
            } else {
                log.warn("Not found any reserved Instances for VNFR with id: " + vnfr.getId() + " of NSR with id: " + nsr.getId());
            }
        } else {
            log.warn("Not found any reserved Instances for NSR with id: " + nsr.getId());
        }

    }

    public void releaseReservedInstances(NetworkServiceRecord nsr, VirtualNetworkFunctionRecord vnfr, VirtualDeploymentUnit vdu) throws VimException {
        log.info("Releasing reserved Instances of NSR with id: " + nsr.getId() + " of VNFR with id: " + vnfr.getId() + " of VDU with id: " + vdu.getId());
        if (reservedInstances.containsKey(nsr.getId())) {
            if (reservedInstances.get(nsr.getId()).containsKey(vnfr.getId())) {
                if (reservedInstances.get(nsr.getId()).get(vnfr.getId()).containsKey(vdu.getId())) {
                    if (reservedInstances.get(nsr.getId()).get(vnfr.getId()).get(vdu.getId()) != null) {
                        Set<VNFCInstance> vnfcInstances = reservedInstances.get(nsr.getId()).get(vnfr.getId()).get(vdu.getId());
                        for (VNFCInstance vnfcInstance : vnfcInstances) {
                            resourceManagement.release(vnfcInstance, vdu.getVimInstance());
                        }
                        reservedInstances.get(nsr.getId()).get(vnfr.getId()).remove(vdu.getId());
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
    }

    public void startPoolCheck(String nsr_id) throws NotFoundException {
        log.debug("Activating Pool size checking for NSR with id: " + nsr_id);
        if (!tasks.containsKey(nsr_id)) {
            log.debug("Creating new PoolTask for NSR with id: " + nsr_id);
            PoolTask poolTask = new PoolTask(nsr_id, pool_size);
            ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(poolTask, pool_check_period * 1000);
            log.debug("Activated Pool size checking for NSR with id: " + nsr_id);
            tasks.put(nsr_id, scheduledFuture);
        } else {
            log.debug("Pool size checking of NSR with id: " + nsr_id + " were already activated");
        }
    }

    public void stopPoolCheck(String nsr_id) {
        log.debug("Activating Pool size checking for NSR with id: " + nsr_id);
        if (tasks.containsKey(nsr_id)) {
            tasks.get(nsr_id).cancel(true);
            tasks.remove(nsr_id);
        } else {
            log.debug("Not Found PoolTask for NSR with id: " + nsr_id);
        }
        log.debug("Deactivated Pool size checking for NSR with id: " + nsr_id);
    }


}
