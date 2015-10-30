package org.openbaton.autoscaling.catalogue;

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

    public synchronized boolean requestScaling(List<String> allVnfrIds) throws NotFoundException {
        for (String otherVnfrId : allVnfrIds) {
            if (states.get(otherVnfrId) == ScalingStatus.BUSY) {
                return false;
            }
        }
        for (String otherVnfrId : allVnfrIds) {
            states.put(otherVnfrId, ScalingStatus.BUSY);
        }
        return true;
    }

    @Override
    public String toString() {
        return "VnfrMonitor{" +
                "states=" + states +
                '}';
    }

    public void release(List<String> allVnfrIds) {
        for (String otherVnfrId : allVnfrIds) {
            states.put(otherVnfrId, ScalingStatus.READY);
        }
    }
}
