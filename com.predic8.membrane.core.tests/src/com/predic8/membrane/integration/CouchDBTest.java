package com.predic8.membrane.integration;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpTransport;


public class CouchDBTest extends TestCase {

	private String targetHost = "192.168.2.131";
	private int targetPort = 5984;
	
	private HttpClient client = new HttpClient();
	
	private Rule rule;
	
	private int currentId = 25;
	
	@Before
	public void setUp() throws Exception {
		
	}
	
	@Test
	public void testCreateTable() throws Exception {
		
		HttpExchange exchange = new HttpExchange();
		exchange.setRule(getRule());
		exchange.setRequest(createRequest("http://" + targetHost + ":" + targetPort + "/tblmembrane/", null));
		
		exchange.setProperty(HttpTransport.HEADER_HOST, exchange.getRequest().getHeader().getHost());
		exchange.setRequestUri(exchange.getRequest().getUri());
		exchange.getRequest().getHeader().setHost(((ForwardingRule) exchange.getRule()).getTargetHost() + ":" + ((ForwardingRule) exchange.getRule()).getTargetPort());
		
		try {
			Response resp = client.call(exchange);
			System.err.println("Status code of DB response: " + resp.getStatusCode());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
	}
	
	@Test
	public void testPutDocument() throws Exception {
		
		HttpExchange exchange = new HttpExchange();
		exchange.setRule(getRule());
		exchange.setRequest(createRequest("http://" + targetHost + ":" + targetPort + "/tblmembrane/" + currentId, "{\"alpha\":\"thomas\"}"));
		
		exchange.setProperty(HttpTransport.HEADER_HOST, exchange.getRequest().getHeader().getHost());
		exchange.setRequestUri(exchange.getRequest().getUri());
		exchange.getRequest().getHeader().setHost(((ForwardingRule) exchange.getRule()).getTargetHost() + ":" + ((ForwardingRule) exchange.getRule()).getTargetPort());
		
		try {
			Response resp = client.call(exchange);
			System.err.println("Status code of DB response: " + resp.getStatusCode());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		currentId ++;
	}
	
	private Request createRequest(String url, String content) {
		Request request = new Request();
		request.setMethod(Request.METHOD_PUT);
		request.setVersion("1.1");
		request.setUri(url);
		Header header = new Header();
		header.add("Accept", "application/json");
		header.add("Content-Type", "application/json");
		
		request.setHeader(header);
		if (content != null)
			request.setBodyContent(content.getBytes());
		return request;
	}
	
	private Rule getRule() {
		if (rule == null) {
			rule = new ForwardingRule(new ForwardingRuleKey("localhost", Request.METHOD_POST, ".*", 4100), targetHost, "" + targetPort);
		}
		return rule;
	}
	
}
