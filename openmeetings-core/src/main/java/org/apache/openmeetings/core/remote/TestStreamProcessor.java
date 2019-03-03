/*

 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.core.remote;

import static org.apache.openmeetings.core.remote.KurentoHandler.MODE_TEST;
import static org.apache.openmeetings.core.remote.KurentoHandler.PARAM_CANDIDATE;
import static org.apache.openmeetings.core.remote.KurentoHandler.PARAM_ICE;
import static org.apache.openmeetings.core.remote.KurentoHandler.TAG_KUID;
import static org.apache.openmeetings.core.remote.KurentoHandler.TAG_MODE;
import static org.apache.openmeetings.core.remote.KurentoHandler.TAG_ROOM;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.openmeetings.core.util.WebSocketHelper;
import org.apache.openmeetings.db.entity.basic.IWsClient;
import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.openjson.JSONObject;

@Component
public class TestStreamProcessor implements IStreamProcessor {
	private final Map<String, KTestStream> streamByUid = new ConcurrentHashMap<>();

	@Autowired
	private KurentoHandler kHandler;

	void onMessage(IWsClient _c, final String cmdId, JSONObject msg) {
		KTestStream user = getByUid(_c.getUid());
		switch (cmdId) {
			case "wannaRecord":
				WebSocketHelper.sendClient(_c, newTestKurentoMsg()
						.put("id", "canRecord")
						.put(PARAM_ICE, kHandler.getTurnServers(true))
						);
				break;
			case "record":
				if (user != null) {
					user.release(this);
				}
				user = new KTestStream(_c, msg, createTestPipeline());
				streamByUid.put(_c.getUid(), user);
				break;
			case "iceCandidate":
				JSONObject candidate = msg.getJSONObject(PARAM_CANDIDATE);
				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate.getString(PARAM_CANDIDATE),
							candidate.getString("sdpMid"), candidate.getInt("sdpMLineIndex"));
					user.addCandidate(cand);
				}
				break;
			case "wannaPlay":
				WebSocketHelper.sendClient(_c, newTestKurentoMsg()
						.put("id", "canPlay")
						.put(PARAM_ICE, kHandler.getTurnServers(true))
						);
				break;
			case "play":
				if (user != null) {
					user.play(_c, msg, createTestPipeline());
				}
				break;
		}
	}

	private KTestStream getByUid(String uid) {
		return uid == null ? null : streamByUid.get(uid);
	}

	private MediaPipeline createTestPipeline() {
		Transaction t = kHandler.beginTransaction();
		MediaPipeline pipe = kHandler.getClient().createMediaPipeline(t);
		pipe.addTag(t, TAG_KUID, kHandler.getKuid());
		pipe.addTag(t, TAG_MODE, MODE_TEST);
		pipe.addTag(t, TAG_ROOM, MODE_TEST);
		t.commit();
		return pipe;
	}

	static JSONObject newTestKurentoMsg() {
		return KurentoHandler.newKurentoMsg().put(TAG_MODE, MODE_TEST);
	}

	void remove(IWsClient _c) {
		AbstractStream s = getByUid(_c.getUid());
		if (s != null) {
			s.release(this);
		}
	}

	@Override
	public void release(AbstractStream stream) {
		streamByUid.remove(stream.getUid());
	}

	@Override
	public void destroy() {
		for (Entry<String, KTestStream> e : streamByUid.entrySet()) {
			e.getValue().release(this);
			streamByUid.remove(e.getKey());
		}
		streamByUid.clear();
	}
}
