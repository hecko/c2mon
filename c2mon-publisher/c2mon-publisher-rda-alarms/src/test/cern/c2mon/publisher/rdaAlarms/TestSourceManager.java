/**
 * Copyright (c) 2015 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.c2mon.publisher.rdaAlarms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class TestSourceManager extends TestBaseClass {

    private static SourceManager smgr;
    private C2monConnectionMock c2mon;
    
    @Before
    public void prepareSourceManager() throws Exception {
        if (smgr == null) {
            smgr = applicationContext.getBean("sourceMgr", SourceManager.class);
            c2mon = applicationContext.getBean("c2mon", C2monConnectionMock.class);
            smgr.initialize(c2mon.getActiveAlarms());        
        }
    }
    
    @Test
    public void testSourceCount() throws Exception {
        getLogger().info("Starting testSourceCount() ----------------- ");

        assertTrue(1 <= smgr.getSourceCount());
        
        assertEquals(TestBaseClass.SOURCE_ID, smgr.getSourceNameForAlarm(TestBaseClass.ALARM_ID));
        assertNull(smgr.getSourceNameForAlarm(TestBaseClass.MISSING_ALARM_ID));
        
        assertNotNull(smgr.findPropForAlarm(TestBaseClass.ALARM_ID));
        assertNull(smgr.findPropForAlarm(TestBaseClass.MISSING_ALARM_ID));
        getLogger().info("Completed testSourceCount() ---------------- ");
    }
    
    /**
     * Here we send an alarm to check if it is received (appears in the cache), and remove a source
     * to check that the garbage collector will delete it.
     * @throws Exception for any error in providers
     */
    @Test
    public void testAlarmCount() throws Exception {
        getLogger().info("Starting testAlarmCount() ----------------- ");
        assertTrue(1 == smgr.getAlarmCount());
        getLogger().info("getSourceCount(): {}", smgr.getSourceCount());
        assertTrue(2 == smgr.getSourceCount());
        
        // now send an alarm and check that we have 2!
        c2mon.activateAlarm(TestBaseClass.SAMPLE_FF, TestBaseClass.SAMPLE_FM, TestBaseClass.SAMPLE_FC);
        assertTrue(2 == smgr.getAlarmCount());
        assertTrue(2 == smgr.getSourceCount());
        
        // now run the garbage collector and check that some dissappeared
        DataProviderMock dpi = applicationContext.getBean("dataProvider", DataProviderMock.class);
        dpi.removeSource(TestBaseClass.SOURCE_ID_bis);
        smgr.run();
        assertTrue(1 == smgr.getAlarmCount());
        assertTrue(1 == smgr.getSourceCount());
        
        getLogger().info("Completed testAlarmCount() ---------------- ");
    }
    
    
    
}