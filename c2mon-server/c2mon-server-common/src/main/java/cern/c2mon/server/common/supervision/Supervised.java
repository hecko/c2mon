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
package cern.c2mon.server.common.supervision;

import java.sql.Timestamp;

import cern.c2mon.shared.common.Cacheable;
import cern.c2mon.shared.common.supervision.SupervisionConstants.SupervisionEntity;
import cern.c2mon.shared.common.supervision.SupervisionConstants.SupervisionStatus;

/**
 * Implemented by cache objects that are supervised by the
 * C2MON server core supervision mechanism.
 * 
 * @author Mark Brightwell
 *
 */
public interface Supervised extends Cacheable {

  /**
   * Returns the human-readable name of the cache object.
   * 
   * @return the object name
   */
  String getName();
  
  /**
   * Returns the supervision status of this item at
   * the current time.
   * 
   * @return the supervision status
   */
  SupervisionStatus getSupervisionStatus();
  
  /**
   * Returns the id of the state tag used to publish the 
   * status to clients.
   * 
   * @return the id of the associated state tag
   */
  Long getStateTagId();

  /**
   * Returns the id of the alive tag used for the supervision
   * mechanism.
   * 
   * @return the id of the associated alive tag; returns null if
   * no alive tag is defined (so no supervision can take place)
   */
  Long getAliveTagId();
  
  /**
   * Returns the alive interval specifying how often an alive
   * message is expected.
   * 
   * @return the interval in milliseconds
   */
  Integer getAliveInterval();
  
  /**
   * Returns the entity name of this supervised object.
   * @return the supervision entity name
   */
  SupervisionEntity getSupervisionEntity();
  
  /**
   * Returns a reason/description of the current status.
   * @return a description of the status
   */
  String getStatusDescription();

  /**
   * Sets the status of this supervised object.
   * @param supervisionStatus new status
   */
  void setSupervisionStatus(SupervisionStatus supervisionStatus);

  /**
   * Sets the description of the status.
   * @param statusDescription a reason for the current status
   */
  void setStatusDescription(String statusDescription);

  /**
   * Setter.
   * @param supervisionTime time of the supervision event
   */
  void setStatusTime(Timestamp supervisionTime);

  /**
   * Getter.
   * @return the time of the last change in the supervision status
   */
  Timestamp getStatusTime();
  
}
