/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.datatype.Duration;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import com.marklogic.client.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.marklogic.client.MarkLogicIOException;
import com.marklogic.client.MarkLogicInternalException;
import com.marklogic.client.io.marker.BufferableHandle;
import com.marklogic.client.io.marker.DocumentMetadataReadHandle;
import com.marklogic.client.io.marker.DocumentMetadataWriteHandle;
import com.marklogic.client.util.NameMap;

/**
 * A DocumentMetadataHandle represents the metadata for a database document.
 */
public class DocumentMetadataHandle
  extends BaseHandle<InputStream, OutputStreamSender>
  implements OutputStreamSender, BufferableHandle, DocumentMetadataReadHandle, DocumentMetadataWriteHandle
{
  final static private Logger logger = LoggerFactory.getLogger(DOMHandle.class);

  final static private String REST_API_NS     = "http://marklogic.com/rest-api";
  final static private String PROPERTY_API_NS = "http://marklogic.com/xdmp/property";

  /**
   * Represents the collections for a database document.
   */
  public interface DocumentCollections extends Set<String> {
    /**
     * Adds one or more collections to the metadata that can be written
     * for the document.
     * @param collections	the document collections
     */
    public void addAll(String... collections);
  }
  @SuppressWarnings("serial")
  static private class CollectionsImpl extends HashSet<String> implements DocumentCollections {
    @Override
    public void addAll(String... collections) {
      if (collections == null || collections.length < 1)
        return;

      for (String collection: collections)
        add(collection);
    }
  }

	/**
	 * Represents the permissions for a database document.
	 */
	public interface DocumentPermissions extends Map<String, Set<Capability>> {
		/**
		 * Adds a role with one or more capabilities to the metadata that can be written
		 * for the document.
		 *
		 * @param role         the role for users permitted to access the document
		 * @param capabilities the permissions to be granted to users with the role
		 */
		void add(String role, Capability... capabilities);

		/**
		 * Adds one or more permissions based on the given comma-delimited string. Each capability value is
		 * case-insensitive; you do not need to worry about providing the correct case. Similar to {@code add}, this
		 * method adds permissions and can thus add capabilities to roles already present in this object.
		 *
		 * For example, the following string would add two permissions - a "read" permission for "rest-reader" and an
		 * "update" permission for "rest-writer": rest-reader,read,rest-writer,update.
		 *
		 * @param commaDelimitedRolesAndCapabilities comma-delimited string of the pattern: role1,capability1,role2,capability2,etc.
		 * @since 6.3.0
		 */
		void addFromDelimitedString(String commaDelimitedRolesAndCapabilities);
	}

  @SuppressWarnings("serial")
  static private class PermissionsImpl extends HashMap<String,Set<Capability>> implements DocumentPermissions {
    @Override
    public void add(String role, Capability... capabilities) {
      if (capabilities == null || capabilities.length < 1)
        return;

      Set<Capability> caps = new HashSet<>(capabilities.length);
      for (Capability capability: capabilities)
        caps.add(capability);

      put(role, caps);
    }
    @SuppressWarnings("unused")
    public void add(String role, Capability capability) {
      if (containsKey(role)) {
        get(role).add(capability);
      } else {
        Set<Capability> caps = new HashSet<>();
        caps.add(capability);
        put(role, caps );
      }
    }

	  /**
	   *
	   * @param commaDelimitedRolesAndCapabilities comma-delimited string of the pattern: role1,capability1,role2,capability2,etc.
	   * @since 6.3.0
	   */
	  @Override
	  public void addFromDelimitedString(String commaDelimitedRolesAndCapabilities) {
		  if (commaDelimitedRolesAndCapabilities != null && commaDelimitedRolesAndCapabilities.trim().length() > 0) {
			  String[] tokens = commaDelimitedRolesAndCapabilities.trim().split(",");
			  for (int i = 0; i < tokens.length; i += 2) {
				  String role = tokens[i];
				  if (i + 1 >= tokens.length) {
					  throw new IllegalArgumentException(String.format(
						  "Unable to parse permissions string, which must be a comma-delimited " +
							  "list of role names and capabilities - i.e. role1,read,role2,update,role3,execute; string: %s",
						  commaDelimitedRolesAndCapabilities));
				  }
				  Capability c;
				  try {
					  c = Capability.getValueOf(tokens[i + 1]);
				  } catch (Exception e) {
					  throw new IllegalArgumentException(String.format(
						  "Unable to parse permissions string: %s; cause: %s",
						  commaDelimitedRolesAndCapabilities, e.getMessage()));
				  }
				  if (this.containsKey(role)) {
					  this.get(role).add(c);
				  } else {
					  this.add(role, c);
				  }
			  }
		  }
	  }
  }

  /**
   * A document operation restricted to users with a role.
   */
  public enum Capability {
    /**
     * Capability to execute the document.
     */
    EXECUTE,
    /**
     * Capability to create but not modify the document.
     */
    INSERT,
    /**
     * Capability to read the document.
     */
    READ,
    /**
     * Capability to create or modify the document.
     */
    UPDATE,
    /**
     * Capability to update nodes within the document or properties of the document.
     */
    NODE_UPDATE;

    @Override
    public String toString() {
      return super.toString().replace('_', '-');
    }

    public static final Capability getValueOf(String name) {
      if ( name != null ) {
        name = name.replace('-', '_').toUpperCase();
      }
      return valueOf(name);
    }
  }

  /**
   * Represents the properties for a database document.
   */
  public interface DocumentProperties extends NameMap<Object> {
    /**
     * Sets a document property to a BigDecimal value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, BigDecimal value);
    /**
     * Sets a document property to a BigInteger value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, BigInteger value);
    /**
     * Sets a document property to a boolean value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Boolean value);
    /**
     * Sets a document property to a byte value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Byte value);
    /**
     * Sets a document property to a byte array value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, byte[] value);
    /**
     * Sets a document property to a Calendar value, which can
     * express a date, a time, or a datetime.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Calendar value);
    /**
     * Sets a document property to a double value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Double value);
    /**
     * Sets a document property to a Duration value,
     * which can also express a year-month or day-millisecond duration.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Duration value);
    /**
     * Sets a document property to a float value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Float value);
    /**
     * Sets a document property to an int value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Integer value);
    /**
     * Sets a document property to a long value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Long value);
    /**
     * Sets a document property to a NodeList value, which
     * can accommodate subelements or mixed content.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, NodeList value);
    /**
     * Sets a document property to a short value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, Short value);
    /**
     * Sets a document property to a string value.
     * @param name	the property name
     * @param value	the property value
     * @return	the previous value of the property (if any)
     */
    public Object put(QName name, String value);
  }
  @SuppressWarnings("serial")
  static private class PropertiesImpl extends ClientPropertiesImpl implements DocumentProperties {
    private PropertiesImpl() {
      super();
    }


  }

  /**
   * Represents the metadata-values for a database document.
   */
  public interface DocumentMetadataValues extends Map<String, String> {
    /**
     * Adds metadata-value that can be written for the document.
     *
     * @param key	the key of the metadata-value
     * @param value	the value of the metadata-value
     */
    public void add(String key, String value);
  }

  @SuppressWarnings("serial")
  static private class ValuesImpl extends HashMap<String, String> implements DocumentMetadataValues {
    @Override
    public void add(String key, String value) {
      put(key, value);
    }
  }

  private DocumentCollections    collections;
  private DocumentPermissions    permissions;
  private DocumentProperties     properties;
  private int                    quality = 0;
  private DocumentMetadataValues metadataValues;
  private boolean                qualityModified = false;

  /**
   * Zero-argument constructor.
   */
  public DocumentMetadataHandle() {
    super();
    super.setFormat(Format.XML);
    setResendable(true);
  }

  /**
   * Returns a container for the collections for the document
   * as read from the server or modified locally.
   * @return	the document collections
   */
  public DocumentCollections getCollections() {
    if (collections == null)
      collections = new CollectionsImpl();
    return collections;
  }
  /**
   * Locally assigns a container with document collections.
   * Ordinarily, you never change the container but, instead,
   * modify the collections stored by the container.
   * @param collections	the document collections
   */
  public void setCollections(DocumentCollections collections) {
    this.collections = collections;
  }
  /**
   * Locally adds the collections to the current collections
   * for the document.
   * @param collections	the document collections
   * @return	the document metadata handle
   */
  public DocumentMetadataHandle withCollections(String... collections) {
    getCollections().addAll(collections);
    return this;
  }

  /**
   * Returns a container for the permissions on the document
   * as read from the server or modified locally.
   * @return	the document permissions
   */
  public DocumentPermissions getPermissions() {
    if (permissions == null)
      permissions = new PermissionsImpl();
    return permissions;
  }
  /**
   * Locally assigns a container with document permissions.
   * Ordinarily, you never change the container but, instead,
   * modify the permissions stored by the container.
   * @param permissions	the document permissions
   */
  public void setPermissions(DocumentPermissions permissions) {
    this.permissions = permissions;
  }
  /**
   * Locally adds the role and its capabilities to the current
   * permissions for the document.
   * @param role	the role for users permitted to access the document
   * @param capabilities	the permissions to be granted to users with the role
   * @return	the document metadata handle
   */
  public DocumentMetadataHandle withPermission(
    String role, Capability... capabilities
  ) {
    getPermissions().add(role, capabilities);
    return this;
  }

  /**
   * Returns a container for the properties of the document
   * as read from the server or modified locally.
   * @return	the document properties
   */
  public DocumentProperties getProperties() {
    if (properties == null)
      properties = new PropertiesImpl();
    return properties;
  }
  /**
   * Locally assigns a container with document properties.
   * Ordinarily, you never change the container but, instead,
   * modify the properties stored by the container.
   * @param properties	the document properties
   */
  public void setProperties(DocumentProperties properties) {
    this.properties = properties;
  }
  /**
   * Locally adds the property name and value to the current
   * properties for the document.
   * @param name	the namespaced QName identifying the property
   * @param value	the value of the property
   * @return	the document metadata handle
   */
  public DocumentMetadataHandle withProperty(QName name, Object value) {
    getProperties().put(name, value);
    return this;
  }
  /**
   * Locally adds the property name and value to the current
   * properties for the document.
   * @param name	the simple string name identifying the property
   * @param value	the value of the property
   * @return	the document metadata handle
   */
  public DocumentMetadataHandle withProperty(String name, Object value) {
    getProperties().put(name, value);
    return this;
  }

  /**
   * Returns the quality of the document.
   * @return	the document quality
   */
  public int getQuality() {
    return quality;
  }
  /**
   * Specifies the quality of the document, which affects search weighting.
   * @param quality	the document quality
   */
  public void setQuality(int quality) {
    this.quality = quality;
    this.qualityModified = true;
  }
  /**
   * Locally specifies the match quality for the document.
   * @param quality	the document quality
   * @return	the document metadata handle
   */
  public DocumentMetadataHandle withQuality(int quality) {
    setQuality(quality);
    return this;
  }

  /**
   * Returns a container for the metadata-values for the document as read from
   * the server or modified locally.
   *
   * @return	the document metadata-values
   */
  public DocumentMetadataValues getMetadataValues() {
    if (metadataValues == null) {
      metadataValues = new ValuesImpl();
    }
    return metadataValues;
  }

  /**
   * Locally assigns a container with document metadata-values. Ordinarily, you never
   * change the container but, instead, modify the metadata-values stored by the
   * container.
   *
   * @param metadataValues	the document metadata-values
   */
  public void setMetadataValues(DocumentMetadataValues metadataValues) {
    this.metadataValues = metadataValues;
  }

  /**
   * Locally adds the metadata-value to the current metadata-values for the document.
   *
   * @param key	the key of metadata-value
   * @param value	the value of metadata-value
   * @return	 the document metadata handle
   */
  public DocumentMetadataHandle withMetadataValue(String key, String value) {
    getMetadataValues().put(key, value);
    return this;
  }

  /**
   * Restricts the format used parsing and serializing the metadata.
   */
  @Override
  public void setFormat(Format format) {
    if (format != Format.XML)
      throw new IllegalArgumentException("DocumentMetadataHandle supports the XML format only");
  }

  /**
   * fromBuffer() populates DocumentMetadataHandle from a byte array
   * buffer.  The buffer must store document metadata in XML format
   * in the UTF-8 encoding.
   */
  @Override
  public void fromBuffer(byte[] buffer) {
    if (buffer == null || buffer.length == 0)
      receiveContent(null);
    else
      receiveContent(new ByteArrayInputStream(buffer));
  }
  @Override
  public byte[] toBuffer() {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      write(buffer);

      return buffer.toByteArray();
    } catch (IOException e) {
      throw new MarkLogicIOException(e);
    }
  }
  /**
   * Returns the document metadata as an XML string.
   */
  @Override
  public String toString() {
    byte[] buffer = toBuffer();
    return (buffer == null) ? null : new String(buffer, StandardCharsets.UTF_8);
  }

  @Override
  protected Class<InputStream> receiveAs() {
    return InputStream.class;
  }
  @Override
  protected void receiveContent(InputStream content) {
    try {
      if (logger.isInfoEnabled())
        logger.info("Parsing metadata structure from input stream");

      Document document = null;
      if (content != null) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.parse(new InputSource(new InputStreamReader(content, StandardCharsets.UTF_8)));
        content.close();
      }

      receiveMetadataImpl(document);
    } catch (SAXException e) {
      logger.error("Failed to parse metadata structure from input stream",e);
      throw new MarkLogicInternalException(e);
    } catch (IOException e) {
      logger.error("Failed to parse metadata structure from input stream",e);
      throw new MarkLogicInternalException(e);
    } catch (ParserConfigurationException e) {
      logger.error("Failed to parse metadata structure from input stream",e);
      throw new MarkLogicInternalException(e);
    }
  }
  @Override
  protected OutputStreamSender sendContent() {
    return this;
  }
  @Override
  public void write(OutputStream out) throws IOException {
    sendMetadataImpl(out);
  }

  private void receiveMetadataImpl(Document document) {
    receiveCollectionsImpl(document);
    receivePermissionsImpl(document);
    receivePropertiesImpl(document);
    receiveQualityImpl(document);
    receiveMetadataValuesImpl(document);
  }

  private void receiveMetadataValuesImpl(Document document) {
    DocumentMetadataValues metadataValues = getMetadataValues();
    metadataValues.clear();

    if (document == null)
      return;

    NodeList valuesIn = document.getElementsByTagNameNS(REST_API_NS, "metadata-values");
    int valuesInLength = valuesIn.getLength();
    for (int i = 0; i < valuesInLength; i++) {
      String key = null;
      String value = null;

      NodeList children = valuesIn.item(i).getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
        Node node = children.item(j);
        if (node.getNodeType() != Node.ELEMENT_NODE)
          continue;
        Element element = (Element) node;

        if ("metadata-value".equals(element.getLocalName())) {
          key = element.getAttribute("key");
          value = element.getTextContent();
        } else if (logger.isWarnEnabled())
          logger.warn("Skipping unknown value element", element.getTagName());
        if (key == null || value == null) {
          if (logger.isWarnEnabled())
            logger.warn("Could not parse value");
          continue;
        }

        metadataValues.put(key, value);
      }
    }
  }

  private void receiveCollectionsImpl(Document document) {
    DocumentCollections collections = getCollections();
    collections.clear();

    if (document == null)
      return;

    NodeList collectionsIn = document.getElementsByTagNameNS(REST_API_NS, "collection");
    int collectionsInLength = collectionsIn.getLength();
    for (int i=0; i < collectionsInLength; i++) {
      collections.add(collectionsIn.item(i).getTextContent());
    }
  }
  private void receivePermissionsImpl(Document document) {
    DocumentPermissions permissions = getPermissions();
    permissions.clear();

    if (document == null)
      return;

    NodeList permissionsIn = document.getElementsByTagNameNS(REST_API_NS, "permission");
    int permissionsInLength = permissionsIn.getLength();
    for (int i=0; i < permissionsInLength; i++) {
      String roleName = null;
      Set<Capability> caps = new HashSet<>();

      NodeList children = permissionsIn.item(i).getChildNodes();
      for (int j=0; j < children.getLength(); j++) {
        Node node = children.item(j);
        if (node.getNodeType() != Node.ELEMENT_NODE)
          continue;
        Element element = (Element) node;

        if ("role-name".equals(element.getLocalName()))
          roleName = element.getTextContent();
        else if ("capability".equals(element.getLocalName()))
          caps.add(Capability.getValueOf(element.getTextContent()));
        else if (logger.isWarnEnabled())
          logger.warn("Skipping unknown permission element", element.getTagName());
      }

      if (roleName == null || caps.size() == 0) {
        if (logger.isWarnEnabled())
          logger.warn("Could not parse permission");
        continue;
      }

      permissions.put(roleName, caps);
    }
  }
  private void receivePropertiesImpl(Document document) {
    DocumentProperties properties = getProperties();
    properties.clear();

    if (document == null)
      return;

    Node propertyContainer = document.getElementsByTagNameNS(PROPERTY_API_NS, "properties").item(0);
    if (propertyContainer == null)
      return;

    NodeList propertiesIn = propertyContainer.getChildNodes();
    for (int i=0; i < propertiesIn.getLength(); i++) {
      Node node = propertiesIn.item(i);
      if (node.getNodeType() != Node.ELEMENT_NODE)
        continue;
      Element property = (Element) node;

      QName propertyName = null;

      String namespaceURI = property.getNamespaceURI();
      if (namespaceURI != null) {
        String prefix    = property.getPrefix();
        if (prefix != null) {
          propertyName = new QName(namespaceURI, property.getLocalName(), prefix);
        } else {
          propertyName = new QName(namespaceURI, property.getTagName());
        }
      } else {
        propertyName = new QName(property.getTagName());
      }

      if (!property.hasChildNodes()) {
        properties.put(propertyName, (String) null);
        continue;
      }

      NodeList children = property.getChildNodes();
      boolean hasChildElements = false;
      int childCount = children.getLength();
      for (int j=0; j < childCount; j++) {
        Node child = children.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          hasChildElements = true;
          break;
        }
      }
      if (hasChildElements) {
        properties.put(propertyName, children);
        continue;
      }

      // TODO: casting known properties such as prop:last-modified

      String value = property.getTextContent();
      if (property.hasAttributeNS(
        XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type")) {
        String type = property.getAttributeNS(
          XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        properties.put(propertyName, ValueConverter.convertToJava(type, value));
        continue;
      } else {
        properties.put(propertyName, value);
      }

      properties.put(propertyName, value);
    }
  }
  private void receiveQualityImpl(Document document) {
    if (document == null) {
      setQuality(0);
      return;
    }

    Node quality = document.getElementsByTagNameNS(REST_API_NS, "quality").item(0);
    if (quality == null) {
      setQuality(0);
      return;
    }

    String qualityText = quality.getTextContent();

    int qualityNum = 0;
    try {
      qualityNum = Utilities.parseInt(qualityText);
      if (qualityNum < 0) {
        qualityNum = 0;
      }
    } catch(NumberFormatException ex) {
      if (logger.isWarnEnabled())
        logger.warn("Could not parse quality integer from", qualityText);
    }

    setQuality(qualityNum);
  }

  // TODO: select the metadata sent
  private void sendMetadataImpl(OutputStream out) {
    try {
      XMLOutputFactory factory = XmlFactories.getOutputFactory();

      XMLStreamWriter serializer = factory.createXMLStreamWriter(out, "UTF-8");

      serializer.writeStartDocument("utf-8", "1.0");

      serializer.writeStartElement("rapi", "metadata", REST_API_NS);
      serializer.writeNamespace("rapi", REST_API_NS);
      serializer.writeNamespace("prop", PROPERTY_API_NS);
      if ( properties != null ) {
        serializer.writeNamespace("xsi",  XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
        serializer.writeNamespace("xs",   XMLConstants.W3C_XML_SCHEMA_NS_URI);
      }

      sendCollectionsImpl(serializer);
      sendPermissionsImpl(serializer);
      sendPropertiesImpl(serializer);
      sendQualityImpl(serializer);
      sendMetadataValuesImpl(serializer);
      serializer.writeEndElement();

      serializer.writeEndDocument();

      serializer.flush();
      serializer.close();
    } catch (Exception e) {
		throw new MarkLogicIOException("Failed to serialize metadata: cause: " + e.getMessage(), e);
    }
  }

  private void sendMetadataValuesImpl(XMLStreamWriter serializer) throws XMLStreamException {
    if (getMetadataValues() == null || getMetadataValues().size() == 0)
      return;
    serializer.writeStartElement(REST_API_NS, "metadata-values");

    for (Map.Entry<String, String> value : getMetadataValues().entrySet()) {
      serializer.writeStartElement("rapi", "metadata-value", REST_API_NS);
      serializer.writeAttribute("key", value.getKey());
      if(value.getValue() != null)
        serializer.writeCharacters(value.getValue());
      serializer.writeEndElement();
    }

    serializer.writeEndElement();
  }

  private void sendCollectionsImpl(XMLStreamWriter serializer) throws XMLStreamException {
    if ( getCollections() == null || getCollections().size() == 0 ) return;
    serializer.writeStartElement("rapi", "collections", REST_API_NS);

    for (String collection: getCollections()) {
      serializer.writeStartElement("rapi", "collection", REST_API_NS);
      if(collection != null)
        serializer.writeCharacters(collection);
      serializer.writeEndElement();
    }

    serializer.writeEndElement();
  }
  private void sendPermissionsImpl(XMLStreamWriter serializer) throws XMLStreamException {
    if ( getPermissions() == null || getPermissions().size() == 0 ) return;
    serializer.writeStartElement("rapi", "permissions", REST_API_NS);

    for (Map.Entry<String, Set<Capability>> permission: getPermissions().entrySet()) {
      serializer.writeStartElement("rapi", "permission", REST_API_NS);

      serializer.writeStartElement("rapi", "role-name", REST_API_NS);
      if(permission.getKey() != null)
        serializer.writeCharacters(permission.getKey());
      serializer.writeEndElement();

      for (Capability capability: permission.getValue()) {
        serializer.writeStartElement("rapi", "capability", REST_API_NS);
        serializer.writeCharacters(capability.toString().toLowerCase());
        serializer.writeEndElement();
      }

      serializer.writeEndElement();
    }

    serializer.writeEndElement();
  }
  private void sendPropertiesImpl(final XMLStreamWriter serializer) throws XMLStreamException, TransformerFactoryConfigurationError, TransformerException {
    if ( getProperties() == null || getProperties().size() == 0 ) return;

    ValueSerializer valueSerializer = null;

    serializer.writeStartElement("prop", "properties", PROPERTY_API_NS);

    for (Map.Entry<QName, Object> property: getProperties().entrySet()) {
      QName  propertyName = property.getKey();
      Object value        = property.getValue();

      boolean hasNodeValue = value instanceof NodeList;

      String namespaceURI = propertyName.getNamespaceURI();
      String prefix       = null;
      String localPart    = propertyName.getLocalPart();
      if (namespaceURI != null && namespaceURI.length() > 0) {
        if (PROPERTY_API_NS.equals(namespaceURI))
          continue;

        prefix = propertyName.getPrefix();

        serializer.writeStartElement(prefix, localPart, namespaceURI);
      } else {
        serializer.writeStartElement(localPart);
      }

      if (!hasNodeValue) {
        if (valueSerializer == null)
          valueSerializer = new ValueSerializer(serializer);

        ValueConverter.convertFromJava(value, valueSerializer);
      } else {
        new DOMWriter(serializer).serializeNodeList((NodeList) value);
      }

      serializer.writeEndElement();
    }

    serializer.writeEndElement();
  }
  private void sendQualityImpl(XMLStreamWriter serializer) throws XMLStreamException {
    if ( qualityModified == false ) return;
    serializer.writeStartElement("rapi", "quality", REST_API_NS);
    serializer.writeCharacters(String.valueOf(getQuality()));
    serializer.writeEndElement();
  }
  static private class ValueSerializer implements ValueConverter.ValueProcessor {
    private XMLStreamWriter serializer;
    public ValueSerializer(XMLStreamWriter serializer) {
      super();
      this.serializer = serializer;
    }
    @Override
    public void process(Object original, String type, String value) {
      if (original == null)
        return;
      try {
        serializer.writeAttribute(
          "xsi",  XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type", type);
        if(value != null)
          serializer.writeCharacters(value);
      } catch(XMLStreamException e) {
        throw new MarkLogicIOException(e);
      }
    }

  }
}
