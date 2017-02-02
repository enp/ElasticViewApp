package ru.itx.elasticview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class ElasticVerticle extends AbstractVerticle {
	
	private HttpServerOptions serverOptions = new HttpServerOptions();
	private HttpClientOptions clientOptions = new HttpClientOptions();
	
	private HttpClient client;
	
	private ElasticAuth elasticAuth;
	private ElasticActions elasticActions;
	
	private String prefix;
	
	private Boolean accessControl;
	
	private Logger log = LoggerFactory.getLogger(ElasticVerticle.class.getName());
	
	public ElasticVerticle() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("elasticview.conf"))));
		serverOptions.setPort(conf.getJsonObject("server").getInteger("port", 1025));
		serverOptions.setHost(conf.getJsonObject("server").getString("host", "127.0.0.1"));
		clientOptions.setDefaultPort(conf.getJsonObject("client").getInteger("port", 9200));
		clientOptions.setDefaultHost(conf.getJsonObject("client").getString("host", "127.0.0.1"));
		clientOptions.setConnectTimeout(30);
		prefix = conf.getJsonObject("server").getString("prefix", "");
		accessControl = conf.getJsonObject("view").getBoolean("accessControl", false);
	}

	public void start(Future<Void> future) throws Exception {
		
		client = vertx.createHttpClient(clientOptions);
		
		Router router = Router.router(vertx);
		
		if (accessControl) {
			
			elasticAuth = new ElasticAuth(prefix, client);
			
			router.route(prefix+"/view/*").handler(elasticAuth::authorize);
			
			router.route(prefix+"/logged").handler(elasticAuth::authorize);
			router.route(prefix+"/logged").handler(context -> {
				Object user = context.get("user");
				String response = user == null ? "" : " :: logged as "+user.toString();
				context.response().end(response);
			});
			
			router.route(prefix+"/logout").handler(context -> {
				context.response().setStatusCode(401).end("Not authorized");
			});
			
		} else {

			router.route(prefix+"/logged").handler(context -> {
				context.response().end("");
			});
			
			log.info("accessControl is disabled");
		}
		
		router.route().handler(BodyHandler.create());
		
		elasticActions = new ElasticActions(client, elasticAuth);
		
		router.route(HttpMethod.DELETE,prefix+"/view/:index/:type/:id").handler(elasticActions::updateDocument);
		router.route(HttpMethod.POST,prefix+"/view/:index/:type/:id").handler(elasticActions::updateDocument);
		router.route(HttpMethod.POST,prefix+"/view/:index/:type/").handler(elasticActions::updateDocument);
		
		router.route(prefix+"/view/:index/:type/:id").handler(elasticActions::viewDocument);
		router.route(prefix+"/view/:index/:type").handler(elasticActions::viewIndex);
		
		router.route(prefix+"/view").handler(elasticActions::viewAll);
		
		router.route(prefix+"/*").handler(StaticHandler.create().setCachingEnabled(false));

		vertx.createHttpServer(serverOptions).requestHandler(router::accept).listen(result -> {
			if (result.succeeded()) {
				log.info("Application stared");
				future.complete();
			} else {
				future.fail(result.cause());
			}
		});
	}
	
}
