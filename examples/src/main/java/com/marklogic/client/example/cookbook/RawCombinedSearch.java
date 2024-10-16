/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.example.cookbook;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.example.cookbook.Util.ExampleProperties;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.SearchHandle;
import com.marklogic.client.query.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * RawCombinedSearch illustrates searching for documents and iterating over results
 * by passing structured criteria and query options in a single request.
 */
public class RawCombinedSearch {
  static final private String[] filenames = {"curbappeal.xml", "flipper.xml", "justintime.xml"};

  public static void main(String[] args) throws IOException {
    run(Util.loadProperties());
  }

  public static void run(ExampleProperties props) throws IOException {
    System.out.println("example: "+RawCombinedSearch.class.getName());

	  DatabaseClient client = Util.newClient(props);

    setUpExample(client);

    // create a manager for searching
    QueryManager queryMgr = client.newQueryManager();

    // specify the query and options for the search criteria
    // in raw XML (raw JSON is also supported)
    String rawSearch = new StringBuilder()
      .append("<search:search ")
      .append(    "xmlns:search='http://marklogic.com/appservices/search'>")
      .append("<search:query>")
      .append(    "<search:term-query>")
      .append(        "<search:text>neighborhoods</search:text>")
      .append(    "</search:term-query>")
      .append(    "<search:value-constraint-query>")
      .append(        "<search:constraint-name>industry</search:constraint-name>")
      .append(        "<search:text>Real Estate</search:text>")
      .append(    "</search:value-constraint-query>")
      .append("</search:query>")
      .append("<search:options>")
      .append(    "<search:constraint name='industry'>")
      .append(        "<search:value>")
      .append(            "<search:element name='industry' ns=''/>")
      .append(        "</search:value>")
      .append(    "</search:constraint>")
      .append("</search:options>")
      .append("</search:search>")
      .toString();

    // create a search definition for the search criteria
    RawCombinedQueryDefinition querydef
      = queryMgr.newRawCombinedQueryDefinitionAs(Format.XML, rawSearch);

    // create a handle for the search results
    SearchHandle resultsHandle = new SearchHandle();

    // run the search
    queryMgr.search(querydef, resultsHandle);

    System.out.println("(Strong Typed) Matched "+resultsHandle.getTotalResults()+
      " documents with structured query\n");

    // iterate over the result documents
    MatchDocumentSummary[] docSummaries = resultsHandle.getMatchResults();
    System.out.println("Listing "+docSummaries.length+" documents:\n");
    for (MatchDocumentSummary docSummary: docSummaries) {
      String uri = docSummary.getUri();
      int score = docSummary.getScore();

      // iterate over the match locations within a result document
      MatchLocation[] locations = docSummary.getMatchLocations();
      System.out.println("Matched "+locations.length+" locations in "+uri+" with "+score+" score:");
      for (MatchLocation location: locations) {

        // iterate over the snippets at a match location
        for (MatchSnippet snippet : location.getSnippets()) {
          boolean isHighlighted = snippet.isHighlighted();

          if (isHighlighted)
            System.out.print("[");
          System.out.print(snippet.getText());
          if (isHighlighted)
            System.out.print("]");
        }
        System.out.println();
      }
    }
  }

  // set up by writing the document content used in the example query
  public static void setUpExample(DatabaseClient client) throws IOException {
    XMLDocumentManager docMgr = client.newXMLDocumentManager();

    InputStreamHandle contentHandle = new InputStreamHandle();

    for (String filename: filenames) {
      try ( InputStream docStream = Util.openStream("data"+File.separator+filename) ) {
        if (docStream == null)
          throw new IOException("Could not read document example");

        contentHandle.set(docStream);

        docMgr.write("/example/"+filename, contentHandle);
      }
    }
  }

  // clean up by deleting the documents used in the example query
  public static void tearDownExample(DatabaseClient client) {
    XMLDocumentManager docMgr = client.newXMLDocumentManager();

    for (String filename: filenames) {
      docMgr.delete("/example/"+filename);
    }
  }
}
