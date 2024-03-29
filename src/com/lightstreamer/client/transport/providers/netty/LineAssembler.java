/*
 *  Copyright (c) Lightstreamer Srl
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      https://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package com.lightstreamer.client.transport.providers.netty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * Extracts the lines from a byte frame.
 * 
 * 
 * @since January 2017
 */
public class LineAssembler {
    
    private static final Logger log = LogManager.getLogger(Constants.TRANSPORT_LOG);
    
    /* NB
     * This class is synchronized because the HTTP facility may call the constructor and 
     * the method readBytes() in different threads.
     * On the contrary the WebSocket facility calls both methods in the same thread.
     */

    private final RequestListener networkListener;
    private final PeekableByteArrayOutputStream linePart;
    
    private static final byte LF = '\n';
    private static final byte CR = '\r';

    public LineAssembler(RequestListener networkListener) {
//        assert (this.owner = Thread.currentThread()) != null;
        assert networkListener != null;
        this.networkListener = networkListener;
        linePart = new PeekableByteArrayOutputStream();
    }
    
    /**
     * Reads the available bytes and extracts the contained lines. 
     * For each line found the method {@link RequestListener#onMessage(String)} is notified.
     */
    public synchronized void readBytes(ByteBuf buf) {
//        assert this.owner == Thread.currentThread();
        /*
         * A frame has the following structure:
         * <frame> ::= <head><body><tail>
         * 
         * The head of a frame (if present) is the rest of a line started in a previous frame.
         * <head> ::= <rest-previous-line>?
         * <rest-previous-line> ::= <line-part><LF> 
         * NB line-part can be empty. In that case the char CR is in the previous frame.
         * 
         * The body consists of a sequence of whole lines.
         * <body> ::= <line>*
         * <line> ::= <line-body><EOL>
         * 
         * The tail of a frame (if present) is a line lacking the EOL terminator (NB it can span more than one frame).
         * <tail> ::= <line-part>?
         * 
         * EOL is the sequence \r\n.
         * <EOL> ::= <CR><LF>
         * 
         */
        /*
         * NB 
         * startIndex and eolIndex are the most important variables (and the only non-final)
         * and they must be updated together since they represents the next part of frame to elaborate. 
         */
        final int endIndex = buf.readerIndex() + buf.readableBytes(); // ending index of the byte buffer (exclusive)
        int startIndex = buf.readerIndex(); // starting index of the current line/part of line (inclusive)
        int eolIndex; // ending index of the current line/part of line (inclusive) (it points to EOL)
        if (startIndex >= endIndex) {
            return; // byte buffer is empty: nothing to do 
        }
        /* head */
        final boolean hasHead;
        final boolean prevLineIsIncomplete = linePart.size() != 0;
        if (prevLineIsIncomplete) {
            /* 
             * Since the previous line is incomplete (it lacks the line terminator), 
             * is the rest of the line in this frame?
             * We have three cases:
             * A) the char CR is in the previous frame and the char LF is in this one;
             * B) the chars CR and LF are in this frame;
             * C) the sequence CR LF is not in this frame (maybe there is CR but not LF).
             * 
             * If case A) or B) holds, the next part to compute is <head> (see grammar above).
             * In case C) we must compute <tail>.
             */
            if (linePart.peekAtLastByte() == CR && buf.getByte(startIndex) == LF) {
                // case A) EOL is across the previous and the current frame
                hasHead = true;
                eolIndex = startIndex;
            } else {
                eolIndex = findEol(buf, startIndex, endIndex);
                if (eolIndex != -1) {
                    // case B)
                    hasHead = true;
                } else {
                    // case C)
                    hasHead = false;
                }
            }
            
        } else {
            /* 
             * The previous line is complete.
             * We must consider two cases:
             * D) the sequence CR LF is in this frame;
             * E) the sequence CR LF is not in this frame (maybe there is CR but not LF).
             * 
             * If case D) holds, the next part to compute is <body>.
             * If case E) holds, the next part is <tail>.
             */
            hasHead = false;
            eolIndex = findEol(buf, startIndex, endIndex);
        }
        if (hasHead) {
            copyLinePart(buf, startIndex, eolIndex + 1);
            final String line = linePart.toLine();
            networkListener.onMessage(line);
            
            startIndex = eolIndex + 1;
            eolIndex = findEol(buf, startIndex, endIndex);
            linePart.reset();
        }
        /* body */
        while (eolIndex != -1) {
            final String line = byteBufToString(buf, startIndex, eolIndex - 1); // exclude CR LF chars
            networkListener.onMessage(line);
            
            startIndex = eolIndex + 1;
            eolIndex = findEol(buf, startIndex, endIndex);
        }
        /* tail */
        final boolean hasTail = startIndex != endIndex;
        if (hasTail) {
            copyLinePart(buf, startIndex, endIndex);
        }
    }
    
    /**
     * Finds the index of a CR LF sequence (EOL). The index points to LF.
     * Returns -1 if there is no EOL.
     * @param startIndex starting index (inclusive)
     * @param endIndex ending index (exclusive)
     */
    private int findEol(ByteBuf buf, int startIndex, int endIndex) {
        int eolIndex = -1;
        if (startIndex >= endIndex) {
            return eolIndex;
        }
        int crIndex = buf.indexOf(startIndex, endIndex, CR);
        if (crIndex != -1 
                && crIndex != endIndex - 1 // CR it is not the last byte
                && buf.getByte(crIndex + 1) == LF) {
            eolIndex = crIndex + 1;
        }
        return eolIndex;
    }
    
    /**
     * Copies a slice of a frame representing a part of a bigger string in a temporary buffer to be reassembled.
     * @param startIndex starting index (inclusive)
     * @param endIndex ending index (exclusive)
     */
    private void copyLinePart(ByteBuf buf, int startIndex, int endIndex) {
        try {
            buf.getBytes(startIndex, linePart, endIndex - startIndex);
        } catch (IOException e) {
            log.error("Unexpected exception", e); // should not happen
        }
    }
    
    /**
     * Converts a line to a UTF-8 string.
     * @param startIndex starting index (inclusive)
     * @param endIndex ending index (exclusive)
     */
    private String byteBufToString(ByteBuf buf, int startIndex, int endIndex) {
        return buf.toString(startIndex, endIndex - startIndex, CharsetUtil.UTF_8);
    }
    
    private static class PeekableByteArrayOutputStream extends ByteArrayOutputStream {
        
        PeekableByteArrayOutputStream() {
            super(1024);
        }
        
        /**
         * Returns the last byte written.
         */
        byte peekAtLastByte() {
            // assert count > 0;
            return buf[count - 1];
        }
        
        /**
         * Converts the bytes in a UTF-8 string. The last two bytes (which are always '\r' '\n') are excluded.
         */
        String toLine() {
            assert count >= 2;
            assert buf[count - 2] == '\r' && buf[count - 1] == '\n';
            return new String(buf, 0, count - 2, CharsetUtil.UTF_8);
        }
    }
    
}