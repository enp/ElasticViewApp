var ElasticView = {

	view        : {},
	user		: {},
	jsoneditor  : {},

	document_id : null,

	viewIndexes : function() {
		$.ajax({ type: "GET", url: "view", dataType: "json" })
		.done(function(data) { 
			view = data
			$.each(view, function(index) {
				$("#index").append(new Option(index))
			})
			ElasticView.viewTypes()
		})
	},
	
	viewTypes : function() {
		var index = $("#index").val()
		if (view && index) {
			$("#type").empty()
			$.each(view[index], function(type) {
				$("#type").append(new Option(type))
			})
			ElasticView.viewSort()
		}
	},
	
	viewSort : function() {
		var index = $("#index").val()
		var type = $("#type").val()
		if (view && index && type) {
			$("#sort").empty()
			if (!view[index][type].sortFields) {
				view[index][type].sortFields = {}
				$.each(view[index][type].fields, function(i, field) {
					view[index][type].sortFields[field] = 'keyword'
				})
			}
			$.each(view[index][type].sortFields, function(field, style) {
				var value = field
				if (style == "keyword")
					value += ".keyword"
				$("#sort").append(new Option(field, value))
			})
		}
	},
	
	viewDocuments : function() {
		var index  = $("#index").val()
		var type   = $("#type").val()
		var sort   = $("#sort").val()
		var order  = $("#order").val()
		var filter = $("#filter").val()
		var size   = $("#limit").val()
		$("#data").empty()
		$.ajax({ 
			type: "GET", 
			url: "view/"+index+"/"+type+"?sort="+sort+"&order="+order+"&filter="+filter+"&size="+size+"&fields="+view[index][type].fields.join(), 
			dataType: "json"
		})
		.done(function(data) {
			var table = $("<table>")
			var head = $("<tr>")
			$.each(view[index][type].fields, function(number, column){
				if (column != 'color')
					head.append("<th class='head'>"+column+"</th>")
			})
			table.append(head)
			table.addClass("documents")
			$.each(data, function(id, document){
				id = document[0]
				document = document[1]
				var row = $("<tr>")
				if (document.color)
					row = $("<tr style='background-color:"+document.color+"'>")
				row.hover(function() {
					$(this).addClass("tr-hover")
				}, function() {
				    $(this).removeClass("tr-hover")
				})
				row.click(function(e) {
					ElasticView.viewDocument(id)
				})
				$.each(view[index][type].fields, function(number, column){
					if (column != 'color') {
						var cell = (document[column] == undefined) ? "" : document[column]
						if (typeof cell === 'object')
							cell = JSON.stringify(cell)
						if (typeof cell === 'boolean' && cell == true) 
							cell = '<img src="img/true.png">';
						if (typeof cell === 'boolean' && cell == false) 
							cell = '<img src="img/false.png">';
						row.append("<td class='cell'>"+cell+"</td>")
					}
				})
				table.append(row)
			})
			$("#data").append(table)
		})
		.fail(function(data) {
			alert(data.responseText || data.statusText)
		})
	},
	
	exportDocuments : function() {
		var index  = $("#index").val()
		var type   = $("#type").val()
		var sort   = $("#sort").val()
		var order  = $("#order").val()
		var filter = $("#filter").val()
		var size   = $("#limit").val()
		window.open("view/"+index+"/"+type+".csv?sort="+sort+"&order="+order+"&filter="+filter+"&size="+size+"&fields="+view[index][type].fields.join())
	},
	
	viewDocument : function(id) {
		var index = $("#index").val()
		var type  = $("#type").val()
		$.ajax({ 
			type: "GET", 
			url: "view/"+index+"/"+type+"/"+encodeURIComponent(id),
			dataType: "json"
		})
		.done(function(data) {			
			jsoneditor.setName(type+" ("+id+")")
			jsoneditor.set(data)
			document_id = id
			$("#editpanel").empty()
			if (user.fullAccess || view[index][type].actions.save || view[index][type].actions.copy || view[index][type].actions['delete']) {
				if (user.fullAccess || view[index][type].actions.save || view[index][type].actions.copy) 
					jsoneditor.setMode("tree")
				else
					jsoneditor.setMode("view")
				$("#editpanel").append("<br>")
				if (user.fullAccess || view[index][type].actions.save)
					$("#editpanel").append("<button class='edit' id='save' disabled>Save</button>")
				if (user.fullAccess || view[index][type].actions.copy)
					$("#editpanel").append("<button class='edit' id='copy' disabled>Copy</button>")
				if (user.fullAccess || view[index][type].actions['delete'])
					$("#editpanel").append("<button class='edit' id='delete'>Delete</button>")
				$(".edit").click(ElasticView.updateDocument)
			} else {
				jsoneditor.setMode("view")
			}
			$("#popup").bPopup({opacity:0.6})
		})
		.fail(function(data) {
			alert(data.responseText || data.statusText || data.statusText)
		})
	},
	
	updateDocument : function() {
		var index  = $("#index").val()
		var type   = $("#type").val()
		var method = this.id == 'delete' ? 'DELETE' : 'POST'
		var id     = this.id == 'copy' ? '' : encodeURIComponent(document_id)
		var data   = this.id == 'delete' ? '' : jsoneditor.getText()
		if (this.id != 'delete' || (this.id == 'delete' && confirm ('Delete document?'))) {
			$.ajax({ 
				type: method, 
				url: "view/"+index+"/"+type+"/"+id, 
				dataType: "json",
				data: data
			})
			.done(function(data) {
				ElasticView.viewDocuments()
			})
			.fail(function(data) {
				alert(data.responseText || data.statusText)
			})
			.always(function(data) {
				$("#popup").bPopup().close()
			})
		}
	},

	init : function() {
		
		$.ajax({ type: "GET", url: "logged", dataType: "json" })
		.done(function(data) {
			
			user = data
			
			if (user.description) {
				$("#auth").text(" :: "+user.description)
				$("#login").text("Logout")
			} else {
				$("#login").hide()
			}				
			
			$("#index").change(ElasticView.viewTypes)
			$("#type").change(ElasticView.viewSort)
			$("#view").click(ElasticView.viewDocuments)
			$("#export").click(ElasticView.exportDocuments)
			
			ElasticView.viewIndexes()
			
			if (user.limit)
				$("#limit").val(user.limit)
			
			$("#query").show()
			
			var modes = user.login != null && user.fullAccess ? ['tree','code'] : [] 
			
			jsoneditor = new JSONEditor($("#jsoneditor")[0], {
				search: false, sortObjectKeys: true, modes: modes, onChange: function(){
					$("#save").prop("disabled", false)
					$("#copy").prop("disabled", false)
				},
				onEditable: function (node) {
					if (user.fullAccess) {
						return true
					} else if (typeof document_id !== 'undefined') {
						var index = $("#index").val()
						var type = $("#type").val()
						if (node.path.length > 0) {
							if ($.inArray(node.path[0], view[index][type].editFields) == -1) {
								return false
							} else {
								return {
									field: node.path.length > 1 ? true : false,
									value: true
				            	}
							}
						} else {
							return false
						}
					} else {
			            return false
					}
			    }
			})
		})
		.always(function(data) {			
			$("#login").click(function() {
				$.get("logout")
				location.reload()
			})
		})
	}
}

$(document).ready(ElasticView.init)
