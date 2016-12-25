var ElasticView = {
		
	indexes   : {},
	documents : {},	
	jsonview  : {},
	
	viewIndexes : function() {
		$.ajax({ type: "GET", url: "view", dataType: "json" })
		.done(function(data) { 
			indexes = data
			$.each(indexes, function(index) {
				$("#index").append(new Option(index));
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
				$("#type").append(new Option(type));
			})
		}
	},
	
	view : function() {
		var index  = $("#index").val()
		var type   = $("#type").val()
		var filter = $("#filter").val()
		var size   = $("#limit").val()
		$("#data").empty();
		$.ajax({ type: "GET", url: "view/"+index+"/"+type+"?filter="+filter+"&size="+size, dataType: "json" })
		.done(function(data) {
			ElasticView.documents = data
			var table = $("<table>")
			var head = $("<tr>")
			$.each(indexes[index][type], function(number, column){
				head.append("<th class='head'>"+column+"</th>")
			})
			table.append(head)
			table.addClass("documents")
			$.each(ElasticView.documents, function(id, document){
				var row = $("<tr>")
				row.hover(function() {
					$(this).addClass('tr-hover');
				}, function() {
				    $(this).removeClass('tr-hover');
				})
				row.click(function(e) {
					ElasticView.jsonview.set(ElasticView.documents[id])
					$('#popup').bPopup({opacity:0.6})
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
		
	init : function() {
		ElasticView.jsonview = new JSONEditor($("#jsonview")[0], { search: false, mode: 'view', sortObjectKeys: true })
		$("#index").change(ElasticView.viewTypes)
		$("#view").click(ElasticView.view)
		ElasticView.viewIndexes()
	}
}

$(document).ready(ElasticView.init)
