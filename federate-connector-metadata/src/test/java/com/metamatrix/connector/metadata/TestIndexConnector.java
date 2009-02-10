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

package com.metamatrix.connector.metadata;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import junit.framework.TestCase;

import com.metamatrix.cdk.CommandBuilder;
import com.metamatrix.cdk.api.EnvironmentUtility;
import com.metamatrix.common.application.ApplicationEnvironment;
import com.metamatrix.common.application.ApplicationService;
import com.metamatrix.connector.api.Connection;
import com.metamatrix.connector.api.ConnectorEnvironment;
import com.metamatrix.connector.api.DataNotAvailableException;
import com.metamatrix.connector.api.ExecutionContext;
import com.metamatrix.connector.api.ResultSetExecution;
import com.metamatrix.connector.exception.ConnectorException;
import com.metamatrix.connector.language.IQuery;
import com.metamatrix.connector.metadata.adapter.ObjectConnector;
import com.metamatrix.connector.metadata.internal.IObjectSource;
import com.metamatrix.connector.metadata.internal.TestConnectorHost;
import com.metamatrix.connector.metadata.internal.TestObjectQueryProcessor;
import com.metamatrix.connector.metadata.runtime.RuntimeMetadata;
import com.metamatrix.core.MetaMatrixRuntimeException;
import com.metamatrix.dqp.internal.datamgr.impl.ConnectorEnvironmentImpl;
import com.metamatrix.dqp.internal.datamgr.impl.ExecutionContextImpl;
import com.metamatrix.dqp.service.DQPServiceNames;
import com.metamatrix.metadata.runtime.FakeMetadataService;
import com.metamatrix.metadata.runtime.FakeQueryMetadata;
import com.metamatrix.query.metadata.QueryMetadataInterface;

public class TestIndexConnector extends TestCase {
    private FakeMetadataService fakeApplicationService = null;
    
    public TestIndexConnector(String name) {
        super(name);
    }

    public void test() throws Exception {
        IndexConnector connector = new IndexConnector();
        connector.start(helpGetConnectorEnvironment());
        Connection connection = connector.getConnection(helpGetSecurityContext());
        QueryMetadataInterface metadata = FakeQueryMetadata.getQueryMetadata();
        CommandBuilder commandBuilder = new CommandBuilder(metadata);
        IQuery command = (IQuery) commandBuilder.getCommand("select FullName from tables"); //$NON-NLS-1$

        RuntimeMetadata runtimeMetadata = TestObjectQueryProcessor.getRuntimeMetadata();
        ExecutionContext envContext = EnvironmentUtility.createExecutionContext("100", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        ResultSetExecution execution = (ResultSetExecution)connection.createExecution(command, envContext, runtimeMetadata);            
        execution.execute();
        int i = getSize(execution);
        assertEquals(5, i);
        execution.close();
    }
    
    public void testBatches() throws Exception {
        ObjectConnector connector = new ObjectConnector() {

            public IObjectSource getMetadataObjectSource(ExecutionContext context) {
                return new IObjectSource() {
					public Collection getObjects(String groupName, Map criteria) {
						return Collections.nCopies(30, 1);
					}
                };
            }

        };

        connector.start(helpGetConnectorEnvironment());
        Connection connection = connector.getConnection(helpGetSecurityContext());
        QueryMetadataInterface metadata = FakeQueryMetadata.getQueryMetadata();
        CommandBuilder commandBuilder = new CommandBuilder(metadata);
        IQuery command = (IQuery) commandBuilder.getCommand("select toString from junk"); //$NON-NLS-1$

        RuntimeMetadata runtimeMetadata = TestObjectQueryProcessor.getRuntimeMetadata();
        ExecutionContext envContext = EnvironmentUtility.createExecutionContext("100", "1"); //$NON-NLS-1$ //$NON-NLS-2$            
        ResultSetExecution execution = (ResultSetExecution)connection.createExecution(command, envContext, runtimeMetadata);            
        execution.execute();
        int i = getSize(execution);
        assertEquals(30, i);
        execution.close();
    }

	private int getSize(ResultSetExecution execution)
			throws ConnectorException, DataNotAvailableException {
		int i = 0;
        while (execution.next() != null) {
        	i++;
        }
		return i;
	}
    
    public void testPropertyFileLoading() throws Exception {
        IndexConnector connector = new IndexConnector();
        connector.start(helpGetConnectorEnvironment());
        Connection connection = connector.getConnection(helpGetSecurityContext());
        QueryMetadataInterface metadata = FakeQueryMetadata.getQueryMetadata();
        CommandBuilder commandBuilder = new CommandBuilder(metadata);
        IQuery command = (IQuery) commandBuilder.getCommand("select key from fake1Properties"); //$NON-NLS-1$

        RuntimeMetadata runtimeMetadata = TestObjectQueryProcessor.getRuntimeMetadata();
        ExecutionContext envContext = EnvironmentUtility.createExecutionContext("100", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        ResultSetExecution execution = (ResultSetExecution)connection.createExecution(command, envContext, runtimeMetadata);            
        execution.execute();
        assertNotNull(execution.next());
        execution.close();
    }
    
    private ConnectorEnvironment helpGetConnectorEnvironment() {
        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment() {
            public Properties getApplicationProperties() {
                return null;
            }

            public void bindService(String type, ApplicationService service) {

            }

            public void unbindService(String type) {

            }

            public ApplicationService findService(String type) {
                if (type.equals(DQPServiceNames.METADATA_SERVICE)) {
                    clearApplicationService();
                    try {
						fakeApplicationService = new FakeMetadataService(TestConnectorHost.TEST_FILE);
					} catch (IOException e) {
						throw new MetaMatrixRuntimeException(e);
					}
                    return fakeApplicationService;
                }
                return null;
            }
        };
        return new ConnectorEnvironmentImpl(new Properties(), null, applicationEnvironment);
    }
    
    private ExecutionContext helpGetSecurityContext() {
        return new ExecutionContextImpl("testname", "1", null, null, null, null, null, null, null, null, false); //$NON-NLS-1$ //$NON-NLS-2$
    }
    /* 
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
        clearApplicationService();
    }
    
    private void clearApplicationService() {
        if (fakeApplicationService != null) {
            fakeApplicationService.clear();
            fakeApplicationService = null;
        }
    }

}

