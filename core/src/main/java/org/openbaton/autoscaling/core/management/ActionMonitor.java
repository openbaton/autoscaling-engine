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

package org.openbaton.autoscaling.core.management;

import java.util.HashMap;
import javax.annotation.PostConstruct;
import org.openbaton.autoscaling.catalogue.Action;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope
public class ActionMonitor {

  private HashMap<String, Action> states;

  public ActionMonitor() {
    states = new HashMap<String, Action>();
  }

  @PostConstruct
  public synchronized void init() {
    states = new HashMap<String, Action>();
  }

  public synchronized void removeId(String id) {
    if (states.containsKey(id)) {
      states.remove(id);
    }
  }

  public synchronized Action getAction(String Id) {
    return states.get(Id);
  }

  public synchronized boolean requestAction(String id, Action action) {
    if (states.containsKey(id)) {
      if (states.get(id) == Action.INACTIVE) {
        states.put(id, action);
        return true;
      } else if (action == Action.COOLDOWN && states.get(id) == Action.SCALED) {
        states.put(id, action);
        return true;
      } else {
        return false;
      }
    } else {
      states.put(id, action);
    }
    return true;
  }

  public synchronized void finishedAction(String id) {
    if (states.containsKey(id)) {
      if (states.get(id) != Action.TERMINATING && states.get(id) != Action.TERMINATED) {
        states.put(id, Action.INACTIVE);
      } else if (states.get(id) == Action.TERMINATING) {
        states.put(id, Action.TERMINATED);
      }
    }
  }

  public synchronized void finishedAction(String id, Action nextAction) {
    if (states.containsKey(id)) {
      if (nextAction == Action.TERMINATED
          || ((states.get(id) != Action.TERMINATING && states.get(id) != Action.TERMINATED))) {
        states.put(id, nextAction);
      } else if (states.get(id) == Action.TERMINATING) {
        states.put(id, Action.TERMINATED);
      }
    }
  }

  public synchronized void terminate(String id) {
    if (states.containsKey(id)
        && states.get(id) != Action.INACTIVE
        && states.get(id) != Action.TERMINATED) {
      states.put(id, Action.TERMINATING);
    }
  }

  public boolean isTerminating(String id) {
    if (states.containsKey(id)) {
      if (states.get(id) == Action.TERMINATING || states.get(id) == Action.TERMINATED) {
        return true;
      } else {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "ActionMonitor{" + "states=" + states + '}';
  }

  public boolean isTerminated(String id) {
    if (states.containsKey(id)) {
      if (states.get(id) == Action.TERMINATED) {
        return true;
      } else {
        return false;
      }
    } else {
      return true;
    }
  }
}
