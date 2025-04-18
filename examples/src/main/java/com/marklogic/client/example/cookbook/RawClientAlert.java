/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.example.cookbook;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.alerting.RuleDefinition;
import com.marklogic.client.alerting.RuleDefinitionList;
import com.marklogic.client.alerting.RuleManager;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.example.cookbook.Util.ExampleProperties;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StringQueryDefinition;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * RawClientAlert illustrates defining and finding rules that match documents.
 */
public class RawClientAlert {
  static final private String RULE_NAME = "real-estate";

  static final private String[] filenames = {"curbappeal.xml", "flipper.xml", "justintime.xml"};

  public static void main(String[] args) throws IOException {
    run(Util.loadProperties());
  }

  public static void run(ExampleProperties props) throws IOException {
    System.out.println("example: "+RawClientAlert.class.getName());

	  DatabaseClient client = Util.newClient(props);

    setUpExample(client);

    configure(client);

    match(client);

    tearDownExample(client);

    // release the client
    client.release();
  }

  // set up alert rules
  public static void configure(DatabaseClient client) throws IOException {
    // create a manager for configuring rules
    RuleManager ruleMgr = client.newRuleManager();


    // specify a rule in raw XML (raw JSON is also supported
    // as well as a POJO rule definition)
    String rawRule =
      "<rapi:rule xmlns:rapi='http://marklogic.com/rest-api'>"+
        "<rapi:name>"+RULE_NAME+"</rapi:name>"+
        "<rapi:description>industry of Real Estate</rapi:description>"+
        "<search:search "+
        "xmlns:search='http://marklogic.com/appservices/search'>"+
        "<search:query>"+
        "<search:value-constraint-query>"+
        "<search:constraint-name>industry</search:constraint-name>"+
        "<search:text>Real Estate</search:text>"+
        "</search:value-constraint-query>"+
        "</search:query>"+
        "<search:options>"+
        "<search:constraint name='industry'>"+
        "<search:value>"+
        "<search:element name='industry' ns=''/>"+
        "</search:value>"+
        "</search:constraint>"+
        "</search:options>"+
        "</search:search>"+
        "<rapi:rule-metadata>"+
        "<correlate-with>/demographic-statistics?zipcode=</correlate-with>"+
        "</rapi:rule-metadata>"+
        "</rapi:rule>";

    // create a handle for writing the rule
    StringHandle writeHandle = new StringHandle(rawRule);

    // write the rule to the database
    ruleMgr.writeRule(RULE_NAME, writeHandle);
  }

  // match documents against the alert rules
  public static void match(DatabaseClient client) throws IOException {
    // create a manager for document search criteria
    QueryManager queryMgr = client.newQueryManager();

    // specify the search criteria for the documents
    String criteria = "neighborhoods";
    StringQueryDefinition querydef = queryMgr.newStringDefinition();
    querydef.setCriteria(criteria);

    // create a manager for matching rules
    RuleManager ruleMgr = client.newRuleManager();

    // match the rules against the documents qualified by the criteria
    RuleDefinitionList matchedRules = ruleMgr.match(querydef, new RuleDefinitionList());

    // iterate over the matched rules
    Iterator<RuleDefinition> ruleItr = matchedRules.iterator();
    while (ruleItr.hasNext()) {
      RuleDefinition rule = ruleItr.next();
      System.out.println(
        "document criteria "+criteria+" matched rule "+
          rule.getName()+" with metadata "+rule.getMetadata()
      );
    }
  }

  // set up by writing the document content used in the example query
  public static void setUpExample(DatabaseClient client) throws IOException {
    XMLDocumentManager docMgr = client.newXMLDocumentManager();

    InputStreamHandle contentHandle = new InputStreamHandle();

    for (String filename: filenames) {
      try ( InputStream docStream = Util.openStream("data"+File.separator+filename) ) {
        if (docStream == null) throw new IOException("Could not read document example");

        contentHandle.set(docStream);

        docMgr.write("/example/"+filename, contentHandle);
      }
    }
  }

  // clean up by deleting the documents used in the example query and
  // the rules used for matching
  public static void tearDownExample(DatabaseClient client) {
    XMLDocumentManager docMgr = client.newXMLDocumentManager();

    for (String filename: filenames) {
      docMgr.delete("/example/"+filename);
    }

    RuleManager ruleMgr = client.newRuleManager();

    ruleMgr.delete(RULE_NAME);
  }
}
