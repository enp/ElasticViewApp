package ru.itx.elasticview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

public class ElasticView extends AbstractVerticle {
	
	private HttpServerOptions serverOptions = new HttpServerOptions();
	private HttpClientOptions clientOptions = new HttpClientOptions();
	
	private String prefix;
	private Boolean showHiddenIndexes;
	
	private Logger log = LoggerFactory.getLogger(ElasticView.class.getName());
	
	public ElasticView() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("elasticview.conf"))));
		serverOptions.setPort(conf.getJsonObject("server").getInteger("port", 1025));
		serverOptions.setHost(conf.getJsonObject("server").getString("host", "127.0.0.1"));
		clientOptions.setDefaultPort(conf.getJsonObject("client").getInteger("port", 9200));
		clientOptions.setDefaultHost(conf.getJsonObject("client").getString("host", "127.0.0.1"));
		clientOptions.setConnectTimeout(30);
		prefix = conf.getJsonObject("server").getString("prefix", "");
		showHiddenIndexes = conf.getJsonObject("view").getBoolean("showHiddenIndexes", false);
	}

	public void start(Future<Void> future) throws Exception {

		Router router = Router.router(vertx);
		router.route(prefix+"/view/:index/:type").handler(this::view);
		router.route(prefix+"/view").handler(this::viewAll);
		router.route(prefix+"/*").handler(StaticHandler.create().setCachingEnabled(false));

		vertx.createHttpServer(serverOptions).requestHandler(router::accept).listen(result -> {
			if (result.succeeded()) {
				log.info("Elastic View application stared");
				future.complete();
			} else {
				future.fail(result.cause());
			}
		});
	}

	private void view(RoutingContext context) {
		
		String index  = context.request().getParam("index");
		String type   = context.request().getParam("type");
		String filter = context.request().getParam("filter");
		String size   = context.request().getParam("size");
		String path   = index+"/"+type+"/_search?q="+filter+"&size="+size;
		
		log.info("Request to ES : "+path);
		
		vertx.createHttpClient(clientOptions).get(path, response -> {
			response.bodyHandler( body -> {
				JsonObject data = body.toJsonObject();
				JsonObject view = new JsonObject();
				for (Object document : data.getJsonObject("hits").getJsonArray("hits")) {
					String id = ((JsonObject)document).getString("_id");
					JsonObject source = ((JsonObject)document).getJsonObject("_source");
					view.put(id, source);
					
				}
				context.response().end(view.toString());
		    });
		    response.exceptionHandler(error -> {
		      context.fail(error);
		    });

		}).exceptionHandler(error -> {
			context.fail(error);
		}).exceptionHandler(error -> {
			context.fail(error);
		}).end();
	}

	private void viewAll(RoutingContext context) {
		vertx.createHttpClient(clientOptions).get("/_all/_mapping", response -> {
			response.bodyHandler( body -> {
				JsonObject data = body.toJsonObject();
				JsonObject view = new JsonObject();
				for (String index : data.fieldNames()) {
					if (!showHiddenIndexes && index.startsWith(".")) continue;
					JsonObject types = new JsonObject();
					for (String type : data.getJsonObject(index).getJsonObject("mappings").fieldNames()) {
						JsonArray fields = new JsonArray();
						JsonObject properties = data.getJsonObject(index).getJsonObject("mappings").getJsonObject(type).getJsonObject("properties");
						for (String field : properties.fieldNames()) {
							if (properties.getJsonObject(field).containsKey("type"))
								fields.add(field);
						}
						types.put(type, fields);
					}
					view.put(index, types);
				}
				context.response().end(view.toString());
		    });
		    response.exceptionHandler(error -> {
		      context.fail(error);
		    });
		}).exceptionHandler(error -> {
			context.fail(error);
		}).end();
	}
	
}
