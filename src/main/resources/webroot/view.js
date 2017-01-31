var ElasticView = {
		
	indexes     : {},
	jsonview    : {},
	
	document_id : null,

	viewIndexes : function() {
		$.ajax({ type: "GET", url: "view", dataType: "json" })
		.done(function(data) { 
			indexes = data
			$.each(indexes, function(index) {
				$("#index").append(new Option(index))
			})
			ElasticView.viewTypes()
		})
		.fail(function(data) {
			alert(data.responseText);
		})
	},
	
	viewTypes : function() {
		var index = $("#index").val()
		if (indexes && index) {
			$("#type").empty();
			$.each(indexes[index], function(type) {
				$("#type").append(new Option(type))
			})
			ElasticView.viewSort()
		}
	},
	
	viewSort : function() {
		var index = $("#index").val()
		var type = $("#type").val()
		if (indexes && index && type) {
			$("#sort").empty();
			$.each(indexes[index][type].fields, function(i, sort) {
				$("#sort").append(new Option(sort))
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
			url: "view/"+index+"/"+type+"?sort="+sort+"&order="+order+"&filter="+filter+"&size="+size+"&fields="+indexes[index][type].fields.join(), 
			dataType: "json"
		})
		.done(function(data) {
			var table = $("<table>")
			var head = $("<tr>")
			$.each(indexes[index][type].fields, function(number, column){
				head.append("<th class='head'>"+column+"</th>")
			})
			table.append(head)
			table.addClass("documents")
			$.each(data, function(id, document){
				id = document[0]
				document = document[1]
				var row = $("<tr>")
				row.hover(function() {
					$(this).addClass("tr-hover");
				}, function() {
				    $(this).removeClass("tr-hover");
				})
				row.click(function(e) {
					ElasticView.viewDocument(id)
				})
				$.each(indexes[index][type].fields, function(number, column){
					var cell = (document[column] == undefined) ? "" : document[column]
					row.append("<td class='cell'>"+cell+"</td>")
				})
				table.append(row)
			})
			$("#data").append(table)
		})
		.fail(function(data) {
			alert(data.responseText);
		})
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
			ElasticView.jsonview.setName(type+" ("+id+")")
			ElasticView.jsonview.set(data)
			document_id = id
			$("#edit").empty()
			if (indexes[index][type].edit) {
				ElasticView.jsonview.setMode("tree")
				$("#edit").append("<br>")
				$("#edit").append("<button class='edit' id='save' style='float: right; margin-left: 5px;' disabled>Save</button>")
				$("#edit").append("<button class='edit' id='copy' style='float: right; margin-left: 5px;' disabled>Copy</button>");
				$("#edit").append("<button class='edit' id='delete' style='float: right;'>Delete</button>");
				$(".edit").click(ElasticView.updateDocument)
			} else {
				ElasticView.jsonview.setMode("view")
			}
			$("#popup").bPopup({opacity:0.6})
		})
		.fail(function(data) {
			alert(data.responseText);
		})
	},
	
	updateDocument : function() {
		var index  = $("#index").val()
		var type   = $("#type").val()
		var method = this.id == 'delete' ? 'DELETE' : 'POST'
		var id     = this.id == 'copy' ? '' : encodeURIComponent(document_id)
		var data   = this.id == 'delete' ? '' : ElasticView.jsonview.getText()
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
			alert(data.responseText);
		})
		.always(function(data) {
			$("#popup").bPopup().close()
		})
	},
	
	init : function() {
		ElasticView.jsonview = new JSONEditor($("#jsonview")[0], { 
			search: false, sortObjectKeys: true, onChange: function(){
				$("#save").prop("disabled", false)
				$("#copy").prop("disabled", false)
			}
		})
		$("#index").change(ElasticView.viewTypes)
		$("#type").change(ElasticView.viewSort)
		$("#view").click(ElasticView.viewDocuments)
		ElasticView.viewIndexes()
	}
}

$(document).ready(ElasticView.init)
