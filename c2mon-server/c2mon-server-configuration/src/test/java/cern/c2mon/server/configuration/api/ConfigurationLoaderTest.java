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
package cern.c2mon.server.configuration.api;

import cern.c2mon.server.cache.*;
import cern.c2mon.server.cache.dbaccess.*;
import cern.c2mon.server.common.alarm.AlarmCacheObject;
import cern.c2mon.server.common.alive.AliveTimerCacheObject;
import cern.c2mon.server.common.command.CommandTagCacheObject;
import cern.c2mon.server.common.control.ControlTagCacheObject;
import cern.c2mon.server.common.datatag.DataTagCacheObject;
import cern.c2mon.server.common.equipment.EquipmentCacheObject;
import cern.c2mon.server.common.process.ProcessCacheObject;
import cern.c2mon.server.common.rule.RuleTagCacheObject;
import cern.c2mon.server.common.subequipment.SubEquipmentCacheObject;
import cern.c2mon.server.configuration.ConfigurationLoader;
import cern.c2mon.server.configuration.api.util.CacheObjectFactory;
import cern.c2mon.server.configuration.api.util.TestConfigurationProvider;
import cern.c2mon.server.configuration.junit.CachePopulationRule;
import cern.c2mon.server.configuration.parser.util.*;
import cern.c2mon.server.configuraton.helper.ObjectEqualityComparison;
import cern.c2mon.server.daqcommunication.out.ProcessCommunicationManager;
import cern.c2mon.shared.client.configuration.ConfigConstants;
import cern.c2mon.shared.client.configuration.ConfigurationReport;
import cern.c2mon.shared.client.configuration.api.Configuration;
import cern.c2mon.shared.client.configuration.api.alarm.Alarm;
import cern.c2mon.shared.client.configuration.api.alarm.ValueCondition;
import cern.c2mon.shared.client.configuration.api.equipment.Equipment;
import cern.c2mon.shared.client.configuration.api.equipment.SubEquipment;
import cern.c2mon.shared.client.configuration.api.process.Process;
import cern.c2mon.shared.client.configuration.api.tag.*;
import cern.c2mon.shared.client.tag.TagMode;
import cern.c2mon.shared.common.NoSimpleValueParseException;
import cern.c2mon.shared.common.datatag.DataTagQualityImpl;
import cern.c2mon.shared.common.datatag.address.impl.SimpleHardwareAddressImpl;
import cern.c2mon.shared.daq.config.Change;
import cern.c2mon.shared.daq.config.ChangeReport;
import cern.c2mon.shared.daq.config.ConfigurationChangeEventReport;
import junit.framework.Assert;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static cern.c2mon.server.configuration.parser.util.ConfigurationProcessUtil.buildCreateAllFieldsProcess;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * @author Franz Ritter
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
    "classpath:test-config/process-communication-manager-mock.xml",
    "classpath:config/server-configuration.xml",
    "classpath:config/server-cache.xml",
    "classpath:config/server-cachedbaccess.xml",
    "classpath:config/server-cacheloading.xml",
    "classpath:config/server-daqcommunication-in.xml",
    "classpath:config/server-daqcommunication-out.xml",
    "classpath:config/server-rule.xml",
    "classpath:config/server-configuration.xml",
    "classpath:config/server-supervision.xml",
    "classpath:test-config/server-test-properties.xml"
})
@TestPropertySource("classpath:c2mon-server-default.properties")
public class ConfigurationLoaderTest {

  @Rule
  @Autowired
  public CachePopulationRule cachePopulationRule;

  @Autowired
  CacheObjectFactory cacheObjectFactory;

  @Autowired
  private ProcessCommunicationManager mockManager;

  @Autowired
  private ConfigurationLoader configurationLoader;

  @Autowired
  private DataTagCache dataTagCache;

  @Autowired
  private DataTagMapper dataTagMapper;

  @Autowired
  private ControlTagCache controlTagCache;

  @Autowired
  private ControlTagMapper controlTagMapper;

  @Autowired
  private CommandTagMapper commandTagMapper;

  @Autowired
  private CommandTagCache commandTagCache;

  @Autowired
  private RuleTagCache ruleTagCache;

  @Autowired
  private RuleTagMapper ruleTagMapper;

  @Autowired
  private EquipmentCache equipmentCache;

  @Autowired
  private EquipmentMapper equipmentMapper;

  @Autowired
  private SubEquipmentCache subEquipmentCache;

  @Autowired
  private SubEquipmentMapper subEquipmentMapper;

  @Autowired
  private ProcessCache processCache;

  @Autowired
  private ProcessMapper processMapper;

  @Autowired
  private AliveTimerCache aliveTimerCache;

  @Autowired
  private CommFaultTagCache commFaultTagCache;

  @Autowired
  private AlarmCache alarmCache;

  @Autowired
  private AlarmMapper alarmMapper;

  @Autowired
  private ProcessFacade processFacade;

  @Value("${c2mon.server.daqcommunication.jms.queue.trunk}")
  private String jmsDaqQueueTrunk;

  @Value("${c2mon.server.client.jms.topic.tag.trunk}")
  private String tagPublicationTrunk = "c2mon.client.tag.default";

  @Before
  public void beforeTest() throws IOException {
    // reset mock
    reset(mockManager);
  }

  @Test
  public void createProcess() {
    replay(mockManager);

    Properties expectedProperties = new Properties();
    Process process = buildCreateAllFieldsProcess(1L, expectedProperties);
    expectedProperties.setProperty("stateTagId", "300000");
    expectedProperties.setProperty("aliveTagId", "300001");

    Configuration configuration = new Configuration();
    configuration.addEntity(process);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result and caches
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.RESTART, report.getStatus());
    assertTrue(report.getElementReports().size() == 3);

    assertTrue(processCache.hasKey(1L));
    assertNotNull(processMapper.getItem(1L));
    assertTrue(controlTagCache.hasKey(300_000L));
    assertNotNull(controlTagMapper.getItem(300_000L));
    assertTrue(aliveTimerCache.hasKey(300_000L));
    assertTrue(controlTagCache.hasKey(300_001L));
    assertNotNull(controlTagMapper.getItem(300_001L));

    // Check Process in the cache
    ProcessCacheObject cacheObjectProcess = (ProcessCacheObject) processCache.get(1L);
    ProcessCacheObject expectedObjectProcess = cacheObjectFactory.buildProcessCacheObject(1L, process);

    ObjectEqualityComparison.assertProcessEquals(expectedObjectProcess, cacheObjectProcess);

    verify(mockManager);

    // remove the process from the server
    Process removeProcess = ConfigurationProcessUtil.buildDeleteProcess(1L);
    Configuration remove = new Configuration();
    remove.addEntity(removeProcess);
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(1L));
    assertNull(processMapper.getItem(1L));
    assertFalse(controlTagCache.hasKey(300_000L));
    assertNull(controlTagMapper.getItem(300_000L));
    assertFalse(controlTagCache.hasKey(300_001L));
    assertNull(controlTagMapper.getItem(300_001L));
    assertFalse(aliveTimerCache.hasKey(300_001L));

    verify(mockManager);
  }

  @Test
  public void updateProcess() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test equipment
    Process process = ConfigurationProcessUtil.buildUpdateProcessWithAllFields(5L, null);
    Configuration configuration = new Configuration();
    configuration.addEntity(process);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.RESTART, report.getStatus());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    ProcessCacheObject cacheObjectProcess = (ProcessCacheObject) processCache.get(5L);
    ProcessCacheObject expectedCacheObjectProcess = cacheObjectFactory.buildProcessUpdateCacheObject(cacheObjectProcess, process);

    expectedCacheObjectProcess.setJmsDaqCommandQueue(cacheObjectProcess.getJmsDaqCommandQueue());
    ObjectEqualityComparison.assertProcessEquals(expectedCacheObjectProcess, cacheObjectProcess);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    verify(mockManager);
  }

  @Test
  public void createEquipment() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andAnswer(() -> {
      List<Change> changeList = (List<Change>) EasyMock.getCurrentArguments()[1];
      ConfigurationChangeEventReport report = new ConfigurationChangeEventReport();
      for (Change change : changeList) {
        ChangeReport changeReport = new ChangeReport(change);
        changeReport.setState(ChangeReport.CHANGE_STATE.SUCCESS);
        report.appendChangeReport(changeReport);
      }
      return report;
    });

    replay(mockManager);

    // SETUP:First add a process to the server
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:Build configuration to add the test equipment
    Properties expectedProperties = new Properties();
    Equipment equipment = ConfigurationEquipmentUtil.buildCreateAllFieldsEquipment(10L, expectedProperties);
    equipment.setProcessId(5L);
    expectedProperties.setProperty("stateTagId", "300000");
    expectedProperties.setProperty("commFaultTagId", "300001");
    expectedProperties.setProperty("aliveTagId", "300002");

    Configuration configuration = new Configuration();
    configuration.addEntity(equipment);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check Report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getElementReports().size() == 4);

    // check Equipment in the cache
    EquipmentCacheObject cacheObject = (EquipmentCacheObject) equipmentCache.get(10L);
    EquipmentCacheObject expectedObject = cacheObjectFactory.buildEquipmentCacheObject(10L, equipment);

    ObjectEqualityComparison.assertEquipmentEquals(expectedObject, cacheObject);

    // Check if all caches are updated
    cern.c2mon.server.common.process.Process processObj = processCache.get(expectedObject.getProcessId());
    assertTrue(processObj.getEquipmentIds().contains(expectedObject.getId()));
    assertNotNull(commFaultTagCache.get(expectedObject.getCommFaultTagId()));
    assertEquals(expectedObject.getId(), commFaultTagCache.get(cacheObject.getCommFaultTagId()).getEquipmentId());
    assertNotNull(equipmentMapper.getItem(10L));
    assertNotNull(controlTagMapper.getItem(300_000L));
    assertNotNull(controlTagMapper.getItem(300_001L));
    assertNotNull(controlTagMapper.getItem(300_002L));

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(10L));
    assertNull(equipmentMapper.getItem(10L));
    assertFalse(controlTagCache.hasKey(300_000L));
    assertNull(controlTagMapper.getItem(300_000L));
    assertFalse(commFaultTagCache.hasKey(300_001L));
    assertFalse(controlTagCache.hasKey(300_001L));
    assertNull(controlTagMapper.getItem(300_002L));
    assertFalse(aliveTimerCache.hasKey(300_002L));

    verify(mockManager);
  }

  @Test
  public void updateEquipment() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andAnswer(new IAnswer<ConfigurationChangeEventReport>() {

      @Override
      public ConfigurationChangeEventReport answer() throws Throwable {
        List<Change> changeList = (List<Change>) EasyMock.getCurrentArguments()[1];
        ConfigurationChangeEventReport report = new ConfigurationChangeEventReport();
        for (Change change : changeList) {
          ChangeReport changeReport = new ChangeReport(change);
          changeReport.setState(ChangeReport.CHANGE_STATE.SUCCESS);
          report.appendChangeReport(changeReport);
        }
        return report;
      }
    });

    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test equipment
    Equipment equipment = ConfigurationEquipmentUtil.buildUpdateEquipmentWithAllFields(15L, null);
    Configuration configuration = new Configuration();
    configuration.addEntity(equipment);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    EquipmentCacheObject cacheObjectEquipment = (EquipmentCacheObject) equipmentCache.get(15L);
    EquipmentCacheObject expectedCacheObjectEquipment = cacheObjectFactory.buildEquipmentUpdateCacheObject(cacheObjectEquipment, equipment);

    ObjectEqualityComparison.assertEquipmentEquals(expectedCacheObjectEquipment, cacheObjectEquipment);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));

    verify(mockManager);
  }

  @Test
  public void createSubEquipment() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andAnswer(new IAnswer<ConfigurationChangeEventReport>() {

      @Override
      public ConfigurationChangeEventReport answer() throws Throwable {
        List<Change> changeList = (List<Change>) EasyMock.getCurrentArguments()[1];
        ConfigurationChangeEventReport report = new ConfigurationChangeEventReport();
        for (Change change : changeList) {
          ChangeReport changeReport = new ChangeReport(change);
          changeReport.setState(ChangeReport.CHANGE_STATE.SUCCESS);
          report.appendChangeReport(changeReport);
        }
        return report;
      }
    });
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:Build configuration to add the test equipment
    Properties expectedProperties = new Properties();
    SubEquipment subEquipment = ConfigurationSubEquipmentUtil.buildCreateAllFieldsSubEquipment(20L, expectedProperties);
    subEquipment.setEquipmentId(15L);
    expectedProperties.setProperty("stateTagId", "300000");
    expectedProperties.setProperty("commFaultTagId", "300001");
    expectedProperties.setProperty("aliveTagId", "300002");

    Configuration configuration = new Configuration();
    configuration.addEntity(subEquipment);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getElementReports().size() == 4);

    // check Equipment in the cache
    SubEquipmentCacheObject cacheObject = (SubEquipmentCacheObject) subEquipmentCache.get(20L);
    SubEquipmentCacheObject expectedObject = cacheObjectFactory.buildSubEquipmentCacheObject(20L, subEquipment);

    ObjectEqualityComparison.assertSubEquipmentEquals(expectedObject, cacheObject);

    // Check if all caches are updated
    cern.c2mon.server.common.equipment.Equipment equip = equipmentCache.get(expectedObject.getParentId());
    assertTrue(equip.getSubEquipmentIds().contains(expectedObject.getId()));
    assertNotNull(commFaultTagCache.get(expectedObject.getCommFaultTagId()));
    assertEquals(expectedObject.getId(), commFaultTagCache.get(cacheObject.getCommFaultTagId()).getEquipmentId());
    assertNotNull(subEquipmentMapper.getItem(20L));
    assertNotNull(controlTagMapper.getItem(300_000L));
    assertNotNull(controlTagMapper.getItem(300_001L));
    assertNotNull(controlTagMapper.getItem(300_002L));

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(aliveTimerCache.hasKey(201L));

    assertFalse(equipmentCache.hasKey(20L));
    assertNull(subEquipmentMapper.getItem(20L));
    assertFalse(controlTagCache.hasKey(300_000L));
    assertNull(controlTagMapper.getItem(300_000L));
    assertFalse(commFaultTagCache.hasKey(300_001L));
    assertFalse(aliveTimerCache.hasKey(300_002L));

    verify(mockManager);
  }

  @Test
  public void updateSubEquipment() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andAnswer(new IAnswer<ConfigurationChangeEventReport>() {

      @Override
      public ConfigurationChangeEventReport answer() throws Throwable {
        List<Change> changeList = (List<Change>) EasyMock.getCurrentArguments()[1];
        ConfigurationChangeEventReport report = new ConfigurationChangeEventReport();
        for (Change change : changeList) {
          ChangeReport changeReport = new ChangeReport(change);
          changeReport.setState(ChangeReport.CHANGE_STATE.SUCCESS);
          report.appendChangeReport(changeReport);
        }
        return report;
      }
    });
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test equipment
    SubEquipment subEquipment = ConfigurationSubEquipmentUtil.buildUpdateSubEquipmentWithAllFields(25L, null);
    Configuration configuration = new Configuration();
    configuration.addEntity(subEquipment);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    SubEquipmentCacheObject cacheObjectEquipment = (SubEquipmentCacheObject) subEquipmentCache.get(25L);
    SubEquipmentCacheObject expectedCacheObjectEquipment = cacheObjectFactory.buildSubEquipmentUpdateCacheObject(cacheObjectEquipment, subEquipment);

    ObjectEqualityComparison.assertSubEquipmentEquals(expectedCacheObjectEquipment, cacheObjectEquipment);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(aliveTimerCache.hasKey(201L));

    assertFalse(equipmentCache.hasKey(25L));
    assertNull(subEquipmentMapper.getItem(25L));
    assertFalse(controlTagCache.hasKey(300L));
    assertNull(controlTagMapper.getItem(300L));
    assertFalse(commFaultTagCache.hasKey(301L));
    assertFalse(aliveTimerCache.hasKey(302L));

    verify(mockManager);
  }

  @Test
  public void updateAliveTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    AliveTag aliveTagUpdate = AliveTag.update(101L).description("new description").mode(TagMode.OPERATIONAL).build();
    Configuration configuration = new Configuration();
    configuration.addEntity(aliveTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // check aliveTag in the cache
    AliveTimerCacheObject cacheObjectAlive = (AliveTimerCacheObject) aliveTimerCache.get(101L);
    AliveTimerCacheObject expectedObjectAlive = new AliveTimerCacheObject(101L, 5L, "P_INI_TEST", 100L, "PROC", 60000);
    ControlTagCacheObject cacheObjectAliveControlCache = (ControlTagCacheObject) controlTagCache.get(101L);

    Assert.assertEquals(cacheObjectAliveControlCache.getDescription(), "new description");
    ObjectEqualityComparison.assertAliveTimerValuesEquals(expectedObjectAlive, cacheObjectAlive);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));

    verify(mockManager);
  }

  @Test
  public void updateStatusTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    StatusTag statusTagUpdate = StatusTag.update(100L).description("new description").mode(TagMode.OPERATIONAL).build();
    Configuration configuration = new Configuration();
    configuration.addEntity(statusTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    ControlTagCacheObject cacheObjectStatusControlCache = (ControlTagCacheObject) controlTagCache.get(100L);
    assertEquals(cacheObjectStatusControlCache.getDescription(), "new description");
    assertEquals(cacheObjectStatusControlCache.getMode(), TagMode.OPERATIONAL.ordinal());

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    verify(mockManager);
  }

  @Test
  public void updateCommFaultTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to update the test equipment
    CommFaultTag commFaultTagUpdate = CommFaultTag.update(201L).description("new description").mode(TagMode.OPERATIONAL).build();
    Configuration configuration = new Configuration();
    configuration.addEntity(commFaultTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // check aliveTag in the cache
    ControlTagCacheObject cacheObjectStatusControlCache = (ControlTagCacheObject) controlTagCache.get(201L);
    assertEquals(cacheObjectStatusControlCache.getDescription(), "new description");
    assertEquals(cacheObjectStatusControlCache.getMode(), TagMode.OPERATIONAL.ordinal());

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    verify(mockManager);
  }

  @Test
  public void createEquipmentDataTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test DataTag
    DataTag dataTag = ConfigurationDataTagUtil.buildCreateAllFieldsDataTag(1000L, null);
    dataTag.setEquipmentId(15L);

    Configuration configuration = new Configuration();
    configuration.addEntity(dataTag);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    DataTagCacheObject cacheObjectData = (DataTagCacheObject) dataTagCache.get(1000L);
    DataTagCacheObject expectedCacheObjectData = cacheObjectFactory.buildDataTagCacheObject(1000L, dataTag);

    ObjectEqualityComparison.assertDataTagConfigEquals(expectedCacheObjectData, cacheObjectData);
    // Check if all caches are updated
    equipmentCache.acquireWriteLockOnKey(cacheObjectData.getEquipmentId());
    assertTrue(equipmentCache.get(cacheObjectData.getEquipmentId()).getDataTagIds().contains(1000L));
    equipmentCache.releaseWriteLockOnKey(cacheObjectData.getEquipmentId());

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));
    assertFalse(dataTagCache.hasKey(1000L));
    assertNull(dataTagMapper.getItem(1000L));

    verify(mockManager);
  }

  @Test
  public void updateEquipmentDataTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to update the test DataTag
    DataTag dataTagUpdate = DataTag.update(1000L)
        .description("new description")
        .mode(TagMode.OPERATIONAL)
        .minValue(99)
        .unit("updateUnit").build();
    Configuration configuration = new Configuration();
    configuration.addEntity(dataTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    DataTagCacheObject cacheObjectData = (DataTagCacheObject) dataTagCache.get(1000L);
    DataTagCacheObject expectedCacheObjectData = cacheObjectFactory.buildDataTagUpdateCacheObject(cacheObjectData, dataTagUpdate);

    ObjectEqualityComparison.assertDataTagConfigEquals(expectedCacheObjectData, cacheObjectData);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));
    assertFalse(dataTagCache.hasKey(1000L));
    assertNull(dataTagMapper.getItem(1000L));

    verify(mockManager);
  }

  @Test
  public void createSubEquipmentDataTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);

    /// SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test DataTag
    DataTag dataTag = ConfigurationDataTagUtil.buildCreateAllFieldsDataTag(1000L, null);
    dataTag.setSubEquipmentId(25L);
    dataTag.setEquipmentId(null);

    Configuration configuration = new Configuration();
    configuration.addEntity(dataTag);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    DataTagCacheObject cacheObjectData = (DataTagCacheObject) dataTagCache.get(1000L);
    DataTagCacheObject expectedCacheObjectData = cacheObjectFactory.buildDataTagCacheObject(1000L, dataTag);
    expectedCacheObjectData.setSubEquipmentId(dataTag.getSubEquipmentId());
    expectedCacheObjectData.setEquipmentId(null);

    ObjectEqualityComparison.assertDataTagConfigEquals(expectedCacheObjectData, cacheObjectData);
    // Check if all caches are updated
    subEquipmentCache.acquireWriteLockOnKey(cacheObjectData.getSubEquipmentId());
    assertTrue(subEquipmentCache.get(cacheObjectData.getSubEquipmentId()).getDataTagIds().contains(1000L));
    subEquipmentCache.releaseWriteLockOnKey(cacheObjectData.getSubEquipmentId());

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(aliveTimerCache.hasKey(201L));

    assertFalse(equipmentCache.hasKey(25L));
    assertNull(subEquipmentMapper.getItem(25L));
    assertFalse(controlTagCache.hasKey(300L));
    assertNull(controlTagMapper.getItem(300L));
    assertFalse(commFaultTagCache.hasKey(301L));
    assertFalse(aliveTimerCache.hasKey(302L));
    assertFalse(dataTagCache.hasKey(1000L));
    assertNull(dataTagMapper.getItem(1000L));

    verify(mockManager);
  }

  @Test
  public void updateSubEquipmentDataTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);

    /// SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createSubEquipmentDataTag(25L);
    configurationLoader.applyConfiguration(createDataTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to update the test DataTag
    DataTag dataTagUpdate = DataTag.update(1000L)
        .description("new description")
        .mode(TagMode.OPERATIONAL)
        .minValue(99)
        .unit("updateUnit").build();
    Configuration configuration = new Configuration();
    configuration.addEntity(dataTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    DataTagCacheObject cacheObjectData = (DataTagCacheObject) dataTagCache.get(1000L);
    DataTagCacheObject expectedCacheObjectData = cacheObjectFactory.buildDataTagUpdateCacheObject(cacheObjectData, dataTagUpdate);

    ObjectEqualityComparison.assertDataTagConfigEquals(expectedCacheObjectData, cacheObjectData);

    verify(mockManager);

    // remove the process and equipments and dataTag from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));

    assertFalse(equipmentCache.hasKey(25L));
    assertNull(subEquipmentMapper.getItem(25L));
    assertFalse(controlTagCache.hasKey(300L));
    assertNull(controlTagMapper.getItem(300L));
    assertFalse(commFaultTagCache.hasKey(301L));
    assertFalse(aliveTimerCache.hasKey(302L));
    assertFalse(dataTagCache.hasKey(1000L));
    assertNull(dataTagMapper.getItem(1000L));

    verify(mockManager);
  }

  @Test
  public void createRuleTag() {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:Build configuration to add the test RuleTag
    RuleTag ruleTag = ConfigurationRuleTagUtil.buildCreateAllFieldsRuleTag(1500L, null);
    Configuration configuration = new Configuration();
    configuration.addEntity(ruleTag);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    RuleTagCacheObject cacheObjectRule = (RuleTagCacheObject) ruleTagCache.getCopy(1500L);
    cacheObjectRule.setDataTagQuality(new DataTagQualityImpl());
    RuleTagCacheObject expectedCacheObjectRule = cacheObjectFactory.buildRuleTagCacheObject(1500L, ruleTag);

    ObjectEqualityComparison.assertRuleTagConfigEquals(expectedCacheObjectRule, cacheObjectRule);
    // Check if all caches are updated
    assertNotNull(ruleTagMapper.getItem(1500L));

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(ruleTagCache.hasKey(1500L));
    assertNull(ruleTagMapper.getItem(1500L));

    verify(mockManager);
  }

  @Test
  public void updateRuleTag() throws InterruptedException, IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    replay(mockManager);
    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    Configuration createRuleTag = TestConfigurationProvider.createRuleTag();
    configurationLoader.applyConfiguration(createRuleTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test RuleTagUpdate
    RuleTag ruleTagUpdate = RuleTag.update(1500L).ruleText("(2 > 1)[1],true[0]").description("new description").build();
    Configuration configuration = new Configuration();
    configuration.addEntity(ruleTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    RuleTagCacheObject cacheObjectData = (RuleTagCacheObject) ruleTagCache.get(1500L);
    RuleTagCacheObject expectedCacheObjectRule = cacheObjectFactory.buildRuleTagUpdateCacheObject(cacheObjectData, ruleTagUpdate);
    expectedCacheObjectRule.setProcessIds(Collections.EMPTY_SET);
    expectedCacheObjectRule.setEquipmentIds(Collections.EMPTY_SET);
    expectedCacheObjectRule.getDataTagQuality().validate();
    expectedCacheObjectRule.setTopic(tagPublicationTrunk + "." + 0L);
    Thread.sleep(1000); // sleep 1s to allow for rule evaluation on separate thread

    ObjectEqualityComparison.assertRuleTagConfigEquals(expectedCacheObjectRule, cacheObjectData);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);
    Configuration removeRule = TestConfigurationProvider.deleteRuleTag();
    report = configurationLoader.applyConfiguration(removeRule);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(ruleTagCache.hasKey(1500L));
    assertNull(ruleTagMapper.getItem(1500L));

    verify(mockManager);
  }

  @Test
  public void deleteRuleWithDeleteDataTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException, InterruptedException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));
    Configuration createRuleTag = TestConfigurationProvider.createRuleTag();
    configurationLoader.applyConfiguration(createRuleTag);

    // TEST:
    // check if the DataTag and rules are in the cache
    assertTrue(ruleTagCache.hasKey(1500L));
    assertNotNull(ruleTagMapper.getItem(1500L));
    assertTrue(dataTagCache.hasKey(1000L));
    assertNotNull(dataTagMapper.getItem(1000L));

    // Build configuration to remove the DataTag
    Configuration removeTag = TestConfigurationProvider.deleteDataTag();
    ConfigurationReport report = configurationLoader.applyConfiguration(removeTag);

    //check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);
    assertTrue(report.getElementReports().size() == 1);

    // Check if all caches are updated
    assertFalse(ruleTagCache.hasKey(1500L));
    assertNull(ruleTagMapper.getItem(1500L));
    assertFalse(dataTagCache.hasKey(1000L));
    assertNull(dataTagMapper.getItem(1000L));

    verify(mockManager);

    // remove the rest for finishing the test
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));

    verify(mockManager);
  }

  @Test
  public void createAlarm() {
    // SETUP:
    replay(mockManager);
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:Build configuration to add the test Alarm
    Alarm alarm = ConfigurationAlarmUtil.buildCreateAllFieldsAlarm(2000L, null);
    Configuration configuration = new Configuration();
    configuration.addEntity(alarm);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    AlarmCacheObject cacheObjectAlarm = (AlarmCacheObject) alarmCache.get(2000L);
    AlarmCacheObject expectedCacheObjectAlarm = cacheObjectFactory.buildAlarmCacheObject(2000L, alarm);

    ObjectEqualityComparison.assertAlarmEquals(expectedCacheObjectAlarm, cacheObjectAlarm);
    // Check if all caches are updated
    assertNotNull(alarmMapper.getItem(2000L));

    verify(mockManager);

    // remove the process and equipments and dataTag from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(alarmCache.hasKey(2000L));
    assertNull(alarmMapper.getItem(2000L));

    verify(mockManager);
  }

  @Test
  public void updateAlarm() {
    replay(mockManager);
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    Configuration createAlarm = TestConfigurationProvider.createAlarm();
    configurationLoader.applyConfiguration(createAlarm);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:Build configuration to update the test Alarm
    Alarm alarmUpdate = Alarm.update(2000L).alarmCondition(new ValueCondition(Integer.class, 5)).build();
    Configuration configuration = new Configuration();
    configuration.addEntity(alarmUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    AlarmCacheObject cacheObjectAlarm = (AlarmCacheObject) alarmCache.get(2000L);
    AlarmCacheObject expectedCacheObjectAlarm = cacheObjectFactory.buildAlarmUpdateCacheObject(cacheObjectAlarm, alarmUpdate);

    ObjectEqualityComparison.assertAlarmEquals(expectedCacheObjectAlarm, cacheObjectAlarm);

    verify(mockManager);
    // remove the process and equipments and dataTag from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(alarmCache.hasKey(2000L));
    assertNull(alarmMapper.getItem(2000L));

    verify(mockManager);
  }

  @Test
  public void deleteAlarmWithDeleteDataTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException, InterruptedException {
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createSubEquipment = TestConfigurationProvider.createSubEquipment();
    configurationLoader.applyConfiguration(createSubEquipment);
    Configuration createDataTag = TestConfigurationProvider.createEquipmentDataTag(15L);
    configurationLoader.applyConfiguration(createDataTag);
    Configuration createAlarm = TestConfigurationProvider.createAlarm();
    configurationLoader.applyConfiguration(createAlarm);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // check if the DataTag and rules are in the cache
    assertTrue(alarmCache.hasKey(2000L));
    assertNotNull(alarmMapper.getItem(2000L));
    assertTrue(dataTagCache.hasKey(1000L));
    assertNotNull(dataTagMapper.getItem(1000L));

    // Build configuration to remove the DataTag
    Configuration removeTag = TestConfigurationProvider.deleteDataTag();
    ConfigurationReport report = configurationLoader.applyConfiguration(removeTag);

    //check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);
    assertTrue(report.getElementReports().size() == 1);

    // Check if all caches are updated
    assertFalse(alarmCache.hasKey(2000L));
    assertNull(alarmMapper.getItem(2000L));
    assertFalse(dataTagCache.hasKey(100L));
    assertNull(dataTagMapper.getItem(100L));

    verify(mockManager);
    // remove the process and equipments and dataTag from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));

    verify(mockManager);
  }

  @Test
  public void createCommandTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to add the test DataTag
    CommandTag commandTag = ConfigurationCommandTagUtil.buildCreateAllFieldsCommandTag(500L, null);
    commandTag.setEquipmentId(15L);

    Configuration configuration = new Configuration();
    configuration.addEntity(commandTag);

    //apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    CommandTagCacheObject cacheObjectCommand = (CommandTagCacheObject) commandTagCache.get(500L);
    CommandTagCacheObject expectedCacheObjectCommand = cacheObjectFactory.buildCommandTagCacheObject(500L, commandTag);

    ObjectEqualityComparison.assertCommandTagEquals(expectedCacheObjectCommand, cacheObjectCommand);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));
    assertFalse(commandTagCache.hasKey(500L));
    assertNull(commandTagMapper.getItem(500L));

    verify(mockManager);
  }

  @Test
  public void updateCommandTag() throws IllegalAccessException, TransformerException, InstantiationException, NoSimpleValueParseException, ParserConfigurationException, NoSuchFieldException {
    // called once when updating the equipment;
    // mock returns a list with the correct number of SUCCESS ChangeReports
    expect(mockManager.sendConfiguration(eq(5L), isA(List.class))).andReturn(new ConfigurationChangeEventReport());
    replay(mockManager);

    // SETUP:
    Configuration createProcess = TestConfigurationProvider.createProcess();
    configurationLoader.applyConfiguration(createProcess);
    Configuration createEquipment = TestConfigurationProvider.createEquipment();
    configurationLoader.applyConfiguration(createEquipment);
    Configuration createCommandTag = TestConfigurationProvider.createCommandTag();
    configurationLoader.applyConfiguration(createCommandTag);
    processFacade.start(5L, "hostname", new Timestamp(System.currentTimeMillis()));

    // TEST:
    // Build configuration to update the test CommandTag
    CommandTag commandTagUpdate = CommandTag.update(500L)
        .hardwareAddress(new SimpleHardwareAddressImpl("updateAddress"))
        .minimum(50)
        .rbacClass("updateClass")
        .description("new description").build();
    Configuration configuration = new Configuration();
    configuration.addEntity(commandTagUpdate);

    ///apply the configuration to the server
    ConfigurationReport report = configurationLoader.applyConfiguration(configuration);

    // check report result
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertEquals(ConfigConstants.Status.OK, report.getStatus());
    assertTrue(report.getProcessesToReboot().isEmpty());
    assertTrue(report.getElementReports().size() == 1);

    // get cacheObject from the cache and compare to the an expected cacheObject
    CommandTagCacheObject cacheObjectCommand = (CommandTagCacheObject) commandTagCache.get(500L);
    CommandTagCacheObject expectedCacheObjectCommand = cacheObjectFactory.buildCommandTagUpdateCacheObject(cacheObjectCommand, commandTagUpdate);

    ObjectEqualityComparison.assertCommandTagEquals(expectedCacheObjectCommand, cacheObjectCommand);

    verify(mockManager);

    // remove the process and equipments from the server
    processFacade.stop(5L, new Timestamp(System.currentTimeMillis()));
    Configuration remove = TestConfigurationProvider.deleteProcess();
    report = configurationLoader.applyConfiguration(remove);
    assertFalse(report.toXML().contains(ConfigConstants.Status.FAILURE.toString()));
    assertTrue(report.getStatus() == ConfigConstants.Status.OK);

    assertFalse(processCache.hasKey(5L));
    assertNull(processMapper.getItem(5L));
    assertFalse(controlTagCache.hasKey(100L));
    assertNull(controlTagMapper.getItem(100L));
    assertFalse(aliveTimerCache.hasKey(101L));

    // equipment stuff
    assertFalse(equipmentCache.hasKey(15L));
    assertNull(equipmentMapper.getItem(15L));
    assertFalse(controlTagCache.hasKey(200L));
    assertNull(controlTagMapper.getItem(200L));
    assertFalse(commFaultTagCache.hasKey(201L));
    assertFalse(controlTagCache.hasKey(201L));
    assertNull(controlTagMapper.getItem(202L));
    assertFalse(aliveTimerCache.hasKey(202L));
    assertFalse(commandTagCache.hasKey(500L));
    assertNull(commandTagMapper.getItem(500L));

    verify(mockManager);
  }
}