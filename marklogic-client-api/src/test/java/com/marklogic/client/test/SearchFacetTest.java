/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.test;

import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.ResourceNotResendableException;
import com.marklogic.client.admin.QueryOptionsManager;
import com.marklogic.client.io.DOMHandle;
import com.marklogic.client.io.SearchHandle;
import com.marklogic.client.query.FacetResult;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StringQueryDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SearchFacetTest {
  private static String options =
    "<options xmlns='http://marklogic.com/appservices/search'>"
    + "  <constraint name='collection'>"
    + "    <collection prefix=''/>"
    + "  </constraint>"
    + "  <constraint name='user'>"
    + "    <value>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='user'/>"
    + "    </value>"
    + "  </constraint>"
    + "  <constraint name='tag'>"
    + "    <range type='xs:string' facet='true'>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='tag'/>"
    + "    </range>"
    + "  </constraint>"
    + "  <constraint name='date'>"
    + "    <range type='xs:date' facet='false'>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='date'/>"
    + "    </range>"
    + "  </constraint>"
    + "  <constraint name='vdate'>"
    + "    <value>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='view'/>"
    + "      <attribute ns='' name='date'/>"
    + "    </value>"
    + "  </constraint>"
    + "  <constraint name='country'>"
    + "    <value>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='country'/>"
    + "    </value>"
    + "  </constraint>"
    + "  <constraint name='city'>"
    + "    <value>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='city'/>"
    + "    </value>"
    + "  </constraint>"
    + "  <constraint name='geo'>"
    + "    <geo-elem-pair>"
    + "      <heatmap s='-90' n='90' e='180' w='-180' latdivs='360' londivs='360'/>"
    + "      <parent ns='http://marklogic.com/ns/test/places' name='place'/>"
    + "      <lat ns='http://marklogic.com/ns/test/places' name='lat'/>"
    + "      <lon ns='http://marklogic.com/ns/test/places' name='long'/>"
    + "    </geo-elem-pair>"
    + "  </constraint>"
    + "  <constraint name='viewed'>"
    + "    <range type='xs:date' facet='true'>"
    + "      <element ns='http://nwalsh.com/ns/photolib' name='view'/>"
    + "      <attribute ns='' name='date'/>"
    + "      <computed-bucket name='D6' ge='-P6D' lt='-P5D' anchor='start-of-day'/>"
    + "      <computed-bucket name='D5' ge='-P5D' lt='-P4D' anchor='start-of-day'/>"
    + "      <computed-bucket name='D4' ge='-P4D' lt='-P3D' anchor='start-of-day'/>"
    + "      <computed-bucket name='D3' ge='-P3D' lt='-P2D' anchor='start-of-day'/>"
    + "      <computed-bucket name='D2' ge='-P2D' lt='-P1D' anchor='start-of-day'/>"
    + "      <computed-bucket name='D1' ge='-P1D' lt='P0D' anchor='start-of-day'/>"
    + "      <computed-bucket name='D0' ge='P0D'  lt='P1D' anchor='start-of-day'/>"
    + "    </range>"
    + "  </constraint>"

    + "  <operator name='sort'>"
    + "    <state name='uri'>"
    + "      <sort-order direction='ascending' type='xs:string'"
    + "                  collation='http://marklogic.com/collation/codepoint'>"
    + "        <attribute ns='http://www.w3.org/1999/02/22-rdf-syntax-ns#' name='about'/>"
    + "        <element ns='http://www.w3.org/1999/02/22-rdf-syntax-ns#' name='Description'/>"
    + "      </sort-order>"
    + "    </state>"
    + "    <state name='date'>"
    + "      <sort-order direction='ascending' type='xs:string'"
    + "                  collation='http://marklogic.com/collation/codepoint'>"
    + "        <element ns='http://ns.exiftool.ca/EXIF/ExifIFD/1.0/' name='CreateDate'/>"
    + "      </sort-order>"
    + "    </state>"
    + "  </operator>"
    + "  <transform-results apply='empty-snippet'/>"
    + "  <page-length>35</page-length>"
    + "  <debug>false</debug>"
    + "</options>";

  private QueryOptionsManager mgr;

  @BeforeAll
  public static void beforeClass() {
    Common.connectRestAdmin();
    Common.connect();
  }

  @AfterAll
  public static void afterClass() {
  }

  @Test
  public void testFacetSearch()
    throws IOException, ParserConfigurationException, SAXException, FailedRequestException, ForbiddenUserException,
           ResourceNotFoundException, ResourceNotResendableException
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setValidating(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(new InputSource(new StringReader(options)));

    mgr = Common.restAdminClient.newServerConfigManager().newQueryOptionsManager();
    mgr.writeOptions("photos", new DOMHandle(document));

    QueryManager queryMgr = Common.client.newQueryManager();

    StringQueryDefinition qdef = queryMgr.newStringDefinition("photos");
    qdef.setCriteria("Grand");

    SearchHandle results = queryMgr.search(qdef, new SearchHandle());
    assertNotNull(results);

    FacetResult[] facets = results.getFacetResults();

    assertNotNull(facets);

  }

}
