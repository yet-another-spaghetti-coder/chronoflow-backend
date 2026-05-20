package nus.edu.u.shared.rpc.events;

public interface EventRpcService {
    EventRespDTO getEvent(Long eventId);

    String getEventName(Long eventId);

    boolean exists(Long eventId);
}
