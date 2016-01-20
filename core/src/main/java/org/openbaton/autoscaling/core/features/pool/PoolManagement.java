package org.openbaton.autoscaling.core.features.pool;

import org.openbaton.autoscaling.core.features.pool.task.PoolTask;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class PoolManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> tasks;

    private NFVORequestor nfvoRequestor;

    private Map<String, Map<String, Map<String, Set<VNFCInstance>>>> reservedInstances;

    private Properties properties;

    private int pool_size;

    private int pool_check_period;

    @Autowired
    private PoolEngine poolEngine;

    @PostConstruct
    public void init() {
        this.properties = Utils.loadProperties();
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("nfvo.username"), properties.getProperty("nfvo.password"), properties.getProperty("nfvo.ip"), properties.getProperty("nfvo.port"), "1");
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();

        this.pool_size = Integer.parseInt(properties.getProperty("autoscaling.pool.size"));
        this.pool_check_period = Integer.parseInt(properties.getProperty("autoscaling.pool.period"));
        //poolEngine = new PoolEngine(properties);

        reservedInstances = new HashMap<>();
    }


    public void activate(String nsr_id) throws NotFoundException {
        log.debug("Activating pool mechanism for NSR " + nsr_id);
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
        Map<String, Map<String, Set<VNFCInstance>>> vnfrMap;
        if (reservedInstances.containsKey(nsr_id)) {
            vnfrMap = reservedInstances.get(nsr_id);
        } else {
            vnfrMap = new HashMap<String, Map<String, Set<VNFCInstance>>>();
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            Map<String, Set<VNFCInstance>> vduMap;
            if (vnfrMap.containsKey(vnfr.getId())) {
                vduMap = vnfrMap.get(vnfr.getId());
            } else {
                vduMap = new HashMap<String, Set<VNFCInstance>>();
            }
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                Set<VNFCInstance> vnfcInstances;
                if (vduMap.containsKey(vdu.getId())) {
                    vnfcInstances = vduMap.get(vdu.getId());
                } else {
                    vnfcInstances = new HashSet<>();
                }
                if (properties.getProperty("autoscaling.pool.prepare", "false").equals("true")) {
                    for (int i = 1; i <= pool_size; i++) {
                        try {
                            vnfcInstances.add(poolEngine.allocateNewInstance(nsr, vnfr, vdu));
                        } catch (VimException e) {
                            log.warn(e.getMessage(), e);
                        }
                    }
                }
                vduMap.put(vdu.getId(), vnfcInstances);
            }
            vnfrMap.put(vnfr.getId(), vduMap);
        }
        reservedInstances.put(nsr_id, vnfrMap);
        startPoolCheck(nsr_id);
    }

    public void activate(String nsr_id, String vnfr_id) throws NotFoundException {
        log.debug("Activating pool mechanism for VNFR " + vnfr_id);
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
        Map<String, Map<String, Set<VNFCInstance>>> vnfrMap;
        if (reservedInstances.containsKey(nsr_id)) {
            vnfrMap = reservedInstances.get(nsr_id);
        } else {
            vnfrMap = new HashMap<String, Map<String, Set<VNFCInstance>>>();
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            Map<String, Set<VNFCInstance>> vduMap;
            if (vnfrMap.containsKey(vnfr_id)) {
                vduMap = vnfrMap.get(vnfr_id);
            } else {
                vduMap = new HashMap<String, Set<VNFCInstance>>();
            }
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                Set<VNFCInstance> vnfcInstances;
                if (vduMap.containsKey(vdu.getId())) {
                    vnfcInstances = vduMap.get(vdu.getId());
                } else {
                    vnfcInstances = new HashSet<>();
                }
                if (properties.getProperty("autoscaling.pool.prepare", "false").equals("true")) {
                    for (int i = vnfcInstances.size() + 1; i <= pool_size; i++) {
                        try {
                            vnfcInstances.add(poolEngine.allocateNewInstance(nsr, vnfr, vdu));
                        } catch (VimException e) {
                            log.warn(e.getMessage(), e);
                        }
                    }
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
        poolEngine.releaseReservedInstances(nsr_id);
        log.info("Deactivated pool mechanism for NSR " + nsr_id);
    }

    public void deactivate(String nsr_id, String vnfr_id) throws NotFoundException, VimException {
        log.debug("Deactivating pool mechanism for NSR " + nsr_id);
        if (reservedInstances.containsKey(nsr_id)) {
            if (reservedInstances.get(nsr_id).size() == 1) {
                stopPoolCheck(nsr_id);
            }
        }
        poolEngine.releaseReservedInstances(nsr_id, vnfr_id);
        log.info("Deactivated pool mechanism for NSR " + nsr_id);
    }

    public void deactivate(NetworkServiceRecord nsr) throws NotFoundException, VimException {
        log.debug("Deactivating pool mechanism for NSR " + nsr.getId());
        stopPoolCheck(nsr.getId());
        poolEngine.releaseReservedInstances(nsr);
        log.info("Deactivated pool mechanism for NSR " + nsr.getId());
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

    public VNFCInstance getReservedInstance(String nsr_id, String vnfr_id, String vdu_id) throws NotFoundException, VimException {
        VNFCInstance returnedInstance = null;
        if (!getReservedInstances(nsr_id).isEmpty()) {
            if (getReservedInstances(nsr_id).containsKey(vnfr_id)) {
                if (getReservedInstances(nsr_id).get(vnfr_id).containsKey(vdu_id)) {
                    if (getReservedInstances(nsr_id).get(vnfr_id).get(vdu_id).iterator().hasNext()) {
                        returnedInstance = getReservedInstances(nsr_id).get(vnfr_id).get(vdu_id).iterator().next();
                        getReservedInstances(nsr_id).get(vnfr_id).get(vdu_id).remove(returnedInstance);
                    } else {
                        //Allocate new Instance if no one was found
                        returnedInstance = poolEngine.allocateNewInstance(nsr_id, vnfr_id, vdu_id);
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

    public void removeReservedInstances(String nsr_id) {
        reservedInstances.remove(nsr_id);
    }

    public void startPoolCheck(String nsr_id) throws NotFoundException {
        log.debug("Activating Pool size checking for NSR with id: " + nsr_id);
        if (!tasks.containsKey(nsr_id)) {
            log.debug("Creating new PoolTask for NSR with id: " + nsr_id);
            PoolTask poolTask = new PoolTask(nsr_id, pool_size, poolEngine);
            ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(poolTask, pool_check_period * 1000);
            log.debug("Activated Pool size checking for NSR with id: " + nsr_id);
            tasks.put(nsr_id, scheduledFuture);
        } else {
            log.debug("Pool size checking of NSR with id: " + nsr_id + " were already activated");
        }
    }

    public void stopPoolCheck(String nsr_id) {
        log.debug("Deactivating Pool size checking for NSR with id: " + nsr_id);
        if (tasks.containsKey(nsr_id)) {
            tasks.get(nsr_id).cancel(false);
            try {
                tasks.get(nsr_id).get();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
//            while (!tasks.get(nsr_id).isDone()) {
//                log.debug("Waiting for finishing PoolTask for NSR with id: " + nsr_id);
//            }
            tasks.remove(nsr_id);
        } else {
            log.debug("Not Found PoolTask for NSR with id: " + nsr_id);
        }
        log.debug("Deactivated Pool size checking for NSR with id: " + nsr_id);
    }
}