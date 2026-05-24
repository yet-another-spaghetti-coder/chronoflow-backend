package nus.edu.u.provider.push;

import java.util.Map;

public interface PushClient {
    String send(String token, String title, String body, Map<String, Object> data) throws Exception;

    String sendData(String token, Map<String, String> data) throws Exception;
}
