# RDF driver for Epsilon

This is a prototype of an [Epsilon Model Connectivity](https://eclipse.dev/epsilon/doc/emc/) driver for RDF and related technologies, based on [Apache Jena](https://jena.apache.org/).

This document provides instructions for users.
For instructions on how to set up a development environment, please see [`CONTRIBUTING.md`](./CONTRIBUTING.md).

This project also includes a prototype of an EMF resource implementation for RDF graphs, on top of Apache Jena: see [`RESOURCE.md`](./RESOURCE.md) for details.

## Features and limitations

Currently, the driver can:

* Read and query one or more RDF documents in the formats supported by Jena (Turtle and RDF/XML have been tested).
* Trigger the OWL reasoner in Jena during loading.

For now, the driver has these limitations:

* Does *not* support modifying or saving RDF documents.

The driver requires Epsilon 2.1 or newer: it will not work on Epsilon 1.x due to breaking API changes from 1.x to 2.x.

## Installation

### Eclipse IDE

Install the features from this update site:

https://epsilonlabs.github.io/emc-rdf/updates/

Alternatively, you can download a [zipped version](https://epsilonlabs.github.io/emc-rdf/updates.zip) of the update site.

Example projects are available from the [`examples`](./examples) folder of this repository.

### Maven

The RDF driver is available as a Maven dependency from [Github Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry):

```
<dependency>
  <groupId>org.eclipse.epsilon</groupId>
  <artifactId>org.eclipse.epsilon.emc.rdf</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Features

This is a high-level description of the features of the driver.
The features are described using examples based on the [W3C RDF 1.2 Turtle](https://www.w3.org/TR/rdf12-turtle/#sec-intro) example:

```
BASE <http://example.org/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
PREFIX rel: <http://www.perceive.net/schemas/relationship/>

<#green-goblin>
  rel:enemyOf <#spiderman> ;
  a foaf:Person ;    # in the context of the Marvel universe
  foaf:name "Green Goblin" .

<#spiderman>
  rel:enemyOf <#green-goblin> ;
  a foaf:Person ;
  foaf:name "Spiderman", "Человек-паук"@ru .
```

### Accessing model elements

To obtain a specific RDF resource by its URL:

```
var goblin = Model.getElementById('http://example.org/#green-goblin');
```

To list all the resources that have an `rdf:type` predicate with a certain object acting as their type, use `Prefix::Type.all`:

```
foaf::Person.all.println('All people: ');
```

You could also use `` `foaf:Person` `` to follow a more RDF-like style, but you would need backticks to escape the name.

If there is no risk of ambiguity, you can just use the type name:

```
Person.all.println('All people: ');
```

By default, the prefixes are read from the documents, but you can also specify custom prefixes while loading the model.

### Accessing predicates

Using `resource.p`, you can access all the objects of the `p` predicate where `resource` is the subject.
For example:

```
goblin.`rel:enemyOf`.println('Enemies of the Green Goblin: ');
```

If we need to specify a prefix, we can use `` x.`prefix:localName` `` or `` x.`prefix::localName` `` (the EOL grammar requires backticks for colons inside property names, whether it's `:` or `::`).

If there is no risk of ambiguity, you can also drop the prefix:

```
goblin.enemyOf.println('Enemies of the Green Goblin: ');
```

If there are predicates with the same local name but different namespace URIs in the graph formed by all the loaded documents in a model, a warning will be issued.
In this case, you should be using a prefix to avoid the ambiguity.

**Note:** currently `resource.p` will always return a collection, as we do not leverage yet the RDFS descriptions that could indicate the cardinality of `p`.

### Values of predicates

The values in `resource.p` will be either other resources, or the values of the associated literals (without filtering by language tags).

It is possible to mention a language suffix, to limit the results to literals with that language.
For instance:

```
var spider = Model.getElementById('http://example.org/#spiderman');
spider.`name@ru`.println('Name of Spiderman in Russian: ');
```

### Accessing literal objects

If you would like to access the `RDFLiteral` objects rather than just their values, use a `_literal` suffix as part of the local name (before any language tags).
For instance, we could change the above example to:

```
var spider = Model.getElementById('http://example.org/#spiderman');
spider.`name_literal@ru`.println('Name literal of Spiderman in Russian: ');
```

`RDFLiteral` objects have several properties:

* `value`: the raw value of the literal (usually a String, but it can be different for typed literals - see [Apache Jena typed literals](https://jena.apache.org/documentation/notes/typed-literals.html)).
* `language`: the language tag for the literal (if any).
* `datatypeURI`: the datatype URI for the literal.

### Limiting returned literals to preferred languages

The "Language tag preference" section of the RDF model configuration dialog allows for specifying a comma-separated list of [BCP 47](https://www.ietf.org/rfc/bcp/bcp47.txt) language tags.
If these preferences are set, `x.property` will filter literals, by only returning the values for the first tag with matches, or falling back to the untagged values if no matches are found for any of the mentioned tags.

For instance, if we set the language preferences to `en-gb,en`, filtering `x.property` will work as follows:

* If any `en-gb` literals exist, return only those.
* If any `en` literals exist, return only those.
* Otherwise, return the untagged literals (if any).

Language preferences do not apply if an explicit language tag is used: `x.property@en` will always get the `en`-tagged literals, and `x.property@` will always get the untagged literals.

### Platform URL support

Data and schema models can be loaded using `platform:/` URLs when using the driver in an Eclipse enviroment. All `platform:/` URLs are converted to `file:/` URLs before being passed to Jena.

### Data models, schema models and reasoners

RDF models are loaded as Ontology Resource Models with Jena's default OWL reasoner.
This reasoner infers OWL concepts onto an RDF data model when it is loaded.

In order to support OWL inferencing, the `RDF Model` configuration dialog is divided into two sections:

* "Data Model URLs to load": each of the elements in the list is loaded and merged into a single RDF data model.
* "Schema Model URLs to load": each element is merged into a single RDF schema model.

The resulting RDF data and schema models are then processed by Jena's reasoner using the default OWL settings.
The inferred model is then used by Epsilon for querying.

### MOF2RDF models

The [OMG MOF2RDF specification](https://www.omg.org/spec/MOF2RDF/) defines a standard mapping from MOF metamodels into OWL ontologies.
These ontologies follow certain conventions that can be used to specialise the driver and support similar queries to the ones we would have done on models conforming to the original MOF metamodel.

This project includes a `MOF2RDFModel` model class which implements some of these specialisations.
At the moment, this includes:

* When computing `resource.property`, if an OWL maximum cardinality restriction is defined for `property`, then the number of returned values will be limited to that maximum size.
If there are multiple maximum cardinality restrictions, the most restrictive one will be used.

  In the specific case that the maximum cardinality is 1, `resource.property` will directly return the value (if set) or `null`, instead of returning a collection.

