var ElasticView = {
		
	indexes   : {},
	jsonview  : {},
	
	//documents : {},	

	viewIndexes : function() {
		$.ajax({ type: "GET", url: "view", dataType: "json" })
		.done(function(data) { 
			indexes = data
			$.each(indexes, function(index) {
				$("#index").append(new Option(index))
			})
			ElasticView.viewTypes()
		})
		.fail(function(jqXHR, textStatus, errorThrown ) {
			alert(errorThrown)
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
			$.each(indexes[index][type], function(i, sort) {
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
			url: "view/"+index+"/"+type+"?sort="+sort+"&order="+order+"&filter="+filter+"&size="+size+"&fields="+indexes[index][type].join(), 
			dataType: "json"
		})
		.done(function(data) {
			//ElasticView.documents = data
			var table = $("<table>")
			var head = $("<tr>")
			$.each(indexes[index][type], function(number, column){
				head.append("<th class='head'>"+column+"</th>")
			})
			table.append(head)
			table.addClass("documents")
			$.each(data, function(id, document){
				id = document[0]
				document = document[1]
				var row = $("<tr>")
				row.hover(function() {
					$(this).addClass('tr-hover');
				}, function() {
				    $(this).removeClass('tr-hover');
				})
				row.click(function(e) {
					ElasticView.viewDocument(id)
				})
				$.each(indexes[index][type], function(number, column){
					var cell = (document[column] == undefined) ? '' : document[column]
					row.append("<td class='cell'>"+cell+"</td>")
				})
				table.append(row)
			})
			$("#data").append(table)
		})
		.fail(function(jqXHR, textStatus, errorThrown ) {
			alert(errorThrown)
		})
	},
	
	viewDocument : function(id) {
		var index = $("#index").val()
		var type  = $("#type").val()
		$.ajax({ 
			type: "GET", 
			url: "view/"+index+"/"+type+"/"+id, 
			dataType: "json"
		})
		.done(function(data) {
			$("#document_id").val(id)
			ElasticView.jsonview.set(data)
			$('#popup').bPopup({opacity:0.6})
		})
	},
	
	updateDocument : function() {
		var index = $("#index").val()
		var type  = $("#type").val()
		var id    = $("#document_id").val()
		$.ajax({ 
			type: "PUT", 
			url: "view/"+index+"/"+type+"/"+id, 
			dataType: "json",
			data: ElasticView.jsonview.getText()
		})
		.done(function(data) {
			$('#popup').bPopup().close()
			ElasticView.viewDocuments()
		})		
	},
	
	init : function() {
		ElasticView.jsonview = new JSONEditor($("#jsonview")[0], { search: false, sortObjectKeys: true })
		$("#index").change(ElasticView.viewTypes)
		$("#type").change(ElasticView.viewSort)
		$("#view").click(ElasticView.viewDocuments)
		$("#update").click(ElasticView.updateDocument)
		ElasticView.viewIndexes()
	}
}

$(document).ready(ElasticView.init)
