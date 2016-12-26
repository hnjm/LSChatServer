/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.viperfish.chatapplication.handlers;

import net.viperfish.chatapplication.core.LSPayload;
import net.viperfish.chatapplication.core.LSRequest;
import net.viperfish.chatapplication.core.LSResponse;
import net.viperfish.chatapplication.core.RequestHandler;

/**
 *
 * @author sdai
 */
public final class MessagingHandler implements RequestHandler {

    
    
    @Override
    public void init() {
    }

    @Override
    public LSResponse handleRequest(LSRequest req, LSPayload resp) {
        resp.setType(LSPayload.LS_MESSAGE);
        resp.setSource(req.getSource());
        resp.setTarget(req.getAttribute("target"));
        resp.setData(req.getData());
        
        return new LSResponse(LSResponse.SUCCESS, "Message Proccessed", "");
    }
    
}
