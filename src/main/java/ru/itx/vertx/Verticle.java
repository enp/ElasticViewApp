package ru.itx.vertx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class Verticle extends AbstractVerticle {

	private Integer portNumber;
	
	private Logger log = LoggerFactory.getLogger(Verticle.class.getName());
	
	public Verticle() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("vertxwebapp.conf"))));
		portNumber = conf.getInteger("portNumber", 1025);
		log.info("server params ["+portNumber+"]");
	}

	public void start() throws Exception {
		HttpServer server = vertx.createHttpServer();
		Router router = Router.router(vertx);
		router.route("/hello").handler(context -> {
			context.response().end("Hello World");
		});
		router.route("/*").handler(StaticHandler.create());
		server.requestHandler(router::accept).listen(portNumber);
	}	
	
}
