package ru.itx.elasticview;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class ElasticView extends AbstractVerticle {
	
	private HttpServerOptions serverOptions = new HttpServerOptions();
	private HttpClientOptions clientOptions = new HttpClientOptions();
	
	private String prefix;
	
	private Boolean showHiddenIndexes;
	private Boolean accessControl;
	
	private Logger log = LoggerFactory.getLogger(ElasticView.class.getName());
	
	public ElasticView() throws IOException {
		JsonObject conf = new JsonObject(new String(Files.readAllBytes(Paths.get("elasticview.conf"))));
		serverOptions.setPort(conf.getJsonObject("server").getInteger("port", 1025));
		serverOptions.setHost(conf.getJsonObject("server").getString("host", "127.0.0.1"));
		clientOptions.setDefaultPort(conf.getJsonObject("client").getInteger("port", 9200));
		clientOptions.setDefaultHost(conf.getJsonObject("client").getString("host", "127.0.0.1"));
		clientOptions.setConnectTimeout(30);
		prefix = conf.getJsonObject("server").getString("prefix", "");
		showHiddenIndexes = conf.getJsonObject("view").getBoolean("showHiddenIndexes", true);
		accessControl = conf.getJsonObject("view").getBoolean("accessControl", false);
	}

	public void start(Future<Void> future) throws Exception {
		
		Router router = Router.router(vertx);
		
		if (accessControl) {
			
			ElasticAuth elasticAuth = new ElasticAuth(prefix);
			
			router.route(prefix+"/view/*").handler(elasticAuth::authorize);
			
			router.route(prefix+"/logged").handler(elasticAuth::authorize);
			router.route(prefix+"/logged").handler(context -> {
				Object user = context.get("user");
				String response = user == null ? "" : " :: "+user.toString();
				context.response().end(response);
			});
			
			router.route(prefix+"/logout").handler(context -> {
				context.response().setStatusCode(401).end("Not authorized");
			});
			
			vertx.createHttpClient(clientOptions).get("/.elasticview/_search?size=1000", response -> {
				response.bodyHandler( body -> {
					elasticAuth.load(body.toJsonObject().getJsonObject("hits"));
			    }).exceptionHandler(error -> {
			      log.error("Users/groups read error : "+error.getMessage());
			    });
			}).exceptionHandler(error -> {
				log.error("Users/groups request error : "+error.getMessage());
			}).end();
			
		} else {

			router.route(prefix+"/logged").handler(context -> {
				context.response().end("");
			});
			
			log.info("accessControl is disabled");
		}
		
		router.route().handler(BodyHandler.create());
		
		router.route(HttpMethod.DELETE,prefix+"/view/:index/:type/:id").handler(this::updateDocument);
		router.route(HttpMethod.POST,prefix+"/view/:index/:type/:id").handler(this::updateDocument);
		router.route(HttpMethod.POST,prefix+"/view/:index/:type/").handler(this::updateDocument);
		
		router.route(prefix+"/view/:index/:type/:id").handler(this::viewDocument);
		router.route(prefix+"/view/:index/:type").handler(this::viewIndex);
		
		router.route(prefix+"/view").handler(this::viewAll);
		
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
	
	private void updateDocument(RoutingContext context) {
		
		String index = context.request().getParam("index");
		String type  = context.request().getParam("type");		
		String id    = context.request().getParam("id");
			
		if (id == null) {
			id = "";
		} else {
			try { id = URLEncoder.encode(id,"UTF-8"); } catch (UnsupportedEncodingException e) {}
		}

		HttpMethod method = context.request().method();

		vertx.createHttpClient(clientOptions).request(method, "/"+index+"/"+type+"/"+id+"?refresh=wait_for", response -> {
			response.bodyHandler( body -> {
				context.response().end(body);
			}).exceptionHandler(error -> {
				context.fail(error);
			});
		}).exceptionHandler(error -> {
			context.fail(error);
		}).end(context.getBody());	
	}
	
	private void viewDocument(RoutingContext context) {
		
		String index = context.request().getParam("index");
		String type  = context.request().getParam("type");		
		String id    = context.request().getParam("id");

		try { id = URLEncoder.encode(id,"UTF-8"); } catch (UnsupportedEncodingException e) {}

		vertx.createHttpClient(clientOptions).get("/"+index+"/"+type+"/"+id+"/_source", response -> {
			response.bodyHandler( body -> {
				JsonObject document = body.toJsonObject();
				context.response().end(document.encode());
			}).exceptionHandler(error -> {
				context.fail(error);
			});
		}).exceptionHandler(error -> {
			context.fail(error);
		}).end();		
	}

	private void viewIndex(RoutingContext context) {
		
		String filter = context.request().getParam("filter");
		
		String fields = context.request().getParam("fields");
		
		String sort   = context.request().getParam("sort");
		String order  = context.request().getParam("order");
		
		String size   = context.request().getParam("size");
		
		if (filter == null || filter.trim().isEmpty()) filter = "*";

		JsonObject query = new JsonObject()
			.put("query", new JsonObject()
				.put("query_string", new JsonObject()
					.put("analyze_wildcard", true)
					.put("query", filter)))
			.put("highlight", new JsonObject()
				.put("require_field_match", false)
				.put("fields", new JsonObject()));
		
		if (fields != null)
			for (String field : fields.split(","))
				query.getJsonObject("highlight").getJsonObject("fields").put(field, new JsonObject());
			
		if (sort != null && order != null)
			query.put("sort", new JsonArray()
				.add(new JsonObject().put(sort+".keyword", new JsonObject().put("order", order))));
		
		if (size != null)
			query.put("size",size);


		String index  = context.request().getParam("index");
		String type   = context.request().getParam("type");
		String path   = index+"/"+type+"/_search";

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
					String     id        = ((JsonObject)document).getString("_id");
					JsonObject source    = ((JsonObject)document).getJsonObject("_source");
					JsonObject highlight = ((JsonObject)document).getJsonObject("highlight");
					if (highlight != null) {
						for (String field : source.fieldNames()) {		
							JsonArray item = highlight.getJsonArray(field);
							if (item != null)
								source.put(field, item.getString(0));
						}
					}
					for (String field : source.fieldNames()) {		
						if (field.equals("password"))
							source.put(field, "***");
					}
					view.add(new JsonArray().add(id).add(source));
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
				for (String indexName : data.fieldNames()) {
					if (!showHiddenIndexes && indexName.startsWith(".")) continue;
					JsonObject types = new JsonObject();
					for (String typeName : data.getJsonObject(indexName).getJsonObject("mappings").fieldNames()) {
						JsonArray fields = new JsonArray();
						JsonObject properties = data.getJsonObject(indexName).getJsonObject("mappings").getJsonObject(typeName).getJsonObject("properties");
						for (String field : properties.fieldNames()) {
							if (properties.getJsonObject(field).containsKey("type"))
								fields.add(field);
						}
						types.put(typeName, new JsonObject().put("fields", fields).put("edit", true));
					}
					view.put(indexName, types);
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
