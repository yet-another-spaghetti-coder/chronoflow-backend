package nus.edu.u.provider.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FcmPushClient implements PushClient {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public String send(String token, String title, String body, Map<String, Object> data)
            throws Exception {
        // Build the notification (visible title/body)
        Notification notification = Notification.builder().setTitle(title).setBody(body).build();

        // Build the message to a device token (you can extend to topics if needed)
        Message.Builder msg = Message.builder().setToken(token).setNotification(notification);

        // FCM "data" payload must be Map<String, String>
        if (data != null && !data.isEmpty()) {
            Map<String, String> stringData = new HashMap<>(data.size());
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue; // skip nulls
                stringData.put(e.getKey(), String.valueOf(e.getValue()));
            }
            if (!stringData.isEmpty()) {
                msg.putAllData(stringData);
            }
        }

        // Send and return provider message ID
        return firebaseMessaging.send(msg.build());
    }

    @Override
    public String sendData(String token, Map<String, String> data) throws Exception {
        Message.Builder msg = Message.builder().setToken(token);
        if (data != null && !data.isEmpty()) {
            msg.putAllData(data);
        }
        return firebaseMessaging.send(msg.build());
    }
}
