/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.viperfish.chatapplication;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.HashMap;
import java.util.Map;
import net.viperfish.chatapplication.core.DefaultLSSession;
import net.viperfish.chatapplication.core.JsonGenerator;
import net.viperfish.chatapplication.core.LSFilter;
import net.viperfish.chatapplication.core.LSPayload;
import net.viperfish.chatapplication.core.LSRequest;
import net.viperfish.chatapplication.core.LSStatus;
import net.viperfish.chatapplication.core.RequestHandler;
import net.viperfish.chatapplication.core.UserRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;

/**
 *
 * @author sdai
 */
public class ChatApplication extends WebSocketApplication {

    private final Map<Long, RequestHandler> handlerMapper;
    private final JsonGenerator generator;
    private UserRegister socketMapper;
    private final Logger logger;
    private final DefaultFilterChain filterChain;

    public ChatApplication() {
        handlerMapper = new HashMap<>();
        generator = new JsonGenerator();
        logger = LogManager.getLogger();
        filterChain = new DefaultFilterChain();
    }

    public void setSocketMapper(UserRegister mapper) {
        this.socketMapper = mapper;
    }

    public void addHandler(Long type, RequestHandler handler) {
        handlerMapper.put(type, handler);
    }

    private void updateSessionInfo(LSRequest req) {
        req.setSession(DefaultLSSession.getSession(req.getSource()));
    }

    private LSRequest convertToRequest(String data, WebSocket socket) throws JsonParseException, JsonMappingException {
        LSRequest req = generator.fromJson(LSRequest.class, data);
        req.setSocket(socket);
        return req;
    }

    private void sendPayload(LSPayload payload, LSStatus status) throws JsonGenerationException, JsonMappingException {
        if (payload.getTarget() != null) {
            WebSocket targetSocket = socketMapper.getSocket(payload.getTarget());
            if (targetSocket == null || !targetSocket.isConnected()) {
                status.setStatus(LSStatus.USER_OFFLINE, "Target User Offline");
            } else {
                String sentData = generator.toJson(payload);
                logger.info("Sending:\n" + sentData);
                targetSocket.send(sentData);
            }
        }
    }

    private LSStatus handleRequest(LSRequest req, WebSocket socket) throws JsonGenerationException, JsonMappingException {
        RequestHandler handler = handlerMapper.get(req.getType());
        LSPayload payload = new LSPayload();
        LSStatus status = new LSStatus();
        if (handler != null) {
            filterChain.setEndpoint(handler);
            status = filterChain.process(req, payload);
            sendPayload(payload, status);
        } else {
            logger.info("No Handler Present For Message Type:" + req.getType());
            status.setStatus(LSStatus.NO_HANDLER, "No Handler Found For Type" + req.getType());
        }
        return status;
    }

    private void sendStatus(LSPayload statusPayload, LSStatus status, WebSocket origin) throws JsonGenerationException, JsonMappingException {
        statusPayload.setData(generator.toJson(status));
        statusPayload.setSource(null);
        statusPayload.setType(LSPayload.LS_STATUS);
        origin.send(generator.toJson(statusPayload));
    }

    @Override
    public void onMessage(WebSocket socket, String text) {
        logger.info("Received Message:" + text);
        LSStatus status = new LSStatus();
        LSPayload statusPayload = new LSPayload();
        statusPayload.setSource(null);
        try {
            LSRequest req = convertToRequest(text, socket);
            if (req.getType() == null) {
                logger.warn("Invalid Message:" + text);
                return;
            }
            updateSessionInfo(req);

            status = handleRequest(req, socket);

            if (socketMapper.getSocket(req.getSource()) != null) {
                statusPayload.setTarget(req.getSource());
            } else {
                statusPayload.setTarget(null);
            }
        } catch (JsonParseException | JsonMappingException | JsonGenerationException ex) {
            logger.warn("Exception Caught:" + ex);
            status.setStatus(LSStatus.INTERNAL_ERROR, "JSON Processing Error ");
        } finally {
            try {
                sendStatus(statusPayload, status, socket);
            } catch (JsonGenerationException | JsonMappingException ex) {
                logger.warn("Exception Caught While sending status", ex);
                socket.send("{'status':204, 'reason':\"\", 'additional':\"\"}");
            }
        }
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        logger.info("Closing Socket");
        String username = socketMapper.getUsername(socket);
        if(username != null) {
            logger.info("Invalidating session for " + username);
            DefaultLSSession.getSession(username).invalidate();
        }
        socketMapper.unregister(socket);
        super.onClose(socket, frame);
    }

    public void addFilter(LSFilter filter) {
        this.filterChain.addFilter(filter);
    }
    
}
