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
package cern.c2mon.server.cachepersistence;

import cern.c2mon.server.cache.AlarmCache;
import cern.c2mon.server.cache.dbaccess.AlarmMapper;
import cern.c2mon.server.cachepersistence.junit.DatabasePopulationRule;
import cern.c2mon.server.common.alarm.Alarm;
import cern.c2mon.server.common.alarm.AlarmCacheObject;
import cern.c2mon.server.common.alarm.AlarmCondition;
import cern.c2mon.server.test.CacheObjectCreation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests of persistence mechanisms to the Alarm cache.
 * Integration test with the cache module (including loading module).
 * @author Mark Brightwell
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
    "classpath:config/server-cache.xml",
    "classpath:config/server-cachedbaccess.xml",
    "classpath:config/server-cachepersistence.xml",
    "classpath:test-config/server-test-properties.xml"
})
@TestPropertySource("classpath:c2mon-server-default.properties")
public class AlarmCachePersistenceTest implements ApplicationContextAware {

  @Rule
  @Autowired
  public DatabasePopulationRule databasePopulationRule;

  /**
   * Need context to explicitly start it (listeners
   * require an explicit start to the Spring context).
   */
  private ApplicationContext context;
  
  @Autowired
  private AlarmCache alarmCache;
  
  @Autowired
  private AlarmMapper alarmMapper;

  private Alarm originalObject;
  
  @Before
  public void before() {
    originalObject = alarmMapper.getItem(350000L);
    
    //need *explicit* start of listeners
    ((AbstractApplicationContext) context).start();
  }
  
  /**
   * Tests the functionality: put value in cache -> persist to DB.
   */
  @Test
  public void testAlarmPersistence() {
    
    alarmCache.put(originalObject.getId(), originalObject);
    
    //check state is as expected
    assertEquals(AlarmCondition.TERMINATE, originalObject.getState());
    
    //check it is in cache (only compares states so far)   
    AlarmCacheObject cacheObject = (AlarmCacheObject) alarmCache.get(originalObject.getId());
    assertEquals(((Alarm) alarmCache.get(originalObject.getId())).getState(), originalObject.getState());    
    //check it is in database (only values so far...)
    AlarmCacheObject objectInDB = (AlarmCacheObject) alarmMapper.getItem(originalObject.getId());
    assertNotNull(objectInDB);
    assertEquals(objectInDB.getState(), originalObject.getState());
    assertEquals(AlarmCondition.TERMINATE, objectInDB.getState()); //state is TERMINATE in test alarm 1
    
    //now update the cache object to new value
    cacheObject.setState(AlarmCondition.ACTIVE);
    //notify the listeners
    alarmCache.notifyListenersOfUpdate(cacheObject);
    
    //...and check the DB was updated after the buffer has time to fire
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    objectInDB = (AlarmCacheObject) alarmMapper.getItem(originalObject.getId());
    assertNotNull(objectInDB);    
    assertEquals(AlarmCondition.ACTIVE, objectInDB.getState());
    
    //clean up...
    //remove from cache
    alarmCache.remove(originalObject.getId());  
  }

  /**
   * Set the application context. Used for explicit start.
   */
  @Override
  public void setApplicationContext(ApplicationContext arg0) throws BeansException {
    context = arg0;
  }
  
}