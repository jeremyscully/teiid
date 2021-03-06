/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
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

package org.teiid.jdbc;

import static org.junit.Assert.*;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.Principal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.security.auth.Subject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.teiid.client.security.InvalidSessionException;
import org.teiid.client.util.ResultsFuture;
import org.teiid.core.types.DataTypeManager;
import org.teiid.core.util.UnitTestUtil;
import org.teiid.dqp.internal.datamgr.ConnectorManager;
import org.teiid.dqp.internal.datamgr.ConnectorManagerRepository;
import org.teiid.jdbc.FakeServer.DeployVDBParameter;
import org.teiid.language.Command;
import org.teiid.metadata.FunctionMethod;
import org.teiid.metadata.FunctionMethod.PushDown;
import org.teiid.metadata.FunctionParameter;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.query.function.metadata.FunctionCategoryConstants;
import org.teiid.security.SecurityHelper;
import org.teiid.translator.DataNotAvailableException;
import org.teiid.translator.Execution;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ExecutionFactory;
import org.teiid.translator.ResultSetExecution;
import org.teiid.translator.TranslatorException;

@SuppressWarnings("nls")
public class TestLocalConnections {
	
	private final class SimpleUncaughtExceptionHandler implements
			UncaughtExceptionHandler {
		volatile Throwable t;

		@Override
		public void uncaughtException(Thread arg0, Throwable arg1) {
			t = arg1;
		}
	}

	static ReentrantLock lock = new ReentrantLock();
	static Condition waiting = lock.newCondition();
	static Condition wait = lock.newCondition();
	
	static Semaphore sourceCounter = new Semaphore(0);
	
	public static int blocking() throws InterruptedException {

		lock.lock();
		try {
			waiting.signal();
			if (!wait.await(2, TimeUnit.SECONDS)) {
				throw new RuntimeException();
			}
		} finally {
			lock.unlock();
		}
		return 1;
	}

	static FakeServer server = new FakeServer(true);
	
	@SuppressWarnings("serial")
	@BeforeClass public static void oneTimeSetup() throws Exception {
    	server.setUseCallingThread(true);
    	server.setConnectorManagerRepository(new ConnectorManagerRepository() {
    		@Override
    		public ConnectorManager getConnectorManager(String connectorName) {
    			return new ConnectorManager(connectorName, connectorName) {
    				@Override
    				public ExecutionFactory<Object, Object> getExecutionFactory() {
    					return new ExecutionFactory<Object, Object>() {
    						
    						@Override
    						public boolean isSourceRequired() {
    							return false;
    						}
    						
    						@Override
    						public Execution createExecution(Command command,
    								ExecutionContext executionContext,
    								RuntimeMetadata metadata, Object connection)
    								throws TranslatorException {
    						    return new ResultSetExecution() {
    						    	
    						    	boolean returnedRow = false;
    						    	
									@Override
									public void execute() throws TranslatorException {
										lock.lock();
										try {
											sourceCounter.release();
											if (!wait.await(5, TimeUnit.SECONDS)) {
												throw new RuntimeException();
											}
										} catch (InterruptedException e) {
											throw new RuntimeException(e);
										} finally {
											lock.unlock();
										}
									}
									
									@Override
									public void close() {
										
									}
									
									@Override
									public void cancel() throws TranslatorException {
										
									}
									
									@Override
									public List<?> next() throws TranslatorException, DataNotAvailableException {
										if (returnedRow) {
											return null;
										}
										returnedRow = true;
										return new ArrayList<Object>(Collections.singleton(null));
									}
								};
    						}
    					};
    				}
    				
    				@Override
    				public Object getConnectionFactory()
    						throws TranslatorException {
    					return null;
    				}
    			};
    		}
    	});
    	FunctionMethod function = new FunctionMethod("foo", null, FunctionCategoryConstants.MISCELLANEOUS, PushDown.CANNOT_PUSHDOWN, TestLocalConnections.class.getName(), "blocking", null, new FunctionParameter("result", DataTypeManager.DefaultDataTypes.INTEGER), false, FunctionMethod.Determinism.NONDETERMINISTIC);
    	HashMap<String, Collection<FunctionMethod>> udfs = new HashMap<String, Collection<FunctionMethod>>();
    	udfs.put("test", Arrays.asList(function));
    	server.deployVDB("PartsSupplier", UnitTestUtil.getTestDataPath() + "/PartsSupplier.vdb", new DeployVDBParameter(udfs, null));
	}
	
	@AfterClass public static void oneTimeTearDown() {
		server.stop();
	}
	
	@Test public void testConcurrentExection() throws Throwable {
    	
    	Thread t = new Thread() {
    		
    		public void run() {
    			try {
	    	    	Connection c = server.createConnection("jdbc:teiid:PartsSupplier");
	    	    	
	    	    	Statement s = c.createStatement();
	    	    	s.execute("select foo()");
	    	    	s.close();
    			} catch (Exception e) {
    				throw new RuntimeException(e);
    			}
    		}
    	};
    	SimpleUncaughtExceptionHandler handler = new SimpleUncaughtExceptionHandler();
    	t.setUncaughtExceptionHandler(handler);
    	t.start();
    	
    	lock.lock();
    	try {
    		waiting.await();
    	} finally {
    		lock.unlock();
    	}
    	Connection c = server.createConnection("jdbc:teiid:PartsSupplier");
    	Statement s = c.createStatement();
    	s.execute("select * from tables");
    	
    	lock.lock();
    	try {
    		wait.signal();
    	} finally {
    		lock.unlock();
		}
    	t.join(2000);
    	if (t.isAlive()) {
    		fail();
    	}
    	s.close();
    	if (handler.t != null) {
    		throw handler.t;
    	}
	}
	
	@Test public void testUseInDifferentThreads() throws Throwable {
		for (int i = 0; !server.getDqp().getRequests().isEmpty() && i < 40; i++) {
    		//the previous test may not have cleaned up
    		Thread.sleep(50);
    	}
		int count = server.getDqp().getRequests().size();
		Connection c = server.createConnection("jdbc:teiid:PartsSupplier");
    	
    	final Statement s = c.createStatement();
    	s.execute("select 1");
    	
    	assertFalse(server.getDqp().getRequests().isEmpty());

    	Thread t = new Thread() {
    		public void run() {
    			try {
					s.close();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
    		}
    	};
    	SimpleUncaughtExceptionHandler handler = new SimpleUncaughtExceptionHandler();
    	t.setUncaughtExceptionHandler(handler);
    	t.start();
    	t.join(2000);
    	if (t.isAlive()) {
    		fail();
    	}
    	if (handler.t != null) {
    		throw handler.t;
    	}    	
    	for (int i = 0; server.getDqp().getRequests().size() != count && i < 40; i++) {
    		//the concurrent modification may not be seen initially
    		Thread.sleep(50);
    	}
    	assertEquals(count, server.getDqp().getRequests().size());
	}
	
	@Test public void testWait() throws Throwable {
		final Connection c = server.createConnection("jdbc:teiid:PartsSupplier");
    	
		Thread t = new Thread() {
			public void run() {
		    	Statement s;
				try {
					s = c.createStatement();
			    	assertTrue(s.execute("select part_id from parts"));
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
		};
    	t.start();
    	SimpleUncaughtExceptionHandler handler = new SimpleUncaughtExceptionHandler();
    	t.setUncaughtExceptionHandler(handler);
    	
    	sourceCounter.acquire();
    	
    	//t should now be waiting also
    	
    	lock.lock();
    	try {
    		wait.signal();
    	} finally {
    		lock.unlock();
    	}

    	//t should finish
    	t.join();
    	
    	if (handler.t != null) {
    		throw handler.t;
    	}
	}
	
	@Test public void testWaitMultiple() throws Throwable {
		final Connection c = server.createConnection("jdbc:teiid:PartsSupplier");
    	
		Thread t = new Thread() {
			public void run() {
		    	Statement s;
				try {
					s = c.createStatement();
			    	assertTrue(s.execute("select part_id from parts union all select part_name from parts"));
			    	ResultSet r = s.getResultSet();
			    	
			    	//wake up the other source thread, should put the requestworkitem into the more work state 
			    	lock.lock();
			    	try {
			    		wait.signal();
			    	} finally {
			    		lock.unlock();
			    	}
			    	Thread.sleep(1000); //TODO: need a better hook to determine that connector work has finished
			    	while (r.next()) {
			    		//will hang unless this thread is allowed to resume processing
			    	}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
    	t.start();
    	SimpleUncaughtExceptionHandler handler = new SimpleUncaughtExceptionHandler();
    	t.setUncaughtExceptionHandler(handler);
    	
    	sourceCounter.acquire(2);
    	
    	//t should now be waiting also
    	
    	//wake up 1 source thread
    	lock.lock();
    	try {
    		wait.signal();
    	} finally {
    		lock.unlock();
    	}
    	
    	t.join();
    	
    	if (handler.t != null) {
    		throw handler.t;
    	}
	}
	
	@Test public void testWaitForLoad() throws Exception {
		final ResultsFuture<Void> future = new ResultsFuture<Void>();
		
		Thread t = new Thread() {
			public void run() {
				try {
					server.createConnection("jdbc:teiid:not_there.1");
					future.getResultsReceiver().receiveResults(null);
				} catch (Exception e) {
					future.getResultsReceiver().exceptionOccurred(e);
				}
			}
		};
		t.setDaemon(true);
		t.start();
		assertFalse(future.isDone());
		try {
			server.deployVDB("not_there", UnitTestUtil.getTestDataPath() + "/PartsSupplier.vdb");
			future.get(5000, TimeUnit.SECONDS);
		} finally {
			server.undeployVDB("not_there");
		}
		try {
			server.createConnection("jdbc:teiid:not_there.1;waitForLoad=0");
			fail();
		} catch (TeiidSQLException e) {
			
		}
	}

	static int calls;
	static Object currentContext = new Object();
	
	@Test public void testPassThroughDifferentUsers() throws Throwable {
		SecurityHelper securityHelper = new SecurityHelper() {
			
			@Override
			public boolean sameSubject(String securityDomain, Object context,
					Subject subject) {
				return context == currentContext;
			}
			
			@Override
			public Subject getSubjectInContext(String securityDomain) {
				return new Subject();
			}
			
			@Override
			public String getSecurityDomain(Object context) {
				return "teiid-security";
			}
			
			@Override
			public Object getSecurityContext() {
				calls++;
				return currentContext;
			}
			
			@Override
			public Object createSecurityContext(String securityDomain, Principal p,
					Object credentials, Subject subject) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void clearSecurityContext() {
			}
			
			@Override
			public Object associateSecurityContext(Object context) {
				Object result = currentContext;
				currentContext = context;
				return result;
			}
		};
		SecurityHelper current = server.getSessionService().getSecurityHelper();
		server.getClientServiceRegistry().setSecurityHelper(securityHelper);
		server.getSessionService().setSecurityHelper(securityHelper);
		try {
		
			final Connection c = server.createConnection("jdbc:teiid:PartsSupplier;PassthroughAuthentication=true");
			
			Statement s = c.createStatement();
			ResultSet rs = s.executeQuery("select session_id()");
			Object o = currentContext;
			currentContext = null;
			s.cancel();
			currentContext = o;
			rs.next();
			String id = rs.getString(1);
			rs.close();
			assertEquals(2, calls);
			server.getSessionService().pingServer(id);
			currentContext = new Object();
			rs = s.executeQuery("select session_id()");
			rs.next();
			String id1 = rs.getString(1);
			rs.close();
			assertNotEquals(id, id1);
			try {
				server.getSessionService().pingServer(id);
				//should have logged off
				fail();
			} catch (InvalidSessionException e) {
				
			}
		
		} finally {
			server.getClientServiceRegistry().setSecurityHelper(current);
			server.getSessionService().setSecurityHelper(current);
		}
	}
	
}
