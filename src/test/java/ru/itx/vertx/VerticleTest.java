package ru.itx.vertx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class VerticleTest {
	
	private Integer portNumber;
	
	private Logger log = LoggerFactory.getLogger(Verticle.class.getName());

	private Vertx vertx;
	
	public VerticleTest() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("vertxwebapp.conf"))));
		portNumber = conf.getInteger("portNumber", 1025);
		log.info("client params ["+portNumber+"]");
	}
	
	@Before
	public void setUp(TestContext context) throws Exception {
		vertx = Vertx.vertx();
	    vertx.deployVerticle(Verticle.class.getName(), context.asyncAssertSuccess());
	}

	@Test
	public void test(TestContext context) throws IOException {
		final Async async = context.async();
		HttpClient client = vertx.createHttpClient();
		client.getNow(portNumber, "localhost", "/", response -> {
			log.info("response with status code " + response.statusCode());
			client.close();
			async.complete();
		});
	}

	@After
	public void tearDown(TestContext context) throws Exception {
		vertx.close(context.asyncAssertSuccess());
	}

}
