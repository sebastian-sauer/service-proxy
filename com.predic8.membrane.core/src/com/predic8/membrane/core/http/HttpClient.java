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


package com.predic8.membrane.core.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.EndOfStreamException;

public class HttpClient {

	private static Log log = LogFactory.getLog(HttpClient.class.getName());
	
	private Socket socket;
	
	private InputStream in;
	
	private OutputStream out;
	
	private static final int MAX_CALL = 5;
	

	private String host;
	
	private int port; 
	
	public void init(String host, int port) throws UnknownHostException, IOException {
		
		while (socket == null || socket.isClosed() || !host.equals(this.host) || this.port != port) {
			log.debug("opening a new socket for host: " + host + " on port: " + port);
			if (in != null) 
				in.close();
			
			if (out != null) {
				out.flush();
				out.close();
			}
			
			if (socket != null)
				socket.close();
			socket = new Socket(host, port);
			in = new BufferedInputStream(socket.getInputStream(), 2048);
			out = new BufferedOutputStream(socket.getOutputStream(), 2048);
			this.host = host;
			this.port = port;
		}
		
	}
	
	private void init(HttpExchange exc) throws UnknownHostException, IOException, MalformedURLException {
		if (exc.getRule() instanceof ProxyRule) {
			log.debug("PROXY: " + exc.getRequest().getUri());
			URL url = new URL((String) exc.getProperty(HttpTransport.REQUEST_URI));
			
			//TODO move to ProxyInterceptor ????
			exc.getRequest().getHeader().setHost(url.getHost() + ":" +  getTargetPort(url));
			log.debug("PATH: " + url.getPath());
			exc.getRequest().setUri(url.getPath());
			
			init(url.getHost(), getTargetPort(url)); 
		} else if (exc.getRule() instanceof ForwardingRule){
			init(((ForwardingRule) exc.getRule()).getTargetHost(), ((ForwardingRule) exc.getRule()).getTargetPort());
		}
	}
	
	private int getTargetPort(URL url) throws MalformedURLException {
		if (url.getProtocol().equalsIgnoreCase("http")) {
			if (url.getPort() == -1)
				return 80;
			return url.getPort();
		}
		throw new RuntimeException("Does not support protocol for URI: " + url.getPath()); 
	}

	public Response call(HttpExchange exc) throws Exception {
		log.debug("calling using rule: " + exc.getRule() + " : " + exc.getRequest().getUri());
		int counter = 0;
	    Exception exception = null;
		while (counter < MAX_CALL) {
	    	try {
	    		log.debug("try # " + counter);
	    		init(exc);
	    		return doCall(exc);
	    	} catch (ConnectException ce) {
	    		exception = ce; 
	    		log.debug("Connection to " + host + " on port " + port + " refused.");
	    	} catch (Exception e) {
	    		exc.getRequest().writeStartLine(System.out); 
	    		exc.getRequest().getHeader().write(System.out);
	    		e.printStackTrace();
	    		exception = e;
	    		log.debug(e);
	    	}
    		counter ++;
	    	try {
	    		close();
				Thread.sleep(250);
			} catch (Exception e) { 
				e.printStackTrace();
			}
	    }
	    throw exception;
	}

	private Response doCall(HttpExchange exchange) throws IOException, SocketException, EndOfStreamException {
		exchange.getServerThread().setTargetSocket(socket);
		exchange.setTimeReqSent(System.currentTimeMillis());
		exchange.getRequest().write(out);
		
		if(!exchange.getRequest().getHeader().hasContentLength() && exchange.getRequest().isHTTP10()){
			if (!socket.isOutputShutdown()) {
				log.info("Shutting down socket outputstream");
				socket.shutdownOutput();
			}
			//TODO close ?
		}
		
		Response response = new Response();
		response.read(in, !exchange.getRequest().isHEADRequest());
		
		exchange.setReceived();
		exchange.setTimeResReceived(System.currentTimeMillis());
		return response;
	}
	
	public void close() throws IOException {
		if (socket == null) 
			return;
		
		log.debug("Closing HTTP connection");
		socket.shutdownInput();
		socket.close();
	}
	
}