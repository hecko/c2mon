/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.server.daqcommunication.in.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * This class is responsible for configuring the Spring Context and beans used by DAQ Communication
 * for daqcommunication-in module. It is automatically detected.
 *
 * @author Szymon Halastra
 */
@Configuration
public class DaqCommunicationInConfiguration {

  private static final String THREAD_NAME_PREFIX = "TagUpdater-";

  @Bean
  public ThreadPoolTaskExecutor daqCommunicationThreadPoolTaskExecutor(Environment environment) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(environment.getRequiredProperty("c2mon.server.daqcommunication.jms.update.numExecutorThreads", Integer.class));
    executor.setMaxPoolSize(environment.getRequiredProperty("c2mon.server.daqcommunication.jms.update.numExecutorThreads", Integer.class));
    executor.setKeepAliveSeconds(environment.getRequiredProperty("c2mon.server.daqcommunication.jms.update.keepAliveSeconds", Integer.class));
    executor.setAllowCoreThreadTimeOut(true);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    return executor;
  }
}
