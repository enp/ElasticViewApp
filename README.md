# ElasticView #

ElasticView is a simple web application which allows to view and edit [Elasticsearch](https://www.elastic.co/products/elasticsearch) indexes/types as tables

### How to build ###

With installed JDK and Gradle just run:

```
gradle jar
```

Also you can download ElasticViewApp.jar from [releases](https://github.com/enp/ElasticViewApp/releases) 

### How to run ###

Just run:

```
java -jar ElasticViewApp.jar
```

You must have file elasticview.conf in the same folder with jar. 
Use [elasticview.conf.example](https://github.com/enp/ElasticViewApp/blob/master/elasticview.conf.example)
as example of configuration.

### How to use access control ###

It is possible to limit view/edit access to different indexes/type for different users. You must set "accessControl" 
to true in elasticview.conf and add users/groups into .elasticview index. See file [elasticview.data.example]
(https://github.com/enp/ElasticViewApp/blob/master/elasticview.data.example) as example which contains:

* example index **connections**
* example group **connections** with permission to edit and sort only one filed
* example user **janedoe** in this group
* example user **johndoe** with full access

Import this file into Elasticsearch before running ElasticView with:

```
curl -XPOST 127.0.0.1:9200/_bulk --data-binary @elasticview.data.example
```

**NOTE:** access control implementation very simple: it uses basic http authentication and plain text passwords in .elasticview index.
