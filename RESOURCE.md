# EMF resource for RDF graphs

This repository includes a prototype implementation of an EMF resource for RDF graphs, on top of [Apache Jena](https://jena.apache.org/).

## Differences with emf-triple

This implementation has some major differences with [emf-triple](https://github.com/ghillairet/emftriple):

* A single resource can combine information from multiple sources (e.g. Turtle or RDF/XML files).
* OWL inference is supported.

These differences are achieved by loading an intermediary `.rdfres` file with all the data and schema models to be combined, as well as any relevant options.

## Current limitations

* Saving is not supported at the moment.

## .rdfres file format

Suppose you have a `model.ttl` Turtle file with some statements of interest, written against an ontology in `schema.ttl`.

Suppose as well that the RDF resources in `model.ttl` follows certain conventions that relate them to an Ecore metamodel, in the [MOF2RDF](https://www.omg.org/spec/MOF2RDF/1.0/About-MOF2RDF) style:

* There are `rdf:type` predicates from the RDF resource to another RDF resource whose URI is `ePackageNamespaceURI#eClassName`.
* Statements use predicates with URIs of the form `ePackageNamespaceURI#eStructuralFeatureName`:
  * Predicate objects can be other RDF resources (in the case of `EReference`s), or literals (in the case of `EAttribute`s).
  * RDF lists are supported for many-valued features.

In that case, you could write an `.rdfres` file like this, and load it as an EMF resource where the relevant RDF resources would be deserialised into EMF `EObject`s:

```yaml
dataModels:
  - model.ttl
schemaModels:
  - schema.ttl
```

The `.rdfres` file can then be loaded and used by any EMF-compatible tool as usual.
