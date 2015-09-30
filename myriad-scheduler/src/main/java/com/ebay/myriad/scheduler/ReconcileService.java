/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ebay.myriad.scheduler;

import com.ebay.myriad.configuration.MyriadConfiguration;
import com.ebay.myriad.state.SchedulerState;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * {@link ReconcileService} is responsible for reconciling tasks with the mesos master
 */
public class ReconcileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReconcileService.class);

    public static final long DEFAULT_RECONCILATION_DELAY_MS = 10000;
    public static final long MAX_RECONCILE_ATTEMPTS = 10;

    private SchedulerState state;
    private MyriadConfiguration cfg;
    private Date lastReconcileTime;

    @Inject
    public ReconcileService(SchedulerState state, MyriadConfiguration cfg) {
        this.state = state;
        this.cfg = cfg;
    }

    public void reconcile(SchedulerDriver driver) {
        Collection<Protos.TaskStatus> taskStatuses = state.getTaskStatuses();

        if (taskStatuses.size() == 0) {
            return;
        }
        LOGGER.info("Reconciling {} tasks.", taskStatuses.size());

        driver.reconcileTasks(taskStatuses);

        lastReconcileTime = new Date();

        int attempt = 1;

        while (attempt <= MAX_RECONCILE_ATTEMPTS) {
            try {
                // TODO(mohit): Using exponential backoff here, maybe backoff strategy should be configurable.
                Thread.sleep(DEFAULT_RECONCILATION_DELAY_MS * attempt);
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted", e);
            }
            Collection<Protos.TaskStatus> notYetReconciled = new ArrayList<>();
            for (Protos.TaskStatus status : state.getTaskStatuses()) {
                if (status.getTimestamp() < lastReconcileTime.getTime()) {
                    notYetReconciled.add(status);
                }
            }
            LOGGER.info("Reconcile attempt {} for {} tasks", attempt, notYetReconciled.size());
            driver.reconcileTasks(notYetReconciled);
            lastReconcileTime = new Date();
            attempt++;
        }
    }
}