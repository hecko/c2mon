package cern.c2mon.server.configuration.handler.transacted;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import cern.c2mon.server.configuration.handler.AlarmConfigHandler;
import cern.c2mon.server.configuration.handler.RuleTagConfigHandler;
import cern.c2mon.server.configuration.impl.ProcessChange;
import cern.tim.server.cache.DataTagCache;
import cern.tim.server.cache.DataTagFacade;
import cern.tim.server.cache.EquipmentFacade;
import cern.tim.server.cache.TagLocationService;
import cern.tim.server.cache.loading.DataTagLoaderDAO;
import cern.tim.server.common.datatag.DataTagCacheObject;
import cern.tim.server.common.process.ProcessCacheObject;
import cern.tim.server.test.CacheObjectCreation;
import cern.tim.shared.daq.config.DataTagUpdate;

/**
 * Unit test.
 * 
 * @author Mark Brightwell
 *
 */
public class DataTagConfigTransactedImplTest {

  IMocksControl control;
  
  //class to test
  private DataTagConfigTransactedImpl dataTagConfigTransacted;
  
  //mocks
  private EquipmentFacade equipmentFacade;
  private RuleTagConfigHandler ruleTagConfigHandler;
  private AlarmConfigHandler alarmConfigHandler;
  private DataTagLoaderDAO dataTagLoaderDAO;
  private DataTagFacade dataTagFacade;
  private DataTagCache dataTagCache;
  private TagLocationService tagLocationService;
  
  @Before
  public void setUp() {
    control = EasyMock.createControl();
    equipmentFacade = control.createMock(EquipmentFacade.class);
    ruleTagConfigHandler = control.createMock(RuleTagConfigHandler.class);
    alarmConfigHandler = control.createMock(AlarmConfigHandler.class); 
    dataTagLoaderDAO = control.createMock(DataTagLoaderDAO.class);
    dataTagFacade = control.createMock(DataTagFacade.class);
    dataTagCache = control.createMock(DataTagCache.class);
    tagLocationService = control.createMock(TagLocationService.class);
    dataTagConfigTransacted = new DataTagConfigTransactedImpl(dataTagFacade, dataTagLoaderDAO, dataTagCache, equipmentFacade, tagLocationService);
  }
  
  @Test
  public void testEmptyUpdateDataTag() throws IllegalAccessException {
    control.reset();
    
    DataTagCacheObject dataTag = CacheObjectCreation.createTestDataTag();
    //mimick the actions of the datatag facade
    DataTagUpdate update = new DataTagUpdate();
    update.setDataTagId(dataTag.getId());
    update.setEquipmentId(dataTag.getEquipmentId());
    EasyMock.expect(dataTagCache.get(10L)).andReturn(dataTag);
    EasyMock.expect(dataTagFacade.updateConfig(dataTag, new Properties())).andReturn(update);
    dataTagLoaderDAO.updateConfig(dataTag);
    
    control.replay();
    
    ProcessChange change = dataTagConfigTransacted.doUpdateDataTag(10L, new Properties());
    assertTrue(!change.processActionRequired());    
    
    control.verify();
  }
  
  /**
   * Tests a non-empty update gets through to DAQ.
   * @throws IllegalAccessException 
   */
  @Test
  public void testNotEmptyUpdateDataTag() throws IllegalAccessException {
    control.reset();
    
    DataTagCacheObject dataTag = CacheObjectCreation.createTestDataTag();   
    //mimick the actions of the datatag facade
    DataTagUpdate update = new DataTagUpdate();
    update.setDataTagId(dataTag.getId());
    update.setEquipmentId(dataTag.getEquipmentId());
    update.setName("new name");
    EasyMock.expect(dataTagCache.get(10L)).andReturn(dataTag);
    EasyMock.expect(dataTagFacade.updateConfig(dataTag, new Properties())).andReturn(update);
    EasyMock.expect(equipmentFacade.getProcessForAbstractEquipment(dataTag.getEquipmentId())).andReturn(new ProcessCacheObject(50L));
    dataTagLoaderDAO.updateConfig(dataTag);
    
    control.replay();
    
    ProcessChange change = dataTagConfigTransacted.doUpdateDataTag(10L, new Properties());
    assertTrue(change.processActionRequired());    
    assertEquals(Long.valueOf(50), change.getProcessId());
    
    control.verify();
  }
  
}
