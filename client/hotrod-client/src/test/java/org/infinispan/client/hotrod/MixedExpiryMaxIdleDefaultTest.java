package org.infinispan.client.hotrod;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

/**
 * This test verifies that an entry has the proper lifespan when a default value is applied
 *
 * @author William Burns
 * @since 8.0
 */
@Test(groups = "functional", testName = "client.hotrod.MixedExpiryMaxIdleDefaultTest")
public class MixedExpiryMaxIdleDefaultTest extends MixedExpiryTest {
   @Override
   protected void configure(ConfigurationBuilder configurationBuilder) {
      configurationBuilder.expiration().maxIdle(3, TimeUnit.MINUTES);
   }
}
