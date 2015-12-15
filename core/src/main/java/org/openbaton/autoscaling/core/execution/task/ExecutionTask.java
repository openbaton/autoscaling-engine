package org.openbaton.autoscaling.core.execution.task;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
public class ExecutionTask implements Runnable {

    @Override
    public void run() {

    }
}
