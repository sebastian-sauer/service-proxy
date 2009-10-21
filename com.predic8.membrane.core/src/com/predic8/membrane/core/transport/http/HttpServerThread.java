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

package com.predic8.membrane.core.transport.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.TerminateException;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

public class HttpServerThread extends AbstractHttpThread {

	public static int counter = 0;

	public HttpServerThread(HttpExchange exchange, Socket socket) throws IOException {
		this.exchange = exchange;
		exchange.setServerThread(this);
		log = LogFactory.getLog(HttpServerThread.class.getName());
		counter++;
		log.debug("New ServerThread created. " + counter);
		this.sourceSocket = socket;
		srcIn = new BufferedInputStream(sourceSocket.getInputStream(), 2048);
		srcOut =  new BufferedOutputStream(sourceSocket.getOutputStream(), 2048);
		sourceSocket.setSoTimeout(30000);
	}

	public void run() {
		try {
			
			while (true) {
				srcReq = new Request();
				srcReq.read(srcIn, true);
				//exchange = new HttpExchange();
				//exchange.setSrcOut(srcOut);
				exchange.setTimeReqReceived(System.currentTimeMillis());
				boolean alive = srcReq.isKeepAlive();
				if (!alive) {
					sourceSocket.shutdownInput();
				}
				process();
				if (!alive) {
					break;
				}
				if (exchange.getResponse().isRedirect()) {
					break;
				}
				exchange = new HttpExchange();
				exchange.setServerThread(this);
			}
			client.close();
		} catch (SocketTimeoutException e) {
			log.debug("Socket of thread " + counter + " timed out");
		} catch (SocketException se) {
			log.debug("client socket closed");
		}  catch (IOException e) {
			e.printStackTrace();
		} catch (EndOfStreamException e) {
			log.debug("stream closed");
		} catch (AbortException e) {
			log.debug("exchange aborted.");
		}  
		
		finally {
			try {
				if (!sourceSocket.isClosed()) {
					sourceSocket.shutdownOutput();
					sourceSocket.close();
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}

		}

	}

	private void process() throws AbortException {
		targetRes = null;
		try {

//			exchange.setSourceSocket(exchange.getSourceSocket());
			exchange.setProperty(HttpTransport.SOURCE_HOSTNAME,  sourceSocket.getLocalAddress().getHostName());
			exchange.setProperty(HttpTransport.SOURCE_IP, sourceSocket.getLocalAddress().getHostAddress());

			exchange.setRequest(srcReq);
			//execute transport interceptors first, because exchange has not a rule yet 
			if (Outcome.ABORT == invokeInterceptors(exchange, Core.getTransport().getInInterceptors())) 
				throw new AbortException();

			if (Outcome.ABORT == invokeInterceptors(exchange, exchange.getRule().getInInterceptors())) 
				throw new AbortException();
			
			synchronized (exchange.getRequest()) {
				if (exchange.getRule().isBlockRequest())
					block(exchange.getRequest());
			}

			
			try {
				targetRes = client.call(exchange);
			} catch (ConnectException e) {
				targetRes = HttpUtil.createErrorResponse("Target is not reachable.");
			}
			exchange.setResponse(targetRes);
			if (Outcome.ABORT == invokeInterceptors(exchange, exchange.getRule().getOutInterceptors()))
				throw new AbortException();
			
			if (Outcome.ABORT == invokeInterceptors(exchange, Core.getTransport().getOutInterceptors()))
				throw new AbortException();
			
			synchronized (exchange.getResponse()) {
				if (exchange.getRule().isBlockResponse()) {
					block(exchange.getResponse());
				}
			}

		} catch (AbortException e ) {
			throw e;
		} catch (Exception ex) {
			ex.printStackTrace();
			exchange.setException(ex);
			return;
		}
		try {
			//exchange.setResponse(targetRes);
			log.debug("Start writing targetRes to srcOut" );
			targetRes.write(srcOut);
			srcOut.flush();
			log.debug("Done writing targetRes to srcOut" );
			exchange.setTimeResSent(System.currentTimeMillis());
			exchange.setCompleted();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void block(Message message) throws TerminateException {
		try {
			log.debug("message thread waits");
			message.wait();
			log.debug("message thread received notify");
			if (exchange.isForceToStop())
				throw new TerminateException("Force the exchange to stop.");
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
}