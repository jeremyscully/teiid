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

package org.teiid.transport;

import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Properties;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.teiid.adminapi.impl.SessionMetadata;
import org.teiid.client.security.ILogon;
import org.teiid.client.security.InvalidSessionException;
import org.teiid.client.security.LogonException;
import org.teiid.client.security.LogonResult;
import org.teiid.client.security.SessionToken;
import org.teiid.client.util.ResultsFuture;
import org.teiid.core.CoreConstants;
import org.teiid.core.TeiidComponentException;
import org.teiid.dqp.internal.process.DQPWorkContext;
import org.teiid.dqp.service.SessionService;
import org.teiid.dqp.service.SessionServiceException;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.net.CommunicationException;
import org.teiid.net.TeiidURL;
import org.teiid.net.socket.AuthenticationType;
import org.teiid.runtime.RuntimePlugin;
import org.teiid.security.Credentials;
import org.teiid.security.SecurityHelper;


public class LogonImpl implements ILogon {
	
	private SessionService service;
	private String clusterName;

	public LogonImpl(SessionService service, String clusterName) {
		this.service = service;
		this.clusterName = clusterName;
	}

	public LogonResult logon(Properties connProps) throws LogonException, TeiidComponentException, CommunicationException {
		if (this.service.getGssSecurityDomain() != null && connProps.get(ILogon.KRB5TOKEN) != null) {
			Subject user = this.service.getSecurityHelper().getSubjectInContext(this.service.getGssSecurityDomain());
			if (user == null) {
				 throw new LogonException(RuntimePlugin.Event.TEIID40054, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40054));
			}
			return logon(connProps, (byte[])connProps.get(ILogon.KRB5TOKEN));
		}
		
		if (!AuthenticationType.CLEARTEXT.equals(service.getAuthenticationType())) {
			 throw new LogonException(RuntimePlugin.Event.TEIID40055, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40055, "JAAS")); //$NON-NLS-1$
		}
		return logon(connProps, null);
	}
	
	private LogonResult logon(Properties connProps, byte[] krb5ServiceTicket) throws LogonException {
		//DQPWorkContext workContext = DQPWorkContext.getWorkContext();
		//String oldSessionId = workContext.getSessionId();
        String applicationName = connProps.getProperty(TeiidURL.CONNECTION.APP_NAME);
        // user may be null if using trustedToken to log on
        String user = connProps.getProperty(TeiidURL.CONNECTION.USER_NAME, CoreConstants.DEFAULT_ANON_USERNAME);
        // password may be null if using trustedToken to log on
        String password = connProps.getProperty(TeiidURL.CONNECTION.PASSWORD);
		Credentials credential = null;
        if (password != null) {
            credential = new Credentials(password.toCharArray());
        }
        
		try {
			SessionMetadata sessionInfo = service.createSession(user,credential, applicationName, connProps, true);
	        updateDQPContext(sessionInfo);
	        if (DQPWorkContext.getWorkContext().getClientAddress() == null) {
				sessionInfo.setEmbedded(true);
	        }
	        //if (oldSessionId != null) {
	        	//TODO: we should be smarter about disassociating the old sessions from the client.  we'll just rely on 
	        	//ping based clean up
	        //}
			LogonResult result = new LogonResult(sessionInfo.getSessionToken(), sessionInfo.getVDBName(), sessionInfo.getVDBVersion(), clusterName);
			if (krb5ServiceTicket != null) {
				result.addProperty(ILogon.KRB5TOKEN, krb5ServiceTicket);
			}
			return result;
		} catch (LoginException e) {
			 throw new LogonException(e);
		} catch (SessionServiceException e) {
			 throw new LogonException(e);
		}
	}
	  
	class GssAction implements PrivilegedAction<GSSResult> {
		byte[] serviceTicket;
		
		public GssAction(byte[] ticket) {
			this.serviceTicket = ticket;
		}
		
		@Override
		public GSSResult run() {
			GSSContext context = null;
			try {
				GSSManager manager = GSSManager.getInstance();
				context = manager.createContext((GSSCredential)null);				
				this.serviceTicket = context.acceptSecContext(this.serviceTicket, 0, this.serviceTicket.length);				
				return new GSSResult(context, serviceTicket);
			} catch (GSSException e) {
				LogManager.logError(LogConstants.CTX_SECURITY, e, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40014));
			}
			return null;
		}	
	}
	
	class GSSResult {
		GSSContext context;
		byte[] serviceTicket;
		public GSSResult(GSSContext context, byte[] serviceTicket) {
			this.context = context;
			this.serviceTicket = serviceTicket;
		}
	}
	
	@Override
	public LogonResult neogitiateGssLogin(Properties connProps, byte[] serviceTicket, boolean createSession) throws LogonException {
		
		if (!AuthenticationType.GSS.equals(service.getAuthenticationType())) {
			 throw new LogonException(RuntimePlugin.Event.TEIID40055, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40055, "Kerberos")); //$NON-NLS-1$
		}		
		
        String user = connProps.getProperty(TeiidURL.CONNECTION.USER_NAME);
        String password = connProps.getProperty(TeiidURL.CONNECTION.PASSWORD);		
		Object previous = null;
		boolean associated = false;
		try {
			String securityDomain = service.getGssSecurityDomain();
			if (securityDomain == null) {
				 throw new LogonException(RuntimePlugin.Event.TEIID40059, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40059));
			}
			// If this KRB5 and using keytab, user and password callback handler never gets called 
			LoginContext ctx = service.createLoginContext(securityDomain, user, password);
			ctx.login();
			Subject subject = ctx.getSubject();
			GSSResult result =  Subject.doAs(subject, new GssAction(serviceTicket));
			if (result == null) {
				 throw new LogonException(RuntimePlugin.Event.TEIID40014, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40014));
			}
			
			if (result.context.isEstablished()) {
				Principal principal = null;
		    	for(Principal p:subject.getPrincipals()) {
					principal = p;
					break;
		    	}
		    	SecurityHelper securityHelper = service.getSecurityHelper();
		    	Object securityContext = securityHelper.createSecurityContext(securityDomain, principal, null, subject);
		    	previous = securityHelper.associateSecurityContext(securityContext);
				associated = true;
			}
			
			if (!result.context.isEstablished() || !createSession) {
				LogonResult logonResult = new LogonResult(new SessionToken(0, "temp"), "internal", 0, "internal"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				logonResult.addProperty(ILogon.KRB5TOKEN, result.serviceTicket);
				logonResult.addProperty(ILogon.KRB5_ESTABLISHED, new Boolean(result.context.isEstablished()));
				return logonResult;
			}		
			
			LogManager.logDetail(LogConstants.CTX_SECURITY, "Kerberos context established"); //$NON-NLS-1$
			//connProps.setProperty(TeiidURL.CONNECTION.PASSTHROUGH_AUTHENTICATION, "true"); //$NON-NLS-1$
			LogonResult loginInResult =  logon(connProps, result.serviceTicket);
			return loginInResult;
		} catch (LoginException e) {
			 throw new LogonException(RuntimePlugin.Event.TEIID40014, e, RuntimePlugin.Util.gs(RuntimePlugin.Event.TEIID40014));
		} finally {
			if (associated) {
				service.getSecurityHelper().associateSecurityContext(previous);
			}
		}
	}
	
	private String updateDQPContext(SessionMetadata s) {
		String sessionID = s.getSessionId();
		
		DQPWorkContext workContext = DQPWorkContext.getWorkContext();
		workContext.setSession(s);
		return sessionID;
	}
		
	public ResultsFuture<?> logoff() throws InvalidSessionException {
		DQPWorkContext workContext = DQPWorkContext.getWorkContext();
		this.service.closeSession(workContext.getSessionId());
		workContext.getSession().setSessionId(null);
		workContext.getSession().setSecurityContext(null);
		workContext.getSession().getSessionVariables().clear();
		return ResultsFuture.NULL_FUTURE;
	}

	public ResultsFuture<?> ping() throws InvalidSessionException,TeiidComponentException {
		// ping is double used to alert the aliveness of the client, as well as check the server instance is 
		// alive by socket server instance, so that they can be cached.
		String id = DQPWorkContext.getWorkContext().getSessionId();
		if (id != null) {
			this.service.pingServer(id);
		}
		LogManager.logTrace(LogConstants.CTX_SECURITY, "Ping", id); //$NON-NLS-1$
		return ResultsFuture.NULL_FUTURE;
	}
	
	@Override
	public ResultsFuture<?> ping(Collection<String> sessions)
			throws TeiidComponentException, CommunicationException {
		for (String string : sessions) {
			try {
				this.service.pingServer(string);
			} catch (InvalidSessionException e) {
			}
		}
		return ResultsFuture.NULL_FUTURE;
	}

	@Override
	public void assertIdentity(SessionToken checkSession) throws InvalidSessionException, TeiidComponentException {
		if (checkSession == null) {
			//disassociate
			this.updateDQPContext(new SessionMetadata());
			return;
		}
		SessionMetadata sessionInfo = null;
		try {
			sessionInfo = this.service.validateSession(checkSession.getSessionID());
		} catch (SessionServiceException e) {
			 throw new TeiidComponentException(RuntimePlugin.Event.TEIID40062, e);
		}
		
		if (sessionInfo == null) {
			 throw new InvalidSessionException(RuntimePlugin.Event.TEIID40063);
		}
		
		SessionToken st = sessionInfo.getSessionToken();
		if (!st.equals(checkSession)) {
			 throw new InvalidSessionException(RuntimePlugin.Event.TEIID40064);
		}
		this.updateDQPContext(sessionInfo);
	}
}
