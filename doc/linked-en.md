# Vocabulary publication in the form of Linked Open Data

TermIt tool is intended for creation of vocabularies and terms within them, edit them and manage them. This documentation demonstrates how to access data created in TermIt as linked open data in browsable web pages using Pubby.

## About Pubby

Pubby is a tool for making RDF datasets accessible in a form of browsable internet pages. It assumes source data in a form of RDF triples accessible via SPARQL endpoint. It may also be used to make RDF data URIs dereferencable. Tool was developed and managed at Freie Universität in Berlin as an open source project. Soure code is available at [GitHub](https://github.com/cygri/pubby). More information about Pubby is available at [web page of Pubby](http://wifo5-03.informatik.uni-mannheim.de/pubby/). Last release of Pubby is version v0.3.3 from 2014, tool is fully functional and widely used.

## Download and install
[Download latest release of Pubby (v0.3.3)](http://wifo5-03.informatik.uni-mannheim.de/pubby/download/) and install a **servlet container**. Pubby was tested with [Tomcat](http://tomcat.apache.org/) and [Jetty](http://www.mortbay.org/).
Extract ZIP archive with Pubby and copy **_webapp_** directory into **_webapps_** folder of the used servlet container. Rename directory (one from Pubby) to "root" to make Pubby root directory http://myserver/, or to "pubby", or any other name to change root directory of Pubby to http://myserver/pubby/.

## Configuration
Before usage it is important to change config file. It is located in ***webapp*** directory in ***./WEB-INF/config.ttl***. Configuration file is in [Turtle](http://www.w3.org/TeamSubmission/turtle/) syntax.

Configuration file is divided into two parts: server configuration and datasets.

### Server configuration
Server configuration is an instance of `conf:Configuration` with following properties:

- *projectName* - name of the project displayed on the page title,
- *projectHomepage* - here write URL of project homepage,
- *webBase* - defines base URL used to build resource pages URLs,
- *usePrefixesFrom* - defines location to upload prefixes. Use <> to upload prefixes from config file,
- *defaultLanguage* - fill two letter language code in quotes,
- *webResourcePrefix*  - defines prefix of web resources.

Example of the server configuration:

```turtle
@prefix conf: <http://richard.cyganiak.de/2007/pubby/config.rdf#> .

<>
  a conf:Configuration ;
  conf:projectName "KBSS Ontologies" ;
  conf:projectHomepage <http://onto.fel.cvut.cz/ontologies> ;
  conf:webBase <http://onto.fel.cvut.cz/ontologies/> ;
  conf:usePrefixesFrom <> ;
  conf:defaultLanguage "en" ;
  conf:webResourcePrefix "" .
```
Project name used in this example is "KBSS Ontologies" and its homepage is located at the URL http://onto.fel.cvut.cz/ontologies. Resources are based on the URL http://onto.fel.cvut.cz/ontologies/, e.g. [https://onto.fel.cvut.cz/ontologies/page/slovník/datový/mpp-3.5-np/pojem/typ-struktury](https://onto.fel.cvut.cz/ontologies/page/slovn%C3%ADk/datov%C3%BD/mpp-3.5-np/pojem/typ-struktury). Default language is english and prefix base is this config file.

### Datasets configuration

Datasets are defined using property `conf:dataset` containing following properties:

- *sparqlEndpoint*  - URL of SPARQL endpoint containing dataset triples, i.e. SPARQL endpoint where TermIt saves data,
- *datasetBase* - basic dataset prefix (same as @prefix in RDF data).

Example of dataset configuration:
```turtle
<>
  a conf:Configuration ;
  ...
	conf:dataset
	  [
		 conf:sparqlEndpoint <https://onto.fel.cvut.cz:7200/repositories/termit-dev> ;
	     conf:datasetBase <http://onto.fel.cvut.cz/ontologies/>
	  ].
```
Dataset defined in this example has SPARQL endpoint at https://onto.fel.cvut.cz:7200/repositories/termit-dev and its base prefix is http://onto.fel.cvut.cz/ontologies/.


### Configuration file example

```turtle
@prefix conf: <http://richard.cyganiak.de/2007/pubby/config.rdf#> .

<>
  a <http://richard.cyganiak.de/2007/pubby/config.rdf#Configuration> ;
  conf:projectName "KBSS Ontologies" ;
  conf:projectHomepage <http://onto.fel.cvut.cz/ontologies> ;
  conf:webBase <http://onto.fel.cvut.cz/ontologies/> ;
  conf:usePrefixesFrom <> ;
  conf:defaultLanguage "en" ;
  conf:webResourcePrefix "" ;

  conf:dataset
 # 14GISON - IPR Datasets
  [
    conf:sparqlEndpoint <https://onto.fel.cvut.cz:7200/repositories/ipr_datasets> ;
    conf:datasetBase <http://onto.fel.cvut.cz/ontologies/>
  ],
  [
    conf:sparqlEndpoint <http://onto.fel.cvut.cz/rdf4j-server/repositories/cz-vugtk> ;
    conf:datasetBase <http://onto.fel.cvut.cz/ontologies/>
  ],

  conf:metadataTemplate "metadata.ttl" .
```

This config file puts data from two SPARQL endpoints into one dataset. Property `conf:metadataTemplate` points to the location with template for resource metadata.

Complete list of configuration properties is on the [Pubby project web page](http://wifo5-03.informatik.uni-mannheim.de/pubby/).

Complex commented example of configuration file is in [latest release of Pubby](http://wifo5-03.informatik.uni-mannheim.de/pubby/download/) in the ***webapp/WEB-INF*** directory.
