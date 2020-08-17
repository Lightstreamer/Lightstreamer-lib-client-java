package com.lightstreamer.client.protocol;

import com.lightstreamer.util.EncodingUtils;

/**
 * Parses REQOK/REQERR/ERROR responses to control requests.
 * 
 * 
 * @since February 2018
 */
public class ControlResponseParser {
    
    /**
     * Parses a response to a control request.
     */
    public static ControlResponseParser parseControlResponse(String message) throws ParsingException {
        if (message.startsWith("REQOK")) {
            return new REQOKParser(message);
            
        } else if (message.startsWith("REQERR")) {
            return new REQERRParser(message);
            
        } else if (message.startsWith("ERROR")) {
            return new ERRORParser(message);
            
        } else {
            throw new ParsingException("Unexpected response to control request: " + message);
        }
    }
    
    /**
     * Parses REQOK message.
     */
    public static class REQOKParser extends ControlResponseParser {
        private final long requestId;
        
        public REQOKParser(String message) throws ParsingException {
            // REQOK,<requestId>
            int reqIndex = message.indexOf(',') + 1; 
            if (reqIndex <= 0) {
                // heartbeat REQOKs have no requestId 
                requestId = -1;
            } else {                
                requestId = myParseLong(message.substring(reqIndex), "request field", message);
            }
        }

        public long getRequestId() {
            if (requestId == -1) {
                throw new IllegalStateException("Invalid request identifier");
            }
            return requestId;
        }
    }
    
    /**
     * Parses REQERR message.
     */
    public static class REQERRParser extends ControlResponseParser {
        public final long requestId;
        public final int errorCode;
        public final String errorMsg;
        
        public REQERRParser(String message) throws ParsingException {
            // REQERR,<requestId>,<error code>,<error message>
            String[] pieces = message.trim().split(",", 4);
            if (pieces.length != 4) {
                throw new ParsingException("Unexpected response to control request: " + message);
            }
            requestId = myParseLong(pieces[1], "request identifier", message);
            errorCode = myParseInt(pieces[2], "error code", message);
            errorMsg = EncodingUtils.unquote(pieces[3]);
        }
    }
    
    /**
     * Parses ERROR message.
     */
    public static class ERRORParser extends ControlResponseParser {
        public final int errorCode;
        public final String errorMsg;
        
        public ERRORParser(String message) throws ParsingException {
            // ERROR,<error code>,<error message>
            String[] pieces = message.trim().split(",", 3);
            if (pieces.length != 3) {
                throw new ParsingException("Unexpected response to control request: " + message);
            }
            errorCode = myParseInt(pieces[1], "error code", message);
            errorMsg = EncodingUtils.unquote(pieces[2]);
        }
    }
    
    private static long myParseLong(String field, String description, String orig) throws ParsingException {
        try {
            return Long.parseLong(field);
        } catch (NumberFormatException e) {
            throw new ParsingException("Malformed " + description + " in message: " + orig);
        }
    }
    
    private static int myParseInt(String field, String description, String orig) throws ParsingException {
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException e) {
            throw new ParsingException("Malformed " + description + " in message: " + orig);
        }
    }
    
    public static class ParsingException extends Exception {
        private static final long serialVersionUID = 1L;

        public ParsingException(String string) {
            super(string);
        }
    }
}
