/*
 *
 *  *
 *  *  * Copyright (c) 2016 Open Baton (http://www.openbaton.org)
 *  *  *
 *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  * you may not use this file except in compliance with the License.
 *  *  * You may obtain a copy of the License at
 *  *  *
 *  *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  * See the License for the specific language governing permissions and
 *  *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.autoscaling.core.execution.task;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.execution.ExecutionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("prototype")
public class CooldownTask implements Runnable {

  protected Logger log = LoggerFactory.getLogger(this.getClass());

  private String nsr_id;

  private String name;

  private ExecutionEngine executionEngine;

  private long cooldown;

  private ActionMonitor actionMonitor;

  public CooldownTask(
      String nsr_id, long cooldown, ExecutionEngine executionEngine, ActionMonitor actionMonitor) {
    this.actionMonitor = actionMonitor;
    log.debug("Initializing CooldownTask for NSR with id: " + nsr_id);
    this.nsr_id = nsr_id;
    this.cooldown = cooldown;
    this.executionEngine = executionEngine;
    this.name = "ExecutionTask#" + nsr_id;
  }

  @Override
  public void run() {
    try {
      int i = 0;
      int increment = 5;
      while (i < cooldown) {
        log.debug("Waiting for Cooldown ... " + (cooldown - i) + "s");
        Thread.sleep(increment * 1000);
        i = i + increment;
        //terminate gracefully at this point in time if suggested from the outside
        if (actionMonitor.isTerminating(nsr_id)) {
          actionMonitor.finishedAction(nsr_id, Action.TERMINATED);
          return;
        }
      }
    } catch (InterruptedException e) {
      log.warn("Cooldown for NSR with id: " + nsr_id + " was interrupted");
    }
    actionMonitor.removeId(nsr_id);
    log.info("Cooldown finished for VNFR with id " + nsr_id);
  }
}
