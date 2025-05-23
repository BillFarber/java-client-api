/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */

package com.marklogic.client.functionaltest;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Product
{
  String name;
  String industry;
  String description;

  public Product()
  {
    super();
  }

  public String getName()
  {
    return name;
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public String getIndustry()
  {
    return industry;
  }

  public void setIndustry(String industry)
  {
    this.industry = industry;
  }

  public String getDescription()
  {
    return description;
  }

  public void setDescription(String description)
  {
    this.description = description;
  }
}
