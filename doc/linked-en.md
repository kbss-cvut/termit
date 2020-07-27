# Vocabulary publication in the form of Linked Open Data

TermIt tool is intended for creation of vocabularies and terms within them, edit them and manage them. This documentation demonstrates how to access data created in TermIt as linked open data in browsable web pages using Pubby.

## About Pubby

Pubby je nástroj, který slouží k zpřístupnění datových sad v RDF formou webových stránek. Předpokladem je, že RDF data jsou dostupná přes SPARQL přístupový bod. Svým způsobem zajišťuje dereferencovatelnost URI nad RDF daty. Nástroj byl vyvinut a je spravován Freie Universität Berlin jako open source. Zdrojový kód je dostupný na [GitHubu](https://github.com/cygri/pubby). Detailní informace o nástroji a kompletní návod je na [webové stránce nástroje Pubby](http://wifo5-03.informatik.uni-mannheim.de/pubby/) (v angličtině). Poslední úpravy ve zdrojovém kódu jsou z roku 2014, nástroj je ale plně funkční a široce používán.

## Stažení a instalace
[Stáhněte si poslední verzi Pubby (v0.3.3)](http://wifo5-03.informatik.uni-mannheim.de/pubby/download/) a, pokud jste tak ještě neudělali,  nainstalujte **servlet container**. Podle stránek vývojáře bylo Pubby testováno s [Tomcat](http://tomcat.apache.org/) a [Jetty](http://www.mortbay.org/).
Rozbalte ZIP archiv s Pubby a zkopírujte adresář **_webapp_** do do adresáře **_webapps_** použitého servlet containeru. Přejmenujte zkopírovaný adresář (ten z Pubby) na "pubby", nebo jakkoliv jinak chcete. Kořenový adresář pubby se tím změní na http://vášserver/pubby/.

## Konfigurace
Před použitím je potřeba aplikaci Pubby nakonfigurovat. Konfigurační soubor je v jazyce [Turtle](http://www.w3.org/TeamSubmission/turtle/) a najdete ho v adresáři **_webapp_** na umístění **./WEB-INF/config.ttl**.

Konfigurační soubor je rozdělen do dvou částí. V první části je nastavení serveru, druhá obsahuje nastavení datových sad, které jsou pomocí Pubby zpřístupněny.

### Konfigurace serveru
Konfigurace serveru je v Turtle souboru instancí `conf:Configuration`. Obsahuje tyto atributy:

- *projectName* - název projektu zobrazený na stránce,
- *projectHomepage* - zde vyplňtě URL domovské stránky projektu,
- *webBase* - definuje základní URL, ze kterého jsou sestavovány URL jednotlivých zdrojů,
- *usePrefixesFrom* - defnuje umístění, ze kterého načítá prefixy. Pro použití prefixů z tohoto konfiguračního souboru použijte <>,
- *defaultLanguage* - vyplňte dvoupísmennou zkratku jazyka,
- *webResourcePrefix*  - definuje prefix webových zdrojů.

Následuje příklad konfigurace serveru:

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
V tomto příkladu je názvem projektu "KBSS Ontologies" a jeho domovskou stránkou je adresa http://onto.fel.cvut.cz/ontologies. URL adresy jednotlivých zdrojů jsou sestavovány ze základního URL http://onto.fel.cvut.cz/ontologies/, například [https://onto.fel.cvut.cz/ontologies/page/slovník/datový/mpp-3.5-np/pojem/typ-struktury](https://onto.fel.cvut.cz/ontologies/page/slovn%C3%ADk/datov%C3%BD/mpp-3.5-np/pojem/typ-struktury). |Jsou použity prefixy z tohoto konfiguračního dokumentu výchozím jazykem je angličtina.

### Konfigurace datových sad

Datové sady jsou v turtle vlastností konfigurace serveru `conf:dataset` a obsahují následující vlastnosti:

- *sparqlEndpoint*  - URL přístupového bodu SPARQL, na kterém se nachází data datové sady,
- *datasetBase* - základní prefix datové sady (ten, který je použit jako @prefix v RDF datech datové sady).

Příkladem nastavení datové sady je:

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
Datová sada definovaná v tomto příkladu se nachází na SPARQL přístupovém bodu na URL adrese https://onto.fel.cvut.cz:7200/repositories/termit-dev a jako základní URL pro zdroje z této datové sady je použito http://onto.fel.cvut.cz/ontologies/.


### Příklad konfiiguračního souboru
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
Tento konfigurační soubor sdružuje do jedné datové sady zdroje nacházející se na dvou SPARQL přístupových bodech. Vlastnost `conf:metadataTemplate` odkazuje na umístění soubory s šablonou pro metadata zdrojů.

Kompletní seznam všech konfiguračních vlastností najdete na [stránce projektu Pubby](http://wifo5-03.informatik.uni-mannheim.de/pubby/).

Komplexní příklad vzorového konfiguračního souboru s komentáři najdete v [současné verzi Pubby](http://wifo5-03.informatik.uni-mannheim.de/pubby/download/) vwé složce ***webapp/WEB-INF***.
