package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.catalogue.ScalingStatus;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;

@Service
@Scope
public class VnfrMonitor {

    private HashMap<String, ScalingStatus> states;

    @PostConstruct
    public synchronized void init() {
        states = new HashMap<String, ScalingStatus>();
    }

    public synchronized void addVnfr(String vnfrId) {
        states.put(vnfrId, ScalingStatus.READY);
    }

    public synchronized void removeVnfr(String vnfrId) {
        states.remove(vnfrId);
    }

    public synchronized void setState(String vnfrId, ScalingStatus state) {
        states.put(vnfrId, state);
    }

    public synchronized ScalingStatus getState(String vnfrId) {
        return states.get(vnfrId);
    }

    public synchronized boolean requestScaling(List<String> vnfrIds) {
        for (String vnfrId : vnfrIds) {
            if (!states.containsKey(vnfrId)) {
                addVnfr(vnfrId);
            } else if (states.get(vnfrId) == ScalingStatus.SCALING || states.get(vnfrId) == ScalingStatus.COOLDOWN) {
                return false;
            }
        }
        for (String vnfrId : vnfrIds) {
            states.put(vnfrId, ScalingStatus.SCALING);
        }
        return true;
    }

    public synchronized void startCooldown(List<String> allVnfrIds) {
        for (String otherVnfrId : allVnfrIds) {
            states.put(otherVnfrId, ScalingStatus.COOLDOWN);
        }
    }

    @Override
    public String toString() {
        return "VnfrMonitor{" +
                "states=" + states +
                '}';
    }

    public void finishedScaling(List<String> allVnfrIds) {
        for (String otherVnfrId : allVnfrIds) {
            states.put(otherVnfrId, ScalingStatus.READY);
        }
    }
}
