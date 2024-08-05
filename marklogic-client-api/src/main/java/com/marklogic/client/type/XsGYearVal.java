/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.type;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * An instance of a server GYear value.
 */
public interface XsGYearVal extends XsAnyAtomicTypeVal, XsGYearSeqVal, PlanParamBindingVal {
    public XMLGregorianCalendar getXMLGregorianCalendar();
}
