// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.secmgr.servlets;

import com.google.enterprise.secmgr.authncontroller.AuthnController;
import com.google.enterprise.secmgr.authncontroller.AuthnSession;
import com.google.enterprise.secmgr.authncontroller.AuthnSession.AuthnState;
import com.google.enterprise.secmgr.authncontroller.AuthnSessionManager;
import com.google.enterprise.secmgr.common.Decorator;
import com.google.enterprise.secmgr.common.GettableHttpServlet;
import com.google.enterprise.secmgr.common.HttpUtil;
import com.google.enterprise.secmgr.common.PostableHttpServlet;
import com.google.enterprise.secmgr.common.SessionUtil;
import com.google.enterprise.secmgr.saml.SamlSharedData;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler for SAML authentication requests.  These requests are sent by a service provider, in our
 * case the Google Search Appliance.  This is one part of the security manager's identity provider.
 */
@Singleton
@Immutable
public class SamlAuthn extends SamlIdpServlet
    implements GettableHttpServlet, PostableHttpServlet {
  private static final Logger logger = Logger.getLogger(SamlAuthn.class.getName());

  // TODO: I18N this message.
  protected static final String PLEASE_ENABLE_COOKIES_MSG = "Please enable cookies";
  @Nonnull private final AuthnController controller;
  private final AuthnSessionManager authnSessionManager;

  @Inject
  public SamlAuthn(AuthnSessionManager sessionManager, AuthnController controller) {
    super(SamlSharedData.getProductionInstance(SamlSharedData.Role.IDENTITY_PROVIDER));
    this.controller = controller;
    this.authnSessionManager = sessionManager;
  }

  /**
   * Accept an authentication request and (eventually) respond to the service provider with a
   * response.  The request is generated by the service provider, then sent to the user agent as a
   * redirect.  The user agent redirects here, with the SAML AuthnRequest message encoded as a query
   * parameter.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Decorator decorator = SessionUtil.getLogDecorator(request);
    controller.setSecureSearchApiMode(false);
    AuthnSession.setSecureSearchApiMode(false);
    AuthnSession session = authnSessionManager.createPersistentSession(request);
    if (session == null) {
      logger.warning(decorator.apply("Could not get/make session; abandoning request."));
      initNormalResponseWithHeaders(response, HttpServletResponse.SC_EXPECTATION_FAILED)
        .print(PLEASE_ENABLE_COOKIES_MSG);
      return;
    }
    prepareSamlContextForSerialization(request, session);

    session.logIncomingRequest(request);

    try {

      // If the session is newly created in AuthnSession#getInstance due to
      // createGsaSmSessionIfNotExist is set to true, then it must be in
      // AuthnState.IDLE.
      if (session.assertState(AuthnState.IDLE, AuthnState.IN_CREDENTIALS_GATHERER)
          == AuthnState.IN_CREDENTIALS_GATHERER) {
        doAuthn(session, request, response);
        return;
      }

      // Establish the SAML message context.
      GeneratedContext generatedContext = createAuthnContext(request, response, getSharedData());

      if (generatedContext == null) {
        return;
      }
      // If we are here, we've received a valid SAML SSO request.  If the GET
      // request was not a SAML SSO request, an error would have been signalled
      // during decoding and we wouldn't reach this point.
      session.setStateAuthenticating(HttpUtil.getRequestUrl(request, false),
          generatedContext.getContext());

      if (generatedContext.getSecurityException() != null) {
        failFromException(generatedContext.getSecurityException(), session, request, response);
        return;
      }

      // Start authentication process.
      doAuthn(session, request, response);

    } catch (IOException | RuntimeException e) {
      failFromException(e, session, request, response);
    }
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    controller.setSecureSearchApiMode(false);
    AuthnSession.setSecureSearchApiMode(false);
    AuthnSession session = authnSessionManager.findSession(request);
    if (session == null) {
      failNoSession(request, response);
      return;
    }
    restoreSamlContext(session);
    session.logIncomingRequest(request);
    try {
      session.assertState(AuthnState.IN_UL_FORM, AuthnState.IN_CREDENTIALS_GATHERER);
      doAuthn(session, request, response);
    } catch (IOException | RuntimeException e) {
      failFromException(e, session, request, response);
    }
  }
}
