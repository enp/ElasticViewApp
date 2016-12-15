package ru.itx.vertx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Verticle extends AbstractVerticle {

	private Integer portNumber;
	
	private Logger log = LoggerFactory.getLogger(Verticle.class.getName());
	
	public Verticle() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("vertxwebapp.conf"))));
		portNumber = conf.getInteger("portNumber", 1025);
		log.info("server params ["+portNumber+"]");
	}

	public void start() throws Exception {
		vertx.createHttpServer().requestHandler(request -> {
			log.info("request from ["+request.remoteAddress()+"]");
			request.response().end("Hello world");
		}).listen(portNumber);
	}	
	
}
