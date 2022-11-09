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


package com.lightstreamer.client.session;

/**
 * 
 */
public interface MessagesListener {
  
  void onSessionStart();
  void onSessionClose();
  
  void onMessageAck(String sequence, int number);

  void onMessageOk(String sequence, int number);

  void onMessageDeny(String sequence, int denyCode, String denyMessage,
      int number);

  void onMessageDiscarded(String sequence, int number);

  void onMessageError(String sequence, int errorCode, String errorMessage,
      int number);
}
