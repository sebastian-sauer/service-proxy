/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.HttpUtil;

public class RoutingInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RoutingInterceptor.class.getName());

	private boolean xForwardedForEnabled = true;

	private RuleManager ruleManager;

	public Outcome handleRequest(Exchange exc) throws Exception {
		if (!(exc instanceof HttpExchange))
			throw new RuntimeException("RoutingInterceptor accepts only HttpExchange objects");

		HttpExchange httpExc = (HttpExchange) exc;

		Rule rule = getRule(httpExc);
		httpExc.setRule(rule);
		if (rule instanceof NullRule) {
			httpExc.getRequest().readBody();
			httpExc.getServerThread().getSourceSocket().shutdownInput();
			Response res = HttpUtil.createErrorResponse("This request was not accepted by Membrane Monitor. Please correct the request and try again.");
			httpExc.setResponse(res);
			res.write(httpExc.getServerThread().getSrcOut());
			httpExc.getServerThread().getSrcOut().flush();

			httpExc.setTimeResSent(System.currentTimeMillis());
			httpExc.finishExchange(true, httpExc.getErrorMessage());
			return Outcome.ABORT;
		}

		httpExc.setProperty(HttpTransport.HEADER_HOST, httpExc.getRequest().getHeader().getHost());
		httpExc.setRequestUri(httpExc.getRequest().getUri());
		adjustHostHeader(httpExc);

		if (xForwardedForEnabled && (rule instanceof ForwardingRule))
			insertXForwardedFor(httpExc);

		return Outcome.CONTINUE;
	}

	private Rule getRule(HttpExchange exc) {
		ForwardingRuleKey key = new ForwardingRuleKey(getHostname(exc), exc.getRequest().getMethod(), exc.getRequest().getUri(), ((HttpExchange) exc).getServerThread().getSourceSocket().getLocalPort());
		Rule rule = ruleManager.getMatchingRule(key);
		if (rule != null) {
			log.debug("Matching Rule found for RuleKey " + key);
			return rule;
		}

		return findProxyRule(exc);
	}

	private Rule findProxyRule(HttpExchange exc) {
		for (Rule rule : ruleManager.getRules()) {
			if (!(rule instanceof ProxyRule))
				continue;

			if (rule.getKey().getPort() == exc.getServerThread().getSourceSocket().getLocalPort()) {
				log.debug("proxy rule found: " + rule);
				return rule;
			}
		}
		log.debug("No rule found for incomming request");
		return new NullRule();
	}

	private void insertXForwardedFor(Exchange exc) {
		String value = getXForwardedFor(exc) != null ? getXForwardedFor(exc) + ", " + getSourceIp(exc): getSourceIp(exc);
		exc.getRequest().getHeader().setXForwardedFor(value);
	}

	private String getSourceIp(Exchange exc) {
		return (String) exc.getProperty(HttpTransport.SOURCE_IP);
	}

	private String getXForwardedFor(Exchange exc) {
		return exc.getRequest().getHeader().getXForwardedFor();
	}

	private String getHostname(Exchange exc) {
		String host = exc.getRequest().getHeader().getHost();
		StringTokenizer tokenizer = new StringTokenizer(host, ":");
		if (tokenizer.countTokens() >= 1) {
			return tokenizer.nextToken();
		}
		return host;
	}

	private void adjustHostHeader(Exchange exc) {
		if (exc.getRule() instanceof ForwardingRule) {
			exc.getRequest().getHeader().setHost(((ForwardingRule) exc.getRule()).getTargetHost() + ":" + ((ForwardingRule) exc.getRule()).getTargetPort());
		}
	}

	@Override
	public String toString() {
		return "RoutingInterceptor";
	}

	public boolean isxForwardedForEnabled() {
		return xForwardedForEnabled;
	}

	public void setxForwardedForEnabled(boolean xForwardedForEnabled) {
		this.xForwardedForEnabled = xForwardedForEnabled;
	}

	public RuleManager getRuleManager() {
		return ruleManager;
	}

	public void setRuleManager(RuleManager ruleManager) {
		this.ruleManager = ruleManager;
	}

}
