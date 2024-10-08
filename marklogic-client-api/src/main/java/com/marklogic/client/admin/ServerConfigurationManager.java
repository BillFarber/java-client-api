/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.admin;

import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.ResourceNotResendableException;

/**
 * The ServerConfigurationManager reads and writes the configurable properties
 * of the server.
 */
public interface ServerConfigurationManager {
  /**
   * Specifies the policy for updating documents in the database.
   */
  public enum UpdatePolicy {
    /**
     * A document can be updated or deleted only if its version number
     * is supplied and matches; a document can be created only if no
     * version number is supplied and the document doesn't exist. This
     * update policy requires optimistic locking.
     */
    VERSION_REQUIRED,
    /**
     * A document can be updated or deleted if its version number is
     * not supplied or if the version number matches. This update policy
     * provides for optional optimistic locking.
     */
    VERSION_OPTIONAL,
    /**
     * If a document exists, the supplied metadata is merged with the
     * persisted document metadata. This update policy is the default
     * (equivalent to content versions of Policy.NONE).
     */
    MERGE_METADATA,
    /**
     * The document is written without testing whether it exists. Only
     * the supplied metadata is persisted. This update policy is slightly
     * faster than other update policies.
     */
    OVERWRITE_METADATA
  }
  /**
   * Specifies the policy for use of a capability.
   */
  public enum Policy {
    /**
     * Specifies that the capability must be used.
     */
    REQUIRED,
    /**
     * Specifies that the capability may be used.
     */
    OPTIONAL,
    /**
     * Specifies that the capability may not be used.
     */
    NONE;
  }

  /**
   * Reads the values of the properties from the server into this object.
   */
  void readConfiguration()
    throws FailedRequestException, ForbiddenUserException;
  /**
   * Writes the values of the properties of this object to the server.
   */
  void writeConfiguration()
    throws FailedRequestException, ResourceNotFoundException, ResourceNotResendableException, ForbiddenUserException;

  /**
   * Returns whether the server validates query options before storing them.
   * @return	true if query options are validated
   */
  Boolean getQueryOptionValidation();
  /**
   * Specifies whether the server validates query options before storing them.
   * @param on	set to true to validate the query options
   */
  void setQueryOptionValidation(Boolean on);

  /**
   * Returns whether the server validates queries before running them.
   * @return true if queries are validated
   */
  Boolean getQueryValidation();

  /**
   * Specifies whether the server validates queries before running them.
   * @param on	set to true to validate queries
   */
  void setQueryValidation(Boolean on);

  /**
   * Returns the name of the default transform for reading documents.
   * @return	the default transform name
   */
  String getDefaultDocumentReadTransform();
  /**
   * Specifies the name of the default transform for reading documents.
   * The default transform is applied before any transform specified
   * on the read request.
   * @param name	the default transform name
   */
  void setDefaultDocumentReadTransform(String name);

  /**
   * Returns whether the default transform for reading documents
   * applies to all users or only users with the rest-reader role.
   * @return	true if the default read transform applies to all users
   */
  Boolean getDefaultDocumentReadTransformAll();
  /**
   * Specifies whether the default transform for reading documents
   * applies to all users or only users with the rest-reader role.
   * @param on	true to apply the default read transform to all users
   */
  void setDefaultDocumentReadTransformAll(Boolean on);

  /**
   * Returns whether the server logs requests to the error log on the server.
   * @return	true if the server logs requests
   */
  Boolean getServerRequestLogging();
  /**
   * Specifies whether the server logs requests to the error log on the server.
   * @param on	true to log requests on the server
   */
  void setServerRequestLogging(Boolean on);

  /**
   * Returns the policy for updating or deleting documents in the database.
   * @return	the policy controlling updates or deletes
   */
  UpdatePolicy getUpdatePolicy();
  /**
   * Specifies the policy for updating or deleting documents in the database.
   * @param policy	the policy controlling updates or deletes
   */
  void setUpdatePolicy(UpdatePolicy policy);

  /**
   * Creates a manager for listing, reading, writing, and deleting query options.
   * @return	a new manager for query options
   */
  QueryOptionsManager        newQueryOptionsManager();
  /**
   * Creates a manager for listing, reading, writing, and deleting
   * resource service extensions.
   * @return	a new manager for resource service extensions
   */
  ResourceExtensionsManager  newResourceExtensionsManager();
  /**
   * Creates a manager for listing, reading, writing, and deleting
   * transform extensions.
   * @return	a new manager for transform extensions
   */
  TransformExtensionsManager newTransformExtensionsManager();
  /**
   * Creates a manager for writing and reading assets in the REST
   * server's modules database.
   * @return The extensions manager.
   */
  ExtensionLibrariesManager newExtensionLibrariesManager();

}
