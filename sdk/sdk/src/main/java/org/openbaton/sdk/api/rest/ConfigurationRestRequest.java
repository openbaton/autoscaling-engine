/*
 *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.openbaton.sdk.api.rest;

import org.openbaton.catalogue.nfvo.Configuration;
import org.openbaton.sdk.api.util.AbstractRestAgent;

/**
 * OpenBaton configuration-related api requester.
 */
public class ConfigurationRestRequest extends AbstractRestAgent<Configuration> {

	/**
	 * Create a configuration requester with a given url path
	 *
	 * @param url
	 * 				the url path used for the api requests
	 */
	public ConfigurationRestRequest(String username, String password, String url, String  nfvoIp, String nfvoPort, String version) {
		super(username, password, nfvoIp, nfvoPort, url, version, Configuration.class);
	}
}
