/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.app.calllog;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;

/**
 * Provides operations for managing call-related notifications.
 *
 * <p>It handles the following actions:
 *
 * <ul>
 *   <li>Updating voicemail notifications
 *   <li>Marking new voicemails as old
 *   <li>Updating missed call notifications
 *   <li>Marking new missed calls as old
 *   <li>Calling back from a missed call
 *   <li>Sending an SMS from a missed call
 * </ul>
 */
public class CallLogNotificationsService extends IntentService {

  /** Action to mark all the new voicemails as old. */
  public static final String ACTION_MARK_NEW_VOICEMAILS_AS_OLD =
      "com.android.dialer.calllog.ACTION_MARK_NEW_VOICEMAILS_AS_OLD";

  /** Action to mark all the new missed calls as old. */
  public static final String ACTION_MARK_NEW_MISSED_CALLS_AS_OLD =
      "com.android.dialer.calllog.ACTION_MARK_NEW_MISSED_CALLS_AS_OLD";

  /** Action to update missed call notifications with a post call note. */
  public static final String ACTION_INCOMING_POST_CALL =
      "com.android.dialer.calllog.INCOMING_POST_CALL";

  /** Action to call back a missed call. */
  public static final String ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION =
      "com.android.dialer.calllog.CALL_BACK_FROM_MISSED_CALL_NOTIFICATION";

  /**
   * Extra to be included with {@link #ACTION_INCOMING_POST_CALL} to represent a post call note.
   *
   * <p>It must be a {@link String}
   */
  public static final String EXTRA_POST_CALL_NOTE = "POST_CALL_NOTE";

  /**
   * Extra to be included with {@link #ACTION_INCOMING_POST_CALL} to represent the phone number the
   * post call note came from.
   *
   * <p>It must be a {@link String}
   */
  public static final String EXTRA_POST_CALL_NUMBER = "POST_CALL_NUMBER";

  public static final int UNKNOWN_MISSED_CALL_COUNT = -1;
  private VoicemailQueryHandler mVoicemailQueryHandler;

  public CallLogNotificationsService() {
    super("CallLogNotificationsService");
  }

  public static void insertPostCallNote(Context context, String number, String postCallNote) {
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(ACTION_INCOMING_POST_CALL);
    serviceIntent.putExtra(EXTRA_POST_CALL_NUMBER, number);
    serviceIntent.putExtra(EXTRA_POST_CALL_NOTE, postCallNote);
    context.startService(serviceIntent);
  }

  public static void markNewVoicemailsAsOld(Context context, @Nullable Uri voicemailUri) {
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
    serviceIntent.setData(voicemailUri);
    context.startService(serviceIntent);
  }

  public static void markNewMissedCallsAsOld(Context context, @Nullable Uri callUri) {
    Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
    serviceIntent.setAction(ACTION_MARK_NEW_MISSED_CALLS_AS_OLD);
    serviceIntent.setData(callUri);
    context.startService(serviceIntent);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent == null) {
      LogUtil.d("CallLogNotificationsService.onHandleIntent", "could not handle null intent");
      return;
    }

    if (!PermissionsUtil.hasPermission(this, android.Manifest.permission.READ_CALL_LOG)) {
      return;
    }

    String action = intent.getAction();
    switch (action) {
      case ACTION_MARK_NEW_VOICEMAILS_AS_OLD:
        // VoicemailQueryHandler cannot be created on the IntentService worker thread. The completed
        // callback might happen when the thread is dead.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(
            () -> {
              if (mVoicemailQueryHandler == null) {
                mVoicemailQueryHandler = new VoicemailQueryHandler(this, getContentResolver());
              }
              mVoicemailQueryHandler.markNewVoicemailsAsOld(intent.getData());
            });
        break;
      case ACTION_INCOMING_POST_CALL:
        String note = intent.getStringExtra(EXTRA_POST_CALL_NOTE);
        String phoneNumber = intent.getStringExtra(EXTRA_POST_CALL_NUMBER);
        MissedCallNotifier.getIstance(this).insertPostCallNotification(phoneNumber, note);
        break;
      case ACTION_MARK_NEW_MISSED_CALLS_AS_OLD:
        CallLogNotificationsQueryHelper.removeMissedCallNotifications(this, intent.getData());
        TelecomUtil.cancelMissedCallsNotification(this);
        break;
      case ACTION_CALL_BACK_FROM_MISSED_CALL_NOTIFICATION:
        MissedCallNotifier.getIstance(this)
            .callBackFromMissedCall(
                intent.getStringExtra(
                    MissedCallNotificationReceiver.EXTRA_NOTIFICATION_PHONE_NUMBER),
                intent.getData());
        break;
      default:
        LogUtil.d("CallLogNotificationsService.onHandleIntent", "could not handle: " + intent);
        break;
    }
  }
}
