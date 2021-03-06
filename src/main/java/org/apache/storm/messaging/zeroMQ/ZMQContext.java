/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.storm.messaging.zeroMQ;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import backtype.storm.Config;
import backtype.storm.messaging.IConnection;
import backtype.storm.messaging.IContext;
import backtype.storm.utils.Utils;

public class ZMQContext implements IContext, ZMQContextQuery {
	private final static Logger LOG = LoggerFactory.getLogger(ZMQContext.class);
	private Context context;
	private int lingerMs;
	private boolean isLocal;
	private int hwm;

	@SuppressWarnings({ "rawtypes" })
	@Override
	public void prepare(Map stormConf) {
		int numThreads = Utils.getInt(stormConf.get(Config.ZMQ_THREADS), 1);
		lingerMs = Utils.getInt(stormConf.get(Config.ZMQ_LINGER_MILLIS), 5000);
		hwm = Utils.getInt(stormConf.get(Config.ZMQ_HWM), 0);
		isLocal = clusterMode(stormConf).equals("local");
		context = ZeroMQ.context(numThreads);
		LOG.info("MQContext prepare done...");
	}

	@SuppressWarnings("rawtypes")
	private String clusterMode(Map stormConf) {
		String clusterMode = (String) stormConf.get(Config.STORM_CLUSTER_MODE);
		return clusterMode == null ? "local" : clusterMode;
	}

	@Override
	public IConnection bind(String stormId, int port) {
		String bindUrl = this.getBindZmqUrl(isLocal, port);
		Socket socket = ZeroMQ.socket(context, ZeroMQ.pull);
		socket = ZeroMQ.set_hwm(socket, hwm);
		socket = ZeroMQ.bind(socket, bindUrl);
		LOG.info("Create zmq receiver {}", bindUrl);
		return new ZMQRecvConnection(socket);
	}

	@Override
	public IConnection connect(String stormId, String host, int port) {
		String connectUrl = this.getConnectZmqUrl(isLocal, host, port);
		Socket socket = ZeroMQ.socket(context, ZeroMQ.push);
		socket = ZeroMQ.set_hwm(socket, hwm);
		socket = ZeroMQ.set_linger(socket, lingerMs);
		socket = ZeroMQ.connect(socket, connectUrl);
		LOG.info("Create zmq sender {}", connectUrl);
		return new ZMQSendConnection(socket);
	}

	public void term() {
		LOG.info("ZMQ context terminates ");
		context.term();
	}

	private String getBindZmqUrl(boolean isLocal, int port) {
		if (isLocal) {
			return "ipc://" + port + ".ipc";
		} else {
			return "tcp://*:" + port;
		}
	}

	private String getConnectZmqUrl(boolean isLocal, String host, int port) {
		if (isLocal) {
			return "ipc://" + port + ".ipc";
		} else {
			return "tcp://" + host + ":" + port;
		}
	}

	@Override
	public Context zmqContext() {
		return context;
	}
}
