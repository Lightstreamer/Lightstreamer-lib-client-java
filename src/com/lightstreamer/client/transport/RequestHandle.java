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


package com.lightstreamer.client.transport;

/**
 * Interface to be implemented to permit to Transport users to notify the transport their
 * lost interest in a request.
 */
public interface RequestHandle {

  /**
   * Although the suggested implementation is to stop event notifications and to close the 
   * associated socket, it is also possible to ignore this call: obviously that would be a 
   * waste of resources but can help during development.
   * 
   * @param forceConnectionClose if true, closes the underlying socket;
   * otherwise marks the connection as closed but keeps the socket open
   */
  void close(boolean forceConnectionClose);
  
}
