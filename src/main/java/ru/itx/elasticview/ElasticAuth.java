package ru.itx.elasticview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

public class ElasticAuth implements AuthProvider {

private Logger log = LoggerFactory.getLogger(ElasticAuth.class.getName());
	
private class ElasticUser extends AbstractUser {

	private JsonObject principal;
		
		public ElasticUser(JsonObject principal) {
			this.principal = principal;
		}
	
		public JsonObject principal() {
			return principal;
		}
	
		public void setAuthProvider(AuthProvider authProvider) {
			throw new RuntimeException("Not implemented");
		}
	
		protected void doIsPermitted(String permission, Handler<AsyncResult<Boolean>> resultHandler) {
			resultHandler.handle(Future.succeededFuture(true));
		}
		
	}

	private String prefix;

	private List<JsonObject> users = new ArrayList<JsonObject>();	
	private Map<String,JsonObject> groups = new HashMap<String,JsonObject>();

	public ElasticAuth(String prefix) {
		this.prefix = prefix;
	}
	
	public void load(JsonObject data) {
		if (data != null && data.getJsonArray("hits") != null) {
			for (Object object : data.getJsonArray("hits")) {
				JsonObject document = (JsonObject)object;
				if (document.getString("_type").equals("group"))
					groups.put(document.getJsonObject("_source").getString("name"),document.getJsonObject("_source").getJsonObject("view"));
				else if (document.getString("_type").equals("user"))
					users.add(document.getJsonObject("_source"));
			}
			log.info("Users/groups are loaded");
		} else {
			log.error("Users/groups are not found");
		}
	}
	
	private void handleError(String message, Handler<AsyncResult<User>> handler) {
		log.error(message);
		handler.handle(Future.failedFuture(new Exception(message)));
	}

	public void authenticate(JsonObject info, Handler<AsyncResult<User>> handler) {
		if (users != null) {
			for (JsonObject user : users) {
				if (user.getString("login").equals(info.getString("username")) && 
					user.getString("password").equals(info.getString("password"))) {
					log.trace("User ["+info.getString("username")+"] authenticated");
					handler.handle(Future.succeededFuture(
						new ElasticUser(new JsonObject()
							.put("login", user.getString("login"))
							.put("group", user.getString("group"))
							.put("description", user.getString("description"))
							.put("fullAccess", user.getBoolean("fullAccess"))
						)
					));
					return;
				}
			}
			handleError("User ["+info.getString("username")+":"+info.getString("password")+"] not found", handler);
		} else {
			handleError("No users loaded", handler);
		}
	}
	
	public void checkAccess(RoutingContext context) {
		JsonObject principal = context.user().principal();
		if (principal.getBoolean("fullAccess")) {
			context.next();
		} else {
			JsonObject view = groups.get(principal.getString("group"));
			String[] path = context.normalisedPath().replace(prefix+"/view", "view").split("/");
			if (path.length == 1 && path[0].equals("view")) {
				if (principal.getBoolean("fullAccess"))
					context.next();
				else
					context.response().end(view.encode());
			} else if (path.length > 1 && path[0].equals("view")) {
				if (context.request().method() == HttpMethod.GET && 
					view.getJsonObject(path[1]) != null && 
					view.getJsonObject(path[1]).getJsonObject(path[2]) != null)
					context.next();
				else if (view.getJsonObject(path[1]) != null && 
					view.getJsonObject(path[1]).getJsonObject(path[2]) != null && 
					view.getJsonObject(path[1]).getJsonObject(path[2]).getBoolean("edit"))
					context.next();
				else
					context.response().setStatusCode(403).end("Not allowed");
			} else {
				context.next();
			}
		}
	}
	
	public void viewPrincipal(RoutingContext context) {
		context.response().end(" :: logged as "+context.user().principal().getString("description"));
	}

}
