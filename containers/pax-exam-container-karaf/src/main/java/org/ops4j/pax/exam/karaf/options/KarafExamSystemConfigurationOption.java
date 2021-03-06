/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.exam.karaf.options;

import org.ops4j.pax.exam.Option;

/**
 * Option to configure the internal invoker for integration tests to be used.
 */
public class KarafExamSystemConfigurationOption implements Option {

    private String invoker;

    /**
     * Define the pax.exam.invoker property as system property in the environment when creating the
     * subproject.
     * 
     * @param invoker
     *            name of probe invoker
     */
    public KarafExamSystemConfigurationOption(String invoker) {
        this.invoker = invoker;
    }

    public String getInvoker() {
        return invoker;
    }

}
