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
package cern.c2mon.server.cache.loading.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import cern.c2mon.server.cache.dbaccess.ControlTagMapper;
import cern.c2mon.server.cache.dbaccess.DataTagMapper;
import cern.c2mon.server.cache.loading.ControlTagLoaderDAO;
import cern.c2mon.server.cache.loading.common.AbstractDefaultLoaderDAO;
import cern.c2mon.server.common.control.ControlTag;
import cern.c2mon.server.common.control.ControlTagCacheObject;
import cern.c2mon.server.common.datatag.DataTag;
import cern.c2mon.server.common.datatag.DataTagCacheObject;

/**
 * ControlTag loader DAO.
 * @author Mark Brightwell
 *
 */
@Service("controlTagLoaderDAO")
public class ControlTagLoaderDAOImpl extends AbstractDefaultLoaderDAO<ControlTag> implements ControlTagLoaderDAO {

  private ControlTagMapper controlTagMapper;

  private DataTagMapper dataTagMapper;

  @Autowired
  public ControlTagLoaderDAOImpl(ControlTagMapper controlTagMapper, DataTagMapper dataTagMapper) {
    super(10000, controlTagMapper);
    this.controlTagMapper = controlTagMapper;
    this.dataTagMapper = dataTagMapper;
  }

  @Override
  public void updateConfig(ControlTag controlTag) {
    dataTagMapper.updateConfig(controlTag);
  }

  @Override
  public void deleteItem(Long controlTagId) {
    controlTagMapper.deleteControlTag(controlTagId);
  }

  @Override
  public void insert(ControlTag controlTag) {
    controlTagMapper.insertControlTag((ControlTagCacheObject) controlTag);
  }

  @Override
  protected ControlTag doPostDbLoading(ControlTag item) {
    return item;
  }
}
