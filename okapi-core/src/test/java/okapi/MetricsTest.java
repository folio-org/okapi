package okapi;

import org.folio.okapi.MainVerticle;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MetricsTest {

  private Vertx vertx;
  private Async async;
  private HttpClient httpClient;
  private int port;
  private ConsoleReporter reporter1;
  private GraphiteReporter reporter2;

  public MetricsTest() {
  }

  @Before
  public void setUp(TestContext context) {
    port = Integer.parseInt(System.getProperty("port", "9130"));
    String graphiteHost = System.getProperty("graphiteHost");

    final String registryName = "okapi";
    MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);

    // Note the setEnabled (true or false)
    DropwizardMetricsOptions metricsOpt = new DropwizardMetricsOptions().
            setEnabled(false).setRegistryName(registryName);

    vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(metricsOpt));

    reporter1 = ConsoleReporter.forRegistry(registry).build();
    reporter1.start(1, TimeUnit.SECONDS);

    if (graphiteHost != null) {
      Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, 2003));
      reporter2 = GraphiteReporter.forRegistry(registry)
              .prefixedWith("okapiserver")
              .build(graphite);
      reporter2.start(1, TimeUnit.MILLISECONDS);
    }

    DeploymentOptions opt = new DeploymentOptions();

    vertx.deployVerticle(MainVerticle.class.getName(),
            opt, context.asyncAssertSuccess());
    httpClient = vertx.createHttpClient();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    if (reporter1 != null) {
      reporter1.report();
      reporter1.stop();
    }
    if (reporter2 != null) {
      reporter2.report();
      reporter2.stop();
    }
  }

  @Test
  public void test1(TestContext context) {
    async = context.async();
    checkHealth(context);
  }

  public void checkHealth(TestContext context) {
    httpClient.get(port, "localhost", "/_/proxy/health", response -> {
      response.handler(body -> {
        context.assertEquals(200, response.statusCode());
      });
      response.endHandler(x -> {
        done(context);
      });
    }).end();
  }

  public void done(TestContext context) {
    async.complete();
  }
}
