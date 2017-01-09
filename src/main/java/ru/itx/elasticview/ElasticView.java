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
		String sort   = context.request().getParam("sort");
		String order  = context.request().getParam("order");
		String filter = context.request().getParam("filter");
		String fields = context.request().getParam("fields");
		String size   = context.request().getParam("size");
		
		String path   = index+"/"+type+"/_search";
		
		JsonObject query = new JsonObject()
			.put("query", new JsonObject()
				.put("query_string", new JsonObject()
					.put("analyze_wildcard", true)
					.put("query", filter)))
			.put("highlight", new JsonObject()
				.put("require_field_match", false)
				.put("fields", new JsonObject()))
			.put("sort", new JsonArray()
				.add(new JsonObject().put(sort+".keyword", new JsonObject().put("order", order))))
			.put("size",size);
		
		for (String field : fields.split(","))
			query.getJsonObject("highlight").getJsonObject("fields").put(field, new JsonObject());
		
		log.info("Request to ES : "+path+" : "+query.encode());
		
		vertx.createHttpClient(clientOptions).get(path, response -> {
			response.bodyHandler( body -> {
				JsonObject data = body.toJsonObject();
				JsonObject fail = data.getJsonObject("error");
				if (fail != null) {
					log.error("Request to ES failed : "+fail.encodePrettily());
					context.fail(fail.getInteger("status", 400));
					return;
				}
				JsonArray view = new JsonArray();
				for (Object document : data.getJsonObject("hits").getJsonArray("hits")) {
					JsonObject source    = ((JsonObject)document).getJsonObject("_source");
					JsonObject highlight = ((JsonObject)document).getJsonObject("highlight");
					if (highlight != null) {
						for (String field : source.fieldNames()) {		
							JsonArray item = highlight.getJsonArray(field);
							if (item != null)
								source.put(field, item.getString(0));
						}
					}
					view.add(source);			
				}
				context.response().end(view.encode());
		    }).exceptionHandler(error -> {
		      context.fail(error);
		    });

		}).exceptionHandler(error -> {
			context.fail(error);
		}).end(query.encode());
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
				context.response().end(view.encode());
		    }).exceptionHandler(error -> {
		      context.fail(error);
		    });
		}).exceptionHandler(error -> {
			context.fail(error);
		}).end();
	}
	
}