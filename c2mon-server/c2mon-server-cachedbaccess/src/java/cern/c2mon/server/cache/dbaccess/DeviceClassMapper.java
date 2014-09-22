/*******************************************************************************
 * This file is part of the Technical Infrastructure Monitoring (TIM) project.
 * See http://ts-project-tim.web.cern.ch
 *
 * Copyright (C) 2004 - 2014 CERN. This program is free software; you can
 * redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received
 * a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 * Author: TIM team, tim.support@cern.ch
 ******************************************************************************/
package cern.c2mon.server.cache.dbaccess;

import org.apache.ibatis.annotations.Param;

import cern.c2mon.server.common.device.DeviceClass;
import cern.c2mon.server.common.device.DeviceClassCacheObject;

/**
 * MyBatis mapper for for accessing and updating {@link DeviceClassCacheObject}s
 * in the cache database.
 *
 * @author Justin Lewis Salmon
 */
public interface DeviceClassMapper extends LoaderMapper<DeviceClass> {

  /**
   * Insert a device class object from the cache into the db.
   *
   * @param deviceClass the device class cache object to insert
   */
  void insertDeviceClass(DeviceClass deviceClass);

  /**
   * Insert a property of a device class into the DB.
   *
   * @param id the ID of the device class to which this property belongs
   * @param property the property to insert
   */
  void insertDeviceClassProperty(@Param("id") Long id, @Param("property") String property);

  /**
   * Insert a command of a device class into the DB.
   *
   * @param id the ID of the device class to which this command belongs
   * @param command the command to insert
   */
  void insertDeviceClassCommand(@Param("id") Long id, @Param("command") String command);

  /**
   * Delete a device class object from the db.
   *
   * @param id the ID of the device class object to be deleted
   */
  void deleteDeviceClass(Long id);

  /**
   * Update a device object in the db.
   *
   * @param deviceClass the device class object to be updated
   */
  void updateDeviceClassConfig(DeviceClass deviceClass);

  /**
   * Delete all properties belonging to a particular device class.
   *
   * @param id the ID of the device class from which to delete properties
   */
  void deleteProperties(Long id);

  /**
   * Delete all commands belonging to a particular device class.
   *
   * @param id the ID of the device class from which to delete commands
   */
  void deleteCommands(Long id);
}
