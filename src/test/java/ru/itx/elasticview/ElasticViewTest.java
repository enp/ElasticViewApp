package ru.itx.elasticview;

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
import ru.itx.elasticview.ElasticView;

@RunWith(VertxUnitRunner.class)
public class ElasticViewTest {
	
	private Integer portNumber;
	
	private Logger log = LoggerFactory.getLogger(ElasticView.class.getName());

	private Vertx vertx;
	
	public ElasticViewTest() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("elasticview.conf"))));
		portNumber = conf.getJsonObject("server").getInteger("port", 1025);
		log.info("client params ["+portNumber+"]");
	}
	
	@Before
	public void setUp(TestContext context) throws Exception {
		vertx = Vertx.vertx();
	    vertx.deployVerticle(ElasticView.class.getName(), context.asyncAssertSuccess());
	}

	@Test
	public void test(TestContext context) throws IOException {
		final Async async = context.async();
		HttpClient client = vertx.createHttpClient();
		client.getNow(portNumber, "localhost", "/view", response -> {
			response.bodyHandler( body -> {
				log.info(body.toString());
		    });
		    response.exceptionHandler(error -> {
		      context.fail(error);
		    });
			async.complete();
		});
	}

	@After
	public void tearDown(TestContext context) throws Exception {
		vertx.close(context.asyncAssertSuccess());
	}

}
