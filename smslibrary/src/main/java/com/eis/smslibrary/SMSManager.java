package com.eis.smslibrary;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eis.communication.CommunicationManager;
import com.eis.smslibrary.exceptions.InvalidTelephoneNumberException;
import com.eis.smslibrary.listeners.SMSDeliveredListener;
import com.eis.smslibrary.listeners.SMSReceivedServiceListener;
import com.eis.smslibrary.listeners.SMSSentListener;

import java.util.ArrayList;

import it.lucacrema.preferences.PreferencesManager;

/**
 * Communication handler for SMSs. It's a Singleton, you should
 * access it with {@link #getInstance}
 *
 * @author Luca Crema, Marco Mariotto, Alberto Ursino, Marco Tommasini, Marco Cognolato, Giovanni Velludo
 * @since 29/11/2019
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SMSManager implements CommunicationManager<SMSMessage> {

    public static final String SENT_MESSAGE_INTENT_ACTION = "SMS_SENT";
    public static final String DELIVERED_MESSAGE_INTENT_ACTION = "SMS_DELIVERED";
    public static final int RANDOM_STARTING_COUNTER_VALUE_RANGE = 100000;

    /**
     * Singleton instance
     */
    private static SMSManager instance;

    /**
     * Received listener reference
     */
    private SMSReceivedServiceListener receivedListener;

    /**
     * This message counter is used so that we can have a different action name
     * for pending intent (that will call broadcastReceiver). If we were to use the
     * same action name for every message we would have a conflict and we wouldn't
     * know what message has been sent
     */
    private long messageCounter;

    /**
     * Private constructor for Singleton
     */
    private SMSManager() {
        //Random because if we close and open the app the value probably differs
        messageCounter = (int) (Math.random() * RANDOM_STARTING_COUNTER_VALUE_RANGE);
    }

    /**
     * @return the current instance of this class
     */
    public static SMSManager getInstance() {
        if (instance == null)
            instance = new SMSManager();
        return instance;
    }

    /**
     * Sends a message to a destination peer via SMS.
     * Requires {@link android.Manifest.permission#SEND_SMS}
     *
     * @param message to be sent in the channel to a peer
     * @throws InvalidTelephoneNumberException If message.peer.invalidityReason is not null.
     */
    @Override
    public void sendMessage(final @NonNull SMSMessage message)
            throws InvalidTelephoneNumberException {
        sendMessage(message, null, null, null);
    }

    /**
     * Sends a message to a destination peer via SMS then calls the listener.
     * Requires {@link android.Manifest.permission#SEND_SMS}
     *
     * @param message      to be sent in the channel to a peer
     * @param sentListener called on message sent or on error, can be null
     * @param context      The context of the application used to setup the listener
     * @throws InvalidTelephoneNumberException If message.peer.invalidityReason is not null.
     */
    public void sendMessage(final @NonNull SMSMessage message,
                            final @Nullable SMSSentListener sentListener,
                            Context context) throws InvalidTelephoneNumberException {
        sendMessage(message, sentListener, null, context);
    }

    /**
     * Sends a message to a destination peer via SMS then calls the listener.
     * Requires {@link android.Manifest.permission#SEND_SMS}
     *
     * @param message           to be sent in the channel to a peer
     * @param deliveredListener called on message delivered or on error, can be null
     * @param context           The context of the application used to setup the listener
     * @throws InvalidTelephoneNumberException If message.peer.invalidityReason is not null.
     */
    public void sendMessage(final @NonNull SMSMessage message,
                            final @Nullable SMSDeliveredListener deliveredListener,
                            Context context) throws InvalidTelephoneNumberException {
        sendMessage(message, null, deliveredListener, context);
    }

    /**
     * Sends a message to a destination peer via SMS then
     * calls the listener.
     *
     * @param message           to be sent in the channel to a peer
     * @param sentListener      called on message sent or on error, can be null
     * @param deliveredListener called on message delivered or on error, can be null
     * @param context           The context of the application used to setup the listener
     * @throws InvalidTelephoneNumberException If message.peer.invalidityReason is not null.
     */
    public void sendMessage(final @NonNull SMSMessage message,
                            final @Nullable SMSSentListener sentListener,
                            final @Nullable SMSDeliveredListener deliveredListener,
                            Context context) throws InvalidTelephoneNumberException {
        if (message.getPeer().getInvalidityReason() != null) {
            InvalidTelephoneNumberException.Type invReason =
                    message.getPeer().getInvalidityReason();
            String invMessage = message.getPeer().getInvalidityMessage();
            throw new InvalidTelephoneNumberException(invReason, invMessage);
        }
        ArrayList<String> texts = SmsManager.getDefault().divideMessage(getSMSContent(message));
        ArrayList<PendingIntent> sentPIs =
                setupNewSentReceiver(texts, sentListener, message.getPeer(), context);
        ArrayList<PendingIntent> deliveredPIs =
                setupNewDeliverReceiver(texts, deliveredListener, message.getPeer(), context);
        SMSCore.sendMessages(texts, message.getPeer().getAddress(), sentPIs, deliveredPIs);
    }

    /**
     * Creates a new {@link SMSSentBroadcastReceiver} and registers it to receive broadcasts
     * with actions {@value SENT_MESSAGE_INTENT_ACTION}
     *
     * @param texts    the parts of the message to be sent.
     * @param listener the listener to call on broadcast received.
     * @param context  the context of the application used to setup the listener
     * @return an {@link ArrayList} of {@link PendingIntent} to be passed to SMSCore.
     */
    private ArrayList<PendingIntent> setupNewSentReceiver(
            final @NonNull ArrayList<String> texts, final @Nullable SMSSentListener listener,
            final @NonNull SMSPeer peer, Context context) {
        if (listener == null || context == null)
            return null; //Doesn't make any sense to have a BroadcastReceiver if there is no listener

        ArrayList<PendingIntent> intents = new ArrayList<>();
        IntentFilter intentFilter = new IntentFilter();
        for (String text : texts) {
            String actionName = SENT_MESSAGE_INTENT_ACTION + messageCounter++;
            intents.add(PendingIntent.getBroadcast(context, 0, new Intent(actionName), 0));
            intentFilter.addAction(actionName);
        }
        SMSSentBroadcastReceiver onSentReceiver = new SMSSentBroadcastReceiver(texts, listener, peer);
        context.registerReceiver(onSentReceiver, intentFilter);
        return intents;
    }

    /**
     * Creates a new {@link SMSDeliveredBroadcastReceiver} and registers it to receive broadcasts
     * with actions {@value DELIVERED_MESSAGE_INTENT_ACTION}
     *
     * @param texts    the parts of the message to be delivered.
     * @param listener the listener to call on broadcast received.
     * @param context  the context of the application used to setup the listener
     * @return an {@link ArrayList} of {@link PendingIntent} to be passed to SMSCore.
     */
    private ArrayList<PendingIntent> setupNewDeliverReceiver(
            final @NonNull ArrayList<String> texts, final @Nullable SMSDeliveredListener listener,
            final @NonNull SMSPeer peer, Context context) {
        if (listener == null || context == null)
            return null; //Doesn't make any sense to have a BroadcastReceiver if there is no listener

        ArrayList<PendingIntent> intents = new ArrayList<>();
        IntentFilter intentFilter = new IntentFilter();
        for (String text : texts) {
            String actionName = DELIVERED_MESSAGE_INTENT_ACTION + messageCounter++;
            intents.add(PendingIntent.getBroadcast(context, 0, new Intent(actionName), 0));
            intentFilter.addAction(actionName);
        }
        SMSDeliveredBroadcastReceiver onDeliverReceiver = new SMSDeliveredBroadcastReceiver(texts, listener, peer);
        context.registerReceiver(onDeliverReceiver, intentFilter);
        return intents;
    }

    /**
     * Saves in memory the service class name to wake up. It doesn't need an
     * instance of the class, it just saves the name and instantiates it when needed.
     *
     * @param receivedListenerClassName the listener called on message received
     * @param <T>                       the class type that extends {@link SMSReceivedServiceListener} to be called
     * @param context                   the context used to set the listener
     */
    public <T extends SMSReceivedServiceListener> void setReceivedListener(Class<T> receivedListenerClassName, Context context) {
        PreferencesManager.setString(context, SMSReceivedBroadcastReceiver.SERVICE_CLASS_PREFERENCES_KEY, receivedListenerClassName.getName());
    }

    /**
     * Unsubscribe the current {@link SMSReceivedServiceListener} from being called on message arrival
     *
     * @param context The context used to remove the listener
     */
    public void removeReceivedListener(Context context) {
        PreferencesManager.removeValue(context, SMSReceivedBroadcastReceiver.SERVICE_CLASS_PREFERENCES_KEY);
    }

    /**
     * Helper function that gets the message content by using the pre-setup parser in {@link SMSMessageHandler}
     *
     * @param message to get the data from
     * @return the data parsed from the message
     */
    private String getSMSContent(SMSMessage message) {
        return SMSMessageHandler.getInstance().parseData(message);
    }
}
