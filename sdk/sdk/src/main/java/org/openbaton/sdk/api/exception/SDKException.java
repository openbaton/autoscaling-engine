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

package org.openbaton.sdk.api.exception;

import java.lang.Exception;
import java.lang.Throwable;


/**
* OpenBaton SDK base Exception. Thrown to the caller of the library while using requester's functions.
*/
public class SDKException extends Exception {

    /**
     * Creates an sdk exeption without exception message
     */
    public SDKException () {}

    /**
     * Creates an sdk exeption with an exception message as string
     * @param message custom message
     */
    public SDKException (String message) {
        super(message);
    }

    /**
     * Creates an sdk exeption with a throwable cause
     * @param cause the throwable cause
     */
    public SDKException (Throwable cause) {
        super(cause);
    }

    /**
     * Creates an sdk exeption with a message as string and a throwable cause
     * @param message custom message
     * @param cause the throwable cause
     */
    public SDKException (String message, Throwable cause) {
        super(message, cause);
    }
}