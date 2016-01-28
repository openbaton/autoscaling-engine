package org.openbaton.autoscaling.core.features.pool;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.features.pool.task.PoolTask;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vnfm.configuration.NfvoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class PoolManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, ScheduledFuture> poolTasks;

    private NFVORequestor nfvoRequestor;

    private Map<String, Map<String, Map<String, Set<VNFCInstance>>>> reservedInstances;

    private ActionMonitor actionMonitor;

    @Autowired
    private PoolEngine poolEngine;

    @Autowired
    private NfvoProperties nfvoProperties;

    @Value("${autoscaling.pool.size}")
    private int POOL_SIZE;

    @Value("${autoscaling.pool.period}")
    private int POOL_CHECK_PERIOD;

    @Value("${autoscaling.pool.prepare}")
    private boolean POOL_PREPARE;


    @PostConstruct
    public void init() {
        this.actionMonitor = new ActionMonitor();
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getUsername(), nfvoProperties.getPassword(), nfvoProperties.getIp(), nfvoProperties.getPort(), "1");
        this.poolTasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setRemoveOnCancelPolicy(true);
        this.taskScheduler.initialize();

        reservedInstances = new HashMap<>();
    }


    public void activate(String nsr_id) throws NotFoundException {
        log.debug("Activating pool mechanism for NSR " + nsr_id);
        log.info("AutoScaling: Pool Size for nsr with: " + nsr_id + " -> " + POOL_SIZE);
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
                if (POOL_PREPARE == true) {
                    for (int i = 1; i <= POOL_SIZE; i++) {
                        VNFCInstance vnfcInstance = poolEngine.allocateNewInstance(nsr_id, vnfr, vdu);
                        if (vnfcInstance != null) {
                            vnfcInstances.add(vnfcInstance);
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
        log.info("AutoScaling: Pool Size for nsr with: " + nsr_id + " -> " + POOL_SIZE);
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = nfvoRequestor.getNetworkServiceRecordAgent().getVirtualNetworkFunctionRecord(nsr_id, vnfr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        }
        if (vnfr == null) {
            throw new NotFoundException("Not Found VirtualNetworkFunctionRecord with id: " + vnfr_id);
        }
        //Prepare data structure
        Map<String, Map<String, Set<VNFCInstance>>> vnfrMap;
        if (reservedInstances.containsKey(nsr_id)) {
            vnfrMap = reservedInstances.get(nsr_id);
        } else {
            vnfrMap = new HashMap<String, Map<String, Set<VNFCInstance>>>();
        }
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
            if (POOL_PREPARE == true) {
                for (int i = vnfcInstances.size() + 1; i <= POOL_SIZE; i++) {
                    VNFCInstance vnfcInstance = poolEngine.allocateNewInstance(nsr_id, vnfr, vdu);
                    if (vnfcInstance != null) {
                        vnfcInstances.add(vnfcInstance);
                    }
                }
            }
            vduMap.put(vdu.getId(), vnfcInstances);
        }
        vnfrMap.put(vnfr.getId(), vduMap);
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

    public VNFCInstance getReservedInstance(String nsr_id, String vnfr_id, String vdu_id) {
        VNFCInstance returnedInstance = null;
        if (!getReservedInstances(nsr_id).isEmpty()) {
            if (getReservedInstances(nsr_id).containsKey(vnfr_id)) {
                if (getReservedInstances(nsr_id).get(vnfr_id).containsKey(vdu_id)) {
                    if (getReservedInstances(nsr_id).get(vnfr_id).get(vdu_id).iterator().hasNext()) {
                        returnedInstance = getReservedInstances(nsr_id).get(vnfr_id).get(vdu_id).iterator().next();
                        getReservedInstances(nsr_id).get(vnfr_id).get(vdu_id).remove(returnedInstance);
                    } else {
                        //Allocate new Instance if no one was found
                        //returnedInstance = poolEngine.allocateNewInstance(nsr_id, vnfr_id, vdu_id);
                        log.warn("No VNFCInstances left in pool for VDU with id: " + vdu_id);
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
        if (!poolTasks.containsKey(nsr_id)) {
            log.debug("Creating new PoolTask for NSR with id: " + nsr_id);
            actionMonitor.requestAction(nsr_id, Action.INACTIVE);
            PoolTask poolTask = new PoolTask(nsr_id, POOL_SIZE, poolEngine, actionMonitor);
            ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(poolTask, POOL_CHECK_PERIOD * 1000);
            log.debug("Activated Pool size checking for NSR with id: " + nsr_id);
            poolTasks.put(nsr_id, scheduledFuture);
        } else {
            log.debug("Pool size checking of NSR with id: " + nsr_id + " were already activated");
        }
    }

    public void stopPoolCheck(String nsr_id) {
        log.debug("Deactivating Pool size checking for NSR with id: " + nsr_id);
        if (poolTasks.containsKey(nsr_id)) {
            poolTasks.get(nsr_id).cancel(false);
            int i=60;
            while (!actionMonitor.isTerminated(nsr_id) && actionMonitor.getAction(nsr_id) != Action.INACTIVE && i>=0) {
                actionMonitor.terminate(nsr_id);
                log.debug("Waiting for finishing gracefully PoolTask for NSR with id: " + nsr_id + " (" + i + "s)");
                log.debug(actionMonitor.toString());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
                i--;
                if (i<=0) {
                    poolTasks.get(nsr_id).cancel(true);
                }
            }
            actionMonitor.removeId(nsr_id);
            log.debug("Deactivated Pool size checking for NSR with id: " + nsr_id);
        } else {
            log.debug("Not Found PoolTask for NSR with id: " + nsr_id);
        }
    }

}
