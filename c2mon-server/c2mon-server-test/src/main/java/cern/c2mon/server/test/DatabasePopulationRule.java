package cern.c2mon.server.test;

import org.junit.rules.ExternalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Use this class if you need a cleanly populated backup database at the
 * start of your tests.
 * <p>
 * Example:
 *
 * @author Justin Lewis Salmon
 * @RunWith(SpringJUnit4ClassRunner.class)
 * @ContextConfiguration(classes = {
 * CommonModule.class,
 * CacheDbAccessModule.class,
 * CacheLoadingModule.class,
 * DatabasePopulationRule.class
 * })
 * public class ControlTagLoaderDAOTest {
 * @Rule
 * @Autowired public DatabasePopulationRule databasePopulationRule;
 * @Test ...
 * }
 */
@Configuration
public class DatabasePopulationRule extends ExternalResource {

  @Autowired
  private DataSource cacheDataSource;

  @Override
  protected void before() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("sql/cache-data-remove.sql"));
    if (cacheDataSource.getConnection().getMetaData().getURL().contains("mysql")) {
      populator.addScript(new ClassPathResource("sql/cache-data-update-sequence.sql"));
    } else {
      populator.addScript(new ClassPathResource("sql/cache-data-alter-sequence.sql"));
    }
    populator.addScript(new ClassPathResource("sql/cache-data-insert.sql"));
    DatabasePopulatorUtils.execute(populator, cacheDataSource);
  }
}
