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

package org.openbaton.autoscaling.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by mpa on 28.10.15.
 */
public class Utils {

  private final static Logger log = LoggerFactory.getLogger(Utils.class);

  public static boolean isNfvoStarted(String ip, String port) {
    int i = 0;
    log.info("Waiting until NFVO is available...");
    while (!Utils.available(ip, port)) {
      i++;
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (i > 600) {
        return false;
      }
    }
    return true;
  }

  public static boolean available(String ip, String port) {
    try {
      Socket s = new Socket(ip, Integer.parseInt(port));
      log.info("NFVO is listening on port " + port + " at " + ip);
      s.close();
      return true;
    } catch (IOException ex) {
      // The remote host is not listening on this port
      log.warn("NFVO is not reachable on port " + port + " at " + ip);
      return false;
    }
  }
}
