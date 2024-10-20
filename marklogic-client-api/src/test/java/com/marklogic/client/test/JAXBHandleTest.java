/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.test;

import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.io.JAXBHandle;
import com.marklogic.client.test.util.Referred;
import com.marklogic.client.test.util.Refers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JAXBHandleTest {
  @BeforeAll
  public static void beforeClass() {
    Common.connect();
  }
  @AfterAll
  public static void afterClass() {
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testReadWriteJAXB() throws JAXBException {
    String docId = "/test/jaxbWrite1.xml";

    Map<String,Integer> map = new HashMap<>();
    map.put("alpha", 1);
    map.put("beta",  2);
    map.put("gamma", 3);
    List<String> list = new ArrayList<>(3);
    list.add("apple");
    list.add("banana");
    list.add("cactus");
    Refers refers = new Refers();
    refers.child = new Referred();
    refers.map   = map;
    refers.list  = list;

    JAXBContext context = JAXBContext.newInstance(Refers.class);

    XMLDocumentManager docMgr = Common.client.newXMLDocumentManager();

    // First with raw types -- needed to support multiple classes with one handle
    @SuppressWarnings("rawtypes")
    JAXBHandle objectHandle = new JAXBHandle(context);

    docMgr.write(docId, objectHandle.with(refers));

    Refers refers2 = (Refers) docMgr.read(docId, objectHandle).get();
    assertTrue( refers2 != null);
    assertEquals( refers.name, refers2.name);
    assertTrue( refers2.child != null);
    assertEquals( refers.child.name, refers2.child.name);
    assertTrue( refers2.map != null);
    assertEquals( refers.map.size(), refers2.map.size());
    assertTrue( refers2.list != null);
    assertEquals( refers.list.size(), refers2.list.size());

    // Again with a type token -- useful for convenience and strong typing
    JAXBHandle<Refers> objectHandle2 = new JAXBHandle<>(context, Refers.class);

    docMgr.write(docId, objectHandle2.with(refers));

    refers2 = docMgr.read(docId, objectHandle2).get();
    assertTrue( refers2 != null);
    assertEquals( refers.name, refers2.name);
    assertTrue( refers2.child != null);
    assertEquals( refers.child.name, refers2.child.name);
    assertTrue( refers2.map != null);
    assertEquals( refers.map.size(), refers2.map.size());
    assertTrue( refers2.list != null);
    assertEquals( refers.list.size(), refers2.list.size());
  }
}
