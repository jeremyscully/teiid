/*
 * JBoss, Home of Professional Open Source.
 * Copyright (C) 2008 Red Hat, Inc.
 * Copyright (C) 2000-2007 MetaMatrix, Inc.
 * Licensed to Red Hat, Inc. under one or more contributor 
 * license agreements.  See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.connector.internal;

public class ConnectorPropertyNames {

   /**
    * The property that specifies the class name of the custom connector class
    * that connects to the data source.  This property is required.
    */
    public static final String CONNECTOR_CLASS = "ConnectorClass"; //$NON-NLS-1$

    /**
     * The environment property name whose value defines the maximum number
     * of processor threads.  This property is required.
     */
    public static final String MAX_THREADS = "ConnectorMaxThreads"; //$NON-NLS-1$

    /**
     * The environment property name whose value defines the maximum length of time
	 * a processor thread may live idle.  This property is required and is in milliseconds.
     */
    public static final String THREAD_TTL = "ConnectorThreadTTL"; //$NON-NLS-1$

	/**
	 * This property can be used to specify the maximum number of rows to be returned
	 * from the datasource of this connector. The connector should stop adding records
	 * to the ResultsCollector when the rows collected is equal to this value.  This
	 * property is optional and if no value is specified, we should return all the rows.
	 * @since 3.0
	 */
	public static final String MAX_RESULT_ROWS = "MaxResultRows"; //$NON-NLS-1$

    /**
     * This property can be used to specify whether or not an exception should be thrown
     * if the number of rows for a query exceeds the value of MAX_RESULT_ROWS.  If this
     * flag is set to false, then no more than MAX_RESULT_ROWS values will be returned but
     * no exception will be thrown.
     */
    public static final String EXCEPTION_ON_MAX_ROWS = "ExceptionOnMaxRows"; //$NON-NLS-1$
    
    /**
     * This property can be used to specify whether or not Connection Pooling is enabled.
     * If this flag is set to false, then connection pooling is disabled.
     */
    public static final String CONNECTION_POOL_ENABLED = "ConnectionPoolEnabled"; //$NON-NLS-1$

    /**
     * The environment property used to identify a <i>type</i> of connector binding.
     * This property is required and is a component used to uniquely identify a
     * connector instance.
     * @since 4.0
     */
    public static final String CONNECTOR_BINDING_NAME = "ConnectorBindingName"; //$NON-NLS-1$

    /**
     * The environment property used to identify a routing ID.  This value should
     * be used to locate this particular connector from the DQP.
     * @since 4.0
     */
    public static final String CONNECTOR_ID = "ConnectorID"; //$NON-NLS-1$
    
    /**
     * The environment property used as a flag to deregister the driver
     */
    public static final String DEREGISTER_DRIVER = "deregisterDriver"; //$NON-NLS-1$
    
    /**
     * The environment property used as a flag to deregister the driver by ClassLoader
     */
    public static final String DEREGISTER_BY_CLASSLOADER = "deregisterByClassLoader"; //$NON-NLS-1$
    
    /**
     * The environment property used as a flag to deregister the driver by ClassName
     */
    public static final String DEREGISTER_BY_CLASSNAME = "deregisterByClassName"; //$NON-NLS-1$
    
    /**
     * The environment property used to identify a class loader to load connector.
     * The default class loader will be used if it is not defined.
     * This is an optional property.
     * @since 4.0
     */
    public static final String CONNECTOR_CLASS_LOADER = "ConnectorClassLoader"; //$NON-NLS-1$

    /**
     * The name of the VM where the connector is running on.
     * @since 4.0
     */
    public static final String CONNECTOR_VM_NAME = "ConnectorVMName"; //$NON-NLS-1$

    public static final String USE_RESULTSET_CACHE = "ResultSetCacheEnabled"; //$NON-NLS-1$
    public static final String MAX_RESULTSET_CACHE_SIZE = "ResultSetCacheMaxSize"; //$NON-NLS-1$
    public static final String MAX_RESULTSET_CACHE_AGE = "ResultSetCacheMaxAge"; //$NON-NLS-1$
    public static final String RESULTSET_CACHE_SCOPE = "ResultSetCacheScope"; //$NON-NLS-1$

    /**
     * Indicates whether the connector represents a pooled resource.  If it does, then
     * synchronous workers will be used.
     */
    public static final String SYNCH_WORKERS = "SynchWorkers"; //$NON-NLS-1$
}
