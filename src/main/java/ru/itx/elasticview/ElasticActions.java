package ru.itx.elasticview;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class ElasticActions {
	
	private Logger log = LoggerFactory.getLogger(ElasticActions.class.getName());
	
	private HttpClient client;
	private ElasticAuth elasticAuth;

	public ElasticActions(HttpClient client, ElasticAuth elasticAuth) {
		this.client = client;
		this.elasticAuth = elasticAuth;
	}
	
	public void updateDocument(RoutingContext context) {
		
		String index = context.request().getParam("index");
		String type  = context.request().getParam("type");		
		String id    = context.request().getParam("id");
			
		if (id == null) {
			id = "";
		} else {
			try { id = URLEncoder.encode(id,"UTF-8"); } catch (UnsupportedEncodingException e) {}
		}

		HttpMethod method = context.request().method();

		client.request(method, "/"+index+"/"+type+"/"+id+"?refresh=wait_for", response -> {
			response.bodyHandler( body -> {
				if (index.equals(".elasticview") && elasticAuth != null)
					elasticAuth.load();
				context.response().end(body);
			}).exceptionHandler(error -> {
				context.fail(error);
			});
		}).exceptionHandler(error -> {
			context.fail(error);
		}).end(context.getBody());	
	}
	
	public void viewDocument(RoutingContext context) {
		
		String index = context.request().getParam("index");
		String type  = context.request().getParam("type");		
		String id    = context.request().getParam("id");

		try { id = URLEncoder.encode(id,"UTF-8"); } catch (UnsupportedEncodingException e) {}

		client.get("/"+index+"/"+type+"/"+id+"/_source", response -> {
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

	public void viewIndex(RoutingContext context) {

		String index  = context.request().getParam("index");
		String type   = context.request().getParam("type");
		String path   = index+"/"+type+"/_search";
		
		String filter = context.request().getParam("filter");
		
		String fields = context.request().getParam("fields");
		
		String sort   = context.request().getParam("sort");
		String order  = context.request().getParam("order");
		
		String size   = context.request().getParam("size");
		
		String format = context.request().getParam("format");
		
		if (filter == null || filter.trim().isEmpty()) filter = "*";

		JsonObject query = new JsonObject()
			.put("query", new JsonObject()
				.put("query_string", new JsonObject()
					.put("analyze_wildcard", true)
					.put("query", filter)))
			.put("highlight", new JsonObject()
				.put("require_field_match", false)
				.put("fields", new JsonObject()));
		
		if (fields != null) {
			JsonArray source = new JsonArray(); 
			for (String field : fields.split(",")) {
				query.getJsonObject("highlight").getJsonObject("fields").put(field, new JsonObject());
				source.add(field);
			}
			query.put("_source", source);
		}
			
		if (sort != null && !sort.equals("null") && order != null)
			query.put("sort", new JsonArray()
				.add(new JsonObject().put(sort, new JsonObject().put("order", order))));
		
		if (size != null)
			query.put("size",size);

		log.info("Request to ES : "+path+" : "+query.encode());

		client.get(path, response -> {
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
					if (highlight != null && format != null && !format.equals("csv")) {
						for (String field : source.fieldNames()) {		
							JsonArray item = highlight.getJsonArray(field);
							if (item != null)
								source.put(field, item.getString(0));
						}
					}
					for (String field : fields.split(",")) {
						if (field.equals("password"))
							source.put(field, "***");
					}
					view.add(new JsonArray().add(id).add(source));
				}
				if (format != null && format.equals("csv")) {
					try (HSSFWorkbook workbook = new HSSFWorkbook()) {
						int line = 0, col = 0;
			            HSSFSheet sheet = workbook.createSheet(type);
			            HSSFRow rowhead = sheet.createRow(line++);
			            HSSFFont fonthead = workbook.createFont();
			            fonthead.setFontName("Monospace");
			            fonthead.setBold(true);
			            HSSFCellStyle stylehead = workbook.createCellStyle();
		                stylehead.setFont(fonthead);
			            for (String field : fields.split(",")) {
			            	Cell cell = rowhead.createCell(col++);
			            	cell.setCellValue(field);
			            	cell.setCellStyle(stylehead);
			            }
			            HSSFFont font = workbook.createFont();
			            font.setFontName("Monospace");
			            HSSFCellStyle style = workbook.createCellStyle();
		                style.setFont(font);
			            for (Object item : view.getList()) {
			            	JsonObject document = ((JsonArray)item).getJsonObject(1);
			            	HSSFRow row = sheet.createRow(line++);
			            	col = 0;
			            	for (String field : fields.split(",")) {
				            	Cell cell = row.createCell(col++);
				            	cell.setCellValue(document.getValue(field).toString());
				            	cell.setCellStyle(style);
				            }
			            }
			            for (;col>-1;col--) {
			            	sheet.autoSizeColumn(col);
			            }
			            context.response().putHeader("content-type", "application/vnd.ms-excel").end(Buffer.buffer(workbook.getBytes()));
					} catch (IOException e) {}
				} else {
					context.response().putHeader("content-type", "application/json").end(view.encode());
				}
			}).exceptionHandler(error -> {
				context.fail(error);
			});

		}).exceptionHandler(error -> {
			context.fail(error);
		}).end(query.encode());
	}

	public void viewAll(RoutingContext context) {
		
		client.get("/_all/_mapping", response -> {
			response.bodyHandler( body -> {
				JsonObject data = body.toJsonObject();
				JsonObject view = new JsonObject();
				for (String indexName : data.fieldNames()) {
					JsonObject types = new JsonObject();
					for (String typeName : data.getJsonObject(indexName).getJsonObject("mappings").fieldNames()) {
						JsonArray fields = new JsonArray();
						JsonObject sortFields = new JsonObject();
						JsonObject properties = data.getJsonObject(indexName).getJsonObject("mappings").getJsonObject(typeName).getJsonObject("properties");
						if (properties != null) {
							for (String fieldName : properties.fieldNames()) {
								JsonObject field = properties.getJsonObject(fieldName);
								if (field.getString("type") != null) {
									fields.add(fieldName);
									if (field.getString("type").equals("text")) {
										if (field.getJsonObject("fields") != null &&
											field.getJsonObject("fields").getJsonObject("keyword") != null &&
											field.getJsonObject("fields").getJsonObject("keyword").getString("type").equals("keyword")) {
											sortFields.put(fieldName, "keyword");
										} else if (field.getBoolean("fielddata") != null && field.getBoolean("fielddata").equals(true)) {
											sortFields.put(fieldName, "direct");
										}
									} else {
										sortFields.put(fieldName, "direct");
									}
								}
							}
							types.put(typeName, new JsonObject().put("fields", fields).put("sortFields", sortFields));
						}
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
